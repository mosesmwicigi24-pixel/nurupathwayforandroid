package org.nuruplace.member.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Since 2026-09-28 every push's data carries its `template` (and
 *  `nuru_kind` / `nuru_sound`) — before, the dispatcher copied only the
 *  payload, so [NuruMessagingService.destFor]'s template rules only ever saw
 *  "" from a real push. This pins what a tap on each real push now opens,
 *  with the data exactly as workers/dispatch.ts fcmMessage builds it: the
 *  template rules route the pushes they were written for, a push's own keys
 *  still win ahead of its template, and chat still opens Home. */
class PushTemplateRoutingTest {

    private fun push(template: String, kind: String, vararg payload: Pair<String, String>) =
        mapOf("template" to template, "nuru_kind" to kind, "nuru_sound" to "on") + payload

    @Test fun `a Live invite opens its own stream, not the newest`() {
        val invite = push(
            "live_guest_invite", "ring",
            "stream_id" to "5f0c7a52-2d7e-4a8b-9c1d-0e5b8f3a6d21", "title" to "Sunday service",
            "alert_title" to "You're invited to go live", "alert_body" to "…", "body" to "…",
        )
        assertEquals("live-now?streamId=5f0c7a52-2d7e-4a8b-9c1d-0e5b8f3a6d21&title=Sunday%20service", NuruMessagingService.destFor(invite))
    }

    @Test fun `a Live invite with no usable stream falls back to the newest`() {
        assertEquals("live-now", NuruMessagingService.destFor(push("live_guest_invite", "ring")))
        assertEquals("live-now", NuruMessagingService.destFor(push("live_guest_invite", "ring", "stream_id" to "x/y")))
    }

    @Test fun `a stream starting opens the stream it names, not whichever is live now`() {
        // live/service.ts notifyStreamStarted: { stream_id, scope, cell_id, title }
        // — EXPERIENCE.md §7.3: the player while it is live, "This Live has
        // ended" with its name once it is over.
        val started = push("live_stream_started", "update", "stream_id" to "s-1", "scope" to "church", "title" to "Sunday service")
        assertEquals("live-now?streamId=s-1&title=Sunday%20service", NuruMessagingService.destFor(started))
        // Without a usable stream: the newest watchable one.
        assertEquals("live-now", NuruMessagingService.destFor(push("live_stream_started", "update", "stream_id" to "a/b")))
    }

    @Test fun `the template rules now route the pushes they were written for`() {
        assertEquals("profile", NuruMessagingService.destFor(push("badge_awarded", "update", "name" to "Faithful")))
        assertEquals("events", NuruMessagingService.destFor(push("event_cancelled", "update", "title" to "Prayer night")))
        assertEquals("events", NuruMessagingService.destFor(push("event_reminder_1h", "update")))
        assertEquals("prayer-room?tab=corporate", NuruMessagingService.destFor(push("prayer_chain", "update")))
        assertEquals("read-with-friend", NuruMessagingService.destFor(push("plan_group_member_joined", "update", "group_id" to "g-1")))
        assertEquals("pathway", NuruMessagingService.destFor(push("level_completed", "update")))
    }

    @Test fun `a push's own keys still win ahead of its template`() {
        assertEquals("level/3", NuruMessagingService.destFor(push("level_completed", "update", "level_number" to "3")))
        assertEquals("module/m-1", NuruMessagingService.destFor(push("reflection_approved", "update", "module_id" to "m-1")))
        assertEquals("reading/join/tok", NuruMessagingService.destFor(push("plan_group_invite_received", "update", "invite_token" to "tok")))
        assertEquals("department/d-1", NuruMessagingService.destFor(push("department_post", "update", "department_id" to "d-1")))
        assertEquals(
            "give-gift/t-9",
            NuruMessagingService.destFor(push("giving_gift_failed", "update", "transaction_id" to "t-9", "failure_code" to "no_answer")),
        )
        assertEquals("partners-pledge/p-1", NuruMessagingService.destFor(push("pledge_due_soon", "update", "pledge_id" to "p-1")))
    }

    @Test fun `chat and the other pushes with no rule still open Home`() {
        for (template in listOf("chat_dm_message", "chat_discipler_message", "chat_pastoral_message", "chat_broadcast")) {
            assertNull(template, NuruMessagingService.destFor(push(template, "message", "conversation_id" to "c-1")))
        }
        for (template in listOf(
            "space_join_requested", "space_join_accepted", "connection_request_received", "connection_request_declined",
            "community_blessing", "member_care_flag", "sunday_letter", "flock_brief", "reengage", "check_in_welcome",
        )) {
            assertNull(template, NuruMessagingService.destFor(push(template, "update")))
        }
    }
}
