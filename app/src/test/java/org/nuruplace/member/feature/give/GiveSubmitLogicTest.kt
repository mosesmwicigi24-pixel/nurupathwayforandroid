// The Android money fix (docs/PARTNERS_PROGRAMME.md §0): a Weekly/Monthly
// choice creates a schedule, a one-time gift creates an intent, recurring is
// mobile-money only, and the cover-fee choice rides inside amount_minor the
// way iOS sends it. Pinned here because the bug this fixes — every frequency
// silently creating a one-off intent — was invisible on the screen.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.nuruplace.member.data.net.FundRef
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

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

    // ── Double-pay guard (owner, 2026-09-26) ──

    private val pledgePay = GivePreset(
        amountMinor = 100_000, pledgeId = "p1", title = "General partnership",
        paysTo = FundRef("discipleship", "Discipleship"), terms = "KSh 1,000 monthly · due on the 25th",
    )
    private val needGive = GivePreset(fundId = NEED_GIFT_FUND, amountMinor = 250_000, needId = "n1", title = "Sound desk")

    @Test
    fun `a bound gift that succeeded or is on its way spends its binding`() {
        // New intents answer "processing"; an idempotent replay can answer any status.
        listOf("processing", "pending", "succeeded", "settled", "completed", " PROCESSING ").forEach { status ->
            assertTrue("pledge · $status", giftSpendsBinding(pledgePay, status))
            assertTrue("need · $status", giftSpendsBinding(needGive, status))
        }
    }

    @Test
    fun `only a failed gift keeps the binding, so the member can retry`() {
        listOf("failed", "FAILED", " cancelled ", "canceled").forEach { status ->
            assertFalse("pledge · $status", giftSpendsBinding(pledgePay, status))
            assertFalse("need · $status", giftSpendsBinding(needGive, status))
        }
    }

    @Test
    fun `an unknown or missing status spends the binding — a second payment is the worse mistake`() {
        assertTrue(giftSpendsBinding(pledgePay, "requires_action"))
        assertTrue(giftSpendsBinding(pledgePay, ""))
        assertTrue(giftSpendsBinding(pledgePay, null))
    }

    @Test
    fun `an unbound gift has no binding to spend`() {
        assertFalse(giftSpendsBinding(null, "succeeded"))
        assertFalse(giftSpendsBinding(GivePreset(fundId = "tithe", amountMinor = 100_000), "processing"))
    }

    @Test
    fun `the form seeds from a bound preset on one-time, and resets to the ordinary form`() {
        assertEquals(GiveFormSeed("tithe", 1_000, FREQ_ONCE), giveFormSeed(pledgePay)) // a General partnership pledge has no fund of its own
        assertEquals(GiveFormSeed("discipleship", 1_000, FREQ_ONCE), giveFormSeed(pledgePay.copy(fundId = "discipleship")))
        assertEquals(GiveFormSeed(NEED_GIFT_FUND, 2_500, FREQ_ONCE), giveFormSeed(needGive))
        // What a spent binding resets to — and what an unbound screen starts on.
        val ordinary = GiveFormSeed(DEFAULT_GIVE_FUND, DEFAULT_GIVE_AMOUNT_MAJOR, FREQ_MONTHLY)
        assertEquals(ordinary, giveFormSeed(null))
        assertEquals(GiveFormSeed("tithe", 1_000, FREQ_MONTHLY), ordinary)
        // A plain preset (no pledge, no need) keeps the ordinary frequency.
        assertEquals(GiveFormSeed("offering", 500, FREQ_MONTHLY), giveFormSeed(GivePreset(fundId = "offering", amountMinor = 50_000)))
    }

    // ── Idempotent retry: keep vs rotate the key (owner, 2026-09-26) ──

    private val gift = GiveRequestShape(
        amountMajor = 1_000, fundId = "tithe", methodId = "mpesa", pledgeId = "p1", needId = null,
        freq = FREQ_ONCE, giftName = "", coverFee = false, phone = "+254700000000",
    )
    private var minted = 0
    private val fresh = { "key-${++minted}" }
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())))

    @Test
    fun `the first tap mints a key for exactly that gift`() {
        val a = giveKeyFor(null, gift, fresh)
        assertEquals("key-1", a.key)
        assertEquals(gift, a.shape)
    }

    @Test
    fun `no server answer keeps the key, and the identical retry replays it`() {
        val held = giveKeyFor(null, gift, fresh)
        listOf(
            SocketTimeoutException("timeout"), SocketTimeoutException("connect timed out"),
            UnknownHostException("pathway.nuruplace.org"), ConnectException("refused"),
            SSLException("reset"), IOException("unexpected end of stream"),
        ).forEach { assertTrue("${it::class.simpleName} keeps the key", keepGiveKeyAfter(it)) }
        // The retry sends the SAME key: the server answers with the transaction
        // it may already have made — never a second STK push.
        assertEquals("key-1", giveKeyFor(held, gift, fresh).key)
        assertEquals(1, minted)
    }

    @Test
    fun `any server answer releases the key — success, 4xx or 5xx — so a genuine failure never locks the member out`() {
        assertFalse(keepGiveKeyAfter(null)) // success (the ceremony takes over)
        listOf(400, 401, 402, 409, 422, 429, 500, 502, 503).forEach { assertFalse("HTTP $it", keepGiveKeyAfter(http(it))) }
        assertFalse(keepGiveKeyAfter(SerializationException("bad body"))) // an answer came, just unreadable
        assertFalse(keepGiveKeyAfter(IllegalStateException("anything else")))
        // Released → nothing held → the next tap mints a new key.
        assertEquals("key-1", giveKeyFor(null, gift, fresh).key)
        assertEquals("key-2", giveKeyFor(null, gift, fresh).key)
    }

    @Test
    fun `a changed gift never replays a held key`() {
        val held = giveKeyFor(null, gift, fresh) // key-1, held after a lost reply
        val changes = listOf(
            "amount" to gift.copy(amountMajor = 2_000),
            "fund" to gift.copy(fundId = "offering"),
            "method" to gift.copy(methodId = "airtel"),
            "binding (another pledge)" to gift.copy(pledgeId = "p2"),
            "binding (dropped)" to gift.copy(pledgeId = null),
            "binding (a need)" to gift.copy(pledgeId = null, needId = "n1"),
            "frequency" to gift.copy(freq = FREQ_MONTHLY),
            "gift name" to gift.copy(giftName = "Thanksgiving"),
            "cover-fee" to gift.copy(coverFee = true),
            "phone" to gift.copy(phone = "+254711111111"),
        )
        changes.forEach { (what, changed) ->
            val next = giveKeyFor(held, changed, fresh)
            assertTrue("$what → a fresh key", next.key != held.key)
            assertEquals(changed, next.shape)
        }
    }

    @Test
    fun `a gift changed and changed back is the same request, and replays its key`() {
        val held = giveKeyFor(null, gift, fresh)
        // 1,000 → 2,000 → 1,000: the tap sends exactly the lost request again.
        assertEquals(held.key, giveKeyFor(held, gift.copy(amountMajor = 2_000).copy(amountMajor = 1_000), fresh).key)
    }
}
