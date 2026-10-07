package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.LetterFigure
import org.nuruplace.member.data.net.LetterPhoto
import org.nuruplace.member.data.net.LetterScripture
import org.nuruplace.member.data.net.LetterSignedBy
import org.nuruplace.member.data.net.PastoralLetter

/** The editorial Sunday Letter (owner, 2026-10-07): its drop cap, and what
 *  each part says — and falls back to when a letter carries no v3 fields. */
class EditorialLetterTest {

    // ── The drop cap ────────────────────────────────────────────────────────

    @Test fun `the drop cap is the first letter, and the words run on from it`() {
        assertEquals(DropCap("T", "en lessons. You finished every one."), dropCapSplit("Ten lessons. You finished every one."))
        assertEquals(DropCap("Y", "ou marked two prayers answered."), dropCapSplit("  You marked two prayers answered."))
    }

    @Test fun `a one-letter first word keeps its space out of the line beside the cap`() {
        assertEquals(DropCap("I", "have been praying for you."), dropCapSplit("I have been praying for you."))
    }

    @Test fun `an opening quotation mark stays with the letter it opens`() {
        assertEquals(DropCap("“H", "e is faithful,” you wrote."), dropCapSplit("“He is faithful,” you wrote."))
    }

    @Test fun `no drop cap for a figure, a symbol, or nothing beside it`() {
        assertNull("1 · 0 lessons would split a number", dropCapSplit("10 lessons this week."))
        assertNull(dropCapSplit("— a quiet week."))
        assertNull(dropCapSplit(""))
        assertNull(dropCapSplit("   "))
        assertNull(dropCapSplit("A"))
        assertNull(dropCapSplit("“"))
    }

    @Test fun `the lines beside the cap are its depth, the rest runs full width`() {
        val rest = "en lessons. You finished every one of them this week, and the last was the hardest."
        // Three lines laid out beside the cap: ends after "You ", "week, ", and the end.
        val ends = listOf(rest.indexOf("finished"), rest.indexOf("and the"), rest.length)
        assertEquals(
            "en lessons. You finished every one of them this week," to "and the last was the hardest.",
            dropCapLines(rest, ends, depth = 2),
        )
        assertEquals(2, DROP_CAP_LINES)
    }

    @Test fun `a paragraph that fits beside the cap leaves nothing below`() {
        val rest = "ou marked two prayers answered."
        assertEquals(rest to "", dropCapLines(rest, listOf(rest.indexOf("prayers"), rest.length), depth = 2))
        assertEquals(rest to "", dropCapLines(rest, listOf(rest.length), depth = 2))
    }

    // ── The masthead, the dek, the paragraphs ───────────────────────────────

    @Test fun `the masthead names the issue and the letter's Sunday as sent`() {
        assertEquals("No. 6 · Sunday 4 October 2026", EditorialLetter.mastheadLine(6, "2026-10-04"))
        assertEquals("No. 6 · Sunday 4 October 2026", EditorialLetter.mastheadLine(6, "2026-10-04T00:00:00.000Z"))
    }

    @Test fun `a letter before v3 has its Sunday without a number`() {
        assertEquals("Sunday 4 October 2026", EditorialLetter.mastheadLine(null, "2026-10-04"))
        assertEquals("No. 3", EditorialLetter.mastheadLine(3, "not a date"))
        assertEquals("", EditorialLetter.mastheadLine(0, ""))
    }

    @Test fun `the dek says the minutes only when the server counted them`() {
        assertEquals("Your week, read back to you · 2 min", EditorialLetter.dek(2))
        assertEquals("Your week, read back to you", EditorialLetter.dek(null))
        assertEquals("Your week, read back to you", EditorialLetter.dek(0))
    }

    @Test fun `the paragraphs are v3's own`() {
        val l = PastoralLetter(body = "One.\n\nTwo.", paragraphs = listOf(" First. ", "", "Second."))
        assertEquals(listOf("First.", "Second."), EditorialLetter.paragraphs(l))
    }

    @Test fun `before v3 the body is split on blank lines, as the server splits it`() {
        val l = PastoralLetter(body = "Ten lessons.\nYou finished them.\n\n  The exam is waiting.  \n\n\n")
        assertEquals(listOf("Ten lessons. You finished them.", "The exam is waiting."), EditorialLetter.paragraphs(l))
        assertEquals(listOf("One paragraph."), EditorialLetter.paragraphs(PastoralLetter(body = "One paragraph.")))
        assertEquals(emptyList<String>(), EditorialLetter.paragraphs(PastoralLetter(body = "  ")))
    }

    // ── The photograph and the pull quote ───────────────────────────────────

    @Test fun `the photograph loads from an https address, else the theme art is the picture`() {
        val photo = LetterPhoto(id = "p", url = "https://images.unsplash.com/photo-1?w=1080", alt = "Trees", caption = "Trees. Chosen for a week of light.")
        assertEquals("https://images.unsplash.com/photo-1?w=1080", EditorialLetter.photoUrl(photo))
        assertEquals("Trees. Chosen for a week of light.", EditorialLetter.caption(photo))
        assertNull(EditorialLetter.photoUrl(null))
        assertNull(EditorialLetter.photoUrl(photo.copy(url = "")))
        assertNull(EditorialLetter.photoUrl(photo.copy(url = "http://images.example/p.jpg")))
        assertNull(EditorialLetter.caption(photo.copy(caption = " ")))
    }

    @Test fun `the share line is quoted once`() {
        assertEquals("“He is faithful.”", EditorialLetter.pullQuote(" He is faithful. "))
        assertEquals("“He is faithful.”", EditorialLetter.pullQuote("“He is faithful.”"))
    }

    // ── YOUR WEEK, IN GRACE ─────────────────────────────────────────────────

    @Test fun `a week with neither figures nor moments has no YOUR WEEK, IN GRACE`() {
        assertFalse(EditorialLetter.showWeekInGrace(PastoralLetter()))
        assertFalse(EditorialLetter.showWeekInGrace(PastoralLetter(highlightLines = listOf(" ", ""), figures = listOf(LetterFigure("", "")))))
    }

    @Test fun `moments alone show the card without its grid`() {
        val l = PastoralLetter(highlightLines = listOf("You prayed four mornings."))
        assertTrue(EditorialLetter.showWeekInGrace(l))
        assertEquals(emptyList<LetterFigure>(), EditorialLetter.figures(l))
    }

    @Test fun `the grid holds at most three figures, each with a value and a label`() {
        val l = PastoralLetter(
            figures = listOf(
                LetterFigure("10", "lessons finished"), LetterFigure("", "days in the Word"), LetterFigure("4", "reflections written"),
                LetterFigure("5 of 7", "days in the Word"), LetterFigure("2", "prayers answered"),
            ),
        )
        assertEquals(listOf("10", "4", "5 of 7"), EditorialLetter.figures(l).map { it.value })
        assertTrue(EditorialLetter.showWeekInGrace(l))
    }

    // ── The verse ───────────────────────────────────────────────────────────

    @Test fun `the verse in full, with its reference and version`() {
        val l = PastoralLetter(scriptureRef = "Philippians 4:6", scripture = LetterScripture("Philippians 4:6", "Do not be anxious about anything…", "ESV"))
        assertEquals("Do not be anxious about anything…", EditorialLetter.verseText(l.scripture))
        assertEquals("Philippians 4:6 · ESV", EditorialLetter.verseKicker(l))
    }

    @Test fun `with no words, the reference alone, as before`() {
        val noText = PastoralLetter(scriptureRef = "Philippians 1:6", scripture = LetterScripture("Philippians 1:6", null, null))
        assertNull(EditorialLetter.verseText(noText.scripture))
        assertEquals("Philippians 1:6", EditorialLetter.verseKicker(noText))
        val v2 = PastoralLetter(scriptureRef = " Philippians 1:6 ")
        assertEquals("Philippians 1:6", EditorialLetter.verseKicker(v2))
        assertNull(EditorialLetter.verseKicker(PastoralLetter()))
    }

    // ── The signature and the footer ────────────────────────────────────────

    @Test fun `the letter is signed by whom the server says`() {
        val by = LetterSignedBy("Pastor Moses", "Nuru Place")
        assertEquals("Pastor Moses", EditorialLetter.signerName(by))
        assertEquals("Nuru Place", EditorialLetter.signerRole(by))
    }

    @Test fun `with no signer, the church's name in type`() {
        assertNull(EditorialLetter.signerName(null))
        assertNull(EditorialLetter.signerName(LetterSignedBy(" ", "")))
        assertEquals("Nuru Place", EditorialLetter.signerRole(null))
    }

    @Test fun `the reply goes to the member's own pastor, whoever signed`() {
        // The row opens the member's pastoral thread — their ASSIGNED pastor —
        // so it never names the signer (owner, 2026-10-07).
        assertEquals("Write back to your pastor", EditorialLetter.WRITE_BACK)
        assertFalse(EditorialLetter.WRITE_BACK.contains("Moses"))
    }

    @Test fun `the next step's pill begins a lesson and opens anything else`() {
        assertEquals("Begin", EditorialLetter.stepVerb("module"))
        assertEquals("Open", EditorialLetter.stepVerb("pathway"))
        assertEquals("Open", EditorialLetter.stepVerb("something-new"))
    }

    @Test fun `last week's letter is the newest with an earlier Sunday`() {
        val now = PastoralLetter(letterId = "c", weekOf = "2026-10-04", title = "Now")
        val last = PastoralLetter(letterId = "b", weekOf = "2026-09-27", title = "Two prayers, answered")
        val older = PastoralLetter(letterId = "a", weekOf = "2026-09-13", title = "Older")
        val newer = PastoralLetter(letterId = "d", weekOf = "2026-10-11", title = "Newer")
        assertEquals(last, EditorialLetter.previousOf(listOf(newer, now, last, older), now))
        assertEquals("Last week: Two prayers, answered", EditorialLetter.previousLabel(last, now))
        assertNull(EditorialLetter.previousOf(listOf(now, newer), now))
        assertNull(EditorialLetter.previousOf(listOf(now), now))
    }

    @Test fun `an older letter is named by its own Sunday, never "last week"`() {
        val now = PastoralLetter(letterId = "c", weekOf = "2026-10-04", title = "Now")
        assertEquals("Sun 13 Sep: Older", EditorialLetter.previousLabel(PastoralLetter(weekOf = "2026-09-13", title = "Older"), now))
        assertEquals("Sun 28 Dec 2025: Last year's", EditorialLetter.previousLabel(PastoralLetter(weekOf = "2025-12-28", title = "Last year's"), now))
        assertEquals("Last week: Your Sunday Letter", EditorialLetter.previousLabel(PastoralLetter(weekOf = "2026-09-27", title = null), now))
    }

    // ── The PDF ─────────────────────────────────────────────────────────────

    @Test fun `the PDF's path is resolved against the API's host — one v1, not two`() {
        val path = "/v1/me/letters/a68526c0-a54a-4461-aa25-f1fd5c1e1a76/pdf"
        assertEquals(
            "http://10.0.2.2:8080/v1/me/letters/a68526c0-a54a-4461-aa25-f1fd5c1e1a76/pdf",
            EditorialLetter.pdfUrl("http://10.0.2.2:8080/v1/", path),
        )
        assertEquals("https://pathway.nuruplace.org/v1/me/letters/x/pdf", EditorialLetter.pdfUrl("https://pathway.nuruplace.org/v1/", "/v1/me/letters/x/pdf"))
        assertEquals("https://pathway.nuruplace.org/v1/me/letters/x/pdf", EditorialLetter.pdfUrl("https://pathway.nuruplace.org/v1", "/v1/me/letters/x/pdf"))
        // A relative path sits under the API base.
        assertEquals("https://pathway.nuruplace.org/v1/me/letters/x/pdf", EditorialLetter.pdfUrl("https://pathway.nuruplace.org/v1/", "me/letters/x/pdf"))
    }

    @Test fun `the member's token never leaves the API's own host`() {
        val base = "https://pathway.nuruplace.org/v1/"
        assertNull(EditorialLetter.pdfUrl(base, "https://elsewhere.example/letter.pdf"))
        assertNull(EditorialLetter.pdfUrl(base, "//elsewhere.example/letter.pdf"))
        assertNull(EditorialLetter.pdfUrl(base, "http://pathway.nuruplace.org/v1/me/letters/x/pdf"))
        assertNull(EditorialLetter.pdfUrl(base, "https://pathway.nuruplace.org:8443/v1/me/letters/x/pdf"))
        assertNull(EditorialLetter.pdfUrl(base, null))
        assertNull(EditorialLetter.pdfUrl(base, "  "))
    }

    @Test fun `the PDF is named as the server names it, and its failure said in §4's words`() {
        assertEquals("sunday-letter-2026-10-04.pdf", EditorialLetter.pdfFileName("2026-10-04"))
        assertEquals("sunday-letter-letter.pdf", EditorialLetter.pdfFileName("../../x"))
        assertEquals("You're offline — the PDF needs a connection.", EditorialLetter.pdfErrorLine(offline = true))
        assertEquals("The PDF isn't available right now — your letter is all here.", EditorialLetter.pdfErrorLine(offline = false))
    }

    // ── A letter before v3 (pathway#512) ─────────────────────────────────────

    @Test fun `a letter with no v3 fields keeps everything it has`() {
        val v2 = PastoralLetter(
            letterId = "l1", weekOf = "2026-07-26", title = "A week of small beginnings", salutation = "Dear Ada,",
            body = "You started.\n\nKeep going.", scriptureRef = "Zechariah 4:10", imageKey = "seed",
            highlightLines = listOf("You started First Steps."),
        )
        assertEquals("Sunday 26 July 2026", EditorialLetter.mastheadLine(v2.issueNo, v2.weekOf))
        assertEquals("Your week, read back to you", EditorialLetter.dek(v2.readingMinutes))
        assertEquals(listOf("You started.", "Keep going."), EditorialLetter.paragraphs(v2))
        assertNull("the theme art is the picture", EditorialLetter.photoUrl(v2.photo))
        assertEquals("seed", v2.artKey)
        assertEquals("Zechariah 4:10", EditorialLetter.verseKicker(v2))
        assertTrue(EditorialLetter.showWeekInGrace(v2))
        assertNull(EditorialLetter.signerName(v2.signedBy))
        assertNull("no PDF to keep", EditorialLetter.pdfUrl("https://pathway.nuruplace.org/v1/", v2.pdfUrl))
    }
}
