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
            "Continue on PayPal to complete KSh 500 to Tithe, then confirm below.",
            giveCeremonyLine(fundOnly.copy(provider = "paypal", approveUrl = "https://paypal.example/approve"), 50_000, null),
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
    fun `the watch reads every 3 s while processing, at most 20 times`() {
        assertEquals(3_000L, CEREMONY_WATCH_INTERVAL_MS)
        assertTrue(keepWatchingGift(GiftOutcome.Processing, 0))
        assertTrue(keepWatchingGift(GiftOutcome.Processing, CEREMONY_WATCH_MAX - 1))
        assertFalse(keepWatchingGift(GiftOutcome.Processing, CEREMONY_WATCH_MAX))
        assertFalse(keepWatchingGift(GiftOutcome.Succeeded, 0))
        assertFalse(keepWatchingGift(GiftOutcome.Failed, 0))
    }

    @Test
    fun `the ceremony says where the gift stands`() {
        assertEquals(
            "Enter your PIN to complete KSh 1,000 toward your School fees pledge.",
            giveCeremonyStatusLine(withPledge, 100_000, "Tithe", GiftOutcome.Processing, watchLapsed = false),
        )
        assertEquals(
            "Still processing — your gift will show once it clears.",
            giveCeremonyStatusLine(withPledge, 100_000, "Tithe", GiftOutcome.Processing, watchLapsed = true),
        )
        assertEquals("Gift confirmed — receipt on its way. 🎉", giveCeremonyStatusLine(withPledge, 100_000, "Tithe", GiftOutcome.Succeeded, false))
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
}
