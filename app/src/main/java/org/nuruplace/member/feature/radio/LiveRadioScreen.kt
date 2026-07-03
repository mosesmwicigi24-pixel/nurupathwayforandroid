// Live radio — the member player (GET /radio/now-playing + /radio/programs).
// A now-playing hero (artwork, LIVE badge, title, speaker, gold play/pause) over
// a Media3 ExoPlayer that streams the live HLS/Icecast feed or a hosted
// recording, then the schedule (live · upcoming · recorded). Port of the iOS
// RadioPlayerView. Audio only; the player is released with the composition.
package org.nuruplace.member.feature.radio

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RadioProgram
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

private data class RadioBundle(val nowPlaying: RadioProgram?, val programs: List<RadioProgram>)

@Composable
fun LiveRadioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var playing by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    fun toggle(url: String) {
        if (currentUrl == url && player.isPlaying) { player.pause(); return }
        if (currentUrl != url) {
            player.setMediaItem(MediaItem.fromUri(url)); player.prepare(); currentUrl = url
        }
        player.play()
    }

    AsyncContent(
        load = {
            val np = runCatching { Net.client.api.radioNowPlaying() }.getOrNull()
            val progs = runCatching { Net.client.api.radioPrograms() }.getOrDefault(emptyList())
            RadioBundle(np, progs)
        },
    ) { bundle: RadioBundle, _ ->
        val now = bundle.nowPlaying ?: bundle.programs.firstOrNull { it.live } ?: bundle.programs.firstOrNull()
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Radio", kicker = "Nuru Place", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.base),
            ) {
                if (now != null) item { NowPlaying(now, isThisPlaying = currentUrl == now.streamUrl && playing, onToggle = { now.streamUrl?.let(::toggle) }) }

                val upcoming = bundle.programs.filter { it.status == "scheduled" && !it.live }
                val recorded = bundle.programs.filter { it.recorded }
                if (upcoming.isNotEmpty()) {
                    item { Kicker("Coming up") }
                    items(upcoming, key = { it.id }) { p -> ProgramRow(p, onToggle = { p.streamUrl?.let(::toggle) }, playingUrl = currentUrl.takeIf { playing }) }
                }
                if (recorded.isNotEmpty()) {
                    item { Spacer(Modifier.height(Spacing.sm)); Kicker("Recorded") }
                    items(recorded, key = { it.id }) { p -> ProgramRow(p, onToggle = { p.streamUrl?.let(::toggle) }, playingUrl = currentUrl.takeIf { playing }) }
                }
            }
        }
    }
}

@Composable
private fun NowPlaying(p: RadioProgram, isThisPlaying: Boolean, onToggle: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.hero)).background(Nuru.ceremonyGradient)) {
        p.artworkUrl?.let {
            AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(Radii.hero)))
            Box(Modifier.fillMaxWidth().height(220.dp).background(Nuru.navyDeep.copy(alpha = 0.55f)))
        }
        Column(Modifier.padding(Spacing.lg)) {
            if (p.live) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Nuru.danger))
                    Spacer(Modifier.size(Spacing.xs))
                    Text("LIVE", style = NuruType.micro, color = Nuru.onNavy, fontWeight = FontWeight.Bold)
                    p.peakListeners?.let { Spacer(Modifier.size(Spacing.sm)); Text("$it listening", style = NuruType.micro, color = Nuru.onNavyDim) }
                }
            } else {
                Kicker(p.category.ifBlank { "On demand" })
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(p.title, style = NuruType.display, color = Nuru.onNavy, maxLines = 2)
            p.speaker?.let { Text(it, style = NuruType.body, color = Nuru.onNavyDim) }
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Nuru.gold)
                        .then(if (p.streamUrl != null) Modifier.clickable { onToggle() } else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Text(if (isThisPlaying) "⏸" else "▶", style = NuruType.title, color = Nuru.navy) }
                Spacer(Modifier.size(Spacing.md))
                Text(
                    if (p.streamUrl == null) "Nothing playing yet" else if (isThisPlaying) "On air" else "Tap to tune in",
                    style = NuruType.caption, color = Nuru.onNavyDim,
                )
            }
        }
    }
}

@Composable
private fun ProgramRow(p: RadioProgram, onToggle: () -> Unit, playingUrl: String?) {
    val isThis = playingUrl != null && playingUrl == p.streamUrl
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Nuru.white)
            .then(if (p.streamUrl != null) Modifier.clickable { onToggle() } else Modifier)
            .padding(Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radii.control)).background(Nuru.goldTint), contentAlignment = Alignment.Center) {
            if (p.artworkUrl != null) AsyncImage(model = p.artworkUrl, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(Radii.control)))
            else Text(if (isThis) "⏸" else "▶", style = NuruType.rowTitle, color = Nuru.goldLo)
        }
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = NuruType.rowTitle, color = Nuru.ink, maxLines = 1)
            val meta = listOfNotNull(p.speaker, p.scheduledAt?.let { org.nuruplace.member.util.fmtEventTime(it) }.takeIf { !it.isNullOrBlank() }).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = NuruType.caption, color = Nuru.ink600, maxLines = 1)
        }
        if (p.live) Text("LIVE", style = NuruType.micro, color = Nuru.danger, fontWeight = FontWeight.Bold)
    }
}
