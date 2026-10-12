package org.nuruplace.member.feature.pathway

import org.junit.Assert.assertEquals
import org.junit.Test

/** The key verse is the verse's words with its reference under them — never
 *  the reference in quotes (Cycle 3 close walk E22). */
class KeyVerseTest {
    @Test fun `a bare reference is fetched for its words`() {
        assertEquals(KeyVerseContent.Fetch("James 1:5"), keyVerseContent("James 1:5"))
        assertEquals(KeyVerseContent.Fetch("Proverbs 3:5-6"), keyVerseContent(" Proverbs 3:5–6 "))
    }

    @Test fun `a long passage stays its reference`() {
        assertEquals(KeyVerseContent.Passage("John 1:1-18"), keyVerseContent("John 1:1-18"))
    }

    @Test fun `words with a trailing reference split into the two`() {
        assertEquals(
            KeyVerseContent.Words("Trust in the Lord with all your heart", "Proverbs 3:5"),
            keyVerseContent("“Trust in the Lord with all your heart” — Proverbs 3:5"),
        )
        assertEquals(
            KeyVerseContent.Words("For God so loved the world", "John 3:16"),
            keyVerseContent("For God so loved the world - John 3:16"),
        )
    }

    @Test fun `words alone stay words`() {
        assertEquals(KeyVerseContent.Words("Be still, and know that I am God.", null), keyVerseContent("\"Be still, and know that I am God.\""))
    }

    @Test fun `a fetched passage loses its inline verse numbers`() {
        assertEquals(
            "If any of you lacks wisdom, you should ask God.",
            keyVerseWords("5 If any of you lacks wisdom, you should ask God.", "James 1:5"),
        )
        assertEquals(
            "Do not merely listen to the word. Do what it says.",
            keyVerseWords("22 Do not merely listen to the word. 23 Do what it says.", "James 1:22-23"),
        )
        assertEquals(null, keyVerseWords("   ", "James 1:5"))
    }
}
