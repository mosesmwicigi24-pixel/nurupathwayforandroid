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
}
