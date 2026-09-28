// The notification centre says what the push said (Giving Cycles 3–5): each
// giving and Partners template's words, pinned against workers/dispatch.ts
// PUSH_TEMPLATE_COPY at pathway cfbf98a — amounts in the app's own words
// ("US$ 12.50" where dispatch.ts writes "USD 12.50"), and the template's
// headline ahead of the payload's `title`, which on these notices is the
// pledge's NAME.
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.NotifPayload
import org.nuruplace.member.data.net.NotificationRow

class GivingNotificationCopyTest {
    private fun title(t: String, p: NotifPayload?) = givingNotificationTitle(t, p)
    private fun body(t: String, p: NotifPayload?) = givingNotificationBody(t, p)

    // ── the pledge's collector (Giving Cycle 5) ──

    @Test
    fun `a collector that skipped a paid cycle says nothing is owed this time`() {
        val p = NotifPayload(title = "Kenya trip", frequency = "monthly", currency = "KES", coveredThrough = "2026-10-05", pledgeId = "p1", scheduleId = "s1")
        assertEquals("Nothing to pay this month", title("giving_schedule_covered", p))
        assertEquals("“Kenya trip” is already paid through 5 October, so no M-Pesa prompt is coming this time. Thank you.", body("giving_schedule_covered", p))
        assertEquals("Nothing to pay this week", title("giving_schedule_covered", p.copy(frequency = "weekly")))
        assertEquals(
            "Your pledge is already paid, so no M-Pesa prompt is coming this time. Thank you.",
            body("giving_schedule_covered", p.copy(title = null, coveredThrough = null)),
        )
    }

    @Test
    fun `a collector that stopped says why, by reason`() {
        val p = NotifPayload(title = "School fees", pledgeId = "p1", scheduleId = "s1", untilOn = "2026-10-31")
        assertEquals("Your pledge is complete", title("giving_schedule_stopped", p.copy(reason = "pledge_fulfilled")))
        assertEquals(
            "“School fees” is fulfilled, so its automatic M-Pesa prompts have stopped. Thank you for carrying it through.",
            body("giving_schedule_stopped", p.copy(reason = "pledge_fulfilled")),
        )
        assertEquals("Your pledge has ended", title("giving_schedule_stopped", p.copy(reason = "pledge_ended")))
        assertEquals(
            "“School fees” ended on 31 October, so its automatic prompts have stopped. Open Partners to make a new pledge.",
            body("giving_schedule_stopped", p.copy(reason = "pledge_ended")),
        )
        assertEquals(
            "“School fees” ended, so its automatic prompts have stopped. Open Partners to make a new pledge.",
            body("giving_schedule_stopped", p.copy(reason = "pledge_ended", untilOn = null)),
        )
        assertEquals("Automatic prompts stopped", title("giving_schedule_stopped", p.copy(reason = "pledge_cancelled")))
        assertEquals("“School fees” was cancelled, so its recurring gift has stopped too.", body("giving_schedule_stopped", p.copy(reason = "pledge_cancelled")))
        assertEquals("Your pledge was cancelled, so its recurring gift has stopped too.", body("giving_schedule_stopped", NotifPayload()))
    }

    @Test
    fun `the heads-up says when a collector asks only the rest`() {
        val p = NotifPayload(amountMinor = 300_000, currency = "KES", frequency = "monthly", fundName = "Tithe", scheduleId = "s1", promptAt = "2026-10-05T06:00:00Z")
        assertEquals("Your monthly gift is ready", title("giving_schedule_heads_up", p))
        assertEquals(
            "An M-Pesa prompt for KSh 3,000 to Tithe is coming to your phone in a few minutes. Enter your PIN to give.",
            body("giving_schedule_heads_up", p),
        )
        val partial = p.copy(pledgeTitle = "General partnership", partial = true)
        assertEquals(
            "An M-Pesa prompt for KSh 3,000 — the rest of what's due on “General partnership” — is coming to your phone in a few minutes. Enter your PIN to give.",
            body("giving_schedule_heads_up", partial),
        )
        // A pledge's collector asking the whole amount reads as any other gift.
        assertEquals(body("giving_schedule_heads_up", p), body("giving_schedule_heads_up", partial.copy(partial = false)))
        assertEquals("Your weekly gift is ready", title("giving_schedule_heads_up", p.copy(frequency = "weekly")))
    }

    // ── the pledge's own notices ──

    @Test
    fun `a fulfilled pledge says whether its prompts stopped`() {
        val p = NotifPayload(title = "Kenya trip", pledgeId = "p1", targetMinor = 1_800_000, currency = "KES")
        assertEquals("Pledge fulfilled — thank you", title("pledge_fulfilled", p))
        assertEquals(
            "You completed your Kenya trip. Every shilling carried someone further. Open Partners to see it.",
            body("pledge_fulfilled", p),
        )
        assertEquals(
            "You completed your Kenya trip. Every shilling carried someone further. Its automatic prompts have stopped. Open Partners to see it.",
            body("pledge_fulfilled", p.copy(scheduleStopped = true)),
        )
    }

    @Test
    fun `a claim confirmed or not is said with its amount and pledge`() {
        val p = NotifPayload(title = "Kenya trip", pledgeId = "p1", amountMinor = 300_000, currency = "KES")
        assertEquals("Your payment is recorded", title("pledge_claim_confirmed", p))
        assertEquals("KSh 3,000 toward Kenya trip has been confirmed by the office. Thank you.", body("pledge_claim_confirmed", p))
        assertEquals("We couldn't match that payment", title("pledge_claim_rejected", p))
        assertEquals(
            "The office could not find KSh 3,000 toward Kenya trip. Reply in Community or give again from Partners.",
            body("pledge_claim_rejected", p),
        )
        // Dollars read the app's way, cents and all.
        assertEquals(
            "US$ 12.50 toward your pledge has been confirmed by the office. Thank you.",
            body("pledge_claim_confirmed", NotifPayload(amountMinor = 1_250, currency = "USD")),
        )
    }

    @Test
    fun `due soon, overdue and the office's reminder`() {
        val p = NotifPayload(title = "Kenya trip", pledgeId = "p1", amountMinor = 100_000, currency = "KES", dueOn = "2026-10-05")
        assertEquals("Kenya trip — due in 3 days", title("pledge_due_soon", p.copy(daysAway = 3)))
        assertEquals("Kenya trip — due tomorrow", title("pledge_due_soon", p.copy(daysAway = 1)))
        assertEquals("Kenya trip — due today", title("pledge_due_soon", p.copy(daysAway = 0)))
        assertEquals("Your pledge — due in a few days", title("pledge_due_soon", NotifPayload()))
        assertEquals("KSh 1,000 toward your pledge. Open Partners to give, or to pause it if this month is tight.", body("pledge_due_soon", p))
        assertEquals("A gentle nudge on Kenya trip", title("pledge_overdue", p))
        assertEquals(
            "KSh 1,000 was due on 5 October. No pressure — give when you can, or tell us if you paid another way.",
            body("pledge_overdue", p),
        )
        assertEquals("From the church office: Kenya trip", title("pledge_reminder_manual", p))
        assertEquals(
            "A reminder that KSh 1,000 toward your pledge is waiting. Thank you for standing with us.",
            body("pledge_reminder_manual", p),
        )
        assertEquals("Please see Mary after service.", body("pledge_reminder_manual", p.copy(message = "Please see Mary after service.")))
    }

    // ── Cycles 3–4, now said in the centre too ──

    @Test
    fun `failed and paused gifts say why and what next`() {
        val failed = NotifPayload(reason = "The prompt was cancelled.", hint = "Try again when you're ready.", frequency = "monthly")
        assertEquals("Your gift didn't go through", title("giving_gift_failed", failed))
        assertEquals("The prompt was cancelled. Try again when you're ready.", body("giving_gift_failed", failed))
        assertEquals("The payment didn't complete. Open Give to try again.", body("giving_gift_failed", NotifPayload()))
        assertEquals("Your monthly gift didn't go through", title("giving_schedule_failed", failed))
        assertEquals("Your recurring gift didn't go through", title("giving_schedule_failed", failed.copy(frequency = null)))
        assertEquals("The prompt was cancelled. Try again when you're ready.", body("giving_schedule_failed", failed))
        assertEquals(
            "The prompt was cancelled. We'll send the prompt once more later today.",
            body("giving_schedule_failed", failed.copy(retryAt = "2026-10-05T09:00:00Z")),
        )
        assertEquals("Your recurring gift is paused", title("giving_schedule_paused", failed))
        assertEquals(
            "The prompt was cancelled. We've stopped sending prompts for now. Open Give to resume it whenever you're ready.",
            body("giving_schedule_paused", failed),
        )
        assertEquals(
            "We've stopped sending prompts for now. Open Give to resume it whenever you're ready.",
            body("giving_schedule_paused", NotifPayload()),
        )
    }

    // ── the office changed a recurring gift at the member's request (Giving Cycle 7) ──

    @Test
    fun `the office's change to a recurring gift says what it did, and opens that gift`() {
        // workers/dispatch.ts giving_schedule_office_change, word for word.
        val row = json.decodeFromString<NotificationRow>(
            """{"notification_id":"n4","template":"giving_schedule_office_change","status":"sent","scheduled_for":"x",
               "payload":{"schedule_id":"s1","action":"pause","resume_on":"2026-10-12","amount_minor":100000,
                          "currency":"KES","frequency":"weekly","fund_name":"Tithe"}}""",
        )
        val paused = row.payload!!
        assertEquals("pause", paused.action)
        assertEquals("2026-10-12", paused.resumeOn)
        assertEquals("Your recurring gift is paused", title(row.template, paused))
        assertEquals(
            "The church office paused your weekly gift of KSh 1,000 to Tithe, as you asked — it starts again on 12 October.",
            body(row.template, paused),
        )
        // Paused with no day to start again.
        assertEquals(
            "The church office paused your weekly gift of KSh 1,000 to Tithe, as you asked.",
            body(row.template, paused.copy(resumeOn = null)),
        )
        // A tap opens that gift, by its schedule_id — a cancelled one shows
        // its record there, with nothing left to act on.
        assertEquals(
            scheduleRoute("s1"),
            givingDest(transactionId = null, failureCode = null, scheduleId = paused.scheduleId, promptAt = null, pledgeId = null),
        )

        val resumed = NotifPayload(scheduleId = "s1", action = "resume", amountMinor = 500_000, currency = "KES", frequency = "monthly")
        assertEquals("Your recurring gift is back on", title("giving_schedule_office_change", resumed))
        assertEquals("The church office resumed your monthly gift of KSh 5,000, as you asked.", body("giving_schedule_office_change", resumed))

        val cancelled = NotifPayload(
            scheduleId = "s1", action = "cancel", amountMinor = 100_000, currency = "KES", frequency = "weekly", fundName = "Tithe",
        )
        assertEquals("Your recurring gift was cancelled", title("giving_schedule_office_change", cancelled))
        assertEquals(
            "The church office cancelled your weekly gift of KSh 1,000 to Tithe, as you asked. Nothing more will be prompted.",
            body("giving_schedule_office_change", cancelled),
        )
    }

    // ── a department need (PARTNERS_PROGRAMME §4) — its `title` is the NEED's name ──

    @Test
    fun `a department need's notices say what happened, not only the need's name`() {
        // workers/dispatch.ts department_need_*, word for word.
        val row = json.decodeFromString<NotificationRow>(
            """{"notification_id":"n7","template":"department_need_rejected","status":"sent","scheduled_for":"x",
               "payload":{"need_id":"nd1","department_id":"d1","title":"Roof repairs","note":"We covered this from the building fund."}}""",
        )
        val need = row.payload!!
        assertEquals("We covered this from the building fund.", need.note)
        assertEquals("About the need you submitted", title(row.template, need))
        // The office's note when it gave one — else the need, by name.
        assertEquals("We covered this from the building fund.", body(row.template, need))
        assertEquals("Roof repairs was not approved this time.", body(row.template, need.copy(note = null)))
        assertEquals("The need was not approved this time.", body(row.template, need.copy(note = "", title = null)))

        assertEquals("Roof repairs — giving is open", title("department_need_open", need))
        assertEquals("A need — giving is open", title("department_need_open", NotifPayload()))
        assertEquals("Your department has a need you can help carry. Open Departments to give.", body("department_need_open", need))

        assertEquals("Your need was approved", title("department_need_approved", need))
        assertEquals("Roof repairs is open for giving.", body("department_need_approved", need))
        assertEquals("The need is open for giving.", body("department_need_approved", NotifPayload()))

        assertEquals("Need closed", title("department_need_closed", need))
        assertEquals("Roof repairs has been closed. Thank you.", body("department_need_closed", need))
        assertEquals("The need has been closed. Thank you.", body("department_need_closed", NotifPayload()))
    }

    @Test
    fun `a giving receipt's row says the receipt is ready`() {
        // Email/SMS only — its row in the centre is that notice (iOS words).
        val p = NotifPayload(transactionId = "t1", amountMinor = 100_000, currency = "KES")
        assertEquals("Giving receipt", title("giving_receipt", p))
        assertEquals("Thank you for giving — your receipt is ready.", body("giving_receipt", p))
        assertEquals("Thank you for giving — your receipt is ready.", body("giving_receipt", null))
    }

    @Test
    fun `any other template has no giving words`() {
        assertNull(title("badge_awarded", NotifPayload(title = "Faithful")))
        assertNull(body("event_cancelled", NotifPayload()))
    }

    @Test
    fun `dates are read as given, never shifted`() {
        assertEquals("5 October", dayWords("2026-10-05"))
        assertEquals("31 December", dayWords("2026-12-31T23:30:00Z"))
        assertEquals("soon", dayWords("soon"))
        assertEquals("2026-13-01", dayWords("2026-13-01"))
    }

    // ── the centre's rows decode these payloads, old and new ──

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test
    fun `notification payloads decode the Partners fields, and an older row still decodes`() {
        val row = json.decodeFromString<NotificationRow>(
            """{"notification_id":"n1","template":"giving_schedule_heads_up","status":"sent","scheduled_for":"x",
               "payload":{"schedule_id":"s1","amount_minor":300000,"currency":"KES","frequency":"monthly","fund_name":"Tithe",
                          "prompt_at":"2026-10-05T06:00:00Z","pledge_title":"General partnership","partial":true}}""",
        )
        assertEquals(300_000L, row.payload?.amountMinor)
        assertEquals("General partnership", row.payload?.pledgeTitle)
        assertEquals(true, row.payload?.partial)
        val fulfilled = json.decodeFromString<NotificationRow>(
            """{"notification_id":"n2","template":"pledge_fulfilled","status":"sent","scheduled_for":"x",
               "payload":{"pledge_id":"p1","title":"Kenya trip","target_minor":1800000,"currency":"KES","schedule_stopped":true}}""",
        )
        assertEquals("p1", fulfilled.payload?.pledgeId)
        assertEquals(1_800_000L, fulfilled.payload?.targetMinor)
        assertEquals(true, fulfilled.payload?.scheduleStopped)
        val due = json.decodeFromString<NotificationRow>(
            """{"notification_id":"n3","template":"pledge_overdue","status":"sent","scheduled_for":"x",
               "payload":{"pledge_id":"p1","title":"Kenya trip","amount_minor":100000,"currency":"KES","due_on":"2026-10-05",
                          "sequence":2,"of":3,"days_away":0,"message":null,"until_on":null,"covered_through":null}}""",
        )
        assertEquals("2026-10-05", due.payload?.dueOn)
        assertEquals(0, due.payload?.daysAway)
        assertNull(due.payload?.message)
        // An older row — none of the new keys — decodes with them all null.
        val old = json.decodeFromString<NotificationRow>(
            """{"notification_id":"n4","template":"level_completed","status":"sent","scheduled_for":"x","payload":{"level_number":2}}""",
        )
        assertEquals(2, old.payload?.levelNumber)
        assertNull(old.payload?.pledgeId)
        assertNull(old.payload?.amountMinor)
        assertNull(old.payload?.partial)
        assertNull(old.payload?.scheduleStopped)
    }
}
