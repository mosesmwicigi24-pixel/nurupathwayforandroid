// Pathway hub — the member's journey, level by level (GET /me/pathway). Ported to the
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
import org.nuruplace.member.ui.icons.Lucide

// The calm all-levels overview (iOS LevelsMapView) — reached from the Pathway hub's
// "Map view" link. The hub itself is PathwayHubScreen.
@Composable
fun LevelsMapScreen(me: MeResponse?, onOpenLevel: (Int) -> Unit, onBack: () -> Unit = {}) {
    // The current level's trail rides along, as on Home, Pathway and the
    // level page: production counts the exam among a level's modules, so the
    // summary alone says "learning" at 10 of 10 — Map view read "In progress"
    // and "Complete Level 1 to unlock" while every other screen said "Exam
    // ready" (Cycle 4's closing walk, 18–19).
    AsyncContent(load = {
        val s = Net.client.api.pathway()
        s to JourneyState.derive(s)?.levelNumber?.let { n -> runCatching { Net.client.api.levelModules(n).data }.getOrNull() }
    }) { (summary: PathwaySummary, trail), _ ->
        val levels = summary.levels
        // Lessons, never the exams (§8.2 #4).
        val totalModules = levels.sumOf { it.lessonCount }
        val doneModules = levels.sumOf { it.lessonsDone }
        // The journey in levels (docs/EXPERIENCE.md §3), the same number the
        // hub's ring shows — never a share of published modules (20 of 20
        // read 100% at Level 1 of 6).
        val journey = JourneyState.derive(summary, trail)
        val pct = journey?.percent ?: 0
        val levelsDone = levels.count { it.status == LevelStatus.COMPLETED }
        // The member's own level — the journey's, whatever its status — then
        // the server's active one.
        val active = levels.firstOrNull { it.levelNumber == journey?.levelNumber }
            ?: levels.firstOrNull { it.status == LevelStatus.ACTIVE }

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
                    Text("‹  Pathway", style = NuruType.cardCta, color = Nuru.navy, modifier = Modifier.clickable { onBack() })
                    Spacer(Modifier.height(Spacing.md))
                    // A pushed page: back · kicker · title (§8.1 rule 2) — the
                    // greeting ("WELCOME BACK, ADA") belongs to Home alone.
                    Kicker("Pathway · Map")
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
                        // "Offline · Ready" was jargon about the app, not the
                        // member's journey (§8.1 rule 8) — gone.
                    }
                }
            }

            // Continue-your-journey card for the active level.
            active?.let { lvl ->
                item {
                    ContinueCard(lvl, LevelsMapWords.continueCard(lvl, journey), levelPercent(lvl, journey) / 100f, Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.base)) { onOpenLevel(lvl.levelNumber) }
                }
            }

            item {
                Column(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.lg, bottom = Spacing.sm)) {
                    // The road's real length (Cycle 3's closing walk, B4): "SIX"
                    // was written in, beside a "LEVELS 0/7" tile on the same screen.
                    Kicker("${countWord(levels.size)}-level pathway")
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Choose your level", style = NuruType.title, color = Nuru.ink)
                }
            }
            items(levels, key = { it.levelNumber }) { level ->
                LevelCard(
                    level = level,
                    currentLevel = summary.currentLevel,
                    journey = journey,
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
private fun ContinueCard(level: PathwayLevel, words: LevelsMapWords.Card, pct: Float, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    // [pct]: the level with its exam as the last step (§9.2 #10).
    Row(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Radii.hero))
            .background(Nuru.white)
            .border(1.dp, Nuru.gold.copy(alpha = 0.35f), RoundedCornerShape(Radii.hero))
            .clickable { onOpen() }
            .padding(Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Lucide on a gold-tint tile, not a colour emoji on grey (§8.1 rule 7;
        // Cycle 4 walk 18) — iOS's book-open.
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radii.control)).background(Nuru.goldTint), contentAlignment = Alignment.Center) {
            Icon(Lucide.BookOpen, null, tint = Nuru.navy, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(Spacing.base))
        Column(Modifier.weight(1f)) {
            Kicker(words.kicker)
            Spacer(Modifier.height(Spacing.xs))
            Text(words.title, style = NuruType.cardTitle, color = Nuru.ink, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            words.line?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(it, style = NuruType.caption, color = Nuru.ink600)
            }
            Spacer(Modifier.height(Spacing.sm))
            ProgressBar(pct)
        }
        Spacer(Modifier.size(Spacing.sm))
        Icon(Lucide.ChevronRight, null, tint = Nuru.gold, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LevelCard(level: PathwayLevel, currentLevel: Int, journey: Journey?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
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
                done -> Icon(Lucide.Check, null, tint = Nuru.goldLo, modifier = Modifier.size(22.dp))
                locked -> Icon(Lucide.Lock, null, tint = Nuru.ink400, modifier = Modifier.size(18.dp))
                // The level being walked: the cross, drawn — not a "✝" typed in
                // a text face (§8.1 rule 7; Cycle 4 walk 18) — as iOS draws it.
                else -> CrossMark(18.dp, Nuru.gold)
            }
        }
        Spacer(Modifier.size(Spacing.base))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker("Level ${level.levelNumber}", modifier = Modifier.weight(1f))
                // The level page's own words (levelBadge, B3) — "Active" here,
                // "IN PROGRESS" there and "Exam ready" on Pathway were three
                // words for one state.
                val badge = levelBadge(level.levelNumber, level, journey)
                StatusPill(
                    if (locked) "Locked" else badge.text.lowercase().replaceFirstChar { it.uppercase() },
                    done = !locked && badge.tone == LevelBadge.Tone.ACHIEVED,
                    active = !locked && badge.tone != LevelBadge.Tone.ACHIEVED && (active || journey?.levelNumber == level.levelNumber),
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(level.title, style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.Medium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            level.theme?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
            Spacer(Modifier.height(Spacing.sm))
            if (locked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Lock, null, tint = Nuru.ink400, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(Spacing.xs))
                    Text(LevelsMapWords.lockLine(level.levelNumber, journey, preparing = level.lessonCount <= 0), style = NuruType.caption, color = Nuru.ink400)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${level.lessonsDone}/${level.lessonCount} modules", style = NuruType.caption, color = Nuru.ink600, modifier = Modifier.weight(1f))
                    // The exam is the level's last step (§9.2 #10): Map gave
                    // Level 1 "100%" before its exam was sat.
                    val p = levelPercent(level, journey)
                    Text("$p%", style = NuruType.caption, color = Nuru.navy, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(Spacing.xs))
                ProgressBar(levelPercent(level, journey) / 100f)
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

/** A small count in words, as a heading says it ("Six-level pathway");
 *  past ten, the number. */
internal fun countWord(n: Int): String = when (n) {
    1 -> "One"; 2 -> "Two"; 3 -> "Three"; 4 -> "Four"; 5 -> "Five"
    6 -> "Six"; 7 -> "Seven"; 8 -> "Eight"; 9 -> "Nine"; 10 -> "Ten"
    else -> n.toString()
}

/** The cross on the level being walked — two rounded bars, as iOS's CrossMark
 *  ("Figma's lucide Cross"): a mark, not a glyph in a text face. */
@Composable
private fun CrossMark(size: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    val bar = size * 0.32f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = bar, height = size).clip(RoundedCornerShape(999.dp)).background(color))
        Box(Modifier.size(width = size, height = bar).clip(RoundedCornerShape(999.dp)).background(color))
    }
}
