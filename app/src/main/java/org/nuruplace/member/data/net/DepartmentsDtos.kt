// Departments — where members serve (docs/PARTNERS_PROGRAMME.md §4, §5).
// snake_case on the wire via the client's global SnakeCase strategy. The
// server derives everything here (fit, my_status, counts, need progress);
// nothing is a second copy of the truth.
package org.nuruplace.member.data.net

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** One department — the row `GET /departments` and `GET /me/departments`
 *  return, and the head of `GET /departments/{id}`. The detail-only fields
 *  (`isLeader`, `posts`, `members`, `needs`) stay at their empty defaults on
 *  a list row, so one class serves both shapes without a copy. */
@Serializable
data class Department(
    val departmentId: String = "",
    val name: String = "",
    val purpose: String = "",
    val meets: String? = null,
    val imageUrl: String? = null,
    val giftKeys: List<String> = emptyList(),
    val isOpenToJoin: Boolean = true,
    val leaderName: String? = null,
    val leaderAvatar: String? = null,
    val memberCount: Int = 0,
    /** requested | active | declined | left | null (never asked). */
    val myStatus: String? = null,
    /** leader | member | null. */
    val myRole: String? = null,
    val openNeeds: Int = 0,
    /** First 140 chars of the newest post, or null. */
    val latestPost: String? = null,
    val latestPostAt: String? = null,
    /** `gift_keys` ∩ the member's top gifts is non-empty. */
    val fit: Boolean = false,
    val matchedGifts: List<String> = emptyList(),
    // --- GET /departments/{id} only ---
    val isLeader: Boolean = false,
    val posts: List<DepartmentPost> = emptyList(),
    val members: List<DepartmentMember> = emptyList(),
    val needs: List<DepartmentNeed> = emptyList(),
) {
    val isActive: Boolean get() = myStatus == "active"
    val isRequested: Boolean get() = myStatus == "requested"
}

@Serializable
data class DepartmentPost(
    val postId: String = "",
    val body: String = "",
    val imageUrl: String? = null,
    val createdAt: String = "",
    val authorName: String? = null,
    val authorAvatar: String? = null,
)

@Serializable
data class DepartmentMember(
    val userId: String = "",
    val fullName: String = "",
    val avatarUrl: String? = null,
    /** leader | member. */
    val role: String = "member",
)

/** A financial need — its own giving target once the office approves it
 *  (spec §4). Members see only `approved`; the leader also sees `pending`
 *  and `closed`. `raisedMinor`/`percent`/`reached` are server-computed. */
@Serializable
data class DepartmentNeed(
    val needId: String = "",
    val title: String = "",
    val why: String = "",
    val targetMinor: Int = 0,
    val currency: String = "KES",
    val deadline: String? = null,
    /** pending | approved | rejected | closed. */
    val status: String = "approved",
    val createdAt: String = "",
    val submittedName: String? = null,
    val raisedMinor: Int = 0,
    val percent: Int = 0,
    val reached: Boolean = false,
) {
    val isOpen: Boolean get() = status == "approved"
}

/** POST /departments/{id}/serve → `{status}` (also the decision reply). */
@Serializable
data class ServeStatus(val status: String = "")

/** POST /departments/{id}/posts (leader). `image_url` omitted when absent. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DepartmentPostBody(
    val body: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val imageUrl: String? = null,
)

@Serializable
data class DepartmentPostCreated(val postId: String = "", val createdAt: String = "")

/** POST /departments/{id}/needs (leader) — lands as `pending` for the office. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DepartmentNeedBody(
    val title: String,
    val why: String,
    val targetMinor: Int,
    val currency: String = "KES",
    /** ISO date (yyyy-MM-dd); omitted when absent. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val deadline: String? = null,
)

@Serializable
data class DepartmentNeedCreated(val needId: String = "", val status: String = "pending", val createdAt: String = "")
