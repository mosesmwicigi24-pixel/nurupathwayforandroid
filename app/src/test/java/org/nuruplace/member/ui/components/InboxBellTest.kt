// One bell (pathway docs/EXPERIENCE.md §7.2 #4 — rule 8: signals tell the
// truth): a bell's dot means something is unread — never painted on, never
// shown on a guess before the server has said.
package org.nuruplace.member.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxBellTest {
    @Test fun `the dot shows only while something is unread`() {
        assertFalse(bellShowsDot(null))
        assertFalse(bellShowsDot(0))
        assertTrue(bellShowsDot(1))
        assertTrue(bellShowsDot(12))
    }

    @Test fun `one count — what the inbox says, never below zero, nobody's once signed out`() {
        InboxUnread.set(3)
        assertEquals(3, InboxUnread.count.value)
        InboxUnread.set(-1)
        assertEquals(0, InboxUnread.count.value)
        InboxUnread.clear()
        assertNull(InboxUnread.count.value)
    }
}
