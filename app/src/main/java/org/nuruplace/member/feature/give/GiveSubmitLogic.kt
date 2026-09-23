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
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.CreateScheduleBody
import org.nuruplace.member.data.net.GiveBody

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
