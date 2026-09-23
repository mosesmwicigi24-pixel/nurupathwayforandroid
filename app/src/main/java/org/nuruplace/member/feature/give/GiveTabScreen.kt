// The Give tab (docs/PARTNERS_PROGRAMME.md §0) — a two-segment capsule over
// ONE bottom-bar destination: Give (the giving screen, unchanged) · Partners
// (the programme, PartnersScreen.kt). The same outer-capsule idiom as the You
// tab (YouScreen.kt) so the two tabs read as siblings.
//
// Two routes wear this tab: "give" opens on Give, "partners" opens on Partners
// — so every existing nav.navigate("give") (Home's Give card, pushes,
// NotificationsScreen.routeFor) and the Home invite's "Become a partner"
// (→ "partners") keep landing correctly.
//
// Cross-segment handoffs live here, not in either screen: a pledge's "Pay now"
// hands a GivePreset to Give and switches the segment; "Add a pledge" opens
// the full-screen NewPledgeFlow over the tab (system back closes it), and a
// created pledge reloads Partners through the ViewModel hoisted here so it
// survives the segment switch.
package org.nuruplace.member.feature.give

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import org.nuruplace.member.feature.community.CHAT
import org.nuruplace.member.feature.community.Segment
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Spacing

private val Capsule = RoundedCornerShape(999.dp)

/** The two segments — `route` doubles as the NavHost route that pre-selects it
 *  (MainShell), so the two never drift apart. */
enum class GiveSegment(val route: String, val label: String, val icon: ImageVector) {
    Give("give", "Give", Icons.Filled.VolunteerActivism),
    Partners("partners", "Partners", Icons.Filled.Handshake),
}

@Composable
fun GiveTabScreen(
    initial: GiveSegment,
    onNavigate: (String) -> Unit,
    /** A preset arriving from OUTSIDE the tab — a department need's "Give to
     *  this need" (MainShell's give-need route). In-tab handoffs (a pledge's
     *  Pay now) set the same state from Partners below. */
    initialPreset: GivePreset? = null,
) {
    val view = LocalView.current
    // rememberSaveable so rotation / process death restore the segment; landing
    // fresh on "give" or "partners" re-seeds to THAT segment.
    var segment by rememberSaveable(initial) { mutableStateOf(initial) }
    // Hoisted so a pledge created in the flow, or a Pay now handoff, reloads
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(CHAT.paper)
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .clip(Capsule)
                    .background(CHAT.white.copy(alpha = 0.7f))
                    .border(1.dp, CHAT.border, Capsule)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GiveSegment.entries.forEach { s ->
                    Segment(label = s.label, icon = s.icon, count = null, selected = segment == s) {
                        if (segment != s) Haptics.tick(view)
                        segment = s
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (segment) {
                // key(payPreset): a fresh Pay now re-seeds the giving form's
                // remembered fund/amount/frequency instead of leaving stale ones.
                GiveSegment.Give -> key(payPreset) {
                    GivingScreen(
                        onBack = {},
                        onOpenStatement = { onNavigate("statement") },
                        onOpenSchedules = { onNavigate("schedules") },
                        onOpenPartners = { segment = GiveSegment.Partners },
                        preset = payPreset,
                    )
                }
                GiveSegment.Partners -> PartnersScreen(
                    vm = partnersVm,
                    onPayNow = { preset ->
                        payPreset = preset
                        segment = GiveSegment.Give
                    },
                    onOpenReceipt = { onNavigate("receipt/$it") },
                    onAddPledge = { newPledge = true },
                )
            }
        }
    }
}
