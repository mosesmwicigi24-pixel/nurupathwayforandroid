package org.nuruplace.member.data.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline is honest (EXPERIENCE.md §4, §9.7 M3): the banner says "Showing
 *  what you last saw" only once the page in front was given a saved copy —
 *  never over skeletons or a spinner still waiting, nor over a page with
 *  nothing saved, whose own card says so (the final walk's #27 and #85). */
class StaleBannerTest {
    @After fun tidy() { ServerReach.reached(); ServerReach.newPage() }

    @Test fun `offline with a saved copy in front — the state language, whole`() {
        assertEquals("You're offline. Showing what you last saw — we'll refresh when you're back.", staleBannerLine(phoneOffline = true, savedOnPage = true))
    }

    @Test fun `offline with nothing saved yet in front — only the cause`() {
        assertEquals("You're offline.", staleBannerLine(phoneOffline = true, savedOnPage = false))
    }

    @Test fun `the server away — the same rule`() {
        assertEquals("Nuru Place can't be reached right now — showing what you last saw. We'll keep trying.", staleBannerLine(phoneOffline = false, savedOnPage = true))
        assertEquals("Nuru Place can't be reached right now. We'll keep trying.", staleBannerLine(phoneOffline = false, savedOnPage = false))
    }

    @Test fun `a saved copy counts for the page it was served to, until the next page comes to the front`() {
        ServerReach.newPage()
        assertFalse(ServerReach.savedOnThisPage)
        ServerReach.servedStale()
        assertTrue(ServerReach.savedOnThisPage)
        assertTrue(ServerReach.staleSince != null)
        // A new page: nothing it shows is a saved copy until one is served to it.
        ServerReach.newPage()
        assertFalse(ServerReach.savedOnThisPage)
        assertTrue("the outage itself goes on", ServerReach.staleSince != null)
    }
}
