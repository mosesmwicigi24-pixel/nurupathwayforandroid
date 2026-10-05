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
//
// And one value that must stay on one line — an amount on a narrow tile —
// shrinks to its width as iOS's minimumScaleFactor does ([shrinkToFit],
// [fitScale]) instead of splitting mid-figure or being cut.
package org.nuruplace.member.feature.give

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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

/**
 * How far a one-line value shrinks to fit its width, as SwiftUI's
 * `.lineLimit(1).minimumScaleFactor(floor)` does: 1 when its [natural] width
 * fits in [available] (px), else the ratio — never below [floor], where it
 * is clipped rather than shrunk further. An unmeasured width (≤ 0) is 1.
 */
internal fun fitScale(natural: Int, available: Int, floor: Float): Float {
    if (natural <= 0 || natural <= available) return 1f
    if (available <= 0) return floor
    return (available.toFloat() / natural).coerceIn(floor, 1f)
}

/**
 * A one-line value shrunk to the width it is given ([fitScale]) — the iOS
 * tiles' and summary columns' `.lineLimit(1).minimumScaleFactor(…)`, so an
 * amount is never split mid-figure nor cut while it can still shrink. Put it
 * on a Text with `maxLines = 1, softWrap = false` (or a Row of them): it is
 * measured at its natural width, then drawn scaled from its leading top
 * corner inside the room it has.
 */
internal fun Modifier.shrinkToFit(floor: Float): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val scale = fitScale(placeable.width, constraints.maxWidth, floor)
        val w = (placeable.width * scale).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (placeable.height * scale).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(w, h) {
            // Absolute, not mirrored: the unscaled child is wider than the
            // room, so a mirrored x would push it off its own edge.
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }

/**
 * Whether a row's trailing chip or button goes BELOW its lead line rather
 * than beside it: when the lead line ([leadWidth], its one-line width) and
 * the trailing element ([trailingWidth]) don't fit side by side in
 * [available] with [gap] between them. A DUE row's "KSh 5,000 · today" never
 * splits beside "Collected on Mon 5 Oct" (EXPERIENCE.md §8.2 #20): on one
 * line beside it, or stacked deliberately above it.
 */
internal fun stacksBelow(leadWidth: Int, trailingWidth: Int, available: Int, gap: Int): Boolean =
    leadWidth.coerceAtLeast(0) + gap.coerceAtLeast(0) + trailingWidth.coerceAtLeast(0) > available.coerceAtLeast(0)

/**
 * A content row whose [lead] line (an amount and when) must stay whole: the
 * [lead] with the [rest] under it on the leading side and [trailing] (a chip
 * or a pill) centred on the trailing side while the lead fits beside it
 * ([stacksBelow]); otherwise the trailing element moves below the text,
 * start-aligned, [stackGap] under it. The [rest] may wrap either way.
 */
@Composable
internal fun LeadOrStackRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    stackGap: Dp = 8.dp,
    lead: @Composable () -> Unit,
    rest: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(
        content = {
            Box { lead() }
            Box { rest() }
            Box { trailing() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val (mLead, mRest, mTrail) = measurables
        val gap = spacing.roundToPx()
        val leadIdeal = mLead.maxIntrinsicWidth(Constraints.Infinity)
        val trailIdeal = mTrail.maxIntrinsicWidth(Constraints.Infinity)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else leadIdeal + gap + trailIdeal
        if (!stacksBelow(leadIdeal, trailIdeal, width, gap)) {
            val pTrail = mTrail.measure(Constraints(maxWidth = trailIdeal.coerceAtMost(width)))
            val textWidth = (width - gap - pTrail.width).coerceAtLeast(0)
            val pLead = mLead.measure(Constraints(maxWidth = textWidth))
            val pRest = mRest.measure(Constraints(maxWidth = textWidth))
            val textHeight = pLead.height + pRest.height
            val height = maxOf(textHeight, pTrail.height).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                val top = (height - textHeight) / 2
                pLead.placeRelative(0, top)
                pRest.placeRelative(0, top + pLead.height)
                pTrail.placeRelative(width - pTrail.width, (height - pTrail.height) / 2)
            }
        } else {
            val pLead = mLead.measure(Constraints(maxWidth = width))
            val pRest = mRest.measure(Constraints(maxWidth = width))
            val pTrail = mTrail.measure(Constraints(maxWidth = width))
            val below = pLead.height + pRest.height + stackGap.roundToPx()
            val height = (below + pTrail.height).coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                pLead.placeRelative(0, 0)
                pRest.placeRelative(0, pLead.height)
                pTrail.placeRelative(0, below)
            }
        }
    }
}
