// The notification permission, asked at the right moment (pathway docs/
// EXPERIENCE.md §7.2 #12): only when the member turns on something that
// needs it — never cold — and never a dead "Allow" once refused for good.
package org.nuruplace.member.data.firebase

import org.junit.Assert.assertEquals
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
}
