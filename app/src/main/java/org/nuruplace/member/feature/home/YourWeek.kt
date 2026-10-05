// YOUR WEEK (pathway docs/EXPERIENCE.md §6.1, Cycle 2) — Home's one week
// block. Each pillar has one home, and Home points to it once: five rows in
// the journey's order — Pathway · Plans · Events · Giving · Cell — each the
// next thing in that pillar, one line of when or where it stands, and the
// place a tap opens. §6.1's table, word for word; iOS builds the same table.
//
// A row reads only what Home already has, or one cheap read beside it (the
// member's RSVPs, GET /giving/partnership and GET /giving/schedules). When
// its read failed the row says its "none" form — it never blocks the card.
// A row's forms are tried in the table's order; the first that holds shows.
//
// Pure, so YourWeekTest pins every form.
package org.nuruplace.member.feature.home

import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.data.net.CellSummary
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.HomeEventRow
import org.nuruplace.member.data.net.MyRsvp
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.ReadingPlanRow
import org.nuruplace.member.feature.events.evZdt
import org.nuruplace.member.feature.give.collectedOnLine
import org.nuruplace.member.feature.give.dueOverdue
import org.nuruplace.member.feature.give.money
import org.nuruplace.member.feature.give.partnerDate
import org.nuruplace.member.feature.give.pledgeCollectedOn
import org.nuruplace.member.feature.give.pledgeCollector
import org.nuruplace.member.feature.give.pledgeRoute
import org.nuruplace.member.feature.give.scheduleRoute
import org.nuruplace.member.feature.give.scheduleRunning
import org.nuruplace.member.feature.grow.activePlan
import org.nuruplace.member.feature.grow.planReadToday
import org.nuruplace.member.feature.grow.planTodayLine
import org.nuruplace.member.feature.grow.planDay
import org.nuruplace.member.feature.pathway.Journey
import org.nuruplace.member.feature.pathway.JourneyStage
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The five pillars, in the journey's order. */
enum class WeekPillar { PATHWAY, PLANS, EVENTS, GIVING, CELL }

/** Which of §6.1's forms a row is showing. */
enum class WeekForm(val pillar: WeekPillar) {
    /** The journey's next step. */
    JOURNEY(WeekPillar.PATHWAY),
    /** The journey not loaded — the level alone. */
    JOURNEY_UNKNOWN(WeekPillar.PATHWAY),
    /** An enrolled, unfinished plan: its day. */
    PLAN_DAY(WeekPillar.PLANS),
    /** No plan being read. */
    PLAN_START(WeekPillar.PLANS),
    /** A gathering this week the member is going to. */
    EVENT_GOING(WeekPillar.EVENTS),
    /** A gathering this week, not RSVP'd. */
    EVENT_NEXT(WeekPillar.EVENTS),
    /** Nothing this week. */
    EVENT_NONE(WeekPillar.EVENTS),
    /** A recurring gift — or a pledge's collector — prompts this week. */
    GIFT_COLLECTED(WeekPillar.GIVING),
    /** A pledge instalment due that no collector takes. */
    PLEDGE_DUE(WeekPillar.GIVING),
    /** Otherwise — the giving banner shows only with this form. */
    GIVE(WeekPillar.GIVING),
    /** In a cell. */
    CELL(WeekPillar.CELL),
    /** No cell. */
    CELL_FIND(WeekPillar.CELL),
}

/** Where a row's tap goes. */
sealed interface WeekDest {
    /** A screen over Home: a module, a plan's day, a pledge, a gift's sheet, the cell. */
    data class Screen(val route: String) : WeekDest

    /** A tab root, selected: Pathway, Plans, Events, Give, Partners, You (Community). */
    data class Tab(val route: String) : WeekDest

    /** One gathering — its end rides along (GET /events/{id} carries none). */
    data class Event(val occurrenceId: String, val endAt: String?) : WeekDest
}

/** One row: the next thing, one line of when or where it stands, and where a tap goes. */
data class WeekRow(val form: WeekForm, val title: String, val line: String, val dest: WeekDest)

object YourWeek {
    /** How far the week looks ahead: today and the seven days after — the
     *  server's own DUE window (PartnersService.DUE_WINDOW_DAYS). */
    const val WEEK_DAYS = 7L

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val DAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.ENGLISH)

    /** Pathway — always: the journey's next-step title, "Level N · " + its
     *  pill, and the journey's own destination (a step with nothing to tap —
     *  the exam not yet open, a level being prepared — opens the Pathway tab).
     *  Without the journey: "Your pathway" and the level alone. */
    fun pathway(journey: Journey?, enrolledLevel: Int?): WeekRow {
        val j = journey ?: return WeekRow(
            WeekForm.JOURNEY_UNKNOWN, "Open your pathway", enrolledLevel?.let { "Level $it" }.orEmpty(), WeekDest.Tab("pathway"),
        )
        val dest = j.next.action?.destination?.let { WeekDest.Screen(it.route) } ?: WeekDest.Tab("pathway")
        // Each row says its verb (EXPERIENCE.md §9.1 rule 3): a lesson to read
        // is "Continue · God & His Nature"; a level not yet begun is "Start
        // Level 1 · God & His Nature" — a first day leads with the path's
        // first step (rule 4). The other stages' titles are their own verbs
        // or facts ("Take the Level 1 exam", "Level 2 is being prepared").
        val title = if (j.stage == JourneyStage.LEARNING && j.next.action != null) {
            if (j.completedModules == 0) "Start Level ${j.levelNumber} · ${j.next.title}" else "Continue · ${j.next.title}"
        } else j.next.title
        return WeekRow(WeekForm.JOURNEY, title, "Level ${j.levelNumber} · ${j.pill}", dest)
    }

    /** Plans — an enrolled, unfinished plan: its title and today's word on it
     *  (planTodayLine — "Day X of Y · today's reading", or "Done for today ·
     *  Day X is next" once a day was finished today, as the Plans tab says),
     *  opening that day — or the plan, once today's reading is done; else
     *  "Start a reading plan" → the Plans tab. [sealedHere]: this phone sealed
     *  a plan day today. */
    fun plans(plans: List<ReadingPlanRow>?, sealedHere: Boolean = false, now: java.time.Instant = java.time.Instant.now()): WeekRow {
        val p = plans?.let(::activePlan) ?: return WeekRow(
            WeekForm.PLAN_START, "Start a reading plan", "A few minutes a day — with the whole family of God.", WeekDest.Tab("plans"),
        )
        val day = planDay(p)
        val readToday = planReadToday(p, sealedHere, now)
        val dest = if (readToday) WeekDest.Screen("plan/${p.planId}") else WeekDest.Screen("plan/${p.planId}/day/$day")
        // Its verb (§9.1 rule 3): "Done today ·" once today's day is read,
        // "Start ·" before the first day, "Continue ·" between.
        val verb = when {
            readToday -> "Done today"
            p.completedDays.isNullOrEmpty() && day == 1 -> "Start"
            else -> "Continue"
        }
        return WeekRow(WeekForm.PLAN_DAY, "$verb · ${p.title}", planTodayLine(p, readToday), dest)
    }

    /** One gathering, from whichever reads know it. */
    private data class Gathering(val id: String, val title: String, val start: ZonedDateTime, val end: String?, val rsvp: String?)

    /**
     * Events — this week (from [now] through the seventh day after): the
     * soonest gathering the member is going to, "EEE d MMM · h:mm a · You're
     * going"; else the soonest one they have not said no to (one they
     * declined only when nothing else is on), "EEE d MMM · h:mm a"; else "No
     * gatherings this week" → the Events tab. The gatherings are the church
     * calendar Home reads, its curated events and the member's RSVPs — the
     * member's own answers have the last word. [now] is on the church's
     * clock (EV_ZONE).
     */
    fun events(
        calendar: List<CalendarOccurrence>?,
        homeEvents: List<HomeEventRow>?,
        rsvps: List<MyRsvp>?,
        now: ZonedDateTime,
    ): WeekRow {
        val byId = LinkedHashMap<String, Gathering>()
        for (o in calendar.orEmpty()) {
            val start = evZdt(o.startAt) ?: continue
            byId[o.occurrenceId] = Gathering(o.occurrenceId, o.title, start, o.endAt.takeIf { it.isNotBlank() }, null)
        }
        for (e in homeEvents.orEmpty()) {
            val known = byId[e.occurrenceId]
            val start = known?.start ?: evZdt(e.startsAt) ?: continue
            byId[e.occurrenceId] = Gathering(
                e.occurrenceId, known?.title?.takeIf { it.isNotBlank() } ?: e.title, start, known?.end, e.myRsvp ?: known?.rsvp,
            )
        }
        for (r in rsvps.orEmpty()) {
            val status = r.status.takeIf { it.isNotBlank() } ?: continue
            val known = byId[r.eventId]
            if (known != null) byId[r.eventId] = known.copy(rsvp = status)
            else evZdt(r.occursAt)?.let { start -> byId[r.eventId] = Gathering(r.eventId, r.title, start, null, status) }
        }
        val lastDay = now.toLocalDate().plusDays(WEEK_DAYS)
        val week = byId.values
            .filter { it.title.isNotBlank() && !it.start.isBefore(now) && !it.start.toLocalDate().isAfter(lastDay) }
            .sortedBy { it.start }
        // Each row says its verb (§9.1 rule 3): "Going ·" a gathering the
        // member said yes to, "Join ·" one they haven't answered, and "See the
        // church calendar" in a quiet week.
        week.firstOrNull { it.rsvp == "going" }?.let { g ->
            return WeekRow(WeekForm.EVENT_GOING, "Going · ${g.title}", g.start.format(DAY_TIME), WeekDest.Event(g.id, g.end))
        }
        (week.firstOrNull { it.rsvp != "declined" } ?: week.firstOrNull())?.let { g ->
            return WeekRow(WeekForm.EVENT_NEXT, "Join · ${g.title}", g.start.format(DAY_TIME), WeekDest.Event(g.id, g.end))
        }
        return WeekRow(WeekForm.EVENT_NONE, "See the church calendar", "No gatherings this week", WeekDest.Tab("events"))
    }

    /**
     * Giving — from GET /giving/partnership (its DUE rows and pledges) and
     * GET /giving/schedules, by the Give code's own rules ([pledgeCollector],
     * Partners' "Collected on" rule [pledgeCollectedOn]), so this row and
     * Partners' DUE row never tell two stories:
     *
     *  1. a running recurring gift — or a pledge's collector — prompts this
     *     week (today through the seventh day after) asking for money: the
     *     pledge's title, or "Your weekly gift" / "Your monthly gift";
     *     "Collected on EEE d MMM"; its pledge / the gift's sheet. A pledge
     *     whose instalment its collector does not reach is not collected —
     *     it is due (2).
     *  2. a pledge instalment is due that no collector takes: the pledge's
     *     title; "KSh X due EEE d MMM", "KSh X overdue since EEE d MMM" once
     *     the server says it is past; Partners. Money already on its way is
     *     never asked for twice (iOS HomeWeek's rule, EXPERIENCE.md §7.2
     *     #10): X is only the uncovered rest, and an instalment every
     *     shilling of which is on its way is not owed at all.
     *  3. otherwise "Give", the rails line ([railsLine]), Give.
     *
     * Either read failed → "Give": nothing about a gift or a pledge is said
     * on a guess. [today] is the church's (Nairobi) day.
     */
    fun giving(partnership: Partnership?, schedules: List<GivingSchedule>?, railsLine: String, today: LocalDate): WeekRow {
        val give = WeekRow(WeekForm.GIVE, "Give", railsLine, WeekDest.Tab("give"))
        val p = partnership ?: return give
        val gifts = schedules ?: return give
        val pledgeOf = p.pledges.associateBy { it.pledgeId }
        // The instalments Partners lists as due, less those already fully on
        // their way, less those a collector takes.
        val owedByHand = p.due
            .filter { it.kind == "pledge" && it.action == "pay" && !it.fullyPending }
            .filter { d -> pledgeCollectedOn(d, pledgeOf[d.id]?.let { pledgeCollector(it, gifts) }) == null }
        val owedIds = owedByHand.map { it.id }.toSet()
        val lastDay = today.plusDays(WEEK_DAYS)

        data class Prompt(val gift: GivingSchedule, val day: LocalDate, val pledgeId: String?)
        val soonest = gifts.mapNotNull { s ->
            if (!scheduleRunning(s.status)) return@mapNotNull null
            val day = partnerDate(s.nextRunAt)?.takeIf { !it.isBefore(today) && !it.isAfter(lastDay) } ?: return@mapNotNull null
            val pledgeId = s.pledge?.pledgeId?.takeIf { it.isNotBlank() }
                ?: p.pledges.firstOrNull { !it.scheduleId.isNullOrBlank() && it.scheduleId == s.scheduleId }?.pledgeId
            // What the prompt asks: 0 is nothing (a pledge already covered);
            // a pledge's collector with no amount is stopping with its pledge.
            val asks = if (pledgeId != null) (s.nextAmountMinor ?: 0L) > 0L else s.nextAmountMinor != 0L
            if (!asks || (pledgeId != null && pledgeId in owedIds)) null else Prompt(s, day, pledgeId)
        }.minByOrNull { it.day }
        soonest?.let { pr ->
            val line = collectedOnLine(pr.day)
            // "Giving ·" — in motion, nothing to do (§9.1 rule 3).
            return if (pr.pledgeId != null) {
                val title = pledgeOf[pr.pledgeId]?.displayTitle ?: pr.gift.pledge?.title?.takeIf { it.isNotBlank() } ?: "Your pledge"
                WeekRow(WeekForm.GIFT_COLLECTED, "Giving · $title", line, WeekDest.Screen(pledgeRoute(pr.pledgeId)))
            } else {
                val title = if (pr.gift.frequency.equals("weekly", ignoreCase = true)) "Your weekly gift" else "Your monthly gift"
                WeekRow(WeekForm.GIFT_COLLECTED, "Giving · $title", line, WeekDest.Screen(scheduleRoute(pr.gift.scheduleId)))
            }
        }
        owedByHand.firstOrNull()?.let { d ->
            val title = d.title.ifBlank { null } ?: pledgeOf[d.id]?.displayTitle ?: "Pledge"
            // Only what is still uncovered — part of it may be on its way.
            val amount = money(d.uncoveredMinor, d.currency)
            val line = if (dueOverdue(d, today)) {
                "$amount overdue" + ((partnerDate(d.overdueSince) ?: partnerDate(d.dueOn))?.let { " since ${it.format(DAY)}" } ?: "")
            } else {
                "$amount due" + (partnerDate(d.dueOn)?.let { " ${it.format(DAY)}" } ?: "")
            }
            return WeekRow(WeekForm.PLEDGE_DUE, "Pay · $title", line, WeekDest.Tab("partners"))
        }
        return give
    }

    /** A first day on the path: Level 1, nothing done yet (Ben). It leads
     *  with the path's first step — YOUR WEEK's "Start Level 1 · …" — not a
     *  side task (EXPERIENCE.md §9.1 rule 4): "Reflection due today" sat
     *  above it, pressing a member on day one. */
    fun firstDay(journey: Journey?): Boolean =
        journey != null && journey.stage == JourneyStage.LEARNING && journey.levelPosition == 1 &&
            journey.completedModules == 0 && journey.totalModules > 0

    /** Whether "What needs you today" holds [n] back on a first day: the
     *  rhythm's side task waits; a person waiting (a message, an invite, the
     *  letter, the cell) never does. */
    fun heldOnFirstDay(n: org.nuruplace.member.data.net.HomeNudge, journey: Journey?): Boolean =
        firstDay(journey) && n.kind == "reflection_due"

    /**
     * "What needs you today" never repeats a YOUR WEEK row (EXPERIENCE.md §9.1
     * rule 3): a server nudge that points where a row already points — the
     * exam the Pathway row offers, the plan day the Plans row opens, the cell
     * the Cell row opens, the test in the module the Pathway row continues —
     * is the same ask twice on one page ("Take the Level 1 exam" over "Take
     * the Level 1 exam"). The row keeps it; the rail drops it.
     */
    fun repeats(n: org.nuruplace.member.data.net.HomeNudge, week: List<WeekRow>): Boolean {
        val routes = week.mapNotNull { (it.dest as? WeekDest.Screen)?.route }
        return when (n.route.ifBlank { n.kind }) {
            "level_exam", "level_review" -> n.levelNumber?.let { "exam/$it" in routes } ?: false
            "plan", "plan_day_due" -> n.planId?.let { id -> routes.any { it == "plan/$id" || it.startsWith("plan/$id/") } } ?: false
            "cell", "cell_gathering" -> week.any { it.form == WeekForm.CELL }
            "quiz", "quiz_in_progress" -> n.moduleId?.let { "module/$it" in routes } ?: false
            else -> false
        }
    }

    /** Cell — the member's own cell (GET /me/cell-summary): its name, "Next
     *  gathering EEE d MMM" or "Next gathering not set · N members", the cell
     *  page; no cell (or the read failed): "Find your cell" → Community. */
    fun cell(summary: CellSummary?): WeekRow {
        val c = summary?.cell ?: return WeekRow(
            WeekForm.CELL_FIND, "Find your cell", "Gather with believers near you.", WeekDest.Tab("you"),
        )
        val line = evZdt(c.next?.startAt)?.let { "Next gathering ${it.format(DAY)}" }
            ?: "Next gathering not set · ${c.members} ${if (c.members == 1) "member" else "members"}"
        return WeekRow(WeekForm.CELL, "Gather · ${c.name.ifBlank { "Your cell" }}", line, WeekDest.Screen("cell-info"))
    }
}
