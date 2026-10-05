// Pathway tab — a faithful port of the iOS PathwayView "PathwayHub" (Features/
// Pathway/PathwayView.swift): a light cream hero (streak · bell · progress ring ·
// greeting · active level · progress · navy Continue card), a horizontal journey
// rail of tappable level nodes ("You" on the member's level, "Next" on the one
// after), the selected level's real module trail (with a mid-trail "Pause &
// surrender" image) — folded into "20 of 20 modules done · Show" once the member
// is past learning it (EXPERIENCE.md §6.3, PathwayTrail.kt) — a "Walk with your
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary
import org.nuruplace.member.data.net.StateMessage
import org.nuruplace.member.ui.components.FailedState
import org.nuruplace.member.ui.components.FitImage
import org.nuruplace.member.ui.components.HomeSkeleton
import org.nuruplace.member.ui.components.NuruRefreshBox
import org.nuruplace.member.ui.components.pressScale
import org.nuruplace.member.ui.components.rememberHeld
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif

// Exact Figma palette (LevelsOverview.tsx) — local so the page is 1:1 with iOS.
private object PW {
    val navy = Color(0xFF0A2540)
    val navyDeep = Color(0xFF081C36)
    val gold = Color(0xFFC9A227)
    val goldLight = Color(0xFFE6C068)
    val goldDeep = Color(0xFFA8861C)
    val goldTint = Color(0xFFFFF4C7)
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
    // Type helpers — delegate to the canonical schema (ui/theme/TypeSchema.kt).
    fun over(size: Int, ker: Float = 1.4f) = nuruSans(size, FontWeight.Bold, ker)
    fun t(size: Int, w: FontWeight = FontWeight.Normal, ker: Float = 0f) = nuruSans(size, w, ker.takeIf { it != 0f })
    fun serif(size: Int, w: FontWeight = FontWeight.Medium, ker: Float = 0f) = nuruSerif(size, w, ker.takeIf { it != 0f })
}

private fun pwShort(t: String): String = t.split(" ").firstOrNull()?.replaceFirstChar { it.uppercase() } ?: ""

@Composable
fun PathwayHubScreen(
    me: MeResponse?,
    onOpenLevel: (Int) -> Unit,
    onOpenModule: (String) -> Unit,
    onOpenExam: (Int) -> Unit,
    onOpenMentor: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenWalk: () -> Unit,
    /** The header's bell — the inbox, as on every tab (§7.2 #4). */
    onOpenNotifications: () -> Unit = {},
) {
    // The hub's server data is HELD by the "pathway" destination
    // (rememberHeld): Back from a level, a module or the exam finds the same
    // hub at the same scroll, refreshed in place — no skeleton, no ring at
    // "0%" (EXPERIENCE.md §7.2 #8). The level picked on the rail is saved too.
    var summary by rememberHeld("PathwayHub.summary") { mutableStateOf<PathwaySummary?>(null) }
    var streak by rememberHeld("PathwayHub.streak") { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var modulesByLevel by rememberHeld("PathwayHub.modulesByLevel") { mutableStateOf<Map<Int, List<LevelModule>>>(emptyMap()) }
    // One tick per full load — pull-to-refresh bumps it, and every return to
    // the hub loads again (the tick starts at 0 while the data is held);
    // `hubLoaded` keeps the first-paint skeleton from returning once the
    // wire has answered.
    var refreshTick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var hubLoaded by rememberHeld("PathwayHub.hubLoaded") { mutableStateOf(false) }
    // A pathway that never loaded says so in the state language (§4) — it
    // used to render an empty hub ("Level 1 of 1", no trail) as if all was well.
    var loadError by rememberHeld("PathwayHub.loadError") { mutableStateOf<StateMessage?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(refreshTick) {
        try {
            summary = Net.client.api.pathway()
            loadError = null
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed refresh keeps what the member last saw.
            if (summary == null) loadError = ApiException.state(e, context)
        }
        streak = runCatching { Net.client.api.achievements().streak.current }.getOrElse { streak }
        // The trails already on screen refresh in place — a module just
        // finished reads done on Back — and one that fails keeps its rows.
        // (They were dropped and re-fetched behind skeleton rows.)
        for (n in modulesByLevel.keys.toList()) {
            runCatching { Net.client.api.levelModules(n).data }.onSuccess { modulesByLevel = modulesByLevel + (n to it) }
        }
        refreshing = false
        hubLoaded = true
    }

    val levels = summary?.levels ?: emptyList()
    // The journey (docs/EXPERIENCE.md §3): the level from the summary first,
    // then the full step once that level's trail has loaded.
    val currentNum = JourneyState.derive(summary)?.levelNumber
    val journey = JourneyState.derive(summary, currentNum?.let { modulesByLevel[it] })
    val active = levels.firstOrNull { it.levelNumber == currentNum } ?: levels.firstOrNull()
    val selNum = selected ?: active?.levelNumber
    val selLevel = levels.firstOrNull { it.levelNumber == selNum } ?: active

    // The current level's trail feeds the journey; the selected one feeds the list.
    LaunchedEffect(currentNum, selNum, modulesByLevel) {
        for (n in listOfNotNull(currentNum, selNum).distinct()) {
            if (modulesByLevel[n] == null) {
                val mods = runCatching { Net.client.api.levelModules(n).data }.getOrDefault(emptyList())
                modulesByLevel = modulesByLevel + (n to mods)
            }
        }
    }

    val firstName = me?.profile?.fullName?.substringBefore(' ') ?: "Friend"
    fun go(d: JourneyDestination) = when (d) {
        is JourneyDestination.Module -> onOpenModule(d.moduleId)
        is JourneyDestination.Exam -> onOpenExam(d.levelNumber)
        is JourneyDestination.Level -> onOpenLevel(d.levelNumber)
        JourneyDestination.Walk -> onOpenWalk()
    }

    // Home-screen Pathway widget (Glance) — mirrors iOS's intended
    // progress-ring/streak/next-module snapshot trigger points. Fires once
    // the active level is known, and again once its module trail resolves
    // the journey's next step. Writes only; the widget itself never
    // touches the network (docs/PARITY_AUDIT.md, widgets entry).
    val widgetContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(active?.levelNumber, active?.lessonsDone, journey?.next?.title, streak) {
        val lvl = active ?: return@LaunchedEffect
        org.nuruplace.member.widget.WidgetSnapshotStore.writePathway(
            context = widgetContext,
            currentLevel = summary?.currentLevel ?: lvl.levelNumber,
            levelTitle = lvl.title,
            // The widget's "X of Y modules" counts lessons too (§8.2 #4).
            completedModules = lvl.lessonsDone,
            totalModules = lvl.lessonCount,
            // The journey's next step — never a module already finished.
            nextModuleTitle = journey?.next?.title,
            streak = streak,
        )
    }

    NuruRefreshBox(refreshing = refreshing, onRefresh = { refreshing = true; refreshTick++ }) {
        Column(Modifier.fillMaxSize().background(PW.bg).verticalScroll(rememberScrollState())) {
            val failed = loadError?.takeIf { summary == null }
            if (failed != null) {
                FailedState(failed, onRetry = { refreshTick++ }, modifier = Modifier.padding(20.dp))
                return@Column
            }
            HubHeader(streak, active, levels, journey, ::go, onOpenNotifications)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // First paint before the pathway summary lands → hold the hub's
                // shape instead of an empty page.
                if (!hubLoaded && summary == null) {
                    HomeSkeleton()
                    return@Column
                }
                // Studying together, apart (Wave 2) — renders nothing when quiet.
                CellPresenceLine()
                JourneyRail(levels, selNum ?: -1, current = currentNum, onSelect = { selected = it }, onMap = onOpenMap)
                selLevel?.let { lv ->
                    SelectedModules(
                        level = lv,
                        modules = modulesByLevel[lv.levelNumber] ?: emptyList(),
                        loading = modulesByLevel[lv.levelNumber] == null,
                        journey = journey,
                        onOpenModule = onOpenModule,
                        onOpenExam = onOpenExam,
                    )
                }
                DisciplershipRow(onOpenMentor)
                WalkRow(onOpenWalk)
                Milestones(levels)
                SummitCard(journey, levels, firstName)
                Spacer(Modifier.height(Spacing.tabBarSpace))
            }
        }
    }
}

// ─────────────────────────── Header ───────────────────────────

/** The tab's one header (EXPERIENCE.md §8.1 rule 2, §8.2 #1): the gold
 *  "PATHWAY" kicker, the level's title, one line — "Level 1 of 6 · 20 of 20
 *  modules" — and the bell at the right. The greeting belongs to Home alone
 *  (it used to open this header too, above the title). */
@Composable
private fun HubHeader(
    streak: Int,
    active: PathwayLevel?,
    levels: List<PathwayLevel>,
    journey: Journey?,
    onGo: (JourneyDestination) -> Unit,
    onBell: () -> Unit,
) {
    val idx = levels.indexOfFirst { it.levelNumber == active?.levelNumber }.coerceAtLeast(0)
    // Lessons, never the exam (§8.2 #4): production counts the exam
    // container in total_modules, and the bar read "20/21" beside "20 of 20
    // modules done".
    val pct = active?.let { if (it.lessonCount > 0) it.lessonsDone * 100 / it.lessonCount else 0 } ?: 0
    // Modules still to read — only while learning ("1 module left" beside
    // "Exam ready" would contradict it).
    val remaining = active?.takeIf { journey?.stage == JourneyStage.LEARNING }
        ?.let { (it.lessonCount - it.lessonsDone).coerceAtLeast(0) } ?: 0
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(PW.headerGrad)
            .padding(horizontal = 20.dp).padding(top = Spacing.md, bottom = 20.dp),
    ) {
        // top bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PATHWAY", style = NuruType.kicker, color = Nuru.eyebrow)
            if (streak > 0) {
                Spacer(Modifier.width(Spacing.sm))
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White).border(1.dp, PW.border, RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = PW.eyebrow, modifier = Modifier.size(9.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$streak-day streak", style = PW.over(11, 0f), color = PW.eyebrow)
                }
            }
            Spacer(Modifier.weight(1f))
            // The one bell (EXPERIENCE.md §7.2 #4) — it used to open nothing,
            // under a dot that was always there.
            org.nuruplace.member.ui.components.InboxBell(
                onClick = onBell, size = 36.dp, shape = RoundedCornerShape(999.dp),
                container = Color.White, border = PW.border, tint = PW.navy, iconSize = 17.dp, dotInset = 5.dp,
            )
            Spacer(Modifier.width(Spacing.sm))
            // Journey progress, counted in levels (§3) — not a share of
            // published modules, which read 100% at Level 1 of 6. Empty, with
            // no number, until the journey is known (§7 rule 5).
            HubRing(journey?.percent)
        }
        Text(active?.title ?: "Your pathway", style = PW.serif(26, FontWeight.SemiBold, -0.52f), color = PW.navy, modifier = Modifier.padding(top = 12.dp))
        // One Inter line: where the member is on the road, and how far through
        // the level — lessons, the exam a step of its own (§8.2 #4).
        Text(
            journey?.headerLine ?: "Level ${idx + 1} of ${levels.size.coerceAtLeast(1)}",
            style = nuruSans(13), color = PW.ink2, modifier = Modifier.padding(top = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            Box(Modifier.weight(1f)) { PWBar(pct, PW.goldGrad, PW.navy.copy(alpha = 0.10f)) }
            Spacer(Modifier.width(Spacing.sm))
            Text("${active?.lessonsDone ?: 0}/${active?.lessonCount ?: 0}", style = PW.t(11, FontWeight.SemiBold), color = PW.ink2)
        }
        if (remaining > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = PW.eyebrow, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (remaining == 1) "Just 1 module left to level up 🎉" else "Only $remaining modules to complete this level", style = PW.t(11, FontWeight.SemiBold), color = PW.eyebrow)
            }
        }
        // The member's next step (§3) — the same words Home's continue card says.
        journey?.let { NextStepCard(it, onGo) }
    }
}

/** The hero's navy CTA: the journey's next step — its title, its line and
 *  its one action. A step with no action (the exam not yet open) is a quiet
 *  card, not a button. */
@Composable
private fun NextStepCard(journey: Journey, onGo: (JourneyDestination) -> Unit) {
    val step = journey.next
    val action = step.action
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(PW.navyGrad)
            .clickable(enabled = action != null) { action?.let { onGo(it.destination) } }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(PW.gold), contentAlignment = Alignment.Center) {
            Icon(
                when (journey.stage) {
                    JourneyStage.LEARNING -> Icons.Filled.PlayArrow
                    JourneyStage.EXAM_READY -> Icons.Filled.EmojiEvents
                    JourneyStage.EXAM_SOON -> Icons.Filled.Schedule
                    JourneyStage.AWAITING_USHER -> Icons.Filled.Flag
                    JourneyStage.FINISHED -> Icons.Filled.WorkspacePremium
                },
                null, tint = PW.navy, modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(journey.kicker.uppercase(), style = PW.over(11), color = PW.goldLight, maxLines = 1)
            Text(step.title, style = PW.t(14, FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(step.line, style = PW.t(11), color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(top = 2.dp))
            if (action != null) {
                Row(
                    Modifier.padding(top = 8.dp).clip(RoundedCornerShape(999.dp)).background(PW.gold)
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(action.label, style = PW.t(11, FontWeight.Bold), color = PW.navy, maxLines = 1)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PW.navy, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun HubRing(pct: Int?) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(40.dp)) {
            val sw = 3.dp.toPx(); val inset = sw / 2
            val arc = Size(size.width - sw, size.height - sw)
            drawArc(PW.navy.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(sw))
            if (pct != null && pct > 0) {
                drawArc(PW.gold, -90f, 360f * (pct.coerceIn(0, 100) / 100f), false, Offset(inset, inset), arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        pct?.let { Text("$it%", style = PW.over(11, 0f), color = PW.eyebrow) }
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
private fun JourneyRail(levels: List<PathwayLevel>, selected: Int, current: Int?, onSelect: (Int) -> Unit, onMap: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("THE JOURNEY · ${levels.size} LEVELS", style = PW.over(11), color = PW.goldDeep)
            Spacer(Modifier.weight(1f))
            Text("Map view", style = PW.over(11, 0f), color = PW.gold, modifier = Modifier.clickable { onMap() })
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp)) {
            // "You" on the member's own level — the journey's, whatever its
            // status (walking it, every module done, its exam passed) — and
            // "Next" on the level after it while still locked: a gold ring so
            // the rail reads as a path with a visible next step (§6.3, iOS).
            // It used to key on status "active" alone, so a member at their
            // exam saw neither mark.
            val marks = railMarks(levels, current)
            levels.forEachIndexed { i, lvl ->
                JourneyNode(lvl, i + 1, lvl.levelNumber == selected, isCurrent = i == marks.you, upNext = i == marks.next) { onSelect(lvl.levelNumber) }
                if (i < levels.size - 1) {
                    // Uncompleted connectors at 28% navy — 12% vanished on cream.
                    Box(Modifier.padding(top = 44.dp).width(28.dp).height(3.dp).clip(RoundedCornerShape(999.dp)).background(if (lvl.walked) PW.gold else PW.navy.copy(alpha = 0.28f)))
                }
            }
        }
    }
}

@Composable
private fun JourneyNode(
    level: PathwayLevel,
    number: Int,
    selected: Boolean,
    /** The member's own level — "▾ You" and the navy ring. */
    isCurrent: Boolean = false,
    /** The locked level after the member's — "▾ Next" and a gold ring. */
    upNext: Boolean = false,
    onTap: () -> Unit,
) {
    // A passed exam awaiting the usher is walked ground, never a lock.
    val done = level.walked
    val active = isCurrent
    val locked = !done && !active
    Column(Modifier.width(76.dp).clickable { onTap() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when { active -> "▾ You"; upNext -> "▾ Next"; else -> " " },
            style = PW.over(11, 0.7f),
            color = when { active -> PW.gold; upNext -> PW.goldDeep; else -> Color.Transparent },
            modifier = Modifier.height(16.dp),
        )
        // The level NUMBER never leaves the circle — completion becomes a corner
        // check-seal; locked levels keep their number with a lock-seal. Locked
        // circles are surface + a navy hairline (the flat #EEF1F5 fill and its
        // grey-blue numeral were invisible on cream); the up-next one wears gold.
        Box(contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(54.dp).clip(RoundedCornerShape(999.dp)).border(2.dp, PW.gold, RoundedCornerShape(999.dp)))
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (done || active) PW.goldGrad else Brush.linearGradient(listOf(PW.surface, PW.surface)))
                        .then(
                            when {
                                active -> Modifier.border(2.dp, PW.navy, RoundedCornerShape(999.dp))
                                upNext -> Modifier.border(2.dp, PW.gold, RoundedCornerShape(999.dp))
                                locked -> Modifier.border(1.5.dp, PW.navy.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                                else -> Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$number", style = PW.t(15, FontWeight.Bold), color = if (done || active) PW.navy else PW.navy.copy(alpha = 0.75f))
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
                            .clip(RoundedCornerShape(999.dp)).background(PW.goldTint)
                            .border(1.5.dp, Color.White, RoundedCornerShape(999.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Lock, null, tint = PW.goldDeep, modifier = Modifier.size(9.dp)) }
                }
            }
        }
        Text(
            pwShort(level.title),
            style = PW.t(11, if (active) FontWeight.Bold else FontWeight.Medium),
            color = when { active -> PW.navy; upNext -> PW.goldDeep; else -> PW.ink2 },
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ─────────────────────────── Selected level's module trail ───────────────────────────

@Composable
private fun SelectedModules(
    level: PathwayLevel,
    modules: List<LevelModule>,
    loading: Boolean,
    journey: Journey?,
    onOpenModule: (String) -> Unit,
    onOpenExam: (Int) -> Unit,
) {
    val ordered = remember(modules) {
        fun rank(m: LevelModule) = if (m.status == ModuleStatus.COMPLETED) 0 else if (m.status == ModuleStatus.NEXT) 1 else 2
        modules.sortedWith(compareBy({ rank(it) }, { it.moduleSequenceNumber }))
    }
    // When the level owns an exam container it IS the exam entry (a visible,
    // locked-until-ready row). A level with none gets the journey's waiting
    // step at the foot once its exam is passed — the member's own level only
    // (§3). The open-exam gate that stood there is gone: it showed only while
    // the hero above already showed the exam step (§6.3).
    val hasExamModule = ordered.any { it.isExam }
    val examPassed = journey?.takeIf { !hasExamModule && ordered.isNotEmpty() && it.levelNumber == level.levelNumber }
        ?.stage == JourneyStage.AWAITING_USHER
    // §6.3: the trail's own exam row is not shown again while the hero shows
    // the exam step; and once the member is past learning this level, its
    // list folds into one row — "20 of 20 modules done · Show" — that expands.
    val shown = if (examRowHidden(journey, level.levelNumber)) ordered.filter { !it.isExam } else ordered
    val folds = trailFolds(journey, level.levelNumber, ordered)
    // "Continue →" goes where the list's open row goes — never to an exam
    // the hero already offers, nor to one with nothing to ask yet (§7.2 #1).
    val resume = shown.firstOrNull { it.status == ModuleStatus.NEXT && !it.examOpensSoon }
    var expanded by rememberSaveable(level.levelNumber) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(level.title.uppercase(), style = PW.over(11), color = PW.goldDeep, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // Lessons, as the folded row and the header count them (§8.2 #4).
                Text("${level.lessonsDone} of ${level.lessonCount} done", style = PW.t(11), color = PW.ink2)
            }
            resume?.let { r -> Text(ModuleWords.trailLink(level.lessonsDone), style = PW.over(11, 0f), color = PW.gold, modifier = Modifier.clickable { if (r.isExam) onOpenExam(level.levelNumber) else onOpenModule(r.moduleId) }) }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, PW.border, RoundedCornerShape(22.dp)),
        ) {
            when {
                loading -> repeat(3) { ModuleSkeletonRow() }
                ordered.isEmpty() -> Text("Modules open as you progress.", style = PW.t(13), color = PW.ink3, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                else -> {
                    if (folds) FoldedTrailRow(foldedTrailLine(ordered), expanded) { expanded = !expanded }
                    if (!folds || expanded) {
                        if (folds) Box(Modifier.fillMaxWidth().height(1.dp).background(PW.border))
                        shown.forEachIndexed { i, m ->
                            ModuleRow(m, last = (i == shown.size - 1) && !examPassed) {
                                if (m.status != ModuleStatus.LOCKED && !m.examOpensSoon) {
                                    if (m.isExam) onOpenExam(level.levelNumber) else onOpenModule(m.moduleId)
                                }
                            }
                            if (i == 3 && shown.size > 4) SurrenderFigure()
                        }
                        if (examPassed) journey?.next?.let { step -> ExamPassedRow(step) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleRow(m: LevelModule, last: Boolean, onTap: () -> Unit) {
    val done = m.status == ModuleStatus.COMPLETED
    // An exam with nothing to ask yet is not open: "Opens soon", no tap (§7.2 #1).
    val soon = m.examOpensSoon
    val active = m.status == ModuleStatus.NEXT && !soon
    val locked = m.status == ModuleStatus.LOCKED
    val exam = m.isExam
    // "Up next" and "Start", never "In progress"/"Resume" — `next` is the next
    // one to do, not progress (Cycle 3's closing walk, B2).
    val caption = ModuleWords.trailCaption(m)
    Column {
        Row(
            Modifier.fillMaxWidth().then(if (soon) Modifier else Modifier.pressScale(0.98f))
                .background(if (active) PW.gold.copy(alpha = 0.05f) else if (exam) PW.gold.copy(alpha = 0.03f) else Color.Transparent)
                .clickable(enabled = !soon) { onTap() }.padding(horizontal = 16.dp, vertical = 12.dp),
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
                // A module's title wraps to two lines — never cut (§8.1 rule 9).
                Text(m.title, style = PW.t(13, if (active || exam) FontWeight.Bold else FontWeight.Medium), color = if (locked && !exam) PW.ink2 else PW.navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(caption, style = PW.t(11, if (active || exam) FontWeight.Bold else FontWeight.Medium), color = if (active || (exam && !done)) PW.goldDeep else PW.ink3)
            }
            when {
                active -> Box(Modifier.clip(RoundedCornerShape(999.dp)).background(PW.navy).padding(horizontal = 10.dp, vertical = 5.dp)) { Text(ModuleWords.trailAction(m), style = PW.over(11, 0f), color = PW.gold) }
                soon -> Box(Modifier.clip(RoundedCornerShape(999.dp)).background(PW.gold.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 5.dp)) { Text("Opens soon", style = PW.over(11, 0f), color = PW.goldDeep) }
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

/** A finished level's trail, folded (§6.3): "20 of 20 modules done · Show"
 *  — the whole row opens the list, and folds it again ("· Hide"). */
@Composable
private fun FoldedTrailRow(line: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Hide the modules" else "Show the modules") { onToggle() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(PW.gold.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = PW.goldDeep, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = PW.navy)) { append("$line · ") }
                withStyle(SpanStyle(color = PW.gold, fontWeight = FontWeight.Bold)) { append(if (expanded) "Hide" else "Show") }
            },
            style = PW.t(13, FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )
    }
}

/** Where the gate stood, once the exam is passed: the journey's waiting step
 *  ("Level 2 is next" · "You passed the Level 1 exam…") — nothing to tap; the
 *  member's leader opens the next level. */
@Composable
private fun ExamPassedRow(step: JourneyStep) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(PW.gold.copy(alpha = 0.35f)))
        Row(
            Modifier.fillMaxWidth().background(PW.gold.copy(alpha = 0.06f)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(PW.gold.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = PW.goldDeep, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(step.title, style = PW.t(13, FontWeight.Bold), color = PW.navy, maxLines = 1)
                Text(step.line, style = PW.t(11, FontWeight.SemiBold), color = PW.goldDeep)
            }
        }
    }
}

@Composable
private fun SurrenderFigure() {
    Box(Modifier.fillMaxWidth().height(224.dp)) {
        FitImage("https://images.unsplash.com/photo-1510590337019-5ef8d3d32116?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&q=80&w=1080", modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x26081424), Color(0x8C081424), Color(0xE6081424)))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text("PAUSE & SURRENDER", style = PW.over(11), color = PW.goldLight)
            Text("“Offer yourselves as a living sacrifice, holy and pleasing to God.”", style = PW.serif(12, FontWeight.Medium), color = Color.White)
            Text("Romans 12:1 · Surrender to His Word", style = PW.t(11, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ─────────────────────────── Discipleship + Milestones + Summit ───────────────────────────

@Composable
private fun DisciplershipRow(onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressScale().clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, PW.border, RoundedCornerShape(20.dp)).clickable { onTap() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PW.goldGrad), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.VolunteerActivism, null, tint = PW.navy, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("WALK WITH YOUR DISCIPLER", style = PW.over(11), color = PW.goldDeep)
            Text("Your Discipleship Hub", style = PW.t(14, FontWeight.SemiBold), color = PW.navy, maxLines = 1)
            Text("Message, feedback & meeting notes", style = PW.t(11), color = PW.ink2, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFB5BDC9), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Milestones(levels: List<PathwayLevel>) {
    val earned = levels.count { it.walked }
    val rewardIdx = levels.indexOfFirst { !it.walked }
    val reward = rewardIdx.takeIf { it >= 0 }?.let { levels[it] }
    // Lessons to go — the exam is a step of its own, never "1 to go" (§8.2 #4).
    val remaining = reward?.let { (it.lessonCount - it.lessonsDone).coerceAtLeast(0) } ?: 0
    val rewardPct = reward?.let { if (it.lessonCount > 0) it.lessonsDone * 100 / it.lessonCount else 0 } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("MILESTONES", style = PW.over(11), color = PW.goldDeep)
            Spacer(Modifier.weight(1f))
            Text("$earned earned", style = PW.over(11, 0f), color = PW.ink3)
        }
        if (reward != null && remaining > 0) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PW.navyGrad).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Text(PW.badge[rewardIdx % PW.badge.size], style = nuruSans(22))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NEXT REWARD", style = PW.over(11), color = PW.goldLight)
                    Text("The “${pwShort(reward.title)}” badge", style = PW.t(13, FontWeight.Bold), color = Color.White, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Box(Modifier.weight(1f)) { PWBar(rewardPct, PW.goldGrad, Color.White.copy(alpha = 0.16f)) }
                        Spacer(Modifier.width(8.dp))
                        Text("$remaining to go", style = PW.t(11, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            levels.forEachIndexed { i, lvl -> RewardBadge(pwShort(lvl.title), PW.badge[i % PW.badge.size], lvl.walked) }
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
            Text(emoji, style = nuruSans(22))
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = PW.t(11, FontWeight.SemiBold), color = if (earned) PW.navy else PW.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        if (earned) Row { repeat(3) { Icon(Icons.Filled.Star, null, tint = PW.gold, modifier = Modifier.size(8.dp)) } }
        else Icon(Icons.Filled.Lock, null, tint = PW.ink3, modifier = Modifier.size(9.dp))
    }
}

@Composable
private fun SummitCard(journey: Journey?, levels: List<PathwayLevel>, firstName: String) {
    // Only at the journey's end — the LAST level's exam passed (§3). It used
    // to be "every published module done", which commissioned Level 1
    // finishers while Levels 2–6 had no modules yet.
    val reached = journey?.summitReached == true
    val levelsLeft = levels.count { !it.walked }
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
        Text("THE SUMMIT · WHERE THIS ROAD LEADS", style = PW.over(11), color = PW.goldDeep, modifier = Modifier.padding(horizontal = 4.dp))
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
                Text(if (reached) "SENT" else "AHEAD OF YOU", style = PW.over(11, 1f), color = if (reached) PW.navy else Color.White)
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
                Text("COMMISSIONED", style = PW.over(11, 2.4f), color = PW.goldLight)
                // The actual charge, not a caption — the words carry the weight.
                Text(
                    "\u201CGo therefore and make disciples of all nations\u2026\u201D",
                    style = PW.serif(18, FontWeight.SemiBold, -0.2f).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 25.sp),
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text("MATTHEW 28:19", style = PW.over(11, 1.8f), color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(14.dp))
                // The road itself: one dot per level, gold when walked.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    levels.forEach { lv ->
                        val done = reached || lv.walked
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

// Wave 3 — the door into Your Walk ("EVERY STEP, REMEMBERED").
@Composable
private fun WalkRow(onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressScale().clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(1.dp, PW.border, RoundedCornerShape(20.dp))
            .clickable { onTap() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(PW.navy, Color(0xFF1B3A5C)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Flag, contentDescription = null, tint = PW.gold, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("EVERY STEP, REMEMBERED", style = NuruType.kicker, color = PW.goldDeep)
            Text("Your Walk", style = NuruType.body, color = PW.navy, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("Your whole journey on one gold thread", style = NuruType.micro, color = PW.ink2, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = PW.ink3, modifier = Modifier.size(18.dp))
    }
}
