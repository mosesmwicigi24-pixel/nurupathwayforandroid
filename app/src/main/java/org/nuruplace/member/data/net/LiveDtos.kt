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
    val hlsUrl: String? = null,         // relative OR absolute (CDN) — resolve before playing
    // L1.5 — church-scope only, present iff LIVE_CDN_BASE is configured server-side.
    // The direct, relative, low-latency origin URL. See LivePlayerScreen's header
    // comment for why the viewer prefers THIS for the first seconds of playback
    // (the flicker-to-previous-broadcast root cause + client mitigation).
    val hlsFallbackUrl: String? = null,
    val startedByName: String? = null,
    // Forward-tolerant, speculative: the taste-pass viewer chrome (LivePlayerScreen's
    // BroadcasterIdentityChip) prefers a real avatar over gold initials the moment the
    // server starts sending one — defaults to null and falls back gracefully today,
    // same idiom as InitialsAvatar (LiveBroadcastInteractions.kt) for hands/guests.
    val startedByAvatarUrl: String? = null,
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

// ── Nuru Live — L5 interactions (docs/LIVE_INTERACTIVE.md) ─────────────────
// Wire shapes copied verbatim from packages/backend/src/modules/live/service.ts.
// `emoji` is a free string (not a closed enum) on purpose: the backend is being
// widened in parallel to accept "fire" alongside "like"/"love" — a fixed
// enum/two-field class would silently drop it. `reactions` is likewise a bare
// Map<String, Int> rather than {like, love} fields for the same forward-
// tolerance reason (kotlinx.serialization decodes any object shape into it).

@Serializable
data class LiveReactionBody(val emoji: String)

@Serializable
data class LiveHandBody(val raised: Boolean)

@Serializable
data class LiveMessageRow(
    val messageId: String = "",
    val userId: String = "",
    val fullName: String = "",
    val avatarUrl: String? = null,
    val body: String = "",
    val sentAt: String = "",
)

@Serializable
data class LiveMessagesRes(val messages: List<LiveMessageRow> = emptyList())

@Serializable
data class LiveSendMessageBody(val body: String)

@Serializable
data class LiveRecentReaction(val emoji: String = "like", val at: String = "")

@Serializable
data class LiveGuestRow(
    val userId: String = "",
    val fullName: String = "",
    val avatarUrl: String? = null,
    val status: String = "invited", // invited | accepted | declined | removed | ended
    // L6b (docs/LIVE_INTERACTIVE.md) — ADDITIVE, owner-only: the WHEP URL the
    // broadcaster's own device subscribes to for this guest's live WebRTC
    // video/audio (service.ts's LiveGuestRow.whep_url). Absent for every
    // caller other than the stream's own owner.
    val whepUrl: String? = null,
)

/** GET /live/streams/{id}/guests/me/ingest (L6b) — the one-time mint of a
 *  guest's own WHIP publish credentials. `whipUrl` is the bare MediaMTX WHIP
 *  endpoint with NO query params baked in; the client appends
 *  `?user={myUserId}&pass={token}` itself (mirrors the RTMP publish URL
 *  construction in LiveBroadcastEngine.kt's buildPublishUrl). */
@Serializable
data class LiveGuestIngest(
    val whipUrl: String = "",
    val token: String = "",
    val path: String = "",
)

@Serializable
data class LiveHandRow(
    val userId: String = "",
    val fullName: String = "",
    val avatarUrl: String? = null,
    val raisedAt: String = "",
)

/** GET /live/streams/{id}/pulse — one poll for the whole overlay (broadcaster
 *  3s, viewer 5s per the wire contract). */
@Serializable
data class LivePulse(
    val viewerCount: Int = 0,
    val reactions: Map<String, Int> = emptyMap(),
    val recentReactions: List<LiveRecentReaction> = emptyList(),
    val hands: List<LiveHandRow> = emptyList(),
    val guests: List<LiveGuestRow> = emptyList(),
)

@Serializable
data class LiveGuestRespondBody(val accept: Boolean)
