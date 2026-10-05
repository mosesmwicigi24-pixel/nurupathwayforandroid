// New pledge — the full-screen stepper behind Partners' "Add a pledge"
// (docs/PARTNERS_PROGRAMME.md §2.5), laid out and worded as iOS
// NewPledgeFlow: shape → amount → "what is this pledge for?" → due →
// "collect it automatically?" (a monthly pledge's only) → review → create
// (POST /giving/pledges, §5). One decision per screen, and the review
// repeats every choice in plain words before anything is posted, because a
// pledge is a promise and nobody should make one by accident.
//
// The chrome, as iOS: a cream band with Close, "Step 2 of 6" and a bar per
// step, then "NEW PLEDGE · A promise, in your words"; each step's own
// question and line under it; Back and Continue / Create pledge at the foot.
// A total pledge walks five steps — Back from its review lands on its date.
// Opened over the Give tab by GiveTabScreen; system back steps back, and on
// the first step closes the flow. Once anything is entered past the first
// step, Close (and system back on the first step) asks "Leave this pledge?"
// first — Keep editing / Leave (EXPERIENCE.md §7.2 #7).
//
// "What is this pledge for?" (pledge names, contract 2026-09-25) lays the
// server's `pledge_options` out on the step itself as grouped, selectable
// cards — General partnership, funds, campaigns, approved department needs,
// in the server's order, each with an icon and a one-line cue — then a
// "Custom name" card that expands a 2–60 character field in place. The wire
// rule (an option travels as its target and no title; a custom name travels
// as `title` only) is PledgeRequestLogic.kt, pinned by its test, with every
// word of the flow.
//
// Nothing here moves money. A pledge is a promise; "charge me automatically"
// asks the server to bind a schedule to it, and the server makes the charges
// on its own cycle boundaries (money §5.6 — never faked client-side). The
// first one falls on the due day strictly after today, never today (Giving
// Cycle 5): the step and the review say which day ("First collection: 5
// October"). The rails offered are the server's recurring ones (GET
// /giving/methods: M-Pesa today, M-Pesa alone until it answers). The server
// checks the collection BEFORE writing anything — no number, M-Pesa off, an
// amount M-Pesa can't take — and its words are shown; a pledge made whose
// collection still failed (auto_schedule_error) is made, and GiveTabScreen
// lands on it with the reason. The same pledge a moment ago (`reused`) is
// that pledge, not a second one — success either way. The server joins a
// member who is not yet a partner as it makes the pledge; the review says so.
package org.nuruplace.member.feature.give

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GivingMethodsRes
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PledgeOption
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.nuruplace.member.ui.theme.TypeScale

/** A full pill (§8.1 rule 6) — Give's amount choices' shape. */
private val PillShape = RoundedCornerShape(999.dp)

/** The picked card's cream — warmer than the Give tab's fund-tile tint so a
 *  chosen promise reads as "held", not merely highlighted (design 2026-09-25;
 *  no theme token carries this value). */
private val PLEDGE_FOR_SELECTED_BG = Color(0xFFFFF9EC)


/** A card's icon tile and one-line cue, by the option's kind. */
private class PledgeForLook(val icon: ImageVector, val tileBg: Color, val iconTint: Color, val subtitle: String)

/** general → the church as a whole; fund → the Give tab's own tile for it
 *  (an unknown fund code gets a gold tile and a generic line rather than the
 *  purple "gift" fallback, so nothing looks mislabelled); campaign and need
 *  by the department palette. An unknown kind still gets a card. */
private fun pledgeForLook(option: PledgeOption): PledgeForLook = when (option.kind) {
    PLEDGE_KIND_GENERAL -> PledgeForLook(Icons.Filled.Handshake, GIVE.mutedBg, Nuru.navy, "The church as a whole")
    PLEDGE_KIND_FUND -> {
        val fund = giveFund(option.fund)
        val known = GIVE_FUNDS.any { it.id.equals(option.fund?.trim(), ignoreCase = true) }
        if (known) PledgeForLook(fund.icon, fund.tint, fund.fg, fund.tagline.ifBlank { "A fund of the church" })
        else PledgeForLook(fund.icon, Nuru.goldChipBg, Nuru.gold, "A fund of the church")
    }
    PLEDGE_KIND_CAMPAIGN -> PledgeForLook(Icons.Filled.Campaign, Nuru.goldChipBg, Nuru.gold, "Church campaign")
    PLEDGE_KIND_NEED -> PledgeForLook(Icons.Filled.VolunteerActivism, Nuru.dangerBg, Nuru.danger, "Department need")
    else -> PledgeForLook(Icons.Filled.Flag, GIVE.mutedBg, Nuru.navy, "Another cause of the church")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewPledgeFlow(
    /** GET /giving/partnership `pledge_options`, handed down by GiveTabScreen
     *  from the Partners standing already loaded; fetched here only when
     *  that list is empty (PledgeRequestLogic.pledgeOptionsOrFallback fills
     *  the gap meanwhile so the picker is never blank). */
    pledgeOptions: List<PledgeOption> = emptyList(),
    /** In the Partners programme already (Partnership.isProgrammeMember).
     *  When not, the review says creating the pledge joins it. */
    isMember: Boolean = true,
    onClose: () -> Unit,
    /** The pledge as the server made it (or found it made, `reused`). */
    onCreated: (org.nuruplace.member.data.net.Pledge) -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(PledgeStep.Shape) }
    var shape by remember { mutableStateOf("monthly") }
    var amountMajor by remember { mutableIntStateOf(NEW_PLEDGE_DEFAULT_AMOUNT) }
    var customText by remember { mutableStateOf("") }
    // Default: General partnership (the programme itself).
    var target by remember { mutableStateOf<PledgeFor>(PledgeFor.Option(GENERAL_PLEDGE_OPTION)) }
    var customName by remember { mutableStateOf("") }
    var serverOptions by remember { mutableStateOf(pledgeOptions) }
    // The church's today (Nairobi) — the server's own default due day and
    // the day the first collection is counted from.
    val today = remember { partnerToday() }
    var dueDay by remember { mutableIntStateOf(today.dayOfMonth.coerceIn(1, 28)) }
    var dueOn by remember { mutableStateOf(newPledgeDefaultDueOn(today)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var autoCharge by remember { mutableStateOf(false) }
    var autoMethod by remember { mutableStateOf("mpesa") }
    // GET /giving/methods: which rails take a recurring gift here.
    var methods by remember { mutableStateOf<GivingMethodsRes?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // How the flow began, to tell whether leaving would lose anything (§7.2 #7).
    val began = remember { PledgeEntries(amountMajor, customText, target, customName, dueDay, dueOn, autoCharge) }
    var confirmLeave by remember { mutableStateOf(false) }

    // Options come with the Partners standing; a flow opened before it
    // loaded (or from an older server) asks once itself.
    LaunchedEffect(Unit) {
        if (serverOptions.isEmpty()) {
            runCatching { Net.client.api.partnership().pledgeOptions }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { serverOptions = it }
        }
    }
    // The server's rails, once. A failed read keeps M-Pesa alone — the
    // server still refuses a rail it cannot collect on, before anything is
    // written. With none that can, automatic collection is switched off.
    LaunchedEffect(Unit) {
        methods = try {
            Net.client.api.givingMethods()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val rails = pledgeAutoRails(methods)
        if (rails.isEmpty()) autoCharge = false
        else if (rails.none { it.key == autoMethod }) autoMethod = rails.first().key
    }
    val options = pledgeOptionsOrFallback(serverOptions)
    val monthly = shape == "monthly"
    val steps = pledgeSteps(monthly)
    val idx = steps.indexOf(step).coerceAtLeast(0)
    val autoRails = pledgeAutoRails(methods)
    val railName = autoRails.firstOrNull { it.key == autoMethod }?.label ?: giveMethodLabel(autoMethod)
    val canContinue = when (step) {
        PledgeStep.Amount -> amountMajor > 0
        PledgeStep.Target -> (target as? PledgeFor.Custom)?.let { pledgeTitleValid(it.name) } ?: true
        PledgeStep.Due -> if (monthly) dueDay in 1..28 else newPledgeDueOnAllowed(dueOn, today)
        else -> true
    }

    fun next() {
        error = null
        if (idx + 1 < steps.size) step = steps[idx + 1]
    }
    fun back() {
        error = null
        if (idx > 0) step = steps[idx - 1]
    }
    // System back walks back through the steps, as Back does; on the first
    // step it falls through to GiveTabScreen, which closes the flow — unless
    // something entered would be lost, when it asks first (as ✕ does).
    BackHandler(enabled = idx > 0 && !busy) { back() }
    val leaveAsks = pledgeLeaveAsks(idx, PledgeEntries(amountMajor, customText, target, customName, dueDay, dueOn, autoCharge), began)
    BackHandler(enabled = idx == 0 && leaveAsks && !busy) { confirmLeave = true }
    /** ✕: closes at once on an untouched first step; otherwise asks. */
    fun close() {
        if (leaveAsks) confirmLeave = true else onClose()
    }

    fun create() {
        if (busy) return
        if ((target as? PledgeFor.Custom)?.let { pledgeTitleValid(it.name) } == false) {
            error = PLEDGE_CUSTOM_NAME_ERROR
            return
        }
        busy = true; error = null
        val body = buildPledgeBody(
            shape = shape, amountMajor = amountMajor, target = target,
            dueDay = dueDay, dueOn = dueOn,
            autoMethod = if (autoCharge && monthly && autoRails.isNotEmpty()) autoMethod else null,
        )
        scope.launch {
            try {
                val created = Net.client.api.createPledge(body)
                Haptics.confirm(view)
                // Partners, its statement and the DUE rows count this pledge now.
                GivingEvents.emit()
                onCreated(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // PHONE_REQUIRED / METHOD_UNAVAILABLE / AMOUNT_OUT_OF_RANGE are
                // refused before anything is written: the server's words.
                error = pledgeCreateError(noAnswer = e is IOException, serverWords = ApiException.serverError(e)?.displayMessage)
                Haptics.reject(view)
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(GIVE.paper).imePadding()) {
        // ── Header: Close · "Step 2 of 6", a bar per step, the promise ──
        GiveCreamHeaderBox {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(GIVE.white).border(1.dp, GIVE.border, CircleShape)
                            .clickable(enabled = !busy) { Haptics.tap(view); close() }
                            .semantics { contentDescription = "Close" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, null, tint = GIVE.navy, modifier = Modifier.size(16.dp)) }
                    Spacer(Modifier.weight(1f))
                    Text("Step ${idx + 1} of ${steps.size}", style = giInter(11, FontWeight.SemiBold), color = GIVE.tertiary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    steps.forEachIndexed { i, _ ->
                        Box(
                            Modifier.weight(1f).height(4.dp).clip(CircleShape)
                                .background(if (i <= idx) GIVE.gold else GIVE.navy.copy(alpha = 0.10f)),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NEW PLEDGE", style = giInter(11, FontWeight.Bold, 1.4f), color = GIVE.eyebrow)
                    Text("A promise, in your words", style = giSerif(26, FontWeight.SemiBold), color = GIVE.navy)
                }
            }
        }

        // ── The step: its question, its line, its choice ──
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pledgeStepTitle(step, monthly), style = giSerif(22, FontWeight.Medium), color = GIVE.ink)
                Text(pledgeStepSubtitle(step, monthly), style = giInter(14), color = GIVE.ink600)
            }
            when (step) {
                PledgeStep.Shape -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShapeCard(
                        on = monthly, icon = Icons.Filled.EventRepeat, title = "Monthly",
                        body = "An amount every month, for as long as you choose.",
                    ) { Haptics.tick(view); shape = "monthly" }
                    ShapeCard(
                        on = !monthly, icon = Icons.Filled.TrackChanges, title = "A total, by a date",
                        body = "A goal you reach in instalments of any size.",
                    ) { Haptics.tick(view); shape = "total"; autoCharge = false }
                }
                PledgeStep.Amount -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GIVE.white.copy(alpha = 0.6f))
                        .border(1.dp, GIVE.border, RoundedCornerShape(22.dp)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AMOUNT", style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.tertiary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("KSh", style = giInter(14, FontWeight.Medium), color = GIVE.tertiary, modifier = Modifier.alignByBaseline())
                            val shown = "%,d".format(amountMajor)
                            Text(
                                shown, style = giSerif(TypeScale.amount(shown), FontWeight.SemiBold, -1.2f), color = GIVE.navy,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                        Text(if (monthly) "each month" else "in total", style = giInter(11), color = GIVE.sub)
                    }
                    // The suggested amounts as pills, as Give draws them
                    // (EXPERIENCE.md §8.1 rule 6, §8.2 #12) — they were square
                    // tiles. Every one in sight: they wrap, centred, rather
                    // than scroll a choice off the card.
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NEW_PLEDGE_PRESETS.forEach { v ->
                            val on = amountMajor == v && customText.isEmpty()
                            Text(
                                "%,d".format(v),
                                style = giInter(13, FontWeight.SemiBold),
                                color = if (on) Color.White else GIVE.navy,
                                modifier = Modifier.clip(PillShape)
                                    .background(if (on) GIVE.navy else GIVE.surface)
                                    .then(if (on) Modifier else Modifier.border(1.dp, GIVE.border, PillShape))
                                    .clickable { Haptics.tick(view); customText = ""; amountMajor = v }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { v ->
                            customText = v.filter { it.isDigit() }.take(8)
                            customText.toIntOrNull()?.takeIf { it > 0 }?.let { amountMajor = it }
                        },
                        singleLine = true,
                        placeholder = { Text("Or enter your own amount", style = giInter(14), color = Nuru.ink400) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null, tint = GIVE.gold, modifier = Modifier.size(15.dp)) },
                        textStyle = giInter(14).copy(color = Nuru.ink),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PledgeStep.Target -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val customOn = target is PledgeFor.Custom
                    val customFocus = remember { FocusRequester() }
                    // One tap picks; a tap on the card already showing as picked
                    // is a no-op (no second tick — and General stays whichever
                    // row lit it, local default or the server's). The typed
                    // custom name survives a switch away and back: it lives in
                    // customName, not in the card.
                    fun pick(next: PledgeFor) {
                        val already = when (next) {
                            is PledgeFor.Option -> pledgeOptionSelected(next.option, target)
                            is PledgeFor.Custom -> target is PledgeFor.Custom
                        }
                        if (already) return
                        Haptics.tick(view); target = next
                    }
                    groupedPledgeOptions(options).forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PledgeForEyebrow(group.label)
                            group.options.forEach { o ->
                                val look = pledgeForLook(o)
                                PledgeForCard(
                                    selected = pledgeOptionSelected(o, target),
                                    icon = look.icon, tileBg = look.tileBg, iconTint = look.iconTint,
                                    title = o.title.ifBlank { GENERAL_PLEDGE_OPTION.title }, subtitle = look.subtitle,
                                    onSelect = { pick(PledgeFor.Option(o)) },
                                )
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PledgeForEyebrow("Name it yourself")
                        PledgeForCard(
                            selected = customOn,
                            icon = Icons.Filled.Edit, tileBg = Nuru.goldChipBg, iconTint = Nuru.gold,
                            title = "Custom name", subtitle = "A promise in your own words",
                            onSelect = { pick(PledgeFor.Custom(customName)) },
                        ) {
                            if (customOn) {
                                val count = customName.trim().length
                                val valid = pledgeTitleValid(customName)
                                OutlinedTextField(
                                    value = customName,
                                    onValueChange = { v ->
                                        customName = v.take(PLEDGE_TITLE_MAX)
                                        target = PledgeFor.Custom(customName)
                                    },
                                    singleLine = true,
                                    placeholder = { Text("e.g. School fees for Grace", style = giInter(14), color = Nuru.ink400) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = GIVE.gold, modifier = Modifier.size(15.dp)) },
                                    suffix = {
                                        Text(
                                            "$count/$PLEDGE_TITLE_MAX", style = giInter(11),
                                            color = if (valid || count == 0) Nuru.ink400 else Nuru.danger,
                                        )
                                    },
                                    isError = !valid && count > 0,
                                    textStyle = giInter(14).copy(color = Nuru.ink),
                                    modifier = Modifier.fillMaxWidth().focusRequester(customFocus),
                                )
                                Text(PLEDGE_CUSTOM_NAME_HELP, style = giInter(12), color = Nuru.ink400)
                                // The field exists in this same composition, so
                                // the requester is attached by the time this runs.
                                LaunchedEffect(Unit) { customFocus.requestFocus() }
                            }
                        }
                    }
                }
                PledgeStep.Due -> if (monthly) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GIVE.white.copy(alpha = 0.6f))
                            .border(1.dp, GIVE.border, RoundedCornerShape(22.dp)).padding(12.dp),
                    ) {
                        DueDayPicker(dueDay) { dueDay = it }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GIVE.white)
                            .border(1.dp, GIVE.border, RoundedCornerShape(22.dp))
                            .clickable { showDatePicker = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = GIVE.gold, modifier = Modifier.size(20.dp))
                        Text(org.nuruplace.member.util.NuruDates.day(dueOn), style = giInter(15, FontWeight.SemiBold), color = GIVE.ink)
                    }
                    if (showDatePicker) {
                        // Only days after today — a total is reached by a date to come.
                        val state = rememberDatePickerState(
                            initialSelectedDateMillis = dueOn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                            selectableDates = object : SelectableDates {
                                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                                    newPledgeDueOnAllowed(Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate(), today)
                                override fun isSelectableYear(year: Int): Boolean = year >= today.year
                            },
                        )
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    state.selectedDateMillis?.let { ms ->
                                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                                        if (newPledgeDueOnAllowed(d, today)) dueOn = d
                                    }
                                    showDatePicker = false
                                }) { Text("Set date", style = NuruType.cardCta, color = Nuru.gold) }
                            },
                            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", style = NuruType.cardCta, color = Nuru.ink600) } },
                        ) { DatePicker(state = state) }
                    }
                }
                PledgeStep.Auto -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GIVE.white)
                            .border(1.dp, GIVE.border, RoundedCornerShape(16.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Charge me automatically", style = giInter(15, FontWeight.SemiBold), color = GIVE.ink)
                            Text(pledgeAutoCaption(dueDay), style = giInter(12), color = GIVE.ink600)
                        }
                        Switch(
                            checked = autoCharge, enabled = autoRails.isNotEmpty(),
                            onCheckedChange = { Haptics.tick(view); autoCharge = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Nuru.gold, checkedThumbColor = Color.White),
                        )
                    }
                    when {
                        autoRails.isEmpty() -> Text(PLEDGE_AUTO_UNAVAILABLE_NOTE, style = giInter(12), color = Nuru.ink400)
                        autoCharge -> {
                            // The day the member can hold us to — the server's
                            // own rule: the due day strictly after today.
                            Row(
                                Modifier.semantics(mergeDescendants = true) {},
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Filled.EventRepeat, null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
                                Text(firstCollectionLine(today, dueDay), style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
                            }
                            Text("BY", style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                autoRails.forEach { rail ->
                                    RailChip(rail.key, rail.label, on = autoMethod == rail.key, modifier = Modifier.weight(1f)) {
                                        Haptics.tick(view); autoMethod = rail.key
                                    }
                                }
                            }
                            Text(pledgeAutoOnNote(dueDay), style = giInter(12), color = Nuru.ink400)
                        }
                        else -> Text(PLEDGE_AUTO_OFF_NOTE, style = giInter(12), color = Nuru.ink400)
                    }
                }
                PledgeStep.Review -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GIVE.white)
                        .border(1.dp, GIVE.gold.copy(alpha = 0.28f), RoundedCornerShape(22.dp)).padding(16.dp),
                ) {
                    pledgeReviewRows(
                        monthly = monthly, amountMajor = amountMajor, forName = pledgeForTitle(target),
                        dueDay = dueDay, dueOn = dueOn,
                        autoRail = if (autoCharge && autoRails.isNotEmpty()) railName else null,
                        firstCollection = firstCollectionDay(today, dueDay),
                    ).forEach { (label, value) -> ReviewRow(label, value) }
                    if (!isMember) {
                        Row(
                            Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Filled.Handshake, null, tint = GIVE.gold, modifier = Modifier.size(13.dp))
                            Text(PLEDGE_JOINS_NOTE, style = giInter(12), color = GIVE.goldChipText)
                        }
                    }
                }
            }
            // A refusal or a lost answer, said under the step it came from.
            error?.let { Text(it, style = giInter(12), color = Nuru.danger) }
        }

        // ── Back · Continue / Create pledge ──
        Box(Modifier.fillMaxWidth().height(1.dp).background(GIVE.border))
        Row(
            Modifier.fillMaxWidth().background(GIVE.paper).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (step != PledgeStep.Shape) {
                Row(
                    Modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(16.dp))
                        .clickable(enabled = !busy) { Haptics.tap(view); back() }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
                    Text("Back", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
                }
            }
            val last = step == PledgeStep.Review
            Row(
                Modifier.weight(1f).height(48.dp).alpha(if (canContinue) 1f else 0.6f)
                    .clip(RoundedCornerShape(16.dp)).background(Nuru.goldGradient)
                    .clickable(enabled = canContinue && !busy) { Haptics.tick(view); if (last) create() else next() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                when {
                    busy -> {
                        CircularProgressIndicator(Modifier.size(16.dp), color = GIVE.navy, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Creating…", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
                    }
                    last -> {
                        Text("Create pledge", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Check, null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
                    }
                    else -> {
                        Text("Continue", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    // Leaving part-made asks first (§7.2 #7): Keep editing / Leave.
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(PLEDGE_LEAVE_TITLE, style = NuruType.cardTitle, color = Nuru.navy) },
            text = { Text(PLEDGE_LEAVE_LINE, style = NuruType.body, color = Nuru.ink600) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onClose() }) {
                    Text(PLEDGE_LEAVE_GO, style = NuruType.cardCta, color = Nuru.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(PLEDGE_LEAVE_STAY, style = NuruType.cardCta, color = Nuru.navy)
                }
            },
        )
    }
}

/** A section's small label on the "what is this pledge for?" step — the
 *  Give tab's "CHOOSE A FUND" eyebrow, so the two screens read as one. */
@Composable
private fun PledgeForEyebrow(label: String) {
    Text(label.uppercase(), style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
}

/** One selectable card on the "what is this pledge for?" step: icon tile,
 *  title, one-line cue, and a ring — gold with a check once picked (the Give
 *  tab's fund tile, laid out as a row). [expanded] renders inside the same
 *  card under the row — the custom-name card puts its field there. */
@Composable
private fun PledgeForCard(
    selected: Boolean,
    icon: ImageVector,
    tileBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
    expanded: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) PLEDGE_FOR_SELECTED_BG else Nuru.white)
            .border(if (selected) 2.dp else 1.dp, if (selected) Nuru.gold else Nuru.border, shape)
            .clickable { onSelect() }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tileBg), Alignment.Center) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Two lines for a long campaign or need name, as iOS allows.
                Text(title, style = giInter(15, FontWeight.SemiBold), color = Nuru.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = giInter(12), color = GIVE.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Nuru.gold), Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Nuru.navyDeep, modifier = Modifier.size(12.dp))
                }
            } else {
                Box(Modifier.size(22.dp).border(1.5.dp, GIVE.border, CircleShape))
            }
        }
        expanded()
    }
}

/** Monthly · A total, by a date: a gold tile when chosen, and iOS's ring. */
@Composable
private fun ShapeCard(on: Boolean, icon: ImageVector, title: String, body: String, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GIVE.white)
            .border(1.dp, if (on) GIVE.gold.copy(alpha = 0.6f) else GIVE.border, RoundedCornerShape(16.dp))
            .clickable { onSelect() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (on) GIVE.gold else GIVE.gold.copy(alpha = 0.12f)),
            Alignment.Center,
        ) {
            Icon(icon, null, tint = if (on) GIVE.navy else GIVE.gold, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = giInter(15, FontWeight.SemiBold), color = GIVE.ink)
            Text(body, style = giInter(12), color = GIVE.ink600)
        }
        Box(Modifier.size(20.dp).border(if (on) 6.dp else 1.5.dp, if (on) GIVE.gold else GIVE.border, CircleShape))
    }
}

/** A rail for the pledge's collection: its badge ("M" green, "A" red) and
 *  name; navy once chosen. */
@Composable
private fun RailChip(key: String, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val badge = GIVE_METHODS.firstOrNull { it.provider == key }
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp)).background(if (on) GIVE.navy else GIVE.white)
            .border(1.dp, if (on) Color.Transparent else GIVE.border, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(badge?.badgeBg ?: GIVE.navy),
            Alignment.Center,
        ) {
            Text(if (key == "mpesa") "M" else "A", style = giInter(11, FontWeight.Bold), color = badge?.badgeFg ?: Color.White)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = giInter(13, FontWeight.SemiBold), color = if (on) Color.White else GIVE.navy)
    }
}

/** Label at the leading edge, value at the trailing one, sharing the width
 *  (FairSplitRow) so a long pledge name or a large font wraps each side
 *  instead of squeezing the other. */
@Composable
private fun ReviewRow(label: String, value: String) {
    FairSplitRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        spacing = 12.dp,
        first = { Text(label, style = giInter(14), color = GIVE.ink600) },
        second = { Text(value, style = giInter(14, FontWeight.SemiBold), color = GIVE.ink, textAlign = TextAlign.End) },
    )
}
