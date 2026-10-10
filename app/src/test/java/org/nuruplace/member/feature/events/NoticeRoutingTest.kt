// The inbox and a tapped push share one router (pathway docs/EXPERIENCE.md
// §7.2 #3): a notice tapped in the inbox lands exactly where its push does.
// What it found: a "Ring check" Live notice in the inbox opened a generic
// sheet ("Grace and peace, friend." · "Continue my journey") while its push
// opened the Live — the inbox carried its own, drifted copy of the rules.
package org.nuruplace.member.feature.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.firebase.NuruMessagingService
import org.nuruplace.member.data.net.NotifPayload
import org.nuruplace.member.data.net.NotificationRow

class NoticeRoutingTest {
    private val stream = "a34a265b-9f24-4ba1-a474-8cf81db7cd52"

    private fun row(template: String, payload: NotifPayload? = null) =
        NotificationRow(notificationId = "n1", template = template, payload = payload, status = "sent")

    @Test fun `a Live notice opens the stream it names — the player, or This Live has ended with its name`() {
        // EXPERIENCE.md §7.3: never whichever stream happens to be live now.
        assertEquals("live-now?streamId=$stream&title=Ring%20check", noticeRoute(row("live_stream_started", NotifPayload(title = "Ring check", streamId = stream))))
        assertEquals("live-now?streamId=$stream&title=Ring%20check", noticeRoute(row("live_guest_invite", NotifPayload(title = "Ring check", streamId = stream))))
        // A notice naming no stream: the newest watchable one, as a push does.
        assertEquals("live-now", noticeRoute(row("live_stream_started")))
    }

    @Test fun `every notice routes exactly as its push`() {
        val cases = listOf(
            row("live_stream_started", NotifPayload(streamId = stream)) to
                mapOf("template" to "live_stream_started", "stream_id" to stream),
            row("reflection_returned", NotifPayload(moduleId = "m1")) to mapOf("template" to "reflection_returned", "module_id" to "m1"),
            row("announcement", NotifPayload(announcementId = "a1")) to mapOf("template" to "announcement", "announcement_id" to "a1"),
            row("level_ushered", NotifPayload(levelNumber = 2)) to mapOf("template" to "level_ushered", "level_number" to "2"),
            row("plan_group_invite", NotifPayload(inviteToken = "tok")) to mapOf("template" to "plan_group_invite", "invite_token" to "tok"),
            row("department_post", NotifPayload(departmentId = "d1")) to mapOf("template" to "department_post", "department_id" to "d1"),
            row("giving_gift_failed", NotifPayload(transactionId = "t1", failureCode = "insufficient_funds")) to
                mapOf("template" to "giving_gift_failed", "transaction_id" to "t1", "failure_code" to "insufficient_funds"),
            row("pledge_due_soon", NotifPayload(pledgeId = "p1")) to mapOf("template" to "pledge_due_soon", "pledge_id" to "p1"),
            row("prayer_chain") to mapOf("template" to "prayer_chain"),
            row("event_reminder_24h") to mapOf("template" to "event_reminder_24h"),
            row("plan_group_joined") to mapOf("template" to "plan_group_joined"),
        )
        for ((n, push) in cases) assertEquals(n.template, NuruMessagingService.destFor(push), noticeRoute(n))
        // The ones the inbox used to miss now land.
        assertEquals("reading/join/tok", noticeRoute(cases[4].first))
        assertEquals("read-with-friend", noticeRoute(row("plan_group_joined")))
        assertEquals("partners-pledge/p1", noticeRoute(cases[7].first))
    }

    @Test fun `a notice with nowhere to go has no route — the inbox shows the notice itself`() {
        assertNull(noticeRoute(row("reengage", NotifPayload(title = "We've missed you"))))
        assertNull(noticeRoute(row("dm_message")))
    }
}
