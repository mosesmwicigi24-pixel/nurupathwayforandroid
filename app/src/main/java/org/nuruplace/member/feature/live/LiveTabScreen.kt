// Nuru Live — the "Live" bottom tab (L4 tab restructure). Broadcaster-only:
// MainShell only mounts this destination when canGoLive(me) is true (the
// tab itself is hidden for everyone else, who keep watching live/replay
// content through Home's "LIVE now" banner and the cell screen — see
// docs/LIVE_STREAMING.md L4). Hosts L3's Go Live entry point (reusing
// GoLiveSetupSheet verbatim, same flow as Home/CellInfoScreen) stacked above
// L2's Replays list (reusing [ReplaysList] verbatim) so a broadcaster's past
// streams are one tap away from where they start a new one.
package org.nuruplace.member.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

/**
 * @param onNavigate hands off to MainShell's NavHost — used for both the
 * post-mint broadcast destination ([liveBroadcastRoute]) and a tapped replay
 * row ([liveRecordingRoute]).
 */
@Composable
fun LiveTabScreen(me: MeResponse?, onNavigate: (String) -> Unit) {
    var showGoLiveSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Nuru.paper)) {
        ScreenHeader("Nuru Live", kicker = "Broadcast")

        // Defensive only — MainShell already gates this whole tab's visibility
        // on canGoLive(me) (GoLiveShared.kt), same client-advisory posture as
        // every other Go Live entry point; the server is the real authority.
        if (canGoLive(me)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
                GoLiveButton(onClick = { showGoLiveSheet = true }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.lg))
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
            Kicker("Replays")
            Spacer(Modifier.height(Spacing.xs))
            Text("Your past streams", style = NuruType.heading, color = Nuru.ink)
        }
        Spacer(Modifier.height(Spacing.sm))
        // Bounded by the outer Column's remaining space (weight) — AsyncContent's
        // loading/error states need a real (non-infinite) height to measure
        // against, so this must stay a `weight` sibling, never nested inside a
        // scrolling ancestor (see ReplaysList's own doc comment).
        ReplaysList(
            onOpenRecording = { row -> onNavigate(liveRecordingRoute(row)) },
            modifier = Modifier.weight(1f),
        )
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
