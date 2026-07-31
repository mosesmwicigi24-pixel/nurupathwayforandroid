// Nuru Live L6b — shared WebRTC plumbing for both the guest publisher
// (WhipPublisher.kt) and the host subscriber (WhepSubscriber.kt): a single
// process-wide PeerConnectionFactory/EglBase (WebRTC's own guidance — one
// factory per process, expensive to recreate), suspend wrappers around
// SdpObserver/PeerConnection's callback-style API, and the WHIP/WHEP HTTP
// exchange itself (POST SDP offer -> 201 + SDP answer + Location header;
// DELETE Location to tear down). See docs/LIVE_INTERACTIVE.md's pinned wire
// contract for the exact shape MediaMTX expects.
//
// Auth (REVISED 2026-07-31 — proven wrong the first time): MediaMTX's
// WHIP/WHEP auth (same `authMethod: http` webhook as RTMP) was originally
// carried as query params baked into the request URL (`?user=<id>&pass=
// <token>`). A controlled experiment against production proved that's dead
// on arrival: MediaMTX v1.19.3 simply IGNORES `user`/`pass` query params on
// WHIP/WHEP endpoints — POSTing with `?user=PROBEUSER&pass=PROBEPASS` still
// forwarded an EMPTY user to the auth webhook, which is exactly the guest
// join failure ("Couldn't connect to the stage" / webhook 400
// VALIDATION_FAILED on `user`). The SAME probe with HTTP Basic auth (an
// `Authorization: Basic base64(user:pass)` header) reached the webhook with
// the real username. Basic auth is the only mechanism this MediaMTX version
// honours for WHIP/WHEP, so both callers now send it as a header via
// [postSdpOffer]/[deleteResource] and credentials never touch the URL
// (query strings leak into logs/proxies) — see [basicAuthHeader].
package org.nuruplace.member.feature.live

import android.content.Context
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Process-wide WebRTC plumbing — created lazily on first guest publish or
 *  first host subscribe, whichever comes first, and kept alive for the rest
 *  of the process (matches how PeerConnectionFactory is meant to be used;
 *  tearing it down between broadcasts/guest sessions buys nothing and risks
 *  a slow re-init mid-stream). */
object LiveWebRtc {
    private val sdpMediaType = "application/sdp".toMediaType()

    val eglBase: EglBase by lazy { EglBase.create() }

    // ── One-shot native init guard (2026-07-31 host-process-death incident,
    // round 2) ──────────────────────────────────────────────────────────────
    //
    // Real root cause, PROVEN by disassembling the exact .so this app ships
    // (io.github.webrtc-sdk:android:144.7559.09's libjingle_peerconnection_
    // so.so) at the tombstone's own offsets: JNI_OnLoad -> InitGlobalJniVariables
    // succeeds (g_jvm was null, first load — the earlier "JNI_OnLoad called
    // twice" hypothesis is NOT what fires), then falls into WebRTC's jni_zero
    // bootstrap, which calls `env->FindClass("org/jni_zero/JniInit")`. R8's
    // release build strips that class — `-keep class org.webrtc.** { *; }`
    // (added for a PRIOR, related incident, see proguard-rules.pro) does not
    // cover the separate org.jni_zero package the SDK's JNI bridge depends
    // on, and confirmed absent from both mapping.txt and the shipped
    // classes*.dex of the exact release build (vc56) the tombstone came
    // from. FindClass returns null -> ExceptionCheck() true -> WebRTC's own
    // RTC_CHECK/CHECK fails -> its FatalMessage formatter -> __builtin_trap()
    // (BRK #imm) -> SIGTRAP/TRAP_BRKPT, matching the tombstone exactly. Fixed
    // at the source in proguard-rules.pro (org.jni_zero.** now kept).
    //
    // This guard is the belt-and-suspenders half of that fix: a native trap
    // cannot be caught by try/catch, so the ONLY thing Kotlin code can do
    // about a WebRTC init failure is (a) never re-enter the risky native
    // call a second time once one attempt has run, and (b) make sure that
    // one attempt happens at a moment the app can afford to lose (a warm-up
    // probe, see [warmUp]) rather than the exact instant a guest joins a
    // live broadcast. [OneShotInit] guarantees exactly one [PeerConnectionFactory]
    // creation attempt per process no matter how many threads call
    // [factory]/[warmUp] concurrently (WhipPublisher on a guest device,
    // WhepSubscriber on the host, and any warm-up call can all race) — every
    // caller after the first gets the SAME outcome (the cached factory, or
    // the SAME cached, catchable exception) instead of each independently
    // retrying a call that's already proven risky.
    private val factoryInit = OneShotInit<PeerConnectionFactory>()

    /** Non-null once a [factory]/[warmUp] call has failed with a catchable
     *  (non-native-trap) error — e.g. [UnsatisfiedLinkError] if the .so is
     *  missing entirely, or a Java-level [RuntimeException] from the SDK.
     *  Surfaced by callers as an in-app "guest video unavailable" chip
     *  instead of silently retrying the same risky call. Null means either
     *  "not yet attempted" or "succeeded". */
    fun lastInitError(): Throwable? = factoryInit.failure()

    fun isReady(): Boolean = factoryInit.value() != null

    /** Proactively runs WebRTC's native init OFF the guest-join critical
     *  path — call this from Go Live setup (host, before the broadcast
     *  actually starts) and from the guest's "Join stage" entry point
     *  (before a guest attempts to publish), not lazily on first use. By the
     *  time a guest actually joins a live broadcast, WebRTC is therefore
     *  either already initialized (no risky first-load left to trigger) or
     *  already known-failed (surfaced here, in a setup screen that can show
     *  a retry/error chip, instead of crashing mid-broadcast). Safe to call
     *  from a background dispatcher; safe to call redundantly (subsequent
     *  calls are free — see [OneShotInit]). */
    fun warmUp(context: Context): Result<Unit> =
        runCatching { factory(context) }.map {}

    // Owner-flagged hypothesis (2026-07-31, demoted after device evidence
    // showed a full HOST PROCESS DEATH, not a degraded/silent publish — a mic
    // conflict alone can't kill the process). Kept as a real, independently
    // worthwhile fix: verified via javap against the resolved io.github.
    // webrtc-sdk:android:144.7559.09 Builder.createPeerConnectionFactory()
    // bytecode that when no AudioDeviceModule is supplied, WebRTC silently
    // creates a DEFAULT `JavaAudioDeviceModule.builder(context).
    // createAudioDeviceModule()` — a long-standing WebRTC-Android gotcha
    // (upstream bug 8214: the ADM opens AudioRecord even for a purely
    // RECV_ONLY connection) that this SDK version's `setAudioRecordEnabled`
    // API exists specifically to work around. On the HOST device only
    // WhepSubscriber (recvonly, never sends audio) ever touches this
    // factory — RootEncoder owns the real broadcast mic entirely outside
    // WebRTC (MixingMicrophoneSource, GuestAudioMixer.kt) — so the host has
    // ZERO legitimate need for WebRTC audio recording, ever. Recording stays
    // OFF by default and is only switched on for the narrow window a GUEST
    // device (WhipPublisher, LivePlayerScreen — never the host/broadcast
    // screen) is actually sending its own mic. See setAudioRecordEnabled().
    @Volatile
    private var audioDeviceModuleRef: JavaAudioDeviceModule? = null

    /** A public STUN server for ICE server-reflexive candidates — MediaMTX
     *  itself sits on a publicly routable VPS (no NAT on its side), so once
     *  our own device has ANY reachable candidate (host on a public network,
     *  or srflx via STUN when behind carrier/NAT) the ICE handshake can
     *  complete without a TURN relay. */
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    )

    /** Creates (once — see [factoryInit]) or returns the cached process-wide
     *  [PeerConnectionFactory]. Prefer calling [warmUp] proactively at a safe
     *  moment; if a first attempt already failed, this rethrows the SAME
     *  cached exception immediately without ever re-entering the native call
     *  that proved risky. */
    fun factory(context: Context): PeerConnectionFactory =
        factoryInit.getOrInit { createFactory(context) }

    private fun createFactory(context: Context): PeerConnectionFactory {
        val appContext = context.applicationContext
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, /* enableIntelVp8Encoder */ true, /* enableH264HighProfile */ true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        // Explicit ADM (rather than letting the Builder default one in)
        // so we hold a reference to gate recording — see the field doc
        // above. Off by default; WhipPublisher flips it on/off around
        // its own publish lifecycle.
        val adm = JavaAudioDeviceModule.builder(appContext).createAudioDeviceModule()
        runCatching { adm.setAudioRecordEnabled(false) }
        audioDeviceModuleRef = adm
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    /** Guest-publish-only mic gate (see [audioDeviceModuleRef]'s doc) —
     *  WhipPublisher calls this true right before it creates its own local
     *  AudioSource/AudioTrack, and false the moment it stops. A no-op if the
     *  factory hasn't been created yet (setAudioRecordEnabled(true) before
     *  any publish would be pointless — WhipPublisher always calls
     *  [factory] first in its own start()). */
    fun setAudioRecordEnabled(enabled: Boolean) {
        runCatching { audioDeviceModuleRef?.setAudioRecordEnabled(enabled) }
    }

    fun rtcConfig(): PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // MediaMTX's WHIP/WHEP is gather-then-send (no trickle ICE
            // signaling channel exists in the HTTP exchange) — bundling +
            // rtcp-mux keep the single SDP compact and match every WHIP
            // client MediaMTX is tested against (its own docs' examples).
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

    // ── SdpObserver / PeerConnection callback -> suspend bridges ───────────

    suspend fun PeerConnection.createOfferSuspend(constraints: MediaConstraints = MediaConstraints()): SessionDescription =
        suspendCancellableCoroutine { cont ->
            createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(sdp: SessionDescription) { cont.resume(sdp) }
                override fun onCreateFailure(error: String?) { cont.resumeWithException(IllegalStateException("createOffer failed: $error")) }
            }, constraints)
        }

    suspend fun PeerConnection.setLocalDescriptionSuspend(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            setLocalDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(error: String?) { cont.resumeWithException(IllegalStateException("setLocalDescription failed: $error")) }
            }, sdp)
        }

    suspend fun PeerConnection.setRemoteDescriptionSuspend(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() { cont.resume(Unit) }
                override fun onSetFailure(error: String?) { cont.resumeWithException(IllegalStateException("setRemoteDescription failed: $error")) }
            }, sdp)
        }

    /** Gather-then-send: poll iceGatheringState() rather than wire up
     *  onIceGatheringChange, since both callers just need "done or gave up"
     *  — simpler than a second observer callback path. Caps at [timeoutMs]
     *  so a slow/absent STUN response can never hang the publish/subscribe
     *  flow forever; whatever candidates gathered by then (usually just the
     *  host candidate, sent within the first poll or two) still go out. */
    suspend fun PeerConnection.awaitIceGatheringComplete(timeoutMs: Long = 4_000) {
        withTimeoutOrNull(timeoutMs) {
            while (iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) {
                delay(100)
            }
        }
    }

    // ── WHIP/WHEP HTTP exchange ─────────────────────────────────────────────

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    data class WhipWhepResult(val answerSdp: String, val resourceUrl: String)

    /** Builds the `Authorization: Basic base64(user:pass)` header value —
     *  the ONLY auth mechanism MediaMTX v1.19.3 honours for WHIP/WHEP (see
     *  this file's header doc for the proof). Pure/stateless so it's
     *  directly unit-testable without a network stack; uses [java.util.Base64]
     *  (not `android.util.Base64`) specifically so it also runs unmocked in
     *  plain JVM unit tests. */
    internal fun basicAuthHeader(user: String, pass: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray(Charsets.UTF_8))

    /** Builds the WHIP/WHEP offer POST — [url] is the bare endpoint with NO
     *  query string (credentials never go in the URL: they leak into
     *  server/proxy logs). Split out from [postSdpOffer] so tests can
     *  inspect the built [Request] (header present, URL has no query)
     *  without making a real network call. */
    internal fun buildSdpOfferRequest(url: String, offerSdp: String, user: String, pass: String): Request =
        Request.Builder()
            .url(url)
            .header("Authorization", basicAuthHeader(user, pass))
            .post(offerSdp.toRequestBody(sdpMediaType))
            .build()

    /** Builds the teardown DELETE against the WHIP/WHEP `Location` resource
     *  URL — carries the SAME Basic auth header as the offer POST.
     *  MediaMTX's session-resource DELETE is authenticated the same way as
     *  the initial POST; skipping the header here silently no-ops the
     *  teardown server-side (session lingers until MediaMTX's own idle
     *  timeout) rather than failing loudly, so this is real, not
     *  speculative, hardening. */
    internal fun buildDeleteRequest(resourceUrl: String, user: String, pass: String): Request =
        Request.Builder()
            .url(resourceUrl)
            .header("Authorization", basicAuthHeader(user, pass))
            .delete()
            .build()

    /** POSTs the local SDP offer to [url] (bare, no query — see
     *  [basicAuthHeader]) and returns the SDP answer body plus the resource
     *  URL to DELETE on teardown (the `Location` header, resolved against
     *  the request URL if relative — WHIP/WHEP servers are allowed to send
     *  either). Throws on any non-201 response. */
    suspend fun postSdpOffer(url: String, offerSdp: String, user: String, pass: String): WhipWhepResult =
        withContext(Dispatchers.IO) {
            val request = buildSdpOfferRequest(url, offerSdp, user, pass)
            httpClient.newCall(request).execute().use { response ->
                if (response.code != 201) {
                    throw IllegalStateException("WHIP/WHEP POST failed: HTTP ${response.code}")
                }
                val answer = response.body?.string().orEmpty()
                if (answer.isBlank()) throw IllegalStateException("WHIP/WHEP POST returned an empty SDP answer")
                val location = response.header("Location")
                    ?: throw IllegalStateException("WHIP/WHEP POST response missing Location header")
                val resourceUrl = resolveAgainst(url, location)
                WhipWhepResult(answer, resourceUrl)
            }
        }

    suspend fun deleteResource(resourceUrl: String, user: String, pass: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = buildDeleteRequest(resourceUrl, user, pass)
                httpClient.newCall(request).execute().close()
            }
        }
    }

    private fun resolveAgainst(baseUrl: String, maybeRelative: String): String =
        runCatching { URI(baseUrl).resolve(maybeRelative).toString() }.getOrDefault(maybeRelative)
}

/** Guarantees [init] runs AT MOST ONCE across however many threads call
 *  [getOrInit] concurrently — every caller (including the one that "loses"
 *  the race) gets the exact same outcome: the same successful value, or the
 *  SAME cached exception rethrown, never a second attempt. Built for
 *  [LiveWebRtc.factory]: WebRTC's native JNI_OnLoad is a proven RTC_CHECK/
 *  __builtin_trap risk (see that file's header doc) that can be triggered by
 *  a guest publish AND a host subscribe racing on first touch — this makes
 *  "exactly one native init attempt, ever" a structural guarantee rather
 *  than a hope, and is independently unit-testable (no Android framework
 *  involved) by injecting a fake [init]. */
internal class OneShotInit<T> {
    @Volatile private var outcome: Result<T>? = null

    /** Returns the cached value/exception if a prior call already ran
     *  [init] (successfully or not) — [init] itself is NEVER invoked again
     *  after the first attempt, successful or not. The `synchronized` block
     *  only guards the (rare, one-time-per-process) race to become that
     *  first attempt; every subsequent call is a lock-free volatile read. */
    fun getOrInit(init: () -> T): T {
        outcome?.let { return it.getOrThrow() }
        synchronized(this) {
            outcome?.let { return it.getOrThrow() }
            val result = runCatching(init)
            outcome = result
            return result.getOrThrow()
        }
    }

    fun value(): T? = outcome?.getOrNull()

    fun failure(): Throwable? = outcome?.exceptionOrNull()
}

/** All-methods-implemented SdpObserver so call sites only override the two
 *  they actually need per call (create-only or set-only). */
private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}

/** All-methods-implemented PeerConnection.Observer — both WhipPublisher and
 *  WhepSubscriber extend this and override only what they need (WHIP: none,
 *  since we never trickle and don't care about connection-state chrome
 *  beyond what's surfaced to the UI already; WHEP: onTrack, to grab the
 *  remote guest's video/audio tracks as they arrive). */
internal open class SimplePeerConnectionObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>?) {}
    override fun onAddStream(stream: org.webrtc.MediaStream?) {}
    override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
    override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {}
}
