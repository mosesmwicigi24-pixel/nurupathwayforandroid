// "Why this score?" — the drill-down behind the five growth scores. Renders
// GET /me/scores/{pillar} exactly as the server computes it (§1.1): the 0–100
// composite + pastoral band, each weighted component as a gold bar, and the raw
// counts behind it. A pillar picker swaps between the five disciplines. Port of
// the iOS ScoreDetailView. Real data only.
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ScoreBreakdown
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

private val PILLARS = listOf("word", "prayer", "habits", "curriculum", "attendance")

@Composable
fun ScoreDetailScreen(initialPillar: String, onBack: () -> Unit) {
    var pillar by remember { mutableStateOf(if (initialPillar in PILLARS) initialPillar else "word") }
    var breakdown by remember { mutableStateOf<ScoreBreakdown?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(pillar) {
        breakdown = null; failed = false
        runCatching { Net.client.api.scoreDetail(pillar) }
            .onSuccess { breakdown = it }
            .onFailure { failed = true }
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        ScreenHeader("Why this score?", kicker = "Growth", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PILLARS.forEach { p ->
                    val selected = p == pillar
                    Box(
                        Modifier.clip(RoundedCornerShape(Radii.pill))
                            .background(if (selected) Nuru.navyDeep else Nuru.surface)
                            .clickable { pillar = p }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            p.replaceFirstChar { it.uppercase() },
                            style = NuruType.cardCta,
                            color = if (selected) Nuru.onNavy else Nuru.ink600,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            val b = breakdown
            when {
                b != null -> {
                    NuruCard {
                        Kicker(pillar.replaceFirstChar { it.uppercase() })
                        Text("${b.score}", style = NuruType.display, color = Nuru.navyDeep)
                        Text(b.band, style = NuruType.caption, color = Nuru.ink600)
                    }
                    if (b.components.isNotEmpty()) {
                        NuruCard {
                            Kicker("How it's weighted")
                            b.components.entries.sortedByDescending { it.value }.forEach { (label, value) ->
                                Spacer(Modifier.height(Spacing.sm))
                                ComponentBar(label, value)
                            }
                        }
                    }
                    if (b.detail.isNotEmpty()) {
                        NuruCard {
                            Kicker("The numbers behind it")
                            b.detail.entries.forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                                    Text(prettyLabel(label), style = NuruType.body, color = Nuru.ink600, modifier = Modifier.weight(1f))
                                    Text(fmtNum(value), style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Text(
                        "Scores are formative, never a leaderboard — they decay gently when you lapse and grow as you do.",
                        style = NuruType.micro,
                        color = Nuru.ink400,
                    )
                }
                failed -> NuruCard { Text("Couldn't load this score.", style = NuruType.body, color = Nuru.ink600) }
                else -> NuruCard { Text("Loading…", style = NuruType.body, color = Nuru.ink400) }
            }
        }
    }
}

@Composable
private fun ComponentBar(label: String, value: Double) {
    val pct = value.coerceIn(0.0, 1.0).toFloat()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(prettyLabel(label), style = NuruType.caption, color = Nuru.ink, modifier = Modifier.weight(1f))
        Text("${(pct * 100).toInt()}%", style = NuruType.micro, color = Nuru.goldLo)
    }
    Spacer(Modifier.height(4.dp))
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.surface)) {
        Box(Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.gold))
    }
}

private fun prettyLabel(key: String): String =
    key.replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun fmtNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)
