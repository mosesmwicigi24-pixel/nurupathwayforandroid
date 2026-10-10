// No zero counts (pathway docs/EXPERIENCE.md §7.4 #9). Seen: Community's
// assistant card read "The AI assistant · 0 updates across 0 spaces". The
// same words as iOS's ZeroCounts.
package org.nuruplace.member.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZeroCountsTest {
    @Test fun `the assistant card says only the counts above zero`() {
        assertEquals("The AI assistant", ZeroCounts.assistantLine(unread = 0, spaces = 0))
        assertEquals("The AI assistant", ZeroCounts.assistantLine(unread = 0, spaces = 3))
        assertEquals("The AI assistant · 3 updates across 2 spaces", ZeroCounts.assistantLine(unread = 3, spaces = 2))
        assertEquals("The AI assistant · 1 update across 1 space", ZeroCounts.assistantLine(unread = 1, spaces = 1))
        assertEquals("The AI assistant · 2 updates", ZeroCounts.assistantLine(unread = 2, spaces = 0))
    }

    @Test fun `a count at zero is nothing`() {
        assertNull(ZeroCounts.count(0, "update", "updates"))
        assertNull(ZeroCounts.count(-1, "update", "updates"))
        assertEquals("1 update", ZeroCounts.count(1, "update", "updates"))
        assertEquals("4 updates", ZeroCounts.count(4, "update", "updates"))
    }

    // §7.4 #9, at parity with iOS (c043329).
    @Test fun `Home's prayer pill — only the counts above zero, no pill before anyone prays`() {
        assertNull(ZeroCounts.prayerLine(praying = 0, replies = 0))
        assertEquals("4 praying", ZeroCounts.prayerLine(praying = 4, replies = 0))
        assertEquals("1 reply", ZeroCounts.prayerLine(praying = 0, replies = 1))
        assertEquals("4 praying · 2 replies", ZeroCounts.prayerLine(praying = 4, replies = 2))
    }

    @Test fun `the calendar's header is the month alone when nothing is coming`() {
        assertEquals("October 2026", ZeroCounts.calendarHeader(upcoming = 0, month = "October 2026"))
        assertEquals("12 upcoming · October 2026", ZeroCounts.calendarHeader(upcoming = 12, month = "October 2026"))
    }

    @Test fun `a label keeps its count only above zero`() {
        assertEquals("RAISED HANDS", ZeroCounts.labelled("RAISED HANDS", 0))
        assertEquals("RAISED HANDS · 3", ZeroCounts.labelled("RAISED HANDS", 3))
        assertEquals("Answered", ZeroCounts.labelled("Answered", 0) { l, n -> "$l ($n)" })
        assertEquals("Active (2)", ZeroCounts.labelled("Active", 2) { l, n -> "$l ($n)" })
    }

    @Test fun `a summary joins what is there`() {
        assertEquals("7-day plan", ZeroCounts.join("7-day plan", ZeroCounts.count(0, "reading together", "reading together")))
        assertEquals("7-day plan · 2 reading together", ZeroCounts.join("7-day plan", ZeroCounts.count(2, "reading together", "reading together")))
        assertEquals("4:12", ZeroCounts.join("4:12", null))
        assertEquals("Started 2m ago · 9 watching", ZeroCounts.join("Started 2m ago", ZeroCounts.count(9, "watching", "watching")))
        assertEquals("", ZeroCounts.join(null, null))
    }
}
