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
        // A v2-only response (before pathway#512): every v3 field absent, so
        // the editorial page falls back to what v2 carries.
        assertNull(letter.issueNo)
        assertNull(letter.readingMinutes)
        assertEquals(emptyList<String>(), letter.paragraphs)
        assertNull(letter.scripture)
        assertNull(letter.photo)
        assertEquals(emptyList<LetterFigure>(), letter.figures)
        assertNull(letter.signedBy)
        assertNull(letter.pdfUrl)
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
        assertEquals(1, letter.issueNo)
        assertEquals("ESV", letter.scripture?.version)
        assertEquals("1732106846688-3bac122e4f97", letter.photo?.id)
        assertEquals("/v1/me/letters/5528cf92-a9d8-4215-9974-17181926ca6f/pdf", letter.pdfUrl)
    }

    @Test fun `the editorial letter decodes as the local API sends it`() {
        // GET /me/letters/latest for build7, verbatim from the local API
        // running pathway#512 (2026-10-07) — the letter the editorial page
        // was checked against on the emulator.
        val res = json.decodeFromString<LatestLetterRes>(
            """{"letter":{"letter_id":"a68526c0-a54a-4461-aa25-f1fd5c1e1a76","week_of":"2026-10-04","title":"The two prayers you marked answered","salutation":"Dear Builder,","theme":"light","image_key":"dawn","body":"You marked two prayers answered this week. That is worth stopping for.","scripture_ref":"Philippians 4:6","highlights":[],"next_step":null,"share_line":null,"created_at":"2026-10-04T15:00:00.000Z","read_at":"2026-10-07T05:03:06.434Z","issue_no":1,"reading_minutes":1,"paragraphs":["You marked two prayers answered this week. That is worth stopping for."],"scripture":{"ref":"Philippians 4:6","text":"Do not be anxious about anything, but in everything by prayer and supplication with thanksgiving let your requests be made known to God.","version":"ESV"},"photo":{"id":"1580687774725-4e23db308efc","url":"https://images.unsplash.com/photo-1580687774725-4e23db308efc?auto=format&fit=crop&w=1080&q=70","alt":"Trees in the hazy savanna light","caption":"Trees in the hazy savanna light. Chosen for a week of light breaking through."},"figures":[],"signed_by":{"name":"Pastor Moses","role":"Nuru Place"},"pdf_url":"/v1/me/letters/a68526c0-a54a-4461-aa25-f1fd5c1e1a76/pdf"}}""",
        )
        val letter = res.letter!!
        assertEquals(1, letter.issueNo)
        assertEquals(1, letter.readingMinutes)
        assertEquals(listOf("You marked two prayers answered this week. That is worth stopping for."), letter.paragraphs)
        assertEquals(
            LetterScripture(
                ref = "Philippians 4:6",
                text = "Do not be anxious about anything, but in everything by prayer and supplication with thanksgiving let your requests be made known to God.",
                version = "ESV",
            ),
            letter.scripture,
        )
        assertEquals("https://images.unsplash.com/photo-1580687774725-4e23db308efc?auto=format&fit=crop&w=1080&q=70", letter.photo?.url)
        assertEquals("Trees in the hazy savanna light", letter.photo?.alt)
        assertEquals("Trees in the hazy savanna light. Chosen for a week of light breaking through.", letter.photo?.caption)
        assertEquals(emptyList<LetterFigure>(), letter.figures)
        assertEquals(LetterSignedBy("Pastor Moses", "Nuru Place"), letter.signedBy)
        assertEquals("/v1/me/letters/a68526c0-a54a-4461-aa25-f1fd5c1e1a76/pdf", letter.pdfUrl)
        // …and v2's fields beside them, unchanged.
        assertEquals("Dear Builder,", letter.displaySalutation)
        assertNull(letter.shareLine)
    }

    @Test fun `the archive decodes as the local API sends it`() {
        // GET /me/letters for build7, verbatim (2026-10-07).
        val res = json.decodeFromString<Envelope<PastoralLetter>>(
            """{"data":[{"letter_id":"a68526c0-a54a-4461-aa25-f1fd5c1e1a76","week_of":"2026-10-04","title":"The two prayers you marked answered","salutation":"Dear Builder,","theme":"light","image_key":"dawn","body":"You marked two prayers answered this week. That is worth stopping for.","scripture_ref":"Philippians 4:6","highlights":[],"next_step":null,"share_line":null,"created_at":"2026-10-04T15:00:00.000Z","read_at":"2026-10-07T05:03:06.434Z","issue_no":1,"reading_minutes":1,"paragraphs":["You marked two prayers answered this week. That is worth stopping for."],"scripture":{"ref":"Philippians 4:6","text":"Do not be anxious about anything, but in everything by prayer and supplication with thanksgiving let your requests be made known to God.","version":"ESV"},"photo":{"id":"1580687774725-4e23db308efc","url":"https://images.unsplash.com/photo-1580687774725-4e23db308efc?auto=format&fit=crop&w=1080&q=70","alt":"Trees in the hazy savanna light","caption":"Trees in the hazy savanna light. Chosen for a week of light breaking through."},"figures":[],"signed_by":{"name":"Pastor Moses","role":"Nuru Place"},"pdf_url":"/v1/me/letters/a68526c0-a54a-4461-aa25-f1fd5c1e1a76/pdf"}]}""",
        )
        assertEquals(1, res.data.size)
        assertEquals("2026-10-04", res.data[0].weekOf)
        assertEquals(1, res.data[0].issueNo)
    }

    @Test fun `figures and a verse without its words decode`() {
        val letter = json.decodeFromString<PastoralLetter>(
            """{"letter_id":"l2","week_of":"2026-10-04","body":"…","figures":[{"value":"5 of 7","label":"days in the Word"},{"value":"2","label":"prayers answered"}],"scripture":{"ref":"Philippians 1:6","text":null,"version":null},"photo":null,"signed_by":null,"pdf_url":null}""",
        )
        assertEquals(listOf(LetterFigure("5 of 7", "days in the Word"), LetterFigure("2", "prayers answered")), letter.figures)
        assertEquals(LetterScripture("Philippians 1:6", null, null), letter.scripture)
        assertNull(letter.photo)
        assertNull(letter.signedBy)
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
