// Giving Cycle 5 — the Partners statement math mirrors the server's
// partnerStatementMath.ts exactly, pinned with the server's own cases
// (test/giving-cycle-05.test.ts S9, S17, and the coordinator's Kenya-trip
// case): remaining is owed PER PLEDGE, a pledge starts at the later of its
// starts_on and its creation day, its instalments end at until_on, dates are
// the church's (Nairobi) calendar, and nothing is summed across currencies.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.StatementPayment
import java.time.LocalDate

class PartnerStatementCycle5Test {
    private fun pay(pledgeId: String?, amount: Int, currency: String = "KES", at: String = "2026-06-10T09:00:00Z") =
        StatementPayment(transactionId = "t-$pledgeId-$amount", amountMinor = amount, currency = currency, pledgeId = pledgeId, at = at)

    // ── (a) Kenya trip: money beyond one pledge never hides what another owes ──

    @Test
    fun `remaining is owed per pledge — an overpaid pledge never hides another's`() {
        val trip = Pledge(pledgeId = "trip", shape = "total", targetMinor = 1_800_000, dueOn = "2026-12-20", title = "Kenya trip")
        val small = Pledge(pledgeId = "small", shape = "total", targetMinor = 30_000, dueOn = "2026-11-30")
        val s = statementSummary(2026, listOf(trip, small), listOf(pay("trip", 900_000), pay("small", 50_000)))
        assertEquals(1_830_000, s.pledgedMinor)
        assertEquals(950_000, s.paidMinor)
        // KSh 9,000 still owed on the trip; the KSh 200 over on the small one does not count against it.
        assertEquals(900_000, s.remainingMinor) // was max(18,300 − 9,500) = KSh 8,800
    }

    // ── (b) the server's S17 ──

    @Test
    fun `S17 — a monthly, an overpaid total and a cancelled one foot per pledge`() {
        val a = Pledge(pledgeId = "a", shape = "monthly", amountMinor = 1_000, dueDay = 5, createdAt = "2026-01-01")
        val b = Pledge(pledgeId = "b", shape = "total", targetMinor = 3_000, dueOn = "2026-11-30", createdAt = "2026-01-01")
        val c = Pledge(pledgeId = "c", shape = "total", targetMinor = 5_000, dueOn = "2026-11-30", status = "cancelled", createdAt = "2026-01-01")
        val s = statementSummary(2026, listOf(a, b, c), listOf(pay("a", 4_000), pay("b", 5_000), pay("c", 1_000)))
        assertEquals(StatementSummary(pledgedMinor = 15_000, paidMinor = 10_000, remainingMinor = 8_000), s)
    }

    // ── (c) the server's S9: until_on ends the instalments ──

    private val ended = Pledge(
        pledgeId = "x", shape = "monthly", amountMinor = 100, dueDay = 5, createdAt = "2026-08-01", untilOn = "2026-10-31",
    )

    @Test
    fun `S9 — a monthly pledge to 31 Oct is pledged for Aug, Sep, Oct and has no instalment after`() {
        assertEquals(300, pledgedInYear(ended, 2026))
        assertEquals(
            listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5)),
            instalmentDueDates(ended, through = LocalDate.of(2026, 12, 20)),
        )
        // Nothing comes due after the end: 3 elapsed, however late in the year.
        assertEquals(0 to 3, keptThisYear(ended, emptyList(), LocalDate.of(2026, 12, 20)))
        assertEquals(0 to 3, keptInYear(ended, 0, 2026, LocalDate.of(2026, 12, 20)))
        assertEquals(0, pledgedInYear(ended, 2027))
    }

    // ── starts_on: a pledge collected automatically starts with its first collection ──

    @Test
    fun `a pledge starts on the later of its starts_on and its creation day`() {
        val auto = Pledge(pledgeId = "p", shape = "monthly", amountMinor = 100_000, dueDay = 5, createdAt = "2026-09-05T09:00:00Z", startsOn = "2026-10-05")
        assertEquals(LocalDate.of(2026, 10, 5), pledgeStart(auto))
        // Made on its own due day, collected from next month: Oct, Nov, Dec — not Sep.
        assertEquals(300_000, pledgedInYear(auto, 2026))
        assertEquals(LocalDate.of(2026, 10, 5), instalmentDueDates(auto, LocalDate.of(2026, 12, 31)).first())
        // A starts_on before creation (never sent, but held to the rule): the creation day.
        assertEquals(LocalDate.of(2026, 9, 5), pledgeStart(auto.copy(startsOn = "2026-01-01")))
        assertEquals(LocalDate.of(2026, 9, 5), pledgeStart(auto.copy(startsOn = null)))
        assertEquals(LocalDate.of(2026, 10, 5), pledgeStart(auto.copy(createdAt = null)))
        assertNull(pledgeStart(auto.copy(createdAt = null, startsOn = null)))
    }

    @Test
    fun `dates are the church's calendar — an evening in UTC is the next morning in Nairobi`() {
        // 21:30 UTC on 31 Jul is 00:30 on 1 Aug in Nairobi: the pledge starts in August.
        assertEquals(LocalDate.of(2026, 8, 1), partnerDate("2026-07-31T21:30:00Z"))
        assertEquals(LocalDate.of(2026, 7, 31), partnerDate("2026-07-31T20:30:00Z"))
        assertEquals(LocalDate.of(2026, 8, 1), partnerDate("2026-08-01T00:30:00+03:00"))
        // A bare date is already a calendar day — never shifted.
        assertEquals(LocalDate.of(2026, 7, 31), partnerDate("2026-07-31"))
        assertNull(partnerDate("not a date"))
        assertNull(partnerDate(" "))
        val lateJuly = Pledge(pledgeId = "p", shape = "monthly", amountMinor = 100, dueDay = 1, createdAt = "2026-07-31T21:30:00Z")
        assertEquals(500, pledgedInYear(lateJuly, 2026)) // 1 Aug … 1 Dec: five due days × 100
    }

    // ── never summed across currencies ──

    @Test
    fun `a statement in shillings and dollars has one summary per currency`() {
        val kes = Pledge(pledgeId = "k", shape = "monthly", amountMinor = 100_000, dueDay = 5, createdAt = "2026-01-01", currency = "KES")
        val usd = Pledge(pledgeId = "u", shape = "total", targetMinor = 50_000, dueOn = "2026-12-01", currency = "USD")
        val sums = statementSummaries(2026, listOf(usd, kes), listOf(pay("k", 400_000), pay("u", 12_500, "USD")))
        assertEquals(
            listOf(
                CurrencyStatementSummary("KES", pledgedMinor = 1_200_000, paidMinor = 400_000, remainingMinor = 800_000),
                CurrencyStatementSummary("USD", pledgedMinor = 50_000, paidMinor = 12_500, remainingMinor = 37_500),
            ),
            sums,
        )
        // One currency: one summary, as before; nothing at all: KSh 0.
        assertEquals(listOf("KES"), statementSummaries(2026, listOf(kes), listOf(pay("k", 1))).map { it.currency })
        assertEquals(listOf(CurrencyStatementSummary("KES", 0, 0, 0)), statementSummaries(2026, emptyList(), emptyList()))
        // A gift outside any pledge never enters it.
        assertEquals(listOf("KES"), statementSummaries(2026, listOf(kes), listOf(pay(null, 5_000, "USD"))).map { it.currency })
    }

    @Test
    fun `the server's single numbers stand only while everything is in one currency`() {
        val kes = Pledge(pledgeId = "k", shape = "monthly", amountMinor = 100_000, dueDay = 5, createdAt = "2026-01-01")
        val one = GivingStatement(year = 2026, pledgedMinor = 1_200_000, paidMinor = 300_000, remainingMinor = 900_000, payments = listOf(pay("k", 300_000)))
        assertEquals(listOf(CurrencyStatementSummary("KES", 1_200_000, 300_000, 900_000)), partnerStatementSummaries(2026, one, listOf(kes)))
        // Add a dollar pledge: the server's one sum cannot be split, so each currency is summed on its own.
        val usd = Pledge(pledgeId = "u", shape = "total", targetMinor = 50_000, dueOn = "2026-12-01", currency = "USD")
        val two = one.copy(payments = one.payments + pay("u", 10_000, "USD"), pledgedMinor = 1_250_000, paidMinor = 310_000)
        assertEquals(
            listOf(CurrencyStatementSummary("KES", 1_200_000, 300_000, 900_000), CurrencyStatementSummary("USD", 50_000, 10_000, 40_000)),
            partnerStatementSummaries(2026, two, listOf(kes, usd)),
        )
        // The month list and its foot are per currency too.
        val months = paymentsByMonth(two.payments)
        assertEquals(listOf(CurrencyAmount("KES", 300_000), CurrencyAmount("USD", 10_000)), months.single().subtotals)
        assertEquals("KSh 3,000 + US$ 100.00", moneyTotals(statementYearTotals(months)))
    }

    // ── the first automatic collection: never today ──

    @Test
    fun `the first collection is the first due day strictly after today`() {
        // Later this month.
        assertEquals(LocalDate.of(2026, 9, 20), firstDueAfter(LocalDate.of(2026, 9, 10), 20))
        // Already past this month: next month.
        assertEquals(LocalDate.of(2026, 10, 5), firstDueAfter(LocalDate.of(2026, 9, 28), 5))
        assertEquals(LocalDate.of(2026, 10, 1), firstDueAfter(LocalDate.of(2026, 9, 28), 1))
    }

    @Test
    fun `a due day of today is next month, never today`() {
        assertEquals(LocalDate.of(2026, 10, 5), firstDueAfter(LocalDate.of(2026, 9, 5), 5))
        assertEquals(LocalDate.of(2026, 10, 28), firstDueAfter(LocalDate.of(2026, 9, 28), 28))
        assertEquals(LocalDate.of(2026, 9, 6), firstDueAfter(LocalDate.of(2026, 9, 5), 6)) // tomorrow is fine
    }

    @Test
    fun `day 28 at the month's end, and December into January`() {
        // 30 Sep is past the 28th: the 28th of October.
        assertEquals(LocalDate.of(2026, 10, 28), firstDueAfter(LocalDate.of(2026, 9, 30), 28))
        // 28 Feb in a common year is the 28th itself — so next month.
        assertEquals(LocalDate.of(2027, 3, 28), firstDueAfter(LocalDate.of(2027, 2, 28), 28))
        // A day past 28 is held to 28, as the server holds it.
        assertEquals(LocalDate.of(2026, 10, 28), firstDueAfter(LocalDate.of(2026, 9, 29), 31))
        // December rolls into January of the next year.
        assertEquals(LocalDate.of(2027, 1, 5), firstDueAfter(LocalDate.of(2026, 12, 5), 5))
        assertEquals(LocalDate.of(2027, 1, 3), firstDueAfter(LocalDate.of(2026, 12, 20), 3))
        assertEquals(LocalDate.of(2026, 12, 25), firstDueAfter(LocalDate.of(2026, 12, 20), 25))
    }
}
