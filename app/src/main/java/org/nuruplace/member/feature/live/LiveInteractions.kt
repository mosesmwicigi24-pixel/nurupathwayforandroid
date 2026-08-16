// Nuru Live — L5 interactions (docs/LIVE_INTERACTIVE.md): the viewer-side
// reaction rail, floating particles, chat overlay, raised-hand chip, and
// guest-invite card. Drawing on TikTok's right-rail reactions, Instagram
// Live's double-tap heart + floating chat, and a classroom "hands up" touch —
// pure UI, all wire calls stay in LivePlayerScreen (this file only renders +
// reports taps back via callbacks).
package org.nuruplace.member.feature.live

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.nuruplace.member.data.net.LiveGuestRow
import org.nuruplace.member.data.net.LiveHandRow
import org.nuruplace.member.data.net.LiveMessageRow
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** System-wide "Remove animations" (Settings > Accessibility) reflected via
 *  the animator duration scale — 0 means the user asked for no motion.
 *  Reaction bursts are skipped in that case; the counter itself still pops
 *  (a value change, not a decorative animation), per the design spec. Shared
 *  by the viewer player and the broadcaster HUD — both fall back to a static
 *  counter chip identically. */
fun isReduceMotionEnabled(context: android.content.Context): Boolean = try {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Exception) {
    false
}

/** TikTok-style abbreviation: 999 stays exact, 1_200 -> "1.2K", 10_000 -> "10K",
 *  1_500_000 -> "1.5M". Pure integer math — no locale-dependent float formatting. */
fun abbreviateCount(n: Int): String {
    val v = n.coerceAtLeast(0).toLong()
    return when {
        v < 1000 -> v.toString()
        v < 1_000_000 -> {
            val tenths = v / 100
            if (v < 10_000) {
                val whole = tenths / 10
                val frac = tenths % 10
                if (frac == 0L) "${whole}K" else "$whole.${frac}K"
            } else {
                "${v / 1000}K"
            }
        }
        else -> {
            val tenths = v / 100_000
            if (v < 10_000_000) {
                val whole = tenths / 10
                val frac = tenths % 10
                if (frac == 0L) "${whole}M" else "$whole.${frac}M"
            } else {
                "${v / 1_000_000}M"
            }
        }
    }
}

// ── Reaction particles — one spawns per tap, plus a slow ambient trickle from
// pulse.recent_reactions; each floats up the rail's edge with jitter+fade. ──

data class Particle(val id: Long, val emoji: String)

/** Owns the live particle list; [spawn] is safe to call from any tap handler
 *  or ambient-reaction reconciliation loop. Reduce-Motion callers should
 *  simply not call [spawn] at all (see LivePlayerScreen) — the counter itself
 *  still updates, satisfying "counter pop only". */
class LiveParticleController {
    private var nextId = 0L
    val items = mutableStateListOf<Particle>()
    fun spawn(emoji: String) {
        if (items.size > 24) return // never let a burst runaway degrade the frame
        items.add(Particle(nextId++, emoji))
    }
    fun expire(id: Long) {
        items.removeAll { it.id == id }
    }
}

@Composable
fun rememberLiveParticleController(): LiveParticleController = remember { LiveParticleController() }

/** Anchor this at the bottom-right, roughly where the action rail sits — each
 *  particle floats straight up from there with a randomized horizontal wobble. */
@Composable
fun LiveParticleLayer(controller: LiveParticleController, modifier: Modifier = Modifier) {
    Box(modifier) {
        controller.items.toList().forEach { p ->
            key(p.id) {
                FloatingParticle(p.emoji, Modifier.align(Alignment.BottomCenter)) { controller.expire(p.id) }
            }
        }
    }
}

@Composable
private fun BoxScope.FloatingParticle(emoji: String, modifier: Modifier, onExpire: () -> Unit) {
    val jitter = remember { Random.nextFloat() * 28f - 14f }
    val cycles = remember { 1.6f + Random.nextFloat() * 1.2f }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1700 + Random.nextInt(400), easing = LinearOutSlowInEasing))
        onExpire()
    }
    val t = progress.value
    val fadeStart = 0.55f
    val alpha = if (t < fadeStart) 1f else (1f - (t - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f)
    val scale = 0.6f + 0.5f * (t.coerceAtMost(0.3f) / 0.3f)
    Text(
        emoji, fontSize = 22.sp,
        modifier = modifier.graphicsLayer {
            translationX = (sin(t * PI * cycles).toFloat() * jitter).dp.toPx()
            translationY = (-230 * t).dp.toPx()
            this.alpha = alpha
            scaleX = scale; scaleY = scale
        },
    )
}

// ── Reaction key -> emoji glyph (shared by viewer AND broadcaster) ─────────

/** THE single reaction-key -> emoji mapping for the whole Live feature —
 *  every place a reaction key gets rendered as a glyph (the rail's own
 *  buttons below, the ambient/self-tap floating particles in both
 *  LivePlayerScreen and LiveBroadcastScreen, and the broadcaster's read-only
 *  [LiveReactionCounts]) MUST go through this function, never a second
 *  hand-rolled `when`. Backend reaction keys ("like"/"love"/"fire", widened
 *  by migration 1758000000182_live-reaction-fire) are a free string, not a
 *  closed enum (see LiveDtos.kt's LiveRecentReaction doc) specifically so a
 *  new key can be added server-side without breaking older clients — which
 *  means an unrecognized key here is an EXPECTED case, not a bug, and must
 *  never fall through to rendering the raw key text on screen (that's
 *  exactly the bug that shipped for the broadcaster's reaction surface: it
 *  rendered "fire" as literal text instead of 🔥). Falls back to a neutral
 *  glyph instead. */
fun reactionEmoji(key: String): String = when (key) {
    "love" -> "❤️"
    "fire" -> "🔥"
    "like" -> "👍"
    else -> "✨"
}

// ── Broadcaster-side read-only reaction counts ──────────────────────────────
// (The old right-side vertical TikTok-style action rail — LiveActionRail/
// RailButton — was removed in the owner's 2026-08-01 layout redesign: every
// viewer/guest control now lives in the ONE bottom dock, LiveChrome.kt's
// LiveBottomDock, driven by LiveDockLogic.kt's pure liveDockItems. Its own
// glyph buttons (DockGlyphButton/DockIconButton) took over this file's
// KNOWN_REACTION_ORDER/reactionEmoji/abbreviateCount building blocks
// directly — nothing here needed to change shape for that move, only the
// composable that consumed it did.)

/** Stable display order for the reaction row — known keys first (so the row
 *  doesn't jitter position across polls as counts change), anything the
 *  client doesn't recognize yet sorted alphabetically after. */
private val KNOWN_REACTION_ORDER = listOf("love", "fire", "like")

/** Zero-count keys dropped, known keys first (stable position across polls),
 *  anything unrecognized alphabetical after — split out from
 *  [LiveReactionCounts] as a plain function so the ordering/aggregation is
 *  unit-testable without a Compose/Robolectric harness (same posture as
 *  every other pure helper in this file). Both host and viewer read counts
 *  straight off the SAME server-authoritative `pulse.reactions` map — there
 *  is deliberately no separate client-side tally to drift out of sync. */
internal fun orderedReactionEntries(counts: Map<String, Int>): List<Pair<String, Int>> =
    counts.entries
        .filter { it.value > 0 }
        .sortedWith(
            compareBy(
                { entry -> KNOWN_REACTION_ORDER.indexOf(entry.key).let { if (it < 0) Int.MAX_VALUE else it } },
                { entry -> entry.key },
            ),
        )
        .map { it.key to it.value }

/** Read-only per-emoji reaction counts for the broadcaster HUD — same
 *  emoji+count pairing and pill styling as the viewer/guest dock's own
 *  reaction buttons (LiveChrome.kt's DockGlyphButton), just non-tappable
 *  (the broadcaster sees the SAME reaction picture the viewer/guest dock
 *  shows; it never originates a reaction on its own stream).
 *  Iterates whatever keys [counts] actually reports rather than a hardcoded
 *  three, so a reaction type added server-side shows up here with zero
 *  client change (see [reactionEmoji]'s doc on why that matters). Renders
 *  nothing when every count is zero. */
@Composable
fun LiveReactionCounts(counts: Map<String, Int>, modifier: Modifier = Modifier) {
    val ordered = orderedReactionEntries(counts)
    if (ordered.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ordered.forEach { (key, count) ->
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(reactionEmoji(key), fontSize = 12.sp)
                Text(abbreviateCount(count), style = NuruType.micro, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Chrome polish (owner taste pass): every round chrome control on the stage —
// the bottom dock's own buttons (LiveChrome.kt), the broadcaster HUD's mic/
// End/flip/source/hand/chat circles — is a SOFT TRANSLUCENT 48dp circle, one
// shared size/opacity so the viewer, guest, and broadcaster surfaces read as
// the same visual system.
val LiveChromeCircleSize = 48.dp
val LiveChromeCircleBg = Color.Black.copy(alpha = 0.38f)

/** Gentle fade+rise entrance for a piece of chrome (the host chip, the LIVE
 *  pill, the action rail) — Reduce Motion just shows it immediately, matching
 *  every other motion decision in this file (a value/visibility change, not a
 *  decorative animation, is still allowed). */
@Composable
fun GentleEntrance(reduceMotion: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val rise = remember { Animatable(if (reduceMotion) 0f else 10f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            alpha.animateTo(1f, tween(360, easing = LinearOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            rise.animateTo(0f, tween(360, easing = LinearOutSlowInEasing))
        }
    }
    Box(modifier.graphicsLayer { this.alpha = alpha.value; translationY = rise.value.dp.toPx() }) { content() }
}

// ── Floating draggable chat — owner taste pass ──────────────────────────
// Replaces the earlier fixed bottom-left overlay. NOT a sheet, and never a
// full-screen/modal presentation either — this is the ONE shared chat
// surface for both the viewer player (LivePlayerScreen) and the broadcaster
// HUD (LiveBroadcastScreen), since neither may cover the whole stage.
// Draggable by its header (clamped to the container it's placed in — the
// caller should size [modifier] to the full stage so there's real room to
// drag), collapsible to a small chat-bubble FAB that is ALSO independently
// draggable. Both offsets live in `remember` (no key) — position survives
// collapse/expand for as long as this composable stays in composition, i.e.
// "remembered in-session" per the owner's spec, never persisted to disk.

/** Clamps a floating overlay's drag offset so it always stays fully inside
 *  its container — pure (no Compose runtime dependency) so it's unit-
 *  testable; see LiveInteractionsTest. */
fun clampDragOffset(offset: Offset, containerSize: Size, elementSize: Size): Offset {
    val maxX = (containerSize.width - elementSize.width).coerceAtLeast(0f)
    val maxY = (containerSize.height - elementSize.height).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(0f, maxX), offset.y.coerceIn(0f, maxY))
}

@Composable
fun LiveFloatingChat(
    visible: Boolean,
    messages: List<LiveMessageRow>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomMargin: Dp = 170.dp,
) {
    if (!visible) return
    val density = LocalDensity.current
    var collapsed by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var panelOffset by remember { mutableStateOf<Offset?>(null) }
    var bubbleOffset by remember { mutableStateOf<Offset?>(null) }
    var lastSeenCount by remember { mutableStateOf(messages.size) }
    LaunchedEffect(collapsed, messages.size) { if (!collapsed) lastSeenCount = messages.size }
    val hasUnread = collapsed && messages.size > lastSeenCount

    Box(modifier.fillMaxSize().onGloballyPositioned { containerSize = it.size }) {
        if (containerSize == IntSize.Zero) return@Box
        val containerPx = Size(containerSize.width.toFloat(), containerSize.height.toFloat())
        val marginPx = with(density) { bottomMargin.toPx() }
        val startPx = with(density) { 12.dp.toPx() }

        if (collapsed) {
            val bubblePx = with(density) { 52.dp.toPx() }
            val elementPx = Size(bubblePx, bubblePx)
            val default = Offset(startPx, (containerPx.height - marginPx - bubblePx).coerceAtLeast(0f))
            val current = clampDragOffset(bubbleOffset ?: default, containerPx, elementPx)
            Box(
                Modifier
                    .offset { IntOffset(current.x.roundToInt(), current.y.roundToInt()) }
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(LiveChromeCircleBg)
                    .pointerInput(containerSize) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val base = bubbleOffset ?: default
                            bubbleOffset = clampDragOffset(base + drag, containerPx, elementPx)
                        }
                    }
                    .clickable { collapsed = false },
                contentAlignment = Alignment.Center,
            ) {
                Text("💬", fontSize = 20.sp)
                if (hasUnread) {
                    Box(
                        Modifier.size(11.dp).align(Alignment.TopEnd)
                            .clip(CircleShape).background(Nuru.gold),
                    )
                }
            }
        } else {
            val panelWidthPx = containerPx.width * 0.55f
            val panelHeightPx = containerPx.height * 0.26f
            val elementPx = Size(panelWidthPx, panelHeightPx)
            val default = Offset(startPx, (containerPx.height - marginPx - panelHeightPx).coerceAtLeast(0f))
            val current = clampDragOffset(panelOffset ?: default, containerPx, elementPx)
            val panelWidthDp = with(density) { panelWidthPx.toDp() }
            val panelHeightDp = with(density) { panelHeightPx.toDp() }
            var draft by remember { mutableStateOf("") }
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
            }

            Column(
                Modifier
                    .offset { IntOffset(current.x.roundToInt(), current.y.roundToInt()) }
                    .size(width = panelWidthDp, height = panelHeightDp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                // Header — the ONLY draggable strip (so a normal tap/scroll on
                // the message list or input never accidentally moves the panel).
                Row(
                    Modifier.fillMaxWidth()
                        .pointerInput(containerSize) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val base = panelOffset ?: default
                                panelOffset = clampDragOffset(base + drag, containerPx, elementPx)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⠿⠿", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Live chat", style = NuruType.micro, color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))
                            .clickable { collapsed = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("–", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (messages.isEmpty()) {
                        item { Text("Say hello 👋", style = NuruType.micro, color = Color.White.copy(alpha = 0.55f)) }
                    } else {
                        items(messages.takeLast(60), key = { it.messageId.ifBlank { it.sentAt } }) { m ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(m.fullName.ifBlank { "Member" } + "  ", style = NuruType.micro, color = Nuru.goldSoft, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(m.body, style = NuruType.micro, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (draft.isBlank()) Text("Say something…", style = NuruType.micro, color = Color.White.copy(alpha = 0.5f))
                        BasicTextField(
                            value = draft,
                            onValueChange = { if (it.length <= 500) draft = it },
                            textStyle = NuruType.micro.copy(color = Color.White),
                            cursorBrush = SolidColor(Nuru.gold),
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Nuru.gold,
                        modifier = Modifier.size(16.dp).clickable {
                            val body = draft.trim()
                            if (body.isNotEmpty()) { onSend(body); draft = "" }
                        },
                    )
                }
            }
        }
    }
}

// (The old standalone "✋ N" RaisedHandsChip — a floating top-right corner
// chip — was folded into LiveChrome.kt's LiveTopBar counters in the owner's
// 2026-08-01 layout redesign: raised-hand count now renders inline in the
// SAME one-line top bar as the viewer/LIVE count, never a second floating
// element.)

// ── L6 scaffolding: guest invite card + "on stage soon" chip ───────────────

@Composable
fun GuestInviteCard(onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Nuru.goldGradient)
            .padding(16.dp),
    ) {
        Text("You're invited on stage", style = NuruType.cardTitle, color = Nuru.homeNavy, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("The broadcaster wants you to join live.", style = NuruType.micro, color = Nuru.homeNavy.copy(alpha = 0.8f))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.homeNavy).clickable { onAccept() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) { Text("Accept", style = NuruType.cardCta, color = Color.White) }
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.4f)).clickable { onDecline() }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) { Text("Decline", style = NuruType.cardCta, color = Nuru.homeNavy) }
        }
    }
}

/** Small corner pill either banner above collapses to — a tap re-expands. */
@Composable
private fun GoldCornerPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Nuru.gold.copy(alpha = 0.9f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎤", fontSize = 12.sp)
        if (label.isNotBlank()) {
            Spacer(Modifier.width(5.dp))
            Text(label, style = NuruType.micro, color = Nuru.homeNavy, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Owner taste pass: the guest invite card auto-collapses to a small gold
 * corner pill ~3s after it appears (initial show, OR after tapping a
 * collapsed pill to re-expand) — tap the pill to bring the full card back.
 * Renders nothing once responded to — an "accepted" status is L6b's real
 * publish flow now (LivePlayerScreen's own GuestConnectingChip/self-preview
 * PiP/error chip, WhipPublisher-driven), not a static banner here anymore.
 */
@Composable
fun GuestStageBanner(
    status: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status != "invited") return
    var expanded by remember(status) { mutableStateOf(true) }
    LaunchedEffect(status, expanded) {
        if (expanded) {
            delay(3_000)
            expanded = false
        }
    }
    if (expanded) {
        GuestInviteCard(onAccept, onDecline, modifier.fillMaxWidth(0.85f))
    } else {
        GoldCornerPill("Invited", onClick = { expanded = true }, modifier = modifier)
    }
}

/** True iff [guests] has ME as `status`, matched by [myUserId]. */
fun myGuestStatus(guests: List<LiveGuestRow>, myUserId: String?): String? =
    if (myUserId.isNullOrBlank()) null else guests.firstOrNull { it.userId == myUserId }?.status

/** True iff [hands] has ME currently raised, matched by [myUserId]. */
fun myHandRaised(hands: List<LiveHandRow>, myUserId: String?): Boolean =
    !myUserId.isNullOrBlank() && hands.any { it.userId == myUserId }
