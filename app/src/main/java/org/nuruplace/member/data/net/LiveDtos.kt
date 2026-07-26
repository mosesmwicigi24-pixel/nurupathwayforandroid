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
