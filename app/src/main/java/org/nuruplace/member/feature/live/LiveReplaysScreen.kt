// Nuru Live — Replays (L2). GET /live/recordings — a plain list, no duration
// (the server doesn't emit one; we don't fake it). Rows: title, date (from
// `started_at`), and a scope label (Church / your cell). Tapping a row hands
// the recording to the same full-screen player used for live streams via
// [liveRecordingRoute] (built by the caller, MainShell, from the row).
package org.nuruplace.member.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.LiveRecordingRow
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LiveReplaysScreen(onBack: () -> Unit, onOpenRecording: (LiveRecordingRow) -> Unit) {
    Column(Modifier.fillMaxSize().background(Nuru.paper)) {
        ScreenHeader("Replays", kicker = "Nuru Live", onBack = onBack)
        AsyncContent(load = { Net.client.api.getLiveRecordings().data }) { recordings, _ ->
            if (recordings.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No replays yet", style = NuruType.heading, color = Nuru.ink)
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "Past Nuru Live streams will appear here once they're recorded.",
                            style = NuruType.caption, color = Nuru.ink600, textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    recordings.sortedByDescending { it.startedAt }.forEach { row ->
                        RecordingRow(row, onClick = { onOpenRecording(row) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(row: LiveRecordingRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(18.dp)).clickable { onClick() }.padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Nuru.homeNavyGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (row.isAudio) Icons.Filled.GraphicEq else Icons.Filled.Videocam,
                contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(row.title.ifBlank { "Nuru Live" }, style = NuruType.rowTitle, color = Nuru.ink)
            Spacer(Modifier.height(2.dp))
            Text(replayDate(row.startedAt), style = NuruType.micro, color = Nuru.ink600)
        }
        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(
                if (row.scope == "cell") "Cell" else "Church",
                style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun replayDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val zone = ZoneId.of("Africa/Nairobi")
    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    return runCatching { Instant.parse(iso).atZone(zone).format(fmt) }
        .recoverCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(fmt) }
        .getOrDefault("")
}
