// Nuru Live L6b — host-side WHEP subscriber: one instance per accepted guest,
// recvonly (video + audio), rendering into a small tile on the broadcaster's
// own HUD (LiveBroadcastScreen.kt's guest rail) and feeding the guest's
// decoded audio into GuestAudioMixer.kt so it can also ride the outgoing
// RTMP stream to the congregation (see that file's header for the mechanism).
// The host ALSO simply hears guests via WebRTC's own default audio output —
// addSink() is an additive tap, not a replacement for normal playout.
package org.nuruplace.member.feature.live

import android.content.Context
import android.net.Uri
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

class WhepSubscriber(private val context: Context, private val guestId: String) {
    private var peerConnection: PeerConnection? = null
    private var resourceUrl: String? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var attachedRenderer: SurfaceViewRenderer? = null
    private var audioSink: AudioTrackSink? = null

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
        val factory = LiveWebRtc.factory(context)

        val observer = object : SimplePeerConnectionObserver() {
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track() ?: return
                when (track) {
                    is VideoTrack -> {
                        remoteVideoTrack = track
                        attachedRenderer?.let { track.addSink(it) }
                    }
                    is org.webrtc.AudioTrack -> {
                        val sink = GuestAudioMixer.attach(guestId)
                        audioSink = sink
                        track.addSink(sink)
                    }
                }
            }
        }
        val pc = factory.createPeerConnection(LiveWebRtc.rtcConfig(), observer)
            ?: throw IllegalStateException("createPeerConnection returned null")
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
    }

    suspend fun stop() {
        resourceUrl?.let { LiveWebRtc.deleteResource(it) }
        resourceUrl = null
        GuestAudioMixer.detach(guestId)
        remoteVideoTrack?.let { track -> attachedRenderer?.let { track.removeSink(it) } }
        attachedRenderer = null
        remoteVideoTrack = null
        audioSink = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }
}
