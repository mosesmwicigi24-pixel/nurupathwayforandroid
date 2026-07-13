// Shimmer skeleton system — quiet placeholders that hold a page's shape while
// its first data arrives (iOS parity: a bare centered spinner reads as broken;
// a skeleton reads as "almost there"). One shimmer voice for the whole app:
// warm paper → slightly lighter, a 1.1s linear sweep. Compose animations honor
// the user's system animator scale, so reduced-motion settings apply for free.
package org.nuruplace.member.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.theme.Spacing

// Paper-adjacent neutrals — dark enough to show on white cards, light enough
// to stay quiet on the paper background.
private val SkeletonBase = Color(0xFFECE8DE)
private val SkeletonHi = Color(0xFFF7F4EC)

/** Infinite linear gradient sweep (1.1s) over a paper-toned base. Apply after
 *  a `clip` so the shimmer respects the block's corners. */
fun Modifier.shimmer(): Modifier = composed {
    val sweep by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "shimmerSweep",
    )
    drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val x = sweep * w
        drawRect(SkeletonBase)
        drawRect(
            Brush.linearGradient(
                colors = listOf(SkeletonBase, SkeletonHi, SkeletonBase),
                start = Offset(x, 0f),
                end = Offset(x + w, size.height),
            ),
        )
    }
}

/** One shimmering rectangle. `width = null` fills the available width. */
@Composable
fun SkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    corner: Dp = 12.dp,
) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(corner))
            .shimmer(),
    )
}

/** Home/hub first-paint skeleton — a greeting bar and three card blocks, on the
 *  same 20dp feed rhythm the real cards use. */
@Composable
fun HomeSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SkeletonBlock(height = 22.dp, width = 200.dp, corner = 8.dp)
        SkeletonBlock(height = 148.dp, corner = 20.dp)
        SkeletonBlock(height = 112.dp, corner = 20.dp)
        SkeletonBlock(height = 148.dp, corner = 20.dp)
    }
}

/** List first-paint skeleton — an avatar circle and two text lines per row. */
@Composable
fun ListSkeleton(rows: Int) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.base),
    ) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).shimmer())
                Spacer(Modifier.width(Spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(height = 12.dp, width = 180.dp, corner = 6.dp)
                    SkeletonBlock(height = 10.dp, width = 120.dp, corner = 5.dp)
                }
            }
        }
    }
}
