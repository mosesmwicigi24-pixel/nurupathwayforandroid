package org.nuruplace.member.feature.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Read aloud says, and where it cuts — the engine itself needs a device. */
class ListenScriptTest {

    @Test fun `a heading is read once with a pause after it`() {
        assertEquals(listOf("Building Successful."), ListenScript.paragraphs(listOf(ListenPiece.Heading("Building Successful"))))
    }

    @Test fun `prose splits at blank lines and runs its own lines together`() {
        val p = ListenScript.paragraphs(listOf(ListenPiece.Prose("God sees you.\nHe knows you.\n\nWalk with Him.")))
        assertEquals(listOf("God sees you. He knows you.", "Walk with Him."), p)
    }

    @Test fun `markdown marks, links, images and rules are not read out`() {
        val md = "## The **first** step\n\n> Trust *Him* fully\n\n- one thing\n- [another](https://x.org)\n\n---\n\n![photo](a.png)"
        assertEquals(
            listOf("The first step.", "Trust Him fully", "one thing. another."),
            ListenScript.paragraphs(listOf(ListenPiece.Prose(md))),
        )
    }

    @Test fun `a table reads row by row, its cells paused, its rule silent`() {
        val md = "| Day | Reading |\n|---|:---:|\n| Monday | Psalm 23:1 |"
        assertEquals(
            listOf("Day, Reading. Monday, Psalm 23, verse 1."),
            ListenScript.paragraphs(listOf(ListenPiece.Prose(md))),
        )
    }

    @Test fun `references in prose are read as words, and only real books count`() {
        assertEquals("As Romans 8, verse 28 says", ListenScript.speakable("As Romans 8:28 says"))
        assertEquals("Read John 15, verses 1 to 4 today", ListenScript.speakable("Read John 15:1-4 today"))
        assertEquals("1 John 4, verse 8", ListenScript.speakable("1 John 4:8"))
        assertEquals("The service starts at 10:30", ListenScript.speakable("The service starts at 10:30"))
    }

    @Test fun `a cross-chapter reference names the second chapter`() {
        assertEquals("John 3, verse 16 to chapter 4, verse 2", ListenScript.naturalReference("John 3:16–4:2"))
        assertEquals("Read the whole chapter", ListenScript.naturalReference("Read the whole chapter"))
    }

    @Test fun `a passage is its reference then its verses without their numbers`() {
        val p = ListenScript.paragraphs(
            listOf(ListenPiece.Scripture("22 Do not merely listen to the word. 23 Anyone who listens", "James 1:22-23", 22)),
        )
        assertEquals(listOf("James 1, verses 22 to 23.", "Do not merely listen to the word. Anyone who listens"), p)
    }

    @Test fun `a long paragraph is cut at sentence ends under the limit`() {
        val sentence = "Grace is enough for today."
        val text = List(10) { sentence }.joinToString(" ")
        val chunks = ListenScript.chunk(text, maxChars = 60)
        assertTrue(chunks.all { it.length <= 60 })
        assertEquals(text, chunks.joinToString(" "))
        assertEquals(5, chunks.size)
    }

    @Test fun `one sentence longer than the limit is cut at a space`() {
        val chunks = ListenScript.chunk("a ".repeat(50).trim(), maxChars = 20)
        assertTrue(chunks.all { it.length <= 20 && !it.startsWith(" ") })
    }

    @Test fun `nothing speakable means no paragraphs`() {
        assertTrue(ListenScript.paragraphs(listOf(ListenPiece.Prose("---\n\n![x](y)"), ListenPiece.Heading("  "))).isEmpty())
        assertNull(ListenScript.speakable("***"))
    }

    @Test fun `the same words give the same key`() {
        assertEquals(ListenScript.key(listOf("a", "b")), ListenScript.key(listOf("a", "b")))
        assertTrue(ListenScript.key(listOf("a", "b")) != ListenScript.key(listOf("ab")))
    }

    @Test fun `speeds cycle and read as multipliers`() {
        assertEquals(1.25f, ListenSpeed.next(1.0f))
        assertEquals(0.8f, ListenSpeed.next(1.5f))
        assertEquals(1.0f, ListenSpeed.next(0.9f)) // an odd value moves on from its nearest step
        assertEquals("1×", ListenSpeed.label(1.0f))
        assertEquals("1.25×", ListenSpeed.label(1.25f))
        assertEquals("0.8×", ListenSpeed.label(0.8f))
    }
}
