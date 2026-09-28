// GET/PUT /me/notification-preferences on the wire (Settings' notification
// toggles). "Sound and vibration" is `sound_enabled` (owner request
// 2026-09-28, server migration 223): on by default, so a response without it
// reads as on, and the PUT always sends it beside the three toggles the
// server requires.
package org.nuruplace.member.data.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class NotificationPreferencesWireTest {
    // Same configuration as ApiClient's private json.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test fun `sound is on when the response doesn't mention it`() {
        val prefs = json.decodeFromString(
            NotificationPreferences.serializer(),
            """{"push_enabled":true,"email_enabled":false,"sms_enabled":false}""",
        )
        assertTrue(prefs.soundEnabled)
        assertFalse(prefs.emailEnabled)
    }

    @Test fun `a muted member reads as muted`() {
        val prefs = json.decodeFromString(
            NotificationPreferences.serializer(),
            """{"push_enabled":true,"email_enabled":true,"sms_enabled":false,"sound_enabled":false}""",
        )
        assertFalse(prefs.soundEnabled)
    }

    @Test fun `the PUT sends all four toggles, sound included`() {
        val body = json.encodeToJsonElement(
            NotificationPreferences.serializer(),
            NotificationPreferences(pushEnabled = true, emailEnabled = false, smsEnabled = false, soundEnabled = false),
        ).jsonObject
        assertEquals(setOf("push_enabled", "email_enabled", "sms_enabled", "sound_enabled"), body.keys)
        assertFalse(body.getValue("sound_enabled").jsonPrimitive.boolean)
        assertTrue(body.getValue("push_enabled").jsonPrimitive.boolean)
    }

    @Test fun `turning sound back on sends true, not an omission`() {
        // encodeDefaults: a value equal to its default is still written — the
        // server keeps its stored value when the key is absent, so an omitted
        // `true` would never unmute anyone.
        val body = json.encodeToJsonElement(NotificationPreferences.serializer(), NotificationPreferences()).jsonObject
        assertTrue(body.getValue("sound_enabled").jsonPrimitive.boolean)
    }
}
