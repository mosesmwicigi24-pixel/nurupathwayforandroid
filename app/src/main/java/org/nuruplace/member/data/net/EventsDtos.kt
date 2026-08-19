// Events / calendar + notification-center DTOs — ported from the iOS
// Models/Events.swift + Notifications.swift.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

@Serializable
data class EventAttendee(val userId: String, val fullName: String = "", val avatarUrl: String? = null)

@Serializable
data class CalendarOccurrence(
    val occurrenceId: String,
    val seriesId: String = "",
    val title: String = "",
    val description: String? = null,
    val location: String? = null,
    val category: String? = null,
    val primaryImageUrl: String? = null,
    val startAt: String = "",
    val endAt: String = "",
    // Wire truth (calendar/service.ts projectRange): cancelled occurrences are
    // dropped server-side; a moved one arrives with rescheduled=true (+ the new
    // start/end already applied). status is the series status (draft|active).
    val status: String = "active",
    val rescheduled: Boolean = false,
    val going: Int = 0,
    val attendees: List<EventAttendee>? = null,
)

@Serializable
data class RsvpCounts(val going: Int? = null, val maybe: Int? = null, val declined: Int? = null)

@Serializable
data class EventDetail(
    val eventId: String,
    val title: String = "",
    val occursAt: String = "",
    val description: String? = null,
    val location: String? = null,
    val category: String? = null,
    val primaryImageUrl: String? = null,
    // Wire truth (calendar/service.ts getEvent): [primary, …gallery] — feeds the
    // detail image carousel. primaryImageUrl stays for the hero fallback.
    val images: List<String> = emptyList(),
    val videoUrl: String? = null,
    val rsvpCounts: RsvpCounts = RsvpCounts(),
    val myRsvp: String? = null,
    val attendees: List<EventAttendee>? = null,
)

/** GET /home/featured-event — the ONE admin-featured event for the mobile Home
 *  (portal "feature on homepage" toggle; partial unique index enforces one). */
@Serializable
data class FeaturedEvent(
    val seriesId: String = "",
    val title: String = "",
    val description: String? = null,
    val location: String? = null,
    val category: String? = null,
    val primaryImageUrl: String? = null,
    val dtstartLocal: String = "",
)

@Serializable
data class FeaturedEventEnv(val data: FeaturedEvent? = null)

// --- Notifications ---
@Serializable
data class NotifPayload(
    val title: String? = null,
    val body: String? = null,
    val feedback: String? = null,
    val levelNumber: Int? = null,
    val name: String? = null,
    val moduleId: String? = null,
    val announcementId: String? = null,
)

@Serializable
data class NotificationRow(
    val notificationId: String,
    val template: String = "",
    val payload: NotifPayload? = null,
    val status: String = "",
    val scheduledFor: String = "",
    val sentAt: String? = null,
    val readAt: String? = null,
) {
    val isUnread: Boolean get() = readAt == null && status == "sent"
}

@Serializable
data class NotificationsRes(val data: List<NotificationRow> = emptyList(), val unread: Int = 0)

// --- Request bodies ---
@Serializable
data class RsvpBody(val status: String)

@Serializable
data class MarkReadBody(val ids: List<String>? = null)

/** GET /me/rsvps — the member's own RSVP list (event_id → status feeds the Events tab). */
@Serializable
data class MyRsvp(
    val rsvpId: String = "",
    val status: String = "",
    val eventId: String = "",
    val title: String = "",
    val occursAt: String? = null,
)

// --- Church service attendance (§3.3) ---
// Distinct from the event check-in above: church services are the weekly cadence
// the attendance streak is measured in, and a check-in registers the member's
// contact details alongside the time they attended.

/** One church service — the cadence slot members scan into. */
@Serializable
data class ChurchService(
    val serviceId: String = "",
    val title: String = "",
    val serviceDate: String = "",
    val startsAt: String = "",
    val endsAt: String? = null,
    val checkinOpensAt: String? = null,
    val checkinClosesAt: String? = null,
    val qrEnabled: Boolean = true,
    val countsForStreak: Boolean = true,
    /** Whether a member could scan into it right now. */
    val checkinOpen: Boolean = false,
    /** Whether this member is already checked in. */
    val attended: Boolean = false,
    val attendedAt: String? = null,
)

/**
 * POST /services/{id}/attendance. Contact fields are optional on the wire — the
 * server falls back to the member's profile for anything omitted.
 */
@Serializable
data class ServiceCheckInBody(
    val clientScanId: String,
    val scanToken: String,
    val fullName: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    /** The real arrival time when the offline queue replays a queued scan. */
    val attendedAt: String? = null,
)

/**
 * Attendance measured in SERVICES, not days. The window is anchored at the
 * member's first-ever check-in, so services held before they joined are not
 * counted against them.
 */
@Serializable
data class AttendanceStreak(
    /** Consecutive services attended, counting back from the most recent. */
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalAttended: Int = 0,
    /** "Failures" — eligible services missed since the first check-in. */
    val totalMissed: Int = 0,
    /** "Breaks" — one per interruption, so two misses in a row is 1 break, 2 failures. */
    val breaks: Int = 0,
    /** Consecutive services missed right now; 0 while the streak is alive. */
    val currentMissRun: Int = 0,
    val lastAttendedAt: String? = null,
    val lastServiceDate: String? = null,
    /** new | active | at_risk | broken */
    val status: String = "new",
)

@Serializable
data class ServiceCheckInResult(
    val attendanceId: String = "",
    val duplicate: Boolean = false,
    val serviceId: String = "",
    val serviceTitle: String = "",
    val attendedAt: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val email: String? = null,
    val streak: AttendanceStreak = AttendanceStreak(),
)

/** One service in the member's history — attended, or a visible miss. */
@Serializable
data class AttendanceHistoryEntry(
    val serviceId: String = "",
    val title: String = "",
    val serviceDate: String = "",
    val startsAt: String = "",
    val attended: Boolean = false,
    val attendedAt: String? = null,
)
