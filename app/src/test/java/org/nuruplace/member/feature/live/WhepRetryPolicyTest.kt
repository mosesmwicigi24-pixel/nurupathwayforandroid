package org.nuruplace.member.feature.live

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the WHEP subscribe retry policy (2026-07-31 fix —
 *  see WhepRetryPolicy.kt's header for the production-proven root cause).
 *  Deliberately no coroutines/real network here, per the fix brief — the
 *  backoff schedule, failure classification, and window-expiry math are all
 *  plain functions specifically so they're testable this way; WhepSubscriber
 *  itself (the actual retry loop + cancellation wiring) is exercised
 *  manually against the real MediaMTX server, not here. */
class WhepRetryPolicyTest {
    // ── backoffDelayMs — exponential, capped ────────────────────────────────

    @Test fun `the first retry uses the initial delay`() {
        assertEquals(500L, WhepRetryPolicy.backoffDelayMs(0))
    }

    @Test fun `a negative attempt never produces a shorter-than-initial delay`() {
        assertEquals(500L, WhepRetryPolicy.backoffDelayMs(-1))
    }

    @Test fun `delay doubles with each subsequent attempt`() {
        assertEquals(500L, WhepRetryPolicy.backoffDelayMs(0))
        assertEquals(1_000L, WhepRetryPolicy.backoffDelayMs(1))
        assertEquals(2_000L, WhepRetryPolicy.backoffDelayMs(2))
        assertEquals(4_000L, WhepRetryPolicy.backoffDelayMs(3))
    }

    @Test fun `delay caps at 8 seconds and never exceeds it`() {
        assertEquals(8_000L, WhepRetryPolicy.backoffDelayMs(4))
        assertEquals(8_000L, WhepRetryPolicy.backoffDelayMs(5))
        assertEquals(8_000L, WhepRetryPolicy.backoffDelayMs(100))
    }

    @Test fun `an enormous attempt count never overflows into a negative or zero delay`() {
        val delay = WhepRetryPolicy.backoffDelayMs(Int.MAX_VALUE)
        assertTrue(delay > 0)
        assertEquals(WhepRetryPolicy.MAX_DELAY_MS, delay)
    }

    @Test fun `a custom initial and cap are respected`() {
        assertEquals(100L, WhepRetryPolicy.backoffDelayMs(0, initialMs = 100, capMs = 3_000))
        assertEquals(200L, WhepRetryPolicy.backoffDelayMs(1, initialMs = 100, capMs = 3_000))
        assertEquals(3_000L, WhepRetryPolicy.backoffDelayMs(10, initialMs = 100, capMs = 3_000))
    }

    // ── hasWindowExpired ─────────────────────────────────────────────────────

    @Test fun `the window has not expired before its budget is consumed`() {
        assertFalse(WhepRetryPolicy.hasWindowExpired(0))
        assertFalse(WhepRetryPolicy.hasWindowExpired(WhepRetryPolicy.DEFAULT_WINDOW_MS - 1))
    }

    @Test fun `the window expires exactly at and beyond its budget`() {
        assertTrue(WhepRetryPolicy.hasWindowExpired(WhepRetryPolicy.DEFAULT_WINDOW_MS))
        assertTrue(WhepRetryPolicy.hasWindowExpired(WhepRetryPolicy.DEFAULT_WINDOW_MS + 5_000))
    }

    @Test fun `a custom window is respected`() {
        assertFalse(WhepRetryPolicy.hasWindowExpired(999, windowMs = 1_000))
        assertTrue(WhepRetryPolicy.hasWindowExpired(1_000, windowMs = 1_000))
    }

    // ── classifyWhepHttpStatus — the proven "no stream is available" (404)
    // case, plus the neighbors it must NOT be confused with ────────────────

    @Test fun `404 no-stream-yet is retryable`() {
        val failure = classifyWhepHttpStatus(404)
        assertEquals(WhepFailure.NotYetAvailable, failure)
        assertTrue(failure.isRetryable)
    }

    @Test fun `5xx server errors are treated as retryable too`() {
        assertTrue(classifyWhepHttpStatus(500).isRetryable)
        assertTrue(classifyWhepHttpStatus(503).isRetryable)
        assertTrue(classifyWhepHttpStatus(599).isRetryable)
    }

    @Test fun `401 and 403 are terminal — retrying bad credentials never helps`() {
        assertFalse(classifyWhepHttpStatus(401).isRetryable)
        assertFalse(classifyWhepHttpStatus(403).isRetryable)
        assertTrue(classifyWhepHttpStatus(401) is WhepFailure.Terminal)
    }

    @Test fun `an unrecognized status code is terminal, not silently retried forever`() {
        assertFalse(classifyWhepHttpStatus(418).isRetryable)
    }

    // ── classifyWhepThrowable ────────────────────────────────────────────────

    @Test fun `the dedicated tracks-timeout exception classifies as TracksTimedOut and is retryable`() {
        val failure = classifyWhepThrowable(WhepTracksTimeoutException())
        assertEquals(WhepFailure.TracksTimedOut, failure)
        assertTrue(failure.isRetryable)
    }

    @Test fun `a WhipWhepHttpException classifies by its carried status code`() {
        assertEquals(WhepFailure.NotYetAvailable, classifyWhepThrowable(LiveWebRtc.WhipWhepHttpException(404)))
        assertTrue(classifyWhepThrowable(LiveWebRtc.WhipWhepHttpException(401)) is WhepFailure.Terminal)
    }

    @Test fun `a bare IOException (timeout, connection reset) is treated as retryable`() {
        assertEquals(WhepFailure.NotYetAvailable, classifyWhepThrowable(IOException("timeout")))
        assertEquals(WhepFailure.NotYetAvailable, classifyWhepThrowable(java.net.SocketTimeoutException()))
    }

    @Test fun `an unexpected local failure is terminal, not retried forever`() {
        val failure = classifyWhepThrowable(IllegalStateException("createPeerConnection returned null"))
        assertTrue(failure is WhepFailure.Terminal)
        assertEquals("createPeerConnection returned null", (failure as WhepFailure.Terminal).reason)
    }

    @Test fun `a terminal failure with no message still gets a usable, non-blank reason`() {
        val failure = classifyWhepThrowable(IllegalStateException())
        assertTrue(failure is WhepFailure.Terminal)
        assertTrue((failure as WhepFailure.Terminal).reason.isNotBlank())
    }

    // ── isRetryable / reason — the closed set is exactly {NotYetAvailable,
    // TracksTimedOut} retryable, {Terminal} not, with no silent fallthrough ──

    @Test fun `exactly the two non-terminal cases are retryable`() {
        assertTrue(WhepFailure.NotYetAvailable.isRetryable)
        assertTrue(WhepFailure.TracksTimedOut.isRetryable)
        assertFalse(WhepFailure.Terminal("x").isRetryable)
    }

    @Test fun `reason is always a non-blank, user-presentable string`() {
        assertTrue(WhepFailure.NotYetAvailable.reason.isNotBlank())
        assertTrue(WhepFailure.TracksTimedOut.reason.isNotBlank())
        assertEquals("custom reason", WhepFailure.Terminal("custom reason").reason)
    }
}
