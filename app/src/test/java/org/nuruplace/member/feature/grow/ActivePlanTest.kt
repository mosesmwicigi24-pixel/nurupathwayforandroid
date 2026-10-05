// The plan the member is reading now (pathway docs/EXPERIENCE.md §6.1, §6.2)
// — Home's YOUR WEEK row and the Plans header say the same plan, the same day.
package org.nuruplace.member.feature.grow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.ReadingPlanRow

class ActivePlanTest {
    private fun plan(
        id: String,
        title: String = "Rooted: 10 Days in the Psalms",
        days: Int = 10,
        current: Int? = 1,
        done: List<Int>? = null,
        enrolled: Boolean = true,
        completedAt: String? = null,
    ) = ReadingPlanRow(planId = id, title = title, dayCount = days, currentDay = current, completedDays = done, enrolled = enrolled, completedAt = completedAt)

    @Test
    fun `the plan being read is the first enrolled, unfinished one`() {
        val plans = listOf(
            plan("browse", enrolled = false),
            plan("finished", completedAt = "2026-09-30T08:00:00Z"),
            plan("reading", title = "Hope"),
            plan("later", title = "Joy"),
        )
        assertEquals("reading", activePlan(plans)?.planId)
        assertNull(activePlan(plans.take(2)))
        assertNull(activePlan(emptyList()))
    }

    @Test
    fun `the day is the server's current day, else the one after the finished days`() {
        assertEquals(3, planDay(plan("p", current = 3)))
        assertEquals(4, planDay(plan("p", current = null, done = listOf(1, 2, 3))))
        assertEquals(1, planDay(plan("p", current = null)))
        // Held to the plan's length — never "Day 11 of 10", never "Day 0".
        assertEquals(10, planDay(plan("p", current = 11)))
        assertEquals(1, planDay(plan("p", current = 0)))
    }

    @Test
    fun `the Plans header line names the plan and its day, or nothing`() {
        assertEquals("Rooted: 10 Days in the Psalms · Day 1 of 10", activePlanLine(listOf(plan("p"))))
        assertEquals("Hope · Day 4 of 7", activePlanLine(listOf(plan("p", title = "Hope", days = 7, current = 4))))
        assertNull(activePlanLine(listOf(plan("p", enrolled = false))))
    }

    // EXPERIENCE.md §9.1 rule 5 (Cycle 5): a pause is named kindly, once —
    // when, and the day that waits; never a count of days missed.
    @Test
    fun `a plan last finished two or more days ago reads as paused, by the day it paused`() {
        val monday = java.time.Instant.parse("2026-10-05T12:00:00Z")           // Mon 5 Oct, Nairobi 15:00
        val cara = plan("first", title = "First Steps", days = 7, current = 2, done = listOf(1))
            .copy(lastDayFinishedAt = "2026-10-01T09:33:50Z")                  // Thu 1 Oct
        assertEquals(java.time.LocalDate.of(2026, 10, 1), planPausedOn(cara, monday))
        assertEquals("You paused on Thursday — Day 2 is waiting", planTodayLine(cara, readToday = false, now = monday))
        assertEquals("You paused on Thursday — Day 2 is waiting", planCardLine(cara, readToday = false, now = monday))
        // Yesterday's reading is no pause; nor is a plan with no day finished yet.
        assertNull(planPausedOn(cara.copy(lastDayFinishedAt = "2026-10-04T18:00:00Z"), monday))
        assertNull(planPausedOn(cara.copy(lastDayFinishedAt = null), monday))
        assertNull(planPausedOn(cara.copy(completedAt = "2026-10-02T08:00:00Z"), monday))
        // Further back than a week: the date, not a weekday.
        assertEquals("You paused on Thu 24 Sep — Day 2 is waiting", planTodayLine(cara.copy(lastDayFinishedAt = "2026-09-24T09:00:00Z"), false, monday))
        // Read today wins over any pause.
        assertEquals("Day 1 done today · Day 2 next", planTodayLine(cara, readToday = true, now = monday))
        // Otherwise the card keeps its "Today ·" line.
        val fresh = cara.copy(lastDayFinishedAt = "2026-10-04T18:00:00Z", subtitle = "Seven days for the new believer")
        assertEquals("Today · Seven days for the new believer", planCardLine(fresh, readToday = false, now = monday))
    }
}
