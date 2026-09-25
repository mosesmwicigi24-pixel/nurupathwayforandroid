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
//   Pending   the server's `pending` rows (started, not settled) sit above
//             them as PROCESSING with a "Waiting for M-Pesa" chip — and are
//             counted in NO total: every figure here reads `payments` only.
//   Years     the chips: this year back to the join year, at most four —
//             the same list the Partners tab shows.
//
// Statement v2 (docs/PARTNERS_PROGRAMME.md §3d, 2026-09-25) adds the
// impact-led top: the hero's three tiles, the FAITHFULNESS strip and its
// line, COMMITMENTS' "Remaining this year", "Church raised N%" and the
// SINCE YOU BEGAN line. Each reads a server block and answers null when the
// block is absent, so the screen hides it — never a zero, and never
// "0 disciples": below the first, the tile shows progress toward it.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.PartnerSeason
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.StatementFaithfulness
import org.nuruplace.member.data.net.StatementImpact
import org.nuruplace.member.data.net.StatementMonthStatus
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.StatementPendingPayment
import org.nuruplace.member.data.net.StatementPledge
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

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

/** The PROCESSING rows above PAYMENTS: the server's `pending` list,
 *  pledge-tied only (as every row here), newest first, minus any row that
 *  has meanwhile settled into `payments` (the two are read in one answer,
 *  but a row is never shown twice). Never summed anywhere. */
internal fun pendingPaymentRows(s: GivingStatement): List<StatementPendingPayment> {
    val settled = s.payments.map { it.transactionId }.filter { it.isNotBlank() }.toSet()
    return s.pending.orEmpty()
        .filter { !it.pledgeId.isNullOrBlank() && (it.transactionId.isBlank() || it.transactionId !in settled) }
        .sortedByDescending { it.at ?: "" }
}

/** The amber chip on a PROCESSING row: what the payment is waiting for. */
internal fun pendingChipText(method: String?): String = when (method?.trim()?.lowercase(Locale.ROOT)) {
    "mpesa" -> "Waiting for M-Pesa"
    "airtel" -> "Waiting for Airtel Money"
    else -> "Processing"
}

// ── Statement v2: the impact-led top (spec §3d) ──────────────────────────────

/** What one disciple through a level costs (giving-tier economics, tiers.ts:
 *  KSh 20,000). Used ONLY when the server's `per_disciple_minor` is missing
 *  or non-positive, so the progress tile never divides by zero. */
internal const val DISCIPLE_COST_MINOR = 2_000_000

/** The hero's first tile. Never "0 disciples": at one or more it is the
 *  count; below the first it is progress toward it. */
internal sealed interface DisciplesTile {
    /** `count` ≥ 1 disciples carried through a level. */
    data class Carried(val count: Int) : DisciplesTile

    /** Below the first: `towardMinor` of `perDiscipleMinor`, and the bar's
     *  fill 0..1. */
    data class Toward(val towardMinor: Int, val perDiscipleMinor: Int, val fraction: Float) : DisciplesTile {
        /** "KSh 6,000 of 20,000 toward carrying one disciple through a level". */
        val text: String
            get() = "${ksh(towardMinor)} of ${PartnerFormat.grouped(perDiscipleMinor / 100)} toward carrying one disciple through a level"
    }
}

internal fun disciplesTile(impact: StatementImpact): DisciplesTile {
    if (impact.disciplesCarried >= 1) return DisciplesTile.Carried(impact.disciplesCarried)
    val per = impact.perDiscipleMinor.takeIf { it > 0 } ?: DISCIPLE_COST_MINOR
    // The server's toward_next; an older or partial answer that left it at 0
    // while money was paid reads the paid amount's remainder instead.
    val toward = (impact.towardNextMinor.takeIf { it > 0 } ?: (maxOf(impact.paidMinor, 0) % per)).coerceIn(0, per)
    return DisciplesTile.Toward(toward, per, toward.toFloat() / per)
}

/** The Kept tile's "5 of 6" (kept = on time + late), or null to hide it —
 *  no faithfulness block, or nothing has come due yet. */
internal fun keptTileValue(f: StatementFaithfulness?): String? {
    if (f == null || f.dueCount <= 0) return null
    return "${f.keptOnTime + f.late} of ${f.dueCount}"
}

/** A compact amount in major units for a narrow tile — "950", "9.5k",
 *  "22k", "1.5M". Always rounded DOWN so the short form never overstates;
 *  the tile's content description carries the full figure. */
internal fun compactAmount(minor: Int): String {
    val major = maxOf(minor, 0) / 100
    fun tenths(n: Int, unit: Int, suffix: String): String {
        val t = n / (unit / 10) // floor to one decimal of `unit`
        return if (t % 10 == 0) "${t / 10}$suffix" else "${t / 10}.${t % 10}$suffix"
    }
    return when {
        major < 1_000 -> "$major"
        major < 10_000 -> tenths(major, 1_000, "k")
        major < 1_000_000 -> "${major / 1_000}k"
        major < 10_000_000 -> tenths(major, 1_000_000, "M")
        else -> "${major / 1_000_000}M"
    }
}

/** One square of the FAITHFULNESS strip. */
internal enum class MonthMark { Kept, Late, Missed, Upcoming, None }

internal fun monthMark(status: String?): MonthMark = when (status?.trim()?.lowercase(Locale.ROOT)) {
    "kept" -> MonthMark.Kept
    "late" -> MonthMark.Late
    "missed" -> MonthMark.Missed
    "upcoming" -> MonthMark.Upcoming
    else -> MonthMark.None
}

/** Jan..Dec, exactly twelve, whatever order or gaps the wire has (a month
 *  the server left out reads as none). Null — hide the card — when there is
 *  no `months` block, or no month carries anything but none (a partner with
 *  only total pledges has no monthly rhythm to show). */
internal fun faithfulnessMarks(months: List<StatementMonthStatus>?): List<MonthMark>? {
    if (months.isNullOrEmpty()) return null
    val marks = (1..12).map { m -> monthMark(months.firstOrNull { it.month == m }?.status) }
    return marks.takeIf { list -> list.any { it != MonthMark.None } }
}

/** "Kept on time 6 months · late 1 (Jul) · next due 5 Oct" under the strip.
 *  Counts are the strip's own months; each part appears only when it has
 *  something to say, and `nextDue` only for the year being lived. Null when
 *  nothing is left to say. */
internal fun faithfulnessLine(marks: List<MonthMark>, nextDue: LocalDate?, today: LocalDate): String? {
    val kept = marks.count { it == MonthMark.Kept }
    val lateMonths = marks.withIndex().filter { it.value == MonthMark.Late }
        .map { Month.of(it.index + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    val parts = buildList {
        if (kept > 0) add("kept on time $kept month${if (kept == 1) "" else "s"}")
        if (lateMonths.isNotEmpty()) add("late ${lateMonths.size} (${lateMonths.joinToString(", ")})")
        nextDue?.let { d ->
            add("next due ${if (d.year == today.year) PartnerFormat.dayMonth(d) else PartnerFormat.dayMonthYear(d.toString())}")
        }
    }
    if (parts.isEmpty()) return null
    return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
}

/** FAITHFULNESS' "next due": the earliest server `progress.next_due` on or
 *  after `today` across ACTIVE MONTHLY pledges. The server's instalment
 *  ledger advances it once an instalment is paid (26 Sep paid → 26 Oct; a
 *  pre-payment → 26 Nov), so a paid instalment is never shown as still due —
 *  the DUE list is NOT read (it holds an instalment until its payment
 *  settles). `due_day` stands in only for a pledge whose next_due is absent
 *  (an older server). Null when none. */
internal fun nextPledgeDue(p: Partnership?, today: LocalDate): LocalDate? {
    if (p == null) return null
    return p.pledges
        .filter { it.status == "active" && it.shape != "total" }
        .mapNotNull { pl -> partnerDate(pl.progress.nextDue) ?: pl.dueDay?.let { nextDueDayDate(it, today) } }
        .filter { !it.isBefore(today) }
        .minOrNull()
}

/** The first `dueDay`-of-the-month date on or after `today` (days 1–28,
 *  spec §1) — the fallback for a pledge the server sent no next_due for. */
internal fun nextDueDayDate(dueDay: Int, today: LocalDate): LocalDate {
    val thisMonth = today.withDayOfMonth(dueDay.coerceIn(1, 28))
    return if (thisMonth.isBefore(today)) thisMonth.plusMonths(1) else thisMonth
}

/** COMMITMENTS' header figure: Σ remaining_year_minor, or null (hidden) when
 *  no row carries it — an older server, or rows derived locally. */
internal fun remainingThisYear(rows: List<StatementPledge>): Int? {
    if (rows.none { it.remainingYearMinor != null }) return null
    return rows.sumOf { it.remainingYearMinor ?: maxOf(it.pledgedMinor - it.paidMinor, 0) }
}

/** "Church raised 42%" under a department-need pledge; null otherwise.
 *  Rounded down and held to 0..100 — never more than the need. */
internal fun churchRaisedLine(e: StatementPledge): String? {
    val pct = e.churchProgressPercent ?: return null
    if (pct.isNaN()) return null
    return "Church raised ${pct.toInt().coerceIn(0, 100)}%"
}

/** SINCE YOU BEGAN: "4 levels completed and 12 plans finished across the
 *  church while you have partnered." A zero half is left out; with nothing
 *  to say at all (or no season block) the card is hidden. */
internal fun seasonLine(season: PartnerSeason?): String? {
    if (season == null) return null
    val parts = listOfNotNull(
        season.levelsCompleted.takeIf { it > 0 }?.let { "$it level${if (it == 1) "" else "s"} completed" },
        season.plansFinished.takeIf { it > 0 }?.let { "$it plan${if (it == 1) "" else "s"} finished" },
    )
    if (parts.isEmpty()) return null
    return "${parts.joinToString(" and ")} across the church while you have partnered."
}
