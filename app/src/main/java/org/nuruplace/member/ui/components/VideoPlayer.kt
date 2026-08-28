// Inline video — it plays INSIDE the card that hosts it and never bounces out
// to a browser. Faithful port of the iOS InlineVideoPlayer.swift:
//
//   youtube / vimeo  → provider embed page in a WebView (playsinline, autoplay)
//   everything else  → Media3 ExoPlayer (direct .mp4/.mov, cloudinary, HLS)
//
// Owner bug, 2026-08-26: the Home "Nuru Pathway / FEATURED" card opened an
// external window and offered to DOWNLOAD the file instead of playing it. Root
// cause (traced end to end, see [needsWebEmbed] in ParityDtos.kt): the backend
// registers an UPLOADED video as `video_source = 'direct'` with `external_url`
// set to its own public /media/<uuid>.mp4 URL (packages/backend .../media/
// video.ts registerUploaded + welcomeVideo, whose EXTERNAL_SOURCES set includes
// "direct"). The Android DTO read `externalUrl != null` as "needs a browser",
// so every self-hosted upload took the openExternal() → ACTION_VIEW path and
// the system handler downloaded the .mp4. `external_url` means "shareable
// link", never "unplayable in-app" — the provider is the only thing that
// decides, exactly as iOS does it.
package org.nuruplace.member.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.nuruplace.member.ui.theme.Nuru

/**
 * Provider-aware inline player — the single entry point every video card should
 * use. [source] is the server's `video_source` when known (cloudinary | youtube
 * | vimeo | direct | private); when it is null the provider is sniffed from the
 * URL host, so a call site that only has a URL still gets it right.
 *
 * The player fills its parent's width at 16:9. Put rounding on [modifier].
 */
@Composable
fun InlineVideoPlayer(
    url: String,
    source: String? = null,
    externalVideoId: String? = null,
    modifier: Modifier = Modifier,
) {
    val embed = remember(url, source, externalVideoId) { videoEmbedUrl(url, source, externalVideoId) }
    if (embed != null) InlineWebVideo(embed, modifier) else InlineVideo(url, modifier)
}

/** Play a direct/cloudinary/HLS URL inline, auto-playing, released on dispose.
 *  A gold spinner sits INSIDE the black box while it buffers, so the card never
 *  shows a silent black rectangle. */
@Composable
fun InlineVideo(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var buffering by remember(url) { mutableStateOf(true) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }
            // A dead URL must not spin forever — drop the cue and let PlayerView
            // surface its own error surface.
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
        )
        if (buffering) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(32.dp),
                color = Nuru.gold,
                strokeWidth = 3.dp,
            )
        }
    }
}

/** YouTube / Vimeo embed inside the card — a WebView pinned to the same 16:9
 *  box. A WebViewClient is mandatory, not decorative: a WebView with no client
 *  hands every navigation to an ACTION_VIEW Intent, which is the very
 *  "it opened a browser" behaviour this file exists to prevent. */
@Composable
private fun InlineWebVideo(embedUrl: String, modifier: Modifier = Modifier) {
    var loading by remember(embedUrl) { mutableStateOf(true) }
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Keep every navigation inside this view.
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?,
                        ) {
                            loading = false
                        }
                    }
                    // The provider players are JS; inline playback + autoplay
                    // after the member's own tap (iOS: allowsInlineMediaPlayback
                    // + mediaTypesRequiringUserActionForPlayback = []).
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    loadUrl(embedUrl)
                }
            },
            onRelease = { web ->
                web.stopLoading()
                web.loadUrl("about:blank")
                web.destroy()
            },
        )
        if (loading) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(32.dp),
                color = Nuru.gold,
                strokeWidth = 3.dp,
            )
        }
    }
}

/** Open an external link in the browser or a handling app. Reserved for real
 *  links (web pages, maps); NEVER for a video — as of 2026-08-28 every video
 *  surface plays in place via [InlineVideoPlayer], so this has no video call
 *  site left and must not gain one. See the file header for the
 *  download-prompt bug that rule exists to prevent. */
fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// Hosts whose URLs are player PAGES, not media files: there is no direct video
// stream at the URL for ExoPlayer to point at, so they need the provider's own
// iframe player. Everything else — including every one of this app's own
// self-hosted uploads (MEDIA_PUBLIC_BASE_URL's `/media/<uuid>.mp4|.mov` —
// welcome/featured video, event video, plan-segment video) — is a direct video
// file URL that plays fine in-app via [InlineVideo].
//
// Real-device bug (2026-07-31): a guest device's browser popped a raw
// "Download file again?" prompt for a `/media/....mov` — the signature of a
// direct video URL being handed to openExternal() instead of played in-app.
// Fixed then for PlanReaderKit/PlanSegmentScreen; the Home featured card was
// left on the server's `external_url != null` flag and kept the bug until
// 2026-08-26 (see the file header).
//
// As of 2026-08-28 NO video surface hands off at all: the last two — the plan
// cards PlanReaderKit.RMediaCard and PlanSegmentScreen.VideoCard — dropped
// their YouTube/Vimeo branch and call [InlineVideoPlayer], which embeds those
// player pages in the card. [videoEmbedUrl] is now the only live provider gate;
// [isExternalVideoHost] below keeps its tests as the pinned definition of "a
// player page, not a media file", but has no production call site, and a new
// `if (isExternalVideoHost(...)) openExternal(...)` would be a regression, not
// a feature.
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

/**
 * The provider embed page for a URL that needs one, or null when the URL is a
 * direct media file ExoPlayer can play. Pure + JDK-only, for the same
 * testability reason as [isExternalVideoHost].
 *
 * Query strings are exactly the iOS ones (InlineVideoPlayer.embedURL), so both
 * apps get the same chrome-less, inline, auto-playing embed.
 */
fun videoEmbedUrl(url: String, source: String? = null, externalVideoId: String? = null): String? {
    val provider = source?.lowercase()?.takeIf { it == "youtube" || it == "vimeo" }
        ?: providerFromHost(url)
        ?: return null
    val id = externalVideoId?.takeIf { it.isNotBlank() } ?: videoIdFromUrl(url, provider) ?: return null
    return when (provider) {
        "youtube" -> "https://www.youtube.com/embed/$id?playsinline=1&autoplay=1&modestbranding=1&rel=0"
        else -> "https://player.vimeo.com/video/$id?autoplay=1&playsinline=1"
    }
}

private fun providerFromHost(url: String): String? {
    val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return null
    fun match(d: String) = host == d || host.endsWith(".$d")
    return when {
        match("youtube.com") || match("youtu.be") -> "youtube"
        match("vimeo.com") -> "vimeo"
        else -> null
    }
}

/** `?v=` for a watch URL, else the last non-empty path segment (youtu.be/<id>,
 *  youtube.com/embed/<id>, vimeo.com/<id>). */
private fun videoIdFromUrl(url: String, provider: String): String? {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
    if (provider == "youtube") {
        uri.query?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            ?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return uri.path?.split('/')?.lastOrNull { it.isNotBlank() }
}
