// Departments on the wire (docs/PARTNERS_PROGRAMME.md §4, §5): a GET
// /departments row decodes with the client's global SnakeCase strategy, the
// detail's nested posts/members/needs decode, a giving intent carries need_id
// only when a need is attached, and the leader's write bodies use the spec
// keys and omit absent optionals (a stray null would 400 on the server).
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
class DepartmentsWireTest {
    // Same configuration as ApiClient's private json.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    private val listRow = """
        {"department_id":"d1","name":"Worship","purpose":"Lead the church in song","meets":"Sat 4pm",
         "image_url":null,"gift_keys":["music","leadership"],"is_open_to_join":true,
         "leader_name":"Grace N.","leader_avatar":null,"member_count":7,
         "my_status":"requested","my_role":null,"open_needs":2,
         "latest_post":"Rehearsal moved to 5pm this week","latest_post_at":"2026-09-20T10:00:00.000Z",
         "fit":true,"matched_gifts":["music"]}
    """.trimIndent()

    @Test
    fun `a GET departments row decodes`() {
        val env = json.decodeFromString<Envelope<Department>>("""{"data":[$listRow]}""")
        val d = env.data.single()
        assertEquals("d1", d.departmentId)
        assertEquals("Worship", d.name)
        assertEquals("Sat 4pm", d.meets)
        assertNull(d.imageUrl)
        assertEquals(listOf("music", "leadership"), d.giftKeys)
        assertTrue(d.isOpenToJoin)
        assertEquals("Grace N.", d.leaderName)
        assertEquals(7, d.memberCount)
        assertTrue(d.isRequested)
        assertFalse(d.isActive)
        assertNull(d.myRole)
        assertEquals(2, d.openNeeds)
        assertEquals("Rehearsal moved to 5pm this week", d.latestPost)
        assertTrue(d.fit)
        assertEquals(listOf("music"), d.matchedGifts)
        // Detail-only fields stay at their empty defaults on a list row.
        assertFalse(d.isLeader)
        assertTrue(d.posts.isEmpty() && d.members.isEmpty() && d.needs.isEmpty())
    }

    @Test
    fun `nulls for non-null fields coerce to defaults instead of failing`() {
        val d = json.decodeFromString<Department>("""{"department_id":"d2","name":"Ushers","purpose":null,"gift_keys":null,"member_count":null,"my_status":null}""")
        assertEquals("", d.purpose)
        assertTrue(d.giftKeys.isEmpty())
        assertEquals(0, d.memberCount)
        assertNull(d.myStatus)
    }

    @Test
    fun `GET departments id decodes posts, members and needs with progress`() {
        val d = json.decodeFromString<Department>(
            """{"department_id":"d1","name":"Worship","purpose":"","my_status":"active","my_role":"leader","is_leader":true,
                "posts":[{"post_id":"p1","body":"Hello team","image_url":"https://x/y.jpg","created_at":"2026-09-20T10:00:00.000Z","author_name":"Grace","author_avatar":null}],
                "members":[{"user_id":"u1","full_name":"Grace N.","avatar_url":null,"role":"leader"},{"user_id":"u2","full_name":"Ben","avatar_url":null,"role":"member"}],
                "needs":[{"need_id":"n1","title":"Sound desk","why":"The old one crackles","target_minor":50000000,"currency":"KES","deadline":"2026-12-31",
                          "status":"approved","created_at":"2026-09-01T00:00:00.000Z","submitted_name":"Grace","raised_minor":12500000,"percent":25,"reached":false},
                         {"need_id":"n2","title":"Cables","why":"","target_minor":100000,"currency":"KES","deadline":null,"status":"pending","created_at":"","submitted_name":"Grace","raised_minor":0,"percent":0,"reached":false}]}""",
        )
        assertTrue(d.isLeader && d.isActive && d.myRole == "leader")
        assertEquals("Hello team", d.posts.single().body)
        assertEquals("https://x/y.jpg", d.posts.single().imageUrl)
        assertEquals(listOf("leader", "member"), d.members.map { it.role })
        val open = d.needs.first()
        assertTrue(open.isOpen)
        assertEquals(50_000_000, open.targetMinor)
        assertEquals(12_500_000, open.raisedMinor)
        assertEquals(25, open.percent)
        assertEquals("2026-12-31", open.deadline)
        assertFalse(d.needs[1].isOpen)
        assertNull(d.needs[1].deadline)
    }

    @Test
    fun `serve reply decodes`() {
        assertEquals("requested", json.decodeFromString<ServeStatus>("""{"status":"requested"}""").status)
    }

    @Test
    fun `giving intent carries need_id only when a need is attached`() {
        val withNeed = json.parseToJsonElement(json.encodeToString(GiveBody("gift", 500_000, "KES", "mpesa", idempotencyKey = "k", needId = "n1"))).jsonObject
        assertEquals("n1", withNeed["need_id"]!!.jsonPrimitive.content)
        assertFalse("pledge_id" in withNeed)
        val plain = json.parseToJsonElement(json.encodeToString(GiveBody("tithe", 1, "KES", "mpesa", idempotencyKey = "k"))).jsonObject
        assertFalse("need_id" in plain)
    }

    @Test
    fun `leader post body omits image_url when absent and sends it when given`() {
        assertEquals(setOf("body"), json.parseToJsonElement(json.encodeToString(DepartmentPostBody("Hi"))).jsonObject.keys)
        val o = json.parseToJsonElement(json.encodeToString(DepartmentPostBody("Hi", "https://x/y.jpg"))).jsonObject
        assertEquals(setOf("body", "image_url"), o.keys)
    }

    @Test
    fun `leader need body uses the spec keys and omits deadline when absent`() {
        val o = json.parseToJsonElement(json.encodeToString(DepartmentNeedBody("Sound desk", "The old one crackles", 50_000_000))).jsonObject
        assertEquals(setOf("title", "why", "target_minor", "currency"), o.keys)
        assertEquals("KES", o["currency"]!!.jsonPrimitive.content)
        val dated = json.parseToJsonElement(json.encodeToString(DepartmentNeedBody("T", "W", 1, deadline = "2026-12-31"))).jsonObject
        assertEquals("2026-12-31", dated["deadline"]!!.jsonPrimitive.content)
    }
}
