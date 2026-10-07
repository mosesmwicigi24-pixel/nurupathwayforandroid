package org.nuruplace.member.data.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The Sunday Letter as the server sends it (intelligence/letters.ts
 *  rowFromDb, since v2 #410): `highlights` an array of the week's moments,
 *  `next_step` and `share_line` beside it. Android read `highlights` as an
 *  object, so every v2 letter failed to decode and Home showed "Your letter
 *  arrives Sunday evening" over a letter that was waiting. */
class LetterWireTest {
    // Mirrors ApiClient.json exactly.
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test fun `the latest letter decodes as the local API sent it`() {
        // GET /me/letters/latest, verbatim from the local API (2026-10-07).
        val res = json.decodeFromString<LatestLetterRes>(
            """{"letter":{"letter_id":"5528cf92-a9d8-4215-9974-17181926ca6f","week_of":"2026-10-04","title":"The two prayers you marked answered","salutation":"Dear Builder,","theme":"light","image_key":"dawn","body":"You marked two prayers answered this week. That is worth stopping for.","scripture_ref":"Philippians 4:6","highlights":[],"next_step":null,"share_line":null,"created_at":"2026-10-07T05:11:24.664Z","read_at":null}}""",
        )
        assertNotNull(res.letter)
        val letter = res.letter!!
        assertEquals("The two prayers you marked answered", letter.displayTitle)
        assertEquals(true, letter.isUnread)
        assertEquals(emptyList<String>(), letter.moments)
        assertNull(letter.nextStep)
    }

    @Test fun `its moments, next step and share line are read where the server puts them`() {
        val letter = json.decodeFromString<PastoralLetter>(
            """{"letter_id":"l1","week_of":"2026-10-04","body":"…","highlights":["You prayed four mornings."," ",""],"next_step":{"label":"God's Plan for Humanity is waiting","route":"module","params":{"moduleId":"m2"}},"share_line":" He is faithful. ","created_at":"2026-10-04T15:00:00Z","read_at":"2026-10-05T07:00:00Z"}""",
        )
        assertEquals(listOf("You prayed four mornings."), letter.moments)
        assertEquals("module", letter.nextStep?.route)
        assertEquals("m2", letter.nextStep?.params?.moduleId)
        assertEquals("He is faithful.", letter.shareLine)
        assertEquals(false, letter.isUnread)
    }

    @Test fun `a v3 letter (pathway#512) still decodes with this reading`() {
        // GET /me/letters/latest, verbatim from the local API running
        // pathway#512 (2026-10-07). v3 only adds keys (issue_no,
        // reading_minutes, paragraphs, scripture, photo, figures, signed_by,
        // pdf_url), so when production deploys it this build keeps showing
        // the letter exactly as today.
        val res = json.decodeFromString<LatestLetterRes>(
            """{"letter":{"letter_id":"5528cf92-a9d8-4215-9974-17181926ca6f","week_of":"2026-10-04","title":"The two prayers you marked answered","salutation":"Dear Builder,","theme":"light","image_key":"dawn","body":"You marked two prayers answered this week. That is worth stopping for.","scripture_ref":"Philippians 4:6","highlights":[],"next_step":null,"share_line":null,"created_at":"2026-10-07T05:11:24.664Z","read_at":"2026-10-07T05:49:35.111Z","issue_no":1,"reading_minutes":1,"paragraphs":["You marked two prayers answered this week. That is worth stopping for."],"scripture":{"ref":"Philippians 4:6","text":"Do not be anxious about anything, but in everything by prayer and supplication with thanksgiving let your requests be made known to God.","version":"ESV"},"photo":{"id":"1732106846688-3bac122e4f97","url":"https://images.unsplash.com/photo-1732106846688-3bac122e4f97?auto=format&fit=crop&w=1080&q=70","alt":"The sun behind a mountain at daybreak","caption":"The sun behind a mountain at daybreak. Chosen for a week of light breaking through."},"figures":[],"signed_by":{"name":"Pastor Moses","role":"Nuru Place"},"pdf_url":"/v1/me/letters/5528cf92-a9d8-4215-9974-17181926ca6f/pdf"}}""",
        )
        val letter = res.letter!!
        assertEquals("The two prayers you marked answered", letter.displayTitle)
        assertEquals("Philippians 4:6", letter.displayScripture)
        assertEquals("dawn", letter.artKey)
        assertEquals(false, letter.isUnread)
        assertNull(letter.nextStep)
    }

    /** The old reading, for the record: `highlights` as an object fails on the
     *  wire's array — the whole letter was lost to Home's runCatching. */
    @kotlinx.serialization.Serializable
    private data class OldHighlights(val moments: List<String> = emptyList())

    @kotlinx.serialization.Serializable
    private data class OldLetter(val letterId: String = "", val highlights: OldHighlights? = null)

    @Test fun `the old object reading threw on every v2 letter`() {
        val failed = runCatching { json.decodeFromString<OldLetter>("""{"letter_id":"l1","highlights":[]}""") }
        assertEquals(true, failed.isFailure)
    }

    @Test fun `a letter from before v2 has none of them`() {
        val letter = json.decodeFromString<PastoralLetter>(
            """{"letter_id":"l0","week_of":"2026-07-26","body":"…","created_at":"2026-07-26T15:00:00Z","read_at":null,"highlights":null}""",
        )
        assertEquals(emptyList<String>(), letter.moments)
        assertNull(letter.nextStep)
        assertNull(letter.shareLine)
    }
}
