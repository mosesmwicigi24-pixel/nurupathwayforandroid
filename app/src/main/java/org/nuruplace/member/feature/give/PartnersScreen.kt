package org.nuruplace.member.feature.give

// The Partners portal — Give → Partners (docs/PARTNERS_PROGRAMME.md §2). The
// Android half of the same design as iOS PartnersView.swift; keep in step.
//
// Six things, in the spec's order: (1) standing — or, for a non-partner, the
// invitation to JOIN the programme (no fund, no campaign, no money: §1);
// (2) my pledges, one card each with server-computed progress and the actions
// Pay now · Pause/Resume · Edit · Cancel · "I paid another way" (phase 2);
// (3) every upcoming due, soonest first; (4) statements by year → by pledge
// → by fund → payments, the yearly PDF kept; (5) join / add a pledge — the
// stepper is NewPledgeFlow.kt, opened by GiveTabScreen; (6) reminders on/off
// per pledge.
//
// Everything is derived server-side (GET /giving/partnership), so this screen
// holds no second copy of the truth. Two rules from the original design still
// carry all the way into the copy, and both are easy to erode:
//
//   · `kept` is cycles COLLECTED, never scheduled. The label reads "collected"
//     for exactly that reason — a partner whose June failed did not keep June.
//   · the season block is what the WHOLE CHURCH did while they partnered. Never
//     "your giving produced this": we cannot trace a shilling to a disciple.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.DueItem
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.PartnerRhythm
import org.nuruplace.member.data.net.PartnerSeason
import org.nuruplace.member.data.net.PartnerTrouble
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.PledgeDetail
import org.nuruplace.member.data.net.UpdatePledgeBody
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PartnersViewModel : ViewModel() {
    var partnership by mutableStateOf<Partnership?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var resuming by mutableStateOf(false); private set
    var joining by mutableStateOf(false); private set
    /** The pledge an action is in flight for — its card shows a spinner. */
    var busyPledgeId by mutableStateOf<String?>(null); private set
    /** A failed action, said once under the pledges; cleared on the next action. */
    var actionError by mutableStateOf<String?>(null); private set

    fun load() {
        viewModelScope.launch {
            loading = true; error = null
            val r = runCatching { Net.client.api.partnership() }
            r.getOrNull()?.let { partnership = it }
            if (r.isFailure) error = ApiException.message(r.exceptionOrNull() ?: Exception())
            loading = false
        }
    }

    fun resume(scheduleId: String) {
        viewModelScope.launch {
            resuming = true
            val ok = runCatching { Net.client.api.resumeSchedule(scheduleId) }.isSuccess
            resuming = false
            if (ok) load() else actionError = "That didn't go through. Your giving is unchanged."
        }
    }

    /** POST /giving/partners/join `{}` — no money changes hands. */
    fun join(onJoined: () -> Unit = {}) {
        viewModelScope.launch {
            joining = true; actionError = null
            val r = runCatching { Net.client.api.joinPartners() }
            joining = false
            if (r.isSuccess) { load(); onJoined() } else actionError = ApiException.message(r.exceptionOrNull() ?: Exception())
        }
    }

    /** PATCH /giving/pledges/{id} — pause/resume/cancel, amount, due day, reminders. */
    fun update(pledgeId: String, body: UpdatePledgeBody, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            busyPledgeId = pledgeId; actionError = null
            val r = runCatching { Net.client.api.updatePledge(pledgeId, body) }
            busyPledgeId = null
            if (r.isSuccess) { load(); onDone() } else actionError = ApiException.message(r.exceptionOrNull() ?: Exception())
        }
    }
}

@Composable
fun PartnersScreen(
    vm: PartnersViewModel = remember { PartnersViewModel() },
    onPayNow: (GivePreset) -> Unit = {},
    onOpenReceipt: (String) -> Unit = {},
    onAddPledge: () -> Unit = {},
) {
    LaunchedEffect(Unit) { if (vm.partnership == null) vm.load() }
    val p = vm.partnership

    Column(
        Modifier.fillMaxSize().background(Nuru.paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when {
            p == null && vm.loading -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), Alignment.Center) {
                CircularProgressIndicator(color = Nuru.gold)
            }
            p == null -> PartnerNotice(
                "We couldn't load this just now",
                vm.error ?: "Your giving is unaffected.",
                action = "Try again" to { vm.load() },
            )
            p.isMember || p.isPartner -> {
                PartnerStanding(p)
                // Shown ONLY when there is something to say. A partner whose
                // giving is collecting cleanly never sees a warning-shaped block.
                p.trouble?.let { t ->
                    PartnerTroubleCard(t, vm.resuming) { p.scheduleId?.let(vm::resume) }
                }
                PledgesSection(p, vm, onPayNow, onOpenReceipt, onAddPledge)
                if (p.due.isNotEmpty()) DueSection(p.due, p, vm, onPayNow)
                p.rhythm?.let { PartnerRhythmCard(it, p.currency) }
                StatementsSection(onOpenReceipt)
                p.sinceYouBegan?.let { PartnerSeasonCard(it) }
                Text(
                    "Receipts are emailed per gift · the yearly statement is above.",
                    style = NuruType.caption, color = Nuru.ink400,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                JoinHero(p, vm.joining, onJoin = { vm.join() })
                vm.actionError?.let { Text(it, style = NuruType.caption, color = Nuru.danger) }
                if (p.givenMinor > 0 || p.everPartnered) StatementsSection(onOpenReceipt)
            }
        }
        if (vm.error != null && p != null) {
            // A refresh failed but we still have a standing to show — say so quietly.
            Text("Couldn't refresh just now — showing what we last had.", style = NuruType.caption, color = Nuru.ink400, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── 1. Standing / Join ───────────────────────────────────────────────────────

@Composable
private fun PartnerStanding(p: Partnership) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Nuru.white)
            .border(1.dp, Nuru.gold.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("YOUR STANDING", style = NuruType.micro, color = Nuru.goldLo)
        val since = p.membership?.joinedAt ?: p.since
        Text(
            since?.let { PartnerFormat.monthYear(it) }
                ?.let { "You have partnered since $it." }
                ?: "You are a partner of this church.",
            style = nuruSerif(26, FontWeight.Medium), color = Nuru.ink,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            p.tier?.takeIf { it.name.isNotBlank() }?.let { t ->
                Row(
                    Modifier.clip(CircleShape).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Filled.Verified, null, tint = Nuru.goldChipText, modifier = Modifier.size(12.dp))
                    Text(t.name, style = nuruSans(12, FontWeight.SemiBold), color = Nuru.goldChipText)
                }
            }
            p.membership?.takeIf { it.status == "paused" }?.let {
                Text("Paused", style = nuruSans(12, FontWeight.SemiBold), color = Nuru.ink400,
                    modifier = Modifier.clip(CircleShape).background(Nuru.surface).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        if (p.kept > 0) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${p.kept}", style = nuruSerif(30, FontWeight.Medium), color = Nuru.gold)
                // "collected", never "kept" — the word carries the rule the
                // server enforces.
                Text(
                    if (p.kept == 1) "gift collected" else "gifts collected",
                    style = NuruType.body, color = Nuru.ink600,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

/** The invitation to JOIN — warm, and clear that joining costs nothing. */
@Composable
private fun JoinHero(p: Partnership, joining: Boolean, onJoin: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)).background(Nuru.heroGradient)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f)), Alignment.Center) {
            Icon(Icons.Filled.Handshake, null, tint = Nuru.gold, modifier = Modifier.size(22.dp))
        }
        Text(
            if (p.everPartnered) "Partner with us again" else "Join the Partners programme",
            style = nuruSerif(26, FontWeight.Medium), color = Color.White,
        )
        Text(
            "A partner decides in advance to stand with this church — so we can plan beyond what arrives on a Sunday. " +
                "Joining is a decision, not a payment: no fund, no amount, no charge. Pledge whenever you're ready, and change or stop it any time.",
            style = NuruType.bodyLg, color = Color.White.copy(alpha = 0.8f),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            JoinPoint("Pledge a monthly amount, or a total by a date")
            JoinPoint("Point a pledge at a fund or a campaign — or keep it general")
            JoinPoint("Gentle reminders before a due date, if you want them")
        }
        Button(
            onClick = onJoin, enabled = !joining,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Nuru.gold, contentColor = Nuru.navyDeep),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (joining) CircularProgressIndicator(color = Nuru.navyDeep, strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp).size(16.dp))
            Text(if (joining) "Joining…" else "Join the programme", style = NuruType.cardCta, fontWeight = FontWeight.Bold)
        }
        Text("No money needed to join.", style = NuruType.caption, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun JoinPoint(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Check, null, tint = Nuru.gold, modifier = Modifier.padding(top = 3.dp).size(14.dp))
        Text(text, style = NuruType.body, color = Color.White.copy(alpha = 0.85f))
    }
}

// ── 2. My pledges ────────────────────────────────────────────────────────────

@Composable
private fun PledgesSection(
    p: Partnership,
    vm: PartnersViewModel,
    onPayNow: (GivePreset) -> Unit,
    onOpenReceipt: (String) -> Unit,
    onAddPledge: () -> Unit,
) {
    val view = LocalView.current
    var editing by remember { mutableStateOf<Pledge?>(null) }
    var cancelling by remember { mutableStateOf<Pledge?>(null) }
    var detailOf by remember { mutableStateOf<Pledge?>(null) }
    val live = p.pledges.filter { it.status != "cancelled" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MY PLEDGES", style = NuruType.micro, color = Nuru.goldLo)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(CircleShape).background(Nuru.navyDeep).clickable { Haptics.tap(view); onAddPledge() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Filled.Add, null, tint = Nuru.gold, modifier = Modifier.size(13.dp))
                Text("Add a pledge", style = nuruSans(12, FontWeight.SemiBold), color = Color.White)
            }
        }
        if (live.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.white).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("No pledges yet", style = NuruType.heading, color = Nuru.ink)
                Text(
                    "A pledge is a promise — an amount every month, or a total by a date. Add one when you're ready; being a partner does not require it.",
                    style = NuruType.body, color = Nuru.ink600,
                )
            }
        } else {
            live.forEach { pl ->
                PledgeCard(
                    pl = pl,
                    busy = vm.busyPledgeId == pl.pledgeId,
                    onOpen = { detailOf = pl },
                    onPayNow = { onPayNow(GivePreset(fundId = pl.fund?.code, amountMinor = payNowAmount(pl), pledgeId = pl.pledgeId, title = pl.targetTitle)) },
                    onPauseResume = {
                        vm.update(pl.pledgeId, UpdatePledgeBody(status = if (pl.status == "paused") "active" else "paused"))
                    },
                    onEdit = { editing = pl },
                    onCancel = { cancelling = pl },
                    onReminders = { on -> vm.update(pl.pledgeId, UpdatePledgeBody(remindersEnabled = on)) },
                )
            }
        }
        vm.actionError?.let { Text(it, style = NuruType.caption, color = Nuru.danger) }
    }

    editing?.let { pl ->
        EditPledgeSheet(
            pl = pl, busy = vm.busyPledgeId == pl.pledgeId,
            onDismiss = { editing = null },
            onSave = { amountMinor, dueDay ->
                vm.update(pl.pledgeId, UpdatePledgeBody(amountMinor = amountMinor, dueDay = dueDay)) { editing = null }
            },
        )
    }
    cancelling?.let { pl ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel this pledge?", style = NuruType.cardTitle, color = Nuru.navy) },
            text = {
                Text(
                    "Nothing is owed. What you have already given stays counted; nothing more will be asked for this pledge.",
                    style = NuruType.body, color = Nuru.ink600,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = pl.pledgeId; cancelling = null
                    vm.update(id, UpdatePledgeBody(status = "cancelled"))
                }) { Text("Cancel pledge", style = NuruType.cardCta, color = Nuru.danger) }
            },
            dismissButton = {
                TextButton(onClick = { cancelling = null }) { Text("Keep it", style = NuruType.cardCta, color = Nuru.ink600) }
            },
        )
    }
    detailOf?.let { pl ->
        PledgeDetailSheet(pl, onDismiss = { detailOf = null }, onOpenReceipt = onOpenReceipt)
    }
}

/** What "Pay now" presets: the month's remainder for a monthly pledge, the
 *  outstanding balance for a total pledge — never more than is owed. */
private fun payNowAmount(pl: Pledge): Int? = when (pl.shape) {
    "total" -> (pl.targetMinor ?: 0) - pl.progress.paidMinor
    else -> (pl.amountMinor ?: 0) - (pl.progress.periodPaidMinor ?: 0)
}.takeIf { it > 0 } ?: pl.headlineMinor.takeIf { it > 0 }

private fun progressFraction(pl: Pledge): Float = when (pl.shape) {
    "total" -> pl.targetMinor?.takeIf { it > 0 }?.let { pl.progress.paidMinor.toFloat() / it }
    else -> pl.amountMinor?.takeIf { it > 0 }?.let { (pl.progress.periodPaidMinor ?: 0).toFloat() / it }
}?.coerceIn(0f, 1f) ?: 0f

private fun labelChip(label: String, status: String): Triple<String, Color, Color> = when {
    status == "paused" || label == "paused" -> Triple("Paused", Nuru.surface, Nuru.ink400)
    status == "fulfilled" || label == "fulfilled" -> Triple("Fulfilled", Nuru.successBg, Nuru.successText)
    label == "behind" -> Triple("Behind", Nuru.warningBg, Nuru.warning)
    else -> Triple("On track", Nuru.goldChipBg, Nuru.goldChipText)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PledgeCard(
    pl: Pledge,
    busy: Boolean,
    onOpen: () -> Unit,
    onPayNow: () -> Unit,
    onPauseResume: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onReminders: (Boolean) -> Unit,
) {
    val view = LocalView.current
    val (chipText, chipBg, chipFg) = labelChip(pl.progress.label, pl.status)
    val paused = pl.status == "paused"
    val done = pl.status == "fulfilled"
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(14.dp))
            .clickable { onOpen() }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (pl.shape == "total") "BY ${pl.dueOn?.let { PartnerFormat.dayMonthYear(it) }?.uppercase() ?: "A DATE"}" else "MONTHLY",
                style = NuruType.micro, color = Nuru.goldLo,
            )
            Spacer(Modifier.weight(1f))
            if (busy) CircularProgressIndicator(color = Nuru.gold, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Text(chipText, style = nuruSans(11, FontWeight.SemiBold), color = chipFg,
                modifier = Modifier.clip(CircleShape).background(chipBg).padding(horizontal = 9.dp, vertical = 4.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(money(pl.headlineMinor, pl.currency), style = nuruSerif(24, FontWeight.Medium), color = Nuru.ink)
            Text(if (pl.shape == "total") "target" else "each month", style = NuruType.body, color = Nuru.ink600, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(pl.targetTitle ?: "General partnership", style = NuruType.body, color = Nuru.ink600)

        LinearProgressIndicator(
            progress = { progressFraction(pl) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = if (done) Nuru.success else Nuru.gold, trackColor = Nuru.track,
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (pl.shape == "total") "${money(pl.progress.paidMinor, pl.currency)} of ${money(pl.targetMinor ?: 0, pl.currency)}"
                else "${money(pl.progress.periodPaidMinor ?: 0, pl.currency)} this month",
                style = NuruType.caption, color = Nuru.ink600,
            )
            val next = pl.progress.nextDue ?: pl.dueOn
            Text(
                when {
                    done -> "Complete"
                    paused -> "Paused"
                    next != null -> "Next due ${PartnerFormat.dayMonth(next)}"
                    pl.dueDay != null -> "Due on the ${ordinal(pl.dueDay)}"
                    else -> ""
                },
                style = NuruType.caption, color = Nuru.ink600,
            )
        }

        if (!done) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionPill("Pay now", primary = true, enabled = !busy && !paused) { Haptics.tap(view); onPayNow() }
                ActionPill(if (paused) "Resume" else "Pause", enabled = !busy) { Haptics.tap(view); onPauseResume() }
                ActionPill("Edit", enabled = !busy) { onEdit() }
                ActionPill("Cancel", enabled = !busy, danger = true) { onCancel() }
                // Phase 2 (spec §6): the claim endpoint is not live yet.
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Coming soon") } },
                    state = rememberTooltipState(),
                ) {
                    ActionPill("I paid another way", enabled = false) {}
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remind me before it's due", style = NuruType.label, color = Nuru.ink)
                    Text("A nudge three days ahead, on the channels you allow.", style = NuruType.caption, color = Nuru.ink400)
                }
                Switch(
                    checked = pl.remindersEnabled, enabled = !busy,
                    onCheckedChange = { Haptics.tick(view); onReminders(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Nuru.gold, checkedThumbColor = Color.White),
                )
            }
        }
    }
}

@Composable
private fun ActionPill(label: String, primary: Boolean = false, enabled: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val bg = when { primary -> Nuru.navyDeep; else -> Nuru.white }
    val fg = when { primary -> Color.White; danger -> Nuru.danger; else -> Nuru.navy }
    Text(
        label, style = nuruSans(12, FontWeight.SemiBold), color = fg,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape).background(bg)
            .then(if (primary) Modifier else Modifier.border(1.dp, if (danger) Nuru.danger.copy(alpha = 0.3f) else Nuru.border, CircleShape))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** Edit amount / due day (spec §5 PATCH). Due day only for monthly pledges. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPledgeSheet(pl: Pledge, busy: Boolean, onDismiss: () -> Unit, onSave: (amountMinor: Int?, dueDay: Int?) -> Unit) {
    var amountText by remember { mutableStateOf(((pl.amountMinor ?: pl.targetMinor ?: 0) / 100).toString()) }
    var dueDay by remember { mutableStateOf(pl.dueDay ?: 1) }
    val parsed = amountText.filter { it.isDigit() }.take(8).toIntOrNull() ?: 0
    val valid = parsed in 1..5_000_000
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nuru.paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Edit pledge", style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
            Text(if (pl.shape == "total") "TARGET" else "AMOUNT EACH MONTH", style = NuruType.micro, color = Nuru.goldLo)
            OutlinedTextField(
                value = amountText,
                onValueChange = { v -> amountText = v.filter { it.isDigit() }.take(8) },
                singleLine = true,
                prefix = { Text("KSh ", style = NuruType.body, color = Nuru.ink600) },
                textStyle = nuruSerif(22, FontWeight.Medium).copy(color = Nuru.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (pl.shape != "total") {
                Text("DUE DAY", style = NuruType.micro, color = Nuru.goldLo)
                DueDayPicker(dueDay) { dueDay = it }
            }
            Button(
                onClick = { if (valid) onSave(parsed * 100, if (pl.shape != "total") dueDay else null) },
                enabled = valid && !busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Nuru.navyDeep, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp).size(16.dp))
                Text(if (busy) "Saving…" else "Save changes", style = NuruType.cardCta)
            }
        }
    }
}

/** 1–28 so every month has the day (spec §1). Shared with NewPledgeFlow. */
@Composable
internal fun DueDayPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..28).forEach { d ->
            val on = d == selected
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(if (on) Nuru.navyDeep else Nuru.white)
                    .border(1.dp, if (on) Nuru.navyDeep else Nuru.border, CircleShape)
                    .clickable { onSelect(d) },
                contentAlignment = Alignment.Center,
            ) {
                Text("$d", style = nuruSans(13, FontWeight.SemiBold), color = if (on) Color.White else Nuru.navy)
            }
        }
    }
}

/** GET /giving/pledges/{id} — the pledge and every payment attributed to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PledgeDetailSheet(pl: Pledge, onDismiss: () -> Unit, onOpenReceipt: (String) -> Unit) {
    var detail by remember(pl.pledgeId) { mutableStateOf<PledgeDetail?>(null) }
    var error by remember(pl.pledgeId) { mutableStateOf<String?>(null) }
    var attempt by remember(pl.pledgeId) { mutableIntStateOf(0) }
    LaunchedEffect(pl.pledgeId, attempt) {
        error = null
        runCatching { Net.client.api.pledge(pl.pledgeId) }
            .onSuccess { detail = it }
            .onFailure { error = ApiException.message(it) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nuru.paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pl.targetTitle ?: "General partnership", style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
                    Text(
                        "${money(pl.headlineMinor, pl.currency)} ${if (pl.shape == "total") "target" else "each month"} · ${money(pl.progress.paidMinor, pl.currency)} paid",
                        style = NuruType.caption, color = Nuru.ink600,
                    )
                }
                Box(Modifier.size(32.dp).clip(CircleShape).background(Nuru.surface).clickable { onDismiss() }, Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", tint = Nuru.navy, modifier = Modifier.size(15.dp))
                }
            }
            Text("PAYMENTS", style = NuruType.micro, color = Nuru.goldLo)
            val d = detail
            when {
                d == null && error == null -> Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) { CircularProgressIndicator(color = Nuru.gold) }
                d == null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(error ?: "", style = NuruType.body, color = Nuru.ink600)
                    TextButton(onClick = { attempt++ }) { Text("Try again", style = NuruType.cardCta, color = Nuru.gold) }
                }
                d.payments.isEmpty() -> Text("Nothing counted toward this pledge yet.", style = NuruType.body, color = Nuru.ink600)
                else -> d.payments.forEach { pay ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nuru.white)
                            .border(1.dp, Nuru.border, RoundedCornerShape(12.dp))
                            .clickable(enabled = pay.transactionId.isNotBlank()) { onOpenReceipt(pay.transactionId) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(money(pay.amountMinor, pay.currency), style = NuruType.label, color = Nuru.ink)
                            Text(
                                listOfNotNull(pay.at?.let { PartnerFormat.dayMonthYear(it) }, pay.receiptCode?.takeIf { it.isNotBlank() }?.let { "Ref $it" }).joinToString(" · "),
                                style = NuruType.caption, color = Nuru.ink600,
                            )
                        }
                        if (pay.transactionId.isNotBlank()) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Nuru.gold, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ── 3. Due ───────────────────────────────────────────────────────────────────

@Composable
private fun DueSection(due: List<DueItem>, p: Partnership, vm: PartnersViewModel, onPayNow: (GivePreset) -> Unit) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("COMING UP", style = NuruType.micro, color = Nuru.goldLo)
        due.sortedBy { it.dueOn }.forEach { d ->
            val pledge = p.pledges.firstOrNull { it.pledgeId == d.id }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.white)
                    .border(1.dp, Nuru.border, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.goldChipBg), Alignment.Center) {
                    Icon(if (d.kind == "schedule") Icons.Filled.Autorenew else Icons.Filled.Handshake, null, tint = Nuru.goldChipText, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(d.title.ifBlank { if (d.kind == "schedule") "Recurring gift" else "Pledge" }, style = NuruType.label, color = Nuru.ink)
                    Text("${money(d.amountMinor, d.currency)} · ${PartnerFormat.dayMonth(d.dueOn)}", style = NuruType.caption, color = Nuru.ink600)
                }
                if (d.action == "resume") {
                    ActionPill("Resume", enabled = !vm.resuming && vm.busyPledgeId == null) {
                        Haptics.tap(view)
                        if (d.kind == "schedule") vm.resume(d.id) else vm.update(d.id, UpdatePledgeBody(status = "active"))
                    }
                } else {
                    ActionPill("Pay now", primary = true) {
                        Haptics.tap(view)
                        onPayNow(
                            GivePreset(
                                fundId = pledge?.fund?.code,
                                amountMinor = d.amountMinor.takeIf { it > 0 } ?: pledge?.let(::payNowAmount),
                                pledgeId = if (d.kind == "pledge") d.id else pledge?.pledgeId,
                                title = d.title.ifBlank { null } ?: pledge?.targetTitle,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ── 4. Statements ────────────────────────────────────────────────────────────

/** GET /giving/statements?year= — by year → by pledge → by fund → payments,
 *  with the yearly PDF (giving/statement.pdf) kept. Loads on its own so a
 *  statements outage never blanks the standing above it. */
@Composable
private fun StatementsSection(onOpenReceipt: (String) -> Unit) {
    var year by remember { mutableStateOf<Int?>(null) }
    var stmt by remember { mutableStateOf<GivingStatement?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(year, attempt) {
        loading = true; error = null
        runCatching { Net.client.api.statements(year) }
            .onSuccess { stmt = it }
            .onFailure { error = ApiException.message(it) }
        loading = false
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(14.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STATEMENTS", style = NuruType.micro, color = Nuru.goldLo)
            Spacer(Modifier.weight(1f))
            // The yearly PDF — authed fetch, never a token URL.
            Row(
                Modifier.clip(CircleShape).background(Nuru.surface).border(1.dp, Nuru.border, CircleShape)
                    .clickable { scope.launch { openPdfAuthed(ctx, "nuru-giving-statement.pdf") { Net.client.api.givingStatementPdf() } } }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(Icons.Filled.Download, "Download statement PDF", tint = Nuru.navy, modifier = Modifier.size(13.dp))
                Text("PDF", style = nuruSans(11, FontWeight.SemiBold), color = Nuru.navy)
            }
        }
        val s = stmt
        val years = (s?.years?.takeIf { it.isNotEmpty() } ?: listOf(LocalDate.now().year)).sortedDescending()
        val shownYear = year ?: s?.year?.takeIf { it > 0 } ?: years.first()
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            years.forEach { y ->
                val on = y == shownYear
                Text(
                    "$y", style = nuruSans(12, FontWeight.SemiBold), color = if (on) Color.White else Nuru.navy,
                    modifier = Modifier.clip(CircleShape).background(if (on) Nuru.navyDeep else Nuru.white)
                        .border(1.dp, if (on) Nuru.navyDeep else Nuru.border, CircleShape)
                        .clickable { year = y }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        when {
            s == null && loading -> Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(color = Nuru.gold, modifier = Modifier.size(22.dp)) }
            s == null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(error ?: "We couldn't load your statement just now.", style = NuruType.body, color = Nuru.ink600)
                TextButton(onClick = { attempt++ }, contentPadding = PaddingValues(0.dp)) { Text("Try again", style = NuruType.cardCta, color = Nuru.gold) }
            }
            else -> {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(money(s.totalMinor, s.currency), style = nuruSerif(28, FontWeight.Medium), color = Nuru.ink)
                    Text("given in $shownYear", style = NuruType.body, color = Nuru.ink600, modifier = Modifier.padding(bottom = 4.dp))
                }
                if (s.byPledge.isNotEmpty()) {
                    Text("BY PLEDGE", style = NuruType.micro, color = Nuru.goldLo)
                    s.byPledge.forEach { line ->
                        PartnerRow(line.title.ifBlank { "General partnership" }, money(line.totalMinor, s.currency))
                    }
                }
                if (s.byFund.isNotEmpty()) {
                    Text("BY FUND", style = NuruType.micro, color = Nuru.goldLo)
                    s.byFund.forEach { line ->
                        val f = giveFund(line.code)
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(f.tint), Alignment.Center) { Icon(f.icon, null, tint = f.fg, modifier = Modifier.size(13.dp)) }
                            Text(line.name.ifBlank { f.name }, style = NuruType.body, color = Nuru.ink600, modifier = Modifier.weight(1f))
                            Text(money(line.totalMinor, s.currency), style = NuruType.body, color = Nuru.ink)
                        }
                    }
                }
                if (s.payments.isEmpty()) {
                    Text("No gifts in $shownYear.", style = NuruType.body, color = Nuru.ink600)
                } else {
                    Text("PAYMENTS", style = NuruType.micro, color = Nuru.goldLo)
                    s.payments.sortedByDescending { it.occurredAt ?: "" }.forEach { pay ->
                        val f = pay.fund?.let(::giveFund)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nuru.surface)
                                .clickable(enabled = pay.transactionId.isNotBlank()) { onOpenReceipt(pay.transactionId) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pay.title?.takeIf { it.isNotBlank() } ?: f?.name ?: "Gift", style = NuruType.label, color = Nuru.ink)
                                Text(
                                    listOfNotNull(
                                        pay.occurredAt?.let { PartnerFormat.dayMonthYear(it) },
                                        pay.method?.replaceFirstChar { it.uppercase() },
                                        pay.receiptCode?.takeIf { it.isNotBlank() }?.let { "Ref $it" },
                                    ).joinToString(" · "),
                                    style = NuruType.caption, color = Nuru.ink600,
                                )
                            }
                            Text(money(pay.amountMinor, pay.currency), style = NuruType.label, color = Nuru.ink)
                            if (pay.transactionId.isNotBlank()) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Nuru.gold, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Existing cards (trouble · rhythm · season) ───────────────────────────────

@Composable
private fun PartnerTroubleCard(t: PartnerTrouble, resuming: Boolean, onResume: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Nuru.goldChipBg)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (t.paused) "Your giving is paused" else "One gift didn't go through",
            style = NuruType.heading, color = Nuru.ink,
        )
        // Plain, never alarming. Nothing is owed, and we say so first.
        Text(
            if (t.paused)
                "We tried a few times and couldn't collect it, so we stopped trying rather than keep charging you. Nothing is owed. Starting again picks up from your next gift — it will not collect the one that was missed."
            else
                "We couldn't collect your last gift. We'll try again shortly, and nothing is owed in the meantime.",
            style = NuruType.body, color = Nuru.ink600,
        )
        if (t.paused) {
            Button(
                onClick = onResume, enabled = !resuming,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Nuru.navyDeep, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (resuming) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp).size(16.dp))
                }
                Text(if (resuming) "Starting again…" else "Start it again", style = NuruType.label)
            }
        }
    }
}

@Composable
private fun PartnerRhythmCard(r: PartnerRhythm, currency: String) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Nuru.white).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("YOUR RHYTHM", style = NuruType.micro, color = Nuru.goldLo)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$currency ${PartnerFormat.grouped(r.amountMinor / 100)}", style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
            Text(if (r.frequency == "weekly") "each week" else "each month", style = NuruType.body, color = Nuru.ink600, modifier = Modifier.padding(bottom = 2.dp))
        }
        PartnerRow("Method", when (r.method) { "mpesa" -> "M-Pesa"; "airtel" -> "Airtel Money"; else -> "Card" })
        PartnerRow("Fund", r.fund.replaceFirstChar { it.uppercase() })
        // A paused schedule carries no next date, and we say the true thing
        // rather than showing a stale one.
        PartnerRow("Next gift", r.nextRunAt?.let { PartnerFormat.dayMonth(it) } ?: "Paused")
    }
}

@Composable
private fun PartnerRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = NuruType.body, color = Nuru.ink600, modifier = Modifier.weight(1f))
        Text(value, style = NuruType.body, color = Nuru.ink)
    }
}

@Composable
private fun PartnerSeasonCard(s: PartnerSeason) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Nuru.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SINCE YOU BEGAN", style = NuruType.micro, color = Nuru.goldLo)
        // The framing IS the honesty. "Across the church" is load-bearing —
        // remove it and the page starts claiming what we cannot prove.
        Text("Across the church, in the season you have been partnering:", style = NuruType.body, color = Nuru.ink600)

        val empty = s.levelsCompleted == 0 && s.modulesCompleted == 0 && s.plansFinished == 0
        if (empty) {
            Text("It is early days. This will fill as the church walks on.", style = NuruType.body, color = Nuru.ink400)
        } else {
            if (s.levelsCompleted > 0) PartnerCount(s.levelsCompleted, "disciple finished a level", "disciples finished a level")
            if (s.modulesCompleted > 0) PartnerCount(s.modulesCompleted, "module completed", "modules completed")
            if (s.plansFinished > 0) PartnerCount(s.plansFinished, "reading plan finished", "reading plans finished")
        }
    }
}

@Composable
private fun PartnerCount(n: Int, one: String, many: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        Text("$n", style = nuruSerif(24, FontWeight.Medium), color = Nuru.gold, modifier = Modifier.widthIn(min = 44.dp))
        Text(if (n == 1) one else many, style = NuruType.body, color = Nuru.ink, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/** A titled notice with an optional real action — never "pull down" copy. */
@Composable
private fun PartnerNotice(title: String, message: String, action: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp)
            .clip(RoundedCornerShape(14.dp)).background(Nuru.white).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = nuruSerif(24, FontWeight.Medium), color = Nuru.ink)
        Text(message, style = NuruType.bodyLg, color = Nuru.ink600)
        action?.let { (label, onClick) ->
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Nuru.navyDeep, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(label, style = NuruType.label) }
        }
    }
}

/**
 * Timestamps arrive as ISO instants (with or without fractional seconds) or as
 * bare dates (`due_on`). Trying each is the difference between a real date and
 * a screen that quietly says "Paused" when nothing is paused.
 */
internal object PartnerFormat {
    private fun parse(iso: String): LocalDate? =
        runCatching { OffsetDateTime.parse(iso).toLocalDate() }.getOrNull()
            ?: runCatching { java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()

    fun monthYear(iso: String): String? = parse(iso)?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    fun dayMonth(iso: String): String = parse(iso)?.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())) ?: "Paused"

    fun dayMonthYear(iso: String): String = parse(iso)?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) ?: iso.take(10)

    fun grouped(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)
}
