// Reading plans — the discovery/browse home for the Plans tab. A pixel-faithful
// port of the iOS ReadingPlansView (ReadingPlansView.swift) + its screen-specific
// cards (PLStreakStrip / PLContinueRow / PLPlanCard / PLPlanTile from
// ReadingPlanCards.swift): cream header with bell, search, a streak/reward strip,
// continue-reading rows, a "Plan of the day" hero, topic chips and collection
// carousels — with a 2-col search grid when a query/topic filter is active.
// Shared PL palette + PLCover/PLDaysBadge/plInter/plSerif/PLOverline live in
// PlansShared.kt. Plan detail lives in its own file.
package org.nuruplace.member.feature.grow

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ReadingPlanRow
import org.nuruplace.member.ui.theme.Spacing
import java.util.Calendar

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReadingPlansScreen(onOpenPlan: (String) -> Unit, onOpenNotifications: () -> Unit = {}) {
    var plans by remember { mutableStateOf<List<ReadingPlanRow>>(emptyList()) }
    var streak by remember { mutableStateOf(0) }
    var todayWordDone by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Plans is the blocking load; achievements + rhythm are non-fatal accents.
        plans = runCatching { Net.client.api.plans().data }.getOrDefault(emptyList())
        streak = runCatching { Net.client.api.achievements().streak.current }.getOrDefault(0)
        todayWordDone = runCatching { Net.client.api.rhythmToday().word }.getOrDefault(false)
        loading = false
    }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("all") }

    val q = query.trim().lowercase()
    val searching = q.isNotEmpty() || category != "all"

    val filtered = plans.filter { p ->
        (category == "all" || p.category == category) &&
            (q.isEmpty() || p.title.lowercase().contains(q) || (p.category ?: "").lowercase().contains(q))
    }
    val continueReading = plans.filter { it.enrolled && it.completedAt == null }
    val planOfDay = plans.firstOrNull { !it.enrolled } ?: plans.firstOrNull()
    val categories = buildList {
        val seen = HashSet<String>()
        for (p in plans) {
            val c = p.category
            if (!c.isNullOrEmpty() && seen.add(c)) add(c)
        }
    }
    val collections = buildList {
        if (plans.isNotEmpty()) add(Triple("featured", "Featured for you", plans.take(8)))
        val short = plans.filter { it.dayCount <= 7 }
        if (short.isNotEmpty()) add(Triple("short", "Short reads · 7 days or less", short))
        // Mid-length (8–13 days) — most study plans are 10-day, so without this
        // bucket they'd fall between "short" and "long" and never appear in browse.
        val mid = plans.filter { it.dayCount in 8..13 }
        if (mid.isNotEmpty()) add(Triple("mid", "Mid-length journeys · about 10 days", mid))
        val long = plans.filter { it.dayCount >= 14 }
        if (long.isNotEmpty()) add(Triple("long", "Longer journeys · 2 weeks and up", long))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PL.cream)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Header(
            query = query,
            onQuery = { query = it },
            onOpenNotifications = onOpenNotifications,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = Spacing.tabBarSpace + 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (loading && plans.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PL.gold)
                }
            } else {
                if (!searching) StreakStrip(count = streak, todayDone = todayWordDone)
                if (!searching && continueReading.isNotEmpty()) {
                    ContinueSection(plans = continueReading, onOpenPlan = onOpenPlan)
                }
                if (!searching && planOfDay != null) {
                    PlanOfDayCard(plan = planOfDay, onOpenPlan = onOpenPlan)
                }
                CategoriesSection(
                    categories = categories,
                    selected = category,
                    onSelect = { c -> category = if (category == c) "all" else c },
                )
                if (searching) {
                    FilteredResults(category = category, plans = filtered, onOpenPlan = onOpenPlan)
                } else {
                    CollectionsSections(collections = collections, onOpenPlan = onOpenPlan)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header — cream gradient, overline + title + subtitle + bell, then search
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Header(query: String, onQuery: (String) -> Unit, onOpenNotifications: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(PL.headerGrad)
            // The Scaffold already insets content below the status bar, so only a
            // small top pad is needed here (not the iOS 64pt island clearance).
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                PLOverline("PLANS", color = PL.catText, kerning = 1.8f)
                Text(
                    "Grow in the Word",
                    style = plSerif(26, FontWeight.SemiBold, -0.52f),
                    color = PL.navy,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "A little every day — with the whole family of God.",
                    style = plInter(12),
                    color = PL.ink2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            BellButton(onClick = onOpenNotifications)
        }
        Spacer(Modifier.height(16.dp))
        SearchBar(query = query, onQuery = onQuery)
    }
}

@Composable
private fun BellButton(onClick: () -> Unit) {
    Box(Modifier.size(40.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, PL.border, RoundedCornerShape(16.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = PL.navy, modifier = Modifier.size(18.dp))
        }
        // Gold unread dot, 8dp, inset 8dp from the top-trailing corner.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(PL.gold),
        )
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, PL.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = PL.ink3, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Search plans, topics, books…", style = plInter(14), color = PL.ink3)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = plInter(14).copy(color = PL.navy),
                cursorBrush = SolidColor(PL.gold),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = PL.ink3,
                modifier = Modifier.size(15.dp).clickable { onQuery("") },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Streak strip
// ─────────────────────────────────────────────────────────────────────────────

private val WEEK = listOf("S", "M", "T", "W", "T", "F", "S")
private const val STREAK_GOAL = 7

@Composable
private fun StreakStrip(count: Int, todayDone: Boolean) {
    val todayIdx = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // Sun=0
    val toReward = (STREAK_GOAL - count).coerceAtLeast(0)
    val pct = (count.toFloat() / STREAK_GOAL).coerceIn(0f, 1f)

    fun isDone(i: Int): Boolean {
        if (i == todayIdx) return todayDone
        if (i >= todayIdx) return false
        val back = (if (todayDone) count - 1 else count).coerceAtLeast(0)
        return todayIdx - i <= back
    }

    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, PL.border, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Flame tile
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(PL.gold.copy(alpha = 0.15f), PL.gold.copy(alpha = 0.05f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = PL.gold, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "$count-day streak",
                    style = plInter(14, FontWeight.Bold, -0.14f),
                    color = PL.navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (count > 0) "Read today to keep it alive 🔥" else "Read today to start your streak 🔥",
                    style = plInter(12),
                    color = PL.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) WeekDot(label = WEEK[i], done = isDone(i), today = i == todayIdx)
            }
        }
        // Progress bar + gift chip
        Row(
            Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(PL.navy.copy(alpha = 0.07f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = pct.coerceAtLeast(0.06f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(PL.goldProgressGrad),
                )
            }
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = PL.catText, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (toReward == 0) "Reward ready!" else "$toReward day${if (toReward == 1) "" else "s"} to a badge",
                    style = plInter(10, FontWeight.Bold),
                    color = PL.catText,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WeekDot(label: String, done: Boolean, today: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = plInter(8, FontWeight.Bold), color = PL.ink3)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when {
                done -> {
                    Box(Modifier.matchParentSize().clip(RoundedCornerShape(999.dp)).background(PL.gold))
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                }
                today -> {
                    Box(
                        Modifier.matchParentSize()
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                            .border(1.5.dp, PL.gold, RoundedCornerShape(999.dp)),
                    )
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(999.dp)).background(PL.gold))
                }
                else -> Box(Modifier.matchParentSize().clip(RoundedCornerShape(999.dp)).background(PL.navy.copy(alpha = 0.06f)))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Continue reading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContinueSection(plans: List<ReadingPlanRow>, onOpenPlan: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PLOverline("CONTINUE READING")
        for (plan in plans) ContinueRow(plan = plan, onOpenPlan = onOpenPlan)
    }
}

@Composable
private fun ContinueRow(plan: ReadingPlanRow, onOpenPlan: (String) -> Unit) {
    val total = plan.dayCount.coerceAtLeast(1)
    val day = plan.currentDay ?: ((plan.completedDays?.size ?: 0) + 1)
    val pct = (day.toFloat() / total).coerceIn(0f, 1f)

    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, PL.border, shape)
            .clickable { onOpenPlan(plan.planId) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PLCover(url = plan.imageUrl, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                plan.title,
                style = plInter(14, FontWeight.Bold, -0.14f),
                color = PL.navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Today · ${plan.subtitle ?: "Day $day of $total"}",
                style = plInter(12),
                color = PL.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(PL.navy.copy(alpha = 0.08f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = pct.coerceAtLeast(0.05f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(PL.gold),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("Day $day/$total", style = plInter(9, FontWeight.SemiBold), color = PL.ink2)
            }
        }
        Spacer(Modifier.width(12.dp))
        // Gold play button
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(PL.gold.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PL.gold, modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Plan of the day (hero)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlanOfDayCard(plan: ReadingPlanRow, onOpenPlan: (String) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(192.dp)
            .clip(shape)
            .clickable { onOpenPlan(plan.planId) },
    ) {
        PLCover(url = plan.imageUrl, modifier = Modifier.matchParentSize())
        // Top→bottom scrim (light at top, deep at the bottom for text legibility).
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x40081424),
                            Color(0x59081424),
                            Color(0xE6081424),
                        ),
                    ),
                ),
        )
        // Shimmering "PLAN OF THE DAY" pill, top-leading, 14dp inset.
        Box(Modifier.align(Alignment.TopStart).padding(14.dp)) { PlanOfDayPill() }
        // Title + meta, bottom-leading.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                plan.title,
                style = plSerif(22, FontWeight.SemiBold, -0.22f),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("${plan.dayCount} days", style = plInter(11), color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun PlanOfDayPill() {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PL.gold)
            .clipToBounds(),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PL.navy, modifier = Modifier.size(9.dp))
            Spacer(Modifier.width(4.dp))
            Text("PLAN OF THE DAY", style = plInter(9, FontWeight.Bold, 1.26f), color = PL.navy)
        }
        // A slow white sweep across the pill — the iOS PLShimmer.
        ShimmerSweep(Modifier.matchParentSize())
    }
}

@Composable
private fun ShimmerSweep(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -0.8f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    Box(
        modifier.background(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent),
                start = Offset(x * 400f, 0f),
                end = Offset(x * 400f + 120f, 0f),
            ),
        ),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Categories (browse by topic)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoriesSection(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PLOverline("BROWSE BY TOPIC")
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (c in listOf("all") + categories) {
                TopicChip(label = if (c == "all") "All plans" else c, selected = selected == c) { onSelect(c) }
            }
        }
    }
}

@Composable
private fun TopicChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(PL.navy) else Modifier.background(Color.White).border(1.dp, PL.border, shape))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = plInter(12, if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) Color.White else PL.ink2,
            maxLines = 1,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Collections (browse) — horizontal carousels of portrait cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollectionsSections(
    collections: List<Triple<String, String, List<ReadingPlanRow>>>,
    onOpenPlan: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        for ((_, label, colPlans) in collections) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = plSerif(13, FontWeight.SemiBold, -0.13f),
                        color = PL.navy,
                        modifier = Modifier.weight(1f),
                    )
                    Text("See all", style = plInter(11, FontWeight.Bold), color = PL.gold)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (plan in colPlans) PlanCard(plan = plan, onOpenPlan = onOpenPlan)
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: ReadingPlanRow, onOpenPlan: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .width(150.dp)
            .height(200.dp)
            .clip(shape)
            .clickable { onOpenPlan(plan.planId) },
    ) {
        PLCover(url = plan.imageUrl, modifier = Modifier.matchParentSize())
        // iOS scrim: #081424@5% at ~40% down → #081424@85% at the bottom.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.4f to Color(0x0D081424),
                        1.0f to Color(0xD9081424),
                    ),
                ),
        )
        // "N DAYS" badge, top-leading (PLDaysBadge already carries its 8dp inset).
        PLDaysBadge(days = plan.dayCount, modifier = Modifier.align(Alignment.TopStart))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                plan.title,
                style = plSerif(13, FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            plan.category?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.uppercase(),
                    style = plInter(9, FontWeight.Bold, 0.9f),
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filtered results (search) — 2-col grid of tiles + count
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilteredResults(category: String, plans: List<ReadingPlanRow>, onOpenPlan: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PLOverline(if (category == "all") "RESULTS" else category, modifier = Modifier.weight(1f))
            Text(
                "${plans.size} plan${if (plans.size == 1) "" else "s"}",
                style = plInter(10, FontWeight.SemiBold),
                color = PL.ink3,
            )
        }
        if (plans.isEmpty()) {
            val shape = RoundedCornerShape(22.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color.White)
                    .border(1.dp, PL.border, shape)
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(PL.gold.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = PL.gold, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("No plans found", style = plInter(13, FontWeight.SemiBold), color = PL.navy)
            }
        } else {
            // Two-column grid, laid out as rows so the parent stays a plain scroll
            // Column (no nested-vertical-scroll conflict from a LazyVerticalGrid).
            val rows = plans.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (pair in rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlanTile(plan = pair[0], onOpenPlan = onOpenPlan, modifier = Modifier.weight(1f))
                        if (pair.size > 1) {
                            PlanTile(plan = pair[1], onOpenPlan = onOpenPlan, modifier = Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanTile(plan: ReadingPlanRow, onOpenPlan: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, PL.border, shape)
            .clickable { onOpenPlan(plan.planId) },
    ) {
        // 16:10 cover with the "N DAYS" badge in its top-leading corner.
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            PLCover(url = plan.imageUrl, modifier = Modifier.matchParentSize())
            PLDaysBadge(days = plan.dayCount, modifier = Modifier.align(Alignment.TopStart))
        }
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                plan.title,
                style = plInter(12, FontWeight.Bold),
                color = PL.navy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            plan.category?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.uppercase(),
                    style = plInter(9, FontWeight.Bold, 0.9f),
                    color = PL.catText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
