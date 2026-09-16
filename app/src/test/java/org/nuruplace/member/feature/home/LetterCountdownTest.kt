// Home "letter arrives" card — the countdown pill to Sunday 6 pm East Africa
// Time. Pure date logic, pinned here because the edge that matters (a Sunday
// evening AFTER the letter hour must point at NEXT Sunday, not say "Today")
// is exactly the kind of thing a glance at the card would not catch.
package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LetterCountdownTest {
    private val eat: ZoneId = ZoneId.of("Africa/Nairobi")

    // 2026-09-20 is a Sunday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, eat)

    @Test
    fun `Sunday morning is Today`() = assertEquals("Today", letterCountdownLabel(at(20, 10)))

    @Test
    fun `Sunday one minute before six is still Today`() = assertEquals("Today", letterCountdownLabel(at(20, 17, 59)))

    @Test
    fun `Sunday at six exactly has already been delivered so it points at next Sunday`() =
        assertEquals("In 7 days", letterCountdownLabel(at(20, 18)))

    @Test
    fun `Sunday evening after six points at next Sunday`() = assertEquals("In 7 days", letterCountdownLabel(at(20, 21)))

    @Test
    fun `Saturday is Tomorrow`() = assertEquals("Tomorrow", letterCountdownLabel(at(19, 9)))

    @Test
    fun `Monday is six days out`() = assertEquals("In 6 days", letterCountdownLabel(at(21, 8)))

    @Test
    fun `Wednesday is four days out`() = assertEquals("In 4 days", letterCountdownLabel(at(16, 14)))
}
