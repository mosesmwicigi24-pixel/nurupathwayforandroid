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
}
