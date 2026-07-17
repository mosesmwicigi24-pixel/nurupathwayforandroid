package org.nuruplace.member.data.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same Json config as ApiClient (snake_case wire, tolerant of missing keys) —
 *  every broadcast DTO must decode a minimal payload without throwing, the
 *  same tolerance the iOS models get from their hand-written Decodable inits. */
@OptIn(ExperimentalSerializationApi::class)
class BroadcastDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test fun `broadcast list tolerates a minimal row`() {
        val res = json.decodeFromString(
            BroadcastListRes.serializer(),
            """{"data":[{"broadcast_id":"b1"}],"total":1}""",
        )
        assertEquals(1, res.data.size)
        assertEquals("b1", res.data[0].broadcastId)
        assertEquals("", res.data[0].body)
        assertEquals("all", res.data[0].audience)
        assertEquals(0, res.data[0].seenCount)
    }

    @Test fun `broadcast detail tolerates missing responses and recipients`() {
        val res = json.decodeFromString(
            BroadcastDetailRes.serializer(),
            """{"broadcast_id":"b1","body":"Grace and peace"}""",
        )
        assertEquals("b1", res.broadcastId)
        assertEquals("Grace and peace", res.body)
        assertEquals(emptyList<BroadcastResponseRow>(), res.responses)
        assertEquals(emptyList<BroadcastRecipientRow>(), res.recipients)
        assertEquals(0, res.deliveredCount)
    }

    @Test fun `recipient row defaults delivered true and seen false`() {
        val row = json.decodeFromString(
            BroadcastRecipientRow.serializer(),
            """{"user_id":"u1","full_name":"Amina"}""",
        )
        assertEquals(true, row.delivered)
        assertEquals(false, row.seen)
    }
}
