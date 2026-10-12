package org.nuruplace.member.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cold/dead-app push taps deep-link via [NuruMessagingService.destFor], which
 *  reads the FCM `data` map. The backend dispatcher (workers/dispatch.ts) copies
 *  each notification's `payload` JSONB into `data` VERBATIM — no
 *  snake_case→camelCase conversion for push — so these keys must match the exact
 *  snake_case keys the backend `.schedule({ payload })` call sites write. This
 *  test locks that contract in: a regression to camelCase here silently strips
 *  the deep-link target off every level/module/announcement push tap. */
class NuruMessagingServiceDestForTest {

    // --- specific targets: keys must be snake_case (matches backend payloads) ---

    @Test fun `module_id routes to the module`() {
        // assessment/moduleReflection.ts: payload { module_id, feedback }
        assertEquals("module/m-42", NuruMessagingService.destFor(mapOf("module_id" to "m-42")))
    }

    @Test fun `announcement_id routes to the announcement`() {
        // announcements/service.ts: payload { announcement_id, title, body }
        assertEquals(
            "announcement/a-7",
            NuruMessagingService.destFor(mapOf("announcement_id" to "a-7", "template" to "announcement")),
        )
    }

    @Test fun `level_number routes to the level`() {
        // assessment/levelAdvancement.ts (level_ushered) + workers/handlers.ts (level_completed)
        assertEquals(
            "level/5",
            NuruMessagingService.destFor(mapOf("level_number" to "5", "template" to "level_ushered")),
        )
    }

    @Test fun `invite_token routes to the join preview`() {
        // reading-social/{groups,invites}.ts: payload { invite_token, ... }
        assertEquals(
            "reading/join/tok-123",
            NuruMessagingService.destFor(mapOf("invite_token" to "tok-123", "template" to "plan_group_invite")),
        )
    }

    @Test fun `department_id routes to the department page`() {
        // departments/service.ts: serve_request_* / department_post / department_need_*
        // all carry department_id (the need pushes also need_id — the page is the target).
        assertEquals(
            "department/dep-1",
            NuruMessagingService.destFor(mapOf("department_id" to "dep-1", "template" to "serve_request_approved")),
        )
        assertEquals(
            "department/dep-1",
            NuruMessagingService.destFor(mapOf("department_id" to "dep-1", "need_id" to "n-1", "template" to "department_need_open")),
        )
        assertEquals(
            "department/dep-1",
            NuruMessagingService.destFor(mapOf("department_id" to "dep-1", "template" to "department_post", "preview" to "Hi")),
        )
    }

    @Test fun `department templates without an id land on the Departments segment`() {
        assertEquals("departments", NuruMessagingService.destFor(mapOf("template" to "serve_request_received")))
        assertEquals("departments", NuruMessagingService.destFor(mapOf("template" to "department_post")))
        assertEquals("departments", NuruMessagingService.destFor(mapOf("department_id" to "", "template" to "department_need_approved")))
    }

    // --- giving (Giving Cycle 3): no template in the data, the keys say where ---

    @Test fun `a gift that failed where the member could not see it opens that gift`() {
        // financial/service.ts giving_gift_failed: { transaction_id, amount_minor, currency, fund, failure_code, reason, hint }
        assertEquals(
            "give-gift/t-9",
            NuruMessagingService.destFor(
                mapOf(
                    "transaction_id" to "t-9", "amount_minor" to "100000", "currency" to "KES", "fund" to "tithe",
                    "failure_code" to "no_answer", "reason" to "M-Pesa never told us how this prompt ended.", "hint" to "If money left your account, the office will match it.",
                ),
            ),
        )
    }

    @Test fun `a failed or paused schedule opens that schedule, the heads-up opens Give`() {
        // giving_schedule_failed / giving_schedule_paused: { schedule_id, fund, amount_minor, currency, method, frequency, failure_code, reason, hint, retry_at }
        val failed = mapOf(
            "schedule_id" to "s-1", "fund" to "tithe", "amount_minor" to "100000", "currency" to "KES", "method" to "mpesa",
            "frequency" to "monthly", "failure_code" to "insufficient_funds", "reason" to "There wasn't enough in the M-Pesa account.",
            "hint" to "Nothing was taken. Top up, or try a smaller amount.",
        )
        assertEquals("schedules?open=s-1", NuruMessagingService.destFor(failed))
        assertEquals("schedules?open=s-1", NuruMessagingService.destFor(failed + ("retry_at" to "2026-09-28T12:00:00.000Z")))
        // giving_schedule_heads_up: { schedule_id, amount_minor, currency, frequency, fund_name, prompt_at }
        assertEquals(
            "give",
            NuruMessagingService.destFor(
                mapOf(
                    "schedule_id" to "s-1", "amount_minor" to "100000", "currency" to "KES", "frequency" to "weekly",
                    "fund_name" to "Tithe", "prompt_at" to "2026-10-05T06:00:00.000Z",
                ),
            ),
        )
    }

    // --- regression guard: the OLD camelCase keys must NOT be honoured ---

    @Test fun `stale camelCase keys do not match`() {
        assertNull(NuruMessagingService.destFor(mapOf("moduleId" to "m-42")))
        assertNull(NuruMessagingService.destFor(mapOf("announcementId" to "a-7")))
        assertNull(NuruMessagingService.destFor(mapOf("levelNumber" to "5")))
    }

    // --- template fallback still works for payloads without a specific id ---

    @Test fun `template fallbacks still resolve`() {
        assertEquals("prayer-room?tab=corporate", NuruMessagingService.destFor(mapOf("template" to "prayer_chain")))
        assertEquals("prayer-room?tab=ekklesia", NuruMessagingService.destFor(mapOf("template" to "ekklesia_request")))
        assertEquals("memory-verses", NuruMessagingService.destFor(mapOf("template" to "memory_verse")))
        assertEquals("give", NuruMessagingService.destFor(mapOf("template" to "giving_receipt")))
        assertEquals("profile", NuruMessagingService.destFor(mapOf("template" to "badge_awarded")))
        assertEquals("read-with-friend", NuruMessagingService.destFor(mapOf("template" to "plan_group_day_completed")))
    }

    @Test fun `unknown payload yields no deep link`() {
        assertNull(NuruMessagingService.destFor(emptyMap()))
        assertNull(NuruMessagingService.destFor(mapOf("template" to "something_new")))
    }

    @Test fun `blank id falls through instead of routing to an empty target`() {
        // A present-but-blank value must not produce "module/" — it falls through.
        assertNull(NuruMessagingService.destFor(mapOf("module_id" to "")))
    }
}
