// Pledge-pay mode (owner, 2026-09-26): paying a pledge, the Give screen shows
// ONE card — PAYING YOUR PLEDGE · name · terms · where the server routes it —
// instead of a fund chooser with Tithe selected. These pin the words: the
// card, the amount subtitle, the CTA, the pays-to line with and without
// `pays_to`, the need shape, and the pledge's terms line.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.FundRef
import org.nuruplace.member.data.net.Pledge
import java.time.LocalDate

class GiveTargetCopyTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 26)

    private val pledgePay = GivePreset(
        fundId = null, amountMinor = 100_000, pledgeId = "p1", title = "General partnership",
        paysTo = FundRef("discipleship", "Discipleship"), terms = "KSh 1,000 monthly · due on the 25th",
    )

    @Test
    fun `paying a pledge names it, its terms, the fund it goes to, and the button says Pay`() {
        val c = giveTargetCopy(pledgePay, chargedMajor = 1_000)!!
        assertEquals("PAYING YOUR PLEDGE", c.kicker)
        assertEquals("General partnership", c.title)
        assertEquals("KSh 1,000 monthly · due on the 25th", c.terms)
        assertEquals("Goes to the Discipleship fund", c.destination)
        assertEquals("General partnership pledge · one-time", c.amountSubtitle)
        assertEquals("Pay KSh 1,000 toward General partnership", c.cta)
    }

    @Test
    fun `without pays_to the church routes it`() {
        val c = giveTargetCopy(pledgePay.copy(paysTo = null), chargedMajor = 1_000)!!
        assertEquals("Routed by the church", c.destination)
        // A pays_to with nothing to say is the same as none.
        assertEquals("Routed by the church", giveTargetCopy(pledgePay.copy(paysTo = FundRef("", " ")), 1_000)!!.destination)
    }

    @Test
    fun `the CTA carries what will be charged, fee included`() {
        assertEquals("Pay KSh 1,013 toward General partnership", giveTargetCopy(pledgePay, chargedMajor = 1_013)!!.cta)
    }

    @Test
    fun `a pledge with no name still reads plainly`() {
        val c = giveTargetCopy(pledgePay.copy(title = "  ", terms = null), chargedMajor = 500)!!
        assertEquals("Your pledge", c.title)
        assertNull(c.terms)
        assertEquals("Pledge · one-time", c.amountSubtitle)
        assertEquals("Pay KSh 500 toward your pledge", c.cta)
    }

    @Test
    fun `a name already ending in pledge or fund is not doubled`() {
        val c = giveTargetCopy(pledgePay.copy(title = "Building pledge", paysTo = FundRef("building", "Building Fund")), 1_000)!!
        assertEquals("Building pledge · one-time", c.amountSubtitle)
        assertEquals("Goes to the Building Fund", c.destination)
    }

    @Test
    fun `pays_to without a name falls back to the local fund name for its code`() {
        assertEquals("Goes to the Discipleship fund", paysToLine(FundRef("discipleship", "")))
        assertEquals("Goes to the Tithe fund", paysToLine(FundRef("tithe", "Tithe")))
        assertEquals("Routed by the church", paysToLine(null))
    }

    @Test
    fun `giving to a need wears the same shape`() {
        val need = GivePreset(fundId = NEED_GIFT_FUND, amountMinor = 250_000, needId = "n1", title = "Sound desk")
        val c = giveTargetCopy(need, chargedMajor = 2_500)!!
        assertEquals("GIVING TO A NEED", c.kicker)
        assertEquals("Sound desk", c.title)
        assertNull(c.terms)
        assertEquals("Routed by the church", c.destination)
        assertEquals("Sound desk · one-time", c.amountSubtitle)
        assertEquals("Give KSh 2,500 to Sound desk", c.cta)

        val untitled = giveTargetCopy(need.copy(title = null), chargedMajor = 2_500)!!
        assertEquals("A department need", untitled.title)
        assertEquals("Department need · one-time", untitled.amountSubtitle)
        assertEquals("Give KSh 2,500 to this need", untitled.cta)
    }

    @Test
    fun `an unbound gift has no card`() {
        assertNull(giveTargetCopy(null, 1_000))
        assertNull(giveTargetCopy(GivePreset(fundId = "tithe", amountMinor = 100_000), 1_000))
    }

    @Test
    fun `pledge terms read monthly with the due day, or a total by its date`() {
        assertEquals(
            "KSh 1,000 monthly · due on the 25th",
            pledgeTermsLine(Pledge(pledgeId = "p", shape = "monthly", amountMinor = 100_000, dueDay = 25), today),
        )
        assertEquals("KSh 2,000 monthly", pledgeTermsLine(Pledge(pledgeId = "p", shape = "monthly", amountMinor = 200_000), today))
        assertEquals(
            "KSh 50,000 by 15 Dec",
            pledgeTermsLine(Pledge(pledgeId = "p", shape = "total", targetMinor = 5_000_000, dueOn = "2026-12-15"), today),
        )
        // Not this year → the year is said.
        assertEquals(
            "KSh 50,000 by 15 Mar 2027",
            pledgeTermsLine(Pledge(pledgeId = "p", shape = "total", targetMinor = 5_000_000, dueOn = "2027-03-15"), today),
        )
        assertEquals("KSh 50,000 in total", pledgeTermsLine(Pledge(pledgeId = "p", shape = "total", targetMinor = 5_000_000), today))
        // Nothing to state → no line.
        assertNull(pledgeTermsLine(Pledge(pledgeId = "p", shape = "monthly", amountMinor = null), today))
        assertNull(pledgeTermsLine(Pledge(pledgeId = "p", shape = "total", targetMinor = 0), today))
    }
}
