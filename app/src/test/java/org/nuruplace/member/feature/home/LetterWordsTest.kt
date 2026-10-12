package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The Sunday Letter's quiet lines (owner, 2026-10-07: colour option A) —
 *  the same words as iOS HomeLetterWords. */
class LetterWordsTest {
    private val today = LocalDate.of(2026, 10, 7)

    @Test fun `a read letter says its Sunday, never shifted by a time zone`() {
        assertEquals("Sun 4 Oct · Read again", LetterWords.readLine("2026-10-04", today))
        // week_of as a midnight timestamp is still that calendar day.
        assertEquals("Sun 4 Oct · Read again", LetterWords.readLine("2026-10-04T00:00:00.000Z", today))
        // Another year says its year.
        assertEquals("Sun 28 Dec 2025 · Read again", LetterWords.readLine("2025-12-28", today))
        assertEquals("Read again", LetterWords.readLine("", today))
    }

    @Test fun `before a letter exists, the countdown joins its line`() {
        assertEquals("Written for your week · In 4 days", LetterWords.arrivalLine("In 4 days"))
    }
}
