// Today's rhythm tiles in plain words (pathway docs/EXPERIENCE.md §8.2 #20):
// what each tile means — a prayer prayed, Scripture read, a reflection written
// — said as it stands today, never "Pending".
package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RhythmTileWordsTest {
    @Test fun `done says what was done, not yet says not yet`() {
        assertEquals("Prayed", RhythmTileWords.PRAYER.status(true))
        assertEquals("Read", RhythmTileWords.WORD.status(true))
        assertEquals("Written", RhythmTileWords.REFLECTION.status(true))
        RhythmTileWords.entries.forEach { assertEquals("Not yet", it.status(false)) }
    }

    @Test fun `the tiles keep their names, in the day's order, and never say Pending`() {
        assertEquals(listOf("Prayer", "Word", "Reflection"), RhythmTileWords.entries.map { it.label })
        RhythmTileWords.entries.forEach { w ->
            listOf(true, false).forEach { assertFalse(w.status(it).contains("pending", ignoreCase = true)) }
        }
    }
}
