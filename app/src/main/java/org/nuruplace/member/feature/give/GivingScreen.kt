// Give — the member giving tab, ported pixel-for-pixel from the iOS GivingView.
// Money is server-authoritative + ONLINE-ONLY (§5.6): pick a fund + amount +
// method, then create a REAL intent server-side (never fabricated, never queued).
// M-Pesa STK push is the default; PayPal returns an approve URL; SOON methods are
// disabled. Recurring gifts create schedules (POST /giving/schedules — the
// intent-vs-schedule decision is GiveSubmitLogic.kt, pinned by a unit test);
// the active-schedules rail lets you review + cancel one in a bottom sheet.
// A pledge's "Pay now" (Partners) opens this screen with a GivePreset — fund,
// amount and the pledge_id the intent must carry — in PLEDGE-PAY MODE: the fund
// chooser and the frequency control step aside for one PAYING YOUR PLEDGE card
// (GiveTargetCopy.kt), since the server, not the member, picks a pledge's fund.
// A department need's preset wears the same shape (GIVING TO A NEED). A bound
// gift that goes through SPENDS its binding (the double-pay guard,
// GiveSubmitLogic.giftSpendsBinding): cleared upstream the moment the intent
// answers, and the form back to the ordinary one when the ceremony closes.
// Every gift that moves money raises GivingEvents so Partners and its
// statement refetch (GivingEvents.kt). The segment's own server data — the
// year pill, the schedules rail — lives in GiveViewModel, held by the tab's
// destination: shown at once on every showing while it refetches (entry,
// segment switch, ON_RESUME, GivingEvents), so money that lands without a
// ceremony (a scheduled charge, another device) appears. The ceremony watches the real
// transaction until it is final (GiveCeremonyCopy.kt); a Pay tap replays the
// previous idempotency key only after an attempt that got no server answer
// (GiveSubmitLogic.giveKeyFor). All shared tokens/tables live in
// GiveShared.kt (same package — no import).
package org.nuruplace.member.feature.give

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GivingIntentResult
import org.nuruplace.member.data.net.GivingRecord
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PayPalCaptureBody
import org.nuruplace.member.ui.components.CelebrationCenter
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.Moment
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val Capsule = RoundedCornerShape(999.dp)

private fun freqLabel(f: Int): String = when (f) { 0 -> "one-time"; 1 -> "weekly"; else -> "monthly" }

/** Parse an ISO-8601 timestamp's year, best-effort. */
private fun isoYear(iso: String?): Int? =
    iso?.takeIf { it.isNotBlank() }?.let { s ->
        runCatching { OffsetDateTime.parse(s).year }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10)).year }.getOrNull()
            ?: s.take(4).toIntOrNull()
    }

/** "12 Mar 2026" from an ISO timestamp, best-effort — falls back to the raw string's date part. */
private fun prettyDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    return runCatching { OffsetDateTime.parse(iso).toLocalDate().format(fmt) }.getOrNull()
        ?: runCatching { LocalDate.parse(iso.take(10)).format(fmt) }.getOrNull()
        ?: iso.take(10)
}

private fun firstChargeDate(freq: Int): String =
    LocalDate.now().plusDays(if (freq == 1) 7L else 30L)
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

/** What the Give segment shows from the server: the history (the year
 *  pill), the schedules (the rail) and the phone the STK push goes to. */
data class GiveSegmentData(val history: List<GivingRecord>, val schedules: List<GivingSchedule>, val phone: String)

/**
 * The Give segment's server data, held by the tab's DESTINATION (viewModel())
 * rather than the segment, which leaves composition on every GIVE · PARTNERS
 * switch: the last values show at once while they refetch — on every showing,
 * on ON_RESUME and on GivingEvents — so a scheduled charge or a gift from
 * another device lands in "given this year" without a ceremony.
 */
class GiveViewModel : ViewModel() {
    var data by mutableStateOf<GiveSegmentData?>(null); private set
    private val freshness = GivingFreshness()
    private var seq = 0

    init {
        viewModelScope.launch {
            GivingEvents.changed.debouncedGivingReloads().collect {
                // Only once the segment has asked for its data.
                if (seq > 0 && freshness.shouldRefetchAfterEvent()) load()
            }
        }
    }

    /** Entry / resume: refetch unless a fetch has just started. */
    fun refresh() {
        if (freshness.shouldRefetchOnEntry()) load()
    }

    /** Refetch now. A part that fails keeps what is on screen (the very
     *  first load falls back to empty, as before); the latest request wins. */
    fun load() {
        freshness.fetchStarted()
        val mine = ++seq
        viewModelScope.launch {
            val hist = runCatching { Net.client.api.givingHistory().data }
            val sched = runCatching { Net.client.api.schedules().data }
            val phone = runCatching { Net.client.api.me().profile.phoneNumber.orEmpty() }
            if (mine != seq) return@launch
            val shown = data
            data = GiveSegmentData(
                history = hist.getOrElse { shown?.history ?: emptyList() },
                schedules = sched.getOrElse { shown?.schedules ?: emptyList() },
                phone = phone.getOrElse { shown?.phone ?: "" },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GivingScreen(
    onBack: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenSchedules: () -> Unit = {},
    /** A pledge's "Pay now": fund + amount preset, pledge_id carried into the intent. */
    preset: GivePreset? = null,
    /** The tab's GIVE · PARTNERS control (GiveTabScreen) — the first row of
     *  this screen's header band, so the member can switch even mid-load. */
    segmentControl: @Composable () -> Unit = {},
    /** The binding is gone — spent by a gift that went through, or dropped
     *  by "Give to a fund instead": forget the preset upstream so no path
     *  back to this screen binds it again. */
    onUnbind: () -> Unit = {},
    /** A binding spent on the intent's answer comes back: the ceremony's
     *  watch found the payment failed, so the member can retry at once. */
    onRebind: (GivePreset) -> Unit = {},
    /** Scoped to the tab's destination, so it outlives the segment. */
    vm: GiveViewModel = viewModel(),
) {
    // Stale-while-revalidate on every showing of the segment (entry, a
    // GIVE · PARTNERS switch, a Pay handoff) and every return to the
    // foreground; the two can land together — refresh() fetches once.
    LaunchedEffect(Unit) { vm.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val d = vm.data
    if (d == null) {
        // First load only — every later refetch keeps the values on screen.
        Column(Modifier.fillMaxSize().background(GIVE.paper)) {
            GiveHeaderBand(segmentControl, yearTotalMinor = null, onOpenStatement = onOpenStatement)
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GIVE.gold)
            }
        }
        return
    }
    GiveTab(d.history, d.schedules, d.phone, vm::load, onOpenStatement, onOpenSchedules, preset, segmentControl, onUnbind, onRebind)
}

/** The ONE cream band over the Give segment: the segment control, the title,
 *  the subline, then the year pill (→ statement) beside the eye that masks
 *  it. `yearTotalMinor == null` while history is still loading — the pill
 *  waits, the rest does not. A bound gift is said by the PAYING YOUR PLEDGE /
 *  GIVING TO A NEED card in the body, not by a chip here. */
@Composable
private fun GiveHeaderBand(
    segmentControl: @Composable () -> Unit,
    yearTotalMinor: Int?,
    onOpenStatement: () -> Unit,
) {
    val hidden = AppPrefs.hideGiveYearTotal
    GiveCreamHeaderBox {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp)) {
            segmentControl()
            Text("Sow into the Kingdom", style = giSerif(24, FontWeight.SemiBold, -0.48f), color = GIVE.navy, modifier = Modifier.padding(top = 14.dp))
            Text("Generosity is worship — a quiet, joyful act.", style = giInter(11), color = GIVE.sub, modifier = Modifier.padding(top = 4.dp))
            if (yearTotalMinor != null) {
                Row(
                    Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.clip(Capsule).background(GIVE.white)
                            .border(1.dp, GIVE.gold.copy(alpha = 0.45f), Capsule)
                            .clickable { onOpenStatement() }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
                        Text(
                            (if (hidden) "KSh ••••" else ksh(yearTotalMinor)) + " given this year",
                            style = giInter(13, FontWeight.SemiBold), color = GIVE.eyebrow,
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(13.dp))
                    }
                    // Masks the amount on a phone that gets shown around.
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(GIVE.white)
                            .border(1.dp, GIVE.border, CircleShape)
                            .clickable { AppPrefs.updateHideGiveYearTotal(!hidden) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (hidden) "Show this year's total" else "Hide this year's total",
                            tint = GIVE.navy, modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** PAYING YOUR PLEDGE / GIVING TO A NEED — where a bound gift goes, in place
 *  of the fund chooser: the name, the pledge's terms, the fund the server
 *  routes it to, and a quiet way out to an ordinary gift. */
@Composable
private fun GiveTargetCard(copy: GiveTargetCopy, onGiveToFund: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GIVE.priorityBg)
            .border(1.dp, GIVE.gold.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(copy.kicker, style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
        Text(
            copy.title, style = giSerif(20, FontWeight.SemiBold, -0.4f), color = GIVE.navy,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
        copy.terms?.let { Text(it, style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 2.dp)) }
        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = GIVE.goldChipText, modifier = Modifier.size(13.dp))
            Text(copy.destination, style = giInter(12, FontWeight.SemiBold), color = GIVE.goldChipText)
        }
        Text(
            "Give to a fund instead", style = giInter(12, FontWeight.Medium), color = GIVE.ink600,
            modifier = Modifier.padding(top = 8.dp).clip(Capsule).clickable { onGiveToFund() }
                .padding(vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiveTab(
    history: List<GivingRecord>,
    schedules: List<GivingSchedule>,
    phone: String,
    reload: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenSchedules: () -> Unit = {},
    preset: GivePreset? = null,
    segmentControl: @Composable () -> Unit = {},
    onUnbind: () -> Unit = {},
    onRebind: (GivePreset) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val seed = remember { giveFormSeed(preset) }

    // What this gift is bound to — a pledge's Pay or a need's Give — and so
    // what the PAYING YOUR PLEDGE card says. "Give to a fund instead" clears
    // it (the iOS "Remove"): the amount stays, the chooser and the frequency
    // control come back, and the gift is an ordinary one.
    var target by remember { mutableStateOf(preset?.takeIf { it.isTargeted }) }
    var fundId by remember { mutableStateOf(seed.fundId) }
    var amountMajor by remember { mutableIntStateOf(seed.amountMajor) }
    var customOpen by remember { mutableStateOf(false) }
    // "Named giving" (custom sheet, optional): set from the custom-amount
    // dialog. Rides the M-Pesa AccountReference + persists for
    // receipts/statements/portal Finance.
    var accountName by remember { mutableStateOf("") }
    if (customOpen) {
        CustomAmountDialog(
            initial = amountMajor,
            initialName = accountName.ifBlank { AppPrefs.lastGivingAccountName },
            onConfirm = { amt, name ->
                amountMajor = amt
                accountName = name.orEmpty()
                if (!name.isNullOrBlank()) AppPrefs.lastGivingAccountName = name
                customOpen = false
            },
            onDismiss = { customOpen = false },
        )
    }
    // 0 once / 1 weekly / 2 monthly (FREQ_* in GiveSubmitLogic.kt). A pledge's
    // "Pay now" (or a need's "Give to this need") is a one-time payment toward
    // it, so it lands on One-time.
    var freq by remember { mutableIntStateOf(seed.freq) }
    val methods = remember { mutableStateListOf(*GIVE_METHODS.toTypedArray()) }
    var selectedMethod by remember { mutableStateOf("mpesa") }
    var coverFee by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<GivingIntentResult?>(null) }
    // What the intent charged (fee inside), so the ceremony can say "KSh 1,000".
    var resultAmountMinor by remember { mutableIntStateOf(0) }
    var scheduled by remember { mutableStateOf<GivingSchedule?>(null) }
    var sheetSchedule by remember { mutableStateOf<GivingSchedule?>(null) }
    // The ceremony on screen is for a bound gift that went through: its
    // binding is already cleared upstream, and closing it resets the form.
    var ceremonySpentBinding by remember { mutableStateOf(false) }
    // What the ceremony's gift was bound to, as sent — handed back if its
    // watch finds the payment failed.
    var ceremonyBoundTo by remember { mutableStateOf<GivePreset?>(null) }
    // The idempotency key of the last attempt, held ONLY while that attempt
    // got no server answer, so an identical retry replays it
    // (GiveSubmitLogic.giveKeyFor / keepGiveKeyAfter).
    var heldKey by remember { mutableStateOf<HeldGiveKey?>(null) }

    /** Double-pay guard: back to the ordinary form — no pledge or need, the
     *  fund chooser and frequency control, the default fund and amount, no
     *  gift name or fee. The member's payment method stays. */
    fun resetToOrdinaryGift() {
        val ordinary = giveFormSeed(null)
        target = null
        fundId = ordinary.fundId
        amountMajor = ordinary.amountMajor
        freq = ordinary.freq
        accountName = ""
        coverFee = false
        error = null
    }

    // Full-screen generosity ceremony once an intent is created. The copy
    // reads the RESULT (GiveCeremonyCopy.kt): the server names the fund it
    // routed the gift to and the pledge it counts toward; the chip label is
    // only the fallback for a result that carries neither.
    result?.let { r ->
        GiveResult(
            // A bound gift's fund is the server's to name (pays_to); the
            // chooser's tile was never shown, so it is never the fallback.
            r, amountMinor = resultAmountMinor,
            chipFundLabel = if (target != null) target?.paysTo?.name?.takeIf { it.isNotBlank() } else giveFund(fundId).name,
            giftName = accountName.trim().ifBlank { null },
            onOutcome = { outcome ->
                // The server's final word, read by the watch: everything that
                // counts the gift refetches (the Processing row goes).
                GivingEvents.emit()
                // It didn't go through after all (the STK was cancelled or
                // timed out): a binding spent on the intent's answer comes
                // back — unless the member let it go meanwhile — so they can
                // retry at once, as the double-pay guard promises.
                if (outcome == GiftOutcome.Failed && ceremonySpentBinding) {
                    ceremonySpentBinding = false
                    val back = ceremonyBoundTo
                    if (back != null && target == back) onRebind(back)
                }
            },
            // Done: the PIN has been entered by now, so the gift has most
            // likely settled — one more refetch for everything that counts it.
            // A bound gift that went through leaves an ordinary form behind,
            // so the same instalment cannot be paid twice. (Leaving by the
            // segment control or navigation lands on a fresh, unbound form:
            // the binding was cleared upstream when the intent answered.)
            // The ceremony resolved: the next Pay tap mints a fresh key.
            onDone = {
                result = null
                heldKey = null
                if (ceremonySpentBinding) { ceremonySpentBinding = false; resetToOrdinaryGift() }
                GivingEvents.emit()
            },
        )
        return
    }
    // …or once the server really created a schedule (never faked here).
    scheduled?.let { s ->
        ScheduledResult(s, fundLabel = giveFund(fundId).name, onDone = { scheduled = null; reload() })
        return
    }

    val thisYear = LocalDate.now().year
    val yearTotalMinor = history
        .filter { it.status in listOf("succeeded", "settled") && (isoYear(it.settledAt ?: it.createdAt) == thisYear) }
        .sumOf { it.amountMinor }

    val activeSchedules = schedules.filter { it.status == "active" }

    fun submit() {
        // One request at a time: a second tap inside the same frame (before
        // the disabled button recomposes) must not start a second payment.
        if (busy) return
        val method = methods.firstOrNull { it.id == selectedMethod } ?: return
        // One-time → intent; Weekly/Monthly → schedule (mobile money only);
        // cover-fee inside amount_minor; pledge_id carried. GiveSubmitLogic.kt.
        // A bound gift is one-time whatever the (hidden) control last held.
        val sendFreq = if (target != null) FREQ_ONCE else freq
        // The key: replayed only when the last attempt at this EXACT gift got
        // no server answer; otherwise fresh.
        val attempt = giveKeyFor(
            heldKey,
            GiveRequestShape(
                amountMajor = amountMajor, fundId = fundId, methodId = method.id,
                pledgeId = target?.pledgeId, needId = target?.needId, freq = sendFreq,
                giftName = accountName, coverFee = coverFee, phone = phone,
            ),
        ) { UUID.randomUUID().toString() }
        val plan = planGiveSubmission(
            freq = sendFreq, provider = method.provider, fundId = fundId, amountMajor = amountMajor,
            coverFee = coverFee, phone = phone, accountName = accountName,
            idempotencyKey = attempt.key, pledgeId = target?.pledgeId,
            needId = target?.needId,
        )
        if (plan is GiveSubmission.Blocked) { error = plan.message; return }
        // What this request is bound to, as sent — not whatever the form
        // holds when the answer lands.
        val boundTo = target
        heldKey = attempt
        busy = true; error = null
        scope.launch {
            var failure: Throwable? = null
            try {
                when (plan) {
                    is GiveSubmission.Intent -> {
                        resultAmountMinor = plan.body.amountMinor
                        // A replay (`reused: true`) is read exactly like a
                        // fresh answer — the ceremony watches either.
                        val r = Net.client.api.giving(plan.body)
                        ceremonyBoundTo = boundTo
                        // Spent at once upstream (before the ceremony shows),
                        // so every way off this screen forgets the binding.
                        if (giftSpendsBinding(boundTo, r.status)) {
                            ceremonySpentBinding = true
                            onUnbind()
                        }
                        result = r
                        // Succeeded, or pending on a PIN / card / PayPal:
                        // Partners, its statement and the year pill refetch.
                        if (givingIntentAnnounces(r.status)) GivingEvents.emit()
                    }
                    is GiveSubmission.Schedule -> {
                        scheduled = Net.client.api.createSchedule(plan.body)
                        // A schedule is the partnership's rhythm — the standing changes.
                        GivingEvents.emit()
                    }
                    is GiveSubmission.Blocked -> error = plan.message
                }
            } catch (e: Exception) {
                failure = e
                error = ApiException.message(e)
            } finally {
                // No server answer (transport failure / timeout): hold the key
                // so an identical retry replays it. Any answer releases it.
                if (!keepGiveKeyAfter(failure)) heldKey = null
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(GIVE.paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ── Header — the one cream band (segment control · title · year pill) ──
            GiveHeaderBand(segmentControl, yearTotalMinor = yearTotalMinor, onOpenStatement = onOpenStatement)

            // ── Body ──
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Pledge-pay / need mode: ONE card says where the gift goes,
                // in place of the chooser (the server picks a pledge's fund).
                val targetCopy = giveTargetCopy(target, chargedAmountMajor(amountMajor, coverFee))
                if (targetCopy != null) {
                    GiveTargetCard(targetCopy) { Haptics.tick(view); target = null; onUnbind() }
                } else {
                    // Funds row
                    Text("CHOOSE A FUND", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GIVE_FUNDS.forEach { f ->
                            val on = f.id == fundId
                            Column(
                                Modifier.width(124.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (on) GIVE.priorityBg else GIVE.white)
                                    .border(if (on) 2.dp else 1.dp, if (on) GIVE.gold else GIVE.border, RoundedCornerShape(16.dp))
                                    .clickable { fundId = f.id }
                                    .padding(12.dp),
                            ) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(f.tint), contentAlignment = Alignment.Center) {
                                    Icon(f.icon, contentDescription = null, tint = f.fg, modifier = Modifier.size(17.dp))
                                }
                                Text(f.name, style = giInter(13, FontWeight.SemiBold, -0.13f), color = GIVE.navy, modifier = Modifier.padding(top = 8.dp))
                                Text(f.tagline, style = giInter(10), color = GIVE.sub, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }

                // Amount card
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(22.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("AMOUNT", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.tertiary)
                    Row(
                        Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("KSh", style = giInter(14, FontWeight.Medium), color = GIVE.tertiary)
                        Text("%,d".format(amountMajor), style = giSerif(42, FontWeight.SemiBold, -1.2f), color = GIVE.navy)
                    }
                    Text(
                        targetCopy?.amountSubtitle ?: "${giveFund(fundId).name} · ${freqLabel(freq)}",
                        style = giInter(11), color = GIVE.sub, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GIVE_PRESETS.forEach { p ->
                            val on = amountMajor == p
                            Text(
                                "%,d".format(p),
                                style = giInter(13, FontWeight.SemiBold),
                                color = if (on) Color.White else GIVE.navy,
                                modifier = Modifier.clip(Capsule)
                                    .background(if (on) GIVE.navy else GIVE.surface)
                                    .then(if (on) Modifier else Modifier.border(1.dp, GIVE.border, Capsule))
                                    .clickable { amountMajor = p }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    // The custom choice, BELOW the suggested amounts on its own
                    // row (owner's revision, 2026-08-24 — iOS parity): inside
                    // the scrolling chip row it slid out of sight, and a giver
                    // who wants their own number should never have to hunt.
                    Row(
                        Modifier.padding(top = 8.dp).fillMaxWidth().clip(Capsule)
                            .background(GIVE.white)
                            .border(1.dp, GIVE.gold.copy(alpha = 0.55f), Capsule)
                            .clickable { customOpen = true }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Edit, null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enter a custom amount", style = giInter(13, FontWeight.Bold), color = GIVE.gold)
                    }
                }

                // Frequency segmented — a bound gift is one-time, so no control.
                if (targetCopy == null) Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GIVE.track).padding(4.dp),
                ) {
                    listOf("One-time", "Weekly", "Monthly").forEachIndexed { i, label ->
                        val on = freq == i
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                                .then(if (on) Modifier.background(GIVE.white) else Modifier)
                                .clickable { freq = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = giInter(13, FontWeight.SemiBold), color = if (on) GIVE.navy else GIVE.sub)
                        }
                    }
                }

                // Recurring summary
                if (freq != 0 && targetCopy == null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GIVE.priorityBg)
                            .border(1.dp, GIVE.gold.copy(alpha = 0.25f), RoundedCornerShape(18.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(GIVE.goldTile), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("${kshMajor(amountMajor)} every ${if (freq == 1) "week" else "month"}", style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
                            Text(
                                "First charge ${firstChargeDate(freq)} · then every ${if (freq == 1) "week" else "month"}. Cancel anytime.",
                                style = giInter(11), color = GIVE.sub,
                            )
                        }
                    }
                }

                // Pay methods
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CHOOSE HOW TO PAY", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = GIVE.tertiary, modifier = Modifier.size(11.dp))
                        Text("Reorder", style = giInter(11), color = GIVE.tertiary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.forEachIndexed { i, m ->
                        val soon = m.provider == null
                        val on = m.id == selectedMethod && !soon
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (on) GIVE.priorityBg else GIVE.white)
                                .border(if (on) 1.5.dp else 1.dp, if (on) GIVE.gold else GIVE.border, RoundedCornerShape(18.dp))
                                .then(if (soon) Modifier.alpha(0.7f) else Modifier.clickable { selectedMethod = m.id })
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // badge
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(m.badgeBg), contentAlignment = Alignment.Center) {
                                if (m.badgeText != null) {
                                    Text(
                                        m.badgeText,
                                        style = giInter(if (m.badgeText.length > 3) 8 else 11, FontWeight.Bold, -0.2f),
                                        color = m.badgeFg,
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    Icon(m.badgeIcon!!, contentDescription = null, tint = m.badgeFg, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(m.label, style = giInter(14, FontWeight.SemiBold, -0.14f), color = GIVE.navy)
                                if (on) {
                                    Text(
                                        if (m.id == "mpesa" || m.id == "airtel") phone.ifBlank { m.sub } else m.sub,
                                        style = giInter(11), color = GIVE.sub,
                                    )
                                }
                            }
                            if (soon) {
                                Box(Modifier.clip(Capsule).background(GIVE.goldChipBg).padding(horizontal = 9.dp, vertical = 4.dp)) {
                                    Text("SOON", style = giInter(10, FontWeight.Bold, 0.5f), color = GIVE.goldChipText)
                                }
                            }
                            if (on) {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(GIVE.gold), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(13.dp))
                                }
                            }
                            // reorder arrows
                            Column {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", tint = GIVE.ink300,
                                    modifier = Modifier.size(14.dp).clickable {
                                        if (i > 0) { val t = methods[i]; methods[i] = methods[i - 1]; methods[i - 1] = t }
                                    },
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", tint = GIVE.ink300,
                                    modifier = Modifier.size(14.dp).clickable {
                                        if (i < methods.lastIndex) { val t = methods[i]; methods[i] = methods[i + 1]; methods[i + 1] = t }
                                    },
                                )
                            }
                            Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = Color(0xFFC4C9D0), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Cover-fee row
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(18.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cover the transaction fee", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
                        Text(
                            if (coverFee) "Adds ${kshMajor(giveFee(amountMajor))} — you'll be charged ${kshMajor(chargedAmountMajor(amountMajor, true))}"
                            else "Adds ${kshMajor(giveFee(amountMajor))} — 100% reaches the fund",
                            style = giInter(11), color = GIVE.sub,
                        )
                    }
                    Switch(
                        checked = coverFee,
                        onCheckedChange = { coverFee = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = GIVE.gold, checkedThumbColor = Color.White),
                    )
                }

                // Active schedules rail
                if (activeSchedules.isNotEmpty()) {
                    Text("ACTIVE SCHEDULES", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        activeSchedules.forEach { s ->
                            Column(
                                Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.white)
                                    .border(1.dp, GIVE.border, RoundedCornerShape(16.dp))
                                    .clickable { sheetSchedule = s }
                                    .padding(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(12.dp))
                                    Text(s.frequency.uppercase(), style = giInter(11, FontWeight.Bold, 1.4f), color = GIVE.overline)
                                }
                                Text(ksh(s.amountMinor), style = giInter(15, FontWeight.Bold, -0.15f), color = GIVE.navy, modifier = Modifier.padding(top = 4.dp))
                                Text(s.fund.replaceFirstChar { it.uppercase() }, style = giInter(13), color = GIVE.sub)
                                Text("Next ${prettyDate(s.nextRunAt)}", style = giInter(11), color = GIVE.tertiary)
                            }
                        }
                    }
                }

                // Manage schedules — the recurring-gifts list (route "schedules")
                // was unreachable before the Partners programme (spec §0).
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)).background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(14.dp))
                        .clickable { onOpenSchedules() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Manage schedules", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
                        Text(
                            if (activeSchedules.isEmpty()) "No recurring gifts yet" else "${activeSchedules.size} active · review or cancel",
                            style = giInter(11), color = GIVE.sub,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GIVE.ink300, modifier = Modifier.size(14.dp))
                }
            }
        }

        // ── Sticky CTA ──
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(GIVE.paper)
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        ) {
            error?.let {
                Text(it, style = giInter(12), color = GIVE.danger, modifier = Modifier.padding(bottom = 8.dp))
            }
            // A BLOCK, not an outline (owner, 2026-08-24 — iOS parity): solid
            // gold with navy text, the same voice as every primary CTA.
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(GIVE.gold, Color(0xFFB6862F)),
                        ),
                    )
                    .clickable(enabled = amountMajor > 0 && !busy) { submit() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val boundCta = giveTargetCopy(target, chargedAmountMajor(amountMajor, coverFee))?.cta
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = GIVE.navy, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Processing…", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
                } else if (boundCta != null) {
                    // "Pay KSh 1,000 toward General partnership" — a long name
                    // ellipsizes; the arrow keeps its room.
                    Text(
                        boundCta, style = giInter(14, FontWeight.Bold), color = GIVE.navy,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GIVE.navy, modifier = Modifier.padding(end = 16.dp).size(14.dp))
                } else if (freq != FREQ_ONCE) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Schedule ${kshMajor(chargedAmountMajor(amountMajor, coverFee))} / ${if (freq == FREQ_WEEKLY) "week" else "month"}", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
                } else {
                    Text("Give ${kshMajor(chargedAmountMajor(amountMajor, coverFee))}", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
                }
            }
        }

        // ── Recurring-gift bottom sheet ──
        sheetSchedule?.let { s ->
            ModalBottomSheet(onDismissRequest = { sheetSchedule = null }) {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recurring gift", style = giSerif(18, FontWeight.SemiBold, -0.36f), color = GIVE.navy)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(GIVE.surface).clickable { sheetSchedule = null },
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
                            Text(ksh(s.amountMinor), style = giInter(17, FontWeight.Bold), color = GIVE.navy)
                            Text(
                                "Every ${if (s.frequency == "weekly") "week" else "month"} · ${s.fund.replaceFirstChar { it.uppercase() }}",
                                style = giInter(12), color = GIVE.sub,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SheetDetailRow("Fund", s.fund.replaceFirstChar { it.uppercase() })
                    SheetDivider()
                    SheetDetailRow("Amount", ksh(s.amountMinor))
                    SheetDivider()
                    SheetDetailRow("Frequency", s.frequency.replaceFirstChar { it.uppercase() })
                    SheetDivider()
                    SheetDetailRow("Next charge", prettyDate(s.nextRunAt))
                    SheetDivider()
                    SheetDetailRow("Method", s.method.ifBlank { "—" }.replaceFirstChar { it.uppercase() })
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.cancelBg)
                            .border(1.dp, GIVE.cancelBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                scope.launch {
                                    try {
                                        Net.client.api.cancelSchedule(s.scheduleId)
                                        GivingEvents.emit()
                                        reload()
                                    } catch (_: Exception) {}
                                }
                                sheetSchedule = null
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("Cancel schedule", style = giInter(13, FontWeight.Bold), color = GIVE.cancelText)
                    }
                }
            }
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
        Text(value, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
    }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GIVE.border))
}

/** Full-screen ceremony once the server really created a schedule — the first
 *  charge is the server's next cycle boundary (`next_run_at`), shown as such;
 *  "Cancel anytime" is true because Manage schedules is one tap away. */
@Composable
private fun ScheduledResult(s: GivingSchedule, fundLabel: String, onDone: () -> Unit) {
    LaunchedEffect(s.scheduleId) {
        CelebrationCenter.fire(Moment("schedule-${s.scheduleId}", "Thank you for committing", "Faithfulness, month after month, carries the gospel further."))
    }
    val cadence = if (s.frequency == "weekly") "week" else "month"
    val firstCharge = s.nextRunAt.takeIf { it.isNotBlank() }?.let { prettyDate(it) }
        ?: firstChargeDate(if (s.frequency == "weekly") FREQ_WEEKLY else FREQ_MONTHLY)
    Box(Modifier.fillMaxSize().background(GIVE.paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(GIVE.gold.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(GIVE.gold), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Scheduled", style = giSerif(24, FontWeight.Medium, -0.48f), color = GIVE.navy, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "${ksh(s.amountMinor)} every $cadence to ${s.fund.replaceFirstChar { it.uppercase() }.ifBlank { fundLabel }}.",
                style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "First charge $firstCharge · then every $cadence. Cancel anytime from Manage schedules.",
                style = giInter(13), color = GIVE.sub, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.gold)
                    .clickable { onDone() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Done", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
            }
        }
    }
}

/** Full-screen generosity ceremony once an intent is created. Its words come
 *  from GiveCeremonyCopy.kt: "Enter your PIN to complete KSh 1,000 toward your
 *  Building pledge." / "… to Tithe." — the RESULT's pledge and fund, never the
 *  chip, which is only the fallback when the result carries no fund — until
 *  the watch reads the transaction's final status: "Gift confirmed" or "The
 *  payment didn't complete". */
@Composable
private fun GiveResult(
    r: GivingIntentResult,
    amountMinor: Int,
    chipFundLabel: String? = null,
    giftName: String? = null,
    /** The gift reached its final outcome while on screen. */
    onOutcome: (GiftOutcome) -> Unit = {},
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    // PayPal is a two-step settle: approve in the browser, then POST
    // /giving/paypal/capture with the order id (the intent's provider_ref) —
    // without the capture the gift never settles (money §5.6: online-only).
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    // The transaction's status: first the intent's answer — fresh or a replay
    // of the same key, read the same way — then what the watch or a PayPal
    // capture reports. Server truth only (§5.6).
    var status by remember(r.transactionId) { mutableStateOf(r.status) }
    var watchLapsed by remember(r.transactionId) { mutableStateOf(false) }
    val outcome = giftOutcome(status)
    // The watch (iOS parity): GET /giving/transactions/{id} every 3 s, at most
    // 20 times, while the gift is processing. Ends with the ceremony.
    LaunchedEffect(r.transactionId) {
        if (r.transactionId.isBlank()) return@LaunchedEffect
        var readings = 0
        while (keepWatchingGift(giftOutcome(status), readings)) {
            delay(CEREMONY_WATCH_INTERVAL_MS)
            readings++
            runCatching { Net.client.api.givingDetail(r.transactionId) }.getOrNull()?.let { status = it.status }
        }
        if (giftOutcome(status) == GiftOutcome.Processing) watchLapsed = true
    }
    // A final outcome, once each: the human moment only on the server's
    // success — never on a pending intent — and the caller is told.
    val onOutcomeNow by rememberUpdatedState(onOutcome)
    LaunchedEffect(outcome) {
        if (outcome == GiftOutcome.Processing) return@LaunchedEffect
        if (outcome == GiftOutcome.Succeeded) {
            CelebrationCenter.fire(Moment("gift-${r.transactionId.ifBlank { r.providerRef.orEmpty() }}", "Thank you for sowing", "Every gift carries the gospel further."))
        }
        onOutcomeNow(outcome)
    }
    Box(Modifier.fillMaxSize().background(GIVE.paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Badge — gold@0.18 halo + gold core + navy check; a muted cross
            // when the payment didn't complete.
            val failed = outcome == GiftOutcome.Failed
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(if (failed) GIVE.mutedBg else GIVE.gold.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(if (failed) GIVE.ink300 else GIVE.gold), contentAlignment = Alignment.Center) {
                    Icon(if (failed) Icons.Filled.Close else Icons.Filled.Check, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                giveCeremonyTitle(outcome),
                style = giSerif(24, FontWeight.Medium, -0.48f),
                color = GIVE.navy,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                giveCeremonyStatusLine(r, amountMinor, chipFundLabel, outcome, watchLapsed),
                style = giInter(13),
                color = when (outcome) { GiftOutcome.Succeeded -> GIVE.successText; GiftOutcome.Failed -> GIVE.danger; else -> GIVE.sub },
                textAlign = TextAlign.Center,
            )
            // Where it went — the pledge by name and the fund the church
            // routed it to, else the fund — with the member's own gift name
            // ("named giving") when they gave one. Stays through success.
            giveDestinationLabel(r, chipFundLabel, giftName)?.let { label ->
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = giInter(12, FontWeight.SemiBold),
                    color = GIVE.eyebrow,
                    textAlign = TextAlign.Center,
                )
            }

            if (r.approveUrl != null && outcome == GiftOutcome.Processing) {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.navy)
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.approveUrl)))
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Continue on PayPal", style = giInter(14, FontWeight.SemiBold), color = Color.White)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(GIVE.white).border(1.dp, GIVE.gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable(enabled = !capturing && !r.providerRef.isNullOrBlank()) {
                            capturing = true; captureError = null
                            scope.launch {
                                // The capture's own answer is the status —
                                // "succeeded" confirms; "processing" means
                                // PayPal hasn't released it yet.
                                runCatching { Net.client.api.capturePayPal(PayPalCaptureBody(r.providerRef!!)) }
                                    .onSuccess { res ->
                                        if (res.status.isNotBlank()) status = res.status
                                        if (giftOutcome(res.status) == GiftOutcome.Processing) {
                                            captureError = "PayPal hasn't confirmed it yet — finish approving there, then try again."
                                        }
                                    }
                                    .onFailure { captureError = org.nuruplace.member.data.net.ApiException.message(it) }
                                capturing = false
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (capturing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = GIVE.gold, strokeWidth = 2.dp)
                    } else {
                        Text("I've approved — confirm gift", style = giInter(14, FontWeight.SemiBold), color = GIVE.gold)
                    }
                }
                captureError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = giInter(11), color = GIVE.danger, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.gold)
                    .clickable { onDone() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Done", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
            }
        }
    }
}

/** Preset gift-name chips on the custom-amount sheet — like an M-Pesa Paybill
 *  account name, shown on the church's M-Pesa statement (sanitized server-side). */
private val GIVE_NAME_PRESETS = listOf("Tithe", "Offering", "Building", "Missions", "Thanksgiving", "First Fruits")

// Custom giving amount — a real editor (numeric keyboard, KSh, 1..2,000,000)
// plus an optional "Name your gift" field (named giving).
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CustomAmountDialog(
    initial: Int,
    initialName: String = "",
    onConfirm: (Int, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial.toString()) }
    var name by remember { mutableStateOf(initialName) }
    val parsed = text.filter { it.isDigit() }.take(7).toIntOrNull() ?: 0
    val valid = parsed in 1..2_000_000
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GIVE.white,
        title = { Text("Enter amount", style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    prefix = { Text("KSh ", style = giInter(15, FontWeight.Medium), color = GIVE.sub) },
                    textStyle = giSerif(24, FontWeight.SemiBold).copy(color = GIVE.navy),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!valid && text.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Enter an amount between KSh 1 and KSh 2,000,000.", style = giInter(12), color = GIVE.sub)
                }

                Spacer(Modifier.height(16.dp))
                Text("NAME YOUR GIFT (OPTIONAL)", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GIVE_NAME_PRESETS.forEach { p ->
                        val on = name == p
                        Text(
                            p,
                            style = giInter(12, FontWeight.SemiBold),
                            color = if (on) Color.White else GIVE.navy,
                            modifier = Modifier.clip(Capsule)
                                .background(if (on) GIVE.navy else GIVE.surface)
                                .then(if (on) Modifier else Modifier.border(1.dp, GIVE.border, Capsule))
                                .clickable { name = if (on) "" else p }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { v -> name = v.take(60) },
                    singleLine = true,
                    placeholder = { Text("e.g. \"For Mom's healing\"", style = giInter(13), color = GIVE.tertiary) },
                    textStyle = giInter(13, FontWeight.Medium).copy(color = GIVE.navy),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Shows on the church's M-Pesa statement — like a Paybill account name.",
                    style = giInter(10),
                    color = GIVE.tertiary,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (valid) onConfirm(parsed, name.trim().ifBlank { null }) },
                enabled = valid,
            ) {
                Text("Set amount", style = giInter(14, FontWeight.Bold), color = if (valid) GIVE.gold else GIVE.sub)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", style = giInter(14, FontWeight.SemiBold), color = GIVE.sub)
            }
        },
    )
}
