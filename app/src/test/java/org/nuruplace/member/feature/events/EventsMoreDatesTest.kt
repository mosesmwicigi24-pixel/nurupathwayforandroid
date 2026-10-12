// Final walk C16 (iOS 0bcf4a0): a weekly service is one card with its later
// dates beneath, and search and the filters stand only over something to
// search — the same rules and words as iOS.
package org.nuruplace.member.feature.events

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.CalendarOccurrence

class EventsMoreDatesTest {
    private fun iso(day: LocalDate, h: Int, m: Int = 0) =
        ZonedDateTime.of(day, LocalTime.of(h, m), EV_ZONE).toInstant().toString()

    private fun occ(id: String, series: String, day: LocalDate) =
        CalendarOccurrence(occurrenceId = id, seriesId = series, title = "Sunday Service", startAt = iso(day, 9), endAt = iso(day, 13))

    @Test fun `a series' later dates fold under its first card, in order`() {
        val d = LocalDate.of(2026, 10, 11)
        val list = listOf(
            occ("s1", "sunday", d), occ("y1", "youth", d.plusDays(2)),
            occ("s2", "sunday", d.plusDays(7)), occ("s3", "sunday", d.plusDays(14)),
            occ("x1", "", d.plusDays(3)), occ("x2", "", d.plusDays(4)),
        )
        val g = evGrouped(list)
        assertEquals(listOf("s1", "y1", "x1", "x2"), g.map { it.first.occurrenceId })
        assertEquals(listOf("s2", "s3"), g[0].more.map { it.occurrenceId })
        // A gathering with no series stands alone.
        assertTrue(g[2].more.isEmpty() && g[3].more.isEmpty())
    }

    @Test fun `a more-dates row says the time and how far off`() {
        val today = LocalDate.now(EV_ZONE)
        assertEquals("9:00 AM – 1:00 PM · In 10 days", evMoreDateLine(occ("s", "sunday", today.plusDays(10))))
        assertEquals("9:00 AM – 1:00 PM · Tomorrow", evMoreDateLine(occ("s", "sunday", today.plusDays(1))))
    }

    @Test fun `search and filters never stand over an empty day unless one is in use`() {
        assertFalse(eventsShowsSearchAndFilters(segmentCount = 0, search = "", category = "All"))
        assertFalse(eventsShowsSearchAndFilters(segmentCount = 0, search = "   ", category = "All"))
        assertTrue(eventsShowsSearchAndFilters(segmentCount = 2, search = "", category = "All"))
        assertTrue(eventsShowsSearchAndFilters(segmentCount = 0, search = "youth", category = "All"))
        assertTrue(eventsShowsSearchAndFilters(segmentCount = 0, search = "", category = "Cell"))
    }

    @Test fun `the announcements list with none says so as a state title`() {
        assertEquals("No announcements yet", ANNOUNCEMENTS_EMPTY)
    }
}
