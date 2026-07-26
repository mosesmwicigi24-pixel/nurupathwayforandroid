// Nuru Live — the full-screen viewer player (L2). Media3 ExoPlayer over the
// resolved HLS/recording url (media3-exoplayer-hls follows the server's HLS
// redirect natively — no special handling needed). Video renders into a
// PlayerView; audio-kind streams show a branded navy/gold backdrop with a
// waveform animation (the Radio screen's on-air aesthetic) while the same
// ExoPlayer plays in the background — there is no video track to render.
//
// Lifecycle: the player is a fresh instance per screen (not the shared
// RadioController service — Live viewing does not keep playing once the
// screen closes), released via DisposableEffect keyed on the url. While
// `live` is true, a LaunchedEffect posts a heartbeat every 30s AND re-checks
// GET /live/now — if the stream has dropped out of that list, the server has
// ended it (even before ExoPlayer notices), so the ended state fires either
// way. Both loops are plain suspend while(true) bodies inside LaunchedEffect,
// which Compose cancels outright the moment this composable leaves the
// composition (back/close) — not merely paused.
package org.nuruplace.member.feature.live

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.LivePulsingDot
import org.nuruplace.member.ui.components.startedAgoLabel
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun LivePlayerScreen(
    url: String,
    title: String,
    kind: String,
    live: Boolean,
    streamId: String?,
    startedAt: String?,
    initialViewerCount: Int,
    onBack: () -> Unit,
    onOpenReplays: () -> Unit,
) {
    val context = LocalContext.current
    val isAudio = kind == "audio"
    var ended by remember(url) { mutableStateOf(false) }
    var viewerCount by remember(url) { mutableIntStateOf(initialViewerCount) }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            addListener(object : Player.Listener {
                // ExoPlayer surfacing a playback error (the HLS manifest 404ing
                // once the broadcaster stops, a stalled fetch, etc.) is the
                // primary end-of-stream signal for both live and recorded
                // playback — never leave a spinner forever.
                override fun onPlayerError(error: PlaybackException) { ended = true }
                // A recording naturally reaching its end also counts as "over".
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) ended = true
                }
            })
            prepare()
            playWhenReady = true
        }
    }
    // A fresh player per url; released the moment this screen closes (back,
    // system back, or navigating to Replays) — Live never keeps playing in
    // the background the way Radio's foreground-service player does.
    DisposableEffect(url) { onDispose { player.release() } }

    // Heartbeat (POST /live/streams/{id}/heartbeat) every ~30s while this is a
    // LIVE stream (never for a replay — recordings have no viewer_count and
    // heartbeat is meaningless on an ended stream). Piggybacks the SAME 30s
    // tick to re-fetch GET /live/now: if `streamId` has dropped out of that
    // list the server has ended the broadcast — that's a second, authoritative
    // end-of-stream signal independent of whatever ExoPlayer itself observes.
    LaunchedEffect(streamId, live) {
        if (!live || streamId.isNullOrBlank()) return@LaunchedEffect
        while (true) {
            delay(30_000)
            runCatching { Net.client.api.postLiveHeartbeat(streamId) }
            val row = runCatching { Net.client.api.getLiveNow().data }
                .getOrNull()?.firstOrNull { it.streamId == streamId }
            if (row == null) ended = true else viewerCount = row.viewerCount
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            ended -> EndedState(onOpenReplays)
            isAudio -> AudioBackdrop(title)
            else -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
            )
        }

        // Chrome overlay — close (top-left), LIVE badge + viewer count
        // (top-right, live only), title + "started Xm ago" (bottom).
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.weight(1f))
                if (live && !ended) {
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LivePulsingDot(size = 7.dp)
                        Text("LIVE", style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("·", style = NuruType.micro, color = Color.White.copy(alpha = 0.5f))
                        Text("$viewerCount watching", style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (!ended) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(14.dp),
                ) {
                    Text(title.ifBlank { "Nuru Live" }, style = NuruType.rowTitle, color = Color.White)
                    Spacer(Modifier.height(2.dp))
                    Text(startedAgoLabel(startedAt), style = NuruType.micro, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** "The stream has ended" — never leave a spinner forever; a way back into
 *  Replays is always one tap away. */
@Composable
private fun EndedState(onOpenReplays: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Nuru.gold.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("The stream has ended", style = NuruType.title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("A recording will appear in Replays shortly.", style = NuruType.body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldGradient).clickable { onOpenReplays() }
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) { Text("View Replays", style = NuruType.cardCta, color = Nuru.homeNavy, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Audio-kind branded still — navy + gold, breathing waveform bars, matching
 *  the Radio screen's on-air language (no video track to render). */
@Composable
private fun AudioBackdrop(title: String) {
    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(20.dp))
            Text(title.ifBlank { "Nuru Live" }, style = NuruType.title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            AudioWaveform(Modifier.width(180.dp).height(28.dp))
        }
    }
}

// Not private — LiveBroadcastScreen.kt (L3, same package) reuses this exact
// breathing-bars visual for the broadcaster's own audio-kind HUD, per the
// Radio/Live on-air aesthetic (no live mic-level API exposed by RootEncoder
// at the version this app resolves — see that file's comment).
@Composable
fun AudioWaveform(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "liveAudioWave")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    Canvas(modifier) {
        val n = 21
        val slot = size.width / n
        val barW = (slot * 0.42f).coerceAtMost(3.dp.toPx())
        val mid = size.height / 2f
        for (i in 0 until n) {
            val x = slot * i + slot / 2f
            val envelope = sin(PI.toFloat() * i / (n - 1))
            val a = 0.35f + 0.3f * sin(phase + i * 0.9f)
            val h = (size.height * a.coerceIn(0.15f, 0.9f) * (0.5f + 0.5f * envelope)).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = Nuru.gold.copy(alpha = 0.4f + 0.5f * envelope),
                topLeft = Offset(x - barW / 2f, mid - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}
