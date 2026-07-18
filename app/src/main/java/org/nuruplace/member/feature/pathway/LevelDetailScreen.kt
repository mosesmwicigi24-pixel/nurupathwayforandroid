// Level detail — ported to the Figma PathwayTab: a hero header with the level
// title + level/status pills, a progress-snapshot card, the member's discipler
// card, then the vertical MODULE TRAIL (gold nodes + connectors, station cards
// with status pills, resume/done affordances). Locked modules are dimmed +
// non-tappable (§1.9, server-authoritative); a finished level offers the exam.
package org.nuruplace.member.feature.pathway

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.net.LevelEncouragement
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelScore
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.MentorInfo
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

private data class LevelBundle(
    val level: PathwayLevel?,
    val modules: List<LevelModule>,
    val mentor: MentorInfo.Mentor?,
    val encouragements: List<LevelEncouragement>,
    /** This level's mastery (GET /me/levels/{n}/score) — same server band shown
     *  at level-end, surfaced early on the mid-level stats card too. */
    val levelScore: LevelScore?,
    /** Current streak in days (GET /me/achievements) — same figure the Pathway
     *  hub header already shows. */
    val streak: Int,
)

/** One row of the trail — module cards and authored encouragement cards
 *  interleave on the same gold rail (iOS LevelTrailItem parity). */
private sealed class TrailItem {
    data class Mod(val module: LevelModule, val moduleIndex: Int) : TrailItem()
    data class Enc(val item: LevelEncouragement) : TrailItem()
    /** The mid-level stats card (owner spec: "around modules 5-8 you should
     *  have a card that captures all your stats") — woven in once, positionally,
     *  like an authored encouragement, but built from the member's own real data. */
    object Stats : TrailItem()
}

/** Where the mid-level stats card lands — directly after the 6th module row
 *  (or the 5th on a shorter level; skipped on very short levels where the
 *  card would feel out of place before there's anything to look back on). */
private fun statsCardAfterIndex(moduleCount: Int): Int? {
    if (moduleCount < 4) return null
    return minOf(moduleCount, 6) - 1
}

/** Weaves encouragements in at their after_module_sequence slot: each lands
 *  after the LAST module whose sequence <= its slot (tolerates sequence gaps),
 *  0 (or a slot below every visible module) surfaces at the trail head, and a
 *  slot past the last module trails it. Mirrors iOS LevelDetailViewModel.trailItems. */
private fun weaveTrail(modules: List<LevelModule>, encouragements: List<LevelEncouragement>): List<TrailItem> {
    val statsAt = statsCardAfterIndex(modules.size)
    if (encouragements.isEmpty()) {
        val items = mutableListOf<TrailItem>()
        modules.forEachIndexed { i, m ->
            items += TrailItem.Mod(m, i)
            if (i == statsAt) items += TrailItem.Stats
        }
        return items
    }
    val slots = mutableMapOf<Int, MutableList<LevelEncouragement>>()   // module index (-1 = trail head)
    for (e in encouragements) {
        val idx = if (e.afterModuleSequence <= 0) -1
        else modules.indexOfLast { it.moduleSequenceNumber <= e.afterModuleSequence }
        slots.getOrPut(idx) { mutableListOf() }.add(e)
    }
    val items = mutableListOf<TrailItem>()
    items += (slots[-1] ?: emptyList()).map { TrailItem.Enc(it) }
    modules.forEachIndexed { i, m ->
        items += TrailItem.Mod(m, i)
        if (i == statsAt) items += TrailItem.Stats
        items += (slots[i] ?: emptyList()).map { TrailItem.Enc(it) }
    }
    return items
}

@Composable
fun LevelDetailScreen(
    levelNumber: Int,
    onBack: () -> Unit,
    onOpenModule: (String) -> Unit,
    onTakeExam: (Int) -> Unit,
    onOpenDiscipler: () -> Unit = {},
) {
    AsyncContent(
        key = levelNumber,
        load = {
            val modules = Net.client.api.levelModules(levelNumber).data
            val level = runCatching { Net.client.api.pathway().levels.firstOrNull { it.levelNumber == levelNumber } }.getOrNull()
            val mentor = runCatching { Net.client.api.mentor().mentor }.getOrNull()
            // Best-effort: no encouragements (unauthored or failed fetch) weaves nothing in.
            val encouragements = runCatching { Net.client.api.levelEncouragements(levelNumber).data }.getOrDefault(emptyList())
            val levelScore = runCatching { Net.client.api.levelScore(levelNumber) }.getOrNull()
            val streak = runCatching { Net.client.api.achievements().streak.current }.getOrDefault(0)
            LevelBundle(level, modules, mentor, encouragements, levelScore, streak)
        },
    ) { bundle: LevelBundle, _ ->
        val modules = bundle.modules
        val level = bundle.level
        val total = level?.totalModules ?: modules.size
        val done = level?.completedModules ?: modules.count { it.completed }
        val pct = if (total > 0) done * 100 / total else 0
        // The exam container is its own visible row in the trail — exclude it from
        // "finished every module", and keep the standalone exam button only as a
        // fallback for levels that have no exam module authored.
        val hasExamModule = modules.any { it.isExam }
        val content = modules.filter { !it.isExam }
        val allDone = content.isNotEmpty() && content.all { it.completed }
        val nextIdx = modules.indexOfFirst { !it.completed && !(it.locked || it.status == ModuleStatus.LOCKED) }

        // Within-level "walk with your discipler" reminder — eligible once the
        // member is genuinely mid-level (≥3 modules done, not yet at the level's
        // end). Re-appearance policy lives in DisciplerReminderSession (session
        // cap) + AppPrefs (24h quiet period after an explicit X dismissal).
        var showReminder by remember { mutableStateOf(false) }
        val reduceMotion = rememberDisciplerReduceMotion()
        LaunchedEffect(done, allDone, levelNumber) {
            if (done >= 3 && !allDone && DisciplerReminderSession.shouldShow(levelNumber)) {
                showReminder = true
            }
        }
        if (showReminder) {
            LaunchedEffect(showReminder) {
                delay(60_000)
                showReminder = false
            }
        }

        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
            // Hero — navy gradient with overlaid pills + serif title (data-honest
            // fallback for the Figma's per-level hero image).
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(Nuru.heroGradient)) {
                IconButton(onClick = onBack, modifier = Modifier.padding(top = Spacing.md, start = Spacing.sm)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.navyDeep.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nuru.onNavy)
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(Spacing.screen)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.gold).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("LEVEL $levelNumber", style = NuruType.micro, color = Nuru.navy, fontWeight = FontWeight.Bold)
                        }
                        val complete = level?.status == LevelStatus.COMPLETED
                        Box(Modifier.clip(RoundedCornerShape(Radii.pill)).background(if (complete) Nuru.success else Nuru.white.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(if (complete) "COMPLETE" else "IN PROGRESS", style = NuruType.micro, color = Nuru.onNavy, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(level?.title ?: "Level $levelNumber", style = NuruType.display, color = Nuru.onNavy, maxLines = 2)
                    level?.theme?.let { Text(it, style = NuruType.body, color = Nuru.onNavyDim) }
                }
            }

            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                // Progress snapshot.
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Nuru.white)
                        .border(1.dp, Nuru.border, RoundedCornerShape(Radii.card)).padding(Spacing.base),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$done of $total modules", style = NuruType.cardCta, color = Nuru.navy, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("$pct%", style = NuruType.cardCta, color = Nuru.gold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    ProgressBar(if (total > 0) done.toFloat() / total else 0f)
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        level?.minutes?.takeIf { it > 0 }?.let { MetaChip("≈ $it min") }
                        MetaChip("$total lessons")
                    }
                }

                // Discipler card — real /growth/mentor pairing.
                bundle.mentor?.let { m -> DisciplerCard(m) }

                // Module trail — authored encouragements woven in at their
                // after_module_sequence slot (iOS LevelDetailView parity).
                Column {
                    Kicker("Your module trail")
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Learn step by step", style = NuruType.title, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.md))
                    val trail = remember(modules, bundle.encouragements) { weaveTrail(modules, bundle.encouragements) }
                    trail.forEachIndexed { idx, item ->
                        val isLastRow = idx == trail.lastIndex
                        when (item) {
                            is TrailItem.Mod -> ModuleStation(
                                module = item.module,
                                isNext = item.moduleIndex == nextIdx,
                                isLast = isLastRow,
                                onOpen = { if (item.module.isExam) onTakeExam(levelNumber) else onOpenModule(item.module.moduleId) },
                            )
                            is TrailItem.Enc -> EncouragementStation(item.item, isLast = isLastRow)
                            is TrailItem.Stats -> StatsStation(
                                done = done, total = total, pct = pct,
                                band = bundle.levelScore?.band, streak = bundle.streak,
                                mentorName = bundle.mentor?.fullName,
                                isLast = isLastRow,
                                onMessage = onOpenDiscipler,
                            )
                        }
                    }
                }

                if (allDone && !hasExamModule) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Nuru.goldTint).padding(Spacing.base),
                    ) {
                        Text("You've finished every module in this level.", style = NuruType.body, color = Nuru.ink)
                        Spacer(Modifier.height(Spacing.md))
                        // The exam gate stays hidden until an admin publishes it.
                        if (level?.examPublished != false) {
                            PrimaryButton("Take the Level $levelNumber exam", onClick = { onTakeExam(levelNumber) })
                        } else {
                            Text(
                                "Your discipler is preparing this level's exam. It will appear here once it's ready.",
                                style = NuruType.caption, color = Nuru.ink600,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }

        // The reminder floats above the trail — a card, not a modal; it never
        // blocks scrolling or the content underneath (owner spec: "stays about
        // a minute, then disappears — and it can pop again later").
        AnimatedVisibility(
            visible = showReminder,
            enter = if (reduceMotion) fadeIn(tween(220)) else slideInVertically(tween(320)) { it / 2 } + fadeIn(tween(320)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = Spacing.screen, vertical = Spacing.tabBarSpace),
        ) {
            DisciplerReminderCard(
                mentor = bundle.mentor,
                onMessage = { showReminder = false; onOpenDiscipler() },
                onDismiss = {
                    AppPrefs.markDisciplerReminderDismissed(levelNumber)
                    showReminder = false
                },
            )
        }
        }
    }
}

@Composable
private fun DisciplerCard(m: MentorInfo.Mentor) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(Radii.card)).padding(Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radii.control)).background(Nuru.success), contentAlignment = Alignment.Center) {
            Text(m.fullName.firstOrNull()?.uppercase() ?: "?", style = NuruType.rowTitle, color = Nuru.onNavy, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Kicker("Walk it with your discipler")
            Text(m.fullName, style = NuruType.rowTitle, color = Nuru.navy, fontWeight = FontWeight.SemiBold, maxLines = 1)
            m.cellName?.let { Text(it, style = NuruType.caption, color = Nuru.ink600, maxLines = 1) }
        }
    }
}

@Composable
private fun ModuleStation(module: LevelModule, isNext: Boolean, isLast: Boolean, onOpen: () -> Unit) {
    val locked = module.locked || module.status == ModuleStatus.LOCKED
    val done = module.completed
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Node + connector column.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The module NUMBER never leaves the node — state lives in the
            // medallion fill + a small corner seal (check when done, lock when
            // locked); the next module keeps its navy ring.
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(Radii.pill))
                        .background(if (done || isNext) Nuru.gold else Nuru.inputBg)
                        .then(if (isNext) Modifier.border(2.dp, Nuru.navy, RoundedCornerShape(Radii.pill)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${module.moduleSequenceNumber}",
                        style = NuruType.cardCta,
                        color = when { done || isNext -> Nuru.navy; else -> Nuru.ink400 },
                    )
                }
                if (done) {
                    Box(
                        Modifier.offset(x = 4.dp, y = (-3).dp).size(15.dp)
                            .clip(RoundedCornerShape(Radii.pill)).background(Nuru.navy)
                            .border(1.5.dp, Nuru.white, RoundedCornerShape(Radii.pill)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, null, tint = Nuru.white, modifier = Modifier.size(9.dp)) }
                } else if (locked) {
                    Box(
                        Modifier.offset(x = 4.dp, y = (-3).dp).size(15.dp)
                            .clip(RoundedCornerShape(Radii.pill)).background(Nuru.inputBg)
                            .border(1.5.dp, Nuru.white, RoundedCornerShape(Radii.pill)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Lock, null, tint = Nuru.ink400, modifier = Modifier.size(8.dp)) }
                }
            }
            if (!isLast) {
                Box(Modifier.width(3.dp).height(46.dp).background(if (done) Nuru.gold else Nuru.track))
            }
        }
        Spacer(Modifier.size(Spacing.md))
        // Station card.
        Column(
            Modifier.weight(1f).padding(bottom = Spacing.md)
                .clip(RoundedCornerShape(20.dp))
                .background(if (locked) Nuru.surface else Nuru.white)
                .border(1.dp, if (isNext) Nuru.gold.copy(alpha = 0.4f) else Nuru.border, RoundedCornerShape(20.dp))
                .then(if (locked) Modifier.alpha(0.85f) else Modifier.clickable { onOpen() })
                .padding(Spacing.base),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Kicker(if (module.isExam) "Level exam" else "Module ${module.moduleSequenceNumber}")
                    Text(module.title, style = NuruType.rowTitle, color = Nuru.navy, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
                ModuleStatusPill(done, isNext)
            }
            module.summary?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(it, style = NuruType.caption, color = Nuru.ink600, maxLines = 2)
            }
            if (done || isNext) {
                Spacer(Modifier.height(Spacing.sm))
                ProgressBar(if (done) 1f else module.progress.toFloat().coerceIn(0f, 1f), navy = isNext)
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                module.estimatedMinutes?.let { MetaChip("$it min") }
                if (module.evaluationKind.lowercase().contains("quiz")) MetaChip("Quiz · ${module.quizPassMark}%", gold = true)
                Spacer(Modifier.weight(1f))
                if (isNext) {
                    Row(
                        Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.navy).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (module.isExam) "Start exam" else "Resume", style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Nuru.gold, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                when {
                    module.isExam && done -> "Level exam · passed."
                    module.isExam && isNext -> "Level exam · ready — tap to begin."
                    module.isExam -> "Unlocks when you finish every module."
                    done -> "Completed — nicely done."
                    isNext -> "Pick up where you left off."
                    else -> "Unlocks when you finish the one before."
                },
                style = NuruType.micro, color = if (done) Nuru.goldChipText else if (isNext) Nuru.goldLo else Nuru.ink400,
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

/** A small gold moment strung on the same rail as the module stations (iOS
 *  encouragementRow + EncouragementTrailCard parity) — authored content only,
 *  rendered whenever it exists. */
@Composable
private fun EncouragementStation(e: LevelEncouragement, isLast: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.goldTint)
                    .border(1.dp, Nuru.gold.copy(alpha = 0.4f), RoundedCornerShape(Radii.pill)),
                contentAlignment = Alignment.Center,
            ) {
                if (!e.emoji.isNullOrBlank()) {
                    Text(e.emoji, style = NuruType.caption)
                } else {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Nuru.gold, modifier = Modifier.size(12.dp))
                }
            }
            if (!isLast) {
                Box(Modifier.width(2.dp).height(46.dp).background(Nuru.gold.copy(alpha = 0.35f)))
            }
        }
        Spacer(Modifier.size(Spacing.md))
        Column(
            Modifier.weight(1f).padding(bottom = Spacing.base)
                .clip(RoundedCornerShape(20.dp)).background(Nuru.verseBg)
                .border(1.dp, Nuru.gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(Spacing.base),
        ) {
            if (!e.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = e.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Nuru.goldChipText, modifier = Modifier.size(11.dp))
                Text(encouragementKicker(e.kind), style = NuruType.kicker, color = Nuru.goldChipText)
            }
            e.title?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(it, style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
            }
            e.body?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(it, style = NuruType.caption, color = Nuru.ink600)
            }
            e.scriptureRef?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(it, style = NuruType.micro, color = Nuru.goldLo, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Mid-level stats card (owner spec: "around modules 5-8 you should have a
// card that captures all your stats, beautiful, with imagery, that reminds
// and encourages you"). Navy-gradient + gold accents, the member's real,
// server-provided progress, and the same discipler affordance as the
// reminder pop-up. Static: it lives in the trail, not a popup.

@Composable
private fun StatsStation(
    done: Int, total: Int, pct: Int, band: String?, streak: Int, mentorName: String?,
    isLast: Boolean, onMessage: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.gold),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            if (!isLast) {
                Box(Modifier.width(2.dp).height(46.dp).background(Nuru.gold.copy(alpha = 0.35f)))
            }
        }
        Spacer(Modifier.size(Spacing.md))
        Column(
            Modifier.weight(1f).padding(bottom = Spacing.base)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Nuru.navy, Nuru.navyDeep)))
                .border(1.dp, Nuru.gold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(Spacing.base),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Nuru.gold, modifier = Modifier.size(12.dp))
                Text("YOUR JOURNEY SO FAR", style = NuruType.kicker, color = Nuru.gold)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text("Look how far you've come", style = NuruType.cardTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Spacing.base))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatsRing(pct)
                Spacer(Modifier.size(Spacing.base))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatLine("$done of $total", "modules complete")
                    if (!band.isNullOrBlank()) StatLine(band, "your mastery so far")
                    if (streak > 0) StatLine("$streak-day", "streak")
                }
            }
            Spacer(Modifier.height(Spacing.base))
            Text(
                "Walk the rest with your discipler" + (mentorName?.let { " — $it is right there with you" } ?: "") + ".",
                style = NuruType.caption, color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.gold)
                    .clickable { onMessage() }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Nuru.navyDeep, modifier = Modifier.size(14.dp))
                Text("Message your discipler", style = NuruType.micro, color = Nuru.navyDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatLine(value: String, label: String) {
    Column {
        Text(value, style = NuruType.rowTitle, color = Color.White, fontWeight = FontWeight.Bold)
        Text(label, style = NuruType.micro, color = Color.White.copy(alpha = 0.55f))
    }
}

/** Small gold progress ring on the navy card ground (PathwayHubScreen's HubRing
 *  palette flipped — see that file's version for the light-background ring). */
@Composable
private fun StatsRing(pct: Int) {
    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(60.dp)) {
            val sw = 6.dp.toPx(); val inset = sw / 2
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            drawArc(Nuru.gold, -90f, 360f * (pct.coerceIn(0, 100) / 100f), false, Offset(inset, inset), arc, style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text("$pct%", style = NuruType.rowTitle, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// ── Within-level discipler reminder pop-up (owner spec: "a pop-up appears
// that says work with your discipler, stays about a minute, then disappears
// — and it can pop again later; its work is to REMIND you"). A floating
// card, not a modal — see AnimatedVisibility usage above.

/** Remind-don't-nag session cap — at most once per app process per level (this
 *  object's lifetime IS the app session; a relaunch clears it). The 24h
 *  post-dismissal quiet period is separately persisted in AppPrefs. */
private object DisciplerReminderSession {
    private val shown = mutableSetOf<Int>()

    fun shouldShow(levelNumber: Int): Boolean {
        if (shown.contains(levelNumber) || AppPrefs.isDisciplerReminderDismissedRecently(levelNumber)) return false
        shown += levelNumber
        return true
    }
}

/** True when the user has turned system animations off ("Remove animations") —
 *  mirrors LiveRadioScreen's rememberReduceMotion for this screen's popup. */
@Composable
private fun rememberDisciplerReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        val scale = android.provider.Settings.Global.getFloat(
            resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        )
        scale == 0f
    }
}

@Composable
private fun DisciplerReminderCard(mentor: MentorInfo.Mentor?, onMessage: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Nuru.white)
            .border(1.dp, Nuru.gold.copy(alpha = 0.4f), RoundedCornerShape(20.dp)).padding(Spacing.base),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.success), contentAlignment = Alignment.Center) {
            val initial = mentor?.fullName?.firstOrNull()?.uppercase() ?: "?"
            Text(initial, style = NuruType.rowTitle, color = Nuru.onNavy, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Kicker("Walk with your discipler")
            Text(
                mentor?.fullName?.let { "$it is walking this with you" } ?: "A discipler is walking this with you",
                style = NuruType.rowTitle, color = Nuru.navy, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "You're making real progress — you don't have to walk it alone.",
                style = NuruType.caption, color = Nuru.ink600,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.navyDeep)
                    .clickable { onMessage() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text("Message", style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.inputBg), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Dismiss", tint = Nuru.ink400, modifier = Modifier.size(13.dp))
            }
        }
    }
}

/** Kicker per authored kind — splash/cheer/sticker/note/celebration/nudge/verse. */
private fun encouragementKicker(kind: String?): String = when (kind?.lowercase()) {
    "celebration", "cheer" -> "CELEBRATE"
    "nudge" -> "KEEP GOING"
    "verse" -> "A VERSE FOR YOU"
    "note" -> "A NOTE FOR YOU"
    else -> "ENCOURAGEMENT"
}

@Composable
private fun ModuleStatusPill(done: Boolean, isNext: Boolean) {
    val (label, bg, fg) = when {
        done -> Triple("Done", Nuru.successBg, Nuru.successText)
        isNext -> Triple("In progress", Nuru.goldTint, Nuru.goldChipText)
        else -> Triple("Locked", Nuru.inputBg, Nuru.ink400)
    }
    Box(Modifier.clip(RoundedCornerShape(Radii.pill)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(label, style = NuruType.micro, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetaChip(label: String, gold: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(Radii.pill)).background(if (gold) Nuru.goldTint else Nuru.inputBg).padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(label, style = NuruType.micro, color = if (gold) Nuru.goldChipText else Nuru.ink600) }
}

@Composable
private fun ProgressBar(fraction: Float, navy: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.track)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp)
                .clip(RoundedCornerShape(Radii.pill)).background(if (navy) Nuru.navy else Nuru.gold),
        )
    }
}
