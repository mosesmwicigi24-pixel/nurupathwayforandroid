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
