// Giving DTOs — Give v2 contract (ported from the iOS Models/Giving.swift). Money
// is server-authoritative + ONLINE-ONLY (§5.6): the client creates a real intent
// and never fabricates a gift; cards never touch our server.
package org.nuruplace.member.data.net

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
)

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
