// Recurring gifts — the member's running and paused giving schedules (never a
// cancelled one, as Give lists them — listedSchedules). Giving Cycle 1: a
// paused schedule says why, a failing one why its last prompt failed (the
// server's words), and each which phone it prompts. Giving Cycle 4: a tap
// opens the schedule's sheet (ScheduleSheet.kt) to change, pause, resume or
// cancel it — the only place a gift is cancelled, as on iOS (the per-card
// Cancel is gone) — and a schedule push lands here with that schedule open
// ([openScheduleId]). Giving Cycle 5: a gift that collects a pledge says
// which, and what its next prompt asks; the member's pledges are read only
// when one does, so a MONTHLY pledge's collector sends its amount and day to
// the pledge.
package org.nuruplace.member.feature.give

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun SchedulesScreen(
    onBack: () -> Unit,
    /** A schedule to open on arrival — a failed or paused schedule's push. */
    openScheduleId: String? = null,
    /** Open a pledge ("Change it on the pledge", Giving Cycle 5). */
    onOpenPledge: (String) -> Unit = {},
) {
    // Opened once per arrival; saveable, so coming back never re-opens it.
    var opened by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<GivingSchedule?>(null) }
    // The member's pledges, only when a schedule collects one (the shape of
    // its pledge decides where its amount and day change). A failed read
    // leaves them unknown — the server then answers a change itself.
    var pledges by remember { mutableStateOf<List<Pledge>>(emptyList()) }
    AsyncContent(load = { Net.client.api.schedules().data }) { all: List<GivingSchedule>, reload ->
        // Running, then paused — never a cancelled one (iOS / Give).
        val schedules = listedSchedules(all)
        LaunchedEffect(schedules) {
            if (!opened && openScheduleId != null) {
                opened = true
                sheet = schedules.firstOrNull { it.scheduleId == openScheduleId }
            }
            if (schedules.any { it.pledge != null }) {
                runCatching { Net.client.api.pledges().data }.onSuccess { pledges = it }
            }
        }
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Recurring gifts", kicker = "Give", onBack = onBack)
            if (schedules.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No recurring gifts set up.", style = NuruType.body, color = Nuru.ink600) }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(schedules, key = { it.scheduleId }) { s -> ScheduleCard(s, onOpen = { sheet = s }) }
                }
            }
        }
        sheet?.let { picked ->
            // The reloaded row for this gift reaches the open sheet.
            val s = schedules.firstOrNull { it.scheduleId == picked.scheduleId } ?: picked
            ScheduleSheet(
                s,
                onClose = { sheet = null },
                // Partners' standing derives from schedules; the list refetches.
                onChanged = { GivingEvents.emit(); reload() },
                // Paused, resumed or cancelled: the sheet closes (iOS).
                onDone = { sheet = null; GivingEvents.emit(); reload() },
                pledges = pledges,
                onOpenPledge = { id -> sheet = null; onOpenPledge(id) },
            )
        }
    }
}

@Composable
private fun ScheduleCard(s: GivingSchedule, onOpen: () -> Unit) {
    // Paused: why, in words (after failures / until a date / with its pledge).
    val pause = schedulePauseView(s)
    NuruCard(modifier = Modifier.clip(RoundedCornerShape(Radii.card)).clickable { onOpen() }) {
        Column(Modifier.fillMaxWidth()) {
            Text(money(s.amountMinor, s.currency) + " · ${s.frequency}", style = NuruType.cardTitle, color = Nuru.ink)
            Text(
                "${giveFund(s.fund).name} · ${s.method.takeIf { it.isNotBlank() }?.let { giveMethodLabel(it) } ?: "—"}",
                style = NuruType.caption, color = Nuru.ink600,
            )
            if (pause == null) {
                Text("Next: ${nairobiDayOf(s.nextRunAt) ?: "—"}", style = NuruType.micro, color = Nuru.goldLo)
            } else {
                // Paused is trouble the member can act on (tap: Resume).
                Text(pause.line, style = NuruType.micro, fontWeight = FontWeight.SemiBold, color = Nuru.danger)
            }
            Text(schedulePromptLine(s), style = NuruType.micro, color = Nuru.ink600)
            // Collecting a pledge: which, and what the next prompt asks.
            listOfNotNull(schedulePledgeLine(s), scheduleNextAmountLine(s)).forEach {
                Text(it, style = NuruType.micro, fontWeight = FontWeight.SemiBold, color = Nuru.goldLo)
            }
            // Why the last prompt failed — the server's reason and hint.
            scheduleFailureLine(s)?.let {
                Text(it, style = NuruType.caption, color = Nuru.danger, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
