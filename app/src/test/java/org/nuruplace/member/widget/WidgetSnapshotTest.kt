package org.nuruplace.member.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure logic the two Glance widgets render from — no Context/Android
 *  framework needed, so these run as plain JVM tests. */
class WidgetSnapshotTest {

    // ── WidgetSnapshot.modulePct ────────────────────────────────────────

    @Test fun `modulePct is 0 with no modules yet`() {
        assertEquals(0, WidgetSnapshot(completedModules = 0, totalModules = 0).modulePct)
    }

    @Test fun `modulePct rounds down`() {
        assertEquals(33, WidgetSnapshot(completedModules = 1, totalModules = 3).modulePct)
    }

    @Test fun `modulePct is 100 when every module is done`() {
        assertEquals(100, WidgetSnapshot(completedModules = 5, totalModules = 5).modulePct)
    }

    @Test fun `modulePct clamps above 100 (stale completedModules never overshoots the ring)`() {
        assertEquals(100, WidgetSnapshot(completedModules = 9, totalModules = 5).modulePct)
    }

    // ── radioHeadline (RadioGlanceWidget.kt) ────────────────────────────

    @Test fun `on air with a title shows the program title`() {
        val snap = WidgetSnapshot(radioOnAir = true, radioProgramTitle = "Morning Devotion")
        assertEquals("Morning Devotion", radioHeadline(snap))
    }

    @Test fun `on air with a blank title falls back to the default on-air line`() {
        val snap = WidgetSnapshot(radioOnAir = true, radioProgramTitle = "  ")
        assertEquals("Worship & the Word", radioHeadline(snap))
    }

    @Test fun `off air with a next program shows the next title`() {
        val snap = WidgetSnapshot(radioOnAir = false, radioNextProgramTitle = "Evening Prayer")
        assertEquals("Evening Prayer", radioHeadline(snap))
    }

    @Test fun `off air with nothing scheduled prompts to tune in`() {
        val snap = WidgetSnapshot(radioOnAir = false, radioNextProgramTitle = null)
        assertEquals("Tap to tune in", radioHeadline(snap))
    }
}
