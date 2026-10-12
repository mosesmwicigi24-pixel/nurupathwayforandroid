package org.nuruplace.member.feature.pathway

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.nuruplace.member.data.net.ModuleReflectionEnv
import org.nuruplace.member.data.net.SavedModuleReflection

/** A finished module's folded reflection says only what the server holds
 *  (Cycle 4 walk: "YOUR REFLECTION · ✓ Saved · —" — the words were never
 *  fetched, so every finished module claimed a saved reflection and showed
 *  none). */
class FoldedReflectionTest {
    // Mirrors ApiClient.json exactly.
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test fun `words on the server are Saved, and shown`() {
        val env = json.decodeFromString<ModuleReflectionEnv>(
            """{"data":{"body":"He is faithful when I am not.","created_at":"2026-10-05T09:12:00.000Z"}}""",
        )
        assertEquals(FoldedReflection.Saved("He is faithful when I am not."), foldedReflectionOf(env.data))
    }

    @Test fun `no reflection on the server is never called Saved`() {
        val env = json.decodeFromString<ModuleReflectionEnv>("""{"data":null}""")
        assertEquals(FoldedReflection.NoneSaved, foldedReflectionOf(env.data))
        assertEquals(FoldedReflection.NoneSaved, foldedReflectionOf(SavedModuleReflection(body = "   ")))
    }

    @Test fun `the folded card's words are plain`() {
        assertEquals("No reflection saved for this module.", NO_REFLECTION_SAVED)
        assertEquals("Couldn't load your reflection.", RELOAD_FAILED)
    }
}
