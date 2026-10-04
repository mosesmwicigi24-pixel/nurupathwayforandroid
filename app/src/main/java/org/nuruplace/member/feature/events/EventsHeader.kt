// The Events tab's one header line and its quiet week (pathway docs/
// EXPERIENCE.md §6.2, §6.5) — pure, so EventsHeaderTest pins them. "In range"
// is what the tab loads (the calendar from today); a quiet week is one with
// nothing in it, and the header says so in the same words.
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
