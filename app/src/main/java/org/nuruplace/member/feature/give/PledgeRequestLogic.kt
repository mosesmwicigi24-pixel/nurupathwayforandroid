// New pledge — "What is this pledge for?" (pledge names, contract 2026-09-25),
// kept pure so the wire rule is pinned by PledgeRequestLogicTest and cannot
// drift from iOS:
//
//   · a picked OPTION travels as its target — fund / campaign_id / need_id
//     copied from the option — and NO title: the server derives the name
//     (campaign → fund → need → "General partnership");
//   · General partnership travels as nothing at all — no target, no title;
//   · a CUSTOM name travels as `title` ONLY (2–60, trimmed), no target.
//
// The options themselves come from GET /giving/partnership `pledge_options`,
// server-ordered; an older server that sends none gets General + the funds
// this app already knows, so the flow never dead-ends.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.AutoScheduleBody
import org.nuruplace.member.data.net.CreatePledgeBody
import org.nuruplace.member.data.net.GivingMethodsRes
import org.nuruplace.member.data.net.PledgeOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val PLEDGE_TITLE_MIN = 2
const val PLEDGE_TITLE_MAX = 60

const val PLEDGE_KIND_GENERAL = "general"
const val PLEDGE_KIND_FUND = "fund"
const val PLEDGE_KIND_CAMPAIGN = "campaign"
const val PLEDGE_KIND_NEED = "need"

/** The programme itself — the default, and the fallback's first row. */
val GENERAL_PLEDGE_OPTION = PledgeOption(key = "general", title = "General partnership", kind = PLEDGE_KIND_GENERAL)

/** The target step's choice: one of the server's options, or the member's own name. */
sealed interface PledgeFor {
    data class Option(val option: PledgeOption) : PledgeFor
    data class Custom(val name: String) : PledgeFor
}

/** A custom name is 2–60 characters once trimmed. */
fun pledgeTitleValid(name: String): Boolean = name.trim().length in PLEDGE_TITLE_MIN..PLEDGE_TITLE_MAX

/** What the review step and the card will call it. */
fun pledgeForTitle(target: PledgeFor?): String = when (target) {
    is PledgeFor.Option -> target.option.title.ifBlank { GENERAL_PLEDGE_OPTION.title }
    is PledgeFor.Custom -> target.name.trim()
    null -> GENERAL_PLEDGE_OPTION.title
}

/** The server's options as sent, or — when it sent none — General plus the
 *  funds this app already knows (GIVE_FUNDS), so the picker is never empty. */
fun pledgeOptionsOrFallback(server: List<PledgeOption>): List<PledgeOption> {
    if (server.isNotEmpty()) return server
    return listOf(GENERAL_PLEDGE_OPTION) + GIVE_FUNDS.map { f ->
        PledgeOption(key = "fund:${f.id}", title = f.name, kind = PLEDGE_KIND_FUND, fund = f.id)
    }
}

/** A picker section: its small label and the options under it, in the server's order. */
data class PledgeOptionGroup(val label: String, val options: List<PledgeOption>)

/** Group by kind with the picker's section labels, General first; a kind
 *  with no options has no section. Unknown kinds land under "Other" rather
 *  than vanishing — a server that adds a kind should still be pickable. */
fun groupedPledgeOptions(options: List<PledgeOption>): List<PledgeOptionGroup> {
    val labels = listOf(
        PLEDGE_KIND_GENERAL to "General",
        PLEDGE_KIND_FUND to "Funds",
        PLEDGE_KIND_CAMPAIGN to "Campaigns",
        PLEDGE_KIND_NEED to "Department needs",
    )
    val known = labels.map { it.first }.toSet()
    val groups = labels.mapNotNull { (kind, label) ->
        options.filter { it.kind == kind }.takeIf { it.isNotEmpty() }?.let { PledgeOptionGroup(label, it) }
    }
    val other = options.filter { it.kind !in known }
    return if (other.isEmpty()) groups else groups + PledgeOptionGroup("Other", other)
}

/** Whether an option's card shows as picked. By key — except General, which
 *  matches by kind: the flow's local default (GENERAL_PLEDGE_OPTION) and the
 *  server's own General row need not share a key, and one of them must still
 *  light up. A custom name picks no option's card. */
fun pledgeOptionSelected(option: PledgeOption, target: PledgeFor?): Boolean {
    val picked = (target as? PledgeFor.Option)?.option ?: return false
    return option.key == picked.key || (option.kind == PLEDGE_KIND_GENERAL && picked.kind == PLEDGE_KIND_GENERAL)
}

/** Build the exact wire body (spec §5 + pledge names) from the flow's choices. */
internal fun buildPledgeBody(
    shape: String,
    amountMajor: Int,
    target: PledgeFor?,
    dueDay: Int?,
    dueOn: LocalDate?,
    autoMethod: String?,
): CreatePledgeBody {
    val option = (target as? PledgeFor.Option)?.option?.takeIf { it.kind != PLEDGE_KIND_GENERAL }
    val custom = (target as? PledgeFor.Custom)?.name?.trim()?.takeIf { pledgeTitleValid(it) }
    return CreatePledgeBody(
        shape = shape,
        amountMinor = if (shape == "monthly") amountMajor * 100 else null,
        targetMinor = if (shape == "total") amountMajor * 100 else null,
        currency = "KES",
        dueDay = if (shape == "monthly") dueDay else null,
        dueOn = if (shape == "total") dueOn?.toString() else null,
        fund = option?.fund?.takeIf { it.isNotBlank() },
        campaignId = option?.campaignId?.takeIf { it.isNotBlank() },
        needId = option?.needId?.takeIf { it.isNotBlank() },
        autoSchedule = autoMethod?.let { AutoScheduleBody(method = it, frequency = "monthly") },
        title = custom,
    )
}

/** A pledge to open on Partners, and what to say at the top of it — for a
 *  pledge just made whose automatic collection could not be set up, the
 *  server's reason (Giving Cycle 5). */
data class PledgeLanding(val pledgeId: String, val notice: String? = null)

/** After POST /giving/pledges: land on the pledge only when it came back
 *  with `auto_schedule_error` — the pledge WAS made, only its collection
 *  failed, and the member should see which pledge and why without being
 *  blocked. A plain success, or the same pledge replayed (`reused`), just
 *  returns to Partners as before. */
fun pledgeLandingAfterCreate(created: org.nuruplace.member.data.net.Pledge): PledgeLanding? {
    val why = created.autoScheduleError?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val id = created.pledgeId.trim().takeIf { it.isNotEmpty() } ?: return null
    return PledgeLanding(id, why)
}

/** The edit field's text for a pledge amount, in its currency: whole
 *  shillings ("1000"), or dollars with their cents ("25" / "25.50"). */
fun pledgeAmountInput(minor: Int, currency: String?): String =
    if (currencyCode(currency) == USD_CURRENCY) usdInput(minor) else (minor / 100).toString()

/** The typed pledge amount in minor units of its currency — whole shillings
 *  up to KSh 5,000,000, or dollars and cents up to the same figure — null
 *  when it is not one (empty, zero, a third decimal). */
fun pledgeAmountMinor(text: String, currency: String?): Int? {
    val minor = if (currencyCode(currency) == USD_CURRENCY) usdCentsOf(text)
    else text.filter { it.isDigit() }.take(8).toIntOrNull()?.let { it * 100 }
    return minor?.takeIf { it in 1..500_000_000 }
}

/** The PATCH an edit makes — only what changed travels; null when nothing
 *  did. A monthly pledge's amount is `amount_minor` (its collector follows
 *  it, and the server refuses one M-Pesa can't take); a total pledge's is
 *  `target_minor` — an amount_minor sent for a total pledge used to change
 *  nothing the member could see. The due day is a monthly pledge's only. */
fun pledgeEditPatch(
    pl: org.nuruplace.member.data.net.Pledge,
    amountMinor: Int?,
    dueDay: Int?,
    titlePatch: kotlinx.serialization.json.JsonElement?,
): org.nuruplace.member.data.net.UpdatePledgeBody? {
    val total = pl.shape == "total"
    val newAmount = amountMinor?.takeIf { it != pl.headlineMinor }
    val newDay = if (total) null else dueDay?.takeIf { it != pl.dueDay }
    if (newAmount == null && newDay == null && titlePatch == null) return null
    return org.nuruplace.member.data.net.UpdatePledgeBody(
        amountMinor = if (total) null else newAmount,
        targetMinor = if (total) newAmount else null,
        dueDay = newDay,
        title = titlePatch,
    )
}

// ── Editing a pledge (iOS EditPledgeSheet / PledgeAmountEdit) ──

/** The edit's suggested amounts, in minor units of the pledge's currency:
 *  KSh 500 · 1,000 · 2,000 · 5,000 · 10,000 · 20,000, or Give's dollar ones
 *  (US$ 5 … 100). */
fun pledgeEditPresets(currency: String?): List<Int> =
    if (currencyCode(currency) == USD_CURRENCY) USD_PRESETS.map { it * 100 }
    else listOf(500, 1_000, 2_000, 5_000, 10_000, 20_000).map { it * 100 }

/** The big figure: "1,000" in shillings, "25.50" in dollars. */
fun pledgeEditAmountText(minor: Int, currency: String?): String =
    if (currencyCode(currency) == USD_CURRENCY) "%,.2f".format(minor / 100.0) else "%,d".format(minor / 100)

/** Under the NAME field (iOS): what a name does, whether or not the member
 *  already gave the pledge one of their own. */
fun pledgeNameHelp(hasCustomTitle: Boolean): String =
    if (hasCustomTitle) "2–60 characters. Clear it to go back to the name of what it's for."
    else "Named after what it's for. Give it a name of your own if you like — 2–60 characters."

/** The `title` the edit sends (iOS titlePatch): untouched → nothing; a new
 *  name → it; cleared → back to the derived name — but only when there WAS
 *  a name of the member's own (clearing a derived one changes nothing, so
 *  nothing travels). */
fun pledgeEditTitlePatch(pl: org.nuruplace.member.data.net.Pledge, entered: String): kotlinx.serialization.json.JsonElement? {
    val custom = pl.customTitle?.takeIf { it.isNotBlank() }
    val patch = org.nuruplace.member.data.net.pledgeTitlePatch(custom ?: pl.displayTitle, entered)
    return if (patch is kotlinx.serialization.json.JsonNull && custom == null) null else patch
}

// ── The new-pledge flow's steps and words (iOS NewPledgeFlow) ──

/** The flow's steps, in order. */
internal enum class PledgeStep { Shape, Amount, Target, Due, Auto, Review }

/** The steps a pledge walks. A total pledge has no "collect it
 *  automatically?" — automatic collection is a monthly pledge's (Giving
 *  Cycle 5) — so it walks five, and Back from its review lands on its date. */
internal fun pledgeSteps(monthly: Boolean): List<PledgeStep> =
    if (monthly) PledgeStep.entries else PledgeStep.entries.filter { it != PledgeStep.Auto }

/** The step's heading. */
internal fun pledgeStepTitle(step: PledgeStep, monthly: Boolean): String = when (step) {
    PledgeStep.Shape -> "What shape is the promise?"
    PledgeStep.Amount -> if (monthly) "How much each month?" else "How much in total?"
    PledgeStep.Target -> "What is this pledge for?"
    PledgeStep.Due -> if (monthly) "Which day of the month?" else "By when?"
    PledgeStep.Auto -> "Collect it automatically?"
    PledgeStep.Review -> "Here is your pledge"
}

/** The line under the heading. */
internal fun pledgeStepSubtitle(step: PledgeStep, monthly: Boolean): String = when (step) {
    PledgeStep.Shape -> "Monthly and open-ended, or a total you will reach by a date — in any instalments."
    PledgeStep.Amount -> "Choose an amount, or enter your own. You can change it later."
    PledgeStep.Target -> "Pick what your promise supports, or name it yourself."
    PledgeStep.Due -> if (monthly) "We'll remind you a few days before, if you'd like." else "The date you would like the total reached by."
    PledgeStep.Auto ->
        "If you'd rather not remember, we can collect it for you each month — on your due day, by mobile money. Nothing is collected today."
    PledgeStep.Review -> "Read it once more. Nothing is charged by creating it."
}

/** The suggested amounts, in shillings — the edit sheet's own. */
internal val NEW_PLEDGE_PRESETS = listOf(500, 1_000, 2_000, 5_000, 10_000, 20_000)

/** Where the amount starts: KSh 2,000. */
internal const val NEW_PLEDGE_DEFAULT_AMOUNT = 2_000

/** A total pledge's date starts three months on, and must fall after today. */
internal fun newPledgeDefaultDueOn(today: LocalDate): LocalDate = today.plusMonths(3)

internal fun newPledgeDueOnAllowed(d: LocalDate, today: LocalDate): Boolean = d.isAfter(today)

/** The two rails a pledge's collection accepts (`auto_schedule.method`). */
private val PLEDGE_AUTO_METHODS = setOf("mpesa", "airtel")

/** The rails a pledge's automatic collection may run on: of the two it
 *  accepts, those the server says take a recurring gift here — M-Pesa alone
 *  until GET /giving/methods answers (and if it never does). */
internal fun pledgeAutoRails(res: GivingMethodsRes?): List<GiveMethodOption> =
    giveMethodOptions(res).filter { it.key in PLEDGE_AUTO_METHODS && it.selectable && it.recurring }

/** Under "Charge me automatically": "On the 5th of every month, by mobile money." */
internal fun pledgeAutoCaption(dueDay: Int): String = "On the ${ordinal(dueDay)} of every month, by mobile money."

/** Under the rails once it is on. */
internal fun pledgeAutoOnNote(dueDay: Int): String =
    "Never today — then on the ${ordinal(dueDay)} of each month. You can stop it at any time from your recurring gifts."

/** Left off: the member pays each instalment. */
internal const val PLEDGE_AUTO_OFF_NOTE =
    "You'll pay each instalment yourself with \"Pay now\" on the pledge, and we'll remind you before it's due if you'd like."

/** No rail can collect it here. */
internal const val PLEDGE_AUTO_UNAVAILABLE_NOTE =
    "Automatic collection isn't available right now. You'll pay each instalment yourself with \"Pay now\" on the pledge."

/** Under the custom name field. */
internal const val PLEDGE_CUSTOM_NAME_HELP =
    "2–60 characters. It goes on your pledge card and statement; the office directs the money where it is needed."

/** Said if Create is reached with a custom name out of bounds. */
internal const val PLEDGE_CUSTOM_NAME_ERROR = "A custom name is 2–60 characters."

/** On the review when the member is not yet a partner — the server joins
 *  them as it makes the pledge. */
internal const val PLEDGE_JOINS_NOTE = "Creating this also joins you to the Partners programme."

private val REVIEW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

/** The review, row by row: Shape · Each month / Total · For · Due day / By ·
 *  Collected, and — collected automatically — First collection. `autoRail`
 *  is the rail's name when it will be collected automatically, else null. */
internal fun pledgeReviewRows(
    monthly: Boolean,
    amountMajor: Int,
    forName: String,
    dueDay: Int,
    dueOn: LocalDate?,
    autoRail: String?,
    firstCollection: String,
): List<Pair<String, String>> = buildList {
    val auto = monthly && autoRail != null
    add("Shape" to if (monthly) "Monthly" else "A total, by a date")
    add((if (monthly) "Each month" else "Total") to kshMajor(amountMajor))
    add("For" to forName)
    add(if (monthly) "Due day" to "The ${ordinal(dueDay)} of each month" else "By" to (dueOn?.format(REVIEW_DATE) ?: "—"))
    add("Collected" to if (auto) "Automatically · $autoRail" else "By you, with Pay now")
    if (auto) add("First collection" to firstCollection)
}

/** What the flow says when Create does not go through. No answer at all (a
 *  transport failure): it may have been made, and the same pledge sent again
 *  is found rather than made twice (`reused`). A refusal comes before
 *  anything is written: the server's words, and nothing has changed. */
internal fun pledgeCreateError(noAnswer: Boolean, serverWords: String?): String = when {
    noAnswer -> "We couldn't hear back from the church. Try again — if your pledge was made, it won't be made twice."
    else -> serverWords?.trim()?.takeIf { it.isNotEmpty() } ?: "Couldn't create the pledge. Nothing has changed."
}
