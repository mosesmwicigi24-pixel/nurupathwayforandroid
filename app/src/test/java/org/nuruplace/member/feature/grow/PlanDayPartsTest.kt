// A plan day's parts, counted the way the day hub groups them (pathway
// docs/EXPERIENCE.md §7.4 #1, #2, #4). The day below is production's "First
// Steps" Day 1 as the local API serves it for Ada: The Word (Today's Reading,
// Devotional, Go Deeper) and Respond (Pray) done, Talk it Over still open.
// Seen before: the plan's page said "Start plan" with Day 1 "Start"; the
// streak card said "0-day streak" beside a tick on today.
package org.nuruplace.member.feature.grow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.PlanSegment
import org.nuruplace.member.data.net.ReadingPlanDay
import org.nuruplace.member.data.net.ReadingPlanDetail
import org.nuruplace.member.data.net.ReadingPlanRow
import java.time.Instant

class PlanDayPartsTest {
    private fun seg(id: String, sort: Int, kind: String, title: String, done: Boolean) =
        PlanSegment(segmentId = id, sort = sort, kind = kind, title = title, completed = done)

    /** First Steps, Day 1 — production's shape (ORDER BY sort). */
    private fun firstStepsDay1(talkDone: Boolean = false) = ReadingPlanDay(
        dayNumber = 1,
        reference = "John 3:1-8",
        title = "What Just Happened to You",
        segments = listOf(
            seg("a51a", 1, "scripture", "Today's Reading", true),
            seg("3b0c", 2, "devotional", "Devotional", true),
            seg("0631", 3, "talk", "Talk it Over", talkDone),
            seg("6c60", 4, "devotional", "Pray", true),
            seg("86e9", 5, "reading", "Go Deeper", true),
        ),
    )

    private fun freshDay(n: Int) = ReadingPlanDay(
        dayNumber = n,
        reference = "Matthew 6:5-13",
        segments = listOf(
            seg("s$n", 1, "scripture", "Today's Reading", false),
            seg("d$n", 2, "devotional", "Devotional", false),
            seg("t$n", 3, "talk", "Talk it Over", false),
            seg("p$n", 4, "devotional", "Pray", false),
            seg("g$n", 5, "reading", "Go Deeper", false),
        ),
    )

    @Test fun `a day's parts are the hub's three — The Word, Respond, Talk it Over`() {
        val p = dayParts(firstStepsDay1())
        assertEquals(DayParts(done = 2, total = 3), p)
        assertEquals(1, p.left)
        assertTrue(p.partway)
        assertEquals(listOf("word", "respond", "talk"), hubParts(firstStepsDay1().segments!!).map { it.tag })
    }

    @Test fun `the plan page's day row says what is left once the day is begun`() {
        assertEquals("1 part left", nextDayPill(firstStepsDay1()))
        assertEquals("Start", nextDayPill(freshDay(2)))
        val oneDone = freshDay(2).let { d -> d.copy(segments = d.segments!!.map { if (it.kind == "talk") it.copy(completed = true) else it }) }
        assertEquals("2 parts left", nextDayPill(oneDone))
        assertEquals("1 part left", partsLeftLabel(1))
        assertEquals("3 parts left", partsLeftLabel(3))
    }

    @Test fun `a begun plan never says Start again`() {
        // Ada: two of Day 1's three parts done, no whole day — it read "Start plan".
        val ada = ReadingPlanDetail(planId = "21c7", title = "First Steps", dayCount = 7, enrolled = true,
            days = listOf(firstStepsDay1(), freshDay(2)), nextDay = 1)
        assertTrue(planBegun(ada))
        assertEquals("Continue · Day 1", planCtaLabel(begun = planBegun(ada), allDone = false, day = 1))
        assertEquals("Continue · Day 4", planCtaLabel(begun = true, allDone = false, day = 4))
        // Started (enrolled) but nothing read yet — still the invitation.
        val fresh = ada.copy(days = listOf(freshDay(1), freshDay(2)))
        assertFalse(planBegun(fresh))
        // iOS's words, one product: "Begin Day 1" before, "Read again" after.
        assertEquals("Begin Day 1", planCtaLabel(begun = planBegun(fresh), allDone = false, day = 1))
        assertEquals("Read again", planCtaLabel(begun = true, allDone = true, day = 1))
        // A whole day recorded (completed_days) counts as begun too.
        assertTrue(planBegun(fresh.copy(days = listOf(freshDay(1).copy(completed = true), freshDay(2)))))
    }

    @Test fun `I've talked it over completes the talk part, and only it`() {
        assertEquals(listOf("0631"), talkPartSegments(firstStepsDay1()).map { it.segmentId })
        val noTalk = firstStepsDay1().let { d -> d.copy(segments = d.segments!!.filterNot { it.kind == "talk" }) }
        assertTrue(talkPartSegments(noTalk).isEmpty())
        // Done, the day is whole.
        assertEquals(DayParts(3, 3), dayParts(firstStepsDay1(talkDone = true)))
        assertFalse(dayParts(firstStepsDay1(talkDone = true)).partway)
    }

    @Test fun `today's parts are the server's next day`() {
        val plan = ReadingPlanDetail(planId = "21c7", title = "First Steps", dayCount = 7, enrolled = true,
            days = listOf(firstStepsDay1(), freshDay(2)), nextDay = 1)
        assertEquals(DayParts(2, 3, day = 1), todayParts(plan))
        assertEquals(DayParts(0, 3, day = 2), todayParts(plan.copy(nextDay = 2)))
        assertNull(todayParts(plan.copy(nextDay = null)))   // the whole plan is done
        assertNull(todayParts(null))
    }

    @Test fun `today is ticked only once the server has sealed a day today`() {
        // Ada now: two of Day 1's three parts read, nothing sealed today. It
        // used to tick today (the rhythm's `word`) beside "0-day streak".
        val partway = streakView(count = 0, todayDone = false, today = DayParts(2, 3, day = 1))
        assertFalse(partway.todayMarked)
        // "Today" means today (§9.7 M6): the parts' day is named — the server
        // says which are done, not when.
        assertEquals("Day 1: 2 of 3 parts", partway.line)
        assertEquals(0, partway.count)

        // "I've talked it over" seals Day 1: ticked, and never "0-day streak".
        assertEquals(StreakView(1, true, "Today's reading is done"), streakView(count = 0, todayDone = true, today = DayParts(0, 3)))
        // She goes on into Day 2 the same day — today stays ticked.
        assertEquals(StreakView(1, true, "Today's reading is done"), streakView(count = 0, todayDone = true, today = DayParts(1, 3)))

        // A longer walk keeps its count while today is under way.
        assertEquals(StreakView(4, false, "Day 4: 1 of 3 parts"), streakView(count = 4, todayDone = false, today = DayParts(1, 3, day = 4)))
        // Never "Today:", even when the day isn't known.
        assertEquals("This day: 1 of 3 parts", streakView(count = 4, todayDone = false, today = DayParts(1, 3)).line)
        assertEquals(StreakView(4, true, "Today's reading is done"), streakView(count = 4, todayDone = true, today = null))
    }

    @Test fun `before a part is read the card keeps its invitation`() {
        assertEquals(StreakView(0, false, "Read today to start your streak"), streakView(count = 0, todayDone = false, today = null))
        assertEquals(StreakView(3, false, "Read today to keep it alive"), streakView(count = 3, todayDone = false, today = DayParts(0, 3)))
    }

    @Test fun `the sealed-day note counts on the church's calendar`() {
        val now = java.time.Instant.parse("2026-10-04T22:30:00Z") // 01:30 on Mon 5 Oct in Nairobi
        val mon = java.time.LocalDate.of(2026, 10, 5).toEpochDay()
        assertTrue(isSealedToday(mon, now))
        assertFalse(isSealedToday(mon - 1, now))
        assertFalse(isSealedToday(null, now))
    }

    // §7.4 #4, the tick on every phone: GET /growth/plans' last_day_finished_at.
    // 12:00 on Mon 5 Oct in Nairobi.
    private val noonOct5 = Instant.parse("2026-10-05T09:00:00Z")
    private fun row(finishedAt: String?, enrolled: Boolean = true) =
        ReadingPlanRow(planId = "21c7", title = "First Steps", dayCount = 7, enrolled = enrolled, lastDayFinishedAt = finishedAt)

    @Test fun `a day finished on another phone ticks today — on the church's calendar`() {
        // 21:10 UTC on 4 Oct is 00:10 on 5 Oct in Nairobi: today.
        assertTrue(planDayFinishedToday(listOf(row("2026-10-04T21:10:00Z")), noonOct5))
        // 20:50 UTC on 4 Oct is 23:50 on 4 Oct in Nairobi: not today.
        assertFalse(planDayFinishedToday(listOf(row("2026-10-04T20:50:00Z")), noonOct5))
        // The server's own shape, fractional seconds included; an offset reads the same.
        assertTrue(planDayFinishedToday(listOf(row("2026-10-05T08:41:07.512Z")), noonOct5))
        assertTrue(planDayFinishedToday(listOf(row("2026-10-05T00:10:00+03:00")), noonOct5))
    }

    @Test fun `any started plan counts, and nothing else does`() {
        val older = row("2026-10-03T09:00:00Z").copy(planId = "68871876")
        assertTrue(planDayFinishedToday(listOf(older, row("2026-10-05T06:00:00Z")), noonOct5))
        assertFalse(planDayFinishedToday(listOf(older), noonOct5))
        assertFalse(planDayFinishedToday(listOf(row(null)), noonOct5))
        assertFalse(planDayFinishedToday(listOf(row("yesterday")), noonOct5))
        assertFalse(planDayFinishedToday(listOf(row("2026-10-05T06:00:00Z", enrolled = false)), noonOct5))
        assertFalse(planDayFinishedToday(emptyList(), noonOct5))
    }

    // EXPERIENCE.md §9.2 #3: one streak, one rule, on Home and Plans.
    @Test fun `the one streak counts today once the member was active today — on any screen`() {
        assertEquals(0, StreakWords.days(0, activeToday = false))
        assertEquals(1, StreakWords.days(0, activeToday = true))
        assertEquals(4, StreakWords.days(4, activeToday = true))
        assertEquals("3-day streak", StreakWords.label(3))
        // Prayed today, nothing read: Plans counts today as Home does, and
        // still invites the reading.
        assertEquals(StreakView(1, false, "Read today to keep it alive"), streakView(count = 0, todayDone = false, today = null, activeToday = true))
    }
}
