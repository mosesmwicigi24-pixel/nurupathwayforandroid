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
    val idempotencyKey: String,
)

/** POST /giving/paypal/capture — settles an approved PayPal order (money §5.6: online-only). */
@Serializable
data class PayPalCaptureBody(val orderId: String)

@Serializable
data class PayPalCaptureRes(val status: String = "")
