// The fullest form that fits (iOS ViewThatFits): [candidates] are tried in
// order, each measured at its own width, and the first no wider than the
// space draws; the last draws whatever its width. The You tab's bar uses it so
// every segment is in view at every text size — icons and words, then words
// alone, then the chosen segment's words with the others' icons (final walk
// M10 and C3; iOS CapsuleSegmentBar, the same three forms).
package org.nuruplace.member.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints

@Composable
fun FirstThatFits(modifier: Modifier = Modifier, candidates: List<@Composable () -> Unit>) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        var chosen: List<Placeable> = emptyList()
        for ((i, candidate) in candidates.withIndex()) {
            val last = i == candidates.lastIndex
            val measured = subcompose(i, candidate).map {
                it.measure(if (last) loose else Constraints(maxHeight = loose.maxHeight))
            }
            val width = measured.maxOfOrNull { it.width } ?: 0
            if (last || width <= constraints.maxWidth) {
                chosen = measured
                break
            }
        }
        val w = (chosen.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (chosen.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(w, h) { chosen.forEach { it.placeRelative(0, 0) } }
    }
}
