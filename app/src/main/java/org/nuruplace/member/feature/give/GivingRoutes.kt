// Where a giving notification lands, kept pure so its tests pin it (Giving
// Cycles 3–5). A push's `data` is the notification's payload VERBATIM
// (workers/dispatch.ts): snake_case keys, every value a string, and NO
// template name, so the kind is read off the keys each giving template writes:
//
//   giving_gift_failed        { transaction_id, amount_minor, currency, fund,
//                               failure_code, reason, hint }       → that gift
//   giving_schedule_failed /  { schedule_id, fund, amount_minor, currency,
//   giving_schedule_paused      method, frequency, failure_code, reason,
//                               hint, retry_at }               → that schedule
//   giving_schedule_heads_up  { schedule_id, amount_minor, currency,
//                               frequency, fund_name, prompt_at,
//                               pledge_title, partial }            → Give
//   giving_schedule_covered   { schedule_id, pledge_id, title, frequency,
//                               currency, covered_through,
//                               next_prompt_at }                → that pledge
//   giving_schedule_stopped   { schedule_id, pledge_id, title, reason,
//                               until_on, amount_minor, currency,
//                               frequency }                     → that pledge
//   pledge_due_soon / _overdue / _reminder_manual / _fulfilled,
//   pledge_claim_confirmed / _rejected
//                             { pledge_id, title, … }           → that pledge
//
// The in-app notification centre reads the same payload fields
// (NotificationsScreen), and a tray tap made while the app was closed reads
// them off the launch intent (MainActivity → NuruMessagingService.trayTapDest),
// so a tap lands in the same place from anywhere.
package org.nuruplace.member.feature.give

/** The Give tab (MainShell's "give"). */
private const val GIVE_ROUTE = "give"

/** The Give tab on one gift's result — the reason, the hint and Try again
 *  when it failed (MainShell's give-gift route). */
fun giftRoute(transactionId: String): String = "give-gift/$transactionId"

/** The recurring-gifts list with one schedule open (MainShell's schedules
 *  route): why it failed or paused, and Resume / Change right there. */
fun scheduleRoute(scheduleId: String): String = "schedules?open=$scheduleId"

/** The Give tab on Partners with one pledge open (MainShell's
 *  partners-pledge route, Giving Cycle 5): its payments, what the member told
 *  the office, and its actions. */
fun pledgeRoute(pledgeId: String): String = "partners-pledge/$pledgeId"

/** A giving notification's destination from its payload fields; null when it
 *  is not one of these (the caller's other rules then apply). */
fun givingDest(
    transactionId: String?,
    failureCode: String?,
    scheduleId: String? = null,
    promptAt: String? = null,
    pledgeId: String? = null,
): String? {
    val pledge = pledgeId?.trim()?.takeIf { it.isNotEmpty() }
    scheduleId?.trim()?.takeIf { it.isNotEmpty() }?.let { schedule ->
        // The heads-up: a prompt is coming, nothing to fix — Give.
        if (!promptAt.isNullOrBlank() && failureCode.isNullOrBlank()) return GIVE_ROUTE
        // A prompt that failed, or a schedule that paused: that schedule.
        if (!failureCode.isNullOrBlank()) return scheduleRoute(schedule)
        // A pledge's collector that skipped a cycle already paid, or stopped
        // with its pledge (Giving Cycle 5): the pledge it collects.
        if (pledge != null) return pledgeRoute(pledge)
        return scheduleRoute(schedule)
    }
    val gift = transactionId?.trim()?.takeIf { it.isNotEmpty() }
    // A gift that failed where the member could not see it (giving_gift_failed).
    if (gift != null && !failureCode.isNullOrBlank()) return giftRoute(gift)
    // The pledge's own notices — due soon, overdue, the office's reminder,
    // fulfilled, a payment told another way confirmed or not: that pledge.
    if (pledge != null) return pledgeRoute(pledge)
    return null
}

/** [givingDest] over a push's data map (snake_case keys). */
fun givingPushRoute(data: Map<String, String>): String? =
    givingDest(
        transactionId = data["transaction_id"],
        failureCode = data["failure_code"],
        scheduleId = data["schedule_id"],
        promptAt = data["prompt_at"],
        pledgeId = data["pledge_id"],
    )
