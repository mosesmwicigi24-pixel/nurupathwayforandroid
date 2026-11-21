package org.nuruplace.member.feature.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.Devotional
import org.nuruplace.member.data.net.LetterScripture
import org.nuruplace.member.data.net.LetterSignedBy
import org.nuruplace.member.data.net.ModuleDetail
import org.nuruplace.member.data.net.PastoralLetter
import org.nuruplace.member.data.net.PlanSegment
import org.nuruplace.member.feature.grow.devotionalListenPieces
import org.nuruplace.member.feature.grow.planPartListenPieces
import org.nuruplace.member.feature.grow.planPartPassagesToFetch
import org.nuruplace.member.feature.home.EditorialLetter
import org.nuruplace.member.feature.pathway.moduleListenPieces

/** What each reader reads aloud, in the order its page shows it. */
class ReaderListenTest {
    private fun read(pieces: List<ListenPiece>) = ListenScript.paragraphs(pieces)

    @Test fun `a plan's Word part reads the title, the passage and the teaching`() {
        val group = listOf(
            PlanSegment("s1", kind = "scripture", reference = "Proverbs 13:4", content = "4 A sluggard’s appetite is never filled"),
            PlanSegment("s2", kind = "devotional", content = "Diligence is faith at work.\n\nStart small."),
            PlanSegment("s3", kind = "reading", content = "James 1:22-25; Romans 12:11"),
        )
        assertEquals(
            listOf(
                "Building well.",
                "Proverbs 13, verse 4.",
                "A sluggard’s appetite is never filled",
                "Diligence is faith at work.",
                "Start small.",
                "Go deeper.",
                "James 1, verses 22 to 25",
                "Romans 12, verse 11",
            ),
            read(planPartListenPieces("word", group, "Building well", passages = emptyMap())),
        )
    }

    @Test fun `a bare reference reads its fetched passage, or just the reference until it lands`() {
        val group = listOf(PlanSegment("s1", kind = "scripture", reference = "John 3:16"))
        assertEquals(listOf("John 3:16"), planPartPassagesToFetch("word", group))
        assertEquals(listOf("John 3, verse 16."), read(planPartListenPieces("word", group, null, emptyMap())))
        assertEquals(
            listOf("John 3, verse 16.", "For God so loved the world"),
            read(planPartListenPieces("word", group, null, mapOf("John 3:16" to "16 For God so loved the world"))),
        )
    }

    @Test fun `the Respond part reads its prayers, and a film part reads nothing over the film`() {
        val prayer = listOf(PlanSegment("p", kind = "devotional", title = "Pray", content = "Lord, make me faithful."))
        assertEquals(listOf("Lord, make me faithful."), read(planPartListenPieces("respond", prayer, null, emptyMap())))
        val film = listOf(PlanSegment("v", kind = "video", title = "Talk", content = "Keynotes"))
        assertTrue(read(planPartListenPieces("watch", film, null, emptyMap())).isEmpty())
        assertTrue(planPartPassagesToFetch("watch", film).isEmpty())
    }

    @Test fun `the devotional ends on its question`() {
        val d = Devotional(
            devotionalId = "d", title = "Planted", scriptureRef = "Psalm 1:3",
            scriptureText = "3 He is like a tree planted by streams", body = "Roots first.", reflectionPrompt = "Where are you planted?",
        )
        assertEquals(
            listOf("Planted.", "Psalm 1, verse 3.", "He is like a tree planted by streams", "Roots first.", "Reflection.", "Where are you planted?"),
            read(devotionalListenPieces(d)),
        )
    }

    @Test fun `the letter reads its title, salutation, body, verse and signature`() {
        val l = PastoralLetter(
            title = "A week of grace", salutation = "Dear Wanjiru,",
            paragraphs = listOf("You came back.", "God noticed."),
            scripture = LetterScripture(ref = "Philippians 4:6", text = "Do not be anxious about anything"),
            signedBy = LetterSignedBy(name = "Moses", role = "Senior Pastor"),
        )
        assertEquals(
            listOf(
                "A week of grace.", "Dear Wanjiru,", "You came back.", "God noticed.",
                "Philippians 4, verse 6.", "Do not be anxious about anything", "With grace, Moses, Senior Pastor.",
            ),
            read(EditorialLetter.listenPieces(l)),
        )
    }

    @Test fun `a letter without a signer is signed by the church`() {
        val words = read(EditorialLetter.listenPieces(PastoralLetter(body = "One.")))
        assertEquals(listOf("One.", "With grace, Nuru Place."), words)
    }

    @Test fun `a lesson numbers its sections only when it has more than one`() {
        val one = ModuleDetail(moduleId = "m", levelNumber = 1, moduleSequenceNumber = 1, title = "Prayer", lessonContent = "## Why pray\n\nBecause He listens.")
        assertEquals(listOf("Prayer.", "Why pray.", "Because He listens."), read(moduleListenPieces(one)))

        val two = one.copy(contentPages = listOf("Part one.", "Part two."), keyVerses = listOf("“Pray continually.” — 1 Thessalonians 5:17"))
        assertEquals(
            listOf("Prayer.", "Key verse.", "“Pray continually.” — 1 Thessalonians 5, verse 17", "Section 1.", "Part one.", "Section 2.", "Part two."),
            read(moduleListenPieces(two)),
        )
    }
}
