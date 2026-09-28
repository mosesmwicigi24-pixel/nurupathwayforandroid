// The giving statement's split (docs/PARTNERS_PROGRAMME.md §3d, owner-delegated
// 2026-09-25) — pure functions over GET /giving/history rows so the numbers a
// member sees are pinned by GivingStatementLogicTest and match iOS.
//
// A giving statement is a financial record: it must reconcile with the
// member's bank and the church ledger, so pledge money is never EXCLUDED — it
// is SEPARATED. Gifts are the rows without a pledge_id; they make the hero,
// BY FUND and the day list. Pledge-tied rows sit in one collapsed PARTNER
// PLEDGES group with its own total, and Gifts + Partner pledges = Total, to
// the shilling.
//
// Giving Cycle 2: every sum is PER CURRENCY — a USD PayPal gift and shilling
// gifts read "KSh 3,500 + US$ 20.00", never one number (the server's totals[]
// rule: shillings first, then by code) — and a gift belongs to the Nairobi
// year it was MADE in (created_at), as the server's statement and PDF count
// it, so the app, the PDF and the office agree at the turn of the year.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GivingRecord
import java.time.LocalDate

/** Money in one currency — one entry of a per-currency sum. */
internal data class CurrencyAmount(val currency: String, val minor: Long)

/** A row's currency code, upper-case; blank reads as shillings. */
private fun currencyOf(r: GivingRecord): String = r.currency.trim().uppercase().ifEmpty { GIVE_FORM_CURRENCY }

/** Shillings first, then by code — the server's totals[] order. */
private val SHILLINGS_FIRST = compareBy<String> { it != GIVE_FORM_CURRENCY }.thenBy { it }

/** Rows summed per currency, shillings first then by code. No rows → no entries. */
internal fun currencySums(records: List<GivingRecord>): List<CurrencyAmount> =
    records.groupBy(::currencyOf)
        .map { (currency, rows) -> CurrencyAmount(currency, rows.sumOf { it.amountMinor.toLong() }) }
        .sortedWith(compareBy(SHILLINGS_FIRST) { it.currency })

/** "KSh 3,500 + US$ 20.00" — never one sum across currencies; nothing at
 *  all reads "KSh 0". */
internal fun moneyTotals(sums: List<CurrencyAmount>): String =
    sums.ifEmpty { listOf(CurrencyAmount(GIVE_FORM_CURRENCY, 0)) }.joinToString(" + ") { money(it.minor, it.currency) }

/** The first currency's amount — the hero's big number. */
internal fun primaryAmount(sums: List<CurrencyAmount>): String =
    money(sums.firstOrNull()?.minor ?: 0, sums.firstOrNull()?.currency ?: GIVE_FORM_CURRENCY)

/** The rest, under the big number: "+ US$ 20.00"; null with one currency. */
internal fun extraAmounts(sums: List<CurrencyAmount>): String? =
    sums.drop(1).takeIf { it.isNotEmpty() }?.joinToString(" ") { "+ ${money(it.minor, it.currency)}" }

/** The Nairobi year a gift counts in: the year it was made (created_at), as
 *  the server's statements and PDF count it; else when it settled. */
internal fun givingYear(r: GivingRecord): Int? =
    (parseNairobi(r.createdAt) ?: parseNairobi(r.settledAt))?.year

/** One period's rows, split. Total = gifts + pledges, per currency, by construction. */
internal data class GivingSplit(
    val gifts: List<GivingRecord>,
    val pledges: List<GivingRecord>,
) {
    val giftsSums: List<CurrencyAmount> get() = currencySums(gifts)
    val pledgesSums: List<CurrencyAmount> get() = currencySums(pledges)
    val totalSums: List<CurrencyAmount> get() = currencySums(gifts + pledges)
}

/** A row counted toward a pledge (wire pledge_id, contract 2026-09-25). A
 *  blank id is no pledge, the same rule as the partners statement. */
internal fun isPledgeRecord(r: GivingRecord): Boolean = !r.pledgeId.isNullOrBlank()

internal fun givingSplit(records: List<GivingRecord>): GivingSplit {
    val (pledges, gifts) = records.partition(::isPledgeRecord)
    return GivingSplit(gifts = gifts, pledges = pledges)
}

/** The hero's words. With no pledge money it stays "Total given" over the
 *  one number; with some, the big number is Gifts and one muted line carries
 *  "Partner pledges KSh Y · Total KSh X+Y". `footLabel` is BY FUND's foot,
 *  which now sums gifts only. (Settled rows only reach here, so "no pledge
 *  rows" and "Y = 0" are the same thing; keying on the rows means a pledge
 *  row can never drop out of both the day list and the group.) */
internal data class GivingHero(val label: String, val amounts: List<CurrencyAmount>, val pledgeLine: String?, val footLabel: String) {
    /** The big number: the first currency ("KSh 3,500"). */
    val primary: String get() = primaryAmount(amounts)

    /** Under it, any other currency ("+ US$ 20.00"); null with one. */
    val extra: String? get() = extraAmounts(amounts)
}

internal fun givingHero(split: GivingSplit): GivingHero =
    if (split.pledges.isEmpty()) {
        GivingHero("Total given", split.totalSums, null, "TOTAL GIVEN")
    } else {
        GivingHero(
            label = "Gifts",
            amounts = split.giftsSums,
            pledgeLine = "Partner pledges ${moneyTotals(split.pledgesSums)} · Total ${moneyTotals(split.totalSums)}",
            footLabel = "TOTAL GIFTS",
        )
    }

/** One BY FUND row: a fund's gifts in ONE currency — a fund given to in
 *  shillings and dollars has two rows, never one sum. */
internal data class FundLine(val fund: String, val currency: String, val minor: Long, val count: Int)

/** BY FUND, in the order the funds first appear (newest gift first), each
 *  fund's currencies shillings first. */
internal fun fundLines(records: List<GivingRecord>): List<FundLine> =
    records.groupBy { it.fund }.flatMap { (fund, rows) ->
        rows.groupBy(::currencyOf)
            .map { (currency, rs) -> FundLine(fund, currency, rs.sumOf { it.amountMinor.toLong() }, rs.size) }
            .sortedWith(compareBy(SHILLINGS_FIRST) { it.currency })
    }

/** One day of rows, newest first. `date` is null for rows whose timestamp
 *  cannot be read (they keep their money under a "—" header). */
internal data class StatementDay(val date: LocalDate?, val records: List<GivingRecord>)

/** Rows by Nairobi calendar day (createdAt), newest first. Ordered by the
 *  instant rather than the raw string, so a row whose timestamp cannot be
 *  read trails in its own "—" group instead of sorting to the top. */
internal fun statementDays(records: List<GivingRecord>): List<StatementDay> =
    records.sortedWith(compareByDescending<GivingRecord> { parseNairobi(it.createdAt)?.toInstant() }.thenByDescending { it.createdAt })
        .groupBy { parseNairobi(it.createdAt)?.toLocalDate() }
        .map { (date, recs) -> StatementDay(date, recs) }

/** The collapsed PARTNER PLEDGES group: its total per currency, its count,
 *  the header line "KSh Y · N payments", and the rows by day for when it opens. */
internal data class PledgeGroup(val totals: List<CurrencyAmount>, val count: Int, val days: List<StatementDay>) {
    val summary: String get() = "${moneyTotals(totals)} · $count payment${if (count == 1) "" else "s"}"
}

/** Null — no group at all — when the period has no pledge money. */
internal fun pledgeGroup(split: GivingSplit): PledgeGroup? {
    if (split.pledges.isEmpty()) return null
    return PledgeGroup(split.pledgesSums, split.pledges.size, statementDays(split.pledges))
}

/** The gold tag a pledge row wears: the server's pledge title, else "Partner"
 *  (→ "Partner pledge") for an older row that carries only the id. */
internal fun pledgeTagTitle(r: GivingRecord): String = r.pledgeTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "Partner"
