// Pathway hub — the member's six-level journey (GET /me/pathway). Ported to the
// Figma LevelsOverview: a calm cream header with an overall progress ring + stat
// cards, a gold-ringed "continue your journey" card for the active level, then
// the level cards (icon chip · status pill · progress or locked label). Locked
// levels (§1.9) are dimmed + non-tappable; the server is authoritative.
package org.nuruplace.member.feature.pathway

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun LevelsScreen(me: MeResponse?, onOpenLevel: (Int) -> Unit) {
    AsyncContent(load = { Net.client.api.pathway() }) { summary: PathwaySummary, _ ->
        val levels = summary.levels
        val totalModules = levels.sumOf { it.totalModules }
        val doneModules = levels.sumOf { it.completedModules }
        val pct = if (totalModules > 0) (doneModules * 100 / totalModules) else 0
        val levelsDone = levels.count { it.status == LevelStatus.COMPLETED }
        val active = levels.firstOrNull { it.status == LevelStatus.ACTIVE }
        val firstName = me?.profile?.fullName?.substringBefore(' ')

        LazyColumn(
            Modifier.fillMaxWidth().background(Nuru.paper),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.tabBarSpace),
        ) {
            // Calm cream header — kicker, serif headline, ring + stat cards.
            item {
                Column(
                    Modifier.fillMaxWidth().background(Nuru.surface)
                        .padding(horizontal = Spacing.screen).padding(top = Spacing.xl, bottom = Spacing.lg),
                ) {
                    Kicker(if (firstName != null) "Welcome back, $firstName" else "Welcome back")
                    Spacer(Modifier.height(Spacing.md))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("Your pathway is unfolding.", style = NuruType.display, color = Nuru.navy)
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                "A calm view of your discipleship journey, saved progress, and what opens next.",
                                style = NuruType.body, color = Nuru.ink600,
                            )
                        }
                        Spacer(Modifier.size(Spacing.base))
                        ProgressRing(pct)
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatCard("Levels", "$levelsDone/${levels.size}", Modifier.weight(1f))
                        StatCard("Modules", "$doneModules/$totalModules", Modifier.weight(1f))
                        StatCard("Offline", "Ready", Modifier.weight(1f))
                    }
                }
            }

            // Continue-your-journey card for the active level.
            active?.let { lvl ->
                item {
                    ContinueCard(lvl, Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.base)) { onOpenLevel(lvl.levelNumber) }
                }
            }

            item {
                Column(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.lg, bottom = Spacing.sm)) {
                    Kicker("Six-level pathway")
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Choose your level", style = NuruType.title, color = Nuru.ink)
                }
            }
            items(levels, key = { it.levelNumber }) { level ->
                LevelCard(
                    level = level,
                    currentLevel = summary.currentLevel,
                    onOpen = { onOpenLevel(level.levelNumber) },
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(Radii.control)).background(Nuru.white)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(label.uppercase(), style = NuruType.micro, color = Nuru.ink400)
        Spacer(Modifier.height(Spacing.xs))
        Text(value, style = NuruType.rowTitle, color = Nuru.navy, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProgressRing(pct: Int) {
    Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(74.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Nuru.track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arc, style = Stroke(width = stroke),
            )
            drawArc(
                color = Nuru.gold, startAngle = -90f, sweepAngle = 360f * (pct / 100f), useCenter = false,
                topLeft = Offset(inset, inset), size = arc, style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", style = NuruType.rowTitle, color = Nuru.navy, fontWeight = FontWeight.Medium)
            Text("DONE", style = NuruType.micro, color = Nuru.ink400)
        }
    }
}

@Composable
private fun ContinueCard(level: PathwayLevel, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val pct = if (level.totalModules > 0) level.completedModules.toFloat() / level.totalModules else 0f
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Radii.hero))
            .background(Nuru.white)
            .border(1.dp, Nuru.gold.copy(alpha = 0.35f), RoundedCornerShape(Radii.hero))
            .clickable { onOpen() }
            .padding(Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radii.control)).background(Nuru.navy.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            Text("📖", style = NuruType.title)
        }
        Spacer(Modifier.size(Spacing.base))
        Column(Modifier.weight(1f)) {
            Kicker("Continue your journey")
            Spacer(Modifier.height(Spacing.xs))
            Text("Level ${level.levelNumber}: ${level.title}", style = NuruType.cardTitle, color = Nuru.ink, maxLines = 2)
            Spacer(Modifier.height(Spacing.sm))
            ProgressBar(pct)
        }
        Spacer(Modifier.size(Spacing.sm))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Nuru.gold)
    }
}

@Composable
private fun LevelCard(level: PathwayLevel, currentLevel: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val locked = LevelGating.isLevelLocked(level.levelNumber, currentLevel, level.status)
    val done = level.status == LevelStatus.COMPLETED
    val active = level.status == LevelStatus.ACTIVE

    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .background(Nuru.white)
            .border(1.dp, if (active) Nuru.gold.copy(alpha = 0.45f) else Nuru.border, RoundedCornerShape(Radii.card))
            .then(if (locked) Modifier.alpha(0.6f) else Modifier.clickable { onOpen() })
            .padding(Spacing.base),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(Radii.control))
                .background(if (active) Nuru.navy else if (done) Nuru.goldTint else Nuru.inputBg),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Filled.Check, null, tint = Nuru.goldLo, modifier = Modifier.size(20.dp))
                locked -> Icon(Icons.Filled.Lock, null, tint = Nuru.ink400, modifier = Modifier.size(18.dp))
                else -> Text("✝", style = NuruType.title, color = Nuru.gold)
            }
        }
        Spacer(Modifier.size(Spacing.base))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker("Level ${level.levelNumber}", modifier = Modifier.weight(1f))
                StatusPill(if (done) "Complete" else if (active) "Active" else "Locked", done, active)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(level.title, style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.Medium, maxLines = 2)
            level.theme?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
            Spacer(Modifier.height(Spacing.sm))
            if (locked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = Nuru.ink400, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.size(Spacing.xs))
                    Text(LevelGating.lockedLevelLabel(currentLevel), style = NuruType.caption, color = Nuru.ink400)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${level.completedModules}/${level.totalModules} modules", style = NuruType.caption, color = Nuru.ink600, modifier = Modifier.weight(1f))
                    val p = if (level.totalModules > 0) level.completedModules * 100 / level.totalModules else 0
                    Text("$p%", style = NuruType.caption, color = Nuru.navy, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(Spacing.xs))
                ProgressBar(if (level.totalModules > 0) level.completedModules.toFloat() / level.totalModules else 0f)
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, done: Boolean, active: Boolean) {
    val (bg, fg) = when {
        done -> Nuru.goldTint to Nuru.goldChipText
        active -> Nuru.successBg to Nuru.successText
        else -> Nuru.inputBg to Nuru.ink400
    }
    Box(Modifier.clip(RoundedCornerShape(Radii.pill)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(label, style = NuruType.micro, color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.track)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp)
                .clip(RoundedCornerShape(Radii.pill)).background(Nuru.gold),
        )
    }
}
