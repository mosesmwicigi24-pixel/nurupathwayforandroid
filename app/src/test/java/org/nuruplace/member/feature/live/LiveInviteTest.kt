package org.nuruplace.member.feature.live

import android.app.NotificationManager
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.LiveNowRow

/** The ringing Live guest invite (owner request 2026-09-28). The ring push is
 *  DATA-ONLY, and its data is exactly what workers/dispatch.ts fcmMessage
 *  builds for `live_guest_invite` (payload `{ stream_id, title }` from
 *  live/service.ts inviteGuest, plus template, nuru_kind, nuru_sound,
 *  alert_title, alert_body and body). */
class LiveInviteTest {

    private val ring = mapOf(
        "stream_id" to "5f0c7a52-2d7e-4a8b-9c1d-0e5b8f3a6d21",
        "title" to "Sunday service",
        "template" to "live_guest_invite",
        "nuru_kind" to "ring",
        "nuru_sound" to "on",
        "alert_title" to "You're invited to go live",
        "alert_body" to "You've been invited to join \"Sunday service\" as a guest.",
        "body" to "You've been invited to join \"Sunday service\" as a guest.",
    )

    // --- parsing the data-only ring ---

    @Test fun `a ring push parses into its invite`() {
        val invite = liveInviteFrom(ring)!!
        assertEquals("5f0c7a52-2d7e-4a8b-9c1d-0e5b8f3a6d21", invite.streamId)
        assertEquals("Sunday service", invite.streamTitle) // the STREAM's name, from `title`
        assertEquals("You're invited to go live", invite.heading)
        assertEquals("You've been invited to join \"Sunday service\" as a guest.", invite.words)
        assertFalse(invite.quiet)
    }

    @Test fun `a muted member's ring is quiet`() {
        assertTrue(liveInviteFrom(ring + ("nuru_sound" to "off"))!!.quiet)
        // No nuru_sound at all — the setting defaults to on.
        assertFalse(liveInviteFrom(ring - "nuru_sound")!!.quiet)
    }

    @Test fun `no stream, no invite`() {
        assertNull(liveInviteFrom(ring - "stream_id"))
        assertNull(liveInviteFrom(ring + ("stream_id" to "  ")))
    }

    @Test fun `a stream id that could bend the route is refused`() {
        assertNull(liveInviteFrom(ring + ("stream_id" to "abc&accept=true")))
        assertNull(liveInviteFrom(ring + ("stream_id" to "../home")))
        assertNull(liveInviteFrom(ring + ("stream_id" to "a".repeat(65))))
    }

    @Test fun `the words fall back to body, then to the backend's own sentence`() {
        val noAlert = ring - "alert_title" - "alert_body"
        val fromBody = liveInviteFrom(noAlert)!!
        assertEquals("You're invited to go live", fromBody.heading)
        assertEquals("You've been invited to join \"Sunday service\" as a guest.", fromBody.words)
        val bare = liveInviteFrom(mapOf("stream_id" to "s-1", "title" to "Youth night"))!!
        assertEquals("You've been invited to join \"Youth night\" as a guest.", bare.words)
        assertEquals("Nuru Live", liveInviteFrom(mapOf("stream_id" to "s-1"))!!.streamTitle)
    }

    // --- the ring's 30 seconds ---

    @Test fun `the ring lasts thirty seconds`() {
        assertEquals(30_000L, RING_TIMEOUT_MS)
    }

    @Test fun `a fresh ring has all of it, a later look only what is left`() {
        val posted = 1_000_000L
        assertEquals(30_000L, ringRemainingMs(posted, posted))
        assertEquals(18_000L, ringRemainingMs(posted, posted + 12_000))
        assertEquals(1L, ringRemainingMs(posted, posted + 29_999))
    }

    @Test fun `a ring that has run out has nothing left`() {
        val posted = 1_000_000L
        assertEquals(0L, ringRemainingMs(posted, posted + 30_000))
        assertEquals(0L, ringRemainingMs(posted, posted + 90_000))
    }

    @Test fun `a clock set backwards never makes it ring for longer`() {
        assertEquals(30_000L, ringRemainingMs(postedAtMs = 1_000_000L, nowMs = 400_000L))
    }

    // --- Join lands in exactly that stream's player ---

    @Test fun `join routes to the live-now forwarder for that stream`() {
        assertEquals("live-now?streamId=s-42", liveInviteRoute("s-42"))
    }

    private fun row(id: String) = LiveNowRow(streamId = id, title = id, hlsUrl = "/live/$id/index.m3u8")

    @Test fun `the forwarder opens the named stream, not the newest`() {
        val rows = listOf(row("newest"), row("invited"))
        assertEquals("invited", liveForwardTarget(rows, "invited")?.streamId)
    }

    @Test fun `the forwarder goes home when the named stream has ended - never to another`() {
        assertNull(liveForwardTarget(listOf(row("newest")), "invited"))
        assertNull(liveForwardTarget(emptyList(), "invited"))
    }

    @Test fun `with no stream named the forwarder still opens the newest`() {
        val rows = listOf(row("newest"), row("older"))
        assertEquals("newest", liveForwardTarget(rows, null)?.streamId)
        assertEquals("newest", liveForwardTarget(rows, "")?.streamId)
        assertNull(liveForwardTarget(emptyList(), null))
    }

    // --- how the full screen rings: as the member set the channel, and the phone ---

    private fun plan(
        importance: Int = NotificationManager.IMPORTANCE_HIGH,
        sound: Boolean = true,
        vibrates: Boolean = true,
        ringer: Int = AudioManager.RINGER_MODE_NORMAL,
        dnd: Boolean = false,
    ) = ringPlan(importance, sound, vibrates, ringer, dnd)

    @Test fun `a phone on normal rings aloud and buzzes`() {
        assertEquals(RingPlan(sound = true, vibrate = true), plan())
    }

    @Test fun `vibrate mode buzzes without sound, silent mode does neither`() {
        assertEquals(RingPlan(sound = false, vibrate = true), plan(ringer = AudioManager.RINGER_MODE_VIBRATE))
        assertEquals(RingPlan(sound = false, vibrate = false), plan(ringer = AudioManager.RINGER_MODE_SILENT))
    }

    @Test fun `do not disturb, or a channel turned down, rings nothing`() {
        assertEquals(RingPlan(sound = false, vibrate = false), plan(dnd = true))
        assertEquals(RingPlan(sound = false, vibrate = false), plan(importance = NotificationManager.IMPORTANCE_LOW))
    }

    @Test fun `the member's own channel choices hold`() {
        assertEquals(RingPlan(sound = false, vibrate = true), plan(sound = false))
        assertEquals(RingPlan(sound = true, vibrate = false), plan(vibrates = false))
        assertEquals(RingPlan(sound = true, vibrate = true), plan(importance = NotificationManager.IMPORTANCE_DEFAULT))
    }
}
