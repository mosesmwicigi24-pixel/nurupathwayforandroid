// The notification centre's icon for each notice (NotificationsScreen.toneFor).
// Giving and Partners notices wear one giving tone — the Give tab's gold
// (iOS: its hand-and-heart on 0xFFF4DA / 0xA8861C, nuru-member-ios 6fbc9ed).
// Found comparing the inboxes (Giving Cycle 10): "give" never matched
// "giving_…", so they fell to the default bell, and pledge_reminder_manual
// matched the calendar's "reminder" rule.
package org.nuruplace.member.feature.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.feature.give.GIVE
import org.nuruplace.member.ui.theme.Nuru

class NotificationToneTest {
    @Test
    fun `every giving and Partners notice wears the giving tone`() {
        listOf(
            "giving_gift_failed", "giving_schedule_failed", "giving_schedule_paused", "giving_schedule_heads_up",
            "giving_schedule_office_change", "giving_schedule_covered", "giving_schedule_stopped",
            "pledge_due_soon", "pledge_overdue", "pledge_reminder_manual", "pledge_fulfilled",
            "pledge_claim_confirmed", "pledge_claim_rejected", "payment_received",
        ).forEach { template ->
            assertTrue(template, isGivingNotice(template))
            val tone = toneFor(template)
            assertEquals(template, "🤲", tone.glyph)
            assertEquals(template, Nuru.goldChipBg, tone.bg)
            assertEquals(template, GIVE.overline, tone.fg)
            assertFalse(template, tone.reward)
        }
    }

    @Test
    fun `the other notices keep their own tones`() {
        // The office's reminder about a pledge is giving; a calendar reminder is not.
        assertEquals("📅", toneFor("event_reminder").glyph)
        assertEquals("📅", toneFor("event_cancelled").glyph)
        assertEquals("🤝", toneFor("department_need_approved").glyph)
        assertEquals("🏅", toneFor("badge_awarded").glyph)
        assertEquals("📣", toneFor("announcement_published").glyph)
        assertEquals("🔔", toneFor("something_new").glyph)
        assertFalse(isGivingNotice("event_reminder"))
        assertFalse(isGivingNotice("department_need_approved"))
    }
}
