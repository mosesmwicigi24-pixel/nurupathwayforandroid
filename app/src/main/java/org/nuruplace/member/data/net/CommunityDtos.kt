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
