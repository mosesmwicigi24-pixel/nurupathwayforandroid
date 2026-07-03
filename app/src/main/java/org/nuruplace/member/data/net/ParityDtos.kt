// Parity-completion DTOs — auth (register/forgot/reset), recurring giving
// schedules, announcements, event series + buzz posts. Ported from the iOS
// Models + MemberAPI. snake_case via the global Json naming strategy.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

// --- Auth ---
@Serializable
data class RegisterBody(val fullName: String, val email: String, val password: String)

@Serializable
data class ForgotBody(val email: String)

@Serializable
data class ForgotRes(val sent: Boolean = false, val devToken: String? = null)

@Serializable
data class ResetBody(val token: String, val newPassword: String)

// --- Recurring giving schedules ---
@Serializable
data class GivingSchedule(
    val scheduleId: String,
    val fund: String = "",
    val amountMinor: Int = 0,
    val currency: String = "KES",
    val frequency: String = "monthly",   // weekly | monthly
    val method: String = "",
    val status: String = "active",       // active | cancelled
    val nextRunAt: String = "",
    val createdAt: String = "",
)

// --- Announcements ---
@Serializable
data class MyAnnouncement(
    val announcementId: String,
    val title: String = "",
    val body: String = "",
    val sentAt: String? = null,
    val bannerExpiresAt: String? = null,
    val primaryImageUrl: String? = null,
    val galleryImageUrls: List<String>? = null,
    val videoUrl: String? = null,
    val opened: Boolean = false,
)

@Serializable
data class AnnouncementDetail(
    val announcementId: String,
    val title: String = "",
    val body: String = "",
    val sentAt: String? = null,
    val primaryImageUrl: String? = null,
    val galleryImageUrls: List<String>? = null,
    val images: List<String> = emptyList(),
    val videoUrl: String? = null,
    val opened: Boolean = false,
)

@Serializable
data class FeaturedAnnouncementEnv(val data: FeaturedAnnouncement? = null)

@Serializable
data class FeaturedAnnouncement(
    val announcementId: String,
    val title: String = "",
    val body: String = "",
    val primaryImageUrl: String? = null,
    val galleryImageUrls: List<String>? = null,
    val sentAt: String? = null,
)

// --- Event series + buzz posts ---
@Serializable
data class EventSeries(
    val seriesId: String,
    val title: String = "",
    val category: String? = null,
    val cadence: String = "",
    val nextAt: String? = null,
    val nextOccurrenceId: String? = null,
    val nextEndAt: String? = null,
    val location: String? = null,
    val following: Boolean = false,
    val newCount: Int = 0,
)

@Serializable
data class SeriesFollowResult(val seriesId: String = "", val following: Boolean = false)

@Serializable
data class EventPost(
    val postId: String,
    val authorUserId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val body: String? = null,
    val imageUrl: String? = null,
    val createdAt: String = "",
    val mine: Boolean = false,
    val rsvpStatus: String? = null,
    val cheerCount: Int = 0,
    val loveCount: Int = 0,
    val myReaction: String? = null,
)

@Serializable
data class EventPostReactionResult(val cheerCount: Int = 0, val loveCount: Int = 0, val myReaction: String? = null)

@Serializable
data class EventPostBody(val postId: String, val body: String, val clientMutationId: String)

@Serializable
data class EventReactBody(val kind: String? = null)
