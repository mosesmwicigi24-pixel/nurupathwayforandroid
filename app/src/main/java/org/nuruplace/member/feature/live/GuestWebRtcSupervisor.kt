// Nuru Live — resilience layer, owner directive (2026-07-31 host-stability
// investigation): "the broadcast must SURVIVE any guest-subsystem failure."
// WhepSubscribers live in LiveBroadcastScreen's Compose state (screen-scoped
// — see that file's L6b comment), while the RTMP publish itself lives in
// LiveBroadcastService (survives navigation — see that file's header). This
// process-wide registry is the bridge between the two: the Screen registers
// every WhepSubscriber it creates here as it creates it, and the Service's
// ConnectChecker/watchdog (LiveBroadcastService.handleDrop) can reach in and
// force-disconnect every live guest WITHOUT needing a reference to the
// Screen's own Compose state, the moment the publish drops.
//
// This is deliberately a LAST-RESORT breaker, not the primary defense — the
// primary defense is WhepSubscriber's own start()/stop() mutex (see its file
// header) making a guest's own teardown race-free in the first place. This
// registry exists for the case a publish drop has some OTHER cause entirely
// (network blip, MediaMTX hiccup) and guests should be shed anyway so a
// reconnect attempt starts from a clean slate rather than immediately
// re-fighting whatever guest state was live when the drop happened.
package org.nuruplace.member.feature.live

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "GuestWebRtcSupervisor"

object GuestWebRtcSupervisor {
    private val active = ConcurrentHashMap<String, WhepSubscriber>()

    val hasActiveGuests: Boolean get() = active.isNotEmpty()

    fun register(guestId: String, subscriber: WhepSubscriber) {
        active[guestId] = subscriber
    }

    /** Called by LiveBroadcastScreen once a subscriber's own stop() has run
     *  through the normal (non-watchdog) path — keeps this registry from
     *  accumulating stale entries for guests that left normally. */
    fun unregister(guestId: String) {
        active.remove(guestId)
    }

    /** Emergency teardown — called ONLY by LiveBroadcastService.handleDrop
     *  when the RTMP publish drops while guests are attached. Best-effort,
     *  fire-and-forget per subscriber (each one's own stop() already isolates
     *  its own failures internally — see WhepSubscriber's mutex/runCatching
     *  discipline) so one stuck guest teardown can never block the others or
     *  the reconnect attempt that follows this call. */
    fun disconnectAllForWatchdog(scope: CoroutineScope) {
        val snapshot = active.toMap()
        active.clear()
        if (snapshot.isEmpty()) return
        Log.w(TAG, "watchdog: publish dropped with ${snapshot.size} guest(s) attached — tearing down guest WebRTC before retrying the broadcast")
        snapshot.forEach { (guestId, sub) ->
            scope.launch {
                runCatching { sub.stop() }
                    .onFailure { e -> Log.w(TAG, "watchdog: guest=$guestId stop() failed (non-fatal, continuing)", e) }
            }
        }
        // Belt-and-braces reset — mirrors LiveBroadcastService.cleanupEngine's
        // own "no stale guest bleeds into the next state" discipline, in case
        // any of the snapshot's stop() calls above don't fully land before
        // the retry/reconnect that triggered this proceeds.
        GuestAudioMixer.reset()
        GuestStageCompositor.reset()
    }
}
