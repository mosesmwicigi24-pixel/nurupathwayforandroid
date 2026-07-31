// Inline video — a Media3 ExoPlayer pinned to a 16:9 box, released with the
// composition. Direct/cloudinary URLs play here; external sources (YouTube,
// Vimeo) hand off to the browser via openExternal(). Port of the iOS
// InlineVideoPlayer + the external-link fallback.
package org.nuruplace.member.ui.components

import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Play a direct/cloudinary URL inline, auto-playing, released on dispose. */
@Composable
fun InlineVideo(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(url) {
        onDispose { player.release() }
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
    )
}

/** Open an external video/link in the browser or a handling app. */
fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// Hosts that genuinely need their own app/webview chrome to play (no direct
// media file at the URL for ExoPlayer to point at). Everything else —
// including every one of this app's own self-hosted uploads
// (MEDIA_PUBLIC_BASE_URL's `/media/<uuid>.mp4|.mov` — welcome/featured
// video, event video, plan-segment video) — is a direct video file URL that
// plays fine in-app via [InlineVideo]. Real-device bug (2026-07-31): a guest
// device's browser popped a raw "Download file again?" prompt for a
// `/media/....mov` — the signature of a direct video URL being handed to
// openExternal() instead of played in-app. Traced every ACTION_VIEW/
// openExternal call site in the app (grep for both): the live/guest feature
// itself never calls openExternal at all — the two ACTUAL unconditional
// (ungated) call sites were PlanPartReaderScreen.kt/PlanSegmentScreen.kt's
// plan-video cards (RMediaCard/VideoCard in PlanReaderKit.kt/
// PlanSegmentScreen.kt), which called openExternal() for ANY videoUrl with
// no host check at all — unlike HomeScreen's FeaturedVideo, which already
// gates on the server's own `isExternal` flag. Fixed at the source (those
// two card composables now check this helper and play self-hosted URLs
// in-app), this constant stays exported so any FUTURE video-tap call site
// gets it right by construction instead of reinventing (or omitting) the
// check.
private val EXTERNAL_VIDEO_HOSTS = setOf("youtube.com", "youtu.be", "vimeo.com")

// java.net.URI (plain JDK), not android.net.Uri — deliberately, so this stays
// a genuinely unit-testable pure function under this module's plain-JUnit
// posture (android.net.Uri is a stub in JVM unit tests; with this project's
// `unitTests.isReturnDefaultValues = true` it silently returns null instead
// of throwing, which would make every call here return false regardless of
// input — a test using it could pass while checking nothing at all).
fun isExternalVideoHost(url: String): Boolean =
    runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
        ?.let { host -> EXTERNAL_VIDEO_HOSTS.any { host == it || host.endsWith(".$it") } }
        ?: false
