// The plan the member is reading now — one fact, said the same way on Home's
// YOUR WEEK row and the Plans tab's header line (pathway docs/EXPERIENCE.md
// §6.1, §6.2). Pure, so ActivePlanTest pins it.
package org.nuruplace.member.feature.grow

import org.nuruplace.member.data.net.ReadingPlanRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The plan being read: the first enrolled, unfinished plan in the server's
 *  order (the Plans tab's first CONTINUE READING row). Null when none. */
fun activePlan(plans: List<ReadingPlanRow>): ReadingPlanRow? =
    plans.firstOrNull { it.enrolled && it.completedAt == null }

/** The day to read — the server's `current_day` (it moves past every
 *  finished day, never beyond the last), else the one after the finished
 *  days; held to 1..the plan's length. */
fun planDay(p: ReadingPlanRow): Int {
    val day = p.currentDay ?: ((p.completedDays?.size ?: 0) + 1)
    return day.coerceIn(1, p.dayCount.coerceAtLeast(1))
}

/** "Rooted: 10 Days in the Psalms · Day 1 of 10" — the Plans header's line;
 *  null when no plan is being read (the tagline then stands). */
fun activePlanLine(plans: List<ReadingPlanRow>): String? =
    activePlan(plans)?.let { p -> "${p.title} · Day ${planDay(p)} of ${p.dayCount}" }

/** A day of this plan was finished today, on the church's (Nairobi)
 *  calendar — by the server's `last_day_finished_at` (any phone), or by this
 *  phone's own note of a day it sealed ([sealedHere]). */
fun planReadToday(p: ReadingPlanRow, sealedHere: Boolean = false, now: Instant = Instant.now()): Boolean =
    sealedHere || p.lastDayFinishedAt?.let(::parseInstant)?.let(::nairobiEpochDay) == nairobiEpochDay(now)

/**
 * One story about today for a plan in progress, on Home and on Plans
 * (Cycle 3's closing walk, B6): its day is "today's reading" only while today's
 * reading is still to do; once a day of it was finished today, it is done
 * for today and the next day waits — Home said "Day 4 of 7 · today's reading"
 * beside Plans' "Today's reading is done 🔥".
 */
fun planTodayLine(p: ReadingPlanRow, readToday: Boolean, now: Instant = Instant.now()): String =
    planDoneOrPausedLine(p, readToday, now) ?: "Day ${planDay(p)} of ${p.dayCount} · today's reading"

/** The Plans tab's continue card: the same two stories as Home's row, else
 *  "Today · " and the plan's own subtitle. */
fun planCardLine(p: ReadingPlanRow, readToday: Boolean, now: Instant = Instant.now()): String =
    planDoneOrPausedLine(p, readToday, now) ?: "Today · ${p.subtitle ?: "Day ${planDay(p)} of ${p.dayCount}"}"

/** The two lines Home and Plans share: today's day read, or a pause named. */
private fun planDoneOrPausedLine(p: ReadingPlanRow, readToday: Boolean, now: Instant): String? {
    val day = planDay(p)
    return when {
        // "Day 3 done today · Day 4 next" (EXPERIENCE.md §9.2 #3) — the day
        // read today and the one that waits, in one breath.
        readToday && day > 1 -> "Day ${day - 1} done today · Day $day next"
        readToday -> "Done today · Day $day next"
        else -> planPausedOn(p, now)?.let { pauseLine(it, day, now) }
    }
}

private val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/**
 * The church's (Nairobi) day the member paused [p]: the day its last day
 * was finished, when that was two or more days ago — not yesterday's
 * reading, not today's. Null while it's being read, once it's finished, and
 * before any day is (the server says only when a day was finished).
 */
fun planPausedOn(p: ReadingPlanRow, now: Instant = Instant.now()): LocalDate? {
    if (!p.enrolled || p.completedAt != null) return null
    val last = p.lastDayFinishedAt?.let(::parseInstant)?.atZone(NAIROBI)?.toLocalDate() ?: return null
    val today = now.atZone(NAIROBI).toLocalDate()
    return last.takeIf { !it.isAfter(today.minusDays(2)) }
}

/**
 * A pause named kindly, once (EXPERIENCE.md §9.1 rule 5): when, and the day
 * that waits — "You paused on Thursday — Day 2 is waiting" — never a count
 * of days missed. The weekday within the week; "Thu 24 Sep" further back.
 */
fun pauseLine(pausedOn: LocalDate, waitingDay: Int, now: Instant = Instant.now()): String {
    val today = now.atZone(NAIROBI).toLocalDate()
    val day = if (pausedOn.isAfter(today.minusDays(7))) pausedOn.format(WEEKDAY) else pausedOn.format(DAY_MONTH)
    return "You paused on $day — Day $waitingDay is waiting"
}

/**
 * "Today" means today (EXPERIENCE.md §9.7 M6): a plan day is today's only
 * when it is the day the member is on ([planDay]) and the plan isn't paused
 * ([planPausedOn]) — Day 4, waiting since Monday, read "TODAY'S JOURNEY" and
 * "TODAY'S READING". Unknown (the plans list didn't answer): not today's.
 */
fun planDayIsToday(row: ReadingPlanRow?, dayNumber: Int, now: Instant = Instant.now()): Boolean =
    row != null && row.enrolled && row.completedAt == null && planDay(row) == dayNumber && planPausedOn(row, now) == null

/** The day hub's kicker: "TODAY'S JOURNEY · 3 PARTS" on today's day, else
 *  "THE JOURNEY · 3 PARTS". */
fun dayJourneyKicker(today: Boolean, parts: Int): String =
    "${if (today) "TODAY'S JOURNEY" else "THE JOURNEY"} · $parts PART${if (parts == 1) "" else "S"}"

/** The Word's kicker: "TODAY'S READING" on today's day, else "THE READING". */
fun dayReadingKicker(today: Boolean): String = if (today) "TODAY'S READING" else "THE READING"

