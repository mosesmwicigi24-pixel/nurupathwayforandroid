// Giving Cycle 9 parity items: a resent PayPal order still waiting reopens
// its approval page (the server's Cycle 10 returns approve_url on a resend),
// and a Try again refused for the gift itself — a 429 RATE_LIMITED among
// them — goes back to the form with the server's words, where the number can
// change (iOS GiveRetry.target); only no answer, or a key conflict, keeps it
// on the same failed gift.
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GivingIntentResult
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

@OptIn(ExperimentalSerializationApi::class)
class GivingCycle9ParityTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    // ── a resent PayPal order reopens its approval (Cycle 10) ──

    @Test
    fun `a resent PayPal order still waiting reopens its approval page`() {
        val resend = json.decodeFromString<GivingIntentResult>(
            """{"transaction_id":"t9","status":"processing","provider":"paypal","provider_ref":"5O190127TN364715T",
               "approve_url":"https://www.paypal.com/checkoutnow?token=5O190127TN364715T","idempotency_key":"k","reused":true}""",
        )
        assertTrue(resend.reused)
        assertTrue(canReopenPayPal(resend, resend.status))
        assertEquals(
            "Continue on PayPal to complete US$ 25.00 to Mission — we'll confirm it when you come back.",
            giveCeremonyLine(resend, 2_500, chipFundLabel = "Mission", currency = "USD"),
        )
        // Settled or failed meanwhile, or no page sent (an older server): nothing to reopen.
        assertFalse(canReopenPayPal(resend, "succeeded"))
        assertFalse(canReopenPayPal(resend, "failed"))
        assertFalse(canReopenPayPal(resend.copy(approveUrl = null), "processing"))
        assertFalse(canReopenPayPal(resend.copy(approveUrl = " "), "processing"))
    }

    // ── Try again refused with 429: back to the form (iOS parity) ──

    private fun http(status: Int, body: String) =
        HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))

    @Test
    fun `a Try again refused for the gift itself goes back to the form, a key conflict does not`() {
        val limited = """{"error":{"code":"RATE_LIMITED","message":"We've sent several prompts to that number just now. Try again in 10 minutes, or give from your own number.","details":{"retry_after_sec":540}}}"""
        val refusal = ApiException.parseServerError(429, limited)
        // 429: the form's turn — where the number can change — with the server's words.
        assertFalse(retryStaysOnGift(http(429, limited), refusal))
        assertEquals(
            GiveErrorAction.Say("We've sent several prompts to that number just now. Try again in 10 minutes, or give from your own number."),
            giveErrorAction(refusal, "x"),
        )
        // Any other refusal of the gift itself: the same (it would be refused again).
        val phone = """{"error":{"code":"PHONE_REQUIRED","message":"Add your M-Pesa number first."}}"""
        assertFalse(retryStaysOnGift(http(422, phone), ApiException.parseServerError(422, phone)))
        // Only the key refused: the same failed gift, a fresh key next tap.
        val conflict = """{"error":{"code":"CONFLICT","message":"That request key is already in use. Try again."}}"""
        assertTrue(retryStaysOnGift(http(409, conflict), ApiException.parseServerError(409, conflict)))
        // No answer at all: the same gift, the same key (the replay finds it).
        assertTrue(retryStaysOnGift(SocketTimeoutException("timeout"), null))
    }
}
