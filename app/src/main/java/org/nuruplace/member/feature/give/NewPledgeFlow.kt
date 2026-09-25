// New pledge — the full-screen stepper behind Partners' "Add a pledge"
// (docs/PARTNERS_PROGRAMME.md §2.5): shape → amount → "what is this pledge
// for?" → due → "charge me automatically" → review → create (POST
// /giving/pledges, §5). Opened over the Give tab by GiveTabScreen; system
// back closes it.
//
// "What is this pledge for?" (pledge names, contract 2026-09-25) is a picker
// over the server's `pledge_options` — General partnership, funds, campaigns,
// approved department needs, in the server's order — plus "Custom name…",
// which reveals a 2–60 character field. The wire rule (an option travels as
// its target and no title; a custom name travels as `title` only) is
// PledgeRequestLogic.kt, pinned by its test.
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PledgeOption
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
    Target("What is this pledge for?"),
    Due("When is it due?"),
    Auto("Charge it automatically?"),
    Review("Review your pledge"),
}

private val PLEDGE_PRESETS = listOf(500, 1_000, 2_500, 5_000, 10_000, 20_000)

/** The field's second line for the chosen target. */
private fun pledgeForSubline(target: PledgeFor): String = when (target) {
    is PledgeFor.Custom -> "Your own name for it"
    is PledgeFor.Option -> when (target.option.kind) {
        PLEDGE_KIND_FUND -> "A fund"
        PLEDGE_KIND_CAMPAIGN -> "A campaign"
        PLEDGE_KIND_NEED -> "A department need"
        PLEDGE_KIND_GENERAL -> "Wherever the need is greatest"
        else -> "A target the church named"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewPledgeFlow(
    /** GET /giving/partnership `pledge_options`, handed down by GiveTabScreen
     *  from the Partners standing already loaded; fetched here only when
     *  that list is empty (PledgeRequestLogic.pledgeOptionsOrFallback fills
     *  the gap meanwhile so the picker is never blank). */
    pledgeOptions: List<PledgeOption> = emptyList(),
    onClose: () -> Unit,
    onCreated: () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(PledgeStep.Shape) }
    var shape by remember { mutableStateOf("monthly") }
    var amountMajor by remember { mutableIntStateOf(1_000) }
    var customText by remember { mutableStateOf("") }
    // Default: General partnership (the programme itself).
    var target by remember { mutableStateOf<PledgeFor>(PledgeFor.Option(GENERAL_PLEDGE_OPTION)) }
    var customName by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var serverOptions by remember { mutableStateOf(pledgeOptions) }
    var dueDay by remember { mutableIntStateOf(LocalDate.now().dayOfMonth.coerceIn(1, 28)) }
    var dueOn by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var autoCharge by remember { mutableStateOf(false) }
    var autoMethod by remember { mutableStateOf("mpesa") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Options come with the Partners standing; a flow opened before it
    // loaded (or from an older server) asks once itself.
    LaunchedEffect(Unit) {
        if (serverOptions.isEmpty()) {
            runCatching { Net.client.api.partnership().pledgeOptions }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { serverOptions = it }
        }
    }
    val options = pledgeOptionsOrFallback(serverOptions)

    val steps = PledgeStep.entries
    val idx = steps.indexOf(step)
    val canContinue = when (step) {
        PledgeStep.Amount -> amountMajor > 0
        PledgeStep.Target -> (target as? PledgeFor.Custom)?.let { pledgeTitleValid(it.name) } ?: true
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
            shape = shape, amountMajor = amountMajor, target = target,
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
                    Text(
                        "General partnership goes wherever the need is greatest. Or point it at a fund, a campaign or a department need — or give it a name of your own.",
                        style = NuruType.body, color = Nuru.ink600,
                    )
                    Text("FOR", style = NuruType.micro, color = Nuru.goldLo)
                    // The field: what it's for, a chevron; tap → the picker sheet.
                    val custom = target is PledgeFor.Custom
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.white)
                            .border(1.dp, Nuru.border, RoundedCornerShape(14.dp))
                            .clickable { Haptics.tick(view); pickerOpen = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(if (custom) Icons.Filled.Edit else Icons.Filled.Flag, null, tint = Nuru.gold, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (custom) "Custom name" else pledgeForTitle(target), style = NuruType.heading, color = Nuru.ink)
                            Text(pledgeForSubline(target), style = NuruType.caption, color = Nuru.ink600)
                        }
                        Icon(Icons.Filled.ExpandMore, "Choose", tint = Nuru.ink400, modifier = Modifier.size(22.dp))
                    }
                    if (custom) {
                        val valid = pledgeTitleValid(customName)
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { v ->
                                customName = v.take(PLEDGE_TITLE_MAX)
                                target = PledgeFor.Custom(customName)
                            },
                            singleLine = true,
                            placeholder = { Text("Name your pledge", style = NuruType.body, color = Nuru.ink400) },
                            supportingText = {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(
                                        if (!valid && customName.isNotBlank()) "$PLEDGE_TITLE_MIN–$PLEDGE_TITLE_MAX characters" else "",
                                        style = NuruType.caption, color = Nuru.danger, modifier = Modifier.weight(1f),
                                    )
                                    Text("${customName.length}/$PLEDGE_TITLE_MAX", style = NuruType.caption, color = Nuru.ink400)
                                }
                            },
                            isError = !valid && customName.isNotBlank(),
                            textStyle = nuruSans(16, FontWeight.Medium).copy(color = Nuru.ink),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Your name is what the card and every reminder will call it.", style = NuruType.caption, color = Nuru.ink400)
                    }
                    if (pickerOpen) {
                        PledgeForPicker(
                            options = options, selected = target,
                            onPick = { Haptics.tick(view); target = it; pickerOpen = false },
                            onCustom = { Haptics.tick(view); target = PledgeFor.Custom(customName); pickerOpen = false },
                            onDismiss = { pickerOpen = false },
                        )
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
                        ReviewRow("For", pledgeForTitle(target))
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

/** The "what is this pledge for?" picker — the app's bottom-sheet idiom
 *  (PartnersScreen's sheets): every server option by title, grouped under
 *  small section labels in the server's order, then "Custom name…" last. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PledgeForPicker(
    options: List<PledgeOption>,
    selected: PledgeFor,
    onPick: (PledgeFor) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picked = (selected as? PledgeFor.Option)?.option
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Nuru.paper) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("What is this pledge for?", style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
            groupedPledgeOptions(options).forEach { group ->
                Text(group.label.uppercase(), style = NuruType.micro, color = Nuru.goldLo, modifier = Modifier.padding(top = 6.dp))
                group.options.forEach { o ->
                    // General matches by kind too: the local default and the
                    // server's own General row may not share a key.
                    val on = picked != null && (o.key == picked.key || (o.kind == PLEDGE_KIND_GENERAL && picked.kind == PLEDGE_KIND_GENERAL))
                    PickerRow(selected = on, title = o.title, icon = null) { onPick(PledgeFor.Option(o)) }
                }
            }
            Text("OR", style = NuruType.micro, color = Nuru.goldLo, modifier = Modifier.padding(top = 6.dp))
            PickerRow(selected = selected is PledgeFor.Custom, title = "Custom name…", icon = Icons.Filled.Edit) { onCustom() }
        }
    }
}

@Composable
private fun PickerRow(selected: Boolean, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Nuru.goldChipBg else Nuru.white)
            .border(1.dp, if (selected) Nuru.gold else Nuru.border, RoundedCornerShape(14.dp))
            .clickable { onSelect() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Nuru.gold, modifier = Modifier.size(16.dp))
        Text(title, style = NuruType.label, color = Nuru.ink, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (selected) Nuru.gold else Nuru.white)
                .border(1.dp, if (selected) Nuru.gold else Nuru.ink300, CircleShape),
            Alignment.Center,
        ) { if (selected) Icon(Icons.Filled.Check, null, tint = Nuru.navyDeep, modifier = Modifier.size(12.dp)) }
    }
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
