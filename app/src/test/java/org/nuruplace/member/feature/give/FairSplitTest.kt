// Android ↔ iOS visual parity (2026-09-28) — the width rule behind
// FairSplitRow. Moses' screenshot: on Partners → STANDING, the tier chip
// "carries one disciple through a level, every year" was measured first at its
// full width and left "Partner since Sep 2026" a ~40dp column, a letter or two
// a line. iOS shares the row: the standing wraps once, the kept line stays on
// one, and the chip wraps inside roughly the right half. The rule: each side
// its own width when both fit; the side needing less than half keeps it; both
// over half → half each — so no side is ever narrower than the smaller of its
// own width and half the row, at any font scale.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FairSplitTest {
    /** A 411dp phone (1080px @ 2.625): the STANDING card's inner width, less the 16dp gap. */
    private val available = 331

    @Test
    fun `both fit — each keeps its own width`() {
        assertEquals(190 to 70, fairSplit(available, 190, 70)) // "Builder"
        assertEquals(0 to 120, fairSplit(available, 0, 120))
        assertEquals(331 to 0, fairSplit(available, 331, 0))
    }

    @Test
    fun `the reported case — a sentence of a tier name shares the row instead of starving the standing`() {
        // "Partner since Sep 2026" at 16sp ≈ 190dp; the chip at its full width ≈ 344dp.
        val (standing, chip) = fairSplit(available, 190, 344)
        assertEquals(166 to 165, standing to chip)
        // The standing column is wide enough for "Partner since" (≈117dp) and
        // "0 gifts kept · on track" (≈145dp) on a line each — never letters.
        assertTrue(standing >= 145)
        assertEquals(available, standing + chip)
    }

    @Test
    fun `at 1_3x font scale the standing still gets half the row`() {
        val (standing, chip) = fairSplit(available, (190 * 1.3).toInt(), (344 * 1.3).toInt())
        assertEquals(166 to 165, standing to chip)
        // "Partner since" at 20.8sp ≈ 148dp still fits on its line.
        assertTrue(standing >= 148)
    }

    @Test
    fun `the side needing less than half keeps its width, the other takes the rest`() {
        // A short tier chip beside a long standing line: the chip keeps its width.
        assertEquals(261 to 70, fairSplit(available, 400, 70))
        // A short left side ("Next 5 Oct" as the second is short too) — the long side gets the rest.
        assertEquals(80 to 251, fairSplit(available, 80, 600))
    }

    @Test
    fun `no side is ever narrower than the smaller of its own width and half the row`() {
        val widths = listOf(0, 10, 40, 100, 160, 165, 166, 200, 330, 331, 500, 2000)
        for (a in widths) for (b in widths) for (avail in listOf(0, 1, 50, 331, 332, 600)) {
            val (w1, w2) = fairSplit(avail, a, b)
            assertTrue("$avail $a $b", w1 >= 0 && w2 >= 0 && w1 + w2 <= maxOf(avail, 0))
            assertTrue("$avail $a $b → $w1", w1 >= minOf(a, avail / 2))
            assertTrue("$avail $a $b → $w2", w2 >= minOf(b, avail / 2))
            assertTrue("$avail $a $b → never more than it wants", w1 <= maxOf(a, avail - b) && w2 <= maxOf(b, avail - a))
        }
    }

    @Test
    fun `an odd width gives the leading side the extra pixel, and nothing below zero`() {
        assertEquals(166 to 165, fairSplit(331, 400, 400))
        assertEquals(0 to 0, fairSplit(-5, 100, 100))
        assertEquals(0 to 0, fairSplit(0, 100, 100))
    }

    // ── one value on one line, shrunk to fit (iOS minimumScaleFactor) ──

    @Test
    fun `a value that fits keeps its size, a wider one shrinks to the room`() {
        assertEquals(1f, fitScale(natural = 90, available = 120, floor = 0.6f), 0f)
        assertEquals(1f, fitScale(natural = 120, available = 120, floor = 0.6f), 0f)
        // "11 of 12" at 1.3× (~87dp) on a 55dp tile: shrunk to 0.63, whole.
        assertEquals(55f / 87f, fitScale(natural = 87, available = 55, floor = 0.6f), 0.0001f)
        // Too wide even at the floor: held at the floor (and clipped there).
        assertEquals(0.6f, fitScale(natural = 200, available = 55, floor = 0.6f), 0f)
        assertEquals(0.7f, fitScale(natural = 200, available = 55, floor = 0.7f), 0f)
    }

    @Test
    fun `an unmeasured or unbounded width is never scaled, and no room is the floor`() {
        // An intrinsic pass reports no width; an unbounded row has room for all.
        assertEquals(1f, fitScale(natural = Int.MIN_VALUE, available = 100, floor = 0.6f), 0f)
        assertEquals(1f, fitScale(natural = 0, available = 0, floor = 0.6f), 0f)
        assertEquals(1f, fitScale(natural = 500, available = Int.MAX_VALUE, floor = 0.6f), 0f)
        assertEquals(0.6f, fitScale(natural = 50, available = 0, floor = 0.6f), 0f)
    }
}
