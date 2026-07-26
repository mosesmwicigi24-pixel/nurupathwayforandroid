// Nuru Live discovery — the two "invite loudly, never hijack" surfaces that
// aren't the full-screen player itself: [LiveMiniPopup] (Home's floating
// muted-preview mini-window) and [AppLiveBar] (the slim app-wide strip on
// every other screen). Both are pure presentation — LiveDiscoveryCenter owns
// all the state; LivePlayerScreen owns the real (unmuted) playback surface.
package org.nuruplace.member.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.nuruplace.member.data.net.LiveNowRow
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.LivePulsingDot
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

/** Home's floating mini-window: a MUTED autoplaying preview of the actual
 *  HLS (media3, volume 0 — falls back to the branded icon tile if the item
 *  fails to load/play, the owner's "acceptable fallback"), title, pulsing
 *  LIVE, "Join live" → the full unmuted player, and a ✕ that collapses this
 *  to the ordinary LIVE banner card and never re-pops for this stream_id. */
@Composable
fun LiveMiniPopup(
    stream: LiveNowRow,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var previewFailed by remember(stream.streamId) { mutableStateOf(false) }

    // Muted preview player — only for video streams (audio has nothing to
    // show, so it renders the branded waveform-icon tile below instead).
    val player = remember(stream.streamId) {
        if (stream.isAudio) return@remember null
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                volume = 0f
                setMediaItem(MediaItem.fromUri(Net.client.resolveMediaUrl(stream.hlsUrl.orEmpty())))
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) { previewFailed = true }
                })
                prepare()
                playWhenReady = true
            }
        }.getOrNull()
    }
    DisposableEffect(stream.streamId) { onDispose { player?.release() } }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Nuru.homeNavyGradient)
            .border(1.dp, Nuru.gold.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.homeNavyDark),
            contentAlignment = Alignment.Center,
        ) {
            if (player != null && !previewFailed) {
                AndroidView(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                        }
                    },
                )
            } else {
                Icon(
                    if (stream.isAudio) Icons.Filled.GraphicEq else Icons.Filled.Videocam,
                    contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LivePulsingDot(size = 6.dp)
                Spacer(Modifier.width(5.dp))
                Text("LIVE", style = NuruType.kicker, color = Nuru.onNavy)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                stream.title.ifBlank { "Nuru Live" }, style = NuruType.cardCta,
                color = Nuru.onNavy, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            "Join live", style = NuruType.actionLabel, color = Nuru.homeNavy,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp)).background(Nuru.gold)
                .clickable { onJoin() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        }
    }
}

/** Slim app-wide LIVE bar — every screen but Home, while a stream is
 *  watchable and the player isn't already open. Mirrors the radio on-air
 *  pill's "you're missing something" idiom in a 36dp gold/navy strip. */
@Composable
fun AppLiveBar(stream: LiveNowRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Nuru.navy)
            .border(width = 1.dp, color = Nuru.gold.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(horizontal = Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LivePulsingDot(size = 6.dp)
        Text("LIVE", style = NuruType.kicker, color = Nuru.gold)
        Text("—", color = Color.White.copy(alpha = 0.4f), style = NuruType.micro)
        Text(
            stream.title.ifBlank { "Nuru Live" }, style = NuruType.cardCta, color = Color.White,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text("›", style = NuruType.title, color = Color.White.copy(alpha = 0.6f))
    }
}
