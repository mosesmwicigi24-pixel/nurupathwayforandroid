package org.nuruplace.member.feature.home

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.nuruplace.member.data.net.CellRoster
import org.nuruplace.member.data.net.CellRosterMember
import org.nuruplace.member.data.net.RosterAttendance
import org.junit.Test

/**
 * The roster's two contracts: the wire's privacy split must survive decoding
 * (a member's payload has NO shepherd fields — they must land null, never 0),
 * and the shepherd's ordering must float whoever needs attention.
 * Same Json config as ApiClient.
 */
@OptIn(ExperimentalSerializationApi::class)
class CellRosterTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test fun `an ordinary member's payload decodes with no fabricated standing`() {
        val roster = json.decodeFromString(
            CellRoster.serializer(),
            """{"cell":{"cell_group_id":"c1","name":"Junction"},"can_shepherd":false,
               "members":[{"user_id":"u1","full_name":"Jake Wealth","first_name":"Jake","is_leader":true}]}""",
        )
        assertEquals(false, roster.canShepherd)
        assertEquals("Junction", roster.cell?.name)
        val m = roster.members.single()
        assertEquals("Jake Wealth", m.fullName)
        assertTrue(m.isLeader)
        // The shepherd fields are ABSENT from this payload — they must stay null.
        assertNull(m.score)
        assertNull(m.band)
        assertNull(m.attendance)
        assertNull(m.lastSeenDays)
    }

    @Test fun `a shepherd's payload carries score band and attendance`() {
        val roster = json.decodeFromString(
            CellRoster.serializer(),
            """{"cell":{"cell_group_id":"c1","name":"Junction"},"can_shepherd":true,
               "members":[{"user_id":"u1","full_name":"Jake Wealth","first_name":"Jake","is_leader":true,
                           "score":81,"band":"thriving","attendance":{"present":2,"of":2},"last_seen_days":3}]}""",
        )
        val m = roster.members.single()
        assertEquals(81, m.score)
        assertEquals("thriving", m.band)
        assertEquals(RosterAttendance(2, 2), m.attendance)
        assertEquals(3, m.lastSeenDays)
    }

    @Test fun `an older server's empty body still decodes`() {
        val roster = json.decodeFromString(CellRoster.serializer(), "{}")
        assertNull(roster.cell)
        assertEquals(false, roster.canShepherd)
        assertTrue(roster.members.isEmpty())
    }

    @Test fun `a null attendance survives as null rather than zero of zero`() {
        val roster = json.decodeFromString(
            CellRoster.serializer(),
            """{"can_shepherd":true,"members":[{"user_id":"u1","score":40,"attendance":null}]}""",
        )
        assertNull(roster.members.single().attendance)
    }

    @Test fun `the shepherd sees the lowest score first and the unscored last`() {
        val roster = CellRoster(
            canShepherd = true,
            members = listOf(
                member("leader", 88, isLeader = true),
                member("nobody", null),
                member("struggling", 21),
                member("steady", 60),
            ),
        )
        assertEquals(
            listOf("struggling", "steady", "leader", "nobody"),
            rosterOrder(roster).map { it.userId },
        )
    }

    @Test fun `an ordinary member keeps the server's leader-first order untouched`() {
        val roster = CellRoster(
            canShepherd = false,
            members = listOf(member("leader", null, isLeader = true), member("b", null), member("a", null)),
        )
        assertEquals(listOf("leader", "b", "a"), rosterOrder(roster).map { it.userId })
    }

    @Test fun `equal scores keep the server's order`() {
        val roster = CellRoster(
            canShepherd = true,
            members = listOf(member("first", 50), member("second", 50), member("third", 10)),
        )
        assertEquals(listOf("third", "first", "second"), rosterOrder(roster).map { it.userId })
    }

    private fun member(id: String, score: Int?, isLeader: Boolean = false) = CellRosterMember(
        userId = id,
        fullName = id,
        firstName = id,
        isLeader = isLeader,
        score = score,
    )
}
