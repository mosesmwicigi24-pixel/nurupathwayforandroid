// Give — the receipt's words, kept pure so GiveReceiptCopyTest pins them
// (receipt v2, owner 2026-09-25: "appealing and well coloured; I like the
// green; best UX"). The server now resolves the display names on
// GET /giving/transactions/{id} — fund_name, pledge, need, method_label,
// member_name — so the receipt never guesses; every rule here still falls
// back for an older server that sends none of them.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GivingDetail
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WHEN_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy · h:mm a", Locale.ENGLISH)
private val DAY_ONLY_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** The fund's display name: the server's `fund_name`, else the local table's
 *  name for the code, else the code capitalised (giveFund's own fallback). */
fun receiptFundName(d: GivingDetail): String = d.fundName.clean() ?: giveFund(d.fund).name

/** The pledge's title when this gift counts toward one. */
fun receiptPledgeTitle(d: GivingDetail): String? = d.pledge?.title.clean()

/** Where the gift went — the hero's line under the amount:
 *  pledge → "toward your School fees pledge" · need → "to Sound desk" ·
 *  else "to the Discipleship fund". A pledge outranks a need. */
fun receiptDestinationLine(d: GivingDetail): String {
    receiptPledgeTitle(d)?.let { return "toward your $it pledge" }
    d.need?.title.clean()?.let { return "to $it" }
    return "to the ${receiptFundName(d)} fund"
}

/** "Thank you, Moses." — the first word of the server's member_name, else of
 *  the signed-in profile's name, else null (the line is omitted). */
fun receiptFirstName(memberName: String?, profileName: String?): String? =
    listOf(memberName, profileName).firstNotNullOfOrNull { n ->
        n.clean()?.split(Regex("\\s+"))?.firstOrNull().clean()
    }

/** "M-Pesa" — the server's method_label, else the local table's, else "—". */
fun receiptMethodLabel(d: GivingDetail): String =
    d.methodLabel.clean() ?: d.method.clean()?.let { giveMethodLabel(it) } ?: "—"

/** The provider's own reference (the M-Pesa receipt code, a PayPal order id…), if any. */
fun receiptProviderRef(d: GivingDetail): String? = d.receiptCode.clean() ?: d.providerRef.clean()

/** The provider-reference row's label: "M-Pesa receipt" / "Airtel receipt" / "Reference". */
fun receiptReferenceLabel(d: GivingDetail): String {
    val code = d.method.clean()?.lowercase()
    val label = receiptMethodLabel(d)
    return when {
        code == "mpesa" || label.equals("M-Pesa", ignoreCase = true) -> "M-Pesa receipt"
        code == "airtel" || label.startsWith("Airtel", ignoreCase = true) -> "Airtel receipt"
        else -> "Reference"
    }
}

/** ("KSh", "500") / ("$", "12.50") — the hero's small currency mark beside the
 *  big number. Currency-aware like [money] (a PayPal gift settles in USD). */
fun receiptAmountParts(minor: Int, currency: String?): Pair<String, String> {
    val s = money(minor, currency)
    return if (s.startsWith("$")) "$" to s.drop(1) else s.substringBefore(' ') to s.substringAfter(' ')
}

/** How a not-yet-succeeded gift is chipped; null = succeeded (the green check says it). */
enum class ReceiptTone { Waiting, NotCompleted, Refunded }

data class ReceiptChip(val label: String, val tone: ReceiptTone)

private val MOBILE_MONEY = setOf("mpesa", "airtel")

/** Status → chip. pending / processing (or anything unknown — never assume
 *  received) → "Waiting for M-Pesa" for mobile money, "Processing" otherwise;
 *  failed / cancelled → "Not completed"; refunded → "Refunded";
 *  succeeded / settled / completed → null. */
fun receiptStatusChip(d: GivingDetail): ReceiptChip? = when (d.status.lowercase().trim()) {
    "succeeded", "settled", "completed" -> null
    "failed", "cancelled", "canceled" -> ReceiptChip("Not completed", ReceiptTone.NotCompleted)
    "refunded" -> ReceiptChip("Refunded", ReceiptTone.Refunded)
    else -> ReceiptChip(
        if (d.method.clean()?.lowercase() in MOBILE_MONEY) "Waiting for ${receiptMethodLabel(d)}" else "Processing",
        ReceiptTone.Waiting,
    )
}

/** The hero's eyebrow, honest to the status. */
fun receiptEyebrow(chip: ReceiptChip?): String = when (chip?.tone) {
    null -> "GIFT RECEIVED"
    ReceiptTone.Waiting -> "GIFT PENDING"
    ReceiptTone.NotCompleted -> "GIFT NOT COMPLETED"
    ReceiptTone.Refunded -> "GIFT REFUNDED"
}

/** "100% of this gift reaches the Discipleship fund." (+ " · counts toward your pledge"). */
fun receiptWhereItWent(d: GivingDetail): String {
    val base = "100% of this gift reaches the ${receiptFundName(d)} fund."
    val onPledge = receiptPledgeTitle(d) != null || d.pledge?.pledgeId.clean() != null
    return if (onPledge) "$base · counts toward your pledge" else base
}

/** The instant the receipt is dated by: settled_at once settled, else created_at. */
private fun receiptInstant(d: GivingDetail) = parseNairobi(d.settledAt.clean() ?: d.createdAt)

/** "Fri 25 Sep 2026 · 8:11 PM" (Nairobi), "—" when unknown. */
fun receiptWhen(d: GivingDetail): String = receiptInstant(d)?.format(WHEN_FMT) ?: "—"

/** "25 Sep 2026" (Nairobi) — the share line's date; empty when unknown. */
fun receiptDay(d: GivingDetail): String = receiptInstant(d)?.format(DAY_ONLY_FMT) ?: ""

/** The one-line summary that rides with the PDF — and is all that is shared
 *  when the PDF cannot be fetched:
 *  "KSh 500 to the Discipleship fund · M-Pesa UIPJ27PBO3 · 25 Sep 2026". */
fun receiptShareText(d: GivingDetail): String {
    val method = listOfNotNull(receiptMethodLabel(d).takeIf { it != "—" }, receiptProviderRef(d)).joinToString(" ")
    return listOfNotNull(
        "${money(d.amountMinor, d.currency)} ${receiptDestinationLine(d)}",
        method.takeIf { it.isNotEmpty() },
        receiptDay(d).takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
}

/** "nuru-receipt-UIPJ27PBO3.pdf" — the receipt code, else the short id. Only
 *  letters, digits, "-" and "_" survive (no dots: never a ".." in a file name). */
fun receiptFileName(d: GivingDetail): String {
    val key = (d.receiptCode.clean() ?: d.transactionId.take(8)).replace(Regex("[^A-Za-z0-9_-]"), "")
    return "nuru-receipt-${key.ifEmpty { "gift" }}.pdf"
}

/** "3f2a9c1e…" — the transaction id shortened for the eye (a tap copies the full id). */
fun receiptShortId(d: GivingDetail): String = d.transactionId.take(8) + "…"
