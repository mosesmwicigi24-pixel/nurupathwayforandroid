// Nuru Live — WhepSubscriber's retry-policy logic (2026-07-31 fix), pulled
// out as pure, coroutine-free functions specifically so it's unit-testable
// without a network stack or a real WebRTC engine (per the fix brief: "unit-
// test the pure retry-policy logic... do not test real network").
//
// PROVEN FROM PRODUCTION MediaMTX LOGS (owner testing S24-as-host +
// iPhone-as-guest): the host's one-shot WHEP subscribe fired the INSTANT a
// guest was marked accepted, but the guest's WHIP publish only began
// seconds later (permission prompt, camera warm-up, ICE) — MediaMTX answered
// "no stream is available" (HTTP 404) and the host gave up permanently,
// leaving a dead tile with the guest's own name on it. A guest can also stop
// and restart publishing (MediaMTX logs: "closed: terminated" then a new
// publish moments later) and the host must recover from that too. WhepSubscriber
// (the caller of this file) turns the one-shot attempt into a bounded,
// backed-off retry loop using the classification/schedule below.
package org.nuruplace.member.feature.live

import java.io.IOException

/** How a single WHEP subscribe attempt failed, from the retry loop's point
 *  of view — NOT from the raw exception's, since the same underlying cause
 *  (e.g. an [IOException]) can mean different things depending on when in
 *  the flow it happened. */
sealed class WhepFailure {
    /** The path isn't there yet (MediaMTX "no stream is available" / HTTP
     *  404) or a plain network hiccup (timeout, connection reset) — entirely
     *  expected while the guest's own WHIP publish is still warming up. Keep
     *  retrying on backoff. */
    data object NotYetAvailable : WhepFailure()

    /** The peer connection was established (WHEP subscribe succeeded) but no
     *  media ever arrived — MediaMTX's own "deadline exceeded while waiting
     *  tracks". The connection is half-dead, not merely slow; the caller
     *  must tear it down before retrying, never leave it hanging. */
    data object TracksTimedOut : WhepFailure()

    /** Nothing a retry can fix — bad credentials (401/403), a malformed
     *  offer/answer, a local WebRTC engine failure. Surface immediately
     *  instead of burning the retry window on something that will never
     *  succeed. */
    data class Terminal(val reason: String) : WhepFailure()
}

/** Whether the retry loop should keep going after this failure — the ONLY
 *  thing that stops it besides this is the window expiring (see
 *  [WhepRetryPolicy.hasWindowExpired]). */
val WhepFailure.isRetryable: Boolean get() = this !is WhepFailure.Terminal

/** A human-readable reason, used once a failure actually gets surfaced to
 *  the UI (either because it was terminal, or because the retry window
 *  expired while it kept recurring). */
val WhepFailure.reason: String
    get() = when (this) {
        WhepFailure.NotYetAvailable -> "Couldn't connect to the stage."
        WhepFailure.TracksTimedOut -> "Couldn't connect to the stage."
        is WhepFailure.Terminal -> this.reason
    }

/** MediaMTX's own phrase for a peer connection that was established but
 *  never received media — WhepSubscriber throws this itself (mirroring the
 *  server-side wording) once its own track-arrival deadline elapses, so
 *  [classifyWhepThrowable] can recognize it deterministically by type rather
 *  than sniffing a message string. */
class WhepTracksTimeoutException : Exception("deadline exceeded while waiting tracks")

/** HTTP status -> [WhepFailure] for a failed WHIP/WHEP offer POST
 *  ([LiveWebRtc.WhipWhepHttpException]). 404 is the PROVEN "not yet" signal
 *  from production logs; 5xx is treated the same way (MediaMTX/its proxy
 *  having a transient moment shouldn't be fatal either). 401/403 mean the
 *  credentials are simply wrong — retrying changes nothing. Anything else
 *  unrecognized is treated as terminal too: a genuinely malformed request
 *  the client sent, not a timing race. */
fun classifyWhepHttpStatus(code: Int): WhepFailure = when {
    code == 404 -> WhepFailure.NotYetAvailable
    code in 500..599 -> WhepFailure.NotYetAvailable
    code == 401 || code == 403 -> WhepFailure.Terminal("Not authorized to join this stage (HTTP $code).")
    else -> WhepFailure.Terminal("WHIP/WHEP POST failed: HTTP $code")
}

/** Classifies whatever a single WHEP subscribe attempt threw. Order matters:
 *  the dedicated [WhepTracksTimeoutException] and
 *  [LiveWebRtc.WhipWhepHttpException] types are checked first (precise,
 *  no string-sniffing); a bare [IOException] (connect/read timeout,
 *  connection refused, DNS hiccup — anything OkHttp/WebRTC's own suspend
 *  wrappers can throw before a response is even seen) is treated as
 *  transient/retryable, same reasoning as a 5xx; everything else is
 *  terminal — an unexpected local failure (e.g. `createPeerConnection`
 *  returned null) that a retry is very unlikely to fix. */
fun classifyWhepThrowable(e: Throwable): WhepFailure = when {
    e is WhepTracksTimeoutException -> WhepFailure.TracksTimedOut
    e is LiveWebRtc.WhipWhepHttpException -> classifyWhepHttpStatus(e.code)
    e is IOException -> WhepFailure.NotYetAvailable
    else -> WhepFailure.Terminal(e.message ?: (e::class.simpleName ?: "Couldn't connect to the stage."))
}

/** Pure backoff-schedule + retry-window math. No coroutines, no wall-clock
 *  reads — WhepSubscriber owns the actual `delay()`/`System.currentTimeMillis()`
 *  plumbing around these functions, which is what keeps this file testable
 *  as plain data-in, data-out. */
object WhepRetryPolicy {
    const val INITIAL_DELAY_MS: Long = 500
    const val MAX_DELAY_MS: Long = 8_000

    /** Generous — guests on slow networks (permission prompt, camera
     *  warm-up, ICE, all on a mobile connection) are the NORMAL case here,
     *  not the exception; per the fix brief this window must comfortably
     *  outlast the ~32s worst case actually observed in production logs. */
    const val DEFAULT_WINDOW_MS: Long = 45_000

    /** How long tolerated between the FIRST attempt and either giving up
     *  (window expired) or waiting for a subscribed-but-silent connection to
     *  either receive media or officially time out. */
    const val TRACK_WAIT_TIMEOUT_MS: Long = 12_000

    /** Exponential backoff, doubling from [initialMs] and capped at [capMs]
     *  (~0.5s -> 1s -> 2s -> 4s -> 8s -> 8s -> ...). [attempt] is the 0-based
     *  count of retries already made — attempt 0 is the delay before the
     *  FIRST retry (i.e. right after the very first, immediate attempt
     *  failed). Negative attempts (shouldn't happen, but a defensive floor
     *  costs nothing) clamp to the initial delay. */
    fun backoffDelayMs(attempt: Int, initialMs: Long = INITIAL_DELAY_MS, capMs: Long = MAX_DELAY_MS): Long {
        if (attempt <= 0) return initialMs.coerceAtMost(capMs)
        // Cap the shift itself (not just the result) so a guest stuck
        // retrying for the entire window can never risk a Long overflow —
        // once the shift alone would already blow past the cap, skip
        // straight to it rather than computing `initialMs shl shift`.
        val shift = attempt.coerceAtMost(20)
        val doubled = initialMs shl shift
        return if (doubled <= 0 || doubled > capMs) capMs else doubled
    }

    /** True once [elapsedMs] since the FIRST attempt of the current connect
     *  cycle has consumed the whole [windowMs] retry budget — the point at
     *  which the caller stops retrying and surfaces a real error. A fresh
     *  guest republish (recovered automatically, see WhepSubscriber.kt's
     *  "Recover from republish" doc) starts an entirely new cycle with its
     *  own fresh window, never inherits the old one. */
    fun hasWindowExpired(elapsedMs: Long, windowMs: Long = DEFAULT_WINDOW_MS): Boolean =
        elapsedMs >= windowMs
}
