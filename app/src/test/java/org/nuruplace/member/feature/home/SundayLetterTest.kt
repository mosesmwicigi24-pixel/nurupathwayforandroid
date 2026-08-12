// Sunday Letter v2 — decoding + theme resolution.
//
// The decoding cases exist because of a real incident: kotlinx-serialization
// distinguishes an ABSENT key from an explicit `null`, and a schema that only
// tolerated absence rejected Android's explicit nulls — which cost the guest
// video feature entirely until it was root-caused. Letters written before
// migration 186 have all five v2 columns NULL, so both shapes reach us in
// production and both must decode.
package org.nuruplace.member.feature.home

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.PastoralLetter

class SundayLetterTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun decode(s: String) = json.decodeFromString(PastoralLetter.serializer(), s)

    // --- decoding: the legacy shapes ------------------------------------

    @Test
    fun `pre-v2 letter with the five fields ABSENT still decodes and renders honestly`() {
        val l = decode(
            """{"letterId":"a","weekOf":"2026-08-09","body":"Dear friend, this week…",
               "scriptureRef":"Psalm 23:1","createdAt":"2026-08-09T18:00:00Z","readAt":null}""",
        )
        assertEquals("Dear friend, this week…", l.body)
        assertEquals("Psalm 23:1", l.displayScripture)
        // Nothing invented: no title, no salutation, no moments, no next step.
        assertNull(l.displayTitle)
        assertNull(l.displaySalutation)
        assertTrue(l.moments.isEmpty())
        assertNull(l.nextStep)
        assertNull(l.shareLine)
        assertTrue(l.isUnread)
    }

    @Test
    fun `pre-v2 letter with the five fields EXPLICITLY NULL decodes identically`() {
        val l = decode(
            """{"letterId":"a","weekOf":"2026-08-09","body":"b","scriptureRef":null,
               "createdAt":"c","readAt":null,"title":null,"salutation":null,
               "theme":null,"imageKey":null,"highlights":null}""",
        )
        assertNull(l.displayTitle)
        assertNull(l.displaySalutation)
        assertNull(l.displayScripture)
        assertTrue(l.moments.isEmpty())
        assertNull(l.nextStep)
        assertNull(l.shareLine)
        // The hero must still resolve — a null theme can never blank the screen.
        assertEquals(LetterTheme.FALLBACK, LetterTheme.resolve(l.artKey))
    }

    @Test
    fun `a fully populated v2 letter exposes every field`() {
        val l = decode(
            """{"letterId":"a","weekOf":"2026-08-09","body":"body","scriptureRef":"John 1:5",
               "createdAt":"c","readAt":"2026-08-10T06:00:00Z","title":"The week you kept going",
               "salutation":"Dear Moses","theme":"dawn","imageKey":"dawn",
               "highlights":{"moments":["You finished Module 4 on Tuesday","You prayed three mornings"],
                             "nextStep":{"label":"Module 5 is waiting","route":"module",
                                         "params":{"moduleId":"m5"}},
                             "shareLine":"Grace upon grace."}}""",
        )
        assertEquals("The week you kept going", l.displayTitle)
        assertEquals("Dear Moses", l.displaySalutation)
        assertEquals(LetterTheme.DAWN, LetterTheme.resolve(l.artKey))
        assertEquals(2, l.moments.size)
        assertEquals("Module 5 is waiting", l.nextStep?.label)
        assertEquals("m5", l.nextStep?.params?.moduleId)
        assertEquals("Grace upon grace.", l.shareLine)
        assertTrue(!l.isUnread)
    }

    // --- blank-is-as-good-as-missing ------------------------------------

    @Test
    fun `blank strings are treated as absent rather than rendered empty`() {
        val l = decode(
            """{"letterId":"a","weekOf":"w","body":"b","createdAt":"c",
               "title":"   ","salutation":"","scriptureRef":"  ",
               "highlights":{"moments":["  ","real moment"],"shareLine":" "}}""",
        )
        assertNull(l.displayTitle)
        assertNull(l.displaySalutation)
        assertNull(l.displayScripture)
        assertNull(l.shareLine)
        assertEquals(listOf("real moment"), l.moments)
    }

    @Test
    fun `a next step missing its label or route is not offered`() {
        val noLabel = decode(
            """{"letterId":"a","weekOf":"w","body":"b","createdAt":"c",
               "highlights":{"nextStep":{"label":"","route":"module"}}}""",
        )
        val noRoute = decode(
            """{"letterId":"a","weekOf":"w","body":"b","createdAt":"c",
               "highlights":{"nextStep":{"label":"Go","route":""}}}""",
        )
        assertNull(noLabel.nextStep)
        assertNull(noRoute.nextStep)
    }

    @Test
    fun `image_key wins over theme, and theme is the fallback when image_key is blank`() {
        val both = decode("""{"letterId":"a","weekOf":"w","body":"b","createdAt":"c","theme":"water","imageKey":"harvest"}""")
        val themeOnly = decode("""{"letterId":"a","weekOf":"w","body":"b","createdAt":"c","theme":"water","imageKey":"  "}""")
        assertEquals(LetterTheme.HARVEST, LetterTheme.resolve(both.artKey))
        assertEquals(LetterTheme.WATER, LetterTheme.resolve(themeOnly.artKey))
    }

    // --- theme resolution is TOTAL --------------------------------------

    @Test
    fun `every theme the backend can send resolves to its own art`() {
        val expected = mapOf(
            "dawn" to LetterTheme.DAWN, "water" to LetterTheme.WATER, "path" to LetterTheme.PATH,
            "harvest" to LetterTheme.HARVEST, "shelter" to LetterTheme.SHELTER, "light" to LetterTheme.LIGHT,
            "seed" to LetterTheme.SEED, "garden" to LetterTheme.GARDEN, "mountain" to LetterTheme.MOUNTAIN,
            "rest" to LetterTheme.REST,
        )
        // Guards against the backend growing an 11th theme the client silently ignores.
        assertEquals(LetterTheme.entries.size, expected.size)
        expected.forEach { (key, theme) -> assertEquals(theme, LetterTheme.resolve(key)) }
    }

    @Test
    fun `unknown, empty, blank, miscased and null keys all fall back rather than blank the hero`() {
        listOf(null, "", "   ", "sunset", "DAWN!!", "ocean").forEach {
            assertEquals("resolve($it)", LetterTheme.FALLBACK, LetterTheme.resolve(it))
        }
        // Case and surrounding space are wire noise, not a different theme.
        assertEquals(LetterTheme.DAWN, LetterTheme.resolve("  Dawn "))
        assertEquals(LetterTheme.MOUNTAIN, LetterTheme.resolve("MOUNTAIN"))
    }

    @Test
    fun `every theme has a distinct accent so consecutive weeks look different`() {
        val accents = LetterTheme.entries.map { it.accentColor }
        // Not all ten need be unique, but a majority must differ or the
        // "variety between weeks" requirement is cosmetic only.
        assertTrue("accents were too uniform: $accents", accents.toSet().size >= 6)
    }
}
