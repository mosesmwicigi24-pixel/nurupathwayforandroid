// Events, said from the member's side (final walk C2, C5): a date chip in
// another month carries its month; the member's own RSVP is theirs; and the
// empty list sends no one down a second road to the calendar.
package org.nuruplace.member.feature.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EventsWordsTest {
    private val today = LocalDate.of(2026, 10, 7)

    @Test fun `a chip in this month carries no month, another month carries its own (C2, Android #13)`() {
        assertNull(evOtherMonth(LocalDate.of(2026, 10, 11), today))
        assertNull(evOtherMonth(LocalDate.of(2026, 10, 31), today))
        assertEquals("NOV", evOtherMonth(LocalDate.of(2026, 11, 25), today))
        // Next January is another month, though the countdown says how far.
        assertEquals("JAN", evOtherMonth(LocalDate.of(2027, 1, 3), today))
        // The same month a year on is another month too.
        assertEquals("OCT", evOtherMonth(LocalDate.of(2027, 10, 11), today))
    }

    @Test fun `the member's own RSVP is theirs — You're going, never 1 going (C5, Android #12)`() {
        assertEquals("You're going", evGoingLine(going = 1, mine = true))
        // A count the server hasn't caught up with yet still says it.
        assertEquals("You're going", evGoingLine(going = 0, mine = true))
        assertEquals("You and 1 other are going", evGoingLine(going = 2, mine = true))
        assertEquals("You and 4 others are going", evGoingLine(going = 5, mine = true))
        // Someone else's: the count; nobody: nothing (the card asks instead).
        assertEquals("3 going", evGoingLine(going = 3, mine = false))
        assertNull(evGoingLine(going = 0, mine = false))
    }

    @Test fun `the gathering's GOING tile says You before it counts people`() {
        assertEquals("You", evGoingTile(going = 1, mine = true))
        assertEquals("You and 1 other", evGoingTile(going = 2, mine = true))
        assertEquals("You and 6 others", evGoingTile(going = 7, mine = true))
        assertEquals("1 person", evGoingTile(going = 1, mine = false))
        assertEquals("3 people", evGoingTile(going = 3, mine = false))
        assertNull(evGoingTile(going = 0, mine = false))
    }

    @Test fun `an empty list points to the calendar above, with no second route (C5, Android #23) — iOS's words`() {
        assertEquals("Nothing on this day" to "The calendar above holds every gathering.", eventsEmptyWords(segment = 0, hasFilters = false))
        assertEquals("Nothing on this day" to "The calendar above holds every gathering.", eventsEmptyWords(segment = 1, hasFilters = false))
        assertEquals("No RSVPs yet" to "Tap an event to say you'll be there.", eventsEmptyWords(segment = 2, hasFilters = false))
        assertEquals("No events match" to "Try a different search or category.", eventsEmptyWords(segment = 2, hasFilters = true))
    }
}
