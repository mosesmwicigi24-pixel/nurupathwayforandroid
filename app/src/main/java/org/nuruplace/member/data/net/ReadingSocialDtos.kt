// Reading & Social R1 — "Read with a Friend" DTOs (spec §3/§6). Kotlin mirror
// of packages/backend/src/modules/reading-social/{groups,invites}.ts's wire
// shapes exactly (ported from the iOS Models/ReadingSocial.swift). snake_case
// handled by the global Json naming strategy (ApiClient.kt); every field the
// server might omit carries a default so an older/partial response never
// blanks the whole screen.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

// --- Request bodies ---

@Serializable
data class CreateReadingGroupBody(
    val planId: String,
    val memberUserIds: List<String>? = null,
    val name: String? = null,
)

@Serializable
data class CreateReadingInviteBody(
    val userId: String? = null,
    val message: String? = null,
    val clientMutationId: String,
)

// --- Shared shapes ---

/** Plan summary embedded in a group or invite preview — the subset of
 *  ReadingPlanRow the reading-social endpoints echo back (no `enrolled`/
 *  `currentDay`, since those are per-caller and this is shared state). */
@Serializable
data class ReadingGroupPlanSummary(
    val code: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val description: String? = null,
    val dayCount: Int = 0,
    val imageUrl: String? = null,
)

/** One member's row inside a shared plan group — mirrors GroupMemberRow
 *  (groups.ts). `daysDone`/`currentDay` are the progress-visibility
 *  exception (docs/READING_SOCIAL_PLAN.md §2.1): exposed ONLY because this
 *  caller shares an active group with this person. */
@Serializable
data class ReadingGroupMember(
    val userId: String,
    val fullName: String = "A friend",
    val avatarUrl: String? = null,
    val status: String = "active",   // active | left | removed
    val joinedAt: String? = null,
    val leftAt: String? = null,
    val currentDay: Int? = null,
    val completedDays: List<Int>? = null,
    val daysDone: Int = 0,
    val completedAt: String? = null,
) {
    val isActive: Boolean get() = status == "active"
}

/** GroupRow — a bounded, invite-only circle walking one plan together
 *  (migration 174; groups.ts `GroupRow`). */
@Serializable
data class ReadingGroupRow(
    val groupId: String,
    val planId: String = "",
    val createdBy: String = "",
    val name: String? = null,
    val createdAt: String? = null,
    val archivedAt: String? = null,
    val plan: ReadingGroupPlanSummary = ReadingGroupPlanSummary(),
    val members: List<ReadingGroupMember> = emptyList(),
) {
    val isArchived: Boolean get() = archivedAt != null

    /** Every OTHER active member — the roster a card shows progress for. */
    fun otherMembers(myUserId: String): List<ReadingGroupMember> =
        members.filter { it.isActive && it.userId != myUserId }
}

@Serializable
data class ReadingGroupsRes(val data: List<ReadingGroupRow> = emptyList())

/** InviteRow — a targeted (inviteeUserId set) or open-link invite (invites.ts). */
@Serializable
data class ReadingInviteRow(
    val inviteId: String,
    val groupId: String = "",
    val inviterId: String = "",
    val inviteeUserId: String? = null,
    val token: String = "",
    val status: String = "pending",   // pending | accepted | declined | revoked | expired
    val message: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val decidedAt: String? = null,
    val acceptedBy: String? = null,
) {
    val isOpenLink: Boolean get() = inviteeUserId == null
    val isPending: Boolean get() = status == "pending"
}

@Serializable
data class ReadingInvitesRes(val data: List<ReadingInviteRow> = emptyList())

@Serializable
data class ReadingInviter(val userId: String = "", val fullName: String = "A friend")

/** GET /reading/invites/{token} — the authenticated in-app preview
 *  (invites.ts `InvitePreview`). */
@Serializable
data class ReadingInvitePreview(
    val token: String = "",
    val status: String = "pending",
    val message: String? = null,
    val groupId: String = "",
    val plan: ReadingGroupPlanSummary = ReadingGroupPlanSummary(),
    val inviter: ReadingInviter = ReadingInviter(),
    val memberCount: Int = 0,
)

/** POST /reading/invites/{token}/accept response. */
@Serializable
data class ReadingInviteAcceptResult(
    val groupId: String = "",
    val status: String = "",
    val joined: Boolean = false,
)
