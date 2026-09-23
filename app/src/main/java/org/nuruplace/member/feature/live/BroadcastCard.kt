// The "Broadcast" card at the top of Events (docs/PARTNERS_PROGRAMME.md §0):
// Live moved out of the bottom bar and into Events for broadcasters only
// (canGoLive(me) — the server stays the real authority on every /live write).
// It is LiveTabScreen's hero — Go Live, or "LIVE now — watch/return" while a
// church-scope stream is up — plus a "My Broadcasts" row that opens the full
// studio screen (route "live", kept reachable) for the recordings list.
package org.nuruplace.member.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

/**
 * @param onNavigate hands off to MainShell's NavHost — the post-mint broadcast
 * destination ([liveBroadcastRoute]), the hero's watch/return pill, and the
 * "live" studio route for My Broadcasts.
 */
@Composable
fun BroadcastCard(me: MeResponse?, onNavigate: (String) -> Unit) {
    var showGoLiveSheet by remember { mutableStateOf(false) }

    // Snappier first paint — MainShell keeps this fresh app-wide every 75s;
    // this just doesn't make the hero wait out that window on a cold visit.
    LaunchedEffect(Unit) { LiveDiscoveryCenter.refresh() }
    val liveStreamsNow by LiveDiscoveryCenter.streams.collectAsState()
    val churchLiveNow = liveStreamsNow.firstOrNull { it.scope == "church" }
    val broadcastState by BroadcastController.state.collectAsState()
    val myActiveSession = broadcastState.session?.takeIf { broadcastState.phase != BroadcastPhase.SUMMARY }

    Column(Modifier.fillMaxWidth()) {
        Kicker("Broadcast")
        Spacer(Modifier.height(Spacing.sm))
        LiveHeroCard(
            churchLiveNow = churchLiveNow,
            myActiveSession = myActiveSession,
            onGoLive = { showGoLiveSheet = true },
            onWatch = { row -> onNavigate(liveNowRoute(row)) },
            onReturn = { session -> onNavigate(liveBroadcastRoute(session)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nuru.white)
                .border(1.dp, Nuru.border, RoundedCornerShape(18.dp))
                .clickable { onNavigate("live") }
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text("My Broadcasts", style = NuruType.rowTitle, color = Nuru.ink)
                Text("Your past streams — play, share, delete", style = NuruType.micro, color = Nuru.ink600)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Nuru.ink300, modifier = Modifier.size(18.dp))
        }
    }

    if (showGoLiveSheet) {
        GoLiveSetupSheet(
            me = me,
            lockedScope = null, // let the member pick between church/my cell, same as Home's entry point
            onDismiss = { showGoLiveSheet = false },
            onStarted = { created, streamTitle, kind, _ ->
                showGoLiveSheet = false
                onNavigate(liveBroadcastRoute(created, streamTitle, kind))
            },
        )
    }
}
