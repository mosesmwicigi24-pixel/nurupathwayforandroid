// Level detail — ported to the Figma PathwayTab: a hero header with the level
// title + level/status pills, a progress-snapshot card, the member's discipler
// card, then the vertical MODULE TRAIL (gold nodes + connectors, station cards
// with status pills, resume/done affordances). Locked modules are dimmed +
// non-tappable (§1.9, server-authoritative); a finished level offers the exam.
package org.nuruplace.member.feature.pathway

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.LevelModule
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
)

@Composable
fun LevelDetailScreen(
    levelNumber: Int,
    onBack: () -> Unit,
    onOpenModule: (String) -> Unit,
    onTakeExam: (Int) -> Unit,
) {
    AsyncContent(
        key = levelNumber,
        load = {
            val modules = Net.client.api.levelModules(levelNumber).data
            val level = runCatching { Net.client.api.pathway().levels.firstOrNull { it.levelNumber == levelNumber } }.getOrNull()
            val mentor = runCatching { Net.client.api.mentor().mentor }.getOrNull()
            LevelBundle(level, modules, mentor)
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

                // Module trail.
                Column {
                    Kicker("Your module trail")
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Learn step by step", style = NuruType.title, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.md))
                    modules.forEachIndexed { i, m ->
                        ModuleStation(
                            module = m,
                            isNext = i == nextIdx,
                            isLast = i == modules.lastIndex,
                            onOpen = { if (m.isExam) onTakeExam(levelNumber) else onOpenModule(m.moduleId) },
                        )
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
