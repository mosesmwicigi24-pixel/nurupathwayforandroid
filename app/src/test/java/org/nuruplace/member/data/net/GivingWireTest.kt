// The Partners programme contract (docs/PARTNERS_PROGRAMME.md §5) on the wire:
// snake_case keys via the client's global SnakeCase strategy, and optionals
// OMITTED (not sent as null) so a backend `.optional()` field never sees a
// null. Pinned here because a wrong key or a stray null is invisible on screen
// and only shows up as a 400 from the server.
package org.nuruplace.member.data.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class GivingWireTest {
    // Same configuration as ApiClient's private json.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test
    fun `schedule body uses the spec keys and omits pledge_id when absent`() {
        val o = json.encodeToString(CreateScheduleBody("tithe", 100_000, "KES", "monthly", "mpesa", "k")).let { json.parseToJsonElement(it).jsonObject }
        assertEquals(setOf("fund", "amount_minor", "currency", "frequency", "method", "idempotency_key"), o.keys)
        assertEquals("monthly", o["frequency"]!!.jsonPrimitive.content)
    }

    @Test
    fun `schedule and intent bodies carry pledge_id when bound`() {
        val s = json.parseToJsonElement(json.encodeToString(CreateScheduleBody("tithe", 1, "KES", "weekly", "airtel", "k", pledgeId = "pl"))).jsonObject
        assertEquals("pl", s["pledge_id"]!!.jsonPrimitive.content)
        val i = json.parseToJsonElement(json.encodeToString(GiveBody("tithe", 1, "KES", "mpesa", idempotencyKey = "k", pledgeId = "pl"))).jsonObject
        assertEquals("pl", i["pledge_id"]!!.jsonPrimitive.content)
        val plain = json.parseToJsonElement(json.encodeToString(GiveBody("tithe", 1, "KES", "mpesa", idempotencyKey = "k"))).jsonObject
        assertFalse("pledge_id" in plain)
    }

    @Test
    fun `monthly pledge body sends amount_minor and due_day only`() {
        val o = json.parseToJsonElement(json.encodeToString(CreatePledgeBody(shape = "monthly", amountMinor = 500_000, dueDay = 5, fund = "mission", autoSchedule = AutoScheduleBody("mpesa")))).jsonObject
        assertEquals(setOf("shape", "amount_minor", "currency", "due_day", "fund", "auto_schedule"), o.keys)
        assertEquals(setOf("method", "frequency"), o["auto_schedule"]!!.jsonObject.keys)
        assertEquals("monthly", o["auto_schedule"]!!.jsonObject["frequency"]!!.jsonPrimitive.content)
    }

    @Test
    fun `total pledge body sends target_minor and due_on only`() {
        val o = json.parseToJsonElement(json.encodeToString(CreatePledgeBody(shape = "total", targetMinor = 2_000_000, dueOn = "2026-12-31", campaignId = "c1"))).jsonObject
        assertEquals(setOf("shape", "target_minor", "currency", "due_on", "campaign_id"), o.keys)
    }

    @Test
    fun `patch body carries only the changed field`() {
        assertEquals(setOf("status"), json.parseToJsonElement(json.encodeToString(UpdatePledgeBody(status = "paused"))).jsonObject.keys)
        assertEquals(setOf("reminders_enabled"), json.parseToJsonElement(json.encodeToString(UpdatePledgeBody(remindersEnabled = false))).jsonObject.keys)
        assertEquals(setOf("amount_minor", "due_day"), json.parseToJsonElement(json.encodeToString(UpdatePledgeBody(amountMinor = 1, dueDay = 28))).jsonObject.keys)
    }

    @Test
    fun `join body is an empty object`() {
        assertEquals("{}", json.encodeToString(JoinPartnersBody()))
    }

    @Test
    fun `partnership decodes the spec shape and keeps the existing fields`() {
        val p = json.decodeFromString<Partnership>(
            """{"is_partner":true,"since":"2026-01-05T00:00:00Z","kept":3,"given_minor":900000,
                "membership":{"status":"active","joined_at":"2026-01-05T00:00:00Z"},
                "tier":{"name":"Builder","monthly_minor":2000000},
                "pledges":[{"pledge_id":"p1","shape":"monthly","amount_minor":300000,"currency":"KES","due_day":5,
                  "fund":{"code":"tithe","name":"Tithe"},"status":"active",
                  "progress":{"paid_minor":900000,"period_paid_minor":0,"label":"behind","next_due":"2026-10-05"},
                  "schedule_id":null,"reminders_enabled":true}],
                "due":[{"kind":"pledge","id":"p1","title":"Tithe","amount_minor":300000,"currency":"KES","due_on":"2026-10-05","action":"pay"}]}""",
        )
        assertTrue(p.isMember)
        assertEquals(3, p.kept)
        assertEquals("Builder", p.tier?.name)
        assertEquals("behind", p.pledges.single().progress.label)
        assertEquals("Tithe", p.pledges.single().targetTitle)
        assertNull(p.pledges.single().scheduleId)
        assertEquals("pay", p.due.single().action)
    }

    @Test
    fun `pledge detail tolerates flat or nested pledge`() {
        val flat = json.decodeFromString<PledgeDetail>("""{"pledge_id":"p1","shape":"total","target_minor":100,"payments":[{"transaction_id":"t","amount_minor":50,"at":"2026-09-01T00:00:00Z","receipt_code":"R1"}]}""")
        assertEquals("p1", flat.asPledge().pledgeId)
        assertEquals("R1", flat.payments.single().receiptCode)
        val nested = json.decodeFromString<PledgeDetail>("""{"pledge":{"pledge_id":"p2","shape":"monthly","amount_minor":100},"payments":[]}""")
        assertEquals("p2", nested.asPledge().pledgeId)
    }

    @Test
    fun `statement decodes by_pledge and by_fund`() {
        val s = json.decodeFromString<GivingStatement>("""{"years":[2025,2026],"year":2026,"total_minor":1000,"by_pledge":[{"pledge_id":"p","title":"Tithe","total_minor":600}],"by_fund":[{"code":"tithe","name":"Tithe","total_minor":1000}],"payments":[{"transaction_id":"t","amount_minor":1000,"currency":"KES","receipt_code":"R","settled_at":"2026-02-01T00:00:00Z"}]}""")
        assertEquals(listOf(2025, 2026), s.years)
        assertEquals(600, s.byPledge.single().totalMinor)
        assertEquals("2026-02-01T00:00:00Z", s.payments.single().occurredAt)
    }
}
