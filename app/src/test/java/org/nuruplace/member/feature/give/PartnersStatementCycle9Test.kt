// Giving Cycle 9 — the Partners statement never adds currencies (server
// pathway 04b6cef, docs/GIVING.md §12): the server's summary_by_currency is
// used as it came; an older server's statement is summed here to the same
// figures; a payment counts toward a pledge only in the pledge's currency.
// Pinned with the server's S10: a KES pledge 30,000 paid 25,000, a USD
// pledge 500.00 paid 200.00, and a stray USD 99.99 toward the KES pledge.
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.StatementPayment
import java.time.LocalDate

@OptIn(ExperimentalSerializationApi::class)
class PartnersStatementCycle9Test {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
    private val today = LocalDate.of(2026, 9, 28)

    // ── the Partners statement, per currency ──

    /** The server's S10: a KES pledge 30,000 paid 25,000, a USD pledge 500.00
     *  paid 200.00, and a stray USD 99.99 recorded toward the KES pledge. */
    private val kesPledge = Pledge(pledgeId = "kes", shape = "total", targetMinor = 3_000_000, currency = "KES", dueOn = "2026-11-30", title = "Roof")
    private val usdPledge = Pledge(pledgeId = "usd", shape = "total", targetMinor = 50_000, currency = "USD", dueOn = "2026-11-30", title = "Mission trip")
    private val payments = listOf(
        StatementPayment(transactionId = "t1", amountMinor = 2_500_000, currency = "KES", pledgeId = "kes", at = "2026-09-10T09:00:00Z"),
        StatementPayment(transactionId = "t2", amountMinor = 20_000, currency = "USD", pledgeId = "usd", at = "2026-09-10T09:00:00Z"),
        StatementPayment(transactionId = "t3", amountMinor = 9_999, currency = "USD", pledgeId = "kes", at = "2026-09-10T09:00:00Z"),
    )
    private val pinned = listOf(
        CurrencyStatementSummary("KES", pledgedMinor = 3_000_000, paidMinor = 2_500_000, remainingMinor = 500_000),
        CurrencyStatementSummary("USD", pledgedMinor = 50_000, paidMinor = 29_999, remainingMinor = 30_000),
    )

    @Test
    fun `the server's per-currency summary is used as it came`() {
        val st = json.decodeFromString<GivingStatement>(
            """{"years":[2026],"year":2026,"total_minor":2500000,"currency":"KES",
               "pledged_minor":3000000,"paid_minor":2500000,"remaining_minor":500000,"summary_currency":"KES",
               "summary_by_currency":[
                 {"currency":"KES","pledged_minor":3000000,"paid_minor":2500000,"remaining_minor":500000},
                 {"currency":"USD","pledged_minor":50000,"paid_minor":29999,"remaining_minor":30000}],
               "pledges":[{"pledge_id":"kes","title":"Roof","shape":"total","target_minor":3000000,"currency":"KES","pledged_minor":3000000,"paid_minor":2500000}],
               "payments":[],"by_pledge":[],"by_fund":[]}""",
        )
        assertEquals("KES", st.summaryCurrency)
        // Straight from the server — even with no pledges or payments to hand.
        assertEquals(pinned, partnerStatementSummaries(2026, st, emptyList()))
        // The headline numbers are the shilling ones; the Roof's own paid stays KSh 25,000.
        assertEquals(StatementSummary(3_000_000, 2_500_000, 500_000), partnerStatementSummary(2026, st, emptyList()))
        assertEquals(2_500_000, partnerStatementPledges(2026, st, emptyList(), today).single().paidMinor)
        // An empty list is an answer: nothing pledged or paid, KSh 0.
        val none = st.copy(summaryByCurrency = emptyList())
        assertEquals(listOf(CurrencyStatementSummary("KES", 0, 0, 0)), partnerStatementSummaries(2026, none, emptyList()))
        // The Given tile: every currency paid, shillings first.
        assertEquals(listOf(CurrencyAmount("KES", 2_500_000), CurrencyAmount("USD", 29_999)), givenTileAmounts(st))
        assertEquals("KSh 25,000 + US$ 299.99", moneyTotals(givenTileAmounts(st)))
    }

    @Test
    fun `an older server's statement is summed here to the same figures`() {
        val older = json.decodeFromString<GivingStatement>(
            """{"years":[2026],"year":2026,"total_minor":2529999,"currency":"KES","payments":[],"by_pledge":[],"by_fund":[]}""",
        ).copy(payments = payments)
        assertNull(older.summaryByCurrency)
        assertNull(older.summaryCurrency)
        assertEquals(pinned, partnerStatementSummaries(2026, older, listOf(kesPledge, usdPledge)))
        // The KES pledge's own paid stays 25,000 — the stray USD 99.99 is not its shillings.
        val rows = partnerStatementPledges(2026, older, listOf(kesPledge, usdPledge), today)
        assertEquals(2_500_000, rows.single { it.pledgeId == "kes" }.paidMinor)
        assertEquals(20_000, rows.single { it.pledgeId == "usd" }.paidMinor)
        // What a pledge still owes counts only its own currency, even summed together.
        assertEquals(500_000 + 30_000, statementSummary(2026, listOf(kesPledge, usdPledge), payments).remainingMinor)
        // With no per-currency answer, the Given tile falls back to the impact — shillings.
        assertEquals(listOf(CurrencyAmount("KES", 0)), givenTileAmounts(older))
    }

    @Test
    fun `a monthly pledge keeps a month only with money in its own currency`() {
        val monthly = Pledge(pledgeId = "m", shape = "monthly", amountMinor = 100_000, dueDay = 5, createdAt = "2026-01-01", currency = "KES")
        val paid = listOf(
            StatementPayment(transactionId = "a", amountMinor = 100_000, currency = "KES", pledgeId = "m", at = "2026-02-05T09:00:00Z"),
            StatementPayment(transactionId = "b", amountMinor = 1_000, currency = "USD", pledgeId = "m", at = "2026-03-05T09:00:00Z"),
        )
        assertEquals(1, keptThisYear(monthly, paid, today).first)
    }
}
