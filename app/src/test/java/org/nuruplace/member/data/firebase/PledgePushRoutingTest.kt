package org.nuruplace.member.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.feature.give.givingDest
import org.nuruplace.member.feature.give.pledgeRoute

/** Giving Cycle 5's pushes, as FCM hands them over: the notification's payload
 *  VERBATIM (workers/dispatch.ts) — snake_case keys, every value a string (a
 *  boolean arrives as "true", a number as "300000") and no template name — so
 *  the keys alone say where a tap lands. Payloads copied from the server's
 *  call sites at pathway cfbf98a (financial/service.ts, partners.ts). */
class PledgePushRoutingTest {

    @Test fun `a collector that skipped a paid cycle opens its pledge`() {
        // service.ts giving_schedule_covered
        val covered = mapOf(
            "schedule_id" to "s1", "pledge_id" to "p1", "title" to "General partnership", "frequency" to "monthly",
            "currency" to "KES", "covered_through" to "2026-10-05", "next_prompt_at" to "2026-11-05T06:00:00.000Z",
        )
        assertEquals("partners-pledge/p1", NuruMessagingService.destFor(covered))
    }

    @Test fun `a collector that stopped with its pledge opens that pledge`() {
        // service.ts giving_schedule_stopped — fulfilled, ended or cancelled
        for (reason in listOf("pledge_fulfilled", "pledge_ended", "pledge_cancelled")) {
            val stopped = mapOf(
                "schedule_id" to "s1", "pledge_id" to "p1", "title" to "Kenya trip", "reason" to reason,
                "until_on" to "2026-10-31", "amount_minor" to "100000", "currency" to "KES", "frequency" to "monthly",
            )
            assertEquals(reason, "partners-pledge/p1", NuruMessagingService.destFor(stopped))
        }
    }

    @Test fun `every pledge notice opens its pledge`() {
        // partners.ts: pledge_fulfilled (schedule_stopped stringified), claims, reminders
        val fulfilled = mapOf("pledge_id" to "p1", "title" to "Kenya trip", "target_minor" to "1800000", "currency" to "KES", "schedule_stopped" to "true")
        assertEquals("partners-pledge/p1", NuruMessagingService.destFor(fulfilled))
        val confirmed = mapOf("pledge_id" to "p2", "title" to "Roof", "amount_minor" to "300000", "currency" to "KES")
        assertEquals("partners-pledge/p2", NuruMessagingService.destFor(confirmed)) // pledge_claim_confirmed / _rejected
        val dueSoon = mapOf("pledge_id" to "p3", "title" to "Tithe", "amount_minor" to "100000", "currency" to "KES", "due_on" to "2026-10-05", "days_away" to "3")
        assertEquals("partners-pledge/p3", NuruMessagingService.destFor(dueSoon))
    }

    @Test fun `the heads-up still opens Give, a pledge's collector's too`() {
        // service.ts giving_schedule_heads_up — pledge_title and partial, no pledge_id
        val heads = mapOf(
            "schedule_id" to "s1", "amount_minor" to "300000", "currency" to "KES", "frequency" to "monthly",
            "fund_name" to "Tithe", "prompt_at" to "2026-10-05T06:00:00.000Z", "pledge_title" to "General partnership", "partial" to "true",
        )
        assertEquals("give", NuruMessagingService.destFor(heads))
    }

    @Test fun `a failed or paused collector opens the schedule, never the pledge`() {
        // A failure is fixed on the schedule (number, retry, resume) — even with a pledge id beside it.
        assertEquals("schedules?open=s1", givingDest(transactionId = null, failureCode = "insufficient_funds", scheduleId = "s1", pledgeId = "p1"))
        assertEquals(
            "schedules?open=s1",
            NuruMessagingService.destFor(mapOf("schedule_id" to "s1", "failure_code" to "cancelled", "reason" to "x", "frequency" to "monthly")),
        )
        // A gift that failed stays on the gift.
        assertEquals("give-gift/t1", givingDest(transactionId = "t1", failureCode = "cancelled", pledgeId = "p1"))
    }

    @Test fun `a blank pledge id routes nowhere new`() {
        assertNull(givingDest(transactionId = null, failureCode = null, pledgeId = " "))
        assertEquals("schedules?open=s1", givingDest(transactionId = null, failureCode = null, scheduleId = "s1", pledgeId = ""))
        assertEquals("partners-pledge/p9", pledgeRoute("p9"))
    }

    // ── a tap on a push the system put in the tray (app in the background or closed) ──

    @Test fun `a tray tap reads the push's data off the launch intent`() {
        val extras = mapOf(
            "google.message_id" to "0:1727", "google.sent_time" to "1727500000000", "from" to "1234",
            "collapse_key" to "org.nuruplace.member",
            "pledge_id" to "p1", "title" to "Kenya trip", "schedule_stopped" to "true",
        )
        assertEquals("partners-pledge/p1", NuruMessagingService.trayTapDest(extras))
        // Every other push routes the same way from the tray.
        assertEquals("module/m1", NuruMessagingService.trayTapDest(mapOf("google.message_id" to "0:1", "module_id" to "m1")))
        assertEquals("give-gift/t1", NuruMessagingService.trayTapDest(mapOf("google.sent_time" to "1", "transaction_id" to "t1", "failure_code" to "cancelled")))
    }

    @Test fun `a launch that is not a push tap routes nowhere`() {
        // A launcher tap, a shortcut, a widget: no google.* keys.
        assertNull(NuruMessagingService.trayTapDest(mapOf("pledge_id" to "p1")))
        assertNull(NuruMessagingService.trayTapDest(emptyMap()))
        // A push with nothing to open.
        assertNull(NuruMessagingService.trayTapDest(mapOf("google.message_id" to "0:1", "title" to "Hello")))
    }
}
