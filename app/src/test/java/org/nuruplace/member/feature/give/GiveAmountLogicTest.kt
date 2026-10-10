// Giving Cycle 2 — the amount in two currencies. PayPal's dollar entry takes
// cents ("25.50" is 2550), shows "US$ 25.50", and is typed with at most two
// decimals; covering the M-Pesa fee splits the charged total into the gift
// and the fee the receipt names.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.ui.theme.TypeScale

class GiveAmountLogicTest {
    @Test
    fun `dollar text reads as cents`() {
        mapOf(
            "25" to 2_500, "25.5" to 2_550, "25.50" to 2_550, "25.05" to 2_505, "0.99" to 99, "0.5" to 50,
            "25." to 2_500, "1,000" to 100_000, "1,000.25" to 100_025, " 10 " to 1_000, "0" to 0, "100" to 10_000,
        ).forEach { (text, cents) -> assertEquals(text, cents, usdCentsOf(text)) }
    }

    @Test
    fun `anything else is not an amount`() {
        listOf("", " ", ".", ".5", "25.505", "2.5.0", "-5", "5e2", "abc", "US$ 5", "12345678").forEach { text ->
            assertNull("\"$text\"", usdCentsOf(text))
        }
    }

    @Test
    fun `the dollar field keeps digits, one point and two decimals as it is typed`() {
        assertEquals("25.50", usdTyping("25.50"))
        assertEquals("25.50", usdTyping("25.505"))
        assertEquals("25.50", usdTyping("25.5.0")) // a second point is dropped, its digits kept
        assertEquals("25", usdTyping("2a5"))
        assertEquals("1000", usdTyping("1,000"))
        assertEquals("12.", usdTyping("12."))
        assertEquals("1234567", usdTyping("123456789"))
    }

    @Test
    fun `cents show as the field and as money`() {
        assertEquals("25", usdInput(2_500))
        assertEquals("25.50", usdInput(2_550))
        assertEquals("0.99", usdInput(99))
        assertEquals("US$ 25.00", usd(2_500))
        assertEquals("US$ 1,000.50", usd(100_050))
        // Round trip: what the field shows reads back as the same cents.
        listOf(1, 99, 100, 2_550, 1_000_000).forEach { assertEquals(it, usdCentsOf(usdInput(it))) }
        assertEquals("Enter an amount between US$ 1.00 and US$ 10,000.00.", usdRangeMessage(100, 1_000_000))
    }

    @Test
    fun `the dollar form suggests five amounts and starts on ten`() {
        assertEquals(listOf(5, 10, 25, 50, 100), USD_PRESETS)
        assertEquals(1_000, DEFAULT_USD_CENTS)
    }

    @Test
    fun `covering the fee splits the charged total into gift and fee`() {
        val covered = feeSplit(1_000, coverFee = true)
        assertEquals(100_000, covered.giftMinor)
        assertEquals(1_300, covered.feeMinor) // iOS feeFor(1000) == 13
        assertEquals(101_300, covered.totalMinor) // amount_minor = the total charged
        assertEquals(chargedAmountMajor(1_000, true) * 100, covered.totalMinor)
        val plain = feeSplit(1_000, coverFee = false)
        assertEquals(0, plain.feeMinor)
        assertEquals(100_000, plain.totalMinor)
        // Whole shillings, and never more than half the gift (the server's rule).
        listOf(100, 500, 1_000, 2_500, 5_000, 10_000, 250_000).forEach { a ->
            val f = feeSplit(a, coverFee = true)
            assertEquals("$a", 0, f.feeMinor % 100)
            assertEquals("$a", true, f.feeMinor * 2 <= f.totalMinor)
        }
        assertEquals(0, coverFeeMinor(100, coverFee = true)) // KSh 100 has no fee
    }

    @Test
    fun `a long amount steps down in size instead of breaking mid-number`() {
        // On the type scale (§8.2 #21): the screen-title size up to "100,000"…
        assertEquals(28, TypeScale.amount("1,000"))
        assertEquals(28, TypeScale.amount("10,000"))
        assertEquals(28, TypeScale.amount("25.50"))
        assertEquals(28, TypeScale.amount("100,000"))
        assertEquals(28, TypeScale.amount("1,234.56"))
        // …then a step down, never mid-number.
        assertEquals(26, TypeScale.amount("2,000,000"))
        assertEquals(26, TypeScale.amount("10,000.00"))
        assertEquals(22, TypeScale.amount("100,000,000"))
    }
}
