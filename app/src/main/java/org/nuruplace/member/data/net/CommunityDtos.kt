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
    val unread: Int = 0,
    val avatarUrl: String? = null,
    val peerUserId: String? = null,
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
    val replyBody: String? = null,
    val replyAuthor: String? = null,
    val isEdited: Boolean = false,
    val createdAt: String = "",
    val mine: Boolean = false,
    val reactions: List<ChatReaction> = emptyList(),
    val readCount: Int? = null,
    val recipientCount: Int? = null,
    val aiTag: String? = null,
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
@Serializable
data class CreatePrayerBody(val postId: String, val title: String? = null, val body: String, val clientMutationId: String)

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

@Serializable
data class PeopleRes(val people: List<ChatPerson> = emptyList())

@Serializable
data class DmBody(val userId: String)

@Serializable
data class DmRes(val conversationId: String = "")
