// Give — the ONE decision the sticky CTA makes, kept pure so it can be pinned
// by a unit test (GiveSubmitLogicTest): a One-time gift creates an INTENT
// (POST /giving/intents); a Weekly/Monthly choice creates a SCHEDULE
// (POST /giving/schedules) — never an intent (docs/PARTNERS_PROGRAMME.md §0,
// "Android money fix"; before this, every frequency silently created a
// one-off intent). Recurring is mobile-money only, exactly as iOS
// GivingView.createSchedule gates it.
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
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.CreateScheduleBody
import org.nuruplace.member.data.net.GiveBody
import java.io.IOException

/** Frequency indices as the segmented control lays them out. */
const val FREQ_ONCE = 0
const val FREQ_WEEKLY = 1
const val FREQ_MONTHLY = 2

/** Methods a server-charged schedule can run on (iOS parity). */
val RECURRING_PROVIDERS = setOf("mpesa", "airtel")

const val RECURRING_BLOCKED_MESSAGE = "Recurring gifts are available with M-Pesa or Airtel Money."
const val RECURRING_CARD_BLOCKED_MESSAGE = "Recurring card gifts need the Stripe step — coming soon. Use M-Pesa or Airtel Money."
const val NEED_RECURRING_BLOCKED_MESSAGE = "A gift to a department need is a one-time gift. Choose One-time to give to it."

sealed interface GiveSubmission {
    data class Intent(val body: GiveBody) : GiveSubmission
    data class Schedule(val body: CreateScheduleBody) : GiveSubmission
    data class Blocked(val message: String) : GiveSubmission
}

/** The ordinary form: Tithe · KSh 1,000 · Monthly. */
const val DEFAULT_GIVE_FUND = "tithe"
const val DEFAULT_GIVE_AMOUNT_MAJOR = 1_000

/** Where the giving form starts. */
data class GiveFormSeed(val fundId: String, val amountMajor: Int, val freq: Int)

/** A bound preset (a pledge's Pay, a need's Give) seeds its fund and amount
 *  on One-time; anything else — including the form a spent binding resets
 *  to — is the ordinary Tithe · KSh 1,000 · Monthly. */
fun giveFormSeed(preset: GivePreset?): GiveFormSeed = GiveFormSeed(
    fundId = preset?.fundId?.takeIf { it.isNotBlank() } ?: DEFAULT_GIVE_FUND,
    amountMajor = preset?.amountMinor?.takeIf { it > 0 }?.let { it / 100 } ?: DEFAULT_GIVE_AMOUNT_MAJOR,
    freq = if (preset?.isTargeted == true) FREQ_ONCE else FREQ_MONTHLY,
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
 * @param provider the method's wire provider (`GiveMethod.provider`); null
 *   means a SOON method, which is blocked for every frequency.
 * @param pledgeId carried from a pledge's "Pay now" so the server attributes
 *   the gift (intent) or binds the schedule (spec §1, §5).
 * @param needId carried from a department need's "Give to this need" (spec
 *   §4). Intents only — a schedule cannot target a need, so a recurring
 *   choice with a need is blocked rather than silently dropping the need.
 */
fun planGiveSubmission(
    freq: Int,
    provider: String?,
    fundId: String,
    amountMajor: Int,
    coverFee: Boolean,
    phone: String,
    accountName: String,
    idempotencyKey: String,
    pledgeId: String? = null,
    currency: String = "KES",
    needId: String? = null,
): GiveSubmission {
    if (amountMajor <= 0) return GiveSubmission.Blocked("Enter an amount to give.")
    if (provider.isNullOrBlank()) return GiveSubmission.Blocked("This method is coming soon.")
    val amountMinor = chargedAmountMajor(amountMajor, coverFee) * 100
    return if (freq == FREQ_ONCE) {
        GiveSubmission.Intent(
            GiveBody(
                fund = fundId,
                amountMinor = amountMinor,
                currency = currency,
                method = provider,
                phoneNumber = phone.ifBlank { null },
                accountName = accountName.trim().ifBlank { null },
                idempotencyKey = idempotencyKey,
                pledgeId = pledgeId,
                needId = needId,
            ),
        )
    } else {
        if (needId != null) return GiveSubmission.Blocked(NEED_RECURRING_BLOCKED_MESSAGE)
        if (provider !in RECURRING_PROVIDERS) {
            return GiveSubmission.Blocked(if (provider == "card") RECURRING_CARD_BLOCKED_MESSAGE else RECURRING_BLOCKED_MESSAGE)
        }
        GiveSubmission.Schedule(
            CreateScheduleBody(
                fund = fundId,
                amountMinor = amountMinor,
                currency = currency,
                frequency = frequencyWire(freq),
                method = provider,
                idempotencyKey = idempotencyKey,
                pledgeId = pledgeId,
            ),
        )
    }
}
