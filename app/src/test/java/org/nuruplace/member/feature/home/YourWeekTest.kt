// YOUR WEEK (pathway docs/EXPERIENCE.md §6.1) — Home's one week block, every
// form of every row in §6.1's words: Pathway · Plans · Events · Giving · Cell.
// A row whose read failed says its "none" form; it never blocks the card.
package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.data.net.CellSummary
import org.nuruplace.member.data.net.DueItem
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.HomeEventRow
import org.nuruplace.member.data.net.IntentPledge
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.MyRsvp
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.ReadingPlanRow
import org.nuruplace.member.feature.events.EV_ZONE
import org.nuruplace.member.feature.pathway.JourneyState
import java.time.LocalDate
import java.time.ZonedDateTime

class YourWeekTest {
    // Sunday 4 Oct 2026, 10:00 on the church's clock.
    private val now = ZonedDateTime.of(2026, 10, 4, 10, 0, 0, 0, EV_ZONE)
    private val today: LocalDate = now.toLocalDate()
    private val rails = "Tithe & offering · M-Pesa"

    // ── Pathway ──

    private fun summary(status: LevelStatus, done: Int, total: Int = 20) = PathwaySummary(
        currentLevel = 1,
        levels = listOf(
            PathwayLevel(1, "Foundations of Faith", totalModules = total, completedModules = done, status = status, examPublished = true),
            PathwayLevel(2, "Knowing God", status = LevelStatus.LOCKED),
        ),
    )

    private fun trail(done: Int, total: Int = 20) = (1..total).map { i ->
        LevelModule(
            moduleId = "m$i", levelNumber = 1, moduleSequenceNumber = i, title = "Module $i", completed = i <= done,
            status = when { i <= done -> ModuleStatus.COMPLETED; i == done + 1 -> ModuleStatus.NEXT; else -> ModuleStatus.LOCKED },
        )
    }

    @Test
    fun `Pathway — a first day leads with the path's first step, its verb first`() {
        // EXPERIENCE.md §9.1 rules 3–4: "Start Level 1 · God & His Nature", no "0 of".
        val first = YourWeek.pathway(JourneyState.derive(summary(LevelStatus.ACTIVE, 0), trail(0)), 1)
        assertEquals(WeekRow(WeekForm.JOURNEY, "Start Level 1 · Module 1", "Level 1 · 20 modules", WeekDest.Screen("module/m1"), WeekAsk("Begin", "Module 1")), first)
    }

    @Test
    fun `Pathway — the journey's next step, its level and pill, its destination`() {
        val exam = YourWeek.pathway(JourneyState.derive(summary(LevelStatus.COMPLETED, 20), trail(20)), 1)
        assertEquals(WeekRow(WeekForm.JOURNEY, "Take the Level 1 exam", "Level 1 · Exam ready", WeekDest.Screen("exam/1"), WeekAsk("Begin", "Take the Level 1 exam")), exam)

        val learning = YourWeek.pathway(JourneyState.derive(summary(LevelStatus.ACTIVE, 5), trail(5)), 1)
        assertEquals(WeekRow(WeekForm.JOURNEY, "Continue · Module 6", "Level 1 · 5 of 20 modules", WeekDest.Screen("module/m6"), WeekAsk("Continue", "Module 6")), learning)
    }

    @Test
    fun `Pathway — a step with nothing to tap opens the Pathway tab`() {
        val soon = YourWeek.pathway(JourneyState.derive(summary(LevelStatus.COMPLETED, 20).let {
            it.copy(levels = it.levels.map { l -> if (l.levelNumber == 1) l.copy(examPublished = false) else l })
        }), 1)
        assertEquals(WeekRow(WeekForm.JOURNEY, "Level 1 complete", "Level 1 · Exam opens soon", WeekDest.Tab("pathway")), soon)
        // Published with nothing to ask yet (`exam_available` false, §7.2 #1):
        // the same row — it never opens the exam, which would only refuse.
        val noQuestions = YourWeek.pathway(JourneyState.derive(summary(LevelStatus.COMPLETED, 20).let {
            it.copy(levels = it.levels.map { l -> if (l.levelNumber == 1) l.copy(examAvailable = false) else l })
        }, trail(20)), 1)
        assertEquals(WeekRow(WeekForm.JOURNEY, "Level 1 complete", "Level 1 · Exam opens soon", WeekDest.Tab("pathway")), noQuestions)
    }

    @Test
    fun `Pathway — without the journey, the level alone`() {
        assertEquals(WeekRow(WeekForm.JOURNEY_UNKNOWN, "Open your pathway", "Level 2", WeekDest.Tab("pathway")), YourWeek.pathway(null, 2))
        assertEquals(WeekRow(WeekForm.JOURNEY_UNKNOWN, "Open your pathway", "", WeekDest.Tab("pathway")), YourWeek.pathway(null, null))
    }

    // ── Plans ──

    @Test
    fun `Plans — the plan being read, its day, that day`() {
        val plans = listOf(
            ReadingPlanRow(planId = "done", title = "Hope", dayCount = 7, enrolled = true, completedAt = "2026-09-01T00:00:00Z"),
            ReadingPlanRow(planId = "rooted", title = "Rooted: 10 Days in the Psalms", dayCount = 10, currentDay = 3, enrolled = true),
        )
        assertEquals(
            WeekRow(WeekForm.PLAN_DAY, "Continue · Rooted: 10 Days in the Psalms", "Day 3 of 10 · today's reading", WeekDest.Screen("plan/rooted/day/3"), WeekAsk("Continue", "Rooted: 10 Days in the Psalms")),
            YourWeek.plans(plans),
        )
    }

    @Test
    fun `Plans — none being read — Start a reading plan, and a read that failed says so`() {
        val start = WeekRow(WeekForm.PLAN_START, "Start a reading plan", "A few minutes a day — with the whole family of God.", WeekDest.Tab("plans"))
        assertEquals(start, YourWeek.plans(listOf(ReadingPlanRow(planId = "x", title = "Joy", dayCount = 5))))
        // One story about today (Cycle 3's closing walk, B6): once a day of the
        // plan was finished today — on any phone, or sealed on this one — the
        // row says so and opens the plan, as Plans' streak card does.
        val now = java.time.Instant.parse("2026-10-05T12:00:00Z")
        val readToday = listOf(
            ReadingPlanRow(planId = "first", title = "First Steps", dayCount = 7, currentDay = 4, enrolled = true, lastDayFinishedAt = "2026-10-05T08:45:00Z"),
        )
        assertEquals(
            WeekRow(WeekForm.PLAN_DAY, "Done today · First Steps", "Day 3 done today · Day 4 next", WeekDest.Screen("plan/first")),
            YourWeek.plans(readToday, now = now),
        )
        val readYesterday = listOf(readToday[0].copy(lastDayFinishedAt = "2026-10-04T08:45:00Z"))
        assertEquals("Day 4 of 7 · today's reading", YourWeek.plans(readYesterday, now = now).line)
        assertEquals("Day 3 done today · Day 4 next", YourWeek.plans(readYesterday, sealedHere = true, now = now).line)
        assertEquals(start, YourWeek.plans(emptyList()))
        // The read never answered: never "Start a reading plan" for a member
        // who may well be reading one (EXPERIENCE.md §9.7 M4).
        assertEquals(
            WeekRow(WeekForm.PLAN_UNKNOWN, "Your reading plan", "Didn't load — pull down to try again", WeekDest.Tab("plans")),
            YourWeek.plans(null),
        )
    }

    // ── Events ──

    // Mon 5 Oct 09:00, Tue 6 Oct 18:00, Sun 11 Oct 09:00, Tue 13 Oct 09:00 (EAT).
    private val mon = CalendarOccurrence("mon", title = "Sunday Service", startAt = "2026-10-05T06:00:00Z", endAt = "2026-10-05T08:00:00Z")
    private val tue = CalendarOccurrence("tue", title = "Youth Night", startAt = "2026-10-06T15:00:00Z", endAt = "2026-10-06T17:00:00Z")
    private val nextSun = CalendarOccurrence("sun", title = "Harvest Sunday", startAt = "2026-10-11T06:00:00Z")
    private val farTue = CalendarOccurrence("far", title = "Leaders' Retreat", startAt = "2026-10-13T06:00:00Z")
    private val earlier = CalendarOccurrence("early", title = "Dawn Prayer", startAt = "2026-10-04T03:00:00Z")

    @Test
    fun `Events — a gathering the member is going to leads, You're going`() {
        val rsvps = listOf(MyRsvp(eventId = "tue", status = "going", title = "Youth Night", occursAt = tue.startAt))
        assertEquals(
            WeekRow(WeekForm.EVENT_GOING, "Going · Youth Night", "Tue 6 Oct · 6:00 PM", WeekDest.Event("tue", "2026-10-06T17:00:00Z")),
            YourWeek.events(listOf(mon, tue), emptyList(), rsvps, now),
        )
        // Home's own events carry the member's answer too.
        val home = listOf(HomeEventRow("mon", title = "Sunday Service", startsAt = mon.startAt, myRsvp = "going"))
        assertEquals(WeekForm.EVENT_GOING, YourWeek.events(listOf(mon, tue), home, null, now).form)
        // An RSVP to a gathering the calendar did not send still counts.
        val only = listOf(MyRsvp(eventId = "cell", status = "going", title = "Cell night", occursAt = "2026-10-07T16:00:00Z"))
        assertEquals(
            WeekRow(WeekForm.EVENT_GOING, "Going · Cell night", "Wed 7 Oct · 7:00 PM", WeekDest.Event("cell", null)),
            YourWeek.events(null, null, only, now),
        )
    }

    @Test
    fun `Events — otherwise the soonest gathering this week, its day and time`() {
        assertEquals(
            WeekRow(WeekForm.EVENT_NEXT, "Join · Sunday Service", "Mon 5 Oct · 9:00 AM", WeekDest.Event("mon", "2026-10-05T08:00:00Z"), WeekAsk("Join", "Sunday Service")),
            YourWeek.events(listOf(tue, mon), null, emptyList(), now),
        )
        // One the member declined steps aside for the next…
        val declined = listOf(MyRsvp(eventId = "mon", status = "declined"))
        assertEquals("Join · Youth Night", YourWeek.events(listOf(mon, tue), null, declined, now).title)
        // …unless it is all that is on.
        assertEquals("Join · Sunday Service", YourWeek.events(listOf(mon), null, declined, now).title)
        // The member's own answers have the last word over Home's copy.
        val home = listOf(HomeEventRow("mon", title = "Sunday Service", startsAt = mon.startAt, myRsvp = "going"))
        assertEquals(WeekForm.EVENT_NEXT, YourWeek.events(listOf(mon), home, listOf(MyRsvp(eventId = "mon", status = "maybe")), now).form)
    }

    @Test
    fun `Events — the week is today through the seventh day after, from now`() {
        // Sun 11 Oct is the seventh day: in. Tue 13 Oct: out. Earlier today: past.
        assertEquals("Join · Harvest Sunday", YourWeek.events(listOf(nextSun, farTue, earlier), null, null, now).title)
        val none = WeekRow(WeekForm.EVENT_NONE, "See the church calendar", "No gatherings this week", WeekDest.Tab("events"))
        assertEquals(none, YourWeek.events(listOf(farTue, earlier), null, null, now))
        // The calendar never answered: never "No gatherings this week" (§9.7 M4).
        assertEquals(
            WeekRow(WeekForm.EVENT_UNKNOWN, "The church calendar", "Didn't load — pull down to try again", WeekDest.Tab("events")),
            YourWeek.events(null, null, null, now),
        )
        // …though a gathering the member's own RSVPs know is still told.
        val rsvp = listOf(MyRsvp(eventId = "sun", status = "going", title = "Harvest Sunday", occursAt = nextSun.startAt))
        assertEquals("Going · Harvest Sunday", YourWeek.events(null, null, rsvp, now).title)
    }

    // ── Giving ──

    private fun gift(
        id: String = "g1",
        next: String = "2026-10-06T06:00:00Z",
        frequency: String = "monthly",
        status: String = "active",
        pledgeId: String? = null,
        nextAmount: Long? = 500_000,
    ) = GivingSchedule(
        scheduleId = id, status = status, frequency = frequency, nextRunAt = next, method = "mpesa", amountMinor = 500_000,
        pledge = pledgeId?.let { IntentPledge(it, "Roof") }, nextAmountMinor = nextAmount,
    )

    private val roof = Pledge(pledgeId = "roof", shape = "monthly", amountMinor = 500_000, dueDay = 5, status = "active", title = "Roof")

    private fun roofDue(dueOn: String = "2026-10-05", overdue: Boolean? = false, since: String? = null) =
        DueItem(kind = "pledge", id = "roof", title = "Roof", amountMinor = 500_000, dueOn = dueOn, action = "pay", overdue = overdue, overdueSince = since)

    private val give = WeekRow(WeekForm.GIVE, "Give", "Tithe & offering · M-Pesa", WeekDest.Tab("give"))

    @Test
    fun `Giving — a recurring gift prompting this week says when it is collected`() {
        val p = Partnership(isPartner = true)
        assertEquals(
            WeekRow(WeekForm.GIFT_COLLECTED, "Giving · Your monthly gift", "Collected on Tue 6 Oct", WeekDest.Screen("schedules?open=g1")),
            YourWeek.giving(p, listOf(gift()), rails, today),
        )
        assertEquals("Giving · Your weekly gift", YourWeek.giving(p, listOf(gift(frequency = "weekly")), rails, today).title)
        // The seventh day is in; the eighth is not; nor is a paused gift, or one asking nothing.
        assertEquals("Collected on Sun 11 Oct", YourWeek.giving(p, listOf(gift(next = "2026-10-11T06:00:00Z")), rails, today).line)
        assertEquals(give, YourWeek.giving(p, listOf(gift(next = "2026-10-12T06:00:00Z")), rails, today))
        assertEquals(give, YourWeek.giving(p, listOf(gift(nextAmount = 0)), rails, today))
        // The soonest prompt leads.
        assertEquals("g2", (YourWeek.giving(p, listOf(gift(), gift(id = "g2", next = "2026-10-05T06:00:00Z")), rails, today).dest as WeekDest.Screen).route.substringAfter("="))
    }

    @Test
    fun `Giving — a pledge's collector prompting this week, on or before its instalment, is collected — it opens the pledge`() {
        val p = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofDue()))
        assertEquals(
            WeekRow(WeekForm.GIFT_COLLECTED, "Giving · Roof", "Collected on Mon 5 Oct", WeekDest.Screen("partners-pledge/roof")),
            YourWeek.giving(p, listOf(gift(pledgeId = "roof", next = "2026-10-05T06:00:00Z")), rails, today),
        )
        // With nothing due yet on the pledge, its collector's prompt is still the week's.
        val ahead = Partnership(isPartner = true, pledges = listOf(roof))
        assertEquals(WeekForm.GIFT_COLLECTED, YourWeek.giving(ahead, listOf(gift(pledgeId = "roof")), rails, today).form)
    }

    @Test
    fun `Giving — an instalment no collector takes is due — Partners`() {
        val p = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofDue()))
        val due = WeekRow(WeekForm.PLEDGE_DUE, "Pay · Roof", "KSh 5,000 due Mon 5 Oct", WeekDest.Tab("partners"), WeekAsk("Pay", "Roof"))
        // No collector at all.
        assertEquals(due, YourWeek.giving(p, emptyList(), rails, today))
        // A collector whose prompt comes after the instalment does not take it (§6.4).
        assertEquals(due, YourWeek.giving(p, listOf(gift(pledgeId = "roof", next = "2026-10-08T06:00:00Z")), rails, today))
        // A paused collector, or one stopping with its pledge, takes nothing.
        assertEquals(due, YourWeek.giving(p, listOf(gift(pledgeId = "roof", status = "paused")), rails, today))
        assertEquals(due, YourWeek.giving(p, listOf(gift(pledgeId = "roof", next = "2026-10-05T06:00:00Z", nextAmount = null)), rails, today))
    }

    @Test
    fun `Giving — an instalment the server says is past reads overdue since`() {
        val p = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofDue(dueOn = "2026-10-04", overdue = true, since = "2026-09-05")))
        assertEquals(
            WeekRow(WeekForm.PLEDGE_DUE, "Pay · Roof", "KSh 5,000 overdue since Sat 5 Sep", WeekDest.Tab("partners"), WeekAsk("Pay", "Roof")),
            YourWeek.giving(p, emptyList(), rails, today),
        )
        // Its own currency, as Partners says it.
        val dollars = Partnership(isPartner = true, due = listOf(roofDue().copy(currency = "USD", amountMinor = 5_000)))
        assertEquals("US$ 50.00 due Mon 5 Oct", YourWeek.giving(dollars, emptyList(), rails, today).line)
    }

    @Test
    fun `Giving — money already on its way is not asked for twice`() {
        // iOS HomeWeek's rule (EXPERIENCE.md §7.2 #10), its tests mirrored:
        // every shilling on its way is not owed; part of it, only the rest.
        val roofOn6 = roofDue(dueOn = "2026-10-06")
        val onItsWay = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofOn6.copy(pendingMinor = 500_000)))
        assertEquals(give, YourWeek.giving(onItsWay, emptyList(), rails, today))
        val partly = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofOn6.copy(pendingMinor = 200_000)))
        assertEquals(
            WeekRow(WeekForm.PLEDGE_DUE, "Pay · Roof", "KSh 3,000 due Tue 6 Oct", WeekDest.Tab("partners"), WeekAsk("Pay", "Roof")),
            YourWeek.giving(partly, emptyList(), rails, today),
        )
        // A paused pledge's Resume is not a payment.
        val paused = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofOn6.copy(action = "resume")))
        assertEquals(give, YourWeek.giving(paused, emptyList(), rails, today))
        // Overdue, part on its way: the rest, overdue.
        val late = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofDue(dueOn = "2026-10-01", overdue = true).copy(pendingMinor = 100_000)))
        assertEquals("KSh 4,000 overdue since Thu 1 Oct", YourWeek.giving(late, emptyList(), rails, today).line)
    }

    @Test
    fun `the DUE row's in-flight money — uncovered rest, fully pending`() {
        val due = roofDue()
        assertEquals(500_000, due.uncoveredMinor)
        assertEquals(300_000, due.copy(pendingMinor = 200_000).uncoveredMinor)
        assertEquals(0, due.copy(pendingMinor = 600_000).uncoveredMinor)
        assertEquals(false, due.fullyPending)
        assertEquals(false, due.copy(pendingMinor = 200_000).fullyPending)
        assertEquals(true, due.copy(pendingMinor = 500_000).fullyPending)
        assertEquals(false, due.copy(pendingMinor = 500_000, action = "resume").fullyPending)
        assertEquals(false, due.copy(pendingMinor = 500_000, kind = "schedule").fullyPending)
    }

    @Test
    fun `Giving — a prompt that failed is told in Give's words, never Collected on (Ben)`() {
        val p = Partnership(isPartner = false)
        val failing = gift(frequency = "weekly").copy(
            lastFailure = org.nuruplace.member.data.net.GiftFailure(code = "insufficient_funds", reason = "There wasn't enough in the M-Pesa account.", hint = "Top up, then it tries again."),
        )
        assertEquals(
            WeekRow(WeekForm.GIFT_FAILING, "Giving · Your weekly gift", "There wasn't enough in the M-Pesa account.", WeekDest.Screen("schedules?open=g1")),
            YourWeek.giving(p, listOf(failing), rails, today),
        )
        // Even beyond this week, and even without the partnership read.
        val later = failing.copy(nextRunAt = "2026-10-20T06:00:00Z")
        assertEquals(WeekForm.GIFT_FAILING, YourWeek.giving(p, listOf(later), rails, today).form)
        assertEquals(WeekForm.GIFT_FAILING, YourWeek.giving(null, listOf(failing), rails, today).form)
    }

    @Test
    fun `Giving — a paused gift says why, in Give's words, and is never offered as a new Give (Cara)`() {
        val p = Partnership(isPartner = false)
        val stopped = gift(status = "paused").copy(
            pauseReason = "failures", consecutiveFailures = 3,
            lastFailure = org.nuruplace.member.data.net.GiftFailure(code = "unreachable", reason = "The M-Pesa prompt couldn't reach the phone."),
        )
        val row = YourWeek.giving(p, listOf(stopped), rails, today)
        assertEquals(
            WeekRow(WeekForm.GIFT_PAUSED, "Giving · Your monthly gift", "Paused · The M-Pesa prompt couldn't reach the phone.", WeekDest.Screen("schedules?open=g1")),
            row,
        )
        // Not the GIVE form, so Home's "Give now" banner stays away.
        assertTrue(row.form != WeekForm.GIVE)
        // A member's own pause with a date says when it comes back; without one, nothing is owed.
        val mine = gift(status = "paused").copy(pauseReason = "member", resumeOn = "2026-10-12")
        assertEquals("Paused · Resumes Mon 12 Oct", YourWeek.giving(p, listOf(mine), rails, today).line)
        assertEquals("Paused · Nothing is owed", YourWeek.giving(p, listOf(gift(status = "paused")), rails, today).line)
        // A pledge owed by hand still comes first.
        val owed = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofDue()))
        assertEquals(WeekForm.PLEDGE_DUE, YourWeek.giving(owed, listOf(stopped), rails, today).form)
    }

    @Test
    fun `Giving — the table's order, a collection this week before a pledge owed by hand`() {
        val p = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofDue()))
        assertEquals(WeekForm.GIFT_COLLECTED, YourWeek.giving(p, listOf(gift(id = "tithe", next = "2026-10-09T06:00:00Z")), rails, today).form)
    }

    @Test
    fun `Giving — otherwise, or either read failed, Give and the rails`() {
        assertEquals(give, YourWeek.giving(Partnership(), emptyList(), rails, today))
        val owed = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofDue()))
        // Nothing is said on a guess: without the gifts, a collector cannot be ruled out.
        assertEquals(give, YourWeek.giving(owed, null, rails, today))
        assertEquals(give, YourWeek.giving(null, listOf(gift()), rails, today))
    }

    // ── Cell ──

    private fun cell(next: String? = "2026-10-08T15:00:00Z", members: Int = 12, name: String = "Kilimani Cell") = CellSummary(
        CellSummary.Cell(cellGroupId = "c1", name = name, members = members, next = next?.let { CellSummary.Next(startAt = it) }),
    )

    @Test
    fun `Cell — the member's cell, its next gathering, the cell page`() {
        assertEquals(WeekRow(WeekForm.CELL, "Gather · Kilimani Cell", "Next gathering Thu 8 Oct", WeekDest.Screen("cell-info")), YourWeek.cell(cell()))
        assertEquals("Next gathering not set · 12 members", YourWeek.cell(cell(next = null)).line)
        assertEquals("Next gathering not set · 1 member", YourWeek.cell(cell(next = null, members = 1)).line)
        assertEquals("Gather · Your cell", YourWeek.cell(cell(name = "")).title)
    }

    @Test
    fun `Cell — no cell — Find your cell, and a read that failed says so`() {
        // "Ask to be connected" (EXPERIENCE.md §9.2 #12): it opened Community,
        // which has no way to find a cell.
        val find = WeekRow(WeekForm.CELL_FIND, "Find your cell", "Ask to be connected — tell the church where you live.", WeekDest.Screen("cell-connect"), WeekAsk("Ask", "Find your cell"))
        assertEquals(find, YourWeek.cell(CellSummary(null)))
        // The read never answered: never "Find your cell · Ask" — the navy
        // band's ask — for a member who may be in one (§9.7 M4).
        assertEquals(
            WeekRow(WeekForm.CELL_UNKNOWN, "Your cell", "Didn't load — pull down to try again", WeekDest.Screen("cell-info")),
            YourWeek.cell(null),
        )
        assertEquals(null, YourWeek.cell(null).ask)
    }

    // ── The week's one next step (owner, 2026-10-07: colour option A) ──

    private val going = WeekRow(WeekForm.EVENT_GOING, "Going · Sunday Service", "Sun 11 Oct · 9:00 AM", WeekDest.Event("sun", null))
    private val collected = WeekRow(WeekForm.GIFT_COLLECTED, "Giving · Your weekly gift", "Collected on Mon 12 Oct", WeekDest.Screen("schedule/s1"))
    private val gather = WeekRow(WeekForm.CELL, "Gather · Dev Cell A", "Next gathering not set · 5 members", WeekDest.Screen("cell-info"))
    private val planStart = WeekRow(WeekForm.PLAN_START, "Start a reading plan", "A few minutes a day — with the whole family of God.", WeekDest.Tab("plans"))

    @Test
    fun `the first row that asks the member to act now leads, and leaves the list`() {
        // Ada: the exam asks — it leads, and the rest keep their order.
        val exam = WeekRow(WeekForm.JOURNEY, "Take the Level 1 exam", "Level 1 · Exam ready", WeekDest.Screen("exam/1"), WeekAsk("Begin", "Take the Level 1 exam"))
        val join = WeekRow(WeekForm.EVENT_NEXT, "Join · Sunday Service", "Sun 11 Oct · 9:00 AM", WeekDest.Event("sun", null), WeekAsk("Join", "Sunday Service"))
        val (step, rest) = YourWeek.cardOrder(listOf(exam, planStart, join, collected, gather))
        assertEquals(exam, step)
        assertEquals(listOf(planStart, join, collected, gather), rest)
        // Eli: his level waits on the usher — the first row that asks is the
        // gathering he hasn't answered; a standing invitation never leads.
        val waiting = WeekRow(WeekForm.JOURNEY, "Level 2 is being prepared", "Level 1 · Exam passed", WeekDest.Tab("pathway"))
        val (eliStep, eliRest) = YourWeek.cardOrder(listOf(waiting, planStart, join, collected, gather))
        assertEquals(join, eliStep)
        assertEquals(listOf(waiting, planStart, collected, gather), eliRest)
    }

    @Test
    fun `with nothing to act on now there is no band`() {
        val waiting = WeekRow(WeekForm.JOURNEY, "Level 1 complete", "Level 1 · Exam opens soon", WeekDest.Tab("pathway"))
        val rows = listOf(waiting, planStart, going, collected, gather)
        assertEquals(null to rows, YourWeek.cardOrder(rows))
    }

    @Test
    fun `rows that only say where things stand never ask`() {
        assertEquals(null, YourWeek.cell(null, askedAt = "2026-10-05T09:00:00Z").ask)
        assertEquals(null, YourWeek.giving(null, null, rails, today).ask)
        assertEquals(null, YourWeek.plans(null).ask)
        assertEquals(null, YourWeek.events(null, null, null, now).ask)
        assertEquals(null, YourWeek.pathway(null, 1).ask)
    }

    @Test
    fun `the five rows are the five pillars, in the journey's order`() {
        assertEquals(
            listOf(WeekPillar.PATHWAY, WeekPillar.PLANS, WeekPillar.EVENTS, WeekPillar.GIVING, WeekPillar.CELL),
            listOf(
                YourWeek.pathway(null, 1), YourWeek.plans(null), YourWeek.events(null, null, null, now),
                YourWeek.giving(null, null, rails, today), YourWeek.cell(null),
            ).map { it.form.pillar },
        )
    }

    // ── What needs you today never repeats a row (EXPERIENCE.md §9.1 rule 3) ──

    private fun nudge(kind: String, vararg params: Pair<String, Any>) = org.nuruplace.member.data.net.HomeNudge(
        id = kind, kind = kind, title = kind,
        params = kotlinx.serialization.json.JsonObject(params.associate { (k, v) ->
            k to if (v is Int) kotlinx.serialization.json.JsonPrimitive(v) else kotlinx.serialization.json.JsonPrimitive(v.toString())
        }),
    )

    @Test
    fun `a first day holds the reflection back so the path's first step leads — never a person waiting`() {
        val first = JourneyState.derive(summary(LevelStatus.ACTIVE, 0), trail(0))
        assertTrue(YourWeek.firstDay(first))
        assertTrue(YourWeek.heldOnFirstDay(nudge("reflection_due"), first))
        for (k in listOf("chat_unread", "reading_invite", "letter_unread", "cell_gathering")) assertTrue(k, !YourWeek.heldOnFirstDay(nudge(k), first))
        val begun = JourneyState.derive(summary(LevelStatus.ACTIVE, 1), trail(1))
        assertTrue(!YourWeek.firstDay(begun))
        assertTrue(!YourWeek.heldOnFirstDay(nudge("reflection_due"), begun))
        assertTrue(!YourWeek.firstDay(null))
    }

    @Test
    fun `the exam nudge goes when the Pathway row offers the exam, and stays when it doesn't`() {
        val examWeek = listOf(YourWeek.pathway(JourneyState.derive(summary(LevelStatus.COMPLETED, 20), trail(20)), 1))
        assertTrue(YourWeek.repeats(nudge("level_review", "levelNumber" to 1), examWeek))
        val learningWeek = listOf(YourWeek.pathway(JourneyState.derive(summary(LevelStatus.ACTIVE, 5), trail(5)), 1))
        assertTrue(!YourWeek.repeats(nudge("level_review", "levelNumber" to 1), learningWeek))
    }

    @Test
    fun `the plan, the cell and the module's test are the rows' own — the rest of the rail stays`() {
        val plan = ReadingPlanRow(planId = "p1", title = "First Steps", dayCount = 7, currentDay = 2, completedDays = listOf(1), enrolled = true)
        val week = listOf(
            YourWeek.pathway(JourneyState.derive(summary(LevelStatus.ACTIVE, 5), trail(5)), 1),
            YourWeek.plans(listOf(plan)),
            WeekRow(WeekForm.CELL, "Dev Cell A", "Next gathering Mon 5 Oct", WeekDest.Screen("cell-info")),
        )
        assertTrue(YourWeek.repeats(nudge("plan_day_due", "planId" to "p1"), week))
        assertTrue(!YourWeek.repeats(nudge("plan_day_due", "planId" to "p9"), week))
        assertTrue(YourWeek.repeats(nudge("cell_gathering"), week))
        assertTrue(YourWeek.repeats(nudge("quiz_in_progress", "moduleId" to "m6"), week))
        assertTrue(!YourWeek.repeats(nudge("quiz_in_progress", "moduleId" to "m2"), week))
        for (k in listOf("reflection_due", "letter_unread", "reading_invite", "chat_unread")) assertTrue(k, !YourWeek.repeats(nudge(k), week))
        // The unread letter's nudge goes while Home draws the letter's card
        // (EXPERIENCE.md §9.7 C1) — and stays when there is no card.
        assertTrue(YourWeek.repeats(nudge("letter_unread"), week, letterCard = true))
        assertTrue(YourWeek.repeats(org.nuruplace.member.data.net.HomeNudge(id = "l", kind = "letter_unread", route = "letter"), week, letterCard = true))
        assertTrue(!YourWeek.repeats(nudge("letter_unread"), week, letterCard = false))
        // No cell: "Find your cell" is not the cell's gathering.
        assertTrue(!YourWeek.repeats(nudge("cell_gathering"), listOf(YourWeek.cell(null))))
    }

    @Test
    fun `Cell — once asked, the row says when it went to the pastor`() {
        val asked = YourWeek.cell(CellSummary(null), askedAt = "2026-10-05T09:30:00Z", today = today)
        assertEquals(WeekForm.CELL_FIND, asked.form)
        assertEquals("Sent to your pastor on Mon 5 Oct — they'll connect you", asked.line)
        assertEquals(WeekDest.Screen("cell-connect"), asked.dest)
    }
}
