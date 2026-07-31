// Nuru Live — the full-screen viewer player (L2 + L5 interactions, see
// docs/LIVE_INTERACTIVE.md). Media3 ExoPlayer over the resolved HLS/recording
// url (media3-exoplayer-hls follows the server's HLS redirect natively — no
// special handling needed). Video renders into a PlayerView; audio-kind
// streams show a branded navy/gold backdrop with a waveform animation (the
// Radio screen's on-air aesthetic) while the same ExoPlayer plays in the
// background — there is no video track to render.
//
// Lifecycle: the player is a fresh instance per screen (not the shared
// RadioController service — Live viewing does not keep playing once the
// screen closes), released via DisposableEffect keyed on the STREAM ID (see
// "flicker fix" below for why that key matters, not just the url). While
// `live` is true, a LaunchedEffect posts a heartbeat every 30s AND re-checks
// GET /live/now — if the stream has dropped out of that list, the server has
// ended it (even before ExoPlayer notices), so the ended state fires either
// way. A second LaunchedEffect polls GET /pulse every 5s (viewer cadence per
// the wire contract) for viewer_count/reactions/hands/guests, and a third
// polls GET /messages every 3s via a since-cursor. All are plain suspend
// while(true) bodies inside LaunchedEffect, which Compose cancels outright
// the moment this composable leaves the composition (back/close) — not
// merely paused.
//
// ── Flicker-to-previous-broadcast: root cause + fix ────────────────────────
// Owner report: opening the viewer briefly shows the PREVIOUS test
// broadcast's video, then snaps to the current live one. Traced against
// packages/backend/src/modules/live/service.ts (listNow): for CHURCH-scope
// streams with LIVE_CDN_BASE configured, `hls_url` is
// "{cdnBase}/live-cdn/church/index.m3u8" — a STATIC path, IDENTICAL for every
// church broadcast, ever. There is no stream_id in it. The R2 CDN mirrors
// that same path from the VPS origin on a short delay; when a new stream
// starts, the edge can still be serving the previous stream's manifest/
// segments for a few seconds until its mirror catches up — the exact
// "briefly shows the previous broadcast" symptom. This is a genuine
// server/CDN cache-staleness bug, NOT a client-side stale-player-instance
// bug (confirmed: this app never persists an ExoPlayer across the URL —
// `remember(streamId)` below always builds a brand-new instance per stream,
// and Media3's DefaultHttpDataSource here has no on-disk cache configured to
// serve a locally-cached response either).
//
// Client mitigation shipped here (does not require a server change):
// `hls_fallback_url` (service.ts's `directUrl`, "/live/{path}/index.m3u8")
// bypasses the CDN entirely — MediaMTX's own origin always reflects
// whatever is ACTUALLY live on that path right now, no fan-out cache in
// front of it. So the player opens on the fallback (origin) URL for a short
// warm-up window, then swaps the MediaItem to the primary CDN url once the
// R2 mirror has had time to catch up — the viewer never sees the CDN's stale
// copy at all. See warmUpMs below.
//
// The CORRECT permanent fix is server-side: scope the CDN mirror path by
// stream_id (e.g. "/live-cdn/church/{stream_id}/index.m3u8") so a new
// broadcast can never alias a previous one's cached objects, or failing
// that, set a very short Cache-Control/TTL (or purge-on-publish) for the
// church manifest object. Documented in docs/PARITY_AUDIT.md — NOT
// implemented here (out of scope: this session may not touch the pathway
// backend repo).
package org.nuruplace.member.feature.live

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.LiveGuestRespondBody
import org.nuruplace.member.data.net.LiveHandBody
import org.nuruplace.member.data.net.LiveMessageRow
import org.nuruplace.member.data.net.LivePulse
import org.nuruplace.member.data.net.LiveReactionBody
import org.nuruplace.member.data.net.LiveSendMessageBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.webrtc.SurfaceViewRenderer
import kotlin.math.PI
import kotlin.math.sin

/** How long the viewer stays on the direct-origin fallback URL before
 *  swapping to the CDN one — long enough for R2's mirror to have almost
 *  certainly caught up to a just-started stream. See the flicker-fix header
 *  comment above. */
private const val CDN_WARM_UP_MS = 8_000L

// ── Low-latency playback tuning (owner ask, 2026-08-01) ─────────────────────
// "the round trip tight enough that we are able to communicate, ask
// questions, raise hands, and get answers" — two client-side levers, both
// safe no-ops for a VOD/replay (ExoPlayer only applies LiveConfiguration to
// an actual live HLS manifest; a tighter-but-not-starved buffer just makes
// VOD start faster too, it never causes a replay to stall):
//  1. [MediaItem.LiveConfiguration] — a low `targetOffsetMs` tells ExoPlayer
//     to sit close to the live edge, with a narrow [1.0, 1.04] playback-speed
//     band so it gently speeds up to catch up rather than visibly jumping
//     forward.
//  2. A tightened [DefaultLoadControl] — small min/max buffers and a short
//     "wait this long before first frame" so playback starts fast and never
//     silently accumulates several seconds of buffered-but-already-stale
//     live video before the viewer sees anything.
// Server-side HLS segment/part tuning (the OTHER half of genuine low-latency
// HLS — LL-HLS partial segments, shorter segment duration) is explicitly OUT
// OF SCOPE here: the task brief says that's being handled separately, and
// this repo has no access to the MediaMTX/nginx config that would need to
// change. Not measured end-to-end against a live broadcast in this session
// (no device/server round-trip available in this sandbox) — see
// docs/PARITY_AUDIT.md's dated entry for the honest "changed but unverified
// on-device" limit.
private const val LIVE_TARGET_OFFSET_MS = 3_000L
private const val LIVE_MIN_BUFFER_MS = 3_000
private const val LIVE_MAX_BUFFER_MS = 8_000
private const val LIVE_BUFFER_FOR_PLAYBACK_MS = 500
private const val LIVE_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_000

private fun liveMediaItem(uri: String): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                .setMinPlaybackSpeed(1.0f)
                .setMaxPlaybackSpeed(1.04f)
                .build(),
        )
        .build()

private fun liveLoadControl(): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(LIVE_MIN_BUFFER_MS, LIVE_MAX_BUFFER_MS, LIVE_BUFFER_FOR_PLAYBACK_MS, LIVE_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        .build()

@Composable
fun LivePlayerScreen(
    url: String,
    title: String,
    kind: String,
    live: Boolean,
    streamId: String?,
    startedAt: String?,
    initialViewerCount: Int,
    onBack: () -> Unit,
    onOpenReplays: () -> Unit,
    fallbackUrl: String? = null,
    startedByName: String? = null,
    startedByAvatarUrl: String? = null,
    myUserId: String? = null,
    myFullName: String? = null,
    myAvatarUrl: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAudio = kind == "audio"
    var ended by remember(url) { mutableStateOf(false) }
    var viewerCount by remember(url) { mutableIntStateOf(initialViewerCount) }
    val reduceMotion = remember { isReduceMotionEnabled(context) }

    // Keyed on streamId (falling back to url only for recordings, which have
    // no streamId) — NEVER just `url`, because church-scope live urls are the
    // SAME literal string across every broadcast (see header comment). Keying
    // on the real identity guarantees a fresh ExoPlayer per stream even
    // though the URL text can repeat.
    val playerKey = streamId ?: url
    val usesFallbackFirst = live && !fallbackUrl.isNullOrBlank() && fallbackUrl != url
    val player = remember(playerKey) {
        ExoPlayer.Builder(context)
            // Low-latency lever #1 (owner ask, 2026-08-01: "the round trip
            // tight enough that we are able to communicate, ask questions,
            // raise hands, and get answers") — small buffers so the player
            // starts fast and never sits several seconds behind the live
            // edge just because it accumulated a big buffer. See
            // liveLoadControl()'s own doc for the exact numbers.
            .setLoadControl(liveLoadControl())
            .build().apply {
            setMediaItem(liveMediaItem(if (usesFallbackFirst) fallbackUrl!! else url))
            addListener(object : Player.Listener {
                // ExoPlayer surfacing a playback error (the HLS manifest 404ing
                // once the broadcaster stops, a stalled fetch, etc.) is the
                // primary end-of-stream signal for both live and recorded
                // playback — never leave a spinner forever.
                override fun onPlayerError(error: PlaybackException) { ended = true }
                // A recording naturally reaching its end also counts as "over".
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) ended = true
                }
            })
            prepare()
            playWhenReady = true
        }
    }
    // A fresh player per stream; released the moment this screen closes
    // (back, system back, or navigating to Replays) — Live never keeps
    // playing in the background the way Radio's foreground-service player does.
    DisposableEffect(playerKey) { onDispose { player.release() } }

    // WebRTC warm-up (2026-07-31 host-process-death incident) — see
    // GoLiveSetupSheet.kt's matching effect for the full doc. Any viewer of
    // a LIVE (never a replay) stream might go on to raise their hand and get
    // accepted as a guest, at which point WhipPublisher makes THIS device's
    // first native WebRTC touch; probing it here, the moment the live player
    // opens, means that first touch already happened well before "join
    // stage" is even requested, off a background dispatcher.
    LaunchedEffect(streamId, live) {
        if (!live || streamId.isNullOrBlank()) return@LaunchedEffect
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            LiveWebRtc.warmUp(context).onFailure { e ->
                Log.w("LivePlayerScreen", "WebRTC warm-up failed — joining the stage will be unavailable", e)
            }
        }
    }

    // Flicker fix, part 2: swap from the direct-origin fallback to the CDN
    // url after a warm-up window, once the R2 mirror has almost certainly
    // caught up. Never re-fires (keyed on playerKey — one swap per stream).
    LaunchedEffect(playerKey) {
        if (usesFallbackFirst) {
            delay(CDN_WARM_UP_MS)
            runCatching {
                val wasPlaying = player.isPlaying || player.playWhenReady
                player.setMediaItem(liveMediaItem(url))
                player.prepare()
                player.playWhenReady = wasPlaying
            }
        }
    }

    // Heartbeat (POST /live/streams/{id}/heartbeat) every ~30s while this is a
    // LIVE stream (never for a replay — recordings have no viewer_count and
    // heartbeat is meaningless on an ended stream). Piggybacks the SAME 30s
    // tick to re-fetch GET /live/now: if `streamId` has dropped out of that
    // list the server has ended the broadcast — that's a second, authoritative
    // end-of-stream signal independent of whatever ExoPlayer itself observes.
    LaunchedEffect(streamId, live) {
        if (!live || streamId.isNullOrBlank()) return@LaunchedEffect
        while (true) {
            delay(30_000)
            runCatching { Net.client.api.postLiveHeartbeat(streamId) }
            val row = runCatching { Net.client.api.getLiveNow().data }
                .getOrNull()?.firstOrNull { it.streamId == streamId }
            if (row == null) ended = true else viewerCount = row.viewerCount
        }
    }

    // ── L5 interactions (docs/LIVE_INTERACTIVE.md) ─────────────────────────
    var pulse by remember(streamId) { mutableStateOf<LivePulse?>(null) }
    // null = not yet seeded (first poll); prevents the whole reaction history
    // from bursting as particles the instant the screen opens.
    var seenReactionKeys by remember(streamId) { mutableStateOf<Set<String>?>(null) }
    var messages by remember(streamId) { mutableStateOf<List<LiveMessageRow>>(emptyList()) }
    // Optimistic, purely-local chat sends (latency lever — owner ask: "make
    // interactions... feel instant by rendering them optimistically before
    // the server round trip confirms"). A send appends here immediately;
    // once the server confirms, the real row lands in [messages] via the
    // existing onSuccess path below and its local placeholder is dropped —
    // never double-appended, and [messageCursor] (the poll's own since-
    // cursor) is untouched by anything in here, so the 3s poll's dedup logic
    // is unaffected. Dropped silently on failure, matching every other
    // interaction's error handling in this file (sendReaction/toggleHand).
    var pendingMessages by remember(streamId) { mutableStateOf<List<LiveMessageRow>>(emptyList()) }
    var chatOpen by remember(streamId) { mutableStateOf(false) }
    var lastReactionAt by remember { mutableStateOf(0L) }
    val particles = rememberLiveParticleController()
    var handOverride by remember(streamId) { mutableStateOf<Boolean?>(null) }
    var heartPopAt by remember { mutableStateOf<Offset?>(null) }
    var heartPopId by remember { mutableIntStateOf(0) }

    LaunchedEffect(streamId, live) {
        if (!live || streamId.isNullOrBlank()) return@LaunchedEffect
        while (true) {
            val p = runCatching { Net.client.api.getLivePulse(streamId) }.getOrNull()
            if (p != null) {
                pulse = p
                if (p.viewerCount > 0 || viewerCount == 0) viewerCount = p.viewerCount
                val keys = p.recentReactions.map { "${it.emoji}@${it.at}" }.toSet()
                val prevSeen = seenReactionKeys
                if (prevSeen != null && !reduceMotion) {
                    (keys - prevSeen).take(6).forEach { k -> particles.spawn(reactionEmoji(k.substringBefore('@'))) }
                }
                seenReactionKeys = keys
            }
            delay(5_000)
        }
    }

    LaunchedEffect(streamId, live) {
        if (!live || streamId.isNullOrBlank()) return@LaunchedEffect
        while (true) {
            val res = runCatching { Net.client.api.getLiveMessages(streamId, messageCursor) }.getOrNull()
            if (res != null && res.messages.isNotEmpty()) {
                messages = (messages + res.messages).takeLast(60)
                messageCursor = res.messages.last().sentAt
            }
            delay(3_000)
        }
    }

    fun sendReaction(emoji: String) {
        val now = System.currentTimeMillis()
        // ~1s client cooldown mirroring the server's own rate limit (any
        // emoji, per user) — avoids a wasted round-trip on a rapid tap burst.
        if (now - lastReactionAt < 1_000) return
        lastReactionAt = now
        val current = pulse
        pulse = (current ?: LivePulse()).let { c ->
            c.copy(reactions = c.reactions.toMutableMap().apply { this[emoji] = (this[emoji] ?: 0) + 1 })
        }
        if (!reduceMotion) particles.spawn(reactionEmoji(emoji))
        if (streamId != null) {
            scope.launch { runCatching { Net.client.api.postLiveReaction(streamId, LiveReactionBody(emoji)) } }
        }
    }

    val handRaised = handOverride ?: myHandRaised(pulse?.hands ?: emptyList(), myUserId)
    fun toggleHand() {
        val next = !handRaised
        handOverride = next
        if (streamId != null) {
            scope.launch {
                runCatching { Net.client.api.postLiveHand(streamId, LiveHandBody(next)) }
                handOverride = null // let the next pulse reconcile the authoritative state
            }
        }
    }

    val myGuestState = myGuestStatus(pulse?.guests ?: emptyList(), myUserId)

    // ── L6b — real guest video (docs/LIVE_INTERACTIVE.md): the moment
    // `myGuestState` reports "accepted", this device checks camera/mic
    // permission (same pattern as GoLiveSetupSheet's Go Live flow — a hard
    // denial must never leave the member thinking they're "on stage" with
    // nothing actually publishing) and starts a WhipPublisher session. If
    // the invite is later withdrawn/the stream ends, this stops locally
    // without re-issuing the DELETE (the server already knows). ──────────
    var guestStageState by remember(streamId) { mutableStateOf<GuestStageState>(GuestStageState.Idle) }
    var guestMuted by remember(streamId) { mutableStateOf(false) }
    var guestCameraOn by remember(streamId) { mutableStateOf(true) }
    val whipPublisher = remember(streamId) { WhipPublisher(context) }
    var selfPreviewRenderer by remember(streamId) { mutableStateOf<SurfaceViewRenderer?>(null) }
    // Owner layout redesign (2026-08-01): the self-preview's own minimize/
    // restore state, "remembered within the session" — see GuestStageUi.kt's
    // reduceSelfPreview doc for why this is a small reducer, not a raw flag.
    var selfPreviewState by remember(streamId) { mutableStateOf(SelfPreviewUiState()) }
    // The bottom dock's SPEAKER control — device audio output route, offered
    // to every role (see LiveDockLogic.kt's doc on why it's not guest-only).
    // Defaults ON: this is a "watch party" surface, not a phone call, so the
    // speaker (not the earpiece WebRTC's own communication audio mode can
    // silently default to) is the right starting point for almost everyone.
    var speakerOn by remember(streamId) { mutableStateOf(true) }

    fun requiredGuestPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    fun hasGuestPermissions(): Boolean = requiredGuestPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun startGuestPublish() {
        val sid = streamId ?: return
        val uid = myUserId ?: return
        guestStageState = GuestStageState.Connecting
        // A fresh join starts from a clean slate — camera on, mic unmuted —
        // regardless of whatever the guest left a PREVIOUS stint on this
        // same stream set to (Leave Stage then get re-invited, say).
        guestCameraOn = true
        guestMuted = false
        val ingest = runCatching { Net.client.api.getLiveGuestIngest(sid) }.getOrNull()
        if (ingest == null || ingest.whipUrl.isBlank()) {
            guestStageState = GuestStageState.Error("Couldn't join the stage. Tap to retry.")
            return
        }
        runCatching { whipPublisher.start(ingest.whipUrl, uid, ingest.token, selfPreviewRenderer) }
            .onSuccess { guestStageState = GuestStageState.Live }
            .onFailure { e -> guestStageState = GuestStageState.Error(e.message ?: "Couldn't join the stage. Tap to retry.") }
    }

    /** [leaveServerSide] is false when the server has ALREADY moved this
     *  guest out of "accepted" (removed, stream ended) — re-issuing the
     *  DELETE then would be a harmless no-op but a wasted round-trip; it's
     *  true for an intentional Leave Stage tap or backgrounding. */
    fun stopGuestPublish(leaveServerSide: Boolean) {
        val wasActive = guestStageState is GuestStageState.Live || guestStageState is GuestStageState.Connecting
        if (!wasActive) return
        guestStageState = GuestStageState.Idle
        scope.launch { runCatching { whipPublisher.stop() } }
        if (leaveServerSide && streamId != null && myUserId != null) {
            scope.launch { runCatching { Net.client.api.deleteLiveGuest(streamId, myUserId) } }
        }
    }

    val guestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            scope.launch { startGuestPublish() }
        } else {
            guestStageState = GuestStageState.Error("Camera and microphone access is needed to join the stage.")
        }
    }

    LaunchedEffect(myGuestState) {
        if (myGuestState == "accepted" && guestStageState is GuestStageState.Idle) {
            if (hasGuestPermissions()) startGuestPublish() else guestPermissionLauncher.launch(requiredGuestPermissions())
        } else if (myGuestState != "accepted") {
            stopGuestPublish(leaveServerSide = false)
        }
    }

    // Backgrounding while on stage must never half-die (item 6 of the L6b
    // brief) — leave cleanly rather than leaving a dangling WHIP session the
    // server has to notice is gone on its own.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        stopGuestPublish(leaveServerSide = true)
    }

    // Screen fully leaving composition (back/close) — bgScope survives past
    // this composable's own cancelled scope, same precedent ApiClient.kt
    // documents for the engagement-flush-on-exit call.
    DisposableEffect(streamId) {
        onDispose {
            if (guestStageState is GuestStageState.Live || guestStageState is GuestStageState.Connecting) {
                Net.client.bgScope.launch { runCatching { whipPublisher.stop() } }
                if (streamId != null && myUserId != null) {
                    Net.client.bgScope.launch { runCatching { Net.client.api.deleteLiveGuest(streamId, myUserId) } }
                }
            }
        }
    }

    // Auto-mute the HLS playback's own audio while on stage (per the L6b
    // brief) — the WHIP publish is send-only (no return audio), so without
    // this a guest would hear their own voice echo back several seconds
    // later over HLS while actively speaking, which is disorienting.
    LaunchedEffect(guestStageState) {
        player.volume = if (guestStageState is GuestStageState.Live) 0f else 1f
    }

    // Dock SPEAKER control (owner ask: "speaker" among the guest's own
    // publish controls) — WebRTC's JavaAudioDeviceModule flips the device
    // into MODE_IN_COMMUNICATION the moment a guest starts sending their own
    // mic (a well-known WebRTC-Android gotcha: that audio mode can default
    // routing to the EARPIECE instead of the speaker), so this both applies
    // the user's own speakerOn choice AND forces communication mode back to
    // "normal" the instant the guest is no longer on stage — never leave the
    // device's audio route in a call-like state after Leave Stage.
    LaunchedEffect(guestStageState, speakerOn) {
        runCatching {
            val am = context.getSystemService(AudioManager::class.java) ?: return@runCatching
            am.mode = if (guestStageState is GuestStageState.Live) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
            am.isSpeakerphoneOn = speakerOn
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            ended -> EndedState(onOpenReplays)
            isAudio -> AudioBackdrop(title)
            else -> AndroidView(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(streamId, live) {
                        if (!live) return@pointerInput
                        detectTapGestures(onDoubleTap = { offset ->
                            sendReaction("love")
                            heartPopAt = offset
                            heartPopId += 1
                        })
                    },
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
            )
        }

        // IG-style big heart pop at the double-tap point.
        heartPopAt?.let { at ->
            androidx.compose.runtime.key(heartPopId) {
                BigHeartPop(at, reduceMotion) { heartPopAt = null }
            }
        }

        // Ambient + tap reaction particles float up from roughly the
        // reaction cluster's own position at the left of the dock's audience
        // row (owner layout redesign — the rail that used to anchor these no
        // longer exists).
        LiveParticleLayer(
            particles,
            Modifier.fillMaxSize().padding(bottom = 250.dp),
        )

        // ONE top row (owner requirement #2) — close, host identity, title,
        // LIVE pill, counters, all on one line, replacing the old three
        // scattered rows (close+badge / identity chip / bottom title box).
        LiveTopBar(
            onClose = onBack,
            live = live && !ended,
            hostName = startedByName,
            hostAvatarUrl = startedByAvatarUrl,
            title = title,
            viewerCount = viewerCount,
            handCount = pulse?.hands?.size ?: 0,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Guest invite sits just above the dock — auto-collapses to a small
        // gold corner pill after ~3s (owner taste pass, pre-existing). Once
        // accepted, this is superseded by the real L6b publish-state chip
        // right below (connecting/error — Live shows the self-preview
        // instead, and the dock's own controls, rendered at the Box root so
        // both can be positioned/dragged across the whole stage).
        if (streamId != null && !ended) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 226.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GuestStageBanner(
                    status = myGuestState,
                    onAccept = { scope.launch { runCatching { Net.client.api.postLiveGuestRespond(streamId, LiveGuestRespondBody(true)) } } },
                    onDecline = { scope.launch { runCatching { Net.client.api.postLiveGuestRespond(streamId, LiveGuestRespondBody(false)) } } },
                )
                when (val s = guestStageState) {
                    is GuestStageState.Connecting -> GuestConnectingChip("Joining the stage…", Modifier.padding(top = 8.dp))
                    is GuestStageState.Error -> GuestConnectingChip(
                        s.message,
                        Modifier.padding(top = 8.dp).clickable { scope.launch { startGuestPublish() } },
                    )
                    else -> {}
                }
            }
        }

        // ONE bottom dock (owner requirement #3) — EVERY control lives here:
        // reactions, raise hand, chat, and — once accepted onto the stage —
        // camera on/off, switch camera, mic, speaker, and Leave Stage.
        // Item set/order comes from the pure liveDockItems (LiveDockLogic.kt).
        if (live && !ended && streamId != null) {
            val dockRole = if (guestStageState is GuestStageState.Live) LiveDockRole.GUEST_ON_STAGE else LiveDockRole.VIEWER
            val dockItems = liveDockItems(dockRole, isVideoKind = !isAudio)
            LiveBottomDock(
                items = dockItems,
                state = LiveDockState(
                    reactionCounts = pulse?.reactions ?: emptyMap(),
                    handRaised = handRaised,
                    chatOpen = chatOpen,
                    cameraOn = guestCameraOn,
                    micMuted = guestMuted,
                    speakerOn = speakerOn,
                ),
                onReact = ::sendReaction,
                onToggleHand = ::toggleHand,
                onToggleChat = { chatOpen = !chatOpen },
                onToggleCamera = {
                    guestCameraOn = !guestCameraOn
                    whipPublisher.setVideoEnabled(guestCameraOn)
                },
                onSwitchCamera = { whipPublisher.switchCamera() },
                onToggleMic = {
                    guestMuted = !guestMuted
                    whipPublisher.setMicMuted(guestMuted)
                },
                onToggleSpeaker = { speakerOn = !speakerOn },
                onLeaveStage = { stopGuestPublish(leaveServerSide = true) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Floating chat — draggable, collapsible to a bubble, NEVER a sheet
        // and never covering the whole stage. Fills the whole screen as its
        // own layer purely so it has real room to be dragged around in —
        // it positions its own content, nothing here is `align`ed. Confirmed
        // sends (messages) plus optimistic local-only sends (pendingMessages,
        // the latency lever) render as one merged, ordered list; see
        // pendingMessages' own doc above for why they're kept separate
        // rather than folded into [messages] directly.
        if (live && !ended && streamId != null) {
            LiveFloatingChat(
                visible = chatOpen,
                messages = messages + pendingMessages,
                onSend = { body ->
                    val localId = "local-${System.nanoTime()}"
                    pendingMessages = pendingMessages + LiveMessageRow(
                        messageId = localId,
                        userId = myUserId.orEmpty(),
                        fullName = myFullName?.takeIf { it.isNotBlank() } ?: "You",
                        avatarUrl = myAvatarUrl,
                        body = body,
                        sentAt = java.time.Instant.now().toString(),
                    )
                    scope.launch {
                        runCatching { Net.client.api.postLiveMessage(streamId, LiveSendMessageBody(body)) }
                            .onSuccess { messages = (messages + it).takeLast(60); messageCursor = it.sentAt }
                        pendingMessages = pendingMessages.filterNot { it.messageId == localId }
                    }
                },
            )
        }

        // L6b — the guest's own draggable self-preview, real camera+mic
        // video over WHIP (docs/LIVE_INTERACTIVE.md). Defaults to the LEFT
        // edge and is minimizable to a small handle (owner requirement #4);
        // fills the whole stage as its own layer purely for drag room, same
        // idiom as the chat overlay above; renders nothing unless actually
        // publishing. Mic mute now lives in the bottom dock above.
        if (guestStageState is GuestStageState.Live) {
            GuestSelfPreviewPiP(
                collapsed = selfPreviewState.collapsed,
                onToggleCollapsed = { selfPreviewState = reduceSelfPreview(selfPreviewState, SelfPreviewAction.Toggle) },
                onRendererReady = { renderer ->
                    selfPreviewRenderer = renderer
                    whipPublisher.attachLocalPreview(renderer)
                },
                onRendererReleased = { renderer ->
                    whipPublisher.detachLocalPreview(renderer)
                    if (selfPreviewRenderer === renderer) selfPreviewRenderer = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** IG-style big heart pop at the double-tap point — scales up then fades.
 *  Reduce-Motion skips the grow, keeping only a brief fade so a tap still
 *  gives feedback without a decorative animation. */
@Composable
private fun BigHeartPop(at: Offset, reduceMotion: Boolean, onExpire: () -> Unit) {
    val scale = remember(at) { androidx.compose.animation.core.Animatable(if (reduceMotion) 1f else 0.4f) }
    val alpha = remember(at) { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(at) {
        if (!reduceMotion) {
            scale.animateTo(1.15f, tween(220, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
        }
        delay(260)
        alpha.animateTo(0f, tween(420))
        onExpire()
    }
    Text(
        "❤️", fontSize = 64.sp,
        modifier = Modifier.graphicsLayer {
            translationX = at.x - 32.dp.toPx()
            translationY = at.y - 32.dp.toPx()
            scaleX = scale.value; scaleY = scale.value
            this.alpha = alpha.value
        },
    )
}

/** "The stream has ended" — never leave a spinner forever; a way back into
 *  Replays is always one tap away. */
@Composable
private fun EndedState(onOpenReplays: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Nuru.gold.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("The stream has ended", style = NuruType.title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("A recording will appear in Replays shortly.", style = NuruType.body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldGradient).clickable { onOpenReplays() }
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) { Text("View Replays", style = NuruType.cardCta, color = Nuru.homeNavy, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Audio-kind branded still — navy + gold, breathing waveform bars, matching
 *  the Radio screen's on-air language (no video track to render). */
@Composable
private fun AudioBackdrop(title: String) {
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

// Not private — LiveBroadcastScreen.kt (L3, same package) reuses this exact
// breathing-bars visual for the broadcaster's own audio-kind HUD, per the
// Radio/Live on-air aesthetic (no live mic-level API exposed by RootEncoder
// at the version this app resolves — see that file's comment).
@Composable
fun AudioWaveform(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "liveAudioWave")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    Canvas(modifier) {
        val n = 21
        val slot = size.width / n
        val barW = (slot * 0.42f).coerceAtMost(3.dp.toPx())
        val mid = size.height / 2f
        for (i in 0 until n) {
            val x = slot * i + slot / 2f
            val envelope = sin(PI.toFloat() * i / (n - 1))
            val a = 0.35f + 0.3f * sin(phase + i * 0.9f)
            val h = (size.height * a.coerceIn(0.15f, 0.9f) * (0.5f + 0.5f * envelope)).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = Nuru.gold.copy(alpha = 0.4f + 0.5f * envelope),
                topLeft = Offset(x - barW / 2f, mid - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}
