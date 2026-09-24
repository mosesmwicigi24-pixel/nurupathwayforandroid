// The partner statement rule (docs/PARTNERS_PROGRAMME.md §3), pinned:
//   Paid      = Σ payments with a pledge_id
//   Pledged   = monthly: amount × due_day dates from max(created_at, 1 Jan)
//               through 31 Dec; total: target if due_on is in the year
//   Remaining = max(Pledged − Paid, 0)
// plus the pledge card's "N of M kept this year" and the DUE row's relative
// date. Both clients show these numbers; only this file decides them.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.PledgeProgress
import org.nuruplace.member.data.net.StatementPayment
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
}
