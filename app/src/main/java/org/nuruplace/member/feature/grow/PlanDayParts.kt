// A plan day's parts — the day hub's story arc (Watch/Listen · The Word ·
// Respond · Talk it Over) — counted the same way wherever a day's progress
// shows: the day hub, the plan's page ("Continue · Day 1", "1 part left") and
// the Plans streak card ("Today: 2 of 3 parts") (pathway docs/EXPERIENCE.md
// §7.4 #1, #2, #4). Pure, so PlanDayPartsTest pins every rule; iOS says the
// same words.
package org.nuruplace.member.feature.grow

import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.net.PlanSegment
import org.nuruplace.member.data.net.ReadingPlanDay
import org.nuruplace.member.data.net.ReadingPlanDetail
import java.time.Instant

/** How many of a day's parts are done, of how many. */
internal data class DayParts(val done: Int, val total: Int) {
    val left: Int get() = (total - done).coerceAtLeast(0)

    /** Begun and not yet finished. */
    val partway: Boolean get() = done in 1 until total
}

/** A day's parts, done and total — the hub's own grouping ([hubParts]); a
 *  part is done when every one of its segments is (the server's word). */
internal fun dayParts(segments: List<PlanSegment>): DayParts {
    val parts = hubParts(segments)
    return DayParts(done = parts.count { p -> p.segs.all { it.completed } }, total = parts.size)
}

internal fun dayParts(day: ReadingPlanDay): DayParts = dayParts(day.segments.orEmpty())

/** "1 part left" · "2 parts left". */
internal fun partsLeftLabel(left: Int): String = if (left == 1) "1 part left" else "$left parts left"

/** The pill on the plan page's row for the day the member is on: "Start"
 *  until a part of that day is done, then what is left ("1 part left"). */
internal fun nextDayPill(day: ReadingPlanDay): String {
    val p = dayParts(day)
    return if (p.partway) partsLeftLabel(p.left) else "Start"
}

/** Anything of the plan done — a whole day, or any part of one (the same test
 *  iOS's plan page uses). It used to count whole days only, so a member with
 *  two of Day 1's three parts done read "Start plan". */
internal fun planBegun(plan: ReadingPlanDetail): Boolean =
    plan.days.any { d -> d.completed == true || d.segments.orEmpty().any { it.completed } }

/** The plan page's gold button: "Start plan" until anything of the plan is
 *  done ([planBegun]), then "Continue · Day N" on the day the member is on;
 *  "Review plan" once every day is finished. */
internal fun planCtaLabel(begun: Boolean, allDone: Boolean, day: Int): String = when {
    allDone -> "Review plan"
    begun -> "Continue · Day $day"
    else -> "Start plan"
}

/** The Talk it Over part's segments for a day — what "I've talked it over"
 *  (or a post in the conversation) completes. Empty when the day has none. */
internal fun talkPartSegments(day: ReadingPlanDay): List<PlanSegment> =
    hubParts(day.segments.orEmpty()).firstOrNull { it.tag == "talk" }?.segs.orEmpty()

/** The day the member is on in [plan] (the server's `next_day`), as parts;
 *  null when the plan is finished, the day isn't in the payload, or it has
 *  no parts. */
internal fun todayParts(plan: ReadingPlanDetail?): DayParts? {
    val p = plan ?: return null
    val n = p.nextDay ?: return null
    val day = p.days.firstOrNull { it.dayNumber == n } ?: return null
    return dayParts(day).takeIf { it.total > 0 }
}

/**
 * The Nairobi day on which this phone last saw the server seal a plan day
 * (§7.4 #4) — the Plans streak card ticks today only then. Written only from
 * the server's own answer (the last part's `day_complete` ack, or the day's
 * complete-day 200 — [announceDaySealed], the hub's seal), never from a
 * guess; forgotten at sign-out (ApiClient.signOutLocally) so the next member
 * starts clean. A day sealed on another phone is not ticked here: the card
 * under-claims, it never ticks a day that wasn't. iOS keeps the same note
 * (PlanDayLog, "nuru.plans.daySealedOn").
 */
internal object PlanDayLog {
    fun noteSealed(now: Instant = Instant.now()) {
        AppPrefs.planDaySealedOn = nairobiEpochDay(now)
    }

    fun sealedToday(now: Instant = Instant.now()): Boolean = isSealedToday(AppPrefs.planDaySealedOn, now)
}

/** The note says today, on the church's (Nairobi) calendar. */
internal fun isSealedToday(sealedOn: Long?, now: Instant): Boolean = sealedOn != null && sealedOn == nairobiEpochDay(now)

/** What the Plans streak card shows (EXPERIENCE.md §7.4 #4). */
internal data class StreakView(val count: Int, val todayMarked: Boolean, val line: String)

/**
 * The streak card's count, today's mark and its line — the same rule as iOS
 * (StreakWords). [count] — the server's streak (GET /me/achievements,
 * recomputed overnight, so it does not hold today yet); [todayDone] — a plan
 * day the server sealed today ([PlanDayLog]; it was the rhythm's `word`, so
 * reading one part ticked today beside "0-day streak"); [today] — the day
 * under way in the plan being read, as parts (null with no plan in progress).
 *
 * A sealed day is a day of the streak, so beside the tick the count is at
 * least 1. The line: "Today's reading is done 🔥" once a day is sealed today;
 * else today's progress while the day is under way ("Today: 2 of 3 parts");
 * else the invitation.
 */
internal fun streakView(count: Int, todayDone: Boolean, today: DayParts?): StreakView {
    val shown = if (todayDone) maxOf(count, 1) else maxOf(count, 0)
    val line = when {
        todayDone -> "Today's reading is done 🔥"
        today != null && today.partway -> "Today: ${today.done} of ${today.total} parts"
        shown > 0 -> "Read today to keep it alive 🔥"
        else -> "Read today to start your streak 🔥"
    }
    return StreakView(count = shown, todayMarked = todayDone, line = line)
}
