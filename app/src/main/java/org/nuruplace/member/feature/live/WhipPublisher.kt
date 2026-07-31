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
        val factory = LiveWebRtc.factory(context)
        val eglBase = LiveWebRtc.eglBase

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

    suspend fun stop() {
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
    }
}
