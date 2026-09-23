// The Android money fix (docs/PARTNERS_PROGRAMME.md §0): a Weekly/Monthly
// choice creates a schedule, a one-time gift creates an intent, recurring is
// mobile-money only, and the cover-fee choice rides inside amount_minor the
// way iOS sends it. Pinned here because the bug this fixes — every frequency
// silently creating a one-off intent — was invisible on the screen.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GiveSubmitLogicTest {
    private fun plan(freq: Int, provider: String?, coverFee: Boolean = false, amount: Int = 1000, pledgeId: String? = null) =
        planGiveSubmission(
            freq = freq, provider = provider, fundId = "tithe", amountMajor = amount, coverFee = coverFee,
            phone = "+254700000000", accountName = " Tithe ", idempotencyKey = "idem-1", pledgeId = pledgeId,
        )

    @Test
    fun `one-time gift creates an intent with the exact body`() {
        val s = plan(FREQ_ONCE, "mpesa")
        assertTrue(s is GiveSubmission.Intent)
        val b = (s as GiveSubmission.Intent).body
        assertEquals("tithe", b.fund)
        assertEquals(100_000, b.amountMinor)
        assertEquals("KES", b.currency)
        assertEquals("mpesa", b.method)
        assertEquals("+254700000000", b.phoneNumber)
        assertEquals("Tithe", b.accountName)
        assertEquals("idem-1", b.idempotencyKey)
        assertNull(b.pledgeId)
    }

    @Test
    fun `monthly with mpesa creates a monthly schedule`() {
        val s = plan(FREQ_MONTHLY, "mpesa")
        assertTrue(s is GiveSubmission.Schedule)
        val b = (s as GiveSubmission.Schedule).body
        assertEquals("monthly", b.frequency)
        assertEquals("mpesa", b.method)
        assertEquals(100_000, b.amountMinor)
        assertEquals("tithe", b.fund)
        assertEquals("idem-1", b.idempotencyKey)
    }

    @Test
    fun `weekly with airtel creates a weekly schedule`() {
        val s = plan(FREQ_WEEKLY, "airtel")
        assertTrue(s is GiveSubmission.Schedule)
        assertEquals("weekly", (s as GiveSubmission.Schedule).body.frequency)
    }

    @Test
    fun `monthly with card is blocked, never an intent`() {
        val s = plan(FREQ_MONTHLY, "card")
        assertTrue(s is GiveSubmission.Blocked)
        assertEquals(RECURRING_CARD_BLOCKED_MESSAGE, (s as GiveSubmission.Blocked).message)
    }

    @Test
    fun `monthly with paypal is blocked with the mobile-money message`() {
        val s = plan(FREQ_MONTHLY, "paypal")
        assertTrue(s is GiveSubmission.Blocked)
        assertEquals(RECURRING_BLOCKED_MESSAGE, (s as GiveSubmission.Blocked).message)
    }

    @Test
    fun `a SOON method is blocked for every frequency`() {
        assertTrue(plan(FREQ_ONCE, null) is GiveSubmission.Blocked)
        assertTrue(plan(FREQ_MONTHLY, null) is GiveSubmission.Blocked)
    }

    @Test
    fun `cover fee is added to the amount exactly as iOS does`() {
        // iOS feeFor(1000) == 13 → total 1013 → amount_minor 101300
        assertEquals(1013, chargedAmountMajor(1000, coverFee = true))
        assertEquals(1000, chargedAmountMajor(1000, coverFee = false))
        val intent = plan(FREQ_ONCE, "mpesa", coverFee = true) as GiveSubmission.Intent
        assertEquals(101_300, intent.body.amountMinor)
        val sched = plan(FREQ_MONTHLY, "mpesa", coverFee = true) as GiveSubmission.Schedule
        assertEquals(101_300, sched.body.amountMinor)
    }

    @Test
    fun `fee table matches iOS feeFor`() {
        assertEquals(0, giveFee(100))
        assertEquals(7, giveFee(500))
        assertEquals(13, giveFee(1000))
        assertEquals(23, giveFee(1500))
        assertEquals(33, giveFee(2500))
        assertEquals(53, giveFee(3500))
        assertEquals(57, giveFee(5000))
        assertEquals(120, giveFee(10_000))
    }

    @Test
    fun `pledge id rides the intent and binds the schedule`() {
        val intent = plan(FREQ_ONCE, "mpesa", pledgeId = "pl-1") as GiveSubmission.Intent
        assertEquals("pl-1", intent.body.pledgeId)
        val sched = plan(FREQ_MONTHLY, "airtel", pledgeId = "pl-1") as GiveSubmission.Schedule
        assertEquals("pl-1", sched.body.pledgeId)
    }

    @Test
    fun `zero amount is blocked before any method check`() {
        assertTrue(plan(FREQ_ONCE, "mpesa", amount = 0) is GiveSubmission.Blocked)
    }

    // --- Departments (docs/PARTNERS_PROGRAMME.md §4): a need is a giving target ---

    @Test
    fun `need id rides a one-time intent`() {
        val intent = planGiveSubmission(
            freq = FREQ_ONCE, provider = "mpesa", fundId = "gift", amountMajor = 5000, coverFee = false,
            phone = "", accountName = "", idempotencyKey = "idem-2", needId = "need-1",
        ) as GiveSubmission.Intent
        assertEquals("need-1", intent.body.needId)
        assertNull(intent.body.pledgeId)
        assertEquals("gift", intent.body.fund)
    }

    @Test
    fun `a recurring choice with a need is blocked rather than dropping the need`() {
        val s = planGiveSubmission(
            freq = FREQ_MONTHLY, provider = "mpesa", fundId = "gift", amountMajor = 5000, coverFee = false,
            phone = "", accountName = "", idempotencyKey = "idem-3", needId = "need-1",
        )
        assertTrue(s is GiveSubmission.Blocked)
        assertEquals(NEED_RECURRING_BLOCKED_MESSAGE, (s as GiveSubmission.Blocked).message)
    }

    @Test
    fun `a need preset is targeted and lands the intent on the need`() {
        val p = GivePreset(fundId = NEED_GIFT_FUND, amountMinor = 3_750_000, needId = "need-1", title = "Sound desk")
        assertTrue(p.isTargeted)
        assertNull(p.pledgeId)
        assertEquals("need-1", p.needId)
        assertEquals("gift", p.fundId)
        val intent = planGiveSubmission(
            freq = FREQ_ONCE, provider = "card", fundId = p.fundId!!, amountMajor = p.amountMinor!! / 100, coverFee = false,
            phone = "", accountName = "", idempotencyKey = "idem-4", pledgeId = p.pledgeId, needId = p.needId,
        ) as GiveSubmission.Intent
        assertEquals("need-1", intent.body.needId)
        assertEquals(3_750_000, intent.body.amountMinor)
        // A plain preset (no pledge, no need) is not targeted.
        assertTrue(!GivePreset(fundId = "tithe").isTargeted)
    }
}
