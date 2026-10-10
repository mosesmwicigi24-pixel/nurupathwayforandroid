// My Prayer Room — the single destination that replaces the separate
// "Prayer wall" and "Prayer journal" entries. FIVE tabs over one screen:
// Private (the member's own journal, PrayerJournalScreen, embedded), Corporate
// (the congregation's wall, PrayerWallScreen, embedded), and Answered (the
// journal again, pinned to its answered filter — iOS build 80 parity). Child
// screens keep their real behavior (add/edit/answer, share-to-wall, compose,
// react, comment, voice notes) — only their own header/back-button chrome is
// suppressed in favor of this screen's shared header + segmented control.
// Port of iOS PrayerRoomView.
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.nuruplace.member.feature.grow.PrayerJournalScreen
import org.nuruplace.member.ui.components.GrowCreamHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.icons.Lucide

// "Answered" used to be its own top-level tab; it now folds into Private's own
// Active/Answered chips (PrayerJournalScreen already shows them whenever it
// isn't pinned to a forced tab) so the room stays at a clean four across —
// Selah and Prayer Points took its top-level slot.
// Ekklesia (2026-10-10): the church's intercessory watch, beside Corporate —
// the two shared segments stand together; the private ones follow.
enum class PrayerRoomTab { Private, Corporate, Ekklesia, Selah, PrayerPoints }

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun PrayerRoomScreen(
    initialTab: PrayerRoomTab = PrayerRoomTab.Private,
    /** Inside the Community segment there is nothing to go back TO — the back
     *  circle is hidden. Same precedent as PrayerWallScreen(embedded). */
    embedded: Boolean = false,
    onBack: () -> Unit = {},
    /** The bell at the header's right (§8.1 rule 2) — given on the Pray door. */
    onOpenNotifications: (() -> Unit)? = null,
    onOpenPost: (String) -> Unit,
    /** A need on the watch, pushed to its own page (EkklesiaRequestScreen). */
    onOpenEkklesiaRequest: (String) -> Unit = {},
) {
    var tab by remember { mutableStateOf(initialTab) }

    Column(Modifier.fillMaxSize().background(Nuru.paper)) {
        GrowCreamHeader {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.lg)) {
                // Rule 2's header (§8.1), as iOS: the kicker names where you
                // are — "PRAY" — and the title says it once; the bell at the
                // right. Pushed, it is the standard shape — back, then kicker,
                // then title (it sat the kicker beside the back button).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!embedded) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(Nuru.white)
                                .border(1.dp, Nuru.border, RoundedCornerShape(16.dp))
                                .clickable(onClickLabel = "Back") { onBack() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Lucide.ArrowLeft, "Back", tint = Nuru.navy, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.weight(1f))
                    } else {
                        Text("PRAY", style = NuruType.kicker, color = Nuru.eyebrow, modifier = Modifier.weight(1f))
                    }
                    onOpenNotifications?.let {
                        org.nuruplace.member.ui.components.InboxBell(onClick = it)
                    }
                }
                if (!embedded) {
                    Text("PRAY", style = NuruType.kicker, color = Nuru.eyebrow, modifier = Modifier.padding(top = 14.dp))
                    Text(
                        "My Prayer Room", style = org.nuruplace.member.ui.theme.nuruSerif(26, androidx.compose.ui.text.font.FontWeight.SemiBold), color = Nuru.navy,
                        modifier = Modifier.padding(top = 4.dp, bottom = Spacing.md),
                    )
                } else {
                    Text(
                        "My Prayer Room", style = NuruType.title, color = Nuru.navy,
                        modifier = Modifier.padding(top = if (onOpenNotifications != null) 0.dp else Spacing.sm, bottom = Spacing.md),
                    )
                }
                SegmentedControl(tab) { tab = it }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                PrayerRoomTab.Private -> PrayerJournalScreen(embedded = true)
                // Off the tab (a pushed route), the wall's "+" clears the gesture bar itself.
                PrayerRoomTab.Corporate -> PrayerWallScreen(embedded = true, clearNavigationBar = !embedded, onOpenPost = onOpenPost)
                PrayerRoomTab.Ekklesia -> EkklesiaScreen(clearNavigationBar = !embedded, onOpenRequest = onOpenEkklesiaRequest)
                PrayerRoomTab.Selah -> SelahScreen()
                PrayerRoomTab.PrayerPoints -> PrayerPointsScreen()
            }
        }
    }
}

// Capsule pills, navy gradient active — Chat's segmented-control idiom. Four
// tabs no longer fit one equal-width row on a phone, so the capsule scrolls
// horizontally; each pill sizes to its own label instead of splitting evenly.
@Composable
private fun SegmentedControl(tab: PrayerRoomTab, onSelect: (PrayerRoomTab) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        Modifier.fillMaxWidth().clip(Capsule).background(Nuru.white)
            .border(1.dp, Nuru.border, Capsule).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SegmentButton("Private", tab == PrayerRoomTab.Private) { onSelect(PrayerRoomTab.Private) } }
        item { SegmentButton("Corporate", tab == PrayerRoomTab.Corporate) { onSelect(PrayerRoomTab.Corporate) } }
        item { SegmentButton("Ekklesia", tab == PrayerRoomTab.Ekklesia) { onSelect(PrayerRoomTab.Ekklesia) } }
        item { SegmentButton("Selah", tab == PrayerRoomTab.Selah) { onSelect(PrayerRoomTab.Selah) } }
        item { SegmentButton("Prayer Points", tab == PrayerRoomTab.PrayerPoints) { onSelect(PrayerRoomTab.PrayerPoints) } }
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    Box(
        modifier.clip(Capsule)
            .then(
                if (selected) {
                    Modifier.background(Brush.linearGradient(listOf(Color(0xFF0A1628), Color(0xFF16273F))))
                } else {
                    Modifier
                },
            )
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = NuruType.chipLabel,
            color = if (selected) Color.White else Color(0xFF59667C),
            maxLines = 1,
        )
    }
}
