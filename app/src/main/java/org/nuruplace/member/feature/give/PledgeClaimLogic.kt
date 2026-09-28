// "I paid another way" (Giving Cycle 5) — the claim form's rules and the claim
// list's words, kept pure so PledgeClaimLogicTest pins them. A member who paid
// a pledge outside the app (at the office, by bank, to M-Pesa directly) tells
// the church here; the office checks it before it counts. What the server
// takes (partners.ts createClaim): the amount in the PLEDGE's currency —
// whole shillings for KES, cents for USD — the day it was paid, today or
// within the last year on the Nairobi calendar, and a note of at most 300
// characters; the same amount and day already waiting, or five waiting at
// once, is refused (409 CONFLICT) in the server's own words. Online only:
// nothing here is ever queued (OfflineQueue refuses pledge writes anyway).
//
// Also here: the pledge flow's "First collection: 5 October" — a pledge
// collected automatically is collected on its due day from the first one
// strictly after today, never today (PartnerStatementMath.firstDueAfter).
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.ClaimBody
import org.nuruplace.member.data.net.PledgeClaim
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A claim's note, at most this long (the server's limit). */
const val CLAIM_NOTE_MAX = 300

/** Said instead of the form while the phone is offline. */
const val CLAIM_OFFLINE_LINE = "You're offline — telling us about a payment needs a connection."

private val COLLECTION_DAY_FMT = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
private val COLLECTION_DAY_YEAR_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

/** The days a payment can be told about: a year back to today (Nairobi).
 *  The server allows 366 days back, so a year always passes. */
fun claimDateRange(today: LocalDate): ClosedRange<LocalDate> = today.minusDays(365)..today

fun claimDateAllowed(day: LocalDate, today: LocalDate): Boolean = day in claimDateRange(today)

/** Why the typed amount cannot be sent (null = fine), in the pledge's
 *  currency: whole shillings for KES, dollars and cents for USD. */
fun claimAmountError(text: String, currency: String): String? {
    val t = text.trim().replace(",", "")
    if (t.isEmpty()) return "Enter the amount you paid."
    return if (currencyCode(currency) == USD_CURRENCY) {
        val cents = usdCentsOf(t) ?: return "Enter the amount in US dollars, like 25.50."
        if (cents <= 0) "Enter the amount you paid." else null
    } else {
        if ('.' in t) return "Whole shillings only — no cents."
        val major = t.takeIf { s -> s.all { it.isDigit() } }?.toLongOrNull() ?: return "Enter the amount you paid."
        if (major <= 0) "Enter the amount you paid." else if (major > 10_000_000) "That's more than one payment can be." else null
    }
}

/** The typed amount in minor units of the pledge's currency; null when it is not one. */
fun claimAmountMinor(text: String, currency: String): Int? {
    if (claimAmountError(text, currency) != null) return null
    val t = text.trim().replace(",", "")
    return if (currencyCode(currency) == USD_CURRENCY) usdCentsOf(t) else t.toIntOrNull()?.times(100)
}

/** The claim form, checked: ready to send, or why not. */
sealed interface ClaimPlan {
    data class Ready(val body: ClaimBody) : ClaimPlan
    data class Invalid(val message: String) : ClaimPlan
}

/** Everything the form holds, checked against what the server takes. The
 *  currency is always the PLEDGE's — never the member's choice. */
fun planClaim(amountText: String, pledgeCurrency: String, paidOn: LocalDate, note: String, today: LocalDate): ClaimPlan {
    claimAmountError(amountText, pledgeCurrency)?.let { return ClaimPlan.Invalid(it) }
    if (!claimDateAllowed(paidOn, today)) return ClaimPlan.Invalid("Choose the day you paid — today or within the last year.")
    val trimmed = note.trim()
    if (trimmed.length > CLAIM_NOTE_MAX) return ClaimPlan.Invalid("Keep the note to $CLAIM_NOTE_MAX characters.")
    return ClaimPlan.Ready(
        ClaimBody(
            amountMinor = claimAmountMinor(amountText, pledgeCurrency)!!,
            currency = currencyCode(pledgeCurrency),
            paidOn = paidOn.toString(),
            note = trimmed.ifEmpty { null },
        ),
    )
}

/** How a claim stands, for its colour. */
enum class ClaimTone { Waiting, Recorded, Unmatched }

fun claimTone(status: String?): ClaimTone = when (status?.trim()?.lowercase()) {
    "confirmed" -> ClaimTone.Recorded
    "rejected" -> ClaimTone.Unmatched
    else -> ClaimTone.Waiting
}

/** "The office is checking it" · "Recorded — thank you" · "The office couldn't match it". */
fun claimStatusLine(status: String?): String = when (claimTone(status)) {
    ClaimTone.Waiting -> "The office is checking it"
    ClaimTone.Recorded -> "Recorded — thank you"
    ClaimTone.Unmatched -> "The office couldn't match it"
}

/** "KSh 3,000 · paid 12 September" — the year only when it isn't this one
 *  (iOS ClaimCopy.line). */
fun claimRowLine(c: PledgeClaim, today: LocalDate): String {
    val day = c.paidOn?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        ?.let { d -> d.format(if (d.year == today.year) COLLECTION_DAY_FMT else COLLECTION_DAY_YEAR_FMT) }
    return listOfNotNull(money(c.amountMinor, c.currency), day?.let { "paid $it" }).joinToString(" · ")
}

/** The day a monthly pledge collected automatically is first collected: its
 *  due day (held to 1–28) strictly after today — never today. */
fun firstCollectionDate(today: LocalDate, dueDay: Int): LocalDate = firstDueAfter(today, dueDay)

/** "5 October" — "5 January 2027" when it is not this year. */
fun firstCollectionDay(today: LocalDate, dueDay: Int): String {
    val d = firstCollectionDate(today, dueDay)
    return d.format(if (d.year == today.year) COLLECTION_DAY_FMT else COLLECTION_DAY_YEAR_FMT)
}

/** "First collection: 5 October" — under the toggle. */
fun firstCollectionLine(today: LocalDate, dueDay: Int): String = "First collection: ${firstCollectionDay(today, dueDay)}"
