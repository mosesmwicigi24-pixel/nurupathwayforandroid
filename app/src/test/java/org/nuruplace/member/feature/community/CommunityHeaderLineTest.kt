// The Community header's line (pathway docs/EXPERIENCE.md §7.4 #15). Seen:
// "You're all caught up" beside a bell wearing its dot for unread notices —
// the line counts messages, so it says messages.
package org.nuruplace.member.feature.community

import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityHeaderLineTest {
    @Test fun `the line says what it counts`() {
        assertEquals("No new messages", communityHeaderLine(0))
        assertEquals("1 new message", communityHeaderLine(1))
        assertEquals("3 new messages", communityHeaderLine(3))
        assertEquals("No new messages", communityHeaderLine(-2))
    }
}
