// A total pledge's pace (Giving Cycle 9, pathway docs/GIVING.md §12), kept
// pure so PledgePaceTest pins it. The server sends `pace` on an ACTIVE TOTAL
// pledge with money still owed and its date not passed: what is still owed
// spread over the monthly collections left — one today, then the same day
// each month through the date — rounded up to whole shillings so the last is
// never short. The pledge says it:
//
//   To reach KSh 20,000 by 31 Dec: KSh 5,000 a month — 4 collections
//
// and, in shillings with M-Pesa on and nothing collecting it yet, offers
// "Collect it automatically at this pace": a monthly M-Pesa gift bound to the
// pledge at that amount, its first prompt today (the pace counts today's
// collection). A gift bound to a pledge asks only what is left (Cycle 5), so
// it lands exactly on the target and stops there. A recurring gift already
// collecting the pledge is shown instead — never a second one.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.CreateScheduleBody
import org.nuruplace.member.data.net.GivingMethodsRes
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Pledge
import java.time.LocalDate

/** Said under "Collect it automatically at this pace". */
const val PACE_OFFER_NOTE = "Each month asks only what's left, and stops when you reach it."

/** "To reach KSh 20,000 by 31 Dec: KSh 5,000 a month — 4 collections" ("1
 *  collection"; the year added when it is not this one; dollars with their
 *  cents). Null when the pledge has no pace. */
fun paceLine(pl: Pledge, today: LocalDate): String? {
    val pace = pl.pace?.takeIf { it.perMonthMinor > 0 && it.collectionsLeft >= 1 } ?: return null
    val by = partnerDate(pace.by) ?: partnerDate(pl.dueOn)
    val byText = by?.let { d -> if (d.year == today.year) PartnerFormat.dayMonth(d) else PartnerFormat.dayMonthYear(d.toString()) }
    val n = pace.collectionsLeft
    val target = money(pl.targetMinor ?: 0, pl.currency) + (byText?.let { " by $it" } ?: "")
    return "To reach $target: ${money(pace.perMonthMinor, pl.currency)} a month — $n collection${if (n == 1) "" else "s"}"
}

/** The recurring gift already collecting this pledge — bound to it
 *  (`pledge.pledge_id`, or the pledge's own `schedule_id` on an older row),
 *  running or paused (a paused one still collects once resumed) — the
 *  running one first. Null when none does. */
fun pledgeCollector(pl: Pledge, schedules: List<GivingSchedule>): GivingSchedule? =
    schedules
        .filter { s ->
            scheduleCancellable(s.status) &&
                (s.pledge?.pledgeId == pl.pledgeId || (!pl.scheduleId.isNullOrBlank() && s.scheduleId == pl.scheduleId))
        }
        .minByOrNull { if (scheduleRunning(it.status)) 0 else 1 }

/** "Collect it automatically at this pace" is offered: the pledge has a pace,
 *  is in shillings (M-Pesa's currency), the server says M-Pesa can take money
 *  (GET /giving/methods), and no recurring gift already collects it (GET
 *  /giving/schedules). Either answer missing — not loaded, or it failed — is
 *  no offer: never a second collector for a pledge on a guess. */
fun paceOfferAvailable(pl: Pledge, methods: GivingMethodsRes?, schedules: List<GivingSchedule>?): Boolean {
    val pace = pl.pace ?: return false
    if (pace.perMonthMinor <= 0 || pace.collectionsLeft < 1) return false
    if (currencyCode(pl.currency) != GIVE_FORM_CURRENCY) return false
    if (methods == null || methods.methods.none { it.key == "mpesa" && it.enabled }) return false
    if (schedules == null) return false
    return pledgeCollector(pl, schedules) == null
}

/** POST /giving/schedules for "Collect it automatically at this pace": the
 *  pledge's pace, monthly, on M-Pesa, bound to the pledge, its first prompt
 *  now. The fund is where the pledge's money goes (`pays_to` — the server
 *  books a bound gift there whatever is sent). Every cycle prompts the
 *  profile's number. The key is [newGivingKey]'s. Null without a pace. */
fun paceScheduleBody(pl: Pledge, key: String = newGivingKey()): CreateScheduleBody? {
    val pace = pl.pace?.takeIf { it.perMonthMinor > 0 } ?: return null
    return CreateScheduleBody(
        fund = pl.paysTo?.code?.takeIf { it.isNotBlank() } ?: pl.fund?.code?.takeIf { it.isNotBlank() } ?: DEFAULT_GIVE_FUND,
        amountMinor = pace.perMonthMinor,
        currency = GIVE_FORM_CURRENCY,
        frequency = "monthly",
        method = "mpesa",
        idempotencyKey = key,
        pledgeId = pl.pledgeId,
        firstCharge = "now",
    )
}

/** The row shown instead of the offer: "Collected automatically — next KSh
 *  3,000 on 5 Oct" (what its next prompt asks, Cycle 5); "— nothing to pay
 *  next time" when the pledge is paid ahead; "— paused" while paused; just
 *  "Collected automatically" when no prompt is coming. */
fun collectedLine(s: GivingSchedule, today: LocalDate): String {
    val base = "Collected automatically"
    if (!scheduleRunning(s.status)) return if (s.status.trim().equals("paused", ignoreCase = true)) "$base — paused" else base
    val next = s.nextAmountMinor ?: return base
    if (next <= 0L) return "$base — nothing to pay next time"
    val day = parseNairobi(s.nextRunAt)?.toLocalDate()
        ?.let { d -> if (d.year == today.year) PartnerFormat.dayMonth(d) else PartnerFormat.dayMonthYear(d.toString()) }
    return "$base — next ${money(next, s.currency)}" + (day?.let { " on $it" } ?: "")
}
