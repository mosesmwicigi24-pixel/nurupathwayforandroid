// The Partners statement screen's derivations (PartnersStatementLogic.kt),
// pinned: the year chips from the join year, "prefer the server's numbers,
// else the local rule", the pledge rows (server's or derived), pledge-tied
// payments by month with subtotals, and the two progress lines. Both clients
// show these; only this file decides them.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.StatementPledge
import java.time.LocalDate

class PartnersStatementLogicTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 25)

    private fun monthly(id: String = "m1", amount: Int = 200_000, dueDay: Int = 5, createdAt: String? = null, status: String = "active", title: String? = null) =
        Pledge(pledgeId = id, shape = "monthly", amountMinor = amount, dueDay = dueDay, createdAt = createdAt, status = status, title = title)

    private fun total(id: String = "t1", target: Int = 5_000_000, dueOn: String, status: String = "active") =
        Pledge(pledgeId = id, shape = "total", targetMinor = target, dueOn = dueOn, status = status)

    private fun payment(amount: Int, pledgeId: String?, at: String?, id: String = "tx-$amount-$at") =
        StatementPayment(transactionId = id, amountMinor = amount, pledgeId = pledgeId, at = at)

    // ── Year chips ──

    @Test
    fun `year chips run from this year back to the join year, at most four`() {
        assertEquals(listOf(2026), partnerStatementYears(2026, 2026))
        assertEquals(listOf(2026, 2025), partnerStatementYears(2026, 2025))
        assertEquals(listOf(2026, 2025, 2024, 2023), partnerStatementYears(2026, 2023))
        // A partner since 2019 still sees only four.
        assertEquals(listOf(2026, 2025, 2024, 2023), partnerStatementYears(2026, 2019))
    }

    @Test
    fun `year chips fall back to this year without a join year or with one in the future`() {
        assertEquals(listOf(2026), partnerStatementYears(2026, null))
        assertEquals(listOf(2026), partnerStatementYears(2026, 2027))
    }

    // ── Summary: server first, else local ──

    @Test
    fun `summary prefers the server's numbers when it sends pledged and paid`() {
        // The local rule over these inputs would say 12 × 200,000 pledged and 0 paid;
        // the server's figures win whenever they are present.
        val s = GivingStatement(year = 2026, pledgedMinor = 600_000, paidMinor = 400_000, remainingMinor = 200_000)
        assertEquals(StatementSummary(600_000, 400_000, 200_000), partnerStatementSummary(2026, s, listOf(monthly())))
    }

    @Test
    fun `summary derives remaining when the server sends pledged and paid only`() {
        val s = GivingStatement(year = 2026, pledgedMinor = 600_000, paidMinor = 400_000)
        assertEquals(StatementSummary(600_000, 400_000, 200_000), partnerStatementSummary(2026, s, emptyList()))
        // Overpaid never goes negative.
        assertEquals(0, partnerStatementSummary(2026, GivingStatement(pledgedMinor = 100, paidMinor = 900), emptyList()).remainingMinor)
    }

    @Test
    fun `summary falls back to the local rule when the server sends none`() {
        val payments = listOf(payment(200_000, "m1", "2026-10-05T09:00:00Z"), payment(999, null, "2026-10-06T09:00:00Z"))
        val s = GivingStatement(year = 2026, payments = payments)
        val pledges = listOf(monthly(createdAt = "2026-10-01T00:00:00Z")) // Oct, Nov, Dec = 600,000
        assertEquals(StatementSummary(600_000, 200_000, 400_000), partnerStatementSummary(2026, s, pledges))
        // Half a contract (paid without pledged) is not the server's answer either.
        assertEquals(StatementSummary(600_000, 200_000, 400_000), partnerStatementSummary(2026, s.copy(paidMinor = 1), pledges))
    }

    // ── Pledge rows ──

    @Test
    fun `pledge rows are the server's when present, even an empty list`() {
        val server = listOf(StatementPledge(pledgeId = "p", title = "School fees", pledgedMinor = 1, paidMinor = 2, kept = 3, dueCount = 4))
        assertEquals(server, partnerStatementPledges(2026, GivingStatement(pledges = server), listOf(monthly()), today))
        assertTrue(partnerStatementPledges(2026, GivingStatement(pledges = emptyList()), listOf(monthly()), today).isEmpty())
    }

    @Test
    fun `pledge rows fall back to the partnership's live pledges with local math`() {
        val pledges = listOf(
            monthly(id = "m1", createdAt = "2026-06-10T00:00:00Z", title = "School fees"), // due 5 Jul..5 Dec = 6 owed; elapsed by 25 Sep: Jul, Aug, Sep = 3
            total(id = "t1", dueOn = "2026-12-15"),
            monthly(id = "c", status = "cancelled"),
            total(id = "next", dueOn = "2027-03-01"),                                     // nothing owed or paid this year
        )
        val payments = listOf(
            payment(200_000, "m1", "2026-07-05T09:00:00Z"), payment(200_000, "m1", "2026-08-05T09:00:00Z"),
            payment(1_000_000, "t1", "2026-08-20T09:00:00Z"), payment(50_000, null, "2026-08-21T09:00:00Z"),
        )
        val rows = partnerStatementPledges(2026, GivingStatement(payments = payments), pledges, today)
        assertEquals(listOf("m1", "t1"), rows.map { it.pledgeId })
        val m = rows[0]
        assertEquals("School fees", m.title)
        assertEquals(6 * 200_000, m.pledgedMinor)
        assertEquals(400_000, m.paidMinor)
        assertEquals(2 to 3, m.kept to m.dueCount)
        val t = rows[1]
        assertEquals(5_000_000, t.pledgedMinor)
        assertEquals(1_000_000, t.paidMinor)
        assertEquals(0 to 0, t.kept to t.dueCount)
        assertEquals("total", t.shape)
    }

    @Test
    fun `kept in a past year counts the whole year, in a future year nothing`() {
        val pl = monthly(createdAt = "2025-03-15T00:00:00Z") // due 5 Apr..5 Dec 2025 = 9
        assertEquals(9 to 9, keptInYear(pl, 12, 2025, today))
        assertEquals(0 to 0, keptInYear(pl, 12, 2027, today))
        // This year, through today (25 Sep): Jan..Sep = 9 elapsed of 12.
        assertEquals(4 to 9, keptInYear(pl, 4, 2026, today))
        assertEquals(0 to 0, keptInYear(total(dueOn = "2026-12-01"), 3, 2026, today))
    }

    @Test
    fun `progress line reads paid and kept for monthly, paid alone for total or nothing due`() {
        assertEquals("KSh 6,000 paid · 3 of 4 kept", pledgeProgressLine(StatementPledge(shape = "monthly", paidMinor = 600_000, kept = 3, dueCount = 4)))
        assertEquals("KSh 20,000 paid", pledgeProgressLine(StatementPledge(shape = "total", paidMinor = 2_000_000, kept = 0, dueCount = 0)))
        assertEquals("KSh 0 paid", pledgeProgressLine(StatementPledge(shape = "monthly", paidMinor = 0, kept = 0, dueCount = 0)))
    }

    @Test
    fun `amount line names the rhythm or the deadline`() {
        assertEquals("KSh 2,000 monthly · due on the 5th", pledgeAmountLine(StatementPledge(shape = "monthly", amountMinor = 200_000, dueDay = 5)))
        assertEquals("KSh 2,000 monthly", pledgeAmountLine(StatementPledge(shape = "monthly", amountMinor = 200_000)))
        assertEquals("KSh 50,000 by Dec 2026", pledgeAmountLine(StatementPledge(shape = "total", targetMinor = 5_000_000, dueOn = "2026-12-15")))
    }

    // ── Payments by month ──

    @Test
    fun `payments group by month newest first with subtotals, pledge-tied only`() {
        val payments = listOf(
            payment(100_000, "m1", "2026-07-05T09:00:00Z", id = "jul5"),
            payment(300_000, "m1", "2026-09-20T09:00:00Z", id = "sep20"),
            payment(200_000, "t1", "2026-09-03T09:00:00Z", id = "sep3"),
            payment(50_000, null, "2026-09-10T09:00:00Z", id = "outside"),   // a gift outside a pledge: not here
            payment(1, "", "2026-09-11T09:00:00Z", id = "blank"),             // blank pledge id = outside too
            payment(400_000, "m1", "2026-09-20T17:00:00Z", id = "sep20pm"),
        )
        val months = paymentsByMonth(payments)
        assertEquals(listOf(9, 7), months.map { it.month })
        assertEquals(listOf(2026, 2026), months.map { it.year })
        // Newest first inside the month, the later same-day gift ahead of the earlier.
        assertEquals(listOf("sep20pm", "sep20", "sep3"), months[0].payments.map { it.transactionId })
        assertEquals(900_000, months[0].subtotalMinor)
        assertEquals(listOf("jul5"), months[1].payments.map { it.transactionId })
        assertEquals(100_000, months[1].subtotalMinor)
        assertEquals(1_000_000, statementYearTotal(months))
        assertTrue(months.none { it.undated })
    }

    @Test
    fun `undated payments keep their money in a trailing group`() {
        val months = paymentsByMonth(listOf(payment(100_000, "m1", "2026-02-05T09:00:00Z"), payment(70_000, "m1", null), payment(30_000, "m1", "not a date")))
        assertEquals(listOf(2, 0), months.map { it.month })
        assertTrue(months.last().undated)
        assertEquals(100_000, months.last().subtotalMinor)
        assertEquals(200_000, statementYearTotal(months))
    }

    @Test
    fun `no pledge payments means no months`() {
        assertTrue(paymentsByMonth(emptyList()).isEmpty())
        assertTrue(paymentsByMonth(listOf(payment(5, null, "2026-01-01T00:00:00Z"))).isEmpty())
    }
}
