// EXPERIENCE.md §8.1 rule 8: one way to say a date — "Mon 5 Oct", the year
// only when it isn't this year — and a 12-hour time; a date-only value is the
// calendar date sent, never shifted by a zone.
package org.nuruplace.member.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NuruDatesTest {
    private val nairobi = ZoneId.of("Africa/Nairobi")
    private val today = LocalDate.of(2026, 10, 5)

    @Test fun `a day this year has no year, another year has its own`() {
        assertEquals("Mon 5 Oct", NuruDates.day(LocalDate.of(2026, 10, 5), today))
        assertEquals("Thu 25 Sep 2025", NuruDates.day(LocalDate.of(2025, 9, 25), today))
    }

    @Test fun `an instant reads in the zone, with a 12-hour time`() {
        val t = Instant.parse("2026-10-05T08:58:00Z") // 11:58 in Nairobi
        assertEquals("Mon 5 Oct · 11:58 AM", NuruDates.dayTime(t, nairobi, today))
        assertEquals("11:58 AM", NuruDates.time(t, nairobi))
        assertEquals("Tue 6 Oct", NuruDates.day(Instant.parse("2026-10-05T22:30:00Z"), nairobi, today)) // after midnight in Nairobi
    }

    @Test fun `a bare date is never shifted by a zone`() {
        assertEquals("Sat 31 Dec 2022", NuruDates.day("2022-12-31", ZoneId.of("America/Los_Angeles"), today))
        assertEquals(LocalDate.of(2026, 11, 5), NuruDates.date("2026-11-05", nairobi))
    }

    @Test fun `what can't be read is null — never the raw text`() {
        assertNull(NuruDates.day("soon", nairobi, today))
        assertNull(NuruDates.dayTime("", nairobi, today))
        assertNull(NuruDates.time(null as String?, nairobi))
    }
}
