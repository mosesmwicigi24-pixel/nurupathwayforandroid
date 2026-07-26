// Nuru Live discovery — "invite loudly, never hijack" (owner-approved design).
// The ONE app-wide source of truth for "what's watchable right now", feeding:
//   1. A tapped live_stream_started push — MainShell's "live-now" route
//      re-checks GET /live/now and forwards to the newest watchable stream
//      (falling back to Home, which shows its own banner, if it already
//      ended by the time the tap lands).
//   2. Home's mini-window pop-up for a stream this session hasn't seen yet.
//   3. The app-wide LIVE bar shown on every screen but Home while the player
//      isn't already open.
// Never gates or originates anything — GET /live/now stays the ONE
// server-authoritative call; this object only remembers, for this process,
// which stream_ids have already been surfaced so they don't re-interrupt.
package org.nuruplace.member.feature.live

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.nuruplace.member.data.net.LiveNowRow
import org.nuruplace.member.data.net.Net

object LiveDiscoveryCenter {
    private val _streams = MutableStateFlow<List<LiveNowRow>>(emptyList())
    val streams: StateFlow<List<LiveNowRow>> = _streams.asStateFlow()

    /** Non-null exactly while Home's mini-window should be showing — the
     *  stream_id it's for. One popup at a time; cleared by [dismissPopup] or
     *  [markSeen] and never re-set for that same stream_id afterward. */
    private val _popupStreamId = MutableStateFlow<String?>(null)
    val popupStreamId: StateFlow<String?> = _popupStreamId.asStateFlow()

    /** stream_ids already surfaced this app process — never popped up twice. */
    private val seen = mutableSetOf<String>()

    /** The newest stream the member may watch, if any (server already orders
     *  `/live/now` newest-first) — what the app-wide bar names and what a
     *  live_stream_started notification tap opens. */
    val newestWatchable: LiveNowRow? get() = _streams.value.firstOrNull()

    /** Re-fetch GET /live/now and fold the result in. Best-effort: a failed
     *  fetch leaves the previous rows in place rather than clearing them. */
    suspend fun refresh() {
        val rows = runCatching { Net.client.api.getLiveNow().data }.getOrNull() ?: return
        ingest(rows)
    }

    /** Fold a fresh `/live/now` result (however it was fetched — Home's own
     *  poll piggybacks this too, so there is only ever one notion of "current
     *  streams") into shared state, and surface the mini-window for the
     *  first row this session hasn't already seen. */
    fun ingest(rows: List<LiveNowRow>) {
        _streams.value = rows
        if (_popupStreamId.value != null) return   // one mini-window at a time
        rows.firstOrNull { it.streamId !in seen }?.let { _popupStreamId.value = it.streamId }
    }

    /** X dismiss on the mini-window — collapses to the ordinary LIVE banner
     *  card; this stream_id never pops up again this session. */
    fun dismissPopup(streamId: String) {
        seen += streamId
        if (_popupStreamId.value == streamId) _popupStreamId.value = null
    }

    /** Any path that hands the member straight into the player (mini-window
     *  "Join live", the app-wide bar, a routed notification tap) also counts
     *  as "seen" — no popup left waiting when they come back to Home. */
    fun markSeen(streamId: String) {
        seen += streamId
        if (_popupStreamId.value == streamId) _popupStreamId.value = null
    }
}
