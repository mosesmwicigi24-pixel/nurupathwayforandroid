// The partner statement rule (docs/PARTNERS_PROGRAMME.md §3), pinned:
//   Paid      = Σ payments with a pledge_id
//   Pledged   = monthly: amount × due_day dates from max(created_at, 1 Jan)
//               through 31 Dec; total: target if due_on is in the year
//   Remaining = max(Pledged − Paid, 0)
// plus the pledge card's "N of M kept this year" and the DUE row's relative
// date. Both clients show these numbers; only this file decides them.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.DueItem
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.PartnerRhythm
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.PledgeProgress
import org.nuruplace.member.data.net.StatementFaithfulness
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.StatementPendingPayment
import org.nuruplace.member.data.net.StatementPledge
import java.time.LocalDate

class PartnerStatementMathTest {
    private fun monthly(id: String = "m1", amount: Int = 200_000, dueDay: Int = 5, createdAt: String? = null, status: String = "active") =
        Pledge(pledgeId = id, shape = "monthly", amountMinor = amount, dueDay = dueDay, createdAt = createdAt, status = status)

    private fun total(id: String = "t1", target: Int = 5_000_000, dueOn: String, status: String = "active") =
        Pledge(pledgeId = id, shape = "total", targetMinor = target, dueOn = dueOn, status = status)

    private fun payment(amount: Int, pledgeId: String?, at: String = "2026-04-05T09:00:00Z") =
        StatementPayment(transactionId = "tx-${amount}-${pledgeId}", amountMinor = amount, pledgeId = pledgeId, at = at)

    // ── Pledged ──

    @Test
    fun `monthly pledge created mid-year is owed only from its creation`() {
        // Created 15 March, due on the 5th: April..December = 9 due dates.
        val s = statementSummary(2026, listOf(monthly(createdAt = "2026-03-15T10:00:00Z")), emptyList())
        assertEquals(9 * 200_000, s.pledgedMinor)
    }

    @Test
    fun `monthly pledge created on or before its due day counts that month`() {
        // Created 5 March at noon, due on the 5th: March..December = 10.
        assertEquals(10, dueDatesInYear(2026, 5, LocalDate.of(2026, 3, 5)))
        // Created 6 March: April..December = 9.
        assertEquals(9, dueDatesInYear(2026, 5, LocalDate.of(2026, 3, 6)))
    }

    @Test
    fun `monthly pledge created in an earlier year is owed the whole year`() {
        val s = statementSummary(2026, listOf(monthly(createdAt = "2025-11-20T10:00:00Z")), emptyList())
        assertEquals(12 * 200_000, s.pledgedMinor)
    }

    @Test
    fun `monthly pledge with no created_at falls back to the whole year`() {
        assertEquals(12 * 200_000, statementSummary(2026, listOf(monthly()), emptyList()).pledgedMinor)
    }

    @Test
    fun `monthly pledge created next year contributes nothing to this year`() {
        assertEquals(0, statementSummary(2026, listOf(monthly(createdAt = "2027-01-02T10:00:00Z")), emptyList()).pledgedMinor)
    }

    @Test
    fun `total pledge counts in the year its due date falls`() {
        assertEquals(5_000_000, statementSummary(2026, listOf(total(dueOn = "2026-12-15")), emptyList()).pledgedMinor)
    }

    @Test
    fun `total pledge due next year contributes nothing to this year`() {
        assertEquals(0, statementSummary(2026, listOf(total(dueOn = "2027-01-31")), emptyList()).pledgedMinor)
        assertEquals(5_000_000, statementSummary(2027, listOf(total(dueOn = "2027-01-31")), emptyList()).pledgedMinor)
    }

    @Test
    fun `cancelled pledges are ignored, paused and fulfilled are not`() {
        val pledges = listOf(
            monthly(id = "c", status = "cancelled"),
            total(id = "tc", dueOn = "2026-06-01", status = "cancelled"),
            monthly(id = "p", status = "paused"),
            total(id = "f", dueOn = "2026-06-01", status = "fulfilled"),
        )
        assertEquals(12 * 200_000 + 5_000_000, statementSummary(2026, pledges, emptyList()).pledgedMinor)
    }

    // ── Paid + Remaining ──

    @Test
    fun `paid sums only payments that carry a pledge id`() {
        val payments = listOf(payment(150_000, "m1"), payment(50_000, "t1"), payment(999_999, null), payment(1, ""))
        val s = statementSummary(2026, listOf(monthly()), payments)
        assertEquals(200_000, s.paidMinor)
        assertEquals(listOf("m1", "t1"), pledgePayments(payments).map { it.pledgeId })
    }

    @Test
    fun `remaining is pledged minus paid, never below zero`() {
        val pl = listOf(monthly(createdAt = "2026-10-01T00:00:00Z")) // Oct, Nov, Dec = 600,000
        assertEquals(400_000, statementSummary(2026, pl, listOf(payment(200_000, "m1"))).remainingMinor)
        assertEquals(0, statementSummary(2026, pl, listOf(payment(900_000, "m1"))).remainingMinor)
        assertEquals(StatementSummary(600_000, 900_000, 0), statementSummary(2026, pl, listOf(payment(900_000, "m1"))))
    }

    // ── "N of M kept this year" ──

    @Test
    fun `kept counts this pledge's payments against due dates elapsed`() {
        val pl = monthly(createdAt = "2026-01-10T00:00:00Z") // first due 5 Feb
        val payments = listOf(payment(200_000, "m1"), payment(200_000, "m1"), payment(200_000, "other"), payment(200_000, null))
        // 20 Sep: due dates elapsed = Feb..Sep = 8; two of them kept.
        assertEquals(2 to 8, keptThisYear(pl, payments, LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun `kept never exceeds the due dates elapsed`() {
        val pl = monthly(createdAt = "2026-08-01T00:00:00Z") // due 5 Aug, 5 Sep
        val payments = List(4) { payment(200_000, "m1") }
        assertEquals(2 to 2, keptThisYear(pl, payments, LocalDate.of(2026, 9, 20)))
        // Before the first due date nothing has elapsed.
        assertEquals(0 to 0, keptThisYear(pl, payments, LocalDate.of(2026, 8, 4)))
    }

    @Test
    fun `the card reads the server's kept and due first, the local estimate only as a fallback`() {
        val today = LocalDate.of(2026, 9, 20)
        val pl = monthly(createdAt = "2026-01-10T00:00:00Z") // local: due Feb..Sep = 8
        val payments = listOf(payment(200_000, "m1"), payment(200_000, "m1"))
        // Server (FIFO instalment ledger): 3 kept of 4 RESOLVED — the card says so,
        // though the local count over the same payments would say 2 of 8.
        val server = GivingStatement(
            year = 2026, payments = payments,
            pledges = listOf(StatementPledge(pledgeId = "other", kept = 9, dueCount = 9), StatementPledge(pledgeId = "m1", kept = 3, dueCount = 4)),
        )
        assertEquals(3 to 4, pledgeKeptThisYear(pl, server, today))
        assertEquals("3 of 4 kept this year", pledgeKeptLine(pl, server, today))

        // The server says nothing is resolved yet (e.g. the first instalment is
        // due today, unpaid): say nothing — never the local "0 of 8".
        val nothingResolved = server.copy(pledges = listOf(StatementPledge(pledgeId = "m1", kept = 0, dueCount = 0)))
        assertEquals(0 to 0, pledgeKeptThisYear(pl, nothingResolved, today))
        assertNull(pledgeKeptLine(pl, nothingResolved, today))

        // Fallback 1: an older server — no pledges[] at all.
        val older = GivingStatement(year = 2026, payments = payments, pledges = null)
        assertEquals(2 to 8, pledgeKeptThisYear(pl, older, today))
        assertEquals("2 of 8 kept this year", pledgeKeptLine(pl, older, today))
        // Fallback 2: pledges[] without an entry for this pledge.
        val noEntry = server.copy(pledges = listOf(StatementPledge(pledgeId = "other", kept = 1, dueCount = 1)))
        assertEquals(2 to 8, pledgeKeptThisYear(pl, noEntry, today))
        // Fallback 3: this year's statement not loaded — no payments to count.
        assertEquals(0 to 8, pledgeKeptThisYear(pl, null, today))

        // The local fallback with nothing due also says nothing.
        assertNull(pledgeKeptLine(monthly(createdAt = "2026-09-10T00:00:00Z", dueDay = 25), older, today))
        // A malformed server entry never reads "5 of 4" or a negative count.
        assertEquals(4 to 4, pledgeKeptThisYear(pl, server.copy(pledges = listOf(StatementPledge(pledgeId = "m1", kept = 5, dueCount = 4))), today))
        assertEquals(0 to 0, pledgeKeptThisYear(pl, server.copy(pledges = listOf(StatementPledge(pledgeId = "m1", kept = -1, dueCount = -2))), today))
    }

    // ── DUE row relative date ──

    @Test
    fun `due relative label reads today, tomorrow, in N days, then the date`() {
        val today = LocalDate.of(2026, 9, 24)
        val label = { d: LocalDate -> "${d.dayOfMonth} ${d.month.name.take(3)}" }
        assertEquals("today", dueRelativeLabel(today, today, label))
        assertEquals("tomorrow", dueRelativeLabel(today.plusDays(1), today, label))
        assertEquals("in 5 days", dueRelativeLabel(today.plusDays(5), today, label))
        assertEquals("25 OCT", dueRelativeLabel(LocalDate.of(2026, 10, 25), today, label))
        assertEquals("20 SEP", dueRelativeLabel(LocalDate.of(2026, 9, 20), today, label))
    }

    // ── DUE rows and pending_minor (owner, 2026-09-26) ──

    private fun due(amount: Int = 100_000, pending: Int = 0, kind: String = "pledge", action: String = "pay") =
        DueItem(kind = kind, id = "p1", title = "General partnership", amountMinor = amount, dueOn = "2026-09-25", action = action, pendingMinor = pending)

    @Test
    fun `nothing on its way leaves the row as it was`() {
        assertEquals(DueRowView(100_000), dueRowView(due(pending = 0), "mpesa"))
        assertEquals(DueRowView(100_000), dueRowView(due(pending = -5), "mpesa")) // never negative
    }

    @Test
    fun `the whole instalment on its way shows Processing instead of Pay`() {
        assertEquals(DueRowView(100_000, processingChip = "Waiting for M-Pesa"), dueRowView(due(pending = 100_000), "mpesa"))
        assertEquals(DueRowView(100_000, processingChip = "Waiting for Airtel Money"), dueRowView(due(pending = 150_000), "airtel"))
        assertEquals(DueRowView(100_000, processingChip = "Processing"), dueRowView(due(pending = 100_000), "card"))
        assertEquals(DueRowView(100_000, processingChip = "Processing"), dueRowView(due(pending = 100_000), null))
    }

    @Test
    fun `part of it on its way keeps Pay for the uncovered remainder`() {
        val v = dueRowView(due(amount = 100_000, pending = 40_000), "mpesa")
        assertEquals(60_000, v.leadMinor) // what the row leads with, and what Pay presets
        assertNull(v.processingChip)
        assertEquals("KSh 400 processing", v.processingNote)
    }

    @Test
    fun `schedule and resume rows are never changed by pending`() {
        assertEquals(DueRowView(50_000), dueRowView(due(amount = 50_000, pending = 50_000, kind = "schedule"), "mpesa"))
        assertEquals(DueRowView(100_000), dueRowView(due(pending = 100_000, action = "resume"), "mpesa"))
    }

    @Test
    fun `the chip's method is the pledge's newest payment in flight on this year's statement`() {
        val s = GivingStatement(
            year = 2026,
            pending = listOf(
                StatementPendingPayment(transactionId = "a", method = "card", at = "2026-09-26T08:00:00Z", pledgeId = "p1"),
                StatementPendingPayment(transactionId = "b", method = "mpesa", at = "2026-09-26T19:00:00Z", pledgeId = "p1"),
                StatementPendingPayment(transactionId = "c", method = "airtel", at = "2026-09-26T20:00:00Z", pledgeId = "p2"),
            ),
        )
        assertEquals("mpesa", pendingMethodFor("p1", s))
        assertEquals("airtel", pendingMethodFor("p2", s))
        assertNull(pendingMethodFor("p3", s))
        assertNull(pendingMethodFor("p1", null))
        assertNull(pendingMethodFor("p1", GivingStatement(year = 2026)))
    }

    // ── STANDING line (owner, 2026-09-26) ──

    private fun year(keptOnTime: Int, late: Int, missed: Int = 0, dueCount: Int) =
        GivingStatement(year = 2026, faithfulness = StatementFaithfulness(keptOnTime = keptOnTime, late = late, missed = missed, dueCount = dueCount))

    private val pledgeOnly = Partnership(isPartner = true, kept = 0, pledges = listOf(monthly(id = "p1")))
    private val scheduleOnly = Partnership(isPartner = true, kept = 3, scheduleId = "s1", rhythm = PartnerRhythm(method = "mpesa", amountMinor = 100_000))

    @Test
    fun `a pledge partner's standing counts this year's kept commitments, never schedule cycles`() {
        // partnership.kept is 0 (no schedule) while the pledge card says "2 of 2 kept".
        assertEquals("2 commitments kept this year · on track", standingKeptLine(pledgeOnly, year(1, 1, dueCount = 2), paused = false))
        assertEquals("1 commitment kept this year · on track", standingKeptLine(pledgeOnly, year(1, 0, dueCount = 1), paused = false))
        // A schedule as well: the pledge ledger still speaks.
        assertEquals("2 commitments kept this year · on track", standingKeptLine(scheduleOnly.copy(pledges = pledgeOnly.pledges), year(2, 0, dueCount = 2), false))
    }

    @Test
    fun `behind comes from the pledges, paused from the partnership`() {
        val behind = pledgeOnly.copy(pledges = listOf(monthly(id = "p1").copy(progress = PledgeProgress(label = "behind"))))
        assertEquals("0 commitments kept this year · behind", standingKeptLine(behind, year(0, 0, missed = 1, dueCount = 1), false))
        assertEquals("1 commitment kept this year · paused", standingKeptLine(behind, year(1, 0, dueCount = 1), paused = true))
    }

    @Test
    fun `the count is left out while nothing is due yet, or before the statement answers`() {
        assertEquals("On track", standingKeptLine(pledgeOnly, year(0, 0, dueCount = 0), false))
        assertEquals("On track", standingKeptLine(pledgeOnly, null, false))
        assertEquals("On track", standingKeptLine(pledgeOnly, GivingStatement(year = 2026), false)) // no faithfulness block
    }

    @Test
    fun `gifts kept is said only for a schedule-only partner`() {
        assertEquals("3 gifts kept · on track", standingKeptLine(scheduleOnly, year(0, 0, dueCount = 0), false))
        assertEquals("1 gift kept · paused", standingKeptLine(scheduleOnly.copy(kept = 1), null, paused = true))
        // A cancelled monthly pledge does not make a pledge partner.
        assertEquals("3 gifts kept · on track", standingKeptLine(scheduleOnly.copy(pledges = listOf(monthly(status = "cancelled"))), null, false))
        // Neither a schedule nor a monthly pledge (a total pledge only, or just joined): the state alone.
        assertEquals("On track", standingKeptLine(Partnership(isPartner = true, pledges = listOf(total(dueOn = "2026-12-15"))), null, false))
        assertEquals("On track", standingKeptLine(Partnership(isPartner = true), null, false))
    }

    // ── Overdue wording (owner, 2026-09-26) ──

    private val sep25: LocalDate = LocalDate.of(2026, 9, 25)
    private fun pledgeDue(dueOn: String, count: Int = 0, since: String? = null, kind: String = "pledge") =
        DueItem(kind = kind, id = "p1", amountMinor = 200_000, dueOn = dueOn, overdueCount = count, overdueSince = since)

    @Test
    fun `a DUE row counts down to its date, and an instalment already past reads overdue since`() {
        assertEquals(WhenLabel("today", false), dueWhen(pledgeDue("2026-09-25"), sep25))
        assertEquals(WhenLabel("tomorrow", false), dueWhen(pledgeDue("2026-09-26"), sep25))
        assertEquals(WhenLabel("in 3 days", false), dueWhen(pledgeDue("2026-09-28"), sep25))
        assertEquals(WhenLabel("20 Oct", false), dueWhen(pledgeDue("2026-10-20"), sep25))
        // Past: amber "overdue since", one instalment behind or an older server.
        assertEquals(WhenLabel("overdue since 10 Aug", true), dueWhen(pledgeDue("2026-08-10"), sep25))
        assertEquals(WhenLabel("overdue since 10 Aug", true), dueWhen(pledgeDue("2026-08-10", count = 1), sep25))
        // Two or more behind: the count leads (the amount is the catch-up total).
        assertEquals(WhenLabel("2 overdue since 10 Aug", true), dueWhen(pledgeDue("2026-08-10", count = 2), sep25))
        // overdue_since is preferred for the date when sent.
        assertEquals(WhenLabel("3 overdue since 10 Jul", true), dueWhen(pledgeDue("2026-08-10", count = 3, since = "2026-07-10"), sep25))
        // Another year carries its year.
        assertEquals(WhenLabel("overdue since 10 Dec 2025", true), dueWhen(pledgeDue("2025-12-10"), sep25))
    }

    @Test
    fun `a recurring-gift row is never overdue, and an unreadable date reads soon`() {
        assertEquals(WhenLabel("10 Sep", false), dueWhen(pledgeDue("2026-09-10", kind = "schedule"), sep25))
        assertEquals(WhenLabel("soon", false), dueWhen(pledgeDue(""), sep25))
    }

    @Test
    fun `the pledge card says Next, or Overdue since once its next instalment has passed`() {
        fun card(nextDue: String?) = Pledge(pledgeId = "p", status = "active", progress = PledgeProgress(nextDue = nextDue))
        assertEquals(WhenLabel("Next 5 Oct", false), pledgeNextLabel(card("2026-10-05"), sep25))
        assertEquals(WhenLabel("Next 25 Sep", false), pledgeNextLabel(card("2026-09-25"), sep25)) // due today is not overdue
        assertEquals(WhenLabel("Overdue since 10 Aug", true), pledgeNextLabel(card("2026-08-10"), sep25))
        assertEquals(null, pledgeNextLabel(card(null), sep25))
    }
}
