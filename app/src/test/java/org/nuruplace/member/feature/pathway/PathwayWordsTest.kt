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

class FootprintsWordsTest {
    @org.junit.Test fun `before you only while you haven't finished — then too (B10)`() {
        org.junit.Assert.assertEquals("Eli walked here before you.", footprintsLine(listOf("Eli"), 1, youFinished = false))
        org.junit.Assert.assertEquals("Eli walked here too.", footprintsLine(listOf("Eli"), 1, youFinished = true))
        org.junit.Assert.assertEquals("Eli, Cara and 3 others walked here too.", footprintsLine(listOf("Eli", "Cara"), 5, youFinished = true))
    }

    @org.junit.Test fun `names take one and (B13)`() {
        org.junit.Assert.assertEquals("Cara, Builder, Ada and 2 others", joinNames(listOf("Cara", "Builder", "Ada"), 2))
        org.junit.Assert.assertEquals("Cara and Ada", joinNames(listOf("Cara", "Ada")))
        org.junit.Assert.assertEquals("Cara and 1 other", joinNames(listOf("Cara"), 1))
        org.junit.Assert.assertEquals("Cara", joinNames(listOf("Cara")))
    }
}

/** Map view speaks the journey's words (Cycle 3 close walk E4/E5, as iOS):
 *  never "CONTINUE YOUR JOURNEY" over a level whose modules are done, never
 *  "Complete Level 1" for a level that is. */
class LevelsMapWordsTest {
    private val one = PathwayLevel(levelNumber = 1, title = "Foundations of Faith", status = LevelStatus.ACTIVE, totalModules = 10, completedModules = 10)
    private fun journey(stage: JourneyStage, level: Int = 1) = Journey(
        stage = stage, levelNumber = level, levelTitle = "Foundations of Faith", levelPosition = level, levelCount = 6,
        completedModules = 10, totalModules = 10, pill = "", kicker = "Exam ready · Level $level",
        next = JourneyStep("Take the Level $level exam", "Every module is done — the exam is next.", null), progress = 0.1,
    )

    @Test fun `a finished level's card is the journey's next step`() {
        val card = LevelsMapWords.continueCard(one, journey(JourneyStage.EXAM_READY))
        assertEquals("Take the Level 1 exam", card.title)
        assertEquals("EXAM READY · LEVEL 1", card.kicker)
        assertEquals("Every module is done — the exam is next.", card.line)
        assertFalse(card.kicker.contains("CONTINUE"))
    }

    @Test fun `while modules remain, the card continues`() {
        val card = LevelsMapWords.continueCard(one, journey(JourneyStage.LEARNING))
        assertEquals(LevelsMapWords.Card("CONTINUE YOUR JOURNEY", "Level 1: Foundations of Faith", null), card)
        // Another level than the journey's: the plain card.
        assertEquals("CONTINUE YOUR JOURNEY", LevelsMapWords.continueCard(one, journey(JourneyStage.EXAM_READY, level = 2)).kicker)
    }

    @Test fun `a locked level names the step before it`() {
        val ready = journey(JourneyStage.EXAM_READY)
        assertEquals("Pass the Level 1 exam — then your leader opens Level 2", LevelsMapWords.lockLine(2, ready))
        assertEquals("Complete Level 2 to unlock", LevelsMapWords.lockLine(3, ready))
        assertEquals("Complete Level 1 to unlock", LevelsMapWords.lockLine(2, journey(JourneyStage.LEARNING)))
        assertEquals("The Level 1 exam opens soon — then your leader opens Level 2", LevelsMapWords.lockLine(2, journey(JourneyStage.EXAM_SOON)))
        assertEquals("Your leader will open Level 2 — you'll get a notice", LevelsMapWords.lockLine(2, journey(JourneyStage.AWAITING_USHER)))
        assertEquals("Complete Level 1 to unlock", LevelsMapWords.lockLine(2, null))
    }
}
