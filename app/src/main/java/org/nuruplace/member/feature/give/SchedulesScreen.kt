// Recurring gifts — the member's giving schedules with a cancel action. Port of
// the iOS schedules list. Giving Cycle 1: a paused schedule says Paused, a
// failing one says why its last prompt failed (the server's words), each says
// which phone it prompts, and a cancel asks first and says so when it fails —
// it used to fire on one tap and swallow any error.
package org.nuruplace.member.feature.give

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun SchedulesScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.schedules().data }) { schedules: List<GivingSchedule>, reload ->
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
                    items(schedules, key = { it.scheduleId }) { s -> ScheduleCard(s, onChanged = reload) }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(s: GivingSchedule, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val status = scheduleStatusLabel(s.status)
    val cancellable = scheduleCancellable(s.status)
    NuruCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ksh(s.amountMinor) + " · ${s.frequency}", style = NuruType.cardTitle, color = Nuru.ink)
                Text(
                    "${giveFund(s.fund).name} · ${s.method.takeIf { it.isNotBlank() }?.let { giveMethodLabel(it) } ?: "—"}",
                    style = NuruType.caption, color = Nuru.ink600,
                )
                if (status == null) {
                    Text("Next: ${nairobiDayOf(s.nextRunAt) ?: "—"}", style = NuruType.micro, color = Nuru.goldLo)
                } else {
                    // Paused is trouble the member can act on; Cancelled is history.
                    Text(
                        status, style = NuruType.micro, fontWeight = FontWeight.SemiBold,
                        color = if (cancellable) Nuru.danger else Nuru.ink400,
                    )
                }
                if (cancellable) Text(schedulePromptLine(s), style = NuruType.micro, color = Nuru.ink600)
                // Why the last prompt failed — the server's reason and hint.
                scheduleFailureLine(s)?.let {
                    Text(it, style = NuruType.caption, color = Nuru.danger, modifier = Modifier.padding(top = 4.dp))
                }
                error?.let {
                    Text(it, style = NuruType.caption, color = Nuru.danger, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (cancellable) {
                TextButton(onClick = { if (!busy) { error = null; confirming = true } }, enabled = !busy) {
                    Text(if (busy) "Cancelling…" else "Cancel", style = NuruType.cardCta, color = Nuru.danger)
                }
            }
        }
    }
    if (confirming) {
        CancelScheduleDialog(
            s,
            onConfirm = {
                confirming = false
                busy = true
                scope.launch {
                    try {
                        Net.client.api.cancelSchedule(s.scheduleId)
                        // A cancelled schedule changes the partnership standing.
                        GivingEvents.emit()
                        onChanged()
                    } catch (e: Exception) {
                        // Said, never swallowed: the schedule still stands.
                        error = ApiException.message(e)
                    } finally {
                        busy = false
                    }
                }
            },
            onDismiss = { confirming = false },
        )
    }
}

/** Asked before a recurring gift stops — here and on the Give tab's sheet:
 *  what stops, and that nothing already given is touched. */
@Composable
internal fun CancelScheduleDialog(s: GivingSchedule, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GIVE.white,
        title = { Text("Cancel this recurring gift?", style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy) },
        text = { Text(scheduleCancelText(s), style = giInter(14), color = GIVE.sub) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Cancel gift", style = giInter(14, FontWeight.Bold), color = GIVE.cancelText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep it", style = giInter(14, FontWeight.SemiBold), color = GIVE.sub) }
        },
    )
}
