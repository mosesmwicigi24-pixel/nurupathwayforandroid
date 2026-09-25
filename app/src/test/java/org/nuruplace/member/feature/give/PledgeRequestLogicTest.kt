// "What is this pledge for?" on the wire (pledge names, contract 2026-09-25),
// pinned: an option travels as its target and NO title; General travels as
// nothing; a custom name travels as `title` ONLY, trimmed and length-checked.
// Plus the picker's grouping and its fallback when the server sends no options.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.PledgeOption
import java.time.LocalDate

class PledgeRequestLogicTest {
    private val general = PledgeOption(key = "general", title = "General partnership", kind = "general")
    private val fund = PledgeOption(key = "fund:mission", title = "Mission", kind = "fund", fund = "mission")
    private val campaign = PledgeOption(key = "campaign:c1", title = "New roof", kind = "campaign", campaignId = "c1")
    private val need = PledgeOption(key = "need:n1", title = "Sound desk", kind = "need", needId = "n1")

    private fun body(target: PledgeFor?, shape: String = "monthly") =
        buildPledgeBody(shape = shape, amountMajor = 1_000, target = target, dueDay = 5, dueOn = LocalDate.of(2026, 12, 31), autoMethod = null)

    @Test
    fun `general partnership sends no target and no title`() {
        val b = body(PledgeFor.Option(general))
        assertNull(b.fund); assertNull(b.campaignId); assertNull(b.needId); assertNull(b.title)
        // A null target is General too.
        val n = body(null)
        assertNull(n.fund); assertNull(n.campaignId); assertNull(n.needId); assertNull(n.title)
        assertEquals(100_000, b.amountMinor)
        assertEquals(5, b.dueDay)
    }

    @Test
    fun `a fund option sends the fund code and no title`() {
        val b = body(PledgeFor.Option(fund))
        assertEquals("mission", b.fund)
        assertNull(b.campaignId); assertNull(b.needId); assertNull(b.title)
    }

    @Test
    fun `a campaign option sends campaign_id`() {
        val b = body(PledgeFor.Option(campaign), shape = "total")
        assertEquals("c1", b.campaignId)
        assertNull(b.fund); assertNull(b.needId); assertNull(b.title)
        assertEquals(100_000, b.targetMinor)
        assertEquals("2026-12-31", b.dueOn)
    }

    @Test
    fun `a need option sends need_id`() {
        val b = body(PledgeFor.Option(need))
        assertEquals("n1", b.needId)
        assertNull(b.fund); assertNull(b.campaignId); assertNull(b.title)
    }

    @Test
    fun `a custom name sends title only, trimmed`() {
        val b = body(PledgeFor.Custom("  Mum's house  "))
        assertEquals("Mum's house", b.title)
        assertNull(b.fund); assertNull(b.campaignId); assertNull(b.needId)
    }

    @Test
    fun `a custom name is 2 to 60 characters once trimmed`() {
        assertFalse(pledgeTitleValid(""))
        assertFalse(pledgeTitleValid(" a "))
        assertTrue(pledgeTitleValid("ab"))
        assertTrue(pledgeTitleValid("x".repeat(60)))
        assertFalse(pledgeTitleValid("x".repeat(61)))
        // An invalid custom name never leaks onto the wire as a title.
        assertNull(body(PledgeFor.Custom("a")).title)
    }

    @Test
    fun `the review step names the choice`() {
        assertEquals("General partnership", pledgeForTitle(null))
        assertEquals("General partnership", pledgeForTitle(PledgeFor.Option(general)))
        assertEquals("Mission", pledgeForTitle(PledgeFor.Option(fund)))
        assertEquals("Mum's house", pledgeForTitle(PledgeFor.Custom(" Mum's house ")))
    }

    @Test
    fun `options fall back to general plus the funds when the server sends none`() {
        val fallback = pledgeOptionsOrFallback(emptyList())
        assertEquals("general", fallback.first().kind)
        assertEquals(GIVE_FUNDS.map { it.id }, fallback.drop(1).map { it.fund })
        assertTrue(fallback.drop(1).all { it.kind == "fund" })
        // The server's list is used as sent, in its order.
        val server = listOf(general, fund, campaign, need)
        assertEquals(server, pledgeOptionsOrFallback(server))
    }

    @Test
    fun `options group by kind in the server's order with the section labels`() {
        val other = PledgeOption(key = "x", title = "Something new", kind = "mystery")
        val groups = groupedPledgeOptions(listOf(general, need, fund, other, campaign))
        assertEquals(listOf("General", "Funds", "Campaigns", "Department needs", "Other"), groups.map { it.label })
        assertEquals(listOf(need), groups[3].options)
        assertEquals(listOf(other), groups[4].options)
        // Empty kinds have no section.
        assertEquals(listOf("General", "Funds"), groupedPledgeOptions(listOf(general, fund)).map { it.label })
    }

    @Test
    fun `a card is picked by key, and General by kind alone`() {
        assertTrue(pledgeOptionSelected(fund, PledgeFor.Option(fund)))
        assertFalse(pledgeOptionSelected(fund, PledgeFor.Option(campaign)))
        // The server's General row and the flow's local default share a kind, not a key.
        val serverGeneral = PledgeOption(key = "partnership", title = "General partnership", kind = "general")
        assertTrue(pledgeOptionSelected(serverGeneral, PledgeFor.Option(GENERAL_PLEDGE_OPTION)))
        assertFalse(pledgeOptionSelected(fund, PledgeFor.Option(GENERAL_PLEDGE_OPTION)))
        // A custom name — or nothing — lights no option's card.
        assertFalse(pledgeOptionSelected(general, PledgeFor.Custom("Mum's house")))
        assertFalse(pledgeOptionSelected(general, null))
    }
}
