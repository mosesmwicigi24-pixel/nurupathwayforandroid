// "Ask to be connected" (pathway docs/EXPERIENCE.md §9.2 #12): the form asks
// within the server's own bounds, and the sent line names the day.
package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CellConnectWordsTest {
    @Test fun `the form asks only within the server's bounds`() {
        assertTrue(CellConnectWords.canAsk("Kilimani", "Weekday evenings", ""))
        assertFalse(CellConnectWords.canAsk("K", "Weekday evenings", ""))           // area under 2
        assertFalse(CellConnectWords.canAsk("Kilimani", " ", ""))                   // no time
        assertFalse(CellConnectWords.canAsk("x".repeat(81), "Evenings", ""))        // area over 80
        assertFalse(CellConnectWords.canAsk("Kilimani", "Evenings", "n".repeat(301))) // note over 300
        assertTrue(CellConnectWords.canAsk("  Kilimani  ", "  Evenings ", "n".repeat(300)))
    }

    @Test fun `sent names the church's day it went, and says the pastor will connect`() {
        val nairobi = java.time.ZoneId.of("Africa/Nairobi")
        val today = java.time.LocalDate.of(2026, 10, 6)
        assertEquals("Sent to your pastor on Mon 5 Oct — they'll connect you", CellConnectWords.sent("2026-10-05T20:30:00Z", nairobi, today))
        // 23:30 UTC on Sunday is Monday in Nairobi.
        assertEquals("Sent to your pastor on Mon 5 Oct — they'll connect you", CellConnectWords.sent("2026-10-04T23:30:00Z", nairobi, today))
        // Another year names it.
        assertEquals("Sent to your pastor on Mon 5 Oct 2026 — they'll connect you", CellConnectWords.sent("2026-10-05T09:00:00Z", nairobi, java.time.LocalDate.of(2027, 1, 4)))
        assertEquals("Sent to your pastor — they'll connect you", CellConnectWords.sent("not a date", nairobi, today))
    }
}
