// Giving Cycle 5 on a recurring gift: one that collects a pledge says which,
// and what its next prompt asks — "Next: KSh 3,000 — the rest of what's
// due", "Nothing to pay next time — your pledge is already paid", or nothing
// when no prompt is coming — and collecting a MONTHLY pledge, its amount and
// day change on the pledge. A weekly gift is celebrated week after week. The
// schedule rows decode `pledge` and `next_amount_minor`, both absent on an
// older server.
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.Envelope
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.IntentPledge
import org.nuruplace.member.data.net.Pledge

@OptIn(ExperimentalSerializationApi::class)
class ScheduleCycle5Test {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private val collector = GivingSchedule(
        scheduleId = "s1", fund = "partnership", amountMinor = 500_000, currency = "KES", frequency = "monthly",
        method = "mpesa", status = "active", nextRunAt = "2026-10-05T06:00:00Z",
        pledge = IntentPledge("p1", "Kenya trip"), nextAmountMinor = 500_000,
    )

    // ── what the rows, the rhythm row and the sheet say ──

    @Test
    fun `a gift that collects a pledge names it`() {
        assertEquals("Collects your pledge “Kenya trip”", schedulePledgeLine(collector))
        assertNull(schedulePledgeLine(collector.copy(pledge = null)))
        assertNull(schedulePledgeLine(collector.copy(pledge = IntentPledge("p1", "  "))))
    }

    @Test
    fun `the next prompt's amount is said only when it is not the whole`() {
        // The rest of what's due.
        assertEquals("Next: KSh 3,000 — the rest of what's due", scheduleNextAmountLine(collector.copy(nextAmountMinor = 300_000)))
        // Already paid: nothing next time.
        assertEquals("Nothing to pay next time — your pledge is already paid", scheduleNextAmountLine(collector.copy(nextAmountMinor = 0)))
        // The whole amount, or no prompt coming (paused, stopping), or an older server: no line.
        assertNull(scheduleNextAmountLine(collector))
        assertNull(scheduleNextAmountLine(collector.copy(nextAmountMinor = null)))
        // In its own currency.
        assertEquals(
            "Next: US$ 12.50 — the rest of what's due",
            scheduleNextAmountLine(collector.copy(currency = "USD", amountMinor = 2_500, nextAmountMinor = 1_250)),
        )
    }

    @Test
    fun `a monthly pledge's collector changes its amount and day on the pledge`() {
        val monthly = Pledge(pledgeId = "p1", shape = "monthly", amountMinor = 500_000, dueDay = 5, title = "Kenya trip")
        assertEquals(monthly, monthlyPledgeCollected(collector, listOf(monthly)))
        // A total pledge's collector changes here; so does a gift that collects none.
        assertNull(monthlyPledgeCollected(collector, listOf(monthly.copy(shape = "total"))))
        assertNull(monthlyPledgeCollected(collector.copy(pledge = null), listOf(monthly)))
        // Its pledge unknown here: the server answers a change (422, details.pledge_id).
        assertNull(monthlyPledgeCollected(collector, emptyList()))
        assertNull(monthlyPledgeCollected(collector, listOf(monthly.copy(pledgeId = "other"))))
    }

    @Test
    fun `a monthly pledge's collector sends only its number`() {
        // The dialog passes no amount and no day for it: only the number travels.
        val patch = scheduleEditPatch(collector, amountMajor = null, day = null, number = PromptNumberChoice.Own("+254711222333"))
        assertEquals(null, patch?.amountMinor)
        assertEquals(null, patch?.day)
        assertEquals("\"+254711222333\"", patch?.phoneNumber.toString())
    }

    @Test
    fun `a weekly gift is celebrated week after week`() {
        assertEquals("Faithfulness, week after week, carries the gospel further.", scheduleCelebrationLine(FREQ_WEEKLY))
        assertEquals("Faithfulness, month after month, carries the gospel further.", scheduleCelebrationLine(FREQ_MONTHLY))
    }

    // ── the wire ──

    @Test
    fun `schedule rows decode pledge and next_amount_minor, and an older server leaves them null`() {
        val list = json.decodeFromString<Envelope<GivingSchedule>>(
            """{"data":[
                 {"schedule_id":"s1","fund":"partnership","amount_minor":500000,"currency":"KES","frequency":"monthly","method":"mpesa",
                  "status":"active","next_run_at":"2026-10-05T06:00:00.000Z","created_at":"x","last_failure":null,
                  "pledge":{"pledge_id":"p1","title":"Kenya trip"},"next_amount_minor":300000},
                 {"schedule_id":"s2","fund":"tithe","amount_minor":100000,"currency":"KES","frequency":"weekly","method":"mpesa",
                  "status":"paused","next_run_at":"2026-10-04T06:00:00.000Z","created_at":"x","pledge":null,"next_amount_minor":null},
                 {"schedule_id":"s3","fund":"tithe","amount_minor":100000,"frequency":"monthly","method":"mpesa","status":"active",
                  "next_run_at":"2026-10-04T06:00:00.000Z","created_at":"x"}
               ]}""",
        )
        val (paying, paused, older) = list.data
        assertEquals(IntentPledge("p1", "Kenya trip"), paying.pledge)
        assertEquals(300_000L, paying.nextAmountMinor)
        assertEquals("Next: KSh 3,000 — the rest of what's due", scheduleNextAmountLine(paying))
        assertNull(paused.pledge)
        assertNull(paused.nextAmountMinor)
        assertNull(older.pledge)
        assertNull(older.nextAmountMinor)
        assertNull(scheduleNextAmountLine(older))
    }

    // ── paying a dollar pledge says dollars ──

    @Test
    fun `a dollar pledge's button says dollars`() {
        val usd = GivePreset(pledgeId = "p1", title = "Kenya trip", amountMinor = 2_550, currency = "USD")
        assertEquals("Pay US$ 25.50 toward Kenya trip", giveTargetCopyFor(usd, usd(2_550))!!.cta)
        // The shilling path is unchanged.
        assertEquals("Pay KSh 1,000 toward Kenya trip", giveTargetCopy(usd.copy(currency = null), 1_000)!!.cta)
        assertNull(giveTargetCopyFor(null, "KSh 1"))
    }
}
