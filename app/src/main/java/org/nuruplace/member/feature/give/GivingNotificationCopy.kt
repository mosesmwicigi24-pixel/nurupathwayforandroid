// The words on a giving notification in the notification centre — the
// server's push copy (workers/dispatch.ts PUSH_TEMPLATE_COPY), so the tray,
// the centre and iOS say the same thing (Giving Cycles 3–5, and Cycle 7's
// office change to a recurring gift). Kept pure so GivingNotificationCopyTest
// pins it against dispatch.ts.
//
// Checked BEFORE the payload's own `title`: on the Partners notices that key
// is the PLEDGE's name ("Kenya trip"), not a headline. One deliberate
// difference: amounts read the app's way — "US$ 12.50", as everywhere else
// in the app — where dispatch.ts writes "USD 12.50".
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.NotifPayload

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val YMD = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

/** "5 October" for a payload's YYYY-MM-DD date, as given — no time-zone
 *  math (dispatch.ts dayWords); the text itself when it is not one. */
internal fun dayWords(ymd: String): String {
    val m = YMD.find(ymd) ?: return ymd
    val month = m.groupValues[2].toInt()
    if (month !in 1..12) return ymd
    return "${m.groupValues[3].toInt()} ${MONTHS[month - 1]}"
}

/** dispatch.ts `str`: a non-empty string, else null. */
private fun String?.said(): String? = this?.takeIf { it.isNotEmpty() }

/** dispatch.ts `money(p)` in the app's words: amount_minor in currency. */
private fun amountOf(p: NotifPayload?): String = money(p?.amountMinor ?: 0L, p?.currency)

/** The headline for a giving notification; null for a template this has no
 *  words for (the centre's own fallback applies). */
internal fun givingNotificationTitle(template: String, p: NotifPayload?): String? {
    val weekly = p?.frequency == "weekly"
    return when (template) {
        "giving_gift_failed" -> "Your gift didn't go through"
        "giving_schedule_failed" -> {
            val kind = when (p?.frequency) { "weekly" -> "weekly"; "monthly" -> "monthly"; else -> "recurring" }
            "Your $kind gift didn't go through"
        }
        "giving_schedule_heads_up" -> "Your ${if (weekly) "weekly" else "monthly"} gift is ready"
        "giving_schedule_paused" -> "Your recurring gift is paused"
        // Giving Cycle 7 — the church office changed it at the member's request.
        "giving_schedule_office_change" -> when (p?.action) {
            "cancel" -> "Your recurring gift was cancelled"
            "resume" -> "Your recurring gift is back on"
            else -> "Your recurring gift is paused"
        }
        "giving_schedule_covered" -> "Nothing to pay this ${if (weekly) "week" else "month"}"
        "giving_schedule_stopped" -> when (p?.reason) {
            "pledge_fulfilled" -> "Your pledge is complete"
            "pledge_ended" -> "Your pledge has ended"
            else -> "Automatic prompts stopped"
        }
        "pledge_due_soon" -> {
            val whenDue = when (val days = p?.daysAway) {
                0 -> "due today"
                1 -> "due tomorrow"
                null -> "due in a few days"
                else -> "due in $days days"
            }
            "${p?.title.said() ?: "Your pledge"} — $whenDue"
        }
        "pledge_overdue" -> "A gentle nudge on ${p?.title.said() ?: "your pledge"}"
        "pledge_reminder_manual" -> "From the church office: ${p?.title.said() ?: "your pledge"}"
        "pledge_fulfilled" -> "Pledge fulfilled — thank you"
        "pledge_claim_confirmed" -> "Your payment is recorded"
        "pledge_claim_rejected" -> "We couldn't match that payment"
        else -> null
    }
}

/** What a giving notification says; null for a template this has no words for. */
internal fun givingNotificationBody(template: String, p: NotifPayload?): String? {
    val amount = amountOf(p)
    // The pledge by name, in quotes — "“Kenya trip”" — else "Your pledge".
    val pledgeQuoted = p?.title.said()?.let { "“$it”" } ?: "Your pledge"
    return when (template) {
        "giving_gift_failed" ->
            "${p?.reason.said() ?: "The payment didn't complete."} ${p?.hint.said() ?: "Open Give to try again."}"
        "giving_schedule_failed" -> listOf(
            p?.reason.said() ?: "We couldn't collect it this time.",
            if (p?.retryAt.said() != null) "We'll send the prompt once more later today."
            else p?.hint.said() ?: "Open Give to give now or check your number.",
        ).joinToString(" ")
        "giving_schedule_heads_up" -> {
            val pledge = p?.pledgeTitle.said()
            if (p?.partial == true && pledge != null) {
                "An M-Pesa prompt for $amount — the rest of what's due on “$pledge” — is coming to your phone in a few minutes. Enter your PIN to give."
            } else {
                "An M-Pesa prompt for $amount to ${p?.fundName.said() ?: "the church"} is coming to your phone in a few minutes. Enter your PIN to give."
            }
        }
        "giving_schedule_paused" ->
            "${p?.reason.said()?.let { "$it " } ?: ""}We've stopped sending prompts for now. Open Give to resume it whenever you're ready."
        "giving_schedule_office_change" -> {
            val gift = "${if (p?.frequency == "weekly") "weekly" else "monthly"} gift of $amount" +
                (p?.fundName.said()?.let { " to $it" } ?: "")
            when (p?.action) {
                "cancel" -> "The church office cancelled your $gift, as you asked. Nothing more will be prompted."
                "resume" -> "The church office resumed your $gift, as you asked."
                else -> {
                    val until = p?.resumeOn.said()?.let { " — it starts again on ${dayWords(it)}" } ?: ""
                    "The church office paused your $gift, as you asked$until."
                }
            }
        }
        "giving_schedule_covered" -> {
            val through = p?.coveredThrough.said()?.let { " through ${dayWords(it)}" } ?: ""
            "$pledgeQuoted is already paid$through, so no M-Pesa prompt is coming this time. Thank you."
        }
        "giving_schedule_stopped" -> when (p?.reason) {
            "pledge_fulfilled" -> "$pledgeQuoted is fulfilled, so its automatic M-Pesa prompts have stopped. Thank you for carrying it through."
            "pledge_ended" -> {
                val on = p?.untilOn.said()?.let { " on ${dayWords(it)}" } ?: ""
                "$pledgeQuoted ended$on, so its automatic prompts have stopped. Open Partners to make a new pledge."
            }
            else -> "$pledgeQuoted was cancelled, so its recurring gift has stopped too."
        }
        "pledge_due_soon" -> "$amount toward your pledge. Open Partners to give, or to pause it if this month is tight."
        "pledge_overdue" ->
            "$amount was due on ${p?.dueOn.said()?.let(::dayWords) ?: "the due date"}. No pressure — give when you can, or tell us if you paid another way."
        "pledge_reminder_manual" ->
            p?.message.said() ?: "A reminder that $amount toward your pledge is waiting. Thank you for standing with us."
        "pledge_fulfilled" -> {
            val stopped = if (p?.scheduleStopped == true) " Its automatic prompts have stopped." else ""
            "You completed your ${p?.title.said() ?: "pledge"}. Every shilling carried someone further.$stopped Open Partners to see it."
        }
        "pledge_claim_confirmed" -> "$amount toward ${p?.title.said() ?: "your pledge"} has been confirmed by the office. Thank you."
        "pledge_claim_rejected" -> "The office could not find $amount toward ${p?.title.said() ?: "your pledge"}. Reply in Community or give again from Partners."
        else -> null
    }
}
