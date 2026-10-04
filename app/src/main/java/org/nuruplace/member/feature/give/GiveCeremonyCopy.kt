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
// The ceremony then WATCHES the real transaction (iOS StkWatch, EXPERIENCE.md
// §7.2 #5, §7.3): GET /giving/transactions/{id} every 3 s for the first
// minute, then every 10 s up to five, while it is on screen — so an answer
// that comes late still lands. The intent's answer is its first reading, read
// the same way whether fresh or a replay of the same idempotency key
// (`reused` is never consulted): a replay of a gift that already went through
// shows "confirmed", one that failed shows "didn't complete" — and, since
// Giving Cycle 1, WHY, in the server's own words (failure.reason + hint).
//
// Waiting for M-Pesa is never a celebration (§7.3): while a PIN prompt waits
// the stage is "Check your phone" — the PIN line, "Prompt sent to …", a quiet
// Close — and "Thank you for your generosity" with its tick comes only on the
// server's confirmed success. It used to say "Thank you" while the prompt was
// still waiting.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GiftFailure
import org.nuruplace.member.data.net.GivingIntentResult

/** Providers whose gift completes with a PIN prompt on the member's phone. */
private val PIN_PROVIDERS = setOf("mpesa", "airtel")

/** Where the gift went, as a phrase: "toward your Building pledge" · "to Tithe"
 *  · "to <chip>" · null when nothing is known. A pledge whose name already
 *  ends in "pledge" is not doubled ([pledgeTag], iOS GiveDestination). */
fun giveDestinationPhrase(r: GivingIntentResult, chipFundLabel: String?): String? {
    r.pledge?.title?.takeIf { it.isNotBlank() }?.let { return "toward your ${pledgeTag(it)}" }
    r.fund?.name?.takeIf { it.isNotBlank() }?.let { return "to $it" }
    return chipFundLabel?.takeIf { it.isNotBlank() }?.let { "to $it" }
}

/** The ceremony's one instruction line. `amountMinor` is what was charged
 *  (the fee rides inside it, GiveSubmitLogic.kt), in `currency` — a PayPal
 *  gift is in US dollars. PayPal finishes on its own when the member comes
 *  back from approving (Giving Cycle 2), so the line says exactly that. */
fun giveCeremonyLine(r: GivingIntentResult, amountMinor: Int, chipFundLabel: String?, currency: String? = null): String {
    val what = listOfNotNull(money(amountMinor, currency), giveDestinationPhrase(r, chipFundLabel)).joinToString(" ")
    return when {
        r.provider?.lowercase() in PIN_PROVIDERS -> "Enter your PIN to complete $what."
        r.approveUrl != null -> "Continue on PayPal to complete $what — we'll confirm it when you come back."
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
        pledge != null -> listOfNotNull(pledgeTag(pledge), r.fund?.name?.takeIf { it.isNotBlank() }).joinToString(" · ")
        fund != null -> fund
        else -> return null
    }
    return giftName?.trim()?.takeIf { it.isNotEmpty() }?.let { "$base — “$it”" } ?: base
}

/** A PayPal gift still waiting for the member's approval, whose approval page
 *  the ceremony can (re)open: its answer carried `approve_url` — a fresh
 *  order's, or since Giving Cycle 10 a RESENT one's (the same key after a
 *  lost answer), which used to come back without it — and it has not settled
 *  or failed. */
fun canReopenPayPal(r: GivingIntentResult, status: String?): Boolean =
    !r.approveUrl.isNullOrBlank() && giftOutcome(status) == GiftOutcome.Processing

/** Where a gift stands, from its transaction status (txn_status:
 *  requires_action · processing · succeeded · failed · refunded; older
 *  spellings settled / completed / cancelled read the same way). */
enum class GiftOutcome { Processing, Succeeded, Failed }

fun giftOutcome(status: String?): GiftOutcome = when (status?.trim()?.lowercase()) {
    "succeeded", "settled", "completed" -> GiftOutcome.Succeeded
    "failed", "cancelled", "canceled" -> GiftOutcome.Failed
    else -> GiftOutcome.Processing
}

/** The ceremony's watch while the gift is processing and on screen (iOS
 *  StkWatch, §7.2 #5): every 3 s for the first minute, then every 10 s up
 *  to five minutes. Past the minute the wait is late: its line says so and
 *  Done leads — while the watch keeps going. */
object GiftWatch {
    const val LATE_AFTER_MS = 60_000L
    const val WATCH_FOR_MS = 300_000L

    /** Milliseconds before the next look, [elapsedMs] into the wait — null
     *  once the watch is over. */
    fun nextDelayMs(elapsedMs: Long): Long? = when {
        elapsedMs >= WATCH_FOR_MS -> null
        elapsedMs < LATE_AFTER_MS -> 3_000L
        else -> 10_000L
    }

    /** Past the minute: the late line, and Done as the primary. */
    fun isLate(elapsedMs: Long): Boolean = elapsedMs >= LATE_AFTER_MS
}

/** The wait for a PIN prompt (§7.3, iOS StkStage) — never a celebration. */
const val STK_TITLE = "Check your phone"

/** Under the prompt for its first minute, beside a small spinner. */
const val STK_WAITING_LINE = "Waiting up to 60s…"

/** Past the minute, on both apps (§7.2 #5): where the gift will show —
 *  RECENT GIVING lists it once it clears. */
const val GIFT_LATE_LINE = "Still processing — it will show in Recent giving once it clears."

/** A gift waiting on a PIN prompt on the member's phone — an M-Pesa or
 *  Airtel gift still processing (a provider-less answer that sent a prompt
 *  counts too): its stage is "Check your phone". A PayPal or card gift keeps
 *  its own stage. */
fun waitsOnPhone(r: GivingIntentResult, status: String?, promptPhone: String?): Boolean =
    giftOutcome(status) == GiftOutcome.Processing && r.approveUrl.isNullOrBlank() &&
        (r.provider?.lowercase() in PIN_PROVIDERS || (r.provider.isNullOrBlank() && promptPhone != null))

/** "Check your phone"'s line, its amount picked out in gold (iOS StkStage):
 *  "Enter your PIN to complete " · "KSh 1,000" · " to Tithe — “Tithe”." —
 *  the RESULT's pledge or fund, then the member's own gift name. */
data class StkPinLine(val lead: String, val amount: String, val rest: String) {
    val text: String get() = lead + amount + rest
}

fun stkPinLine(
    r: GivingIntentResult,
    amountMinor: Int,
    chipFundLabel: String?,
    giftName: String? = null,
    currency: String? = null,
): StkPinLine {
    val destination = giveDestinationPhrase(r, chipFundLabel)?.let { " $it" }.orEmpty()
    val name = giftName?.trim()?.takeIf { it.isNotEmpty() }?.let { " — “$it”" }.orEmpty()
    return StkPinLine("Enter your PIN to complete ", money(amountMinor, currency), "$destination$name.")
}

/** The ceremony's title for where the gift stands — for every stage but
 *  "Check your phone" ([STK_TITLE]): the server's confirmed success, a gift
 *  that didn't go through, and a PayPal gift's own wait. */
fun giveCeremonyTitle(outcome: GiftOutcome): String =
    if (outcome == GiftOutcome.Failed) "Your gift didn't go through" else "Thank you for your generosity"

/** The ceremony's one line for where the gift stands: confirmed, didn't
 *  complete — in the server's words when it named why ([failure]'s reason)
 *  — past the watch's minute ([late]), or, while processing, the
 *  instruction line ([giveCeremonyLine]). */
fun giveCeremonyStatusLine(
    r: GivingIntentResult,
    amountMinor: Int,
    chipFundLabel: String?,
    outcome: GiftOutcome,
    late: Boolean,
    failure: GiftFailure? = null,
    currency: String? = null,
): String = when (outcome) {
    GiftOutcome.Succeeded -> "Gift confirmed — receipt on its way. 🎉"
    GiftOutcome.Failed -> failure?.reason?.trim()?.takeIf { it.isNotEmpty() }
        ?: "The payment didn't complete — no charge was made."
    GiftOutcome.Processing ->
        if (late) GIFT_LATE_LINE else giveCeremonyLine(r, amountMinor, chipFundLabel, currency)
}

/** The failed ceremony's second line: what to do next and whether money
 *  moved — the server's hint, verbatim. Null otherwise. */
fun giveCeremonyHint(outcome: GiftOutcome, failure: GiftFailure?): String? =
    if (outcome == GiftOutcome.Failed) failure?.hint?.trim()?.takeIf { it.isNotEmpty() } else null

/** The failure a gift shows — history row, receipt, ceremony: only a gift
 *  that failed, and only when the server named why. */
fun shownFailure(status: String?, failure: GiftFailure?): GiftFailure? =
    failure?.takeIf { giftOutcome(status) == GiftOutcome.Failed && it.reason.isNotBlank() }
