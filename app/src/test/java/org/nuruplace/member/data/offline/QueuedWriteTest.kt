// A member's write — a chat message, a comment — is sent, waits in the
// offline queue, or is refused in §4's words; never an exception out of the
// tap (EXPERIENCE.md §4, §1.7).
package org.nuruplace.member.data.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class QueuedWriteTest {
    @Test fun `sent, queued and refused are values`() = runBlocking {
        assertEquals(WriteOutcome.Sent, queuedWrite(send = { Unit }, failureLine = { "x" }))
        assertEquals(WriteOutcome.Queued, queuedWrite(send = { null }, failureLine = { "x" }))
        assertEquals(
            WriteOutcome.Failed("Couldn't send that. gone"),
            queuedWrite(send = { throw IOException("gone") }, failureLine = { "Couldn't send that. ${it.message}" }),
        )
    }

    @Test(expected = CancellationException::class)
    fun `a cancel is not a failure`(): Unit = runBlocking {
        queuedWrite(send = { throw CancellationException("left") }, failureLine = { "x" })
    }
}
