// The plan featured at the top of Plans — the same pick on both apps for the
// same member and day (pathway docs/EXPERIENCE.md §7.4). The server is the
// source of truth: its promo page stands all day, fillers included (§7.4 #5),
// so the hero is its first promo whose plan isn't being read, with its own
// kicker; only with no promo to show (none, or the call failed) does the page
// pick for itself — the plan of the day, the first plan not started.
// Seen before: iOS "FROM THE LIBRARY · Rooted…" and Android "FROM THE LIBRARY ·
// Who Am I?" a minute apart (the page turned per request); and "CONTINUE
// READING · First Steps" with "PICK UP WHERE YOU LEFT OFF · First Steps" right
// under it (§7.4 #3).
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

    private fun promo(id: String, slot: String, kicker: String = "FROM THE LIBRARY", reason: String = "A few minutes a day is all it asks.") =
        PlanPromoDto(planId = id, slot = slot, kicker = kicker, reason = reason)

    @Test fun `the hero is the server's first promo, with its own words`() {
        // build8 today: the server's page, first to last.
        val promos = resolvePromos(
            listOf(
                promo("00d5", "cell", "YOUR CELL IS READING", "Someone in your cell is walking this right now — you could walk it together."),
                promo("943a", "fresh", "WORTH YOUR WEEK"),
                promo("faf2", "fresh"),
            ),
            library,
        )
        val f = featuredPlan(library, promos)!!
        assertEquals("00d5", f.plan.planId)
        assertEquals("YOUR CELL IS READING", f.kicker)
        assertEquals("Someone in your cell is walking this right now — you could walk it together.", f.reason)
        // The rest, woven into the browse in the server's order — never the hero.
        assertEquals(listOf("943a", "faf2"), promos.drop(1).map { it.plan.planId })
    }

    // §7.4 #3: the plan in progress has one card, under CONTINUE READING.
    @Test fun `a plan being read is never promoted — the next promo leads`() {
        val walking = library.map { if (it.planId == "00d5") it.copy(enrolled = true) else it }
        val promos = resolvePromos(
            listOf(promo("00d5", "continue", "PICK UP WHERE YOU LEFT OFF"), promo("943a", "fresh", "WORTH YOUR WEEK"), promo("faf2", "fresh")),
            walking,
        )
        assertEquals(listOf("943a", "faf2"), promos.map { it.plan.planId })
        val f = featuredPlan(walking, promos)!!
        assertEquals("943a", f.plan.planId)
        assertEquals("WORTH YOUR WEEK", f.kicker)
    }

    @Test fun `a finished plan is not being read — its promo may lead`() {
        val finished = library.map { if (it.planId == "00d5") it.copy(enrolled = true, completedAt = "2026-10-01T08:00:00Z") else it }
        val f = featuredPlan(finished, resolvePromos(listOf(promo("00d5", "next_step", "BECAUSE YOU FINISHED")), finished))!!
        assertEquals("00d5", f.plan.planId)
        assertEquals("BECAUSE YOU FINISHED", f.kicker)
    }

    @Test fun `no promo to show — the plan of the day, the first not started`() {
        // None from the server (or the call failed)…
        val pod = featuredPlan(library, emptyList())!!
        assertEquals("cb76", pod.plan.planId)
        assertEquals("PLAN OF THE DAY", pod.kicker)
        assertNull(pod.reason)
        // …or only promos naming plans this page doesn't hold, or plans being read.
        val walking = library.map { if (it.planId == "00d5") it.copy(enrolled = true) else it }
        assertEquals("cb76", featuredPlan(walking, resolvePromos(listOf(promo("zzzz", "fresh"), promo("00d5", "continue")), walking))!!.plan.planId)
        // Every plan started: the first plan.
        assertEquals("cb76", featuredPlan(library.map { it.copy(enrolled = true) }, emptyList())!!.plan.planId)
        assertNull(featuredPlan(emptyList(), emptyList()))
    }

    @Test fun `a blank kicker reads FOR YOU, and a plan is promoted once`() {
        val promos = resolvePromos(listOf(promo("943a", "fresh", "  "), promo("943a", "cell", "YOUR CELL IS READING")), library)
        assertEquals(1, promos.size)
        assertEquals("FOR YOU", featuredPlan(library, promos)!!.kicker)
    }

    @Test fun `the plan of the day keeps the server's order and skips what was started`() {
        val started = library.map { if (it.planId == "cb76") it.copy(enrolled = true) else it }
        assertEquals("13fb", planOfTheDay(started)?.planId)
        // Everything started: the first plan still has the slot.
        assertEquals("cb76", planOfTheDay(library.map { it.copy(enrolled = true) })?.planId)
        assertNull(planOfTheDay(emptyList()))
    }

    @Test fun `the day turns at midnight in Nairobi, not in UTC`() {
        // 22:30 UTC on 4 Oct is 01:30 on 5 Oct in Nairobi.
        val lateUtc = Instant.parse("2026-10-04T22:30:00Z")
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), nairobiEpochDay(lateUtc))
        // The old count (UTC millis / 86 400 000) said 4 Oct.
        assertEquals(LocalDate.of(2026, 10, 4).toEpochDay(), lateUtc.toEpochMilli() / 86_400_000L)
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), nairobiEpochDay(Instant.parse("2026-10-05T12:00:00Z")))
    }

    // The mid-page promo shows only when the server had no promo to give
    // (ReadingPlansScreen; iOS the same) — this is its pick.
    @Test fun `the mid promo is the agreed rule over the server's order`() {
        // pool = not started, with words, not the plan of the day: 13fb, 00d5, 943a, faf2.
        val day = LocalDate.of(2026, 10, 5).toEpochDay() // 20731 → (20731 / 2) % 4 = 10365 % 4 = 1
        assertEquals("00d5", midPromoPlan(library, "cb76", day)?.planId)
        assertEquals("00d5", midPromoPlan(library, "cb76", day - 1)?.planId) // 20730 / 2 = 10365 — same pair of days
    }
}
