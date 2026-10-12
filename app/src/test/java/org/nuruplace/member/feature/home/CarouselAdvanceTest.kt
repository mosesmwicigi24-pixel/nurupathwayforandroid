package org.nuruplace.member.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nothing animates behind a covered Home (EXPERIENCE.md §9.7 M7): the
 *  featured carousel's `while (true)` auto-turn ran on behind the open letter
 *  and was the main thread in three ANRs on the final walk. */
class CarouselAdvanceTest {
    @Test fun `it turns only while Home is resumed and its window has the focus`() {
        assertTrue(carouselAdvances(pageCount = 2, resumed = true, windowFocused = true))
        // The letter, a sheet or a dialog over Home takes the window's focus.
        assertFalse(carouselAdvances(pageCount = 2, resumed = true, windowFocused = false))
        // A page pushed over Home, or the app in the background: not resumed.
        assertFalse(carouselAdvances(pageCount = 2, resumed = false, windowFocused = true))
        assertFalse(carouselAdvances(pageCount = 2, resumed = false, windowFocused = false))
    }

    @Test fun `one page never turns`() {
        assertFalse(carouselAdvances(pageCount = 1, resumed = true, windowFocused = true))
        assertFalse(carouselAdvances(pageCount = 0, resumed = true, windowFocused = true))
    }
}
