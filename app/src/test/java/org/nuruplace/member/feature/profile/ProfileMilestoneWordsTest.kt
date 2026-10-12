package org.nuruplace.member.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary
import org.nuruplace.member.feature.pathway.JourneyState

/** Profile's MILESTONES tell the journey's one story (§3; Cycle 3 E13, the
 *  Cycle 4 walks; iOS 3137194): it said "Level 1 · in progress · Keep going"
 *  while every other screen said "Exam ready". */
class ProfileMilestoneWordsTest {
    private fun summary(status: LevelStatus, done: Int, total: Int = 10) = PathwaySummary(
        currentLevel = 1,
        levels = listOf(
            PathwayLevel(1, "Foundations of Faith", totalModules = total, completedModules = done, status = status, examPublished = true),
            PathwayLevel(2, "Inner Transformation", totalModules = 0, completedModules = 0, status = LevelStatus.LOCKED),
        ),
    )

    @Test fun `exam ready reads as every other screen does`() {
        val ada = JourneyState.derive(summary(LevelStatus.COMPLETED, done = 10))
        assertEquals("Level 1 · Exam ready" to "Take the Level 1 exam", profileMilestoneWords(1, ada))
    }

    @Test fun `mid-level, the modules count — never "in progress · Keep going"`() {
        val walking = JourneyState.derive(summary(LevelStatus.ACTIVE, done = 3))
        assertEquals("Level 1 · 3 of 10 modules", profileMilestoneWords(1, walking)?.first)
    }

    @Test fun `until the journey is known, or about another level, the row waits`() {
        assertNull(profileMilestoneWords(1, null))
        assertNull(profileMilestoneWords(2, JourneyState.derive(summary(LevelStatus.ACTIVE, done = 3))))
    }
}
