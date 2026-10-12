// A flow that covers the tab bar while it is open — Android's fullScreenCover
// (pathway docs/EXPERIENCE.md §7.3). The new pledge was drawn inside the Give
// tab with the tab bar still under it, so a tap on another tab threw a
// half-made pledge away without a word. While a cover is composed, MainShell
// hides its whole bottom chrome — the tab bar and the Live bars above it —
// so the flow can only be left through its own Close, which asks first.
package org.nuruplace.member.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** How many covers are open; the shell hides its bottom chrome while any is. */
object TabBarCover {
    private var open by mutableIntStateOf(0)

    /** A cover is open: the tab bar is hidden. */
    val active: Boolean get() = open > 0

    internal fun cover() { open++ }
    internal fun uncover() { open = (open - 1).coerceAtLeast(0) }
}

/** Covers the tab bar for as long as this is composed. */
@Composable
fun CoverTabBar() {
    DisposableEffect(Unit) {
        TabBarCover.cover()
        onDispose { TabBarCover.uncover() }
    }
}
