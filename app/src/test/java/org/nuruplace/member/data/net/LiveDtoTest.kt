package org.nuruplace.member.data.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same Json config as ApiClient (snake_case wire, tolerant of missing keys) —
 *  the L5 interaction DTOs (docs/LIVE_INTERACTIVE.md) must decode a minimal
 *  payload without throwing, same posture as every other DTO in this app. */
@OptIn(ExperimentalSerializationApi::class)
class LiveDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test fun `live now row tolerates a minimal payload and defaults fallback url to null`() {
        val row = json.decodeFromString(
            LiveNowRow.serializer(),
            """{"stream_id":"s1","hls_url":"/live/church/index.m3u8"}""",
        )
        assertEquals("s1", row.streamId)
        assertNull(row.hlsFallbackUrl)
        assertEquals(0, row.viewerCount)
    }

    @Test fun `live now row decodes the CDN fallback url when present`() {
        val row = json.decodeFromString(
            LiveNowRow.serializer(),
            """{"stream_id":"s1","hls_url":"https://cdn.example/live-cdn/church/index.m3u8",
                "hls_fallback_url":"/live/church/index.m3u8"}""",
        )
        assertEquals("/live/church/index.m3u8", row.hlsFallbackUrl)
    }

    @Test fun `pulse tolerates a bare object and decodes the reactions map forward-tolerantly`() {
        // The backend is being widened in parallel to accept "fire" alongside
        // "like"/"love" — a fixed {like,love} shape would silently drop it.
        val pulse = json.decodeFromString(
            LivePulse.serializer(),
            """{"viewer_count":12,"reactions":{"like":3,"love":1,"fire":7}}""",
        )
        assertEquals(12, pulse.viewerCount)
        assertEquals(3, pulse.reactions["like"])
        assertEquals(7, pulse.reactions["fire"])
        assertTrue(pulse.hands.isEmpty())
        assertTrue(pulse.guests.isEmpty())
    }

    @Test fun `pulse decodes hands and guests rows`() {
        val pulse = json.decodeFromString(
            LivePulse.serializer(),
            """{"hands":[{"user_id":"u1","full_name":"Amina","raised_at":"2026-07-31T10:00:00Z"}],
                "guests":[{"user_id":"u2","full_name":"Kato","status":"invited"}]}""",
        )
        assertEquals(1, pulse.hands.size)
        assertEquals("u1", pulse.hands[0].userId)
        assertEquals("invited", pulse.guests[0].status)
    }

    @Test fun `message row tolerates a minimal payload`() {
        val row = json.decodeFromString(
            LiveMessageRow.serializer(),
            """{"message_id":"m1","user_id":"u1","full_name":"Amina","body":"Amen!","sent_at":"2026-07-31T10:00:00.000Z"}""",
        )
        assertEquals("Amen!", row.body)
        assertNull(row.avatarUrl)
    }

    @Test fun `messages response tolerates an empty list`() {
        val res = json.decodeFromString(LiveMessagesRes.serializer(), """{"messages":[]}""")
        assertTrue(res.messages.isEmpty())
    }
}
