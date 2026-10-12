// The one distinction that matters during a host outage: a SYN nobody
// answered ("connect timed out") is the server's absence, not the phone's
// network, and the member is told so — and since the state language
// (EXPERIENCE.md §4, StateLanguageTest) a slow answer is our side too.
//
// And the error envelope read for its CODE (Giving Cycle 1): `{ error: { code,
// message, request_id, details? } }` — once, since an error body is a
// one-shot stream — so Give can follow GIFT_IN_PROGRESS's transaction.
package org.nuruplace.member.data.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ApiExceptionMessageTest {
    @Test fun `a connect timeout is named as the server's absence`() {
        val m = ApiException.message(SocketTimeoutException("connect timed out"))
        assertTrue(m, m.contains("on our side"))
    }

    @Test fun `a read timeout is our side, not the phone's connection`() {
        assertEquals(
            "Something went wrong on our side. It isn't you — please try again in a moment.",
            ApiException.message(SocketTimeoutException("timeout")),
        )
    }

    @Test fun `the envelope's code, message and details are read`() {
        val e = ApiException.parseServerError(
            409,
            """{"error":{"code":"GIFT_IN_PROGRESS","message":"A prompt from a moment ago is still waiting on your phone.","request_id":"r1","details":{"transaction_id":"t9","n":3}}}""",
        )
        assertEquals(409, e.status)
        assertEquals("GIFT_IN_PROGRESS", e.code)
        assertEquals("A prompt from a moment ago is still waiting on your phone.", e.message)
        assertEquals("t9", e.detail("transaction_id"))
        assertNull(e.detail("n"))          // not a string
        assertNull(e.detail("missing"))
        assertEquals(e.message, e.displayMessage)
    }

    @Test fun `a body that says little leaves the status to speak`() {
        val bare = ApiException.parseServerError(422, """{"error":{"code":"PHONE_REQUIRED"}}""")
        assertEquals("PHONE_REQUIRED", bare.code)
        assertNull(bare.message)
        assertNull(bare.details)
        // No words of its own: the state language (§4), never a status code.
        assertEquals(StateLanguage.serverError.sentence, bare.displayMessage)
        // The older flat shape still gives its message.
        assertEquals("Nope", ApiException.parseServerError(400, """{"message":"Nope"}""").message)
        // Not JSON, empty, or absent.
        listOf("<html>502</html>", "", null, "[1,2]", """{"error":"text"}""").forEach { body ->
            val e = ApiException.parseServerError(502, body)
            assertNull(body, e.code)
            assertNull(body, e.message)
            assertEquals(StateLanguage.serverError.sentence, e.displayMessage)
        }
        assertEquals(StateLanguage.sessionEnded.sentence, ApiException.parseServerError(401, null).displayMessage)
    }

    @Test fun `raw server text never reaches the member — a parse failure and a 5xx are our side`() {
        // EXPERIENCE.md §7.3: "Request body failed validation" is our side's fault.
        val parse = ApiException.parseServerError(
            400, """{"error":{"code":"VALIDATION_FAILED","message":"Request body failed validation","details":{"fields":[]}}}""",
        )
        assertEquals("VALIDATION_FAILED", parse.code)
        assertNull(parse.refusalWords)
        assertEquals("Something went wrong on our side. It isn't you — please try again in a moment.", parse.displayMessage)
        val crash = ApiException.parseServerError(500, """{"error":{"code":"INTERNAL","message":"Internal server error"}}""")
        assertNull(crash.refusalWords)
        assertEquals(StateLanguage.serverError.sentence, crash.displayMessage)
        // A refusal in our own words keeps them.
        val refusal = ApiException.parseServerError(422, """{"error":{"code":"AMOUNT_OUT_OF_RANGE","message":"M-Pesa gifts are from KSh 1 to KSh 250,000."}}""")
        assertEquals("M-Pesa gifts are from KSh 1 to KSh 250,000.", refusal.refusalWords)
        assertEquals("M-Pesa gifts are from KSh 1 to KSh 250,000.", refusal.displayMessage)
        // A VALIDATION_FAILED in words written for the member is theirs (iOS:
        // ExperienceCycle3Tests keeps "That code is not valid"); only the
        // body-parse failure is ours.
        val code = ApiException.parseServerError(400, """{"error":{"code":"VALIDATION_FAILED","message":"That code is not valid"}}""")
        assertEquals("That code is not valid", code.refusalWords)
        val photo = ApiException.parseServerError(400, """{"error":{"code":"VALIDATION_FAILED","message":"Photo exceeds 5 MB"}}""")
        assertEquals("Photo exceeds 5 MB", photo.displayMessage)
        // The one-line form says the same (Partners' actions, the claim form, PayPal's capture).
        val http = HttpException(
            Response.error<Any>(
                400,
                """{"error":{"code":"VALIDATION_FAILED","message":"Request body failed validation"}}""".toResponseBody("application/json".toMediaType()),
            ),
        )
        assertEquals(StateLanguage.serverError.sentence, ApiException.message(http))
    }

    @Test fun `serverError reads an HTTP refusal and nothing else`() {
        val body = """{"error":{"code":"SCHEDULE_EXISTS","message":"You already give KSh 1,000 every month to Tithe.","details":{"schedule_id":"s1"}}}"""
        val http = HttpException(Response.error<Any>(409, body.toResponseBody("application/json".toMediaType())))
        val e = ApiException.serverError(http)!!
        assertEquals("SCHEDULE_EXISTS", e.code)
        assertEquals("s1", e.detail("schedule_id"))
        assertNull(ApiException.serverError(IOException("offline")))
        assertNull(ApiException.serverError(IllegalStateException("x")))
    }
}
