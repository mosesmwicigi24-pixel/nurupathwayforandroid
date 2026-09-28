// The Android money fix (docs/PARTNERS_PROGRAMME.md §0): a Weekly/Monthly
// choice creates a schedule, a one-time gift creates an intent, and the
// cover-fee choice rides inside amount_minor the way iOS sends it. Pinned here
// because the bug this fixes — every frequency silently creating a one-off
// intent — was invisible on the screen.
//
// Giving Cycle 1: the form starts on One-time; whether a method can take a
// gift — or a recurring one — is the server's word (GET /giving/methods), so a
// card or a switched-off rail is never sent; a phone rail needs a Kenyan
// mobile number; and a refusal either follows the prompt already waiting
// (409 GIFT_IN_PROGRESS) or says the server's own message.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.FundRef
import org.nuruplace.member.data.net.GivingDetail
import org.nuruplace.member.data.net.IntentPledge
import org.nuruplace.member.data.net.ReceiptNeed
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class GiveSubmitLogicTest {
    // The rails as a live server describes them (FinancialService.RAILS).
    private val mpesa = FALLBACK_MPESA
    private val airtelLive = GiveMethodOption(
        "airtel", "Airtel Money", enabled = true, currency = "KES",
        minMinor = 100, maxMinor = 15_000_000, recurring = true, needsPhone = true,
    )
    private val airtelSoon = airtelLive.copy(enabled = false, unavailableReason = "coming_soon", recurring = false)
    private val paypalLive = GiveMethodOption(
        "paypal", "PayPal", enabled = true, currency = "USD",
        minMinor = 100, maxMinor = 1_000_000, wholeUnits = false,
    )
    private val cardLive = GiveMethodOption("card", "Card", enabled = true, currency = null, minMinor = 100, maxMinor = 100_000_000, wholeUnits = false)

    private fun plan(
        freq: Int,
        method: GiveMethodOption?,
        coverFee: Boolean = false,
        amount: Int = 1000,
        pledgeId: String? = null,
        phone: String? = "+254700000000",
        phoneOnFile: String? = null,
    ) = planGiveSubmission(
        freq = freq, method = method, fundId = "tithe", amountMajor = amount, coverFee = coverFee,
        phone = phone, accountName = " Tithe ", idempotencyKey = "idem-1", pledgeId = pledgeId,
        phoneOnFile = phoneOnFile,
    )

    @Test
    fun `one-time gift creates an intent with the exact body`() {
        val s = plan(FREQ_ONCE, mpesa)
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
        val s = plan(FREQ_MONTHLY, mpesa)
        assertTrue(s is GiveSubmission.Schedule)
        val b = (s as GiveSubmission.Schedule).body
        assertEquals("monthly", b.frequency)
        assertEquals("mpesa", b.method)
        assertEquals(100_000, b.amountMinor)
        assertEquals("tithe", b.fund)
        assertEquals("KES", b.currency)
        assertEquals("idem-1", b.idempotencyKey)
    }

    @Test
    fun `weekly on a live recurring rail creates a weekly schedule`() {
        val s = plan(FREQ_WEEKLY, airtelLive)
        assertTrue(s is GiveSubmission.Schedule)
        assertEquals("weekly", (s as GiveSubmission.Schedule).body.frequency)
        assertEquals("airtel", s.body.method)
    }

    @Test
    fun `recurring is allowed only where the server says the method can run one`() {
        // The server's flag decides, not the method's name.
        val oneOffOnly = mpesa.copy(recurring = false)
        listOf(FREQ_WEEKLY, FREQ_MONTHLY).forEach { f ->
            val s = plan(f, oneOffOnly)
            assertTrue("freq $f", s is GiveSubmission.Blocked)
            assertEquals(RECURRING_BLOCKED_MESSAGE, (s as GiveSubmission.Blocked).message)
        }
        // …and the same method still takes a one-time gift.
        assertTrue(plan(FREQ_ONCE, oneOffOnly) is GiveSubmission.Intent)
        assertTrue(plan(FREQ_MONTHLY, mpesa) is GiveSubmission.Schedule)
    }

    @Test
    fun `a method this form cannot take is never sent, for any frequency`() {
        // A card (no Stripe SDK here — its intents sat "processing" for ever),
        // a currency the form cannot express, and switched-off rails.
        listOf(
            cardLive, paypalLive.copy(currency = "EUR"), airtelSoon,
            mpesa.copy(enabled = false, unavailableReason = "unavailable"),
            paypalLive.copy(enabled = false, unavailableReason = "coming_soon"),
        ).forEach { m ->
            listOf(FREQ_ONCE, FREQ_WEEKLY, FREQ_MONTHLY).forEach { f ->
                // An amount in both currencies, so only the method can refuse it.
                val s = planGiveSubmission(
                    freq = f, method = m, fundId = "tithe", amountMajor = 1_000, coverFee = false,
                    phone = "+254700000000", accountName = "", idempotencyKey = "k", usdCents = 2_500,
                )
                assertTrue("${m.key} · freq $f", s is GiveSubmission.Blocked)
                assertEquals(METHOD_SOON_MESSAGE, (s as GiveSubmission.Blocked).message)
            }
        }
        // No selectable method at all.
        assertEquals(NO_METHOD_MESSAGE, (plan(FREQ_ONCE, null) as GiveSubmission.Blocked).message)
    }

    @Test
    fun `a phone rail needs a Kenyan mobile number, sent as E164`() {
        assertEquals(phoneNeededMessage("M-Pesa"), (plan(FREQ_ONCE, mpesa, phone = null) as GiveSubmission.Blocked).message)
        assertEquals(phoneNeededMessage("M-Pesa"), (plan(FREQ_ONCE, mpesa, phone = "  ") as GiveSubmission.Blocked).message)
        assertEquals(PHONE_INVALID_MESSAGE, (plan(FREQ_ONCE, mpesa, phone = "12345") as GiveSubmission.Blocked).message)
        assertEquals(PHONE_INVALID_MESSAGE, (plan(FREQ_MONTHLY, mpesa, phone = "020 123 4567") as GiveSubmission.Blocked).message)
        val intent = plan(FREQ_ONCE, mpesa, phone = "0711 222 333") as GiveSubmission.Intent
        assertEquals("+254711222333", intent.body.phoneNumber)
    }

    @Test
    fun `the rail's own limits block before a request, fee included`() {
        assertTrue(plan(FREQ_ONCE, mpesa, amount = 250_000) is GiveSubmission.Intent)
        val over = plan(FREQ_ONCE, mpesa, amount = 250_001) as GiveSubmission.Blocked
        assertEquals("M-Pesa gifts are from KSh 1 to KSh 250,000.", over.message)
        // Covering the fee is part of what is charged.
        assertTrue(plan(FREQ_ONCE, mpesa, amount = 250_000, coverFee = true) is GiveSubmission.Blocked)
        // A row with no ceiling (max 0) leaves the ceiling to the server.
        assertTrue(plan(FREQ_ONCE, mpesa.copy(maxMinor = 0), amount = 1_000_000) is GiveSubmission.Intent)
    }

    @Test
    fun `a schedule pins its number only when it is not the profile's`() {
        val same = plan(FREQ_MONTHLY, mpesa, phone = "0700 000 000", phoneOnFile = "+254700000000") as GiveSubmission.Schedule
        assertNull(same.body.phoneNumber) // follows the profile, as the server stores it
        val other = plan(FREQ_MONTHLY, mpesa, phone = "0711222333", phoneOnFile = "+254700000000") as GiveSubmission.Schedule
        assertEquals("+254711222333", other.body.phoneNumber)
        val noneOnFile = plan(FREQ_MONTHLY, mpesa, phone = "0711222333", phoneOnFile = null) as GiveSubmission.Schedule
        assertEquals("+254711222333", noneOnFile.body.phoneNumber)
    }

    // ── PayPal in dollars (Giving Cycle 2) ──

    private fun planUsd(usdCents: Int, freq: Int = FREQ_ONCE, amountMajor: Int = 1000, coverFee: Boolean = false, pledgeId: String? = null, needId: String? = null) =
        planGiveSubmission(
            freq = freq, method = paypalLive, fundId = "mission", amountMajor = amountMajor, coverFee = coverFee,
            phone = "+254700000000", accountName = "", idempotencyKey = "idem-usd", pledgeId = pledgeId, needId = needId,
            usdCents = usdCents,
        )

    @Test
    fun `PayPal sends US dollars in cents, never the shilling amount`() {
        val b = (planUsd(usdCents = 2_550, amountMajor = 1_000) as GiveSubmission.Intent).body
        assertEquals("USD", b.currency)
        assertEquals(2_550, b.amountMinor) // US$ 25.50 — not KSh 1,000's 100000
        assertEquals("paypal", b.method)
        assertNull(b.phoneNumber) // PayPal prompts no phone
        assertNull(b.coverFeeMinor) // the M-Pesa fee is not PayPal's
        // Covering the fee on the shilling form never leaks into a dollar gift.
        assertEquals(2_550, (planUsd(usdCents = 2_550, coverFee = true) as GiveSubmission.Intent).body.amountMinor)
        // No dollar amount → nothing is sent, whatever the shillings say.
        assertEquals("Enter an amount to give.", (planUsd(usdCents = 0, amountMajor = 5_000) as GiveSubmission.Blocked).message)
    }

    @Test
    fun `the shilling form never sends dollars, and switching back restores it`() {
        val kes = plan(FREQ_ONCE, mpesa, amount = 1_000) as GiveSubmission.Intent
        assertEquals("KES", kes.body.currency)
        assertEquals(100_000, kes.body.amountMinor)
        // The dollar amount the form also holds is ignored on M-Pesa.
        val withDollars = planGiveSubmission(
            freq = FREQ_ONCE, method = mpesa, fundId = "tithe", amountMajor = 1_000, coverFee = false,
            phone = "0711222333", accountName = "", idempotencyKey = "k", usdCents = 9_999,
        ) as GiveSubmission.Intent
        assertEquals(100_000, withDollars.body.amountMinor)
        assertEquals("KES", withDollars.body.currency)
    }

    @Test
    fun `PayPal keeps to its own range and never recurs`() {
        assertTrue(planUsd(usdCents = 100) is GiveSubmission.Intent) // US$ 1.00
        assertEquals(
            "PayPal gifts are from US$ 1.00 to US$ 10,000.00.",
            (planUsd(usdCents = 99) as GiveSubmission.Blocked).message,
        )
        assertTrue(planUsd(usdCents = 1_000_001) is GiveSubmission.Blocked)
        assertEquals(RECURRING_BLOCKED_MESSAGE, (planUsd(usdCents = 2_500, freq = FREQ_MONTHLY) as GiveSubmission.Blocked).message)
    }

    @Test
    fun `a pledge or need is never paid in dollars`() {
        assertEquals(BOUND_IN_SHILLINGS_MESSAGE, (planUsd(usdCents = 2_500, pledgeId = "p1") as GiveSubmission.Blocked).message)
        assertEquals(BOUND_IN_SHILLINGS_MESSAGE, (planUsd(usdCents = 2_500, needId = "n1") as GiveSubmission.Blocked).message)
    }

    @Test
    fun `a changed dollar amount never replays a held key`() {
        val shape = gift.copy(methodId = "paypal", usdCents = 2_500)
        val held = giveKeyFor(null, shape, fresh)
        assertTrue(giveKeyFor(held, shape.copy(usdCents = 5_000), fresh).key != held.key)
        assertEquals(held.key, giveKeyFor(held, shape, fresh).key)
    }

    // ── Cover the fee (Giving Cycle 2) ──

    @Test
    fun `covering the fee names the fee inside the total`() {
        val covered = (plan(FREQ_ONCE, mpesa, coverFee = true) as GiveSubmission.Intent).body
        assertEquals(101_300, covered.amountMinor) // still the TOTAL charged
        assertEquals(1_300, covered.coverFeeMinor) // KSh 13 of it is the fee
        assertNull((plan(FREQ_ONCE, mpesa, coverFee = false) as GiveSubmission.Intent).body.coverFeeMinor)
        // KSh 100 has no fee to cover: nothing is named.
        assertNull((plan(FREQ_ONCE, mpesa, coverFee = true, amount = 100) as GiveSubmission.Intent).body.coverFeeMinor)
    }

    @Test
    fun `cover fee is added to the amount exactly as iOS does`() {
        // iOS feeFor(1000) == 13 → total 1013 → amount_minor 101300
        assertEquals(1013, chargedAmountMajor(1000, coverFee = true))
        assertEquals(1000, chargedAmountMajor(1000, coverFee = false))
        val intent = plan(FREQ_ONCE, mpesa, coverFee = true) as GiveSubmission.Intent
        assertEquals(101_300, intent.body.amountMinor)
        val sched = plan(FREQ_MONTHLY, mpesa, coverFee = true) as GiveSubmission.Schedule
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
        val intent = plan(FREQ_ONCE, mpesa, pledgeId = "pl-1") as GiveSubmission.Intent
        assertEquals("pl-1", intent.body.pledgeId)
        val sched = plan(FREQ_MONTHLY, mpesa, pledgeId = "pl-1") as GiveSubmission.Schedule
        assertEquals("pl-1", sched.body.pledgeId)
    }

    @Test
    fun `zero amount is blocked before any method check`() {
        assertEquals("Enter an amount to give.", (plan(FREQ_ONCE, mpesa, amount = 0) as GiveSubmission.Blocked).message)
        assertEquals("Enter an amount to give.", (plan(FREQ_ONCE, null, amount = 0) as GiveSubmission.Blocked).message)
    }

    // --- Departments (docs/PARTNERS_PROGRAMME.md §4): a need is a giving target ---

    @Test
    fun `need id rides a one-time intent`() {
        val intent = planGiveSubmission(
            freq = FREQ_ONCE, method = mpesa, fundId = "gift", amountMajor = 5000, coverFee = false,
            phone = "0711222333", accountName = "", idempotencyKey = "idem-2", needId = "need-1",
        ) as GiveSubmission.Intent
        assertEquals("need-1", intent.body.needId)
        assertNull(intent.body.pledgeId)
        assertEquals("gift", intent.body.fund)
    }

    @Test
    fun `a recurring choice with a need is blocked rather than dropping the need`() {
        val s = planGiveSubmission(
            freq = FREQ_MONTHLY, method = mpesa, fundId = "gift", amountMajor = 5000, coverFee = false,
            phone = "0711222333", accountName = "", idempotencyKey = "idem-3", needId = "need-1",
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
            freq = FREQ_ONCE, method = mpesa, fundId = p.fundId!!, amountMajor = p.amountMinor!! / 100, coverFee = false,
            phone = "0711222333", accountName = "", idempotencyKey = "idem-4", pledgeId = p.pledgeId, needId = p.needId,
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
    fun `the form seeds from a bound preset, and resets to the ordinary form`() {
        assertEquals(GiveFormSeed("tithe", 1_000, FREQ_ONCE), giveFormSeed(pledgePay)) // a General partnership pledge has no fund of its own
        assertEquals(GiveFormSeed("discipleship", 1_000, FREQ_ONCE), giveFormSeed(pledgePay.copy(fundId = "discipleship")))
        assertEquals(GiveFormSeed(NEED_GIFT_FUND, 2_500, FREQ_ONCE), giveFormSeed(needGive))
        // What a spent binding resets to — and what an unbound screen starts on.
        val ordinary = GiveFormSeed(DEFAULT_GIVE_FUND, DEFAULT_GIVE_AMOUNT_MAJOR, FREQ_ONCE)
        assertEquals(ordinary, giveFormSeed(null))
        assertEquals(GiveFormSeed("tithe", 1_000, FREQ_ONCE), ordinary)
        assertEquals(GiveFormSeed("offering", 500, FREQ_ONCE), giveFormSeed(GivePreset(fundId = "offering", amountMinor = 50_000)))
    }

    @Test
    fun `every form starts on One-time, so a tap on Give never sets up a monthly charge`() {
        // Giving Cycle 1: a Monthly start turned "tap Give" into a real
        // monthly M-Pesa schedule. No seed, bound or not, starts recurring.
        listOf(null, pledgePay, needGive, GivePreset(fundId = "offering", amountMinor = 50_000), GivePreset()).forEach { p ->
            assertEquals("seed for $p", FREQ_ONCE, giveFormSeed(p).freq)
        }
        // …so the ordinary form, submitted untouched, is an intent — never a schedule.
        val seed = giveFormSeed(null)
        val s = planGiveSubmission(
            freq = seed.freq, method = FALLBACK_MPESA, fundId = seed.fundId, amountMajor = seed.amountMajor,
            coverFee = false, phone = "0711222333", accountName = "", idempotencyKey = "k",
        )
        assertTrue(s is GiveSubmission.Intent)
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

    // ── A refused gift (Giving Cycle 1) ──

    private fun refusal(status: Int, code: String, message: String, details: String? = null) =
        ApiException.parseServerError(
            status,
            """{"error":{"code":"$code","message":"$message","request_id":"r1"${details?.let { ",\"details\":$it" } ?: ""}}}""",
        )

    @Test
    fun `GIFT_IN_PROGRESS follows the waiting prompt with the server's words`() {
        val err = refusal(409, "GIFT_IN_PROGRESS", "A prompt from a moment ago is still waiting on your phone.", """{"transaction_id":"t9"}""")
        assertEquals(
            GiveErrorAction.FollowPrompt("t9", "A prompt from a moment ago is still waiting on your phone."),
            giveErrorAction(err, fallback = "fallback"),
        )
        // Without a transaction to follow, it is only said.
        assertEquals(
            GiveErrorAction.Say("A prompt from a moment ago is still waiting on your phone."),
            giveErrorAction(refusal(409, "GIFT_IN_PROGRESS", "A prompt from a moment ago is still waiting on your phone."), "fallback"),
        )
        assertEquals(
            GiveErrorAction.Say("Waiting."),
            giveErrorAction(refusal(409, "GIFT_IN_PROGRESS", "Waiting.", """{"transaction_id":""}"""), "fallback"),
        )
    }

    @Test
    fun `the 422s and SCHEDULE_EXISTS say the server's message as-is`() {
        listOf(
            refusal(422, "METHOD_UNAVAILABLE", "Card giving is coming soon. Please give with M-Pesa for now.", """{"method":"card"}"""),
            refusal(422, "METHOD_CURRENCY", "PayPal gifts are in US dollars. Enter the amount in dollars.", """{"method":"paypal","currency":"USD"}"""),
            refusal(422, "AMOUNT_OUT_OF_RANGE", "M-Pesa gifts are from KSh 1 to KSh 250,000.", """{"method":"mpesa","min_minor":100,"max_minor":25000000}"""),
            refusal(422, "AMOUNT_OUT_OF_RANGE", "M-Pesa takes whole shillings — no cents.", """{"method":"mpesa","step_minor":100}"""),
            refusal(422, "PHONE_REQUIRED", "That doesn't look like a Kenyan mobile number. Use 07XX XXX XXX or 01XX XXX XXX.", """{"method":"mpesa"}"""),
            refusal(409, "SCHEDULE_EXISTS", "You already give KSh 1,000 every month to Tithe. Change that gift instead of adding a second one.", """{"schedule_id":"s1","status":"active"}"""),
        ).forEach { err ->
            assertEquals(err.code, GiveErrorAction.Say(err.message!!), giveErrorAction(err, fallback = "fallback"))
        }
    }

    @Test
    fun `no server message falls back — a transport failure, or a body that said nothing`() {
        assertEquals(GiveErrorAction.Say("You appear to be offline."), giveErrorAction(null, "You appear to be offline."))
        val silent = ApiException.parseServerError(502, "<html>Bad gateway</html>")
        assertEquals(GiveErrorAction.Say(silent.displayMessage), giveErrorAction(silent, silent.displayMessage))
        assertEquals("Something went wrong (502).", silent.displayMessage)
    }

    private val waiting = GivingDetail(
        transactionId = "t9", amountMinor = 50_000, status = "processing", fund = "discipleship", method = "mpesa",
        fundName = "Discipleship", pledge = IntentPledge("p1", "General partnership"),
    )

    @Test
    fun `a waiting prompt is followed as the server holds it, never as the form says`() {
        val r = intentResultFromDetail(waiting)
        assertEquals("t9", r.transactionId)
        assertEquals("processing", r.status)
        assertEquals("mpesa", r.provider)
        assertEquals(FundRef("discipleship", "Discipleship"), r.fund)
        assertEquals("General partnership", r.pledge?.title)
        // An older server without fund_name still names the fund.
        assertEquals("Tithe", intentResultFromDetail(waiting.copy(fund = "tithe", fundName = null)).fund?.name)
        // Read like any intent: a PIN prompt, toward the pledge.
        assertEquals("Enter your PIN to complete KSh 500 toward your General partnership pledge.", giveCeremonyLine(r, 50_000, "Tithe"))
    }

    @Test
    fun `a waiting prompt spends the binding only when it is for the same pledge or need`() {
        assertTrue(inflightIsForTarget(pledgePay, waiting))
        assertFalse(inflightIsForTarget(pledgePay.copy(pledgeId = "p2"), waiting))
        assertFalse(inflightIsForTarget(pledgePay, waiting.copy(pledge = null)))
        assertTrue(inflightIsForTarget(needGive, waiting.copy(pledge = null, need = ReceiptNeed("n1", "Sound desk"))))
        assertFalse(inflightIsForTarget(needGive, waiting.copy(need = ReceiptNeed("n2", "Chairs"))))
        assertFalse(inflightIsForTarget(null, waiting))
        assertFalse(inflightIsForTarget(GivePreset(fundId = "tithe"), waiting))
    }
}
