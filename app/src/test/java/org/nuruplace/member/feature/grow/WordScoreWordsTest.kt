package org.nuruplace.member.feature.grow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.ScoreBreakdown

/** One Word score, the server's (EXPERIENCE.md §2, one truth; Cycle 4 walk:
 *  Memory verses named its own band, "Seedling", beside Home's "Word 2"). */
class WordScoreWordsTest {
    @Test fun `the server's score, band and parts`() {
        val w = wordScoreWords(ScoreBreakdown(score = 16, band = "Just beginning",
            components = mapOf("consistency" to 5.0, "memorization" to 31.0, "breadth" to 10.0)))
        assertEquals(16, w.score)
        assertEquals("Just beginning", w.band)
        assertEquals(0.05, w.consistency, 1e-9)
        assertEquals(0.31, w.memorization, 1e-9)
        assertEquals(0.10, w.breadth, 1e-9)
    }

    @Test fun `a part of 1 is one percent, never a full bar`() {
        // The old reader took any value ≤ 1 as a fraction: 1 (of 100) drew 100%.
        assertEquals(0.01, wordScoreWords(ScoreBreakdown(components = mapOf("breadth" to 1.0))).breadth, 1e-9)
    }

    @Test fun `no band, no name of our own, and values held to the scale`() {
        val w = wordScoreWords(ScoreBreakdown(score = 140, band = "  ", components = mapOf("consistency" to 250.0)))
        assertNull(w.band)
        assertEquals(100, w.score)
        assertEquals(1.0, w.consistency, 1e-9)
        assertEquals(0.0, w.memorization, 1e-9)
    }
}
