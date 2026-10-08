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
//
// A gift that collects a pledge (Giving Cycle 5) says which, and what its
// next prompt asks; collecting a MONTHLY pledge, Change keeps the number and
// offers "Change it on the pledge" for the amount and day, and a change the
// server refuses for that reason (details.pledge_id) offers the same. The
// sheet follows the caller's latest row for it, so a reload — a retried
// scheduled charge that cleared its strikes — shows here too.
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import org.nuruplace.member.ui.components.NuruDatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import org.nuruplace.member.ui.components.NuruModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PauseScheduleBody
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.UpdateScheduleBody
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.nuruplace.member.ui.icons.Lucide

private val SheetCapsule = RoundedCornerShape(999.dp)

/** What the sheet shows below its summary (iOS ScheduleDetailSheet.Mode). */
private enum class SheetMode { View, Change, Pause, ConfirmCancel }

/**
 * The recurring-gift sheet, as iOS's ScheduleDetailSheet: the summary, why
 * it is paused (with Resume) and why its last prompt failed, first; then the
 * details, the heads-up and Change · Pause · Cancel schedule — each opening
 * in place (the Change form, the Pause choice, the cancel question), never a
 * dialog over the sheet. [schedule] is where it starts; the sheet keeps the
 * server's answers to its own calls. [onChanged]: changed in place (amount,
 * day, number, heads-up) — the caller refetches, the sheet stays. [onDone]:
 * paused, resumed or cancelled — the sheet closes and the caller refetches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleSheet(
    schedule: GivingSchedule,
    onClose: () -> Unit,
    onChanged: () -> Unit,
    onDone: () -> Unit,
    /** M-Pesa's own range for a changed amount (the server checks again). */
    minMinor: Long = FALLBACK_MPESA.minMinor,
    maxMinor: Long = FALLBACK_MPESA.maxMinor,
    /** The member's pledges as far as the caller knows them — a MONTHLY
     *  pledge this gift collects owns its amount and day. */
    pledges: List<Pledge> = emptyList(),
    /** Open a pledge ("Change it on the pledge"). */
    onOpenPledge: (String) -> Unit = {},
    /** The profile's number, offered as "Use my profile number"; null when
     *  the caller does not know it. */
    phoneOnFile: String? = null,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val actContext = androidx.compose.ui.platform.LocalContext.current
    var s by remember(schedule.scheduleId) { mutableStateOf(schedule) }
    // The caller's newer row for this gift (its list reloaded) replaces ours.
    LaunchedEffect(schedule) { s = schedule }
    var mode by remember(schedule.scheduleId) { mutableStateOf(SheetMode.View) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // A refusal that named the pledge owning this change (details.pledge_id).
    var errorPledgeId by remember { mutableStateOf<String?>(null) }
    // The heads-up switch flips at once, and back if the server says no.
    var headsUp by remember(schedule.scheduleId) { mutableStateOf(schedule.headsUp) }
    var draft by remember(schedule.scheduleId) { mutableStateOf(scheduleDraftOf(schedule)) }
    val today = remember { nairobiToday(Instant.now()) }
    var pauseUntilDate by remember { mutableStateOf(false) }
    // Tomorrow, the first day a pause may end — preselected, as iOS.
    var resumeDate by remember { mutableStateOf(pauseDateRange(today).start) }
    var pickingDate by remember { mutableStateOf(false) }
    val followsPledge = monthlyPledgeCollected(s, pledges)
    val pause = schedulePauseView(s)
    val paused = s.status.trim().lowercase() == "paused"
    val live = scheduleCancellable(s.status)
    val rail = FALLBACK_MPESA.copy(minMinor = minMinor, maxMinor = maxMinor)
    val plan = scheduleChangePlan(draft, s, rail)
    val profileNumber = kenyanMobileE164(phoneOnFile)

    /** One call at a time; its refusal is said in the server's words, else
     *  [lead] and why (§4: offline, or our side) — never swallowed. */
    fun act(lead: String, call: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; errorPledgeId = null
        scope.launch {
            try {
                call()
                Haptics.confirm(view)
            } catch (e: Exception) {
                // Read ONCE (an error body is one-shot): the words, and the
                // pledge a refused amount or day belongs to.
                val refusal = ApiException.serverError(e)
                error = refusal?.displayMessage ?: ApiException.failureLine(lead, e, actContext)
                errorPledgeId = refusal?.detail("pledge_id")
                Haptics.reject(view)
            } finally {
                busy = false
            }
        }
    }

    /** A PATCH answers the schedule row; keep it unless it came back bare. */
    fun keep(row: GivingSchedule) {
        if (row.fund.isNotBlank()) s = row
    }

    NuruModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recurring gift", style = giSerif(18, FontWeight.SemiBold, -0.36f), color = GIVE.navy)
                if (paused) {
                    Text(
                        "Paused", style = giInter(11, FontWeight.Medium), color = GIVE.ink600,
                        modifier = Modifier.clip(SheetCapsule).background(GIVE.mutedBg).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(GIVE.surface).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.X, contentDescription = "Close", tint = GIVE.navy, modifier = Modifier.size(14.dp))
                }
            }
            // The summary: the amount, its day and fund, the pledge it collects.
            Row(
                Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TILE_TINT), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Repeat, contentDescription = null, tint = TILE_ICON, modifier = Modifier.size(18.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(money(s.amountMinor, s.currency), style = giInter(18, FontWeight.Bold), color = GIVE.navy)
                    Text("${scheduleDayLine(s)} · ${giveFund(s.fund).name}", style = giInter(12), color = GIVE.sub)
                    listOfNotNull(schedulePledgeLine(s), scheduleNextAmountLine(s)).forEach {
                        Text(it, style = giInter(12, FontWeight.SemiBold), color = GIVE.eyebrow)
                    }
                }
            }

            // Why it is paused, first — and Resume, when it is ours to resume.
            pause?.let { p ->
                Column(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GIVE.mutedBg).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Lucide.Pause, contentDescription = null, tint = GIVE.ink600, modifier = Modifier.size(14.dp))
                        Text(p.line, style = giInter(12, FontWeight.SemiBold), color = GIVE.navy)
                    }
                    if (p.canResume) {
                        FilledSheetButton("Resume", navy = true, busy = busy, enabled = !busy) {
                            act("Couldn't resume your gift.") {
                                Net.client.api.resumeSchedule(s.scheduleId)
                                onDone()
                            }
                        }
                        Text("Resuming never collects a missed gift.", style = giInter(11), color = GIVE.tertiary)
                    }
                }
            }
            // Why the last prompt failed, while it still fails — the server's words.
            s.lastFailure?.takeIf { it.reason.isNotBlank() }?.let { f ->
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nuru.warningBg)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Lucide.AlertTriangle, contentDescription = null, tint = Nuru.answeredText, modifier = Modifier.padding(top = 1.dp).size(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(f.reason, style = giInter(12, FontWeight.SemiBold), color = Nuru.answeredText)
                        f.hint.takeIf { it.isNotBlank() }?.let { Text(it, style = giInter(11), color = GIVE.sub) }
                    }
                }
            }

            when (mode) {
                SheetMode.View -> {
                    DetailRows(s)
                    if (live) {
                        // The heads-up minutes before each prompt, so it is expected.
                        Row(
                            Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GIVE.white)
                                .border(1.dp, GIVE.border, RoundedCornerShape(18.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Tell me before each prompt", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
                                Text("A notification a few minutes before M-Pesa asks for your PIN.", style = giInter(11), color = GIVE.sub)
                            }
                            Switch(
                                checked = headsUp,
                                enabled = !busy,
                                onCheckedChange = { on ->
                                    headsUp = on
                                    act("Couldn't change that.") {
                                        try {
                                            val row = Net.client.api.updateSchedule(s.scheduleId, UpdateScheduleBody(headsUp = on))
                                            if (row.fund.isNotBlank()) s = row else s = s.copy(headsUp = on)
                                            headsUp = s.headsUp
                                            onChanged()
                                        } catch (e: Exception) {
                                            headsUp = s.headsUp
                                            throw e
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = GIVE.gold, checkedThumbColor = Color.White),
                            )
                        }
                        Row(Modifier.padding(top = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlineSheetButton("Change", modifier = Modifier.weight(1f), enabled = !busy) {
                                draft = scheduleDraftOf(s); error = null; errorPledgeId = null; mode = SheetMode.Change
                            }
                            // Pausing is for a running gift; a paused one resumes above.
                            if (!paused) {
                                OutlineSheetButton("Pause", modifier = Modifier.weight(1f), enabled = !busy) {
                                    pauseUntilDate = false; resumeDate = pauseDateRange(today).start; error = null; mode = SheetMode.Pause
                                }
                            }
                        }
                        Row(
                            Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.cancelBg)
                                .border(1.dp, GIVE.cancelBorder, RoundedCornerShape(16.dp))
                                .clickable(enabled = !busy) { Haptics.tap(view); error = null; mode = SheetMode.ConfirmCancel },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text("Cancel schedule", style = giInter(13, FontWeight.Bold), color = GIVE.cancelText)
                        }
                    }
                }
                SheetMode.Change -> Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CHANGE THIS GIFT", style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    if (followsPledge != null) {
                        // Its amount and day are the pledge's (Giving Cycle 5).
                        Text(
                            "The amount and day come from your pledge “${followsPledge.displayTitle}” — change them there and this gift follows.",
                            style = giInter(12), color = GIVE.sub,
                        )
                        OutlineSheetButton("Change it on the pledge", enabled = !busy) { onOpenPledge(followsPledge.pledgeId) }
                    } else {
                        FieldLabel("Amount")
                        OutlinedTextField(
                            value = draft.amountText,
                            onValueChange = { v -> draft = draft.copy(amountText = v.filter { it.isDigit() || it == ',' }.take(9)) },
                            singleLine = true,
                            prefix = { Text("KSh ", style = giInter(14, FontWeight.Medium), color = GIVE.tertiary) },
                            placeholder = { Text("1,000", style = giInter(15), color = GIVE.tertiary) },
                            textStyle = giInter(15, FontWeight.SemiBold).copy(color = GIVE.navy),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val weekly = freqOf(s.frequency) == FREQ_WEEKLY
                        FieldLabel(if (weekly) "Day of the week" else "Day of the month")
                        DayChoice(s.frequency, draft.day) { d -> draft = draft.copy(day = d) }
                        if (!weekly && draft.day >= 29) {
                            Text("In a shorter month, the prompt comes on its last day.", style = giInter(11), color = GIVE.tertiary)
                        }
                    }
                    // Its own number, or back to the profile's.
                    FieldLabel("M-Pesa number")
                    if (!draft.useProfile) {
                        OutlinedTextField(
                            value = draft.numberText,
                            onValueChange = { v -> draft = draft.copy(numberText = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(18)) },
                            singleLine = true,
                            leadingIcon = { Icon(Lucide.Smartphone, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp)) },
                            placeholder = { Text("07XX XXX XXX", style = giInter(15), color = GIVE.tertiary) },
                            textStyle = giInter(15, FontWeight.SemiBold).copy(color = GIVE.navy),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    profileNumber?.let { own ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    Haptics.tick(view)
                                    val use = !draft.useProfile
                                    draft = draft.copy(
                                        useProfile = use,
                                        numberText = if (!use && draft.numberText.isBlank()) kenyanMobileDisplay(own) else draft.numberText,
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioDot(draft.useProfile)
                            Text("Use my profile number (${kenyanMobileDisplay(own)})", style = giInter(12, FontWeight.SemiBold), color = GIVE.navy)
                        }
                    }
                    plan.problem?.let { Text(it, style = giInter(11), color = GIVE.danger) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineSheetButton("Back", modifier = Modifier.weight(1f), enabled = !busy) {
                            error = null; errorPledgeId = null; mode = SheetMode.View
                        }
                        FilledSheetButton("Save changes", navy = false, busy = busy, enabled = !busy && plan.patch != null, modifier = Modifier.weight(1f)) {
                            val patch = plan.patch ?: return@FilledSheetButton
                            act("Couldn't save that.") {
                                keep(Net.client.api.updateSchedule(s.scheduleId, patch))
                                draft = scheduleDraftOf(s)
                                headsUp = s.headsUp
                                mode = SheetMode.View
                                onChanged()
                            }
                        }
                    }
                }
                SheetMode.Pause -> Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("PAUSE THIS GIFT", style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    Text("Nothing is prompted while it's paused, and nothing is owed.", style = giInter(12), color = GIVE.sub)
                    ChoiceLine("Until I resume", selected = !pauseUntilDate) { pauseUntilDate = false }
                    ChoiceLine("Until a date", selected = pauseUntilDate) { pauseUntilDate = true }
                    if (pauseUntilDate) {
                        // Tomorrow to a year from today, on the church's calendar.
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GIVE.surface)
                                .clickable { pickingDate = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Resume on", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy, modifier = Modifier.weight(1f))
                            Text(org.nuruplace.member.util.NuruDates.day(resumeDate), style = giInter(14, FontWeight.SemiBold), color = GIVE.gold)
                        }
                        Text(pauseComesBackLine(resumeDate), style = giInter(11), color = GIVE.tertiary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineSheetButton("Back", modifier = Modifier.weight(1f), enabled = !busy) { error = null; mode = SheetMode.View }
                        FilledSheetButton("Pause gift", navy = true, busy = busy, enabled = !busy, modifier = Modifier.weight(1f)) {
                            if (pauseUntilDate && !pauseDateAllowed(resumeDate, today)) {
                                error = "Choose a date from tomorrow to a year from now."
                                return@FilledSheetButton
                            }
                            val until = if (pauseUntilDate) resumeDate else null
                            act("Couldn't pause your gift.") {
                                Net.client.api.pauseSchedule(s.scheduleId, PauseScheduleBody(until?.toString()))
                                onDone()
                            }
                        }
                    }
                }
                SheetMode.ConfirmCancel -> {
                    DetailRows(s)
                    Column(
                        Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GIVE.cancelBg)
                            .border(1.dp, GIVE.cancelBorder, RoundedCornerShape(16.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text("Cancel this recurring gift?", style = giInter(12, FontWeight.SemiBold), color = Color(0xFFB91C1C))
                        Text(
                            "Future prompts stop. Gifts already given are not affected — to change the amount or day, use Change instead.",
                            style = giInter(11), color = GIVE.sub,
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(GIVE.white)
                                    .border(1.dp, GIVE.border, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !busy) { mode = SheetMode.View },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                            ) { Text("Keep it", style = giInter(13, FontWeight.SemiBold), color = GIVE.navy) }
                            Row(
                                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(GIVE.cancelText)
                                    .clickable(enabled = !busy) {
                                        act("Couldn't cancel your gift.") {
                                            Net.client.api.cancelSchedule(s.scheduleId)
                                            onDone()
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                            ) {
                                if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                else Text("Cancel schedule", style = giInter(13, FontWeight.Bold), color = Color.White)
                            }
                        }
                    }
                }
            }

            error?.let {
                Text(it, style = giInter(12), color = GIVE.danger, modifier = Modifier.padding(top = 8.dp))
                // The server named the pledge that owns what was refused.
                errorPledgeId?.let { id ->
                    OutlineSheetButton("Change it on the pledge", modifier = Modifier.padding(top = 8.dp)) { onOpenPledge(id) }
                }
            }
        }
    }

    if (pickingDate) {
        val range = pauseDateRange(today)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = resumeDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = range.start.year..range.endInclusive.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in range

                override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
            },
        )
        NuruDatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (pauseDateAllowed(d, today)) resumeDate = d
                    }
                    pickingDate = false
                }) { Text("Set date", style = giInter(14, FontWeight.Bold), color = GIVE.gold) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel", style = giInter(14, FontWeight.SemiBold), color = GIVE.sub) } },
        ) { DatePicker(state = state) }
    }
}

/** The gift's details (iOS detailRows): Fund · Amount · When · Next prompt
 *  ("None while paused") · Method · Prompts. */
@Composable
private fun DetailRows(s: GivingSchedule) {
    Column(Modifier.padding(top = 12.dp)) {
        SheetDetailRow("Fund", giveFund(s.fund).name)
        SheetDivider()
        SheetDetailRow("Amount", money(s.amountMinor, s.currency))
        SheetDivider()
        SheetDetailRow("When", scheduleDayLine(s))
        SheetDivider()
        SheetDetailRow("Next prompt", scheduleNextPromptLine(s))
        SheetDivider()
        SheetDetailRow("Method", giveMethodLabel(s.method.takeIf { it.isNotBlank() } ?: "mpesa"))
        SheetDivider()
        // Its own number, else the profile's (followed if it changes).
        SheetDetailRow("Prompts", s.phoneNumber?.takeIf { it.isNotBlank() }?.let { kenyanMobileDisplay(it) } ?: "Your profile number")
    }
}

/** A day of the week (Sun … Sat chips) or of the month (1 … 31). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChoice(frequency: String, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        scheduleDayOptions(frequency).forEach { d ->
            val on = d == selected
            Text(
                scheduleDayChip(frequency, d),
                style = giInter(12, FontWeight.SemiBold),
                color = if (on) Color.White else GIVE.navy,
                textAlign = TextAlign.Center,
                modifier = Modifier.clip(SheetCapsule)
                    .background(if (on) GIVE.navy else GIVE.surface)
                    .then(if (on) Modifier else Modifier.border(1.dp, GIVE.border, SheetCapsule))
                    .clickable { onSelect(d) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = giInter(12, FontWeight.SemiBold), color = GIVE.sub)
}

/** iOS's radio: a gold filled ring when on. */
@Composable
private fun RadioDot(on: Boolean) {
    Box(
        Modifier.size(18.dp).clip(CircleShape).background(if (on) GIVE.gold else GIVE.white)
            .border(1.5.dp, if (on) GIVE.gold else GIVE.ink300, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Box(Modifier.size(7.dp).clip(CircleShape).background(GIVE.white))
    }
}

/** One choice of two (iOS pauseOption): a gold ring when picked, on the
 *  priority wash. */
@Composable
private fun ChoiceLine(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) GIVE.priorityBg else GIVE.white)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) GIVE.gold else GIVE.border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioDot(selected)
        Text(label, style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
    }
}

/** An outlined sheet action (iOS outlineButton): navy words on white. */
@Composable
private fun OutlineSheetButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val view = LocalView.current
    // At least 44dp, taller rather than clipped when a large font wraps
    // "Change it on the pledge".
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).alpha(if (enabled) 1f else 0.5f).clip(RoundedCornerShape(16.dp))
            .background(GIVE.white)
            .border(1.dp, GIVE.border, RoundedCornerShape(16.dp)) // a hairline secondary (§8.1 rule 4)
            .clickable(enabled = enabled) { Haptics.tap(view); onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, textAlign = TextAlign.Center)
    }
}

/** A filled sheet action: navy (Resume, Pause gift) or gold (Save changes),
 *  a spinner in place of its words while it works. */
@Composable
private fun FilledSheetButton(
    label: String,
    navy: Boolean,
    busy: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fg = if (navy) Color.White else GIVE.navy
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).alpha(if (enabled || busy) 1f else 0.5f).clip(RoundedCornerShape(16.dp))
            .background(if (navy) GIVE.navy else GIVE.gold)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
        } else {
            Text(label, style = giInter(13, FontWeight.Bold), color = fg, textAlign = TextAlign.Center)
        }
    }
}

/** Label at the leading edge, value at the trailing one, sharing the width
 *  with a gap between (FairSplitRow) — a long value wraps, never flush
 *  against its label. */
@Composable
private fun SheetDetailRow(label: String, value: String) {
    FairSplitRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        spacing = 12.dp,
        centerVertically = true,
        first = { Text(label, style = giInter(12), color = GIVE.sub) },
        second = { Text(value, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, textAlign = TextAlign.End) },
    )
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GIVE.border))
}
