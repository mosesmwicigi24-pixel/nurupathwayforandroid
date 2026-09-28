// The one distinction that matters during a host outage: a SYN nobody
// answered ("connect timed out") is the server's absence, not the phone's
// network, and the member is told so; a read timeout stays the softer line.
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

    @Test fun `a read timeout keeps the softer line`() {
        assertEquals(
            "Nuru Place is taking too long to respond. Please try again.",
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
        assertEquals("Something went wrong (422).", bare.displayMessage)
        // The older flat shape still gives its message.
        assertEquals("Nope", ApiException.parseServerError(400, """{"message":"Nope"}""").message)
        // Not JSON, empty, or absent.
        listOf("<html>502</html>", "", null, "[1,2]", """{"error":"text"}""").forEach { body ->
            val e = ApiException.parseServerError(502, body)
            assertNull(body, e.code)
            assertNull(body, e.message)
            assertEquals("Something went wrong (502).", e.displayMessage)
        }
        assertEquals("Your session has expired. Please sign in again.", ApiException.parseServerError(401, null).displayMessage)
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
