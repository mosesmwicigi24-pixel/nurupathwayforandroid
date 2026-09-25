// The Partners statement screen's derivations (PartnersStatementScreen.kt;
// owner 2026-09-25: "have the statement separate for partners") — pure
// functions over the wire DTOs so the numbers a partner sees are pinned by
// unit tests and match iOS exactly. Nothing here is server truth:
//
//   Summary   the server's pledged/paid/remaining_minor when it sends them
//             (contract 2026-09-25), else PartnerStatementMath.statementSummary
//             over the same payments — the two agree by construction.
//   Pledges   the server's statements.pledges[] when present (even empty),
//             else one row per live pledge from GET /giving/partnership with
//             the same local math (pledged, paid, "k of d kept").
//   Payments  pledge-tied only (never a gift outside a pledge — those stay in
//             the giving statement), grouped by month newest first, each month
//             subtotalled. A row whose date cannot be read keeps its money in
//             a trailing "undated" group rather than vanishing.
//   Years     the chips: this year back to the join year, at most four —
//             the same list the Partners tab shows.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.StatementPledge
import java.time.LocalDate

/** One month of pledge-tied payments, newest first, with its subtotal. A
 *  `month` of 0 is the trailing group for rows without a readable date. */
internal data class StatementMonth(
    val year: Int,
    val month: Int,
    val payments: List<StatementPayment>,
    val subtotalMinor: Int,
) {
    val undated: Boolean get() = month == 0
}

/** The year chips: `currentYear` back to `joinYear`, never more than `max`,
 *  and never fewer than the current year (a join date in the future, or none
 *  at all, still leaves this year). */
internal fun partnerStatementYears(currentYear: Int, joinYear: Int?, max: Int = 4): List<Int> {
    val floor = maxOf(minOf(joinYear ?: currentYear, currentYear), currentYear - (max - 1))
    return (currentYear downTo floor).toList()
}

/** Pledged / Paid / Remaining for `year`: the server's numbers when it sends
 *  pledged AND paid (remaining derived if it left that one out), else the
 *  local rule over the statement's payments and the partnership's pledges. */
internal fun partnerStatementSummary(year: Int, s: GivingStatement, pledges: List<Pledge>): StatementSummary {
    val pledged = s.pledgedMinor
    val paid = s.paidMinor
    if (pledged != null && paid != null) {
        return StatementSummary(pledged, paid, s.remainingMinor ?: maxOf(pledged - paid, 0))
    }
    return statementSummary(year, pledges, s.payments)
}

/** "k of d kept" for a monthly pledge in `year`: d = its due dates in that
 *  year that have come (all of them once the year is past, none of a year yet
 *  to come); k = payments attributed to it, capped at d. A total pledge has
 *  no cycle to keep: 0 of 0. */
internal fun keptInYear(pl: Pledge, paymentCount: Int, year: Int, today: LocalDate): Pair<Int, Int> {
    if (pl.shape == "total") return 0 to 0
    val through = when {
        year < today.year -> null
        year == today.year -> today
        else -> return 0 to 0
    }
    val due = dueDatesInYear(year, pl.dueDay ?: DEFAULT_DUE_DAY, partnerDate(pl.createdAt), through)
    return minOf(paymentCount, due) to due
}

/** The YOUR PLEDGES rows: the server's when it sends `pledges` (an empty list
 *  is an answer), else every live pledge that was owed or paid something in
 *  `year`, with the same local math the Partners tab uses. */
internal fun partnerStatementPledges(year: Int, s: GivingStatement, pledges: List<Pledge>, today: LocalDate): List<StatementPledge> {
    s.pledges?.let { return it }
    val tied = pledgePayments(s.payments)
    return pledges.filter { it.status != "cancelled" }.mapNotNull { pl ->
        val pledged = pledgedInYear(pl, year)
        val mine = tied.filter { it.pledgeId == pl.pledgeId }
        val paid = mine.sumOf { it.amountMinor }
        if (pledged == 0 && paid == 0) return@mapNotNull null
        val (kept, due) = keptInYear(pl, mine.size, year, today)
        StatementPledge(
            pledgeId = pl.pledgeId, title = pl.displayTitle, shape = pl.shape,
            amountMinor = pl.amountMinor, targetMinor = pl.targetMinor, currency = pl.currency,
            status = pl.status, dueDay = pl.dueDay, dueOn = pl.dueOn, createdAt = pl.createdAt,
            pledgedMinor = pledged, paidMinor = paid, kept = kept, dueCount = due,
        )
    }
}

/** "KSh 6,000 paid · 3 of 4 kept" for a monthly pledge with due dates behind
 *  it; "KSh 20,000 paid" for a total pledge, or one nothing has come due on. */
internal fun pledgeProgressLine(e: StatementPledge): String {
    val paid = "${ksh(e.paidMinor)} paid"
    return if (e.shape == "total" || e.dueCount <= 0) paid else "$paid · ${e.kept} of ${e.dueCount} kept"
}

/** "KSh 2,000 monthly · due on the 5th" · "KSh 50,000 by Dec 2026". */
internal fun pledgeAmountLine(e: StatementPledge): String = when (e.shape) {
    "total" -> "${ksh(e.targetMinor ?: 0)} by ${e.dueOn?.let(PartnerFormat::monthYear) ?: "a date"}"
    else -> "${ksh(e.amountMinor ?: 0)} monthly" + (e.dueDay?.let { " · due on the ${ordinal(it)}" } ?: "")
}

/** Pledge-tied payments by month, newest month first and newest row first
 *  within it, each month subtotalled; undated rows trail in one group. */
internal fun paymentsByMonth(payments: List<StatementPayment>): List<StatementMonth> {
    val tied = pledgePayments(payments)
    val dated = tied.mapNotNull { pay -> partnerDate(pay.occurredAt)?.let { it to pay } }
    val undated = tied.filter { partnerDate(it.occurredAt) == null }
    val months = dated
        .groupBy { (d, _) -> d.year * 100 + d.monthValue }
        .entries
        .sortedByDescending { it.key }
        .map { (key, rows) ->
            val ordered = rows
                .sortedWith(compareByDescending<Pair<LocalDate, StatementPayment>> { it.first }.thenByDescending { it.second.occurredAt ?: "" })
                .map { it.second }
            StatementMonth(key / 100, key % 100, ordered, ordered.sumOf { it.amountMinor })
        }
    return if (undated.isEmpty()) months else months + StatementMonth(0, 0, undated, undated.sumOf { it.amountMinor })
}

/** What the payments list adds up to — the year's total at its foot. */
internal fun statementYearTotal(months: List<StatementMonth>): Int = months.sumOf { it.subtotalMinor }
