// The Events tab's header line and its quiet week (pathway docs/EXPERIENCE.md
// §6.2, §6.5): "EVENTS", the title, and "Next: «title» · EEE d MMM" or
// "Nothing planned this week"; with nothing in range the week is quiet — the
// calm card, and no tabs, search or filters.
package org.nuruplace.member.feature.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.CalendarOccurrence
import java.time.LocalDate

class EventsHeaderTest {
    private val today = LocalDate.of(2026, 10, 4) // Sunday

    private fun occ(id: String, title: String, startAt: String) = CalendarOccurrence(id, title = title, startAt = startAt)

    @Test
    fun `the line names the soonest gathering from today and its day`() {
        val events = listOf(
            occ("tue", "Youth Night", "2026-10-06T15:00:00Z"),
            occ("mon", "Sunday Service", "2026-10-05T06:00:00Z"),
        )
        assertEquals("Next: Sunday Service · Mon 5 Oct", eventsHeaderLine(events, today))
        assertFalse(eventsQuiet(events, today))
        assertEquals(listOf("mon", "tue"), upcomingGatherings(events, today).map { it.occurrenceId })
    }

    @Test
    fun `a gathering later today is still next, and the day is the church's`() {
        // 21:30 UTC on 3 Oct is 00:30 on Sunday 4 Oct in Nairobi — today.
        val events = listOf(occ("late", "Night Vigil", "2026-10-03T21:30:00Z"))
        assertEquals("Next: Night Vigil · Sun 4 Oct", eventsHeaderLine(events, today))
    }

    @Test
    fun `nothing from today on — a quiet week, in the header's words`() {
        val past = listOf(occ("sat", "Choir practice", "2026-10-03T12:00:00Z"))
        assertEquals("Nothing planned this week", eventsHeaderLine(past, today))
        assertTrue(eventsQuiet(past, today))
        assertTrue(eventsQuiet(emptyList(), today))
        // A start that cannot be read is not something to filter.
        assertTrue(eventsQuiet(listOf(occ("x", "Mystery", "")), today))
    }
}
