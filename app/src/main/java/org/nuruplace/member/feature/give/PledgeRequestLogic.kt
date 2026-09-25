// New pledge — "What is this pledge for?" (pledge names, contract 2026-09-25),
// kept pure so the wire rule is pinned by PledgeRequestLogicTest and cannot
// drift from iOS:
//
//   · a picked OPTION travels as its target — fund / campaign_id / need_id
//     copied from the option — and NO title: the server derives the name
//     (campaign → fund → need → "Partnership");
//   · General partnership travels as nothing at all — no target, no title;
//   · a CUSTOM name travels as `title` ONLY (2–60, trimmed), no target.
//
// The options themselves come from GET /giving/partnership `pledge_options`,
// server-ordered; an older server that sends none gets General + the funds
// this app already knows, so the flow never dead-ends.
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.AutoScheduleBody
import org.nuruplace.member.data.net.CreatePledgeBody
import org.nuruplace.member.data.net.PledgeOption
import java.time.LocalDate

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
