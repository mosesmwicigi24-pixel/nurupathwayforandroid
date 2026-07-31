// Nuru Live L6b/L6c — host-side WHEP subscriber: one instance per accepted
// guest, recvonly (video + audio), rendering into a small tile on the
// broadcaster's own HUD (LiveBroadcastScreen.kt's guest rail), feeding the
// guest's decoded audio into GuestAudioMixer.kt so it can also ride the
// outgoing RTMP stream to the congregation (see that file's header for the
// mechanism), and — L6c — feeding the guest's decoded VIDEO into
// GuestStageCompositor.kt so the congregation SEES the stage too, not just
// the host's own local HUD. All three are additive taps on the SAME remote
// tracks (VideoTrack.addSink/AudioTrack.addSink are documented multi-sink),
// never exclusive of each other. The host ALSO simply hears guests via
// WebRTC's own default audio output — addSink() never replaces normal playout.
//
// ── Retry-with-backoff (2026-07-31 fix, docs/PARITY_AUDIT.md) ─────────────
// PROVEN FROM PRODUCTION MediaMTX LOGS (owner testing S24-as-host + iPhone-
// as-guest): the old one-shot [start] fired the INSTANT a guest was marked
// accepted, but the guest's own WHIP publish only began seconds later
// (permission prompt, camera warm-up, ICE). MediaMTX answered "no stream is
// available" and the host gave up FOREVER — the tile showed the guest's own
// name (that comes from our API, unaffected) over a dead black square and
// "Couldn't connect to the stage", making a guest join that was in fact mere
// seconds from working look like the whole feature was broken. [start] is
// now the entire lifetime loop: bounded exponential backoff (WhepRetryPolicy)
// treating "not yet available" (HTTP 404/5xx, network hiccups) and MediaMTX's
// own "deadline exceeded while waiting tracks" (a peer connection that
// connected but never received media — WhepTracksTimeoutException,
// WhepRetryPolicy.TRACK_WAIT_TIMEOUT_MS) as retryable, not terminal; and once
// actually live, monitoring the peer connection's ICE state so a guest that
// stops and restarts publishing (backgrounded, network flip, MediaMTX
// "closed: terminated") is recovered automatically — a fresh connect cycle
// with its OWN fresh retry window, not stuck on a dead tile. [connectionState]
// is the honest, Compose-observable source of truth the tile renders from
// (GuestStageUi.kt's GuestTile) — Connecting/Live/Error, matching the shape
// of the guest's own [GuestStageState] — so the UI never shows "Couldn't
// connect" while a retry is still in flight.
package org.nuruplace.member.feature.live

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.nuruplace.member.feature.live.LiveWebRtc.awaitIceGatheringComplete
import org.nuruplace.member.feature.live.LiveWebRtc.createOfferSuspend
import org.nuruplace.member.feature.live.LiveWebRtc.setLocalDescriptionSuspend
import org.nuruplace.member.feature.live.LiveWebRtc.setRemoteDescriptionSuspend
import org.webrtc.AudioTrackSink
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private const val TAG = "WhepSubscriber"

/** Honest connection state for a single guest's tile — mirrors the SHAPE of
 *  the guest's own [GuestStageState] (Connecting/Live/Error) so both sides of
 *  the same WHIP/WHEP handshake read consistently, even though this is a
 *  distinct concept (the HOST observing a remote guest, not a guest's own
 *  publish status). GuestStageUi.kt's GuestTile renders straight off this —
 *  never a static "Couldn't connect" while [start]'s retry loop is still
 *  within its window. */
sealed class WhepConnectionState {
    data object Connecting : WhepConnectionState()
    data object Live : WhepConnectionState()
    data class Error(val message: String) : WhepConnectionState()
}

class WhepSubscriber(private val context: Context, private val guestId: String) {
    private var peerConnection: PeerConnection? = null
    private var resourceUrl: String? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: org.webrtc.AudioTrack? = null
    private var attachedRenderer: SurfaceViewRenderer? = null
    private var audioSink: AudioTrackSink? = null

    // WHEP auth credentials for THIS session — see WhipPublisher.kt's
    // matching field doc. stop()'s teardown DELETE needs the same Basic auth
    // header the offer POST used (query-param auth is silently ignored by
    // MediaMTX v1.19.3 for WHIP/WHEP).
    private var authUser: String? = null
    private var authPass: String? = null

    /** Compose-observable — GuestStageUi.kt's HostGuestTileState reads this
     *  directly per guest (see LiveBroadcastScreen.kt's guestTiles mapping)
     *  instead of a boolean-ish error map, so the tile can distinguish
     *  "still retrying" from "genuinely failed" honestly. */
    var connectionState by mutableStateOf<WhepConnectionState>(WhepConnectionState.Connecting)
        private set

    // ── Native-crash prevention (2026-07-31 host-process-death investigation) ──
    //
    // Real-device evidence: the HOST APP PROCESS ITSELF DIES the instant a
    // guest joins — a full process death, not a degraded stream, which means
    // a native (JNI-level) crash, not a Kotlin exception (try/catch cannot
    // save the process from that). LiveBroadcastScreen.kt's
    // `LaunchedEffect(guestVideoGuests.map { it.userId }.toSet())` re-runs
    // this create/destroy dance on EVERY pulse poll that changes the set of
    // accepted+whepUrl guest ids — and `stop()` starts with a suspending
    // network call (LiveWebRtc.deleteResource) BEFORE touching WebRTC
    // objects. If a guest's row flickers out of the pulse (even briefly —
    // e.g. the backend mints the WHEP URL a beat after flipping status to
    // "accepted", or a transient pulse gap) while THIS instance's start()
    // is still mid-flight (its own network round trips: postSdpOffer, ICE
    // gathering up to 4s), the LaunchedEffect calls stop() on the SAME
    // instance CONCURRENTLY with the in-flight start() — both touching the
    // same native PeerConnection with no mutual exclusion. dispose() racing
    // addTransceiver()/createOffer()/setRemoteDescription() on the same
    // native pointer is a textbook use-after-free -> SIGSEGV, which takes
    // the whole process down exactly as observed. The mutex below makes each
    // INDIVIDUAL connect attempt mutually exclusive with stop() so that race
    // is structurally impossible — deliberately scoped to just the attempt
    // (not the retry loop's backoff waits or the post-connect monitoring
    // suspension), so a stop() during a backoff delay or while merely
    // watching for a drop can proceed immediately instead of blocking for up
    // to the backoff/track-wait duration. */
    private val lifecycleMutex = Mutex()

    @Volatile private var stopRequested = false

    // Guards against two concurrent [start] calls (e.g. a stray double
    // Retry tap) driving two independent retry loops against the same
    // instance — the second call is simply a no-op; the loop already
    // running owns [peerConnection]/[resourceUrl] end to end.
    @Volatile private var running = false

    // Completed the instant ANY remote track arrives for the CURRENT attempt
    // (see connectLocked) — what [start]'s per-attempt track-wait timeout
    // awaits. Completed by onTrack on WebRTC's own thread (CompletableDeferred
    // is thread-safe); cancelled by [stop] so an attempt suspended waiting on
    // it during teardown wakes immediately instead of riding out the full
    // TRACK_WAIT_TIMEOUT_MS with the lifecycle lock held.
    @Volatile private var trackArrivedSignal: CompletableDeferred<Unit>? = null

    // Completed when the CURRENT attempt's peer connection reports
    // DISCONNECTED/FAILED/CLOSED after having gone live — the "recover from
    // republish" signal (a guest backgrounded, flipped networks, or MediaMTX
    // itself closed the session because the guest stopped and restarted
    // publishing). Cancelled by [stop] for the same reason as
    // [trackArrivedSignal].
    @Volatile private var connectionDropSignal: CompletableDeferred<Unit>? = null

    // onTrack fires on WebRTC's own internal signaling thread, never the
    // Android main thread. RootEncoder's SurfaceFilterRender (consumed by
    // GuestStageCompositor) is documented, verbatim in its own source
    // (encoder/.../SurfaceFilterRender.java): "This surface must be
    // rendered using an api called on main thread to avoid possible
    // errors." Calling into the compositor straight from the signaling
    // thread violates that contract. This scope hops every onTrack-driven
    // attach onto Main before it ever touches GuestStageCompositor/GL
    // objects; it's cancelled in stop() so a track that arrives (or an
    // attach that was merely QUEUED) after teardown can never run against
    // objects stop() is disposing.
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Attaches/detaches the tile's SurfaceViewRenderer independently of
     *  [start]/[stop] — the host's guest rail (HostGuestRail, GuestStageUi.kt)
     *  composes its AndroidView (and therefore creates the renderer) on
     *  Compose's own schedule, which may land before OR after this
     *  subscriber's remote track actually arrives via onTrack. Whichever
     *  happens second wires the sink; harmless no-op the other way. */
    fun attachRenderer(renderer: SurfaceViewRenderer) {
        attachedRenderer = renderer
        remoteVideoTrack?.addSink(renderer)
    }

    fun detachRenderer(renderer: SurfaceViewRenderer) {
        remoteVideoTrack?.removeSink(renderer)
        if (attachedRenderer === renderer) attachedRenderer = null
    }

    /** Subscribes to [whepUrl] (owner-only, from pulse.guests[].whepUrl) —
     *  authenticated the SAME way the RTMP publish URL is (see
     *  LiveBroadcastEngine.kt's buildPublishUrl comment): the stream's own
     *  id + streamKey as `?user=&pass=`, since a WHEP subscription is a
     *  broadcaster-authority action, not the guest's own credential.
     *
     *  Runs for the WHOLE lifetime of the caller's coroutine (normally the
     *  guest's entry in LiveBroadcastScreen's `whepStartJobs`, cancelled on
     *  Leave/removal) — NOT a single attempt. Internally: connect with
     *  backoff retry until live or [WhepRetryPolicy]'s window expires
     *  (surfaced as [connectionState] = Error at that point, never before);
     *  once live, monitor for a drop and transparently reconnect with a
     *  FRESH window rather than returning. Only actually returns once
     *  [stop] has been called or a connect cycle ends in a genuine,
     *  non-retryable/expired failure. Safe to call again afterwards (e.g. a
     *  user-tapped Retry once [connectionState] is Error) as long as [stop]
     *  was never called on this instance. */
    suspend fun start(whepUrl: String, streamId: String, streamKey: String) {
        if (stopRequested) return // a stop() already landed — never touch WebRTC.
        if (running) return // a loop is already driving this instance — no-op, not a second competing loop.
        running = true
        try {
            while (!stopRequested) {
                val shouldReconnect = runConnectCycle(whepUrl, streamId, streamKey)
                if (!shouldReconnect) return
            }
        } finally {
            running = false
        }
    }

    private sealed class AttemptResult {
        data object Connected : AttemptResult()
        data class Failed(val failure: WhepFailure) : AttemptResult()
    }

    /** One full "get to Live, then watch until it drops" cycle, with its OWN
     *  fresh retry window (a guest republish after a long-lived successful
     *  session starts over, never inherits the original window — the whole
     *  point of "recover from republish" is that it looks like a brand new,
     *  generously-retried join, not a countdown already half spent).
     *  Returns true if the caller should immediately start ANOTHER cycle
     *  (a drop was recovered and we're still not stopped), false once
     *  [start] should actually return (stopped, or a genuine terminal/
     *  window-expired failure — [connectionState] is already set to Error
     *  in that case). */
    private suspend fun runConnectCycle(whepUrl: String, streamId: String, streamKey: String): Boolean {
        connectionState = WhepConnectionState.Connecting
        val windowStartMs = System.currentTimeMillis()
        var attempt = 0
        while (!stopRequested) {
            val result = lifecycleMutex.withLock {
                if (stopRequested) AttemptResult.Failed(WhepFailure.Terminal("stopped"))
                else attemptOnceLocked(whepUrl, streamId, streamKey)
            }
            if (stopRequested) return false
            when (result) {
                is AttemptResult.Connected -> {
                    connectionState = WhepConnectionState.Live
                    Log.d(TAG, "guest=$guestId connected — awaiting a drop or stop")
                    awaitDrop()
                    // Whatever happened (drop signaled, or the deferred was
                    // cancelled by stop()), the attempt's resources need
                    // tearing down before either reconnecting or returning —
                    // stopLocked() is safe to call unconditionally (every
                    // field it clears is already null-guarded) and this
                    // mirrors [stop]'s own teardown exactly, just without
                    // flipping [stopRequested].
                    runCatching { lifecycleMutex.withLock { stopLocked() } }
                    if (stopRequested) return false
                    Log.w(TAG, "guest=$guestId dropped after being live — recovering with a fresh connect cycle")
                    return true
                }
                is AttemptResult.Failed -> {
                    val elapsed = System.currentTimeMillis() - windowStartMs
                    if (!result.failure.isRetryable || WhepRetryPolicy.hasWindowExpired(elapsed)) {
                        connectionState = WhepConnectionState.Error(result.failure.reason)
                        return false
                    }
                    connectionState = WhepConnectionState.Connecting
                    val delayMs = WhepRetryPolicy.backoffDelayMs(attempt)
                    attempt++
                    Log.d(TAG, "guest=$guestId attempt failed ($result), retrying in ${delayMs}ms")
                    delay(delayMs)
                }
            }
        }
        return false
    }

    /** Suspends until the current attempt's peer connection reports a drop,
     *  or [stop] cancels the signal — never throws for either case (a
     *  cancelled [connectionDropSignal] during shutdown is an EXPECTED wake,
     *  not an error). */
    private suspend fun awaitDrop() {
        val signal = connectionDropSignal ?: return
        runCatching { signal.await() }
    }

    private suspend fun attemptOnceLocked(whepUrl: String, streamId: String, streamKey: String): AttemptResult {
        if (stopRequested) return AttemptResult.Failed(WhepFailure.Terminal("stopped"))
        return try {
            connectLocked(whepUrl, streamId, streamKey)
            AttemptResult.Connected
        } catch (e: CancellationException) {
            throw e // never swallow cancellation — let structured concurrency actually stop us.
        } catch (e: Throwable) {
            Log.w(TAG, "guest=$guestId WHEP subscribe attempt failed (isolated, retried per WhepRetryPolicy unless terminal)", e)
            runCatching { teardownFailedAttemptLocked() }
            AttemptResult.Failed(classifyWhepThrowable(e))
        }
    }

    /** One subscribe attempt: create a fresh PeerConnection, exchange SDP,
     *  then wait up to [WhepRetryPolicy.TRACK_WAIT_TIMEOUT_MS] for the FIRST
     *  remote track to actually arrive — MediaMTX's own "deadline exceeded
     *  while waiting tracks" mirrored client-side via
     *  [WhepTracksTimeoutException] rather than left to hang forever on a
     *  peer connection that's technically established but carrying nothing.
     *  Throws on any failure (caller classifies + retries); returns
     *  normally only once real media has started flowing. */
    private suspend fun connectLocked(whepUrl: String, streamId: String, streamKey: String) {
        Log.d(TAG, "guest=$guestId connect attempt begin")
        authUser = streamId
        authPass = streamKey
        val factory = LiveWebRtc.factory(context)

        val trackSignal = CompletableDeferred<Unit>()
        val dropSignal = CompletableDeferred<Unit>()
        trackArrivedSignal = trackSignal
        connectionDropSignal = dropSignal

        // `pc` is captured by this observer closure — every branch below
        // re-checks `peerConnection === pc` on the MAIN thread (after the
        // hop) before touching any shared state, so a track that arrives
        // for a peer connection this instance has since disposed (stop()
        // already ran, a NEWER attempt created a different `pc`) is always
        // a safe, silent no-op instead of a use-after-free.
        lateinit var pcRef: PeerConnection
        val observer = object : SimplePeerConnectionObserver() {
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                if (peerConnection !== pcRef) return // stale callback from an already-torn-down pc.
                val isDrop = state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED ||
                    state == PeerConnection.IceConnectionState.CLOSED
                if (isDrop) dropSignal.complete(Unit)
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track() ?: return
                // Signal connectivity immediately, synchronously — do NOT
                // wait for the Main-thread hop below, that's purely for the
                // compositor/mixer attach and would needlessly delay
                // unblocking the track-wait timeout.
                trackSignal.complete(Unit)
                // Hop to Main BEFORE touching GuestStageCompositor/GuestAudioMixer
                // — see mainScope's doc above (RootEncoder's own documented
                // main-thread-only contract for the Surface these feed into).
                // Cancelled by stop(), so a track arriving after teardown is
                // requested never runs this block at all.
                mainScope.launch {
                    runCatching {
                        if (peerConnection !== pcRef) return@launch // stale callback from an already-torn-down pc — ignore.
                        when (track) {
                            is VideoTrack -> {
                                remoteVideoTrack = track
                                attachedRenderer?.let { track.addSink(it) }
                                GuestStageCompositor.attachVideo(guestId, track)
                            }
                            is org.webrtc.AudioTrack -> {
                                remoteAudioTrack = track
                                val sink = GuestAudioMixer.attach(guestId)
                                audioSink = sink
                                track.addSink(sink)
                            }
                        }
                    }.onFailure { e -> Log.w(TAG, "guest=$guestId onTrack attach failed (isolated, broadcast unaffected)", e) }
                }
            }
        }
        val pc = factory.createPeerConnection(LiveWebRtc.rtcConfig(), observer)
            ?: throw IllegalStateException("createPeerConnection returned null")
        pcRef = pc
        peerConnection = pc

        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        pc.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )

        val offer = pc.createOfferSuspend()
        pc.setLocalDescriptionSuspend(offer)
        pc.awaitIceGatheringComplete()

        val finalSdp = pc.localDescription?.description ?: offer.description
        // Non-201 responses throw LiveWebRtc.WhipWhepHttpException (carries
        // the HTTP status) — a 404 here is the PROVEN "guest hasn't started
        // publishing yet" race from production logs, classified retryable
        // by WhepRetryPolicy.classifyWhepHttpStatus, not a hard failure.
        val result = LiveWebRtc.postSdpOffer(whepUrl, finalSdp, streamId, streamKey)
        resourceUrl = result.resourceUrl
        pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.ANSWER, result.answerSdp))
        Log.d(TAG, "guest=$guestId subscribed, awaiting first track (timeout ${WhepRetryPolicy.TRACK_WAIT_TIMEOUT_MS}ms)")

        val arrived = withTimeoutOrNull(WhepRetryPolicy.TRACK_WAIT_TIMEOUT_MS) { trackSignal.await() }
        if (arrived == null) {
            // Mirrors MediaMTX's own "deadline exceeded while waiting
            // tracks" wording — the peer connection connected but nothing
            // ever arrived. classifyWhepThrowable maps this to
            // WhepFailure.TracksTimedOut (retryable); the catch in
            // attemptOnceLocked tears this half-dead connection down before
            // the next attempt, per the fix brief ("never leave a half-dead
            // connection").
            throw WhepTracksTimeoutException()
        }
        Log.d(TAG, "guest=$guestId first track arrived — connected")
    }

    /** Disposes a FAILED attempt's partial WebRTC state so the next retry
     *  starts from a clean slate — deliberately does NOT touch
     *  [stopRequested]/[authUser]/[authPass] (this is a mid-loop cleanup,
     *  not a real [stop]). If the offer POST had already succeeded (e.g. the
     *  failure was [WhepTracksTimeoutException] — connected but silent),
     *  courtesy-DELETEs that resource rather than just dropping the
     *  reference and leaving MediaMTX to age it out on its own. Safe even if
     *  the attempt failed before [peerConnection]/[resourceUrl] were ever
     *  assigned. */
    private suspend fun teardownFailedAttemptLocked() {
        trackArrivedSignal = null
        connectionDropSignal = null
        resourceUrl?.let { url ->
            val user = authUser
            val pass = authPass
            if (user != null && pass != null) LiveWebRtc.deleteResource(url, user, pass)
        }
        resourceUrl = null
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
    }

    /** Mutually exclusive, per-attempt, with [stop] (see [lifecycleMutex]'s
     *  doc) — a concurrent dispose-while-in-use race on the native
     *  PeerConnection is therefore structurally impossible for any single
     *  attempt, regardless of how erratically the caller invokes start()/
     *  stop(). [mainScope] is cancelled FIRST, before anything else, so any
     *  onTrack-triggered attach already queued on Main can never run once
     *  teardown begins; the track/drop signals are cancelled next so an
     *  attempt currently suspended waiting on either of them (inside the
     *  lock) wakes immediately instead of riding out its full timeout with
     *  the lock held — which is what lets THIS call's own
     *  `lifecycleMutex.withLock` below actually proceed promptly. */
    suspend fun stop() {
        stopRequested = true
        mainScope.cancel()
        trackArrivedSignal?.cancel()
        connectionDropSignal?.cancel()
        lifecycleMutex.withLock {
            stopLocked()
        }
    }

    private suspend fun stopLocked() {
        Log.d(TAG, "guest=$guestId stop() begin")
        trackArrivedSignal = null
        connectionDropSignal = null
        resourceUrl?.let { url ->
            val user = authUser
            val pass = authPass
            if (user != null && pass != null) LiveWebRtc.deleteResource(url, user, pass)
        }
        resourceUrl = null
        authUser = null
        authPass = null
        // AWAITED, not fire-and-forget — GuestStageCompositor.detachVideo is
        // main-thread-dispatched internally (see its own doc); this suspend
        // point guarantees BOTH detaches fully complete before dispose()
        // below ever runs. That's the piece that closes the crash window:
        // without awaiting, dispose() could run while a Main-thread reflow()
        // is still mid addSink/removeSink on the very track being disposed.
        GuestAudioMixer.detach(guestId)
        GuestStageCompositor.detachVideo(guestId)
        remoteVideoTrack?.let { track ->
            attachedRenderer?.let { renderer -> runCatching { track.removeSink(renderer) } }
        }
        // Symmetric with the video sink above — previously never explicitly
        // removed, relying entirely on dispose() to implicitly clean it up.
        remoteAudioTrack?.let { track ->
            audioSink?.let { sink -> runCatching { track.removeSink(sink) } }
        }
        remoteVideoTrack = null
        remoteAudioTrack = null
        audioSink = null
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        Log.d(TAG, "guest=$guestId stop() complete")
    }
}
