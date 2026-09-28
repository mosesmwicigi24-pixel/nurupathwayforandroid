// Recurring gifts — the words around a schedule, kept pure so ScheduleCopyTest
// pins them (Giving Cycle 1). A schedule is a promise to prompt the member's
// phone every week or month, so before one exists the member confirms exactly
// that — how much, how often, where, which phone, and that NOTHING is taken
// today — and once it exists, every place it is shown says whether it is
// running, paused or cancelled, why its last prompt failed (the server's
// words, verbatim), and asks before cancelling it.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GivingSchedule
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NAIROBI_ZONE: ZoneId = ZoneId.of("Africa/Nairobi")
private val SCHEDULE_DAY_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "week" / "month" for a FREQ_* index. */
fun cadenceWord(freq: Int): String = if (freq == FREQ_WEEKLY) "week" else "month"

/** "week" / "month" for a wire frequency (weekly | monthly). */
fun cadenceWord(frequency: String): String = if (frequency.trim().equals("weekly", ignoreCase = true)) "week" else "month"

/** The FREQ_* index for a wire frequency. */
fun freqOf(frequency: String): Int = if (cadenceWord(frequency) == "week") FREQ_WEEKLY else FREQ_MONTHLY

/** When the server's first prompt comes (financial/service.ts nextRun): a
 *  week on, or the same day next month in UTC — JavaScript's calendar, so
 *  the 31st runs over into the following month rather than clamping. */
fun firstPromptAt(now: Instant, freq: Int): Instant {
    val utc = now.atOffset(ZoneOffset.UTC)
    return if (freq == FREQ_WEEKLY) {
        utc.plusDays(7).toInstant()
    } else {
        utc.withDayOfMonth(1).plusMonths(1).plusDays(utc.dayOfMonth - 1L).toInstant()
    }
}

/** "28 Oct 2026" — the Nairobi day of an instant. */
fun nairobiDay(instant: Instant): String = instant.atZone(NAIROBI_ZONE).toLocalDate().format(SCHEDULE_DAY_FMT)

/** "28 Oct 2026" — an ISO timestamp's Nairobi day; null when unreadable. */
fun nairobiDayOf(iso: String?): String? = parseNairobi(iso)?.toLocalDate()?.format(SCHEDULE_DAY_FMT)

/** The confirmation's title. */
fun scheduleConfirmTitle(freq: Int): String = "Give every ${cadenceWord(freq)}?"

/** What the member confirms before a schedule exists: how much, how often,
 *  where, which phone — and that nothing is taken today. */
fun scheduleConfirmText(amountMinor: Int, freq: Int, fundName: String, phone: String?, firstPromptDay: String): String {
    val prompts = phone?.let { ", prompts go to ${kenyanMobileDisplay(it)}" }.orEmpty()
    return "${ksh(amountMinor)} every ${cadenceWord(freq)} to $fundName$prompts. " +
        "Nothing is taken today — the first prompt comes on $firstPromptDay."
}

/** The created schedule's line: nothing was taken, when the first prompt comes. */
fun scheduledFirstPromptLine(freq: Int, firstPromptDay: String): String =
    "Nothing was taken today. The first prompt comes on $firstPromptDay, then every ${cadenceWord(freq)}. " +
        "Cancel anytime from Manage schedules."

/** What a schedule says in place of its next date: "Paused" / "Cancelled";
 *  null while it is running. */
fun scheduleStatusLabel(status: String): String? = when (status.trim().lowercase()) {
    "active" -> null
    "paused" -> "Paused"
    "cancelled", "canceled" -> "Cancelled"
    "" -> null
    else -> status.trim().replaceFirstChar { it.uppercase() }
}

/** A schedule the member can still stop — running or paused (the server
 *  cancels both). */
fun scheduleCancellable(status: String): Boolean = status.trim().lowercase() in setOf("active", "paused")

/** Why the schedule's last prompt failed — the server's reason, then its
 *  hint, verbatim. Null while it is not failing. */
fun scheduleFailureLine(s: GivingSchedule): String? =
    s.lastFailure?.takeIf { it.reason.isNotBlank() }
        ?.let { f -> listOf(f.reason, f.hint).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") }

/** Which phone every cycle prompts: the schedule's own number, else the
 *  profile's (followed if it changes). */
fun schedulePromptLine(s: GivingSchedule): String =
    s.phoneNumber?.takeIf { it.isNotBlank() }?.let { "Prompts go to ${kenyanMobileDisplay(it)}" }
        ?: "Prompts go to your profile number"

/** The cancel confirmation — what stops, and what does not. */
fun scheduleCancelText(s: GivingSchedule): String =
    "${ksh(s.amountMinor)} every ${cadenceWord(s.frequency)} to ${giveFund(s.fund).name} will stop. " +
        "Gifts already given are not affected."
