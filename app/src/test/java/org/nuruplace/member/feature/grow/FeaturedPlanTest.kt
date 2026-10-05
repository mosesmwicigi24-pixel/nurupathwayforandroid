// The plan featured at the top of Plans — the same pick on both apps for the
// same member and day (pathway docs/EXPERIENCE.md §8.2 #6). Seen: one member,
// a minute apart, iOS "FROM THE LIBRARY · Rooted: 10 Days in the Psalms" and
// Android "FROM THE LIBRARY · Who Am I?". Both apps featured the server's
// FIRST promo, and /growth/plans/promos rests every promo it hands out for ten
// days (plan_promo_log), so each request — the other app, a refresh — gets
// the next plans on the shelf. The rule: the plan of the day — the first plan
// not started, in the server's own order. The plan being walked (the server's
// "continue" promo) has its one card under CONTINUE READING (§7.4 #3).
package org.nuruplace.member.feature.grow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.PlanPromo as PlanPromoDto
import org.nuruplace.member.data.net.ReadingPlanRow
import java.time.Instant
import java.time.LocalDate

class FeaturedPlanTest {
    private fun plan(id: String, title: String = "Plan $id", enrolled: Boolean = false) =
        ReadingPlanRow(planId = id, title = title, description = "A plan with words enough to promote itself.", enrolled = enrolled, dayCount = 10)

    /** The local library's first plans, in the server's order (sort), not alphabetical. */
    private val library = listOf(
        plan("cb76", "Rooted: 10 Days in the Psalms"),
        plan("13fb", "Gospel of John"),
        plan("00d5", "Psalms of Comfort"),
        plan("943a", "Who Am I?"),
        plan("faf2", "Built to Last"),
    )

    private fun promo(id: String, slot: String, kicker: String = "FROM THE LIBRARY") =
        PlanPromoDto(planId = id, slot = slot, kicker = kicker, reason = "A few minutes a day is all it asks.")

    @Test fun `the server's rotating library promos never take the top — the plan of the day does`() {
        // Two requests a minute apart answer two different shelves…
        val first = resolvePromos(listOf(promo("943a", "fresh"), promo("faf2", "fresh")), library)
        val second = resolvePromos(listOf(promo("faf2", "fresh"), promo("00d5", "fresh")), library)
        // …and both apps feature the same plan: the first not started, in the server's order.
        val a = featuredPlan(library, first)!!
        val b = featuredPlan(library, second)!!
        assertEquals("Rooted: 10 Days in the Psalms", a.plan.title)
        assertEquals(a, b)
        assertEquals("PLAN OF THE DAY", a.kicker)
        assertNull(a.reason)
    }

    @Test fun `the earned slots that also rotate stay in the browse, not on top`() {
        listOf("next_step", "carrying", "cell", "fresh").forEach { slot ->
            val f = featuredPlan(library, resolvePromos(listOf(promo("faf2", slot, "BECAUSE YOU FINISHED")), library))!!
            assertEquals(slot, "cb76", f.plan.planId)
        }
    }

    // EXPERIENCE.md §7.4 #3 — seen: "CONTINUE READING · First Steps" and,
    // right under it, "PICK UP WHERE YOU LEFT OFF · First Steps". The plan in
    // progress has one card, under CONTINUE READING.
    @Test fun `the plan being walked never takes the top — CONTINUE READING holds it`() {
        val walking = library.map { if (it.planId == "00d5") it.copy(enrolled = true) else it }
        val promos = resolvePromos(
            listOf(promo("00d5", "continue", "PICK UP WHERE YOU LEFT OFF"), promo("faf2", "fresh")),
            walking,
        )
        val f = featuredPlan(walking, promos)!!
        assertEquals("cb76", f.plan.planId)
        assertEquals("PLAN OF THE DAY", f.kicker)
        // …nor is it woven into the browse below.
        assertEquals(listOf("faf2"), browsePromos(promos, f, walking).map { it.plan.planId })
    }

    @Test fun `a finished plan is not in progress — its promo may still show`() {
        val finished = library.map { if (it.planId == "00d5") it.copy(enrolled = true, completedAt = "2026-10-01T08:00:00Z") else it }
        val promos = resolvePromos(listOf(promo("00d5", "next_step", "BECAUSE YOU FINISHED")), finished)
        val f = featuredPlan(finished, promos)!!
        assertEquals("cb76", f.plan.planId)
        assertEquals(listOf("00d5"), browsePromos(promos, f, finished).map { it.plan.planId })
    }

    @Test fun `every plan started — the one in progress still never leads`() {
        val allStarted = library.map { it.copy(enrolled = true, completedAt = if (it.planId == "cb76") null else "2026-10-01T08:00:00Z") }
        // The plan of the day falls back to the first plan, which is the one in progress.
        assertNull(featuredPlan(allStarted, emptyList()))
    }

    @Test fun `the browse never repeats the featured plan`() {
        val promos = resolvePromos(listOf(promo("cb76", "fresh"), promo("943a", "fresh")), library)
        val f = featuredPlan(library, promos)!!
        assertEquals("cb76", f.plan.planId)
        assertEquals(listOf("943a"), browsePromos(promos, f, library).map { it.plan.planId })
    }

    @Test fun `the plan of the day keeps the server's order and skips what was started`() {
        val started = library.map { if (it.planId == "cb76") it.copy(enrolled = true) else it }
        assertEquals("13fb", planOfTheDay(started)?.planId)
        // Everything started: the first plan still has the slot.
        assertEquals("cb76", planOfTheDay(library.map { it.copy(enrolled = true) })?.planId)
        assertNull(planOfTheDay(emptyList()))
        assertNull(featuredPlan(emptyList(), emptyList()))
    }

    @Test fun `the day turns at midnight in Nairobi, not in UTC`() {
        // 22:30 UTC on 4 Oct is 01:30 on 5 Oct in Nairobi.
        val lateUtc = Instant.parse("2026-10-04T22:30:00Z")
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), nairobiEpochDay(lateUtc))
        // The old count (UTC millis / 86 400 000) said 4 Oct.
        assertEquals(LocalDate.of(2026, 10, 4).toEpochDay(), lateUtc.toEpochMilli() / 86_400_000L)
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), nairobiEpochDay(Instant.parse("2026-10-05T12:00:00Z")))
    }

    @Test fun `the mid promo is the agreed rule over the server's order`() {
        // pool = not started, with words, not the plan of the day: 13fb, 00d5, 943a, faf2.
        val day = LocalDate.of(2026, 10, 5).toEpochDay() // 20731 → (20731 / 2) % 4 = 10365 % 4 = 1
        assertEquals("00d5", midPromoPlan(library, "cb76", day)?.planId)
        assertEquals("00d5", midPromoPlan(library, "cb76", day - 1)?.planId) // 20730 / 2 = 10365 — same pair of days
    }
}
