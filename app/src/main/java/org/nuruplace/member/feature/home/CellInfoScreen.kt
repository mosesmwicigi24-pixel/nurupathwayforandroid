// Cell info — the destination behind the "This week at Nuru" featured-cell card.
// Merges GET /home/featured-cell and GET /me/cell-summary into one screen: the
// leader, meeting rhythm, next gathering, and members/attendance stats. Port of
// the iOS CellInfoView.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.nuruplace.member.data.net.CellSummary
import org.nuruplace.member.data.net.FeaturedCell
import org.nuruplace.member.data.net.LiveNowRow
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.feature.live.liveNowRoute
import org.nuruplace.member.feature.profile.AvatarCircle
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.LiveStreamBanner
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.fmtEventTime

@Composable
fun CellInfoScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    var featured by remember { mutableStateOf<FeaturedCell?>(null) }
    var cell by remember { mutableStateOf<CellSummary.Cell?>(null) }
    // Nuru Live (L2) — ONE call to the same GET /live/now Home uses; the
    // server already scopes cell-scope rows to the caller's own cell, so no
    // extra client-side cellId matching (or a second endpoint) is needed here.
    var liveNow by remember { mutableStateOf<List<LiveNowRow>>(emptyList()) }
    LaunchedEffect(Unit) {
        featured = runCatching { Net.client.api.featuredCell().data }.getOrNull()
        cell = runCatching { Net.client.api.cellSummary().cell }.getOrNull()
        liveNow = runCatching { Net.client.api.getLiveNow().data }.getOrDefault(emptyList())
    }
    val cellLive = liveNow.firstOrNull { it.scope == "cell" }

    val name = featured?.name ?: cell?.name ?: "Your cell"
    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        ScreenHeader(name, kicker = "Your cell", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            cellLive?.let { row ->
                LiveStreamBanner(
                    row = row,
                    onOpen = { onNavigate(liveNowRoute(row)) },
                    onReplays = { onNavigate("live-replays") },
                )
            }
            if (featured == null && cell == null) {
                NuruCard {
                    Text("No cell yet", style = NuruType.cardTitle, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("When your leader adds you to a discipleship cell, you'll see your leader, meeting rhythm and gatherings here.", style = NuruType.caption, color = Nuru.ink600)
                }
                return@Column
            }

            val leaderName = cell?.leader?.name ?: featured?.disciplerName
            val leaderRole = cell?.leader?.role ?: featured?.disciplerRole
            NuruCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(cell?.leader?.avatarUrl, leaderName ?: name, size = 52)
                    Spacer(Modifier.size(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Kicker("Cell leader")
                        Text(leaderName ?: "Not assigned yet", style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.Bold)
                        leaderRole?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                    }
                }
            }

            featured?.let { f ->
                if (f.meets != null || f.nextSession != null || f.room != null) {
                    NuruCard {
                        Kicker("Meeting rhythm")
                        f.meets?.let { RhythmRow("Meets", it) }
                        f.nextSession?.let { RhythmRow("Next session", it) }
                        f.room?.let { RhythmRow("Where", it) }
                    }
                }
            }

            cell?.next?.let { n ->
                NuruCard {
                    Kicker("Next gathering")
                    Spacer(Modifier.height(Spacing.xs))
                    Text(fmtEventTime(n.startAt), style = NuruType.cardTitle, color = Nuru.navyDeep)
                    n.location?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                }
            }

            NuruCard {
                Kicker("Cell stats")
                Spacer(Modifier.height(Spacing.xs))
                StatRow("Members", (cell?.members ?: featured?.members ?: 0).toString())
                cell?.attendance?.let { a -> if (a.expected > 0) StatRow("Attendance", "${a.attended}/${a.expected}") }
                featured?.levelLabel?.let { StatRow("Level", it) }
                featured?.focus?.let { StatRow("Focus", it) }
            }
        }
    }
}

@Composable
private fun RhythmRow(label: String, value: String) {
    Spacer(Modifier.height(Spacing.sm))
    Text(label, style = NuruType.micro, color = Nuru.ink400)
    Text(value, style = NuruType.body, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NuruType.body, color = Nuru.ink600, modifier = Modifier.weight(1f))
        Text(value, style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.Bold)
    }
}
