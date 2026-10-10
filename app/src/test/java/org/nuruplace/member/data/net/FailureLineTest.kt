// The one failure line for a write the server did not record (EXPERIENCE.md
// §7.4, §4): "Couldn't save that." or "Couldn't send that." and §4's
// sentence for why — the same words iOS's NuruStateCopy.saveFailureLine says.
package org.nuruplace.member.data.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

class FailureLineTest {
    private fun http(status: Int, body: String) =
        HttpException(Response.error<Unit>(status, body.toResponseBody("application/json".toMediaType())))

    @Test fun `a timeout is our side`() {
        assertEquals(
            "Couldn't save that. Something went wrong on our side. It isn't you — please try again in a moment.",
            ApiException.saveFailureLine(SocketTimeoutException("read timed out")),
        )
    }

    @Test fun `a refusal keeps the server's own words`() {
        assertEquals(
            "Couldn't save that. RSVP is not enabled for this event",
            ApiException.saveFailureLine(http(422, """{"error":{"code":"UNPROCESSABLE","message":"RSVP is not enabled for this event"}}""")),
        )
    }

    @Test fun `a send says send`() {
        assertEquals(
            "Couldn't send that. Something went wrong on our side. It isn't you — please try again in a moment.",
            ApiException.failureLine(ApiException.SEND_FAILED, http(500, """{"error":{"code":"INTERNAL","message":"Internal server error"}}""")),
        )
    }
}
