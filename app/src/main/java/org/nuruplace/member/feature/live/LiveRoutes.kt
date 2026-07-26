// Nuru Live — nav-route builders (L2, viewer-only). The full-screen player is
// one destination fed entirely through query args (there's no per-stream GET,
// so the caller — Home's banner, the cell card, or the Replays list — hands
// over everything the player needs). `hls_url` / `recording_url` are RELATIVE
// on the wire; resolve them against the API's media origin here, once, so
// every call site (Home, CellInfoScreen, Replays) shares the same logic.
package org.nuruplace.member.feature.live

import android.net.Uri
import org.nuruplace.member.data.net.LiveNowRow
import org.nuruplace.member.data.net.LiveRecordingRow
import org.nuruplace.member.data.net.Net

/** The nav route for a currently-live stream (heartbeat + end-of-stream
 *  polling stay on while this is open — see LivePlayerScreen). */
fun liveNowRoute(row: LiveNowRow): String {
    val url = Net.client.resolveMediaUrl(row.hlsUrl.orEmpty())
    return "live-player?streamId=${Uri.encode(row.streamId)}&url=${Uri.encode(url)}" +
        "&title=${Uri.encode(row.title)}&kind=${Uri.encode(row.kind)}&live=true" +
        "&startedAt=${Uri.encode(row.startedAt)}&viewers=${row.viewerCount}"
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
