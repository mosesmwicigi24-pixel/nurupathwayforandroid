// YOUR WEEK (pathway docs/EXPERIENCE.md §6.1) — Home's one week block, every
// form of every row in §6.1's words: Pathway · Plans · Events · Giving · Cell.
// A row whose read failed says its "none" form; it never blocks the card.
package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
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
    fun `Pathway — the journey's next step, its level and pill, its destination`() {
        val exam = YourWeek.pathway(JourneyState.derive(summary(LevelStatus.COMPLETED, 20), trail(20)), 1)
        assertEquals(WeekRow(WeekForm.JOURNEY, "Take the Level 1 exam", "Level 1 · Exam ready", WeekDest.Screen("exam/1")), exam)

        val learning = YourWeek.pathway(JourneyState.derive(summary(LevelStatus.ACTIVE, 5), trail(5)), 1)
        assertEquals(WeekRow(WeekForm.JOURNEY, "Module 6", "Level 1 · 5 of 20 modules", WeekDest.Screen("module/m6")), learning)
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
        assertEquals(WeekRow(WeekForm.JOURNEY_UNKNOWN, "Your pathway", "Level 2", WeekDest.Tab("pathway")), YourWeek.pathway(null, 2))
        assertEquals(WeekRow(WeekForm.JOURNEY_UNKNOWN, "Your pathway", "", WeekDest.Tab("pathway")), YourWeek.pathway(null, null))
    }

    // ── Plans ──

    @Test
    fun `Plans — the plan being read, its day, that day`() {
        val plans = listOf(
            ReadingPlanRow(planId = "done", title = "Hope", dayCount = 7, enrolled = true, completedAt = "2026-09-01T00:00:00Z"),
            ReadingPlanRow(planId = "rooted", title = "Rooted: 10 Days in the Psalms", dayCount = 10, currentDay = 3, enrolled = true),
        )
        assertEquals(
            WeekRow(WeekForm.PLAN_DAY, "Rooted: 10 Days in the Psalms", "Day 3 of 10 · today's reading", WeekDest.Screen("plan/rooted/day/3")),
            YourWeek.plans(plans),
        )
    }

    @Test
    fun `Plans — none being read, or the read failed — Start a reading plan`() {
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
            WeekRow(WeekForm.PLAN_DAY, "First Steps", "Done for today · Day 4 is next", WeekDest.Screen("plan/first")),
            YourWeek.plans(readToday, now = now),
        )
        val readYesterday = listOf(readToday[0].copy(lastDayFinishedAt = "2026-10-04T08:45:00Z"))
        assertEquals("Day 4 of 7 · today's reading", YourWeek.plans(readYesterday, now = now).line)
        assertEquals("Done for today · Day 4 is next", YourWeek.plans(readYesterday, sealedHere = true, now = now).line)
        assertEquals(start, YourWeek.plans(emptyList()))
        assertEquals(start, YourWeek.plans(null))
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
            WeekRow(WeekForm.EVENT_GOING, "Youth Night", "Tue 6 Oct · 6:00 PM · You're going", WeekDest.Event("tue", "2026-10-06T17:00:00Z")),
            YourWeek.events(listOf(mon, tue), emptyList(), rsvps, now),
        )
        // Home's own events carry the member's answer too.
        val home = listOf(HomeEventRow("mon", title = "Sunday Service", startsAt = mon.startAt, myRsvp = "going"))
        assertEquals(WeekForm.EVENT_GOING, YourWeek.events(listOf(mon, tue), home, null, now).form)
        // An RSVP to a gathering the calendar did not send still counts.
        val only = listOf(MyRsvp(eventId = "cell", status = "going", title = "Cell night", occursAt = "2026-10-07T16:00:00Z"))
        assertEquals(
            WeekRow(WeekForm.EVENT_GOING, "Cell night", "Wed 7 Oct · 7:00 PM · You're going", WeekDest.Event("cell", null)),
            YourWeek.events(null, null, only, now),
        )
    }

    @Test
    fun `Events — otherwise the soonest gathering this week, its day and time`() {
        assertEquals(
            WeekRow(WeekForm.EVENT_NEXT, "Sunday Service", "Mon 5 Oct · 9:00 AM", WeekDest.Event("mon", "2026-10-05T08:00:00Z")),
            YourWeek.events(listOf(tue, mon), null, emptyList(), now),
        )
        // One the member declined steps aside for the next…
        val declined = listOf(MyRsvp(eventId = "mon", status = "declined"))
        assertEquals("Youth Night", YourWeek.events(listOf(mon, tue), null, declined, now).title)
        // …unless it is all that is on.
        assertEquals("Sunday Service", YourWeek.events(listOf(mon), null, declined, now).title)
        // The member's own answers have the last word over Home's copy.
        val home = listOf(HomeEventRow("mon", title = "Sunday Service", startsAt = mon.startAt, myRsvp = "going"))
        assertEquals(WeekForm.EVENT_NEXT, YourWeek.events(listOf(mon), home, listOf(MyRsvp(eventId = "mon", status = "maybe")), now).form)
    }

    @Test
    fun `Events — the week is today through the seventh day after, from now`() {
        // Sun 11 Oct is the seventh day: in. Tue 13 Oct: out. Earlier today: past.
        assertEquals("Harvest Sunday", YourWeek.events(listOf(nextSun, farTue, earlier), null, null, now).title)
        val none = WeekRow(WeekForm.EVENT_NONE, "No gatherings this week", "See the church calendar", WeekDest.Tab("events"))
        assertEquals(none, YourWeek.events(listOf(farTue, earlier), null, null, now))
        // Nothing read at all: the none form.
        assertEquals(none, YourWeek.events(null, null, null, now))
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
            WeekRow(WeekForm.GIFT_COLLECTED, "Your monthly gift", "Collected on Tue 6 Oct", WeekDest.Screen("schedules?open=g1")),
            YourWeek.giving(p, listOf(gift()), rails, today),
        )
        assertEquals("Your weekly gift", YourWeek.giving(p, listOf(gift(frequency = "weekly")), rails, today).title)
        // The seventh day is in; the eighth is not; nor is a paused gift, or one asking nothing.
        assertEquals("Collected on Sun 11 Oct", YourWeek.giving(p, listOf(gift(next = "2026-10-11T06:00:00Z")), rails, today).line)
        assertEquals(give, YourWeek.giving(p, listOf(gift(next = "2026-10-12T06:00:00Z")), rails, today))
        assertEquals(give, YourWeek.giving(p, listOf(gift(status = "paused")), rails, today))
        assertEquals(give, YourWeek.giving(p, listOf(gift(nextAmount = 0)), rails, today))
        // The soonest prompt leads.
        assertEquals("g2", (YourWeek.giving(p, listOf(gift(), gift(id = "g2", next = "2026-10-05T06:00:00Z")), rails, today).dest as WeekDest.Screen).route.substringAfter("="))
    }

    @Test
    fun `Giving — a pledge's collector prompting this week, on or before its instalment, is collected — it opens the pledge`() {
        val p = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofDue()))
        assertEquals(
            WeekRow(WeekForm.GIFT_COLLECTED, "Roof", "Collected on Mon 5 Oct", WeekDest.Screen("partners-pledge/roof")),
            YourWeek.giving(p, listOf(gift(pledgeId = "roof", next = "2026-10-05T06:00:00Z")), rails, today),
        )
        // With nothing due yet on the pledge, its collector's prompt is still the week's.
        val ahead = Partnership(isPartner = true, pledges = listOf(roof))
        assertEquals(WeekForm.GIFT_COLLECTED, YourWeek.giving(ahead, listOf(gift(pledgeId = "roof")), rails, today).form)
    }

    @Test
    fun `Giving — an instalment no collector takes is due — Partners`() {
        val p = Partnership(isPartner = true, pledges = listOf(roof), due = listOf(roofDue()))
        val due = WeekRow(WeekForm.PLEDGE_DUE, "Roof", "KSh 5,000 due Mon 5 Oct", WeekDest.Tab("partners"))
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
            WeekRow(WeekForm.PLEDGE_DUE, "Roof", "KSh 5,000 overdue since Sat 5 Sep", WeekDest.Tab("partners")),
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
            WeekRow(WeekForm.PLEDGE_DUE, "Roof", "KSh 3,000 due Tue 6 Oct", WeekDest.Tab("partners")),
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
        assertEquals(WeekRow(WeekForm.CELL, "Kilimani Cell", "Next gathering Thu 8 Oct", WeekDest.Screen("cell-info")), YourWeek.cell(cell()))
        assertEquals("Next gathering not set · 12 members", YourWeek.cell(cell(next = null)).line)
        assertEquals("Next gathering not set · 1 member", YourWeek.cell(cell(next = null, members = 1)).line)
        assertEquals("Your cell", YourWeek.cell(cell(name = "")).title)
    }

    @Test
    fun `Cell — no cell, or the read failed — Find your cell, Community`() {
        val find = WeekRow(WeekForm.CELL_FIND, "Find your cell", "Gather with believers near you.", WeekDest.Tab("you"))
        assertEquals(find, YourWeek.cell(CellSummary(null)))
        assertEquals(find, YourWeek.cell(null))
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
}
