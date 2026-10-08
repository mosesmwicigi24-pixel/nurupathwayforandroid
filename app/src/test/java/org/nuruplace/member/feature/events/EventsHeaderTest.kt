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
    fun `nothing from today on — quiet, in the header's words, never called a week (D3)`() {
        val past = listOf(occ("sat", "Choir practice", "2026-10-03T12:00:00Z"))
        assertEquals("Nothing planned yet", eventsHeaderLine(past, today))
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

/** §9.2 #11 — which series are offered is the server's call (repeating only;
 *  never an ended one unless the member follows it). The app adds no filter
 *  of its own: it used to drop every series without `next_at`, which hid a
 *  followed series that had ended (the server keeps it so it can be
 *  unfollowed; iOS shows it) and a series meeting beyond `next_at`'s 45 days. */
class SeriesRailsTest {
    private fun s(id: String, following: Boolean, nextAt: String?) =
        org.nuruplace.member.data.net.EventSeries(seriesId = id, title = id, following = following, nextAt = nextAt)

    @org.junit.Test fun `the rails hold the server's list, split by following`() {
        val sunday = s("Sunday Service", following = false, nextAt = "2026-10-11T06:00:00Z")
        // Followed, and ended 6 Sep — the server sends it so it can be unfollowed.
        val endedFollowed = s("Pathway Discipleship Classes", following = true, nextAt = null)
        // Meets again, but beyond next_at's 45-day window — not ended.
        val quarterly = s("Quarterly Prayer Day", following = false, nextAt = null)
        val (followed, more) = seriesRails(listOf(endedFollowed, sunday, quarterly))
        org.junit.Assert.assertEquals(listOf("Pathway Discipleship Classes"), followed.map { it.seriesId })
        org.junit.Assert.assertEquals(listOf("Sunday Service", "Quarterly Prayer Day"), more.map { it.seriesId })
    }
}

/** The strip and "N this week" count the same days: today through the
 *  seventh day after (§6; Cycle 3 close walk E21 — Sat–Fri on the strip,
 *  a Sunday in the count). */
class ThisWeekTest {
    private val mon5 = LocalDate.of(2026, 10, 5)

    @org.junit.Test fun `the strip is today through the seventh day after`() {
        val days = thisWeekDays(mon5)
        org.junit.Assert.assertEquals(8, days.size)
        org.junit.Assert.assertEquals(mon5, days.first())
        org.junit.Assert.assertEquals(LocalDate.of(2026, 10, 12), days.last())
        org.junit.Assert.assertTrue(LocalDate.of(2026, 10, 11) in days) // the Sunday the walk counted
    }

    @org.junit.Test fun `the strip rolls from today and its count never says week (owner, 2026-10-08)`() {
        org.junit.Assert.assertEquals("2 in the next 7 days", eventsSoonPill(2))
        org.junit.Assert.assertFalse(eventsSoonPill(1).contains("week"))
        org.junit.Assert.assertFalse(EVENTS_QUIET_LINE.contains("week"))
    }

    @org.junit.Test fun `the count's days are the strip's days`() {
        val days = thisWeekDays(mon5).toSet()
        for (offset in -3L..10L) {
            val d = mon5.plusDays(offset)
            org.junit.Assert.assertEquals(d.toString(), d in days, inThisWeek(d, mon5))
        }
    }
}
