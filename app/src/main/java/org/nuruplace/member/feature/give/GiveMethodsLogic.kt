// Give — HOW a gift is paid, kept pure so GiveMethodsLogicTest pins it
// (Giving Cycle 1). Two things live here:
//
// 1. Which methods the form may select. The server says, on GET
//    /giving/methods, which rails can take a member's money here and on what
//    terms; this app adds what IT can carry to the end — an STK push (M-Pesa,
//    Airtel) or PayPal's approve-then-capture — in a currency the form can
//    express: whole shillings, or (Giving Cycle 2) US dollars with cents for
//    PayPal. A card needs the Stripe SDK this app does not have (its intents
//    sat "processing" for ever), so it is never selectable here even when the
//    server has it live. A pledge or a need has ONE currency, and it decides
//    the rails (Giving Cycle 5): a KES pledge is paid with M-Pesa, a USD one
//    with PayPal — the server refuses any other (422 CURRENCY_MISMATCH), so
//    the form neither shows nor sends one. A rail the app does not know is
//    dropped; no answer at all (an older server, a failed call) leaves M-Pesa
//    alone. Only rails the server lists get a row.
//
// 2. The number the prompt goes to — the server's own Kenyan-mobile rule
//    (financial/service.ts kenyanMobileNumber over providers.ts toMsisdn), so
//    the app never accepts a number the server refuses, nor refuses one it
//    takes. Prefilled from the last number this device used, else the
//    profile's; with neither, the member must add one before giving.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.GivingMethodInfo
import org.nuruplace.member.data.net.GivingMethodsRes

/** The form's home currency: whole Kenyan shillings. */
const val GIVE_FORM_CURRENCY = "KES"

/** The currencies the form can express: shillings, and PayPal's US dollars
 *  (with cents, GiveAmountLogic.kt). */
private val FORM_CURRENCIES = setOf(GIVE_FORM_CURRENCY, USD_CURRENCY)

/** Rails the server may list that this app has a row for. */
private val KNOWN_METHOD_KEYS = setOf("mpesa", "airtel", "paypal", "card")

/** Rails this app can carry to the end: an STK push, or PayPal's approve + capture. */
private val APP_FLOWS = setOf("mpesa", "airtel", "paypal")

/** A rail as the form sees it: the server's terms, and whether this form can use it. */
data class GiveMethodOption(
    val key: String,
    val label: String,
    /** The server can take money on it right now. */
    val enabled: Boolean,
    val unavailableReason: String? = null,
    val currency: String? = GIVE_FORM_CURRENCY,
    val minMinor: Long = 0,
    /** 0 = the server named no ceiling (it still checks). */
    val maxMinor: Long = 0,
    val wholeUnits: Boolean = true,
    /** A Weekly / Monthly gift can run on it. */
    val recurring: Boolean = false,
    /** It prompts a phone, so the gift needs a number. */
    val needsPhone: Boolean = false,
) {
    /** Selectable on THIS form: live on the server, carried by this app, in
     *  a currency the form can express. */
    val selectable: Boolean
        get() = enabled && key in APP_FLOWS && currency?.uppercase() in FORM_CURRENCIES

    /** Settles in US dollars (PayPal): the form switches to its dollar entry. */
    val inDollars: Boolean get() = currency.equals(USD_CURRENCY, ignoreCase = true)

    /** In this currency — null (an older server) is shillings. */
    fun inCurrency(code: String): Boolean = currencyCode(currency) == currencyCode(code)

    /** Offered for this gift: every rail, or — bound to a pledge or need
     *  ([boundCurrency]) — only those in its currency. */
    fun shownFor(boundCurrency: String?): Boolean = boundCurrency == null || inCurrency(boundCurrency)

    /** Selectable for this gift: selectable, and in a bound gift's currency. */
    fun selectableFor(boundCurrency: String?): Boolean = selectable && shownFor(boundCurrency)
}

/** M-Pesa on the server's own terms (FinancialService.RAILS.mpesa) — all the
 *  form offers until GET /giving/methods answers. */
val FALLBACK_MPESA = GiveMethodOption(
    key = "mpesa", label = "M-Pesa", enabled = true, currency = "KES",
    minMinor = 100, maxMinor = 25_000_000, wholeUnits = true, recurring = true, needsPhone = true,
)

/** The rails the server listed that this app knows, in the server's order —
 *  or M-Pesa alone when there is no answer to read. */
fun giveMethodOptions(res: GivingMethodsRes?): List<GiveMethodOption> =
    res?.methods.orEmpty()
        .filter { it.key in KNOWN_METHOD_KEYS }
        .distinctBy { it.key }
        .map { it.toOption() }
        .ifEmpty { listOf(FALLBACK_MPESA) }

private fun GivingMethodInfo.toOption() = GiveMethodOption(
    key = key,
    label = label.ifBlank { giveMethodLabel(key) },
    enabled = enabled,
    unavailableReason = unavailableReason,
    currency = currency,
    minMinor = minMinor,
    maxMinor = maxMinor,
    wholeUnits = wholeUnits,
    recurring = recurring,
    needsPhone = needsPhone,
)

/** Where the form starts: the server's `default_method` when this form can
 *  take it (for this gift — bound to a pledge or need in [boundCurrency], or
 *  not), else the first rail it can, else none at all. */
fun defaultGiveMethod(res: GivingMethodsRes?, options: List<GiveMethodOption>, boundCurrency: String? = null): String? {
    val selectable = options.filter { it.selectableFor(boundCurrency) }.map { it.key }
    return res?.defaultMethod?.takeIf { it in selectable } ?: selectable.firstOrNull()
}

/** The method a gift goes on: the member's pick while the form can still
 *  take it, else the default — a rail switched off mid-visit is never sent,
 *  nor one in another currency than the pledge or need the form is bound to. */
fun effectiveGiveMethod(
    picked: String?,
    res: GivingMethodsRes?,
    options: List<GiveMethodOption>,
    boundCurrency: String? = null,
): GiveMethodOption? {
    val key = picked?.takeIf { p -> options.any { it.key == p && it.selectableFor(boundCurrency) } }
        ?: defaultGiveMethod(res, options, boundCurrency)
    return options.firstOrNull { it.key == key }
}

/** The rows the form lists, in the member's order: the rails the server
 *  listed (no Equity Bank, no device wallet — it never lists them), and —
 *  bound to a pledge or need — only those in its currency. */
fun shownGiveMethods(rows: List<GiveMethod>, options: List<GiveMethodOption>, boundCurrency: String?): List<GiveMethod> =
    rows.filter { row -> options.any { it.key == row.id && it.shownFor(boundCurrency) } }

/** The chip on a method the form cannot take (iOS unavailableBadge): SOON
 *  for one that is coming — the server's `coming_soon`, or one the server
 *  has live that this app cannot carry yet (a card) — UNAVAILABLE for one
 *  switched off here (any other reason, or none), or not listed at all. */
fun methodChipLabel(option: GiveMethodOption?): String = when {
    option != null && option.enabled -> "SOON"
    option?.unavailableReason == "coming_soon" -> "SOON"
    else -> "UNAVAILABLE"
}

/** What Give says when a rail cannot take a gift here (iOS unavailableNote):
 *  "PayPal giving is coming soon." · "PayPal isn't available for giving
 *  right now." — by its chip. */
fun methodUnavailableNote(option: GiveMethodOption): String =
    if (methodChipLabel(option) == "SOON") "${option.label} giving is coming soon."
    else "${option.label} isn't available for giving right now."

/** The Give tab's footer promise (iOS GivingMethods.secureNote): it names
 *  only the rails that can take money here — "Secure · M-Pesa · Receipt sent
 *  instantly", "Secure · M-Pesa & PayPal · Receipt sent instantly" — never a
 *  card or a rail that is off; none at all: "Secure · Receipt sent
 *  instantly". */
fun giveSecureNote(options: List<GiveMethodOption>): String {
    val names = options.filter { it.selectable }.map { it.label.ifBlank { giveMethodLabel(it.key) } }
    return if (names.isEmpty()) "Secure · Receipt sent instantly" else "Secure · ${names.joinToString(" & ")} · Receipt sent instantly"
}

/** "US dollars" · "shillings" — a currency in words. */
fun currencyWords(code: String?): String = when (currencyCode(code)) {
    GIVE_FORM_CURRENCY -> "shillings"
    USD_CURRENCY -> "US dollars"
    else -> currencyCode(code)
}

/** Why a rail in another currency cannot pay a bound gift — the form never
 *  offers one; this holds even if it did (the server's 422 CURRENCY_MISMATCH). */
fun boundCurrencyMessage(boundCurrency: String): String = when (currencyCode(boundCurrency)) {
    USD_CURRENCY -> "Gifts toward this are in US dollars. Choose PayPal to give."
    GIVE_FORM_CURRENCY -> "Gifts toward this are in shillings. Choose M-Pesa to give."
    else -> "Gifts toward this are in ${currencyWords(boundCurrency)}."
}

/** Why nothing can pay a bound gift in its currency (a USD pledge while
 *  PayPal is off), in iOS's words (unavailableNote(forCurrency:)): "Gifts
 *  toward this are in US dollars — PayPal giving is coming soon." Null while
 *  a rail in that currency can take it. */
fun noRailForCurrencyMessage(boundCurrency: String, options: List<GiveMethodOption>): String? {
    if (options.any { it.selectableFor(boundCurrency) }) return null
    val words = currencyWords(boundCurrency)
    val rail = options.firstOrNull { it.shownFor(boundCurrency) }
        ?: return "Gifts toward this are in $words, and there's no way to give in $words here yet."
    return "Gifts toward this are in $words — ${methodUnavailableNote(rail)}"
}

// ── The prompt number ──

private val KENYAN_MSISDN = Regex("254[17][0-9]{8}")

/** The server's words for a number it will not prompt (PHONE_REQUIRED). */
const val PHONE_INVALID_MESSAGE = "That doesn't look like a Kenyan mobile number. Use 07XX XXX XXX or 01XX XXX XXX."

/** What a gift on [label] says with no number to prompt. */
fun phoneNeededMessage(label: String): String = "Add the $label number the prompt should go to."

/**
 * A Kenyan mobile-money number as E.164 (+2547XXXXXXXX / +2541XXXXXXXX), or
 * null when it is not one. Spaces and punctuation go; a leading 0 is the
 * local form (07…, 01…), a bare nine digits starting 7 or 1 is too, and
 * 254… / +254… are already international.
 */
fun kenyanMobileE164(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var d = raw.filter { it in '0'..'9' }
    if (d.startsWith("0")) d = "254" + d.drop(1)
    else if (d.length == 9 && (d[0] == '7' || d[0] == '1')) d = "254$d"
    return if (KENYAN_MSISDN.matches(d)) "+$d" else null
}

/** "0711 222 333" — how a Kenyan reads their own number; anything that is
 *  not one comes back as given. */
fun kenyanMobileDisplay(number: String?): String {
    val e164 = kenyanMobileE164(number) ?: return number.orEmpty()
    val local = "0" + e164.removePrefix("+254")
    return "${local.take(4)} ${local.substring(4, 7)} ${local.substring(7)}"
}

/** The number the form starts on: the last one this device prompted, else
 *  the profile's; null = none, and a phone rail needs one before it gives. */
fun promptPhonePrefill(lastUsed: String?, phoneOnFile: String?): String? =
    kenyanMobileE164(lastUsed) ?: kenyanMobileE164(phoneOnFile)

/**
 * The `phone_number` a new schedule carries: the chosen number ONLY when it
 * is not the profile's. The server stores a number only when the member
 * chose one for this gift; absent, every cycle follows the profile number, so
 * a number changed later is the one prompted.
 */
fun schedulePhoneFor(promptPhone: String?, phoneOnFile: String?): String? {
    val chosen = kenyanMobileE164(promptPhone) ?: return null
    return chosen.takeIf { it != kenyanMobileE164(phoneOnFile) }
}
