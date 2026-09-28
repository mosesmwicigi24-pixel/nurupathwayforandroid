// Giving Cycle 3 — "Try again" on a failed gift. The server retries it with
// everything it carried (POST /giving/transactions/{id}/retry); the app sends
// a fresh key each time — replayed only when the last retry of the SAME gift,
// to the SAME number, got no answer at all — and, for mobile money, the
// number the member is using now. A card gift is not retried from here, and
// a giving_gift_failed push opens that gift.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.SocketTimeoutException

class GiveRetryLogicTest {
    private var minted = 0
    private val fresh = { "retry-${++minted}" }

    @Test
    fun `every Try again sends a fresh key once the last one was answered`() {
        val first = retryKeyFor(null, "t1", "+254711222333", fresh)
        assertEquals(HeldRetryKey("retry-1", "t1", "+254711222333"), first)
        // Answered (any HTTP reply): the key is released, so the next tap mints another.
        assertFalse(keepGiveKeyAfter(HttpException(Response.error<Any>(422, "{}".toResponseBody("application/json".toMediaType())))))
        assertEquals("retry-2", retryKeyFor(null, "t1", "+254711222333", fresh).key)
    }

    @Test
    fun `a retry that got no answer replays its key, so a lost reply is never a second prompt`() {
        val held = retryKeyFor(null, "t1", "+254711222333", fresh)
        assertTrue(keepGiveKeyAfter(SocketTimeoutException("timeout")))
        assertEquals(held, retryKeyFor(held, "t1", "+254711222333", fresh))
        assertEquals(1, minted)
    }

    @Test
    fun `another gift or another number is another request`() {
        val held = retryKeyFor(null, "t1", "+254711222333", fresh)
        assertTrue(retryKeyFor(held, "t2", "+254711222333", fresh).key != held.key)
        assertTrue(retryKeyFor(held, "t1", "+254722000111", fresh).key != held.key)
        assertTrue(retryKeyFor(held, "t1", null, fresh).key != held.key)
    }

    @Test
    fun `only a gift this app can carry offers Try again`() {
        listOf("mpesa", "airtel", "paypal", " MPESA ").forEach { assertTrue(it, canRetryGift(it)) }
        assertFalse(canRetryGift("card")) // no Stripe step here: a card retry would sit processing
        assertFalse(canRetryGift("stripe"))
        assertFalse(canRetryGift(null))
        assertFalse(canRetryGift(""))
    }

    @Test
    fun `a mobile-money retry prompts the number in use, a PayPal one no phone`() {
        assertEquals("+254711222333", retryPhoneFor("mpesa", "0711 222 333"))
        assertEquals("+254711222333", retryPhoneFor("airtel", "+254711222333"))
        assertNull(retryPhoneFor("mpesa", null)) // none known → the server prompts the profile's
        assertNull(retryPhoneFor("mpesa", "12345"))
        assertNull(retryPhoneFor("paypal", "0711222333"))
        assertNull(retryPhoneFor(null, "0711222333"))
    }

    @Test
    fun `a giving_gift_failed push opens that gift`() {
        val push = mapOf(
            "transaction_id" to "t9", "amount_minor" to "100000", "currency" to "KES", "fund" to "tithe",
            "failure_code" to "unreachable", "reason" to "The M-Pesa prompt couldn't reach the phone.", "hint" to "Check the phone is on.",
        )
        assertEquals("give-gift/t9", givingPushRoute(push))
        assertEquals("give-gift/t9", giftRoute("t9"))
        // The in-app notification centre lands in the same place.
        assertEquals("give-gift/t9", givingDest(transactionId = "t9", failureCode = "unreachable"))
        // Not a failure (no failure_code), or no gift: not this route.
        assertNull(givingPushRoute(mapOf("transaction_id" to "t9")))
        assertNull(givingPushRoute(mapOf("transaction_id" to " ", "failure_code" to "cancelled")))
        assertNull(givingPushRoute(mapOf("module_id" to "m1")))
    }
}
