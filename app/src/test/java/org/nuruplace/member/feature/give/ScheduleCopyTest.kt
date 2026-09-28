// Giving Cycle 1 — a schedule's words. Before one exists the member confirms
// what it means (how much, how often, where, which phone, nothing taken
// today, when the first prompt comes — the server's own date rule); once it
// exists it says whether it runs, why its last prompt failed, and asks before
// it stops.
package org.nuruplace.member.feature.give

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.GiftFailure
import org.nuruplace.member.data.net.GivingSchedule
import java.time.Instant

class ScheduleCopyTest {
    @Test
    fun `the first prompt comes when the server's nextRun says`() {
        // Weekly: seven days on.
        assertEquals(Instant.parse("2026-10-05T09:30:00Z"), firstPromptAt(Instant.parse("2026-09-28T09:30:00Z"), FREQ_WEEKLY))
        // Monthly: the same day next month (UTC)…
        assertEquals(Instant.parse("2026-10-28T09:30:00Z"), firstPromptAt(Instant.parse("2026-09-28T09:30:00Z"), FREQ_MONTHLY))
        assertEquals(Instant.parse("2027-01-15T00:00:00Z"), firstPromptAt(Instant.parse("2026-12-15T00:00:00Z"), FREQ_MONTHLY))
        // …on JavaScript's calendar: the 31st runs over rather than clamping
        // (setUTCMonth: 31 Jan + 1 month = "31 Feb" = 3 Mar).
        assertEquals(Instant.parse("2026-03-03T08:00:00Z"), firstPromptAt(Instant.parse("2026-01-31T08:00:00Z"), FREQ_MONTHLY))
        assertEquals(Instant.parse("2028-03-02T08:00:00Z"), firstPromptAt(Instant.parse("2028-01-31T08:00:00Z"), FREQ_MONTHLY)) // leap year
    }

    @Test
    fun `days are Nairobi days`() {
        assertEquals("28 Oct 2026", nairobiDay(Instant.parse("2026-10-28T09:30:00Z")))
        // 22:30 UTC is already the next morning in Nairobi.
        assertEquals("29 Oct 2026", nairobiDay(Instant.parse("2026-10-28T22:30:00Z")))
        assertEquals("29 Oct 2026", nairobiDayOf("2026-10-28T22:30:00.000Z"))
        assertNull(nairobiDayOf(""))
        assertNull(nairobiDayOf("not a date"))
    }

    @Test
    fun `the confirmation says what, how often, where, which phone and that nothing is taken today`() {
        assertEquals("Give every month?", scheduleConfirmTitle(FREQ_MONTHLY))
        assertEquals("Give every week?", scheduleConfirmTitle(FREQ_WEEKLY))
        assertEquals(
            "KSh 1,013 every month to Tithe, prompts go to 0711 222 333. Nothing is taken today — the first prompt comes on 28 Oct 2026.",
            scheduleConfirmText(101_300, FREQ_MONTHLY, "Tithe", "+254711222333", "28 Oct 2026"),
        )
        assertEquals(
            "KSh 500 every week to Mission. Nothing is taken today — the first prompt comes on 5 Oct 2026.",
            scheduleConfirmText(50_000, FREQ_WEEKLY, "Mission", null, "5 Oct 2026"),
        )
        assertEquals(
            "Nothing was taken today. The first prompt comes on 28 Oct 2026, then every month. Cancel anytime from Manage schedules.",
            scheduledFirstPromptLine(FREQ_MONTHLY, "28 Oct 2026"),
        )
    }

    @Test
    fun `wire frequencies read as cadences`() {
        assertEquals("week", cadenceWord("weekly"))
        assertEquals("month", cadenceWord("monthly"))
        assertEquals("week", cadenceWord(FREQ_WEEKLY))
        assertEquals("month", cadenceWord(FREQ_MONTHLY))
        assertEquals(FREQ_WEEKLY, freqOf("weekly"))
        assertEquals(FREQ_MONTHLY, freqOf("monthly"))
    }

    private val sched = GivingSchedule(scheduleId = "s1", fund = "tithe", amountMinor = 100_000, frequency = "monthly", method = "mpesa", status = "active")

    @Test
    fun `a schedule says Paused or Cancelled in place of its next date`() {
        assertNull(scheduleStatusLabel("active"))
        assertEquals("Paused", scheduleStatusLabel("paused"))
        assertEquals("Paused", scheduleStatusLabel(" PAUSED "))
        assertEquals("Cancelled", scheduleStatusLabel("cancelled"))
        assertTrue(scheduleCancellable("active"))
        assertTrue(scheduleCancellable("paused")) // the server cancels both
        assertFalse(scheduleCancellable("cancelled"))
    }

    @Test
    fun `a failing schedule says why — the server's reason and hint, verbatim`() {
        assertNull(scheduleFailureLine(sched))
        val failing = sched.copy(
            lastFailure = GiftFailure("unreachable", "The M-Pesa prompt couldn't reach the phone.", "Check the phone is on and has signal, then try again.", true),
        )
        assertEquals(
            "The M-Pesa prompt couldn't reach the phone. Check the phone is on and has signal, then try again.",
            scheduleFailureLine(failing),
        )
        assertNull(scheduleFailureLine(sched.copy(lastFailure = GiftFailure(code = "declined", reason = " "))))
    }

    @Test
    fun `a schedule says which phone it prompts`() {
        assertEquals("Prompts go to 0722 000 111", schedulePromptLine(sched.copy(phoneNumber = "+254722000111")))
        assertEquals("Prompts go to your profile number", schedulePromptLine(sched))
    }

    @Test
    fun `cancelling asks with what stops and what does not`() {
        assertEquals(
            "KSh 1,000 every month to Tithe will stop. Gifts already given are not affected.",
            scheduleCancelText(sched),
        )
        assertEquals(
            "KSh 500 every week to Mission will stop. Gifts already given are not affected.",
            scheduleCancelText(sched.copy(amountMinor = 50_000, frequency = "weekly", fund = "mission")),
        )
    }
}
