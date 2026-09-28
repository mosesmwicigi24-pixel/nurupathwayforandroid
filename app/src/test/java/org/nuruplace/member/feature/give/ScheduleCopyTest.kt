// Giving Cycles 1 and 4 — a schedule's words and rules. Before one exists the
// member confirms what it means (how much, how often, where, which phone, and
// either "KSh X now, then every Sunday" or nothing taken today — the date by
// the server's own rule); once it exists it says whether it runs and why not,
// why its last prompt failed, and it can be changed (only what changed is
// sent), paused (tomorrow … a year) and resumed.
package org.nuruplace.member.feature.give

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.GiftFailure
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.UpdateScheduleBody
import java.time.Instant
import java.time.LocalDate

class ScheduleCopyTest {
    // ── When prompts come (the server's Cycle 2 calendar) ──

    @Test
    fun `the first prompt comes when the server's nextRun says`() {
        // Weekly: seven days on, same Nairobi time.
        assertEquals(Instant.parse("2026-10-05T09:30:00Z"), firstPromptAt(Instant.parse("2026-09-28T09:30:00Z"), FREQ_WEEKLY))
        // Monthly: the same Nairobi day next month, same Nairobi time.
        assertEquals(Instant.parse("2026-10-28T09:30:00Z"), firstPromptAt(Instant.parse("2026-09-28T09:30:00Z"), FREQ_MONTHLY))
        // The 31st keeps to the month's last day — never runs over to the 3rd.
        assertEquals(Instant.parse("2026-02-28T08:00:00Z"), firstPromptAt(Instant.parse("2026-01-31T08:00:00Z"), FREQ_MONTHLY))
        assertEquals(Instant.parse("2028-02-29T08:00:00Z"), firstPromptAt(Instant.parse("2028-01-31T08:00:00Z"), FREQ_MONTHLY)) // leap year
        assertEquals(Instant.parse("2027-01-15T09:00:00Z"), firstPromptAt(Instant.parse("2026-12-15T09:00:00Z"), FREQ_MONTHLY))
    }

    @Test
    fun `prompts keep to 07 00 to 21 00 Nairobi on the same day`() {
        // 22:30 Nairobi (19:30Z) → 20:00 that day; a weekly gift keeps its weekday.
        assertEquals(Instant.parse("2026-09-28T17:00:00Z"), promptHours(Instant.parse("2026-09-28T19:30:00Z")))
        assertEquals(Instant.parse("2026-10-05T17:00:00Z"), firstPromptAt(Instant.parse("2026-09-28T19:30:00Z"), FREQ_WEEKLY))
        // 01:00 Nairobi on the 1st (22:00Z the day before) → 07:00 on the 1st,
        // and next month on the 1st — it no longer creeps back a day a month.
        assertEquals(Instant.parse("2026-10-01T04:00:00Z"), promptHours(Instant.parse("2026-09-30T22:00:00Z")))
        assertEquals(Instant.parse("2026-11-01T04:00:00Z"), firstPromptAt(Instant.parse("2026-09-30T22:00:00Z"), FREQ_MONTHLY))
        // Inside prompt hours nothing moves.
        assertEquals(Instant.parse("2026-09-28T09:30:00Z"), promptHours(Instant.parse("2026-09-28T09:30:00Z")))
    }

    @Test
    fun `days are Nairobi days`() {
        assertEquals("28 Oct 2026", nairobiDay(Instant.parse("2026-10-28T09:30:00Z")))
        // 22:30 UTC is already the next morning in Nairobi.
        assertEquals("29 Oct 2026", nairobiDay(Instant.parse("2026-10-28T22:30:00Z")))
        assertEquals("29 Oct 2026", nairobiDayOf("2026-10-28T22:30:00.000Z"))
        assertNull(nairobiDayOf(""))
        assertNull(nairobiDayOf("not a date"))
        assertEquals(LocalDate.of(2026, 10, 29), nairobiToday(Instant.parse("2026-10-28T22:30:00Z")))
    }

    // ── Setting one up ──

    private val monday = Instant.parse("2026-09-28T09:30:00Z") // Monday 28 Sep, 12:30 Nairobi

    @Test
    fun `start with a gift now says now, then the rhythm it keeps`() {
        assertEquals("every Monday", cadencePhrase(FREQ_WEEKLY, monday))
        assertEquals("every month on the 28th", cadencePhrase(FREQ_MONTHLY, monday))
        assertEquals("KSh 1,000 now, then every Monday", giveNowLine(100_000, FREQ_WEEKLY, monday))
        assertEquals("KSh 500 now, then every month on the 28th", giveNowLine(50_000, FREQ_MONTHLY, monday))
        // The Nairobi day decides, not UTC's: 22:30Z Sunday is Monday morning.
        assertEquals("every Monday", cadencePhrase(FREQ_WEEKLY, Instant.parse("2026-09-27T22:30:00Z")))
        assertEquals("every month on the 1st", cadencePhrase(FREQ_MONTHLY, Instant.parse("2026-09-30T22:00:00Z")))
        assertEquals(
            "KSh 1,000 to Tithe now, then every Monday. Prompts go to 0711 222 333.",
            scheduleConfirmText(100_000, FREQ_WEEKLY, "Tithe", "+254711222333", "5 Oct 2026", giveNow = true, now = monday),
        )
        assertEquals("now", firstChargeWire(true))
        assertEquals("next", firstChargeWire(false))
    }

    @Test
    fun `without a gift now, nothing is taken today and the first prompt is dated`() {
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
    fun `a first prompt that could not go out still leaves the gift set up`() {
        assertEquals("Your weekly gift is set up — the first prompt comes on 5 Oct 2026.", scheduledSetUpLine(FREQ_WEEKLY, "5 Oct 2026"))
        assertEquals("Your monthly gift is set up — the first prompt comes on 28 Oct 2026.", scheduledSetUpLine(FREQ_MONTHLY, "28 Oct 2026"))
        assertEquals("Your weekly gift is set up — this is the first.", firstChargeNote(FREQ_WEEKLY))
    }

    @Test
    fun `wire frequencies read as cadences`() {
        assertEquals("week", cadenceWord("weekly"))
        assertEquals("month", cadenceWord("monthly"))
        assertEquals("week", cadenceWord(FREQ_WEEKLY))
        assertEquals("month", cadenceWord(FREQ_MONTHLY))
        assertEquals(FREQ_WEEKLY, freqOf("weekly"))
        assertEquals(FREQ_MONTHLY, freqOf("monthly"))
        assertEquals("Sunday", weekdayName(0))
        assertEquals("Saturday", weekdayName(6))
        assertEquals("31st", ordinal(31))
        assertEquals("22nd", ordinal(22))
        assertEquals("11th", ordinal(11))
    }

    // ── Where it stands ──

    private val sched = GivingSchedule(
        scheduleId = "s1", fund = "tithe", amountMinor = 100_000, frequency = "monthly", method = "mpesa", status = "active",
        nextRunAt = "2026-10-28T09:30:00Z", anchorDay = 28,
    )
    private val weekly = sched.copy(scheduleId = "w1", frequency = "weekly", amountMinor = 50_000, nextRunAt = "2026-10-05T06:00:00Z", anchorDay = null)

    @Test
    fun `a schedule says Paused or Cancelled in place of its next date`() {
        assertNull(scheduleStatusLabel("active"))
        assertEquals("Paused", scheduleStatusLabel("paused"))
        assertEquals("Paused", scheduleStatusLabel(" PAUSED "))
        assertEquals("Cancelled", scheduleStatusLabel("cancelled"))
        assertTrue(scheduleCancellable("active"))
        assertTrue(scheduleCancellable("paused")) // the server cancels both
        assertFalse(scheduleCancellable("cancelled"))
        assertTrue(scheduleRunning("active"))
        assertFalse(scheduleRunning("paused"))
    }

    @Test
    fun `a paused schedule says why, and whether it can be resumed here`() {
        assertNull(schedulePauseView(sched))
        val paused = sched.copy(status = "paused")
        assertEquals(PauseView("Paused after 3 prompts didn't go through", canResume = true), schedulePauseView(paused.copy(pauseReason = "failures", consecutiveFailures = 3)))
        assertEquals(PauseView("Paused after 3 prompts didn't go through", canResume = true), schedulePauseView(paused.copy(pauseReason = "failures")))
        assertEquals(PauseView("Paused until 5 Oct 2026", canResume = true), schedulePauseView(paused.copy(pauseReason = "member", resumeOn = "2026-10-05")))
        assertEquals(PauseView("Paused", canResume = true), schedulePauseView(paused.copy(pauseReason = "member")))
        assertEquals(
            PauseView("Paused with its pledge — resume the pledge in Partners", canResume = false),
            schedulePauseView(paused.copy(pauseReason = "pledge")),
        )
        // An older server names no reason: plain Paused, and Resume.
        assertEquals(PauseView("Paused", canResume = true), schedulePauseView(paused))
        assertNull(schedulePauseView(sched.copy(status = "cancelled", pauseReason = "member")))
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
        assertEquals("KSh 1,000 every month to Tithe will stop. Gifts already given are not affected.", scheduleCancelText(sched))
        assertEquals("KSh 500 every week to Mission will stop. Gifts already given are not affected.", scheduleCancelText(weekly.copy(fund = "mission")))
    }

    // ── Pausing ──

    @Test
    fun `a pause can end from tomorrow to a year ahead`() {
        val today = LocalDate.of(2026, 9, 28)
        val range = pauseDateRange(today)
        assertEquals(LocalDate.of(2026, 9, 29), range.start)
        assertEquals(LocalDate.of(2027, 9, 28), range.endInclusive)
        assertFalse(pauseDateAllowed(today, today)) // not today
        assertFalse(pauseDateAllowed(today.minusDays(3), today))
        assertTrue(pauseDateAllowed(LocalDate.of(2026, 9, 29), today))
        assertTrue(pauseDateAllowed(LocalDate.of(2027, 9, 28), today))
        assertFalse(pauseDateAllowed(LocalDate.of(2027, 9, 29), today)) // past a year
        // 29 Feb → 28 Feb next year, never an invalid date.
        assertEquals(LocalDate.of(2029, 2, 28), pauseDateRange(LocalDate.of(2028, 2, 29)).endInclusive)
    }

    // ── Changing it ──

    @Test
    fun `a schedule keeps its day — monthly its own, weekly its next prompt's weekday`() {
        assertEquals(28, scheduleDay(sched))
        assertEquals(31, scheduleDay(sched.copy(anchorDay = 31, nextRunAt = "2026-11-30T09:30:00Z")))
        assertEquals(30, scheduleDay(sched.copy(anchorDay = null, nextRunAt = "2026-11-30T09:30:00Z"))) // older server
        assertEquals(1, scheduleDay(weekly)) // Mon 5 Oct 09:00 Nairobi
        assertEquals(0..6, scheduleDayOptions("weekly"))
        assertEquals(1..31, scheduleDayOptions("monthly"))
        assertEquals("Sun", scheduleDayChip("weekly", 0))
        assertEquals("31", scheduleDayChip("monthly", 31))
    }

    @Test
    fun `a changed amount is whole shillings inside M-Pesa's range`() {
        assertNull(scheduleAmountError("1000", 100, 25_000_000))
        assertNull(scheduleAmountError("250,000", 100, 25_000_000))
        assertEquals("Enter an amount.", scheduleAmountError(" ", 100, 25_000_000))
        assertEquals("Whole shillings only — no cents.", scheduleAmountError("100.50", 100, 25_000_000))
        assertEquals("M-Pesa gifts are from KSh 1 to KSh 250,000.", scheduleAmountError("0", 100, 25_000_000))
        assertEquals("M-Pesa gifts are from KSh 1 to KSh 250,000.", scheduleAmountError("250001", 100, 25_000_000))
    }

    @Test
    fun `only what changed is sent, and a number goes back to the profile as null`() {
        // Nothing changed → nothing to send.
        assertNull(scheduleEditPatch(sched, amountMajor = 1_000, day = 28, number = PromptNumberChoice.Profile, headsUp = true))
        assertEquals(UpdateScheduleBody(amountMinor = 150_000), scheduleEditPatch(sched, amountMajor = 1_500, day = 28))
        assertEquals(UpdateScheduleBody(day = 31), scheduleEditPatch(sched, amountMajor = 1_000, day = 31))
        assertEquals(UpdateScheduleBody(day = 0), scheduleEditPatch(weekly, day = 0)) // Monday → Sunday
        assertNull(scheduleEditPatch(weekly, day = 9)) // not a weekday
        assertEquals(UpdateScheduleBody(headsUp = false), scheduleEditPatch(sched, headsUp = false))
        // A pinned number back to the profile travels as JSON null…
        val pinned = sched.copy(phoneNumber = "+254722000111")
        assertEquals(UpdateScheduleBody(phoneNumber = JsonNull), scheduleEditPatch(pinned, number = PromptNumberChoice.Profile))
        // …another number as itself, and the same number not at all.
        assertEquals(
            UpdateScheduleBody(phoneNumber = JsonPrimitive("+254711222333")),
            scheduleEditPatch(pinned, number = PromptNumberChoice.Own("+254711222333")),
        )
        assertNull(scheduleEditPatch(pinned, number = PromptNumberChoice.Own("+254722000111")))
        assertEquals(PromptNumberChoice.Own("+254722000111"), promptNumberChoiceOf(pinned))
        assertEquals(PromptNumberChoice.Profile, promptNumberChoiceOf(sched))
    }

    // ── Your rhythm ──

    @Test
    fun `the rhythm row names the soonest running gift and when it next prompts`() {
        val sunday = weekly.copy(scheduleId = "sun", nextRunAt = "2026-10-04T06:00:00Z") // Sun 4 Oct 09:00 Nairobi
        assertEquals("KSh 500 every Sunday · next Sun 4 Oct", rhythmText(sunday))
        assertEquals("KSh 1,000 every month on the 28th · next Wed 28 Oct", rhythmText(sched))
        // The soonest running one; paused and cancelled ones never.
        assertEquals("sun", rhythmSchedule(listOf(sched, sunday, weekly.copy(scheduleId = "p", status = "paused", nextRunAt = "2026-09-29T06:00:00Z")))?.scheduleId)
        assertNull(rhythmSchedule(listOf(sched.copy(status = "paused"), sunday.copy(status = "cancelled"))))
        assertNull(rhythmSchedule(emptyList()))
    }

    @Test
    fun `a monthly gift on the 31st keeps its day in a short month`() {
        // November has 30 days: the prompt comes on the 30th, the gift is still "the 31st".
        val eom = sched.copy(amountMinor = 50_000, anchorDay = 31, nextRunAt = "2026-11-30T09:30:00Z")
        assertEquals("KSh 500 every month on the 31st · next Mon 30 Nov", rhythmText(eom))
        // February: the 28th.
        assertEquals("KSh 500 every month on the 31st · next Sun 28 Feb", rhythmText(eom.copy(nextRunAt = "2027-02-28T09:30:00Z")))
        // And back on the 31st when the month has one.
        assertEquals("KSh 500 every month on the 31st · next Thu 31 Dec", rhythmText(eom.copy(nextRunAt = "2026-12-31T09:30:00Z")))
    }
}
