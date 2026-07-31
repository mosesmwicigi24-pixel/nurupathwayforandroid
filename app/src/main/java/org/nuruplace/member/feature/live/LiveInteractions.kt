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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.data.net.LiveGuestRow
import org.nuruplace.member.data.net.LiveHandRow
import org.nuruplace.member.data.net.LiveMessageRow
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import kotlin.math.PI
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

// ── The right-side vertical action rail (TikTok) ───────────────────────────

/** ❤️ 🔥 👍 (each with its abbreviated count underneath), then ✋ raise-hand
 *  (fills gold when raised), then 💬 chat toggle. [onReact] is called with
 *  "love" | "fire" | "like" — the caller owns the ~1s client cooldown so a
 *  double-tap-to-heart gesture elsewhere on screen can share the exact same
 *  gate. */
@Composable
fun LiveActionRail(
    counts: Map<String, Int>,
    handRaised: Boolean,
    chatOpen: Boolean,
    onReact: (String) -> Unit,
    onToggleHand: () -> Unit,
    onToggleChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        RailButton("❤️", abbreviateCount(counts["love"] ?: 0)) { onReact("love") }
        RailButton("🔥", abbreviateCount(counts["fire"] ?: 0)) { onReact("fire") }
        RailButton("👍", abbreviateCount(counts["like"] ?: 0)) { onReact("like") }
        RailButton(
            "✋", null,
            filled = handRaised,
            onClick = onToggleHand,
        )
        RailButton("💬", null, filled = chatOpen, onClick = onToggleChat)
    }
}

@Composable
private fun RailButton(glyph: String, count: String?, filled: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(if (filled) Nuru.gold.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.35f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) { Text(glyph, fontSize = 22.sp) }
        count?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Floating chat overlay (owner's exact vision — anchored bottom-left) ────

/** NOT a sheet: bottom-left, lower ~1/3 of the screen, ~2/3 width, translucent
 *  dark skin, last ~6 messages (oldest fading at the top, auto-updating as the
 *  poll advances), plus a translucent input pill above it while [visible]. */
@Composable
fun LiveChatOverlay(
    visible: Boolean,
    messages: List<LiveMessageRow>,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    var draft by remember { mutableStateOf("") }
    val shown = messages.takeLast(6)
    Column(modifier) {
        // Input pill — above the panel, per the owner's spec ("reveals a
        // translucent input pill above it").
        Row(
            Modifier.fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (draft.isBlank()) Text("Say something…", style = NuruType.body, color = Color.White.copy(alpha = 0.5f))
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 500) draft = it },
                    textStyle = NuruType.body.copy(color = Color.White),
                    cursorBrush = SolidColor(Nuru.gold),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Nuru.gold,
                modifier = Modifier.size(20.dp).clickable {
                    val body = draft.trim()
                    if (body.isNotEmpty()) { onSend(body); draft = "" }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        // Message panel — translucent dark skin, rounded, last 6, oldest fading.
        Column(
            Modifier.fillMaxWidth(0.68f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.32f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            shown.forEachIndexed { i, m ->
                val fade = 0.35f + 0.65f * ((i + 1).toFloat() / shown.size.coerceAtLeast(1))
                Row(Modifier.padding(vertical = 3.dp).graphicsLayer { alpha = fade }) {
                    Text(m.fullName.ifBlank { "Member" } + "  ", style = NuruType.micro, color = Nuru.goldSoft, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(m.body, style = NuruType.micro, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (shown.isEmpty()) {
                Text("Say hello 👋", style = NuruType.micro, color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

// ── Classroom touch: "✋ N" chip near the top when hands are raised ────────

@Composable
fun RaisedHandsChip(hands: List<LiveHandRow>, modifier: Modifier = Modifier) {
    if (hands.isEmpty()) return
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✋", fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        Text("${hands.size}", style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

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

@Composable
fun OnStageSoonChip(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Nuru.gold.copy(alpha = 0.9f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎤", fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text("On stage soon", style = NuruType.micro, color = Nuru.homeNavy, fontWeight = FontWeight.Bold)
    }
}

/** True iff [guests] has ME as `status`, matched by [myUserId]. */
fun myGuestStatus(guests: List<LiveGuestRow>, myUserId: String?): String? =
    if (myUserId.isNullOrBlank()) null else guests.firstOrNull { it.userId == myUserId }?.status

/** True iff [hands] has ME currently raised, matched by [myUserId]. */
fun myHandRaised(hands: List<LiveHandRow>, myUserId: String?): Boolean =
    !myUserId.isNullOrBlank() && hands.any { it.userId == myUserId }
