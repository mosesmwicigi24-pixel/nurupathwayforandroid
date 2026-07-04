// Reusable voice-note waveform composables. WaveformBars renders a shared
// message's stored waveform (3dp rounded bars, heights 4..maxBarHeight scaled
// by level 0..100) with a played-fraction highlight for playback progress;
// LiveWave shows the most recent samples animating right-aligned while a
// recording is in progress. Both are palette-agnostic — callers pass colors
// from CHAT / GrowPal.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A row of rounded bars for a stored waveform. Levels are 0..100; the list is
 * bucket-averaged down to [maxBars] so long waveforms still fit a bubble.
 * Bars before [playedFraction] render in [playedColor] (playback progress).
 * An empty [levels] falls back to a gentle 24-bar placeholder.
 */
@Composable
fun WaveformBars(
    levels: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    playedFraction: Float = 0f,
    playedColor: Color = color,
    maxBarHeight: Dp = 24.dp,
    maxBars: Int = 32,
) {
    val bars = downsampleWave(levels.ifEmpty { placeholderWave() }, maxBars)
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        bars.forEachIndexed { i, lvl ->
            val h = 4.dp + (maxBarHeight - 4.dp) * (lvl.coerceIn(0, 100) / 100f)
            val played = playedFraction > 0f && i < playedFraction * bars.size
            Box(
                Modifier.width(3.dp).height(h).clip(RoundedCornerShape(2.dp))
                    .background(if (played) playedColor else color),
            )
        }
    }
}

/**
 * The live recording wave — a fixed [window] of slots filled right-to-left by
 * the most recent samples, so the wave appears to scroll as you speak.
 */
@Composable
fun LiveWave(
    levels: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    window: Int = 40,
    maxBarHeight: Dp = 28.dp,
) {
    val recent = levels.takeLast(window)
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(window - recent.size) {
            Box(
                Modifier.width(3.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.18f)),
            )
        }
        recent.forEach { lvl ->
            val h = 4.dp + (maxBarHeight - 4.dp) * (lvl.coerceIn(0, 100) / 100f)
            Box(
                Modifier.width(3.dp).height(h).clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** m:ss for voice-note durations. */
fun voiceClock(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/** Deterministic mid-height bars for messages that shipped without a waveform. */
fun placeholderWave(n: Int = 24): List<Int> =
    List(n) { i -> (45 + 22 * sin(i * 0.85)).roundToInt().coerceIn(0, 100) }

/** Bucket-average [src] down to at most [bars] entries (display-side sibling
 *  of VoiceRecorder.waveformFor — kept separate so util/ stays UI-free). */
private fun downsampleWave(src: List<Int>, bars: Int): List<Int> {
    if (src.size <= bars) return src
    return List(bars) { i ->
        val from = i * src.size / bars
        val to = ((i + 1) * src.size / bars).coerceAtLeast(from + 1)
        src.subList(from, to).average().roundToInt().coerceIn(0, 100)
    }
}
