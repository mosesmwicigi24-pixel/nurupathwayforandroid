// Home's greeting and date kicker (pathway docs/EXPERIENCE.md §8.2 #13) — the
// words iOS says (HomeView greeting / todayKicker). Seen: on Sunday 4 Oct iOS
// greeted "Happy Lord's Day, Ada." and Android "Good evening, Ada.".
package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HomeGreetingTest {
    private fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 30)

    @Test fun `on a Sunday it is the Lord's Day, at every hour`() {
        // Sunday 4 October 2026.
        for (hour in listOf(0, 6, 11, 12, 16, 17, 20, 21, 23)) {
            assertEquals("hour $hour", "Happy Lord's Day", HomeGreeting.greeting(at("2026-10-04", hour)))
        }
    }

    @Test fun `on other days the hour decides — iOS's boundaries`() {
        // Monday 5 October 2026.
        assertEquals("Good morning", HomeGreeting.greeting(at("2026-10-05", 0)))
        assertEquals("Good morning", HomeGreeting.greeting(at("2026-10-05", 11)))
        assertEquals("Good afternoon", HomeGreeting.greeting(at("2026-10-05", 12)))
        assertEquals("Good afternoon", HomeGreeting.greeting(at("2026-10-05", 16)))
        assertEquals("Good evening", HomeGreeting.greeting(at("2026-10-05", 17)))
        assertEquals("Good evening", HomeGreeting.greeting(at("2026-10-05", 20)))
        assertEquals("Rest well", HomeGreeting.greeting(at("2026-10-05", 21)))
        assertEquals("Rest well", HomeGreeting.greeting(LocalDateTime.of(2026, 10, 10, 23, 59)))
    }

    @Test fun `the kicker is the date — the Lord's Day on a Sunday`() {
        assertEquals("MON 5 OCT", HomeGreeting.kicker(LocalDate.parse("2026-10-05")))
        assertEquals("SAT 10 OCT", HomeGreeting.kicker(LocalDate.parse("2026-10-10")))
        assertEquals("THE LORD'S DAY · SUN 4 OCT", HomeGreeting.kicker(LocalDate.parse("2026-10-04")))
        assertEquals("THE LORD'S DAY · SUN 27 SEP", HomeGreeting.kicker(LocalDate.parse("2026-09-27")))
    }
}
