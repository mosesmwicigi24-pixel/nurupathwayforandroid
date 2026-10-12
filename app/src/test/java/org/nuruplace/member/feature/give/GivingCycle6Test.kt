// Giving Cycle 6 — safe to give (server pathway 3f0182b, docs/GIVING.md §9).
// Every giving key comes from one function and is never in the server's own
// namespaces (sched: claim: pledge: web: website: office:, refused with 400,
// matched without regard to case); any answer spends a key, so a 409
// CONFLICT — the key is another gift's — goes through on the member's next
// tap with a fresh one, and Try again stays on the same failed gift; a 429
// RATE_LIMITED is said in the server's words and never resent; and a resend's
// answer with provider_ref still null is the same gift, watched by its
// transaction id.
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GivingIntentResult
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
class GivingCycle6Test {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private fun http(status: Int, body: String) =
        HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))

    private val shape = GiveRequestShape(
        amountMajor = 1_000, fundId = "tithe", methodId = "mpesa", pledgeId = null, needId = null,
        freq = FREQ_ONCE, giftName = "", coverFee = false, phone = "+254722000111",
    )

    // ── one place makes every key, and never in the server's namespaces ──

    @Test
    fun `the server's namespaces are recognised whatever their case`() {
        listOf(
            "sched:00000000-0000-0000-0000-000000000000:first", "SCHED:X", "claim:1", "Claim:1",
            "pledge:p1", "PLEDGE:p1", "web:1", "Web:1", "website:1", "WebSite:1", "office:9", "OFFICE:9",
        ).forEach { assertTrue(it, isReservedGivingKey(it)) }
        // Near misses are not the server's.
        listOf(
            "schedule-1", "sched-1", "claims:1", "pledges:1", "webs:1", "offices:1", "x-sched:1", " sched:1",
            "3f1c9a2e-7b4d-4c1e-9a55-0d2f8e6b7c10",
        ).forEach { assertFalse(it, isReservedGivingKey(it)) }
        assertEquals(listOf("sched:", "claim:", "pledge:", "web:", "website:", "office:"), RESERVED_GIVING_KEY_PREFIXES)
    }

    @Test
    fun `every key the app makes is a UUID, never reserved, within the server's 8 to 255`() {
        val keys = (1..1_000).map { newGivingKey() }
        assertEquals(1_000, keys.toSet().size) // never another gift's
        keys.forEach { k ->
            assertFalse(k, isReservedGivingKey(k))
            assertFalse(k, ':' in k)
            assertTrue(k, k.length in 8..255)
            assertEquals(k, UUID.fromString(k).toString())
        }
    }

    @Test
    fun `a gift, a recurring gift and a Try again all take their key from it`() {
        // The same function the screen calls — no key is made anywhere else.
        val gift = giveKeyFor(null, shape)
        val schedule = giveKeyFor(null, shape.copy(freq = FREQ_MONTHLY))
        val retry = retryKeyFor(null, "t-failed", "+254722000111")
        listOf(gift.key, schedule.key, retry.key).forEach { k ->
            assertFalse(isReservedGivingKey(k))
            assertEquals(36, k.length)
        }
        assertEquals(3, setOf(gift.key, schedule.key, retry.key).size)
    }

    // ── 429 RATE_LIMITED: said as-is, the key spent, never resent ──

    private val rateLimited = """{"error":{"code":"RATE_LIMITED","message":"We've sent several prompts to that number just now. Try again in 10 minutes, or give from your own number.","request_id":"r1","details":{"retry_after_sec":540}}}"""

    @Test
    fun `a 429 is said in the server's words, which name the minutes`() {
        val err = ApiException.parseServerError(429, rateLimited)
        assertEquals("RATE_LIMITED", err.code)
        assertEquals(
            GiveErrorAction.Say("We've sent several prompts to that number just now. Try again in 10 minutes, or give from your own number."),
            giveErrorAction(err, fallback = "Something went wrong."),
        )
        // The wait is there too, should it ever be needed.
        assertEquals("540", err.details?.get("retry_after_sec")?.jsonPrimitive?.content)
        // Read off a real refusal the way the screen reads it: once.
        assertEquals("RATE_LIMITED", ApiException.serverError(http(429, rateLimited))?.code)
    }

    @Test
    fun `a 429 spends the key — the next tap is a new request, and only a tap sends it`() {
        val held = giveKeyFor(null, shape) { "k-1" }
        assertNull(heldKeyAfter(held, http(429, rateLimited)))
        // The member taps again (the same gift): a fresh key, not a replay.
        assertEquals("k-2", giveKeyFor(heldKeyAfter(held, http(429, rateLimited)), shape) { "k-2" }.key)
        // A Try again refused the same way spends its key too.
        val retry = retryKeyFor(null, "t-failed", "+254722000111") { "r-1" }
        assertNull(heldRetryAfter(retry, http(429, rateLimited)))
    }

    // ── 409 CONFLICT: the key is another gift's — a fresh one next tap ──

    private val conflict = """{"error":{"code":"CONFLICT","message":"That request key is already in use. Try again.","details":{"fields":[{"path":"idempotency_key","message":"in use"}]}}}"""

    @Test
    fun `a key conflict is said, never followed, and the next tap goes through with a fresh key`() {
        val err = ApiException.parseServerError(409, conflict)
        assertEquals(GiveErrorAction.Say("That request key is already in use. Try again."), giveErrorAction(err, "x"))
        val held = giveKeyFor(null, shape) { "k-1" }
        val after = heldKeyAfter(held, http(409, conflict))
        assertNull(after)
        val next = giveKeyFor(after, shape) { "k-2" }
        assertEquals("k-2", next.key)
        assertEquals(shape, next.shape) // the same gift, asked again
    }

    @Test
    fun `Try again after a 409 CONFLICT retries the same failed gift with a fresh key`() {
        val first = retryKeyFor(null, "t-failed", "+254722000111") { "r-1" }
        // The refusal spends the key; the failed gift stays the ceremony's.
        val after = heldRetryAfter(first, http(409, conflict))
        assertNull(after)
        val next = retryKeyFor(after, "t-failed", "+254722000111") { "r-2" }
        assertEquals("t-failed", next.retryOf)
        assertEquals("+254722000111", next.phone)
        assertNotEquals(first.key, next.key)
    }

    @Test
    fun `only no answer at all keeps a key, so a lost reply is found, never doubled`() {
        val held = giveKeyFor(null, shape) { "k-1" }
        assertEquals(held, heldKeyAfter(held, SocketTimeoutException("timeout")))
        assertEquals(held, giveKeyFor(heldKeyAfter(held, SocketTimeoutException("timeout")), shape) { "k-2" })
        val retry = retryKeyFor(null, "t-failed", null) { "r-1" }
        assertEquals(retry, heldRetryAfter(retry, java.io.IOException("reset")))
        // Success spends it like any answer.
        assertNull(heldKeyAfter(held, null))
    }

    // ── a resend's answer: the same gift, its prompt ref maybe not yet known ──

    @Test
    fun `a resend answered with provider_ref null is the same gift, watched by its id`() {
        val resend = json.decodeFromString<GivingIntentResult>(
            """{"transaction_id":"t1","status":"processing","provider":"mpesa","provider_ref":null,"idempotency_key":"k-1","reused":true,
               "fund":{"code":"tithe","name":"Tithe"},"pledge":null}""",
        )
        assertTrue(resend.reused)
        assertEquals("mpesa", resend.provider)
        assertNull(resend.providerRef)
        // Not an error: still processing, still watched, still the PIN's words.
        assertEquals(GiftOutcome.Processing, giftOutcome(resend.status))
        // Watched on "Check your phone" (§7.3) — never thanked while it waits.
        assertTrue(waitsOnPhone(resend, resend.status, promptPhone = null))
        assertEquals(3_000L, GiftWatch.nextDelayMs(0))
        assertEquals("Enter your PIN to complete KSh 1,000 to Tithe.", giveCeremonyLine(resend, 100_000, chipFundLabel = "Tithe"))
        assertTrue(canRetryGift(resend.provider))
        // Once the prompt is out, a later resend carries its ref.
        val later = json.decodeFromString<GivingIntentResult>(
            """{"transaction_id":"t1","status":"processing","provider":"mpesa","provider_ref":"ws_CO_280920261245","idempotency_key":"k-1","reused":true}""",
        )
        assertEquals("ws_CO_280920261245", later.providerRef)
        assertEquals(resend.transactionId, later.transactionId)
    }

    @Test
    fun `a PayPal or card replay that lost a race answers the first row, fields and all`() {
        val paypal = json.decodeFromString<GivingIntentResult>(
            """{"transaction_id":"t2","status":"processing","provider":"paypal","provider_ref":"5O190127TN364715T","idempotency_key":"k-2","reused":true}""",
        )
        assertEquals("paypal", paypal.provider)
        assertEquals("5O190127TN364715T", paypal.providerRef)
        assertNull(paypal.approveUrl) // a replay carries no approve link
        val card = json.decodeFromString<GivingIntentResult>(
            """{"transaction_id":"t3","status":"processing","provider":null,"provider_ref":null,"idempotency_key":"k-3","reused":true}""",
        )
        assertNull(card.provider)
        assertEquals(GiftOutcome.Processing, giftOutcome(card.status))
    }
}
