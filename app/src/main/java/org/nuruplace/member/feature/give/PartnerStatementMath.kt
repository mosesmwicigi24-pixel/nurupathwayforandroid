// The Partners tab's arithmetic (docs/PARTNERS_PROGRAMME.md §3, "Partner-only
// statement rule") — pure functions over the wire DTOs, kept out of the
// composables so the numbers the member sees are pinned by unit tests and
// match iOS exactly. Nothing here is server truth: the server sends pledges
// and payments; these are the two derived views the design shows.
//
//   Paid      = Σ statement payments[].amount_minor where pledge_id is set
//   Pledged   = Σ over pledges not cancelled:
//                 monthly → amount_minor × number of due_day dates in the year
//                           from max(pledge start, 1 Jan) through
//                           min(until_on, 31 Dec) — the start is the later of
//                           starts_on and the creation day (Nairobi)
//                 total   → target_minor if due_on falls in the year, else 0
//   Remaining = Σ per pledge max(Pledged_i − Paid_i, 0)
//
// Giving Cycle 5 mirrors the server's partnerStatementMath.ts exactly: a
// pledge starts on its starts_on when that is later than its creation day,
// its instalments end at until_on, remaining is owed PER PLEDGE (netting the
// year's totals let money beyond one pledge hide what another still owed),
// dates are the church's (Africa/Nairobi) calendar — and nothing is summed
// across currencies: statementSummaries gives one summary per currency.
//
// Gifts without a pledge are never counted or shown on the Partners tab; they
// stay in the full statement (GivingStatementScreen).
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.DueItem
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.PartnerTier
import org.nuruplace.member.data.net.PartnerTrouble
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.StatementPayment
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The STATEMENT card's three numbers, all minor units. */
internal data class StatementSummary(val pledgedMinor: Int, val paidMinor: Int, val remainingMinor: Int)

/** Every monthly pledge has a due day 1..28 (spec §1); a row without one is
 *  malformed, and the 1st is the least-surprising day to count from. */
internal const val DEFAULT_DUE_DAY = 1

/** "Mon 5 Oct" — the DUE row's collected-on chip. */
private val COLLECTED_ON_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** The church's calendar (UTC+3 all year, no DST). */
private val PARTNER_ZONE: ZoneId = ZoneId.of("Africa/Nairobi")

private val BARE_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")

/** ISO instant / offset timestamp / bare date → the church's calendar date
 *  (Africa/Nairobi), or null. A bare date is already a calendar day and is
 *  returned as-is, never shifted — the server's partnerDate. */
internal fun partnerDate(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    val s = iso.trim()
    if (BARE_DATE.matches(s)) return runCatching { LocalDate.parse(s) }.getOrNull()
    return runCatching { OffsetDateTime.parse(s).atZoneSameInstant(PARTNER_ZONE).toLocalDate() }.getOrNull()
        ?: runCatching { Instant.parse(s).atZone(PARTNER_ZONE).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(s.take(10)) }.getOrNull()
}

/** Today on the church's calendar. */
internal fun partnerToday(now: Instant = Instant.now()): LocalDate = now.atZone(PARTNER_ZONE).toLocalDate()

/** The first day a pledge's instalments can fall due: the later of its
 *  starts_on and its creation day (Nairobi) — the server's pledgeStart. */
internal fun pledgeStart(pl: Pledge): LocalDate? {
    val created = partnerDate(pl.createdAt)
    val starts = partnerDate(pl.startsOn)
    return when {
        created == null -> starts
        starts == null -> created
        else -> maxOf(starts, created)
    }
}

/** The first `day`-of-the-month date on or after `from` (day 1..28). */
internal fun firstDueOnOrAfter(from: LocalDate, day: Int): LocalDate {
    val same = from.withDayOfMonth(day.coerceIn(1, 28))
    return if (!same.isBefore(from)) same else same.plusMonths(1)
}

/** The first `day`-of-the-month date strictly AFTER `from` — a pledge's
 *  first automatic collection, which is never today (the server's
 *  firstDueAfter; the day held to 1..28). */
internal fun firstDueAfter(from: LocalDate, day: Int): LocalDate {
    val first = firstDueOnOrAfter(from, day)
    return if (first == from) first.plusMonths(1) else first
}

/** A monthly pledge's instalment due dates, oldest first: its due day from
 *  its start ([pledgeStart]) through `through`, none after its until_on —
 *  the dates the server's instalment ledger holds. A total pledge, or one
 *  without a start or a positive amount, has none. */
internal fun instalmentDueDates(pl: Pledge, through: LocalDate): List<LocalDate> {
    if (pl.shape == "total" || (pl.amountMinor ?: 0) <= 0) return emptyList()
    val start = pledgeStart(pl) ?: return emptyList()
    val until = partnerDate(pl.untilOn)
    val end = if (until != null && until.isBefore(through)) until else through
    val day = (pl.dueDay ?: DEFAULT_DUE_DAY).coerceIn(1, 28)
    val out = mutableListOf<LocalDate>()
    var due = firstDueOnOrAfter(start, day)
    while (!due.isAfter(end) && out.size < 2_400) {
        out += due
        due = due.plusMonths(1).withDayOfMonth(day)
    }
    return out
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
        else -> (pl.amountMinor ?: 0) * dueDatesInYear(year, pl.dueDay ?: DEFAULT_DUE_DAY, pledgeStart(pl), partnerDate(pl.untilOn))
    }
}

/** The pledge-tied payments only — the rows the Partners tab shows. */
internal fun pledgePayments(payments: List<StatementPayment>): List<StatementPayment> =
    payments.filter { !it.pledgeId.isNullOrBlank() }

/** The three numbers for ONE currency's pledges and payments (the caller
 *  groups by currency — [statementSummaries]). Remaining is what is still
 *  owed on EACH pledge, added up: money beyond one pledge (a cancelled one
 *  paid this year, one paid ahead) never hides what another still owes. */
internal fun statementSummary(year: Int, pledges: List<Pledge>, payments: List<StatementPayment>): StatementSummary {
    val tied = pledgePayments(payments)
    // What each pledge still owes counts only money in ITS currency (the
    // server's rule since Giving Cycle 9: a wrong-currency gift is not that
    // pledge's shillings). Summed per currency, the payments are one currency
    // already; this holds even when they are not.
    val paidBy = tied.groupBy { it.pledgeId!! to currencyCode(it.currency) }.mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
    val pledged = pledges.sumOf { pledgedInYear(it, year) }
    val paid = tied.sumOf { it.amountMinor }
    val remaining = pledges.sumOf { maxOf(pledgedInYear(it, year) - (paidBy[it.pledgeId to currencyCode(it.currency)] ?: 0), 0) }
    return StatementSummary(pledgedMinor = pledged, paidMinor = paid, remainingMinor = remaining)
}

/** One currency's Pledged / Paid / Remaining. */
internal data class CurrencyStatementSummary(
    val currency: String,
    val pledgedMinor: Int,
    val paidMinor: Int,
    val remainingMinor: Int,
)

/** A pledge's or payment's currency code, upper-case; blank reads as shillings. */
internal fun currencyCode(c: String?): String = c?.trim()?.uppercase()?.ifEmpty { null } ?: GIVE_FORM_CURRENCY

/** Shillings first, then by code — the server's totals[] order. */
internal fun shillingsFirst(codes: Collection<String>): List<String> =
    codes.distinct().sortedWith(compareBy<String> { it != GIVE_FORM_CURRENCY }.thenBy { it })

/** The summary PER CURRENCY — never one sum across currencies (Giving Cycle
 *  5): each currency's pledges with that currency's pledge-tied payments.
 *  Only currencies with something pledged or paid in `year`, shillings
 *  first; nothing at all → one zero summary in shillings. */
internal fun statementSummaries(year: Int, pledges: List<Pledge>, payments: List<StatementPayment>): List<CurrencyStatementSummary> {
    val tied = pledgePayments(payments)
    val codes = pledges.filter { pledgedInYear(it, year) > 0 }.map { currencyCode(it.currency) } + tied.map { currencyCode(it.currency) }
    return shillingsFirst(codes).ifEmpty { listOf(GIVE_FORM_CURRENCY) }.map { c ->
        val sum = statementSummary(year, pledges.filter { currencyCode(it.currency) == c }, tied.filter { currencyCode(it.currency) == c })
        CurrencyStatementSummary(c, sum.pledgedMinor, sum.paidMinor, sum.remainingMinor)
    }
}

/** "N of M kept this year" for a monthly pledge: N = payments this year
 *  attributed to it (capped at M, so an early or doubled gift never reads
 *  "13 of 12"); M = its due dates elapsed this year through `today` — from
 *  its start, none after its until_on. The LOCAL estimate —
 *  [pledgeKeptThisYear] prefers the server's figures. */
internal fun keptThisYear(pl: Pledge, payments: List<StatementPayment>, today: LocalDate): Pair<Int, Int> {
    val until = partnerDate(pl.untilOn)
    val through = if (until != null && until.isBefore(today)) until else today
    val elapsed = dueDatesInYear(today.year, pl.dueDay ?: DEFAULT_DUE_DAY, pledgeStart(pl), through = through)
    // Only a payment in the pledge's own currency keeps one (Giving Cycle 9).
    val kept = payments.count { it.pledgeId == pl.pledgeId && it.pledgeId?.isNotBlank() == true && currencyCode(it.currency) == currencyCode(pl.currency) }
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

/** An empty year's PAYMENTS, as §4's state title — no full stop (final walk
 *  C16; iOS PartnerStatementWords.noPayments). */
internal fun noPledgePaymentsTitle(year: Int): String = "No pledge payments in $year"

/** What TalkBack reads for the tier chip: the server's own sentence and what
 *  it gives a month. The tier's name is the server's words — "will carry one
 *  disciple through a level, every year" until the partner's money lands,
 *  then "carries …" (owner, 2026-10-08) — so nothing here says "carries" on
 *  its own: it read "… partner — … KSh 20,000 carries one disciple" beside
 *  KSh 0 paid. */
internal fun tierSpoken(tier: PartnerTier, currency: String?): String =
    "${tier.name.trim()}. ${money(tier.monthlyMinor, currency)} a month."

/** A pledge card's progress bar to TalkBack (iOS): "40 percent". */
internal fun progressSpoken(fraction: Float): String = "${Math.round(fraction.coerceIn(0f, 1f) * 100)} percent"

/** A total pledge's card foot (iOS PledgeCard.leftLine): "KSh 20,000 paid
 *  · 30,000 to go" — what is left said as a bare figure, the currency once.
 *  Dollars keep their cents ("US$ 12.50 paid · 87.50 to go"); iOS drops
 *  them there. */
internal fun totalPledgeLeftLine(pl: Pledge): String {
    val paid = pl.progress.paidMinor
    val toGo = maxOf((pl.targetMinor ?: 0) - paid, 0)
    val bare = if (currencyCode(pl.currency) == GIVE_FORM_CURRENCY) "%,d".format(toGo / 100) else "%,.2f".format(toGo / 100.0)
    return "${money(paid, pl.currency)} paid · $bare to go"
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
        else -> DueRowView(d.amountMinor - pending, processingNote = "${money(pending, d.currency)} processing")
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
        // No zero counts (§7.4 #9): "0 gifts kept" is no gift at all.
        hasSchedule && p.kept > 0 -> "${p.kept} ${if (p.kept == 1) "gift" else "gifts"} kept"
        else -> null
    }
    return listOfNotNull(count, state).joinToString(" · ").replaceFirstChar { it.uppercase() }
}

/** The amber trouble row's words (iOS TroubleRow): a paused recurring gift,
 *  or one that did not go through — nothing is owed either way. */
internal fun troubleLine(t: PartnerTrouble): String =
    if (t.paused) "Your giving is paused — nothing is owed." else "One gift didn't go through — nothing is owed."

/** A "when" as the row or card says it, and whether it is overdue (said in
 *  amber, GIVE.goldChipText 0xFF7A5A14). */
internal data class WhenLabel(val text: String, val overdue: Boolean)

/** "10 Aug", or "10 Aug 2025" outside the year being lived. */
private fun dayMonthIn(d: LocalDate, today: LocalDate): String =
    if (d.year == today.year) PartnerFormat.dayMonth(d) else PartnerFormat.dayMonthYear(d)

/**
 * Whether a DUE row is overdue — the SERVER decides, as sent: its `overdue`
 * flag (overdue_count > 0), `overdue_count`, or an `overdue_since` date. So
 * a catch-up row whose `due_on` is today is overdue when the server says an
 * earlier instalment is, and a row the server calls on time is on time on a
 * phone whose clock or zone disagrees. Only a pledge row can be — nothing is
 * owed on a recurring gift. An older server that sends none of the three:
 * its due date already past on the church's calendar.
 */
internal fun dueOverdue(d: DueItem, today: LocalDate): Boolean {
    if (d.kind != "pledge") return false
    if (d.overdue == true || d.overdueCount > 0 || !d.overdueSince.isNullOrBlank()) return true
    if (d.overdue == false) return false
    return partnerDate(d.dueOn)?.isBefore(today) == true
}

/** How near a due must be to be called DUE (EXPERIENCE.md §9.3 rule 2): the
 *  fortnight. */
internal const val DUE_SOON_DAYS = 14L

/** Nothing is urgent before it is (§9.3 rule 2): a row is DUE when it is
 *  overdue or falls within the fortnight; further out it is coming up — a
 *  total pledge's 31 Dec read "DUE" 87 days ahead. An undated row stays DUE. */
internal fun dueIsSoon(d: DueItem, today: LocalDate): Boolean {
    if (dueOverdue(d, today)) return true
    val day = partnerDate(d.dueOn) ?: return true
    return !day.isAfter(today.plusDays(DUE_SOON_DAYS))
}

/**
 * The DUE row's "when" (iOS dueWhen). Overdue ([dueOverdue]) reads "overdue
 * since 10 Aug" — "2 overdue since 10 Aug" with two or more behind (the
 * amount is then the server's catch-up total) — dated by the server's
 * `overdue_since` when sent, else `due_on`, the year added outside this one.
 * Otherwise "today" · "tomorrow" · "in N days" (to two weeks) · the date; a
 * date that cannot be read is said as sent.
 */
internal fun dueWhen(d: DueItem, today: LocalDate): WhenLabel {
    if (dueOverdue(d, today)) {
        val since = partnerDate(d.overdueSince) ?: partnerDate(d.dueOn)
        val sinceText = since?.let { dayMonthIn(it, today) } ?: (d.overdueSince?.takeIf { it.isNotBlank() } ?: d.dueOn).take(10)
        val lead = if (d.overdueCount >= 2) "${d.overdueCount} overdue since" else "overdue since"
        return WhenLabel("$lead $sinceText", overdue = true)
    }
    val due = partnerDate(d.dueOn) ?: return WhenLabel(d.dueOn, false)
    return WhenLabel(dueRelativeLabel(due, today, PartnerFormat::dayMonth), overdue = false)
}

/** What Partners' DUE row says for a running recurring gift, in place of Pay
 *  (owner, 2026-09-28; iOS ScheduleRhythm.collectedOn): "Collected on Mon 5
 *  Oct" — `due_on`, the server's Nairobi date of its next prompt. Pay only
 *  opened a SEPARATE one-time gift while the recurring gift still prompted
 *  on its day, so the member gave twice that cycle. Null when `due_on` is
 *  not a date. */
internal fun collectedOnLine(dueOn: String): String? {
    val d = runCatching { LocalDate.parse(dueOn.trim().take(10)) }.getOrNull() ?: return null
    return collectedOnLine(d)
}

/** "Collected on Mon 5 Oct" for a day already on the church's calendar. */
internal fun collectedOnLine(day: LocalDate): String = "Collected on ${day.format(COLLECTED_ON_FMT)}"

/** The chip a DUE row wears instead of Pay: a RUNNING recurring gift's
 *  "Collected on …" ([collectedOnLine]). Null for a paused gift (it keeps
 *  Resume) and for a pledge row — a pledge says it only when its collector
 *  takes the instalment ([pledgeCollectedChip]). */
internal fun dueCollectedChip(d: DueItem): String? =
    if (d.kind == "schedule" && d.action != "resume") collectedOnLine(d.dueOn) else null

/**
 * The day a pledge's DUE row is collected automatically (pathway docs/
 * EXPERIENCE.md §6.4 — the owner's 2026-09-28 rule for a running recurring
 * gift, extended to a pledge): its collector ([pledgeCollector]) RUNNING,
 * its next prompt asking for money (`next_amount_minor` > 0 — 0 is a pledge
 * already covered, null a collector stopping with its pledge or an older
 * server), and that prompt's Nairobi day on or before the instalment's
 * (`due_on`). Null for anything else — no collector, a paused one, a prompt
 * after the instalment (one already past is the member's to pay), a day
 * that cannot be read, a row that is not a pledge's Pay row — and the row
 * keeps Pay.
 */
internal fun pledgeCollectedOn(d: DueItem, collector: GivingSchedule?): LocalDate? {
    if (d.kind != "pledge" || d.action != "pay") return null
    val s = collector?.takeIf { scheduleRunning(it.status) } ?: return null
    if ((s.nextAmountMinor ?: 0L) <= 0L) return null
    val prompt = partnerDate(s.nextRunAt) ?: return null
    val instalment = partnerDate(d.dueOn) ?: return null
    return prompt.takeIf { !it.isAfter(instalment) }
}

/** A pledge DUE row's chip in place of Pay — "Collected on Fri 2 Oct", its
 *  collector's prompt ([pledgeCollectedOn]); a tap opens the pledge. Null
 *  keeps Pay, and paying early by hand stays on the pledge's page. */
internal fun pledgeCollectedChip(d: DueItem, collector: GivingSchedule?): String? =
    pledgeCollectedOn(d, collector)?.let(::collectedOnLine)

/** The DUE row's first line (iOS dueRow): "KSh 5,000 · in 3 days" — or,
 *  part of it already on its way, the uncovered rest: "KSh 3,000 left · in
 *  3 days". */
/** What is already happening is said first (EXPERIENCE.md §9.3 rule 1): a
 *  claim the office is checking sits on the DUE row it covers — "KSh 2,000
 *  is being checked by the office" — so nobody pays twice. Said, never
 *  subtracted: a claim counts once confirmed. Null with none. */
internal fun dueClaimLine(d: DueItem): String? =
    d.pendingClaimMinor.takeIf { d.kind == "pledge" && it > 0 }
        ?.let { "${money(it, d.currency)} is being checked by the office" }

/** While the office checks money toward a DUE row, its Pay is the quiet
 *  secondary — white, a hairline, navy words — never the row's navy ask: the
 *  claim line above says what is happening (final walk M1; iOS's DUE row).
 *  A paused row's Resume is never quieted. */
internal fun duePayQuiet(d: DueItem): Boolean = d.action != "resume" && dueClaimLine(d) != null

internal fun dueLeadLine(d: DueItem, view: DueRowView, whenText: String): String =
    "${money(view.leadMinor, d.currency)}${if (view.processingNote != null) " left" else ""} · $whenText"

/** The pledge card's foot: "Next 5 Oct", or — its next instalment already
 *  past — "Overdue since 10 Aug" (amber). Null when the server names no
 *  next due, and on a paused or fulfilled pledge, which asks for nothing
 *  next (iOS PledgeCard.nextLine). */
internal fun pledgeNextLabel(pl: Pledge, today: LocalDate): WhenLabel? {
    val paused = pl.status == "paused" || pl.progress.label == "paused"
    val fulfilled = pl.status == "fulfilled" || pl.progress.label == "fulfilled"
    if (paused || fulfilled) return null
    val next = partnerDate(pl.progress.nextDue) ?: return null
    return if (next.isBefore(today)) WhenLabel("Overdue since ${dayMonthIn(next, today)}", overdue = true)
    else WhenLabel("Next ${PartnerFormat.dayMonth(next)}", overdue = false)
}

/**
 * A partner has a standing to show — since when, what's been kept, the tier —
 * once there is something real behind it: a pledge, or a gift collected.
 * Ben, with a weekly gift set up that had never collected and no pledge,
 * read "Partner since Oct 2026 · 0 gifts kept · on track" beside "carries one
 * disciple through a level, every year" (Cycle 3's closing walk).
 */
internal fun hasStanding(p: Partnership): Boolean =
    p.pledges.any { it.status != "cancelled" } || p.kept > 0
