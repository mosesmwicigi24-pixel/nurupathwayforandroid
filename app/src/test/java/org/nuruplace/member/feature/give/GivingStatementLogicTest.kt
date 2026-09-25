// The giving statement's split (GivingStatementLogic.kt, spec §3d), pinned:
// gifts are rows without a pledge_id and make the hero, BY FUND and the day
// list; pledge rows sit in one PARTNER PLEDGES group; Gifts + Partner pledges
// = Total, and the hero keeps "Total given" when there is no pledge money.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.GivingRecord
import java.time.LocalDate

class GivingStatementLogicTest {
    private fun rec(id: String, amount: Int, at: String, pledgeId: String? = null, pledgeTitle: String? = null, fund: String = "tithe") =
        GivingRecord(
            transactionId = id, amountMinor = amount, status = "succeeded", fund = fund, createdAt = at,
            pledgeId = pledgeId, pledgeTitle = pledgeTitle,
        )

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
        assertEquals(210_000, split.giftsMinor)
        assertEquals(400_000, split.pledgesMinor)
        assertEquals(610_000, split.totalMinor)
        assertEquals(split.giftsMinor + split.pledgesMinor, split.totalMinor)
    }

    @Test
    fun `hero leads with gifts and names the pledges and total when there is pledge money`() {
        val hero = givingHero(givingSplit(listOf(gift1, gift2, pledge1, pledge2)))
        assertEquals("Gifts", hero.label)
        assertEquals(200_000, hero.amountMinor)
        assertEquals("Partner pledges KSh 4,000 · Total KSh 6,000", hero.pledgeLine)
        assertEquals("TOTAL GIFTS", hero.footLabel)
    }

    @Test
    fun `hero keeps Total given when there is no pledge money`() {
        val hero = givingHero(givingSplit(listOf(gift1, gift2)))
        assertEquals("Total given", hero.label)
        assertEquals(200_000, hero.amountMinor)
        assertNull(hero.pledgeLine)
        assertEquals("TOTAL GIVEN", hero.footLabel)
        // Nothing at all is still "Total given · KSh 0".
        val empty = givingHero(givingSplit(emptyList()))
        assertEquals("Total given", empty.label)
        assertEquals(0, empty.amountMinor)
    }

    @Test
    fun `pledge group is its total, count and rows by day newest first`() {
        val late = rec("p3", 100_000, "2026-09-05T15:00:00Z", pledgeId = "pl2", pledgeTitle = "New roof")
        val g = pledgeGroup(givingSplit(listOf(gift1, pledge2, pledge1, late)))!!
        assertEquals(500_000, g.totalMinor)
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
}
