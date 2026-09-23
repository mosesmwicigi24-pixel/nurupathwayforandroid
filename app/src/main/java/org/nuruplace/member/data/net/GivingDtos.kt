// Giving DTOs — Give v2 contract (ported from the iOS Models/Giving.swift). Money
// is server-authoritative + ONLINE-ONLY (§5.6): the client creates a real intent
// and never fabricates a gift; cards never touch our server.
@file:OptIn(ExperimentalSerializationApi::class)

package org.nuruplace.member.data.net

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class GivingRecord(
    val transactionId: String,
    val amountMinor: Int = 0,
    val currency: String = "KES",
    val status: String = "",
    val fund: String = "",
    val method: String? = null,
    val providerRef: String? = null,
    val receiptCode: String? = null,
    // "Named giving" (custom sheet, optional): the member's own label for this
    // gift (e.g. "Tithe", "Building Fund"), as entered. Null when not used.
    val accountName: String? = null,
    val createdAt: String = "",
    val settledAt: String? = null,
)

@Serializable
data class GivingIntentResult(
    val transactionId: String = "",
    val status: String = "",
    val clientSecret: String? = null,
    val provider: String? = null,
    val providerRef: String? = null,
    val approveUrl: String? = null,
    val reused: Boolean = false,
)

@Serializable
data class GivingLedgerEntry(
    val side: String = "",       // debit | credit
    val account: String = "",    // cash:stripe | fund:tithe …
    val amountMinor: Int = 0,
    val currency: String = "KES",
)

@Serializable
data class GivingDetail(
    val transactionId: String,
    val amountMinor: Int = 0,
    val currency: String = "KES",
    val status: String = "",
    val fund: String = "",
    val method: String? = null,
    val providerRef: String? = null,
    val receiptCode: String? = null,
    // "Named giving" (custom sheet, optional): the member's own label for this
    // gift, as entered. Null when not used.
    val accountName: String? = null,
    val createdAt: String = "",
    val settledAt: String? = null,
    val scheduleId: String? = null,
    val ledger: List<GivingLedgerEntry> = emptyList(),
)

@Serializable
data class GiveBody(
    val fund: String,
    val amountMinor: Int,
    val currency: String,
    val method: String,
    val phoneNumber: String? = null,
    // "Named giving" (custom sheet, optional): rides the M-Pesa STK push
    // AccountReference (sanitized server-side) and persists on the transaction
    // for receipts/statements/portal Finance.
    val accountName: String? = null,
    val idempotencyKey: String,
    // Partners programme (docs/PARTNERS_PROGRAMME.md §1, §5): a gift started
    // from a pledge's "Pay now" carries the pledge so the server attributes the
    // transaction to it. Omitted from the wire entirely when absent.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pledgeId: String? = null,
)

/** POST /giving/schedules — a real server-charged recurring gift (money §5.6:
 *  the server makes the first charge on the next cycle boundary, never the
 *  client). The Android half of iOS GivingView.createSchedule's Body; `pledge_id`
 *  binds the schedule to a pledge (spec §5) and is omitted when absent. */
@Serializable
data class CreateScheduleBody(
    val fund: String,
    val amountMinor: Int,
    val currency: String,
    val frequency: String,   // weekly | monthly
    val method: String,      // mpesa | airtel
    val idempotencyKey: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pledgeId: String? = null,
)

/** POST /giving/paypal/capture — settles an approved PayPal order (money §5.6: online-only). */
@Serializable
data class PayPalCaptureBody(val orderId: String)

@Serializable
data class PayPalCaptureRes(val status: String = "")

// --- Partnership (Phase 1 of the Partners design) ---
//
// A partner is not a new record: it is an active or paused giving schedule,
// read a different way. The server derives this standing, so nothing here is a
// second copy of the truth.
//
// Two field names carry rules that must not erode:
//   · `kept` is cycles actually COLLECTED, never cycles scheduled.
//   · `sinceYouBegan` is what the WHOLE CHURCH did during the partnership —
//     never this member's money traced to an outcome.
@Serializable
data class Partnership(
    /** The schedule this standing derives from — what the resume button acts on. */
    val scheduleId: String? = null,
    val isPartner: Boolean = false,
    val everPartnered: Boolean = false,
    val status: String? = null,          // active | paused
    val since: String? = null,
    val kept: Int = 0,
    val givenMinor: Int = 0,
    val currency: String = "KES",
    val rhythm: PartnerRhythm? = null,
    /** Present ONLY when there is something to say. */
    val trouble: PartnerTrouble? = null,
    val sinceYouBegan: PartnerSeason? = null,
    // --- Partners programme (docs/PARTNERS_PROGRAMME.md §1, §2, §5) ---
    // A partner is now ALSO a voluntary programme membership (no money needed
    // to join); pledges are promises with server-computed progress; `due` is
    // every upcoming due across pledges and schedules, soonest first.
    val membership: PartnerMembership? = null,
    val tier: PartnerTier? = null,
    val pledges: List<Pledge> = emptyList(),
    val due: List<DueItem> = emptyList(),
) {
    /** Joined the programme (spec §1: `partner_memberships.status` active|paused|left). */
    val isMember: Boolean get() = membership?.status in setOf("active", "paused")
}

/** `partner_memberships` — one per member. */
@Serializable
data class PartnerMembership(
    val status: String = "",        // active | paused | left
    val joinedAt: String? = null,
)

/** Derived from the monthly commitment vs giving-tier economics (server-side). */
@Serializable
data class PartnerTier(
    val name: String = "",
    val monthlyMinor: Int = 0,
)

/** A pledge — a promise of one of two shapes (spec §1). Progress is computed
 *  server-side and never stored; the label is what the card shows. */
@Serializable
data class Pledge(
    val pledgeId: String = "",
    val shape: String = "monthly",          // monthly | total
    val amountMinor: Int? = null,           // monthly: every month
    val targetMinor: Int? = null,           // total: by due_on
    val currency: String = "KES",
    val dueDay: Int? = null,                // monthly: 1..28
    val dueOn: String? = null,              // total: ISO date
    val fund: PledgeFund? = null,
    val campaign: PledgeCampaign? = null,
    val needId: String? = null,
    val status: String = "active",          // active | paused | fulfilled | cancelled
    val progress: PledgeProgress = PledgeProgress(),
    val scheduleId: String? = null,
    val remindersEnabled: Boolean = true,
) {
    /** What the pledge is for, in the member's words. */
    val targetTitle: String?
        get() = campaign?.title?.takeIf { it.isNotBlank() } ?: fund?.name?.takeIf { it.isNotBlank() }

    /** The headline amount — monthly amount or total target. */
    val headlineMinor: Int get() = if (shape == "total") (targetMinor ?: 0) else (amountMinor ?: 0)
}

@Serializable
data class PledgeFund(val code: String = "", val name: String = "")

@Serializable
data class PledgeCampaign(val campaignId: String = "", val title: String = "")

@Serializable
data class PledgeProgress(
    val paidMinor: Int = 0,
    val periodPaidMinor: Int? = null,
    val label: String = "on_track",         // on_track | behind | fulfilled | paused
    val nextDue: String? = null,
)

/** One upcoming due across pledges and schedules (spec §2.3). */
@Serializable
data class DueItem(
    val kind: String = "pledge",            // pledge | schedule
    val id: String = "",
    val title: String = "",
    val amountMinor: Int = 0,
    val currency: String = "KES",
    val dueOn: String = "",
    val action: String = "pay",             // pay | resume
)

/** POST /giving/partners/join `{}` — joining needs no fund, no campaign and no
 *  money (spec §1). An empty @Serializable class encodes as `{}`. */
@Serializable
class JoinPartnersBody

/** POST /giving/pledges (spec §5). Exactly one of amount_minor (monthly) /
 *  target_minor (total), and one of due_day / due_on, is sent — the others
 *  are omitted from the wire, not sent as null. */
@Serializable
data class CreatePledgeBody(
    val shape: String,                                   // monthly | total
    @EncodeDefault(EncodeDefault.Mode.NEVER) val amountMinor: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val targetMinor: Int? = null,
    val currency: String = "KES",
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dueDay: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dueOn: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fund: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val campaignId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val needId: String? = null,
    /** "Charge me automatically" — creates a schedule bound to the pledge. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val autoSchedule: AutoScheduleBody? = null,
)

@Serializable
data class AutoScheduleBody(val method: String, val frequency: String = "monthly")

/** PATCH /giving/pledges/{id} — only the fields being changed travel. */
@Serializable
data class UpdatePledgeBody(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val status: String? = null,        // paused | active | cancelled
    @EncodeDefault(EncodeDefault.Mode.NEVER) val amountMinor: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val dueDay: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val remindersEnabled: Boolean? = null,
)

/** GET /giving/pledges/{id} → the pledge plus its payments. Tolerant of the
 *  pledge arriving flat (spread into the object) or nested under `pledge`. */
@Serializable
data class PledgeDetail(
    val pledge: Pledge? = null,
    val pledgeId: String = "",
    val shape: String = "monthly",
    val amountMinor: Int? = null,
    val targetMinor: Int? = null,
    val currency: String = "KES",
    val dueDay: Int? = null,
    val dueOn: String? = null,
    val fund: PledgeFund? = null,
    val campaign: PledgeCampaign? = null,
    val needId: String? = null,
    val status: String = "active",
    val progress: PledgeProgress = PledgeProgress(),
    val scheduleId: String? = null,
    val remindersEnabled: Boolean = true,
    val payments: List<PledgePayment> = emptyList(),
) {
    fun asPledge(): Pledge = pledge ?: Pledge(
        pledgeId, shape, amountMinor, targetMinor, currency, dueDay, dueOn, fund, campaign,
        needId, status, progress, scheduleId, remindersEnabled,
    )
}

@Serializable
data class PledgePayment(
    val transactionId: String = "",
    val amountMinor: Int = 0,
    val currency: String = "KES",
    val at: String? = null,
    val receiptCode: String? = null,
)

/** GET /giving/statements?year= (spec §5) — by year → by pledge → by fund →
 *  payments. The yearly PDF stays on giving/statement.pdf. */
@Serializable
data class GivingStatement(
    val years: List<Int> = emptyList(),
    val year: Int = 0,
    val totalMinor: Int = 0,
    val currency: String = "KES",
    val byPledge: List<StatementPledgeLine> = emptyList(),
    val byFund: List<StatementFundLine> = emptyList(),
    val payments: List<StatementPayment> = emptyList(),
)

@Serializable
data class StatementPledgeLine(val pledgeId: String = "", val title: String = "", val totalMinor: Int = 0)

@Serializable
data class StatementFundLine(val code: String = "", val name: String = "", val totalMinor: Int = 0)

/** A statement payment row. Every field defaults so a row shaped like
 *  GivingRecord (transaction_id/created_at/settled_at) or like PledgePayment
 *  (at/receipt_code) both render. */
@Serializable
data class StatementPayment(
    val transactionId: String = "",
    val amountMinor: Int = 0,
    val currency: String = "KES",
    val status: String? = null,
    val fund: String? = null,
    val method: String? = null,
    val title: String? = null,
    val pledgeId: String? = null,
    val receiptCode: String? = null,
    val at: String? = null,
    val createdAt: String? = null,
    val settledAt: String? = null,
) {
    /** When it happened, whichever spelling the row carries. */
    val occurredAt: String? get() = at ?: settledAt ?: createdAt
}

@Serializable
data class PartnerRhythm(
    val frequency: String = "monthly",
    val method: String = "",
    val amountMinor: Int = 0,
    val fund: String = "",
    /** null while paused — nothing is coming. */
    val nextRunAt: String? = null,
)

@Serializable
data class PartnerTrouble(
    val paused: Boolean = false,
    val consecutiveFailures: Int = 0,
    val lastFailedAt: String? = null,
    // No error text by design: the provider's wording is for the church's admin
    // view, not for a member who is already worried.
)

@Serializable
data class PartnerSeason(
    val from: String = "",
    val levelsCompleted: Int = 0,
    val modulesCompleted: Int = 0,
    val plansFinished: Int = 0,
)

// --- The partner invitation ---
//
// The server's answer to "may I invite this member today, and with what".
// Every rule of restraint lives there (invitation.ts) so the two apps cannot
// drift apart — and they would only ever drift towards asking more often.
// This client asks, renders what comes back, and reports what happened.
@Serializable
data class PartnerInvite(
    val show: Boolean = false,
    /** Why not, when show is false. Not rendered — carried for diagnostics. */
    val reason: String? = null,
    /** Which showing this is about to be. "Don't ask again" appears from 2. */
    val showing: Int? = null,
    val campaign: InviteCampaign? = null,
)

@Serializable
data class InviteCampaign(
    val campaignId: String = "",
    val title: String = "",
    val blurb: String = "",
    val imageUrl: String? = null,
    val goalMinor: Int = 0,
    val raisedMinor: Int = 0,
    val currency: String = "KES",
    val endsOn: String = "",
    val daysLeft: Int = 0,
    /** Present ONLY when a real person pledged it. Never rendered otherwise. */
    val match: InviteMatch? = null,
    val tiers: List<InviteTier> = emptyList(),
) {
    /** Clamped: a campaign past its goal shows full, never overflowing. */
    val progress: Float
        get() = if (goalMinor <= 0) 0f else minOf(1f, raisedMinor.toFloat() / goalMinor)
}

@Serializable
data class InviteMatch(val amountMinor: Int = 0, val pledger: String = "")

/** An amount with its meaning. The amount alone is a price list. */
@Serializable
data class InviteTier(
    val amountMinor: Int = 0,
    val currency: String = "KES",
    val disciplesPerYear: Int = 0,
    val meaning: String = "",
)

@Serializable
data class InviteOutcomeBody(val outcome: String)
