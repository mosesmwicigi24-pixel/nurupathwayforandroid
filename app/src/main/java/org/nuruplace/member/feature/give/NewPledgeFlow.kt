// New pledge — the full-screen stepper behind Partners' "Add a pledge"
// (docs/PARTNERS_PROGRAMME.md §2.5): shape → amount → target → due →
// "charge me automatically" → review → create (POST /giving/pledges, §5).
// Opened over the Give tab by GiveTabScreen; system back closes it.
//
// Nothing here moves money. A pledge is a promise; "charge me automatically"
// asks the server to bind a schedule to it, and the server makes the charges
// on its own cycle boundaries (money §5.6 — never faked client-side).
package org.nuruplace.member.feature.give

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.AutoScheduleBody
import org.nuruplace.member.data.net.CreatePledgeBody
import org.nuruplace.member.data.net.InviteCampaign
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class PledgeStep(val title: String) {
    Shape("What kind of pledge?"),
    Amount("How much?"),
    Target("Toward what?"),
    Due("When is it due?"),
    Auto("Charge it automatically?"),
    Review("Review your pledge"),
}

/** The target step's choice — none (general partnership), a fund, or a campaign. */
private sealed interface PledgeTarget {
    data object None : PledgeTarget
    data class Fund(val code: String, val name: String) : PledgeTarget
    data class Campaign(val id: String, val title: String) : PledgeTarget
}

private val PLEDGE_PRESETS = listOf(500, 1_000, 2_500, 5_000, 10_000, 20_000)

/** Build the exact wire body (spec §5) from the flow's choices. Pure, so the
 *  shape-dependent field selection is easy to read and to test. */
internal fun buildPledgeBody(
    shape: String,
    amountMajor: Int,
    fundCode: String?,
    campaignId: String?,
    dueDay: Int?,
    dueOn: LocalDate?,
    autoMethod: String?,
): CreatePledgeBody = CreatePledgeBody(
    shape = shape,
    amountMinor = if (shape == "monthly") amountMajor * 100 else null,
    targetMinor = if (shape == "total") amountMajor * 100 else null,
    currency = "KES",
    dueDay = if (shape == "monthly") dueDay else null,
    dueOn = if (shape == "total") dueOn?.toString() else null,
    fund = fundCode,
    campaignId = campaignId,
    autoSchedule = autoMethod?.let { AutoScheduleBody(method = it, frequency = "monthly") },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewPledgeFlow(onClose: () -> Unit, onCreated: () -> Unit) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(PledgeStep.Shape) }
    var shape by remember { mutableStateOf("monthly") }
    var amountMajor by remember { mutableIntStateOf(1_000) }
    var customText by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<PledgeTarget>(PledgeTarget.None) }
    var dueDay by remember { mutableIntStateOf(LocalDate.now().dayOfMonth.coerceIn(1, 28)) }
    var dueOn by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var autoCharge by remember { mutableStateOf(false) }
    var autoMethod by remember { mutableStateOf("mpesa") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Campaigns: the invitation endpoint is the one place the server names a
    // campaign a member may point at today (spec §2.5 "campaign … if any").
    var campaign by remember { mutableStateOf<InviteCampaign?>(null) }
    LaunchedEffect(Unit) {
        campaign = runCatching { Net.client.api.partnerInvite().campaign }.getOrNull()?.takeIf { it.campaignId.isNotBlank() }
    }

    val steps = PledgeStep.entries
    val idx = steps.indexOf(step)
    val canContinue = when (step) {
        PledgeStep.Amount -> amountMajor > 0
        PledgeStep.Due -> if (shape == "total") dueOn != null else dueDay in 1..28
        else -> true
    }

    fun back() { if (idx == 0) onClose() else step = steps[idx - 1] }
    fun next() {
        Haptics.tick(view)
        if (step == PledgeStep.Due && shape == "total") {
            // Auto-charge is monthly by definition; a total-by-date pledge is
            // paid in instalments the member chooses, so skip the question.
            step = PledgeStep.Review; return
        }
        step = steps[idx + 1]
    }
    fun create() {
        busy = true; error = null
        val body = buildPledgeBody(
            shape = shape, amountMajor = amountMajor,
            fundCode = (target as? PledgeTarget.Fund)?.code,
            campaignId = (target as? PledgeTarget.Campaign)?.id,
            dueDay = dueDay, dueOn = dueOn,
            autoMethod = if (autoCharge && shape == "monthly") autoMethod else null,
        )
        scope.launch {
            try {
                Net.client.api.createPledge(body)
                Haptics.confirm(view)
                onCreated()
            } catch (e: Exception) {
                error = ApiException.message(e)
                Haptics.reject(view)
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).imePadding()) {
        // ── Header ──
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Nuru.white).border(1.dp, Nuru.border, CircleShape)
                        .clickable(enabled = !busy) { back() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nuru.navy, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    steps.forEachIndexed { i, _ ->
                        Box(Modifier.size(if (i == idx) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (i <= idx) Nuru.gold else Nuru.ink300))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose, enabled = !busy) { Text("Close", style = NuruType.cardCta, color = Nuru.ink600) }
            }
            Text("NEW PLEDGE", style = NuruType.kicker, color = Nuru.goldLo, modifier = Modifier.padding(top = 12.dp))
            Text(step.title, style = nuruSerif(26, FontWeight.Medium), color = Nuru.ink)
        }

        // ── Step body ──
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                PledgeStep.Shape -> {
                    ChoiceCard(
                        selected = shape == "monthly", icon = Icons.Filled.Autorenew,
                        title = "An amount every month",
                        body = "Open-ended. Pause, change or stop it whenever you need to.",
                    ) { shape = "monthly" }
                    ChoiceCard(
                        selected = shape == "total", icon = Icons.Filled.Flag,
                        title = "A total by a date",
                        body = "Paid in any instalments you like, until it's fulfilled.",
                    ) { shape = "total" }
                }
                PledgeStep.Amount -> {
                    Text(if (shape == "monthly") "EACH MONTH" else "IN TOTAL", style = NuruType.micro, color = Nuru.goldLo)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("KSh", style = nuruSans(14, FontWeight.Medium), color = Nuru.ink400)
                        Text("%,d".format(amountMajor), style = nuruSerif(42, FontWeight.SemiBold), color = Nuru.ink)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PLEDGE_PRESETS.forEach { p ->
                            val on = amountMajor == p && customText.isBlank()
                            Text(
                                "%,d".format(p), style = nuruSans(13, FontWeight.SemiBold), color = if (on) Color.White else Nuru.navy,
                                modifier = Modifier.clip(CircleShape).background(if (on) Nuru.navyDeep else Nuru.white)
                                    .border(1.dp, if (on) Nuru.navyDeep else Nuru.border, CircleShape)
                                    .clickable { amountMajor = p; customText = "" }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { v ->
                            customText = v.filter { it.isDigit() }.take(8)
                            customText.toIntOrNull()?.let { amountMajor = it }
                        },
                        singleLine = true,
                        placeholder = { Text("Or enter your own amount", style = NuruType.body, color = Nuru.ink400) },
                        prefix = { Text("KSh ", style = NuruType.body, color = Nuru.ink600) },
                        textStyle = nuruSans(16, FontWeight.Medium).copy(color = Nuru.ink),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (shape == "monthly") {
                        Text(
                            "KSh 20,000 a month carries one disciple through a level.",
                            style = NuruType.caption, color = Nuru.ink400,
                        )
                    }
                }
                PledgeStep.Target -> {
                    Text("Optional. A pledge can stay general — that's the programme itself.", style = NuruType.body, color = Nuru.ink600)
                    ChoiceRow(selected = target == PledgeTarget.None, title = "General partnership", sub = "Wherever the need is greatest") { target = PledgeTarget.None }
                    Text("A FUND", style = NuruType.micro, color = Nuru.goldLo)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GIVE_FUNDS.forEach { f ->
                            val on = (target as? PledgeTarget.Fund)?.code == f.id
                            Row(
                                Modifier.clip(CircleShape).background(if (on) Nuru.navyDeep else Nuru.white)
                                    .border(1.dp, if (on) Nuru.navyDeep else Nuru.border, CircleShape)
                                    .clickable { target = PledgeTarget.Fund(f.id, f.name) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(f.icon, null, tint = if (on) Nuru.gold else f.fg, modifier = Modifier.size(13.dp))
                                Text(f.name, style = nuruSans(13, FontWeight.SemiBold), color = if (on) Color.White else Nuru.navy)
                            }
                        }
                    }
                    campaign?.let { c ->
                        Text("A CAMPAIGN", style = NuruType.micro, color = Nuru.goldLo)
                        ChoiceRow(
                            selected = (target as? PledgeTarget.Campaign)?.id == c.campaignId,
                            title = c.title,
                            sub = "${money(c.raisedMinor, c.currency)} of ${money(c.goalMinor, c.currency)} · ${c.daysLeft} days left",
                        ) { target = PledgeTarget.Campaign(c.campaignId, c.title) }
                    }
                }
                PledgeStep.Due -> {
                    if (shape == "monthly") {
                        Text("Pick the day of the month. 1–28, so every month has it.", style = NuruType.body, color = Nuru.ink600)
                        DueDayPicker(dueDay) { dueDay = it }
                        Text("Reminders, if you keep them on, arrive three days before.", style = NuruType.caption, color = Nuru.ink400)
                    } else {
                        Text("The date you'd like the total fulfilled by.", style = NuruType.body, color = Nuru.ink600)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.white)
                                .border(1.dp, Nuru.border, RoundedCornerShape(14.dp))
                                .clickable { showDatePicker = true }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Filled.CalendarMonth, null, tint = Nuru.gold, modifier = Modifier.size(20.dp))
                            Text(
                                dueOn?.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())) ?: "Choose a date",
                                style = NuruType.heading, color = if (dueOn == null) Nuru.ink400 else Nuru.ink,
                            )
                        }
                        if (showDatePicker) {
                            val state = rememberDatePickerState(
                                initialSelectedDateMillis = (dueOn ?: LocalDate.now().plusMonths(6)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                            )
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        state.selectedDateMillis?.let { ms ->
                                            val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                                            if (!d.isBefore(LocalDate.now())) dueOn = d
                                        }
                                        showDatePicker = false
                                    }) { Text("Set date", style = NuruType.cardCta, color = Nuru.gold) }
                                },
                                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", style = NuruType.cardCta, color = Nuru.ink600) } },
                            ) { DatePicker(state = state) }
                        }
                    }
                }
                PledgeStep.Auto -> {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.white)
                            .border(1.dp, Nuru.border, RoundedCornerShape(14.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Charge me automatically", style = NuruType.heading, color = Nuru.ink)
                            Text(
                                "A recurring gift of ${kshMajor(amountMajor)} on the ${ordinalDay(dueDay)} of each month, bound to this pledge. Cancel anytime.",
                                style = NuruType.caption, color = Nuru.ink600,
                            )
                        }
                        Switch(
                            checked = autoCharge, onCheckedChange = { Haptics.tick(view); autoCharge = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Nuru.gold, checkedThumbColor = Color.White),
                        )
                    }
                    if (autoCharge) {
                        Text("WITH", style = NuruType.micro, color = Nuru.goldLo)
                        GIVE_METHODS.filter { it.provider in RECURRING_PROVIDERS }.forEach { m ->
                            ChoiceRow(selected = autoMethod == m.provider, title = m.label, sub = m.sub) { autoMethod = m.provider!! }
                        }
                        Text("Recurring gifts run on M-Pesa or Airtel Money. The first charge is made by the server on the next cycle — never from this screen.", style = NuruType.caption, color = Nuru.ink400)
                    } else {
                        Text("You'll pay each month yourself from Partners — with a gentle reminder before it's due, if you keep reminders on.", style = NuruType.caption, color = Nuru.ink400)
                    }
                }
                PledgeStep.Review -> {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.white)
                            .border(1.dp, Nuru.gold.copy(alpha = 0.28f), RoundedCornerShape(14.dp)).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(kshMajor(amountMajor), style = nuruSerif(28, FontWeight.Medium), color = Nuru.ink)
                            Text(if (shape == "monthly") "each month" else "in total", style = NuruType.body, color = Nuru.ink600, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        ReviewRow("Toward", when (val t = target) {
                            is PledgeTarget.Fund -> t.name
                            is PledgeTarget.Campaign -> t.title
                            PledgeTarget.None -> "General partnership"
                        })
                        ReviewRow(
                            if (shape == "monthly") "Due" else "By",
                            if (shape == "monthly") "The ${ordinalDay(dueDay)} of each month"
                            else dueOn?.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())) ?: "—",
                        )
                        ReviewRow(
                            "Charged automatically",
                            if (shape == "monthly" && autoCharge) (if (autoMethod == "airtel") "Yes · Airtel Money" else "Yes · M-Pesa") else "No — I'll pay myself",
                        )
                    }
                    Text(
                        "A pledge is a promise, not a charge. You can pause, change or cancel it from Partners at any time, and nothing is ever owed.",
                        style = NuruType.caption, color = Nuru.ink400,
                    )
                }
            }
        }

        // ── Sticky CTA ──
        Column(Modifier.fillMaxWidth().background(Nuru.paper).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp)) {
            error?.let { Text(it, style = NuruType.caption, color = Nuru.danger, modifier = Modifier.padding(bottom = 8.dp)) }
            val last = step == PledgeStep.Review
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (canContinue && !busy) Nuru.goldGradient else androidx.compose.ui.graphics.SolidColor(Nuru.ink300))
                    .clickable(enabled = canContinue && !busy) { if (last) create() else next() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Nuru.navyDeep, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Creating…", style = nuruSans(14, FontWeight.Bold), color = Nuru.navyDeep)
                } else {
                    Text(if (last) "Create pledge" else "Continue", style = nuruSans(14, FontWeight.Bold), color = Nuru.navyDeep)
                    if (last) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.Check, null, tint = Nuru.navyDeep, modifier = Modifier.size(15.dp)) }
                }
            }
        }
    }
}

private fun ordinalDay(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

@Composable
private fun ChoiceCard(selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Nuru.goldChipBg else Nuru.white)
            .border(if (selected) 2.dp else 1.dp, if (selected) Nuru.gold else Nuru.border, RoundedCornerShape(16.dp))
            .clickable { onSelect() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Nuru.gold else Nuru.surface), Alignment.Center) {
            Icon(icon, null, tint = if (selected) Nuru.navyDeep else Nuru.ink600, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = NuruType.heading, color = Nuru.ink)
            Text(body, style = NuruType.caption, color = Nuru.ink600)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = Nuru.gold, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, sub: String, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Nuru.goldChipBg else Nuru.white)
            .border(1.dp, if (selected) Nuru.gold else Nuru.border, RoundedCornerShape(14.dp))
            .clickable { onSelect() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NuruType.label, color = Nuru.ink)
            Text(sub, style = NuruType.caption, color = Nuru.ink600)
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (selected) Nuru.gold else Nuru.white)
                .border(1.dp, if (selected) Nuru.gold else Nuru.ink300, CircleShape),
            Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, null, tint = Nuru.navyDeep, modifier = Modifier.size(12.dp)) }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = NuruType.body, color = Nuru.ink600)
        Text(value, style = NuruType.body, color = Nuru.ink, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
    }
}
