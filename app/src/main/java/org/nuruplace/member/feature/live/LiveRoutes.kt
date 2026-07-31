// Nuru Live — nav-route builders (L2, viewer-only). The full-screen player is
// one destination fed entirely through query args (there's no per-stream GET,
// so the caller — Home's banner, the cell card, or the Replays list — hands
// over everything the player needs). `hls_url` / `recording_url` are RELATIVE
// on the wire; resolve them against the API's media origin here, once, so
// every call site (Home, CellInfoScreen, Replays) shares the same logic.
package org.nuruplace.member.feature.live

import android.net.Uri
import org.nuruplace.member.data.net.CreatedLiveStream
import org.nuruplace.member.data.net.LiveNowRow
import org.nuruplace.member.data.net.LiveRecordingRow
import org.nuruplace.member.data.net.Net

/** The nav route for a currently-live stream (heartbeat + end-of-stream
 *  polling stay on while this is open — see LivePlayerScreen). `fallbackUrl`
 *  rides along when the server sent one (church scope + CDN configured) — the
 *  player prefers it for the first seconds of playback; see LivePlayerScreen's
 *  header comment for why (the flicker-to-previous-broadcast fix). */
fun liveNowRoute(row: LiveNowRow): String {
    val url = Net.client.resolveMediaUrl(row.hlsUrl.orEmpty())
    val fallback = row.hlsFallbackUrl?.let { Net.client.resolveMediaUrl(it) }
    return "live-player?streamId=${Uri.encode(row.streamId)}&url=${Uri.encode(url)}" +
        "&fallbackUrl=${Uri.encode(fallback ?: "")}" +
        "&title=${Uri.encode(row.title)}&kind=${Uri.encode(row.kind)}&live=true" +
        "&startedAt=${Uri.encode(row.startedAt)}&viewers=${row.viewerCount}" +
        "&startedByName=${Uri.encode(row.startedByName.orEmpty())}"
}

/** The nav route for a hosted recording (Replays list) — no heartbeat, no
 *  live badge; the player just plays the VOD and shows the ended state once
 *  it naturally finishes. */
fun liveRecordingRoute(row: LiveRecordingRow): String {
    val url = Net.client.resolveMediaUrl(row.recordingUrl.orEmpty())
    return "live-player?streamId=${Uri.encode("")}&url=${Uri.encode(url)}" +
        "&title=${Uri.encode(row.title)}&kind=${Uri.encode(row.kind)}&live=false" +
        "&startedAt=${Uri.encode(row.startedAt)}&viewers=0"
}

/** The nav route into the broadcaster's own screen (L3), fed by the setup
 *  sheet's just-minted CreatedLiveStream. `rtmpUrl`/`streamKey` travel raw —
 *  LiveBroadcastScreen.kt builds the actual publish URL itself (see its
 *  header comment for exactly why, and what it found verifying against
 *  RootEncoder's + MediaMTX's real source). */
fun liveBroadcastRoute(created: CreatedLiveStream, title: String, kind: String): String =
    "live-broadcast?streamId=${Uri.encode(created.streamId)}" +
        "&rtmpUrl=${Uri.encode(created.rtmpUrl)}&streamKey=${Uri.encode(created.streamKey)}" +
        "&title=${Uri.encode(title)}&kind=${Uri.encode(kind)}"
