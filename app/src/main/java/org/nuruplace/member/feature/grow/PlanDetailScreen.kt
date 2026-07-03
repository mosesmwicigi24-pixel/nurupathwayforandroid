// Reading-plan DETAIL screen — pixel-faithful port of the iOS `PlanDetailView`
// (ReadingPlansView.swift 376–648) plus the two detail-only cards `PLFinishEarnCard`
// and `PLDetailDayRow` (ReadingPlanCards.swift 340–419). A ZStack on PL.cream: a
// vertical scroll (hero + about + "what you'll read" + finish-&-earn + nudge) with a
// pinned bottom CTA bar (gold "Start/Continue/Review" + a white "Invite" no-op).
// Server owns enrolment + completion (§1.1); the heart + expand toggles are LOCAL.
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ReadingPlanDay
import org.nuruplace.member.data.net.ReadingPlanDetail
import org.nuruplace.member.ui.theme.Spacing

// Vertical scrim over the hero cover (iOS #081424 @ 40% / 10% / 92%, top→bottom).
private val heroScrim = Brush.verticalGradient(
    listOf(Color(0x66081424), Color(0x1A081424), Color(0xEB081424)),
)

@Composable
fun PlanDetailScreen(planId: String, onBack: () -> Unit, onOpenDay: (Int) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<ReadingPlanDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        runCatching { Net.client.api.plan(planId) }
            .onSuccess { detail = it; error = null }
            .onFailure { error = "Couldn't load this plan." }
        loading = false
    }

    LaunchedEffect(planId) { reload() }

    Box(Modifier.fillMaxSize().background(PL.cream)) {
        val d = detail
        when {
            d != null -> PlanDetailContent(
                d = d,
                onBack = onBack,
                onOpenDay = onOpenDay,
                onStart = { scope.launch { runCatching { Net.client.api.startPlan(planId) }; reload() } },
            )
            loading -> CircularProgressIndicator(
                color = PL.gold,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> Text(
                error ?: "Couldn't load this plan.",
                style = plInter(13),
                color = PL.ink2,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun PlanDetailContent(
    d: ReadingPlanDetail,
    onBack: () -> Unit,
    onOpenDay: (Int) -> Unit,
    onStart: () -> Unit,
) {
    // Real completion state, derived from the day rows the server returns.
    val done = d.days.count { it.completed == true }
    val allDone = d.days.isNotEmpty() && done >= d.days.size
    val firstIncomplete = d.days.firstOrNull { it.completed != true } ?: d.days.firstOrNull()
    val nextDay = firstIncomplete?.dayNumber

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            CoverHero(d = d, onBack = onBack)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AboutCard(d)
                WhatYoullRead(d = d, done = done, allDone = allDone, nextDay = nextDay, onOpenDay = onOpenDay)
                PLFinishEarnCard(category = d.category, dayCount = d.dayCount)
                Nudge()
            }
        }
        CtaBar(
            d = d,
            done = done,
            allDone = allDone,
            firstIncomplete = firstIncomplete,
            onOpenDay = onOpenDay,
            onStart = onStart,
        )
    }
}

// --- Hero -----------------------------------------------------------------

@Composable
private fun CoverHero(d: ReadingPlanDetail, onBack: () -> Unit) {
    var saved by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().height(256.dp)) {
        PLCover(d.imageUrl, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(heroScrim))

        // bottom-leading: category pill + title + meta row
        Column(
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            d.category?.takeIf { it.isNotEmpty() }?.let { c ->
                Text(
                    c.uppercase(),
                    style = plInter(9, FontWeight.Bold, 1.26f),
                    color = PL.navy,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(PL.gold)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Text(
                d.title,
                style = plSerif(26, FontWeight.SemiBold, -0.52f),
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroMeta(Icons.Filled.Schedule, "${d.dayCount} days")
                HeroMeta(Icons.Filled.MenuBook, "Devotional")
            }
        }

        // top overlay: back circle + heart toggle
        Row(
            Modifier.align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBtn(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            CircleBtn(onClick = { saved = !saved }) {
                if (saved) {
                    Icon(Icons.Filled.Favorite, "Saved", tint = PL.gold, modifier = Modifier.size(17.dp))
                } else {
                    Icon(Icons.Filled.FavoriteBorder, "Save", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroMeta(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = PL.goldLight, modifier = Modifier.size(13.dp))
        Text(text, style = plInter(12), color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun CircleBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// --- Cards ----------------------------------------------------------------

@Composable
private fun AboutCard(d: ReadingPlanDetail) {
    PlanWhiteCard {
        PLOverline("ABOUT THIS PLAN", color = PL.goldDeep, kerning = 1.4f)
        Text(
            d.description ?: d.subtitle ?: "A guided plan — Scripture and a short devotional each day.",
            style = plInter(13).copy(lineHeight = 17.sp),
            color = PL.blurb,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun WhatYoullRead(
    d: ReadingPlanDetail,
    done: Int,
    allDone: Boolean,
    nextDay: Int?,
    onOpenDay: (Int) -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    val visible = if (showAll) d.days else d.days.take(4)

    PlanWhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PLOverline("WHAT YOU'LL READ", color = PL.goldDeep, kerning = 1.4f)
            Spacer(Modifier.weight(1f))
            if (done > 0) {
                val pct = Math.round(done.toDouble() / maxOf(d.days.size, 1) * 100).toInt()
                Text("$pct% done", style = plInter(10, FontWeight.Bold), color = PL.catText)
            }
        }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visible.forEach { day ->
                PLDetailDayRow(
                    day = day,
                    isNext = !allDone && day.dayNumber == nextDay,
                    onTap = { onOpenDay(day.dayNumber) },
                )
            }
            if (!showAll && d.days.size > 4) {
                Text(
                    "+ ${d.days.size - 4} more days",
                    style = plInter(10),
                    color = PL.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 36.dp)
                        .clickable { showAll = true }
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Nudge() {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(listOf(PL.gold.copy(alpha = 0.08f), PL.gold.copy(alpha = 0.02f))),
            )
            .border(1.dp, PL.gold.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = PL.gold, modifier = Modifier.size(16.dp))
        Text(
            "Consistency over intensity — a few faithful minutes a day.",
            style = plSerif(12, FontWeight.Normal, italic = true),
            color = PL.navy,
            modifier = Modifier.weight(1f),
        )
    }
}

/** White rounded card (radius 22, PL.border stroke, pad 16) shared by the two content cards. */
@Composable
private fun PlanWhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, PL.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content,
    )
}

// --- CTA bar --------------------------------------------------------------

@Composable
private fun CtaBar(
    d: ReadingPlanDetail,
    done: Int,
    allDone: Boolean,
    firstIncomplete: ReadingPlanDay?,
    onOpenDay: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val target = if (allDone) d.days.firstOrNull() else firstIncomplete
    val targetDay = target?.dayNumber ?: 1
    val label = if (done > 0) {
        if (allDone) "Review plan" else "Continue · Day $targetDay"
    } else {
        "Start plan"
    }

    Column(Modifier.fillMaxWidth().background(Color.White)) {
        // Top hairline (iOS: 1px PL.border across the top of the white CTA bar).
        Box(Modifier.fillMaxWidth().height(1.dp).background(PL.border))
        Row(
            Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = Spacing.tabBarSpace),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Full-width gold CTA
            Row(
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = PL.gold.copy(alpha = 0.45f), spotColor = PL.gold.copy(alpha = 0.45f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(PL.goldCtaGrad)
                    .clickable {
                        if (d.enrolled) onOpenDay(targetDay) else onStart()
                    }
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MenuBook, null, tint = PL.navy, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = plInter(14, FontWeight.Bold), color = PL.navy)
            }
            // White "Invite" button (no-op)
            Row(
                Modifier.heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, PL.border, RoundedCornerShape(16.dp))
                    .clickable { }
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Share, null, tint = PL.navy, modifier = Modifier.size(15.dp))
                Text("Invite", style = plInter(13, FontWeight.SemiBold), color = PL.navy)
            }
        }
    }
}

// --- PLFinishEarnCard (ReadingPlanCards.swift 340–377) --------------------

@Composable
private fun PLFinishEarnCard(category: String?, dayCount: Int) {
    Row(
        Modifier.fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(20.dp), ambientColor = PL.navyDeep.copy(alpha = 0.45f), spotColor = PL.navyDeep.copy(alpha = 0.45f))
            .clip(RoundedCornerShape(20.dp))
            .background(PL.navyGrad)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Trophy tile
        Box(
            Modifier.size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, PL.gold.copy(alpha = 0.33f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.EmojiEvents, null, tint = PL.goldLight, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "FINISH & EARN",
                style = plInter(9, FontWeight.Bold, 1.44f),
                color = PL.goldLight,
            )
            Text(
                "The “${category ?: "Finisher"}” badge",
                style = plInter(13, FontWeight.Bold, -0.13f),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "A little gift for your shelf when you complete all $dayCount days.",
                style = plInter(11),
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

// --- PLDetailDayRow (ReadingPlanCards.swift 381–419) ----------------------

@Composable
private fun PLDetailDayRow(day: ReadingPlanDay, isNext: Boolean, onTap: () -> Unit) {
    val done = day.completed == true
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isNext) PL.highlight else PL.surface)
            .border(1.dp, if (isNext) PL.gold.copy(alpha = 0.27f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 36dp number tile
        Box(
            Modifier.size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (done) PL.gold else Color.White)
                .then(if (done) Modifier else Modifier.border(1.dp, PL.border, RoundedCornerShape(12.dp))),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DAY", style = plInter(7, FontWeight.Bold), color = PL.gold)
                    Text("${day.dayNumber}", style = plSerif(14, FontWeight.SemiBold), color = PL.navy)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                day.title ?: "Reading & reflection",
                style = plInter(12, FontWeight.SemiBold),
                color = PL.navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${day.reference} · ~5 min read",
                style = plInter(11),
                color = PL.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isNext) {
            Text(
                "Start",
                style = plInter(9, FontWeight.Bold),
                color = PL.navy,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(PL.gold).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PL.chev, modifier = Modifier.size(14.dp))
        }
    }
}
