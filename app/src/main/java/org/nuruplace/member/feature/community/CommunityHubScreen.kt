// Community hub — entry to the Prayer wall and Messages (chat). Port of the iOS
// CommunityView.
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun CommunityHubScreen(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        Column(Modifier.background(Nuru.heroGradient).padding(horizontal = Spacing.screen, vertical = Spacing.xl)) {
            Kicker("Community")
            Spacer(Modifier.size(Spacing.sm))
            Text("Pray & connect", style = NuruType.display, color = Nuru.onNavy)
        }
        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            HubCard("prayer-wall", "Prayer wall", "Share & carry each other's requests", Icons.Filled.Favorite, Nuru.danger, Nuru.dangerBg, onOpen)
            HubCard("chat", "Messages", "Spaces, groups and direct messages", Icons.Filled.Email, Nuru.info, Nuru.infoBg, onOpen)
            HubCard("events", "Events", "What's on — RSVP and see who's going", Icons.Filled.DateRange, Nuru.goldLo, Nuru.goldTint, onOpen)
            HubCard("announcements", "Announcements", "News from your church", Icons.Filled.Notifications, Nuru.success, Nuru.successBg, onOpen)
        }
    }
}

@Composable
private fun HubCard(
    route: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onOpen: (String) -> Unit,
) {
    NuruCard(modifier = Modifier.clickable { onOpen(route) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(Radii.control)).background(tint),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.size(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(title, style = NuruType.cardTitle, color = Nuru.ink)
                Text(subtitle, style = NuruType.caption, color = Nuru.ink600)
            }
            Text("›", style = NuruType.title, color = Nuru.ink300)
        }
    }
}
