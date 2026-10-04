// The Give tab (docs/PARTNERS_PROGRAMME.md §0, §3) — a two-segment control over
// ONE bottom-bar destination: Give (the giving screen) · Partners (the
// programme, PartnersScreen.kt).
//
// The control is a pill split in two equal halves (GIVE · PARTNERS, navy fill
// + gold text when selected), the tab's bell at its right (EXPERIENCE.md §6.2
// — the same notifications inbox as every other tab's bell), and it is the
// FIRST ROW INSIDE each screen's cream header band — one band, not a segment
// strip over a second header. The selection state lives here; the control
// itself is handed to each screen as a composable slot so the band scrolls
// with the page.
//
// Two routes wear this tab: "give" opens on Give, "partners" opens on Partners
// — so every existing nav.navigate("give") (Home's Give card, pushes,
// NotificationsScreen.routeFor) and the Home invite's "Become a partner"
// (→ "partners") keep landing correctly.
//
// Cross-segment handoffs live here, not in either screen: a pledge's "Pay"
// hands a GivePreset to Give and switches the segment; "Make a pledge" opens
// the full-screen NewPledgeFlow over the tab (system back steps back through
// it, and closes it from the first step), and a
// created pledge reloads Partners through the ViewModel hoisted here so it
// survives the segment switch. That ViewModel is scoped to this destination
// (viewModel(), not remember) so it outlives a trip to the partners statement
// and back — the standing stays on screen while it refetches — and so its
// GivingEvents collector is cancelled with the destination, never leaked.
//
// A pledge opens here too (Giving Cycle 5): a Partners notice or a pledge's
// collector lands on "partners-pledge/{id}" (MainShell → openPledgeId), and a
// pledge made without its automatic collection lands on itself with the
// server's reason — the pledge stands; nothing blocks. Each opens once: a
// saveable flag keeps a return to the tab from opening it again.
//
// A pledge's "Collect it automatically at this pace" (Giving Cycle 9) sets
// its recurring gift up on Partners and hands the server's answer here: the
// Give segment shows it on the form's own give-now result — today's prompt
// watched, or why it could not go out.
//
// The double-pay guard's upstream half lives here too: when a bound gift goes
// through (or the member picks "Give to a fund instead") GivingScreen calls
// onUnbind and the preset is forgotten at once — WITHOUT re-keying the giving
// form, so the ceremony on screen stays — and a saveable flag keeps a
// give-need destination from re-binding its need when it re-enters
// composition (back from the statement, say). If the ceremony's watch then
// finds the payment FAILED, onRebind hands the binding back for a retry.
package org.nuruplace.member.feature.give

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.InboxBell
import org.nuruplace.member.ui.theme.Nuru

private val Capsule = RoundedCornerShape(999.dp)

/** The two segments — `route` doubles as the NavHost route that pre-selects it
 *  (MainShell), so the two never drift apart. */
enum class GiveSegment(val route: String, val label: String) {
    Give("give", "Give"),
    Partners("partners", "Partners"),
}

/** The tab's segment control: a white pill track (theme border, 4dp inner
 *  padding) split into two equal halves. Selected half = navy fill with gold
 *  text; the other = transparent with muted text. Uppercase Inter 13 SemiBold,
 *  no icons — the same control on both segments' header bands. */
@Composable
internal fun GiveSegmentControl(segment: GiveSegment, onSelect: (GiveSegment) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(Capsule)
            .background(GIVE.white)
            .border(1.dp, GIVE.border, Capsule)
            .padding(4.dp),
    ) {
        GiveSegment.entries.forEach { s ->
            val on = s == segment
            Box(
                Modifier.weight(1f).height(38.dp)
                    .clip(Capsule)
                    .then(if (on) Modifier.background(GIVE.navy) else Modifier)
                    .clickable { onSelect(s) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.label.uppercase(),
                    style = giInter(13, FontWeight.SemiBold, 1.2f),
                    color = if (on) Nuru.goldGlow else GIVE.ink600,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The tab's bell, at the right of the switch — the one bell every tab
 *  wears (EXPERIENCE.md §7.2 #4): the notifications inbox, white, a
 *  hairline, the switch's own height, and the gold dot only while
 *  something is unread. */
@Composable
private fun GiveBell(onClick: () -> Unit) {
    InboxBell(
        onClick = onClick, size = 46.dp, shape = CircleShape,
        container = GIVE.white, border = GIVE.border, tint = GIVE.navy, iconSize = 19.dp, dotInset = 10.dp,
    )
}

@Composable
fun GiveTabScreen(
    initial: GiveSegment,
    onNavigate: (String) -> Unit,
    /** A preset arriving from OUTSIDE the tab — a department need's "Give to
     *  this need" (MainShell's give-need route). In-tab handoffs (a pledge's
     *  Pay) set the same state from Partners below. */
    initialPreset: GivePreset? = null,
    /** A gift to open on its result — a giving_gift_failed push (MainShell's
     *  give-gift route, Giving Cycle 3): its reason, hint and Try again. */
    followTransactionId: String? = null,
    /** A pledge to open on Partners — a Partners notice or "Change it on the
     *  pledge" (MainShell's partners-pledge route, Giving Cycle 5). */
    openPledgeId: String? = null,
) {
    val view = LocalView.current
    // rememberSaveable so rotation / process death restore the segment; landing
    // fresh on "give" or "partners" re-seeds to THAT segment.
    var segment by rememberSaveable(initial) { mutableStateOf(initial) }
    // Hoisted so a pledge created in the flow, or a Pay handoff, reloads
    // Partners without the segment switch throwing the standing away.
    val partnersVm: PartnersViewModel = viewModel()
    // Set once the preset this destination opened with is spent or dropped;
    // saveable, so re-entering composition never re-seeds from initialPreset.
    var initialPresetCleared by rememberSaveable { mutableStateOf(false) }
    var payPreset by remember { mutableStateOf(if (initialPresetCleared) null else initialPreset) }
    // Set once the gift this destination opened with has been shown; saveable,
    // so coming back to the tab never re-opens it.
    var followOpened by rememberSaveable { mutableStateOf(false) }
    // Bumped by each Pay handoff so the giving form re-seeds from it. NOT
    // bumped when a binding is cleared — the form resets itself in place, so
    // a ceremony on screen is never torn down.
    var presetSeq by remember { mutableIntStateOf(0) }
    var newPledge by remember { mutableStateOf(false) }
    // Set once the pledge this destination opened with has been shown.
    var pledgeOpened by rememberSaveable { mutableStateOf(false) }
    // A pledge just made whose automatic collection could not be set up:
    // opened, with the server's reason (auto_schedule_error), once.
    var landing by remember { mutableStateOf<PledgeLanding?>(null) }
    // A recurring gift set up from a pledge's pace, waiting for the Give
    // segment to show its result — once.
    var startedSchedule by remember { mutableStateOf<StartedSchedule?>(null) }

    if (newPledge) {
        BackHandler { newPledge = false }
        NewPledgeFlow(
            // The standing is already loaded (Make a pledge lives on it), so
            // the picker's options ride along instead of a second fetch.
            pledgeOptions = partnersVm.partnership?.pledgeOptions.orEmpty(),
            // Not yet a partner: the review says the pledge joins them.
            isMember = partnersVm.partnership?.isProgrammeMember ?: false,
            onClose = { newPledge = false },
            onCreated = { created ->
                newPledge = false
                partnersVm.load()
                // The pledge WAS made — a replay (`reused`) is the same pledge.
                // Only its collection failed: land on it and say why.
                pledgeLandingAfterCreate(created)?.let { landing = it; segment = GiveSegment.Partners }
            },
        )
        return
    }

    // One control, rendered by whichever screen is showing, as the first row
    // of its header band — with the tab's bell at its right (EXPERIENCE.md
    // §6.2: one header on every tab, the bell always at the far right),
    // opening the same inbox every other bell opens.
    val segmentControl: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                GiveSegmentControl(segment) { s ->
                    if (segment != s) Haptics.tick(view)
                    segment = s
                }
            }
            Spacer(Modifier.width(10.dp))
            GiveBell { onNavigate("notifications") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (segment) {
                // key(presetSeq): a fresh Pay re-seeds the giving form's
                // remembered fund/amount/frequency instead of leaving stale ones.
                GiveSegment.Give -> key(presetSeq) {
                    GivingScreen(
                        onBack = {},
                        onOpenStatement = { onNavigate("statement") },
                        // A RECENT GIVING row opens its receipt.
                        onOpenReceipt = { onNavigate("receipt/$it") },
                        preset = payPreset,
                        segmentControl = segmentControl,
                        onUnbind = {
                            payPreset = null
                            initialPresetCleared = true
                        },
                        // The ceremony's watch found the payment failed: the
                        // binding comes back so the member can retry.
                        onRebind = { p ->
                            payPreset = p
                            if (p == initialPreset) initialPresetCleared = false
                        },
                        followTransactionId = followTransactionId.takeIf { !followOpened },
                        onFollowed = { followOpened = true },
                        // A pledge collector's "Change it on the pledge": the
                        // pledge opens on Partners, in this same tab.
                        onOpenPledge = { id -> landing = PledgeLanding(id); segment = GiveSegment.Partners },
                        startedSchedule = startedSchedule,
                        onStartedShown = { startedSchedule = null },
                    )
                }
                GiveSegment.Partners -> PartnersScreen(
                    vm = partnersVm,
                    onPayNow = { preset ->
                        payPreset = preset
                        presetSeq++
                        segment = GiveSegment.Give
                    },
                    onOpenReceipt = { onNavigate("receipt/$it") },
                    // The PARTNERS statement, not the giving one (owner 2026-09-25).
                    onOpenPartnersStatement = { year -> onNavigate(partnersStatementRoute(year)) },
                    onAddPledge = { newPledge = true },
                    segmentControl = segmentControl,
                    openPledge = landing ?: openPledgeId?.takeIf { !pledgeOpened }?.let { PledgeLanding(it) },
                    onPledgeOpened = { landing = null; pledgeOpened = true },
                    // Its first prompt went out (or could not): the Give
                    // segment shows the same result as a give-now.
                    onScheduleStarted = { started ->
                        startedSchedule = started
                        segment = GiveSegment.Give
                    },
                    // A recurring gift not yet in the standing's list: its
                    // sheet on the Recurring gifts screen.
                    onOpenSchedule = { id -> onNavigate(scheduleRoute(id)) },
                )
            }
        }
    }
}
