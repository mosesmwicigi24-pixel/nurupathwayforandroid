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
}
