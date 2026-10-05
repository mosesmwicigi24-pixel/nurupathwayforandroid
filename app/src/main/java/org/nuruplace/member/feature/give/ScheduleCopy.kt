// Recurring gifts — the words and rules around a schedule, kept pure so
// ScheduleCopyTest pins them (Giving Cycles 1 and 4). A schedule is a promise
// to prompt the member's phone every week or month, so before one exists the
// member confirms exactly that — how much, how often, where, which phone, and
// either "KSh X now, then every Sunday" (the first prompt goes out at once) or
// "nothing is taken today" — and once it exists, every place it is shown says
// whether it is running or paused and why, why its last prompt failed (the
// server's words, verbatim), and the member can change it, pause it, resume it
// or stop it (asked first).
//
// Dates follow the server (financial/service.ts, Giving Cycle 2): the Nairobi
// calendar, a monthly gift keeping its own day clamped to short months (31 Jan
// → 28/29 Feb → 31 Mar), prompts only inside 07:00–21:00 Nairobi.
//
// A gift that collects a pledge (Giving Cycle 5) says so — "Collects your
// pledge “Kenya trip”" — and what its next prompt asks when that is not the
// whole amount: only the rest of what's due, or nothing when the pledge is
// already paid. Collecting a MONTHLY pledge, its amount and day are the
// pledge's: they change on the pledge (the server refuses them here, 422
// details.pledge_id), while its number, heads-up and pause stay here.
package org.nuruplace.member.feature.give

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.UpdateScheduleBody
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val NAIROBI_ZONE: ZoneId = ZoneId.of("Africa/Nairobi")
private val RHYTHM_DAY_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** Prompts go out 07:00–21:00 Nairobi — the platform's quiet hours. */
private const val PROMPT_FROM_HOUR = 7
private const val PROMPT_UNTIL_HOUR = 21

/** "week" / "month" for a FREQ_* index. */
fun cadenceWord(freq: Int): String = if (freq == FREQ_WEEKLY) "week" else "month"

/** "week" / "month" for a wire frequency (weekly | monthly). */
fun cadenceWord(frequency: String): String = if (frequency.trim().equals("weekly", ignoreCase = true)) "week" else "month"

/** The FREQ_* index for a wire frequency. */
fun freqOf(frequency: String): Int = if (cadenceWord(frequency) == "week") FREQ_WEEKLY else FREQ_MONTHLY

/** "Sunday" for Sunday = 0 … "Saturday" = 6 (the server's weekday numbers). */
fun weekdayName(day: Int): String =
    DayOfWeek.of(if (day % 7 == 0) 7 else day % 7).getDisplayName(TextStyle.FULL, Locale.ENGLISH)

/** An instant's weekday in Nairobi, Sunday = 0 … Saturday = 6. */
fun nairobiWeekday(at: Instant): Int = at.atZone(NAIROBI_ZONE).dayOfWeek.value % 7

/** `at` kept on its own Nairobi day but inside prompt hours: before 07:00 →
 *  07:00, from 21:00 → 20:00 (the server's sameDayPromptHours). */
fun promptHours(at: Instant): Instant {
    val eat = at.atZone(NAIROBI_ZONE)
    return when {
        eat.hour < PROMPT_FROM_HOUR -> eat.toLocalDate().atTime(PROMPT_FROM_HOUR, 0).atZone(NAIROBI_ZONE).toInstant()
        eat.hour >= PROMPT_UNTIL_HOUR -> eat.toLocalDate().atTime(PROMPT_UNTIL_HOUR - 1, 0).atZone(NAIROBI_ZONE).toInstant()
        else -> at
    }
}

/** When a schedule set up at [now] next prompts (the server's createSchedule:
 *  nextRun(sameDayPromptHours(now))): a week on, or next month on today's
 *  Nairobi day — clamped to a shorter month — at the same Nairobi time. With
 *  "start with a gift now" this is the SECOND prompt; today's was the first. */
fun firstPromptAt(now: Instant, freq: Int): Instant {
    val start: ZonedDateTime = promptHours(now).atZone(NAIROBI_ZONE)
    return if (freq == FREQ_WEEKLY) start.plusDays(7).toInstant() else start.plusMonths(1).toInstant()
}

/** "Wed 28 Oct" — the Nairobi day of an instant, in the one date form (§8.1
 *  rule 8): the year only when it isn't [today]'s. */
fun nairobiDay(instant: Instant, today: LocalDate = LocalDate.now(NAIROBI_ZONE)): String = org.nuruplace.member.util.NuruDates.day(instant, NAIROBI_ZONE, today)

/** "Wed 28 Oct" — an ISO timestamp's Nairobi day; null when unreadable. */
fun nairobiDayOf(iso: String?, today: LocalDate = LocalDate.now(NAIROBI_ZONE)): String? =
    parseNairobi(iso)?.toLocalDate()?.let { org.nuruplace.member.util.NuruDates.day(it, today) }

/** Today in Nairobi. */
fun nairobiToday(now: Instant): LocalDate = now.atZone(NAIROBI_ZONE).toLocalDate()

/** What Give lists under RECURRING GIFTS (iOS GiveSchedules.listed): the
 *  running gifts, then the paused ones — never a cancelled one, which is
 *  history, nor a status this app does not know. */
fun listedSchedules(all: List<GivingSchedule>): List<GivingSchedule> =
    all.filter { it.status.trim().lowercase() == "active" } + all.filter { it.status.trim().lowercase() == "paused" }

/** A paused gift's card line (iOS PauseCopy.cardLine): when it comes back
 *  on its own — "Resumes 12 Oct", a member's pause with a date — else
 *  "Nothing is owed". */
fun pauseCardLine(s: GivingSchedule, today: LocalDate = LocalDate.now(NAIROBI_ZONE)): String {
    if (s.pauseReason?.trim()?.lowercase() == "member") {
        s.resumeOn?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?.let { return "Resumes ${org.nuruplace.member.util.NuruDates.day(it, today)}" }
    }
    return "Nothing is owed"
}

/** A Give card's next prompt: "Next Mon 5 Oct" — the year only when it
 *  isn't this one ("Next Tue 5 Jan 2027") — on the Nairobi calendar, in the
 *  one date form the rhythm line above it uses ("next Mon 5 Oct"); it read
 *  "Next 5 Oct" under it. "Next —" when there is no date to read. */
fun scheduleCardNextLine(nextRunAt: String?, today: LocalDate): String {
    val d = parseNairobi(nextRunAt)?.toLocalDate() ?: return "Next —"
    return "Next " + org.nuruplace.member.util.NuruDates.day(d, today)
}

// ── Setting one up ──

/** The confirmation's title. */
fun scheduleConfirmTitle(freq: Int): String = "Give every ${cadenceWord(freq)}?"

/** "every Monday" / "every month on the 28th" — the rhythm a schedule set up
 *  at [now] keeps (today's Nairobi weekday, or day of the month). */
fun cadencePhrase(freq: Int, now: Instant): String =
    if (freq == FREQ_WEEKLY) "every ${weekdayName(nairobiWeekday(now))}"
    else "every month on the ${ordinal(now.atZone(NAIROBI_ZONE).dayOfMonth)}"

/** The Give form's recurring summary, before a schedule exists (iOS
 *  recurringSummary): "Every Monday — start with a gift today, or from the
 *  next one. Cancel anytime." — today or next time is the member's choice
 *  at the confirm step. */
fun recurringSummaryLine(freq: Int, now: Instant): String =
    cadencePhrase(freq, now).replaceFirstChar { it.uppercase() } + " — start with a gift today, or from the next one. Cancel anytime."

/** The "Start with a gift now" line: "KSh 1,000 now, then every Monday". */
fun giveNowLine(amountMinor: Int, freq: Int, now: Instant): String =
    "${ksh(amountMinor)} now, then ${cadencePhrase(freq, now)}"

/** What the member confirms before a schedule exists: how much, how often,
 *  where, which phone — and either that the first prompt comes now
 *  ([giveNow]) or that nothing is taken today. */
fun scheduleConfirmText(
    amountMinor: Int,
    freq: Int,
    fundName: String,
    phone: String?,
    firstPromptDay: String,
    giveNow: Boolean = false,
    now: Instant = Instant.now(),
): String = if (giveNow) {
    val prompts = phone?.let { " Prompts go to ${kenyanMobileDisplay(it)}." }.orEmpty()
    "${ksh(amountMinor)} to $fundName now, then ${cadencePhrase(freq, now)}.$prompts"
} else {
    val prompts = phone?.let { ", prompts go to ${kenyanMobileDisplay(it)}" }.orEmpty()
    "${ksh(amountMinor)} every ${cadenceWord(freq)} to $fundName$prompts. " +
        "Nothing is taken today — the first prompt comes on $firstPromptDay."
}

/** first_charge on the wire. */
fun firstChargeWire(giveNow: Boolean): String = if (giveNow) "now" else "next"

/** The created schedule's line: nothing was taken, when the first prompt
 *  comes, and where it stops — its card under RECURRING GIFTS on Give. */
fun scheduledFirstPromptLine(freq: Int, firstPromptDay: String): String =
    "Nothing was taken today. The first prompt comes on $firstPromptDay, then every ${cadenceWord(freq)}. " +
        "Cancel anytime from its card under Recurring gifts."

/** Today's prompt could not go out, but the schedule stands. */
fun scheduledSetUpLine(freq: Int, firstPromptDay: String): String =
    "Your ${if (freq == FREQ_WEEKLY) "weekly" else "monthly"} gift is set up — the first prompt comes on $firstPromptDay."

/** Said over today's prompt when the schedule starts with a gift now. */
fun firstChargeNote(freq: Int): String =
    "Your ${if (freq == FREQ_WEEKLY) "weekly" else "monthly"} gift is set up — this is the first."

// ── Where it stands ──

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

/** Running: it prompts, and can be paused. */
fun scheduleRunning(status: String): Boolean = status.trim().lowercase() == "active"

/** Why a paused schedule is paused, and whether the member can resume it
 *  here (a pledge's schedule follows its pledge). */
data class PauseView(val line: String, val canResume: Boolean)

/** Null while the schedule is not paused. */
fun schedulePauseView(s: GivingSchedule, today: LocalDate = LocalDate.now(NAIROBI_ZONE)): PauseView? {
    if (s.status.trim().lowercase() != "paused") return null
    return when (s.pauseReason?.trim()?.lowercase()) {
        "failures" -> PauseView(
            "Paused after ${s.consecutiveFailures.takeIf { it > 0 } ?: 3} prompts didn't go through",
            canResume = true,
        )
        "member" -> PauseView(
            s.resumeOn?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                ?.let { "Paused until ${org.nuruplace.member.util.NuruDates.day(it, today)}" } ?: "Paused",
            canResume = true,
        )
        "pledge" -> PauseView("Paused with its pledge — resume the pledge in Partners", canResume = false)
        else -> PauseView("Paused", canResume = true)
    }
}

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

// ── Collecting a pledge (Giving Cycle 5) ──

/** "Collects your pledge “Kenya trip”"; null for a gift that pays no pledge. */
fun schedulePledgeLine(s: GivingSchedule): String? =
    s.pledge?.title?.trim()?.takeIf { it.isNotEmpty() }?.let { "Collects your pledge “$it”" }

/** What the next prompt asks when it is not the whole amount — "Next: KSh
 *  3,000 — the rest of what's due", or "Nothing to pay next time — your
 *  pledge is already paid" — else null (the whole amount, or none coming). */
fun scheduleNextAmountLine(s: GivingSchedule): String? {
    val next = s.nextAmountMinor ?: return null
    return when {
        next <= 0L -> "Nothing to pay next time — your pledge is already paid"
        next < s.amountMinor -> "Next: ${money(next, s.currency)} — the rest of what's due"
        else -> null
    }
}

/** The MONTHLY pledge this gift collects, found among [pledges]: its amount
 *  and day are changed on the pledge, never here. Null when it collects
 *  none, a total pledge, or one not among [pledges] — the server then
 *  answers a change itself (422 with details.pledge_id). */
fun monthlyPledgeCollected(s: GivingSchedule, pledges: List<Pledge>): Pledge? {
    val id = s.pledge?.pledgeId?.takeIf { it.isNotBlank() } ?: return null
    return pledges.firstOrNull { it.pledgeId == id && it.shape == "monthly" }
}

// ── The sheet's words (iOS ScheduleDetailSheet) ──


/** "Every Sunday" · "Every month on the 31st" — "Every week" / "Every month"
 *  when its day cannot be read (iOS dayLine). */
fun scheduleDayLine(s: GivingSchedule): String {
    val weekly = freqOf(s.frequency) == FREQ_WEEKLY
    val day = scheduleDay(s) ?: return if (weekly) "Every week" else "Every month"
    return if (weekly) "Every ${weekdayName(day)}" else "Every month on the ${ordinal(day)}"
}

/** The sheet's "Next prompt": "Mon 5 Oct" (the year when it isn't this
 *  year) — "None while paused", as a paused gift's old date is no promise. */
fun scheduleNextPromptLine(s: GivingSchedule, today: LocalDate = LocalDate.now(NAIROBI_ZONE)): String =
    if (s.status.trim().lowercase() == "paused") "None while paused"
    else parseNairobi(s.nextRunAt)?.toLocalDate()?.let { org.nuruplace.member.util.NuruDates.day(it, today) } ?: "—"

/** Under "Until a date": when the gift comes back on its own. */
fun pauseComesBackLine(date: LocalDate, today: LocalDate = LocalDate.now(NAIROBI_ZONE)): String =
    "It comes back on its own at its next day on or after ${org.nuruplace.member.util.NuruDates.day(date, today)}."

/** The sheet's Change form, as typed (iOS ScheduleDraft). */
data class ScheduleDraft(val amountText: String, val day: Int, val numberText: String, val useProfile: Boolean)

/** The form as the gift is now. */
fun scheduleDraftOf(s: GivingSchedule): ScheduleDraft = ScheduleDraft(
    amountText = "${s.amountMinor / 100}",
    day = scheduleDay(s) ?: if (freqOf(s.frequency) == FREQ_WEEKLY) 0 else 1,
    numberText = s.phoneNumber?.takeIf { it.isNotBlank() }?.let(::kenyanMobileDisplay).orEmpty(),
    useProfile = s.phoneNumber.isNullOrBlank(),
)

/** What Save would send — null when nothing changed or something is wrong —
 *  and why it cannot be sent, in the server's terms (iOS ScheduleEdit.plan). */
data class ScheduleChangePlan(val patch: UpdateScheduleBody?, val problem: String?)

/** Whole shillings inside the rail's limits ([rail], null = the server's
 *  check alone), a real day, a Kenyan mobile number or the profile's. Only
 *  what changed travels (scheduleEditPatch). */
fun scheduleChangePlan(d: ScheduleDraft, s: GivingSchedule, rail: GiveMethodOption?): ScheduleChangePlan {
    fun no(why: String) = ScheduleChangePlan(null, why)
    val typed = d.amountText.filter { !it.isWhitespace() && it != ',' }
    if (typed.isEmpty()) return no("Enter an amount.")
    val ksh = typed.takeIf { t -> t.all { it.isDigit() } }?.toIntOrNull() ?: return no("Enter whole shillings — no cents.")
    if (ksh <= 0) return no("Enter an amount.")
    rail?.let { giveAmountProblem(ksh * 100, it) }?.let { return no(it) }
    if (d.day !in scheduleDayOptions(s.frequency)) return no("Choose a day.")
    val number: PromptNumberChoice = if (d.useProfile) {
        PromptNumberChoice.Profile
    } else {
        val text = d.numberText.trim()
        if (text.isEmpty()) return no("Add the number to prompt, or use your profile number.")
        PromptNumberChoice.Own(kenyanMobileE164(text) ?: return no(PHONE_INVALID_MESSAGE))
    }
    return ScheduleChangePlan(scheduleEditPatch(s, amountMajor = ksh, day = d.day, number = number), null)
}

// ── Pausing ──

/** A member's pause may end from tomorrow to a year ahead (Nairobi dates;
 *  the server allows up to 366 days, so a year always passes). */
fun pauseDateRange(today: LocalDate): ClosedRange<LocalDate> = today.plusDays(1)..today.plusYears(1)

fun pauseDateAllowed(date: LocalDate, today: LocalDate): Boolean = date in pauseDateRange(today)

// ── Changing it ──

/** The day a schedule keeps: monthly → its own day of the month (anchor_day,
 *  else its next prompt's); weekly → its next prompt's weekday, Sunday = 0. */
fun scheduleDay(s: GivingSchedule): Int? {
    val next = parseNairobi(s.nextRunAt)
    return if (freqOf(s.frequency) == FREQ_WEEKLY) next?.dayOfWeek?.value?.rem(7)
    else s.anchorDay?.takeIf { it in 1..31 } ?: next?.dayOfMonth
}

/** The days a schedule may move to: weekly 0–6 (Sunday = 0), monthly 1–31. */
fun scheduleDayOptions(frequency: String): IntRange = if (freqOf(frequency) == FREQ_WEEKLY) 0..6 else 1..31

/** A day's chip: "Sun" … "Sat" weekly, "1" … "31" monthly. */
fun scheduleDayChip(frequency: String, day: Int): String =
    if (freqOf(frequency) == FREQ_WEEKLY) weekdayName(day).take(3) else "$day"

/** The number a changed schedule prompts: its own, or back to the profile's. */
sealed interface PromptNumberChoice {
    data object Profile : PromptNumberChoice
    data class Own(val e164: String) : PromptNumberChoice
}

/** The number a schedule prompts today, as a choice. */
fun promptNumberChoiceOf(s: GivingSchedule): PromptNumberChoice =
    kenyanMobileE164(s.phoneNumber)?.let { PromptNumberChoice.Own(it) } ?: PromptNumberChoice.Profile

/**
 * The PATCH for what the member changed — only changed fields travel, so an
 * untouched amount is never re-checked and an untouched day never moves the
 * next prompt. The number is tri-state: unchanged → absent; back to the
 * profile → JSON null; another number → that number. Null when nothing changed.
 */
fun scheduleEditPatch(
    s: GivingSchedule,
    amountMajor: Int? = null,
    day: Int? = null,
    number: PromptNumberChoice? = null,
    headsUp: Boolean? = null,
): UpdateScheduleBody? {
    val amount = amountMajor?.times(100)?.takeIf { it > 0 && it != s.amountMinor }
    val newDay = day?.takeIf { it in scheduleDayOptions(s.frequency) && it != scheduleDay(s) }
    val phone: JsonElement? = number?.takeIf { it != promptNumberChoiceOf(s) }?.let {
        when (it) {
            PromptNumberChoice.Profile -> JsonNull
            is PromptNumberChoice.Own -> JsonPrimitive(it.e164)
        }
    }
    val heads = headsUp?.takeIf { it != s.headsUp }
    if (amount == null && newDay == null && phone == null && heads == null) return null
    return UpdateScheduleBody(amountMinor = amount, day = newDay, phoneNumber = phone, headsUp = heads)
}

// ── "Your rhythm" (docs/PARTNERS_PROGRAMME.md §3a) ──

/** The member's soonest running schedule — the one the rhythm row names. */
fun rhythmSchedule(schedules: List<GivingSchedule>): GivingSchedule? =
    schedules.filter { scheduleRunning(it.status) }
        .minByOrNull { parseNairobi(it.nextRunAt)?.toInstant() ?: Instant.MAX }

/** "KSh 500 every Sunday · next Sun 5 Oct" / "KSh 500 every month on the
 *  31st · next Mon 30 Nov, the last day of November" — the day a monthly
 *  gift keeps (clamped to a short month on the calendar, never renamed), and
 *  when it next prompts, saying so when a short month moved it (iOS
 *  ScheduleRhythm.next). */
fun rhythmText(s: GivingSchedule): String {
    val next = parseNairobi(s.nextRunAt)
    val cadence = if (freqOf(s.frequency) == FREQ_WEEKLY) {
        next?.let { "every ${weekdayName(it.dayOfWeek.value % 7)}" } ?: "every week"
    } else {
        scheduleDay(s)?.let { "every month on the ${ordinal(it)}" } ?: "every month"
    }
    val clamped = next != null && freqOf(s.frequency) != FREQ_WEEKLY && (s.anchorDay ?: 0) > next.dayOfMonth
    val nextPart = next?.let {
        " · next ${it.format(RHYTHM_DAY_FMT)}" +
            (if (clamped) ", the last day of ${it.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}" else "")
    }.orEmpty()
    return "${money(s.amountMinor, s.currency)} $cadence$nextPart"
}
