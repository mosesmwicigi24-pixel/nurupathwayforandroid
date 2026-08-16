package org.nuruplace.member.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Test

/** A tapped live_stream_started push (packages/backend/src/modules/live/
 *  service.ts's `notifyStreamStarted`) must land IN THE PLAYER, not just Home.
 *  The push payload alone (stream_id/scope/cell_id/title) can't build
 *  LiveRoutes.kt's live-player route (no kind/viewers/startedAt), so
 *  [NuruMessagingService.destFor] maps it to the lightweight "live-now"
 *  MainShell destination instead, which re-fetches GET /live/now and forwards
 *  to the newest watchable stream (or Home if it already ended). */
class NuruMessagingServiceLiveDestForTest {

    @Test fun `live_stream_started routes to the live-now forwarder`() {
        assertEquals(
            "live-now",
            NuruMessagingService.destFor(mapOf("template" to "live_stream_started", "streamId" to "s-1")),
        )
    }

    @Test fun `any template containing live routes the same way`() {
        assertEquals("live-now", NuruMessagingService.destFor(mapOf("template" to "LIVE_STREAM_STARTED")))
    }
}
