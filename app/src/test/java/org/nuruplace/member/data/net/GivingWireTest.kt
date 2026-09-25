// The Partners programme contract (docs/PARTNERS_PROGRAMME.md §5) on the wire:
// snake_case keys via the client's global SnakeCase strategy, and optionals
// OMITTED (not sent as null) so a backend `.optional()` field never sees a
// null. Pinned here because a wrong key or a stray null is invisible on screen
// and only shows up as a 400 from the server.
package org.nuruplace.member.data.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
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
    fun `receipt detail decodes the v2 display fields and tolerates an older server`() {
        val v2 = json.decodeFromString<GivingDetail>(
            """{"transaction_id":"t1","amount_minor":50000,"currency":"KES","status":"succeeded","fund":"discipleship",
               "method":"mpesa","receipt_code":"UIPJ27PBO3","created_at":"2026-09-25T17:11:00Z",
               "fund_name":"Discipleship","pledge":{"pledge_id":"p1","title":"School fees"},
               "need":{"need_id":"n1","title":"Sound desk"},"method_label":"M-Pesa",
               "member_name":"Moses Mwicigi","congregation":"Nairobi","ledger":[]}""",
        )
        assertEquals("Discipleship", v2.fundName)
        assertEquals("p1", v2.pledge?.pledgeId)
        assertEquals("School fees", v2.pledge?.title)
        assertEquals("n1", v2.need?.needId)
        assertEquals("Sound desk", v2.need?.title)
        assertEquals("M-Pesa", v2.methodLabel)
        assertEquals("Moses Mwicigi", v2.memberName)
        assertEquals("Nairobi", v2.congregation)

        // Older server: none of the v2 keys, an explicit null pledge, ledger still present.
        val old = json.decodeFromString<GivingDetail>(
            """{"transaction_id":"t1","amount_minor":1,"status":"succeeded","fund":"tithe","created_at":"x","pledge":null,
               "ledger":[{"side":"debit","account":"cash:mpesa","amount_minor":1,"currency":"KES"}]}""",
        )
        assertNull(old.fundName)
        assertNull(old.pledge)
        assertNull(old.need)
        assertNull(old.methodLabel)
        assertNull(old.memberName)
        assertNull(old.congregation)
        assertEquals(1, old.ledger.size)
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

    // ── Partners statement (contract 2026-09-25) ──

    @Test
    fun `statement decodes the partners totals, pledges and the enriched payment rows`() {
        val s = json.decodeFromString<GivingStatement>(
            """{"years":[2025,2026],"year":2026,"total_minor":2600000,"currency":"KES",
                "pledged_minor":2400000,"paid_minor":600000,"remaining_minor":1800000,
                "pledges":[
                  {"pledge_id":"p1","title":"School fees","shape":"monthly","amount_minor":200000,"currency":"KES","status":"active",
                   "due_day":5,"created_at":"2026-06-10T00:00:00Z","pledged_minor":1200000,"paid_minor":600000,"kept":3,"due_count":4},
                  {"pledge_id":"p2","title":"New roof","shape":"total","target_minor":1200000,"currency":"KES","status":"fulfilled",
                   "due_on":"2026-12-15","created_at":"2026-01-02T00:00:00Z","pledged_minor":1200000,"paid_minor":0,"kept":0,"due_count":0}
                ],
                "by_pledge":[{"pledge_id":"p1","title":"School fees","total_minor":600000}],
                "by_fund":[{"code":"tithe","name":"Tithe","total_minor":600000}],
                "payments":[{"transaction_id":"t","amount_minor":200000,"currency":"KES","at":"2026-09-05T09:00:00Z",
                             "pledge_id":"p1","pledge_title":"School fees","fund":"tithe","fund_name":"Tithe","method":"mpesa","receipt_code":"UIKJ2713B5"}]}""",
        )
        assertEquals(2_400_000, s.pledgedMinor)
        assertEquals(600_000, s.paidMinor)
        assertEquals(1_800_000, s.remainingMinor)
        val pledges = s.pledges!!
        assertEquals(2, pledges.size)
        assertEquals("School fees", pledges[0].title)
        assertEquals(5, pledges[0].dueDay)
        assertEquals(3 to 4, pledges[0].kept to pledges[0].dueCount)
        assertEquals(1_200_000, pledges[0].pledgedMinor)
        assertEquals("total", pledges[1].shape)
        assertEquals("2026-12-15", pledges[1].dueOn)
        assertEquals("fulfilled", pledges[1].status)
        val row = s.payments.single()
        assertEquals("p1", row.pledgeId)
        assertEquals("School fees", row.pledgeTitle)
        assertEquals("Tithe", row.fundName)
        assertEquals("mpesa", row.method)
        assertEquals("UIKJ2713B5", row.receiptCode)
    }

    @Test
    fun `older statement payload leaves the partners fields null so the client derives them`() {
        val s = json.decodeFromString<GivingStatement>(
            """{"years":[2026],"year":2026,"total_minor":1000,"by_pledge":[],"by_fund":[],
                "payments":[{"transaction_id":"t","amount_minor":1000,"currency":"KES","at":"2026-02-01T00:00:00Z","receipt_code":"R","fund":"tithe","pledge_id":"p1","pledge_title":"Tithe"}]}""",
        )
        assertNull(s.pledgedMinor)
        assertNull(s.paidMinor)
        assertNull(s.remainingMinor)
        assertNull(s.pledges)
        assertNull(s.payments.single().fundName)
        assertNull(s.payments.single().method)
        // An explicit null is the same as absent (coerceInputValues).
        val nulled = json.decodeFromString<GivingStatement>("""{"year":2026,"pledged_minor":null,"paid_minor":null,"remaining_minor":null,"pledges":null}""")
        assertNull(nulled.pledgedMinor)
        assertNull(nulled.pledges)
    }

    @Test
    fun `history rows decode pledge_id and pledge_title, defaulting null`() {
        val tagged = json.decodeFromString<GivingRecord>(
            """{"transaction_id":"t1","amount_minor":200000,"currency":"KES","status":"succeeded","fund":"tithe","method":"mpesa",
                "created_at":"2026-09-05T09:00:00Z","pledge_id":"p1","pledge_title":"School fees"}""",
        )
        assertEquals("p1", tagged.pledgeId)
        assertEquals("School fees", tagged.pledgeTitle)
        val plain = json.decodeFromString<GivingRecord>("""{"transaction_id":"t2","amount_minor":1,"status":"succeeded","fund":"tithe","created_at":"x"}""")
        assertNull(plain.pledgeId)
        assertNull(plain.pledgeTitle)
    }

    @Test
    fun `statement decodes by_pledge and by_fund`() {
        val s = json.decodeFromString<GivingStatement>("""{"years":[2025,2026],"year":2026,"total_minor":1000,"by_pledge":[{"pledge_id":"p","title":"Tithe","total_minor":600}],"by_fund":[{"code":"tithe","name":"Tithe","total_minor":1000}],"payments":[{"transaction_id":"t","amount_minor":1000,"currency":"KES","receipt_code":"R","settled_at":"2026-02-01T00:00:00Z"}]}""")
        assertEquals(listOf(2025, 2026), s.years)
        assertEquals(600, s.byPledge.single().totalMinor)
        assertEquals("2026-02-01T00:00:00Z", s.payments.single().occurredAt)
    }

    // ── Statement v2 (spec §3d, contract 2026-09-25) ──

    @Test
    fun `statement decodes impact, months, faithfulness, season and the per-pledge v2 fields`() {
        val s = json.decodeFromString<GivingStatement>(
            """{"years":[2026],"year":2026,"total_minor":2200000,"currency":"KES","by_pledge":[],"by_fund":[],"payments":[],
                "pledged_minor":2400000,"paid_minor":2200000,"remaining_minor":200000,
                "impact":{"paid_minor":2200000,"per_disciple_minor":2000000,"disciples_carried":1,"toward_next_minor":200000},
                "months":[{"month":1,"status":"none","due_minor":0,"paid_minor":0},
                          {"month":7,"status":"late","due_minor":200000,"paid_minor":200000},
                          {"month":9,"status":"kept","due_minor":200000,"paid_minor":200000},
                          {"month":10,"status":"upcoming","due_minor":200000,"paid_minor":0}],
                "faithfulness":{"kept_on_time":7,"late":1,"missed":1,"due_count":9},
                "season":{"from":"2026-01-05T00:00:00Z","levels_completed":4,"modules_completed":30,"plans_finished":12},
                "pledges":[
                  {"pledge_id":"p1","title":"School fees","shape":"monthly","amount_minor":200000,"pledged_minor":2400000,"paid_minor":1800000,
                   "kept":8,"due_count":9,"remaining_year_minor":600000,"church_progress_percent":null},
                  {"pledge_id":"p2","title":"Sound desk","shape":"total","target_minor":400000,"pledged_minor":400000,"paid_minor":400000,
                   "remaining_year_minor":0,"church_progress_percent":42.5},
                  {"pledge_id":"p3","title":"Chairs","shape":"total","target_minor":100000,"remaining_year_minor":100000,"church_progress_percent":60}
                ]}""",
        )
        val impact = s.impact!!
        assertEquals(2_200_000, impact.paidMinor)
        assertEquals(2_000_000, impact.perDiscipleMinor)
        assertEquals(1, impact.disciplesCarried)
        assertEquals(200_000, impact.towardNextMinor)
        val months = s.months!!
        assertEquals(listOf(1, 7, 9, 10), months.map { it.month })
        assertEquals(listOf("none", "late", "kept", "upcoming"), months.map { it.status })
        assertEquals(200_000, months[1].dueMinor)
        assertEquals(200_000, months[1].paidMinor)
        val f = s.faithfulness!!
        assertEquals(listOf(7, 1, 1, 9), listOf(f.keptOnTime, f.late, f.missed, f.dueCount))
        val season = s.season!!
        assertEquals("2026-01-05T00:00:00Z", season.from)
        assertEquals(4, season.levelsCompleted)
        assertEquals(30, season.modulesCompleted)
        assertEquals(12, season.plansFinished)
        val pledges = s.pledges!!
        assertEquals(600_000, pledges[0].remainingYearMinor)
        assertNull(pledges[0].churchProgressPercent)
        assertEquals(0, pledges[1].remainingYearMinor)
        assertEquals(42.5, pledges[1].churchProgressPercent!!, 0.0)
        // An integer percent decodes too.
        assertEquals(60.0, pledges[2].churchProgressPercent!!, 0.0)
    }

    @Test
    fun `older statement payload leaves every v2 block null so the screen hides it`() {
        val s = json.decodeFromString<GivingStatement>(
            """{"years":[2026],"year":2026,"total_minor":1000,"by_pledge":[],"by_fund":[],"payments":[],
                "pledges":[{"pledge_id":"p1","title":"Tithe","shape":"monthly","pledged_minor":1,"paid_minor":1,"kept":1,"due_count":1}]}""",
        )
        assertNull(s.impact)
        assertNull(s.months)
        assertNull(s.faithfulness)
        assertNull(s.season)
        assertNull(s.pledges!!.single().remainingYearMinor)
        assertNull(s.pledges!!.single().churchProgressPercent)
        // Explicit nulls read the same as absent (coerceInputValues), including season: null.
        val nulled = json.decodeFromString<GivingStatement>("""{"year":2026,"impact":null,"months":null,"faithfulness":null,"season":null}""")
        assertNull(nulled.impact)
        assertNull(nulled.months)
        assertNull(nulled.faithfulness)
        assertNull(nulled.season)
    }

    @Test
    fun `partial v2 blocks fall back to their defaults`() {
        val s = json.decodeFromString<GivingStatement>(
            """{"year":2026,"impact":{"paid_minor":600000},"months":[{"month":3}],"faithfulness":{},"season":{}}""",
        )
        assertEquals(StatementImpact(paidMinor = 600_000), s.impact)
        assertEquals(StatementMonthStatus(month = 3, status = "none"), s.months!!.single())
        assertEquals(StatementFaithfulness(), s.faithfulness)
        assertEquals(PartnerSeason(), s.season)
    }
}
