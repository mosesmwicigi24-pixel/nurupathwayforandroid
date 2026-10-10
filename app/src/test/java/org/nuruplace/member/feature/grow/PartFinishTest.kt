// Finishing a part of a plan day (pathway docs/EXPERIENCE.md §7.4 #1, §4) —
// the one rule every part kind uses (the readers' Watch/Listen, The Word and
// Respond, and Talk it Over). Seen: the reader ticked the hub's row and went
// back although the server had recorded nothing (a dropped connection). Now
// the first part the server does not record stops the finish; what landed
// keeps its tick; the button tries the rest.
package org.nuruplace.member.feature.grow

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.SegmentCompleteResult
import java.io.IOException

class PartFinishTest {
    private fun ack(id: String, dayDone: Boolean = false) =
        SegmentCompleteResult(segmentId = id, dayNumber = 1, dayCompleted = dayDone, dayComplete = dayDone)

    @Test fun `every part recorded — each ticked, the last ack kept`() = runTest {
        val saved = mutableListOf<String>()
        val r = completeSegments(listOf("a51a", "3b0c", "86e9"), { id -> ack(id, dayDone = id == "86e9") }) { saved += it }
        assertNull(r.failure)
        assertEquals(listOf("a51a", "3b0c", "86e9"), saved)
        assertEquals("86e9", r.lastAck?.segmentId)
        assertTrue(r.lastAck!!.dayComplete)
    }

    @Test fun `the first part not recorded stops the finish — what landed keeps its tick`() = runTest {
        val saved = mutableListOf<String>()
        val tried = mutableListOf<String>()
        val offline = IOException("connection refused")
        val r = completeSegments(listOf("a51a", "3b0c", "86e9"), { id ->
            tried += id
            if (id == "3b0c") throw offline else ack(id)
        }) { saved += it }
        assertSame(offline, r.failure)
        assertEquals(listOf("a51a"), saved)           // only the one the server recorded
        assertEquals(listOf("a51a", "3b0c"), tried)    // the third is never tried
        assertEquals("a51a", r.lastAck?.segmentId)
    }

    @Test fun `trying again finishes the rest`() = runTest {
        val saved = mutableListOf("a51a")
        val r = completeSegments(listOf("3b0c", "86e9"), { id -> ack(id, dayDone = id == "86e9") }) { saved += it }
        assertNull(r.failure)
        assertEquals(listOf("a51a", "3b0c", "86e9"), saved)
    }

    @Test fun `nothing left to record — nothing to say`() = runTest {
        val r = completeSegments(emptyList(), { error("never called") }) { error("never called") }
        assertNull(r.failure)
        assertNull(r.lastAck)
    }
}
