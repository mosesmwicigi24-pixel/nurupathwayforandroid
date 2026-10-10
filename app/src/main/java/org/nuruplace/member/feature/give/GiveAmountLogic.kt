// Give — the amount, kept pure so GiveAmountLogicTest pins it (Giving Cycle 2).
//
// The form speaks two currencies: whole Kenyan shillings for M-Pesa, and US
// dollars WITH cents for PayPal, which cannot hold shillings (the server
// refuses anything else with METHOD_CURRENCY). Each keeps its own amount, so
// switching to PayPal and back restores the shillings untouched, and
// planGiveSubmission picks the amount by the METHOD's currency — a KSh number
// is never sent with PayPal.
//
// Covering the fee adds the M-Pesa fee to what is charged (amount_minor stays
// the total) and says how much of it was the fee (cover_fee_minor), so the
// receipt can read Gift · Fee cover · Total.
package org.nuruplace.member.feature.give

/** PayPal's currency. */
const val USD_CURRENCY = "USD"

/** The dollar form's suggested amounts, in whole dollars. */
val USD_PRESETS = listOf(5, 10, 25, 50, 100)

/** Where the dollar form starts: US$ 10.00. */
const val DEFAULT_USD_CENTS = 1_000

private val USD_TYPED = Regex("[0-9]{1,7}(\\.[0-9]{0,2})?")

/** "25" · "25.5" · "25.50" · "1,000" · "25." → cents; null for anything
 *  else — empty, a third decimal, two points, a sign, letters. */
fun usdCentsOf(text: String): Int? {
    val t = text.trim().replace(",", "")
    if (!USD_TYPED.matches(t)) return null
    val dollars = t.substringBefore('.').toLong()
    val cents = t.substringAfter('.', "").padEnd(2, '0').toLong()
    return (dollars * 100 + cents).takeIf { it <= Int.MAX_VALUE }?.toInt()
}

/** What may be typed into the dollar field: digits and one point, with at
 *  most two decimals — anything else is dropped as it is typed. */
fun usdTyping(v: String): String {
    val kept = v.filter { it.isDigit() || it == '.' }
    val point = kept.indexOf('.')
    if (point < 0) return kept.take(7)
    val whole = kept.substring(0, point).take(7)
    val decimals = kept.substring(point + 1).filter { it.isDigit() }.take(2)
    return "$whole.$decimals"
}

// The big amount's size is TypeScale.amount (ui/theme): the scale's top step,
// 28, stepping down on the scale as the figure grows (EXPERIENCE.md §8.2 #21).
// It replaced amountDisplaySize's 42/38/34/29, which were off the scale.

/** The dollar field's text for an amount: "25" for whole dollars, else "25.50". */
fun usdInput(cents: Int): String =
    if (cents % 100 == 0) "${cents / 100}" else "%d.%02d".format(cents / 100, cents % 100)

/** "US$ 25.00". */
fun usd(cents: Int): String = money(cents, USD_CURRENCY)

/** What the custom dollar amount says when it is out of the rail's range:
 *  "Enter an amount between US$ 1.00 and US$ 10,000.00." */
fun usdRangeMessage(minMinor: Long, maxMinor: Long): String =
    "Enter an amount between ${money(minMinor, USD_CURRENCY)} and ${money(maxMinor, USD_CURRENCY)}."

/** The fee the member covers, in minor units — whole shillings, 0 when not covering. */
fun coverFeeMinor(amountMajor: Int, coverFee: Boolean): Int = if (coverFee) giveFee(amountMajor) * 100 else 0

/** A shilling gift split the way the intent carries it: amount_minor is the
 *  TOTAL charged (gift + fee cover); cover_fee_minor is the fee part. */
data class FeeSplit(val giftMinor: Int, val feeMinor: Int) {
    val totalMinor: Int get() = giftMinor + feeMinor
}

fun feeSplit(amountMajor: Int, coverFee: Boolean): FeeSplit =
    FeeSplit(giftMinor = amountMajor * 100, feeMinor = coverFeeMinor(amountMajor, coverFee))
