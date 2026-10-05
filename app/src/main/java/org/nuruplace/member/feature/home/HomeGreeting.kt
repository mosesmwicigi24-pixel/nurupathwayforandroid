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

    /** The header's kicker — Home's is the date (§8.1 rule 2): "MONDAY · OCT 5
     *  · EAT"; on a Sunday "SUNDAY · THE LORD'S DAY · OCT 4". */
    fun kicker(today: LocalDate): String {
        val monthDay = today.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
        return if (today.dayOfWeek == DayOfWeek.SUNDAY) "SUNDAY · THE LORD'S DAY · $monthDay"
        else today.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)).uppercase(Locale.ENGLISH) + " · $monthDay · EAT"
    }
}
