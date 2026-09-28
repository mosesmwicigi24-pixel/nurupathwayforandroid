// What the Give segment shows from the member's own history (GET
// /giving/history, newest first) — iOS GivingView's "Repeat last gift" and
// RECENT GIVING — kept pure so GiveHistoryLogicTest pins it.
//
// One rule for both (iOS GiveMoney.isSettled, nuru-member-ios c89f483): only
// a gift whose money went through is offered again or listed as given. A
// failed, waiting or cancelled gift was never given — it has its own Try
// again — so a member whose only gift failed sees no "Repeat last gift"
// beside RECENT GIVING's "No gifts yet".
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GivingRecord

private val SETTLED = setOf("succeeded", "settled", "completed")

/** The gift's money went through (iOS GiveMoney.isSettled). */
fun giftSettled(status: String?): Boolean = status?.trim()?.lowercase() in SETTLED

/** "Repeat last gift": the newest ORDINARY gift that went through — never a
 *  pledge instalment or a gift to a department need (it would pay them
 *  again), never a failed, waiting or cancelled one. Null hides the card. */
fun lastRepeatableGift(history: List<GivingRecord>): GivingRecord? =
    history.firstOrNull { giftSettled(it.status) && it.pledgeId.isNullOrBlank() && it.needId.isNullOrBlank() }

/** RECENT GIVING: the three newest gifts that went through. */
fun recentGifts(history: List<GivingRecord>): List<GivingRecord> = history.filter { giftSettled(it.status) }.take(3)

/** "M-Pesa" for a gift's rail — M-Pesa when the row names none (iOS). */
private fun methodName(method: String?): String = giveMethodLabel(method?.takeIf { it.isNotBlank() } ?: "mpesa")

/** The Repeat card's line: "KSh 1,000 · Tithe · via M-Pesa". */
fun repeatGiftLine(g: GivingRecord): String = "${money(g.amountMinor, g.currency)} · ${giveFund(g.fund).name} · via ${methodName(g.method)}"

/** A RECENT GIVING row's line under the fund: "5 Oct · M-Pesa" (its Nairobi day). */
fun recentGiftMeta(g: GivingRecord): String {
    val day = parseNairobi(g.createdAt)?.toLocalDate()?.let { PartnerFormat.dayMonth(it) } ?: g.createdAt.take(10)
    return "$day · ${methodName(g.method)}"
}

/** What "Give again" puts on the form (iOS applyRepeat): its fund when it
 *  is one of the form's; its rail when that can take money here NOW; the
 *  amount in that rail's money — a dollar gift only onto a dollar rail
 *  (never read as shillings), a shilling gift without the fee it covered
 *  (the fee switch, back on, adds it again); and its name. Null fields are
 *  left as the form has them. */
data class RepeatPlan(
    val fundId: String?,
    val methodKey: String?,
    val usdCents: Int?,
    val amountMajor: Int?,
    val coverFee: Boolean?,
    val accountName: String,
)

/** [RepeatPlan] for gift [g], with the form's [options] and the rail it is on now ([current]). */
fun repeatPlan(g: GivingRecord, options: List<GiveMethodOption>, current: GiveMethodOption?): RepeatPlan {
    val fund = g.fund.trim().lowercase().takeIf { f -> GIVE_FUNDS.any { it.id == f } }
    val rail = g.method?.let { m -> options.firstOrNull { it.key == m && it.selectable } }
    // The rail the form will be on: the gift's, else the one it is on.
    val onRail = rail ?: current
    val name = g.accountName.orEmpty()
    return when {
        currencyCode(g.currency) == USD_CURRENCY ->
            RepeatPlan(fund, rail?.key, usdCents = g.amountMinor.takeIf { onRail?.inDollars == true }, amountMajor = null, coverFee = null, accountName = name)
        onRail != null && !onRail.inDollars -> {
            val fee = g.feeCoverMinor?.coerceAtLeast(0) ?: 0
            RepeatPlan(fund, rail?.key, usdCents = null, amountMajor = maxOf(0, g.amountMinor - fee) / 100, coverFee = fee > 0, accountName = name)
        }
        else -> RepeatPlan(fund, rail?.key, usdCents = null, amountMajor = null, coverFee = null, accountName = name)
    }
}
