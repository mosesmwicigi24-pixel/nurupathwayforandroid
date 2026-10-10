package org.nuruplace.member.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sound and vibration (owner request 2026-09-28). The channel ids are part
 *  of the push contract — the backend names them in every Android push it
 *  renders (workers/dispatch.ts PUSH_CHANNEL) — and the app must pick the
 *  same channel for the pushes it renders itself: by `nuru_kind`, unless
 *  `nuru_sound` says the member muted them. */
class NotificationChannelsTest {

    @Test fun `the channel ids are the ones the backend names`() {
        assertEquals("nuru_messages", NotificationChannels.MESSAGES)
        assertEquals("nuru_updates", NotificationChannels.UPDATES)
        assertEquals("nuru_quiet", NotificationChannels.QUIET)
        assertEquals("nuru_live_invite", NotificationChannels.LIVE_INVITE)
        assertEquals("message", PushKind.MESSAGE)
        assertEquals("update", PushKind.UPDATE)
        assertEquals("ring", PushKind.RING)
    }

    @Test fun `with sound on each kind has its own channel`() {
        assertEquals(NotificationChannels.MESSAGES, channelFor("message", "on"))
        assertEquals(NotificationChannels.UPDATES, channelFor("update", "on"))
        assertEquals(NotificationChannels.LIVE_INVITE, channelFor("ring", "on"))
    }

    @Test fun `muted, every kind goes to the quiet channel`() {
        for (kind in listOf("message", "update", "ring", null, "something_new")) {
            assertEquals("kind=$kind", NotificationChannels.QUIET, channelFor(kind, "off"))
        }
    }

    @Test fun `an absent or unknown kind is an update, and an absent sound sounds`() {
        assertEquals(NotificationChannels.UPDATES, channelFor(null, null))
        assertEquals(NotificationChannels.UPDATES, channelFor("something_new", "on"))
        // A push from before the contract (no nuru_sound) — the setting defaults to on.
        assertEquals(NotificationChannels.MESSAGES, channelFor("message", null))
    }

    @Test fun `the ring vibrates long and starts at once`() {
        val pattern = NotificationChannels.LIVE_INVITE_VIBRATION
        assertEquals(0L, pattern.first())
        assertTrue("a call-like buzz, not a tick", pattern.drop(1).sum() >= 5_000L)
    }

    // --- the open-conversation rule: a message for the thread on screen lands IN it ---

    private val dm = mapOf(
        "template" to "chat_dm_message", "nuru_kind" to "message", "nuru_sound" to "on",
        "conversation_id" to "c-1", "title" to "Grace N.", "body" to "See you Sunday", "message_id" to "m-1",
    )

    @Test fun `a message for the open thread lands in it, not the tray`() {
        assertTrue(landsInOpenThread("message", dm, openConversationId = "c-1"))
    }

    @Test fun `a message for another thread, or with no thread open, notifies`() {
        assertFalse(landsInOpenThread("message", dm, openConversationId = "c-2"))
        assertFalse(landsInOpenThread("message", dm, openConversationId = null))
    }

    @Test fun `only a message is held back - an update naming the open conversation still notifies`() {
        val joinRequest = mapOf("template" to "space_join_requested", "nuru_kind" to "update", "conversation_id" to "c-1")
        assertFalse(landsInOpenThread("update", joinRequest, openConversationId = "c-1"))
        assertFalse(landsInOpenThread(null, dm, openConversationId = "c-1"))
    }

    @Test fun `a message push with no conversation never matches`() {
        assertFalse(landsInOpenThread("message", mapOf("template" to "chat_broadcast"), openConversationId = "c-1"))
        assertFalse(landsInOpenThread("message", mapOf("conversation_id" to ""), openConversationId = ""))
    }

    @Test fun `the conversation is read from the key the backend writes`() {
        assertEquals("c-1", pushConversationId(mapOf("conversation_id" to "c-1")))
        // The spelling this client used to look for is still honoured.
        assertEquals("c-9", pushConversationId(mapOf("conversationId" to "c-9")))
        assertNull(pushConversationId(mapOf("conversation_id" to " ")))
        assertNull(pushConversationId(emptyMap()))
    }

    // --- pastoral privacy: recognised by its template or its thread ---

    @Test fun `a pastoral push is recognised by its template`() {
        val pastoral = mapOf(
            "template" to "chat_pastoral_message", "conversation_id" to "p-1",
            "title" to "Nuru Pathway", "body" to "You have a new private pastoral message.",
        )
        assertTrue(isPastoralPush(pastoral, pastoralConversationId = null))
    }

    @Test fun `a push about the known pastoral thread is pastoral whatever its template`() {
        assertTrue(isPastoralPush(mapOf("conversation_id" to "p-1"), pastoralConversationId = "p-1"))
        assertFalse(isPastoralPush(dm, pastoralConversationId = "p-1"))
        assertFalse(isPastoralPush(dm, pastoralConversationId = null))
        assertFalse(isPastoralPush(mapOf("template" to "badge_awarded"), pastoralConversationId = null))
    }
}
