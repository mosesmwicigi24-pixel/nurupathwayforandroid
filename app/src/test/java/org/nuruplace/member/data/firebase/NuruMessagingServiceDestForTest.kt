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

    // --- regression guard: the OLD camelCase keys must NOT be honoured ---

    @Test fun `stale camelCase keys do not match`() {
        assertNull(NuruMessagingService.destFor(mapOf("moduleId" to "m-42")))
        assertNull(NuruMessagingService.destFor(mapOf("announcementId" to "a-7")))
        assertNull(NuruMessagingService.destFor(mapOf("levelNumber" to "5")))
    }

    // --- template fallback still works for payloads without a specific id ---

    @Test fun `template fallbacks still resolve`() {
        assertEquals("prayer-room?tab=corporate", NuruMessagingService.destFor(mapOf("template" to "prayer_chain")))
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
