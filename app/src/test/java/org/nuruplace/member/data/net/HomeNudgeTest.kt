// GET /me/home/nudges — the wire shape is snake_case at the envelope but the
// server writes `params` keys in camelCase ({ moduleId, levelNumber, … } —
// pathway home/service.ts). The client's Json applies a global SnakeCase
// naming strategy, which would turn a typed `moduleId` property into a
// `module_id` lookup that never matches; `params` therefore stays a raw
// JsonObject read by HomeNudge.param. This test decodes the EXACT shape the
// server emits through the SAME Json configuration ApiClient uses, so the
// reasoning is proven rather than assumed.
package org.nuruplace.member.data.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class HomeNudgeTest {
    // Mirrors ApiClient.json exactly.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private fun decode(s: String) = json.decodeFromString(NudgesRes.serializer(), s)

    @Test
    fun `envelope snake_case maps to camelCase properties`() {
        val res = decode(
            """{"nudges":[{"id":"reflection_due","kind":"reflection_due","title":"Reflection due today",
               "body":"Write today's devotional reflection","cta_label":"Start reflection","route":"devotional",
               "accent":"gold","priority":70,"due":"today"}]}""",
        )
        val n = res.nudges.single()
        assertEquals("Start reflection", n.ctaLabel)
        assertEquals("devotional", n.route)
        assertEquals("today", n.due)
        assertNull(n.params)
        assertNull(n.moduleId)
    }

    @Test
    fun `camelCase params keys survive the snake_case strategy`() {
        val res = decode(
            """{"nudges":[
               {"id":"quiz:m1","kind":"quiz_in_progress","title":"t","body":"b","cta_label":"Continue test",
                "route":"quiz","params":{"moduleId":"m1"},"accent":"navy","priority":80,"due":"today"},
               {"id":"level_review:2","kind":"level_review","title":"t","body":"b","cta_label":"Start review",
                "route":"level_exam","params":{"levelNumber":2},"accent":"gold","priority":85,"due":null},
               {"id":"invite:x","kind":"reading_invite","title":"t","body":"b","cta_label":"See invite",
                "route":"reading_invite","params":{"token":"abc-123","conversationId":"c9"},"accent":"gold","priority":50}
            ]}""",
        )
        val (quiz, review, invite) = res.nudges
        assertEquals("m1", quiz.moduleId)
        assertEquals(2, review.levelNumber)
        assertNull(review.due)
        assertEquals("abc-123", invite.token)
        assertEquals("c9", invite.conversationId)
        assertNull(invite.due)
    }

    @Test
    fun `a snake_case twin inside params is tolerated too`() {
        val res = decode(
            """{"nudges":[{"id":"plan:p1","kind":"plan_day_due","title":"t","body":"b","cta_label":"Read today",
               "route":"plan","params":{"plan_id":"p1","level_number":"3"},"accent":"gold","priority":60}]}""",
        )
        val n = res.nudges.single()
        assertEquals("p1", n.planId)
        assertEquals(3, n.levelNumber)
    }

    @Test
    fun `an empty or missing list is the honest nothing-waiting state`() {
        assertEquals(0, decode("""{"nudges":[]}""").nudges.size)
        assertEquals(0, decode("""{}""").nudges.size)
    }
}
