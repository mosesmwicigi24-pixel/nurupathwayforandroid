// One way to say a date and a time (EXPERIENCE.md §8.1 rule 8): "Mon 5 Oct",
// with the year only when it isn't this year ("Fri 25 Sep 2025"), and a
// 12-hour time ("11:58 AM"). Cycle 3's closing walk found five forms on the
// member's screens — "Sun, Aug 30", "Jul 17", "Sunday, October 11",
// "Oct 5, 2026", a 24-hour "10:08" — and two on one Give screen ("Next 12
// Oct" over "next Mon 12 Oct"). Every member-facing date goes through here.
//
// A date-only value (a birthday, a due day, a pledge's start) is the calendar
// date the server sent — never shifted by a time zone; an instant is read in
// the phone's own zone, the member's local time.
package org.nuruplace.member.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object NuruDates {
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val BARE_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** "Mon 5 Oct", or "Mon 5 Oct 2025" when it isn't [today]'s year. */
    fun day(date: LocalDate, today: LocalDate = LocalDate.now()): String =
        date.format(if (date.year == today.year) DAY else DAY_YEAR)

    /** "Mon 5 Oct", or with its year — for a caller that decides the year itself. */
    fun day(date: LocalDate, withYear: Boolean): String = date.format(if (withYear) DAY_YEAR else DAY)

    /** [instant]'s day in [zone]. */
    fun day(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String =
        day(instant.atZone(zone).toLocalDate(), today)

    /** "11:58 AM" in [zone]. */
    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = TIME.format(instant.atZone(zone))

    /** "Mon 5 Oct · 11:58 AM". */
    fun dayTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String =
        "${day(instant, zone, today)} · ${time(instant, zone)}"

    /** "Sun 11 Oct · 2:00 PM" for a wall-clock moment (a gathering's local start). */
    fun dayTime(local: LocalDateTime, today: LocalDate = LocalDate.now()): String =
        "${day(local.toLocalDate(), today)} · ${TIME.format(local)}"

    /** "2:00 PM" for a wall-clock time. */
    fun time(local: LocalTime): String = TIME.format(local)

    /** An instant the server sent ("…Z" or with an offset); null when unreadable. */
    fun instant(iso: String?): Instant? {
        val s = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
    }

    /**
     * The calendar day of a value the server sent: a bare "2026-11-05" is
     * that date, never shifted; an instant is its day in [zone]. Null when
     * unreadable.
     */
    fun date(iso: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
        val s = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (BARE_DATE.matches(s)) return runCatching { LocalDate.parse(s) }.getOrNull()
        return instant(s)?.atZone(zone)?.toLocalDate() ?: runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
    }

    /** [day] of a value the server sent, or null when it can't be read —
     *  never the raw text. */
    fun day(iso: String?, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String? =
        date(iso, zone)?.let { day(it, today) }

    /** [dayTime] of an instant the server sent, or null. */
    fun dayTime(iso: String?, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String? =
        instant(iso)?.let { dayTime(it, zone, today) }

    /** [time] of an instant the server sent, or null. */
    fun time(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? = instant(iso)?.let { time(it, zone) }
}
