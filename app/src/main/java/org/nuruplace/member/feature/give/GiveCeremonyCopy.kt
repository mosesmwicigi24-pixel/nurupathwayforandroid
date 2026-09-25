// Give — the ceremony's words once an intent exists, kept pure so
// GiveCeremonyCopyTest pins them. The rule (pledge names, contract
// 2026-09-25): the copy reads the RESULT, because when a gift carries a
// pledge_id the SERVER decides the fund and ignores the client's — so the
// chip the member tapped is the last thing we should name. Order:
//
//   result.pledge  → "toward your <pledge.title> pledge"
//   result.fund    → "to <fund.name>"
//   neither        → "to <chip label>"   (an older server), else nothing
//
// The ceremony then WATCHES the real transaction (iOS parity, 2026-09-26):
// GET /giving/transactions/{id} every 3 s, at most 20 times, until it is
// final — and says so. The intent's answer is its first reading, read the
// same way whether fresh or a replay of the same idempotency key (`reused`
// is never consulted): a replay of a gift that already went through shows
// "confirmed", one that failed shows "didn't complete".
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GivingIntentResult

/** Providers whose gift completes with a PIN prompt on the member's phone. */
private val PIN_PROVIDERS = setOf("mpesa", "airtel")

/** Where the gift went, as a phrase: "toward your Building pledge" · "to Tithe"
 *  · "to <chip>" · null when nothing is known. */
fun giveDestinationPhrase(r: GivingIntentResult, chipFundLabel: String?): String? {
    r.pledge?.title?.takeIf { it.isNotBlank() }?.let { return "toward your $it pledge" }
    r.fund?.name?.takeIf { it.isNotBlank() }?.let { return "to $it" }
    return chipFundLabel?.takeIf { it.isNotBlank() }?.let { "to $it" }
}

/** The ceremony's one instruction line. `amountMinor` is what was charged
 *  (the fee rides inside it, GiveSubmitLogic.kt). */
fun giveCeremonyLine(r: GivingIntentResult, amountMinor: Int, chipFundLabel: String?): String {
    val what = listOfNotNull(ksh(amountMinor), giveDestinationPhrase(r, chipFundLabel)).joinToString(" ")
    return when {
        r.provider?.lowercase() in PIN_PROVIDERS -> "Enter your PIN to complete $what."
        r.approveUrl != null -> "Continue on PayPal to complete $what, then confirm below."
        else -> "$what is being processed."
    }
}

/** The line under it, and the success state's: the pledge by name with the
 *  fund the church routed it to ("Building pledge · Gift"), else the fund,
 *  else the chip — then the member's own gift name, when they gave one. */
fun giveDestinationLabel(r: GivingIntentResult, chipFundLabel: String?, giftName: String? = null): String? {
    val pledge = r.pledge?.title?.takeIf { it.isNotBlank() }
    val fund = r.fund?.name?.takeIf { it.isNotBlank() } ?: chipFundLabel?.takeIf { it.isNotBlank() }
    val base = when {
        pledge != null -> listOfNotNull("$pledge pledge", r.fund?.name?.takeIf { it.isNotBlank() }).joinToString(" · ")
        fund != null -> fund
        else -> return null
    }
    return giftName?.trim()?.takeIf { it.isNotEmpty() }?.let { "$base — “$it”" } ?: base
}

/** Where a gift stands, from its transaction status (txn_status:
 *  requires_action · processing · succeeded · failed · refunded; older
 *  spellings settled / completed / cancelled read the same way). */
enum class GiftOutcome { Processing, Succeeded, Failed }

fun giftOutcome(status: String?): GiftOutcome = when (status?.trim()?.lowercase()) {
    "succeeded", "settled", "completed" -> GiftOutcome.Succeeded
    "failed", "cancelled", "canceled" -> GiftOutcome.Failed
    else -> GiftOutcome.Processing
}

/** The ceremony's watch: one GET /giving/transactions/{id} every 3 s… */
const val CEREMONY_WATCH_INTERVAL_MS = 3_000L

/** …at most 20 times (~60 s), as iOS. */
const val CEREMONY_WATCH_MAX = 20

/** Watch again only while the gift is still processing and the watch has
 *  readings left. */
fun keepWatchingGift(outcome: GiftOutcome, readingsDone: Int): Boolean =
    outcome == GiftOutcome.Processing && readingsDone < CEREMONY_WATCH_MAX

/** The ceremony's title for where the gift stands. */
fun giveCeremonyTitle(outcome: GiftOutcome): String =
    if (outcome == GiftOutcome.Failed) "Your gift didn't go through" else "Thank you for your generosity"

/** The ceremony's one line for where the gift stands: confirmed, didn't
 *  complete, still out of reach of the watch, or — while processing — the
 *  instruction line ([giveCeremonyLine]). */
fun giveCeremonyStatusLine(
    r: GivingIntentResult,
    amountMinor: Int,
    chipFundLabel: String?,
    outcome: GiftOutcome,
    watchLapsed: Boolean,
): String = when (outcome) {
    GiftOutcome.Succeeded -> "Gift confirmed — receipt on its way. 🎉"
    GiftOutcome.Failed -> "The payment didn't complete — no charge was made."
    GiftOutcome.Processing ->
        if (watchLapsed) "Still processing — your gift will show once it clears." else giveCeremonyLine(r, amountMinor, chipFundLabel)
}
