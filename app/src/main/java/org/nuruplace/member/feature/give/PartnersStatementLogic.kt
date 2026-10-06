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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One month of pledge-tied payments, newest first, with its subtotal per
 *  currency (Giving Cycle 5: never one sum across currencies). A `month` of
 *  0 is the trailing group for rows without a readable date. */
internal data class StatementMonth(
    val year: Int,
    val month: Int,
    val payments: List<StatementPayment>,
    val subtotals: List<CurrencyAmount>,
) {
    val undated: Boolean get() = month == 0
}

/** Payment rows summed per currency, shillings first. */
internal fun paymentSums(rows: List<StatementPayment>): List<CurrencyAmount> =
    rows.groupBy { currencyCode(it.currency) }
        .map { (c, rs) -> CurrencyAmount(c, rs.sumOf { it.amountMinor.toLong() }) }
        .let { sums -> shillingsFirst(sums.map { it.currency }).map { c -> sums.first { it.currency == c } } }

/** The year chips: `currentYear` back to `joinYear`, never more than `max`,
 *  and never fewer than the current year (a join date in the future, or none
 *  at all, still leaves this year). */
internal fun partnerStatementYears(currentYear: Int, joinYear: Int?, max: Int = 4): List<Int> {
    val floor = maxOf(minOf(joinYear ?: currentYear, currentYear), currentYear - (max - 1))
    return (currentYear downTo floor).toList()
}

/** Pledged / Paid / Remaining for `year`: the server's numbers when it sends
 *  pledged AND paid (remaining derived from those two if it left that one
 *  out — it never does; the server's remaining is owed per pledge), else the
 *  local rule over the statement's payments and the partnership's pledges.
 *  One currency's worth — see [partnerStatementSummaries] for more than one. */
internal fun partnerStatementSummary(year: Int, s: GivingStatement, pledges: List<Pledge>): StatementSummary {
    val pledged = s.pledgedMinor
    val paid = s.paidMinor
    if (pledged != null && paid != null) {
        return StatementSummary(pledged, paid, s.remainingMinor ?: maxOf(pledged - paid, 0))
    }
    return statementSummary(year, pledges, s.payments)
}

/** The summary per currency. The server's own `summary_by_currency` (Giving
 *  Cycle 9: each currency's pledges against that currency's payments,
 *  shillings first) whenever it sends it — an empty list is an answer:
 *  nothing pledged or paid, KSh 0. From an older server (Giving Cycle 5's
 *  rule): its three numbers were one sum across currencies, so they stand
 *  only while everything in play is in ONE currency; with more than one,
 *  each currency is summed on its own by the local rule
 *  ([statementSummaries]). */
internal fun partnerStatementSummaries(year: Int, s: GivingStatement, pledges: List<Pledge>): List<CurrencyStatementSummary> {
    s.summaryByCurrency?.let { server ->
        return server.map { CurrencyStatementSummary(currencyCode(it.currency), it.pledgedMinor, it.paidMinor, it.remainingMinor) }
            .ifEmpty { listOf(CurrencyStatementSummary(currencyCode(s.summaryCurrency), 0, 0, 0)) }
    }
    val local = statementSummaries(year, pledges, s.payments)
    if (local.size > 1) return local
    val one = partnerStatementSummary(year, s, pledges)
    return listOf(CurrencyStatementSummary(local.firstOrNull()?.currency ?: GIVE_FORM_CURRENCY, one.pledgedMinor, one.paidMinor, one.remainingMinor))
}

/** The hero's Given tile: what was paid toward pledges, per currency, the
 *  server's order (shillings first) — never one sum. The server's per-currency
 *  paid when it sends it; else the impact's paid, in shillings (its costing
 *  is in shillings — Giving Cycle 9: it counts shillings only). Currencies
 *  with nothing paid are left out; nothing at all is 0 in the statement's
 *  own currency (iOS: a dollar partner's nothing is not "KSh 0"). */
internal fun givenTileAmounts(s: GivingStatement): List<CurrencyAmount> {
    val perCurrency = s.summaryByCurrency?.filter { it.paidMinor != 0 }?.map { CurrencyAmount(currencyCode(it.currency), it.paidMinor.toLong()) }
    return perCurrency?.takeIf { it.isNotEmpty() }
        ?: listOf(CurrencyAmount(currencyCode(s.summaryCurrency), (s.impact?.paidMinor ?: 0).toLong()))
}

/** "k of d kept" for a monthly pledge in `year`: d = its due dates in that
 *  year that have come (all of them once the year is past, none of a year yet
 *  to come) — from its start, none after its until_on; k = payments
 *  attributed to it, capped at d. A total pledge has no cycle to keep: 0 of 0. */
internal fun keptInYear(pl: Pledge, paymentCount: Int, year: Int, today: LocalDate): Pair<Int, Int> {
    if (pl.shape == "total") return 0 to 0
    val through = when {
        year < today.year -> null
        year == today.year -> today
        else -> return 0 to 0
    }
    val until = partnerDate(pl.untilOn)
    val end = when {
        until == null -> through
        through == null -> until
        else -> minOf(until, through)
    }
    val due = dueDatesInYear(year, pl.dueDay ?: DEFAULT_DUE_DAY, pledgeStart(pl), end)
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
        // Only money in the pledge's own currency counts toward it (the
        // server's rule since Giving Cycle 9).
        val mine = tied.filter { it.pledgeId == pl.pledgeId && currencyCode(it.currency) == currencyCode(pl.currency) }
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
 *  it; "KSh 20,000 paid" for a total pledge, or one nothing has come due on.
 *  In the pledge's own currency. */
internal fun pledgeProgressLine(e: StatementPledge): String {
    val paid = "${money(e.paidMinor, e.currency)} paid"
    return if (e.shape == "total" || e.dueCount <= 0) paid else "$paid · ${e.kept} of ${e.dueCount} kept"
}

/** "KSh 2,000 monthly · due on the 5th" · "KSh 50,000 · by 15 Dec" — the
 *  pledge card's own words (iOS pledgeAmountLine): the year only when it
 *  isn't this one, just the amount when the date is unknown. */
internal fun pledgeAmountLine(e: StatementPledge, today: LocalDate): String =
    promiseLine(e.shape == "total", e.amountMinor, e.targetMinor, e.currency, e.dueDay, e.dueOn, today)

/** COMMITMENTS' chip (iOS StatementPledgeRow.stateChip): Paused, Fulfilled
 *  and Cancelled by status; a live monthly pledge is "Behind" while fewer of
 *  its due dates are kept than have come, a live total one once its date
 *  has passed unfulfilled — otherwise "On track". */
internal fun statementPledgeState(e: StatementPledge, today: LocalDate): String = when (e.status) {
    "paused" -> "Paused"
    "fulfilled" -> "Fulfilled"
    "cancelled" -> "Cancelled"
    else -> when {
        e.shape == "monthly" -> if (e.kept < e.dueCount) "Behind" else "On track"
        partnerDate(e.dueOn)?.isBefore(today) == true -> "Behind"
        else -> "On track"
    }
}

/** A month group's head in PAYMENTS: "SEPTEMBER 2026" (iOS "MMMM yyyy",
 *  upper-cased); the trailing group of unreadable dates is "UNDATED". */
internal fun statementMonthLabel(m: StatementMonth): String =
    if (m.undated) "UNDATED"
    else "${Month.of(m.month).getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH)} ${m.year}"

private val WEEKDAY_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)

/** A payment row's day: "Sat 20" — the month is the group's head above
 *  (iOS StatementPaymentLine.dayLabel); "—" when the date can't be read. */
internal fun statementPaymentDay(iso: String?): String = partnerDate(iso)?.format(WEEKDAY_DAY) ?: "—"

/** A settled payment's second line on the statement page (iOS
 *  StatementPaymentLine.meta): the rail when the row names one, else the
 *  fund — the server's name, else the local one for its code — then the
 *  receipt code. Nothing is guessed. (The Partners tab's STATEMENT card says
 *  the fund first: [statementPaymentMeta].) */
internal fun statementLineMeta(pay: StatementPayment): String =
    listOfNotNull(
        pay.method?.takeIf { it.isNotBlank() }?.let(::giveMethodLabel)
            ?: pay.fundName?.takeIf { it.isNotBlank() }
            ?: pay.fund?.takeIf { it.isNotBlank() }?.let { giveFund(it).name },
        pay.receiptCode?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

/** The hero's thank-you: "Thank you, Grace." — the first word of the
 *  member's name — else plain "Thank you." (iOS thankYouLine). */
internal fun thankYouLine(memberName: String?): String =
    receiptFirstName(memberName, null)?.let { "Thank you, $it." } ?: "Thank you."

/** The one quiet line under Download PDF when the PDF can't be had (iOS
 *  downloadMessage): a 404 is a member with no partners statement, a
 *  transport failure is the connection; anything else leaves the page as
 *  the record. */
internal fun partnersPdfErrorLine(status: Int?, offline: Boolean): String = when {
    status == 404 -> "There's no partners statement for you yet."
    offline -> PDF_OFFLINE
    else -> "The PDF isn't available right now. The statement above is still complete."
}

/** The giving statement's line under its PDF button (iOS GivingStatementView
 *  downloadMessage) — it said nothing when the PDF failed. */
internal fun givingPdfErrorLine(offline: Boolean): String =
    if (offline) PDF_OFFLINE else "The PDF isn't available right now. The statement below is still complete."

/** §4's "You're offline", said only when the phone IS offline — a timeout
 *  with a network is not the connection (it read "You appear to be offline"
 *  on any dropped answer). */
internal const val PDF_OFFLINE = "You're offline — the PDF needs a connection."

/** Whether a failure is the phone being offline, by §4's one rule
 *  (ApiException.state asks the phone). */
internal fun isOffline(e: Throwable, context: android.content.Context?): Boolean =
    org.nuruplace.member.data.net.ApiException.state(e, context).cause == org.nuruplace.member.data.net.StateCause.OFFLINE

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
            StatementMonth(key / 100, key % 100, ordered, paymentSums(ordered))
        }
    return if (undated.isEmpty()) months else months + StatementMonth(0, 0, undated, paymentSums(undated))
}

/** What the payments list adds up to, per currency — the year's total at its foot. */
internal fun statementYearTotals(months: List<StatementMonth>): List<CurrencyAmount> =
    paymentSums(months.flatMap { it.payments })

/** The PROCESSING rows above PAYMENTS: the server's `pending` list,
 *  pledge-tied only (as every row here), newest first, minus any row that
 *  has meanwhile settled into `payments` (the two are read in one answer,
 *  but a row is never shown twice), and only those dated in the statement's
 *  own year — or undated (iOS pendingPledgePayments). Never summed anywhere. */
internal fun pendingPaymentRows(s: GivingStatement): List<StatementPendingPayment> {
    val settled = s.payments.map { it.transactionId }.filter { it.isNotBlank() }.toSet()
    return s.pending.orEmpty()
        .filter { !it.pledgeId.isNullOrBlank() && (it.transactionId.isBlank() || it.transactionId !in settled) }
        .filter { r -> partnerDate(r.at)?.let { it.year == s.year } ?: true }
        .sortedByDescending { it.at ?: "" }
}

/** What a pledge payment row calls the pledge (iOS paymentTitle): the
 *  server's name for it, else the row's own title, else the member's pledge
 *  by id, else "Pledge". */
internal fun statementPaymentTitle(pledgeTitle: String?, title: String?, pledgeId: String?, pledges: List<Pledge>): String =
    pledgeTitle?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }
        ?: pledgeId?.let { id -> pledges.firstOrNull { it.pledgeId == id }?.displayTitle }
        ?: "Pledge"

/** A settled pledge payment's second line (iOS StatementPaymentRow.meta):
 *  the fund first — the server's name, else the local one for its code —
 *  then the receipt code. Its rail is not on this wire, so never guessed. */
internal fun statementPaymentMeta(pay: StatementPayment): String =
    listOfNotNull(
        pay.fundName?.takeIf { it.isNotBlank() } ?: pay.fund?.takeIf { it.isNotBlank() }?.let { giveFund(it).name },
        pay.receiptCode?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

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
    data class Carried(val count: Int) : DisciplesTile {
        /** "3 disciples carried through a level" — the tile, read aloud. */
        val spoken: String get() = "$count disciple${if (count == 1) "" else "s"} carried through a level"
    }

    /** Below the first: `towardMinor` of `perDiscipleMinor`, and the bar's
     *  fill 0..1. */
    data class Toward(val towardMinor: Int, val perDiscipleMinor: Int, val fraction: Float) : DisciplesTile {
        /** Under the bar: "KSh 6,000 of 20,000". */
        val ofLine: String get() = "${ksh(towardMinor)} of ${PartnerFormat.grouped(perDiscipleMinor / 100)}"

        /** The tile, read aloud: "KSh 6,000 of KSh 20,000 toward carrying one
         *  disciple through a level". */
        val spoken: String get() = "${ksh(towardMinor)} of ${ksh(perDiscipleMinor)} $TOWARD_CAPTION"
    }
}

/** The progress tile's caption, under "KSh 6,000 of 20,000". */
internal const val TOWARD_CAPTION = "toward carrying one disciple through a level"

/** The Kept tile (iOS HeroTiles.kept): kept = on time + late — "kept" has one
 *  meaning, a due date paid in full (owner, 2026-09-25) — of those that fell
 *  due, and the late ones said beside it. */
internal data class KeptTile(val kept: Int, val due: Int, val late: Int) {
    /** "commitments · 1 late" — the late ones are kept, and said. */
    val caption: String get() = if (late > 0) "commitments · $late late" else "commitments"

    /** "5 of 6 commitments kept, 1 late". */
    val spoken: String get() = "$kept of $due commitments kept${if (late > 0) ", $late late" else ""}"
}

/** The Given tile: the first currency large, any other said beside it and
 *  never added to it (Giving Cycle 9). */
internal data class GivenTile(val amounts: List<CurrencyAmount>) {
    val first: CurrencyAmount get() = amounts.firstOrNull() ?: CurrencyAmount(GIVE_FORM_CURRENCY, 0)

    /** "+ US$ 20.00" for a second currency; null with one. */
    val rest: String?
        get() = amounts.drop(1).takeIf { it.isNotEmpty() }?.let { more -> "+ " + more.joinToString(" + ") { money(it.minor, it.currency) } }

    /** "toward pledges" · "toward pledges · + US$ 20.00". */
    val caption: String get() = rest?.let { "toward pledges · $it" } ?: "toward pledges"

    /** "Given KSh 25,000 + US$ 299.99 toward pledges". */
    val spoken: String get() = "Given ${money(first.minor, first.currency)}${rest?.let { " $it" } ?: ""} toward pledges"
}

/** The hero's tiles, or null — no `impact` (an older server) — when the
 *  Pledged / Paid / Remaining card stays in their place (iOS HeroTiles). */
internal data class HeroTiles(val disciples: DisciplesTile, val kept: KeptTile?, val given: GivenTile)

internal fun heroTiles(s: GivingStatement): HeroTiles? {
    val impact = s.impact ?: return null
    return HeroTiles(disciplesTile(impact), keptTile(s.faithfulness), GivenTile(givenTileAmounts(s)))
}

internal fun disciplesTile(impact: StatementImpact): DisciplesTile {
    if (impact.disciplesCarried >= 1) return DisciplesTile.Carried(impact.disciplesCarried)
    val per = impact.perDiscipleMinor.takeIf { it > 0 } ?: DISCIPLE_COST_MINOR
    // The server's toward_next; an older or partial answer that left it at 0
    // while money was paid reads the paid amount's remainder instead.
    val toward = (impact.towardNextMinor.takeIf { it > 0 } ?: (maxOf(impact.paidMinor, 0) % per)).coerceIn(0, per)
    return DisciplesTile.Toward(toward, per, toward.toFloat() / per)
}

/** The Kept tile, or null to hide it: no faithfulness block, or nothing has
 *  come due yet. */
internal fun keptTile(f: StatementFaithfulness?): KeptTile? {
    if (f == null || f.dueCount <= 0) return null
    val late = maxOf(f.late, 0)
    return KeptTile(maxOf(f.keptOnTime, 0) + late, f.dueCount, late)
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

/** The strip read aloud as one sentence (iOS stripAccessibility):
 *  "Faithfulness: January kept on time, February nothing due, …". */
internal fun faithfulnessSpoken(marks: List<MonthMark>): String =
    "Faithfulness: " + marks.mapIndexed { i, m ->
        val word = when (m) {
            MonthMark.Kept -> "kept on time"
            MonthMark.Late -> "kept late"
            MonthMark.Missed -> "missed"
            MonthMark.Upcoming -> "upcoming"
            MonthMark.None -> "nothing due"
        }
        "${Month.of(i + 1).getDisplayName(TextStyle.FULL, Locale.ENGLISH)} $word"
    }.joinToString(", ")

/** Jan..Dec, exactly twelve, whatever order or gaps the wire has (a month
 *  the server left out reads as none). Null — hide the card — when there is
 *  no `months` block, or no month carries anything but none (a partner with
 *  only total pledges has no monthly rhythm to show). */
internal fun faithfulnessMarks(months: List<StatementMonthStatus>?): List<MonthMark>? {
    if (months.isNullOrEmpty()) return null
    val marks = (1..12).map { m -> monthMark(months.firstOrNull { it.month == m }?.status) }
    return marks.takeIf { list -> list.any { it != MonthMark.None } }
}

/** "3 kept on time · 1 late · 2 missed · next due 5 Oct" under the strip.
 *  The counts are the statement's own `faithfulness` (the ledger the hero's
 *  Kept tile reads); the strip's month marks stand in only when the
 *  statement sends no faithfulness block. Each part appears only when it is
 *  not zero, and `nextDue` only for the year being lived. Null when nothing
 *  is left to say. */
internal fun faithfulnessLine(f: StatementFaithfulness?, marks: List<MonthMark>, nextDue: LocalDate?, today: LocalDate): String? {
    val onTime = maxOf(f?.keptOnTime ?: marks.count { it == MonthMark.Kept }, 0)
    val late = maxOf(f?.late ?: marks.count { it == MonthMark.Late }, 0)
    val missed = maxOf(f?.missed ?: marks.count { it == MonthMark.Missed }, 0)
    val parts = buildList {
        if (onTime > 0) add("$onTime kept on time")
        if (late > 0) add("$late late")
        if (missed > 0) add("$missed missed")
        nextDue?.let { d ->
            add("next due ${if (d.year == today.year) PartnerFormat.dayMonth(d) else PartnerFormat.dayMonthYear(d)}")
        }
    }
    if (parts.isEmpty()) return null
    return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
}

/** FAITHFULNESS' "next due": the earliest, across ACTIVE MONTHLY pledges,
 *  of each pledge's next upcoming date — its server `progress.next_due` when
 *  that is today or later (the ledger advances it once an instalment is paid:
 *  26 Sep paid → 26 Oct; a pre-payment → 26 Nov), else the next `due_day`
 *  on or after today: an OVERDUE pledge (next_due already past) still has
 *  an instalment coming, and a pledge the server sent no next_due for (an
 *  older server) has its day. The DUE list is not read (it holds an
 *  instalment until its payment settles). Null when none. */
internal fun nextPledgeDue(p: Partnership?, today: LocalDate): LocalDate? {
    if (p == null) return null
    return p.pledges
        .filter { it.status == "active" && it.shape != "total" }
        .mapNotNull { pl ->
            partnerDate(pl.progress.nextDue)?.takeIf { !it.isBefore(today) }
                ?: pl.dueDay?.let { nextDueDayDate(it, today) }
        }
        .minOrNull()
}

/** The first `dueDay`-of-the-month date on or after `today` (days 1–28,
 *  spec §1) — the fallback for a pledge the server sent no next_due for. */
internal fun nextDueDayDate(dueDay: Int, today: LocalDate): LocalDate {
    val thisMonth = today.withDayOfMonth(dueDay.coerceIn(1, 28))
    return if (thisMonth.isBefore(today)) thisMonth.plusMonths(1) else thisMonth
}

/** COMMITMENTS' header figure: Σ remaining_year_minor PER CURRENCY (the
 *  rows' own), or null (hidden) when no row carries it — an older server, or
 *  rows derived locally. */
internal fun remainingThisYear(rows: List<StatementPledge>): List<CurrencyAmount>? {
    if (rows.none { it.remainingYearMinor != null }) return null
    return shillingsFirst(rows.map { currencyCode(it.currency) }).map { c ->
        CurrencyAmount(c, rows.filter { currencyCode(it.currency) == c }.sumOf { (it.remainingYearMinor ?: maxOf(it.pledgedMinor - it.paidMinor, 0)).toLong() })
    }
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
