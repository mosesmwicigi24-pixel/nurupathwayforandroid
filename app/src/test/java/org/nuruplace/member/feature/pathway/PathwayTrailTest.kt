// Pathway: a finished level folds away (pathway docs/EXPERIENCE.md §6.3). Once
// the member is past learning their level its module list folds into "20 of 20
// modules done · Show"; the trail's exam row is gone while the hero shows the
// exam step; and the rail marks "You" (the member's level) and "Next" (the one
// after) — on Android it marked only a level the server called "active", so a
// member at their exam (Ada: Level 1, 20 of 20, status "completed") saw neither.
package org.nuruplace.member.feature.pathway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary

class PathwayTrailTest {
    /** Six levels: [current] in [status] with [done] of 20; the ones before
     *  completed, the ones after locked. */
    private fun levels(current: Int = 1, status: LevelStatus = LevelStatus.ACTIVE, done: Int = 0, examPublished: Boolean = true) =
        (1..6).map { n ->
            when {
                n < current -> PathwayLevel(n, "Level $n", totalModules = 10, completedModules = 10, status = LevelStatus.COMPLETED)
                n == current -> PathwayLevel(n, "Level $n", totalModules = 20, completedModules = done, status = status, examPublished = examPublished)
                else -> PathwayLevel(n, "Level $n", status = LevelStatus.LOCKED)
            }
        }

    private fun journey(current: Int = 1, status: LevelStatus = LevelStatus.ACTIVE, done: Int = 0, examPublished: Boolean = true) =
        JourneyState.derive(PathwaySummary(currentLevel = current, levels = levels(current, status, done, examPublished)))!!

    /** A level's lessons 1..20, the first [done] completed — and its exam container. */
    private fun trail(done: Int, exam: ModuleStatus = ModuleStatus.LOCKED, examDone: Boolean = false) = (1..20).map { i ->
        LevelModule(
            moduleId = "m$i", levelNumber = 1, moduleSequenceNumber = i, title = "Module $i", completed = i <= done,
            status = when { i <= done -> ModuleStatus.COMPLETED; i == done + 1 -> ModuleStatus.NEXT; else -> ModuleStatus.LOCKED },
        )
    } + LevelModule(
        moduleId = "exam1", levelNumber = 1, moduleSequenceNumber = 99, title = "Level 1 exam",
        evaluationKind = "exit_exam", completed = examDone, status = exam,
    )

    // ── the rail: You and Next ──

    @Test
    fun `You marks the member's level whatever its status, Next the locked one after`() {
        // Walking Level 1.
        assertEquals(RailMarks(0, 1), railMarks(levels(), 1))
        // Ada: every module done, the exam ready — the server says "completed".
        assertEquals(RailMarks(0, 1), railMarks(levels(status = LevelStatus.COMPLETED, done = 20), 1))
        // The exam passed, waiting on the leader to open Level 2.
        assertEquals(RailMarks(0, 1), railMarks(levels(status = LevelStatus.AWAITING_REVIEW, done = 20), 1))
        // Ushered on: Level 3 of 6.
        assertEquals(RailMarks(2, 3), railMarks(levels(current = 3), 3))
    }

    @Test
    fun `no Next past the last level, nor onto a level already open`() {
        assertEquals(RailMarks(5, null), railMarks(levels(current = 6), 6))
        val open = levels().map { if (it.levelNumber == 2) it.copy(status = LevelStatus.ACTIVE) else it }
        assertEquals(RailMarks(0, null), railMarks(open, 1))
    }

    @Test
    fun `without the journey, the active level — and no marks when there is none`() {
        assertEquals(RailMarks(1, 2), railMarks(levels(current = 2), null))
        assertEquals(RailMarks(null, null), railMarks(levels(status = LevelStatus.COMPLETED, done = 20), null))
        assertEquals(RailMarks(null, null), railMarks(emptyList(), 1))
    }

    // ── the fold ──

    @Test
    fun `past learning their level, its list folds — ready, soon, passed`() {
        val modules = trail(20, exam = ModuleStatus.NEXT)
        assertTrue(trailFolds(journey(status = LevelStatus.COMPLETED, done = 20), 1, modules))                       // exam ready
        assertTrue(trailFolds(journey(status = LevelStatus.COMPLETED, done = 20, examPublished = false), 1, modules)) // exam soon
        assertTrue(trailFolds(journey(status = LevelStatus.AWAITING_REVIEW, done = 20), 1, modules))                 // exam passed
        assertTrue(trailFolds(journey(current = 6, status = LevelStatus.AWAITING_REVIEW, done = 20), 6, modules))     // commissioned
    }

    @Test
    fun `a level still being learned, another level, or an empty one stays open`() {
        assertFalse(trailFolds(journey(done = 5), 1, trail(5)))
        assertFalse(trailFolds(journey(status = LevelStatus.COMPLETED, done = 20), 2, trail(20)))
        assertFalse(trailFolds(null, 1, trail(20)))
        assertFalse(trailFolds(journey(status = LevelStatus.COMPLETED, done = 20), 1, emptyList()))
    }

    @Test
    fun `the folded row counts the level's lessons — the exam is a step, not a module`() {
        assertEquals("20 of 20 modules done", foldedTrailLine(trail(20, exam = ModuleStatus.NEXT)))
        assertEquals("18 of 20 modules done", foldedTrailLine(trail(18)))
        // Done by its status alone (an older payload without the flag) counts.
        val byStatus = trail(0).map { if (!it.isExam) it.copy(status = ModuleStatus.COMPLETED) else it }
        assertEquals("20 of 20 modules done", foldedTrailLine(byStatus))
    }

    // ── the exam row ──

    @Test
    fun `the trail's exam row is gone while the hero shows the exam step`() {
        assertTrue(examRowHidden(journey(status = LevelStatus.COMPLETED, done = 20), 1))
        // Another level's trail, or any other stage, keeps its exam row.
        assertFalse(examRowHidden(journey(status = LevelStatus.COMPLETED, done = 20), 2))
        assertFalse(examRowHidden(journey(status = LevelStatus.AWAITING_REVIEW, done = 20), 1))
        assertFalse(examRowHidden(journey(status = LevelStatus.COMPLETED, done = 20, examPublished = false), 1))
        assertFalse(examRowHidden(journey(done = 5), 1))
        assertFalse(examRowHidden(null, 1))
    }
}

/** No count of nothing (Cycle 4 walk: "0 of 0 done" over a level with nothing
 *  published) — §3's "Level N is being prepared"; a level not begun says what
 *  lies ahead; then the count. The same words as iOS (8bbcf82). */
class LevelCountLineTest {
    private fun level(n: Int, done: Int, total: Int) = org.nuruplace.member.data.net.PathwayLevel(
        n, "Level $n", totalModules = total, completedModules = done,
        status = org.nuruplace.member.data.net.LevelStatus.ACTIVE, lessonsTotal = total, lessonsCompleted = done,
    )

    @org.junit.Test fun `a level with nothing published is being prepared, on every screen`() {
        org.junit.Assert.assertEquals("Level 3 is being prepared", sectionCountLine(level(3, 0, 0)))
        org.junit.Assert.assertEquals("Level 2 is being prepared", cardCountLine(level(2, 0, 0)))
        org.junit.Assert.assertEquals("Level 2 is being prepared", levelPageCountLine(2, 0, 0))
        org.junit.Assert.assertEquals("Its modules open soon — we'll let you know.", emptyListLine(0))
        org.junit.Assert.assertEquals("Modules open as you progress.", emptyListLine(10))
    }

    @org.junit.Test fun `a level not begun says what lies ahead, never zero of ten`() {
        org.junit.Assert.assertEquals("10 modules", sectionCountLine(level(1, 0, 10)))
        org.junit.Assert.assertEquals("10 modules", cardCountLine(level(1, 0, 10)))
        org.junit.Assert.assertEquals("1 module", levelPageCountLine(1, 0, 1))
    }

    @org.junit.Test fun `then the count, in each screen's shape`() {
        org.junit.Assert.assertEquals("3 of 10 done", sectionCountLine(level(1, 3, 10)))
        org.junit.Assert.assertEquals("3/10 modules", cardCountLine(level(1, 3, 10)))
        org.junit.Assert.assertEquals("10 of 10 modules", levelPageCountLine(1, 10, 10))
    }
}
