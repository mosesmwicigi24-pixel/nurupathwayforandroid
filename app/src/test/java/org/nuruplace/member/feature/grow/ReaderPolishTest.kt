// The pure decisions behind the reader polish: how long a page takes to read,
// how the text-size step cycles, and how a passage splits into verses so a
// long press lands on one of them.
package org.nuruplace.member.feature.grow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPolishTest {

    @Test fun `read time rounds to whole minutes and never says zero`() {
        assertEquals(1, ReadTime.minutes(0))
        assertEquals(1, ReadTime.minutes(90))
        assertEquals(3, ReadTime.minutes(500))
        assertEquals(6, ReadTime.minutes(1200))
    }

    @Test fun `word count ignores blank runs`() {
        assertEquals(4, ReadTime.words("  one two\n\nthree   four "))
        assertEquals(0, ReadTime.words(null))
    }

    @Test fun `text scale cycles through the three steps`() {
        assertEquals(1.0f, ReaderTextScale.next(0.9f), 0.0001f)
        assertEquals(1.15f, ReaderTextScale.next(1.0f), 0.0001f)
        assertEquals(0.9f, ReaderTextScale.next(1.15f), 0.0001f)
        assertEquals(1.15f, ReaderTextScale.next(1.02f), 0.0001f)
        assertEquals("Small", ReaderTextScale.label(0.9f))
        assertEquals("Large", ReaderTextScale.label(1.15f))
    }

    @Test fun `a passage splits at its verse numbers, lowercase openings included`() {
        val v = passageVerses("22 Do not merely listen to the word. 23 Anyone who listens to the word 24 and goes away", startVerse = 22)
        assertEquals(listOf("22", "23", "24"), v.map { it.number })
        assertEquals("Anyone who listens to the word", v[1].body)
        assertEquals("and goes away", v[2].body)
    }

    @Test fun `a numeral inside a verse is not a verse number`() {
        val v = passageVerses("40 Now the length of time the people lived in Egypt was 430 years. 41 At the end of the 430 years", startVerse = 40)
        assertEquals(listOf("40", "41"), v.map { it.number })
    }

    @Test fun `without a start verse the first candidate seeds the count`() {
        val v = passageVerses("16 For God so loved the world 17 For God did not send")
        assertEquals(listOf("16", "17"), v.map { it.number })
    }

    @Test fun `an unnumbered passage is one verse`() {
        val v = passageVerses("A sluggard's appetite is never filled.")
        assertEquals(1, v.size)
        assertNull(v[0].number)
    }
}
