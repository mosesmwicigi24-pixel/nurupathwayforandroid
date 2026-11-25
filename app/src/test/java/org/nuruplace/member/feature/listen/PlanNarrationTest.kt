package org.nuruplace.member.feature.listen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanNarrationTest {
    @Test fun `only a valid plan day part and revision can request audio`() {
        val source = PlanNarration("4c5fbfa1-d7e7-4d8f-8a89-a2970a95c904", 1, "word", "a".repeat(64))
        assertTrue(source.valid())
        assertFalse(source.copy(planId = "../other").valid())
        assertFalse(source.copy(day = 0).valid())
        assertFalse(source.copy(part = "reflection").valid())
        assertFalse(source.copy(version = "../bad").valid())
    }
}
