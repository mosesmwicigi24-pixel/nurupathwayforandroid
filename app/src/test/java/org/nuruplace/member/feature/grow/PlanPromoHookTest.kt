// The plan promo's two pure decisions: which words to show, and which plan.
//
// The hook extractor exists because a naive "break at the first full stop" rule
// guillotines "the failure at 2 a.m." into "…at 2 a." — a real bug caught on iOS
// (owner screenshot, 2026-08-26) before this port. A sentence only ends when
// whitespace AND a capital (or digit) follow the terminator.
package org.nuruplace.member.feature.grow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.ReadingPlanRow

class PlanPromoHookTest {

    private fun plan(
        id: String,
        enrolled: Boolean = false,
        description: String? = "A plan for the weary. It has words enough to promote itself.",
    ) = ReadingPlanRow(planId = id, title = "Plan $id", description = description, enrolled = enrolled, dayCount = 10)

    // --- the abbreviation bug -------------------------------------------

    @Test fun `an abbreviation mid-sentence is never mistaken for the end`() {
        val hook = planPromoHook(
            "Fear is a faithful visitor. It comes at 2 a.m. when the bills are counted and the house is quiet.",
        )
        // "a.m." must survive whole — the old rule cut here and shipped "…at 2 a."
        assertNotNull(hook)
        assertTrue("hook lost the abbreviation: $hook", hook!!.contains("2 a.m."))
        assertTrue("hook stopped inside the abbreviation: $hook", !hook.endsWith("a."))
        assertEquals("Fear is a faithful visitor. It comes at 2 a.m. when the bills are counted and the house is quiet.", hook)
    }

    @Test fun `other lowercase abbreviations survive too`() {
        val hook = planPromoHook("He rose at 6 a.m. and prayed. Then the day began in earnest, as it always does.")
        assertNotNull(hook)
        assertTrue(hook!!.startsWith("He rose at 6 a.m. and prayed."))
    }

    // --- ordinary sentence ends -----------------------------------------

    @Test fun `it stops after two real sentences`() {
        val hook = planPromoHook(
            "Grief has a shape. It changes the room you sit in. This third sentence should never appear on the card.",
        )
        assertEquals("Grief has a shape. It changes the room you sit in.", hook)
    }

    @Test fun `a digit after the full stop still ends the sentence`() {
        val hook = planPromoHook("The road was long and the walking slow. 40 days is a long time to wait for anything.")
        assertNotNull(hook)
        assertTrue(hook!!.startsWith("The road was long and the walking slow."))
    }

    @Test fun `a single sentence with no trailing text is returned whole`() {
        val d = "A short walk through the psalms of ascent, one step at a time."
        assertEquals(d, planPromoHook(d))
    }

    @Test fun `a long unbroken description is trimmed with an ellipsis`() {
        val d = "word ".repeat(60).trim()
        val hook = planPromoHook(d)!!
        assertTrue("hook was $hook", hook.length <= 190)
        assertTrue(hook.endsWith("…"))
    }

    @Test fun `nothing is invented when the plan has no description`() {
        assertNull(planPromoHook(null))
        assertNull(planPromoHook(""))
        assertNull(planPromoHook("   \n  "))
    }

    // --- which plan gets the second promo --------------------------------

    @Test fun `the second promo is never the plan of the day and never an enrolled one`() {
        val plans = listOf(plan("a"), plan("b", enrolled = true), plan("c"), plan("d"))
        for (day in 0L..13L) {
            val pick = midPromoPlan(plans, planOfDayId = "a", epochDay = day)
            assertNotNull(pick)
            assertTrue("picked the plan of the day", pick!!.planId != "a")
            assertTrue("picked an enrolled plan", !pick.enrolled)
        }
    }

    @Test fun `plans with no words of their own are not promoted`() {
        val plans = listOf(plan("a"), plan("b", description = null), plan("c", description = ""))
        // Only "a" has a description, and it is the plan of the day → nothing to promote.
        assertNull(midPromoPlan(plans, planOfDayId = "a", epochDay = 20_000L))
        assertEquals("a", midPromoPlan(plans, planOfDayId = "z", epochDay = 20_000L)?.planId)
    }

    @Test fun `the pick is stable within a day and rotates across days`() {
        val plans = listOf(plan("a"), plan("b"), plan("c"), plan("d"), plan("e"))
        val day = 20_000L
        assertEquals(
            midPromoPlan(plans, "a", day)?.planId,
            midPromoPlan(plans, "a", day)?.planId,
        )
        val picks = (day until day + 16).mapNotNull { midPromoPlan(plans, "a", it)?.planId }.toSet()
        assertTrue("the promo never rotated: $picks", picks.size > 1)
    }
}
