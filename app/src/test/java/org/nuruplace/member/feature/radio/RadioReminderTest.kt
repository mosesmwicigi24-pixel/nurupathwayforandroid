// "Remind me when we're live" (pathway docs/EXPERIENCE.md §7.4: no success
// before it is true). Seen in code: the reminder was marked set even when
// nothing was scheduled — a program with no start time sorted first and got
// the button, and a start already past fired "Nuru Radio is live" a second
// after the tap. Only a readable future start can be reminded.
package org.nuruplace.member.feature.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.RadioProgram
import java.time.Instant

class RadioReminderTest {
    private val now = Instant.parse("2026-10-05T11:00:00Z")
    private fun prog(id: String, at: String?, status: String = "scheduled") = RadioProgram(id = id, title = "Show $id", scheduledAt = at, status = status)

    @Test fun `only a readable future start can be reminded`() {
        assertEquals(Instant.parse("2026-10-05T15:00:00Z"), RadioReminder.remindAt(prog("a", "2026-10-05T15:00:00Z"), now))
        assertEquals(Instant.parse("2026-10-05T15:00:00Z"), RadioReminder.remindAt(prog("a", "2026-10-05T18:00:00+03:00"), now))
        assertNull(RadioReminder.remindAt(prog("b", null), now))
        assertNull(RadioReminder.remindAt(prog("c", "tonight"), now))
        assertNull(RadioReminder.remindAt(prog("d", "2026-10-05T10:59:00Z"), now)) // already started
    }

    @Test fun `the next remindable is the soonest future start — never one with no time`() {
        val programs = listOf(
            prog("noTime", null),
            prog("past", "2026-10-05T09:00:00Z"),
            prog("later", "2026-10-06T06:00:00Z"),
            prog("soon", "2026-10-05T17:00:00Z"),
            prog("liveNow", "2026-10-05T12:00:00Z", status = "live"),
        )
        assertEquals("soon", RadioReminder.nextRemindable(programs, now)?.id)
        assertNull(RadioReminder.nextRemindable(listOf(prog("noTime", null), prog("past", "2026-10-04T09:00:00Z")), now))
    }
}
