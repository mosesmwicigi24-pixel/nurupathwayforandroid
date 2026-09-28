// Giving Cycle 9 — a total pledge's pace (server pathway 04b6cef, docs/
// GIVING.md §12): said on the pledge ("To reach KSh 20,000 by 31 Dec: KSh
// 5,000 a month — 4 collections"), and "Collect it automatically at this
// pace" — offered only in shillings, with M-Pesa on, and nothing collecting
// the pledge already — asking for a monthly M-Pesa gift bound to the pledge
// at its pace, its first prompt now; a gift already collecting it is shown
// instead.
package org.nuruplace.member.feature.give

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
import org.nuruplace.member.data.net.FundRef
import org.nuruplace.member.data.net.GivingMethodInfo
import org.nuruplace.member.data.net.GivingMethodsRes
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.IntentPledge
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.PledgePace
import java.time.LocalDate

@OptIn(ExperimentalSerializationApi::class)
class PledgePaceTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
    private val today = LocalDate.of(2026, 9, 28)

    /** The server's S1 case: KSh 20,000 by 31 Dec, nothing paid, 28 Sep. */
    private val roof = Pledge(
        pledgeId = "p-roof", shape = "total", targetMinor = 2_000_000, currency = "KES", dueOn = "2026-12-31",
        status = "active", paysTo = FundRef("building", "Building Fund"), title = "Roof",
        pace = PledgePace(perMonthMinor = 500_000, collectionsLeft = 4, by = "2026-12-31"),
    )

    // ── the pace, in words ──

    @Test
    fun `a pace is said as what reaches the pledge, a month at a time`() {
        assertEquals("To reach KSh 20,000 by 31 Dec: KSh 5,000 a month — 4 collections", paceLine(roof, today))
        // One collection left: singular.
        assertEquals(
            "To reach KSh 20,000 by 31 Dec: KSh 20,000 a month — 1 collection",
            paceLine(roof.copy(pace = PledgePace(2_000_000, 1, "2026-12-31")), today),
        )
        // Rounded up to whole shillings by the server — said as it came.
        assertEquals(
            "To reach KSh 20,000 by 31 Dec: KSh 4,251 a month — 4 collections",
            paceLine(roof.copy(pace = PledgePace(425_100, 4, "2026-12-31")), today),
        )
        // Dollars keep their cents; another year says its year.
        val trip = roof.copy(currency = "USD", targetMinor = 10_001, dueOn = "2027-01-15", pace = PledgePace(2_501, 4, "2027-01-15"))
        assertEquals("To reach US$ 100.01 by 15 Jan 2027: US$ 25.01 a month — 4 collections", paceLine(trip, today))
        // No pace (a monthly pledge, paid, past, not active, an older server): no line.
        assertNull(paceLine(roof.copy(pace = null), today))
        assertNull(paceLine(roof.copy(pace = PledgePace(0, 4, "2026-12-31")), today))
    }

    @Test
    fun `the pledge decodes its pace, null for a monthly one, absent on an older server`() {
        val total = json.decodeFromString<Pledge>(
            """{"pledge_id":"p1","shape":"total","target_minor":2000000,"currency":"KES","due_on":"2026-12-31","status":"active",
               "pace":{"per_month_minor":500000,"collections_left":4,"by":"2026-12-31"}}""",
        )
        assertEquals(PledgePace(500_000, 4, "2026-12-31"), total.pace)
        val monthly = json.decodeFromString<Pledge>("""{"pledge_id":"p2","shape":"monthly","amount_minor":100000,"due_day":5,"pace":null}""")
        assertNull(monthly.pace)
        val older = json.decodeFromString<Pledge>("""{"pledge_id":"p3","shape":"total","target_minor":500000,"due_on":"2026-12-01"}""")
        assertNull(older.pace)
    }

    // ── "Collect it automatically at this pace" ──

    private fun methods(mpesaOn: Boolean = true) = GivingMethodsRes(
        methods = listOf(
            GivingMethodInfo(key = "mpesa", label = "M-Pesa", enabled = mpesaOn, currency = "KES", recurring = true, needsPhone = true),
            GivingMethodInfo(key = "paypal", label = "PayPal", enabled = true, currency = "USD"),
        ),
        defaultMethod = "mpesa",
    )

    private fun collector(status: String = "active", pledgeId: String = "p-roof", next: Long? = 500_000) = GivingSchedule(
        scheduleId = "s1", fund = "building", amountMinor = 500_000, currency = "KES", frequency = "monthly", method = "mpesa",
        status = status, nextRunAt = "2026-10-28T06:00:00Z", pledge = IntentPledge(pledgeId, "Roof"), nextAmountMinor = next,
    )

    @Test
    fun `the offer is made only in shillings, with M-Pesa on, and nothing collecting the pledge`() {
        assertTrue(paceOfferAvailable(roof, methods(), emptyList()))
        // Not in shillings: M-Pesa can't collect dollars.
        assertFalse(paceOfferAvailable(roof.copy(currency = "USD"), methods(), emptyList()))
        // M-Pesa switched off — or not listed at all.
        assertFalse(paceOfferAvailable(roof, methods(mpesaOn = false), emptyList()))
        assertFalse(paceOfferAvailable(roof, GivingMethodsRes(methods = emptyList()), emptyList()))
        // A recurring gift already collects it — running, or paused (never a second).
        assertFalse(paceOfferAvailable(roof, methods(), listOf(collector())))
        assertFalse(paceOfferAvailable(roof, methods(), listOf(collector(status = "paused"))))
        // One bound only through the pledge's own schedule_id (an older row).
        assertFalse(paceOfferAvailable(roof.copy(scheduleId = "s1"), methods(), listOf(collector(pledgeId = "other"))))
        // A cancelled one, or one collecting another pledge, does not.
        assertTrue(paceOfferAvailable(roof, methods(), listOf(collector(status = "cancelled"))))
        assertTrue(paceOfferAvailable(roof, methods(), listOf(collector(pledgeId = "other"))))
        // No pace, or either answer missing: no offer on a guess.
        assertFalse(paceOfferAvailable(roof.copy(pace = null), methods(), emptyList()))
        assertFalse(paceOfferAvailable(roof, null, emptyList()))
        assertFalse(paceOfferAvailable(roof, methods(), null))
    }

    @Test
    fun `the offer needs M-Pesa's recurring gifts, the pledge's pays_to, and an active total pledge`() {
        // M-Pesa takes money but not recurring gifts here: no offer (iOS allowsRecurring).
        val noRecurring = GivingMethodsRes(methods = listOf(GivingMethodInfo(key = "mpesa", label = "M-Pesa", enabled = true, currency = "KES", recurring = false)))
        assertFalse(paceOfferAvailable(roof, noRecurring, emptyList()))
        // No pays_to (an older server): nowhere to book it — no offer, never a guessed fund.
        assertFalse(paceOfferAvailable(roof.copy(paysTo = null), methods(), emptyList()))
        assertFalse(paceOfferAvailable(roof.copy(paysTo = FundRef(" ", "")), methods(), emptyList()))
        // Only an active total pledge.
        assertFalse(paceOfferAvailable(roof.copy(status = "paused"), methods(), emptyList()))
        assertFalse(paceOfferAvailable(roof.copy(shape = "monthly"), methods(), emptyList()))
        assertTrue(paceOfferAvailable(roof, methods(), emptyList()))
    }

    @Test
    fun `it asks for a monthly M-Pesa gift bound to the pledge at its pace, the first prompt now`() {
        val body = paceScheduleBody(roof, key = "k-pace")!!
        val wire = json.parseToJsonElement(json.encodeToString(body)).jsonObject
        assertEquals(
            setOf("fund", "amount_minor", "currency", "frequency", "method", "idempotency_key", "pledge_id", "first_charge"),
            wire.keys,
        )
        assertEquals("building", wire["fund"]?.jsonPrimitive?.content) // the pledge's pays_to
        assertEquals("500000", wire["amount_minor"]?.jsonPrimitive?.content) // pace.per_month_minor
        assertEquals("KES", wire["currency"]?.jsonPrimitive?.content)
        assertEquals("monthly", wire["frequency"]?.jsonPrimitive?.content)
        assertEquals("mpesa", wire["method"]?.jsonPrimitive?.content)
        assertEquals("now", wire["first_charge"]?.jsonPrimitive?.content)
        assertEquals("p-roof", wire["pledge_id"]?.jsonPrimitive?.content)
        assertEquals("k-pace", wire["idempotency_key"]?.jsonPrimitive?.content)
        // Its key comes from the one key maker — never in the server's namespaces.
        val fresh = paceScheduleBody(roof)!!.idempotencyKey
        assertEquals(36, fresh.length)
        assertFalse(isReservedGivingKey(fresh))
        // No pays_to: no request at all — a fund is never guessed (iOS scheduleBody).
        assertNull(paceScheduleBody(roof.copy(paysTo = null), "k"))
        assertNull(paceScheduleBody(roof.copy(pace = null), "k"))
    }

    @Test
    fun `a monthly pledge shows the gift that collects it — not only a total pledge with a pace`() {
        // The Kenya trip case (seen on screen 2026-09-28): monthly, collected
        // by a bound schedule, no pace — iOS says "Collected automatically",
        // Android said nothing.
        val kenya = Pledge(
            pledgeId = "p-kenya", shape = "monthly", amountMinor = 500_000, dueDay = 5, currency = "KES",
            status = "active", title = "Kenya trip",
        )
        val bound = collector(pledgeId = "p-kenya")
        assertEquals(PledgeCollection.Collected(bound), pledgeCollection(kenya, methods(), listOf(bound)))
        // Paused, it still collects once resumed — still shown.
        val paused = collector(status = "paused", pledgeId = "p-kenya")
        assertEquals(PledgeCollection.Collected(paused), pledgeCollection(kenya, methods(), listOf(paused)))
        // The collector needs only the gifts: the methods unknown, still shown.
        assertEquals(PledgeCollection.Collected(bound), pledgeCollection(kenya, null, listOf(bound)))
        // Nothing collecting a monthly pledge: nothing to offer (it has no pace).
        assertEquals(PledgeCollection.None, pledgeCollection(kenya, methods(), emptyList()))
        assertEquals(PledgeCollection.None, pledgeCollection(kenya, methods(), listOf(collector(status = "cancelled", pledgeId = "p-kenya"))))
        // A total pledge with a pace and nothing collecting it: the offer…
        assertEquals(PledgeCollection.Offer, pledgeCollection(roof, methods(), emptyList()))
        // …its collector instead once there is one; no offer without the methods.
        assertEquals(PledgeCollection.Collected(collector()), pledgeCollection(roof, methods(), listOf(collector())))
        assertEquals(PledgeCollection.None, pledgeCollection(roof, null, emptyList()))
        // The gifts not known yet (or the read failed): nothing, never a guess.
        assertEquals(PledgeCollection.None, pledgeCollection(kenya, methods(), null))
        assertEquals(PledgeCollection.None, pledgeCollection(roof, methods(), null))
    }

    @Test
    fun `a gift already collecting the pledge is shown instead, with what it asks next`() {
        assertEquals("Collected automatically — next KSh 5,000 on 28 Oct", collectedLine(collector(), today))
        assertEquals("Collected automatically — next KSh 3,000 on 28 Oct", collectedLine(collector(next = 300_000), today))
        assertEquals("Collected automatically — nothing to pay next time", collectedLine(collector(next = 0), today))
        assertEquals("Collected automatically — paused", collectedLine(collector(status = "paused", next = null), today))
        // Nothing coming (stopping with its pledge), or an older server's row.
        assertEquals("Collected automatically", collectedLine(collector(next = null), today))
        // The running one is the one shown when both exist.
        val both = listOf(collector(status = "paused").copy(scheduleId = "s-old"), collector())
        assertEquals("s1", pledgeCollector(roof, both)?.scheduleId)
    }
}
