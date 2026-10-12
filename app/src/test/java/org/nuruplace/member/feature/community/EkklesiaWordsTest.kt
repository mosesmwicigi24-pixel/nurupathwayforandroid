package org.nuruplace.member.feature.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EkklesiaWordsTest {
    @Test fun `invitation is steady within a day and rotates across days`() {
        assertEquals(EkklesiaWords.invitation(40), EkklesiaWords.invitation(40))
        val seen = (0 until EkklesiaWords.INVITATIONS.size).map { EkklesiaWords.invitation(it) }.toSet()
        assertEquals(EkklesiaWords.INVITATIONS.size, seen.size)
        // Never out of range, whatever the day.
        assertTrue(EkklesiaWords.INVITATIONS.contains(EkklesiaWords.invitation(366)))
        assertTrue(EkklesiaWords.INVITATIONS.contains(EkklesiaWords.invitation(-1)))
    }

    @Test fun `no zero counts in the standing line`() {
        assertNull(EkklesiaWords.standing(0))
        assertEquals("1 intercessor already stands.", EkklesiaWords.standing(1))
        assertEquals("34 intercessors already stand.", EkklesiaWords.standing(34))
    }

    @Test fun `the member line names what waits today, urgency first`() {
        assertEquals("You have stood with every need today.", EkklesiaWords.memberLine(0, 0))
        assertEquals("1 need waits on you today.", EkklesiaWords.memberLine(1, 0))
        assertEquals("1 urgent need waits on you today.", EkklesiaWords.memberLine(1, 1))
        assertEquals("3 needs wait on you today, 1 urgent.", EkklesiaWords.memberLine(3, 1))
        assertEquals("3 needs wait on you today.", EkklesiaWords.memberLine(3, 0))
    }

    @Test fun `counts line drops zeros and repeats`() {
        assertNull(EkklesiaWords.countsLine(0, 0, 0))
        assertEquals("1 interceding", EkklesiaWords.countsLine(1, 1, 0))
        assertEquals("12 interceding · 40 prayers · 2 updates", EkklesiaWords.countsLine(12, 40, 2))
        assertEquals("1 update", EkklesiaWords.countsLine(0, 0, 1))
    }

    @Test fun `every door lands on the watch`() {
        assertEquals("prayer-room?tab=ekklesia", EkklesiaWords.ROUTE)
    }
}
