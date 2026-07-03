// Profile — identity + growth scores + a menu into Your Calling (gifts),
// Resources, the Nuru assistant, notifications, and sign-out. Port of the iOS
// Profile (RootView) + the scores summary.
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ScoresSummary
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun ProfileScreen(me: MeResponse?, onOpen: (String) -> Unit, onSignOut: () -> Unit) {
    var scores by remember { mutableStateOf<ScoresSummary?>(null) }
    LaunchedEffect(Unit) { scores = runCatching { Net.client.api.scores() }.getOrNull() }

    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(Nuru.heroGradient).padding(horizontal = Spacing.screen, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Nuru.goldTint), contentAlignment = Alignment.Center) {
                Text(me?.profile?.fullName?.firstOrNull()?.uppercase() ?: "N", style = NuruType.display, color = Nuru.goldLo)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(me?.profile?.fullName ?: "Member", style = NuruType.title, color = Nuru.onNavy)
            me?.profile?.email?.let { Text(it, style = NuruType.caption, color = Nuru.onNavyDim) }
        }

        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            scores?.let { s ->
                NuruCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Kicker("Growth score")
                            Text("${s.overall.score}", style = NuruType.display, color = Nuru.navyDeep)
                            Text(s.overall.band, style = NuruType.caption, color = Nuru.ink600)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            ScoreLine("Word", s.word.score)
                            ScoreLine("Habits", s.habits.score)
                            ScoreLine("Curriculum", s.curriculum.score)
                            ScoreLine("Attendance", s.attendance.score)
                        }
                    }
                }
            }
            MenuRow("Your Calling", "Spiritual gifts") { onOpen("gifts") }
            MenuRow("Resources", "Books, talks, articles") { onOpen("resources") }
            MenuRow("Nuru Assistant", "Ask a discipleship question") { onOpen("assistant") }
            MenuRow("Notifications", "Your inbox") { onOpen("notifications") }
            TextButton(onClick = onSignOut, modifier = Modifier.padding(top = Spacing.sm)) {
                Text("Sign out", style = NuruType.cardCta, color = Nuru.danger)
            }
            Spacer(Modifier.height(Spacing.tabBarSpace))
        }
    }
}

@Composable
private fun ScoreLine(label: String, value: Int) {
    Row {
        Text(label, style = NuruType.micro, color = Nuru.ink400)
        Spacer(Modifier.size(Spacing.sm))
        Text("$value", style = NuruType.micro, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    NuruCard(modifier = Modifier.clickable { onClick() }) {
        Text(title, style = NuruType.rowTitle, color = Nuru.ink)
        Text(subtitle, style = NuruType.caption, color = Nuru.ink600)
    }
}
