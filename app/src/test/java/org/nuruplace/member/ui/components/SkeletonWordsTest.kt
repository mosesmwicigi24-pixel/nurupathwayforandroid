// Final walk M4's class: a loading shape is read as one thing, in iOS's words.
package org.nuruplace.member.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SkeletonWordsTest {
    @Test fun `each loading shape speaks iOS's label`() {
        assertEquals("Your week, loading", SkeletonWords.WEEK)
        assertEquals("Your level, loading", SkeletonWords.LEVEL)
        assertEquals("Your badges, loading", SkeletonWords.BADGES)
        assertEquals("Your certificates, loading", SkeletonWords.CERTIFICATES)
    }
}
