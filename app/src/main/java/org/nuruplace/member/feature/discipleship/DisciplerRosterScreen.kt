// Discipler roster — the leader's warm pastoral triage list of their disciples,
// from GET /disciples (Instructor+; the server enforces role + scope and pre-sorts
// needs-action first — the client just renders, §1.9). Tapping a row opens the
// per-student dossier via onOpenStudent(userId).
package org.nuruplace.member.feature.discipleship

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RosterRes
import org.nuruplace.member.data.net.RosterRow
import org.nuruplace.member.ui.components.GrowCreamHeader
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif

private val CardShape = RoundedCornerShape(24.dp)
private val ControlShape = RoundedCornerShape(14.dp)
private val Capsule = RoundedCornerShape(999.dp)
private val Green = Color(0xFF16A34A)
private val Red = Color(0xFFDC2626)
private val Blue = Color(0xFF2563EB)

@Composable
fun DisciplerRosterScreen(onBack: () -> Unit, onOpenStudent: (String) -> Unit) {
    var roster by remember { mutableStateOf<RosterRes?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        runCatching { Net.client.api.disciples() }
            .onSuccess { roster = it }
            .onFailure { error = it.message ?: "Couldn't load your disciples." }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(GrowPal.coolPaper)) {
        // Cream header — back circle + serif title + summary sub-line once loaded.
        GrowCreamHeader {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(GrowPal.white)
                        .border(1.dp, GrowPal.border, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = GrowPal.navy, modifier = Modifier.size(18.dp)) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Your disciples", style = gSerif(20, FontWeight.SemiBold), color = GrowPal.navy)
                    roster?.summary?.let { s ->
                        Text(
                            "${s.totalStudents} walking with you · ${s.awaitingAction} awaiting action",
                            style = gInter(11), color = GrowPal.ink600,
                        )
                    }
                }
            }
        }

        val r = roster
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                loading && r == null -> item { LoadingSkeleton() }
                r == null && error != null -> item {
                    ErrorCard(error ?: "Couldn't load your disciples.") { reloadKey++ }
                }
                r != null && r.summary.totalStudents == 0 -> item { EmptyCard() }
                r != null -> {
                    // Server pre-sorts needs-action first — render in order.
                    items(r.data, key = { it.userId }) { row ->
                        StudentRow(row) { onOpenStudent(row.userId) }
                    }
                }
            }
        }
    }
}

// ── one student card — avatar · name · level/cell · band + action cues ───────

@Composable
private fun StudentRow(row: RosterRow, onTap: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(GrowPal.white)
            .border(1.dp, GrowPal.border, CardShape)
            .clickable { onTap() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StudentAvatar(row.fullName, row.avatarUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.fullName, style = gInter(15, FontWeight.SemiBold), color = GrowPal.ink, maxLines = 1)
                val sub = buildString {
                    append("Level ${row.currentLevel}")
                    row.cellName?.let { append(" · $it") }
                }
                Text(sub, style = gInter(12), color = GrowPal.ink600, maxLines = 1)
            }
            row.band?.let { BandPill(it) }
        }

        // Needs-action cues — the gold usher pill and pending-reflection chip.
        val hasUsher = row.awaitingLevel != null
        val hasReflections = row.pendingReflections > 0
        if (hasUsher || hasReflections) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.awaitingLevel?.let { lvl ->
                    Box(
                        Modifier.clip(Capsule).background(GrowPal.gold)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("Usher · L$lvl", style = gInter(11, FontWeight.Bold), color = GrowPal.navy)
                    }
                }
                if (hasReflections) {
                    val n = row.pendingReflections
                    Box(
                        Modifier.clip(Capsule).background(GrowPal.goldChipBg)
                            .border(1.dp, GrowPal.gold.copy(alpha = 0.3f), Capsule)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "$n reflection${if (n == 1) "" else "s"}",
                            style = gInter(11, FontWeight.SemiBold), color = GrowPal.gold,
                        )
                    }
                }
            }
        }

        // Quiet meta line — streak + recency (red when quiet for a week+).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (row.streakDays > 0) {
                Text("🔥 ${row.streakDays}-day", style = gInter(11, FontWeight.SemiBold), color = GrowPal.gold)
            }
            val d = row.daysSinceLastActivity
            val (recency, recencyColor) = when {
                d == null -> "No activity yet" to GrowPal.ink400
                d >= 7 -> "Last active ${d}d ago" to Red
                else -> "Last active ${d}d ago" to GrowPal.ink400
            }
            Text(recency, style = gInter(11), color = recencyColor)
        }
    }
}

@Composable
private fun StudentAvatar(name: String, url: String?) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = name, modifier = Modifier.size(48.dp).clip(CircleShape))
    } else {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(GrowPal.goldChipBg),
            contentAlignment = Alignment.Center,
        ) {
            val initials = name.split(" ").filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }
            Text(initials.ifEmpty { "?" }, style = gSerif(17, FontWeight.SemiBold), color = GrowPal.gold)
        }
    }
}

/** Engagement band → tinted pill (thriving/steady/watch/at_risk). */
@Composable
private fun BandPill(band: String) {
    val (label, color) = when (band.lowercase()) {
        "thriving" -> "Thriving" to Green
        "steady" -> "Steady" to Blue
        "watch" -> "Watch" to GrowPal.gold
        "at_risk" -> "At risk" to Red
        else -> band.replaceFirstChar { it.uppercase() } to GrowPal.ink600
    }
    Box(
        Modifier.clip(Capsule).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), Capsule)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(label, style = gInter(10, FontWeight.Bold), color = color)
    }
}

// ── loading / error / empty states ────────────────────────────────────────────

@Composable
private fun LoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            RosterCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(GrowPal.surface))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(140.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(GrowPal.surface))
                        Box(Modifier.width(90.dp).height(9.dp).clip(RoundedCornerShape(4.dp)).background(GrowPal.surface))
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    RosterCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = gInter(13), color = GrowPal.ink600, modifier = Modifier.fillMaxWidth())
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(ControlShape).background(GrowPal.surface)
                    .border(1.dp, GrowPal.border, ControlShape)
                    .clickable { onRetry() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Try again", style = gInter(13, FontWeight.SemiBold), color = GrowPal.navy)
            }
        }
    }
}

@Composable
private fun EmptyCard() {
    // No students yet — warm, not an error.
    RosterCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(48.dp).clip(ControlShape).background(GrowPal.goldChipBg),
                contentAlignment = Alignment.Center,
            ) { Text("🌱", fontSize = 22.sp) }
            Text(
                "No disciples assigned yet",
                style = gInter(17, FontWeight.Bold), color = GrowPal.ink,
            )
            Text(
                "When members are placed in your care, they'll appear here.",
                style = gInter(13).copy(lineHeight = 19.sp), color = GrowPal.ink600,
            )
        }
    }
}

// ── shared card shell ─────────────────────────────────────────────────────────

@Composable
private fun RosterCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(GrowPal.white)
            .border(1.dp, GrowPal.border, CardShape)
            .padding(16.dp),
    ) { content() }
}
