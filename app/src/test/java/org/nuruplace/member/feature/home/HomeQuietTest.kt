package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** A quiet divider when dark cards would touch (owner, 2026-10-07): only
 *  where a dark card would sit directly on another dark card or on a
 *  photograph's dark edge; nothing moves. */
class HomeQuietTest {
    @Test fun `Ben's first day — the letter would land on the liturgy's photograph`() {
        // The reflection is held back on a first day (§9.2 #4), and with
        // notifications on there is no "What needs you today": the navy letter
        // sits right on the liturgy's photograph.
        val ben = HomeQuiet.opening(verseArt = true, video = false, liveNow = false, needsRail = false, reflectionStrip = false)
        assertEquals(listOf(HomeEdge.VERSE, HomeEdge.LETTER, HomeEdge.LITURGY, HomeEdge.WEEK), ben.map { it.edge })
        assertEquals(setOf(HomeEdge.LITURGY), HomeQuiet.dividersBefore(ben))
    }

    @Test fun `Ben's first day with notifications off — the rail's light label is between`() {
        val ben = HomeQuiet.opening(verseArt = true, video = false, liveNow = false, needsRail = true, reflectionStrip = false)
        assertEquals(emptySet<HomeEdge>(), HomeQuiet.dividersBefore(ben))
    }

    @Test fun `a service near — the Live-now card would sit right on the letter`() {
        val near = HomeQuiet.opening(verseArt = true, video = true, liveNow = true, needsRail = true, reflectionStrip = false)
        assertEquals(setOf(HomeEdge.LETTER), HomeQuiet.dividersBefore(near))
        // …and with nothing between the letter and the liturgy, both.
        val both = HomeQuiet.opening(verseArt = true, video = true, liveNow = true, needsRail = false, reflectionStrip = false)
        assertEquals(setOf(HomeEdge.LETTER, HomeEdge.LITURGY), HomeQuiet.dividersBefore(both))
    }

    @Test fun `an ordinary day has none, and the order never changes`() {
        val ada = HomeQuiet.opening(verseArt = true, video = true, liveNow = false, needsRail = true, reflectionStrip = false)
        assertEquals(emptySet<HomeEdge>(), HomeQuiet.dividersBefore(ada))
        val strip = HomeQuiet.opening(verseArt = false, video = false, liveNow = false, needsRail = false, reflectionStrip = true)
        assertEquals(emptySet<HomeEdge>(), HomeQuiet.dividersBefore(strip))
        assertEquals(
            listOf(HomeEdge.VERSE, HomeEdge.VIDEO, HomeEdge.LIVE_NOW, HomeEdge.LETTER, HomeEdge.NEEDS, HomeEdge.REFLECTION, HomeEdge.LITURGY, HomeEdge.WEEK),
            HomeQuiet.opening(verseArt = true, video = true, liveNow = true, needsRail = true, reflectionStrip = true).map { it.edge },
        )
    }

    @Test fun `the check is general — any dark foot on any dark top`() {
        val blocks = listOf(
            HomeBlock(HomeEdge.VERSE, darkTop = true, darkBottom = true),
            HomeBlock(HomeEdge.VIDEO, darkTop = true, darkBottom = false),
            HomeBlock(HomeEdge.LETTER, darkTop = true, darkBottom = true),
        )
        assertEquals(setOf(HomeEdge.VIDEO), HomeQuiet.dividersBefore(blocks))
    }
}
