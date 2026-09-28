// Giving Cycle 1 — a failed gift says WHY, in the server's own words
// (financial/giftFailure.ts), wherever it is shown: the ceremony after the
// watch, the history row, the receipt, a failing schedule. `failure` rides
// history rows, the transaction detail and schedules (as last_failure); it is
// null unless the gift failed, and absent from an older server.
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.Envelope
import org.nuruplace.member.data.net.GiftFailure
import org.nuruplace.member.data.net.GivingDetail
import org.nuruplace.member.data.net.GivingIntentResult
import org.nuruplace.member.data.net.GivingRecord
import org.nuruplace.member.data.net.GivingSchedule

@OptIn(ExperimentalSerializationApi::class)
class GiftFailureTest {
    // Same configuration as ApiClient's private json.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private val cancelledJson =
        """{"code":"cancelled","reason":"The M-Pesa prompt was cancelled.","hint":"Nothing was taken. Give again whenever you're ready.","retryable":false}"""
    private val cancelled = GiftFailure(
        code = "cancelled",
        reason = "The M-Pesa prompt was cancelled.",
        hint = "Nothing was taken. Give again whenever you're ready.",
        retryable = false,
    )

    @Test
    fun `history rows decode failure when present, null, or absent`() {
        val failed = json.decodeFromString<GivingRecord>(
            """{"transaction_id":"t1","amount_minor":100000,"status":"failed","fund":"tithe","method":"mpesa","created_at":"x","failure":$cancelledJson}""",
        )
        assertEquals(cancelled, failed.failure)
        assertNull(json.decodeFromString<GivingRecord>("""{"transaction_id":"t2","status":"succeeded","failure":null}""").failure)
        assertNull(json.decodeFromString<GivingRecord>("""{"transaction_id":"t3","status":"succeeded"}""").failure)
        // The whole history envelope, as GET /giving/history sends it.
        val env = json.decodeFromString<Envelope<GivingRecord>>(
            """{"data":[{"transaction_id":"t1","status":"failed","failure":$cancelledJson},{"transaction_id":"t2","status":"succeeded","failure":null}]}""",
        )
        assertEquals(listOf(cancelled, null), env.data.map { it.failure })
    }

    @Test
    fun `the transaction detail decodes failure when present, null, or absent`() {
        val d = json.decodeFromString<GivingDetail>(
            """{"transaction_id":"t1","amount_minor":1,"status":"failed","fund":"tithe","created_at":"x","ledger":[],
               "failure":{"code":"unreachable","reason":"The M-Pesa prompt couldn't reach the phone.","hint":"Check the phone is on and has signal, then try again.","retryable":true}}""",
        )
        assertEquals("unreachable", d.failure?.code)
        assertTrue(d.failure!!.retryable)
        assertNull(json.decodeFromString<GivingDetail>("""{"transaction_id":"t1","status":"succeeded","failure":null}""").failure)
        assertNull(json.decodeFromString<GivingDetail>("""{"transaction_id":"t1","status":"succeeded"}""").failure)
        // A partial failure object still decodes (every field defaults).
        assertEquals(GiftFailure(code = "system"), json.decodeFromString<GivingDetail>("""{"transaction_id":"t","failure":{"code":"system"}}""").failure)
    }

    @Test
    fun `schedules decode phone_number, retry_at and last_failure, all null from an older server`() {
        val s = json.decodeFromString<GivingSchedule>(
            """{"schedule_id":"s1","fund":"tithe","amount_minor":100000,"currency":"KES","frequency":"monthly","method":"mpesa",
               "status":"paused","next_run_at":"2026-10-28T09:30:00.000Z","created_at":"x","consecutive_failures":3,
               "phone_number":"+254722000111","retry_at":"2026-09-28T12:00:00.000Z","last_failure":$cancelledJson}""",
        )
        assertEquals("paused", s.status)
        assertEquals("+254722000111", s.phoneNumber)
        assertEquals("2026-09-28T12:00:00.000Z", s.retryAt)
        assertEquals(cancelled, s.lastFailure)
        val old = json.decodeFromString<GivingSchedule>("""{"schedule_id":"s2","status":"active","next_run_at":null}""")
        assertNull(old.phoneNumber)
        assertNull(old.retryAt)
        assertNull(old.lastFailure)
        assertEquals("", old.nextRunAt) // a null next_run_at reads as unknown
        val nulled = json.decodeFromString<GivingSchedule>("""{"schedule_id":"s3","phone_number":null,"retry_at":null,"last_failure":null}""")
        assertNull(nulled.lastFailure)
    }

    @Test
    fun `a failure is shown only on a gift that failed, and only when the server named why`() {
        assertEquals(cancelled, shownFailure("failed", cancelled))
        assertEquals(cancelled, shownFailure(" CANCELLED ", cancelled))
        assertNull(shownFailure("succeeded", cancelled))
        assertNull(shownFailure("processing", cancelled))
        assertNull(shownFailure("failed", null))
        assertNull(shownFailure("failed", GiftFailure(code = "declined", reason = "  ")))
    }

    private val r = GivingIntentResult(transactionId = "t", status = "processing", provider = "mpesa")

    @Test
    fun `the failed ceremony says the server's reason, then its hint`() {
        assertEquals(
            "The M-Pesa prompt was cancelled.",
            giveCeremonyStatusLine(r, 100_000, "Tithe", GiftOutcome.Failed, watchLapsed = false, failure = cancelled),
        )
        assertEquals("Nothing was taken. Give again whenever you're ready.", giveCeremonyHint(GiftOutcome.Failed, cancelled))
        // No failure named (an older server): the line it always said, no hint.
        assertEquals(
            "The payment didn't complete — no charge was made.",
            giveCeremonyStatusLine(r, 100_000, "Tithe", GiftOutcome.Failed, watchLapsed = false, failure = null),
        )
        assertNull(giveCeremonyHint(GiftOutcome.Failed, null))
        // Anything but a failure never shows one.
        assertNull(giveCeremonyHint(GiftOutcome.Processing, cancelled))
        assertNull(giveCeremonyHint(GiftOutcome.Succeeded, cancelled))
        assertEquals(
            "Gift confirmed — receipt on its way. 🎉",
            giveCeremonyStatusLine(r, 100_000, "Tithe", GiftOutcome.Succeeded, watchLapsed = false, failure = cancelled),
        )
    }

    @Test
    fun `the receipt of a failed gift says why instead of where it went`() {
        val d = GivingDetail(transactionId = "t1", amountMinor = 100_000, status = "failed", fund = "tithe", method = "mpesa", failure = cancelled)
        assertEquals(cancelled, receiptFailure(d))
        assertEquals(ReceiptTone.NotCompleted, receiptStatusChip(d)?.tone)
        assertNull(receiptFailure(d.copy(status = "succeeded")))
        assertNull(receiptFailure(d.copy(failure = null)))
        assertFalse(receiptFailure(d)!!.retryable)
    }
}
