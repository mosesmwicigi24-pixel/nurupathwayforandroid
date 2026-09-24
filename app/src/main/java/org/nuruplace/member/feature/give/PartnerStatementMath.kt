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
private const val DEFAULT_DUE_DAY = 1

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
 *  "13 of 12"); M = its due dates elapsed this year through `today`. */
internal fun keptThisYear(pl: Pledge, payments: List<StatementPayment>, today: LocalDate): Pair<Int, Int> {
    val elapsed = dueDatesInYear(today.year, pl.dueDay ?: DEFAULT_DUE_DAY, partnerDate(pl.createdAt), through = today)
    val kept = payments.count { it.pledgeId == pl.pledgeId && it.pledgeId?.isNotBlank() == true }
    return minOf(kept, elapsed) to elapsed
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
