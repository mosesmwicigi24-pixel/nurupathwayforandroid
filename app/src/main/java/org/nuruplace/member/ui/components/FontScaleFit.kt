// Text at the phone's largest text size (EXPERIENCE.md §9.6 #4: text grows
// with the phone's text size, without cutting). Two tools, both working on the
// density's font scale, so every size written in code stays on §8.1 rule 3's
// scale (TypeScaleSourceTest):
//
//  · CappedFontScale — chrome with a fixed size keeps its type at an everyday
//    size: the tab bar of six, a figure inside a fixed ring, a mark in a fixed
//    slot, the Give | Partners switch. The screen's own words carry the same
//    facts, and they grow. (iOS .nuruBarText / .nuruFixedFigure, 84d2acb.)
//  · WholeWordsText — a title or label never breaks a word: where its widest
//    word is wider than its line, it steps its own scale down only until that
//    word fits, and still wraps between words; a text that fits keeps the
//    member's size. (iOS .nuruWholeWords, 30b8b15.)
package org.nuruplace.member.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density

/** The everyday ceiling for chrome that can take a little growth — Android's
 *  "large" text step. */
const val EVERYDAY_MAX_FONT_SCALE = 1.3f

/** The smallest scale a whole-words text steps down to — Android's smallest
 *  text step; below it a word may break rather than become unreadable. */
const val WHOLE_WORDS_FLOOR = 0.85f

/** Whether the phone is past the everyday text sizes — layouts that hold the
 *  owner's exact shape at everyday sizes may grow beyond it. */
@Composable
fun largeText(): Boolean = LocalDensity.current.fontScale > EVERYDAY_MAX_FONT_SCALE

/** [content] at most at [max] font scale; untouched below it. */
@Composable
fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (d.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(d.density, max), content = content)
    }
}

/**
 * The font scale at which a text's widest word fits its line: [current] when
 * it already fits (or nothing is known); else [current] stepped down in
 * proportion — text widths scale linearly with the font scale under the
 * app's density — with a 2 % margin for rounding, never below [floor] (nor
 * above [current]). Pure, so FontScaleFitTest pins it.
 */
fun wholeWordsScale(current: Float, widestWordPx: Float, availablePx: Float, floor: Float = WHOLE_WORDS_FLOOR): Float {
    if (widestWordPx <= 0f || availablePx <= 0f || widestWordPx <= availablePx) return current
    val fit = current * (availablePx / widestWordPx) * 0.98f
    return fit.coerceIn(minOf(floor, current), current)
}

/** The words a text wraps between. */
internal fun wordsOf(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }

/**
 * A [Text] whose words are never broken: at a size where its widest word is
 * wider than the space it has, its font scale steps down until that word
 * fits (see [wholeWordsScale]). Same parameters as the [Text] it draws.
 */
@Composable
fun WholeWordsText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val widest = remember(text, style, density) {
            wordsOf(text).maxOfOrNull { word ->
                measurer.measure(word, style, softWrap = false, maxLines = 1, density = density).size.width.toFloat()
            } ?: 0f
        }
        val scale = if (constraints.hasBoundedWidth) {
            wholeWordsScale(density.fontScale, widest, constraints.maxWidth.toFloat())
        } else {
            density.fontScale
        }
        val draw: @Composable () -> Unit = {
            Text(text, style = style, color = color, maxLines = maxLines, overflow = overflow, textAlign = textAlign)
        }
        if (scale < density.fontScale) {
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), content = draw)
        } else {
            draw()
        }
    }
}
