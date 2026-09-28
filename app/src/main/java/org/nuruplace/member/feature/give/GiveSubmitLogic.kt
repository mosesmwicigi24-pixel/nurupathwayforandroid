// Give — the ONE decision the sticky CTA makes, kept pure so it can be pinned
// by a unit test (GiveSubmitLogicTest): a One-time gift creates an INTENT
// (POST /giving/intents); a Weekly/Monthly choice creates a SCHEDULE
// (POST /giving/schedules) — never an intent (docs/PARTNERS_PROGRAMME.md §0,
// "Android money fix"; before this, every frequency silently created a
// one-off intent). Since Giving Cycle 1 the form starts on One-time — a
// schedule is only ever a deliberate Weekly / Monthly pick, confirmed first —
// and whether a method can take a gift (or a recurring one) is the server's
// word on GET /giving/methods (GiveMethodsLogic.kt), not a table here.
//
// The cover-fee choice is applied the way iOS applies it (GivingView
// `total = amount + fee`, then `amountMinor: total * 100`): the fee rides
// INSIDE amount_minor, for intents and schedules alike. iOS sends no separate
// cover_fee field, so neither do we — the spec's optional `cover_fee` on
// /giving/intents is left for the server to adopt without double-charging.
//
// The double-pay guard (owner, 2026-09-26) lives here too: which outcome of a
// pledge- or need-bound gift SPENDS its binding, and the ordinary form the
// screen returns to once it has (giftSpendsBinding, giveFormSeed) — and the
// idempotent retry: the key a Pay tap sends is REPLAYED only after an attempt
// that got no server answer at all (giveKeyFor, keepGiveKeyAfter).
//
// And what a refused gift does next (giveErrorAction): 409 GIFT_IN_PROGRESS
// follows the prompt already on the member's phone; every other refusal says
// the server's own member-facing message. And "Try again" on a failed gift
// (Giving Cycle 3): POST /giving/transactions/{id}/retry with a fresh key —
// replayed, like a gift's, only after an attempt that got no answer
// (retryKeyFor) — so a retry never loses the failed gift's pledge or need.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.CreateScheduleBody
import org.nuruplace.member.data.net.FundRef
import org.nuruplace.member.data.net.GiveBody
import org.nuruplace.member.data.net.GivingDetail
import org.nuruplace.member.data.net.GivingIntentResult
import org.nuruplace.member.data.net.ServerError
import java.io.IOException

/** Frequency indices as the segmented control lays them out. */
const val FREQ_ONCE = 0
const val FREQ_WEEKLY = 1
const val FREQ_MONTHLY = 2

/** What a pledge's "Charge me automatically" offers (NewPledgeFlow): the one
 *  rail the server collects recurring gifts on (Giving Cycle 1 — Airtel is
 *  not live). The Give form reads each method's own `recurring` instead. */
val RECURRING_PROVIDERS = setOf("mpesa")

const val RECURRING_BLOCKED_MESSAGE = "Recurring gifts are collected with M-Pesa. Choose M-Pesa to set one up."
const val NEED_RECURRING_BLOCKED_MESSAGE = "A gift to a department need is a one-time gift. Choose One-time to give to it."
const val METHOD_SOON_MESSAGE = "This method is coming soon."
const val NO_METHOD_MESSAGE = "Giving isn't available right now. Please try again in a little while."

sealed interface GiveSubmission {
    data class Intent(val body: GiveBody) : GiveSubmission
    data class Schedule(val body: CreateScheduleBody) : GiveSubmission
    data class Blocked(val message: String) : GiveSubmission
}

/** The ordinary form: Tithe · KSh 1,000 · One-time. */
const val DEFAULT_GIVE_FUND = "tithe"
const val DEFAULT_GIVE_AMOUNT_MAJOR = 1_000

/** Where the giving form starts. */
data class GiveFormSeed(val fundId: String, val amountMajor: Int, val freq: Int)

/** A bound preset (a pledge's Pay, a need's Give) seeds its fund and amount;
 *  anything else — including the form a spent binding resets to — is the
 *  ordinary Tithe · KSh 1,000. Always One-time (Giving Cycle 1): a Monthly
 *  start turned a member who simply tapped Give into a monthly M-Pesa charge
 *  they never chose, since a Monthly choice creates a real schedule. */
fun giveFormSeed(preset: GivePreset?): GiveFormSeed = GiveFormSeed(
    fundId = preset?.fundId?.takeIf { it.isNotBlank() } ?: DEFAULT_GIVE_FUND,
    amountMajor = preset?.amountMinor?.takeIf { it > 0 }?.let { it / 100 } ?: DEFAULT_GIVE_AMOUNT_MAJOR,
    freq = FREQ_ONCE,
)

/**
 * The double-pay guard: whether a bound gift's intent outcome SPENDS the
 * binding — the pledge or need is cleared (upstream at once, so no path back
 * to the Give form re-binds it) and the form resets once the ceremony
 * closes, so a second tap cannot pay the same instalment twice.
 *
 * Succeeded, or on its way (processing / pending — a PIN still to enter, a
 * card confirming, PayPal awaiting approval), spends it. Only an explicit
 * failure keeps it, so the member can retry at once. An unknown or missing
 * status spends it too: a second payment is worse than one more tap on Pay.
 * An unbound gift has no binding to spend. The ceremony reads the same
 * status the same way (GiveCeremonyCopy.giftOutcome), so a failure its watch
 * finds later hands the binding back.
 */
fun giftSpendsBinding(target: GivePreset?, status: String?): Boolean =
    target?.isTargeted == true && giftOutcome(status) != GiftOutcome.Failed

/** Everything that shapes a gift on the wire, as the member set it — the
 *  amount, fund, method, binding (pledge / need), frequency, gift name,
 *  cover-fee choice, and the phone the push goes to. A different value is a
 *  different request, and never replays an old key. */
data class GiveRequestShape(
    val amountMajor: Int,
    val fundId: String,
    val methodId: String,
    val pledgeId: String?,
    val needId: String?,
    val freq: Int,
    val giftName: String,
    val coverFee: Boolean,
    val phone: String,
    /** The dollar amount (PayPal, Giving Cycle 2); 0 on the shilling form. */
    val usdCents: Int = 0,
)

/** The idempotency key held for one submission, and the gift it was minted for. */
data class HeldGiveKey(val key: String, val shape: GiveRequestShape)

/**
 * The idempotency key a Pay tap sends. The held key — kept only when the
 * last attempt got NO server answer ([keepGiveKeyAfter]) — is replayed when
 * this tap would send exactly that gift: the server then answers with the
 * transaction it may already have made, so a lost reply can never become a
 * second STK push. Any other tap — nothing held, or the gift changed
 * (amount, fund, method, binding, frequency, gift name, cover-fee) — gets a
 * fresh key. A gift changed and then changed BACK is that same request
 * again, and replays: that is exactly the case the key protects.
 */
fun giveKeyFor(held: HeldGiveKey?, shape: GiveRequestShape, freshKey: () -> String): HeldGiveKey =
    if (held != null && held.shape == shape) held else HeldGiveKey(freshKey(), shape)

/**
 * Whether to keep holding the key after an attempt: only when no HTTP
 * response came back — a transport failure or timeout (IOException) — so
 * the request may or may not have reached the server. Success, any 4xx or
 * 5xx (retrofit2.HttpException), an unreadable body or anything else IS an
 * answer: the key is released, so a genuine failure never locks the member
 * out of trying again.
 */
fun keepGiveKeyAfter(failure: Throwable?): Boolean = failure is IOException

/** iOS `total = amount + fee`: the charged amount in MAJOR units. */
fun chargedAmountMajor(amountMajor: Int, coverFee: Boolean): Int =
    amountMajor + if (coverFee) giveFee(amountMajor) else 0

fun frequencyWire(freq: Int): String = when (freq) {
    FREQ_WEEKLY -> "weekly"
    FREQ_MONTHLY -> "monthly"
    else -> "once"
}

/**
 * Decide intent-vs-schedule from (freq, method) and build the exact body.
 *
 * @param method the selected method as the server describes it
 *   (GiveMethodsLogic.effectiveGiveMethod); null means the form has none it
 *   can take, and one this form cannot take (SOON) is blocked for every
 *   frequency — so a card or a switched-off rail is never sent.
 * @param amountMajor the shilling form's amount (whole KSh).
 * @param usdCents the dollar form's amount (Giving Cycle 2). Which of the two
 *   is sent is the METHOD's currency's choice, never the caller's: a dollar
 *   rail (PayPal) sends currency USD and these cents, with no fee cover and
 *   no phone; a shilling rail sends KSh. A KSh number never goes to PayPal.
 * @param coverFee the fee rides inside amount_minor (the total charged) and
 *   is named in cover_fee_minor so the receipt can split it (Cycle 2).
 * @param phone the number the prompt goes to, for a rail that prompts one
 *   (E.164 from the number sheet); none, or not a Kenyan mobile, is blocked
 *   before any request.
 * @param pledgeId carried from a pledge's "Pay now" so the server attributes
 *   the gift (intent) or binds the schedule (spec §1, §5).
 * @param needId carried from a department need's "Give to this need" (spec
 *   §4). Intents only — a schedule cannot target a need, so a recurring
 *   choice with a need is blocked rather than silently dropping the need.
 * @param phoneOnFile the profile's number: a schedule pins its own number
 *   only when the chosen one differs (GiveMethodsLogic.schedulePhoneFor).
 */
fun planGiveSubmission(
    freq: Int,
    method: GiveMethodOption?,
    fundId: String,
    amountMajor: Int,
    coverFee: Boolean,
    phone: String?,
    accountName: String,
    idempotencyKey: String,
    pledgeId: String? = null,
    needId: String? = null,
    phoneOnFile: String? = null,
    usdCents: Int = 0,
): GiveSubmission {
    val dollars = method?.inDollars == true
    if ((if (dollars) usdCents else amountMajor) <= 0) return GiveSubmission.Blocked("Enter an amount to give.")
    if (method == null) return GiveSubmission.Blocked(NO_METHOD_MESSAGE)
    if (!method.selectable) return GiveSubmission.Blocked(METHOD_SOON_MESSAGE)
    // A pledge or a need is kept in shillings (the form never offers a dollar
    // rail for one; this holds even if it did).
    if (dollars && (pledgeId != null || needId != null)) return GiveSubmission.Blocked(BOUND_IN_SHILLINGS_MESSAGE)
    val currency = if (dollars) USD_CURRENCY else GIVE_FORM_CURRENCY
    val split = if (dollars) FeeSplit(giftMinor = usdCents, feeMinor = 0) else feeSplit(amountMajor, coverFee)
    val amountMinor = split.totalMinor
    // The rail's own limits, as the server sent them (it checks again).
    if (amountMinor < method.minMinor || (method.maxMinor > 0 && amountMinor > method.maxMinor)) {
        return GiveSubmission.Blocked("${method.label} gifts are from ${money(method.minMinor, currency)} to ${money(method.maxMinor, currency)}.")
    }
    val prompt = if (method.needsPhone) {
        kenyanMobileE164(phone)
            ?: return GiveSubmission.Blocked(if (phone.isNullOrBlank()) phoneNeededMessage(method.label) else PHONE_INVALID_MESSAGE)
    } else {
        null
    }
    return if (freq == FREQ_ONCE) {
        GiveSubmission.Intent(
            GiveBody(
                fund = fundId,
                amountMinor = amountMinor,
                currency = currency,
                method = method.key,
                phoneNumber = prompt,
                accountName = accountName.trim().ifBlank { null },
                idempotencyKey = idempotencyKey,
                pledgeId = pledgeId,
                needId = needId,
                coverFeeMinor = split.feeMinor.takeIf { it > 0 },
            ),
        )
    } else {
        if (needId != null) return GiveSubmission.Blocked(NEED_RECURRING_BLOCKED_MESSAGE)
        // Recurring only where the server says a schedule can run.
        if (!method.recurring) return GiveSubmission.Blocked(RECURRING_BLOCKED_MESSAGE)
        GiveSubmission.Schedule(
            CreateScheduleBody(
                fund = fundId,
                amountMinor = amountMinor,
                currency = currency,
                frequency = frequencyWire(freq),
                method = method.key,
                idempotencyKey = idempotencyKey,
                pledgeId = pledgeId,
                phoneNumber = schedulePhoneFor(prompt, phoneOnFile),
            ),
        )
    }
}

// ── A refused gift (Giving Cycle 1) ──

/** What the form does with a refusal. */
sealed interface GiveErrorAction {
    /** 409 GIFT_IN_PROGRESS: a prompt from a moment ago is still on the
     *  member's phone — follow THAT transaction, saying [message]. */
    data class FollowPrompt(val transactionId: String, val message: String) : GiveErrorAction

    /** Say [message]: the server's own member-facing words, else [fallback]'s. */
    data class Say(val message: String) : GiveErrorAction
}

/**
 * What a refused gift does next. 409 GIFT_IN_PROGRESS carries the waiting
 * prompt's `details.transaction_id`, and the ceremony watches it exactly like
 * a fresh intent. Everything else — 422 METHOD_UNAVAILABLE, METHOD_CURRENCY,
 * AMOUNT_OUT_OF_RANGE, PHONE_REQUIRED, 409 SCHEDULE_EXISTS, any other code —
 * says the server's message as-is (it is written for the member). [fallback]
 * speaks when there is no server message: a transport failure (err == null)
 * or a body that said nothing.
 */
fun giveErrorAction(err: ServerError?, fallback: String): GiveErrorAction {
    val message = err?.message ?: fallback
    if (err?.code == "GIFT_IN_PROGRESS") {
        err.detail("transaction_id")?.let { return GiveErrorAction.FollowPrompt(it, message) }
    }
    return GiveErrorAction.Say(message)
}

/** The ceremony's first reading for a prompt already waiting on the phone:
 *  that transaction as the server holds it — its own status, rail, fund and
 *  pledge — never the form's (it may be another gift). */
fun intentResultFromDetail(d: GivingDetail): GivingIntentResult = GivingIntentResult(
    transactionId = d.transactionId,
    status = d.status,
    provider = d.method,
    providerRef = d.providerRef,
    fund = FundRef(d.fund, receiptFundName(d)),
    pledge = d.pledge,
)

/** Whether a waiting prompt is for the gift this form is bound to — the same
 *  pledge or need. Only then does following it spend the binding (the
 *  double-pay guard); another gift's prompt leaves the binding alone. */
fun inflightIsForTarget(target: GivePreset?, d: GivingDetail): Boolean {
    if (target == null) return false
    target.pledgeId?.let { return d.pledge?.pledgeId == it }
    target.needId?.let { return d.need?.needId == it }
    return false
}

// ── Try again (Giving Cycle 3) ──

/** Rails a failed gift can be tried again on from here — the ones this app
 *  can carry to the end (a card needs the Stripe step it does not have). */
private val RETRY_FLOWS = setOf("mpesa", "airtel", "paypal")

/** Whether a failed gift on [provider] offers Try again. */
fun canRetryGift(provider: String?): Boolean = provider?.trim()?.lowercase() in RETRY_FLOWS

/** The number a retry prompts: mobile money only — the form's prompt number
 *  (the one the member is using now) — else none, and the server prompts the
 *  profile's. PayPal prompts no phone. */
fun retryPhoneFor(provider: String?, promptPhone: String?): String? =
    if (provider?.trim()?.lowercase() in setOf("mpesa", "airtel")) kenyanMobileE164(promptPhone) else null

/** The key a Try-again tap sends, and the failed gift and number it was minted for. */
data class HeldRetryKey(val key: String, val retryOf: String, val phone: String?)

/**
 * The idempotency key a Try-again tap sends: fresh for every retry — except
 * the replay of one that got NO server answer ([keepGiveKeyAfter]) for the
 * same failed gift and number, so a lost reply can never become a second
 * prompt. Another gift, or another number, is another request.
 */
fun retryKeyFor(held: HeldRetryKey?, retryOf: String, phone: String?, freshKey: () -> String): HeldRetryKey =
    if (held != null && held.retryOf == retryOf && held.phone == phone) held else HeldRetryKey(freshKey(), retryOf, phone)
