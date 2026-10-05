// Home's greeting and its date kicker — the same words iOS says (HomeView
// greeting / todayKicker), kept pure so HomeGreetingTest pins them
// (pathway docs/EXPERIENCE.md §8.2 #13). Sunday is the Lord's Day on both
// apps: "Happy Lord's Day, Ada." under "SUNDAY · THE LORD'S DAY · OCT 4" —
// Android said "Good evening, Ada." on a Sunday. The greeting belongs to
// Home alone (§8.1 rule 2).
package org.nuruplace.member.feature.home

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object HomeGreeting {
    /** "Happy Lord's Day" on a Sunday; otherwise by the hour — morning before
     *  noon, afternoon before five, evening before nine, then "Rest well". */
    fun greeting(now: LocalDateTime): String = when {
        now.dayOfWeek == DayOfWeek.SUNDAY -> "Happy Lord's Day"
        now.hour < 12 -> "Good morning"
        now.hour < 17 -> "Good afternoon"
        now.hour < 21 -> "Good evening"
        else -> "Rest well"
    }

    /** The header's kicker — Home's is the date (§8.1 rule 2), in the one
     *  date form (rule 8): "MON 5 OCT"; on a Sunday "THE LORD'S DAY · SUN 4
     *  OCT". It read "MONDAY · OCT 5 · EAT" — month first, and a time-zone
     *  code, which is data, not a word. */
    fun kicker(today: LocalDate): String {
        val date = org.nuruplace.member.util.NuruDates.day(today, today).uppercase(Locale.ENGLISH)
        return if (today.dayOfWeek == DayOfWeek.SUNDAY) "THE LORD'S DAY · $date" else date
    }
}
