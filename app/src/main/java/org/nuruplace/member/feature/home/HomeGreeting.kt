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
    /** "Happy Lord's Day" on a Sunday; otherwise by the hour on the church's
     *  clock — the one the liturgy card keeps (the server's partOf: evening
     *  from 16, night from 21 until 4) — so the greeting and the card say the
     *  same part of the day (EXPERIENCE.md §9.3 rule 3). Ben read "Good
     *  afternoon" over an "EVENING" card at 16:32, and "Good morning" sat
     *  over "NIGHT" after midnight. [now] is the church's (Nairobi) time. */
    fun greeting(now: LocalDateTime): String = when {
        now.dayOfWeek == DayOfWeek.SUNDAY -> "Happy Lord's Day"
        now.hour < 4 -> "Rest well"
        now.hour < 12 -> "Good morning"
        now.hour < 16 -> "Good afternoon"
        now.hour < 21 -> "Good evening"
        else -> "Rest well"
    }

    /** The church's clock, which the liturgy is chosen by. */
    val CHURCH_ZONE: java.time.ZoneId = java.time.ZoneId.of("Africa/Nairobi")

    /** The header's kicker — Home's is the date (§8.1 rule 2), in the one
     *  date form (rule 8): "MON 5 OCT"; on a Sunday "THE LORD'S DAY · SUN 4
     *  OCT". It read "MONDAY · OCT 5 · EAT" — month first, and a time-zone
     *  code, which is data, not a word. */
    fun kicker(today: LocalDate): String {
        val date = org.nuruplace.member.util.NuruDates.day(today, today).uppercase(Locale.ENGLISH)
        return if (today.dayOfWeek == DayOfWeek.SUNDAY) "THE LORD'S DAY · $date" else date
    }
}
