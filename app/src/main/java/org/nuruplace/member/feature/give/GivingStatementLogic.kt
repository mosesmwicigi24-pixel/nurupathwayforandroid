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
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GivingRecord
import java.time.LocalDate

/** One period's rows, split. `totalMinor` = gifts + pledges by construction. */
internal data class GivingSplit(
    val gifts: List<GivingRecord>,
    val pledges: List<GivingRecord>,
) {
    val giftsMinor: Int get() = gifts.sumOf { it.amountMinor }
    val pledgesMinor: Int get() = pledges.sumOf { it.amountMinor }
    val totalMinor: Int get() = giftsMinor + pledgesMinor
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
internal data class GivingHero(val label: String, val amountMinor: Int, val pledgeLine: String?, val footLabel: String)

internal fun givingHero(split: GivingSplit): GivingHero =
    if (split.pledges.isEmpty()) {
        GivingHero("Total given", split.totalMinor, null, "TOTAL GIVEN")
    } else {
        GivingHero(
            label = "Gifts",
            amountMinor = split.giftsMinor,
            pledgeLine = "Partner pledges ${ksh(split.pledgesMinor)} · Total ${ksh(split.totalMinor)}",
            footLabel = "TOTAL GIFTS",
        )
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

/** The collapsed PARTNER PLEDGES group: its total, its count, the header line
 *  "KSh Y · N payments", and the rows by day for when it opens. */
internal data class PledgeGroup(val totalMinor: Int, val count: Int, val days: List<StatementDay>) {
    val summary: String get() = "${ksh(totalMinor)} · $count payment${if (count == 1) "" else "s"}"
}

/** Null — no group at all — when the period has no pledge money. */
internal fun pledgeGroup(split: GivingSplit): PledgeGroup? {
    if (split.pledges.isEmpty()) return null
    return PledgeGroup(split.pledgesMinor, split.pledges.size, statementDays(split.pledges))
}

/** The gold tag a pledge row wears: the server's pledge title, else "Partner"
 *  (→ "Partner pledge") for an older row that carries only the id. */
internal fun pledgeTagTitle(r: GivingRecord): String = r.pledgeTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "Partner"
