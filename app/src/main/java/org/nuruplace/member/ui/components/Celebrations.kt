// Human moments — confetti + warm pop-up celebrations fired by REAL server-truth
// milestones (rhythm complete, streak milestones, new badges, gifts, wall posts).
// Screens call CelebrationCenter.fire(...) with a stable key; a SharedPreferences
// set remembers fired keys so each moment celebrates exactly once. CelebrationHost
// is mounted ONCE at the shell level (MainShell) as the topmost overlay.
package org.nuruplace.member.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import kotlin.math.sin
import kotlin.random.Random

/** One celebration. `key` is the once-only memory key (embed the date for daily
 *  moments, e.g. "rhythm-2026-07-04"). `confetti=false` renders as a quiet
 *  auto-dismissing top banner instead of the full-screen card. */
data class Moment(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val confetti: Boolean = true,
)

/**
 * Process-wide celebration queue. Screens `fire(...)` moments as server truth
 * arrives; the host shows one at a time and auto-advances. Fired keys persist
 * in plain SharedPreferences (non-sensitive, same idiom as AppPrefs) so a
 * milestone never celebrates twice — even across restarts.
 */
object CelebrationCenter {
    private const val FILE = "nuru_celebrations"
    private const val KEY_FIRED = "fired-keys"

    private var prefs: SharedPreferences? = null
    private val queue = ArrayDeque<Moment>()
    private val _current = MutableStateFlow<Moment?>(null)

    /** The moment currently on screen (null = nothing showing). */
    val current: StateFlow<Moment?> = _current.asStateFlow()

    /** Idempotent; called from NuruApp.onCreate (and defensively by the host). */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    /** Queue a moment. No-op if this key has ever fired before. */
    fun fire(moment: Moment) {
        if (!seenOnce(KEY_FIRED, moment.key)) return
        synchronized(queue) {
            if (_current.value == null) _current.value = moment else queue.addLast(moment)
        }
    }

    /**
     * Once-only memory helper: returns true exactly once per (setKey, id) and
     * records the id under setKey. Triggers use it to diff server lists (e.g.
     * "seen-badges") without re-celebrating on every reload.
     */
    fun seenOnce(setKey: String, id: String): Boolean {
        val p = prefs ?: return true // pre-init safety net; NuruApp inits at launch
        val set = p.getStringSet(setKey, emptySet()) ?: emptySet()
        if (id in set) return false
        p.edit().putStringSet(setKey, set + id).apply()
        return true
    }

    /** Dismiss the showing moment and advance to the next queued one. */
    fun dismiss() {
        synchronized(queue) { _current.value = queue.removeFirstOrNull() }
    }
}

/** Mount ONCE at the shell level, above the NavHost. Renders nothing when idle. */
@Composable
fun CelebrationHost() {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { CelebrationCenter.init(context) }
    val moment by CelebrationCenter.current.collectAsState()
    val m = moment ?: return
    if (m.confetti) CelebrationCard(m) else CelebrationBanner(m)
}

// ── Full-screen confetti + warm card ──────────────────────────────────────────

@Composable
private fun CelebrationCard(m: Moment) {
    val scale = remember(m.key) { Animatable(0.85f) }
    LaunchedEffect(m.key) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Box(
        Modifier.fillMaxSize()
            .background(Nuru.navyDeep.copy(alpha = 0.45f))
            // Consume taps so the screen underneath can't be poked mid-ceremony.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        ConfettiRain(m.key, Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .clip(RoundedCornerShape(Radii.card))
                .background(Nuru.white)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(m.title, style = NuruType.title, color = Nuru.navy, textAlign = TextAlign.Center)
            m.subtitle?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = NuruType.body, color = Nuru.ink600, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(Spacing.screen))
            Row(
                Modifier.fillMaxWidth().height(Spacing.buttonMd)
                    .clip(RoundedCornerShape(Radii.button))
                    .background(Nuru.gold)
                    .clickable { CelebrationCenter.dismiss() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Amen 🙌", style = NuruType.cardCta, color = Nuru.navyDeep)
            }
        }
    }
}

/** One confetti rectangle's fixed genes; motion is derived from shared progress. */
private data class Confetto(
    val x: Float,       // 0..1 start across the width
    val delay: Float,   // 0..0.35 fraction of the run before it drops
    val speed: Float,   // fall-distance multiplier
    val drift: Float,   // horizontal sway amplitude (dp)
    val phase: Float,   // sway phase offset
    val spin: Float,    // total rotation over the fall (degrees)
    val w: Float,       // dp
    val h: Float,       // dp
    val color: Color,
)

/** ~60 gold/navy/green/pink rectangles falling for 2.2s — pure Compose Canvas. */
@Composable
private fun ConfettiRain(key: String, modifier: Modifier = Modifier) {
    val palette = listOf(Nuru.gold, Nuru.goldHi, Nuru.navy, Nuru.navyMid, Nuru.success, Color(0xFFEC4899))
    val pieces = remember(key) {
        List(60) {
            Confetto(
                x = Random.nextFloat(),
                delay = Random.nextFloat() * 0.35f,
                speed = 0.8f + Random.nextFloat() * 0.7f,
                drift = 6f + Random.nextFloat() * 14f,
                phase = Random.nextFloat() * 6.2832f,
                spin = (if (Random.nextBoolean()) 1 else -1) * (360f + Random.nextFloat() * 720f),
                w = 3f + Random.nextFloat() * 4f,
                h = 6f + Random.nextFloat() * 5f,
                color = palette[Random.nextInt(palette.size)],
            )
        }
    }
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.snapTo(0f); t.animateTo(1f, tween(2200, easing = LinearEasing)) }
    Canvas(modifier) {
        val progress = t.value
        pieces.forEach { p ->
            val local = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val y = local * p.speed * (size.height + 80f * density) - 40f * density
            val x = p.x * size.width + sin(p.phase + local * 5f) * p.drift * density
            rotate(p.spin * local, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - p.w * density / 2f, y - p.h * density / 2f),
                    size = Size(p.w * density, p.h * density),
                    alpha = 1f - local * 0.35f,
                )
            }
        }
    }
}

// ── Quiet gold banner (confetti = false) — auto-dismisses after 3s ───────────

@Composable
private fun CelebrationBanner(m: Moment) {
    LaunchedEffect(m.key) { delay(3000); CelebrationCenter.dismiss() }
    // Plain Box (no background, no clickable) — touches pass through around it.
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = Spacing.md, start = Spacing.base, end = Spacing.base)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Nuru.goldChipBg)
                .border(1.dp, Nuru.gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable { CelebrationCenter.dismiss() }
                .padding(horizontal = Spacing.base, vertical = Spacing.md),
        ) {
            Text(m.title, style = NuruType.rowTitle, color = Nuru.goldChipText)
            m.subtitle?.let {
                Text(it, style = NuruType.caption, color = Nuru.ink600, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
