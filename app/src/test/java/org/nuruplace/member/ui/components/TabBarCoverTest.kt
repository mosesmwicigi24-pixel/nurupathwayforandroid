// The new pledge covers the tab bar (pathway docs/EXPERIENCE.md §7.3): while
// any cover is open the shell shows no bottom chrome, and closing the last
// one brings it back.
package org.nuruplace.member.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabBarCoverTest {
    @Test fun `the tab bar is hidden while any cover is open`() {
        assertFalse(TabBarCover.active)
        TabBarCover.cover()
        assertTrue(TabBarCover.active)
        TabBarCover.cover()
        TabBarCover.uncover()
        assertTrue(TabBarCover.active)
        TabBarCover.uncover()
        assertFalse(TabBarCover.active)
        // Never below none.
        TabBarCover.uncover()
        assertFalse(TabBarCover.active)
    }
}
