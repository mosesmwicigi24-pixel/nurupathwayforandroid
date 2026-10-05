// Cycle 3's closing walk, B2 and B3: an unopened module is "Up next" and
// "Start" — never "In progress"/"Resume" (`next` is the next to do; a module's
// progress is only 0 or 100) — and a level page's badge follows the journey.
package org.nuruplace.member.feature.pathway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.PathwayLevel

class PathwayWordsTest {
    private fun module(status: ModuleStatus, exam: Boolean = false, available: Boolean? = null) = LevelModule(
        moduleId = "m", levelNumber = 1, moduleSequenceNumber = 1, title = "God & His Nature",
        evaluationKind = if (exam) "exit_exam" else "quiz", status = status,
        completed = status == ModuleStatus.COMPLETED, examAvailable = available,
    )

    @Test fun `an unopened next module is Up next and Start — never In progress or Resume`() {
        val next = module(ModuleStatus.NEXT)
        assertEquals("Up next · tap to start", ModuleWords.trailCaption(next))
        assertEquals("Start", ModuleWords.trailAction(next))
        assertEquals("Up next", ModuleWords.levelPagePill(done = false, isNext = true, soon = false, isExam = false))
        for (w in listOf(ModuleWords.trailCaption(next), ModuleWords.trailAction(next))) {
            assertFalse(w, w.contains("progress", ignoreCase = true) || w.contains("Resume"))
        }
    }

    @Test fun `the exam row keeps its own words`() {
        assertEquals("Level exam · ready — tap to begin", ModuleWords.trailCaption(module(ModuleStatus.NEXT, exam = true)))
        assertEquals("Start exam", ModuleWords.trailAction(module(ModuleStatus.NEXT, exam = true)))
        assertEquals("Ready", ModuleWords.levelPagePill(done = false, isNext = true, soon = false, isExam = true))
        assertEquals("Level exam · opens soon", ModuleWords.trailCaption(module(ModuleStatus.LOCKED, exam = true, available = false)))
        assertEquals("Level exam · passed", ModuleWords.trailCaption(module(ModuleStatus.COMPLETED, exam = true)))
    }

    @Test fun `the trail's link starts until something is done, then continues`() {
        assertEquals("Start →", ModuleWords.trailLink(0))
        assertEquals("Continue →", ModuleWords.trailLink(2))
    }

    private fun journey(stage: JourneyStage, done: Int = 10, level: Int = 1) = Journey(
        stage = stage, levelNumber = level, levelTitle = "Foundations of Faith", levelPosition = level, levelCount = 6,
        completedModules = done, totalModules = 10, pill = "", kicker = "",
        next = JourneyStep("", "", null), progress = 0.1,
    )

    @Test fun `the member's own level wears the journey's state`() {
        assertEquals("EXAM READY", levelBadge(1, null, journey(JourneyStage.EXAM_READY)).text)
        assertEquals(LevelBadge.Tone.NEXT_STEP, levelBadge(1, null, journey(JourneyStage.EXAM_READY)).tone)
        assertEquals("EXAM OPENS SOON", levelBadge(1, null, journey(JourneyStage.EXAM_SOON)).text)
        assertEquals("EXAM PASSED", levelBadge(1, null, journey(JourneyStage.AWAITING_USHER)).text)
        assertEquals("COMMISSIONED", levelBadge(1, null, journey(JourneyStage.FINISHED)).text)
        assertEquals("IN PROGRESS", levelBadge(1, null, journey(JourneyStage.LEARNING, done = 2)).text)
        assertEquals("UP NEXT", levelBadge(1, null, journey(JourneyStage.LEARNING, done = 0)).text)
    }

    @Test fun `another level says what the server holds of it`() {
        val j = journey(JourneyStage.LEARNING, level = 2, done = 3)
        assertEquals("COMPLETE", levelBadge(1, PathwayLevel(levelNumber = 1, title = "Foundations of Faith", status = LevelStatus.COMPLETED), j).text)
        assertEquals("LOCKED", levelBadge(3, PathwayLevel(levelNumber = 3, title = "Grace", status = LevelStatus.LOCKED), j).text)
        assertEquals("EXAM PASSED", levelBadge(1, PathwayLevel(levelNumber = 1, title = "Foundations of Faith", status = LevelStatus.AWAITING_REVIEW), j).text)
    }
}

class LevelCountWordTest {
    @org.junit.Test fun `the map's heading counts the levels it shows`() {
        org.junit.Assert.assertEquals("Six", countWord(6))
        org.junit.Assert.assertEquals("Seven", countWord(7))
        org.junit.Assert.assertEquals("12", countWord(12))
    }
}
