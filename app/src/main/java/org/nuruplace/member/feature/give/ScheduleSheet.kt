// One recurring gift, managed in place (Giving Cycle 4) — the sheet the Give
// tab's rail, its "Your rhythm" row and the recurring-gifts list all open.
// It says what the gift is, whether it runs and why not (paused after failed
// prompts, by the member until a date, or with its pledge), which phone it
// prompts and why the last prompt failed; and the member can Change it
// (amount / day / number — PATCH, so a change never needs a cancel and a new
// gift), Pause it (until they resume, or until a date), Resume it, switch the
// heads-up before each prompt, or Cancel it (asked first). Every call is
// online and its refusal is said in the server's own words. The rules and
// words are ScheduleCopy.kt's (pinned by ScheduleCopyTest).
package org.nuruplace.member.feature.give

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PauseScheduleBody
import org.nuruplace.member.data.net.UpdateScheduleBody
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SheetCapsule = RoundedCornerShape(999.dp)

/**
 * The recurring-gift sheet. [schedule] is where it starts; the sheet keeps
 * the server's answers to its own calls, and [onChanged] tells the caller to
 * refetch whatever lists it (the rail, the list, Partners' standing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleSheet(
    schedule: GivingSchedule,
    onClose: () -> Unit,
    onChanged: () -> Unit,
    onCancelled: () -> Unit,
    /** M-Pesa's own range for a changed amount (the server checks again). */
    minMinor: Long = FALLBACK_MPESA.minMinor,
    maxMinor: Long = FALLBACK_MPESA.maxMinor,
) {
    val scope = rememberCoroutineScope()
    var s by remember(schedule.scheduleId) { mutableStateOf(schedule) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmingCancel by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    var pausing by remember { mutableStateOf(false) }
    val pause = schedulePauseView(s)
    val running = scheduleRunning(s.status)
    val live = scheduleCancellable(s.status)

    /** One call at a time; its refusal is said, never swallowed. */
    fun act(call: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                call()
                onChanged()
            } catch (e: Exception) {
                error = ApiException.message(e)
            } finally {
                busy = false
            }
        }
    }

    /** A PATCH answers the schedule row; keep it unless it came back bare. */
    fun keep(row: GivingSchedule) {
        if (row.fund.isNotBlank()) s = row
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recurring gift", style = giSerif(18, FontWeight.SemiBold, -0.36f), color = GIVE.navy)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(GIVE.surface).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = GIVE.navy, modifier = Modifier.size(15.dp))
                }
            }
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(GIVE.gold.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(19.dp))
                }
                Column {
                    Text(money(s.amountMinor, s.currency), style = giInter(17, FontWeight.Bold), color = GIVE.navy)
                    Text(
                        "Every ${cadenceWord(s.frequency)} · ${giveFund(s.fund).name}" + (scheduleStatusLabel(s.status)?.let { " · $it" } ?: ""),
                        style = giInter(12), color = GIVE.sub,
                    )
                }
            }

            // Why it is paused — and Resume, unless it follows its pledge.
            pause?.let { p ->
                Column(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GIVE.priorityBg)
                        .border(1.dp, GIVE.gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(p.line, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
                    if (p.canResume) {
                        SheetButton("Resume", filled = true, busy = busy) {
                            act {
                                val r = Net.client.api.resumeSchedule(s.scheduleId)
                                s = s.copy(
                                    status = r.status.ifBlank { "active" }, pauseReason = null, resumeOn = null,
                                    nextRunAt = r.nextRunAt ?: s.nextRunAt, lastFailure = null, consecutiveFailures = 0,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SheetDetailRow("Fund", giveFund(s.fund).name)
            SheetDivider()
            SheetDetailRow("Amount", money(s.amountMinor, s.currency))
            SheetDivider()
            SheetDetailRow(
                "Frequency",
                if (freqOf(s.frequency) == FREQ_WEEKLY) scheduleDay(s)?.let { "Every ${weekdayName(it)}" } ?: "Weekly"
                else scheduleDay(s)?.let { "Monthly, on the ${ordinal(it)}" } ?: "Monthly",
            )
            SheetDivider()
            if (running) SheetDetailRow("Next prompt", nairobiDayOf(s.nextRunAt) ?: "—") else SheetDetailRow("Status", scheduleStatusLabel(s.status) ?: "—")
            SheetDivider()
            SheetDetailRow("Method", s.method.takeIf { it.isNotBlank() }?.let { giveMethodLabel(it) } ?: "—")
            SheetDivider()
            SheetDetailRow("Prompts go to", s.phoneNumber?.takeIf { it.isNotBlank() }?.let { kenyanMobileDisplay(it) } ?: "Your profile number")

            // Why the last prompt failed — the server's reason and hint.
            scheduleFailureLine(s)?.let {
                Text(
                    it, style = giInter(12), color = GIVE.danger,
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(GIVE.cancelBg).padding(12.dp),
                )
            }

            if (live) {
                // The heads-up minutes before each prompt, so it is expected.
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Tell me before each prompt", style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
                        Text("A notification a few minutes before M-Pesa asks for your PIN.", style = giInter(11), color = GIVE.sub)
                    }
                    Switch(
                        checked = s.headsUp,
                        enabled = !busy,
                        onCheckedChange = { on -> act { keep(Net.client.api.updateSchedule(s.scheduleId, UpdateScheduleBody(headsUp = on))) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = GIVE.gold, checkedThumbColor = Color.White),
                    )
                }
                Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetButton("Change", filled = false, busy = false, modifier = Modifier.weight(1f), enabled = !busy) { changing = true }
                    if (running) {
                        SheetButton("Pause", filled = false, busy = false, modifier = Modifier.weight(1f), enabled = !busy) { pausing = true }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.cancelBg)
                        .border(1.dp, GIVE.cancelBorder, RoundedCornerShape(16.dp))
                        .clickable(enabled = !busy) { error = null; confirmingCancel = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Cancel schedule", style = giInter(13, FontWeight.Bold), color = GIVE.cancelText)
                }
            }
            if (busy) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = GIVE.gold, strokeWidth = 2.dp)
                }
            }
            error?.let {
                Text(it, style = giInter(12), color = GIVE.danger, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (confirmingCancel) {
        CancelScheduleDialog(
            s,
            onConfirm = {
                confirmingCancel = false
                if (!busy) {
                    busy = true; error = null
                    scope.launch {
                        try {
                            Net.client.api.cancelSchedule(s.scheduleId)
                            onCancelled()
                        } catch (e: Exception) {
                            // Said, never swallowed: the schedule still stands.
                            error = ApiException.message(e)
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            onDismiss = { confirmingCancel = false },
        )
    }
    if (changing) {
        ChangeScheduleDialog(
            s, minMinor, maxMinor,
            onSave = { patch ->
                changing = false
                // Nothing changed → nothing is sent.
                if (patch != null) act { keep(Net.client.api.updateSchedule(s.scheduleId, patch)) }
            },
            onDismiss = { changing = false },
        )
    }
    if (pausing) {
        PauseScheduleDialog(
            today = nairobiToday(Instant.now()),
            onPause = { until ->
                pausing = false
                act {
                    val r = Net.client.api.pauseSchedule(s.scheduleId, PauseScheduleBody(until?.toString()))
                    s = s.copy(status = r.status.ifBlank { "paused" }, pauseReason = r.pauseReason ?: "member", resumeOn = r.resumeOn ?: until?.toString())
                }
            },
            onDismiss = { pausing = false },
        )
    }
}

/** Change the amount, the day or the number — only what changed is sent
 *  (ScheduleCopy.scheduleEditPatch); whole shillings inside M-Pesa's range. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangeScheduleDialog(
    s: GivingSchedule,
    minMinor: Long,
    maxMinor: Long,
    onSave: (UpdateScheduleBody?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("${s.amountMinor / 100}") }
    var day by remember { mutableStateOf(scheduleDay(s)) }
    val startNumber = promptNumberChoiceOf(s)
    var useProfile by remember { mutableStateOf(startNumber is PromptNumberChoice.Profile) }
    var numberText by remember { mutableStateOf((startNumber as? PromptNumberChoice.Own)?.e164?.let(::kenyanMobileDisplay).orEmpty()) }
    val amountError = scheduleAmountError(amountText, minMinor, maxMinor)
    val number = kenyanMobileE164(numberText)
    val numberWrong = !useProfile && number == null && numberText.count { it.isDigit() } >= 9
    val valid = amountError == null && (useProfile || number != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GIVE.white,
        title = { Text("Change this gift", style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("AMOUNT", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    prefix = { Text("KSh ", style = giInter(15, FontWeight.Medium), color = GIVE.sub) },
                    isError = amountError != null && amountText.isNotEmpty(),
                    textStyle = giSerif(22, FontWeight.SemiBold).copy(color = GIVE.navy),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                amountError?.let { Text(it, style = giInter(12), color = GIVE.danger, modifier = Modifier.padding(top = 4.dp)) }
                Text(
                    if (freqOf(s.frequency) == FREQ_WEEKLY) "DAY OF THE WEEK" else "DAY OF THE MONTH",
                    style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline, modifier = Modifier.padding(top = 16.dp),
                )
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    scheduleDayOptions(s.frequency).forEach { d ->
                        val on = d == day
                        Text(
                            scheduleDayChip(s.frequency, d),
                            style = giInter(12, FontWeight.SemiBold),
                            color = if (on) Color.White else GIVE.navy,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clip(SheetCapsule)
                                .background(if (on) GIVE.navy else GIVE.surface)
                                .then(if (on) Modifier else Modifier.border(1.dp, GIVE.border, SheetCapsule))
                                .clickable { day = d }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                if (freqOf(s.frequency) != FREQ_WEEKLY && (day ?: 0) > 28) {
                    Text("In a shorter month, the last day.", style = giInter(11), color = GIVE.tertiary, modifier = Modifier.padding(top = 4.dp))
                }
                Text("M-PESA NUMBER", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline, modifier = Modifier.padding(top = 16.dp))
                ChoiceLine("My profile number", selected = useProfile) { useProfile = true }
                ChoiceLine("Another number", selected = !useProfile) { useProfile = false }
                if (!useProfile) {
                    OutlinedTextField(
                        value = numberText,
                        onValueChange = { v -> numberText = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(18) },
                        singleLine = true,
                        placeholder = { Text("07XX XXX XXX", style = giInter(14), color = GIVE.tertiary) },
                        isError = numberWrong,
                        textStyle = giInter(15, FontWeight.SemiBold).copy(color = GIVE.navy),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    if (numberWrong) Text(PHONE_INVALID_MESSAGE, style = giInter(12), color = GIVE.danger, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid) {
                        onSave(
                            scheduleEditPatch(
                                s,
                                amountMajor = amountText.toIntOrNull(),
                                day = day,
                                number = if (useProfile) PromptNumberChoice.Profile else number?.let { PromptNumberChoice.Own(it) },
                            ),
                        )
                    }
                },
                enabled = valid,
            ) { Text("Save", style = giInter(14, FontWeight.Bold), color = if (valid) GIVE.gold else GIVE.sub) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back", style = giInter(14, FontWeight.SemiBold), color = GIVE.sub) }
        },
    )
}

/** Pause until the member resumes it, or until a date from tomorrow to a
 *  year ahead (ScheduleCopy.pauseDateRange; the server checks again). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseScheduleDialog(today: LocalDate, onPause: (LocalDate?) -> Unit, onDismiss: () -> Unit) {
    val range = pauseDateRange(today)
    var untilDate by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    val ready = !untilDate || date != null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GIVE.white,
        title = { Text("Pause this gift?", style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy) },
        text = {
            Column {
                Text("Nothing is prompted while it is paused. Nothing missed is taken when it resumes.", style = giInter(13), color = GIVE.sub)
                Spacer(Modifier.height(10.dp))
                ChoiceLine("Until I resume it", selected = !untilDate) { untilDate = false }
                ChoiceLine("Until a date", selected = untilDate) { untilDate = true; if (date == null) picking = true }
                if (untilDate) {
                    Row(
                        Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GIVE.surface)
                            .border(1.dp, GIVE.border, RoundedCornerShape(12.dp)).clickable { picking = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(18.dp))
                        Text(
                            date?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)) ?: "Choose a date",
                            style = giInter(13, FontWeight.SemiBold), color = if (date == null) GIVE.tertiary else GIVE.navy,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (ready) onPause(if (untilDate) date else null) }, enabled = ready) {
                Text("Pause", style = giInter(14, FontWeight.Bold), color = if (ready) GIVE.gold else GIVE.sub)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back", style = giInter(14, FontWeight.SemiBold), color = GIVE.sub) }
        },
    )
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: range.start).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = range.start.year..range.endInclusive.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in range

                override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (pauseDateAllowed(d, today)) date = d
                    }
                    picking = false
                }) { Text("Set date", style = giInter(14, FontWeight.Bold), color = GIVE.gold) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", style = giInter(14, FontWeight.SemiBold), color = GIVE.sub) } },
        ) { DatePicker(state = state) }
    }
}

/** One choice of two, in the Give rows' voice: a gold ring when picked. */
@Composable
private fun ChoiceLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) GIVE.priorityBg else GIVE.white)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) GIVE.gold else GIVE.border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(if (selected) GIVE.gold else GIVE.white)
                .border(1.dp, if (selected) GIVE.gold else GIVE.ink300, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(12.dp))
        }
        Text(label, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
    }
}

/** A sheet action: gold when [filled], else an outlined pill. */
@Composable
private fun SheetButton(
    label: String,
    filled: Boolean,
    busy: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = !busy,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().height(42.dp).alpha(if (enabled) 1f else 0.5f).clip(RoundedCornerShape(14.dp))
            .background(if (filled) GIVE.gold else GIVE.white)
            .then(if (filled) Modifier else Modifier.border(1.dp, GIVE.gold.copy(alpha = 0.55f), RoundedCornerShape(14.dp)))
            .clickable(enabled = enabled) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), color = GIVE.navy, strokeWidth = 2.dp)
        } else {
            Text(label, style = giInter(13, FontWeight.Bold), color = if (filled) GIVE.navy else GIVE.gold)
        }
    }
}

@Composable
private fun SheetDetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = giInter(12), color = GIVE.sub)
        Text(value, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, textAlign = TextAlign.End)
    }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GIVE.border))
}
