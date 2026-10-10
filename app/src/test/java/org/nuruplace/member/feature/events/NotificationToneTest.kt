// The notification centre's icon for each notice (NotificationsScreen
// noticeFamily) — one family per kind of notice, one icon each, on the
// gold-tint tile (pathway docs/EXPERIENCE.md §8.1 rule 7, §8.2 #14). Seen: a
// Live notice wore the default bell on Android and a gear on iOS; a Live
// notice is the broadcast. Giving and Partners notices are one family —
// "give" never matched "giving_…" and pledge_reminder_manual read as a
// calendar reminder (Giving Cycle 10).
package org.nuruplace.member.feature.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationToneTest {
    @Test
    fun `a Live notice is the broadcast`() {
        assertEquals(NoticeFamily.LIVE, noticeFamily("live_stream_started"))
        assertEquals(NoticeFamily.LIVE, noticeFamily("live_guest_invite"))
        assertFalse(NoticeFamily.LIVE.reward)
    }

    @Test
    fun `every giving and Partners notice is the giving family`() {
        listOf(
            "giving_gift_failed", "giving_schedule_failed", "giving_schedule_paused", "giving_schedule_heads_up",
            "giving_schedule_office_change", "giving_schedule_covered", "giving_schedule_stopped", "giving_receipt",
            "pledge_due_soon", "pledge_overdue", "pledge_reminder_manual", "pledge_fulfilled",
            "pledge_claim_confirmed", "pledge_claim_rejected", "payment_received",
        ).forEach { template ->
            assertTrue(template, isGivingNotice(template))
            assertEquals(template, NoticeFamily.GIVING, noticeFamily(template))
        }
    }

    @Test
    fun `each family of the server's templates has its own icon`() {
        val expected = mapOf(
            "badge_awarded" to NoticeFamily.BADGE,
            "certificate_issued" to NoticeFamily.CERTIFICATE,
            "level_completed" to NoticeFamily.LEVEL,
            "level_ushered" to NoticeFamily.LEVEL,
            "reflection_approved" to NoticeFamily.REFLECTION,
            "reflection_returned" to NoticeFamily.REFLECTION,
            "department_post" to NoticeFamily.DEPARTMENT,
            "department_need_approved" to NoticeFamily.DEPARTMENT,
            "serve_request_approved" to NoticeFamily.DEPARTMENT,
            "event_reminder_24h" to NoticeFamily.EVENT,
            "event_cancelled" to NoticeFamily.EVENT,
            "event_rescheduled" to NoticeFamily.EVENT,
            "announcement" to NoticeFamily.ANNOUNCEMENT,
            "plan_group_invite_received" to NoticeFamily.PLAN,
            "plan_group_day_completed" to NoticeFamily.PLAN,
            "sunday_letter" to NoticeFamily.LETTER,
            "space_join_accepted" to NoticeFamily.COMMUNITY,
            "connection_request_received" to NoticeFamily.COMMUNITY,
            "community_blessing" to NoticeFamily.COMMUNITY,
            "prayer_chain" to NoticeFamily.COMMUNITY,
            "check_in_welcome" to NoticeFamily.CHECK_IN,
            "password_changed" to NoticeFamily.SECURITY,
            "reengage" to NoticeFamily.OTHER,
            "something_new" to NoticeFamily.OTHER,
        )
        expected.forEach { (template, family) -> assertEquals(template, family, noticeFamily(template)) }
        // The office's reminder about a pledge is giving; a calendar reminder is not.
        assertFalse(isGivingNotice("event_reminder_24h"))
        assertFalse(isGivingNotice("department_need_approved"))
    }

    @Test
    fun `one icon per family — no two families share one`() {
        val icons = NoticeFamily.entries.map { it.icon }
        assertEquals(icons.size, icons.toSet().size)
    }

    @Test
    fun `only the rewards wear the gold gift`() {
        assertEquals(
            setOf(NoticeFamily.BADGE, NoticeFamily.CERTIFICATE, NoticeFamily.LEVEL),
            NoticeFamily.entries.filter { it.reward }.toSet(),
        )
    }
}
