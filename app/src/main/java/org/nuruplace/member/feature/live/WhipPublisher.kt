// Nuru Live L6b — guest-side WHIP publisher: front camera (960x540@24,
// ~800kbps) + mic, published to MediaMTX over WebRTC so the broadcaster's
// device can WHEP-subscribe and composite it with sub-second latency (RTMP/
// HLS is one-way and seconds late — see docs/LIVE_INTERACTIVE.md's L6
// architecture section for why this exists at all). One instance per guest
// session; the caller (LivePlayerScreen) owns its lifecycle end to end —
// start() when accepted + permitted, stop() on Leave Stage / screen exit /
// backgrounding.
package org.nuruplace.member.feature.live

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nuruplace.member.feature.live.LiveWebRtc.awaitIceGatheringComplete
import org.nuruplace.member.feature.live.LiveWebRtc.createOfferSuspend
import org.nuruplace.member.feature.live.LiveWebRtc.setLocalDescriptionSuspend
import org.nuruplace.member.feature.live.LiveWebRtc.setRemoteDescriptionSuspend
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** Fixed publish profile per the L6b brief — guests are a thumbnail-rail
 *  participant, not the main event, so this stays modest regardless of
 *  device capability (predictable bandwidth/battery cost, and matches what
 *  the host composites down to anyway). */
private const val GUEST_VIDEO_WIDTH = 960
private const val GUEST_VIDEO_HEIGHT = 540
private const val GUEST_VIDEO_FPS = 24
private const val GUEST_VIDEO_BITRATE_BPS = 800_000

class WhipPublisher(private val context: Context) {
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: org.webrtc.AudioTrack? = null
    private var resourceUrl: String? = null

    // Re-entrancy guard — LivePlayerScreen's `LaunchedEffect(myGuestState)`
    // re-runs this ENTIRE start/stop dance from scratch on every pulse-driven
    // status flip, reusing the SAME WhipPublisher instance (`remember(streamId)
    // { WhipPublisher(context) }`). A flickering `myGuestState` (accepted ->
    // briefly something else -> accepted again, e.g. a transient pulse gap)
    // can therefore call start() while a previous stop() — or stop() while a
    // previous start() — is still mid-flight on the SAME underlying
    // PeerConnection/AudioTrack/VideoCapturer, which is a genuine
    // use-after-free risk at the native WebRTC layer (dispose() racing
    // createOffer()/addTransceiver() etc — a Kotlin try/catch cannot save a
    // native crash). The mutex fully serializes start()/stop() against each
    // other; [stopRequested] is a fast, lock-free flag so a start() that
    // hasn't even begun touching WebRTC objects yet can bail out immediately
    // once a stop() has been requested, without waiting on the lock.
    private val lifecycleMutex = Mutex()

    @Volatile private var stopRequested = false

    val isPublishing: Boolean get() = peerConnection != null

    /** Renders the guest's own outgoing camera feed for the self-preview PiP
     *  — attach BEFORE calling [start] isn't required (the sink is added
     *  once the local video track exists, mid-start), just create + init()
     *  the renderer with [LiveWebRtc.eglBase] first. */
    fun attachLocalPreview(renderer: SurfaceViewRenderer) {
        localVideoTrack?.addSink(renderer)
    }

    fun detachLocalPreview(renderer: SurfaceViewRenderer) {
        localVideoTrack?.removeSink(renderer)
    }

    /** Publishes camera+mic to [whipUrl] (bare MediaMTX endpoint, no query
     *  params). Throws on any failure — the caller decides how to surface
     *  that (an honest error chip + retry, per the L6b brief; never a silent
     *  "joining soon" hang). */
    suspend fun start(whipUrl: String, myUserId: String, token: String, previewRenderer: SurfaceViewRenderer?) {
        if (stopRequested) return // a stop() already landed before we even got scheduled — never touch WebRTC.
        lifecycleMutex.withLock {
            if (stopRequested || isPublishing) return@withLock
            startLocked(whipUrl, myUserId, token, previewRenderer)
        }
    }

    private suspend fun startLocked(whipUrl: String, myUserId: String, token: String, previewRenderer: SurfaceViewRenderer?) {
        val factory = LiveWebRtc.factory(context)
        val eglBase = LiveWebRtc.eglBase
        // See LiveWebRtc.kt's audioDeviceModuleRef doc — recording is OFF by
        // default (host-safe); this device is actually about to send its own
        // mic over WebRTC, so switch it on for the duration of the publish.
        LiveWebRtc.setAudioRecordEnabled(true)

        val enumerator = Camera2Enumerator(context)
        val frontId = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: throw IllegalStateException("No camera available on this device")
        val capturer = enumerator.createCapturer(frontId, null) as? CameraVideoCapturer
            ?: throw IllegalStateException("Front camera capturer could not be created")
        videoCapturer = capturer

        val sth = SurfaceTextureHelper.create("WhipCapture", eglBase.eglBaseContext)
        surfaceTextureHelper = sth
        val videoSource = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(sth, context, videoSource.capturerObserver)
        capturer.startCapture(GUEST_VIDEO_WIDTH, GUEST_VIDEO_HEIGHT, GUEST_VIDEO_FPS)

        val videoTrack = factory.createVideoTrack("guest-video-$myUserId", videoSource)
        videoTrack.setEnabled(true)
        localVideoTrack = videoTrack
        previewRenderer?.let { videoTrack.addSink(it) }

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        val audioSource = factory.createAudioSource(audioConstraints)
        val audioTrack = factory.createAudioTrack("guest-audio-$myUserId", audioSource)
        localAudioTrack = audioTrack

        val pc = factory.createPeerConnection(LiveWebRtc.rtcConfig(), SimplePeerConnectionObserver())
            ?: throw IllegalStateException("createPeerConnection returned null")
        peerConnection = pc

        pc.addTransceiver(videoTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))
        pc.addTransceiver(audioTrack, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY))

        val offer = pc.createOfferSuspend()
        pc.setLocalDescriptionSuspend(offer)
        pc.awaitIceGatheringComplete()

        val finalSdp = pc.localDescription?.description ?: offer.description
        val urlWithAuth = "$whipUrl?user=${Uri.encode(myUserId)}&pass=${Uri.encode(token)}"
        val result = LiveWebRtc.postSdpOffer(urlWithAuth, finalSdp)
        resourceUrl = result.resourceUrl
        pc.setRemoteDescriptionSuspend(SessionDescription(SessionDescription.Type.ANSWER, result.answerSdp))
    }

    fun setMicMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    /** [stopRequested] flips BEFORE the lock so a start() that's still
     *  queued (hasn't acquired the mutex yet) bails immediately rather than
     *  waiting its turn just to build WebRTC objects we're about to tear
     *  down anyway. Reset back to false once THIS stop fully completes — a
     *  guest who's kicked then re-invited within the same stream session
     *  must be able to start() again afterwards; this isn't a permanent
     *  kill switch, only a "don't race the stop currently in flight" one. */
    suspend fun stop() {
        stopRequested = true
        lifecycleMutex.withLock {
            stopLocked()
            stopRequested = false
        }
    }

    private suspend fun stopLocked() {
        resourceUrl?.let { LiveWebRtc.deleteResource(it) }
        resourceUrl = null
        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        localVideoTrack = null
        localAudioTrack = null
        // Mirror LiveWebRtc.kt's audioDeviceModuleRef doc — release the mic
        // gate the instant this device stops actually sending audio.
        LiveWebRtc.setAudioRecordEnabled(false)
    }
}
