// The Events tab's one header line and its quiet week (pathway docs/
// EXPERIENCE.md §6.2, §6.5), the tab it opens on and its series rows (§7.4
// #6–#8) — pure, so EventsHeaderTest pins them. "In range" is what the tab
// loads (the calendar from today); a quiet week is one with nothing in it,
// and the header says so in the same words.
package org.nuruplace.member.feature.events

import org.nuruplace.member.data.net.CalendarOccurrence
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NEXT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** Each gathering from [today] on (the church's day) with its start,
 *  soonest first. */
private fun fromToday(events: List<CalendarOccurrence>, today: LocalDate): List<Pair<CalendarOccurrence, ZonedDateTime>> =
    events.mapNotNull { o -> evZdt(o.startAt)?.let { o to it } }
        .filter { (_, start) -> !start.toLocalDate().isBefore(today) }
        .sortedBy { it.second }

/** The gatherings from [today] on, soonest first — what the tab has to
 *  show, and to filter. */
internal fun upcomingGatherings(events: List<CalendarOccurrence>, today: LocalDate): List<CalendarOccurrence> =
    fromToday(events, today).map { it.first }

/** Nothing from [today] on in range: the quiet week (§6.5) — the calm card,
 *  and no tabs, search or filters, since there is nothing to filter. */
internal fun eventsQuiet(events: List<CalendarOccurrence>, today: LocalDate): Boolean =
    fromToday(events, today).isEmpty()

/** The header's line of what matters now (§6.2): "Next: Sunday Service ·
 *  Sun 5 Oct", else "Nothing planned this week". */
internal fun eventsHeaderLine(events: List<CalendarOccurrence>, today: LocalDate): String =
    fromToday(events, today).firstOrNull()
        ?.let { (o, start) -> "Next: ${o.title} · ${start.format(NEXT_DAY)}" }
        ?: "Nothing planned this week"

/** "This week" (§6, both apps): today through the seventh day after — the
 *  week strip's days and the header's "N this week" count the same days. The
 *  strip ran two days back to eleven ahead and showed Sat–Fri, so "1 this
 *  week" (a Sunday) wasn't on it (Cycle 3 close walk E21). */
internal fun thisWeekDays(today: LocalDate): List<LocalDate> = (0L..7L).map { today.plusDays(it) }

/** [day] falls in [thisWeekDays]. */
internal fun inThisWeek(day: LocalDate, today: LocalDate): Boolean =
    !day.isBefore(today) && !day.isAfter(today.plusDays(7))

/** The tabs under the calendar: Today · Upcoming · My RSVPs. */
internal const val EVENTS_TAB_TODAY = 0
internal const val EVENTS_TAB_UPCOMING = 1
internal const val EVENTS_TAB_RSVPS = 2

/** The tab Events opens on (§7.4 #6): the first, in order, with something in
 *  it — it opened on "Today (0)" while the gatherings sat under Upcoming.
 *  Nothing anywhere: Today. */
internal fun firstEventsTab(todayCount: Int, upcomingCount: Int, rsvpCount: Int): Int = when {
    todayCount > 0 -> EVENTS_TAB_TODAY
    upcomingCount > 0 -> EVENTS_TAB_UPCOMING
    rsvpCount > 0 -> EVENTS_TAB_RSVPS
    else -> EVENTS_TAB_TODAY
}

private val CLOCK = Regex("""\b\d{1,2}:\d{2}""")

/** A series' line (§7.4 #8): the server's cadence already says the time
 *  ("Every Sunday · 9:00 AM"), so the next gathering's time is added only to
 *  a cadence without one — it read "Every Sunday · 9:00 AM · 9:00 AM". */
internal fun seriesLine(cadence: String, nextTime: String?): String {
    val c = cadence.trim()
    return when {
        c.isEmpty() -> nextTime.orEmpty()
        nextTime.isNullOrBlank() || CLOCK.containsMatchIn(c) -> c
        else -> "$c · $nextTime"
    }
}

/** "Series you follow" holds only the series the member follows; the rest
 *  sit under "More series", each with + Follow (§7.4 #7). */
internal fun <T> splitByFollowing(series: List<T>, following: (T) -> Boolean): Pair<List<T>, List<T>> =
    series.partition(following)

/** Events' two series rails, from the server's list as it comes: the server
 *  offers only a repeating series that meets again, and keeps one the member
 *  follows so it can be unfollowed (EXPERIENCE.md §9.2 #11). No second filter
 *  here: `next_at` looks 45 days ahead, so a missing one is not "ended" — a
 *  series meeting further out is still offered, and a followed one that has
 *  ended stays under "Series you follow" to unfollow (as on iOS). */
internal fun seriesRails(
    series: List<org.nuruplace.member.data.net.EventSeries>,
): Pair<List<org.nuruplace.member.data.net.EventSeries>, List<org.nuruplace.member.data.net.EventSeries>> =
    splitByFollowing(series) { it.following }
