// The plan the member is reading now — one fact, said the same way on Home's
// YOUR WEEK row and the Plans tab's header line (pathway docs/EXPERIENCE.md
// §6.1, §6.2). Pure, so ActivePlanTest pins it.
package org.nuruplace.member.feature.grow

import org.nuruplace.member.data.net.ReadingPlanRow
import java.time.Instant

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
fun planTodayLine(p: ReadingPlanRow, readToday: Boolean): String =
    if (readToday) "Done for today · Day ${planDay(p)} is next"
    else "Day ${planDay(p)} of ${p.dayCount} · today's reading"
