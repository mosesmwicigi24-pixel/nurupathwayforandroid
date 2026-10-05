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

    // EXPERIENCE.md §7.4 #6 — seen: Events opened on "Today (0)" while the
    // gatherings sat under "Upcoming".
    @Test
    fun `Events opens on the first tab with something in it`() {
        assertEquals(EVENTS_TAB_UPCOMING, firstEventsTab(todayCount = 0, upcomingCount = 4, rsvpCount = 1))
        assertEquals(EVENTS_TAB_TODAY, firstEventsTab(todayCount = 2, upcomingCount = 4, rsvpCount = 1))
        assertEquals(EVENTS_TAB_RSVPS, firstEventsTab(todayCount = 0, upcomingCount = 0, rsvpCount = 1))
        assertEquals(EVENTS_TAB_TODAY, firstEventsTab(todayCount = 0, upcomingCount = 0, rsvpCount = 0))
    }

    // §7.4 #8 — seen: "Every Sunday · 9:00 AM · 9:00 AM".
    @Test
    fun `a series line says its time once`() {
        assertEquals("Every Sunday · 9:00 AM", seriesLine("Every Sunday · 9:00 AM", "9:00 AM"))
        assertEquals("Monthly · 3:00 PM", seriesLine("Monthly · 3:00 PM", "3:00 PM"))
        assertEquals("One-off · 10:00 AM", seriesLine("One-off · 10:00 AM", null))
        // An older server's cadence without a time still gets one.
        assertEquals("Every Sunday · 9:00 AM", seriesLine("Every Sunday", "9:00 AM"))
        assertEquals("9:00 AM", seriesLine("", "9:00 AM"))
        assertEquals("Every Sunday", seriesLine("Every Sunday", null))
    }

    // §7.4 #7 — seen: "Series you follow" listed series the member does not
    // follow, each with "+ Follow".
    @Test
    fun `series you follow holds only followed series — the rest are more series`() {
        data class S(val title: String, val following: Boolean)
        val (followed, more) = splitByFollowing(
            listOf(S("Sunday Service", false), S("Ablaze", true), S("Pathway classes", false)),
        ) { it.following }
        assertEquals(listOf("Ablaze"), followed.map { it.title })
        assertEquals(listOf("Sunday Service", "Pathway classes"), more.map { it.title })
    }
}

class UpcomingSeriesTest {
    @org.junit.Test fun `a series with no next gathering has ended and is not offered`() {
        val live = org.nuruplace.member.data.net.EventSeries(seriesId = "a", title = "Sunday Service", nextAt = "2026-10-11T06:00:00Z")
        val ended = org.nuruplace.member.data.net.EventSeries(seriesId = "b", title = "Pathway Discipleship Classes", nextAt = null)
        org.junit.Assert.assertEquals(listOf("a"), upcomingSeries(listOf(live, ended)).map { it.seriesId })
    }
}
