// The notification permission, asked at the right moment (pathway docs/
// EXPERIENCE.md §7.2 #12): only when the member turns on something that
// needs it — never cold — and never a dead "Allow" once refused for good.
package org.nuruplace.member.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAskTest {
    @Test fun `nothing to ask below Android 13, or once allowed`() {
        assertEquals(NotificationAskStep.NONE, notificationAskStep(sdkInt = 32, granted = false, askedBefore = false, showRationale = false))
        assertEquals(NotificationAskStep.NONE, notificationAskStep(sdkInt = 34, granted = true, askedBefore = true, showRationale = false))
    }

    @Test fun `never asked, or refused once — the line, then the system's prompt`() {
        assertEquals(NotificationAskStep.PROMPT, notificationAskStep(sdkInt = 33, granted = false, askedBefore = false, showRationale = false))
        assertEquals(NotificationAskStep.PROMPT, notificationAskStep(sdkInt = 34, granted = false, askedBefore = true, showRationale = true))
    }

    @Test fun `refused for good — the line, and the phone's settings`() {
        assertEquals(NotificationAskStep.SETTINGS, notificationAskStep(sdkInt = 34, granted = false, askedBefore = true, showRationale = false))
    }

    @Test fun `the question in iOS's words (EXPERIENCE md 7_3)`() {
        assertEquals("Allow notifications?", notificationAskTitle(NotificationAskStep.PROMPT))
        assertEquals(NotificationWhy.RADIO, notificationAskMessage(NotificationAskStep.PROMPT, NotificationWhy.RADIO))
        assertEquals("Notifications are off", notificationAskTitle(NotificationAskStep.SETTINGS))
        assertEquals(
            "So we can tell you when Nuru Radio goes live. Turn them on for Nuru Pathway in Settings.",
            notificationAskMessage(NotificationAskStep.SETTINGS, NotificationWhy.RADIO),
        )
        assertEquals("So devotionals, events and reminders reach this phone.", NotificationWhy.SETTINGS_PUSH)
        assertEquals("So we can tell you when Nuru Radio goes live.", NotificationWhy.RADIO)
        assertEquals("So your pledge reminders reach this phone.", NotificationWhy.PLEDGE_REMINDER)
    }

    @Test fun `Home's card shows while the phone has notifications off, hidden 14 days by Not now`() {
        val now = 1_800_000_000_000L
        val day = 24L * 60 * 60 * 1000
        assertEquals("Turn on notifications", NOTIFICATIONS_CARD_TITLE)
        assertEquals("So messages, Live invites and reminders reach this phone.", NOTIFICATIONS_CARD_LINE)
        assertTrue(notificationsCardShown(sdkInt = 34, granted = false, snoozedAtMs = 0, nowMs = now))
        // Allowed, or a phone below Android 13 (no permission to ask for): never.
        assertFalse(notificationsCardShown(sdkInt = 34, granted = true, snoozedAtMs = 0, nowMs = now))
        assertFalse(notificationsCardShown(sdkInt = 32, granted = false, snoozedAtMs = 0, nowMs = now))
        // Not now: gone for 14 days, back after.
        assertFalse(notificationsCardShown(sdkInt = 34, granted = false, snoozedAtMs = now - 13 * day, nowMs = now))
        assertTrue(notificationsCardShown(sdkInt = 34, granted = false, snoozedAtMs = now - 14 * day, nowMs = now))
    }
}
