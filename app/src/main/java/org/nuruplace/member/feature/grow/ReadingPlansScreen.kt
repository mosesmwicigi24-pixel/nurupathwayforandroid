// Reading plans — the catalogue with enrolment state, and a plan's day-by-day
// detail (start, view days, complete segments/day). Ports the iOS ReadingPlansView
// + PlanSegmentView (day/segment reading folded into the detail for now).
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.CompleteDayBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ReadingPlanDay
import org.nuruplace.member.data.net.ReadingPlanDetail
import org.nuruplace.member.data.net.ReadingPlanRow
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun ReadingPlansScreen(onBack: () -> Unit, onOpenPlan: (String) -> Unit) {
    AsyncContent(load = { Net.client.api.plans().data }) { plans: List<ReadingPlanRow>, _ ->
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Reading plans", kicker = "Grow", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(plans, key = { it.planId }) { p ->
                    NuruCard(modifier = Modifier.clickable { onOpenPlan(p.planId) }) {
                        p.category?.let { Kicker(it) }
                        Text(p.title, style = NuruType.cardTitle, color = Nuru.ink)
                        p.subtitle?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                        Spacer(Modifier.height(Spacing.xs))
                        val done = p.completedDays?.size ?: 0
                        Text(
                            if (p.enrolled) "Enrolled · $done/${p.dayCount} days" else "${p.dayCount} days",
                            style = NuruType.micro, color = if (p.enrolled) Nuru.goldLo else Nuru.ink400,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlanDetailScreen(planId: String, onBack: () -> Unit) {
    AsyncContent(key = planId, load = { Net.client.api.plan(planId) }) { plan: ReadingPlanDetail, reload ->
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader(plan.title, kicker = plan.category ?: "Reading plan", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                plan.description?.let { item { Text(it, style = NuruType.body, color = Nuru.ink600) } }
                if (!plan.enrolled) {
                    item {
                        PrimaryButton(
                            label = "Start this plan",
                            loading = busy,
                            onClick = {
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        try { Net.client.api.startPlan(planId); reload() }
                                        catch (_: Exception) {} finally { busy = false }
                                    }
                                }
                            },
                        )
                    }
                }
                items(plan.days, key = { it.dayNumber }) { day ->
                    DayCard(planId, day, onChanged = reload)
                }
            }
        }
    }
}

@Composable
private fun DayCard(planId: String, day: ReadingPlanDay, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val done = day.completed == true

    NuruCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(Radii.pill))
                    .background(if (done) Nuru.successBg else Nuru.goldTint),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.Check, null, tint = Nuru.successText, modifier = Modifier.size(16.dp))
                else Text("${day.dayNumber}", style = NuruType.label, color = Nuru.goldLo)
            }
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(day.title ?: "Day ${day.dayNumber}", style = NuruType.rowTitle, color = Nuru.ink)
                Text(day.reference, style = NuruType.caption, color = Nuru.ink600)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(Spacing.sm))
            day.content?.let { Text(it, style = NuruType.body, color = Nuru.ink); Spacer(Modifier.height(Spacing.sm)) }
            day.segments?.forEach { seg ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (seg.completed) "✓ " else "• ", style = NuruType.body, color = if (seg.completed) Nuru.success else Nuru.ink400)
                    Text(seg.title, style = NuruType.body, color = Nuru.ink)
                }
            }
            if (!done) {
                Spacer(Modifier.height(Spacing.sm))
                PrimaryButton(
                    label = "Mark day complete",
                    loading = busy,
                    onClick = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try { Net.client.api.completePlanDay(planId, CompleteDayBody(day.dayNumber)); onChanged() }
                                catch (_: Exception) {} finally { busy = false }
                            }
                        }
                    },
                )
            }
        }
    }
}
