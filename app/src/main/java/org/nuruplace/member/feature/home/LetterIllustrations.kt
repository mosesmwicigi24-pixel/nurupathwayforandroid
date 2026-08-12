// Sunday Letter v2 — the themed hero illustration.
//
// Drawn procedurally with Compose Canvas rather than shipped as drawables,
// mirroring iOS's `LetterIllustrations.swift` (PR #111) EXACTLY: same ten
// themes, same hex palette, same motif vocabulary. The owner requires the two
// apps to look the same, and a shared palette in code is the only way that
// survives — two sets of hand-authored assets drift the moment either side is
// touched.
//
// Like iOS, this deliberately does NOT follow the system light/dark setting.
// The letter is stationery: a fixed navy field with a warm motif, so it reads
// the same in a dark bedroom and bright sunlight, and so the overlaid title
// (always warm white) is legible without a second palette to maintain.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The fixed vocabulary the backend may send (LETTER_THEMES in prompts.ts).
 *  `LIGHT` is the documented default and our fallback. */
enum class LetterTheme(val key: String) {
    DAWN("dawn"), WATER("water"), PATH("path"), HARVEST("harvest"), SHELTER("shelter"),
    LIGHT("light"), SEED("seed"), GARDEN("garden"), MOUNTAIN("mountain"), REST("rest");

    companion object {
        val FALLBACK = LIGHT

        /** TOTAL by construction: an unknown, empty or null key from the
         *  server can never render a blank hero. Case-insensitive because a
         *  theme is a wire string, not a Kotlin identifier. */
        fun resolve(raw: String?): LetterTheme {
            val k = raw?.trim()?.lowercase().orEmpty()
            if (k.isEmpty()) return FALLBACK
            return entries.firstOrNull { it.key == k } ?: FALLBACK
        }
    }
}

private enum class LetterMotif {
    HORIZON_GLOW, WAVES, CONVERGING_LINES, WHEAT_STROKES, ARCH_ROOF,
    SUNBURST, SEED_RINGS, LEAF_CURVES, RIDGE_LAYERS, REST_BANDS,
}

/** base/base2 = the shared navy field, tuned warmer or cooler per theme.
 *  accent/accent2 = the motif's own colours. Hex values are copied verbatim
 *  from iOS so the two apps match pixel-for-pixel in tone. */
private data class LetterArt(
    val base: Color,
    val base2: Color,
    val accent: Color,
    val accent2: Color,
    val motif: LetterMotif,
)

private fun hex(v: Long) = Color(0xFF000000L or v)

private val LetterTheme.art: LetterArt
    get() = when (this) {
        LetterTheme.DAWN -> LetterArt(hex(0x152238), hex(0x0A1628), hex(0xE8CA6C), hex(0xB6862F), LetterMotif.HORIZON_GLOW)
        LetterTheme.WATER -> LetterArt(hex(0x0E2438), hex(0x081828), hex(0x8FC4D9), hex(0x3A7590), LetterMotif.WAVES)
        LetterTheme.PATH -> LetterArt(hex(0x101F30), hex(0x0A1628), hex(0xC9A227), hex(0x3D5C7A), LetterMotif.CONVERGING_LINES)
        LetterTheme.HARVEST -> LetterArt(hex(0x1C1A28), hex(0x11101C), hex(0xE0B85E), hex(0x8A6B1F), LetterMotif.WHEAT_STROKES)
        LetterTheme.SHELTER -> LetterArt(hex(0x0F2038), hex(0x081020), hex(0xC9A227), hex(0x2C4A66), LetterMotif.ARCH_ROOF)
        LetterTheme.LIGHT -> LetterArt(hex(0x18213A), hex(0x0A1628), hex(0xE6CA68), hex(0xA8861C), LetterMotif.SUNBURST)
        LetterTheme.SEED -> LetterArt(hex(0x11241E), hex(0x0A1628), hex(0x9BCB86), hex(0x3F6B33), LetterMotif.SEED_RINGS)
        LetterTheme.GARDEN -> LetterArt(hex(0x13271F), hex(0x0A1E16), hex(0x9BCB86), hex(0xC9A227), LetterMotif.LEAF_CURVES)
        LetterTheme.MOUNTAIN -> LetterArt(hex(0x161F2E), hex(0x0A1420), hex(0x8FA6BF), hex(0x2C4258), LetterMotif.RIDGE_LAYERS)
        LetterTheme.REST -> LetterArt(hex(0x0F1B2E), hex(0x081020), hex(0xB9C4D4), hex(0x2C3B52), LetterMotif.REST_BANDS)
    }

/** The theme's signature accent — for lightweight secondary UI (e.g. an
 *  archive row's swatch) that wants the family colour without the whole hero.
 *  Mirrors iOS's `LetterTheme.accentColor`. */
val LetterTheme.accentColor: Color get() = art.accent

/**
 * The letter's hero: a themed backdrop with room for a title overlaid on top.
 * This draws only the picture and the legibility scrim — the caller supplies
 * the text, exactly as iOS's `LetterHero` does.
 *
 * @param imageKey the raw `image_key` (or `theme`) straight off the wire;
 *   resolution, including the unknown-theme fallback, happens inside.
 */
@Composable
fun LetterHero(
    imageKey: String?,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
) {
    val art = LetterTheme.resolve(imageKey).art
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            // Decorative: the title beside it already carries the meaning,
            // so the hero contributes nothing to the accessibility tree.
            .clearAndSetSemantics {},
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(art.base, art.base2)))
            drawMotif(art)
            // Bottom scrim so the overlaid warm-white title stays legible over
            // any motif — the hero never follows system appearance, so this is
            // a fixed guarantee rather than a per-theme judgement call.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f),
                ),
            )
        }
    }
}

private fun DrawScope.drawMotif(art: LetterArt) {
    val w = size.width
    val h = size.height
    when (art.motif) {
        LetterMotif.HORIZON_GLOW -> {
            drawCircle(
                Brush.radialGradient(
                    listOf(art.accent.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.72f),
                    radius = w * 0.55f,
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.5f, h * 0.72f),
            )
            drawLine(art.accent2.copy(alpha = 0.7f), Offset(0f, h * 0.72f), Offset(w, h * 0.72f), strokeWidth = 1.5f)
        }
        LetterMotif.WAVES -> repeat(4) { i ->
            val y = h * (0.45f + i * 0.13f)
            val amp = h * 0.05f * (1f - i * 0.15f)
            drawPath(wave(y, amp, w), art.accent.copy(alpha = 0.42f - i * 0.07f), style = Stroke(width = 2f))
        }
        LetterMotif.CONVERGING_LINES -> {
            val vanish = Offset(w * 0.5f, h * 0.34f)
            listOf(0.10f, 0.30f, 0.70f, 0.90f).forEachIndexed { i, x ->
                drawLine(
                    art.accent.copy(alpha = if (i == 1 || i == 2) 0.5f else 0.28f),
                    Offset(w * x, h), vanish, strokeWidth = 2f,
                )
            }
            drawLine(art.accent2.copy(alpha = 0.55f), Offset(0f, h * 0.34f), Offset(w, h * 0.34f), strokeWidth = 1f)
        }
        LetterMotif.WHEAT_STROKES -> repeat(7) { i ->
            val x = w * (0.16f + i * 0.115f)
            val top = h * (0.34f + (i % 3) * 0.05f)
            drawLine(art.accent.copy(alpha = 0.45f), Offset(x, h * 0.92f), Offset(x, top), strokeWidth = 2f)
            drawLine(art.accent2.copy(alpha = 0.5f), Offset(x, top), Offset(x - w * 0.03f, top + h * 0.07f), strokeWidth = 1.5f)
            drawLine(art.accent2.copy(alpha = 0.5f), Offset(x, top), Offset(x + w * 0.03f, top + h * 0.07f), strokeWidth = 1.5f)
        }
        LetterMotif.ARCH_ROOF -> {
            val p = Path().apply {
                moveTo(w * 0.18f, h * 0.86f)
                lineTo(w * 0.5f, h * 0.36f)
                lineTo(w * 0.82f, h * 0.86f)
            }
            drawPath(p, art.accent.copy(alpha = 0.5f), style = Stroke(width = 2.5f))
            drawCircle(
                Brush.radialGradient(
                    listOf(art.accent.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.70f), radius = w * 0.22f,
                ),
                radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.70f),
            )
        }
        LetterMotif.SUNBURST -> {
            val c = Offset(w * 0.5f, h * 0.52f)
            repeat(12) { i ->
                val a = (Math.PI * 2 / 12 * i).toFloat()
                val r0 = w * 0.10f
                val r1 = w * (if (i % 2 == 0) 0.30f else 0.22f)
                drawLine(
                    art.accent2.copy(alpha = 0.38f),
                    Offset(c.x + r0 * kotlin.math.cos(a), c.y + r0 * kotlin.math.sin(a)),
                    Offset(c.x + r1 * kotlin.math.cos(a), c.y + r1 * kotlin.math.sin(a)),
                    strokeWidth = 2f,
                )
            }
            drawCircle(
                Brush.radialGradient(listOf(art.accent.copy(alpha = 0.65f), Color.Transparent), center = c, radius = w * 0.16f),
                radius = w * 0.16f, center = c,
            )
        }
        LetterMotif.SEED_RINGS -> {
            val c = Offset(w * 0.5f, h * 0.60f)
            listOf(0.10f, 0.18f, 0.26f).forEachIndexed { i, r ->
                drawCircle(art.accent.copy(alpha = 0.34f - i * 0.09f), radius = w * r, center = c, style = Stroke(width = 2f))
            }
            drawLine(art.accent2.copy(alpha = 0.6f), Offset(c.x, c.y), Offset(c.x, c.y - h * 0.20f), strokeWidth = 2f)
        }
        LetterMotif.LEAF_CURVES -> repeat(3) { i ->
            val baseX = w * (0.28f + i * 0.22f)
            val p = Path().apply {
                moveTo(baseX, h * 0.88f)
                quadraticTo(baseX - w * 0.10f, h * 0.58f, baseX, h * 0.36f)
                quadraticTo(baseX + w * 0.10f, h * 0.58f, baseX, h * 0.88f)
            }
            drawPath(p, (if (i == 1) art.accent2 else art.accent).copy(alpha = 0.40f), style = Stroke(width = 2f))
        }
        LetterMotif.RIDGE_LAYERS -> listOf(0.62f to 0.45f, 0.72f to 0.32f, 0.82f to 0.22f)
            .forEachIndexed { i, (yf, alpha) ->
                val p = Path().apply {
                    moveTo(0f, h * yf)
                    lineTo(w * (0.28f + i * 0.08f), h * (yf - 0.18f))
                    lineTo(w * (0.55f + i * 0.06f), h * yf)
                    lineTo(w * (0.78f - i * 0.05f), h * (yf - 0.12f))
                    lineTo(w, h * yf)
                }
                drawPath(p, (if (i == 2) art.accent else art.accent2).copy(alpha = alpha), style = Stroke(width = 2f))
            }
        LetterMotif.REST_BANDS -> {
            drawCircle(
                Brush.radialGradient(
                    listOf(art.accent.copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.44f), radius = w * 0.20f,
                ),
                radius = w * 0.20f, center = Offset(w * 0.5f, h * 0.44f),
            )
            repeat(3) { i ->
                val y = h * (0.68f + i * 0.09f)
                drawLine(art.accent2.copy(alpha = 0.45f - i * 0.10f), Offset(w * 0.12f, y), Offset(w * 0.88f, y), strokeWidth = 2f)
            }
        }
    }
}

private fun DrawScope.wave(y: Float, amp: Float, w: Float): Path = Path().apply {
    moveTo(0f, y)
    val seg = w / 4f
    var x = 0f
    var up = true
    while (x < w) {
        quadraticTo(x + seg / 2f, if (up) y - amp else y + amp, x + seg, y)
        x += seg
        up = !up
    }
}

/** Kept so a future variant rotation has an obvious home, and so callers can
 *  size the hero consistently across the letter and the archive. */
object LetterHeroDefaults {
    val Height: Dp = 220.dp
    val ArchiveRowHeight: Dp = 84.dp
    @Suppress("unused") val unusedSizeGuard: Size = Size.Unspecified
}
