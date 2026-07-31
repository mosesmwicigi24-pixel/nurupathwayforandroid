// Nuru Live — the broadcaster's RootEncoder engine + session state, shared
// between LiveBroadcastService (which owns the actual encoders so a
// broadcast survives navigation/backgrounding) and LiveBroadcastScreen
// (which only ever reaches the engine through BroadcastController). Split out
// of LiveBroadcastScreen.kt during the Broadcast Studio rework so the same
// Broadcaster/orientation-math/publish-url code isn't duplicated between the
// Compose screen and the Service.
package org.nuruplace.member.feature.live

import android.content.Context
import android.media.projection.MediaProjection
import android.view.SurfaceView
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.generic.GenericOnlyAudio
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.streamclient.StreamBaseClient

internal const val VIDEO_WIDTH = 1920
internal const val VIDEO_HEIGHT = 1080
internal const val VIDEO_BITRATE = 6_000_000 // ~6 Mbps, per docs/LIVE_STREAMING.md
internal const val AUDIO_SAMPLE_RATE = 44_100
internal const val AUDIO_BITRATE = 128_000
internal val RETRY_BACKOFFS_MS = longArrayOf(2_000, 5_000, 10_000)
internal const val MAX_RETRIES = 3

enum class BroadcastPhase { CONNECTING, LIVE, RECONNECTING, FAILED, ENDING, SUMMARY }

/** Engine-level video source. "Document" (the in-app PDF pager) rides on top
 *  of SCREEN at this layer — it's the same ScreenSource/MediaProjection
 *  capture, just with the Compose side showing a full-screen pager instead
 *  of the "Sharing your screen" chip UI, so the engine only needs to
 *  distinguish CAMERA vs SCREEN. See LiveBroadcastSourceUi.kt. */
enum class BroadcastSource { CAMERA, SCREEN }

data class BroadcastSession(
    val streamId: String,
    val rtmpUrl: String,
    val streamKey: String,
    val title: String,
    val kind: String, // "video" | "audio"
) {
    val isVideo: Boolean get() = kind == "video"
}

data class BroadcastState(
    val session: BroadcastSession? = null,
    val phase: BroadcastPhase = BroadcastPhase.CONNECTING,
    val muted: Boolean = false,
    val source: BroadcastSource = BroadcastSource.CAMERA,
    val switchingSource: Boolean = false,
    val retryAttempt: Int = 0,
    val failReason: String? = null,
    val startedAtMillis: Long? = null,
    val endedViaNotification: Boolean = false,
)

/** RTMP publish URL construction — READ THIS BEFORE TOUCHING.
 *
 * MediaMTX's simple query-based publish auth expects the FINAL, fully
 * reconstructed RTMP request to look like ".../church?user=<id>&pass=<key>"
 * (path, then query). ffmpeg achieves this by treating the whole
 * "church?user=..&pass=.." as ONE opaque connect string with an EMPTY
 * stream name/playpath — this matches MediaMTX's own documented "Server=
 * rtmp://host/mypath, Stream Key=(blank)" OBS convention.
 *
 * RootEncoder does NOT behave the same way if you hand it a URL with a
 * real, un-escaped '?'. Verified by reading the actual resolved library's
 * source (RootEncoder 2.5.9's common/UrlParser.kt) together with
 * MediaMTX's RTMP server (gortmplib's server_conn.go buildURL()):
 * RootEncoder's UrlParser splits the URL into an RTMP "app" (sent in the
 * connect command) and a separate "streamName"/playpath (sent in the
 * publish command). For a FLAT single-segment path like "church" plus a
 * query string, UrlParser puts "church" in appName (clean) and
 * "user=..&pass=.." in streamName, WITHOUT the leading '?' (its
 * getStreamName() strips it via removePrefix("?")). MediaMTX's gortmplib
 * then reconstructs the wire path as `"/" + app + "/" + streamName`
 * whenever streamName is non-empty — i.e. it ALWAYS re-joins app and
 * streamName with a literal "/", never a "?". The result for church is
 * "/church/user=..&pass=.." — no '?' survives ANYWHERE in that string, so
 * MediaMTX's Go URL parser sees an empty query and the credentials are
 * silently dropped. The NESTED "cell/{cellId}" scope is unaffected — its
 * extra path segment happens to keep the '?' embedded mid-token in
 * streamName, so gortmplib's re-join reconstructs it correctly by
 * coincidence. Church (flat) is the one that breaks.
 *
 * THE FIX: percent-encode the '?' as "%3F" before handing the string to
 * RootEncoder. `java.net.URI` decodes %3F back to a literal '?' when you
 * read `.getPath()`, but — critically — does NOT treat it as a genuine
 * query delimiter, so `.getQuery()` comes back null. Fed through UrlParser
 * with query == null, the WHOLE "church?user=X&pass=Y" string becomes one
 * opaque appName with an EMPTY streamName — exactly the ffmpeg-equivalent
 * shape — so gortmplib never inserts a spurious "/" and the '?' survives
 * exactly where MediaMTX expects it. Both `streamId` (a UUID) and
 * `streamKey` (32 hex chars) are URL-safe as-is, so only the '?' itself
 * needs escaping.
 */
internal fun buildPublishUrl(rtmpUrl: String, streamId: String, streamKey: String): String =
    "$rtmpUrl%3Fuser=$streamId&pass=$streamKey"

/** StreamBase.prepareVideo's own internal formula (verified against the
 *  pinned 2.5.9 source, library/src/main/java/com/pedro/library/base/
 *  StreamBase.kt: `glInterface.setCameraOrientation(if (rotation == 0) 270
 *  else rotation - 90)`) for converting the `rotation` degrees passed to
 *  prepareVideo into the `cameraOrientation` value setOrientation()/
 *  setCameraOrientation() expect. Used ONLY to re-assert the SAME locked
 *  orientation after a camera flip or a screen-share round-trip, never to
 *  change it. */
internal fun cameraOrientationFor(rotation: Int): Int = if (rotation == 0) 270 else rotation - 90

/** Uniform wrapper over RootEncoder's two unrelated base classes
 *  (StreamBase for GenericStream/video, OnlyAudioBase for GenericOnlyAudio/
 *  audio-only) — they share no common supertype and differ in small ways
 *  (mute mechanism, prepareAudio's parameter order) that this hides so the
 *  rest of the app doesn't need to branch on `kind` everywhere. */
interface Broadcaster {
    val isStreaming: Boolean
    fun startStream(url: String)
    fun stopStream()
    fun release()
    fun setMuted(muted: Boolean)
    fun client(): StreamBaseClient
    fun startPreview(view: SurfaceView) {}
    fun stopPreview() {}
    fun switchCamera() {}

    /** Camera → screen source, live, without stopping the publish (verified
     *  against StreamBase.changeVideoSource — see LiveBroadcastService.kt's
     *  header comment). No-op for audio-only broadcasts. */
    fun useScreenSource(mediaProjection: MediaProjection) {}

    /** Screen → camera source, live. Restores whichever facing (front/back)
     *  was active before the broadcaster switched away, and re-enables the
     *  tilt-compensating auto-orientation that screen-share must disable. */
    fun useCameraSource() {}
}

private class VideoBroadcaster(
    val stream: GenericStream,
    private val context: Context,
    private val rotation: Int,
) : Broadcaster {
    private var frontCamera = false

    override val isStreaming get() = stream.isStreaming
    override fun startStream(url: String) = stream.startStream(url)
    override fun stopStream() { stream.stopStream() }
    override fun release() = stream.release()
    // StreamBase (video) has no disableAudio()/enableAudio() — mute by
    // swapping the audio source itself (verified against the resolved
    // 2.5.9 tag: NoAudioSource exists there; the newer SilenceAudioSource
    // used by master does not, so this is deliberately NOT used).
    override fun setMuted(muted: Boolean) {
        stream.changeAudioSource(if (muted) NoAudioSource() else MicrophoneSource())
    }
    override fun client() = stream.getStreamClient()
    override fun startPreview(view: SurfaceView) { if (!stream.isOnPreview) stream.startPreview(view) }
    override fun stopPreview() { if (stream.isOnPreview) stream.stopPreview() }
    override fun switchCamera() {
        (stream.videoSource as? Camera2Source)?.switchCamera()
        frontCamera = !frontCamera
        // Defensive re-assert of the locked orientation after a camera flip —
        // mirrors the iOS port's BroadcastController.flipCamera() comment
        // ("re-attach resets it — keep the locked orientation"). Verified
        // against the pinned 2.5.9 Camera2Source.switchCamera() source that
        // this is a no-op TODAY, kept anyway as the cheap guard that would
        // matter if a future RootEncoder version changes that behavior.
        stream.setOrientation(cameraOrientationFor(rotation))
    }

    // ── Source switcher (Broadcast Studio) ──────────────────────────────
    //
    // Verified against RootEncoder 2.5.9's StreamBase.changeVideoSource()
    // (library/src/main/java/com/pedro/library/base/StreamBase.kt): it stops
    // + releases the OLD VideoSource, starts the NEW one on the SAME
    // glInterface.surfaceTexture, and — critically — does none of this
    // through stopStream()/startStream(), so isStreaming (and the RTMP
    // publish itself) never drops. This is the exact mechanism RootEncoder's
    // own app/src/main/java/com/pedro/streamer/screen/ScreenService.kt
    // sample uses for its camera↔screen toggle, including the orientation
    // handling below (that sample's comment, verbatim on the source):
    // "ScreenSource need use always setCameraOrientation(0) because the
    // MediaProjection handle orientation. You also need remove
    // autoHandleOrientation if you are using it."
    override fun useScreenSource(mediaProjection: MediaProjection) {
        stream.getGlInterface().autoHandleOrientation = false
        stream.getGlInterface().setCameraOrientation(0)
        stream.changeVideoSource(ScreenSource(context, mediaProjection))
    }

    override fun useCameraSource() {
        val camera = Camera2Source(context)
        // Restore whichever facing was active before the broadcaster
        // switched away — Camera2Source.switchCamera() (verified 2.5.9
        // source) just flips its internal `facing` field when the source
        // isn't running yet, so calling it here (before changeVideoSource
        // starts the new source) is safe and takes effect on first frame.
        if (frontCamera) camera.switchCamera()
        stream.changeVideoSource(camera)
        stream.getGlInterface().autoHandleOrientation = true
        stream.setOrientation(cameraOrientationFor(rotation))
    }
}

private class AudioBroadcaster(val stream: GenericOnlyAudio) : Broadcaster {
    override val isStreaming get() = stream.isStreaming
    override fun startStream(url: String) = stream.startStream(url)
    override fun stopStream() { stream.stopStream() }
    // OnlyAudioBase exposes no release() in this version — stopStream() is
    // the documented full teardown for the audio-only path.
    override fun release() {}
    override fun setMuted(muted: Boolean) { if (muted) stream.disableAudio() else stream.enableAudio() }
    override fun client() = stream.getStreamClient()
}

/** Builds + prepares the RootEncoder engine for one broadcast session.
 *  Video: 1920x1080@30, hardware H.264 ~6 Mbps, back camera + mic, full-bleed
 *  center-crop preview (AspectRatioMode.Fill — see the Broadcast Studio full-
 *  bleed comment below). Audio: 44.1kHz stereo AAC 128kbps mic only, no
 *  camera at all (GenericOnlyAudio never touches Camera2/CameraX).
 *
 *  Codec: explicitly H.264, not HEVC — see docs/LIVE_STREAMING.md and the
 *  original LiveBroadcastScreen.kt header for why (RTMP-transported HEVC
 *  needs "Enhanced RTMP", unverified against the deployed MediaMTX; H.264 is
 *  the safe default every RTMP server generation supports).
 *
 *  Returns null broadcaster + ok=false if prepare failed (bad device codec
 *  support etc.) — caller (the Service) surfaces FAILED phase for that. */
fun buildBroadcaster(context: Context, connectChecker: ConnectChecker, kind: String): Pair<Broadcaster, Boolean> {
    return if (kind == "video") {
        val s = GenericStream(context, connectChecker)
        // Live camera-TILT compensation only (verified against the pinned
        // 2.5.9 GlStreamInterface source): keeps footage upright if the
        // phone tilts, while encoderWidth/Height (set once by prepareVideo,
        // never touched again) keeps the encoded RESOLUTION locked.
        s.getGlInterface().autoHandleOrientation = true
        // Full-bleed, center-crop preview — no black bars in either
        // orientation (Broadcast Studio requirement #1). Verified against
        // the pinned 2.5.9 sources: GlStreamInterface.setAspectRatioMode
        // feeds SizeCalculator.calculateViewPort(mode, ...), whose Adjust
        // branch (the RootEncoder DEFAULT, and what this screen used before
        // this rework — the actual cause of the reported black bars) fits
        // the stream INSIDE the view (letterbox/pillarbox); the Fill branch
        // does the opposite calculation (scales so the stream covers the
        // view, cropping the overflow) — exactly TikTok-style center-crop.
        s.getGlInterface().setAspectRatioMode(AspectRatioMode.Fill)
        s.setVideoCodec(VideoCodec.H264)
        // ── Orientation-follow fix (port of iOS 80f4fdd) ──
        // Reads how the phone is held ONCE, at go-live, and bakes it into
        // prepareVideo's `rotation` param — StreamBase.prepareVideo derives
        // the actual encoder width/height from that single value internally,
        // so VIDEO_WIDTH/VIDEO_HEIGHT stay the fixed "landscape-native" base
        // for both cases. CameraHelper.getCameraOrientation(context) is
        // RootEncoder's own canonical helper for this (verified: same one
        // its "rotation" sample app and ScreenOrientation.lockScreen() use).
        val rotation = CameraHelper.getCameraOrientation(context)
        val ok = runCatching {
            s.prepareVideo(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_BITRATE, fps = 30, rotation = rotation) &&
                s.prepareAudio(AUDIO_SAMPLE_RATE, true, AUDIO_BITRATE)
        }.getOrDefault(false)
        VideoBroadcaster(s, context, rotation) to ok
    } else {
        val s = GenericOnlyAudio(connectChecker)
        val ok = runCatching { s.prepareAudio(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, true) }.getOrDefault(false)
        AudioBroadcaster(s) to ok
    }
}
