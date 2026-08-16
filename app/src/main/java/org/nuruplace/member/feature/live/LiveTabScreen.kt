// Nuru Live — the "Live" bottom tab (L4 tab restructure). Broadcaster-only:
// MainShell only mounts this destination when canGoLive(me) is true (the
// tab itself is hidden for everyone else, who keep watching live/replay
// content through Home's "LIVE now" banner and the cell screen — see
// docs/LIVE_STREAMING.md L4).
//
// Owner taste pass (hero redesign): a navy "studio card" fronts the tab —
// Fraunces "Nuru Live", a warm caption, and a large gold Go Live pill with a
// gentle breathing ring — instead of a plain header + button row. While a
// CHURCH-scope stream is actually live (LiveDiscoveryCenter, the same
// app-wide source of truth the app-wide LIVE bar uses) the hero SWAPS to a
// "● LIVE now — watch/return" pill instead of Go Live: "watch" opens the
// viewer player for someone else's broadcast, "return" reopens the
// broadcaster's own in-progress one (BroadcastController still tracks it —
// mirrors BroadcastReturnBar's own idiom one layer up).
//
// Below the hero: "My Broadcasts" (GET /live/recordings/mine) replaces the
// earlier plain "Replays" list (which showed every PUBLIC replay, not just
// this broadcaster's own) — each row carries a ⋮ menu (Play · Share ·
// Delete forever, the last behind a confirm dialog).
package org.nuruplace.member.feature.live

import android.content.Intent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.LiveNowRow
import org.nuruplace.member.data.net.LiveRecordingRow
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.LivePulsingDot
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

/**
 * @param onNavigate hands off to MainShell's NavHost — used for the
 * post-mint broadcast destination ([liveBroadcastRoute]), a tapped "My
 * Broadcasts" row ([liveRecordingRoute]), and the hero's watch/return pill.
 */
@Composable
fun LiveTabScreen(me: MeResponse?, onNavigate: (String) -> Unit) {
    var showGoLiveSheet by remember { mutableStateOf(false) }

    // Snappier first paint on entering this tab — MainShell already keeps
    // this fresh app-wide every 75s (LiveDiscoveryCenter is the ONE source
    // of truth for "what's watchable right now"), this just doesn't make the
    // hero wait out that whole window on a cold visit.
    LaunchedEffect(Unit) { LiveDiscoveryCenter.refresh() }
    val liveStreamsNow by LiveDiscoveryCenter.streams.collectAsState()
    val churchLiveNow = liveStreamsNow.firstOrNull { it.scope == "church" }
    val broadcastState by BroadcastController.state.collectAsState()
    val myActiveSession = broadcastState.session?.takeIf { broadcastState.phase != BroadcastPhase.SUMMARY }

    Column(Modifier.fillMaxSize().background(Nuru.paper)) {
        // Defensive extra top margin above whatever Scaffold's own status-bar
        // inset already gives every tab (owner-reported clipped kicker) —
        // cheap insurance against a device where the measured inset runs a
        // hair short of the real cutout/status-bar height.
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.sm)) {
            Kicker("Broadcast")
        }
        Spacer(Modifier.height(Spacing.sm))

        LiveHeroCard(
            churchLiveNow = churchLiveNow,
            myActiveSession = myActiveSession,
            onGoLive = { showGoLiveSheet = true },
            onWatch = { row -> onNavigate(liveNowRoute(row)) },
            onReturn = { session -> onNavigate(liveBroadcastRoute(session)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        )

        Spacer(Modifier.height(Spacing.lg))

        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
            Kicker("My Broadcasts")
            Spacer(Modifier.height(Spacing.xs))
            Text("Your past streams", style = NuruType.heading, color = Nuru.ink)
        }
        Spacer(Modifier.height(Spacing.sm))
        // Bounded by the outer Column's remaining space (weight) — AsyncContent's
        // loading/error states need a real (non-infinite) height to measure
        // against, same constraint ReplaysList documents.
        MyBroadcastsList(
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

@Composable
private fun LiveHeroCard(
    churchLiveNow: LiveNowRow?,
    myActiveSession: BroadcastSession?,
    onGoLive: () -> Unit,
    onWatch: (LiveNowRow) -> Unit,
    onReturn: (BroadcastSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(Radii.hero))
            .background(Nuru.homeNavyGradient),
    ) {
        Box(
            Modifier.matchParentSize().background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Nuru.gold.copy(alpha = 0.16f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(840f, -30f), radius = 560f,
                ),
            ),
        )
        Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.xl, horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Nuru Live", style = NuruType.display, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "Bring the family together, wherever they are",
                style = NuruType.body, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.lg))
            if (churchLiveNow != null) {
                LiveNowPill(
                    returning = myActiveSession?.streamId == churchLiveNow.streamId,
                    onClick = {
                        val session = myActiveSession
                        if (session != null && session.streamId == churchLiveNow.streamId) onReturn(session) else onWatch(churchLiveNow)
                    },
                )
            } else {
                BreathingGoLivePill(onClick = onGoLive)
            }
        }
    }
}

/** Large gold "Go Live" pill with a soft breathing ring — Reduce Motion just
 *  shows the static pill (a decorative-only animation, unlike a value/
 *  visibility change, so it's the one thing this composable actually skips
 *  rather than merely speeding up). */
@Composable
private fun BreathingGoLivePill(onClick: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember { isReduceMotionEnabled(context) }
    Box(contentAlignment = Alignment.Center) {
        if (!reduceMotion) {
            val t = rememberInfiniteTransition(label = "goLiveBreathe")
            val ringScale by t.animateFloat(
                initialValue = 1f, targetValue = 1.3f,
                animationSpec = infiniteRepeatable(tween(1900, easing = LinearOutSlowInEasing), RepeatMode.Restart),
                label = "goLiveRingScale",
            )
            val ringAlpha by t.animateFloat(
                initialValue = 0.4f, targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1900, easing = LinearOutSlowInEasing), RepeatMode.Restart),
                label = "goLiveRingAlpha",
            )
            Box(
                Modifier
                    .height(56.dp).width(190.dp)
                    .graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = ringAlpha }
                    .clip(RoundedCornerShape(999.dp))
                    .background(Nuru.gold),
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Nuru.goldGradient)
                .clickable { onClick() }
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Nuru.homeNavy, modifier = Modifier.size(18.dp))
            Text("Go Live", style = NuruType.cardCta, color = Nuru.homeNavy, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LiveNowPill(returning: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Nuru.gold.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .clickable { onClick() }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LivePulsingDot()
        Text("LIVE now", style = NuruType.cardCta, color = Color.White, fontWeight = FontWeight.Bold)
        Text("—", color = Color.White.copy(alpha = 0.4f), style = NuruType.cardCta)
        Text(if (returning) "Return" else "Watch", style = NuruType.cardCta, color = Nuru.gold, fontWeight = FontWeight.Bold)
    }
}

// ── My Broadcasts — the broadcaster's own recordings ────────────────────

@Composable
private fun MyBroadcastsList(onOpenRecording: (LiveRecordingRow) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        AsyncContent(load = { Net.client.api.getMyLiveRecordings().data }) { recordings, reload ->
            if (recordings.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Videocam, contentDescription = null,
                            tint = Nuru.gold.copy(alpha = 0.5f), modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text("Nothing here yet", style = NuruType.heading, color = Nuru.ink)
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "Every stream you go live with is saved here automatically.",
                            style = NuruType.caption, color = Nuru.ink600, textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    recordings.sortedByDescending { it.startedAt }.forEach { row ->
                        MyBroadcastRow(
                            row = row,
                            onPlay = { onOpenRecording(row) },
                            onDeleted = reload,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MyBroadcastRow(row: LiveRecordingRow, onPlay: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val displayTitle = row.title.ifBlank { "Nuru Live" }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(18.dp))
            .clickable(enabled = !deleting) { onPlay() }.padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.homeNavyGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (row.isAudio) Icons.Filled.GraphicEq else Icons.Filled.Videocam,
                contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(displayTitle, style = NuruType.rowTitle, color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(replayDate(row.startedAt), style = NuruType.micro, color = Nuru.ink600)
                Spacer(Modifier.width(6.dp))
                Text("·", style = NuruType.micro, color = Nuru.ink400)
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        if (row.scope == "cell") "Cell" else "Church",
                        style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Box {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(enabled = !deleting) { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                if (deleting) {
                    CircularProgressIndicator(color = Nuru.gold, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Broadcast options", tint = Nuru.ink400, modifier = Modifier.size(20.dp))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Play") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(20.dp)) },
                    onClick = { menuOpen = false; onPlay() },
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, null, modifier = Modifier.size(20.dp)) },
                    onClick = {
                        menuOpen = false
                        // recording_url is a RELATIVE path — resolve to an
                        // ABSOLUTE url before handing it to the system share
                        // sheet (a relative path means nothing outside the app).
                        val absoluteUrl = Net.client.resolveMediaUrl(row.recordingUrl.orEmpty())
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, absoluteUrl)
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "Share recording")) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Nuru.danger) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Nuru.danger, modifier = Modifier.size(20.dp)) },
                    onClick = { menuOpen = false; showDeleteConfirm = true },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete “$displayTitle”?") },
            text = { Text("The recording will be gone forever.") },
            confirmButton = {
                Text(
                    "Delete forever", style = NuruType.cardCta, color = Nuru.danger, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        showDeleteConfirm = false
                        deleting = true
                        scope.launch {
                            runCatching { Net.client.api.deleteLiveRecording(row.streamId) }
                            deleting = false
                            onDeleted()
                        }
                    }.padding(Spacing.sm),
                )
            },
            dismissButton = {
                Text(
                    "Cancel", style = NuruType.cardCta, color = Nuru.ink600,
                    modifier = Modifier.clickable { showDeleteConfirm = false }.padding(Spacing.sm),
                )
            },
        )
    }
}
