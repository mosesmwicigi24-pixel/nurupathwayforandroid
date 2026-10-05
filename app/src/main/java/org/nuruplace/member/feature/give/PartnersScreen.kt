package org.nuruplace.member.feature.give

// The Partners tab — Give → Partners (docs/PARTNERS_PROGRAMME.md §2, §3). The
// Android half of the same design as iOS PartnersView.swift; keep in step.
//
// One cream band (the GIVE · PARTNERS control, "Walk with the church", one
// muted line), then white cards in this order and nothing more:
//
//   STANDING   partner since · commitments (or gifts) kept · tier chip ·
//              Make a pledge (the
//              ONLY gold-filled button on the page) · Statement
//   DUE        one row per upcoming due, Pay / Resume — or "Collected on …"
//              when a running recurring gift, or the collector of a pledge
//              (on or before the instalment's day, EXPERIENCE.md §6.4),
//              takes it — only when there is one; a failed or paused
//              schedule is a compact amber row under it
//   PLEDGES    one card per live pledge: its NAME (the member's own, or the
//              server's derived one), state chip, amount + due line, gold
//              progress, "N of M kept this year" or "paid · to go", next
//   STATEMENT  year chips · Pledged / Paid / Remaining · pledge-tied payments
//              only · "Partners statement and PDF →"
//
// A pledge's card opens its own PAGE over the list (iOS PledgeDetailView):
// "YOUR PLEDGE" and its name; the promise, what counts toward it and a total
// pledge's pace; the recurring gift that collects it — any pledge — or the
// offer to collect it at its pace; Pay now · Pause (Pay early · Pause, both
// quiet, while a gift collects it — EXPERIENCE.md §7 rule 6), Edit | Cancel,
// I paid another way, reminders; PAYMENTS; PAID ANOTHER WAY. Back returns to the
// list where it was.
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
// Freshness (GivingEvents.kt): the standing and the statement refetch every
// time the segment is shown and on every resume, keeping what is on screen
// while they do; the ViewModel also reloads on GivingEvents, so a pledge paid
// from the Give segment is already counted when the member switches back.
//
// Two rules from the original design still carry into the copy:
//   · `kept` is cycles COLLECTED, never scheduled — "N gifts kept", said
//     only for a schedule-only partner; with a monthly pledge the line
//     counts this year's kept commitments (PartnerStatementMath.standingKeptLine).
//   · nothing here says "your giving produced this"; we cannot trace a
//     shilling to a disciple.

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.DueItem
import org.nuruplace.member.data.net.GivingMethodsRes
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.PartnerTrouble
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.PledgeClaim
import org.nuruplace.member.data.net.PledgeDetail
import org.nuruplace.member.data.net.PledgePayment
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.StatementPendingPayment
import org.nuruplace.member.data.net.StateLanguage
import org.nuruplace.member.data.net.StateMessage
import org.nuruplace.member.data.net.UpdatePledgeBody
import org.nuruplace.member.data.offline.Connectivity
import org.nuruplace.member.ui.components.FailedState
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.NuruRefreshBox
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.TypeScale
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Capsule = RoundedCornerShape(999.dp)
private val CardShape = RoundedCornerShape(16.dp)

class PartnersViewModel : ViewModel() {
    var partnership by mutableStateOf<Partnership?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** Why the standing did not load, in the state language (EXPERIENCE.md §4)
     *  — the shared card says it when there is nothing to show yet. */
    var failure by mutableStateOf<StateMessage?>(null); private set
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
    /** True while ANY statement fetch is in flight — two years loading at
     *  once must not flash the "couldn't load" card when one lands first. */
    var statementLoading by mutableStateOf(false); private set
    private var statementsInFlight = 0
    var statementError by mutableStateOf<String?>(null); private set

    // GET /giving/methods and GET /giving/schedules, read WITH the standing —
    // never per pledge — so a pledge shows the recurring gift that collects
    // it (monthly or total), or the offer to collect it at its pace, the
    // moment it opens. Null until each has answered once; a failed read keeps
    // what was there, and with either unknown nothing is offered (never a
    // second collector on a guess).
    var methods by mutableStateOf<GivingMethodsRes?>(null); private set
    var schedules by mutableStateOf<List<GivingSchedule>?>(null); private set

    private val freshness = GivingFreshness()
    // Latest request wins: a slower, older answer never overwrites a newer
    // one (an entry fetch and an event-driven one can overlap).
    private var partnershipSeq = 0
    private val statementSeq = mutableMapOf<Int, Int>()

    init {
        // Money moved somewhere (a gift, a pledge change) — refetch, even
        // while the Partners segment is not the one showing. Only once the
        // standing has been asked for: a member who never opens Partners
        // costs no fetch (its first showing loads anyway).
        viewModelScope.launch {
            GivingEvents.changed.debouncedGivingReloads().collect {
                if (partnershipSeq > 0 && freshness.shouldRefetchAfterEvent()) load()
            }
        }
    }

    /** Entry / resume (stale-while-revalidate): refetch unless a fetch has
     *  just started — what is on screen stays while it runs. */
    fun refresh() {
        if (freshness.shouldRefetchOnEntry()) load()
    }

    fun load() {
        freshness.fetchStarted()
        val seq = ++partnershipSeq
        viewModelScope.launch {
            loading = true; error = null; failure = null
            val r = runCatching { Net.client.api.partnership() }
            if (seq != partnershipSeq) return@launch // a newer fetch owns the state
            r.getOrNull()?.let { partnership = it }
            if (r.isFailure) {
                // Read once (an error body is a one-shot stream): the card and the line.
                val f = ApiException.state(r.exceptionOrNull() ?: Exception())
                failure = f; error = f.sentence
            }
            loading = false
        }
        // What collects each pledge, and whether M-Pesa can take a new
        // recurring gift — side by side, beside the standing.
        viewModelScope.launch {
            val rails = async { runCatching { Net.client.api.givingMethods() } }
            val gifts = async { runCatching { Net.client.api.schedules().data } }
            val m = rails.await()
            val s = gifts.await()
            if (seq != partnershipSeq) return@launch // a newer fetch owns the state
            m.getOrNull()?.let { methods = it }
            s.getOrNull()?.let { schedules = it }
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
        val seq = (statementSeq[year] ?: 0) + 1
        statementSeq[year] = seq
        viewModelScope.launch {
            statementsInFlight++; statementLoading = true; statementError = null
            val r = runCatching { Net.client.api.statements(year) }
            if (statementSeq[year] == seq) {
                r.onSuccess { statements = statements + (year to it) }
                    .onFailure { statementError = ApiException.message(it) }
            }
            statementsInFlight--; statementLoading = statementsInFlight > 0
        }
    }

    fun resume(scheduleId: String) {
        viewModelScope.launch {
            resuming = true
            val ok = runCatching { Net.client.api.resumeSchedule(scheduleId) }.isSuccess
            resuming = false
            if (ok) { GivingEvents.emit(); load() } else actionError = "That didn't go through. Your giving is unchanged."
        }
    }

    /** POST /giving/partners/join `{}` — no money changes hands. */
    fun join(onJoined: () -> Unit = {}) {
        viewModelScope.launch {
            joining = true; actionError = null
            val r = runCatching { Net.client.api.joinPartners() }
            joining = false
            if (r.isSuccess) { GivingEvents.emit(); load(); onJoined() } else actionError = ApiException.message(r.exceptionOrNull() ?: Exception())
        }
    }

    /** A sheet opening starts clean: an older action's error is not about it. */
    fun clearActionError() { actionError = null }

    /** PATCH /giving/pledges/{id} — pause/resume/cancel, amount, due day, reminders, name. */
    fun update(pledgeId: String, body: UpdatePledgeBody, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            busyPledgeId = pledgeId; actionError = null
            val r = runCatching { Net.client.api.updatePledge(pledgeId, body) }
            busyPledgeId = null
            // Emit BEFORE load(): this VM's own collector then sees a fetch
            // that started after the event and skips; everyone else reloads.
            if (r.isSuccess) { GivingEvents.emit(); load(); onDone() } else actionError = ApiException.message(r.exceptionOrNull() ?: Exception())
        }
    }
}

@Composable
fun PartnersScreen(
    vm: PartnersViewModel = viewModel(),
    onPayNow: (GivePreset) -> Unit = {},
    onOpenReceipt: (String) -> Unit = {},
    /** Open the partners statement on this year (the STATEMENT card's chip). */
    onOpenPartnersStatement: (year: Int) -> Unit = {},
    onAddPledge: () -> Unit = {},
    /** The tab's GIVE · PARTNERS control (GiveTabScreen) — first row of the band. */
    segmentControl: @Composable () -> Unit = {},
    /** A pledge to open once the standing is here — a Partners notice, a
     *  collector's "Change it on the pledge", or a pledge just made without
     *  its automatic collection (with the server's reason). Giving Cycle 5. */
    openPledge: PledgeLanding? = null,
    /** [openPledge] has been opened; GiveTabScreen stops asking. */
    onPledgeOpened: () -> Unit = {},
    /** A pledge's "Collect it automatically at this pace" set its recurring
     *  gift up (Giving Cycle 9): the Give segment shows the result. */
    onScheduleStarted: (StartedSchedule) -> Unit = {},
    /** Open a recurring gift by id when the standing's own list does not
     *  have it yet — its sheet on the Recurring gifts screen. */
    onOpenSchedule: (String) -> Unit = {},
) {
    // Stale-while-revalidate on every showing of the segment, and on every
    // return to the foreground (the M-Pesa PIN prompt is its own activity).
    // The two can land together — refresh() fetches once for both.
    LaunchedEffect(Unit) { vm.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val p = vm.partnership
    val openStatement = { onOpenPartnersStatement(vm.statementYear ?: LocalDate.now().year) }
    val today = LocalDate.now()

    // A pledge opens as its own PAGE over the list (iOS PledgeDetailView): its
    // back arrow, or the system back, returns to the list where it was.
    // Saveable, so a receipt opened from the page comes back to it.
    var pageId by rememberSaveable { mutableStateOf<String?>(null) }
    // Why a pledge just made has no automatic collection (Giving Cycle 5),
    // said at the top of its page until dismissed.
    var pageNotice by rememberSaveable { mutableStateOf<String?>(null) }
    // A pledge not in the list — cancelled, or made a moment ago while the
    // list reloads — is fetched on its own; and why one could not be opened.
    var outside by remember { mutableStateOf<Pledge?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Pledge?>(null) }
    var cancelling by remember { mutableStateOf<Pledge?>(null) }
    // The recurring gift collecting a pledge, opened from its page.
    var collector by remember { mutableStateOf<GivingSchedule?>(null) }
    // Held here, so the list keeps its place under an open page.
    val listScroll = rememberScrollState()
    // The member pulled the list down; its spinner shows until the reads end.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(vm.loading, vm.statementLoading) { if (!vm.loading && !vm.statementLoading) pulled = false }

    fun openPage(id: String, notice: String? = null) {
        vm.clearActionError(); openError = null
        pageNotice = notice; pageId = id
    }
    fun closePage() {
        pageId = null; pageNotice = null
        vm.clearActionError()
    }
    // Opened from outside (Giving Cycle 5): a Partners notice, a collector's
    // "Change it on the pledge", a pledge just made without its collection.
    LaunchedEffect(openPledge) {
        val want = openPledge ?: return@LaunchedEffect
        openPage(want.pledgeId, want.notice)
        onPledgeOpened()
    }
    LaunchedEffect(pageId, p != null) {
        val id = pageId ?: return@LaunchedEffect
        val listed = vm.partnership?.pledges ?: return@LaunchedEffect
        if (listed.any { it.pledgeId == id } || outside?.pledgeId == id) return@LaunchedEffect
        runCatching { Net.client.api.pledge(id).asPledge() }
            .onSuccess { outside = it }
            .onFailure { openError = ApiException.message(it); pageId = null; pageNotice = null }
    }
    // Re-resolved by id, so a change made ON the page shows the reloaded
    // pledge; one fetched on its own shows until the list has it.
    val page = pageId?.let { id -> p?.pledges?.firstOrNull { it.pledgeId == id } ?: outside?.takeIf { it.pledgeId == id } }

    if (page != null) {
        BackHandler { closePage() }
        PledgePage(
            pl = page,
            busy = vm.busyPledgeId == page.pledgeId,
            actionError = vm.actionError,
            notice = pageNotice,
            // Read with the standing, so the collector (or the pace offer)
            // is there the moment the page opens.
            methods = vm.methods,
            schedules = vm.schedules,
            onBack = { closePage() },
            onDismissNotice = { pageNotice = null },
            onRefresh = { vm.load() },
            onOpenReceipt = onOpenReceipt,
            onPayNow = {
                closePage()
                onPayNow(
                    GivePreset(
                        fundId = page.fund?.code, amountMinor = payNowAmount(page), pledgeId = page.pledgeId, title = page.displayTitle,
                        paysTo = page.paysTo, terms = pledgeTermsLine(page, today),
                        // Its currency decides the rails (Giving Cycle 5).
                        currency = page.currency,
                    ),
                )
            },
            onPauseResume = { vm.update(page.pledgeId, UpdatePledgeBody(status = if (page.status == "paused") "active" else "paused")) },
            onEdit = { vm.clearActionError(); editing = page },
            onCancel = { cancelling = page },
            onReminders = { on -> vm.update(page.pledgeId, UpdatePledgeBody(remindersEnabled = on)) },
            // Collecting it at its pace: the Give segment shows the first prompt.
            onScheduleStarted = { started -> closePage(); onScheduleStarted(started) },
            onOpenCollector = { s -> collector = s },
        )
    } else {
        // Pull down to read the standing and the statement again (iOS).
        NuruRefreshBox(
            refreshing = pulled && (vm.loading || vm.statementLoading),
            onRefresh = { pulled = true; vm.load() },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().background(GIVE.paper).verticalScroll(listScroll)) {
                PartnersHeaderBand(segmentControl)
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (vm.error != null && p != null) {
                        // A refresh failed but we still have a standing to show — said
                        // quietly, at the top (iOS).
                        Text("Couldn't refresh just now — showing what we last had.", style = giInter(11), color = GIVE.tertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    when {
                        p == null && vm.loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), Alignment.Center) {
                            CircularProgressIndicator(color = GIVE.gold)
                        }
                        // Nothing loaded yet and the read failed — what really
                        // happened, in the shared state card (§4, iOS parity).
                        p == null -> FailedState(vm.failure ?: StateLanguage.serverError, onRetry = { vm.load() })
                        // iOS's rule: the membership decides; an older server's is_partner otherwise.
                        p.isProgrammeMember -> {
                            StandingCard(p, vm.statements[LocalDate.now().year], onAddPledge, openStatement)
                            if (p.due.isNotEmpty()) {
                                DueSection(
                                    p.due, p, vm, onPayNow,
                                    onOpenSchedule = { id ->
                                        // "Collected on …": the gift's own sheet, here, as the
                                        // pledge page's collector row opens it.
                                        vm.schedules?.firstOrNull { it.scheduleId == id }?.let { collector = it } ?: onOpenSchedule(id)
                                    },
                                    // A pledge its collector takes (§6.4): the pledge's page.
                                    onOpenPledge = { id -> openPage(id) },
                                )
                            }
                            // Only when there is something to say — a partner whose
                            // giving is collecting cleanly never sees an amber row.
                            p.trouble?.let { t -> TroubleRow(t, vm.resuming, onResume = p.scheduleId?.let { id -> { vm.resume(id) } }) }
                            PledgesSection(p, vm, openError) { id -> openPage(id) }
                            StatementSection(p, vm, onOpenReceipt, openStatement)
                        }
                        else -> {
                            JoinCard(vm.joining, onJoin = { vm.join() })
                            vm.actionError?.let { Text(it, style = giInter(12), color = GIVE.danger) }
                        }
                    }
                }
            }
        }
    }

    // Over the page (or the list): edit, cancel, the gift that collects it.
    editing?.let { pl ->
        EditPledgeSheet(
            pl = pl, busy = vm.busyPledgeId == pl.pledgeId,
            // The server's refusal — an amount M-Pesa can't take for the
            // collector that follows this pledge is 422 AMOUNT_OUT_OF_RANGE —
            // said in the sheet, not behind it.
            error = vm.actionError,
            onDismiss = { editing = null },
            onSave = { patch ->
                // Only what changed travels (pledgeEditPatch); nothing → close.
                if (patch == null) editing = null else vm.update(pl.pledgeId, patch) { editing = null }
            },
        )
    }
    cancelling?.let { pl ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel “${pl.displayTitle}”?", style = NuruType.cardTitle, color = Nuru.navy) },
            text = {
                Text(
                    "Nothing already given is affected, and nothing further is owed. You can make a new pledge any time.",
                    style = NuruType.body, color = Nuru.ink600,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = pl.pledgeId; cancelling = null
                    vm.update(id, UpdatePledgeBody(status = "cancelled"))
                }) { Text("Cancel the pledge", style = NuruType.cardCta, color = Nuru.danger) }
            },
            dismissButton = {
                TextButton(onClick = { cancelling = null }) { Text("Keep it", style = NuruType.cardCta, color = Nuru.ink600) }
            },
        )
    }
    // The gift collecting a pledge — number, heads-up, pause, cancel; its
    // amount and day follow a monthly pledge (ScheduleSheet).
    collector?.let { s ->
        ScheduleSheet(
            s,
            onClose = { collector = null },
            onChanged = { GivingEvents.emit(); vm.load() },
            // Paused, resumed or cancelled: the sheet closes (iOS).
            onDone = { collector = null; GivingEvents.emit(); vm.load() },
            pledges = p?.pledges.orEmpty(),
            onOpenPledge = { id -> collector = null; openPage(id) },
            phoneOnFile = vm.methods?.phoneOnFile,
        )
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
            // One header (§8.1 rule 2): the switch above the kicker that names
            // the tab, the Fraunces title (26–28), one Inter line.
            Text("PARTNERS", style = NuruType.kicker, color = GIVE.eyebrow, modifier = Modifier.padding(top = 14.dp))
            Text("Walk with the church", style = giSerif(26, FontWeight.SemiBold, -0.52f), color = GIVE.navy, modifier = Modifier.padding(top = 4.dp))
            Text("Decide in advance. The church can plan.", style = giInter(13), color = GIVE.sub, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(text, style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.goldLo)
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
private fun StandingCard(p: Partnership, yearStatement: GivingStatement?, onAddPledge: () -> Unit, onOpenStatement: () -> Unit) {
    val view = LocalView.current
    val since = (p.membership?.joinedAt ?: p.since)?.let { PartnerFormat.monthYear(it) }
    val paused = p.membership?.status == "paused" || p.status == "paused" || p.trouble?.paused == true
    Column(Modifier.partnerCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("STANDING")
        // Nothing to stand on yet — no pledge, no gift collected: no "since",
        // no "0 gifts kept", no tier; the next step instead.
        if (!hasStanding(p)) {
            Text(
                "Your standing begins with your first gift or pledge.",
                style = giInter(14), color = GIVE.sub,
            )
        } else
        // iOS parity (HStack alignment .top): the standing and the tier chip
        // share the width (FairSplitRow) — a long tier name wraps inside its
        // chip instead of squeezing "Partner since Sep 2026" to a letter or
        // two a line, at any font scale.
        FairSplitRow(
            spacing = 16.dp,
            first = {
                Column {
                    Text(since?.let { "Partner since $it" } ?: "Partner", style = giInter(16, FontWeight.SemiBold), color = GIVE.navy)
                    // Commitments kept this year (a monthly pledge), else gifts
                    // collected (a schedule) — PartnerStatementMath.standingKeptLine.
                    Text(
                        standingKeptLine(p, yearStatement, paused),
                        style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 3.dp),
                    )
                }
            },
            second = p.tier?.takeIf { it.name.isNotBlank() }?.let { tier -> { TierChip(tier.name, tierSpoken(tier, p.currency)) } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // The ONLY gold-filled button on the page. At least 44dp tall, and
            // taller rather than clipped when a large font wraps its label.
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(Capsule).background(GIVE.gold)
                    .clickable { Haptics.tap(view); onAddPledge() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Add, null, tint = GIVE.navy, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Make a pledge", style = giInter(14, FontWeight.Bold), color = GIVE.navy, textAlign = TextAlign.Center)
            }
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(Capsule).border(1.5.dp, GIVE.navy, Capsule)
                    .clickable { onOpenStatement() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Text("Statement", style = giInter(14, FontWeight.Bold), color = GIVE.navy, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The partner's tier as iOS shows it: award icon, 11 bold, gold chip — the
 *  name wrapping inside the chip when it is a sentence ("carries one
 *  disciple through a level, every year"), the icon centred on it. TalkBack
 *  reads the tier's whole sentence ([spoken], iOS's accessibility label). */
@Composable
private fun TierChip(name: String, spoken: String) {
    Row(
        Modifier.clip(Capsule).background(GIVE.goldChipBg).clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.WorkspacePremium, null, tint = GIVE.goldChipText, modifier = Modifier.size(12.dp))
        Text(name, style = giInter(11, FontWeight.Bold), color = GIVE.goldChipText)
    }
}

/** The invitation to JOIN — one card, one line, one button. Joining is a
 *  decision, not a payment (spec §1); nothing else shows until joined. */
@Composable
private fun JoinCard(joining: Boolean, onJoin: () -> Unit) {
    Column(Modifier.partnerCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow("JOIN THE PARTNERS PROGRAMME")
        Text("Become a partner", style = giSerif(18, FontWeight.SemiBold), color = GIVE.navy)
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
            if (!joining) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── 2. Due + trouble ─────────────────────────────────────────────────────────

@Composable
private fun DueSection(
    due: List<DueItem>,
    p: Partnership,
    vm: PartnersViewModel,
    onPayNow: (GivePreset) -> Unit,
    /** A running recurring gift's "Collected on …" chip: open that gift. */
    onOpenSchedule: (String) -> Unit,
    /** A pledge's "Collected on …" chip (EXPERIENCE.md §6.4): open that pledge. */
    onOpenPledge: (String) -> Unit,
) {
    val view = LocalView.current
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("DUE")
        Column(Modifier.fillMaxWidth().clip(CardShape).background(GIVE.white).border(1.dp, GIVE.border, CardShape)) {
            // The server's order, as iOS lists them.
            due.forEachIndexed { i, d ->
                // Inset to the text on both sides, as iOS draws it.
                if (i > 0) Box(Modifier.padding(horizontal = 16.dp)) { Hairline() }
                val pledge = p.pledges.firstOrNull { it.pledgeId == d.id }
                // pending_minor: the part of this instalment already on its
                // way — Processing instead of Pay once it covers the amount.
                val shown = dueRowView(d, pendingMethodFor(d.id, vm.statements[today.year]))
                // The recurring gift that collects this pledge, read with the
                // standing: when it prompts on or before the instalment's day,
                // the row says so instead of Pay (§6.4). Gifts not read yet →
                // no collector, and Pay stays — never a guess.
                val pledgeCollected = pledge?.let { pl -> vm.schedules?.let { pledgeCollector(pl, it) } }
                    ?.let { pledgeCollectedChip(d, it) }
                // "today" · "in 3 days" · "5 Oct" — or, already past,
                // "overdue since 10 Aug" / "2 overdue since 10 Aug" in amber.
                val dueWhen = dueWhen(d, today)
                val what = if (d.kind == "schedule") {
                    "Recurring gift" + (p.rhythm?.method?.takeIf { it.isNotBlank() }?.let { " · ${giveMethodLabel(it)}" } ?: "")
                } else {
                    d.title.ifBlank { null } ?: pledge?.displayTitle ?: "Pledge"
                }
                // The due and what it is on the leading side, its button or
                // chip on the trailing one — while "KSh 5,000 · today" fits
                // beside it on one line; otherwise the chip goes below,
                // deliberately (LeadOrStackRow, §8.2 #20). The amount used to
                // split ("KSh 5,000 / · today") beside "Collected on Mon 5 Oct".
                LeadOrStackRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    spacing = 12.dp,
                    lead = {
                        // "KSh 3,000 left · in 3 days" — a content row's title
                        // (§8.1 rule 3, Fraunces 15 semibold); the whole line
                        // amber when the server says it is overdue (iOS).
                        Text(
                            dueLeadLine(d, shown, dueWhen.text),
                            style = NuruType.rowTitle,
                            color = if (dueWhen.overdue) GIVE.goldChipText else GIVE.ink,
                        )
                    },
                    rest = {
                        Text(
                            listOfNotNull(what, shown.processingNote).joinToString(" · "),
                            style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 2.dp),
                        )
                    },
                    trailing = {
                        if (d.action == "resume") {
                            NavyPill("Resume", enabled = !vm.resuming && vm.busyPledgeId == null) {
                                Haptics.tap(view)
                                if (d.kind == "schedule") vm.resume(d.id) else vm.update(d.id, UpdatePledgeBody(status = "active"))
                            }
                        } else if (shown.processingChip != null) {
                            // Already paid and on its way: no Pay to tap twice.
                            Box(Modifier.clearAndSetSemantics { contentDescription = "${shown.processingChip} — this payment is already on its way" }) {
                                StateChip(shown.processingChip, Nuru.warningBg, Nuru.answeredText)
                            }
                        } else if (dueCollectedChip(d) != null) {
                            // A running recurring gift collects itself — no Pay,
                            // which gave a second, one-time gift (owner,
                            // 2026-09-28). A tap opens the gift (pause, change).
                            val collected = dueCollectedChip(d).orEmpty()
                            CollectedChip(collected, "$collected, automatically. Opens the recurring gift.") {
                                Haptics.tap(view); onOpenSchedule(d.id)
                            }
                        } else if (pledgeCollected != null) {
                            // The same for a pledge its collector takes on or
                            // before the instalment's day (§6.4). A tap opens
                            // the pledge, where paying early by hand stays.
                            CollectedChip(pledgeCollected, "$pledgeCollected, automatically. Opens the pledge.") {
                                Haptics.tap(view); onOpenPledge(d.id)
                            }
                        } else {
                            NavyPill("Pay") {
                                Haptics.tap(view)
                                onPayNow(
                                    GivePreset(
                                        fundId = pledge?.fund?.code,
                                        // The uncovered remainder when part is already on its way.
                                        amountMinor = shown.leadMinor.takeIf { it > 0 } ?: pledge?.let(::payNowAmount),
                                        pledgeId = if (d.kind == "pledge") d.id else pledge?.pledgeId,
                                        title = d.title.ifBlank { null } ?: pledge?.displayTitle,
                                        // Where the server will route it, for the
                                        // PAYING YOUR PLEDGE card (GiveTargetCopy).
                                        paysTo = d.paysTo ?: pledge?.paysTo,
                                        terms = pledge?.let { pledgeTermsLine(it, today) },
                                        // Its currency decides the rails (Giving Cycle 5).
                                        currency = d.currency.takeIf { it.isNotBlank() } ?: pledge?.currency,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** A DUE row's "Collected on Mon 5 Oct" in place of Pay — a running
 *  recurring gift's, or a pledge's its collector takes — one line that
 *  shrinks rather than splits; [spoken] is what TalkBack reads. */
@Composable
private fun CollectedChip(text: String, spoken: String, onTap: () -> Unit) {
    Box(
        Modifier.heightIn(min = 32.dp).clip(Capsule).background(GIVE.goldChipBg)
            .clickable { onTap() }
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = giInter(12, FontWeight.SemiBold), color = GIVE.goldChipText,
            maxLines = 1, softWrap = false, modifier = Modifier.shrinkToFit(0.85f),
        )
    }
}

/** A failed or paused schedule, said once and plainly: nothing is owed
 *  (iOS TroubleRow). Resume only for a paused one the standing names a
 *  schedule for ([onResume] null otherwise) — a spinner beside it while it
 *  works. */
@Composable
private fun TroubleRow(t: PartnerTrouble, resuming: Boolean, onResume: (() -> Unit)?) {
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nuru.warningBg).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = Nuru.answeredText, modifier = Modifier.size(14.dp))
        Text(troubleLine(t), style = giInter(12, FontWeight.SemiBold), color = Nuru.answeredText, modifier = Modifier.weight(1f))
        if (t.paused && onResume != null) {
            Row(
                Modifier.heightIn(min = 32.dp).alpha(if (resuming) 0.7f else 1f).clip(Capsule).background(GIVE.navy)
                    .clickable(enabled = !resuming) { Haptics.tap(view); onResume() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (resuming) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                Text("Resume", style = giInter(12, FontWeight.Bold), color = Color.White)
            }
        }
    }
}

// ── 3. My pledges ────────────────────────────────────────────────────────────

/** PLEDGES — one card per live pledge; a tap opens its page. [openError] is
 *  why a pledge opened from outside could not be. */
@Composable
private fun PledgesSection(p: Partnership, vm: PartnersViewModel, openError: String?, onOpen: (String) -> Unit) {
    val live = p.pledges.filter { it.status != "cancelled" }
    val active = live.count { it.status == "active" }
    val today = LocalDate.now()
    // This year's statement: its pledges[] carry the server's kept/due per
    // pledge (PartnerStatementMath.pledgeKeptThisYear), its payments the
    // local fallback.
    val yearStatement = vm.statements[today.year]

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
                    PledgeCard(pl, busy = vm.busyPledgeId == pl.pledgeId, yearStatement = yearStatement, today = today) {
                        onOpen(pl.pledgeId)
                    }
                }
            }
        }
        vm.actionError?.let { Text(it, style = giInter(12), color = GIVE.danger) }
        openError?.let { Text(it, style = giInter(12), color = GIVE.danger) }
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
private fun PledgeCard(pl: Pledge, busy: Boolean, yearStatement: GivingStatement?, today: LocalDate, onOpen: () -> Unit) {
    val total = pl.shape == "total"
    val (chipText, chipBg, chipFg) = stateChip(pl)
    val left = if (total) {
        // "KSh 0 paid · 20,000 to go" — the currency once (iOS).
        totalPledgeLeftLine(pl)
    } else {
        // The server's "N of M" when this year's statement names the pledge,
        // else the local estimate; nothing at all while nothing is due.
        pledgeKeptLine(pl, yearStatement, today)
    }
    Column(Modifier.partnerCard(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                // A pledge is a content row (§8.1 rule 3): Fraunces 15 semibold.
                Text(pl.displayTitle, style = NuruType.rowTitle, color = GIVE.navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // The promise as iOS says it — "KSh 20,000 · by 31 Dec", the
                // date once (it read "KSh 20,000 by Dec · due 31 Dec").
                Text(pledgeAmountLine(pl, today), style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 2.dp))
            }
            if (busy) CircularProgressIndicator(color = GIVE.gold, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            StateChip(chipText, chipBg, chipFg)
        }
        // "40 percent" to TalkBack (iOS).
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(Capsule).background(GIVE.mutedBg)
                .clearAndSetSemantics { contentDescription = progressSpoken(progressFraction(pl)) },
        ) {
            Box(Modifier.fillMaxWidth(progressFraction(pl)).fillMaxHeight().clip(Capsule).background(GIVE.gold))
        }
        // "KSh 20,000 paid · KSh 30,000 to go" at the leading edge and "Next
        // 5 Oct" — or "Overdue since 10 Aug" in amber — at the trailing one,
        // sharing the width (FairSplitRow): long dollar amounts wrap rather
        // than squeeze the date into a column of letters.
        val next = pledgeNextLabel(pl, today)
        if (left != null || next != null) FairSplitRow(
            modifier = Modifier.fillMaxWidth(),
            spacing = 8.dp,
            first = { left?.let { Text(it, style = giInter(11), color = GIVE.sub) } },
            second = next?.let { n ->
                {
                    Text(
                        n.text,
                        style = giInter(11, if (n.overdue) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (n.overdue) GIVE.goldChipText else GIVE.sub,
                        textAlign = TextAlign.End,
                    )
                }
            },
        )
        // A total pledge's pace lives on its page, not here (iOS).
    }
}

internal fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** Edit name / amount / due day (spec §5 PATCH + pledge names), as iOS's
 *  EditPledgeSheet: the NAME (prefilled with the member's own, else the
 *  derived one — a cleared name goes back to the derived one), the promise
 *  in the pledge's own currency (EACH MONTH or TOTAL, the suggested amounts
 *  or one of the member's own), and a monthly pledge's DUE DAY. Only a
 *  CHANGE travels (pledgeEditPatch: a monthly pledge's `amount_minor`, a
 *  total pledge's TARGET); Save waits until something changed. A collector
 *  that follows the pledge moves with it; an amount it can't take is the
 *  server's refusal, said here ([error]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPledgeSheet(
    pl: Pledge,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (UpdatePledgeBody?) -> Unit,
) {
    val view = LocalView.current
    val dollars = currencyCode(pl.currency) == USD_CURRENCY
    val monthly = pl.shape != "total"
    // The promise, in minor units of the pledge's own currency.
    var amountMinor by remember(pl.pledgeId) { mutableIntStateOf(pl.headlineMinor) }
    var customAmount by remember(pl.pledgeId) { mutableStateOf("") }
    var dueDay by remember(pl.pledgeId) { mutableIntStateOf(pl.dueDay ?: 1) }
    val hasCustom = !pl.customTitle.isNullOrBlank()
    var name by remember(pl.pledgeId) { mutableStateOf(pl.customTitle?.takeIf { it.isNotBlank() } ?: pl.displayTitle) }
    // A blank name is a valid CLEAR; anything else must be 2–60.
    val nameValid = name.isBlank() || pledgeTitleValid(name)
    val patch = pledgeEditPatch(pl, amountMinor.takeIf { it > 0 }, if (monthly) dueDay else null, pledgeEditTitlePatch(pl, name))
    val canSave = patch != null && amountMinor > 0 && nameValid && !busy
    val label = giInter(11, FontWeight.SemiBold, 1.6f)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nuru.paper) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Edit pledge", style = giSerif(18, FontWeight.SemiBold, -0.36f), color = GIVE.navy, modifier = Modifier.weight(1f))
                Box(Modifier.size(32.dp).clip(CircleShape).background(Nuru.surface).clickable { onDismiss() }, Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", tint = GIVE.navy, modifier = Modifier.size(15.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NAME", style = label, color = GIVE.overline)
                OutlinedTextField(
                    value = name,
                    onValueChange = { v -> name = v.take(PLEDGE_TITLE_MAX) },
                    singleLine = true,
                    placeholder = { Text("Name this pledge", style = giInter(14), color = Nuru.ink400) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = GIVE.gold, modifier = Modifier.size(15.dp)) },
                    suffix = {
                        Text("${name.trim().length}/$PLEDGE_TITLE_MAX", style = giInter(11), color = if (nameValid) Nuru.ink400 else Nuru.danger)
                    },
                    isError = !nameValid,
                    textStyle = giInter(14).copy(color = Nuru.ink),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(pledgeNameHelp(hasCustom), style = giInter(12), color = Nuru.ink400)
            }
            // The promise, in its own money.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (monthly) "EACH MONTH" else "TOTAL", style = label, color = GIVE.tertiary)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (dollars) "US$" else "KSh", style = giInter(14, FontWeight.Medium), color = GIVE.tertiary)
                    val shown = pledgeEditAmountText(amountMinor, pl.currency)
                    Text(shown, style = giSerif(TypeScale.amount(shown), FontWeight.SemiBold, -1.1f), color = GIVE.navy)
                }
            }
            // The suggested amounts, three to a row.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pledgeEditPresets(pl.currency).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { v ->
                            val on = amountMinor == v && customAmount.isEmpty()
                            Box(
                                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (on) GIVE.navy else Nuru.surface)
                                    .border(1.dp, if (on) Color.Transparent else Nuru.border, RoundedCornerShape(12.dp))
                                    .clickable { Haptics.tick(view); customAmount = ""; amountMinor = v },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("%,d".format(v / 100), style = giInter(13, FontWeight.SemiBold), color = if (on) Color.White else GIVE.navy)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            OutlinedTextField(
                value = customAmount,
                onValueChange = { v ->
                    // Give's rules, in the pledge's money: whole shillings, or dollars and cents.
                    customAmount = if (dollars) usdTyping(v) else v.filter { it.isDigit() }.take(8)
                    pledgeAmountMinor(customAmount, pl.currency)?.let { amountMinor = it }
                },
                singleLine = true,
                placeholder = {
                    Text(if (dollars) "Or enter your own amount, e.g. 20.00" else "Or enter your own amount", style = giInter(14), color = Nuru.ink400)
                },
                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = GIVE.gold, modifier = Modifier.size(15.dp)) },
                textStyle = giInter(14).copy(color = Nuru.ink),
                keyboardOptions = KeyboardOptions(keyboardType = if (dollars) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (dollars) Text("This pledge is in US dollars.", style = giInter(12), color = Nuru.ink400)
            if (monthly) {
                Text("DUE DAY", style = label, color = GIVE.overline)
                DueDayPicker(dueDay) { dueDay = it }
            }
            error?.let { Text(it, style = giInter(12), color = Nuru.danger) }
            // Gold, as iOS's sheet buttons; it waits until something changed.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (canSave || busy) 1f else 0.5f)
                    .clip(RoundedCornerShape(14.dp)).background(GIVE.gold)
                    .clickable(enabled = canSave) { Haptics.tap(view); onSave(patch) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = GIVE.navy, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (busy) "Saving…" else "Save changes", style = giInter(15, FontWeight.Bold), color = GIVE.navy, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 1–28 so every month has the day (spec §1), seven to a row — every day in
 *  sight, as iOS lays it out. Shared with NewPledgeFlow. */
@Composable
internal fun DueDayPicker(selected: Int, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..28).chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { d ->
                    val on = d == selected
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) GIVE.navy else Nuru.surface)
                            .border(1.dp, if (on) Color.Transparent else Nuru.border, RoundedCornerShape(10.dp))
                            .clickable { Haptics.tick(view); onSelect(d) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$d", style = nuruSans(13, FontWeight.SemiBold), color = if (on) Color.White else Nuru.navy)
                    }
                }
            }
        }
    }
}

/** A pledge's own PAGE — the card's tap target (iOS PledgeDetailView), over
 *  the Partners list with its own back. In iOS's order and words: the cream
 *  band ("YOUR PLEDGE", the pledge's name); the promise card ("KSh 5,000
 *  monthly · due on the 5th" · "KSh 0 of KSh 5,000 this month · KSh 0 given
 *  in all", and a total pledge's pace); the recurring gift that collects it,
 *  or "Collect it automatically at this pace"; the actions (Pay now — or Pay
 *  early while a gift collects it — · Pause,
 *  Edit | Cancel, I paid another way, Remind me); PAYMENTS (GET
 *  /giving/pledges/{id}); PAID ANOTHER WAY — what the member told the office
 *  (…/claims, Giving Cycle 5). A cancelled or fulfilled pledge shows its
 *  record and no actions. Pull down to read it all again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PledgePage(
    pl: Pledge,
    busy: Boolean,
    /** A failed action on this pledge — said here, not behind the page. */
    actionError: String?,
    /** Said first: why a pledge just made has no automatic collection. */
    notice: String?,
    /** GET /giving/methods and /giving/schedules, from the screen's
     *  ViewModel — null while unknown (then nothing is shown or offered). */
    methods: GivingMethodsRes?,
    schedules: List<GivingSchedule>?,
    onBack: () -> Unit,
    onDismissNotice: () -> Unit,
    /** Pulled down: the standing too — the pledge's progress, its collector. */
    onRefresh: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    onPayNow: () -> Unit,
    onPauseResume: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onReminders: (Boolean) -> Unit,
    onScheduleStarted: (StartedSchedule) -> Unit,
    onOpenCollector: (GivingSchedule) -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectivity = remember(context) { Connectivity(context) }
    val online by remember(connectivity) { connectivity.online() }.collectAsState(initial = connectivity.isOnline())
    val today = remember { partnerToday() }
    val fulfilled = pl.status == "fulfilled" || pl.progress.label == "fulfilled"
    val cancelled = pl.status == "cancelled"
    var detail by remember(pl.pledgeId) { mutableStateOf<PledgeDetail?>(null) }
    var error by remember(pl.pledgeId) { mutableStateOf<String?>(null) }
    var attempt by remember(pl.pledgeId) { mutableIntStateOf(0) }
    var refreshing by remember(pl.pledgeId) { mutableStateOf(false) }
    // What the member told the office about this pledge, newest first; a
    // failed read with nothing yet on screen is said quietly.
    var claims by remember(pl.pledgeId) { mutableStateOf<List<PledgeClaim>?>(null) }
    var claimsFailed by remember(pl.pledgeId) { mutableStateOf(false) }
    var claiming by remember(pl.pledgeId) { mutableStateOf(false) }
    LaunchedEffect(pl.pledgeId, attempt) {
        error = null
        runCatching { Net.client.api.pledge(pl.pledgeId) }
            .onSuccess { detail = it }
            // A failed re-read keeps the payments already on screen.
            .onFailure { if (detail == null) error = ApiException.message(it, context) }
        runCatching { Net.client.api.pledgeClaims(pl.pledgeId).data }
            .onSuccess { claims = it; claimsFailed = false }
            .onFailure { claimsFailed = claims == null }
        refreshing = false
    }
    // "Collect it automatically at this pace" (Giving Cycle 9): in flight,
    // its refusal in the server's words, and its key.
    var startingPace by remember(pl.pledgeId) { mutableStateOf(false) }
    var paceError by remember(pl.pledgeId) { mutableStateOf<String?>(null) }
    // Held only after a request that got no answer, so tapping again finds
    // the gift that request may have made (newGivingKey otherwise).
    var paceKey by remember(pl.pledgeId) { mutableStateOf<String?>(null) }

    /** "Collect it automatically at this pace": a monthly M-Pesa gift bound
     *  to the pledge at its pace, its first prompt now (PledgePaceLogic).
     *  Online only — money is never queued. */
    fun startPace() {
        if (startingPace || !online) return
        val key = paceKey ?: newGivingKey()
        val body = paceScheduleBody(pl, key) ?: return
        paceKey = key; startingPace = true; paceError = null
        scope.launch {
            var failure: Throwable? = null
            try {
                val created = Net.client.api.createSchedule(body)
                Haptics.confirm(view)
                // A schedule is the partnership's rhythm — the standing changes.
                GivingEvents.emit()
                onScheduleStarted(StartedSchedule(created, body))
            } catch (e: Exception) {
                failure = e
                // GIFT_IN_PROGRESS, SCHEDULE_EXISTS, PHONE_REQUIRED… the server's words.
                paceError = ApiException.message(e, context)
                Haptics.reject(view)
            } finally {
                if (!keepGiveKeyAfter(failure)) paceKey = null
                startingPace = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(GIVE.paper)) {
        PledgePageHeader(pl.displayTitle, onBack)
        NuruRefreshBox(
            refreshing = refreshing,
            onRefresh = { refreshing = true; attempt++; onRefresh() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // The pledge stands; only its automatic collection could not
                // be set up (auto_schedule_error) — said once, nothing blocks.
                notice?.let { AutoScheduleNotice(it, onDismissNotice) }
                actionError?.let { Text(it, style = giInter(12), color = GIVE.danger) }
                PledgePromiseCard(pl, today)
                // The recurring gift that collects it — ANY pledge, monthly or
                // total — or, for a total pledge with a pace, the offer
                // (PledgePaceLogic.pledgeCollection).
                val collection = pledgeCollection(pl, methods, schedules)
                when (val c = collection) {
                    is PledgeCollection.Collected -> CollectorCard(collectedLine(c.schedule, today)) {
                        Haptics.tap(view); onOpenCollector(c.schedule)
                    }
                    PledgeCollection.Offer -> PaceOfferCard(
                        starting = startingPace,
                        enabled = !startingPace && !busy && online,
                        online = online,
                        error = paceError,
                    ) { Haptics.tap(view); startPace() }
                    PledgeCollection.None -> Unit
                }
                if (!fulfilled && !cancelled) {
                    PledgeActionsCard(
                        paused = pl.status == "paused", busy = busy, online = online, reminders = pl.remindersEnabled,
                        // Collected automatically: "Pay early", not the call to action (§7 rule 6).
                        pay = pledgePayButton(collection),
                        onPayNow = onPayNow, onPauseResume = onPauseResume, onEdit = onEdit, onCancel = onCancel,
                        onPaidAnotherWay = { claiming = true }, onReminders = onReminders,
                    )
                }
                val d = detail
                when {
                    d == null && error == null -> Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), Alignment.Center) {
                        CircularProgressIndicator(color = GIVE.gold)
                    }
                    d == null -> Column(
                        Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(error.orEmpty(), style = giInter(14), color = GIVE.sub, textAlign = TextAlign.Center)
                        Text(
                            "Try again", style = giInter(11, FontWeight.SemiBold), color = Color.White,
                            modifier = Modifier.clip(Capsule).background(GIVE.navy)
                                .clickable { Haptics.tap(view); attempt++ }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    else -> PledgePaymentsCard(d.payments, onOpenReceipt)
                }
                PledgeClaimsCard(claims, claimsFailed, today)
            }
        }
    }

    // "I paid another way" — its form in a sheet over the page; the claim
    // joins PAID ANOTHER WAY the moment the office has it.
    if (claiming) {
        ModalBottomSheet(onDismissRequest = { claiming = false }, containerColor = Nuru.paper) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                ClaimForm(
                    pl = pl,
                    onSent = { made ->
                        claims = listOf(made) + claims.orEmpty().filter { it.claimId != made.claimId }
                        claimsFailed = false
                        claiming = false
                    },
                    onClose = { claiming = false },
                )
            }
        }
    }
}

/** The page's cream band (iOS): back, then "YOUR PLEDGE" over the pledge's
 *  name — the name IS the page's title (pledge names contract). */
@Composable
private fun PledgePageHeader(title: String, onBack: () -> Unit) {
    val view = LocalView.current
    GiveCreamHeaderBox {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp)) {
            ReceiptHeaderButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { Haptics.tap(view); onBack() }
            Text("YOUR PLEDGE", style = giInter(11, FontWeight.Bold, 1.4f), color = GIVE.eyebrow, modifier = Modifier.padding(top = 12.dp))
            Text(
                title, style = giSerif(26, FontWeight.SemiBold), color = GIVE.navy,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The promise, under the page's name (iOS): "KSh 5,000 monthly · due on the
 *  5th" or "KSh 20,000 · by 31 Dec"; "KSh 0 of KSh 5,000 this month · KSh 0
 *  given in all"; and a total pledge's pace to reach it on time. */
@Composable
private fun PledgePromiseCard(pl: Pledge, today: LocalDate) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(GIVE.white).padding(18.dp)) {
        Text(pledgeAmountLine(pl, today), style = giSerif(22, FontWeight.Medium), color = GIVE.ink)
        Text(pledgeGivenLine(pl, today), style = giInter(12), color = GIVE.ink600, modifier = Modifier.padding(top = 4.dp))
        // A total pledge's pace (Giving Cycle 9), as the server sets it.
        paceLine(pl, today)?.let { line ->
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.EventRepeat, null, tint = GIVE.gold, modifier = Modifier.padding(top = 2.dp).size(12.dp))
                Text(line, style = giInter(12, FontWeight.SemiBold), color = GIVE.navy)
            }
        }
    }
}

/** The recurring gift that collects the pledge — "Collected automatically —
 *  next KSh 5,000 on 5 Oct" — on every pledge it collects, monthly or total;
 *  it opens that gift's sheet. */
@Composable
private fun CollectorCard(line: String, onOpen: () -> Unit) {
    Row(
        Modifier.partnerCard(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Autorenew, null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
        Text(line, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = GIVE.ink300, modifier = Modifier.size(16.dp))
    }
}

/** "Collect it automatically at this pace" (iOS): an outlined button, its
 *  one-line promise, and why it waits while offline — or the server's
 *  refusal in its own words. */
@Composable
private fun PaceOfferCard(starting: Boolean, enabled: Boolean, online: Boolean, error: String?, onStart: () -> Unit) {
    Column(Modifier.partnerCard(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).alpha(if (enabled || starting) 1f else 0.5f)
                .clip(RoundedCornerShape(12.dp)).background(GIVE.white)
                .border(1.2.dp, GIVE.navy, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { onStart() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            if (starting) {
                CircularProgressIndicator(color = GIVE.navy, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            } else {
                Icon(Icons.Filled.EventRepeat, null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (starting) "Setting it up…" else "Collect it automatically at this pace",
                style = giInter(13, FontWeight.Bold), color = GIVE.navy, textAlign = TextAlign.Center,
            )
        }
        Text(PACE_OFFER_NOTE, style = giInter(12), color = Nuru.ink400)
        if (!online) {
            Text("You're offline — setting this up needs a connection.", style = giInter(11), color = Nuru.ink400)
        } else {
            error?.let { Text(it, style = giInter(12), color = GIVE.danger) }
        }
    }
}

/** The pledge's actions, in one card (iOS): Pay now → beside Pause (Resume)
 *  — or, while a running gift collects the pledge, "Pay early" beside Pause,
 *  both secondary ([pay], §7 rule 6); Edit | Cancel; "I paid another way" —
 *  online only, a claim about money is never queued; and "Remind me before
 *  it's due". */
@Composable
private fun PledgeActionsCard(
    paused: Boolean,
    busy: Boolean,
    online: Boolean,
    reminders: Boolean,
    pay: PledgePayButton,
    onPayNow: () -> Unit,
    onPauseResume: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onPaidAnotherWay: () -> Unit,
    onReminders: (Boolean) -> Unit,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(12.dp)
    // "Remind me before it's due" needs this phone's notification permission:
    // asked when it is turned on, with why (EXPERIENCE.md §7.2 #12).
    val notifyAsk = org.nuruplace.member.data.firebase.rememberNotificationAsk()
    Column(Modifier.partnerCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The page's one gold button — unless the pledge is collected
            // automatically, when paying early is a choice and wears Pause's
            // quiet style beside it. A paused pledge takes nothing.
            Row(
                Modifier.weight(1f).heightIn(min = 40.dp).alpha(if (paused) 0.5f else 1f)
                    .clip(shape)
                    .then(if (pay.primary) Modifier.background(GIVE.gold) else Modifier.background(GIVE.surface).border(1.dp, GIVE.border, shape))
                    .clickable(enabled = !paused && !busy) { Haptics.tap(view); onPayNow() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    pay.label, style = giInter(13, if (pay.primary) FontWeight.Bold else FontWeight.SemiBold),
                    color = GIVE.navy, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = GIVE.navy, modifier = Modifier.size(12.dp))
            }
            Row(
                Modifier.weight(1f).heightIn(min = 40.dp)
                    .clip(shape).background(GIVE.surface).border(1.dp, GIVE.border, shape)
                    .clickable(enabled = !busy) { Haptics.tap(view); onPauseResume() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = GIVE.navy, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                } else {
                    Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null, tint = GIVE.navy, modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (paused) "Resume" else "Pause", style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, textAlign = TextAlign.Center)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallAction("Edit", Icons.Filled.Edit, GIVE.ink600, enabled = !busy, modifier = Modifier.weight(1f), onClick = onEdit)
            Box(Modifier.width(1.dp).height(16.dp).background(GIVE.border))
            SmallAction("Cancel", Icons.Filled.Close, GIVE.danger, enabled = !busy, modifier = Modifier.weight(1f), onClick = onCancel)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val tint = if (online) GIVE.navy else GIVE.ink300
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp)
                    .clickable(enabled = online && !busy) { Haptics.tap(view); onPaidAnotherWay() },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Check, null, tint = tint, modifier = Modifier.size(12.dp))
                Text("I paid another way", style = giInter(12, FontWeight.SemiBold), color = tint, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = GIVE.ink300, modifier = Modifier.size(14.dp))
            }
            if (!online) Text("You're offline — telling the office needs a connection.", style = giInter(11), color = Nuru.ink400)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Notifications, null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
            Text("Remind me before it's due", style = giInter(13), color = GIVE.ink, modifier = Modifier.weight(1f))
            Switch(
                checked = reminders, enabled = !busy,
                onCheckedChange = { on ->
                    Haptics.tick(view)
                    onReminders(on)
                    if (on) notifyAsk.ask(org.nuruplace.member.data.firebase.NotificationWhy.PLEDGE_REMINDER)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = GIVE.gold, checkedThumbColor = Color.White),
            )
        }
    }
}

/** Edit / Cancel — a small action with its icon, half the row each. */
@Composable
private fun SmallAction(label: String, icon: ImageVector, tint: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { Haptics.tap(view); onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = giInter(12, FontWeight.SemiBold), color = tint)
    }
}

/** PAYMENTS — every payment counted toward the pledge, each opening its
 *  receipt; before the first, what will happen (iOS words). */
@Composable
private fun PledgePaymentsCard(payments: List<PledgePayment>, onOpenReceipt: (String) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(GIVE.white).border(1.dp, GIVE.border, shape).padding(16.dp)) {
        Text("PAYMENTS", style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
        if (payments.isEmpty()) {
            Text(
                "No payments yet — the first one will appear here the moment it settles.",
                style = giInter(13), color = GIVE.sub, modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            payments.forEach { pay -> PledgePaymentRow(pay, onOpenReceipt) }
        }
    }
}

/** One payment: the amount, then the day and its receipt code. */
@Composable
private fun PledgePaymentRow(pay: PledgePayment, onOpenReceipt: (String) -> Unit) {
    val opens = pay.transactionId.isNotBlank()
    Row(
        Modifier.fillMaxWidth().clickable(enabled = opens) { onOpenReceipt(pay.transactionId) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(GIVE.goldChipBg), Alignment.Center) {
            Icon(Icons.Filled.VolunteerActivism, null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(money(pay.amountMinor, pay.currency), style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
            val meta = listOfNotNull(pay.at?.let { PartnerFormat.dayMonthYear(it) }, pay.receiptCode?.takeIf { it.isNotBlank() })
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = giInter(11), color = GIVE.tertiary)
        }
        if (opens) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = GIVE.ink300, modifier = Modifier.size(16.dp))
    }
}

/** PAID ANOTHER WAY — each thing the member told the office and where it
 *  stands; hidden while there is none. A first read that failed says so
 *  quietly (pull down to try again). */
@Composable
private fun PledgeClaimsCard(claims: List<PledgeClaim>?, failed: Boolean, today: LocalDate) {
    val told = claims.orEmpty()
    if (told.isNotEmpty()) {
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.fillMaxWidth().clip(shape).background(GIVE.white).border(1.dp, GIVE.border, shape).padding(16.dp)) {
            Text("PAID ANOTHER WAY", style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.overline, modifier = Modifier.padding(bottom = 4.dp))
            told.forEach { ClaimRow(it, today) }
        }
    } else if (failed) {
        Text(
            "We couldn't load what you've told the office. Pull down to try again.",
            style = giInter(11), color = Nuru.ink400, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A payment the member told the office about: the amount and the day it
 *  was paid, where it stands — "The office is checking it" · "Recorded —
 *  thank you" · "The office couldn't match it" — and their note. */
@Composable
private fun ClaimRow(c: PledgeClaim, today: LocalDate) {
    val (tint, icon) = when (claimTone(c.status)) {
        ClaimTone.Waiting -> GIVE.goldChipText to Icons.Filled.Schedule
        ClaimTone.Recorded -> GIVE.successText to Icons.Filled.Verified
        ClaimTone.Unmatched -> GIVE.ink600 to Icons.Outlined.Cancel
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(claimRowLine(c, today), style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
            Text(claimStatusLine(c.status), style = giInter(12, FontWeight.SemiBold), color = tint)
            c.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = giInter(11), color = Nuru.ink400, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "I paid another way" — the form (iOS PledgeClaimSheet, PledgeClaimLogic):
 *  the amount in the PLEDGE's currency (whole shillings, or dollars and
 *  cents), the day it was paid (today back a year, Nairobi; today by
 *  default), an optional note. What stops it is said as the member types,
 *  and "Tell the office" waits for it. Online only — nothing is queued, so
 *  offline the form says so and cannot send. The server's refusals (a
 *  different currency, a day out of range, the same payment already told,
 *  five waiting) are shown in its words. Shown in a sheet over the page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimForm(pl: Pledge, onSent: (PledgeClaim) -> Unit, onClose: () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectivity = remember(context) { Connectivity(context) }
    val online by remember(connectivity) { connectivity.online() }.collectAsState(initial = connectivity.isOnline())
    val today = remember { partnerToday() }
    val dollars = currencyCode(pl.currency) == USD_CURRENCY
    var amountText by remember { mutableStateOf("") }
    var paidOn by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val problem = claimProblem(amountText, pl.currency, paidOn, note, today)
    val label = giInter(11, FontWeight.SemiBold, 1.6f)

    fun send() {
        if (!online || sending) return
        val plan = planClaim(amountText, pl.currency, paidOn, note, partnerToday()) as? ClaimPlan.Ready ?: return
        sending = true; error = null
        scope.launch {
            try {
                val made = Net.client.api.createClaim(pl.pledgeId, plan.body)
                Haptics.confirm(view)
                onSent(made)
            } catch (e: Exception) {
                // No answer at all, or the server's words (422 CURRENCY_MISMATCH
                // / INVALID_DATE, 409 CONFLICT).
                error = if (e is java.io.IOException) CLAIM_NO_ANSWER_LINE else ApiException.message(e, context)
                Haptics.reject(view)
            } finally {
                sending = false
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("I paid another way", style = giSerif(18, FontWeight.SemiBold, -0.36f), color = GIVE.navy, modifier = Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).background(Nuru.surface).clickable(enabled = !sending) { onClose() }, Alignment.Center) {
                Icon(Icons.Filled.Close, "Close", tint = GIVE.navy, modifier = Modifier.size(15.dp))
            }
        }
        Text(claimIntro(pl.displayTitle), style = giInter(12), color = Nuru.ink600)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(claimAmountLabel(pl.currency), style = label, color = GIVE.overline)
            OutlinedTextField(
                value = amountText,
                onValueChange = { v -> amountText = if (dollars) usdTyping(v) else v.filter { it.isDigit() }.take(8); error = null },
                singleLine = true,
                prefix = { Text(if (dollars) "US$ " else "KSh ", style = giInter(14, FontWeight.Medium), color = GIVE.tertiary) },
                placeholder = { Text(if (dollars) "e.g. 20.00" else "e.g. 2000", style = giInter(16), color = Nuru.ink400) },
                textStyle = giInter(16, FontWeight.SemiBold).copy(color = Nuru.ink),
                keyboardOptions = KeyboardOptions(keyboardType = if (dollars) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(claimAmountHelp(pl.currency), style = giInter(12), color = Nuru.ink400)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("THE DAY YOU PAID", style = label, color = GIVE.overline)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.surface)
                    .border(1.dp, Nuru.border, RoundedCornerShape(14.dp))
                    .clickable { picking = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = GIVE.gold, modifier = Modifier.size(18.dp))
                Text(paidOn.format(CLAIM_PICKED_DAY), style = giInter(14), color = Nuru.ink)
            }
            Text("Today, or any day in the last year.", style = giInter(12), color = Nuru.ink400)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("A NOTE FOR THE OFFICE · OPTIONAL", style = label, color = GIVE.overline)
            OutlinedTextField(
                value = note,
                onValueChange = { v -> note = v.take(CLAIM_NOTE_MAX); error = null },
                placeholder = { Text("e.g. Cash at the 9am service", style = giInter(14), color = Nuru.ink400) },
                leadingIcon = { Icon(Icons.Filled.Edit, null, tint = GIVE.gold, modifier = Modifier.size(15.dp)) },
                minLines = 2, maxLines = 5,
                textStyle = giInter(14).copy(color = Nuru.ink),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${note.trim().length}/$CLAIM_NOTE_MAX", style = giInter(11), color = Nuru.ink400,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
            )
        }
        // Offline first, then the server's refusal, then what the typing lacks.
        when {
            !online -> Text(CLAIM_OFFLINE_LINE, style = giInter(12, FontWeight.SemiBold), color = Nuru.answeredText)
            error != null -> Text(error.orEmpty(), style = giInter(12), color = Nuru.danger)
            amountText.isNotEmpty() && problem != null -> Text(problem, style = giInter(12), color = Nuru.danger)
        }
        val canSend = problem == null && online && !sending
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (canSend || sending) 1f else 0.5f)
                .clip(RoundedCornerShape(14.dp)).background(GIVE.gold)
                .clickable(enabled = canSend) { Haptics.tap(view); send() }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            if (sending) {
                CircularProgressIndicator(color = GIVE.navy, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(if (sending) "Sending…" else "Tell the office", style = giInter(15, FontWeight.Bold), color = GIVE.navy, textAlign = TextAlign.Center)
        }
        Text(
            "Nothing is charged. It's added to your pledge once the office confirms it.",
            style = giInter(12), color = Nuru.ink400, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }

    if (picking) {
        // Only the days the server takes: a year back to today (Nairobi).
        val range = claimDateRange(today)
        val years = range.start.year..range.endInclusive.year
        val state = rememberDatePickerState(
            initialSelectedDateMillis = paidOn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = years,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    claimDateAllowed(Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate(), today)
                override fun isSelectableYear(year: Int): Boolean = year in years
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (claimDateAllowed(d, today)) { paidOn = d; error = null }
                    }
                    picking = false
                }) { Text("Set date", style = NuruType.cardCta, color = Nuru.gold) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel", style = NuruType.cardCta, color = Nuru.ink600) } },
        ) { DatePicker(state = state) }
    }
}

/** The day picked on the claim form: "Monday, 28 September 2026". */
private val CLAIM_PICKED_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

/** "Charge me automatically" could not be set up when the pledge was made
 *  (Giving Cycle 5): the pledge stands, and the server's words say why —
 *  one amber row, until dismissed. */
@Composable
private fun AutoScheduleNotice(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nuru.warningBg)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, null, tint = Nuru.answeredText, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Your pledge is made — automatic collection isn't set up.", style = giInter(12, FontWeight.SemiBold), color = Nuru.answeredText)
            Text(message, style = giInter(12), color = GIVE.ink600)
        }
        Box(Modifier.size(28.dp).clip(CircleShape).clickable { onDismiss() }, Alignment.Center) {
            Icon(Icons.Filled.Close, "Dismiss", tint = Nuru.ink400, modifier = Modifier.size(12.dp))
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
                    // One line per currency — never one sum across them: the
                    // server's summary_by_currency (Cycle 9), else summed here.
                    val sums = partnerStatementSummaries(shownYear, s, p.pledges)
                    Row(Modifier.fillMaxWidth()) {
                        SummaryColumn("PLEDGED", sums.map { money(it.pledgedMinor, it.currency) }, GIVE.navy, Modifier.weight(1f))
                        SummaryColumn("PAID", sums.map { money(it.paidMinor, it.currency) }, GIVE.successText, Modifier.weight(1f))
                        SummaryColumn("REMAINING", sums.map { money(it.remainingMinor, it.currency) }, GIVE.goldLo, Modifier.weight(1f))
                    }
                    Hairline()
                    val rows = pledgePayments(s.payments).sortedByDescending { it.occurredAt ?: "" }
                    // Still processing: listed first, never in Paid above (iOS).
                    val pending = pendingPaymentRows(s)
                    Column {
                        pending.forEach { pay ->
                            PendingPledgePaymentRow(
                                pay, statementPaymentTitle(pay.pledgeTitle, null, pay.pledgeId, p.pledges), onOpenReceipt,
                            )
                            Hairline()
                        }
                        if (rows.isEmpty() && pending.isEmpty()) {
                            Text("No pledge payments in $shownYear.", style = giInter(13), color = GIVE.ink600)
                        } else {
                            rows.forEachIndexed { i, pay ->
                                if (i > 0) Hairline()
                                StatementPaymentRow(pay, statementPaymentTitle(pay.pledgeTitle, pay.title, pay.pledgeId, p.pledges), onOpenReceipt)
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
    SummaryColumn(label, listOf(value), color, modifier)
}

/** A summary figure per currency, one under another, each the same size —
 *  the same currency on the same line in every column, never added
 *  together (iOS partnerSummaryColumn). */
@Composable
internal fun SummaryColumn(label: String, values: List<String>, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.tertiary)
        // One line each, shrunk to the column (iOS minimumScaleFactor 0.7) —
        // "KSh 1,250,000" never splits mid-figure in a third of the card.
        values.forEachIndexed { i, v ->
            Text(
                v, style = giInter(16, FontWeight.SemiBold), color = color,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(top = if (i == 0) 4.dp else 3.dp).shrinkToFit(0.7f),
            )
        }
    }
}

/** "20 Sep · Monthly pledge" over "Tithe · UIKJ2713B5" — the fund first,
 *  then the receipt code (iOS StatementPaymentRow) — amount at right. */
@Composable
private fun StatementPaymentRow(pay: StatementPayment, title: String, onOpenReceipt: (String) -> Unit) {
    val date = partnerDate(pay.occurredAt)?.let { PartnerFormat.dayMonth(it) }
    val meta = statementPaymentMeta(pay)
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = pay.transactionId.isNotBlank()) { onOpenReceipt(pay.transactionId) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(date, title).joinToString(" · "), style = giInter(13, FontWeight.SemiBold), color = GIVE.navy,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Text(meta, style = giInter(11), color = Nuru.ink400, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text(money(pay.amountMinor, pay.currency), style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, maxLines = 1)
    }
}

/** A pledge payment still processing (iOS PendingPledgePaymentRow): the
 *  pledge, an amber "Waiting for M-Pesa" chip and its day, the amount muted
 *  — it is in no total yet. Tap → its receipt, which says so too. */
@Composable
internal fun PendingPledgePaymentRow(pay: StatementPendingPayment, title: String, onOpenReceipt: (String) -> Unit) {
    val day = partnerDate(pay.at)?.let { PartnerFormat.dayMonth(it) }
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = pay.transactionId.isNotBlank()) { onOpenReceipt(pay.transactionId) }
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(title, pendingChipText(pay.method), day, money(pay.amountMinor, pay.currency))
                    .joinToString(", ") + ". Not yet counted in your totals"
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    pendingChipText(pay.method), style = giInter(11, FontWeight.Bold), color = Nuru.answeredText,
                    modifier = Modifier.clip(Capsule).background(Nuru.warningBg).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                day?.let { Text(it, style = giInter(11), color = Nuru.ink400) }
            }
        }
        Text(money(pay.amountMinor, pay.currency), style = giInter(13, FontWeight.SemiBold), color = Nuru.ink400, maxLines = 1)
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
        Text(title, style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
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
