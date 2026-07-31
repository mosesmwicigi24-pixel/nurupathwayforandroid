package org.nuruplace.member.feature.live

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nuruplace.member.data.net.LiveNowRow

/** Pure-logic coverage for [filterOutSelfStream] — the fix behind the
 *  2026-07-31 device report where a broadcaster's own Home screen offered
 *  them "● LIVE test 2 [Join live]" for the stream they were actively
 *  hosting (LiveDiscoveryCenter.ingest() never excluded the local device's
 *  own in-progress broadcast from a `/live/now` result; iOS had this exact
 *  bug and was already root-caused there). Kept as a plain top-level
 *  function specifically so it's unit-testable without a StateFlow/Net
 *  harness, matching this codebase's existing Live pure-logic test posture
 *  (see LiveBroadcastInteractionsTest.kt). */
class LiveDiscoveryCenterTest {

    private fun row(streamId: String) = LiveNowRow(streamId = streamId, title = "stream $streamId")

    @Test fun `no self stream leaves the list untouched`() {
        val rows = listOf(row("a"), row("b"))
        assertEquals(rows, filterOutSelfStream(rows, selfStreamId = null))
    }

    @Test fun `self stream is excluded from the result`() {
        val rows = listOf(row("a"), row("b"), row("c"))
        assertEquals(listOf(row("a"), row("c")), filterOutSelfStream(rows, selfStreamId = "b"))
    }

    @Test fun `self stream being the only row yields an empty list`() {
        assertEquals(emptyList<LiveNowRow>(), filterOutSelfStream(listOf(row("solo")), selfStreamId = "solo"))
    }

    @Test fun `a self stream id that matches nothing is a no-op`() {
        val rows = listOf(row("a"), row("b"))
        assertEquals(rows, filterOutSelfStream(rows, selfStreamId = "not-in-the-list"))
    }

    @Test fun `empty input stays empty regardless of self stream id`() {
        assertEquals(emptyList<LiveNowRow>(), filterOutSelfStream(emptyList(), selfStreamId = "anything"))
    }
}
