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

    // ── Pledge names (contract 2026-09-25) ──

    @Test
    fun `create pledge body carries title only when given`() {
        val custom = json.parseToJsonElement(json.encodeToString(CreatePledgeBody(shape = "monthly", amountMinor = 100, dueDay = 1, title = "Mum's house"))).jsonObject
        assertEquals(setOf("shape", "amount_minor", "currency", "due_day", "title"), custom.keys)
        assertEquals("Mum's house", custom["title"]!!.jsonPrimitive.content)
        val plain = json.parseToJsonElement(json.encodeToString(CreatePledgeBody(shape = "monthly", amountMinor = 100, dueDay = 1, fund = "tithe"))).jsonObject
        assertFalse("title" in plain)
    }

    @Test
    fun `patch title sets, clears with an explicit null, or stays away`() {
        val set = json.parseToJsonElement(json.encodeToString(UpdatePledgeBody(title = pledgeTitlePatch("Tithe", "School fees")))).jsonObject
        assertEquals(setOf("title"), set.keys)
        assertEquals("School fees", set["title"]!!.jsonPrimitive.content)
        // Cleared → `"title": null` on the wire (the server falls back to its derived name).
        val cleared = json.encodeToString(UpdatePledgeBody(amountMinor = 500, title = pledgeTitlePatch("School fees", "   ")))
        assertEquals("""{"amount_minor":500,"title":null}""", cleared)
        // Untouched → no title key at all.
        val untouched = json.parseToJsonElement(json.encodeToString(UpdatePledgeBody(dueDay = 5, title = pledgeTitlePatch("Tithe", " Tithe ")))).jsonObject
        assertEquals(setOf("due_day"), untouched.keys)
    }

    @Test
    fun `partnership decodes pledge_options and a pledge's title and custom_title`() {
        val p = json.decodeFromString<Partnership>(
            """{"is_partner":true,"membership":{"status":"active"},
                "pledges":[
                  {"pledge_id":"p1","shape":"monthly","amount_minor":300000,"fund":{"code":"tithe","name":"Tithe"},"title":"School fees","custom_title":"School fees"},
                  {"pledge_id":"p2","shape":"total","target_minor":500000,"fund":{"code":"gift","name":"Gift"},"title":"Gift","custom_title":null}
                ],
                "pledge_options":[
                  {"key":"general","title":"General partnership","kind":"general"},
                  {"key":"fund:tithe","title":"Tithe","kind":"fund","fund":"tithe"},
                  {"key":"campaign:c1","title":"New roof","kind":"campaign","campaign_id":"c1"},
                  {"key":"need:n1","title":"Sound desk","kind":"need","need_id":"n1"}
                ]}""",
        )
        assertEquals(listOf("general", "fund", "campaign", "need"), p.pledgeOptions.map { it.kind })
        assertEquals("tithe", p.pledgeOptions[1].fund)
        assertEquals("c1", p.pledgeOptions[2].campaignId)
        assertEquals("n1", p.pledgeOptions[3].needId)
        val named = p.pledges[0]
        assertEquals("School fees", named.title)
        assertEquals("School fees", named.customTitle)
        assertEquals("School fees", named.displayTitle)
        val derived = p.pledges[1]
        assertEquals("Gift", derived.title)
        assertNull(derived.customTitle)
        assertEquals("Gift", derived.displayTitle)
    }

    @Test
    fun `pledge detail carries the name through asPledge`() {
        val flat = json.decodeFromString<PledgeDetail>("""{"pledge_id":"p1","shape":"monthly","amount_minor":100,"title":"Mum's house","custom_title":"Mum's house","created_at":"2026-03-01T00:00:00Z"}""")
        assertEquals("Mum's house", flat.asPledge().customTitle)
        assertEquals("Mum's house", flat.asPledge().displayTitle)
        assertEquals("2026-03-01T00:00:00Z", flat.asPledge().createdAt)
    }

    @Test
    fun `intent result decodes the server's fund and pledge`() {
        val r = json.decodeFromString<GivingIntentResult>(
            """{"transaction_id":"t1","status":"pending","provider":"mpesa","fund":{"code":"gift","name":"Gift"},"pledge":{"pledge_id":"p1","title":"School fees"}}""",
        )
        assertEquals("gift", r.fund?.code)
        assertEquals("Gift", r.fund?.name)
        assertEquals("p1", r.pledge?.pledgeId)
        assertEquals("School fees", r.pledge?.title)
    }

    @Test
    fun `older payloads without the new fields still decode to the defaults`() {
        val r = json.decodeFromString<GivingIntentResult>("""{"transaction_id":"t1","status":"pending","provider":"mpesa"}""")
        assertNull(r.fund)
        assertNull(r.pledge)
        val p = json.decodeFromString<Partnership>("""{"is_partner":true,"pledges":[{"pledge_id":"p1","shape":"monthly","amount_minor":1,"fund":{"code":"tithe","name":"Tithe"}}]}""")
        assertTrue(p.pledgeOptions.isEmpty())
        assertNull(p.pledges.single().title)
        assertNull(p.pledges.single().customTitle)
        // The derived target still names the card when the server sends no title.
        assertEquals("Tithe", p.pledges.single().displayTitle)
        assertEquals("General partnership", Pledge(pledgeId = "x").displayTitle)
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
