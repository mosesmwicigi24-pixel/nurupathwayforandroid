// The reference parser behind "scripture woven into the plans": what counts as
// a citation in prose, how an authored Go Deeper line splits, and the exact
// form handed to GET /scripture. Pure logic — nothing here touches the network.
package org.nuruplace.member.feature.grow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptureRefsTest {

    @Test fun `detects a citation inside prose`() {
        val m = ScriptureRefs.detect("""as Scripture says, "faith without works is dead" (James 2:17), and desire""")
        assertEquals(listOf("James 2:17"), m.map { it.reference })
    }

    @Test fun `numbered books win over their bare names`() {
        assertEquals(
            listOf("1 John 4:8", "John 3:16"),
            ScriptureRefs.detect("Read 1 John 4:8 and John 3:16 tonight.").map { it.reference },
        )
    }

    @Test fun `ranges keep their span with a plain hyphen`() {
        assertEquals(listOf("James 1:22-25"), ScriptureRefs.detect("See James 1:22–25.").map { it.reference })
        assertEquals("Ephesians 2:8-9", ScriptureRefs.normalize(" Ephesians 2:8 – 9 "))
    }

    @Test fun `ignores times and ordinary numbers`() {
        assertTrue(ScriptureRefs.detect("It comes at 2:17 in the morning, on March 3.").isEmpty())
    }

    @Test fun `splits a semicolon list and normalises each piece`() {
        assertEquals(listOf("Proverbs 13:4", "James 1:22-25"), ScriptureRefs.split("Proverbs 13:4; James 1:22–25"))
    }

    @Test fun `comma splits only when both sides name a book`() {
        assertEquals(listOf("Matthew 5:3", "Luke 6:20"), ScriptureRefs.split("Matthew 5:3, Luke 6:20"))
        assertEquals(listOf("Genesis 1:1, 3"), ScriptureRefs.split("Genesis 1:1, 3"))
    }

    @Test fun `an authored note survives the split untouched`() {
        assertEquals(
            listOf("Read the whole chapter slowly", "Psalm 23:1-6"),
            ScriptureRefs.split("Read the whole chapter slowly\nPsalm 23:1-6"),
        )
        assertFalse(ScriptureRefs.isReference("Read the whole chapter slowly"))
        assertTrue(ScriptureRefs.isReference("Psalm 23:1-6"))
    }

    @Test fun `verse count reads the span off the reference`() {
        assertEquals(1, ScriptureRefs.verseCount("Proverbs 13:4"))
        assertEquals(4, ScriptureRefs.verseCount("James 1:22–25"))
        assertEquals(6, ScriptureRefs.verseCount("Psalm 23:1-6"))
        assertNull(ScriptureRefs.verseCount("John 3:16-4:2"))
        assertNull(ScriptureRefs.verseCount("Read the whole chapter"))
    }

    @Test fun `short passages open without a tap`() {
        assertTrue(ScriptureRefs.opensByDefault("James 1:22-25"))
        assertFalse(ScriptureRefs.opensByDefault("Psalm 23:1-6"))
        assertFalse(ScriptureRefs.opensByDefault("John 3:16-4:2"))
    }

    @Test fun `chapter prefix builds a single verse's reference`() {
        assertEquals("James 1", ScriptureRefs.chapterPrefix("James 1:22-25"))
        assertEquals("1 Peter 2", ScriptureRefs.chapterPrefix("1 Peter 2:9"))
        assertNull(ScriptureRefs.chapterPrefix("not a reference"))
    }

    @Test fun `candidate verse numbers are the inline tokens that open a word`() {
        val text = "22 Do not merely listen to the word. 23 Anyone who listens 24 and, after looking at himself, goes away"
        assertEquals(listOf("22", "23", "24"), ScriptureRefs.verseNumber.findAll(text).map { it.value }.toList())
        assertEquals(22, ScriptureRefs.startVerse("James 1:22-25"))
        assertNull(ScriptureRefs.startVerse("not a reference"))
    }
}
