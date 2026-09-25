package org.nuruplace.member.feature.give

// The Partners tab — Give → Partners (docs/PARTNERS_PROGRAMME.md §2, §3). The
// Android half of the same design as iOS PartnersView.swift; keep in step.
//
// One cream band (the GIVE · PARTNERS control, "Walk with the church", one
// muted line), then white cards in this order and nothing more:
//
//   STANDING   partner since · gifts kept · tier chip · Make a pledge (the
//              ONLY gold-filled button on the page) · Statement
//   DUE        one row per upcoming due, Pay / Resume — only when there is one;
//              a failed or paused schedule is a compact amber row under it
//   PLEDGES    one card per live pledge: its NAME (the member's own, or the
//              server's derived one), state chip, amount + due line, gold
//              progress, "N of M kept this year" or "paid · to go", next
//   STATEMENT  year chips · Pledged / Paid / Remaining · pledge-tied payments
//              only · "Partners statement and PDF →"
//
// The Statement button and that link open the PARTNERS statement
// (PartnersStatementScreen, route "partners-statement?year=") for the year the
// chips show — never the general giving statement (owner 2026-09-25: "have
// the statement separate for partners"). The giving statement is one tap
// further, from the partners statement's foot.
//
// No explanatory paragraphs. "Your rhythm" lives on the Give segment now (one
// row under the amount); "Since you began" left this tab. A non-member sees
// one card: Become a partner → Join the programme. Everything is derived
// server-side (GET /giving/partnership, GET /giving/statements); the three
// statement numbers are the ONE client-side derivation and live as pure
// functions in PartnerStatementMath.kt so iOS and Android cannot disagree.
//
// Two rules from the original design still carry into the copy:
//   · `kept` is cycles COLLECTED, never scheduled — "N gifts kept".
//   · nothing here says "your giving produced this"; we cannot trace a
//     shilling to a disciple.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.DueItem
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.PartnerTrouble
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.PledgeDetail
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.UpdatePledgeBody
import org.nuruplace.member.data.net.pledgeTitlePatch
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Capsule = RoundedCornerShape(999.dp)
private val CardShape = RoundedCornerShape(16.dp)

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

    // GET /giving/statements?year= — by year. The current year's statement
    // also feeds every monthly pledge's "N of M kept this year", so it is
    // fetched with the standing; other years load when their chip is tapped.
    var statements by mutableStateOf<Map<Int, GivingStatement>>(emptyMap()); private set
    /** The tapped year chip; null = the current year. */
    var statementYear by mutableStateOf<Int?>(null); private set
    var statementLoading by mutableStateOf(false); private set
    var statementError by mutableStateOf<String?>(null); private set

    fun load() {
        viewModelScope.launch {
            loading = true; error = null
            val r = runCatching { Net.client.api.partnership() }
            r.getOrNull()?.let { partnership = it }
            if (r.isFailure) error = ApiException.message(r.exceptionOrNull() ?: Exception())
            loading = false
        }
        // Payments may have changed with whatever prompted this reload.
        val current = LocalDate.now().year
        loadStatement(current)
        statementYear?.takeIf { it != current }?.let { loadStatement(it) }
    }

    fun selectStatementYear(year: Int) {
        statementYear = year
        if (statements[year] == null) loadStatement(year)
    }

    fun loadStatement(year: Int) {
        viewModelScope.launch {
            statementLoading = true; statementError = null
            runCatching { Net.client.api.statements(year) }
                .onSuccess { statements = statements + (year to it) }
                .onFailure { statementError = ApiException.message(it) }
            statementLoading = false
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

    /** PATCH /giving/pledges/{id} — pause/resume/cancel, amount, due day, reminders, name. */
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
    /** Open the partners statement on this year (the STATEMENT card's chip). */
    onOpenPartnersStatement: (year: Int) -> Unit = {},
    onAddPledge: () -> Unit = {},
    /** The tab's GIVE · PARTNERS control (GiveTabScreen) — first row of the band. */
    segmentControl: @Composable () -> Unit = {},
) {
    LaunchedEffect(Unit) { if (vm.partnership == null) vm.load() }
    val p = vm.partnership
    val openStatement = { onOpenPartnersStatement(vm.statementYear ?: LocalDate.now().year) }

    Column(Modifier.fillMaxSize().background(GIVE.paper).verticalScroll(rememberScrollState())) {
        PartnersHeaderBand(segmentControl)
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                p == null && vm.loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                    CircularProgressIndicator(color = GIVE.gold)
                }
                p == null -> PartnerNotice(
                    "We couldn't load this just now",
                    vm.error ?: "Your giving is unaffected.",
                    action = "Try again" to { vm.load() },
                )
                p.isMember || p.isPartner -> {
                    StandingCard(p, onAddPledge, openStatement)
                    if (p.due.isNotEmpty()) DueSection(p.due, p, vm, onPayNow)
                    // Only when there is something to say — a partner whose
                    // giving is collecting cleanly never sees an amber row.
                    p.trouble?.let { t -> TroubleRow(t, vm.resuming) { p.scheduleId?.let(vm::resume) } }
                    PledgesSection(p, vm, onPayNow, onOpenReceipt)
                    StatementSection(p, vm, onOpenReceipt, openStatement)
                }
                else -> {
                    JoinCard(vm.joining, onJoin = { vm.join() })
                    vm.actionError?.let { Text(it, style = giInter(12), color = GIVE.danger) }
                }
            }
            if (vm.error != null && p != null) {
                // A refresh failed but we still have a standing to show — say so quietly.
                Text("Couldn't refresh just now — showing what we last had.", style = giInter(11), color = GIVE.tertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ── Band + primitives ────────────────────────────────────────────────────────

/** The same cream band the Give segment wears (GivingScreen.GiveHeaderBand):
 *  the segment control first, then the title and one muted line. */
@Composable
private fun PartnersHeaderBand(segmentControl: @Composable () -> Unit) {
    GiveCreamHeaderBox {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp)) {
            segmentControl()
            Text("Walk with the church", style = giSerif(24, FontWeight.SemiBold, -0.48f), color = GIVE.navy, modifier = Modifier.padding(top = 14.dp))
            Text("Decide in advance. The church can plan.", style = giInter(11), color = GIVE.sub, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(text, style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.goldLo)
}

/** A white card: theme border, 16dp radius, 16dp padding. Clickable when asked. */
internal fun Modifier.partnerCard(onClick: (() -> Unit)? = null): Modifier =
    fillMaxWidth().clip(CardShape).background(GIVE.white).border(1.dp, GIVE.border, CardShape)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(16.dp)

@Composable
internal fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GIVE.border))
}

@Composable
private fun NavyPill(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label, style = giInter(12, FontWeight.SemiBold), color = Color.White,
        modifier = Modifier.alpha(if (enabled) 1f else 0.45f).clip(Capsule).background(GIVE.navy)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
internal fun StateChip(text: String, bg: Color, fg: Color) {
    Text(text, style = giInter(11, FontWeight.SemiBold), color = fg,
        modifier = Modifier.clip(Capsule).background(bg).padding(horizontal = 9.dp, vertical = 4.dp))
}

// ── 1. Standing / Join ───────────────────────────────────────────────────────

@Composable
private fun StandingCard(p: Partnership, onAddPledge: () -> Unit, onOpenStatement: () -> Unit) {
    val view = LocalView.current
    val since = (p.membership?.joinedAt ?: p.since)?.let { PartnerFormat.monthYear(it) }
    val paused = p.membership?.status == "paused" || p.status == "paused" || p.trouble?.paused == true
    Column(Modifier.partnerCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("STANDING")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(since?.let { "Partner since $it" } ?: "Partner", style = giInter(16, FontWeight.SemiBold), color = GIVE.navy)
                // "kept" is cycles collected, never scheduled.
                Text(
                    "${p.kept} ${if (p.kept == 1) "gift" else "gifts"} kept · ${if (paused) "paused" else "on track"}",
                    style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 2.dp),
                )
            }
            p.tier?.name?.takeIf { it.isNotBlank() }?.let { name ->
                Row(
                    Modifier.clip(Capsule).background(GIVE.goldChipBg).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Filled.WorkspacePremium, null, tint = GIVE.goldChipText, modifier = Modifier.size(13.dp))
                    Text(name, style = giInter(12, FontWeight.SemiBold), color = GIVE.goldChipText)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // The ONLY gold-filled button on the page.
            Row(
                Modifier.weight(1f).height(44.dp).clip(Capsule).background(GIVE.gold)
                    .clickable { Haptics.tap(view); onAddPledge() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = GIVE.navy, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Make a pledge", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
            }
            Row(
                Modifier.weight(1f).height(44.dp).clip(Capsule).border(1.5.dp, GIVE.navy, Capsule)
                    .clickable { onOpenStatement() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Text("Statement", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
            }
        }
    }
}

/** The invitation to JOIN — one card, one line, one button. Joining is a
 *  decision, not a payment (spec §1); nothing else shows until joined. */
@Composable
private fun JoinCard(joining: Boolean, onJoin: () -> Unit) {
    Column(Modifier.partnerCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow("JOIN THE PARTNERS PROGRAMME")
        Text("Become a partner", style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy)
        Text("Joining costs nothing today. A pledge can come later.", style = giInter(13), color = GIVE.sub)
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(Capsule).background(GIVE.gold)
                .clickable(enabled = !joining) { onJoin() },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            if (joining) {
                CircularProgressIndicator(color = GIVE.navy, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(if (joining) "Joining…" else "Join the programme", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
        }
    }
}

// ── 2. Due + trouble ─────────────────────────────────────────────────────────

@Composable
private fun DueSection(due: List<DueItem>, p: Partnership, vm: PartnersViewModel, onPayNow: (GivePreset) -> Unit) {
    val view = LocalView.current
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("DUE")
        Column(Modifier.fillMaxWidth().clip(CardShape).background(GIVE.white).border(1.dp, GIVE.border, CardShape)) {
            due.sortedBy { it.dueOn }.forEachIndexed { i, d ->
                if (i > 0) Hairline()
                val pledge = p.pledges.firstOrNull { it.pledgeId == d.id }
                val whenLabel = partnerDate(d.dueOn)?.let { dueRelativeLabel(it, today, PartnerFormat::dayMonth) } ?: "soon"
                val what = if (d.kind == "schedule") {
                    "Recurring gift" + (p.rhythm?.method?.takeIf { it.isNotBlank() }?.let { " · ${giveMethodLabel(it)}" } ?: "")
                } else {
                    d.title.ifBlank { null } ?: pledge?.displayTitle ?: "Pledge"
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${ksh(d.amountMinor)} · $whenLabel", style = giInter(15, FontWeight.SemiBold), color = GIVE.navy)
                        Text(what, style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (d.action == "resume") {
                        NavyPill("Resume", enabled = !vm.resuming && vm.busyPledgeId == null) {
                            Haptics.tap(view)
                            if (d.kind == "schedule") vm.resume(d.id) else vm.update(d.id, UpdatePledgeBody(status = "active"))
                        }
                    } else {
                        NavyPill("Pay") {
                            Haptics.tap(view)
                            onPayNow(
                                GivePreset(
                                    fundId = pledge?.fund?.code,
                                    amountMinor = d.amountMinor.takeIf { it > 0 } ?: pledge?.let(::payNowAmount),
                                    pledgeId = if (d.kind == "pledge") d.id else pledge?.pledgeId,
                                    title = d.title.ifBlank { null } ?: pledge?.displayTitle,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A failed or paused schedule, said once and plainly: nothing is owed. */
@Composable
private fun TroubleRow(t: PartnerTrouble, resuming: Boolean, onResume: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(Nuru.warningBg).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Info, null, tint = Nuru.answeredText, modifier = Modifier.size(16.dp))
        Text(
            if (t.paused) "Your giving is paused — nothing is owed." else "One gift didn't go through — we'll try again. Nothing is owed.",
            style = giInter(12, FontWeight.Medium), color = Nuru.answeredText, modifier = Modifier.weight(1f),
        )
        if (t.paused) NavyPill(if (resuming) "Starting…" else "Resume", enabled = !resuming) { onResume() }
    }
}

// ── 3. My pledges ────────────────────────────────────────────────────────────

@Composable
private fun PledgesSection(
    p: Partnership,
    vm: PartnersViewModel,
    onPayNow: (GivePreset) -> Unit,
    onOpenReceipt: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<Pledge?>(null) }
    var cancelling by remember { mutableStateOf<Pledge?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val live = p.pledges.filter { it.status != "cancelled" }
    val active = live.count { it.status == "active" }
    val today = LocalDate.now()
    val yearPayments = vm.statements[today.year]?.payments.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("PLEDGES")
            Spacer(Modifier.weight(1f))
            if (live.isNotEmpty()) Text("$active active", style = giInter(11), color = GIVE.tertiary)
        }
        if (live.isEmpty()) {
            Box(Modifier.partnerCard()) {
                Text("No pledges yet — monthly, or a total by a date.", style = giInter(13), color = GIVE.sub)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                live.forEach { pl ->
                    PledgeCard(pl, busy = vm.busyPledgeId == pl.pledgeId, yearPayments = yearPayments, today = today) { detailId = pl.pledgeId }
                }
            }
        }
        vm.actionError?.let { Text(it, style = giInter(12), color = GIVE.danger) }
    }

    editing?.let { pl ->
        EditPledgeSheet(
            pl = pl, busy = vm.busyPledgeId == pl.pledgeId,
            onDismiss = { editing = null },
            onSave = { amountMinor, dueDay, titlePatch ->
                // `title` travels only when the Name field changed: a string
                // sets the custom name, JsonNull clears it (pledgeTitlePatch).
                vm.update(pl.pledgeId, UpdatePledgeBody(amountMinor = amountMinor, dueDay = dueDay, title = titlePatch)) { editing = null }
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
    // Re-resolved by id so a pause/resume/reminders change made INSIDE the
    // sheet shows the reloaded pledge, not the one captured at the tap.
    detailId?.let { id ->
        val pl = p.pledges.firstOrNull { it.pledgeId == id } ?: return@let
        PledgeDetailSheet(
            pl = pl, busy = vm.busyPledgeId == pl.pledgeId,
            onDismiss = { detailId = null },
            onOpenReceipt = onOpenReceipt,
            onPayNow = {
                detailId = null
                onPayNow(GivePreset(fundId = pl.fund?.code, amountMinor = payNowAmount(pl), pledgeId = pl.pledgeId, title = pl.displayTitle))
            },
            onPauseResume = { vm.update(pl.pledgeId, UpdatePledgeBody(status = if (pl.status == "paused") "active" else "paused")) },
            onEdit = { detailId = null; editing = pl },
            onCancel = { detailId = null; cancelling = pl },
            onReminders = { on -> vm.update(pl.pledgeId, UpdatePledgeBody(remindersEnabled = on)) },
        )
    }
}

/** What "Pay" presets: the month's remainder for a monthly pledge, the
 *  outstanding balance for a total pledge — never more than is owed. */
private fun payNowAmount(pl: Pledge): Int? = when (pl.shape) {
    "total" -> (pl.targetMinor ?: 0) - pl.progress.paidMinor
    else -> (pl.amountMinor ?: 0) - (pl.progress.periodPaidMinor ?: 0)
}.takeIf { it > 0 } ?: pl.headlineMinor.takeIf { it > 0 }

/** monthly = periodPaid / amount; total = paid / target; both capped at 1. */
private fun progressFraction(pl: Pledge): Float = when (pl.shape) {
    "total" -> pl.targetMinor?.takeIf { it > 0 }?.let { pl.progress.paidMinor.toFloat() / it }
    else -> pl.amountMinor?.takeIf { it > 0 }?.let { (pl.progress.periodPaidMinor ?: 0).toFloat() / it }
}?.coerceIn(0f, 1f) ?: 0f

/** on_track → green · behind → gold · paused → grey · fulfilled → green. */
private fun stateChip(pl: Pledge): Triple<String, Color, Color> = when {
    pl.status == "paused" || pl.progress.label == "paused" -> Triple("Paused", GIVE.mutedBg, GIVE.ink600)
    pl.status == "fulfilled" || pl.progress.label == "fulfilled" -> Triple("Fulfilled", GIVE.successBg, GIVE.successText)
    pl.progress.label == "behind" -> Triple("Behind", GIVE.goldChipBg, GIVE.goldChipText)
    else -> Triple("On track", GIVE.successBg, GIVE.successText)
}

/** The card leads with the pledge's NAME (pledge names: `title` is the
 *  member's own when set, else the server's derived one); the amount and
 *  due line sit under it, so a member with three pledges can tell them apart. */
@Composable
private fun PledgeCard(pl: Pledge, busy: Boolean, yearPayments: List<StatementPayment>, today: LocalDate, onOpen: () -> Unit) {
    val total = pl.shape == "total"
    val (chipText, chipBg, chipFg) = stateChip(pl)
    val amountLine = if (total) {
        "${ksh(pl.targetMinor ?: 0)} by ${partnerDate(pl.dueOn)?.format(PartnerFormat.MONTH) ?: "a date"}"
    } else {
        "${ksh(pl.amountMinor ?: 0)} monthly"
    }
    val dueLine = if (total) partnerDate(pl.dueOn)?.let { "due ${PartnerFormat.dayMonth(it)}" } else pl.dueDay?.let { "due on the ${ordinal(it)}" }
    val left = if (total) {
        val paid = pl.progress.paidMinor
        val toGo = maxOf((pl.targetMinor ?: 0) - paid, 0)
        "${ksh(paid)} paid · ${PartnerFormat.grouped(toGo / 100)} to go"
    } else {
        val (kept, elapsed) = keptThisYear(pl, yearPayments, today)
        if (elapsed == 0) "Nothing due yet this year" else "$kept of $elapsed kept this year"
    }
    Column(Modifier.partnerCard(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(pl.displayTitle, style = giInter(15, FontWeight.SemiBold), color = GIVE.navy, maxLines = 2)
                Text(
                    listOfNotNull(amountLine, dueLine).joinToString(" · "),
                    style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (busy) CircularProgressIndicator(color = GIVE.gold, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            StateChip(chipText, chipBg, chipFg)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Capsule).background(GIVE.mutedBg)) {
            Box(Modifier.fillMaxWidth(progressFraction(pl)).fillMaxHeight().clip(Capsule).background(GIVE.gold))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, style = giInter(11), color = GIVE.sub)
            pl.progress.nextDue?.let { partnerDate(it) }?.let { Text("Next ${PartnerFormat.dayMonth(it)}", style = giInter(11), color = GIVE.sub) }
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

internal fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** Edit name / amount / due day (spec §5 PATCH + pledge names). Due day only
 *  for monthly pledges. The Name field is prefilled with the custom name, or
 *  the derived one when there is none; only a CHANGE travels (a cleared field
 *  sends null so the server falls back to its derived name). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPledgeSheet(
    pl: Pledge,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (amountMinor: Int?, dueDay: Int?, titlePatch: kotlinx.serialization.json.JsonElement?) -> Unit,
) {
    var amountText by remember { mutableStateOf(((pl.amountMinor ?: pl.targetMinor ?: 0) / 100).toString()) }
    var dueDay by remember { mutableStateOf(pl.dueDay ?: 1) }
    val namePrefill = remember(pl.pledgeId) { pl.customTitle?.takeIf { it.isNotBlank() } ?: pl.displayTitle }
    var name by remember(pl.pledgeId) { mutableStateOf(namePrefill) }
    val parsed = amountText.filter { it.isDigit() }.take(8).toIntOrNull() ?: 0
    // A blank name is a valid CLEAR; anything else must be 2–60.
    val nameValid = name.isBlank() || pledgeTitleValid(name)
    val valid = parsed in 1..5_000_000 && nameValid
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nuru.paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Edit pledge", style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
            Text("NAME", style = NuruType.micro, color = Nuru.goldLo)
            OutlinedTextField(
                value = name,
                onValueChange = { v -> name = v.take(PLEDGE_TITLE_MAX) },
                singleLine = true,
                placeholder = { Text(pl.displayTitle, style = NuruType.body, color = Nuru.ink400) },
                supportingText = {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (!nameValid) "$PLEDGE_TITLE_MIN–$PLEDGE_TITLE_MAX characters, or clear it to use the church's name" else "",
                            style = NuruType.caption, color = Nuru.danger, modifier = Modifier.weight(1f),
                        )
                        Text("${name.length}/$PLEDGE_TITLE_MAX", style = NuruType.caption, color = Nuru.ink400)
                    }
                },
                isError = !nameValid,
                textStyle = nuruSans(16, FontWeight.Medium).copy(color = Nuru.ink),
                modifier = Modifier.fillMaxWidth(),
            )
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
                onClick = { if (valid) onSave(parsed * 100, if (pl.shape != "total") dueDay else null, pledgeTitlePatch(namePrefill, name)) },
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

/** The pledge detail — the card's tap target. Its actions (Pay · Pause/Resume
 *  · Edit · Cancel · reminders) live here now that the cards carry none, then
 *  GET /giving/pledges/{id}: every payment attributed to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PledgeDetailSheet(
    pl: Pledge,
    busy: Boolean,
    onDismiss: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    onPayNow: () -> Unit,
    onPauseResume: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onReminders: (Boolean) -> Unit,
) {
    val view = LocalView.current
    val paused = pl.status == "paused"
    val done = pl.status == "fulfilled"
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
                    Text(pl.displayTitle, style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
                    Text(
                        "${money(pl.headlineMinor, pl.currency)} ${if (pl.shape == "total") "target" else "each month"} · ${money(pl.progress.paidMinor, pl.currency)} paid",
                        style = NuruType.caption, color = Nuru.ink600,
                    )
                }
                if (busy) CircularProgressIndicator(color = Nuru.gold, strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp).size(14.dp))
                Box(Modifier.size(32.dp).clip(CircleShape).background(Nuru.surface).clickable { onDismiss() }, Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", tint = Nuru.navy, modifier = Modifier.size(15.dp))
                }
            }
            if (!done) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionPill("Pay now", primary = true, enabled = !busy && !paused) { Haptics.tap(view); onPayNow() }
                    ActionPill(if (paused) "Resume" else "Pause", enabled = !busy) { Haptics.tap(view); onPauseResume() }
                    ActionPill("Edit", enabled = !busy) { onEdit() }
                    ActionPill("Cancel", enabled = !busy, danger = true) { onCancel() }
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

// ── 4. Statement ─────────────────────────────────────────────────────────────

/** Year chips (current year back to the join year, at most four —
 *  partnerStatementYears, the same list the partners statement shows), then
 *  ONE card: Pledged / Paid / Remaining (PartnerStatementMath.kt), a rule, the
 *  pledge-tied payments newest first, and the door to the partners statement
 *  + its PDF. Gifts without a pledge are not shown here. */
@Composable
private fun StatementSection(p: Partnership, vm: PartnersViewModel, onOpenReceipt: (String) -> Unit, onOpenStatement: () -> Unit) {
    val currentYear = LocalDate.now().year
    val years = partnerStatementYears(currentYear, partnerDate(p.membership?.joinedAt ?: p.since)?.year)
    val shownYear = vm.statementYear ?: currentYear
    val s = vm.statements[shownYear]

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("STATEMENT")
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                years.forEach { y ->
                    val on = y == shownYear
                    Text(
                        "$y", style = giInter(11, FontWeight.SemiBold), color = if (on) Color.White else GIVE.navy,
                        modifier = Modifier.clip(Capsule).background(if (on) GIVE.navy else GIVE.white)
                            .border(1.dp, if (on) GIVE.navy else GIVE.border, Capsule)
                            .clickable { vm.selectStatementYear(y) }.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Column(Modifier.partnerCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                s == null && vm.statementLoading -> Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) {
                    CircularProgressIndicator(color = GIVE.gold, modifier = Modifier.size(22.dp))
                }
                s == null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vm.statementError ?: "We couldn't load your statement just now.", style = giInter(13), color = GIVE.sub)
                    TextButton(onClick = { vm.loadStatement(shownYear) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Try again", style = giInter(13, FontWeight.SemiBold), color = GIVE.gold)
                    }
                }
                else -> {
                    val sum = statementSummary(shownYear, p.pledges, s.payments)
                    Row(Modifier.fillMaxWidth()) {
                        SummaryColumn("PLEDGED", ksh(sum.pledgedMinor), GIVE.navy, Modifier.weight(1f))
                        SummaryColumn("PAID", ksh(sum.paidMinor), GIVE.successText, Modifier.weight(1f))
                        SummaryColumn("REMAINING", ksh(sum.remainingMinor), GIVE.goldLo, Modifier.weight(1f))
                    }
                    Hairline()
                    val rows = pledgePayments(s.payments).sortedByDescending { it.occurredAt ?: "" }
                    if (rows.isEmpty()) {
                        Text("No pledge payments in $shownYear.", style = giInter(12), color = GIVE.sub)
                    } else {
                        Column {
                            rows.forEachIndexed { i, pay ->
                                if (i > 0) Hairline()
                                StatementPaymentRow(pay, onOpenReceipt)
                            }
                        }
                    }
                    Text(
                        "Partners statement and PDF →",
                        style = giInter(13, FontWeight.SemiBold), color = GIVE.gold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clip(Capsule).clickable { onOpenStatement() }.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SummaryColumn(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.tertiary)
        Text(value, style = giInter(16, FontWeight.SemiBold), color = color, modifier = Modifier.padding(top = 4.dp))
    }
}

/** "20 Sep · Monthly pledge" over "M-Pesa · UIKJ2713B5" (method when the row
 *  carries one, else the fund; then the receipt code), amount at right. */
@Composable
private fun StatementPaymentRow(pay: StatementPayment, onOpenReceipt: (String) -> Unit) {
    val date = partnerDate(pay.occurredAt)?.let { PartnerFormat.dayMonth(it) }
    val what = pay.pledgeTitle?.takeIf { it.isNotBlank() } ?: pay.title?.takeIf { it.isNotBlank() } ?: "Pledge"
    val via = listOfNotNull(
        pay.method?.takeIf { it.isNotBlank() }?.let(::giveMethodLabel) ?: pay.fund?.takeIf { it.isNotBlank() }?.let { giveFund(it).name },
        pay.receiptCode?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = pay.transactionId.isNotBlank()) { onOpenReceipt(pay.transactionId) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(date, what).joinToString(" · "), style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
            if (via.isNotBlank()) Text(via, style = giInter(11), color = GIVE.sub, modifier = Modifier.padding(top = 2.dp))
        }
        Text(ksh(pay.amountMinor), style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
    }
}

// ── Notice ───────────────────────────────────────────────────────────────────

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
 * bare dates (`due_on`); PartnerStatementMath.partnerDate tries each. English
 * month names, short: "Sep 2026" · "5 Oct" · "5 Oct 2026".
 */
internal object PartnerFormat {
    val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun monthYear(iso: String): String? = partnerDate(iso)?.format(MONTH_YEAR)

    fun dayMonth(d: LocalDate): String = d.format(DAY_MONTH)

    fun dayMonthYear(iso: String): String = partnerDate(iso)?.format(DAY_MONTH_YEAR) ?: iso.take(10)

    fun grouped(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)
}
