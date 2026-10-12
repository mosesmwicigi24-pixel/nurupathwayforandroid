// One state language (pathway docs/EXPERIENCE.md §4): every failure a member
// can meet maps to §4's title, line and action — offline, an ended session,
// our side, a page that is gone — and a refusal written for members by our
// own API keeps its words. What it found: Pathway said "Invalid or expired
// access token", Plans the same in another style, Events "check your
// connection" when the connection was fine.
package org.nuruplace.member.data.net

import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.ui.components.offering
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class StateLanguageTest {
    private fun http(status: Int, body: String = "") =
        HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))

    private fun envelope(code: String, message: String) =
        """{"error":{"code":"$code","message":"$message","request_id":"r1"}}"""

    // ── §4 row 1: no network / timeout ──

    @Test fun `offline — nothing saved yet`() {
        val s = StateLanguage.forError(UnknownHostException("pathway.nuruplace.org"), deviceOnline = false)
        assertEquals("You're offline", s.title)
        assertEquals("Connect to the internet, then try again.", s.line)
        assertEquals(StateAction.RETRY, s.action)
        assertEquals("Try again", s.action.label)
        assertEquals("You're offline. Connect to the internet, then try again.", s.sentence)
    }

    @Test fun `offline — showing what the member last saw`() {
        val s = StateLanguage.forError(SocketTimeoutException("timeout"), deviceOnline = false, hasSavedCopy = true)
        assertEquals("You're offline", s.title)
        assertEquals("Showing what you last saw — we'll refresh when you're back.", s.line)
    }

    @Test fun `a phone with a network that got no answer is never told it is offline`() {
        listOf(
            UnknownHostException("x"), SocketTimeoutException("connect timed out"), SocketTimeoutException("timeout"),
            ConnectException("refused"), SSLException("reset"), IOException("closed"),
        ).forEach { e ->
            val s = StateLanguage.forError(e, deviceOnline = true)
            assertEquals(e.toString(), StateLanguage.serverError, s)
            assertFalse(e.toString(), s.sentence.contains("offline") || s.sentence.contains("connection"))
        }
    }

    @Test fun `with the phone's state unknown a timeout is the host's silence, the rest read offline`() {
        assertEquals(StateLanguage.serverError, StateLanguage.forError(SocketTimeoutException("connect timed out")))
        assertEquals(StateLanguage.serverError, StateLanguage.forError(SocketTimeoutException("timeout")))
        assertEquals(StateLanguage.offline(), StateLanguage.forError(UnknownHostException("x")))
        assertEquals(StateLanguage.offline(), StateLanguage.forError(ConnectException("unreachable")))
    }

    // ── §4 row 2: the session has ended ──

    @Test fun `a 401 ends the session — never the token's own words`() {
        val s = StateLanguage.forError(http(401), serverWords = "Invalid or expired access token")
        assertEquals("Your session has ended", s.title)
        assertEquals("Sign in again to pick up where you left off.", s.line)
        assertEquals(StateAction.SIGN_IN, s.action)
        assertEquals("Sign in", s.action.label)
    }

    @Test fun `a 401 read off the wire never surfaces the server's text`() {
        val s = ApiException.state(http(401, envelope("TOKEN_EXPIRED", "Invalid or expired access token")))
        assertEquals(StateLanguage.sessionEnded, s)
        assertEquals(
            "Your session has ended. Sign in again to pick up where you left off.",
            ApiException.message(http(401, envelope("TOKEN_EXPIRED", "Invalid or expired access token"))),
        )
    }

    @Test fun `a 401 to a typed password keeps the server's own words`() {
        assertEquals(
            "Invalid email or password",
            ApiException.message(http(401, envelope("AUTH_REQUIRED", "Invalid email or password")), credentials = true),
        )
    }

    // ── §4 row 3: our side ──

    @Test fun `a 5xx is our side — never the server's Internal server error`() {
        listOf(500, 502, 503, 504).forEach { status ->
            val s = ApiException.state(http(status, envelope("INTERNAL", "Internal server error")))
            assertEquals("$status", "Something went wrong on our side", s.title)
            assertEquals("It isn't you — please try again in a moment.", s.line)
            assertEquals(StateAction.RETRY, s.action)
        }
        assertEquals(
            "Something went wrong on our side. It isn't you — please try again in a moment.",
            ApiException.message(http(502, "<html>Bad gateway</html>")),
        )
    }

    @Test fun `an answer we could not read is ours too — no parser text`() {
        val s = StateLanguage.forError(SerializationException("Expected start of the object '{', but had 'EOF'"))
        assertEquals(StateLanguage.serverError, s)
        assertEquals(StateLanguage.serverError, ApiException.state(IllegalStateException("boom")))
    }

    // ── §4 row 4: gone ──

    @Test fun `a 404 is not here any more`() {
        val s = ApiException.state(http(404, envelope("NOT_FOUND", "Module not found")))
        assertEquals("This isn't here any more", s.title)
        assertEquals("It may have been moved or removed.", s.line)
        assertEquals(StateAction.BACK, s.action)
        assertEquals("Go back", s.action.label)
    }

    // ── §4 row 5: a refusal in our own words ──

    @Test fun `a 422 with our message keeps it, whole`() {
        val s = ApiException.state(http(422, envelope("UNPROCESSABLE", "No exam questions for this level")))
        assertEquals(StateCause.REFUSAL, s.cause)
        assertEquals("No exam questions for this level", s.title)
        assertNull(s.line)
        assertEquals("No exam questions for this level", s.sentence)
        assertEquals(StateAction.RETRY, s.action)
    }

    @Test fun `400 and 409 refusals keep their words — a 4xx with none is ours`() {
        assertEquals(
            "Finish every module in this level before the exam",
            ApiException.message(http(409, envelope("GATE_LOCKED", "Finish every module in this level before the exam"))),
        )
        // Only OUR envelope speaks: a bare message, a proxy's page, an empty body do not.
        assertEquals(StateLanguage.serverError, ApiException.state(http(400, """{"message":"Nope"}""")))
        assertEquals(StateLanguage.serverError, ApiException.state(http(413, "<html>Request Entity Too Large</html>")))
        assertEquals(StateLanguage.serverError, StateLanguage.forStatus(422, "  "))
    }

    // ── the exam's refusal (EXPERIENCE.md §7.2 #1) ──

    @Test fun `the exam's refusal offers Go back only — never Try again`() {
        // The exam screen asks for refusalAction = BACK: the server's words,
        // and the way out — trying again would only be refused again.
        val notReady = ApiException.state(
            http(422, envelope("UNPROCESSABLE", "Your Level 1 exam isn't ready yet — we'll let you know when it opens.")),
        ).offering(StateAction.BACK)
        assertEquals("Your Level 1 exam isn't ready yet — we'll let you know when it opens.", notReady.title)
        assertNull(notReady.line)
        assertEquals(StateAction.BACK, notReady.action)
        assertEquals("Go back", notReady.action.label)
        val gate = ApiException.state(http(409, envelope("GATE_LOCKED", "Finish every module in this level before the exam")))
            .offering(StateAction.BACK)
        assertEquals(StateAction.BACK, gate.action)
        // Not a refusal: what happened keeps its own action.
        assertEquals(StateAction.RETRY, StateLanguage.serverError.offering(StateAction.BACK).action)
        assertEquals(StateAction.RETRY, StateLanguage.offline().offering(StateAction.BACK).action)
        assertEquals(StateAction.SIGN_IN, StateLanguage.sessionEnded.offering(StateAction.BACK).action)
        // Every other screen keeps Try again on a refusal, as before.
        assertEquals(StateAction.RETRY, StateLanguage.refusal("Nope").offering(StateAction.RETRY).action)
    }
}
