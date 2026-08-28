// Nuru Live — the broadcaster's own screen (L3), Broadcast Studio rework.
// Full-bleed center-crop preview, floating inset-aware HUD, a Camera/Screen/
// Document source switcher, and — the headline change — a screen this thin:
// the actual RootEncoder engine now lives in LiveBroadcastService, reached
// only through BroadcastController, so navigating away, switching tabs, or
// locking the phone no longer ends the broadcast (see that service's header
// comment for the full "why"). This file owns UI-only concerns: the HUD
// chrome, the L5 interaction polling (pulse/reactions/hands/chat — all
// resubscribe-on-return rather than living in the service, since they're
// idempotent GETs with nothing lost by re-fetching), and the source-switcher
// consent flows (MediaProjection, SAF) that only an Activity can launch.
package org.nuruplace.member.feature.live

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.LiveMessageRow
import org.nuruplace.member.data.net.LivePulse
import org.nuruplace.member.data.net.LiveSendMessageBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.LivePulsingDot
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun LiveBroadcastScreen(
    streamId: String,
    rtmpUrl: String,
    streamKey: String,
    title: String,
    kind: String, // "video" | "audio"
    onEnded: () -> Unit,
    myUserId: String? = null,
) {
    val context = LocalContext.current
    val isVideo = kind == "video"

    val state by BroadcastController.state.collectAsState()
    val phase = state.phase

    // Idempotent per streamId — a fresh "Go Live" call starts the service +
    // the engine; landing back here for the SAME streamId (tab switch, the
    // "tap to return" pill, the notification) just re-attaches to whatever's
    // already running. See BroadcastController.start()/LiveBroadcastService.
    LaunchedEffect(streamId) {
        BroadcastController.start(context, streamId, rtmpUrl, streamKey, title, kind)
    }

    var showEndConfirm by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    // Non-null exactly when Document mode's PDF is picked AND the engine has
    // actually switched to the Screen source — see the `documentUri != null
    // && state.source == SCREEN` gate below, which is what actually decides
    // whether the full-screen pager renders.
    var documentUri by remember { mutableStateOf<Uri?>(null) }

    fun endStream() {
        showEndConfirm = false
        BroadcastController.end()
    }

    // ── Source switcher consent flows (MediaProjection + SAF) — must be
    // launched from an Activity, so they live here, not inside the sheet or
    // the Service. ──────────────────────────────────────────────────────
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            BroadcastController.switchToScreenSource(result.resultCode, result.data!!)
        } else {
            // Consent denied/cancelled — nothing switched, so a Document
            // pick that was in flight never actually shows (the render gate
            // above requires source == SCREEN too), but clear it anyway so
            // a later plain Camera→Document pick doesn't inherit a stale uri.
            documentUri = null
        }
    }
    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            documentUri = uri
            val mpm = context.getSystemService(MediaProjectionManager::class.java)
            screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    fun requestScreenShare() {
        val mpm = context.getSystemService(MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    fun switchToCamera() {
        documentUri = null
        BroadcastController.switchToCameraSource()
    }

    // ── Preview attach/detach — the SurfaceView's own OS lifecycle (created
    // when this screen is composed and visible, destroyed when it isn't) is
    // exactly "detach on background/navigate-away, re-attach on return", so
    // no ON_STOP/ON_RESUME wiring is needed here at all; the callback below
    // is the whole mechanism. broadcaster.startPreview()/stopPreview() are
    // StreamBase.startPreview(surface,...)/stopPreview() under the hood
    // (verified 2.5.9 source, LiveBroadcastEngine.kt) — while isStreaming,
    // detaching the preview does NOT stop the publish, it just frees the GL
    // preview surface; the encoder keeps consuming frames the whole time. ──
    DisposableEffect(Unit) {
        onDispose { BroadcastController.detachPreview() }
    }

    // Keep the screen on for the whole broadcast (locally — cleared the
    // instant this screen leaves composition, same as before).
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // "N watching" — poll GET /live/now every 15s while actually live,
    // filtered to our own stream_id, tracking a running peak client-side.
    // Resubscribe-on-return (not service-owned): a harmless re-fetch, and
    // peak resets if you navigate fully away and back — a documented, minor
    // cosmetic tradeoff, not a data-loss concern (the server is still the
    // one true viewer count).
    var viewerCount by remember { mutableIntStateOf(0) }
    var peakViewers by remember { mutableIntStateOf(0) }
    LaunchedEffect(streamId) {
        while (true) {
            delay(15_000)
            if (BroadcastController.state.value.phase == BroadcastPhase.LIVE) {
                val row = runCatching { Net.client.api.getLiveNow().data }.getOrNull()
                    ?.firstOrNull { it.streamId == streamId }
                if (row != null) {
                    viewerCount = row.viewerCount
                    if (row.viewerCount > peakViewers) peakViewers = row.viewerCount
                }
            }
        }
    }

    // Elapsed mm:ss, ticking only once we're actually connected. Reads from
    // the service's own startedAtMillis, so it's correct even on a fresh
    // resubscribe (doesn't restart from 0 when you return to the screen).
    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.startedAtMillis) {
        val started = state.startedAtMillis ?: return@LaunchedEffect
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - started) / 1000).toInt()
            delay(1_000)
        }
    }

    // ── L5 interactions (docs/LIVE_INTERACTIVE.md) — broadcaster side ──────
    val scope = rememberCoroutineScope()
    var pulse by remember { mutableStateOf<LivePulse?>(null) }
    var seenReactionKeys by remember { mutableStateOf<Set<String>?>(null) }
    val reactionParticles = rememberLiveParticleController()
    var showHandsSheet by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }
    val reduceMotion = remember { isReduceMotionEnabled(context) }

    suspend fun refreshPulseNow() {
        val p = runCatching { Net.client.api.getLivePulse(streamId) }.getOrNull() ?: return
        pulse = p
    }

    // Chat — same shared floating overlay the viewer uses (LiveInteractions.kt's
    // LiveFloatingChat), NOT the earlier ModalBottomSheet: it must never cover
    // the broadcaster's own stage. Lifted up here (rather than living inside
    // the sheet, the way LiveBroadcastChatSheet used to own it) since the
    // presentational component now just takes messages/onSend as props.
    var chatMessages by remember { mutableStateOf<List<LiveMessageRow>>(emptyList()) }
    var chatCursor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(streamId) {
        while (true) {
            delay(3_000)
            val res = runCatching { Net.client.api.getLiveMessages(streamId, chatCursor) }.getOrNull()
            if (res != null && res.messages.isNotEmpty()) {
                chatMessages = (chatMessages + res.messages).takeLast(60)
                chatCursor = res.messages.last().sentAt
            }
        }
    }

    LaunchedEffect(streamId) {
        while (true) {
            delay(3_000)
            if (BroadcastController.state.value.phase == BroadcastPhase.LIVE) {
                val p = runCatching { Net.client.api.getLivePulse(streamId) }.getOrNull()
                if (p != null) {
                    pulse = p
                    val keys = p.recentReactions.map { "${it.emoji}@${it.at}" }.toSet()
                    val prevSeen = seenReactionKeys
                    if (prevSeen != null && !reduceMotion) {
                        val fresh = keys - prevSeen
                        // Raw reaction key ("like"/"love"/"fire") -> emoji
                        // glyph via the SAME mapping the viewer's rail uses
                        // (LiveInteractions.kt's reactionEmoji) — previously
                        // this rendered the bare key as literal text (a
                        // floating "fire" instead of 🔥). The persistent
                        // per-emoji count below (LiveReactionCounts, driven
                        // straight off pulse.reactions — the server-
                        // authoritative tally, not a locally-accumulated
                        // guess) is what actually answers "no count"; this
                        // block is just the decorative float.
                        fresh.take(6).forEach { k -> reactionParticles.spawn(reactionEmoji(k.substringBefore('@'))) }
                    }
                    seenReactionKeys = keys
                }
            }
        }
    }

    // ── L6b — real guest video (docs/LIVE_INTERACTIVE.md): one WhepSubscriber
    // per accepted guest whose pulse row carries a whepUrl (owner-only field),
    // added/removed as that set changes; each renders into a tile in the
    // LiveStage Zoom-style stage below (LiveStageView.kt). GuestAudioMixer.kt
    // is where their decoded audio also rides the outgoing RTMP mix — this
    // screen only owns the video rendering + subscription lifecycle. ───────
    val whepSubscribers = remember(streamId) { mutableStateMapOf<String, WhepSubscriber>() }
    // Tracks each subscriber's OWN start() job so a guest that drops out of
    // the pulse while its start() is still in flight gets that job cancelled
    // — belt-and-braces on top of WhepSubscriber's own internal start()/stop()
    // mutex (see that file's header for the actual race this closes): the
    // guest's own device process died mid-join in the real-device report,
    // and a stray in-flight start() coroutine outliving its subscriber being
    // removed from this map is exactly the kind of orphaned work that made
    // that race possible in the first place.
    val whepStartJobs = remember(streamId) { mutableMapOf<String, kotlinx.coroutines.Job>() }
    val guestVideoGuests = pulse?.guests
        ?.filter { it.status == "accepted" && !it.whepUrl.isNullOrBlank() }
        .orEmpty()
    // (Re)launches a guest's subscribe loop — WhepSubscriber.start() now
    // OWNS the whole retry-with-backoff lifecycle internally (2026-07-31 fix,
    // see that file's header) and only returns once genuinely stopped or a
    // non-retryable/window-expired failure leaves connectionState = Error;
    // this coroutine just needs to stay alive for as long as that takes, so
    // cancelling it (Leave/removal, see the stale-guest cleanup below) is
    // the only way this ever ends early. Also the Retry tap's entry point
    // once a tile shows Error — start() is safe to call again as long as
    // stop() was never called on this instance.
    fun launchWhepStart(guestId: String, whepUrl: String) {
        val sub = whepSubscribers[guestId] ?: return
        whepStartJobs[guestId]?.cancel()
        whepStartJobs[guestId] = scope.launch { runCatching { sub.start(whepUrl, streamId, streamKey) } }
    }
    // L6c — GuestStageCompositor (the congregation-facing GL composite) only
    // ever sees WebRTC tracks + guestIds, never the REST guest row, so the
    // display names for its rail name plates have to be pushed in from here.
    LaunchedEffect(guestVideoGuests.map { it.userId to it.fullName }) {
        GuestStageCompositor.updateNames(guestVideoGuests.associate { it.userId to it.fullName })
    }
    LaunchedEffect(guestVideoGuests.map { it.userId }.toSet()) {
        val currentIds = guestVideoGuests.map { it.userId }.toSet()
        val stale = whepSubscribers.keys - currentIds
        stale.forEach { id ->
            whepStartJobs.remove(id)?.cancel()
            whepSubscribers.remove(id)?.let { sub ->
                GuestWebRtcSupervisor.unregister(id)
                scope.launch { runCatching { sub.stop() } }
            }
        }
        guestVideoGuests.forEach { guest ->
            val whepUrl = guest.whepUrl
            if (whepUrl != null && !whepSubscribers.containsKey(guest.userId)) {
                val sub = WhepSubscriber(context, guest.userId)
                whepSubscribers[guest.userId] = sub
                GuestWebRtcSupervisor.register(guest.userId, sub)
                launchWhepStart(guest.userId, whepUrl)
            }
        }
    }
    DisposableEffect(streamId) {
        onDispose {
            whepStartJobs.values.forEach { it.cancel() }
            whepStartJobs.clear()
            whepSubscribers.keys.forEach { GuestWebRtcSupervisor.unregister(it) }
            whepSubscribers.values.forEach { sub -> Net.client.bgScope.launch { runCatching { sub.stop() } } }
            whepSubscribers.clear()
        }
    }
    val guestTiles = guestVideoGuests.mapNotNull { guest ->
        whepSubscribers[guest.userId]?.let { sub ->
            HostGuestTileState(
                guest = guest,
                // sub.connectionState is Compose-observable (mutableStateOf)
                // — reading it here means this tile recomposes the instant
                // WhepSubscriber's own retry loop moves between Connecting/
                // Live/Error, no separate error map to keep in sync.
                connectionState = sub.connectionState,
                onRendererReady = { renderer -> sub.attachRenderer(renderer) },
                onRendererReleased = { renderer -> sub.detachRenderer(renderer) },
                onRetry = {
                    val whepUrl = guest.whepUrl
                    if (whepUrl != null) launchWhepStart(guest.userId, whepUrl)
                },
            )
        }
    }

    // L7 — the SAME spotlight/mute state the composited RTMP frame is built
    // from (GuestStageCompositor.reflow()), collected here so LiveStage
    // (the host's own on-screen stage, below) renders the identical
    // arrangement the congregation receives. Collecting at THIS level
    // (rather than inside LiveStage itself) keeps that composable a pure
    // "given this data, render this UI" function.
    val stageSpotlightState by GuestStageCompositor.spotlight.collectAsState()
    val stageMutedGuests by GuestStageCompositor.mutedGuests.collectAsState()

    // Hardware/gesture back must never silently abandon a live broadcast —
    // route it through the same confirmation as the End button.
    BackHandler(enabled = phase == BroadcastPhase.LIVE || phase == BroadcastPhase.RECONNECTING || phase == BroadcastPhase.CONNECTING) {
        showEndConfirm = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            phase == BroadcastPhase.SUMMARY -> SummaryView(
                streamId = streamId,
                title = title,
                elapsedSec = elapsedSec,
                peakViewers = peakViewers,
                endedInBackground = state.endedViaNotification,
                onDone = onEnded,
            )
            phase == BroadcastPhase.FAILED -> FailedView(
                reason = state.failReason,
                onRetry = { BroadcastController.manualRetry() },
                onEnd = { endStream() },
            )
            documentUri != null && state.source == BroadcastSource.SCREEN -> DocumentPagerScreen(
                uri = documentUri!!,
                muted = state.muted,
                onToggleMute = { BroadcastController.toggleMute() },
                onExit = { switchToCamera() },
                onEnd = { showEndConfirm = true },
            )
            else -> {
                if (isVideo) {
                    if (state.source == BroadcastSource.CAMERA) {
                        // L7 — LiveStage owns BOTH the host's own camera
                        // tile AND the guest rail/spotlight together (see
                        // LiveStageView.kt's header for why they're one
                        // composable now, not a raw full-screen preview
                        // plus a separately-positioned HostGuestRail drawn
                        // on top). tapStageTile is GuestStageCompositor's
                        // single entry point — the SAME call the composited
                        // RTMP frame's own spotlight state is driven from,
                        // so the host's own screen and what viewers receive
                        // can never independently drift.
                        LiveStage(
                            guestTiles = guestTiles,
                            spotlightGuestId = stageSpotlightState.spotlightGuestId,
                            mutedGuestIds = stageMutedGuests,
                            onTapHost = { GuestStageCompositor.tapStageTile(null) },
                            onTapGuest = { guestId -> GuestStageCompositor.tapStageTile(guestId) },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    SurfaceView(ctx).apply {
                                        holder.addCallback(object : SurfaceHolder.Callback {
                                            override fun surfaceCreated(h: SurfaceHolder) { BroadcastController.attachPreview(this@apply) }
                                            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                            override fun surfaceDestroyed(h: SurfaceHolder) { BroadcastController.detachPreview() }
                                        })
                                    }
                                },
                            )
                        }
                    } else {
                        // Plain screen share, not Document — nothing to
                        // preview locally besides a calm backdrop; the
                        // congregation sees whatever's actually on the
                        // broadcaster's screen (MediaProjection mirrors the
                        // whole display, ScreenModeControls included).
                        Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient))
                    }
                } else {
                    AudioHud(title)
                }

                if (state.source == BroadcastSource.CAMERA) {
                    LiveHudOverlay(
                        phase = phase,
                        title = title,
                        kind = kind,
                        elapsedSec = elapsedSec,
                        viewerCount = viewerCount,
                        retryAttempt = state.retryAttempt,
                        muted = state.muted,
                        isVideo = isVideo,
                        handCount = pulse?.hands?.size ?: 0,
                        onToggleMute = { BroadcastController.toggleMute() },
                        onFlipCamera = { BroadcastController.switchCamera() },
                        onSourceTapped = { showSourceSheet = true },
                        onEndTapped = { showEndConfirm = true },
                        onHandsTapped = { showHandsSheet = true },
                        onChatTapped = { showChatSheet = true },
                        reduceMotion = reduceMotion,
                    )

                    if (phase == BroadcastPhase.LIVE || phase == BroadcastPhase.RECONNECTING) {
                        // Persistent per-emoji counts — same emoji+count
                        // pairing the viewer's own action rail shows, driven
                        // by the same server-authoritative pulse.reactions
                        // map. Shown regardless of Reduce Motion (a static
                        // count is data, not a decorative animation — same
                        // reasoning as isReduceMotionEnabled's own doc).
                        // Previously the broadcaster had NO persistent count
                        // at all outside the Reduce-Motion fallback.
                        LiveReactionCounts(
                            pulse?.reactions ?: emptyMap(),
                            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 120.dp, end = 16.dp),
                        )
                        if (!reduceMotion) {
                            LiveParticleLayer(
                                reactionParticles,
                                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 156.dp, end = 24.dp)
                                    .width(70.dp).height(220.dp),
                            )
                        }
                    }

                } else if (isVideo) {
                    // Minimal LIVE indicator (top) + mute/switch-back/End
                    // (bottom) — see ScreenModeControls' header comment for
                    // why the full HUD is deliberately NOT shown here.
                    Row(
                        Modifier.align(Alignment.TopStart).statusBarsPadding().padding(Spacing.md)
                            .clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LivePulsingDot()
                        Text(if (phase == BroadcastPhase.LIVE) "LIVE" else "RECONNECTING…", style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    ScreenModeControls(
                        muted = state.muted,
                        onToggleMute = { BroadcastController.toggleMute() },
                        onSwitchToCamera = { switchToCamera() },
                        onEnd = { showEndConfirm = true },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.lg),
                    )
                }
            }
        }

        if (showSourceSheet) {
            LiveSourceSheet(
                current = when {
                    documentUri != null -> SourceChoice.DOCUMENT
                    state.source == BroadcastSource.SCREEN -> SourceChoice.SCREEN
                    else -> SourceChoice.CAMERA
                },
                onDismiss = { showSourceSheet = false },
                onPick = { choice ->
                    showSourceSheet = false
                    when (choice) {
                        SourceChoice.CAMERA -> switchToCamera()
                        SourceChoice.SCREEN -> requestScreenShare()
                        SourceChoice.DOCUMENT -> documentPickerLauncher.launch(arrayOf("application/pdf"))
                    }
                },
            )
        }

        if (showHandsSheet) {
            LiveHandsGuestsSheet(
                streamId = streamId,
                pulse = pulse,
                onRefreshPulse = ::refreshPulseNow,
                onDismiss = { showHandsSheet = false },
            )
        }

        // Fills the whole stage as its own hit-testable layer purely so it has
        // room to be dragged around in — LiveFloatingChat positions its own
        // content, nothing here is `align`ed, and it renders nothing when
        // collapsed-away's `visible` is false, so it never blocks taps on the
        // camera preview/HUD when chat is closed.
        LiveFloatingChat(
            visible = showChatSheet,
            messages = chatMessages,
            onSend = { body ->
                scope.launch {
                    runCatching { Net.client.api.postLiveMessage(streamId, LiveSendMessageBody(body)) }
                        .onSuccess { chatMessages = (chatMessages + it).takeLast(60); chatCursor = it.sentAt }
                }
            },
        )

        if (showEndConfirm) {
            AlertDialog(
                onDismissRequest = { showEndConfirm = false },
                title = { Text("End this broadcast?", style = NuruType.cardTitle, color = Nuru.navy) },
                text = { Text("Viewers watching now will see the stream end. This can't be undone.", style = NuruType.body, color = Nuru.ink600) },
                confirmButton = {
                    Text(
                        "End", style = NuruType.cardCta, color = Nuru.danger, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { endStream() }.padding(Spacing.sm),
                    )
                },
                dismissButton = {
                    Text(
                        "Cancel", style = NuruType.cardCta, color = Nuru.ink600,
                        modifier = Modifier.clickable { showEndConfirm = false }.padding(Spacing.sm),
                    )
                },
            )
        }
    }
}

@Composable
private fun AudioHud(title: String) {
    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(20.dp))
            Text(title.ifBlank { "Nuru Live" }, style = NuruType.title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            AudioWaveform(Modifier.width(180.dp).height(28.dp))
        }
    }
}

/**
 * Broadcast Studio's floating HUD — same grammar as the viewer/guest screen's
 * redesign (LiveChrome.kt, owner ask 2026-08-01): ONE top row (status/phase
 * pill, duration, watching count, and the stream title, all on one line — no
 * separate floating title chip any more) and ONE bottom dock (mic, End,
 * flip, source, raise-hand queue, chat — already consolidated pre-existing).
 * Both sit on a soft gradient scrim, never a heavy opaque bar, matching the
 * viewer surface's design-quality bar. Full-bleed video/preview underneath;
 * WindowInsets handled per-row so nothing sits under the status/gesture bars.
 */
@Composable
private fun LiveHudOverlay(
    phase: BroadcastPhase,
    title: String,
    kind: String,
    elapsedSec: Int,
    viewerCount: Int,
    retryAttempt: Int,
    muted: Boolean,
    isVideo: Boolean,
    handCount: Int,
    onToggleMute: () -> Unit,
    onFlipCamera: () -> Unit,
    onSourceTapped: () -> Unit,
    onEndTapped: () -> Unit,
    onHandsTapped: () -> Unit,
    onChatTapped: () -> Unit,
    reduceMotion: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        // ONE top row — status pill (LIVE/RECONNECTING/CONNECTING) + duration
        // + watching count + the stream title, all on one line.
        Box(
            Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LivePulsingDot()
                    Text(
                        when (phase) {
                            BroadcastPhase.RECONNECTING -> "RECONNECTING…"
                            BroadcastPhase.CONNECTING -> "CONNECTING…"
                            else -> "LIVE"
                        },
                        style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold,
                    )
                    if (phase == BroadcastPhase.LIVE) {
                        Text("·", style = NuruType.micro, color = Color.White.copy(alpha = 0.5f))
                        Text(mmss(elapsedSec), style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
                        Text("·", style = NuruType.micro, color = Color.White.copy(alpha = 0.5f))
                        Text("$viewerCount watching", style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                if (title.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        title, style = NuruType.body, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Bottom-center dock — [mic, End, flip, source, ✋, 💬], above the
        // gesture-nav inset. Same left-to-right order the iOS port's
        // controlsRow uses (End right after mic, not pinned trailing).
        GentleEntrance(reduceMotion, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Spacing.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                HudIconButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", onToggleMute)
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.danger).clickable { onEndTapped() }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) { Text("End", style = NuruType.cardCta, color = Color.White, fontWeight = FontWeight.Bold) }
                if (isVideo) HudIconButton(Icons.Filled.Cameraswitch, "Flip camera", onFlipCamera)
                if (isVideo) HudIconButton(Icons.Filled.Tune, "Broadcast source", onSourceTapped)
                HudHandButton(count = handCount, onClick = onHandsTapped)
                HudEmojiIconButton("💬", "Live chat", onChatTapped)
            }
        }
    }
}

// Sizes/opacity shared with the viewer's action rail (LiveChromeCircleSize/Bg,
// LiveInteractions.kt) — soft translucent 48dp circles, one visual system
// across both the viewer and broadcaster stages (owner taste pass).
@Composable
private fun HudIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(LiveChromeCircleSize).clip(CircleShape).background(LiveChromeCircleBg).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun HudEmojiIconButton(emoji: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(LiveChromeCircleSize).clip(CircleShape).background(LiveChromeCircleBg).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 19.sp, modifier = Modifier.semantics { contentDescription = label }) }
}

/** ✋ raise-hand queue toggle — gold badge counts hands from the 3s pulse
 *  poll, matching the iOS port's handButton exactly (badge only shown when
 *  count > 0, capped display at 99). */
@Composable
private fun HudHandButton(count: Int, onClick: () -> Unit) {
    Box(Modifier.size(LiveChromeCircleSize)) {
        Box(
            Modifier.size(LiveChromeCircleSize).clip(CircleShape).background(LiveChromeCircleBg).clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) { Text("✋", fontSize = 19.sp, modifier = Modifier.semantics { contentDescription = "Raised hands, $count" }) }
        if (count > 0) {
            Box(
                Modifier.size(20.dp).align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                    .clip(CircleShape).background(Nuru.gold),
                contentAlignment = Alignment.Center,
            ) { Text("${count.coerceAtMost(99)}", style = NuruType.micro, color = Nuru.homeNavy, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun FailedView(reason: String?, onRetry: () -> Unit, onEnd: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.lg)) {
            Text("Connection lost", style = NuruType.title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                reason ?: "Nuru Live couldn't stay connected after several tries.",
                style = NuruType.body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Retry", onClick = onRetry)
            Spacer(Modifier.height(12.dp))
            Text(
                "End broadcast", style = NuruType.cardCta, color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.clickable { onEnd() }.padding(Spacing.sm),
            )
        }
    }
}

/**
 * End-of-broadcast stewardship (owner taste pass): once the broadcast has
 * ended, the recording is already sitting in Replays server-side — this is
 * just the ONE moment to say so plainly and offer a way out. "Keep in
 * Replays" is gold and the assumed default (tapping Done without ever
 * touching Delete — i.e. just dismissing this screen — keeps the recording,
 * exactly like it always has); "Delete recording" is a quiet, low-emphasis
 * red link, gated behind the same "gone forever" confirm dialog the "My
 * Broadcasts" list uses (LiveTabScreen.kt).
 */
@Composable
private fun SummaryView(
    streamId: String,
    title: String,
    elapsedSec: Int,
    peakViewers: Int,
    endedInBackground: Boolean,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.lg)) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Nuru.gold.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                if (endedInBackground) "Nuru Live ended" else "You were live!",
                style = NuruType.title, color = Color.White, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            if (endedInBackground) {
                Text(
                    "The broadcast was ended from the notification.",
                    style = NuruType.body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "${mmss(elapsedSec)} · peak $peakViewers watching",
                style = NuruType.body, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            title.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = NuruType.caption, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
                    .background(if (!deleting) Nuru.goldGradient else androidx.compose.ui.graphics.SolidColor(Nuru.ink300))
                    .then(if (!deleting) Modifier.clickable { onDone() } else Modifier)
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (deleting) "Please wait…" else "Keep in Replays",
                    style = NuruType.cardCta, color = Nuru.homeNavy, fontWeight = FontWeight.SemiBold,
                )
            }
            if (streamId.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Delete recording", style = NuruType.cardCta, color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.clickable(enabled = !deleting) { showDeleteConfirm = true }.padding(Spacing.sm),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false }, // dismissing keeps the recording, per spec
            title = { Text("Delete “${title.ifBlank { "Nuru Live" }}”?", style = NuruType.cardTitle, color = Nuru.navy) },
            text = { Text("The recording will be gone forever.", style = NuruType.body, color = Nuru.ink600) },
            confirmButton = {
                Text(
                    "Delete forever", style = NuruType.cardCta, color = Nuru.danger, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        showDeleteConfirm = false
                        deleting = true
                        scope.launch {
                            runCatching { Net.client.api.deleteLiveRecording(streamId) }
                            deleting = false
                            onDone()
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

private fun mmss(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** App-wide "you're still broadcasting" pill — the return path for the
 *  "in-app floating pill... shows on other screens while broadcasting"
 *  requirement. Mirrors AppLiveBar's (LiveDiscoveryUi.kt) exact idiom for
 *  viewers, one screen over: MainShell shows this in the SAME bottomBar
 *  slot, keyed off [BroadcastController.state] instead of
 *  [LiveDiscoveryCenter], whenever a broadcast is running and this isn't
 *  already the screen showing it. */
@Composable
fun BroadcastReturnBar(session: BroadcastSession, elapsedSec: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Nuru.danger.copy(alpha = 0.92f))
            .clickable { onClick() }
            .padding(horizontal = Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LivePulsingDot()
        Text("LIVE", style = NuruType.kicker, color = Color.White)
        Text(mmss(elapsedSec), style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
        Text("—", color = Color.White.copy(alpha = 0.4f), style = NuruType.micro)
        Text(
            session.title.ifBlank { "Nuru Live" }, style = NuruType.cardCta, color = Color.White,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text("tap to return ›", style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
    }
}
