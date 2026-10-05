// Giving Cycle 5 on the pledge itself: what POST /giving/pledges may answer
// (`reused`, `auto_schedule_error`) and where the app then lands; claims as
// both claim endpoints send them (the list's amount_minor is TEXT — a
// Postgres bigint cast — the create's a number); and a pledge edit that
// sends only what changed, a total pledge's TARGET as target_minor.
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.Envelope
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.PledgeClaim
import org.nuruplace.member.data.net.UpdatePledgeBody
import java.time.LocalDate

@OptIn(ExperimentalSerializationApi::class)
class PledgeCycle5Test {
    // Same configuration as ApiClient's private json.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    // ── POST /giving/pledges ──

    @Test
    fun `a created pledge decodes reused and auto_schedule_error, absent on an older server`() {
        val failed = json.decodeFromString<Pledge>(
            """{"pledge_id":"p1","shape":"monthly","amount_minor":100000,"currency":"KES","due_day":5,"status":"active",
               "starts_on":null,"until_on":null,"auto_schedule_error":"Add your M-Pesa number to your profile first."}""",
        )
        assertEquals("Add your M-Pesa number to your profile first.", failed.autoScheduleError)
        assertFalse(failed.reused)
        assertNull(failed.startsOn)
        val replay = json.decodeFromString<Pledge>(
            """{"pledge_id":"p1","shape":"monthly","amount_minor":100000,"due_day":5,"starts_on":"2026-10-05","reused":true}""",
        )
        assertTrue(replay.reused)
        assertEquals("2026-10-05", replay.startsOn)
        val older = json.decodeFromString<Pledge>("""{"pledge_id":"p1","shape":"total","target_minor":500000,"due_on":"2026-12-01"}""")
        assertFalse(older.reused)
        assertNull(older.autoScheduleError)
        assertNull(older.untilOn)
    }

    @Test
    fun `a pledge made without its collection lands on itself with the server's reason`() {
        val made = Pledge(pledgeId = "p1", autoScheduleError = "M-Pesa can't take that amount in one prompt.")
        assertEquals(PledgeLanding("p1", "M-Pesa can't take that amount in one prompt."), pledgeLandingAfterCreate(made))
        // A plain success, or the same pledge replayed, returns to Partners as before.
        assertNull(pledgeLandingAfterCreate(Pledge(pledgeId = "p1")))
        assertNull(pledgeLandingAfterCreate(Pledge(pledgeId = "p1", reused = true)))
        assertNull(pledgeLandingAfterCreate(Pledge(pledgeId = "p1", autoScheduleError = "  ")))
        // Nothing to land on without an id.
        assertNull(pledgeLandingAfterCreate(Pledge(pledgeId = "", autoScheduleError = "x")))
    }

    // ── claims ──

    @Test
    fun `the claim list decodes amount_minor sent as text`() {
        val list = json.decodeFromString<Envelope<PledgeClaim>>(
            """{"data":[
                 {"claim_id":"c2","pledge_id":"p1","amount_minor":"300000","currency":"KES","paid_on":"2026-09-12",
                  "note":"Paid at the office","status":"pending","decided_at":null,"transaction_id":null,"created_at":"2026-09-28 09:00:00+03"},
                 {"claim_id":"c1","pledge_id":"p1","amount_minor":"2550","currency":"USD","paid_on":"2026-08-01",
                  "note":null,"status":"confirmed","decided_at":"2026-08-03 10:00:00+03","transaction_id":"t9","created_at":"x"}
               ]}""",
        )
        assertEquals(listOf(300_000L, 2_550L), list.data.map { it.amountMinor })
        assertEquals("Paid at the office", list.data[0].note)
        assertEquals("t9", list.data[1].transactionId)
        assertEquals(listOf("The office is checking it", "Recorded — thank you"), list.data.map { claimStatusLine(it.status) })
    }

    @Test
    fun `the created claim decodes with a number, the rest absent`() {
        val made = json.decodeFromString<PledgeClaim>(
            """{"claim_id":"c3","pledge_id":"p1","status":"pending","amount_minor":300000,"currency":"KES","paid_on":"2026-09-28","note":null,"created_at":"x"}""",
        )
        assertEquals(300_000L, made.amountMinor)
        assertEquals("pending", made.status)
        assertNull(made.decidedAt)
        assertNull(made.transactionId)
        assertEquals("KSh 3,000 · paid Mon 28 Sep", claimRowLine(made, LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `claim list states — checking, recorded, unmatched`() {
        val rows = listOf("pending", "confirmed", "rejected").map { PledgeClaim(claimId = it, status = it) }
        assertEquals(listOf(ClaimTone.Waiting, ClaimTone.Recorded, ClaimTone.Unmatched), rows.map { claimTone(it.status) })
        assertEquals(
            listOf("The office is checking it", "Recorded — thank you", "The office couldn't match it"),
            rows.map { claimStatusLine(it.status) },
        )
    }

    // ── editing a pledge ──

    private val monthly = Pledge(pledgeId = "m", shape = "monthly", amountMinor = 100_000, dueDay = 5, currency = "KES")
    private val total = Pledge(pledgeId = "t", shape = "total", targetMinor = 1_800_000, dueOn = "2026-12-20", currency = "KES")

    @Test
    fun `an edit sends only what changed`() {
        assertNull(pledgeEditPatch(monthly, 100_000, 5, null))
        assertEquals(UpdatePledgeBody(amountMinor = 150_000), pledgeEditPatch(monthly, 150_000, 5, null))
        assertEquals(UpdatePledgeBody(dueDay = 20), pledgeEditPatch(monthly, 100_000, 20, null))
        assertEquals(UpdatePledgeBody(title = JsonPrimitive("School fees")), pledgeEditPatch(monthly, 100_000, 5, JsonPrimitive("School fees")))
        assertEquals(UpdatePledgeBody(title = JsonNull), pledgeEditPatch(monthly, 100_000, 5, JsonNull))
    }

    @Test
    fun `a total pledge's amount is its target, and it has no due day`() {
        assertEquals(UpdatePledgeBody(targetMinor = 2_000_000), pledgeEditPatch(total, 2_000_000, 1, null))
        assertNull(pledgeEditPatch(total, 1_800_000, 14, null))
        val wire = json.parseToJsonElement(json.encodeToString(pledgeEditPatch(total, 2_000_000, 1, null)!!)).jsonObject
        assertEquals(setOf("target_minor"), wire.keys)
        assertEquals("2000000", wire["target_minor"].toString())
    }

    @Test
    fun `pledge amounts are typed in the pledge's currency`() {
        assertEquals("1000", pledgeAmountInput(100_000, "KES"))
        assertEquals("25.50", pledgeAmountInput(2_550, "USD"))
        assertEquals("25", pledgeAmountInput(2_500, "usd"))
        assertEquals(150_000, pledgeAmountMinor("1500", "KES"))
        assertEquals(150_000, pledgeAmountMinor("1,500", "KES"))
        assertNull(pledgeAmountMinor("0", "KES"))
        assertNull(pledgeAmountMinor("", "KES"))
        assertEquals(2_550, pledgeAmountMinor("25.50", "USD"))
        assertNull(pledgeAmountMinor("25.555", "USD"))
        assertEquals(500_000_000, pledgeAmountMinor("5000000", "KES"))
        assertNull(pledgeAmountMinor("5000001", "KES"))
    }
}
