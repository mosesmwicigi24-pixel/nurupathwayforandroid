// Community + Chat DTOs — Kotlin mirror of the prayer-wall and chat contracts
// (ported from the iOS Models/Community.swift + Chat.swift).
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

// --- Prayer wall (public, opt-in) ---
@Serializable
data class PrayerReaction(val emoji: String = "", val count: Int = 0, val mine: Boolean = false)

@Serializable
data class PrayerWallPost(
    val postId: String,
    val authorUserId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val title: String? = null,
    val body: String = "",
    val audioUrl: String? = null,
    val audioWaveform: List<Int>? = null,   // ints 0..100, ≤80 bars (server zod cap)
    val isAnswered: Boolean = false,
    val createdAt: String = "",
    val mine: Boolean = false,
    val prayCount: Int = 0,
    val iPrayed: Boolean = false,
    val commentCount: Int? = null,
    val reactions: List<PrayerReaction> = emptyList(),
)

@Serializable
data class PrayerWallComment(
    val commentId: String,
    val authorUserId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val body: String = "",
    val audioUrl: String? = null,
    val audioWaveform: List<Int>? = null,
    val createdAt: String = "",
    val mine: Boolean = false,
)

@Serializable
data class PrayerWallDetail(
    val post: PrayerWallPost,
    val comments: List<PrayerWallComment> = emptyList(),
)

// --- Chat ---
@Serializable
data class ChatConversation(
    val conversationId: String,
    val kind: String = "space",        // dm | group | space
    val isPublic: Boolean = false,
    val title: String? = null,
    val topic: String? = null,
    val category: String? = null,
    val memberCount: Int = 0,
    val lastBody: String? = null,
    val lastType: String? = null,
    val lastAt: String? = null,
    val lastAuthor: String? = null,
    val lastDuration: Int? = null,     // seconds — voice-note length (attachment_meta->>'duration')
    val unread: Int = 0,
    val avatarUrl: String? = null,
    val peerUserId: String? = null,
    val messageCount: Int = 0,
    val reactionCount: Int = 0,
    // `chat_conversations.type` — DIRECT/SPACE/DISCIPLER/PASTORAL/BROADCAST/
    // BROADCAST_RESPONSE (Chat Redesign C4). Server-authoritative way to tell
    // a discipler/pastoral 1:1 apart from an ordinary DM; null on an older
    // server, in which case callers fall back to the client-taught id
    // heuristic (see ChatScreen.kt's `dms`).
    val type: String? = null,
    // This caller has muted the conversation (server-side, per-member; Chat
    // Redesign C4 mute). Additive — false on a server that predates it.
    val muted: Boolean = false,
)

@Serializable
data class DiscoverSpace(
    val conversationId: String,
    val title: String? = null,
    val topic: String? = null,
    val category: String? = null,
    val memberCount: Int = 0,
)

@Serializable
data class ChatInbox(
    val conversations: List<ChatConversation> = emptyList(),
    val discoverSpaces: List<DiscoverSpace> = emptyList(),
)

@Serializable
data class ChatPerson(
    val userId: String,
    val fullName: String = "",
    val role: String? = null,
    val avatarUrl: String? = null,
    val congregation: String? = null,
    val level: Int? = null,
    val badgeCount: Int? = null,
    val badgeIcons: List<String>? = null,
    val certCount: Int? = null,
)

@Serializable
data class ChatReaction(val emoji: String = "", val count: Int = 0, val mine: Boolean = false)

@Serializable
data class ChatMessage(
    val messageId: String,
    val authorUserId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val body: String = "",
    val msgType: String = "text",       // text | voice | image | file | video
    val attachmentUrl: String? = null,
    val attachmentMeta: kotlinx.serialization.json.JsonObject? = null,  // voice: { duration, waveform[] }
    val replyBody: String? = null,
    val replyAuthor: String? = null,
    val isEdited: Boolean = false,
    val createdAt: String = "",
    val mine: Boolean = false,
    val reactions: List<ChatReaction> = emptyList(),
    val readCount: Int? = null,
    val recipientCount: Int? = null,
    val aiTag: String? = null,
    // Set when this message was delivered by a broadcast — the mark that lets a
    // member's thread dress itself as "Talk with Pastor" instead of a plain DM.
    val broadcastId: String? = null,
)

@Serializable
data class ChatThreadDetail(
    val conversationId: String,
    val kind: String = "space",
    val isPublic: Boolean = false,
    val title: String? = null,
    val topic: String? = null,
    val memberCount: Int = 0,
    val joined: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    // Same server `type` / per-member mute as ChatConversation — additive.
    val type: String? = null,
    val muted: Boolean = false,
)

// --- Request bodies / small responses ---
// audio_url / audio_waveform are .nullable().optional() in the server's create
// schema, so the explicit nulls kotlinx emits (encodeDefaults + explicitNulls)
// are accepted for plain text posts.
@Serializable
data class CreatePrayerBody(
    val postId: String,
    val title: String? = null,
    val body: String,
    val clientMutationId: String,
    val audioUrl: String? = null,
    val audioWaveform: List<Int>? = null,
)

@Serializable
data class ReactBody(val emoji: String)

@Serializable
data class ReactOn(val on: Boolean = false)

@Serializable
data class EditMessageBody(val body: String)

@Serializable
data class EditMessageRes(val messageId: String = "", val body: String = "", val isEdited: Boolean = false)

@Serializable
data class DeleteMessageRes(val messageId: String = "", val deleted: Boolean = false)

@Serializable
data class PrayerCommentBody(val commentId: String, val body: String, val clientMutationId: String)

@Serializable
data class AnsweredBody(val answered: Boolean)

@Serializable
data class SendMessageBody(val messageId: String, val body: String, val msgType: String = "text", val clientMutationId: String)

// Voice sends use their own body class: the chat send schema's attachment_url /
// attachment_meta are zod .optional() but NOT nullable, and our Json encodes
// defaults + explicit nulls — a shared class would emit "attachment_meta": null
// on every text send and be rejected. Separate shape = zero risk to text sends.
@Serializable
data class SendVoiceBody(
    val messageId: String,
    val body: String,
    val msgType: String,
    val attachmentUrl: String,
    val attachmentMeta: kotlinx.serialization.json.JsonObject,   // { duration: sec, waveform: [0..100] }
    val clientMutationId: String,
)

@Serializable
data class VoiceUploadRes(val url: String = "")

@Serializable
data class PeopleRes(val people: List<ChatPerson> = emptyList())

@Serializable
data class DmBody(val userId: String)

@Serializable
data class DmRes(val conversationId: String = "")

// --- Chat connections (Chat Redesign C1/C2 backend, C3a client) — consent-gated
// peer relationships. "No unsolicited DMs": POST /chat/dms now 403s with
// CONSENT_REQUIRED for a brand-new thread between two ordinary members unless
// an accepted `user_connections` row exists. Mirrors
// packages/backend/src/modules/chat/connections.ts's wire shapes exactly.
// Every field has a default so an unknown/older server shape never blanks the
// whole Chat tab (same tolerant-DTO convention as the rest of this file).
@Serializable
data class RequestConnectionBody(val userId: String, val message: String? = null, val clientMutationId: String)

@Serializable
data class RequestConnectionRes(val requestId: String = "", val status: String = "pending", val duplicate: Boolean = false, val mutual: Boolean = false)

/** One row of GET /chat/connections/requests?direction=incoming|outgoing. */
@Serializable
data class ConnectionRequestRow(
    val requestId: String,
    val status: String = "pending",
    val message: String? = null,
    val createdAt: String? = null,
    val userId: String = "",
    val fullName: String = "",
    val avatarUrl: String? = null,
)

@Serializable
data class ConnectionRequestsRes(val data: List<ConnectionRequestRow> = emptyList())

/** Shared response shape for accept / decline / cancel. */
@Serializable
data class ConnectionRequestDecision(val requestId: String = "", val status: String = "", val already: Boolean = false)

/** One row of GET /chat/connections — an accepted (chat-able) peer. */
@Serializable
data class ConnectionRow(
    val userId: String,
    val fullName: String = "",
    val avatarUrl: String? = null,
    val status: String = "accepted",   // accepted | blocked | removed
    val establishedAt: String? = null,
)

@Serializable
data class ConnectionsRes(val data: List<ConnectionRow> = emptyList())

/** Shared response shape for remove / block / unblock. */
@Serializable
data class ConnectionActionRes(val userId: String = "", val status: String = "")

// Staff broadcast (POST chat/broadcast, Instructor+). Server zod: body 1..20000,
// msg_type "text"|"image" (default text), attachment_url optional NOT nullable
// (so it's omitted here — same explicitNulls trap as SendVoiceBody above),
// client_mutation_id uuid. Fans out as individual DMs; replies come back 1:1.
@Serializable
data class BroadcastBody(val body: String, val msgType: String = "text", val clientMutationId: String)

@Serializable
data class BroadcastRes(
    val sent: Int = 0,
    val duplicate: Boolean = false,
    val broadcastId: String? = null,
    val recipientCount: Int = 0,
)

// Step-up (§5.3): prove the password NOW; the server re-mints the access token
// with a fresh pwd_at so broadcast routes admit it for 15 minutes.
@Serializable
data class ConfirmPasswordBody(val password: String)

@Serializable
data class ConfirmPasswordRes(val accessToken: String, val expiresIn: Int = 0, val confirmedAt: Long = 0)

// GET chat/broadcasts — the last few sent, and how many there are in all. Same
// step-up gate as the send itself: a 403 password_required here means the
// 15-minute window lapsed since the composer opened.
@Serializable
data class BroadcastSummary(
    val broadcastId: String,
    val body: String = "",
    val msgType: String = "text",
    val attachmentUrl: String? = null,
    // "all" (the whole church) | "congregation" (narrowed on purpose).
    val audience: String = "all",
    val recipientCount: Int = 0,
    val seenCount: Int = 0,
    val repliedCount: Int = 0,
    val createdAt: String? = null,
)

@Serializable
data class BroadcastListRes(val data: List<BroadcastSummary> = emptyList(), val total: Int = 0)

// GET chat/broadcasts/{id} — the message, the church's answers, and the ticks.
// A broadcast is delivered as an individual DM to every member, so each
// response already carries the private thread it came from.
@Serializable
data class BroadcastResponseRow(
    val messageId: String,
    val conversationId: String,
    val body: String = "",
    val msgType: String = "text",
    val attachmentUrl: String? = null,
    val createdAt: String? = null,
    val userId: String = "",
    val fullName: String = "",
    val avatarUrl: String? = null,
    // How many messages this person has written in the thread — 1 is a first
    // word, more is a conversation already under way.
    val fromThem: Int = 1,
)

// One person the broadcast reached — the ticks. `delivered` is always true (a
// broadcast is written into every recipient's thread server-side in one
// transaction, so arrival is a fact); `seen` means they opened the thread.
@Serializable
data class BroadcastRecipientRow(
    val userId: String,
    val fullName: String = "",
    val avatarUrl: String? = null,
    val delivered: Boolean = true,
    val deliveredAt: String? = null,
    val seen: Boolean = false,
    val seenAt: String? = null,
)

@Serializable
data class BroadcastDetailRes(
    val broadcastId: String,
    val body: String = "",
    val msgType: String = "text",
    val attachmentUrl: String? = null,
    val audience: String = "all",
    val recipientCount: Int = 0,
    val createdAt: String? = null,
    val responses: List<BroadcastResponseRow> = emptyList(),
    val recipients: List<BroadcastRecipientRow> = emptyList(),
    val deliveredCount: Int = 0,
    val seenCount: Int = 0,
)

// --- My Discipler / Talk with My Pastor (Chat Redesign C3b) ---
// Mirrors packages/backend/src/modules/chat/service.ts#openDisciplerConversation
// and modules/pastoral/service.ts#openMyThread/inbox exactly — both already
// live server routes (verified by reading the backend source, read-only).

/** GET /chat/discipler/conversation — resolves (lazily creating) my DISCIPLER
 *  thread with my CURRENT assignment. 404 {no_discipler:true} if none. */
@Serializable
data class DisclerConversationRes(val conversationId: String = "")

/** POST /chat/pastoral — create-or-open MY pastoral thread. */
@Serializable
data class PastoralOpenRes(
    val conversationId: String = "",
    val pastorUserId: String = "",
    // assigned | congregation_default | fallback_superadmin
    val source: String = "assigned",
)

/** One row of GET /chat/pastoral/inbox (pastor/SuperAdmin-facing, password
 *  step-up gated). */
@Serializable
data class PastoralInboxRow(
    val conversationId: String,
    val archivedAt: String? = null,
    val updatedAt: String? = null,
    val memberUserId: String = "",
    val memberName: String = "",
    val memberAvatarUrl: String? = null,
    val lastBody: String? = null,
    val lastAt: String? = null,
)

@Serializable
data class PastoralInboxRes(val data: List<PastoralInboxRow> = emptyList())

/** GET /chat/pastoral/eligibility — side-effect-free "have I ever been
 *  assigned as a pastor" probe (Chat Redesign C4). No step-up. */
@Serializable
data class PastoralEligibilityRes(val isPastor: Boolean = false)

/** PUT /chat/conversations/{id}/mute body (Chat Redesign C4) — `until` null
 *  mutes forever; an ISO-8601 instant mutes until then. DELETE the same path
 *  unmutes (no body). */
@Serializable
data class MuteConversationBody(val until: String? = null)

// --- My Space join requests (Chat Redesign C1/C2 backend, already live —
// space_join_requests + leader/moderator review, as distinct from the
// immediate POST /chat/spaces/{id}/join path used for auto-entitled spaces). ---
@Serializable
data class RequestJoinSpaceBody(val message: String? = null)

/** already_member | pending. */
@Serializable
data class JoinSpaceRequestRes(val status: String = "pending", val requestId: String? = null)
