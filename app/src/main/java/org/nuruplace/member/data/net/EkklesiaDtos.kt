// Ekklesia — the intercessory watch (pathway packages/backend/src/modules/
// ekklesia). Kotlin mirror of the wire shapes: the watch itself (group), its
// requests, one request's detail, and the small summary the Home card and the
// invitations read. snake_case ↔ camelCase by the client's naming strategy.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

@Serializable
data class EkklesiaFace(val userId: String = "", val name: String = "", val avatar: String? = null, val role: String = "intercessor")

/** The watch as the member sees it: counts for everyone, membership for me. */
@Serializable
data class EkklesiaGroup(
    val name: String = "Ekklesia",
    val mission: String = "Pray for the Body of Christ",
    val memberCount: Int = 0,
    val activeCount: Int = 0,
    val urgentCount: Int = 0,
    val answeredCount: Int = 0,
    val prayersToday: Int = 0,
    val myPrayersToday: Int = 0,
    val myPrayersTotal: Int = 0,
    val myDays: Int = 0,
    val faces: List<EkklesiaFace> = emptyList(),
    val isMember: Boolean = false,
    val myRole: String? = null,
    val joinedAt: String? = null,
    val isLeader: Boolean = false,
    /** Active needs I have not yet prayed today (0 unless I'm on the watch). */
    val needsMeToday: Int = 0,
)

@Serializable
data class EkklesiaRequest(
    val requestId: String,
    val authorUserId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val title: String = "",
    val body: String = "",
    val forWhom: String? = null,
    val urgency: String = "normal",          // normal | urgent
    val isAnswered: Boolean = false,
    val answeredAt: String? = null,
    val answeredNote: String? = null,
    val isPinned: Boolean = false,
    val createdAt: String = "",
    val mine: Boolean = false,
    val intercessorCount: Int = 0,           // distinct people who have prayed it
    val prayerCount: Int = 0,                // person-days of prayer
    val iPrayedToday: Boolean = false,
    val myPrayerDays: Int = 0,
    val updateCount: Int = 0,
    val lastUpdateAt: String? = null,
) {
    val isUrgent: Boolean get() = urgency == "urgent"
}

/** What a member who has not joined sees of a need: the title, never the body. */
@Serializable
data class EkklesiaPreview(val requestId: String, val title: String = "", val urgency: String = "normal", val intercessorCount: Int = 0, val createdAt: String = "")

@Serializable
data class EkklesiaOverview(
    val group: EkklesiaGroup = EkklesiaGroup(),
    val requests: List<EkklesiaRequest> = emptyList(),
    val preview: List<EkklesiaPreview> = emptyList(),
)

@Serializable
data class EkklesiaLatest(val requestId: String, val title: String = "", val forWhom: String? = null, val urgency: String = "normal", val intercessorCount: Int = 0, val createdAt: String = "")

/** GET /ekklesia/summary — the Home card and every invitation read this one shape. */
@Serializable
data class EkklesiaSummary(
    val name: String = "Ekklesia",
    val mission: String = "Pray for the Body of Christ",
    val isMember: Boolean = false,
    val myRole: String? = null,
    val isLeader: Boolean = false,
    val memberCount: Int = 0,
    val activeCount: Int = 0,
    val urgentCount: Int = 0,
    val answeredCount: Int = 0,
    val prayersToday: Int = 0,
    val needsMeToday: Int = 0,
    val myPrayersToday: Int = 0,
    val myDays: Int = 0,
    val faces: List<EkklesiaFace> = emptyList(),
    val latest: List<EkklesiaLatest> = emptyList(),
)

@Serializable
data class EkklesiaUpdate(
    val updateId: String,
    val authorUserId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val kind: String = "update",             // update | testimony
    val body: String = "",
    val createdAt: String = "",
    val mine: Boolean = false,
)

@Serializable
data class EkklesiaIntercessor(val userId: String = "", val name: String = "", val avatar: String? = null, val days: Int = 0, val lastPrayedOn: String? = null, val me: Boolean = false)

@Serializable
data class EkklesiaRequestDetail(
    val request: EkklesiaRequest,
    val updates: List<EkklesiaUpdate> = emptyList(),
    val intercessors: List<EkklesiaIntercessor> = emptyList(),
)

// --- writes (client-made ids + client_mutation_id: a retry is a no-op, §3.6) ---
@Serializable
data class EkklesiaRequestBody(val requestId: String, val title: String, val body: String, val forWhom: String? = null, val urgency: String = "normal", val clientMutationId: String)

@Serializable
data class EkklesiaUpdateBody(val updateId: String, val body: String, val kind: String = "update", val clientMutationId: String)

@Serializable
data class EkklesiaAnsweredBody(val answered: Boolean, val note: String? = null)

@Serializable
data class EkklesiaPinnedBody(val pinned: Boolean)

@Serializable
data class EkklesiaJoinRes(val joined: Boolean = false, val memberCount: Int = 0, val firstTime: Boolean = false)

@Serializable
data class EkklesiaLeaveRes(val left: Boolean = false, val memberCount: Int = 0)

@Serializable
data class EkklesiaIntercedeRes(val prayedToday: Boolean = false, val firstToday: Boolean = false, val prayerCount: Int = 0, val intercessorCount: Int = 0, val myPrayerDays: Int = 0)

@Serializable
data class EkklesiaCreateRes(val requestId: String = "", val duplicate: Boolean = false, val notified: Int = 0)
