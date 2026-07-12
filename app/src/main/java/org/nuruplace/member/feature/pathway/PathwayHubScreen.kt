// Pathway tab — a faithful port of the iOS PathwayView "PathwayHub" (Features/
// Pathway/PathwayView.swift): a light cream hero (streak · bell · progress ring ·
// greeting · active level · progress · navy Continue card), a horizontal journey
// rail of tappable level nodes, the selected level's real module trail (with a
// mid-trail "Pause & surrender" image and an exam-gate row), a "Walk with your
// discipler" row, a milestones badge rail, and the Summit destination card. The
// calm all-levels list lives behind the "Map view" link (LevelsMapScreen).
package org.nuruplace.member.feature.pathway

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.VolunteerActivism
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary
import org.nuruplace.member.ui.components.FitImage
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import java.time.LocalTime

// Exact Figma palette (LevelsOverview.tsx) — local so the page is 1:1 with iOS.
private object PW {
    val navy = Color(0xFF0A2540)
    val navyDeep = Color(0xFF081C36)
    val gold = Color(0xFFC9A227)
    val goldLight = Color(0xFFE6C068)
    val goldDeep = Color(0xFFA8861C)
    val eyebrow = Color(0xFF9A7A2A)
    val ink = Color(0xFF0B0B0C)
    val ink2 = Color(0xFF59667C)
    val ink3 = Color(0xFF6F7E93)
    val bg = Color(0xFFF4F0E8)
    val surface = Color(0xFFFBF8F1)
    val mutedBg = Color(0xFFEEF1F5)
    val border = Color(0x140A2540)
    val badge = listOf("🪨", "🕊️", "🌿", "🔥", "📖", "👑", "⭐", "🏅")
    val navyGrad = Brush.linearGradient(listOf(navy, navyDeep))
    val goldGrad = Brush.linearGradient(listOf(gold, Color(0xFFA87F29)))
    val headerGrad = Brush.linearGradient(listOf(Color(0xFFF6F4EF), Color(0xFFEFE8DA)))
    val subtitle = mapOf(
        1 to "God, His Word, prayer & the Church", 2 to "Who God is — His character and heart",
        3 to "Grace, repentance, and new life", 4 to "Who you are in Him",
        5 to "How Scripture forms faith and life", 6 to "Walking in the Spirit's gifts and power",
    )
    // Type helpers — delegate to the canonical schema (ui/theme/TypeSchema.kt).
    fun over(size: Int, ker: Float = 1.4f) = nuruSans(size, FontWeight.Bold, ker)
    fun t(size: Int, w: FontWeight = FontWeight.Normal, ker: Float = 0f) = nuruSans(size, w, ker.takeIf { it != 0f })
    fun serif(size: Int, w: FontWeight = FontWeight.Medium, ker: Float = 0f) = nuruSerif(size, w, ker.takeIf { it != 0f })
}

private fun pwShort(t: String): String = t.split(" ").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: ""
private fun pwSubtitle(l: PathwayLevel?): String {
    if (l == null) return ""
    l.theme?.takeIf { it.isNotBlank() }?.let { return it }
    l.description?.takeIf { it.isNotBlank() }?.let { return it }
    return PW.subtitle[l.levelNumber] ?: ""
}
private fun pwGreeting(): String = when (LocalTime.now().hour) {
    in 0..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening"
}

@Composable
fun PathwayHubScreen(
    me: MeResponse?,
    onOpenLevel: (Int) -> Unit,
    onOpenModule: (String) -> Unit,
    onOpenExam: (Int) -> Unit,
    onOpenMentor: () -> Unit,
    onOpenMap: () -> Unit,
) {
    var summary by remember { mutableStateOf<PathwaySummary?>(null) }
    var streak by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var modulesByLevel by remember { mutableStateOf<Map<Int, List<LevelModule>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        summary = runCatching { Net.client.api.pathway() }.getOrNull()
        streak = runCatching { Net.client.api.achievements().streak.current }.getOrDefault(0)
    }

    val levels = summary?.levels ?: emptyList()
    val active = levels.firstOrNull { it.status == LevelStatus.ACTIVE }
        ?: levels.firstOrNull { it.levelNumber == summary?.currentLevel } ?: levels.firstOrNull()
    val selNum = selected ?: active?.levelNumber
    val selLevel = levels.firstOrNull { it.levelNumber == selNum } ?: active

    LaunchedEffect(selNum) {
        val n = selNum ?: return@LaunchedEffect
        if (modulesByLevel[n] == null) {
            val mods = runCatching { Net.client.api.levelModules(n).data }.getOrDefault(emptyList())
            modulesByLevel = modulesByLevel + (n to mods)
        }
    }

    val totalModules = levels.sumOf { it.totalModules }
    val doneModules = levels.sumOf { it.completedModules }
    val overallPct = if (totalModules > 0) (doneModules * 100 / totalModules) else 0
    val firstName = me?.profile?.fullName?.substringBefore(' ') ?: "Friend"
    val activeMods = modulesByLevel[active?.levelNumber]
    val resume = activeMods?.let { it.firstOrNull { m -> m.status == ModuleStatus.NEXT } ?: it.firstOrNull { m -> !m.completed } ?: it.lastOrNull() }

    Column(Modifier.fillMaxSize().background(PW.bg).verticalScroll(rememberScrollState())) {
        HubHeader(firstName, streak, active, levels, overallPct, resume, onOpenModule)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            JourneyRail(levels, selNum ?: -1, onSelect = { selected = it }, onMap = onOpenMap)
            selLevel?.let { lv ->
                SelectedModules(
                    level = lv,
                    modules = modulesByLevel[lv.levelNumber] ?: emptyList(),
                    loading = modulesByLevel[lv.levelNumber] == null,
                    onOpenModule = onOpenModule,
                    onOpenExam = onOpenExam,
                )
            }
            DisciplershipRow(onOpenMentor)
            Milestones(levels)
            SummitCard(overallPct, levels, firstName)
            Spacer(Modifier.height(Spacing.tabBarSpace))
        }
    }
}

// ─────────────────────────── Header ───────────────────────────

@Composable
private fun HubHeader(
    firstName: String,
    streak: Int,
    active: PathwayLevel?,
    levels: List<PathwayLevel>,
    overallPct: Int,
    resume: LevelModule?,
    onOpenModule: (String) -> Unit,
) {
    val idx = levels.indexOfFirst { it.levelNumber == active?.levelNumber }.coerceAtLeast(0)
    val pct = active?.let { if (it.totalModules > 0) it.completedModules * 100 / it.totalModules else 0 } ?: 0
    val remaining = active?.let { (it.totalModules - it.completedModules).coerceAtLeast(0) } ?: 0
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(PW.headerGrad)
            .padding(horizontal = 20.dp).padding(top = Spacing.md, bottom = 20.dp),
    ) {
        // top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR PATHWAY", style = PW.over(9, 1.8f), color = PW.eyebrow)
            if (streak > 0) {
                Spacer(Modifier.width(Spacing.sm))
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White).border(1.dp, PW.border, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = PW.eyebrow, modifier = Modifier.size(9.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$streak-day streak", style = PW.over(9, 0f), color = PW.eyebrow)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(Color.White).border(1.dp, PW.border, RoundedCornerShape(999.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Notifications, null, tint = PW.navy, modifier = Modifier.size(17.dp))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(PW.gold).align(Alignment.TopEnd))
            }
            Spacer(Modifier.width(Spacing.sm))
            HubRing(overallPct)
        }
        Text("${pwGreeting()}, $firstName · Level ${idx + 1} of ${levels.size.coerceAtLeast(1)}", style = PW.t(10), color = PW.ink2, modifier = Modifier.padding(top = 16.dp))
        Text(active?.title ?: "Your pathway", style = PW.serif(26, FontWeight.SemiBold, -0.52f), color = PW.navy, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(pwSubtitle(active), style = PW.t(12), color = PW.ink2, modifier = Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            Box(Modifier.weight(1f)) { PWBar(pct, PW.goldGrad, PW.navy.copy(alpha = 0.10f)) }
            Spacer(Modifier.width(Spacing.sm))
            Text("${active?.completedModules ?: 0}/${active?.totalModules ?: 0}", style = PW.t(10, FontWeight.SemiBold), color = PW.ink2)
        }
        if (remaining > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = PW.eyebrow, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (remaining == 1) "Just 1 module left to level up 🎉" else "Only $remaining modules to complete this level", style = PW.t(10, FontWeight.SemiBold), color = PW.eyebrow)
            }
        }
        // Continue where you left off — navy CTA
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(PW.navyGrad)
                .clickable(enabled = resume != null) { resume?.let { onOpenModule(it.moduleId) } }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(PW.gold), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = PW.navy, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("CONTINUE WHERE YOU LEFT OFF", style = PW.over(8, 1.28f), color = PW.goldLight)
                Text(resume?.title ?: "Level complete", style = PW.t(14, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HubRing(pct: Int) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(40.dp)) {
            val sw = 3.dp.toPx(); val inset = sw / 2
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(PW.navy.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            drawArc(PW.gold, -90f, 360f * (pct.coerceIn(0, 100) / 100f), false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("$pct%", style = PW.over(10, 0f), color = PW.eyebrow)
    }
}

@Composable
private fun PWBar(pct: Int, fill: Brush, track: Color, height: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(999.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).height(height).clip(RoundedCornerShape(999.dp)).background(fill))
    }
}

// ─────────────────────────── Journey rail ───────────────────────────

@Composable
private fun JourneyRail(levels: List<PathwayLevel>, selected: Int, onSelect: (Int) -> Unit, onMap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("THE JOURNEY · ${levels.size} LEVELS", style = PW.over(9, 1.62f), color = PW.goldDeep)
            Spacer(Modifier.weight(1f))
            Text("Map view", style = PW.over(9, 0f), color = PW.gold, modifier = Modifier.clickable { onMap() })
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp)) {
            levels.forEachIndexed { i, lvl ->
                JourneyNode(lvl, i + 1, lvl.levelNumber == selected) { onSelect(lvl.levelNumber) }
                if (i < levels.size - 1) {
                    Box(Modifier.padding(top = 40.dp).width(28.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(if (lvl.status == LevelStatus.COMPLETED) PW.gold else PW.navy.copy(alpha = 0.12f)))
                }
            }
        }
    }
}

@Composable
private fun JourneyNode(level: PathwayLevel, number: Int, selected: Boolean, onTap: () -> Unit) {
    val done = level.status == LevelStatus.COMPLETED
    val active = level.status == LevelStatus.ACTIVE
    Column(Modifier.width(68.dp).clickable { onTap() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (active) "▾ You" else " ", style = PW.over(7, 0.7f), color = if (active) PW.gold else Color.Transparent, modifier = Modifier.height(12.dp))
        // The level NUMBER never leaves the circle — completion becomes a corner
        // check-seal; locked levels keep their number with a lock-seal.
        Box(contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(54.dp).clip(RoundedCornerShape(999.dp)).border(2.dp, PW.gold, RoundedCornerShape(999.dp)))
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (done || active) PW.goldGrad else Brush.linearGradient(listOf(PW.mutedBg, PW.mutedBg)))
                        .then(if (active) Modifier.border(2.dp, PW.navy, RoundedCornerShape(999.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$number", style = PW.t(15, FontWeight.Bold), color = if (done || active) PW.navy else PW.ink3)
                }
                if (done) {
                    Box(
                        Modifier.offset(x = 3.dp, y = (-2).dp).size(16.dp)
                            .clip(RoundedCornerShape(999.dp)).background(PW.navy)
                            .border(1.5.dp, Color.White, RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(9.dp)) }
                } else if (!active) {
                    Box(
                        Modifier.offset(x = 3.dp, y = (-2).dp).size(16.dp)
                            .clip(RoundedCornerShape(999.dp)).background(PW.mutedBg)
                            .border(1.5.dp, Color.White, RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Lock, null, tint = PW.ink3, modifier = Modifier.size(8.dp)) }
                }
            }
        }
        Text(pwShort(level.title), style = PW.t(9, if (active) FontWeight.Bold else FontWeight.Medium), color = if (active) PW.navy else if (level.status == LevelStatus.LOCKED) PW.ink3 else PW.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

// ─────────────────────────── Selected level's module trail ───────────────────────────

@Composable
private fun SelectedModules(level: PathwayLevel, modules: List<LevelModule>, loading: Boolean, onOpenModule: (String) -> Unit, onOpenExam: (Int) -> Unit) {
    val ordered = remember(modules) {
        fun rank(m: LevelModule) = if (m.status == ModuleStatus.COMPLETED) 0 else if (m.status == ModuleStatus.NEXT) 1 else 2
        modules.sortedWith(compareBy({ rank(it) }, { it.moduleSequenceNumber }))
    }
    val resume = ordered.firstOrNull { it.status == ModuleStatus.NEXT }
    // When the level owns an exam container it IS the exam entry (a visible,
    // locked-until-ready row) — the separate gate only serves levels that have
    // no exam module authored.
    val hasExamModule = ordered.any { it.isExam }
    val examReady = !hasExamModule && ordered.isNotEmpty() && ordered.all { it.completed } &&
        level.status != LevelStatus.COMPLETED && level.examPublished  // hidden until published
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(level.title.uppercase(), style = PW.over(9, 1.62f), color = PW.goldDeep, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${level.completedModules} of ${level.totalModules} done", style = PW.t(11), color = PW.ink2)
            }
            resume?.let { r -> Text("Continue →", style = PW.over(10, 0f), color = PW.gold, modifier = Modifier.clickable { if (r.isExam) onOpenExam(level.levelNumber) else onOpenModule(r.moduleId) }) }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, PW.border, RoundedCornerShape(22.dp)),
        ) {
            when {
                loading -> repeat(3) { ModuleSkeletonRow() }
                ordered.isEmpty() -> Text("Modules open as you progress.", style = PW.t(13), color = PW.ink3, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                else -> {
                    ordered.forEachIndexed { i, m ->
                        ModuleRow(m, last = (i == ordered.size - 1) && !examReady) {
                            if (m.status != ModuleStatus.LOCKED) {
                                if (m.isExam) onOpenExam(level.levelNumber) else onOpenModule(m.moduleId)
                            }
                        }
                        if (i == 3 && ordered.size > 4) SurrenderFigure()
                    }
                    if (examReady) ExamGateRow(level.levelNumber) { onOpenExam(level.levelNumber) }
                }
            }
        }
    }
}

@Composable
private fun ModuleRow(m: LevelModule, last: Boolean, onTap: () -> Unit) {
    val done = m.status == ModuleStatus.COMPLETED
    val active = m.status == ModuleStatus.NEXT
    val locked = m.status == ModuleStatus.LOCKED
    val exam = m.isExam
    val caption = when {
        exam && done -> "Level exam · passed"
        exam && active -> "Level exam · ready — tap to begin"
        exam -> "Finish every module to unlock the exam"
        done -> "Completed"
        active -> "In progress · tap to continue"
        else -> "Locked"
    }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .background(if (active) PW.gold.copy(alpha = 0.05f) else if (exam) PW.gold.copy(alpha = 0.03f) else Color.Transparent)
                .clickable { onTap() }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The module NUMBER stays put; state moves to a corner seal. The
            // exam tile keeps its award identity.
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(11.dp))
                        .background(
                            if (done) Brush.linearGradient(listOf(PW.gold.copy(alpha = 0.13f), PW.gold.copy(alpha = 0.13f)))
                            else if (active) PW.goldGrad
                            else if (exam) Brush.linearGradient(listOf(PW.gold.copy(alpha = 0.10f), PW.gold.copy(alpha = 0.10f)))
                            else Brush.linearGradient(listOf(PW.mutedBg, PW.mutedBg)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (exam) Icon(Icons.Filled.EmojiEvents, null, tint = if (active) PW.navy else PW.goldDeep, modifier = Modifier.size(15.dp))
                    else Text(
                        "${m.moduleSequenceNumber}",
                        style = PW.t(13, FontWeight.Bold),
                        color = if (done) PW.goldDeep else if (active) PW.navy else PW.ink3,
                    )
                }
                if (done) {
                    Box(
                        Modifier.offset(x = 4.dp, y = (-3).dp).size(13.dp)
                            .clip(RoundedCornerShape(999.dp)).background(PW.navy)
                            .border(1.2.dp, Color.White, RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(7.dp)) }
                } else if (!active) {
                    Box(
                        Modifier.offset(x = 4.dp, y = (-3).dp).size(13.dp)
                            .clip(RoundedCornerShape(999.dp)).background(PW.mutedBg)
                            .border(1.2.dp, Color.White, RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Lock, null, tint = PW.ink3, modifier = Modifier.size(7.dp)) }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, style = PW.t(13, if (active || exam) FontWeight.Bold else FontWeight.Medium), color = if (locked && !exam) PW.ink2 else PW.navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(caption, style = PW.t(9, if (active || exam) FontWeight.Bold else FontWeight.Medium), color = if (active || (exam && !done)) PW.goldDeep else PW.ink3)
            }
            when {
                active -> Box(Modifier.clip(RoundedCornerShape(999.dp)).background(PW.navy).padding(horizontal = 10.dp, vertical = 5.dp)) { Text(if (exam) "Start exam" else "Resume", style = PW.over(9, 0f), color = PW.gold) }
                done -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
            }
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(PW.border))
    }
}

@Composable
private fun ModuleSkeletonRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(PW.mutedBg))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(150.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(PW.mutedBg))
            Box(Modifier.width(72.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(PW.mutedBg))
        }
    }
}

@Composable
private fun ExamGateRow(levelNumber: Int, onTap: () -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(PW.gold.copy(alpha = 0.35f)))
        Row(
            Modifier.fillMaxWidth().background(PW.gold.copy(alpha = 0.10f)).clickable { onTap() }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(PW.goldGrad), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.EmojiEvents, null, tint = PW.navy, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Take the Level $levelNumber exam", style = PW.t(13, FontWeight.Bold), color = PW.navy, maxLines = 1)
                Text("Every module is done — the gate is open", style = PW.t(9, FontWeight.SemiBold), color = PW.goldDeep)
            }
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(PW.navy).padding(horizontal = 10.dp, vertical = 5.dp)) { Text("Begin", style = PW.over(9, 0f), color = PW.gold) }
        }
    }
}

@Composable
private fun SurrenderFigure() {
    Box(Modifier.fillMaxWidth().height(224.dp)) {
        FitImage("https://images.unsplash.com/photo-1510590337019-5ef8d3d32116?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080", modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x26081424), Color(0x8C081424), Color(0xE6081424)))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text("PAUSE & SURRENDER", style = PW.over(7, 1.54f), color = PW.goldLight)
            Text("“Offer yourselves as a living sacrifice, holy and pleasing to God.”", style = PW.serif(12, FontWeight.Medium), color = Color.White)
            Text("Romans 12:1 · Surrender to His Word", style = PW.t(8, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ─────────────────────────── Discipleship + Milestones + Summit ───────────────────────────

@Composable
private fun DisciplershipRow(onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, PW.border, RoundedCornerShape(20.dp)).clickable { onTap() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PW.goldGrad), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.VolunteerActivism, null, tint = PW.navy, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("WALK WITH YOUR DISCIPLER", style = PW.over(8, 1.28f), color = PW.goldDeep)
            Text("Your Discipleship Hub", style = PW.t(14, FontWeight.SemiBold), color = PW.navy, maxLines = 1)
            Text("Message, feedback & meeting notes", style = PW.t(11), color = PW.ink2, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFB5BDC9), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Milestones(levels: List<PathwayLevel>) {
    val earned = levels.count { it.status == LevelStatus.COMPLETED }
    val rewardIdx = levels.indexOfFirst { it.status != LevelStatus.COMPLETED }
    val reward = rewardIdx.takeIf { it >= 0 }?.let { levels[it] }
    val remaining = reward?.let { (it.totalModules - it.completedModules).coerceAtLeast(0) } ?: 0
    val rewardPct = reward?.let { if (it.totalModules > 0) it.completedModules * 100 / it.totalModules else 0 } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("MILESTONES", style = PW.over(9, 1.62f), color = PW.goldDeep)
            Spacer(Modifier.weight(1f))
            Text("$earned earned", style = PW.over(9, 0f), color = PW.ink3)
        }
        if (reward != null && remaining > 0) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PW.navyGrad).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Text(PW.badge[rewardIdx % PW.badge.size], style = TextStyle(fontSize = 22.sp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NEXT REWARD", style = PW.over(8, 1.28f), color = PW.goldLight)
                    Text("The “${pwShort(reward.title)}” badge", style = PW.t(13, FontWeight.Bold), color = Color.White, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Box(Modifier.weight(1f)) { PWBar(rewardPct, PW.goldGrad, Color.White.copy(alpha = 0.16f)) }
                        Spacer(Modifier.width(8.dp))
                        Text("$remaining to go", style = PW.t(9, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            levels.forEachIndexed { i, lvl -> RewardBadge(pwShort(lvl.title), PW.badge[i % PW.badge.size], lvl.status == LevelStatus.COMPLETED) }
        }
    }
}

@Composable
private fun RewardBadge(name: String, emoji: String, earned: Boolean) {
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(16.dp))
            .background(if (earned) Brush.linearGradient(listOf(PW.gold.copy(alpha = 0.14f), PW.gold.copy(alpha = 0.03f))) else Brush.linearGradient(listOf(PW.surface, PW.surface)))
            .border(1.dp, if (earned) PW.gold.copy(alpha = 0.33f) else PW.navy.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).background(if (earned) Color.White else PW.mutedBg).border(1.dp, if (earned) PW.gold.copy(alpha = 0.33f) else PW.border, RoundedCornerShape(999.dp)).alpha(if (earned) 1f else 0.7f), contentAlignment = Alignment.Center) {
            Text(emoji, style = TextStyle(fontSize = 20.sp))
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = PW.t(9, FontWeight.SemiBold), color = if (earned) PW.navy else PW.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        if (earned) Row { repeat(3) { Icon(Icons.Filled.Star, null, tint = PW.gold, modifier = Modifier.size(8.dp)) } }
        else Icon(Icons.Filled.Lock, null, tint = PW.ink3, modifier = Modifier.size(9.dp))
    }
}

@Composable
private fun SummitCard(overallPct: Int, levels: List<PathwayLevel>, firstName: String) {
    val reached = overallPct >= 100
    val levelsLeft = levels.count { it.status != LevelStatus.COMPLETED }
    // First time the summit is truly reached → a real celebration (once ever).
    if (reached) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            org.nuruplace.member.ui.components.CelebrationCenter.fire(
                org.nuruplace.member.ui.components.Moment(
                    key = "commissioned",
                    title = "You have been commissioned",
                    subtitle = "Sent to make disciples — Matthew 28:19",
                ),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("THE SUMMIT · WHERE THIS ROAD LEADS", style = PW.over(9, 1.62f), color = PW.goldDeep, modifier = Modifier.padding(horizontal = 4.dp))
        Box(
            Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp))
                // Reached earns a gold ceremonial ring; the road there stays quiet.
                .border(if (reached) 1.5.dp else 0.dp, if (reached) PW.gold.copy(alpha = 0.85f) else Color.Transparent, RoundedCornerShape(24.dp)),
        ) {
            // Real sending: a worship gathering, hands raised, JESUS over the stage —
            // visually verified (not picked blind from an ID).
            FitImage("https://images.unsplash.com/photo-1507692049790-de58290a4334?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080", modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x260A1628), Color(0x730A1628), Color(0xF20A1628)))))
            // status chip
            Row(
                Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(999.dp))
                    .background(if (reached) PW.gold else Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reached) Icon(Icons.Filled.Star, null, tint = PW.navy, modifier = Modifier.size(10.dp)) else Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (reached) "SENT" else "AHEAD OF YOU", style = PW.over(9, 1f), color = if (reached) PW.navy else Color.White)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Ceremonial seal — double gold ring, medal, no emoji.
                Box(
                    Modifier.size(58.dp).clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = if (reached) 0.14f else 0.07f))
                        .border(1.5.dp, PW.gold.copy(alpha = if (reached) 0.95f else 0.45f), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(999.dp))
                            .border(1.dp, PW.goldLight.copy(alpha = if (reached) 0.8f else 0.35f), RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.WorkspacePremium, null, tint = if (reached) PW.goldLight else PW.gold.copy(alpha = 0.75f), modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("COMMISSIONED", style = PW.over(10, 2.4f), color = PW.goldLight)
                // The actual charge, not a caption — the words carry the weight.
                Text(
                    "\u201CGo therefore and make disciples of all nations\u2026\u201D",
                    style = PW.serif(19, FontWeight.SemiBold, -0.2f).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 26.sp),
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text("MATTHEW 28:19", style = PW.over(9, 1.8f), color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(14.dp))
                // The road itself: one dot per level, gold when walked.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    levels.forEach { lv ->
                        val done = lv.status == LevelStatus.COMPLETED
                        Box(
                            Modifier.size(if (done) 9.dp else 7.dp).clip(RoundedCornerShape(999.dp))
                                .background(if (done) PW.gold else Color.White.copy(alpha = 0.28f))
                                .border(1.dp, if (done) PW.goldLight else Color.Transparent, RoundedCornerShape(999.dp)),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (reached) "$firstName, you have been commissioned \u2014 go." 
                    else if (levelsLeft == 1) "One level between you and being sent."
                    else "$levelsLeft levels between you and being sent.",
                    style = PW.t(11, FontWeight.Bold),
                    color = PW.goldLight,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
