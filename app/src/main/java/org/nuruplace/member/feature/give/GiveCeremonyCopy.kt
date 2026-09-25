// Give — the ceremony's words once an intent exists, kept pure so
// GiveCeremonyCopyTest pins them. The rule (pledge names, contract
// 2026-09-25): the copy reads the RESULT, because when a gift carries a
// pledge_id the SERVER decides the fund and ignores the client's — so the
// chip the member tapped is the last thing we should name. Order:
//
//   result.pledge  → "toward your <pledge.title> pledge"
//   result.fund    → "to <fund.name>"
//   neither        → "to <chip label>"   (an older server), else nothing
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
