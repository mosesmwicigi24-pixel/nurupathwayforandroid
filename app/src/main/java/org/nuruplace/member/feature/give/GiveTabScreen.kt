// The Give tab (docs/PARTNERS_PROGRAMME.md §0, §3) — a two-segment control over
// ONE bottom-bar destination: Give (the giving screen) · Partners (the
// programme, PartnersScreen.kt).
//
// The control is a FULL-WIDTH pill split in two equal halves (GIVE · PARTNERS,
// navy fill + gold text when selected) and it is the FIRST ROW INSIDE each
// screen's cream header band — one band, not a segment strip over a second
// header. The selection state lives here; the control itself is handed to
// each screen as a composable slot so the band scrolls with the page.
//
// Two routes wear this tab: "give" opens on Give, "partners" opens on Partners
// — so every existing nav.navigate("give") (Home's Give card, pushes,
// NotificationsScreen.routeFor) and the Home invite's "Become a partner"
// (→ "partners") keep landing correctly.
//
// Cross-segment handoffs live here, not in either screen: a pledge's "Pay"
// hands a GivePreset to Give and switches the segment; "Make a pledge" opens
// the full-screen NewPledgeFlow over the tab (system back closes it), and a
// created pledge reloads Partners through the ViewModel hoisted here so it
// survives the segment switch.
package org.nuruplace.member.feature.give

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import org.nuruplace.member.ui.components.Haptics
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

@Composable
fun GiveTabScreen(
    initial: GiveSegment,
    onNavigate: (String) -> Unit,
    /** A preset arriving from OUTSIDE the tab — a department need's "Give to
     *  this need" (MainShell's give-need route). In-tab handoffs (a pledge's
     *  Pay) set the same state from Partners below. */
    initialPreset: GivePreset? = null,
) {
    val view = LocalView.current
    // rememberSaveable so rotation / process death restore the segment; landing
    // fresh on "give" or "partners" re-seeds to THAT segment.
    var segment by rememberSaveable(initial) { mutableStateOf(initial) }
    // Hoisted so a pledge created in the flow, or a Pay handoff, reloads
    // Partners without the segment switch throwing the standing away.
    val partnersVm = remember { PartnersViewModel() }
    var payPreset by remember { mutableStateOf<GivePreset?>(initialPreset) }
    var newPledge by remember { mutableStateOf(false) }

    if (newPledge) {
        BackHandler { newPledge = false }
        NewPledgeFlow(
            onClose = { newPledge = false },
            onCreated = {
                newPledge = false
                partnersVm.load()
            },
        )
        return
    }

    // One control, rendered by whichever screen is showing, as the first row
    // of its header band.
    val segmentControl: @Composable () -> Unit = {
        GiveSegmentControl(segment) { s ->
            if (segment != s) Haptics.tick(view)
            segment = s
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (segment) {
                // key(payPreset): a fresh Pay re-seeds the giving form's
                // remembered fund/amount/frequency instead of leaving stale ones.
                GiveSegment.Give -> key(payPreset) {
                    GivingScreen(
                        onBack = {},
                        onOpenStatement = { onNavigate("statement") },
                        onOpenSchedules = { onNavigate("schedules") },
                        preset = payPreset,
                        segmentControl = segmentControl,
                    )
                }
                GiveSegment.Partners -> PartnersScreen(
                    vm = partnersVm,
                    onPayNow = { preset ->
                        payPreset = preset
                        segment = GiveSegment.Give
                    },
                    onOpenReceipt = { onNavigate("receipt/$it") },
                    onOpenStatement = { onNavigate("statement") },
                    onAddPledge = { newPledge = true },
                    segmentControl = segmentControl,
                )
            }
        }
    }
}
