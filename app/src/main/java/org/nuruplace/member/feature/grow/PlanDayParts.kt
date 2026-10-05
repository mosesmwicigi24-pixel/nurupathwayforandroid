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
import org.nuruplace.member.data.net.ReadingPlanRow
import java.time.Instant
import java.time.OffsetDateTime

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

/** The plan page's gold button, in iOS's words (one product): "Begin Day 1"
 *  until anything of the plan is done ([planBegun]), then "Continue · Day N"
 *  on the day the member is on; "Read again" once every day is finished. */
internal fun planCtaLabel(begun: Boolean, allDone: Boolean, day: Int): String = when {
    allDone -> "Read again"
    begun -> "Continue · Day $day"
    else -> "Begin Day 1"
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
 * starts clean. A day sealed on another phone reaches this card through the
 * server instead ([planDayFinishedToday], `last_day_finished_at`). iOS keeps
 * the same note (PlanDayLog, "nuru.plans.daySealedOn").
 */
internal object PlanDayLog {
    fun noteSealed(now: Instant = Instant.now()) {
        AppPrefs.planDaySealedOn = nairobiEpochDay(now)
    }

    fun sealedToday(now: Instant = Instant.now()): Boolean = isSealedToday(AppPrefs.planDaySealedOn, now)
}

/** The note says today, on the church's (Nairobi) calendar. */
internal fun isSealedToday(sealedOn: Long?, now: Instant): Boolean = sealedOn != null && sealedOn == nairobiEpochDay(now)

/**
 * A day of any plan the member has started was finished today, on the
 * church's (Nairobi) calendar, by the server's word — `last_day_finished_at`
 * on GET /growth/plans, the moment the last part of a fully-read day was read.
 * It carries a day finished on another phone (§7.4 #4: the tick on every
 * phone); the card ticks today when this OR [PlanDayLog] says so. A missing or
 * unreadable timestamp is not today.
 */
internal fun planDayFinishedToday(plans: List<ReadingPlanRow>, now: Instant = Instant.now()): Boolean {
    val today = nairobiEpochDay(now)
    return plans.any { p -> p.enrolled && p.lastDayFinishedAt?.let(::parseInstant)?.let(::nairobiEpochDay) == today }
}

/** An ISO-8601 instant — "…Z" as the server sends it, or with an offset. Null when unreadable. */
internal fun parseInstant(iso: String): Instant? =
    runCatching { Instant.parse(iso) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()

/**
 * The one streak (EXPERIENCE.md §9.2 #3): the server's count of days with the
 * rhythm (GET /me/achievements — any prayer, Word or reflection; recomputed
 * overnight, so it doesn't hold today yet). Home's rhythm card and the Plans
 * card showed it under two looks and two counts — "1-day streak" on Plans
 * beside nothing on Home. Both now say it in these words, by this rule.
 */
internal object StreakWords {
    /** Today counts once the member was active today: at least 1. */
    fun days(serverCount: Int, activeToday: Boolean): Int =
        if (activeToday) maxOf(serverCount, 1) else maxOf(serverCount, 0)

    fun label(days: Int): String = "$days-day streak"
}

/** What the Plans streak card shows (EXPERIENCE.md §7.4 #4). */
internal data class StreakView(val count: Int, val todayMarked: Boolean, val line: String)

/**
 * The streak card's count, today's mark and its line — the same rule as iOS
 * (StreakWords). [count] — the server's streak (GET /me/achievements,
 * recomputed overnight, so it does not hold today yet); [todayDone] — a plan
 * day sealed today, seen by this phone ([PlanDayLog]) or by any other
 * ([planDayFinishedToday]; it was the rhythm's `word`, so reading one part
 * ticked today beside "0-day streak"); [today] — the day
 * under way in the plan being read, as parts (null with no plan in progress).
 *
 * A sealed day is a day of the streak, so beside the tick the count is at
 * least 1. The line: "Today's reading is done 🔥" once a day is sealed today;
 * else today's progress while the day is under way ("Today: 2 of 3 parts");
 * else the invitation.
 */
internal fun streakView(count: Int, todayDone: Boolean, today: DayParts?, activeToday: Boolean = todayDone): StreakView {
    // The one streak's count (StreakWords): a day read — or any of the
    // rhythm done today ([activeToday]) — counts today, as Home counts it.
    val shown = StreakWords.days(count, todayDone || activeToday)
    // No emoji (§8.1 rule 7): the card's flame is the Lucide tile beside it.
    val line = when {
        todayDone -> "Today's reading is done"
        today != null && today.partway -> "Today: ${today.done} of ${today.total} parts"
        shown > 0 -> "Read today to keep it alive"
        else -> "Read today to start your streak"
    }
    return StreakView(count = shown, todayMarked = todayDone, line = line)
}
