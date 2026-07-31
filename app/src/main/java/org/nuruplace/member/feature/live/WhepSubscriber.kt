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
package org.nuruplace.member.feature.live

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

class WhepSubscriber(private val context: Context, private val guestId: String) {
    private var peerConnection: PeerConnection? = null
    private var resourceUrl: String? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: org.webrtc.AudioTrack? = null
    private var attachedRenderer: SurfaceViewRenderer? = null
    private var audioSink: AudioTrackSink? = null

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
    // the whole process down exactly as observed. The mutex below makes
    // start()/stop() fully mutually exclusive so that race is structurally
    // impossible, regardless of how erratically the caller invokes them.
    private val lifecycleMutex = Mutex()

    @Volatile private var stopRequested = false

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
     *  broadcaster-authority action, not the guest's own credential. */
    suspend fun start(whepUrl: String, streamId: String, streamKey: String) {
        if (stopRequested) return // a stop() already landed before we even got scheduled — never touch WebRTC.
        lifecycleMutex.withLock {
            if (stopRequested || peerConnection != null) return@withLock
            startLocked(whepUrl, streamId, streamKey)
        }
    }

    private suspend fun startLocked(whepUrl: String, streamId: String, streamKey: String) {
        Log.d(TAG, "guest=$guestId start() begin")
        val factory = LiveWebRtc.factory(context)

        // `pc` is captured by this observer closure — every branch below
        // re-checks `peerConnection === pc` on the MAIN thread (after the
        // hop) before touching any shared state, so a track that arrives
        // for a peer connection this instance has since disposed (stop()
        // already ran, a NEWER start() created a different `pc`) is always
        // a safe, silent no-op instead of a use-after-free.
        lateinit var pcRef: PeerConnection
        val observer = object : SimplePeerConnectionObserver() {
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track() ?: return
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
        val urlWithAuth = "$whepUrl?user=${Uri.encode(streamId)}&pass=${Uri.encode(streamKey)}"
        val result = LiveWebRtc.postSdpOffer(urlWithAuth, finalSdp)
        resourceUrl = result.resourceUrl
        pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.ANSWER, result.answerSdp))
        Log.d(TAG, "guest=$guestId start() subscribed, awaiting tracks")
    }

    /** Mutually exclusive with [start] (see [lifecycleMutex]'s doc) — a
     *  concurrent dispose-while-in-use race on the native PeerConnection is
     *  therefore structurally impossible, regardless of how erratically the
     *  caller invokes start()/stop(). [mainScope] is cancelled FIRST, before
     *  the lock, so any onTrack-triggered attach already queued on Main can
     *  never run once teardown begins. */
    suspend fun stop() {
        stopRequested = true
        mainScope.cancel()
        lifecycleMutex.withLock {
            stopLocked()
        }
    }

    private suspend fun stopLocked() {
        Log.d(TAG, "guest=$guestId stop() begin")
        resourceUrl?.let { LiveWebRtc.deleteResource(it) }
        resourceUrl = null
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
        attachedRenderer = null
        remoteVideoTrack = null
        remoteAudioTrack = null
        audioSink = null
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        Log.d(TAG, "guest=$guestId stop() complete")
    }
}
