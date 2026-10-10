// EXPERIENCE.md §9.3 (Cycle 6, context): what is already happening is said
// first — a claim the office is checking sits on the DUE row it covers — and
// nothing is urgent before it is.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.DueItem

class DueContextTest {
    private fun due(claim: Int = 0, kind: String = "pledge", currency: String = "KES") =
        DueItem(kind = kind, id = "roof", title = "Roof sheets", amountMinor = 2_000_000, currency = currency, dueOn = "2026-12-31", pendingClaimMinor = claim)

    @Test fun `a claim being checked is said on the row it covers — and never subtracted`() {
        val d = due(claim = 200_000)
        assertEquals("KSh 2,000 is being checked by the office", dueClaimLine(d))
        // The row still asks for what is owed: a claim counts once confirmed.
        assertEquals(2_000_000, dueRowView(d, null).leadMinor)
        assertNull(dueClaimLine(due(claim = 0)))
        assertNull(dueClaimLine(due(claim = 200_000, kind = "schedule")))
    }

    @Test fun `while the office checks a claim the row's Pay is the quiet secondary — final walk M1`() {
        org.junit.Assert.assertTrue(duePayQuiet(due(claim = 200_000)))
        // Nothing waiting: Pay stays the row's navy ask.
        org.junit.Assert.assertFalse(duePayQuiet(due(claim = 0)))
        // Only where the claim line is said — a schedule row carries none.
        org.junit.Assert.assertFalse(duePayQuiet(due(claim = 200_000, kind = "schedule")))
        // A paused row's Resume is never quieted.
        org.junit.Assert.assertFalse(duePayQuiet(due(claim = 200_000).copy(action = "resume")))
    }

    @Test fun `an empty year is a state title with no full stop — final walk C16`() {
        assertEquals("No pledge payments in 2026", noPledgePaymentsTitle(2026))
        assertEquals("No gifts this year", givingYearEmptyTitle("this year"))
        assertEquals("No gifts in 2025", givingYearEmptyTitle("in 2025"))
    }

    @Test fun `DUE only within the fortnight — further out it is coming up`() {
        val today = java.time.LocalDate.of(2026, 10, 5)
        fun on(day: String, overdue: Boolean? = false) = DueItem(kind = "pledge", id = "p", dueOn = day, overdue = overdue, amountMinor = 500_000)
        org.junit.Assert.assertTrue(dueIsSoon(on("2026-10-05"), today))           // today
        org.junit.Assert.assertTrue(dueIsSoon(on("2026-10-19"), today))           // the 14th day
        org.junit.Assert.assertFalse(dueIsSoon(on("2026-10-20"), today))          // a day past the fortnight
        org.junit.Assert.assertFalse(dueIsSoon(on("2026-12-31"), today))          // Ada's Roof sheets, 87 days out
        org.junit.Assert.assertTrue(dueIsSoon(on("2026-09-05", overdue = true), today))
        org.junit.Assert.assertTrue(dueIsSoon(on(""), today))                     // undated: stays DUE
    }
}
