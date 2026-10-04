// Back returns you where you were (pathway docs/EXPERIENCE.md §7.2 #8): a
// screen's data held by its destination comes back as it was — the same
// value, not a fresh one — while its inputs are the same.
package org.nuruplace.member.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class HeldValuesTest {
    @Test fun `a held value comes back as it was, under its own name`() {
        val held = HeldValues()
        var made = 0
        val first = held.take("Home.scores", emptyList()) { made++; Any() }
        // The screen left and came back: the same value, not a new one.
        assertSame(first, held.take("Home.scores", emptyList()) { made++; Any() })
        assertEquals(1, made)
        // Another name is another value.
        assertNotSame(first, held.take("Home.verse", emptyList()) { made++; Any() })
        assertEquals(2, made)
    }

    @Test fun `new inputs make a new value — the level page for another level`() {
        val held = HeldValues()
        val level1 = held.take("AsyncContent.LevelDetail", listOf(1)) { "level 1" }
        assertEquals("level 1", held.take("AsyncContent.LevelDetail", listOf(1)) { "again" })
        assertEquals("level 2", held.take("AsyncContent.LevelDetail", listOf(2)) { "level 2" })
        assertEquals("level 1, anew", held.take("AsyncContent.LevelDetail", listOf(1)) { "level 1, anew" })
        assertEquals("level 1", level1)
    }
}
