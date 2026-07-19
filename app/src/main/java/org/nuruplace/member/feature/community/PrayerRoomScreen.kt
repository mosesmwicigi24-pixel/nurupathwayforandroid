// My Prayer Room — the single destination that replaces the separate
// "Prayer wall" and "Prayer journal" entries. THREE tabs over one screen:
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

enum class PrayerRoomTab { Private, Corporate, Answered }

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun PrayerRoomScreen(
    initialTab: PrayerRoomTab = PrayerRoomTab.Private,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(initialTab) }

    Column(Modifier.fillMaxSize().background(Nuru.paper)) {
        GrowCreamHeader {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Nuru.white)
                            .border(1.dp, Nuru.border, CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nuru.navy, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Text("MY PRAYER ROOM", style = NuruType.kicker, color = Nuru.eyebrow)
                }
                Text(
                    "My Prayer Room", style = NuruType.title, color = Nuru.navy,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm, bottom = Spacing.md),
                )
                SegmentedControl(tab) { tab = it }
            }
        }
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                PrayerRoomTab.Private -> PrayerJournalScreen(embedded = true)
                PrayerRoomTab.Corporate -> PrayerWallScreen(embedded = true, onOpenPost = onOpenPost)
                PrayerRoomTab.Answered -> PrayerJournalScreen(embedded = true, forcedTab = org.nuruplace.member.feature.grow.PrayerTab.Answered)
            }
        }
    }
}

// Capsule pills, navy gradient active — Chat's segmented-control idiom.
@Composable
private fun SegmentedControl(tab: PrayerRoomTab, onSelect: (PrayerRoomTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Capsule).background(Nuru.white)
            .border(1.dp, Nuru.border, Capsule).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentButton("Private", tab == PrayerRoomTab.Private, Modifier.weight(1f)) { onSelect(PrayerRoomTab.Private) }
        SegmentButton("Corporate", tab == PrayerRoomTab.Corporate, Modifier.weight(1f)) { onSelect(PrayerRoomTab.Corporate) }
        SegmentButton("Answered", tab == PrayerRoomTab.Answered, Modifier.weight(1f)) { onSelect(PrayerRoomTab.Answered) }
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
            .padding(vertical = 10.dp),
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
