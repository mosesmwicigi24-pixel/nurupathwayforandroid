// The cell page (pathway docs/EXPERIENCE.md §7.4 #16). Seen: Android "48% ·
// last 8 meetings", iOS "0/8 · you, this month" — the 8 was `expected`, a
// scoring baseline, not the cell's schedule; and Android's page ended in
// stats with no way forward. Both apps now say the same lines.
package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.CellSummary
import org.nuruplace.member.data.net.ChatConversation

class CellAttendanceTest {
    private fun you(attended: Int, meetings: Int) = CellSummary.You(attended = attended, meetings = meetings)
    private fun turnout(rate: Double, meetings: Int, trend: String? = null) = CellSummary.Turnout(rate = rate, meetings = meetings, trend = trend)

    @Test fun `the member's part, then the cell's — the same real meetings`() {
        assertEquals(
            listOf("You: 3 of the last 8 meetings", "The cell: 48% · last 8 meetings"),
            CellAttendanceWords.lines(you(3, 8), turnout(0.48, 8, "up")),
        )
        assertTrue(CellAttendanceWords.hasMet(you(3, 8), turnout(0.48, 8)))
    }

    @Test fun `one meeting reads as one`() {
        assertEquals(listOf("You: 1 of 1 meeting", "The cell: 100% · 1 meeting"), CellAttendanceWords.lines(you(1, 1), turnout(1.0, 1)))
        assertEquals(listOf("You: 0 of 1 meeting"), CellAttendanceWords.lines(you(0, 1), null))
    }

    @Test fun `a cell that hasn't met says so — never a baseline of 8`() {
        // Ada's cell today: attendance {attended 0, expected 8, you null}, no turnout.
        assertEquals(listOf("Your cell hasn't met yet"), CellAttendanceWords.lines(null, null))
        assertEquals(listOf("Your cell hasn't met yet"), CellAttendanceWords.lines(you(0, 0), turnout(0.0, 0)))
        assertFalse(CellAttendanceWords.hasMet(null, null))
    }

    @Test fun `an older server without you shows the cell alone, and figures stay in range`() {
        assertEquals(listOf("The cell: 48% · last 8 meetings"), CellAttendanceWords.lines(null, turnout(0.48, 8)))
        assertEquals("You: 8 of the last 8 meetings", CellAttendanceWords.you(you(11, 8)))
        assertEquals("The cell: 100% · last 3 meetings", CellAttendanceWords.cell(turnout(1.2, 3)))
        assertNull(CellAttendanceWords.you(you(2, 0)))
    }

    @Test fun `Open community lands on the cell's own room`() {
        val dm = ChatConversation(conversationId = "dm1", kind = "dm", title = "Ben")
        val space = ChatConversation(conversationId = "sp1", kind = "space", title = "Worship team")
        val room = ChatConversation(conversationId = "0dfa", kind = "group", title = "Dev Cell A cell")
        assertEquals("0dfa", cellRoom(listOf(dm, space, room), "Dev Cell A"))
        // A leader in two rooms: the one named for this cell.
        val other = ChatConversation(conversationId = "x9", kind = "group", title = "Dev Cell B cell")
        assertEquals("0dfa", cellRoom(listOf(other, room), "Dev Cell A"))
        // The only room, even renamed.
        assertEquals("0dfa", cellRoom(listOf(dm, room.copy(title = "Our cell")), "Dev Cell A"))
        // None, or two unnamed for it: the You tab's Community instead.
        assertNull(cellRoom(listOf(dm, space), "Dev Cell A"))
        assertNull(cellRoom(listOf(other, room.copy(title = "Our cell")), "Dev Cell A"))
    }
}
