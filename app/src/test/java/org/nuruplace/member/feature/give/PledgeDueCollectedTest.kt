// Partners: a pledge collected automatically says so (pathway docs/
// EXPERIENCE.md §6.4). The owner's 2026-09-28 rule — "Collected on Mon 5 Oct"
// instead of Pay for a running recurring gift — extends to a PLEDGE whose
// collector prompts on or before the instalment's date: the DUE row shows
// "Collected on EEE d MMM" and a tap opens the pledge. A pledge with no
// collector keeps Pay; paying early by hand stays on the pledge's page.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.DueItem
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.IntentPledge
import org.nuruplace.member.data.net.Pledge
import java.time.LocalDate

class PledgeDueCollectedTest {
    /** A monthly pledge's instalment due Mon 5 Oct, KSh 5,000. */
    private fun due(dueOn: String = "2026-10-05", kind: String = "pledge", action: String = "pay", id: String = "p1") =
        DueItem(kind = kind, id = id, title = "Roof", amountMinor = 500_000, dueOn = dueOn, action = action)

    /** The recurring gift bound to pledge p1, prompting at [nextRunAt]. */
    private fun collector(
        nextRunAt: String = "2026-10-02T06:00:00Z",
        status: String = "active",
        next: Long? = 500_000,
        pledgeId: String = "p1",
        id: String = "s1",
    ) = GivingSchedule(
        scheduleId = id, status = status, nextRunAt = nextRunAt, frequency = "monthly", method = "mpesa",
        amountMinor = 500_000, pledge = IntentPledge(pledgeId, "Roof"), nextAmountMinor = next,
    )

    @Test
    fun `a collector prompting before the instalment's day — Collected on its prompt, not Pay`() {
        assertEquals(LocalDate.of(2026, 10, 2), pledgeCollectedOn(due(), collector()))
        assertEquals("Collected on Fri 2 Oct", pledgeCollectedChip(due(), collector()))
    }

    @Test
    fun `a prompt on the instalment's own day collects it`() {
        assertEquals("Collected on Mon 5 Oct", pledgeCollectedChip(due(), collector(nextRunAt = "2026-10-05T06:00:00Z")))
    }

    @Test
    fun `a prompt after the instalment's day keeps Pay`() {
        assertNull(pledgeCollectedChip(due(), collector(nextRunAt = "2026-10-06T06:00:00Z")))
    }

    @Test
    fun `an instalment already past keeps Pay — the next prompt comes after it`() {
        // Overdue since 5 Sep; the collector's next prompt is 2 Oct.
        assertNull(pledgeCollectedChip(due(dueOn = "2026-09-05"), collector()))
    }

    @Test
    fun `the prompt is read on the church's calendar, not UTC`() {
        // 22:30 UTC on 5 Oct is 01:30 on 6 Oct in Nairobi — after the instalment.
        assertNull(pledgeCollectedChip(due(), collector(nextRunAt = "2026-10-05T22:30:00Z")))
        // 21:30 UTC on 4 Oct is 00:30 on 5 Oct in Nairobi — the instalment's day.
        assertEquals("Collected on Mon 5 Oct", pledgeCollectedChip(due(), collector(nextRunAt = "2026-10-04T21:30:00Z")))
    }

    @Test
    fun `no collector, a paused one, or one asking nothing — Pay stays`() {
        assertNull(pledgeCollectedChip(due(), null))
        assertNull(pledgeCollectedChip(due(), collector(status = "paused")))
        // 0: the pledge is already covered this cycle; null: the collector is
        // stopping with its pledge (or an older server) — no prompt to count on.
        assertNull(pledgeCollectedChip(due(), collector(next = 0)))
        assertNull(pledgeCollectedChip(due(), collector(next = null)))
    }

    @Test
    fun `a date that cannot be read keeps Pay`() {
        assertNull(pledgeCollectedChip(due(dueOn = "soon"), collector()))
        assertNull(pledgeCollectedChip(due(), collector(nextRunAt = "")))
    }

    @Test
    fun `only a pledge's Pay row — a gift's row and a Resume row keep their own rules`() {
        assertNull(pledgeCollectedChip(due(kind = "schedule"), collector()))
        assertNull(pledgeCollectedChip(due(action = "resume"), collector()))
        // A recurring gift's own row still says it by its due_on (2026-09-28).
        assertEquals("Collected on Mon 5 Oct", dueCollectedChip(due(kind = "schedule")))
        assertNull(dueCollectedChip(due()))
    }

    @Test
    fun `the collector is the pledge's own — bound by the gift, or by the pledge's schedule id`() {
        val pledge = Pledge(pledgeId = "p1", shape = "monthly", amountMinor = 500_000, dueDay = 5, status = "active")
        val other = collector(pledgeId = "p2", id = "s2")
        // Another pledge's gift collects nothing here.
        assertNull(pledgeCollector(pledge, listOf(other))?.let { pledgeCollectedChip(due(), it) })
        // Bound by the gift's own pledge.
        assertEquals(
            "Collected on Fri 2 Oct",
            pledgeCollector(pledge, listOf(other, collector()))?.let { pledgeCollectedChip(due(), it) },
        )
        // An older row bound by the pledge's schedule_id.
        val older = GivingSchedule(scheduleId = "s9", status = "active", nextRunAt = "2026-10-01T06:00:00Z", nextAmountMinor = 500_000)
        assertEquals(
            "Collected on Thu 1 Oct",
            pledgeCollector(pledge.copy(scheduleId = "s9"), listOf(older))?.let { pledgeCollectedChip(due(), it) },
        )
        // A running collector is read before a paused one.
        val paused = collector(status = "paused", id = "s3", nextRunAt = "2026-09-30T06:00:00Z")
        assertEquals(
            "Collected on Fri 2 Oct",
            pledgeCollector(pledge, listOf(paused, collector()))?.let { pledgeCollectedChip(due(), it) },
        )
    }
}
