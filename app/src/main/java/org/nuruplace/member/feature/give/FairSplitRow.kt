// Two things side by side that share the width the way iOS's HStack shares it
// (Android ↔ iOS visual parity, 2026-09-28). A plain Row measures a child
// without a weight FIRST, at its full intrinsic width: a long tier name
// ("carries one disciple through a level, every year") took nearly all of the
// STANDING card and left "Partner since Sep 2026" a ~40dp column, one or two
// letters a line. Here each side gets what it needs when both fit; otherwise
// the side that needs less than half keeps its own width and the other takes
// the rest; when both need more than half, each gets half and wraps inside
// it. Neither is ever starved, at any font scale. The rule is [fairSplit],
// pinned by FairSplitTest; the layout only measures and places.
package org.nuruplace.member.feature.give

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widths two side-by-side children get out of [available] (px, the gap
 * already taken out), given the widths they would like ([firstIdeal],
 * [secondIdeal] — their max intrinsic widths):
 *
 *  - both fit → each its own width;
 *  - one needs no more than half → it keeps its width, the other gets the rest;
 *  - both need more than half → half each (the first takes the odd pixel).
 *
 * So a side is never narrower than the smaller of its own width and half the
 * row — never a one-letter column.
 */
internal fun fairSplit(available: Int, firstIdeal: Int, secondIdeal: Int): Pair<Int, Int> {
    val avail = available.coerceAtLeast(0)
    val a = firstIdeal.coerceAtLeast(0)
    val b = secondIdeal.coerceAtLeast(0)
    if (a + b <= avail) return a to b
    val half = avail / 2
    return when {
        a <= half -> a to (avail - a)
        b <= half -> (avail - b) to b
        else -> (avail - half) to half
    }
}

/**
 * [first] on the leading edge and [second] on the trailing edge, top-aligned
 * (iOS `HStack(alignment: .top)` with a `Spacer` between) — or centred on
 * each other with [centerVertically] (a row's trailing button) — sharing the
 * width by [fairSplit] with at least [spacing] between them. Without
 * [second], [first] has the whole width.
 */
@Composable
internal fun FairSplitRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    centerVertically: Boolean = false,
    first: @Composable () -> Unit,
    second: (@Composable () -> Unit)? = null,
) {
    Layout(
        content = {
            Box { first() }
            if (second != null) Box { second() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else null
        if (measurables.size < 2) {
            val only = measurables.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
            val w = width ?: only?.width ?: 0
            return@Layout layout(w, (only?.height ?: 0).coerceAtLeast(constraints.minHeight)) { only?.placeRelative(0, 0) }
        }
        val gap = spacing.roundToPx()
        val (m1, m2) = measurables
        val ideal1 = m1.maxIntrinsicWidth(constraints.maxHeight)
        val ideal2 = m2.maxIntrinsicWidth(constraints.maxHeight)
        val available = (width ?: (ideal1 + ideal2 + gap)) - gap
        val (w1, w2) = fairSplit(available, ideal1, ideal2)
        val p1 = m1.measure(Constraints(maxWidth = w1))
        val p2 = m2.measure(Constraints(maxWidth = w2))
        val total = width ?: (p1.width + gap + p2.width)
        val height = maxOf(p1.height, p2.height).coerceAtLeast(constraints.minHeight)
        layout(total, height) {
            p1.placeRelative(0, if (centerVertically) (height - p1.height) / 2 else 0)
            p2.placeRelative(total - p2.width, if (centerVertically) (height - p2.height) / 2 else 0)
        }
    }
}
