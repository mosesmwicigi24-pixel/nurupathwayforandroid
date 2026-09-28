// The Give segment from the member's own history (GiveHistoryLogic.kt) — iOS
// GivingView's "Repeat last gift" and RECENT GIVING, one rule for both
// (nuru-member-ios c89f483): only a gift whose money went through.
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.GivingRecord

class GiveHistoryLogicTest {
    private fun gift(
        id: String,
        status: String = "succeeded",
        amount: Int = 100_000,
        fund: String = "tithe",
        method: String? = "mpesa",
        currency: String = "KES",
        pledgeId: String? = null,
        needId: String? = null,
        fee: Int? = null,
        name: String? = null,
        at: String = "2026-09-20T07:00:00Z",
    ) = GivingRecord(
        transactionId = id, amountMinor = amount, currency = currency, status = status, fund = fund, method = method,
        pledgeId = pledgeId, needId = needId, feeCoverMinor = fee, accountName = name, createdAt = at,
    )

    // ── which gift "Repeat last gift" offers ──

    @Test
    fun `repeat offers the newest ordinary gift that went through`() {
        // Newest first, as GET /giving/history sends it.
        val history = listOf(gift("t3"), gift("t2", fund = "offering"), gift("t1"))
        assertEquals("t3", lastRepeatableGift(history)?.transactionId)
        // "settled" and "completed" went through too.
        assertEquals("t4", lastRepeatableGift(listOf(gift("t4", status = "settled")))?.transactionId)
        assertEquals("t5", lastRepeatableGift(listOf(gift("t5", status = " Completed ")))?.transactionId)
    }

    @Test
    fun `a failed, waiting or cancelled gift is never offered again`() {
        // Ada's case (seen 2026-09-28): her only gift failed — iOS offered it
        // beside "No gifts yet". No card.
        assertNull(lastRepeatableGift(listOf(gift("t1", status = "failed"))))
        listOf("failed", "processing", "pending", "requires_action", "cancelled", "refunded", "").forEach { s ->
            assertFalse(s, giftSettled(s))
            assertEquals(s, "t0", lastRepeatableGift(listOf(gift("t9", status = s), gift("t0")))?.transactionId)
        }
        assertFalse(giftSettled(null))
        assertTrue(giftSettled("succeeded"))
        assertNull(lastRepeatableGift(emptyList()))
    }

    @Test
    fun `a pledge instalment or a gift to a need is never repeated`() {
        val history = listOf(gift("p1", pledgeId = "pl-1"), gift("n1", needId = "need-1"), gift("t1"))
        assertEquals("t1", lastRepeatableGift(history)?.transactionId)
        assertNull(lastRepeatableGift(listOf(gift("p1", pledgeId = "pl-1"), gift("n1", needId = "need-1"))))
        // A blank id is no pledge at all.
        assertEquals("b1", lastRepeatableGift(listOf(gift("b1", pledgeId = "  ", needId = "")))?.transactionId)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the history row decodes need_id, absent on an older server`() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; namingStrategy = JsonNamingStrategy.SnakeCase }
        val row = json.decodeFromString<GivingRecord>(
            """{"transaction_id":"t1","amount_minor":100000,"currency":"KES","status":"succeeded","fund":"gift","pledge_id":null,"pledge_title":null,"need_id":"need-1"}""",
        )
        assertEquals("need-1", row.needId)
        assertNull(lastRepeatableGift(listOf(row)))
        val older = json.decodeFromString<GivingRecord>("""{"transaction_id":"t2","amount_minor":100000,"status":"succeeded","fund":"tithe"}""")
        assertNull(older.needId)
        assertEquals("t2", lastRepeatableGift(listOf(older))?.transactionId)
    }

    @Test
    fun `the card says the gift as iOS does`() {
        assertEquals("KSh 1,000 · Tithe · via M-Pesa", repeatGiftLine(gift("t1")))
        assertEquals("US$ 25.50 · Offering · via PayPal", repeatGiftLine(gift("t2", amount = 2_550, currency = "USD", fund = "offering", method = "paypal")))
        // No rail on the row: M-Pesa, as iOS reads it.
        assertEquals("KSh 1,000 · Tithe · via M-Pesa", repeatGiftLine(gift("t3", method = null)))
    }

    // ── RECENT GIVING ──

    @Test
    fun `recent giving lists the three newest gifts that went through`() {
        val history = listOf(
            gift("f1", status = "failed"), gift("t5"), gift("w1", status = "processing"), gift("t4", pledgeId = "pl"),
            gift("t3", status = "settled"), gift("t2"), gift("t1"),
        )
        // Pledge payments are gifts too here (iOS); failed and waiting ones are not.
        assertEquals(listOf("t5", "t4", "t3"), recentGifts(history).map { it.transactionId })
        assertEquals(emptyList<GivingRecord>(), recentGifts(listOf(gift("f1", status = "failed"))))
        // Its line: the Nairobi day and the rail — 20 Sep 23:30 UTC is 21 Sep in Nairobi.
        assertEquals("20 Sep · M-Pesa", recentGiftMeta(gift("t1", at = "2026-09-20T07:00:00Z")))
        assertEquals("21 Sep · PayPal", recentGiftMeta(gift("t2", at = "2026-09-20T23:30:00Z", method = "paypal")))
    }

    // ── what "Give again" puts on the form ──

    private val mpesa = FALLBACK_MPESA
    private val paypal = GiveMethodOption(key = "paypal", label = "PayPal", enabled = true, currency = "USD", minMinor = 100)
    private val paypalOff = paypal.copy(enabled = false)

    @Test
    fun `a shilling gift comes back without the fee it covered, the fee switch on`() {
        val plan = repeatPlan(gift("t1", amount = 101_300, fee = 1_300, fund = "offering", name = "Harvest"), listOf(mpesa, paypal), current = mpesa)
        assertEquals(RepeatPlan(fundId = "offering", methodKey = "mpesa", usdCents = null, amountMajor = 1_000, coverFee = true, accountName = "Harvest"), plan)
        // No fee covered: the whole amount, the switch off.
        val plain = repeatPlan(gift("t2", amount = 250_000), listOf(mpesa), current = mpesa)
        assertEquals(2_500, plain.amountMajor)
        assertEquals(false, plain.coverFee)
        assertEquals("", plain.accountName)
    }

    @Test
    fun `a dollar gift comes back in dollars, only onto a dollar rail`() {
        val usd = gift("t1", amount = 2_550, currency = "USD", method = "paypal")
        val onPayPal = repeatPlan(usd, listOf(mpesa, paypal), current = mpesa)
        assertEquals("paypal", onPayPal.methodKey)
        assertEquals(2_550, onPayPal.usdCents)
        assertNull(onPayPal.amountMajor) // never read as shillings
        // PayPal switched off: the form stays on M-Pesa and its shillings untouched.
        val off = repeatPlan(usd, listOf(mpesa, paypalOff), current = mpesa)
        assertNull(off.methodKey)
        assertNull(off.usdCents)
        assertNull(off.amountMajor)
        assertNull(off.coverFee)
    }

    @Test
    fun `a rail that cannot take money now, or a fund the form lacks, is left as it is`() {
        val airtel = GiveMethodOption(key = "airtel", label = "Airtel Money", enabled = false, currency = "KES")
        val plan = repeatPlan(gift("t1", method = "airtel", fund = "building"), listOf(mpesa, airtel), current = mpesa)
        assertNull(plan.methodKey) // stays on M-Pesa
        assertNull(plan.fundId) // not one of the form's funds
        assertEquals(1_000, plan.amountMajor) // in the rail the form is on
        // A card is never selectable in this app.
        val card = GiveMethodOption(key = "card", label = "Card", enabled = true, currency = "KES")
        assertNull(repeatPlan(gift("t2", method = "card"), listOf(mpesa, card), current = mpesa).methodKey)
    }
}
