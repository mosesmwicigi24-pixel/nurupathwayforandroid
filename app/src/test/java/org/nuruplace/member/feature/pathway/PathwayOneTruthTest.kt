// Pathway tells one truth (final walk C8, C9): one measure on Pathway, the
// level page and Map; the exam named beside the percent it explains; a first
// day said once; and a finished lesson points to the exam once it's next.
package org.nuruplace.member.feature.pathway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary

class PathwayOneTruthTest {
    private fun summary(done: Int, total: Int = 10, status: LevelStatus = LevelStatus.ACTIVE) = PathwaySummary(
        currentLevel = 1,
        levels = (1..6).map { n ->
            if (n == 1) PathwayLevel(1, "Foundations of Faith", totalModules = total, completedModules = done, status = status, examPublished = true)
            else PathwayLevel(n, "Level $n", status = LevelStatus.LOCKED)
        },
    )

    private fun trail(done: Int, total: Int = 10) = (1..total).map { i ->
        LevelModule(
            moduleId = "m$i", levelNumber = 1, moduleSequenceNumber = i, title = "Module $i", completed = i <= done,
            status = when { i <= done -> ModuleStatus.COMPLETED; i == done + 1 -> ModuleStatus.NEXT; else -> ModuleStatus.LOCKED },
        )
    } + LevelModule(
        moduleId = "exam1", levelNumber = 1, moduleSequenceNumber = 99, title = "Level 1 Review",
        evaluationKind = "exit_exam", completed = false, status = if (done >= total) ModuleStatus.NEXT else ModuleStatus.LOCKED,
    )

    @Test fun `every lesson done, the exam ahead — Pathway's bar is the level page's and Map's 91 percent (Android #20)`() {
        val s = summary(done = 10, status = LevelStatus.COMPLETED)
        val j = JourneyState.derive(s, trail(done = 10))!!
        assertEquals(JourneyStage.EXAM_READY, j.stage)
        // The one measure all three screens draw: the exam is the last step.
        assertEquals(91, levelPercent(s.levels.first(), j))
    }

    @Test fun `the level page names the exam beside the percent it explains (iOS #13, in iOS LevelProgressWords' words)`() {
        assertEquals("10 of 10 modules · the Level 1 exam is next", levelPageProgressLine(1, done = 10, total = 10, pct = 91))
        // Passed: the count alone. Midway: the count alone. A first day: what lies ahead.
        assertEquals("10 of 10 modules", levelPageProgressLine(1, done = 10, total = 10, pct = 100))
        assertEquals("3 of 10 modules", levelPageProgressLine(1, done = 3, total = 10, pct = 27))
        assertEquals("10 modules", levelPageProgressLine(1, done = 0, total = 10, pct = 0))
        assertEquals("Level 2 is being prepared", levelPageProgressLine(2, done = 0, total = 0, pct = 0))
    }

    @Test fun `a first day says "10 modules" once — no "Only 10 modules to complete this level" under it (Android #25)`() {
        assertEquals(0, remainingModules(JourneyStage.LEARNING, lessonsDone = 0, lessonCount = 10))
        assertEquals(7, remainingModules(JourneyStage.LEARNING, lessonsDone = 3, lessonCount = 10))
        assertEquals(1, remainingModules(JourneyStage.LEARNING, lessonsDone = 9, lessonCount = 10))
        // Beyond learning ("1 module left" beside "Exam ready" would contradict it).
        assertEquals(0, remainingModules(JourneyStage.EXAM_READY, lessonsDone = 10, lessonCount = 10))
        assertEquals(0, remainingModules(null, lessonsDone = 3, lessonCount = 10))
    }

    @Test fun `a finished lesson points to the exam once every lesson in its level is done (C9, Android #15)`() {
        val ready = JourneyState.derive(summary(done = 10, status = LevelStatus.COMPLETED), trail(done = 10))
        val step = lessonExamStep(ready, levelNumber = 1, completed = true)!!
        assertEquals("Take the Level 1 exam", step.title)
        assertEquals("Begin the exam", step.action?.label)
        assertEquals(JourneyDestination.Exam(1), step.action?.destination)
        // Not for a lesson still being read, nor another level's lesson.
        assertNull(lessonExamStep(ready, levelNumber = 1, completed = false))
        assertNull(lessonExamStep(ready, levelNumber = 2, completed = true))
        // Not while lessons remain, and not without the journey (a read that failed).
        val learning = JourneyState.derive(summary(done = 4), trail(done = 4))
        assertNull(lessonExamStep(learning, levelNumber = 1, completed = true))
        assertNull(lessonExamStep(null, levelNumber = 1, completed = true))
    }
}
