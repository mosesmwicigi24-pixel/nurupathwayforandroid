// Where a giving notification lands, kept pure so GivingRoutesTest pins it
// (Giving Cycle 3). A push's `data` is the notification's payload VERBATIM
// (workers/dispatch.ts): snake_case keys and NO template name, so the kind is
// read off the keys each giving template writes:
//
//   giving_gift_failed  { transaction_id, amount_minor, currency, fund,
//                         failure_code, reason, hint }            → that gift
//
// The in-app notification centre reads the same payload fields
// (NotificationsScreen), so a tap lands in the same place from either.
package org.nuruplace.member.feature.give

/** The Give tab on one gift's result — the reason, the hint and Try again
 *  when it failed (MainShell's give-gift route). */
fun giftRoute(transactionId: String): String = "give-gift/$transactionId"

/** A giving notification's destination from its payload fields; null when it
 *  is not one of these (the caller's other rules then apply). */
fun givingDest(transactionId: String?, failureCode: String?): String? {
    val gift = transactionId?.trim()?.takeIf { it.isNotEmpty() }
    // A gift that failed where the member could not see it (giving_gift_failed).
    if (gift != null && !failureCode.isNullOrBlank()) return giftRoute(gift)
    return null
}

/** [givingDest] over a push's data map (snake_case keys). */
fun givingPushRoute(data: Map<String, String>): String? =
    givingDest(transactionId = data["transaction_id"], failureCode = data["failure_code"])
