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
