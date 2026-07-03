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
