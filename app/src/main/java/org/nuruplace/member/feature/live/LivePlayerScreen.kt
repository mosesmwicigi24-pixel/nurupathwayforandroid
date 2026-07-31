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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RemoveRedEye
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.LiveGuestRespondBody
import org.nuruplace.member.data.net.LiveHandBody
import org.nuruplace.member.data.net.LiveMessageRow
import org.nuruplace.member.data.net.LivePulse
import org.nuruplace.member.data.net.LiveReactionBody
import org.nuruplace.member.data.net.LiveSendMessageBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.LivePulsingDot
import org.nuruplace.member.ui.components.startedAgoLabel
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import kotlin.math.PI
import kotlin.math.sin

/** How long the viewer stays on the direct-origin fallback URL before
 *  swapping to the CDN one — long enough for R2's mirror to have almost
 *  certainly caught up to a just-started stream. See the flicker-fix header
 *  comment above. */
private const val CDN_WARM_UP_MS = 8_000L

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
    myUserId: String? = null,
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
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(if (usesFallbackFirst) fallbackUrl!! else url))
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

    // Flicker fix, part 2: swap from the direct-origin fallback to the CDN
    // url after a warm-up window, once the R2 mirror has almost certainly
    // caught up. Never re-fires (keyed on playerKey — one swap per stream).
    LaunchedEffect(playerKey) {
        if (usesFallbackFirst) {
            delay(CDN_WARM_UP_MS)
            runCatching {
                val wasPlaying = player.isPlaying || player.playWhenReady
                player.setMediaItem(MediaItem.fromUri(url))
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
    var messageCursor by remember(streamId) { mutableStateOf<String?>(null) }
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
                    (keys - prevSeen).take(6).forEach { k -> particles.spawn(k.substringBefore('@')) }
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
        if (!reduceMotion) particles.spawn(emoji)
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

        // Ambient + tap reaction particles float up from roughly the rail's
        // position (bottom-right).
        LiveParticleLayer(
            particles,
            Modifier.fillMaxSize().padding(bottom = 220.dp, end = 28.dp),
        )

        // Chrome overlay — close (top-left), LIVE badge + eye/viewer chip and
        // raised-hands chip (top-right), broadcaster identity chip below it,
        // action rail (right edge, mid-height), floating chat (bottom-left).
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (live && !ended) {
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LivePulsingDot(size = 7.dp)
                            Text("LIVE", style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("·", style = NuruType.micro, color = Color.White.copy(alpha = 0.5f))
                            Icon(Icons.Filled.RemoveRedEye, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(12.dp))
                            Text("$viewerCount", style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    pulse?.hands?.takeIf { it.isNotEmpty() }?.let { hands ->
                        Spacer(Modifier.height(6.dp))
                        RaisedHandsChip(hands)
                    }
                }
            }
            if (!ended && (title.isNotBlank() || !startedByName.isNullOrBlank())) {
                Spacer(Modifier.height(10.dp))
                BroadcasterIdentityChip(startedByName, title, Modifier.align(Alignment.Start))
            }
            Spacer(Modifier.weight(1f))

            // Guest invite (L6 scaffolding) sits just above the chat/title area.
            when (myGuestState) {
                "invited" -> if (streamId != null) {
                    GuestInviteCard(
                        onAccept = { scope.launch { runCatching { Net.client.api.postLiveGuestRespond(streamId, LiveGuestRespondBody(true)) } } },
                        onDecline = { scope.launch { runCatching { Net.client.api.postLiveGuestRespond(streamId, LiveGuestRespondBody(false)) } } },
                        modifier = Modifier.fillMaxWidth(0.85f).padding(bottom = 12.dp),
                    )
                }
                "accepted" -> OnStageSoonChip(Modifier.padding(bottom = 12.dp))
                else -> Unit
            }

            if (!ended) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(14.dp),
                ) {
                    Text(title.ifBlank { "Nuru Live" }, style = NuruType.rowTitle, color = Color.White)
                    Spacer(Modifier.height(2.dp))
                    Text(startedAgoLabel(startedAt), style = NuruType.micro, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }

        // Right-side vertical action rail (TikTok) — mid-height, clear of the
        // top chrome and bottom title/chat.
        if (live && !ended && streamId != null) {
            LiveActionRail(
                counts = pulse?.reactions ?: emptyMap(),
                handRaised = handRaised,
                chatOpen = chatOpen,
                onReact = ::sendReaction,
                onToggleHand = ::toggleHand,
                onToggleChat = { chatOpen = !chatOpen },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, bottom = 90.dp),
            )
        }

        // Floating chat — anchored bottom-left, NOT a sheet.
        if (live && !ended && streamId != null) {
            LiveChatOverlay(
                visible = chatOpen,
                messages = messages,
                onSend = { body ->
                    scope.launch {
                        runCatching { Net.client.api.postLiveMessage(streamId, LiveSendMessageBody(body)) }
                            .onSuccess { messages = (messages + it).takeLast(60); messageCursor = it.sentAt }
                    }
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 90.dp),
            )
        }
    }
}

@Composable
private fun BroadcasterIdentityChip(name: String?, streamTitle: String, modifier: Modifier = Modifier) {
    val displayName = name?.takeIf { it.isNotBlank() } ?: "Nuru Place"
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Nuru.gold),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                displayName.trim().split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                style = NuruType.micro, color = Nuru.homeNavy, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(displayName, style = NuruType.micro, color = Color.White, fontWeight = FontWeight.SemiBold)
            if (streamTitle.isNotBlank()) {
                Text(streamTitle, style = NuruType.micro, color = Color.White.copy(alpha = 0.75f))
            }
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
