// A department's notification words in the notification centre, pinned
// against the server's push copy (workers/dispatch.ts PUSH_TEMPLATE_COPY) for
// rows decoded exactly as GET /me/notifications sends them — snake_case, with
// the payloads departments/service.ts schedules — so the centre says what the
// push said.
package org.nuruplace.member.feature.departments

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.NotificationRow
import org.nuruplace.member.data.net.NotificationsRes
import org.nuruplace.member.feature.events.bodyOf
import org.nuruplace.member.feature.events.titleOf

@OptIn(ExperimentalSerializationApi::class)
class DepartmentNotificationCopyTest {
    // Same configuration as ApiClient's private json.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private fun row(template: String, payload: String): NotificationRow =
        json.decodeFromString<NotificationsRes>(
            """{"data":[{"notification_id":"n1","template":"$template","payload":$payload,"status":"sent",
               "scheduled_for":"2026-09-28T08:00:00.000Z","sent_at":"2026-09-28T08:00:01.000Z","read_at":null}],
               "unread":1}""",
        ).data.single()

    private val declinedBody =
        "The leader couldn't take you on right now. Other departments would love your hands — open Departments."

    // --- the payloads departments/service.ts sends ---

    @Test
    fun `serve_request_received tells the leader who asked and where`() {
        val n = row("serve_request_received", """{"department_id":"d1","department":"Worship","name":"Grace Wanjiru"}""")
        assertEquals("the tap still opens the department page", "d1", n.payload?.departmentId)
        assertEquals("Grace Wanjiru wants to serve in Worship", titleOf(n))
        assertEquals("Open the portal to welcome them in.", bodyOf(n))
    }

    @Test
    fun `serve_request_approved welcomes the member in`() {
        val n = row("serve_request_approved", """{"department_id":"d1","department":"Worship"}""")
        assertEquals("Welcome to Worship", titleOf(n))
        assertEquals("Your request to serve was approved. Open Departments to see what's next.", bodyOf(n))
    }

    @Test
    fun `serve_request_declined points to the other departments`() {
        val n = row("serve_request_declined", """{"department_id":"d1","department":"Worship"}""")
        assertEquals("About Worship", titleOf(n))
        assertEquals(declinedBody, bodyOf(n))
    }

    @Test
    fun `department_post is headed by the department and carries its preview`() {
        val n = row("department_post", """{"department_id":"d1","department":"Worship","preview":"Rehearsal moved to 5pm this week"}""")
        assertEquals("Worship", titleOf(n))
        assertEquals("Rehearsal moved to 5pm this week", bodyOf(n))
    }

    // --- dispatch.ts's fallbacks: a field missing, empty, or no payload at all ---

    @Test
    fun `missing fields fall back to the server's words`() {
        assertEquals("Someone wants to serve in your department", titleOf(row("serve_request_received", """{"department_id":"d1"}""")))
        assertEquals("Welcome to the department", titleOf(row("serve_request_approved", """{"department_id":"d1"}""")))
        assertEquals("About the department", titleOf(row("serve_request_declined", """{"department_id":"d1"}""")))
        val post = row("department_post", """{"department_id":"d1"}""")
        assertEquals("Your department", titleOf(post))
        assertEquals("A new post from your department.", bodyOf(post))
    }

    @Test
    fun `an empty string counts as missing`() {
        // dispatch.ts str(): "" is no value.
        assertEquals("Someone wants to serve in your department", titleOf(row("serve_request_received", """{"department":"","name":""}""")))
        val post = row("department_post", """{"department":"","preview":""}""")
        assertEquals("Your department", titleOf(post))
        assertEquals("A new post from your department.", bodyOf(post))
    }

    @Test
    fun `a row with no payload still has the server's words`() {
        val n = row("serve_request_declined", "null")
        assertNull(n.payload)
        assertEquals("About the department", titleOf(n))
        assertEquals(declinedBody, bodyOf(n))
    }

    // --- the centre's order: dispatch.ts pushCopy() ---

    @Test
    fun `a payload's own title and body come first`() {
        val n = row("department_post", """{"department":"Worship","preview":"Hi","title":"Set by the caller","body":"Its own line"}""")
        assertEquals("Set by the caller", titleOf(n))
        assertEquals("Its own line", bodyOf(n))
    }

    @Test
    fun `a template with no words of its own is still humanised`() {
        val n = row("something_new", "{}")
        assertEquals("Something new", titleOf(n))
        assertNull(bodyOf(n))
    }

    // --- only these four templates ---

    @Test
    fun `other templates are left to their own words`() {
        listOf(
            "department_need_open", "department_need_approved", "department_need_rejected",
            "department_need_closed", "giving_receipt", "badge_awarded", "serve_request", "",
        ).forEach { t ->
            assertNull(t, departmentNotificationTitle(t, null))
            assertNull(t, departmentNotificationBody(t, null))
        }
    }
}
