package org.nuruplace.member.feature.community

// Community — one place to be with people. The Android half of iOS
// CommunityView.swift; keep the two in step.
//
// Phase 3 of the Partners & Community design, steps 1 and 2 only. Before this,
// community life was scattered: conversations in a "Chat" segment under You,
// prayer behind Home tiles, discussions behind Home tiles. This gives the
// segment the name Community and two doors — Talk and Pray. Discussions stay
// on Home for now; "Together" (the cell-based feed) is Phase 4.
//
// RESTRUCTURING ONLY. Nothing behind either door changes: Talk IS the chat
// inbox, Pray IS the prayer room. The segment's enum name and "chat" route are
// untouched so every deep link still resolves.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.theme.nuruSans

enum class CommunityDoor(val label: String, val icon: ImageVector) {
    Talk("Talk", Icons.AutoMirrored.Filled.Chat),
    Pray("Pray", Icons.Filled.VolunteerActivism),
}

@Composable
fun CommunitySegment(
    chatUnread: Int,
    talk: @Composable () -> Unit,
    pray: @Composable () -> Unit,
) {
    var door by rememberSaveable { mutableStateOf(CommunityDoor.Talk) }
    // Lazily mounted, like the You segments: the prayer room does not load
    // until someone actually opens that door — and once it has, it stays.
    val mounted = remember { mutableStateListOf(CommunityDoor.Talk) }

    Column(Modifier.fillMaxSize().background(Nuru.paper)) {
        DoorRow(door, chatUnread) { d ->
            if (d !in mounted) mounted.add(d)
            door = d
        }
        Box(Modifier.weight(1f)) {
            // Both stay in the tree once mounted; the inactive one is simply
            // not composed on top. Mirrors iOS's opacity/hit-test approach.
            if (CommunityDoor.Talk in mounted && door == CommunityDoor.Talk) talk()
            if (CommunityDoor.Pray in mounted && door == CommunityDoor.Pray) pray()
        }
    }
}

// A quieter echo of the You capsule one level up: the same pill idiom,
// smaller, left-aligned, so it reads as "within Community" rather than as a
// second row of top-level tabs.
@Composable
private fun DoorRow(selected: CommunityDoor, chatUnread: Int, onSelect: (CommunityDoor) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CommunityDoor.entries.forEach { d ->
            val on = d == selected
            // Unread rides the Talk door only — a quiet chip, matching the
            // chips the chat inbox already uses.
            val count = if (d == CommunityDoor.Talk) chatUnread else 0
            Row(
                Modifier
                    .clip(CircleShape)
                    .then(
                        if (on) Modifier.background(
                            Brush.linearGradient(listOf(Color(0xFF0A1628), Color(0xFF16273F))))
                        else Modifier.background(Color.White.copy(alpha = 0.7f))
                            .border(1.dp, Nuru.border, CircleShape),
                    )
                    .clickable { onSelect(d) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(d.icon, contentDescription = null, modifier = Modifier.size(11.dp),
                    tint = if (on) Nuru.gold else Color(0xFF59667C))
                Text(d.label, style = nuruSans(12, FontWeight.SemiBold),
                    color = if (on) Color.White else Color(0xFF59667C))
                if (count > 0) {
                    Text(
                        if (count > 9) "9+" else "$count",
                        style = nuruSans(10, FontWeight.Bold),
                        color = if (on) Nuru.navy else Color(0xFF6A7686),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) Nuru.gold else Nuru.surface)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                            .widthIn(min = 18.dp),
                    )
                }
            }
        }
    }
}
