// Nuru Live L6b — shared WebRTC plumbing for both the guest publisher
// (WhipPublisher.kt) and the host subscriber (WhepSubscriber.kt): a single
// process-wide PeerConnectionFactory/EglBase (WebRTC's own guidance — one
// factory per process, expensive to recreate), suspend wrappers around
// SdpObserver/PeerConnection's callback-style API, and the WHIP/WHEP HTTP
// exchange itself (POST SDP offer -> 201 + SDP answer + Location header;
// DELETE Location to tear down). See docs/LIVE_INTERACTIVE.md's pinned wire
// contract for the exact shape MediaMTX expects.
//
// Auth: MediaMTX's WHIP/WHEP auth (same `authMethod: http` webhook as RTMP)
// is carried via query params baked into the request URL itself
// (`?user=<id>&pass=<token>`), NOT an Authorization header — see the pinned
// facts in the L6b task brief. Both callers append these before calling
// postSdpOffer().
package org.nuruplace.member.feature.live

import android.content.Context
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

    @Volatile
    private var factoryRef: PeerConnectionFactory? = null

    /** A public STUN server for ICE server-reflexive candidates — MediaMTX
     *  itself sits on a publicly routable VPS (no NAT on its side), so once
     *  our own device has ANY reachable candidate (host on a public network,
     *  or srflx via STUN when behind carrier/NAT) the ICE handshake can
     *  complete without a TURN relay. */
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    )

    fun factory(context: Context): PeerConnectionFactory {
        factoryRef?.let { return it }
        synchronized(this) {
            factoryRef?.let { return it }
            val appContext = context.applicationContext
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions(),
            )
            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, /* enableIntelVp8Encoder */ true, /* enableH264HighProfile */ true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            val f = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            factoryRef = f
            return f
        }
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

    /** POSTs the local SDP offer to [urlWithAuth] (already carrying
     *  `?user=&pass=`) and returns the SDP answer body plus the resource URL
     *  to DELETE on teardown (the `Location` header, resolved against the
     *  request URL if relative — WHIP/WHEP servers are allowed to send
     *  either). Throws on any non-201 response. */
    suspend fun postSdpOffer(urlWithAuth: String, offerSdp: String): WhipWhepResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(urlWithAuth)
                .post(offerSdp.toRequestBody(sdpMediaType))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code != 201) {
                    throw IllegalStateException("WHIP/WHEP POST failed: HTTP ${response.code}")
                }
                val answer = response.body?.string().orEmpty()
                if (answer.isBlank()) throw IllegalStateException("WHIP/WHEP POST returned an empty SDP answer")
                val location = response.header("Location")
                    ?: throw IllegalStateException("WHIP/WHEP POST response missing Location header")
                val resourceUrl = resolveAgainst(urlWithAuth, location)
                WhipWhepResult(answer, resourceUrl)
            }
        }

    suspend fun deleteResource(resourceUrl: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(resourceUrl).delete().build()
                httpClient.newCall(request).execute().close()
            }
        }
    }

    private fun resolveAgainst(baseUrl: String, maybeRelative: String): String =
        runCatching { URI(baseUrl).resolve(maybeRelative).toString() }.getOrDefault(maybeRelative)
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
