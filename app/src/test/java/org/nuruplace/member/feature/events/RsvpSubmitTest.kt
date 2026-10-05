// EXPERIENCE.md §4 / Cycle 3's audit: a refused RSVP had no handler — the
// HttpException escaped the tap's coroutine and could crash the app (a 422
// "RSVP is not enabled for this event" is a real one). Now every outcome is
// a value: sent, queued offline, or refused in the server's own words.
package org.nuruplace.member.feature.events

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.nuruplace.member.data.net.ApiException
import retrofit2.HttpException
import retrofit2.Response

class RsvpSubmitTest {
    @Test fun `a sent RSVP is sent`() = runBlocking {
        assertEquals(RsvpOutcome.Sent, submitRsvp("going", send = { Unit }, failureLine = { "x" }))
    }

    @Test fun `an offline RSVP waits in the queue and says so`() = runBlocking {
        assertEquals(RsvpOutcome.Queued("maybe"), submitRsvp("maybe", send = { null }, failureLine = { "x" }))
    }

    @Test fun `a refusal is said in the server's own words — never a crash`() = runBlocking {
        val refusal = HttpException(
            Response.error<Unit>(422, """{"error":{"code":"UNPROCESSABLE","message":"RSVP is not enabled for this event"}}""".toResponseBody("application/json".toMediaType())),
        )
        val r = submitRsvp("going", send = { throw refusal }, failureLine = { ApiException.saveFailureLine(it) })
        assertEquals(RsvpOutcome.Failed("Couldn't save that. RSVP is not enabled for this event"), r)
    }

    @Test(expected = CancellationException::class)
    fun `leaving the page is not a failure`(): Unit = runBlocking {
        submitRsvp("going", send = { throw CancellationException("gone") }, failureLine = { "x" })
    }
}
