// Session cache for GET /chat/pastoral/eligibility (Chat Redesign C4): "have I
// ever been assigned as a pastor" — probed once per app session and kept in
// memory only (never persisted — a resolved "yes" for a since-reassigned
// pastor must not survive a relaunch, and a signed-out/other member on this
// device must never inherit it). Reset alongside the rest of the pastoral
// device-local state on sign-out (see AuthStore.signOut).
//
// Closes the gap PARITY_AUDIT.md flagged on 2026-07-18: "the server's real
// gate is step-up + FORBIDDEN_SCOPE for non-pastors; an assigned
// non-SuperAdmin pastor has no client-visible signal to key on" — this route
// is exactly that side-effect-free signal.
package org.nuruplace.member.data

import kotlinx.coroutines.CompletableDeferred
import org.nuruplace.member.data.net.Net

object PastorEligibility {
    private var cached: Boolean? = null
    private var inFlight: CompletableDeferred<Boolean>? = null

    /** De-duped: concurrent callers during the first probe share one request. */
    suspend fun isPastor(): Boolean {
        cached?.let { return it }
        inFlight?.let { return it.await() }
        val deferred = CompletableDeferred<Boolean>()
        inFlight = deferred
        val result = runCatching { Net.client.api.pastoralEligibility().isPastor }.getOrDefault(false)
        cached = result
        inFlight = null
        deferred.complete(result)
        return result
    }

    fun reset() {
        cached = null
        inFlight = null
    }
}
