// The Partners tab's arithmetic (docs/PARTNERS_PROGRAMME.md §3, "Partner-only
// statement rule") — pure functions over the wire DTOs, kept out of the
// composables so the numbers the member sees are pinned by unit tests and
// match iOS exactly. Nothing here is server truth: the server sends pledges
// and payments; these are the two derived views the design shows.
//
//   Paid      = Σ statement payments[].amount_minor where pledge_id is set
//   Pledged   = Σ over pledges not cancelled:
//                 monthly → amount_minor × number of due_day dates in the year
//                           from max(pledge created_at, 1 Jan) through 31 Dec
//                 total   → target_minor if due_on falls in the year, else 0
//   Remaining = max(Pledged − Paid, 0)
//
// Gifts without a pledge are never counted or shown on the Partners tab; they
// stay in the full statement (GivingStatementScreen).
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.DueItem
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.StatementPayment
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The STATEMENT card's three numbers, all minor units. */
internal data class StatementSummary(val pledgedMinor: Int, val paidMinor: Int, val remainingMinor: Int)

/** Every monthly pledge has a due day 1..28 (spec §1); a row without one is
 *  malformed, and the 1st is the least-surprising day to count from. */
internal const val DEFAULT_DUE_DAY = 1

/** ISO instant / offset timestamp / bare date → the calendar date, or null. */
internal fun partnerDate(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(iso).toLocalDate() }.getOrNull()
        ?: runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(iso.take(10)) }.getOrNull()
}

/** How many `dueDay`-of-the-month dates fall in `year` on or after `from`
 *  (clamped to 1 Jan) and on or before `through` (clamped to 31 Dec). */
internal fun dueDatesInYear(year: Int, dueDay: Int, from: LocalDate?, through: LocalDate? = null): Int {
    val jan1 = LocalDate.of(year, 1, 1)
    val dec31 = LocalDate.of(year, 12, 31)
    val start = if (from != null && from.isAfter(jan1)) from else jan1
    val end = if (through != null && through.isBefore(dec31)) through else dec31
    if (end.isBefore(start)) return 0
    val day = dueDay.coerceIn(1, 28)
    return (1..12).count { m ->
        val d = LocalDate.of(year, m, day)
        !d.isBefore(start) && !d.isAfter(end)
    }
}

/** What one pledge contributes to the year's Pledged figure (rule above). */
internal fun pledgedInYear(pl: Pledge, year: Int): Int {
    if (pl.status == "cancelled") return 0
    return when (pl.shape) {
        "total" -> if (partnerDate(pl.dueOn)?.year == year) (pl.targetMinor ?: 0) else 0
        else -> (pl.amountMinor ?: 0) * dueDatesInYear(year, pl.dueDay ?: DEFAULT_DUE_DAY, partnerDate(pl.createdAt))
    }
}

/** The pledge-tied payments only — the rows the Partners tab shows. */
internal fun pledgePayments(payments: List<StatementPayment>): List<StatementPayment> =
    payments.filter { !it.pledgeId.isNullOrBlank() }

internal fun statementSummary(year: Int, pledges: List<Pledge>, payments: List<StatementPayment>): StatementSummary {
    val pledged = pledges.sumOf { pledgedInYear(it, year) }
    val paid = pledgePayments(payments).sumOf { it.amountMinor }
    return StatementSummary(pledgedMinor = pledged, paidMinor = paid, remainingMinor = maxOf(pledged - paid, 0))
}

/** "N of M kept this year" for a monthly pledge: N = payments this year
 *  attributed to it (capped at M, so an early or doubled gift never reads
 *  "13 of 12"); M = its due dates elapsed this year through `today`. The
 *  LOCAL estimate — [pledgeKeptThisYear] prefers the server's figures. */
internal fun keptThisYear(pl: Pledge, payments: List<StatementPayment>, today: LocalDate): Pair<Int, Int> {
    val elapsed = dueDatesInYear(today.year, pl.dueDay ?: DEFAULT_DUE_DAY, partnerDate(pl.createdAt), through = today)
    val kept = payments.count { it.pledgeId == pl.pledgeId && it.pledgeId?.isNotBlank() == true }
    return minOf(kept, elapsed) to elapsed
}

/** The pledge card's (kept, due) for this year — SERVER FIRST: the current
 *  year's statement `pledges[]` entry for this pledge, whose figures come
 *  from the server's FIFO instalment ledger (payments fill due dates oldest
 *  first; kept = on time or late; due_count counts only RESOLVED instalments,
 *  so one due today and still unpaid is not yet counted). Only when that
 *  statement or that entry is absent — an older server, a statement not yet
 *  loaded — does the local [keptThisYear] stand in. `currentYearStatement`
 *  must be THIS year's GET /giving/statements answer. */
internal fun pledgeKeptThisYear(pl: Pledge, currentYearStatement: GivingStatement?, today: LocalDate): Pair<Int, Int> {
    currentYearStatement?.pledges?.firstOrNull { it.pledgeId == pl.pledgeId }?.let { e ->
        val due = maxOf(e.dueCount, 0)
        return e.kept.coerceIn(0, due) to due
    }
    return keptThisYear(pl, currentYearStatement?.payments.orEmpty(), today)
}

/** "3 of 4 kept this year" — or null, saying nothing, while nothing has
 *  come due (M = 0), whichever source answered. */
internal fun pledgeKeptLine(pl: Pledge, currentYearStatement: GivingStatement?, today: LocalDate): String? {
    val (kept, due) = pledgeKeptThisYear(pl, currentYearStatement, today)
    return if (due <= 0) null else "$kept of $due kept this year"
}

/** DUE rows: "today" · "tomorrow" · "in N days" (up to two weeks) · else the
 *  date as "d MMM". A past date reads as its date, never a negative count. */
internal fun dueRelativeLabel(dueOn: LocalDate, today: LocalDate, dateLabel: (LocalDate) -> String): String {
    val days = ChronoUnit.DAYS.between(today, dueOn)
    return when {
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        days in 2L..14L -> "in $days days"
        else -> dateLabel(dueOn)
    }
}

/** What a DUE row shows once the server's `pending_minor` is known — the
 *  part of this instalment already on its way (payments started in the last
 *  15 minutes, still processing). */
internal data class DueRowView(
    /** What the row leads with: the instalment itself, or — when part of it
     *  is already on its way — the uncovered remainder, which Pay presets. */
    val leadMinor: Int,
    /** In place of Pay while the whole amount is on its way: "Waiting for
     *  M-Pesa" · "Waiting for Airtel Money" · "Processing". */
    val processingChip: String? = null,
    /** Under a partly covered row: "KSh 500 processing". */
    val processingNote: String? = null,
)

/** The DUE row's presentation rule. For a pledge's Pay row: pending ≥ the
 *  amount → an amber Processing chip instead of Pay (a second tap would pay
 *  the instalment twice); 0 < pending < amount → Pay stays, for the
 *  uncovered remainder, with the part in flight said underneath; nothing
 *  pending → as before. A schedule row and a Resume row are never changed.
 *  `pendingMethod` names the chip when known ([pendingMethodFor]). */
internal fun dueRowView(d: DueItem, pendingMethod: String?): DueRowView {
    val pending = if (d.kind == "pledge" && d.action == "pay") maxOf(d.pendingMinor, 0) else 0
    return when {
        pending == 0 -> DueRowView(d.amountMinor)
        pending >= d.amountMinor -> DueRowView(d.amountMinor, processingChip = pendingChipText(pendingMethod))
        else -> DueRowView(d.amountMinor - pending, processingNote = "${ksh(pending)} processing")
    }
}

/** How this pledge's newest payment in flight is being paid (mpesa |
 *  airtel | card | paypal), read off this year's statement `pending` rows;
 *  null when the statement is not loaded or names none. */
internal fun pendingMethodFor(pledgeId: String, currentYearStatement: GivingStatement?): String? =
    currentYearStatement?.let { pendingPaymentRows(it) }
        ?.firstOrNull { it.pledgeId == pledgeId }
        ?.method?.takeIf { it.isNotBlank() }

/**
 * The STANDING card's second line.
 *
 *  · Any live MONTHLY pledge → "3 commitments kept this year · on track",
 *    the count being this year's statement `faithfulness` (kept on time +
 *    late) — the same ledger as the pledge cards' "N of M kept". Never
 *    `partnership.kept`: that counts recurring-schedule cycles only, so a
 *    pledge-only partner read "0 gifts kept" beside a card saying "2 of 2
 *    kept". The count is left out while it is 0 with nothing due yet, and
 *    while the statement has not answered.
 *  · Schedule-only → "N gifts kept · on track" (cycles COLLECTED).
 *  · Neither → the state alone ("On track").
 *
 * The state: "paused" when the partnership is, else "behind" when any
 * active pledge is (the server's label), else "on track".
 */
internal fun standingKeptLine(p: Partnership, currentYearStatement: GivingStatement?, paused: Boolean): String {
    val state = when {
        paused -> "paused"
        p.pledges.any { it.status == "active" && it.progress.label == "behind" } -> "behind"
        else -> "on track"
    }
    val hasMonthlyPledge = p.pledges.any { it.status != "cancelled" && it.shape != "total" }
    val hasSchedule = p.scheduleId != null || p.rhythm != null
    val count = when {
        hasMonthlyPledge -> currentYearStatement?.faithfulness?.let { f ->
            val kept = maxOf(f.keptOnTime + f.late, 0)
            if (kept == 0 && f.dueCount <= 0) null else "$kept ${if (kept == 1) "commitment" else "commitments"} kept this year"
        }
        hasSchedule -> "${p.kept} ${if (p.kept == 1) "gift" else "gifts"} kept"
        else -> null
    }
    return listOfNotNull(count, state).joinToString(" · ").replaceFirstChar { it.uppercase() }
}

/** A "when" as the row or card says it, and whether it is overdue (said in
 *  amber, GIVE.goldChipText 0xFF7A5A14). */
internal data class WhenLabel(val text: String, val overdue: Boolean)

/** "10 Aug", or "10 Aug 2025" outside the year being lived. */
private fun dayMonthIn(d: LocalDate, today: LocalDate): String =
    if (d.year == today.year) PartnerFormat.dayMonth(d) else PartnerFormat.dayMonthYear(d.toString())

/**
 * The DUE row's "when". A pledge instalment already past reads "overdue
 * since 10 Aug" — "2 overdue since 10 Aug" with two or more behind (the
 * amount is then the server's catch-up total) — dated by the server's
 * `overdue_since` when sent, else `due_on`. Otherwise "today" · "tomorrow" ·
 * "in N days" (to two weeks) · the date. A recurring-gift row is never
 * "overdue" (nothing is owed on a schedule); a past date there reads as its
 * date.
 */
internal fun dueWhen(d: DueItem, today: LocalDate): WhenLabel {
    val due = partnerDate(d.dueOn) ?: return WhenLabel("soon", false)
    if (d.kind == "pledge" && due.isBefore(today)) {
        val since = partnerDate(d.overdueSince) ?: due
        val lead = if (d.overdueCount >= 2) "${d.overdueCount} overdue since" else "overdue since"
        return WhenLabel("$lead ${dayMonthIn(since, today)}", overdue = true)
    }
    return WhenLabel(dueRelativeLabel(due, today, PartnerFormat::dayMonth), overdue = false)
}

/** The pledge card's foot: "Next 5 Oct", or — its next instalment already
 *  past — "Overdue since 10 Aug" (amber). Null when the server names no
 *  next due. */
internal fun pledgeNextLabel(pl: Pledge, today: LocalDate): WhenLabel? {
    val next = partnerDate(pl.progress.nextDue) ?: return null
    return if (next.isBefore(today)) WhenLabel("Overdue since ${dayMonthIn(next, today)}", overdue = true)
    else WhenLabel("Next ${PartnerFormat.dayMonth(next)}", overdue = false)
}
