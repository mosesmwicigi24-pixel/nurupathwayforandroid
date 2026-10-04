// The member's journey state (pathway docs/EXPERIENCE.md §3) — every stage in
// §3's words, journey progress counted in levels, and the summit only at the
// end. The case that started it: a member with all 20 Level 1 modules done
// (status "completed", exam published) while Levels 2–6 have no published
// modules yet read 100% and was "commissioned" at Level 1 of 6.
package org.nuruplace.member.feature.pathway

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary

class JourneyTest {
    private val titles = listOf(
        "Foundations of Faith", "Knowing God", "New Life", "Identity in Christ", "The Word", "Spirit-Led Life",
    )

    /** Six levels: [current] in [status] with [done]/[total]; the ones before
     *  it completed (10 of 10), the ones after locked with no modules — as
     *  production has them today. */
    private fun summary(
        current: Int = 1,
        status: LevelStatus = LevelStatus.ACTIVE,
        done: Int = 0,
        total: Int = 20,
        examPublished: Boolean = true,
        examAvailable: Boolean? = null,
        awaitingFlag: Boolean = false,
    ) = PathwaySummary(
        currentLevel = current,
        levels = (1..6).map { n ->
            when {
                n < current -> PathwayLevel(n, titles[n - 1], totalModules = 10, completedModules = 10, status = LevelStatus.COMPLETED)
                n == current -> PathwayLevel(
                    n, titles[n - 1], totalModules = total, completedModules = done, status = status,
                    examPublished = examPublished, examAvailable = examAvailable, awaitingReview = awaitingFlag,
                )
                else -> PathwayLevel(n, titles[n - 1], totalModules = 0, completedModules = 0, status = LevelStatus.LOCKED)
            }
        },
    )

    /** A level's trail: modules 1..[total]; the first [done] completed, the
     *  next one open, the rest locked. */
    private fun trail(level: Int = 1, done: Int, total: Int = 20) = (1..total).map { i ->
        LevelModule(
            moduleId = "m$level-$i", levelNumber = level, moduleSequenceNumber = i, title = "Module $i",
            completed = i <= done,
            status = when { i <= done -> ModuleStatus.COMPLETED; i == done + 1 -> ModuleStatus.NEXT; else -> ModuleStatus.LOCKED },
        )
    }

    private fun examRow(level: Int = 1, status: ModuleStatus, completed: Boolean = false, available: Boolean? = null) = LevelModule(
        moduleId = "exam$level", levelNumber = level, moduleSequenceNumber = 99, title = "Level $level exam",
        evaluationKind = "exit_exam", completed = completed, status = status, examAvailable = available,
    )

    // ── learning ──

    @Test fun `learning — the next open module, in the stage's words`() {
        val j = JourneyState.derive(summary(done = 5), trail(done = 5))!!
        assertEquals(JourneyStage.LEARNING, j.stage)
        assertEquals("5 of 20 modules", j.pill)
        // §3: "Continue (or Start, when X = 0): «module title»" — the verb
        // leads the kicker and names the action; the module is the title.
        assertEquals("Continue · Level 1", j.kicker)
        assertEquals("Module 6", j.next.title)
        assertEquals("5 of 20 modules in Level 1", j.next.line)
        assertEquals("Continue", j.next.action?.label)
        assertEquals(JourneyDestination.Module("m1-6"), j.next.action?.destination)
        assertEquals("module/m1-6", j.next.action?.destination?.route)
        assertEquals(JourneyLine("5 of 20 modules", " in Level 1"), j.progressLine)
        assertEquals("5 of 20 modules in Level 1", j.progressLine.text)
        assertFalse(j.summitReached)
    }

    @Test fun `learning — nothing done yet says Start`() {
        val j = JourneyState.derive(summary(done = 0), trail(done = 0))!!
        assertEquals("0 of 20 modules", j.pill)
        assertEquals("Start · Level 1", j.kicker)
        assertEquals("Module 1", j.next.title)
        assertEquals("0 of 20 modules in Level 1", j.next.line)
        assertEquals("Start", j.next.action?.label)
        assertEquals(JourneyDestination.Module("m1-1"), j.next.action?.destination)
    }

    @Test fun `learning — never continues into a module already finished`() {
        // The old hub fell back to the LAST module when none was open — a
        // finished one. With nothing open, the step names the level and opens it.
        val allDone = trail(done = 20)
        val j = JourneyState.derive(summary(done = 19), allDone)!!
        assertEquals(JourneyStage.LEARNING, j.stage)
        assertEquals("Foundations of Faith", j.next.title)
        assertEquals(JourneyDestination.Level(1), j.next.action?.destination)
    }

    @Test fun `learning — a locked next module opens the level page`() {
        val gated = trail(done = 5).map { if (it.moduleSequenceNumber == 6) it.copy(status = ModuleStatus.LOCKED, locked = true) else it }
        val j = JourneyState.derive(summary(done = 5), gated)!!
        assertEquals("Module 6", j.next.title)
        assertEquals(JourneyDestination.Level(1), j.next.action?.destination)
    }

    @Test fun `learning — before the trail loads the level names the step`() {
        val j = JourneyState.derive(summary(done = 5))!!
        assertEquals(JourneyStage.LEARNING, j.stage)
        assertEquals("Foundations of Faith", j.next.title)
        assertEquals("level/1", j.next.action?.destination?.route)
    }

    @Test fun `learning — a trail for another level is not this level's`() {
        val j = JourneyState.derive(summary(done = 5), trail(level = 2, done = 0))!!
        assertEquals("Foundations of Faith", j.next.title)
    }

    // ── the exam ──

    @Test fun `exam ready — every module done and the exam published, Levels 2-6 empty`() {
        val j = JourneyState.derive(summary(status = LevelStatus.COMPLETED, done = 20), trail(done = 20))!!
        assertEquals(JourneyStage.EXAM_READY, j.stage)
        assertEquals("Exam ready", j.pill)
        assertEquals("Exam ready · Level 1", j.kicker)
        assertEquals("Take the Level 1 exam", j.next.title)
        assertEquals("Every module is done — the exam opens the way to Level 2.", j.next.line)
        assertEquals("Begin the exam", j.next.action?.label)
        assertEquals(JourneyDestination.Exam(1), j.next.action?.destination)
        assertEquals("exam/1", j.next.action?.destination?.route)
        // Level 1 of 6 — not 100%, and not the summit.
        assertEquals(17, j.percent)
        assertFalse(j.summitReached)
        // Home's progress line is the step — never "0 modules left before Level 2".
        assertEquals(JourneyLine("Take the Level 1 exam", ""), j.progressLine)
    }

    @Test fun `exam ready — a level whose exam row counts among its modules (20 of 21)`() {
        val withExam = trail(done = 20) + examRow(status = ModuleStatus.NEXT)
        val j = JourneyState.derive(summary(status = LevelStatus.ACTIVE, done = 20, total = 21), withExam)!!
        assertEquals(JourneyStage.EXAM_READY, j.stage)
        assertEquals("Exam ready", j.pill)
        assertEquals(JourneyDestination.Exam(1), j.next.action?.destination)
        // Every module done: the level counts whole, exam row or not.
        assertEquals(17, j.percent)
    }

    @Test fun `an exam row still locked is not ready`() {
        val withExam = trail(done = 12) + examRow(status = ModuleStatus.LOCKED)
        val j = JourneyState.derive(summary(done = 12, total = 21), withExam)!!
        assertEquals(JourneyStage.LEARNING, j.stage)
        assertEquals("Module 13", j.next.title)
    }

    @Test fun `exam soon — every module done, the exam not published`() {
        val j = JourneyState.derive(summary(status = LevelStatus.COMPLETED, done = 20, examPublished = false))!!
        assertEquals(JourneyStage.EXAM_SOON, j.stage)
        assertEquals("Exam opens soon", j.pill)
        assertEquals("Exam opens soon · Level 1", j.kicker)
        assertEquals("Level 1 complete", j.next.title)
        assertEquals("Every module is done. The exam opens soon — we'll let you know.", j.next.line)
        assertNull(j.next.action)
        assertFalse(j.summitReached)
    }

    // ── the exam can be taken (EXPERIENCE.md §7.2 #1) ──
    // Published is not enough: an exam published with no questions answered
    // 422 behind "Exam ready". The server says `exam_available` on each level
    // and on the trail's exam row; absent (an older server) = available.

    @Test fun `exam available — published with questions is ready`() {
        val j = JourneyState.derive(summary(status = LevelStatus.COMPLETED, done = 20, examAvailable = true), trail(done = 20))!!
        assertEquals(JourneyStage.EXAM_READY, j.stage)
        assertEquals("Exam ready", j.pill)
        assertEquals(JourneyDestination.Exam(1), j.next.action?.destination)
    }

    @Test fun `exam unavailable — published with nothing to ask opens soon, with no action`() {
        val j = JourneyState.derive(summary(status = LevelStatus.COMPLETED, done = 20, examAvailable = false), trail(done = 20))!!
        assertEquals(JourneyStage.EXAM_SOON, j.stage)
        assertEquals("Exam opens soon", j.pill)
        assertEquals("Exam opens soon · Level 1", j.kicker)
        assertEquals("Level 1 complete", j.next.title)
        assertEquals("Every module is done. The exam opens soon — we'll let you know.", j.next.line)
        // Nothing offers the exam: no action, so no route to it anywhere.
        assertNull(j.next.action)
        assertEquals(JourneyLine("Level 1 complete", ""), j.progressLine)
        assertEquals(17, j.percent)
        assertFalse(j.summitReached)
    }

    @Test fun `exam availability absent — an older server — behaves as before`() {
        val j = JourneyState.derive(summary(status = LevelStatus.COMPLETED, done = 20, examAvailable = null), trail(done = 20))!!
        assertEquals(JourneyStage.EXAM_READY, j.stage)
        // Not published is still soon, whatever availability says.
        val unpublished = JourneyState.derive(summary(status = LevelStatus.COMPLETED, done = 20, examPublished = false, examAvailable = true))!!
        assertEquals(JourneyStage.EXAM_SOON, unpublished.stage)
    }

    @Test fun `the open exam row — available, unavailable, absent`() {
        fun stage(rowAvailable: Boolean?, levelAvailable: Boolean? = null) = JourneyState.derive(
            summary(status = LevelStatus.ACTIVE, done = 20, total = 21, examAvailable = levelAvailable),
            trail(done = 20) + examRow(status = ModuleStatus.NEXT, available = rowAvailable),
        )!!.stage
        assertEquals(JourneyStage.EXAM_READY, stage(rowAvailable = true))
        assertEquals(JourneyStage.EXAM_SOON, stage(rowAvailable = false))
        assertEquals(JourneyStage.EXAM_READY, stage(rowAvailable = null))
        // Either word from the server is enough to hold it back.
        assertEquals(JourneyStage.EXAM_SOON, stage(rowAvailable = null, levelAvailable = false))
        val soon = JourneyState.derive(
            summary(status = LevelStatus.ACTIVE, done = 20, total = 21),
            trail(done = 20) + examRow(status = ModuleStatus.NEXT, available = false),
        )!!
        assertEquals("Level 1 complete", soon.next.title)
        assertNull(soon.next.action)
    }

    @Test fun `the exam row with nothing to ask reads Opens soon — never a lesson, never a passed exam`() {
        assertTrue(examRow(status = ModuleStatus.NEXT, available = false).examOpensSoon)
        assertFalse(examRow(status = ModuleStatus.NEXT, available = true).examOpensSoon)
        assertFalse(examRow(status = ModuleStatus.NEXT, available = null).examOpensSoon)
        assertFalse(examRow(status = ModuleStatus.COMPLETED, completed = true, available = false).examOpensSoon)
        assertFalse(trail(done = 3).first().copy(examAvailable = false).examOpensSoon)
        // A passed exam still ushers on, whatever availability says now.
        val passed = JourneyState.derive(
            summary(status = LevelStatus.ACTIVE, done = 20, total = 21, examAvailable = false),
            trail(done = 20) + examRow(status = ModuleStatus.COMPLETED, completed = true, available = false),
        )!!
        assertEquals(JourneyStage.AWAITING_USHER, passed.stage)
    }

    @Test fun `the last level's exam opens the way to being sent, not to a Level 7`() {
        val j = JourneyState.derive(summary(current = 6, status = LevelStatus.COMPLETED, done = 10, total = 10))!!
        assertEquals(JourneyStage.EXAM_READY, j.stage)
        assertEquals("Take the Level 6 exam", j.next.title)
        assertEquals("Every module is done — the exam opens the way to being sent.", j.next.line)
        // Every module of every level done — still not 100 while the exam waits.
        assertEquals(99, j.percent)
        assertFalse(j.summitReached)
    }

    // ── the usher ──

    @Test fun `awaiting the usher — the exam passed, the leader opens the next level`() {
        val j = JourneyState.derive(summary(status = LevelStatus.AWAITING_REVIEW, done = 20, awaitingFlag = true), trail(done = 20))!!
        assertEquals(JourneyStage.AWAITING_USHER, j.stage)
        assertEquals("Exam passed", j.pill)
        assertEquals("Exam passed · Level 1", j.kicker)
        assertEquals("Level 2 is next", j.next.title)
        assertEquals("You passed the Level 1 exam. Your leader will open Level 2 — you'll get a notice.", j.next.line)
        assertEquals("See Level 1", j.next.action?.label)
        assertEquals(JourneyDestination.Level(1), j.next.action?.destination)
        assertEquals("level/1", j.next.action?.destination?.route)
        assertEquals(17, j.percent)
        assertFalse(j.summitReached)
    }

    @Test fun `awaiting the usher — the server's flag alone is enough`() {
        val j = JourneyState.derive(summary(status = LevelStatus.LOCKED, done = 20, awaitingFlag = true))!!
        assertEquals(JourneyStage.AWAITING_USHER, j.stage)
    }

    @Test fun `awaiting the usher — a passed exam row says so too`() {
        val withExam = trail(done = 20) + examRow(status = ModuleStatus.COMPLETED, completed = true)
        val j = JourneyState.derive(summary(status = LevelStatus.ACTIVE, done = 20, total = 21), withExam)!!
        assertEquals(JourneyStage.AWAITING_USHER, j.stage)
    }

    // ── the summit ──

    @Test fun `finished — the last level's exam passed`() {
        val j = JourneyState.derive(summary(current = 6, status = LevelStatus.AWAITING_REVIEW, done = 10, total = 10, awaitingFlag = true))!!
        assertEquals(JourneyStage.FINISHED, j.stage)
        assertEquals("Commissioned", j.pill)
        assertEquals("Commissioned", j.kicker)
        assertEquals("You have been commissioned", j.next.title)
        assertEquals("Sent to make disciples — Matthew 28:19", j.next.line)
        assertEquals("See your journey", j.next.action?.label)
        assertEquals(JourneyDestination.Walk, j.next.action?.destination)
        assertEquals("your-walk", j.next.action?.destination?.route)
        assertEquals(100, j.percent)
        assertTrue(j.summitReached)
        assertEquals(JourneyLine("You have been commissioned", ""), j.progressLine)
    }

    @Test fun `finished — ushered past the last level with its exam row passed`() {
        val withExam = trail(level = 6, done = 10, total = 10) + examRow(level = 6, status = ModuleStatus.COMPLETED, completed = true)
        val j = JourneyState.derive(summary(current = 6, status = LevelStatus.ACTIVE, done = 10, total = 11), withExam)!!
        assertEquals(JourneyStage.FINISHED, j.stage)
        assertTrue(j.summitReached)
    }

    @Test fun `the summit only at the end — no other stage reaches it`() {
        val notYet = listOf(
            JourneyState.derive(summary(done = 5), trail(done = 5)),
            JourneyState.derive(summary(status = LevelStatus.COMPLETED, done = 20)),
            JourneyState.derive(summary(status = LevelStatus.COMPLETED, done = 20, examPublished = false)),
            JourneyState.derive(summary(status = LevelStatus.AWAITING_REVIEW, done = 20, awaitingFlag = true)),
            JourneyState.derive(summary(current = 6, status = LevelStatus.COMPLETED, done = 10, total = 10)),
        )
        notYet.forEach { j ->
            assertFalse(j!!.stage.name, j.summitReached)
            assertTrue(j.stage.name, j.percent < 100)
        }
    }

    // ── journey progress, in levels ──

    @Test fun `progress counts levels before the current plus the current fraction`() {
        // Level 3 of 6, half done: (2 + 0.5) / 6 = 41.67%.
        val j = JourneyState.derive(summary(current = 3, done = 5, total = 10))!!
        assertEquals(42, j.percent)
        assertEquals(JourneyLine("5 of 10 modules", " in Level 3"), j.progressLine)
        // Level 1, 5 of 20: 0.25 / 6 = 4.17%.
        assertEquals(4, JourneyState.derive(summary(done = 5))!!.percent)
        assertEquals(0, JourneyState.derive(summary(done = 0))!!.percent)
    }

    @Test fun `a level with no modules yet — ushered into Level 2 today`() {
        val j = JourneyState.derive(summary(current = 2, done = 0, total = 0), emptyList())!!
        assertEquals(JourneyStage.LEARNING, j.stage)
        // Not "0 of 0 modules" — the level is being prepared, and says so.
        assertEquals("Modules open soon", j.pill)
        assertEquals("Modules open soon · Level 2", j.kicker)
        assertEquals("Level 2 is being prepared", j.next.title)
        assertEquals("Its modules open soon — we'll let you know.", j.next.line)
        assertNull(j.next.action)
        assertEquals(JourneyLine("Level 2 is being prepared", ""), j.progressLine)
        // Level 1 behind them: 1 / 6.
        assertEquals(17, j.percent)
        assertFalse(j.summitReached)
    }

    @Test fun `no levels, no journey`() {
        assertNull(JourneyState.derive(null))
        assertNull(JourneyState.derive(PathwaySummary(currentLevel = 1, levels = emptyList())))
    }

    // ── the wire ──

    @OptIn(ExperimentalSerializationApi::class)
    @Test fun `awaiting_review decodes as itself, not as locked`() {
        // The app's own decoder settings (ApiClient): snake_case, unknown enum → default.
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; namingStrategy = JsonNamingStrategy.SnakeCase }
        val s = json.decodeFromString(
            PathwaySummary.serializer(),
            """{"current_level":1,"levels":[
                {"level_number":1,"title":"Foundations","total_modules":20,"completed_modules":20,"status":"awaiting_review","awaiting_review":true,"exam_published":true},
                {"level_number":2,"title":"Knowing God","total_modules":0,"completed_modules":0,"status":"locked","awaiting_review":false,"exam_published":false},
                {"level_number":3,"title":"Next","status":"something_new"}
            ]}""",
        )
        assertEquals(LevelStatus.AWAITING_REVIEW, s.levels[0].status)
        assertTrue(s.levels[0].isAwaitingReview)
        assertTrue(s.levels[0].walked)
        assertFalse(s.levels[1].walked)
        // A vocabulary this client predates still reads as locked.
        assertEquals(LevelStatus.LOCKED, s.levels[2].status)
        assertEquals(JourneyStage.AWAITING_USHER, JourneyState.derive(s)!!.stage)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test fun `exam_available decodes on the level and the exam row — absent is null`() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; namingStrategy = JsonNamingStrategy.SnakeCase }
        val s = json.decodeFromString(
            PathwaySummary.serializer(),
            """{"current_level":1,"levels":[
                {"level_number":1,"title":"Foundations","total_modules":20,"completed_modules":20,"status":"completed","awaiting_review":false,"exam_published":true,"exam_available":false},
                {"level_number":2,"title":"Inner Transformation","total_modules":0,"completed_modules":0,"status":"locked","exam_published":true,"exam_available":true},
                {"level_number":3,"title":"Older server","status":"locked","exam_published":true}
            ]}""",
        )
        assertEquals(false, s.levels[0].examAvailable)
        assertFalse(s.levels[0].examOffered)
        assertEquals(true, s.levels[1].examAvailable)
        assertNull(s.levels[2].examAvailable)
        assertTrue(s.levels[2].examOffered)
        assertEquals(JourneyStage.EXAM_SOON, JourneyState.derive(s)!!.stage)
        val row = json.decodeFromString(
            LevelModule.serializer(),
            """{"module_id":"x","level_number":1,"module_sequence_number":900,"title":"Level 1 exam","evaluation_kind":"exit_exam","quiz_pass_mark":"70.00","completed":false,"status":"next","progress":0,"locked":false,"exam_available":false}""",
        )
        assertTrue(row.examOpensSoon)
    }
}
