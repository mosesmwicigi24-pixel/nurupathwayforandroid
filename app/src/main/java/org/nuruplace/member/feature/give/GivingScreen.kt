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
//
// Giving Cycle 1: the form starts on One-time, and a Weekly / Monthly pick is
// confirmed ("nothing is taken today — the first prompt comes on …") before a
// schedule exists. The methods come from GET /giving/methods: only what the
// server can take AND this app can carry is selectable (GiveMethodsLogic.kt),
// so a card or Airtel is never sent while switched off. The M-Pesa number the
// prompt goes to is on the screen with a way to change it, remembered on this
// device after a gift goes through. A failed gift says why, in the server's
// words, with a Try again back to the same gift; 409 GIFT_IN_PROGRESS follows
// the prompt already on the phone; a schedule is cancelled only after asking.
package org.nuruplace.member.feature.give

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.outlined.VerifiedUser
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.CreateScheduleBody
import org.nuruplace.member.data.net.CreatedScheduleRes
import org.nuruplace.member.data.net.GiftFailure
import org.nuruplace.member.data.net.GivingDetail
import org.nuruplace.member.data.net.GivingIntentResult
import org.nuruplace.member.data.net.GivingMethodsRes
import org.nuruplace.member.data.net.GivingRecord
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PayPalCaptureBody
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.RetryGiftBody
import org.nuruplace.member.ui.components.CelebrationCenter
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.Moment
import java.time.Instant
import java.time.LocalDate

private val Capsule = RoundedCornerShape(999.dp)

private fun freqLabel(f: Int): String = when (f) { 0 -> "one-time"; 1 -> "weekly"; else -> "monthly" }

/** "12 Mar 2026" — an ISO timestamp's Nairobi day (the day the member lives
 *  it, as the statement and receipt date theirs), best-effort — falls back to
 *  the raw string's date part. */
private fun prettyDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return nairobiDayOf(iso) ?: iso.take(10)
}

/** The first prompt of a schedule created now — the server's own rule
 *  (ScheduleCopy.firstPromptAt), as a Nairobi day. */
private fun firstPromptDay(freq: Int): String = nairobiDay(firstPromptAt(Instant.now(), freq))

/** What the Give segment shows from the server: the history (the year
 *  pill), the schedules (the rail), the methods it can give with and the
 *  profile's number for a prompt (Giving Cycle 1). */
data class GiveSegmentData(
    val history: List<GivingRecord>,
    val schedules: List<GivingSchedule>,
    /** GET /giving/methods; null until it has answered once — the form then
     *  offers M-Pesa alone (GiveMethodsLogic.giveMethodOptions). */
    val methods: GivingMethodsRes?,
    /** The profile's number as E.164 when it is a Kenyan mobile, else null. */
    val phoneOnFile: String?,
    /** The member's pledges — fetched only when a schedule collects one, so
     *  its sheet knows a MONTHLY pledge owns its amount and day (Cycle 5). */
    val pledges: List<Pledge> = emptyList(),
)

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
            // The rails and the number on file, in one answer. An older
            // server has no /giving/methods: the profile's number, read the
            // same way, stands in (and the form offers M-Pesa alone).
            val methods = runCatching { Net.client.api.givingMethods() }
            val phoneOnFile = methods.map { it.phoneOnFile }.recoverCatching {
                kenyanMobileE164(Net.client.api.me().profile.phoneNumber)
            }
            // A pledge's collector: whether its pledge is monthly decides
            // where its amount and day change. None collecting one → no call.
            val pledges = if (sched.getOrNull()?.any { it.pledge != null } == true) {
                runCatching { Net.client.api.pledges().data }
            } else {
                Result.success(emptyList())
            }
            if (mine != seq) return@launch
            val shown = data
            data = GiveSegmentData(
                history = hist.getOrElse { shown?.history ?: emptyList() },
                schedules = sched.getOrElse { shown?.schedules ?: emptyList() },
                methods = methods.getOrElse { shown?.methods },
                phoneOnFile = phoneOnFile.getOrElse { shown?.phoneOnFile },
                pledges = pledges.getOrElse { shown?.pledges ?: emptyList() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GivingScreen(
    onBack: () -> Unit,
    onOpenStatement: () -> Unit,
    /** A RECENT GIVING row's receipt. */
    onOpenReceipt: (String) -> Unit = {},
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
    /** A gift to open on its result — a giving_gift_failed push (Giving
     *  Cycle 3) — and the call that marks it opened, so it opens once. */
    followTransactionId: String? = null,
    onFollowed: () -> Unit = {},
    /** Open a pledge on Partners — a pledge collector's "Change it on the
     *  pledge" (Giving Cycle 5). */
    onOpenPledge: (String) -> Unit = {},
    /** A recurring gift just set up elsewhere — a pledge's "Collect it
     *  automatically at this pace" (Giving Cycle 9) — shown on the same
     *  result as the form's give-now, and the call that marks it shown. */
    startedSchedule: StartedSchedule? = null,
    onStartedShown: () -> Unit = {},
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
            GiveHeaderBand(segmentControl, yearTotals = null, onOpenStatement = onOpenStatement)
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GIVE.gold)
            }
        }
        return
    }
    GiveTab(
        d.history, d.schedules, d.methods, d.phoneOnFile, vm::load, onOpenStatement, onOpenReceipt, preset,
        segmentControl, onUnbind, onRebind, followTransactionId, onFollowed,
        pledges = d.pledges, onOpenPledge = onOpenPledge,
        startedSchedule = startedSchedule, onStartedShown = onStartedShown,
    )
}

/** The ONE cream band over the Give segment: the segment control, the title,
 *  the subline, then the year pill (→ statement) beside the eye that masks
 *  it. `yearTotals == null` while history is still loading — the pill waits,
 *  the rest does not. The pill is per currency ("KSh 3,500 + US$ 20.00 given
 *  this year", Giving Cycle 2) — a dollar gift is never added to shillings.
 *  A bound gift is said by the PAYING YOUR PLEDGE / GIVING TO A NEED card in
 *  the body, not by a chip here. */
@Composable
private fun GiveHeaderBand(
    segmentControl: @Composable () -> Unit,
    yearTotals: List<CurrencyAmount>?,
    onOpenStatement: () -> Unit,
) {
    val hidden = AppPrefs.hideGiveYearTotal
    GiveCreamHeaderBox {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp)) {
            segmentControl()
            Text("Sow into the Kingdom", style = giSerif(24, FontWeight.SemiBold, -0.48f), color = GIVE.navy, modifier = Modifier.padding(top = 14.dp))
            Text("Generosity is worship — a quiet, joyful act.", style = giInter(11), color = GIVE.sub, modifier = Modifier.padding(top = 4.dp))
            if (yearTotals != null) {
                Row(
                    Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // The pill takes what is left beside the eye (weighted, not
                    // filling): "KSh 3,500 + US$ 20.00 given this year" wraps
                    // inside it at a large font instead of pushing the eye out.
                    Row(
                        Modifier.weight(1f, fill = false).clip(Capsule).background(GIVE.white)
                            .border(1.dp, GIVE.gold.copy(alpha = 0.45f), Capsule)
                            .clickable { onOpenStatement() }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
                        Text(
                            (if (hidden) "KSh ••••" else moneyTotals(yearTotals)) + " given this year",
                            style = giInter(13, FontWeight.SemiBold), color = GIVE.eyebrow,
                            modifier = Modifier.weight(1f, fill = false),
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

/** "Repeat last gift" (iOS repeatCard): the gold tile, what the gift was —
 *  "KSh 1,000 · Tithe · via M-Pesa" — and Give again, which puts it back on
 *  the form. */
@Composable
private fun RepeatGiftCard(g: GivingRecord, onRepeat: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(GIVE.priorityBg)
            .border(1.dp, GIVE.gold.copy(alpha = 0.25f), shape)
            .clickable { onRepeat() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(GIVE.gold), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Repeat last gift", style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
            Text(repeatGiftLine(g), style = giInter(11), color = GIVE.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Give again", style = giInter(12, FontWeight.SemiBold), color = GIVE.gold)
    }
}

/** RECENT GIVING (iOS recentSection): the three newest gifts that went
 *  through — fund, day · rail, amount — each opening its receipt; "View
 *  statement →" always there; before the first, what will happen. */
@Composable
private fun RecentGivingCard(recent: List<GivingRecord>, onOpenStatement: () -> Unit, onOpenReceipt: (String) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(GIVE.white).border(1.dp, GIVE.border, shape)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RECENT GIVING", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(Capsule).clickable { onOpenStatement() }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("View statement", style = giInter(12, FontWeight.SemiBold), color = GIVE.gold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(11.dp))
            }
        }
        if (recent.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.VolunteerActivism, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
                Text("No gifts yet — your first one will appear here the moment it settles.", style = giInter(13), color = GIVE.sub)
            }
        } else {
            recent.forEachIndexed { i, g ->
                if (i > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(GIVE.border))
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenReceipt(g.transactionId) }.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(giveFund(g.fund).name, style = giInter(14, FontWeight.SemiBold, -0.14f), color = GIVE.navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(recentGiftMeta(g), style = giInter(11), color = GIVE.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(money(g.amountMinor, g.currency), style = giInter(14, FontWeight.SemiBold, -0.14f), color = GIVE.navy, maxLines = 1)
                }
            }
        }
    }
}

/** 2 Corinthians 9:7 on a soft gold wash (iOS scriptureStrip). */
@Composable
private fun ScriptureStrip() {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(GIVE.gold.copy(alpha = 0.10f), GIVE.paper)))
            .border(1.dp, GIVE.gold.copy(alpha = 0.2f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "“Each of you should give what you have decided in your heart to give.”",
            style = giSerif(15, FontWeight.Medium).copy(fontStyle = FontStyle.Italic, lineHeight = 23.sp), color = GIVE.navy,
        )
        Text("2 Corinthians 9:7", style = giInter(11, FontWeight.SemiBold), color = GIVE.overline)
    }
}

/** The footer's promise (iOS secureNote): a shield and "Secure · M-Pesa ·
 *  Receipt sent instantly" — only the rails that can take money here. */
@Composable
private fun SecureNote(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = GIVE.tertiary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = giInter(11), color = GIVE.tertiary, textAlign = TextAlign.Center)
    }
}

/** A Weekly / Monthly gift waiting on the member's Confirm: the exact
 *  request, the key it will carry, and the number its prompts go to. */
private data class ScheduleToConfirm(val plan: GiveSubmission.Schedule, val attempt: HeldGiveKey, val phone: String?)

/** A schedule the server really created, shown from what the member
 *  confirmed — POST /giving/schedules answers with its id, status and first
 *  run only (and, with "start with a gift now", today's prompt or why it
 *  could not go out), so the rest is never read off the answer's defaults. */
private data class CreatedSchedule(val created: CreatedScheduleRes, val body: CreateScheduleBody, val phone: String?)

/** A recurring gift the server really created outside the Give form — a
 *  pledge's "Collect it automatically at this pace" (Giving Cycle 9) —
 *  handed to the Give tab to show on the form's own give-now result. */
data class StartedSchedule(val created: CreatedScheduleRes, val body: CreateScheduleBody)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiveTab(
    history: List<GivingRecord>,
    schedules: List<GivingSchedule>,
    methodsRes: GivingMethodsRes?,
    phoneOnFile: String?,
    reload: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenReceipt: (String) -> Unit = {},
    preset: GivePreset? = null,
    segmentControl: @Composable () -> Unit = {},
    onUnbind: () -> Unit = {},
    onRebind: (GivePreset) -> Unit = {},
    /** A gift to open on its result (a giving_gift_failed push), once. */
    followTransactionId: String? = null,
    onFollowed: () -> Unit = {},
    pledges: List<Pledge> = emptyList(),
    onOpenPledge: (String) -> Unit = {},
    startedSchedule: StartedSchedule? = null,
    onStartedShown: () -> Unit = {},
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
    // The dollar form's own amount (PayPal, Giving Cycle 2) — kept apart, so
    // switching back to M-Pesa finds the shillings untouched.
    var usdCents by remember { mutableIntStateOf(seed.usdCents) }
    var customOpen by remember { mutableStateOf(false) }
    // "Named giving" (custom sheet, optional): set from the custom-amount
    // dialog. Rides the M-Pesa AccountReference + persists for
    // receipts/statements/portal Finance.
    var accountName by remember { mutableStateOf("") }
    // 0 once / 1 weekly / 2 monthly (FREQ_* in GiveSubmitLogic.kt). Always
    // starts on One-time (Giving Cycle 1): recurring is a deliberate pick,
    // and a bound gift (a pledge's Pay, a need's Give) is one-time anyway.
    var freq by remember { mutableIntStateOf(seed.freq) }
    // The rows keep their look and the member's order (GIVE_METHODS); which
    // show and are selectable is the server's word, re-read on every refetch
    // (GiveMethodsLogic.kt). A pick stands only while it stays selectable —
    // otherwise the form falls back to the server's default rail — and a
    // pledge or need is paid in ITS currency (Giving Cycle 5): a KES one
    // with M-Pesa, a USD one with PayPal; the other rails are not shown.
    val methods = remember { mutableStateListOf(*GIVE_METHODS.toTypedArray()) }
    val options = remember(methodsRes) { giveMethodOptions(methodsRes) }
    var pickedMethod by remember { mutableStateOf<String?>(null) }
    // Null while unbound; a bound preset with no currency is shillings.
    val boundCurrency = target?.let { currencyCode(it.currency) }
    val method = effectiveGiveMethod(pickedMethod, methodsRes, options, boundCurrency = boundCurrency)
    // PayPal settles in US dollars: the amount entry speaks dollars with cents.
    val inDollars = method?.inDollars == true
    var coverFee by remember { mutableStateOf(false) }
    if (customOpen) {
        CustomAmountDialog(
            initial = amountMajor,
            initialName = accountName.ifBlank { AppPrefs.lastGivingAccountName },
            dollars = if (inDollars) DollarEntry(usdCents, method?.minMinor ?: 100, method?.maxMinor ?: 0) else null,
            onConfirm = { amt, name ->
                // In dollars the amount is cents; in shillings, whole KSh.
                if (inDollars) usdCents = amt else amountMajor = amt
                accountName = name.orEmpty()
                if (!name.isNullOrBlank()) AppPrefs.lastGivingAccountName = name
                customOpen = false
            },
            onDismiss = { customOpen = false },
        )
    }
    // The number the prompt goes to: the member's pick on the number sheet,
    // else the last number this device prompted, else the profile's.
    var chosenPhone by remember { mutableStateOf<String?>(null) }
    val promptPhone = chosenPhone ?: promptPhonePrefill(AppPrefs.lastGivingPhone, phoneOnFile)
    var phoneSheet by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<GivingIntentResult?>(null) }
    // What the ceremony's gift charged (fee inside), its name and the number
    // it prompted — the sent request's, or the waiting prompt's when the
    // ceremony follows one (GIFT_IN_PROGRESS), whose number is not known.
    var resultAmountMinor by remember { mutableIntStateOf(0) }
    var resultCurrency by remember { mutableStateOf(GIVE_FORM_CURRENCY) }
    var resultGiftName by remember { mutableStateOf<String?>(null) }
    var resultPhone by remember { mutableStateOf<String?>(null) }
    // The server's words when the ceremony follows a prompt already waiting.
    var ceremonyNote by remember { mutableStateOf<String?>(null) }
    // Why the ceremony's gift failed, when the server already said (a gift
    // opened from its giving_gift_failed push, or a waiting prompt's detail).
    var resultFailure by remember { mutableStateOf<GiftFailure?>(null) }
    // Try again (Giving Cycle 3): in flight, what the server refused it with,
    // and its key — held only while a retry got no answer (retryKeyFor).
    var retrying by remember { mutableStateOf(false) }
    var retryError by remember { mutableStateOf<String?>(null) }
    var heldRetry by remember { mutableStateOf<HeldRetryKey?>(null) }
    // The server refused the last Try again itself (a 429 RATE_LIMITED among
    // them): the next tap goes back to the form, with its words (iOS parity).
    var retryToForm by remember { mutableStateOf(false) }
    var confirmSchedule by remember { mutableStateOf<ScheduleToConfirm?>(null) }
    var scheduled by remember { mutableStateOf<CreatedSchedule?>(null) }
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

    /** "Give again" (iOS applyRepeat): the gift back on the form — its fund,
     *  its rail when that can take money now, its amount in that rail's money
     *  and its name (GiveHistoryLogic.repeatPlan). */
    fun applyRepeat(g: GivingRecord) {
        val plan = repeatPlan(g, options, method)
        plan.fundId?.let { fundId = it }
        plan.methodKey?.let { key ->
            pickedMethod = key
            // A rail that cannot carry a schedule turns the gift back to one-time.
            if (options.firstOrNull { it.key == key }?.recurring != true && freq != FREQ_ONCE) freq = FREQ_ONCE
        }
        plan.usdCents?.let { usdCents = it }
        plan.amountMajor?.let { amountMajor = it }
        plan.coverFee?.let { coverFee = it }
        accountName = plan.accountName
        error = null
    }

    /** A gift went through to the server: the next one on this device starts
     *  on the number it prompted (cleared at sign-out). */
    fun rememberPromptPhone(p: String?) {
        kenyanMobileE164(p)?.let { AppPrefs.lastGivingPhone = it }
    }

    /** Put a gift the server holds on the ceremony — a prompt already waiting
     *  (409 GIFT_IN_PROGRESS, with the server's words in [note]) or a gift a
     *  push opened — exactly as the server holds it: its own amount, currency,
     *  fund, pledge and failure, never the form's. It spends [boundTo]'s
     *  binding only when it is for that same pledge or need. */
    fun showTransaction(d: GivingDetail, note: String?, boundTo: GivePreset?) {
        val forThis = inflightIsForTarget(boundTo, d)
        ceremonyBoundTo = boundTo.takeIf { forThis }
        if (forThis && giftSpendsBinding(boundTo, d.status)) {
            ceremonySpentBinding = true
            onUnbind()
        }
        resultAmountMinor = d.amountMinor
        resultCurrency = d.currency
        resultGiftName = d.accountName?.trim()?.ifBlank { null }
        resultPhone = null
        resultFailure = d.failure
        ceremonyNote = note
        retryError = null
        retryToForm = false
        result = intentResultFromDetail(d)
    }

    /** The ceremony resolved: back to the form, the next Pay tap minting a
     *  fresh key — reset to an ordinary gift when the ceremony spent a
     *  binding (the double-pay guard). */
    fun closeCeremony() {
        result = null
        heldKey = null
        ceremonyNote = null
        resultFailure = null
        retryError = null
        retryToForm = false
        if (ceremonySpentBinding) { ceremonySpentBinding = false; resetToOrdinaryGift() }
        GivingEvents.emit()
    }

    /**
     * Try again on the failed gift on the ceremony (Giving Cycle 3): POST
     * /giving/transactions/{id}/retry — the server carries everything the
     * failed gift did (fund, amount, pledge or need, name, fee cover), so a
     * retry never loses its pledge — then the SAME ceremony follows the new
     * transaction. A prompt still waiting is followed instead (409
     * GIFT_IN_PROGRESS); any other refusal is said under the button.
     */
    fun retry(failed: GivingIntentResult) {
        if (retrying) return
        val phone = retryPhoneFor(failed.provider, promptPhone)
        // The key: GiveSubmitLogic.newGivingKey's, replayed only after no answer.
        val attempt = retryKeyFor(heldRetry, failed.transactionId, phone)
        val boundTo = ceremonyBoundTo
        heldRetry = attempt
        retrying = true; retryError = null
        scope.launch {
            var failure: Throwable? = null
            try {
                val r = Net.client.api.retryGift(failed.transactionId, RetryGiftBody(attempt.key, phone))
                rememberPromptPhone(phone)
                // The retry is bound where the failed gift was: spend the
                // binding again, as a fresh intent's answer does.
                if (!ceremonySpentBinding && giftSpendsBinding(boundTo, r.status)) {
                    ceremonySpentBinding = true
                    onUnbind()
                }
                resultPhone = phone
                resultFailure = null
                ceremonyNote = null
                retryToForm = false
                result = r
                if (givingIntentAnnounces(r.status)) GivingEvents.emit()
            } catch (e: Exception) {
                failure = e
                // The refusal is read ONCE — an error body is a one-shot stream.
                val refusal = ApiException.serverError(e)
                when (val next = giveErrorAction(refusal, fallback = refusal?.displayMessage ?: ApiException.message(e))) {
                    is GiveErrorAction.FollowPrompt -> {
                        val waiting = runCatching { Net.client.api.givingDetail(next.transactionId) }.getOrNull()
                        if (waiting == null) retryError = next.message else showTransaction(waiting, next.message, boundTo)
                    }
                    is GiveErrorAction.Say -> {
                        retryError = next.message
                        // Refused for the gift itself (a 429 among them): the
                        // next tap is the form's, where the number can change.
                        // No answer, or only the key refused: this gift again.
                        retryToForm = !retryStaysOnGift(e, refusal)
                    }
                }
            } finally {
                // No server answer: hold the key so the same retry replays it.
                // Any answer spends it (Giving Cycle 6): after a 409 CONFLICT
                // the next Try again is this same failed gift with a fresh
                // key; a 429 RATE_LIMITED is said above, never resent.
                if (heldRetryAfter(attempt, failure) == null) heldRetry = null
                retrying = false
            }
        }
    }

    // A recurring gift set up from a pledge's pace (Giving Cycle 9): the same
    // result as the form's give-now — today's prompt watched like any gift,
    // or "Scheduled" with why today's could not go out. Shown once.
    LaunchedEffect(startedSchedule) {
        val st = startedSchedule ?: return@LaunchedEffect
        val first = st.created.firstCharge
        if (first != null) {
            resultAmountMinor = st.body.amountMinor
            resultCurrency = st.body.currency
            resultGiftName = null
            resultPhone = null // the profile's number: every cycle prompts it
            resultFailure = null
            retryError = null
            retryToForm = false
            ceremonyBoundTo = null
            ceremonyNote = firstChargeNote(freqOf(st.body.frequency))
            result = first
        } else {
            scheduled = CreatedSchedule(st.created, st.body, phone = null)
        }
        onStartedShown()
    }

    // A giving_gift_failed push (Giving Cycle 3) opens the Give tab on that
    // gift: its result, with the reason, the hint and Try again. Read once —
    // marked opened only AFTER the read, since marking it changes this key
    // and would cancel a read still in flight.
    LaunchedEffect(followTransactionId) {
        val id = followTransactionId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        runCatching { Net.client.api.givingDetail(id) }
            .onSuccess { d -> showTransaction(d, note = null, boundTo = null) }
            .onFailure { error = ApiException.message(it) }
        onFollowed()
    }

    // Full-screen generosity ceremony once an intent is created. The copy
    // reads the RESULT (GiveCeremonyCopy.kt): the server names the fund it
    // routed the gift to and the pledge it counts toward; the chip label is
    // only the fallback for a result that carries neither.
    result?.let { r ->
        GiveResult(
            // A bound gift's fund is the server's to name (pays_to); the
            // chooser's tile was never shown, so it is never the fallback.
            r, amountMinor = resultAmountMinor, currency = resultCurrency,
            chipFundLabel = if (target != null) target?.paysTo?.name?.takeIf { it.isNotBlank() } else giveFund(fundId).name,
            giftName = resultGiftName,
            promptPhone = resultPhone,
            note = ceremonyNote,
            initialFailure = resultFailure,
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
            onDone = { closeCeremony() },
            // Try again (a failed gift, Giving Cycle 3): the server retries
            // it with everything it carried, and this ceremony follows the
            // new gift. A card gift has no Try again here.
            canRetry = canRetryGift(r.provider),
            retrying = retrying,
            retryError = retryError,
            // After a refusal of the gift itself (a 429 RATE_LIMITED among
            // them) Try again goes back to the form with the server's words —
            // the member can change the number there; nothing is resent.
            onRetry = {
                if (retryToForm) {
                    val why = retryError
                    closeCeremony()
                    error = why
                } else {
                    retry(r)
                }
            },
        )
        return
    }
    // …or once the server really created a schedule (never faked here).
    scheduled?.let { c ->
        ScheduledResult(
            c.created, c.body, c.phone,
            // Back to a One-time form: the next tap is never a second
            // schedule the member did not pick again.
            onDone = { scheduled = null; freq = FREQ_ONCE; reload() },
        )
        return
    }

    // This Nairobi year's settled giving, per currency — the statement's rule.
    val thisYear = LocalDate.now(java.time.ZoneId.of("Africa/Nairobi")).year
    // The one settled rule (succeeded / settled / completed), as iOS GiveMoney.
    val yearTotals = currencySums(history.filter { giftSettled(it.status) && givingYear(it) == thisYear })

    // Running or paused — a paused schedule still stands until it is cancelled.
    val liveSchedules = schedules.filter { scheduleCancellable(it.status) }

    /** Send a planned request: an intent, or a schedule the member confirmed. */
    fun send(plan: GiveSubmission, attempt: HeldGiveKey) {
        // One request at a time: a second tap inside the same frame (before
        // the disabled button recomposes) must not start a second payment.
        if (busy) return
        // What this request is bound to, and the number it prompts, as sent
        // — not whatever the form holds when the answer lands.
        val boundTo = target
        val phoneAtSend = promptPhone
        heldKey = attempt
        busy = true; error = null
        scope.launch {
            var failure: Throwable? = null
            try {
                when (plan) {
                    is GiveSubmission.Intent -> {
                        // A replay (`reused: true`) is read exactly like a
                        // fresh answer — the ceremony watches either.
                        val r = Net.client.api.giving(plan.body)
                        rememberPromptPhone(plan.body.phoneNumber)
                        ceremonyBoundTo = boundTo
                        // Spent at once upstream (before the ceremony shows),
                        // so every way off this screen forgets the binding.
                        if (giftSpendsBinding(boundTo, r.status)) {
                            ceremonySpentBinding = true
                            onUnbind()
                        }
                        resultAmountMinor = plan.body.amountMinor
                        resultCurrency = plan.body.currency
                        resultGiftName = plan.body.accountName
                        resultPhone = plan.body.phoneNumber
                        resultFailure = null
                        retryError = null
                        ceremonyNote = null
                        result = r
                        // Succeeded, or pending on a PIN / card / PayPal:
                        // Partners, its statement and the year pill refetch.
                        if (givingIntentAnnounces(r.status)) GivingEvents.emit()
                    }
                    is GiveSubmission.Schedule -> {
                        val created = Net.client.api.createSchedule(plan.body)
                        rememberPromptPhone(phoneAtSend)
                        // The form goes back to One-time: the next tap is never
                        // a second schedule the member did not pick again.
                        freq = FREQ_ONCE
                        val first = created.firstCharge
                        if (first != null) {
                            // "Start with a gift now" (Giving Cycle 4): today's
                            // prompt went out — watch it like any gift.
                            resultAmountMinor = plan.body.amountMinor
                            resultCurrency = plan.body.currency
                            resultGiftName = null
                            resultPhone = phoneAtSend
                            resultFailure = null
                            retryError = null
                            ceremonyBoundTo = null
                            ceremonyNote = firstChargeNote(freqOf(plan.body.frequency))
                            result = first
                        } else {
                            // Set up for next time — or today's prompt could
                            // not go out (first_charge_error), the gift stands.
                            scheduled = CreatedSchedule(created, plan.body, phoneAtSend)
                        }
                        // A schedule is the partnership's rhythm — the standing changes.
                        GivingEvents.emit()
                    }
                    is GiveSubmission.Blocked -> error = plan.message
                }
            } catch (e: Exception) {
                failure = e
                // The refusal is read ONCE — an error body is a one-shot stream.
                val refusal = ApiException.serverError(e)
                when (val next = giveErrorAction(refusal, fallback = refusal?.displayMessage ?: ApiException.message(e))) {
                    is GiveErrorAction.FollowPrompt -> {
                        // 409 GIFT_IN_PROGRESS: a prompt from a moment ago is
                        // still on the phone, and a second would fail as busy.
                        // Follow THAT transaction as the server holds it (it
                        // may be another gift) with the same watch; it spends
                        // this form's binding only when it is for the same
                        // pledge or need.
                        val waiting = runCatching { Net.client.api.givingDetail(next.transactionId) }.getOrNull()
                        if (waiting == null) error = next.message else showTransaction(waiting, next.message, boundTo)
                    }
                    // The 422s and SCHEDULE_EXISTS: the server's own words.
                    is GiveErrorAction.Say -> error = next.message
                }
            } finally {
                // No server answer (transport failure / timeout): hold the key
                // so an identical retry replays it. Any answer releases it — a
                // 409 CONFLICT (the key is another gift's) then goes through on
                // the next tap, and a 429 RATE_LIMITED is said, never resent
                // (Giving Cycle 6).
                if (heldKeyAfter(attempt, failure) == null) heldKey = null
                busy = false
            }
        }
    }

    fun submit() {
        if (busy) return
        // One-time → intent; Weekly/Monthly → schedule (where the server says
        // the method can run one), confirmed first; cover-fee inside
        // amount_minor; pledge_id carried. GiveSubmitLogic.kt.
        // A bound gift is one-time whatever the (hidden) control last held.
        val sendFreq = if (target != null) FREQ_ONCE else freq
        // The key: replayed only when the last attempt at this EXACT gift got
        // no server answer; otherwise fresh.
        val attempt = giveKeyFor(
            heldKey,
            GiveRequestShape(
                amountMajor = amountMajor, fundId = fundId, methodId = method?.key.orEmpty(),
                pledgeId = target?.pledgeId, needId = target?.needId, freq = sendFreq,
                giftName = accountName, coverFee = coverFee && !inDollars, phone = promptPhone.orEmpty(),
                usdCents = if (inDollars) usdCents else 0,
            ),
        )
        // The method's currency picks the amount: dollars (with cents) for
        // PayPal, shillings otherwise — never a KSh number with PayPal.
        val plan = planGiveSubmission(
            freq = sendFreq, method = method, fundId = fundId, amountMajor = amountMajor,
            coverFee = coverFee && !inDollars, phone = promptPhone, accountName = accountName,
            idempotencyKey = attempt.key, pledgeId = target?.pledgeId,
            needId = target?.needId, phoneOnFile = phoneOnFile, usdCents = usdCents,
            boundCurrency = boundCurrency,
        )
        when (plan) {
            is GiveSubmission.Blocked -> {
                // Bound in a currency no rail can take here (a USD pledge
                // while PayPal is off): say which, not "unavailable".
                error = if (plan.message == NO_METHOD_MESSAGE && boundCurrency != null) {
                    noRailForCurrencyMessage(boundCurrency, options) ?: plan.message
                } else {
                    plan.message
                }
                // No number to prompt (or not one M-Pesa can reach): ask for it here.
                if (method != null && plan.message in setOf(phoneNeededMessage(method.label), PHONE_INVALID_MESSAGE)) {
                    phoneSheet = true
                }
            }
            // Nothing is sent until the member confirms what a schedule means.
            is GiveSubmission.Schedule -> {
                error = null
                confirmSchedule = ScheduleToConfirm(plan, attempt, promptPhone)
            }
            is GiveSubmission.Intent -> send(plan, attempt)
        }
    }

    Box(Modifier.fillMaxSize().background(GIVE.paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ── Header — the one cream band (segment control · title · year pill) ──
            GiveHeaderBand(segmentControl, yearTotals = yearTotals, onOpenStatement = onOpenStatement)

            // ── Body ──
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Pledge-pay / need mode: ONE card says where the gift goes,
                // in place of the chooser (the server picks a pledge's fund).
                val targetCopy = giveTargetCopyFor(target, if (inDollars) usd(usdCents) else kshMajor(chargedAmountMajor(amountMajor, coverFee)))
                if (targetCopy != null) {
                    GiveTargetCard(targetCopy) { Haptics.tick(view); target = null; onUnbind() }
                } else {
                    // Repeat last gift (iOS): the newest ordinary gift that
                    // went through — never a failed one, a pledge's or a need's.
                    lastRepeatableGift(history)?.let { g -> RepeatGiftCard(g) { Haptics.tap(view); applyRepeat(g) } }
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
                        // PayPal speaks dollars with cents; everything else whole shillings.
                        Text(if (inDollars) "US$" else "KSh", style = giInter(14, FontWeight.Medium), color = GIVE.tertiary)
                        val shownAmount = if (inDollars) usd(usdCents).removePrefix("US$ ") else "%,d".format(amountMajor)
                        // Steps down for long amounts (iOS shrinks to fit), never mid-number.
                        Text(
                            shownAmount,
                            style = giSerif(amountDisplaySize(shownAmount), FontWeight.SemiBold, -1.2f), color = GIVE.navy,
                        )
                    }
                    Text(
                        targetCopy?.amountSubtitle ?: "${giveFund(fundId).name} · ${freqLabel(freq)}",
                        style = giInter(11), color = GIVE.sub, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    )
                    if (inDollars) {
                        Text(
                            "PayPal gifts are in US dollars",
                            style = giInter(11, FontWeight.SemiBold), color = GIVE.eyebrow, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Row(
                        Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Suggested amounts: shillings, or US$ 5 · 10 · 25 · 50 · 100.
                        (if (inDollars) USD_PRESETS else GIVE_PRESETS).forEach { p ->
                            val on = if (inDollars) usdCents == p * 100 else amountMajor == p
                            Text(
                                if (inDollars) "US$ $p" else "%,d".format(p),
                                style = giInter(13, FontWeight.SemiBold),
                                color = if (on) Color.White else GIVE.navy,
                                modifier = Modifier.clip(Capsule)
                                    .background(if (on) GIVE.navy else GIVE.surface)
                                    .then(if (on) Modifier else Modifier.border(1.dp, GIVE.border, Capsule))
                                    .clickable { if (inDollars) usdCents = p * 100 else amountMajor = p }
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

                // Your rhythm (docs/PARTNERS_PROGRAMME.md §3a, Giving Cycle 4):
                // the soonest running schedule, one tap from its sheet —
                // "KSh 500 every Sunday · next Sun 5 Oct".
                if (targetCopy == null) {
                    rhythmSchedule(schedules)?.let { rs ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GIVE.white)
                                .border(1.dp, GIVE.gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable { sheetSchedule = rs }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("YOUR RHYTHM", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                                Text(rhythmText(rs), style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, modifier = Modifier.padding(top = 2.dp))
                                // Collecting a pledge: which, and what the next prompt asks.
                                listOfNotNull(schedulePledgeLine(rs), scheduleNextAmountLine(rs)).forEach {
                                    Text(it, style = giInter(11, FontWeight.Medium), color = GIVE.goldChipText, modifier = Modifier.padding(top = 2.dp))
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GIVE.ink300, modifier = Modifier.size(14.dp))
                        }
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

                // Recurring summary — what a schedule will do, before it
                // exists; a method that cannot recur (PayPal) says so instead.
                if (freq != FREQ_ONCE && targetCopy == null && method?.recurring == false) {
                    Text(RECURRING_BLOCKED_MESSAGE, style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(horizontal = 4.dp))
                } else if (freq != FREQ_ONCE && targetCopy == null) {
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
                            Text("${kshMajor(chargedAmountMajor(amountMajor, coverFee))} every ${cadenceWord(freq)}", style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
                            Text(
                                "Nothing is taken today · first prompt ${firstPromptDay(freq)}, then every ${cadenceWord(freq)}. Cancel anytime.",
                                style = giInter(11), color = GIVE.sub,
                            )
                        }
                    }
                }

                // Pay methods — the server's word on which can take a gift.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CHOOSE HOW TO PAY", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = GIVE.tertiary, modifier = Modifier.size(11.dp))
                        Text("Reorder", style = giInter(11), color = GIVE.tertiary)
                    }
                }
                // Bound in a currency no rail can take here: say so up front.
                boundCurrency?.let { noRailForCurrencyMessage(it, options) }?.let {
                    Text(it, style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(horizontal = 4.dp))
                }
                // Only the rails the server lists; bound, only those in the
                // pledge's or need's currency (GiveMethodsLogic.shownGiveMethods).
                val shownMethods = shownGiveMethods(methods, options, boundCurrency)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shownMethods.forEachIndexed { i, m ->
                        // Not selectable here (switched off, a card) → the
                        // SOON treatment, never a tap.
                        val option = options.firstOrNull { it.key == m.id }
                        val soon = option?.selectableFor(boundCurrency) != true
                        val on = m.id == method?.key && !soon
                        /** Swap this row with a neighbour among the shown rows, in the member's order. */
                        fun swapWith(other: GiveMethod) {
                            val a = methods.indexOf(m); val b = methods.indexOf(other)
                            if (a >= 0 && b >= 0) { methods[a] = other; methods[b] = m }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (on) GIVE.priorityBg else GIVE.white)
                                .border(if (on) 1.5.dp else 1.dp, if (on) GIVE.gold else GIVE.border, RoundedCornerShape(18.dp))
                                .then(if (soon) Modifier.alpha(0.7f) else Modifier.clickable { pickedMethod = m.id })
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
                                // The number itself has its own card below.
                                if (on) Text(m.sub, style = giInter(11), color = GIVE.sub)
                            }
                            if (soon) {
                                Box(Modifier.clip(Capsule).background(GIVE.goldChipBg).padding(horizontal = 9.dp, vertical = 4.dp)) {
                                    Text(methodChipLabel(option), style = giInter(10, FontWeight.Bold, 0.5f), color = GIVE.goldChipText)
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
                                        if (i > 0) swapWith(shownMethods[i - 1])
                                    },
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", tint = GIVE.ink300,
                                    modifier = Modifier.size(14.dp).clickable {
                                        if (i < shownMethods.lastIndex) swapWith(shownMethods[i + 1])
                                    },
                                )
                            }
                            Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = Color(0xFFC4C9D0), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // The number the prompt goes to — on the screen, with a way
                // to change it (the iOS MobileMoneySheet's job). No number
                // yet: Add, and the gift waits for one.
                if (method?.needsPhone == true) {
                    val tint = GIVE_METHODS.firstOrNull { it.id == method.key }?.badgeBg ?: GIVE.gold
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GIVE.white)
                            .border(1.dp, if (promptPhone == null) GIVE.danger.copy(alpha = 0.4f) else GIVE.border, RoundedCornerShape(18.dp))
                            .clickable { phoneSheet = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Smartphone, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${method.label.uppercase()} PROMPT GOES TO", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                            Text(
                                promptPhone?.let { kenyanMobileDisplay(it) } ?: "Add the number to prompt",
                                style = giInter(14, FontWeight.SemiBold),
                                color = if (promptPhone == null) GIVE.danger else GIVE.navy,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text(if (promptPhone == null) "Add" else "Change", style = giInter(13, FontWeight.Bold), color = GIVE.gold)
                    }
                }

                // Cover-fee row — the M-Pesa fee, in shillings; not for PayPal's dollars.
                if (!inDollars) Row(
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

                // Recurring gifts rail — running or paused (a paused one still
                // stands until it is resumed or cancelled).
                if (liveSchedules.isNotEmpty()) {
                    Text("RECURRING GIFTS", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    val today = nairobiToday(Instant.now())
                    // Two cards to the width, as on iOS; more scroll sideways.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cardWidth = (maxWidth - 10.dp) / 2
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            liveSchedules.forEach { s ->
                                val status = scheduleStatusLabel(s.status)
                                Column(
                                    Modifier.width(cardWidth).clip(RoundedCornerShape(16.dp)).background(GIVE.white)
                                        .border(1.dp, GIVE.border, RoundedCornerShape(16.dp))
                                        .clickable { sheetSchedule = s }
                                        .padding(12.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(12.dp))
                                        Text(s.frequency.uppercase(), style = giInter(11, FontWeight.Bold, 1.4f), color = GIVE.overline)
                                    }
                                    Text(ksh(s.amountMinor), style = giInter(15, FontWeight.Bold, -0.15f), color = GIVE.navy, modifier = Modifier.padding(top = 4.dp))
                                    Text(giveFund(s.fund).name, style = giInter(13), color = GIVE.sub)
                                    // The pledge it collects, in gold (iOS): "Collects your pledge “Kenya trip”".
                                    schedulePledgeLine(s)?.let {
                                        Text(
                                            it, style = giInter(10, FontWeight.SemiBold), color = GIVE.eyebrow,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    if (status != null) {
                                        Text(status, style = giInter(11, FontWeight.SemiBold), color = GIVE.danger, modifier = Modifier.padding(top = 4.dp))
                                    } else {
                                        // "Next 5 Oct" — the year only when it isn't this one.
                                        Text(scheduleCardNextLine(s.nextRunAt, today), style = giInter(11), color = GIVE.tertiary, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // No separate "Manage schedules" row (iOS parity): each card
                // above opens its gift's sheet — change, pause, resume, cancel.

                // RECENT GIVING (iOS): the three newest gifts that went
                // through, each opening its receipt; the statement one tap away.
                RecentGivingCard(recentGifts(history), onOpenStatement, onOpenReceipt)
                // 2 Corinthians 9:7, then the footer's promise — naming only
                // the rails that can take money here.
                ScriptureStrip()
                SecureNote(giveSecureNote(options))
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
                val boundCta = giveTargetCopyFor(target, if (inDollars) usd(usdCents) else kshMajor(chargedAmountMajor(amountMajor, coverFee)))?.cta
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
                } else if (freq != FREQ_ONCE && !inDollars) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Schedule ${kshMajor(chargedAmountMajor(amountMajor, coverFee))} / ${cadenceWord(freq)}", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
                } else {
                    Text(
                        "Give ${if (inDollars) usd(usdCents) else kshMajor(chargedAmountMajor(amountMajor, coverFee))}",
                        style = giInter(14, FontWeight.Bold), color = GIVE.navy,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
                }
            }
        }

        // ── Confirm a schedule before it exists (Giving Cycles 1 and 4):
        // "Start with a gift now" (on by default) sends today's prompt as the
        // first cycle; off, nothing is taken today ──
        confirmSchedule?.let { c ->
            val cFreq = freqOf(c.plan.body.frequency)
            val now = remember(c) { Instant.now() }
            var giveNow by remember(c) { mutableStateOf(true) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmSchedule = null },
                containerColor = GIVE.white,
                title = { Text(scheduleConfirmTitle(cFreq), style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy) },
                text = {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GIVE.priorityBg)
                                .border(1.dp, GIVE.gold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Start with a gift now", style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
                                Text(giveNowLine(c.plan.body.amountMinor, cFreq, now), style = giInter(11), color = GIVE.sub)
                            }
                            Switch(
                                checked = giveNow,
                                onCheckedChange = { giveNow = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = GIVE.gold, checkedThumbColor = Color.White),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            scheduleConfirmText(
                                amountMinor = c.plan.body.amountMinor, freq = cFreq,
                                fundName = giveFund(c.plan.body.fund).name, phone = c.phone,
                                firstPromptDay = nairobiDay(firstPromptAt(now, cFreq)),
                                giveNow = giveNow, now = now,
                            ),
                            style = giInter(14), color = GIVE.sub,
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmSchedule = null
                        send(GiveSubmission.Schedule(c.plan.body.copy(firstCharge = firstChargeWire(giveNow))), c.attempt)
                    }) {
                        Text("Confirm", style = giInter(14, FontWeight.Bold), color = GIVE.gold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmSchedule = null }) {
                        Text("Back", style = giInter(14, FontWeight.SemiBold), color = GIVE.sub)
                    }
                },
            )
        }

        // ── The number sheet ──
        if (phoneSheet && method != null) {
            PromptNumberSheet(
                methodLabel = method.label,
                initial = promptPhone,
                phoneOnFile = phoneOnFile,
                onUse = { n ->
                    chosenPhone = n
                    phoneSheet = false
                    // The number was the problem; it is fixed now.
                    if (error == PHONE_INVALID_MESSAGE || error == phoneNeededMessage(method.label)) error = null
                },
                onDismiss = { phoneSheet = false },
            )
        }

        // ── Recurring-gift sheet (ScheduleSheet.kt): change, pause, resume,
        // heads-up, cancel — Giving Cycle 4 ──
        sheetSchedule?.let { opened ->
            val mpesa = options.firstOrNull { it.key == "mpesa" } ?: FALLBACK_MPESA
            // The latest row for this gift: a reload (a retried charge that
            // cleared its strikes, a change) reaches the open sheet.
            val s = schedules.firstOrNull { it.scheduleId == opened.scheduleId } ?: opened
            ScheduleSheet(
                s,
                onClose = { sheetSchedule = null },
                // Partners' standing derives from schedules; the rail refetches.
                onChanged = { GivingEvents.emit(); reload() },
                onCancelled = {
                    sheetSchedule = null
                    GivingEvents.emit()
                    reload()
                },
                minMinor = mpesa.minMinor,
                maxMinor = mpesa.maxMinor,
                pledges = pledges,
                onOpenPledge = { id -> sheetSchedule = null; onOpenPledge(id) },
            )
        }
    }
}

/** The number sheet (iOS MobileMoneySheet): the number the prompt goes to,
 *  typed any way a Kenyan writes it and checked by the server's own rule,
 *  with the profile's number one tap away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptNumberSheet(
    methodLabel: String,
    initial: String?,
    phoneOnFile: String?,
    onUse: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.let { kenyanMobileDisplay(it) }.orEmpty()) }
    val number = kenyanMobileE164(text)
    val onFile = kenyanMobileE164(phoneOnFile)
    // Called wrong only once a whole number's worth is typed, not mid-way.
    val wrong = number == null && text.count { it.isDigit() } >= 9
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$methodLabel number", style = giSerif(18, FontWeight.SemiBold, -0.36f), color = GIVE.navy)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(GIVE.surface).clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = GIVE.navy, modifier = Modifier.size(15.dp))
                }
            }
            Text(
                "We'll send the payment prompt to this number, and start your next gift on it.",
                style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 8.dp),
            )
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(18) },
                singleLine = true,
                placeholder = { Text("07XX XXX XXX", style = giInter(15), color = GIVE.tertiary) },
                leadingIcon = { Icon(Icons.Filled.Smartphone, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(18.dp)) },
                isError = wrong,
                textStyle = giInter(15, FontWeight.SemiBold).copy(color = GIVE.navy),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (wrong) {
                Text(PHONE_INVALID_MESSAGE, style = giInter(12), color = GIVE.danger, modifier = Modifier.padding(top = 6.dp))
            }
            if (onFile != null && onFile != number) {
                Text(
                    "Use my number on file (${kenyanMobileDisplay(onFile)})",
                    style = giInter(12, FontWeight.SemiBold), color = GIVE.goldLo,
                    modifier = Modifier.padding(top = 10.dp).clip(Capsule)
                        .clickable { text = kenyanMobileDisplay(onFile) }
                        .padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp)
                    .alpha(if (number != null) 1f else 0.4f)
                    .clip(RoundedCornerShape(16.dp)).background(GIVE.gold)
                    .clickable(enabled = number != null) { number?.let(onUse) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Use this number", style = giInter(14, FontWeight.Bold), color = GIVE.navy)
            }
            Text(
                "Used only to send the payment prompt.",
                style = giInter(11), color = GIVE.tertiary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}
/** Full-screen ceremony once the server really created a schedule — the first
 *  prompt is the server's next cycle boundary (`next_run_at`), shown as such;
 *  "Cancel anytime" is true because its card under RECURRING GIFTS opens it. The
 *  amount, cadence and fund are what the member confirmed ([body]): the
 *  server's answer carries only the id, status and first run. When the member
 *  asked to start with a gift now and today's prompt could not go out, the
 *  server's reason comes first, then that the gift is set up (Giving Cycle 4). */
@Composable
private fun ScheduledResult(created: CreatedScheduleRes, body: CreateScheduleBody, phone: String?, onDone: () -> Unit) {
    LaunchedEffect(created.scheduleId) {
        CelebrationCenter.fire(Moment("schedule-${created.scheduleId}", "Thank you for committing", scheduleCelebrationLine(freqOf(body.frequency))))
    }
    val freq = freqOf(body.frequency)
    val firstPrompt = created.nextRunAt.takeIf { it.isNotBlank() }?.let { prettyDate(it) } ?: firstPromptDay(freq)
    val todayFailed = created.firstChargeError?.takeIf { it.isNotBlank() }
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
                "${ksh(body.amountMinor)} every ${cadenceWord(freq)} to ${giveFund(body.fund).name}.",
                style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, textAlign = TextAlign.Center,
            )
            phone?.let {
                Spacer(Modifier.height(4.dp))
                Text("Prompts go to ${kenyanMobileDisplay(it)}", style = giInter(12, FontWeight.SemiBold), color = GIVE.eyebrow, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(4.dp))
            if (todayFailed != null) {
                // Today's prompt could not go out; the gift itself stands.
                Text(todayFailed, style = giInter(13), color = GIVE.danger, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(scheduledSetUpLine(freq, firstPrompt), style = giInter(13), color = GIVE.sub, textAlign = TextAlign.Center)
            } else {
                Text(
                    scheduledFirstPromptLine(freq, firstPrompt),
                    style = giInter(13), color = GIVE.sub, textAlign = TextAlign.Center,
                )
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
    /** The gift's currency — a PayPal gift is in US dollars. */
    currency: String = GIVE_FORM_CURRENCY,
    chipFundLabel: String? = null,
    giftName: String? = null,
    /** The number the prompt went to (E.164), when this gift sent one. */
    promptPhone: String? = null,
    /** The server's words when this follows a prompt already waiting
     *  (409 GIFT_IN_PROGRESS) rather than one this tap sent. */
    note: String? = null,
    /** Why it failed, when already known (a gift opened from its push). */
    initialFailure: GiftFailure? = null,
    /** The gift reached its final outcome while on screen. */
    onOutcome: (GiftOutcome) -> Unit = {},
    onDone: () -> Unit,
    /** A failed gift offers Try again (Giving Cycle 3) — not a card gift. */
    canRetry: Boolean = true,
    /** Try again is in flight, and what the server refused it with. */
    retrying: Boolean = false,
    retryError: String? = null,
    /** A failed gift's Try again: the server retries it (POST …/retry). */
    onRetry: () -> Unit = onDone,
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
    // Why it failed, as the watch read it off the transaction (Giving Cycle 1)
    // — or as the server already said when the gift was opened from its push.
    var failure by remember(r.transactionId) { mutableStateOf(initialFailure) }
    // Bumped to read the gift back after a PayPal capture settles it.
    var watchRun by remember(r.transactionId) { mutableIntStateOf(0) }
    // The member went to PayPal from here — coming back captures on its own.
    var openedPayPal by remember(r.transactionId) { mutableStateOf(false) }
    val outcome = giftOutcome(status)

    /** Capture the approved PayPal order. Its own answer is the status —
     *  "succeeded" confirms; "processing" means PayPal has not released it
     *  (not approved yet). [quiet] (the automatic capture on return) says
     *  nothing on "not yet" or a failed call — the button below stays. */
    fun capture(quiet: Boolean) {
        val orderId = r.providerRef?.takeIf { it.isNotBlank() } ?: return
        if (capturing) return
        capturing = true
        if (!quiet) captureError = null
        scope.launch {
            runCatching { Net.client.api.capturePayPal(PayPalCaptureBody(orderId)) }
                .onSuccess { res ->
                    if (res.status.isNotBlank()) status = res.status
                    if (giftOutcome(res.status) == GiftOutcome.Processing) {
                        if (!quiet) captureError = "PayPal hasn't confirmed it yet — finish approving there, then try again."
                    } else {
                        captureError = null
                        watchRun++ // settled either way: read it back (the reason, if it failed)
                    }
                }
                .onFailure { if (!quiet) captureError = org.nuruplace.member.data.net.ApiException.message(it) }
            capturing = false
        }
    }
    // PayPal finishes on its own (Giving Cycle 2, iOS parity): back from
    // approving — the app resumes — the order is captured without a second tap.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (openedPayPal && canReopenPayPal(r, status)) capture(quiet = true)
    }
    // The watch (iOS parity): GET /giving/transactions/{id} every 3 s, at most
    // 20 times, while the gift is processing. Ends with the ceremony.
    LaunchedEffect(r.transactionId, watchRun) {
        if (r.transactionId.isBlank()) return@LaunchedEffect
        watchLapsed = false
        var readings = 0
        while (keepWatchingGift(giftOutcome(status), readings)) {
            delay(CEREMONY_WATCH_INTERVAL_MS)
            readings++
            runCatching { Net.client.api.givingDetail(r.transactionId) }.getOrNull()?.let {
                status = it.status
                failure = it.failure
            }
        }
        if (giftOutcome(status) == GiftOutcome.Processing) watchLapsed = true
        // Failed on its very first answer, so never read back: one reading for WHY.
        if (giftOutcome(status) == GiftOutcome.Failed && failure == null) {
            runCatching { Net.client.api.givingDetail(r.transactionId) }.getOrNull()?.let { failure = it.failure }
        }
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
            // Following a prompt that was already waiting: say so first.
            if (note != null && outcome == GiftOutcome.Processing) {
                Spacer(Modifier.height(10.dp))
                Text(note, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                giveCeremonyStatusLine(r, amountMinor, chipFundLabel, outcome, watchLapsed, failure, currency),
                style = giInter(13),
                color = when (outcome) { GiftOutcome.Succeeded -> GIVE.successText; GiftOutcome.Failed -> GIVE.danger; else -> GIVE.sub },
                textAlign = TextAlign.Center,
            )
            // What to do next, and whether money moved — the server's hint.
            giveCeremonyHint(outcome, failure)?.let { hint ->
                Spacer(Modifier.height(4.dp))
                Text(hint, style = giInter(13), color = GIVE.sub, textAlign = TextAlign.Center)
            }
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
            // Where the prompt went, while it waits on the phone.
            if (promptPhone != null && outcome == GiftOutcome.Processing) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Smartphone, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(13.dp))
                    Text("Prompt sent to ${kenyanMobileDisplay(promptPhone)}", style = giInter(12), color = GIVE.sub)
                }
            }

            // "Continue on PayPal" — for a resent order too (Cycle 10: its
            // answer now carries the approval page).
            if (canReopenPayPal(r, status)) {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.navy)
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.approveUrl.orEmpty())))
                                openedPayPal = true
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Continue on PayPal", style = giInter(14, FontWeight.SemiBold), color = Color.White)
                }
                Spacer(Modifier.height(10.dp))
                // The fallback when the automatic capture on return could not
                // confirm it (back before approving, or no answer).
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(GIVE.white).border(1.dp, GIVE.gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable(enabled = !capturing && !r.providerRef.isNullOrBlank()) { capture(quiet = false) },
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
            // A failed gift offers Try again — the server retries it with
            // everything it carried (Giving Cycle 3) — with a quiet Close
            // beside it; anything else closes with Done.
            val offerRetry = failed && canRetry
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.gold)
                    .clickable(enabled = !retrying) { if (offerRetry) onRetry() else onDone() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (retrying) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = GIVE.navy, strokeWidth = 2.dp)
                } else {
                    Text(if (offerRetry) "Try again" else "Done", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
                }
            }
            retryError?.takeIf { failed }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = giInter(12), color = GIVE.danger, textAlign = TextAlign.Center)
            }
            if (offerRetry) {
                Text(
                    "Close", style = giInter(13, FontWeight.SemiBold), color = GIVE.sub,
                    modifier = Modifier.padding(top = 8.dp).clip(Capsule).clickable(enabled = !retrying) { onDone() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Preset gift-name chips on the custom-amount sheet — like an M-Pesa Paybill
 *  account name, shown on the church's M-Pesa statement (sanitized server-side). */
private val GIVE_NAME_PRESETS = listOf("Tithe", "Offering", "Building", "Missions", "Thanksgiving", "First Fruits")

/** The dollar entry's starting amount and PayPal's own range (cents). */
private data class DollarEntry(val cents: Int, val minMinor: Long, val maxMinor: Long)

// Custom giving amount — a real editor (numeric keyboard, KSh, 1..2,000,000)
// plus an optional "Name your gift" field (named giving). With [dollars]
// (PayPal, Giving Cycle 2) it takes US dollars WITH cents inside PayPal's own
// range, and confirms the amount in cents.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CustomAmountDialog(
    initial: Int,
    initialName: String = "",
    dollars: DollarEntry? = null,
    onConfirm: (Int, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(dollars?.let { usdInput(it.cents) } ?: initial.toString()) }
    var name by remember { mutableStateOf(initialName) }
    val parsed = if (dollars != null) usdCentsOf(text) ?: 0 else text.filter { it.isDigit() }.take(7).toIntOrNull() ?: 0
    val valid = if (dollars != null) {
        parsed >= dollars.minMinor && (dollars.maxMinor <= 0 || parsed <= dollars.maxMinor) && parsed > 0
    } else {
        parsed in 1..2_000_000
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GIVE.white,
        title = { Text("Enter amount", style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = if (dollars != null) usdTyping(v) else v.filter { it.isDigit() }.take(7) },
                    singleLine = true,
                    prefix = { Text(if (dollars != null) "US$ " else "KSh ", style = giInter(15, FontWeight.Medium), color = GIVE.sub) },
                    textStyle = giSerif(24, FontWeight.SemiBold).copy(color = GIVE.navy),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = if (dollars != null) {
                            androidx.compose.ui.text.input.KeyboardType.Decimal
                        } else {
                            androidx.compose.ui.text.input.KeyboardType.Number
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!valid && text.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (dollars != null) usdRangeMessage(dollars.minMinor, dollars.maxMinor.takeIf { it > 0 } ?: 1_000_000)
                        else "Enter an amount between KSh 1 and KSh 2,000,000.",
                        style = giInter(12), color = GIVE.sub,
                    )
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
                    if (dollars != null) "Shows on your receipt and giving statement."
                    else "Shows on the church's M-Pesa statement — like a Paybill account name.",
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
