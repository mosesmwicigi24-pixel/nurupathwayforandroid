// The ceremony reads the RESULT (pledge names, contract 2026-09-25): a gift
// carrying pledge_id lands in the pledge's fund whatever the client sent, so
// the copy names the server's pledge, else the server's fund, and only falls
// back to the chip label when the result carries neither.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.FundRef
import org.nuruplace.member.data.net.GivingIntentResult
import org.nuruplace.member.data.net.IntentPledge

class GiveCeremonyCopyTest {
    private val withPledge = GivingIntentResult(
        transactionId = "t", status = "pending", provider = "mpesa",
        fund = FundRef("gift", "Gift"), pledge = IntentPledge("p1", "School fees"),
    )
    private val fundOnly = GivingIntentResult(transactionId = "t", status = "pending", provider = "mpesa", fund = FundRef("tithe", "Tithe"))
    private val bare = GivingIntentResult(transactionId = "t", status = "pending", provider = "mpesa")

    @Test
    fun `ceremony line names the pledge when the result carries one`() {
        assertEquals(
            "Enter your PIN to complete KSh 1,000 toward your School fees pledge.",
            giveCeremonyLine(withPledge, 100_000, chipFundLabel = "Tithe"),
        )
    }

    @Test
    fun `ceremony line names the server's fund when there is no pledge`() {
        // The chip said Offering; the server says Tithe — the server wins.
        assertEquals("Enter your PIN to complete KSh 1,000 to Tithe.", giveCeremonyLine(fundOnly, 100_000, chipFundLabel = "Offering"))
    }

    @Test
    fun `ceremony line falls back to the chip label only when the result carries no fund`() {
        assertEquals("Enter your PIN to complete KSh 1,013 to Offering.", giveCeremonyLine(bare, 101_300, chipFundLabel = "Offering"))
        assertEquals("Enter your PIN to complete KSh 1,013.", giveCeremonyLine(bare, 101_300, chipFundLabel = null))
    }

    @Test
    fun `ceremony line by provider`() {
        assertEquals("Enter your PIN to complete KSh 500 to Tithe.", giveCeremonyLine(fundOnly.copy(provider = "airtel"), 50_000, null))
        assertEquals(
            "Continue on PayPal to complete US$ 25.00 to Tithe — we'll confirm it when you come back.",
            giveCeremonyLine(fundOnly.copy(provider = "paypal", approveUrl = "https://paypal.example/approve"), 2_500, null, "USD"),
        )
        assertEquals("KSh 500 to Tithe is being processed.", giveCeremonyLine(fundOnly.copy(provider = "card"), 50_000, null))
    }

    @Test
    fun `destination phrase order is pledge, fund, chip, nothing`() {
        assertEquals("toward your School fees pledge", giveDestinationPhrase(withPledge, "Tithe"))
        assertEquals("to Tithe", giveDestinationPhrase(fundOnly, "Offering"))
        assertEquals("to Offering", giveDestinationPhrase(bare, "Offering"))
        assertNull(giveDestinationPhrase(bare, null))
        assertNull(giveDestinationPhrase(bare, "  "))
    }

    @Test
    fun `a pledge whose name already ends in "pledge" is never doubled, in any case`() {
        // "Building pledge" read "Building pledge pledge" (parity list, 2026-09-28).
        listOf("Building pledge", "Building Pledge", "BUILDING PLEDGE", " Building pledge ").forEach { name ->
            val r = withPledge.copy(pledge = IntentPledge("p1", name))
            assertEquals(name, "toward your ${name.trim()}", giveDestinationPhrase(r, "Tithe"))
            assertEquals(name, "Enter your PIN to complete KSh 1,000 toward your ${name.trim()}.", giveCeremonyLine(r, 100_000, "Tithe"))
            assertEquals(name, "${name.trim()} · Gift", giveDestinationLabel(r, "Tithe"))
        }
        // A name that only CONTAINS the word keeps its "pledge".
        val mid = withPledge.copy(pledge = IntentPledge("p1", "Pledge for the roof"))
        assertEquals("toward your Pledge for the roof pledge", giveDestinationPhrase(mid, null))
    }

    @Test
    fun `destination label prefers the pledge with its routed fund, then the fund, then the chip, and carries the gift name`() {
        assertEquals("School fees pledge · Gift", giveDestinationLabel(withPledge, "Tithe"))
        assertEquals("School fees pledge", giveDestinationLabel(withPledge.copy(fund = null), "Tithe"))
        assertEquals("Tithe", giveDestinationLabel(fundOnly, "Offering"))
        assertEquals("Offering", giveDestinationLabel(bare, "Offering"))
        assertNull(giveDestinationLabel(bare, null))
        assertEquals("Tithe — “For Mom”", giveDestinationLabel(fundOnly, null, giftName = " For Mom "))
        assertEquals("School fees pledge · Gift — “Term 3”", giveDestinationLabel(withPledge, null, giftName = "Term 3"))
    }

    // ── The ceremony watches the real transaction (iOS parity, 2026-09-26) ──

    @Test
    fun `the transaction status reads as processing, succeeded or failed`() {
        listOf("processing", "requires_action", "pending", "", null).forEach { assertEquals("$it", GiftOutcome.Processing, giftOutcome(it)) }
        listOf("succeeded", "settled", "completed", " SUCCEEDED ").forEach { assertEquals(it, GiftOutcome.Succeeded, giftOutcome(it)) }
        listOf("failed", "cancelled", "canceled", "Failed").forEach { assertEquals(it, GiftOutcome.Failed, giftOutcome(it)) }
    }

    @Test
    fun `the watch reads every 3 s for a minute, then every 10 s up to five minutes`() {
        // iOS StkWatch (EXPERIENCE.md §7.2 #5): a prompt answered late still lands.
        assertEquals(3_000L, GiftWatch.nextDelayMs(0))
        assertEquals(3_000L, GiftWatch.nextDelayMs(59_999))
        assertEquals(10_000L, GiftWatch.nextDelayMs(60_000))
        assertEquals(10_000L, GiftWatch.nextDelayMs(299_999))
        assertNull(GiftWatch.nextDelayMs(300_000))
        assertFalse(GiftWatch.isLate(59_999))
        assertTrue(GiftWatch.isLate(60_000))
    }

    @Test
    fun `waiting for M-Pesa is never a celebration — Check your phone until the server confirms`() {
        val mpesa = GivingIntentResult(transactionId = "t1", status = "processing", provider = "mpesa", fund = FundRef("tithe", "Tithe"))
        assertTrue(waitsOnPhone(mpesa, "processing", "+254700000000"))
        assertTrue(waitsOnPhone(mpesa.copy(provider = "airtel"), "requires_action", null))
        // Only the server's confirmed outcome ends the wait.
        assertFalse(waitsOnPhone(mpesa, "succeeded", "+254700000000"))
        assertFalse(waitsOnPhone(mpesa, "failed", "+254700000000"))
        // A provider-less answer that sent a prompt waits on the phone too.
        assertTrue(waitsOnPhone(mpesa.copy(provider = null), "processing", "+254700000000"))
        // PayPal (its approval page) and card keep their own stage.
        assertFalse(waitsOnPhone(mpesa.copy(provider = "paypal", approveUrl = "https://paypal.test/a"), "processing", null))
        assertFalse(waitsOnPhone(mpesa.copy(provider = "card"), "processing", null))
        assertEquals("Check your phone", STK_TITLE)
        assertEquals("Waiting up to 60s…", STK_WAITING_LINE)
        assertEquals("Still processing — it will show in Recent giving once it clears.", GIFT_LATE_LINE)
        // The PIN line names the money, where it goes and the gift's own name.
        val line = stkPinLine(mpesa, 100_000, "Tithe", giftName = "For Mom")
        assertEquals("KSh 1,000", line.amount)
        assertEquals("Enter your PIN to complete KSh 1,000 to Tithe — “For Mom”.", line.text)
        assertEquals("Enter your PIN to complete KSh 1,000 to Tithe.", stkPinLine(mpesa, 100_000, "Tithe").text)
    }

    @Test
    fun `the ceremony says where the gift stands`() {
        assertEquals(
            "Enter your PIN to complete KSh 1,000 toward your School fees pledge.",
            giveCeremonyStatusLine(withPledge, 100_000, "Tithe", GiftOutcome.Processing, late = false),
        )
        assertEquals(
            "Still processing — it will show in Recent giving once it clears.",
            giveCeremonyStatusLine(withPledge, 100_000, "Tithe", GiftOutcome.Processing, late = true),
        )
        // Confirmed: iOS's line (§8.2 #17) — never "Gift confirmed — receipt on its way. 🎉".
        assertEquals(
            "KSh 1,000 · toward your School fees pledge · Ref QFG7H2K9LM",
            giveCeremonyStatusLine(withPledge, 100_000, "Tithe", GiftOutcome.Succeeded, false, ref = "QFG7H2K9LM"),
        )
        assertEquals("The payment didn't complete — no charge was made.", giveCeremonyStatusLine(withPledge, 100_000, "Tithe", GiftOutcome.Failed, false))
        assertEquals("Thank you for your generosity", giveCeremonyTitle(GiftOutcome.Processing))
        assertEquals("Thank you for your generosity", giveCeremonyTitle(GiftOutcome.Succeeded))
        assertEquals("Your gift didn't go through", giveCeremonyTitle(GiftOutcome.Failed))
    }

    @Test
    fun `a replayed answer reads exactly like a fresh one`() {
        // A replay carries no provider / approve_url — only the transaction, its
        // status and where it was booked — and `reused` is never consulted.
        val fresh = GivingIntentResult(transactionId = "t", status = "processing", fund = FundRef("gift", "Gift"), pledge = IntentPledge("p1", "School fees"))
        val replay = fresh.copy(reused = true)
        assertEquals(giftOutcome(fresh.status), giftOutcome(replay.status))
        assertEquals(giveCeremonyLine(fresh, 100_000, null), giveCeremonyLine(replay, 100_000, null))
        assertEquals("KSh 1,000 toward your School fees pledge is being processed.", giveCeremonyLine(replay, 100_000, null))
        // A replay of a gift that already went through, or one that failed, is final at once.
        assertEquals(GiftOutcome.Succeeded, giftOutcome(replay.copy(status = "succeeded").status))
        assertEquals(GiftOutcome.Failed, giftOutcome(replay.copy(status = "failed").status))
    }

    // ── the confirmed gift (EXPERIENCE.md §8.2 #17) ──

    @Test
    fun `a confirmed gift says the amount, the fund and its reference — iOS's line`() {
        assertEquals("KSh 1,000 · Tithe · Ref QFG7H2K9LM", giveSuccessLine(fundOnly, 100_000, "Offering", ref = "QFG7H2K9LM"))
        // The chip only when the server named no fund; no reference yet → no "Ref".
        assertEquals("KSh 1,000 · Offering", giveSuccessLine(bare, 100_000, "Offering"))
        assertEquals("KSh 1,000", giveSuccessLine(bare, 100_000, null))
        // A pledge is "toward your … pledge", never doubled.
        assertEquals("KSh 5,000 · toward your School fees pledge · Ref AB12", giveSuccessLine(withPledge, 500_000, "Tithe", ref = "AB12"))
        val named = withPledge.copy(pledge = IntentPledge("p2", "Building pledge"))
        assertEquals("KSh 5,000 · toward your Building pledge", giveSuccessLine(named, 500_000, null))
        assertEquals("KSh 5,000 · toward your pledge", giveSuccessLine(withPledge.copy(pledge = IntentPledge("p3", " ")), 500_000, null))
        // The member's own gift name rides with where it went.
        assertEquals("KSh 1,000 · Tithe — “For Mom” · Ref X1", giveSuccessLine(fundOnly, 100_000, null, giftName = " For Mom ", ref = "X1"))
        // A dollar gift in its own money.
        assertEquals("US$ 25.00 · Tithe", giveSuccessLine(fundOnly, 2_500, null, currency = "USD"))
    }

    @Test
    fun `the reference is the receipt code, else the transaction's first eight — never ws_CO_`() {
        assertEquals("QFG7H2K9LM", giveSuccessRef(" QFG7H2K9LM ", "8f2c1a9e-77aa-4c3b-9d10-1b2c3d4e5f60"))
        assertEquals("8F2C1A9E", giveSuccessRef(null, "8f2c1a9e-77aa-4c3b-9d10-1b2c3d4e5f60"))
        assertEquals("8F2C1A9E", giveSuccessRef("", "8f2c1a9e-77aa-4c3b-9d10-1b2c3d4e5f60"))
        assertNull(giveSuccessRef(null, ""))
    }
}
