// Nuru Live — the viewer-facing "LIVE now" banner (L2). Shared between Home
// (church-scope) and CellInfoScreen (cell-scope) so both surfaces render the
// exact same card: pulsing red LIVE dot, title, "started Xm ago · N
// watching", a camera glyph for video or a waveform glyph for audio, and a
// small "Replays" link. Port of the radio screen's on-air visual language
// (RadioController/LiveRadioScreen) applied to Nuru Live streams.
package org.nuruplace.member.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.LiveNowRow
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.time.Instant
import java.time.OffsetDateTime

/** A pulsing dot — the universal "something is happening right now" signal
 *  (radio's on-air dot, ported here for Nuru Live). */
@Composable
fun LivePulsingDot(color: Color = Nuru.liveRed, size: androidx.compose.ui.unit.Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "livePulse")
    val alpha by t.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "livePulseAlpha",
    )
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

/** Minutes elapsed since an ISO-8601 `started_at` — null if unparsable. */
fun minutesSince(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val then = runCatching { Instant.parse(iso) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: return null
    return java.time.Duration.between(then, java.time.Instant.now()).toMinutes().coerceAtLeast(0)
}

/** "started Xm ago" / "started just now" / "started Xh Ym ago". */
fun startedAgoLabel(iso: String?): String {
    val mins = minutesSince(iso) ?: return "live now"
    return when {
        mins < 1 -> "started just now"
        mins < 60 -> "started ${mins}m ago"
        else -> "started ${mins / 60}h ${mins % 60}m ago"
    }
}

/** The prominent LIVE banner card — tap opens the full-screen player; the
 *  trailing "Replays" link opens the recordings list. Used identically by
 *  Home (church scope) and CellInfoScreen (cell scope). */
@Composable
fun LiveStreamBanner(row: LiveNowRow, onOpen: () -> Unit, onReplays: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Nuru.homeNavyGradient)
            .border(1.dp, Nuru.liveRed.copy(alpha = 0.45f), shape).padding(Spacing.base),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LivePulsingDot()
            Spacer(Modifier.width(6.dp))
            Text(
                if (row.scope == "cell") "LIVE · YOUR CELL" else "LIVE NOW",
                style = NuruType.kicker, color = Nuru.liveRed, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Replays ›", style = NuruType.micro, color = Nuru.goldSoft, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onReplays() },
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            Modifier.fillMaxWidth().clickable { onOpen() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Nuru.homeNavyDark),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (row.isAudio) Icons.Filled.GraphicEq else Icons.Filled.Videocam,
                    contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(row.title.ifBlank { "Nuru Live" }, style = NuruType.featureTitle, color = Nuru.onNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${startedAgoLabel(row.startedAt)} · ${row.viewerCount} watching",
                    style = NuruType.micro, color = Nuru.onNavyDim,
                )
            }
            Text("›", style = NuruType.title, color = Nuru.gold)
        }
    }
}
