// Nuru Live — viewer-surface DTOs (L2). Wire shapes copied verbatim from the
// deployed backend (packages/backend/src/modules/live/{index.ts,service.ts}):
// GET /live/now → { data: LiveNowRow[] }, GET /live/recordings → { data:
// LiveRecordingRow[] } (Envelope<T> from PathwayDtos.kt covers both). Fields
// are nullable/defaulted (forward-tolerant) matching this file's siblings.
// `hls_url` / `recording_url` are RELATIVE paths — resolve against the API's
// origin (NOT the /v1 base) via ApiClient.resolveMediaUrl before use.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

@Serializable
data class LiveNowRow(
    val streamId: String,
    val scope: String = "church",       // "church" | "cell"
    val cellId: String? = null,
    val title: String = "",
    val kind: String = "video",         // "video" | "audio"
    val startedAt: String = "",
    val hlsUrl: String? = null,         // relative — resolve before playing
    val startedByName: String? = null,
    val viewerCount: Int = 0,
) {
    val isAudio: Boolean get() = kind == "audio"
}

@Serializable
data class LiveRecordingRow(
    val streamId: String,
    val scope: String = "church",       // "church" | "cell"
    val cellId: String? = null,
    val title: String = "",
    val kind: String = "video",         // "video" | "audio"
    val startedAt: String = "",
    val endedAt: String = "",
    val recordingUrl: String? = null,   // relative — resolve before playing
) {
    val isAudio: Boolean get() = kind == "audio"
}

// ── Nuru Live — broadcaster DTOs (L3) ──────────────────────────────────────

/** POST /live/streams request body. `cellId` is required iff scope=cell,
 *  omitted (null) iff scope=church — never send an empty string. */
@Serializable
data class CreateLiveStreamBody(
    val scope: String,          // "church" | "cell"
    val cellId: String? = null,
    val title: String,
    val kind: String,           // "video" | "audio"
)

/** POST /live/streams (201) response — the one-time mint of a publish key.
 *  `rtmpUrl` is "{LIVE_RTMP_BASE_URL}/{path}" with NO stream key or query
 *  params baked in; the client appends `?user={streamId}&pass={streamKey}`
 *  itself before handing the URL to the RTMP encoder (see
 *  LiveBroadcastScreen.kt's startStream() call site for the exact
 *  construction + why, including a verified RootEncoder/MediaMTX quirk). */
@Serializable
data class CreatedLiveStream(
    val streamId: String,
    val rtmpUrl: String,
    val streamKey: String,
    val path: String,           // "church" | "cell/{cellId}" — informational
)

/** POST /live/streams/{id}/end response. Idempotent — replays are no-ops. */
@Serializable
data class EndedLiveStream(
    val streamId: String,
    val status: String = "ended",
    val endedAt: String? = null,
)
