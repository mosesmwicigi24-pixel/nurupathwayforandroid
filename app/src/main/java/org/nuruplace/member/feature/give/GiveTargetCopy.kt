// Give — the words for a gift BOUND to a target (a pledge's Pay, a department
// need's Give), kept pure so GiveTargetCopyTest pins them.
//
// Owner, 2026-09-26: paying his "Partnership" pledge, the Give screen showed
// the fund chooser with Tithe selected ("Tithe · one-time") and a note that
// the church routes it — two answers to "where does this go?". In pledge-pay
// mode the chooser, its note and the One-time/Weekly/Monthly control are
// hidden and ONE card answers instead:
//
//   PAYING YOUR PLEDGE
//   General partnership                         ← the pledge's name
//   KSh 1,000 monthly · due on the 25th         ← its terms
//   ✓ Goes to the Discipleship fund             ← pays_to, else "Routed by the church"
//   Give to a fund instead                      ← unbinds (iOS "Remove")
//
// The amount card's subtitle reads "General partnership pledge · one-time"
// and the CTA "Pay KSh 1,000 toward General partnership". A need is the same
// shape: GIVING TO A NEED · its title · "Give KSh 1,000 to <title>".
package org.nuruplace.member.feature.give

import org.nuruplace.member.data.net.FundRef
import org.nuruplace.member.data.net.Pledge
import java.time.LocalDate
import java.util.Locale

/** Everything the bound-gift card, the amount subtitle and the CTA say. */
internal data class GiveTargetCopy(
    /** "PAYING YOUR PLEDGE" · "GIVING TO A NEED". */
    val kicker: String,
    /** The pledge's or need's name. */
    val title: String,
    /** "KSh 1,000 monthly · due on the 25th"; null when unknown (a need). */
    val terms: String?,
    /** "Goes to the Discipleship fund" · "Routed by the church". */
    val destination: String,
    /** Under the amount: "General partnership pledge · one-time". */
    val amountSubtitle: String,
    /** The sticky button: "Pay KSh 1,000 toward General partnership". */
    val cta: String,
)

/** The copy for a bound gift, or null when the preset is bound to nothing.
 *  `chargedMajor` is what the button will charge (the fee rides inside it
 *  when the member covers it, GiveSubmitLogic.chargedAmountMajor). */
internal fun giveTargetCopy(target: GivePreset?, chargedMajor: Int): GiveTargetCopy? {
    if (target == null) return null
    val amount = kshMajor(chargedMajor)
    val name = target.title?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        target.pledgeId != null -> GiveTargetCopy(
            kicker = "PAYING YOUR PLEDGE",
            title = name ?: "Your pledge",
            terms = target.terms?.trim()?.takeIf { it.isNotEmpty() },
            destination = paysToLine(target.paysTo),
            amountSubtitle = "${pledgeTag(name)} · one-time",
            cta = "Pay $amount toward ${name ?: "your pledge"}",
        )
        target.needId != null -> GiveTargetCopy(
            kicker = "GIVING TO A NEED",
            title = name ?: "A department need",
            terms = target.terms?.trim()?.takeIf { it.isNotEmpty() },
            destination = paysToLine(target.paysTo),
            amountSubtitle = "${name ?: "Department need"} · one-time",
            cta = "Give $amount to ${name ?: "this need"}",
        )
        else -> null
    }
}

/** "School fees pledge"; a name already ending in "pledge" is not doubled;
 *  no name → "Pledge". */
internal fun pledgeTag(name: String?): String = when {
    name.isNullOrBlank() -> "Pledge"
    name.trim().lowercase(Locale.ROOT).endsWith("pledge") -> name.trim()
    else -> "${name.trim()} pledge"
}

/** Where a bound gift lands: "Goes to the Discipleship fund" from the
 *  server's `pays_to` (its name, else the local name for its code; a name
 *  already ending in "fund" is not doubled), else "Routed by the church" —
 *  the server decides a pledge's (or need's) fund, never the client. */
internal fun paysToLine(paysTo: FundRef?): String {
    val name = paysTo?.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: paysTo?.code?.trim()?.takeIf { it.isNotEmpty() }?.let { giveFund(it).name }
        ?: return "Routed by the church"
    return if (name.lowercase(Locale.ROOT).endsWith("fund")) "Goes to the $name" else "Goes to the $name fund"
}

/** A pledge's own terms for the PAYING YOUR PLEDGE card: "KSh 1,000 monthly
 *  · due on the 25th" · "KSh 50,000 by 15 Dec" (the year added when it is
 *  not this one). Null when the pledge carries no amount to state. */
internal fun pledgeTermsLine(pl: Pledge, today: LocalDate): String? = when (pl.shape) {
    "total" -> pl.targetMinor?.takeIf { it > 0 }?.let { target ->
        val by = partnerDate(pl.dueOn)?.let { d ->
            if (d.year == today.year) PartnerFormat.dayMonth(d) else PartnerFormat.dayMonthYear(d.toString())
        }
        money(target, pl.currency) + (by?.let { " by $it" } ?: " in total")
    }
    else -> pl.amountMinor?.takeIf { it > 0 }?.let { amount ->
        "${money(amount, pl.currency)} monthly" + (pl.dueDay?.let { " · due on the ${ordinal(it)}" } ?: "")
    }
}
