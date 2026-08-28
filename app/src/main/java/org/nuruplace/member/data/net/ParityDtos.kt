// Parity-completion DTOs — auth (register/forgot/reset), recurring giving
// schedules, announcements, event series + buzz posts. Ported from the iOS
// Models + MemberAPI. snake_case via the global Json naming strategy.
package org.nuruplace.member.data.net

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
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
data class EventPostBody(
    val postId: String,
    val body: String? = null,
    val imageUrl: String? = null,
    val clientMutationId: String,
)

@Serializable
data class EventReactBody(val kind: String? = null)

// --- Home extras ---
@Serializable
data class FeaturedCell(
    val cellGroupId: String = "",
    val name: String = "",
    val disciplerName: String? = null,
    val disciplerRole: String? = null,
    val focus: String? = null,
    val levelLabel: String? = null,
    val meets: String? = null,
    val room: String? = null,
    val nextSession: String? = null,
    val imageUrl: String? = null,
    val members: Int = 0,
)

@Serializable
data class Discipler(
    val userId: String,
    val fullName: String = "",
    val message: String? = null,
    val avatarUrl: String? = null,
    val cellName: String? = null,
    val roleLabel: String = "",
)

@Serializable
data class Moment(
    val momentId: String,
    val imageUrl: String = "",
    val caption: String? = null,
    val tag: String? = null,
    val createdAt: String = "",
)

// --- Profile: notification prefs + MFA ---
@Serializable
data class NotificationPreferences(
    val pushEnabled: Boolean = true,
    val emailEnabled: Boolean = true,
    val smsEnabled: Boolean = false,
)

@Serializable
data class MfaEnrollment(val otpauthUri: String = "", val secret: String = "")

@Serializable
data class MfaCodeBody(val code: String)

@Serializable
class EmptyBody

// --- Mentor / cell / score drill-down ---
@Serializable
data class MentorInfo(
    val mentor: Mentor? = null,
    val nextMeetingAt: String? = null,
    val notes: List<MentorNote> = emptyList(),
) {
    @Serializable
    data class Mentor(
        val mentorUserId: String,
        val fullName: String = "",
        val avatarUrl: String? = null,
        val cellName: String? = null,
        val establishedAt: String? = null,
    )

    @Serializable
    data class MentorNote(
        val noteId: String,
        val topic: String? = null,
        val note: String = "",
        val metAt: String? = null,
        val nextMeetingAt: String? = null,
    )
}

@Serializable
data class CellSummary(val cell: Cell? = null) {
    @Serializable
    data class Cell(
        val cellGroupId: String = "",
        val name: String = "",
        val members: Int = 0,
        val leader: Leader? = null,
        val attendance: Attendance = Attendance(),
        val next: Next? = null,
        // cell-truth (pathway#453) — own-cell identity + rhythm. All nullable
        // with defaults so responses from older servers still decode.
        val focus: String? = null,
        val levelLabel: String? = null,
        val room: String? = null,
        val tone: String? = null,
        val imageUrl: String? = null,
        // Server-derived "Sundays · 2:00 PM" when a real series exists, else
        // the admin-typed fallback; rhythmSource says which ("series"|"static").
        val meets: String? = null,
        val rhythmSource: String? = null,
        val roster: Roster? = null,
        val turnout: Turnout? = null,
        // Only present for the cell's leader when ≥2 recent meetings exist.
        val leaderView: LeaderView? = null,
    )

    @Serializable
    data class Leader(val name: String = "", val role: String? = null, val avatarUrl: String? = null)

    @Serializable
    data class Attendance(val attended: Int = 0, val expected: Int = 0)

    @Serializable
    data class Next(
        val startAt: String = "",
        val location: String? = null,
        val endAt: String? = null,
        val occurrenceId: String? = null,
    )

    @Serializable
    data class Roster(val count: Int = 0, val faces: List<Face> = emptyList())

    @Serializable
    data class Face(val firstName: String = "", val avatarUrl: String? = null)

    @Serializable
    data class Turnout(val rate: Double = 0.0, val meetings: Int = 0, val trend: String? = null)

    @Serializable
    data class LeaderView(val count: Int = 0, val names: List<String> = emptyList())
}

@Serializable
data class ScoreBreakdown(
    val score: Int = 0,
    val band: String = "",
    val components: Map<String, Double> = emptyMap(),
    val detail: Map<String, Double> = emptyMap(),
)

// --- Welcome video + media reactions ---
@Serializable
data class ContentReaction(val emoji: String = "", val count: Int = 0, val mine: Boolean = false)

@Serializable
data class WelcomeVideo(
    val mediaAssetId: String = "",
    val videoSource: String = "direct",   // cloudinary | youtube | vimeo | direct | private
    val caption: String? = null,
    val durationSec: Int? = null,
    val thumbnailUrl: String? = null,
    val reactions: List<ContentReaction>? = null,
    val loveCount: Int? = null,
    val liked: Boolean? = null,
    val externalUrl: String? = null,
    val externalVideoId: String? = null,
    val url: String? = null,
    val expiresAt: String? = null,
) {
    /** The playable URL — external link when set, else the hosted signed url. */
    val playUrl: String? get() = externalUrl ?: url

    /**
     * True when the URL is a player PAGE (YouTube/Vimeo) and needs the
     * provider's iframe rather than ExoPlayer. It is never a reason to leave
     * the app — see ui/components/VideoPlayer.kt.
     *
     * This replaced an `externalUrl != null || …` test that was wrong at the
     * root (owner bug, 2026-08-26: the featured card opened a browser and
     * offered to download the file). The backend registers an UPLOADED video
     * as `video_source = 'direct'` with `external_url` pointing at OUR OWN
     * public `/media/<uuid>.mp4` (pathway packages/backend/src/modules/media/
     * video.ts `registerUploaded`, and `welcomeVideo` returns `external_url`
     * for every source in its EXTERNAL_SOURCES set — which includes "direct").
     * `external_url` means "shareable link", not "unplayable in-app"; only the
     * provider decides, exactly as iOS InlineVideoPlayer does.
     */
    val needsWebEmbed: Boolean get() = videoSource == "youtube" || videoSource == "vimeo"
}

@Serializable
data class ReactionToggleResult(
    val on: Boolean = false,
    val reactions: List<ContentReaction> = emptyList(),
    val loveCount: Int = 0,
    val liked: Boolean = false,
)

@Serializable
data class MediaReactBody(val emoji: String)

// --- Account: avatar + password ---
@Serializable
data class AvatarResult(val avatarUrl: String = "")

@Serializable
data class ChangePasswordBody(val currentPassword: String, val newPassword: String)

// --- Event QR attendance check-in (idempotent on clientScanId, §3.3) ---
@Serializable
data class CheckInBody(val clientScanId: String, val scanToken: String)

@Serializable
data class EventCheckInResult(val attendanceId: String = "", val duplicate: Boolean = false)

// --- Approximate location sharing (§proximity — coarse geohash only) ---
@Serializable
data class LocationBody(val lat: Double, val lng: Double)

// --- Device registration (FCM push token, §D-M9) ---
// `network` is a one-shot census sample ("wifi" | "cellular" | "other") — the
// server field is plain optional, so an unknown sample is OMITTED, not null
// (EncodeDefault(NEVER) overrides the client's global encodeDefaults = true).
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DeviceBody(
    val platform: String = "android",
    val appVersion: String? = null,
    val model: String? = null,
    val pushToken: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val network: String? = null,
)

// --- Radio (member player) ---
@Serializable
data class RadioProgram(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val category: String = "",
    val speaker: String? = null,
    val artworkUrl: String? = null,
    val scheduledAt: String? = null,
    val status: String = "scheduled",   // scheduled | live | ended
    val isLive: Boolean = false,
    val audioUrl: String? = null,
    val hlsUrl: String? = null,
    val peakListeners: Int? = null,
) {
    /** Live → HLS; otherwise the hosted recording (null = nothing to play yet). */
    val streamUrl: String? get() = if (isLive) hlsUrl ?: audioUrl else audioUrl
    val live: Boolean get() = isLive || status == "live"
    val recorded: Boolean get() = !live && status == "ended" && audioUrl != null
}

// --- Offline sync: ordered mutation replay (§1.7, §3.6) ---
@Serializable
data class SyncMutation(
    val mutationId: String,
    val seq: Long,
    val domain: String,
    val op: String,
    val payload: kotlinx.serialization.json.JsonObject,
)

@Serializable
data class SyncPushBody(val deviceId: String? = null, val mutations: List<SyncMutation>)

@Serializable
data class SyncPushResult(val results: List<SyncMutationResult> = emptyList())

@Serializable
data class SyncMutationResult(
    val mutationId: String,
    val status: String = "",   // applied | duplicate | rejected
    val code: String? = null,
    val detail: String? = null,
)

// --- Radio interactivity (react kinds are a fixed server enum, not arbitrary emoji) ---
@Serializable
data class RadioReactBody(val kind: String, val clientEventId: String)

@Serializable
data class RadioReactionCounts(val heart: Int = 0, val amen: Int = 0, val fire: Int = 0)

@Serializable
data class RadioReactRes(val counts: RadioReactionCounts = RadioReactionCounts())

@Serializable
data class RadioCommentBody(val body: String, val clientEventId: String)

// --- Screen-view telemetry (POST /me/activity/screens — best-effort, silent;
// iOS ScreenTracker parity) ---
@Serializable
data class ScreenEvent(
    val screen: String,
    val durationMs: Int,
    val occurredAt: String,
    val clientEventId: String,
)

@Serializable
data class ScreenActivityBody(val events: List<ScreenEvent>)

@Serializable
data class RadioComment(
    val id: String,
    val programId: String = "",
    val memberId: String = "",
    val body: String = "",
    val createdAt: String = "",
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
)
