// The one distinction that matters during a host outage: a SYN nobody
// answered ("connect timed out") is the server's absence, not the phone's
// network, and the member is told so; a read timeout stays the softer line.
package org.nuruplace.member.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
