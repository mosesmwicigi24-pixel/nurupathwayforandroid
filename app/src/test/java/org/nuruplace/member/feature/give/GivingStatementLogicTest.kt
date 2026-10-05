// The giving statement's split (GivingStatementLogic.kt, spec §3d), pinned:
// gifts are rows without a pledge_id and make the hero, BY FUND and the day
// list; pledge rows sit in one PARTNER PLEDGES group; Gifts + Partner pledges
// = Total, and the hero keeps "Total given" when there is no pledge money.
// Every gift is listed whatever became of it; only settled money is counted.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.GivingRecord
import java.time.LocalDate

class GivingStatementLogicTest {
    private fun rec(
        id: String, amount: Int, at: String, pledgeId: String? = null, pledgeTitle: String? = null,
        fund: String = "tithe", currency: String = "KES", settledAt: String? = null, status: String = "succeeded",
    ) =
        GivingRecord(
            transactionId = id, amountMinor = amount, currency = currency, status = status, fund = fund, createdAt = at,
            settledAt = settledAt, pledgeId = pledgeId, pledgeTitle = pledgeTitle,
        )

    private fun sums(vararg pairs: Pair<String, Long>) = pairs.map { CurrencyAmount(it.first, it.second) }

    private val gift1 = rec("g1", 150_000, "2026-09-20T07:00:00Z")
    private val gift2 = rec("g2", 50_000, "2026-09-02T07:00:00Z", fund = "offering")
    private val pledge1 = rec("p1", 200_000, "2026-09-05T09:00:00Z", pledgeId = "pl", pledgeTitle = "School fees")
    private val pledge2 = rec("p2", 200_000, "2026-08-05T09:00:00Z", pledgeId = "pl", pledgeTitle = "School fees")
    private val blankPledge = rec("b1", 10_000, "2026-09-01T07:00:00Z", pledgeId = "  ")

    @Test
    fun `split puts pledge-tied rows apart and the totals foot`() {
        val split = givingSplit(listOf(gift1, pledge1, gift2, pledge2, blankPledge))
        assertEquals(listOf("g1", "g2", "b1"), split.gifts.map { it.transactionId }) // a blank pledge id is a gift
        assertEquals(listOf("p1", "p2"), split.pledges.map { it.transactionId })
        assertEquals(sums("KES" to 210_000), split.giftsSums)
        assertEquals(sums("KES" to 400_000), split.pledgesSums)
        assertEquals(sums("KES" to 610_000), split.totalSums)
    }

    @Test
    fun `hero leads with gifts and names the pledges and total when there is pledge money`() {
        val hero = givingHero(givingSplit(listOf(gift1, gift2, pledge1, pledge2)))
        assertEquals("Gifts", hero.label)
        assertEquals("KSh 2,000", hero.primary)
        assertNull(hero.extra)
        assertEquals("Partner pledges KSh 4,000 · Total KSh 6,000", hero.pledgeLine)
        assertEquals("TOTAL GIFTS", hero.footLabel)
    }

    @Test
    fun `hero keeps Total given when there is no pledge money`() {
        val hero = givingHero(givingSplit(listOf(gift1, gift2)))
        assertEquals("Total given", hero.label)
        assertEquals("KSh 2,000", hero.primary)
        assertNull(hero.pledgeLine)
        assertEquals("TOTAL GIVEN", hero.footLabel)
        // Nothing at all is still "Total given · KSh 0".
        val empty = givingHero(givingSplit(emptyList()))
        assertEquals("Total given", empty.label)
        assertEquals("KSh 0", empty.primary)
        assertNull(empty.extra)
    }

    // ── Per currency (Giving Cycle 2): never one sum across currencies ──

    private val usdGift = rec("u1", 2_000, "2026-09-10T07:00:00Z", currency = "USD", fund = "mission")
    private val usdPledge = rec("u2", 1_250, "2026-09-11T07:00:00Z", pledgeId = "pl", pledgeTitle = "School fees", currency = "usd")

    @Test
    fun `one currency reads as one amount, two as shillings plus dollars`() {
        assertEquals("KSh 2,000", moneyTotals(currencySums(listOf(gift1, gift2))))
        assertEquals("KSh 3,500 + US$ 20.00", moneyTotals(currencySums(listOf(usdGift, gift1, rec("g3", 200_000, "2026-09-03T07:00:00Z")))))
        // Shillings always first, then by code — the server's totals[] order.
        assertEquals(
            sums("KES" to 150_000, "EUR" to 500, "USD" to 2_000),
            currencySums(listOf(usdGift, rec("e", 500, "2026-09-01T00:00:00Z", currency = "EUR"), gift1)),
        )
        // Dollars alone lead with dollars; nothing reads "KSh 0".
        assertEquals("US$ 20.00", moneyTotals(currencySums(listOf(usdGift))))
        assertEquals("KSh 0", moneyTotals(emptyList()))
    }

    @Test
    fun `the hero leads with the first currency and adds the other under it`() {
        val hero = givingHero(givingSplit(listOf(gift1, usdGift)))
        assertEquals("Total given", hero.label)
        assertEquals("KSh 1,500", hero.primary)
        assertEquals("+ US$ 20.00", hero.extra)
        // With pledge money: gifts, pledges and the total each per currency, and they foot.
        val split = givingSplit(listOf(gift1, usdGift, pledge1, usdPledge))
        val withPledges = givingHero(split)
        assertEquals("KSh 1,500", withPledges.primary)
        assertEquals("+ US$ 20.00", withPledges.extra)
        assertEquals("Partner pledges KSh 2,000 + US$ 12.50 · Total KSh 3,500 + US$ 32.50", withPledges.pledgeLine)
        split.totalSums.forEach { t ->
            val g = split.giftsSums.firstOrNull { it.currency == t.currency }?.minor ?: 0
            val p = split.pledgesSums.firstOrNull { it.currency == t.currency }?.minor ?: 0
            assertEquals(t.currency, t.minor, g + p)
        }
        assertEquals("KSh 2,000 + US$ 12.50 · 2 payments", pledgeGroup(split)!!.summary)
    }

    @Test
    fun `BY FUND has one row per fund per currency`() {
        val lines = fundLines(listOf(gift1, usdGift, gift2, rec("g4", 30_000, "2026-09-04T07:00:00Z", fund = "mission")))
        assertEquals(
            listOf(
                FundLine("tithe", "KES", 150_000, 1),
                FundLine("mission", "KES", 30_000, 1),
                FundLine("mission", "USD", 2_000, 1),
                FundLine("offering", "KES", 50_000, 1),
            ),
            lines,
        )
    }

    @Test
    fun `a gift counts in the Nairobi year it was made, as the server's statement counts it`() {
        // Made 23:30 on 31 Dec in Nairobi (20:30Z), settled after midnight: last year.
        val newYearsEve = rec("y", 1, "2025-12-31T20:30:00Z", settledAt = "2025-12-31T21:10:00Z")
        assertEquals(2025, givingYear(newYearsEve))
        // 00:30 on 1 January in Nairobi is 21:30Z the day before: the new year.
        assertEquals(2026, givingYear(rec("z", 1, "2025-12-31T21:30:00Z")))
        // No readable creation time → when it settled.
        assertEquals(2026, givingYear(rec("s", 1, "bad", settledAt = "2026-03-01T10:00:00Z")))
        assertNull(givingYear(rec("n", 1, "bad")))
    }

    @Test
    fun `pledge group is its total, count and rows by day newest first`() {
        val late = rec("p3", 100_000, "2026-09-05T15:00:00Z", pledgeId = "pl2", pledgeTitle = "New roof")
        val g = pledgeGroup(givingSplit(listOf(gift1, pledge2, pledge1, late)))!!
        assertEquals(sums("KES" to 500_000), g.totals)
        assertEquals(3, g.count)
        assertEquals("KSh 5,000 · 3 payments", g.summary)
        assertEquals(listOf(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 8, 5)), g.days.map { it.date })
        assertEquals(listOf("p3", "p1"), g.days[0].records.map { it.transactionId })
        assertEquals(listOf("p2"), g.days[1].records.map { it.transactionId })
        // Gifts never enter the group.
        assertTrue(g.days.flatMap { it.records }.none { it.pledgeId.isNullOrBlank() })
    }

    @Test
    fun `pledge group says one payment and is absent without pledge rows`() {
        assertEquals("KSh 2,000 · 1 payment", pledgeGroup(givingSplit(listOf(pledge1)))!!.summary)
        assertNull(pledgeGroup(givingSplit(listOf(gift1, gift2))))
        assertNull(pledgeGroup(givingSplit(emptyList())))
    }

    // ── Every gift listed, only settled money counted (iOS) ──

    private val failedGift = rec("f1", 100_000, "2026-09-21T07:00:00Z", status = "failed")
    private val waitingGift = rec("w1", 70_000, "2026-09-22T07:00:00Z", status = "processing")
    private val completed = rec("c1", 30_000, "2026-09-23T07:00:00Z", status = "completed")

    @Test
    fun `a failed or waiting gift is listed but never counted`() {
        val split = givingSplit(listOf(gift1, failedGift, waitingGift, completed))
        // Listed: every one of them, in the day list.
        assertEquals(listOf("g1", "f1", "w1", "c1"), split.gifts.map { it.transactionId })
        assertEquals(setOf("g1", "f1", "w1", "c1"), statementDays(split.gifts).flatMap { it.records }.map { it.transactionId }.toSet())
        // Counted: only what went through — succeeded, settled, completed.
        assertEquals(sums("KES" to 180_000), split.giftsSums)
        assertEquals(2, settledCount(split.gifts))
        assertEquals(listOf(FundLine("tithe", "KES", 180_000, 2)), fundLines(split.gifts))
        val hero = givingHero(split)
        assertEquals("Total given", hero.label)
        assertEquals("KSh 1,800", hero.primary)
    }

    @Test
    fun `a pledge payment that has not settled is listed in the group and said, never counted`() {
        val failedPledge = rec("fp", 200_000, "2026-09-06T09:00:00Z", pledgeId = "pl", pledgeTitle = "School fees", status = "failed")
        val g = pledgeGroup(givingSplit(listOf(gift1, pledge1, failedPledge)))!!
        assertEquals(sums("KES" to 200_000), g.totals)
        assertEquals(1, g.count)
        assertEquals(1, g.unsettled)
        assertEquals("KSh 2,000 · 1 payment · 1 not settled", g.summary)
        assertEquals(setOf("p1", "fp"), g.days.flatMap { it.records }.map { it.transactionId }.toSet())
        // Only an unsettled pledge row: the rows are still separated (the group
        // shows), but the hero does not split — there is no pledge money.
        val onlyFailed = givingSplit(listOf(gift1, failedPledge))
        assertEquals("KSh 0 · 0 payments · 1 not settled", pledgeGroup(onlyFailed)!!.summary)
        val hero = givingHero(onlyFailed)
        assertEquals("Total given", hero.label)
        assertNull(hero.pledgeLine)
        assertEquals("TOTAL GIVEN", hero.footLabel)
    }

    @Test
    fun `a statement row's chip is iOS's — its four statuses, any other by name`() {
        assertEquals("Succeeded", giveStatus("completed").first)
        assertEquals("Processing", giveStatus("processing").first)
        assertEquals("Failed", giveStatus("failed").first)
        assertEquals("Refunded", giveStatus("refunded").first)
        // A cancelled gift was never refunded.
        assertEquals("Cancelled", giveStatus("cancelled").first)
        assertEquals("Pending", giveStatus("pending").first)
        assertEquals("Requires action", giveStatus("requires_action").first)
    }

    @Test
    fun `day list groups by Nairobi day and keeps unreadable dates`() {
        // 22:30Z on the 19th is already the 20th in Nairobi (UTC+3).
        val nightOwl = rec("n", 1, "2026-09-19T22:30:00Z")
        val odd = rec("x", 2, "not a date")
        val days = statementDays(listOf(gift2, nightOwl, gift1, odd))
        assertEquals(listOf(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 2), null), days.map { it.date })
        assertEquals(listOf("g1", "n"), days[0].records.map { it.transactionId })
        assertEquals(listOf("x"), days[2].records.map { it.transactionId })
    }

    @Test
    fun `pledge tag names the pledge, else Partner`() {
        assertEquals("School fees", pledgeTagTitle(pledge1))
        assertEquals("Partner", pledgeTagTitle(rec("q", 1, "2026-09-01T00:00:00Z", pledgeId = "pl", pledgeTitle = " ")))
        assertEquals("Partner", pledgeTagTitle(rec("q", 1, "2026-09-01T00:00:00Z", pledgeId = "pl")))
    }

    @Test
    fun `the row's pledge tag never doubles the word`() {
        // The tag reads pledgeTag(pledgeTagTitle(row)), as iOS pledgeTag(g).
        assertEquals("School fees pledge", pledgeTag(pledgeTagTitle(pledge1)))
        assertEquals("Partner pledge", pledgeTag(pledgeTagTitle(rec("q", 1, "2026-09-01T00:00:00Z", pledgeId = "pl"))))
        listOf("Building pledge", "Building Pledge", "BUILDING PLEDGE").forEach { name ->
            assertEquals(name, name, pledgeTag(pledgeTagTitle(rec("b", 1, "2026-09-01T00:00:00Z", pledgeId = "pl", pledgeTitle = name))))
        }
    }
}

class StatementTimeTest {
    @org.junit.Test fun `a settled gift shows when it settled, as its receipt does (Cycle 3 closing walk B8)`() {
        val sent = org.nuruplace.member.data.net.GivingRecord(transactionId = "t", amountMinor = 20_000, createdAt = "2026-10-05T08:58:10Z", settledAt = "2026-10-05T08:59:02Z")
        org.junit.Assert.assertEquals("2026-10-05T08:59:02Z", statementTime(sent))
        org.junit.Assert.assertEquals("2026-10-05T08:58:10Z", statementTime(sent.copy(settledAt = null)))
    }
}
