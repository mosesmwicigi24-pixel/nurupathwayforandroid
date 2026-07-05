// Live radio — the immersive member player (GET /radio/now-playing + /radio/programs).
// An immersive dark radio player: a blurred-artwork backdrop with breathing gold/indigo
// glows, a now-playing centerpiece (LIVE badge, title, speaker, gold play/pause with an
// expanding ring), an indeterminate sweep progress line, transport controls (mute · play ·
// sleep), live stat chips, and segmented tabs (Live · Recordings · Schedule) over a Media3
// ExoPlayer that streams the live HLS/Icecast feed or a hosted recording. Full rewrite that
// matches the iOS RadioPlayerView. Audio only; the player is released with the composition.
package org.nuruplace.member.feature.radio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RadioComment
import org.nuruplace.member.data.net.RadioCommentBody
import org.nuruplace.member.data.net.RadioProgram
import org.nuruplace.member.data.net.RadioReactBody
import org.nuruplace.member.data.net.RadioReactionCounts
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Palette ─────────────────────────────────────────────────────────────────
private object RADIO {
    val navyDeep = Color(0xFF060F1C)
    val navy = Color(0xFF0A1628)
    val panelTop = Color(0xFF16273F)
    val gold = Color(0xFFC89B3C)
    val goldLight = Color(0xFFE6C068)
    val goldDeep = Color(0xFFB6862F)
    val red = Color(0xFFEF4444)
    val redDeep = Color(0xFFDC2626)
    val redSoft = Color(0xFFFCA5A5)
    val indigo = Color(0xFF4338CA)
    val indigoSoft = Color(0xFF818CF8)
    val green = Color(0xFF16A34A)
    val textOnGold = Color(0xFF0B1F33)

    val glass = Color.White.copy(alpha = 0.06f)
    val glassBorder = Color.White.copy(alpha = 0.10f)

    val goldGrad = Brush.linearGradient(listOf(gold, goldDeep))
}

private val Capsule = RoundedCornerShape(999.dp)

// ── Reactions FX — full-screen floating emoji + a live, growing counter ──────
//
// A TikTok/IG-LIVE celebration layer. Each tap spawns an emoji that rises the
// FULL height of the screen with a sine sway, random x-jitter, random scale,
// slight rotation and a fade near the top; many coexist (rapid taps = a
// stream). The counter climbs optimistically on every tap and reconciles with
// the REAL server total from POST /react. The burst overlay is hoisted to the
// screen root so the stream is never clipped inside the reaction row.

/** One rising emoji particle. Randomness is frozen at spawn so the render is a
 *  pure function of elapsed time. `bornNanos` is the frame-clock stamp at birth. */
private class ReactionParticle(
    val emoji: String,
    val bornNanos: Long,
    val lifetimeMs: Float,      // 2500–4000
    val startXFrac: Float,      // 0…1 across the width
    val driftPx: Float,         // net horizontal travel
    val swayPx: Float,          // sine amplitude
    val swayFreq: Float,        // wobbles over the rise
    val phase: Float,           // sine phase offset
    val scale: Float,           // 0.7–1.6
    val rotation: Float,        // final rotation (deg)
) {
    val id = nextId++
    companion object { private var nextId = 0L }
}

/** Holds the live burst + counter state. Hoisted at the screen level; `LiveTab`
 *  pushes taps in, the root [ReactionBurstOverlay] renders them, and the
 *  [LiveReactionCounter] reads the total. */
private class ReactionsFx(private val reduceMotion: Boolean) {
    val particles = mutableStateListOf<ReactionParticle>()
    var displayTotal by mutableIntStateOf(0)
        private set
    var pulse by mutableIntStateOf(0)
        private set

    private val cap = 60
    private val rng = java.util.Random()

    /** Reconcile with a REAL server total — only ever grows (never downgrade a
     *  higher optimistic value on a stale read). */
    fun sync(total: Int) { if (total > displayTotal) displayTotal = total }

    /** A tap landed: bump the counter and spawn a floating emoji. */
    fun tap(emoji: String) {
        displayTotal += 1
        pulse += 1
        val p = ReactionParticle(
            emoji = emoji,
            bornNanos = System.nanoTime(),
            lifetimeMs = if (reduceMotion) 2200f else 2500f + rng.nextFloat() * 1500f,
            startXFrac = 0.15f + rng.nextFloat() * 0.70f,
            driftPx = if (reduceMotion) 0f else (rng.nextFloat() - 0.5f) * 120f,
            swayPx = if (reduceMotion) 0f else 18f + rng.nextFloat() * 36f,
            swayFreq = 1.4f + rng.nextFloat() * 1.4f,
            phase = rng.nextFloat() * (2f * PI.toFloat()),
            scale = if (reduceMotion) 1f else 0.7f + rng.nextFloat() * 0.9f,
            rotation = if (reduceMotion) 0f else (rng.nextFloat() - 0.5f) * 56f,
        )
        particles.add(p)
        if (particles.size > cap) particles.removeRange(0, particles.size - cap)
    }

    /** Drop particles whose life has fully elapsed (called each frame). */
    fun reap(nowNanos: Long) {
        particles.removeAll { (nowNanos - it.bornNanos) / 1_000_000f >= it.lifetimeMs }
    }
}

/** True when the user has turned system animations off ("Remove animations"). */
@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        val scale = android.provider.Settings.Global.getFloat(
            resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        )
        scale == 0f
    }
}

/** The full-screen burst layer — mounted at the screen root so emoji rise the
 *  entire height, never clipped by the reaction row. One frame loop advances a
 *  shared clock; every particle renders from its own elapsed time. */
@Composable
private fun ReactionBurstOverlay(fx: ReactionsFx, modifier: Modifier = Modifier) {
    // `tick` only forces recomposition each frame; elapsed is measured against
    // System.nanoTime() so it matches the born-stamp taken in fx.tap().
    var tick by remember { mutableLongStateOf(0L) }
    val hasParticles = fx.particles.isNotEmpty()
    // Only run the frame loop while there's something to animate — idle → asleep.
    LaunchedEffect(hasParticles) {
        if (!hasParticles) return@LaunchedEffect
        while (true) {
            withInfiniteAnimationFrameNanos { tick = it }
            fx.reap(System.nanoTime())
            if (fx.particles.isEmpty()) break
        }
    }

    val density = LocalDensity.current
    Canvas(modifier.fillMaxSize()) {
        tick                                                    // read → redraw each frame
        val nowNanos = System.nanoTime()
        // Rise from just above the reaction bar (near the bottom) to the top.
        val bottomY = size.height - with(density) { 140.dp.toPx() }
        val topY = with(density) { 40.dp.toPx() }
        for (p in fx.particles) {
            val elapsed = (nowNanos - p.bornNanos) / 1_000_000f
            val t = (elapsed / p.lifetimeMs).coerceIn(0f, 1f)
            val eased = 1f - (1f - t).pow(1.7f)                 // easeOut rise
            val y = bottomY - eased * (bottomY - topY)
            val baseX = size.width * p.startXFrac
            val x = baseX + p.driftPx * t +
                p.swayPx * sin(p.phase + t * p.swayFreq * 2f * PI.toFloat())
            val appear = (t / 0.12f).coerceAtMost(1f)
            val fade = if (t < 0.55f) 1f else (1f - (t - 0.55f) / 0.45f).coerceAtLeast(0f)
            val alpha = appear * fade
            val scale = p.scale * (0.6f + 0.4f * (t / 0.18f).coerceAtMost(1f))

            drawContext.canvas.nativeCanvas.apply {
                val paint = emojiPaint
                paint.textSize = with(density) { 30.dp.toPx() } * scale
                paint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
                val save = save()
                rotate(p.rotation * t, x, y)
                // Center the glyph horizontally on x, vertically on y.
                val w = paint.measureText(p.emoji)
                drawText(p.emoji, x - w / 2f, y + paint.textSize / 3f, paint)
                restoreToCount(save)
            }
        }
    }
}

/** Reused text paint for the emoji canvas — antialiased, centered by hand. */
private val emojiPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    isSubpixelText = true
}

/** A prominent, animated LIVE reaction counter — a soft-glass gold pill with a
 *  heart, the (abbreviated) total, and a "reactions" caption. Scale-bumps on
 *  every increment (`pulse`). */
@Composable
private fun LiveReactionCounter(total: Int, pulse: Int, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val bump = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse == 0 || reduceMotion) return@LaunchedEffect
        bump.snapTo(1.12f)
        bump.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
    }
    Row(
        modifier
            .graphicsLayer { scaleX = bump.value; scaleY = bump.value }
            .clip(Capsule).background(RADIO.gold.copy(alpha = 0.14f))
            .border(1.dp, RADIO.gold.copy(alpha = 0.38f), Capsule)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("❤️", fontSize = 12.sp)
        Text(abbreviateCount(total), style = gInter(15, FontWeight.Bold), color = Color.White)
        Text("reactions", style = gInter(10, FontWeight.SemiBold, 0.6f), color = Color.White.copy(alpha = 0.5f))
    }
}

/** 999 → "999", 1_200 → "1.2K", 12_300 → "12.3K", 1_200_000 → "1.2M". */
private fun abbreviateCount(n: Int): String = when {
    n < 1_000 -> "$n"
    n < 1_000_000 -> {
        val v = n / 1_000.0
        if (v < 10) "%.1fK".format(v) else "${v.toInt()}K"
    }
    else -> {
        val v = n / 1_000_000.0
        if (v < 10) "%.1fM".format(v) else "${v.toInt()}M"
    }
}

// ── Screen ──────────────────────────────────────────────────────────────────
@Composable
fun LiveRadioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Playback lives in RadioService (a foreground media service) via the shared
    // RadioController — so audio, the notification and lock-screen controls all
    // keep going when this screen closes or the phone locks. This screen never
    // owns a player; it binds once and drives the controller. (iOS parity:
    // RadioCenter.shared.) The `nowTitle`/`nowArtist` fed to tune() label the
    // media notification.
    DisposableEffect(Unit) { RadioController.bind(context); onDispose { } }
    val radioState by RadioController.state.collectAsState()
    val playing = radioState.playing
    val currentUrl = radioState.url
    val muted = radioState.muted
    var nowTitle by remember { mutableStateOf("Nuru Radio") }
    var nowArtist by remember { mutableStateOf("") }

    fun toggle(url: String) = RadioController.toggle(url, nowTitle, nowArtist)

    // Elapsed timer: reset the origin whenever a fresh playback starts, tick every second.
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, currentUrl) {
        if (playing) {
            if (startedAt == 0L) startedAt = System.currentTimeMillis()
            while (true) {
                elapsedSec = (System.currentTimeMillis() - startedAt) / 1000
                kotlinx.coroutines.delay(1000)
            }
        } else {
            startedAt = 0L
            elapsedSec = 0L
        }
    }
    val elapsed = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)

    var tab by remember { mutableStateOf(0) }

    // Full-screen reactions celebration layer — shared between the LiveTab
    // (which pushes taps + reads the counter) and the root burst overlay.
    val reduceMotion = rememberReduceMotion()
    val fx = remember(reduceMotion) { ReactionsFx(reduceMotion) }

    Box(Modifier.fillMaxSize().background(RADIO.navyDeep)) {
        AsyncContent(
            load = {
                val now = runCatching { Net.client.api.radioNowPlaying() }.getOrNull()
                val progs = runCatching { Net.client.api.radioPrograms() }.getOrDefault(emptyList())
                now to progs
            },
        ) { (nowPlaying, programs), _ ->
            val now = nowPlaying ?: programs.firstOrNull { it.live } ?: programs.firstOrNull()

            // Live-listener presence — while we're actually playing a LIVE program,
            // heartbeat every 20s so the studio roster shows this member by name.
            LaunchedEffect(playing, now?.id, now?.live) {
                val id = now?.id
                if (playing && now?.live == true && id != null) {
                    while (true) {
                        runCatching { Net.client.api.radioListening(id) }
                        kotlinx.coroutines.delay(20_000)
                    }
                }
            }

            // ── Backdrop ──────────────────────────────────────────────────────
            val glowT = rememberInfiniteTransition(label = "glow")
            val goldAlpha by glowT.animateFloat(
                initialValue = 0.10f, targetValue = 0.20f,
                animationSpec = infiniteRepeatable(tween(6000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "goldAlpha",
            )
            now?.artworkUrl?.let { art ->
                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().blur(44.dp))
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(RADIO.navyDeep.copy(alpha = 0.55f), RADIO.navyDeep.copy(alpha = 0.82f), RADIO.navyDeep)),
                ),
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.radialGradient(listOf(RADIO.gold.copy(alpha = goldAlpha), Color.Transparent), center = Offset(120f, 320f), radius = 300f),
                ),
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.radialGradient(listOf(RADIO.indigo.copy(alpha = 0.28f), Color.Transparent), center = Offset(900f, 1900f), radius = 320f),
                ),
            )

            // ── Foreground ────────────────────────────────────────────────────
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
                Header(live = now?.live == true, onBack = onBack)

                Column(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Feed the live program's name into the media notification.
                    LaunchedEffect(now?.title, now?.speaker) {
                        nowTitle = now?.title?.takeIf { it.isNotBlank() } ?: "Nuru Radio"
                        nowArtist = now?.speaker.orEmpty()
                    }
                    Centerpiece(now, Modifier.padding(top = 12.dp))
                    Titles(now)
                    ProgressLine(now, programs, elapsed, Modifier.padding(top = 20.dp))
                    TransportRow(now, playing, muted, onMute = { RadioController.toggleMute() }, onPlay = { now?.streamUrl?.let(::toggle) }, Modifier.padding(top = 20.dp))
                    if (now?.live == true) StatChips(now, Modifier.padding(top = 20.dp))
                    SegmentedTabs(tab, onSelect = { tab = it }, Modifier.padding(top = 24.dp))
                    Box(Modifier.padding(top = 16.dp).fillMaxWidth()) {
                        when (tab) {
                            0 -> LiveTab(now, fx, reduceMotion)
                            1 -> RecordingsTab(programs, currentUrl, playing, onToggle = { it?.let(::toggle) })
                            else -> ScheduleTab(programs)
                        }
                    }
                }
            }
        }

        // Above everything — the rising-emoji stream fills the whole screen.
        ReactionBurstOverlay(fx, Modifier.matchParentSize())
    }
}

// ── Header ──────────────────────────────────────────────────────────────────
@Composable
private fun Header(live: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSquare(Icons.AutoMirrored.Filled.ArrowBack, 20.dp) { onBack() }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (live) PulsingDot(RADIO.red, 6.dp)
            Text("NURU RADIO", style = gInter(11, FontWeight.Bold, 3.1f), color = RADIO.gold)
        }
        Spacer(Modifier.weight(1f))
        GlassSquare(Icons.Filled.Notifications, 18.dp) { }
    }
}

@Composable
private fun GlassSquare(icon: ImageVector, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(RADIO.glass)
            .border(1.dp, RADIO.glassBorder, RoundedCornerShape(16.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size)) }
}

// ── Centerpiece ─────────────────────────────────────────────────────────────
@Composable
private fun Centerpiece(now: RadioProgram?, modifier: Modifier = Modifier) {
    Box(modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(5f / 3f)) {
        Box(
            Modifier.matchParentSize().clip(RoundedCornerShape(26.dp))
                .border(2.dp, if (now?.live == true) RADIO.red.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp)),
        ) {
            if (now?.artworkUrl != null) {
                AsyncImage(model = now.artworkUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            } else {
                Box(
                    Modifier.matchParentSize().background(Brush.linearGradient(listOf(RADIO.panelTop, RADIO.navy))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = RADIO.gold.copy(alpha = 0.8f), modifier = Modifier.size(44.dp)) }
            }
            if (now?.live != true) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)))
                Box(
                    Modifier.align(Alignment.TopStart).padding(12.dp).clip(Capsule).background(RADIO.navyDeep.copy(alpha = 0.7f))
                        .border(1.dp, RADIO.glassBorder, Capsule).padding(horizontal = 12.dp, vertical = 4.dp),
                ) { Text("OFF AIR", style = gInter(10, FontWeight.Bold, 2.2f), color = Color.White) }
            }
        }
    }
}

// ── Titles ──────────────────────────────────────────────────────────────────
@Composable
private fun Titles(now: RadioProgram?) {
    val kicker = when {
        now?.recorded == true -> "RECORDING"
        now?.category?.isNotBlank() == true -> now.category.uppercase()
        else -> "NURU PLACE RADIO"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(kicker, style = gInter(9, FontWeight.Bold, 2.0f), color = RADIO.goldLight, modifier = Modifier.padding(top = 24.dp))
        Text(
            now?.title ?: "We're off air right now",
            style = gSerif(24, FontWeight.SemiBold, -0.48f), color = Color.White,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
        now?.speaker?.let {
            Text(it, style = gInter(13, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ── Progress line ───────────────────────────────────────────────────────────
@Composable
private fun ProgressLine(now: RadioProgram?, programs: List<RadioProgram>, elapsed: String, modifier: Modifier = Modifier) {
    if (now?.live == true) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(RADIO.redDeep))
            Text("LIVE", style = gInter(10, FontWeight.Bold, 1.4f), color = RADIO.redSoft)
            LiveWaveBar(Modifier.weight(1f))
            Text(elapsed, style = gInter(10), color = Color.White.copy(alpha = 0.55f))
        }
    } else {
        val nextUp = programs.firstOrNull { it.status == "scheduled" }?.title ?: "—"
        Text("Next up · $nextUp", style = gInter(12), color = Color.White.copy(alpha = 0.6f), modifier = modifier)
    }
}

/** Subtle live wave: thin gold bars breathing on layered sines — a radio
 *  signal, not a loading bar. Bright mid-height at the center, fading and
 *  shortening toward the edges; slow drift so it soothes instead of flickers. */
@Composable
private fun LiveWaveBar(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "liveWave")
    val phase by t.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    Canvas(modifier.height(18.dp)) {
        val n = 27
        val slot = size.width / n
        val barW = (slot * 0.42f).coerceAtMost(3.dp.toPx())
        val mid = size.height / 2f
        for (i in 0 until n) {
            val x = slot * i + slot / 2f
            val envelope = sin(PI.toFloat() * i / (n - 1))          // taller + brighter mid-line
            val a = 0.34f + 0.26f * sin(phase + i * 0.9f) +
                    0.18f * sin(phase * 1.7f + i * 0.47f + 1.3f)
            val h = (size.height * a.coerceIn(0.12f, 0.85f) * (0.45f + 0.55f * envelope))
                .coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = RADIO.gold.copy(alpha = 0.35f + 0.55f * envelope),
                topLeft = Offset(x - barW / 2f, mid - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

// ── Transport row ───────────────────────────────────────────────────────────
@Composable
private fun TransportRow(
    now: RadioProgram?,
    playing: Boolean,
    muted: Boolean,
    onMute: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (now?.live == true || now?.streamUrl != null) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            GlassCircle(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, active = !muted) { onMute() }
            PlayButton(playing, enabled = now?.streamUrl != null, onClick = onPlay)
            GlassCircle(Icons.Filled.Bedtime, active = false) { }
        }
    } else {
        Box(
            modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RADIO.goldGrad).clickable { }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Notifications, contentDescription = null, tint = RADIO.textOnGold, modifier = Modifier.size(15.dp))
                Text("Remind me", style = gInter(14, FontWeight.Bold), color = RADIO.textOnGold)
            }
        }
    }
}

@Composable
private fun PlayButton(playing: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    Box(
        Modifier.size(78.dp).clip(CircleShape).background(RADIO.goldGrad).clickable(enabled = enabled) {
            org.nuruplace.member.ui.components.Haptics.tap(view)
            onClick()
        },
        contentAlignment = Alignment.Center,
    ) {
        if (playing) {
            val t = rememberInfiniteTransition(label = "ring")
            val scale by t.animateFloat(
                initialValue = 1f, targetValue = 1.35f,
                animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
                label = "ringScale",
            )
            val ringAlpha by t.animateFloat(
                initialValue = 0.5f, targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
                label = "ringAlpha",
            )
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale; alpha = ringAlpha }
                    .border(2.dp, RADIO.gold, CircleShape),
            )
        }
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null, tint = RADIO.textOnGold, modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun GlassCircle(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .then(
                if (active) Modifier.background(RADIO.gold.copy(alpha = 0.165f)).border(1.dp, RADIO.gold.copy(alpha = 0.4f), CircleShape)
                else Modifier.background(RADIO.glass).border(1.dp, RADIO.glassBorder, CircleShape),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = if (active) RADIO.goldLight else Color.White, modifier = Modifier.size(18.dp)) }
}

// ── Stat chips ──────────────────────────────────────────────────────────────
@Composable
private fun StatChips(now: RadioProgram?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip(Icons.Filled.Headphones, (now?.peakListeners ?: 0).toString(), "LISTENING")
        StatChip(Icons.Filled.Schedule, "LIVE", "STARTED")
        StatChip(Icons.Filled.Public, "🌍", "REACH")
    }
}

@Composable
private fun StatChip(icon: ImageVector, value: String, label: String) {
    Row(
        Modifier.clip(Capsule).background(RADIO.glass).border(1.dp, RADIO.glassBorder, Capsule).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = RADIO.goldLight, modifier = Modifier.size(12.dp))
        Text(value, style = gInter(11, FontWeight.Bold), color = Color.White)
        Text(label, style = gInter(9, FontWeight.SemiBold, 0.9f), color = Color.White.copy(alpha = 0.45f))
    }
}

// ── Segmented tabs ──────────────────────────────────────────────────────────
@Composable
private fun SegmentedTabs(tab: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Capsule).background(RADIO.glass).border(1.dp, RADIO.glassBorder, Capsule).padding(4.dp),
    ) {
        listOf("Live", "Recordings", "Schedule").forEachIndexed { i, label ->
            val on = tab == i
            Box(
                Modifier.weight(1f).clip(Capsule)
                    .then(if (on) Modifier.background(RADIO.goldGrad) else Modifier)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = gInter(11, if (on) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (on) RADIO.textOnGold else Color.White.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ── Tab: Live ───────────────────────────────────────────────────────────────
@Composable
private fun LiveTab(now: RadioProgram?, fx: ReactionsFx, reduceMotion: Boolean) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var counts by remember(now?.id) { mutableStateOf(RadioReactionCounts()) }
    var comments by remember(now?.id) { mutableStateOf<List<RadioComment>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // Reconcile the growing counter with the REAL server total whenever a
    // POST /react response updates the counts (only ever grows).
    LaunchedEffect(fx) {
        snapshotFlow { counts.heart + counts.amen + counts.fire }
            .collectLatest { fx.sync(it) }
    }

    // Initial load + gentle 5s poll of the live comments feed (parity with iOS).
    // Reaction counts have no GET — they arrive only on each react response.
    LaunchedEffect(now?.id) {
        val id = now?.id ?: return@LaunchedEffect
        while (true) {
            runCatching { comments = Net.client.api.radioComments(id) }
            kotlinx.coroutines.delay(5000)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Triple("❤️", RADIO.redDeep, "heart"),
                Triple("🙏", RADIO.gold, "amen"),
                Triple("🙌", RADIO.indigoSoft, "fire"),
            ).forEach { (emoji, tint, kind) ->
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(tint.copy(alpha = 0.165f)).border(1.dp, tint.copy(alpha = 0.4f), CircleShape)
                        .clickable(enabled = now != null) {
                            val id = now?.id ?: return@clickable
                            // Celebrate instantly: haptic + optimistic counter bump
                            // + a floating emoji, then fire the real server react.
                            org.nuruplace.member.ui.components.Haptics.tap(view)
                            fx.tap(emoji)
                            scope.launch {
                                runCatching {
                                    counts = Net.client.api.radioReact(id, RadioReactBody(kind, java.util.UUID.randomUUID().toString())).counts
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 20.sp) }
            }
        }
        LiveReactionCounter(
            total = fx.displayTotal, pulse = fx.pulse, reduceMotion = reduceMotion,
            modifier = Modifier.padding(top = 12.dp),
        )

        if (now != null) {
            // Comments feed.
            if (comments.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    comments.forEach { c -> CommentRow(c) }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            // Composer.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.weight(1f).clip(Capsule).background(RADIO.glass).border(1.dp, RADIO.glassBorder, Capsule)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = gInter(13).copy(color = Color.White),
                        cursorBrush = SolidColor(RADIO.gold),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isBlank()) {
                                    Text("Say amen…", style = gInter(13), color = Color.White.copy(alpha = 0.4f))
                                }
                                inner()
                            }
                        },
                    )
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape)
                        .then(
                            if (draft.isNotBlank()) Modifier.background(RADIO.goldGrad)
                            else Modifier.background(Color.White.copy(alpha = 0.12f)),
                        )
                        .clickable(enabled = draft.isNotBlank() && !busy) {
                            val id = now.id
                            scope.launch {
                                busy = true
                                runCatching {
                                    Net.client.api.addRadioComment(id, RadioCommentBody(draft.trim(), java.util.UUID.randomUUID().toString()))
                                }.onSuccess {
                                    draft = ""
                                    runCatching { comments = Net.client.api.radioComments(id) }
                                }
                                busy = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, contentDescription = null,
                        tint = if (draft.isNotBlank()) RADIO.textOnGold else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        now?.description?.let {
            Text(
                it,
                style = gInter(13).copy(lineHeight = 19.sp), color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun CommentRow(c: RadioComment) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RADIO.glass)
            .border(1.dp, RADIO.glassBorder, RoundedCornerShape(16.dp)).padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(RADIO.goldGrad), contentAlignment = Alignment.Center) {
            if (c.authorAvatarUrl != null) {
                AsyncImage(
                    model = c.authorAvatarUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(CircleShape),
                )
            } else {
                Text(initials(c.authorName ?: ""), style = gInter(9, FontWeight.Bold), color = Color.White)
            }
        }
        Column {
            Text(c.authorName ?: "Member", style = gInter(12, FontWeight.Bold), color = Color.White)
            Text(c.body, style = gInter(12).copy(lineHeight = 17.sp), color = Color.White.copy(alpha = 0.7f))
        }
    }
}

/** First letters of up to two words, uppercased; "?" when there's nothing usable. */
private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }.joinToString("")
}

// ── Tab: Recordings ─────────────────────────────────────────────────────────
@Composable
private fun RecordingsTab(programs: List<RadioProgram>, currentUrl: String?, playing: Boolean, onToggle: (String?) -> Unit) {
    val recs = programs.filter { it.recorded }
    if (recs.isEmpty()) {
        Text("No recordings yet.", style = gInter(13), color = Color.White.copy(alpha = 0.5f))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recs.forEach { p -> RecordingRow(p, tuned = currentUrl == p.streamUrl && playing, onToggle = { onToggle(p.streamUrl) }) }
        }
    }
}

@Composable
private fun RecordingRow(p: RadioProgram, tuned: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .then(
                if (tuned) Modifier.background(RADIO.gold.copy(alpha = 0.12f)).border(1.dp, RADIO.gold.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                else Modifier.background(RADIO.glass).border(1.dp, RADIO.glassBorder, RoundedCornerShape(18.dp)),
            )
            .clickable { onToggle() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(RADIO.panelTop, RADIO.navy))),
            contentAlignment = Alignment.Center,
        ) {
            if (p.artworkUrl != null) {
                AsyncImage(model = p.artworkUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)))
                Box(Modifier.matchParentSize().background(RADIO.navyDeep.copy(alpha = 0.4f)))
            }
            Box(Modifier.size(32.dp).clip(CircleShape).background(RADIO.goldGrad), contentAlignment = Alignment.Center) {
                Icon(if (tuned) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = RADIO.textOnGold, modifier = Modifier.size(13.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            if (p.category.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(Capsule).background(RADIO.gold.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(p.category.uppercase(), style = gInter(8, FontWeight.Bold, 0.8f), color = Color.White)
                    }
                }
            }
            Text(p.title, style = gInter(13, FontWeight.Bold), color = Color.White, modifier = Modifier.padding(top = 2.dp))
            p.speaker?.let { Text(it, style = gInter(10), color = Color.White.copy(alpha = 0.55f)) }
        }
    }
}

// ── Tab: Schedule ───────────────────────────────────────────────────────────
@Composable
private fun ScheduleTab(programs: List<RadioProgram>) {
    val sched = programs.sortedBy { it.scheduledAt ?: "" }
    if (sched.isEmpty()) {
        Text("Nothing scheduled.", style = gInter(13), color = Color.White.copy(alpha = 0.5f))
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sched.forEach { p -> ScheduleRow(p) }
        }
    }
}

@Composable
private fun ScheduleRow(p: RadioProgram) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(RADIO.glass)
            .border(1.dp, if (p.live) RADIO.gold.copy(alpha = 0.53f) else RADIO.glassBorder, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(48.dp)) {
            Text(schedTime(p.scheduledAt), style = gInter(13, FontWeight.Bold, -0.26f), color = Color.White)
        }
        Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.12f)))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = gInter(13, FontWeight.Bold), color = Color.White)
            Text(
                listOfNotNull(p.speaker, p.category.ifBlank { null }).joinToString(" · "),
                style = gInter(10), color = Color.White.copy(alpha = 0.5f),
            )
        }
        if (p.live) {
            Box(Modifier.clip(Capsule).background(RADIO.redDeep).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White))
                    Text("LIVE", style = gInter(8, FontWeight.Bold, 0.8f), color = Color.White)
                }
            }
        } else {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(13.dp))
        }
    }
}

// ── Animations & helpers ────────────────────────────────────────────────────
@Composable
private fun PulsingDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val alpha by t.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

/** Parse an ISO instant → "h:mm a" in Africa/Nairobi; blank on failure. */
private fun schedTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val zone = ZoneId.of("Africa/Nairobi")
    val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    return runCatching { Instant.parse(iso).atZone(zone).format(fmt) }
        .recoverCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(fmt) }
        .getOrDefault("")
}
