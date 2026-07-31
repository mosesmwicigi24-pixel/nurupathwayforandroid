package org.nuruplace.member.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.LiveGuestRow

/** Pure-logic coverage for the L5/L6 broadcaster surfaces (docs/
 *  LIVE_INTERACTIVE.md): the RootEncoder rotation→cameraOrientation formula
 *  behind the orientation-follow fix (LiveBroadcastScreen.kt), and the
 *  "still in play" guest-status matcher behind the hands/guests sheet
 *  (LiveBroadcastInteractions.kt) — kept as plain functions specifically so
 *  they're unit-testable without a Compose/Robolectric harness, matching
 *  this codebase's existing Live test coverage posture. */
class LiveBroadcastInteractionsTest {
    // ── cameraOrientationFor — StreamBase.prepareVideo's own internal
    // formula, verified against the pinned RootEncoder 2.5.9 source. ──

    @Test fun `landscape rotation maps to 270`() {
        assertEquals(270, cameraOrientationFor(0))
    }

    @Test fun `portrait rotation maps to 0`() {
        assertEquals(0, cameraOrientationFor(90))
    }

    @Test fun `reverse-landscape rotation maps to 90`() {
        assertEquals(90, cameraOrientationFor(180))
    }

    @Test fun `reverse-portrait rotation maps to 180`() {
        assertEquals(180, cameraOrientationFor(270))
    }

    // ── LiveGuestRow.isActive ──

    @Test fun `invited and accepted guests are active`() {
        assertTrue(LiveGuestRow(status = "invited").isActive)
        assertTrue(LiveGuestRow(status = "accepted").isActive)
    }

    @Test fun `declined removed and ended guests are not active`() {
        assertFalse(LiveGuestRow(status = "declined").isActive)
        assertFalse(LiveGuestRow(status = "removed").isActive)
        assertFalse(LiveGuestRow(status = "ended").isActive)
    }

    // ── MAX_GUESTS — the pinned L6 cap ──

    @Test fun `guest cap is six`() {
        assertEquals(6, MAX_GUESTS)
    }

    // ── buildPublishUrl (Broadcast Studio, LiveBroadcastEngine.kt) — the
    // '?' must survive as %3F so RootEncoder's UrlParser treats the whole
    // "path?user=..&pass=.." as one opaque appName. See the function's own
    // header comment for the full MediaMTX/UrlParser trace. ──

    @Test fun `publish url percent-encodes the query delimiter`() {
        assertEquals(
            "rtmp://host/church%3Fuser=stream-1&pass=abc123",
            buildPublishUrl("rtmp://host/church", "stream-1", "abc123"),
        )
    }

    @Test fun `publish url preserves a nested scope path untouched`() {
        assertEquals(
            "rtmp://host/cell/42%3Fuser=stream-1&pass=abc123",
            buildPublishUrl("rtmp://host/cell/42", "stream-1", "abc123"),
        )
    }
}
