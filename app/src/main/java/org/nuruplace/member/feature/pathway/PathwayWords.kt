// The words for where a member stands, module by module and level by level —
// one place, so the trail, the level page and its badge can't drift apart
// (EXPERIENCE.md §3; Cycle 3's closing walk, B2 and B3).
//
// The server's module `status: next` means "the next one to do" — a module's
// progress is only ever 0 or 100 (curriculum service) — so an unopened module
// reads "Up next" and "Start", never "In progress" or "Resume": Ben, who had
// opened nothing, read "In progress · tap to continue" beside "Resume".
// A level's badge follows the one journey state, so the page that says
// "IN PROGRESS" while Pathway says "Exam ready" can't happen.
package org.nuruplace.member.feature.pathway

import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.PathwayLevel

/**
 * One name for the exam on the whole journey (EXPERIENCE.md §9.1 rule 1): "the
 * Level N exam" — never "review", never "module". The server titles the exam
 * container "Level 1 Review", so a row that shows the container shows this
 * name instead. And its front door (rule 2): what it is, what it asks, what
 * happens after — before question 1, never "Question 1 of 91" cold.
 */
object ExamWords {
    /** "Level 1 exam" — the name a row, a header or a kicker gives it. */
    fun name(levelNumber: Int): String = "Level $levelNumber exam"

    /** What a row shows for [m]: the exam's one name, else the module's own title. */
    fun rowTitle(m: LevelModule): String = if (m.isExam) name(m.levelNumber) else m.title

    /** "91 questions · pass mark 80%" — the count and the mark the server
     *  sent; without a mark (an older server), the count alone. */
    fun facts(questionCount: Int, passMark: Int?): String =
        listOfNotNull(
            "$questionCount ${if (questionCount == 1) "question" else "questions"}",
            passMark?.takeIf { it in 1..100 }?.let { "pass mark $it%" },
        ).joinToString(" · ")

    /** The front door for Level [levelNumber]'s exam. What happens after is
     *  §3's own line for an open exam — never a leader's promise the church
     *  may not keep yet (§9.1 rule 7). */
    fun frontDoor(levelNumber: Int, questionCount: Int, passMark: Int?): QuizFrontDoor = QuizFrontDoor(
        title = "The ${name(levelNumber)}",
        facts = facts(questionCount, passMark),
        lines = listOf(
            "Your answers are kept if you leave — you pick up at the question you were on.",
            "A pass opens the way to Level ${levelNumber + 1}.",
        ),
        begin = "Begin",
    )

    /** The exam's verdict, in its own name — it read "Module Passed". */
    fun passedTitle(levelNumber: Int): String = "You passed the ${name(levelNumber)}"
}

/** A long test's front door (EXPERIENCE.md §9.1 rule 2): its title, what it
 *  asks, what to know, and the one way in. */
data class QuizFrontDoor(val title: String, val facts: String, val lines: List<String>, val begin: String)

object ModuleWords {
    /** The trail row's line under a module's title (Pathway). */
    fun trailCaption(m: LevelModule): String {
        val done = m.status == ModuleStatus.COMPLETED
        val next = m.status == ModuleStatus.NEXT && !m.examOpensSoon
        // The exam's row is titled "Level N exam" (ExamWords) — its line
        // doesn't name it again.
        return when {
            m.isExam && done -> "Passed"
            m.examOpensSoon -> "Opens soon"
            m.isExam && next -> "Ready — tap to begin"
            m.isExam -> "Finish every module to unlock it"
            done -> "Completed"
            next -> "Up next · tap to start"
            else -> "Locked"
        }
    }

    /** The trail row's pill for the one row to open next: "Start" or
     *  "Start exam" — never "Resume", which promises progress there isn't. */
    fun trailAction(m: LevelModule): String = if (m.isExam) "Start exam" else "Start"

    /** The level page's pill on a module card. */
    fun levelPagePill(done: Boolean, isNext: Boolean, soon: Boolean, isExam: Boolean): String = when {
        done -> "Done"
        soon -> "Opens soon"
        isNext -> if (isExam) "Ready" else "Up next"
        else -> "Locked"
    }

    /** The link over the trail to the row to open next: "Start →" while
     *  nothing in the level is done, "Continue →" once something is. */
    fun trailLink(lessonsDone: Int): String = if (lessonsDone <= 0) "Start →" else "Continue →"
}

/** A level page's badge: its words, and whether it marks something achieved. */
data class LevelBadge(val text: String, val tone: Tone) {
    enum class Tone { ACHIEVED, NEXT_STEP, QUIET }
}

/**
 * The badge on Level [levelNumber]'s page. The member's own level says the
 * journey's stage — "EXAM READY", "EXAM OPENS SOON", "EXAM PASSED",
 * "COMMISSIONED", or "IN PROGRESS" / "UP NEXT" while learning (by whether a
 * lesson is done yet). Any other level says what the server holds of it.
 */
fun levelBadge(levelNumber: Int, level: PathwayLevel?, journey: Journey?): LevelBadge {
    if (journey != null && journey.levelNumber == levelNumber) {
        return when (journey.stage) {
            JourneyStage.LEARNING ->
                if (journey.completedModules > 0) LevelBadge("IN PROGRESS", LevelBadge.Tone.QUIET)
                else LevelBadge("UP NEXT", LevelBadge.Tone.QUIET)
            JourneyStage.EXAM_READY -> LevelBadge("EXAM READY", LevelBadge.Tone.NEXT_STEP)
            JourneyStage.EXAM_SOON -> LevelBadge("EXAM OPENS SOON", LevelBadge.Tone.QUIET)
            JourneyStage.AWAITING_USHER -> LevelBadge("EXAM PASSED", LevelBadge.Tone.ACHIEVED)
            JourneyStage.FINISHED -> LevelBadge("COMMISSIONED", LevelBadge.Tone.ACHIEVED)
        }
    }
    return when {
        level == null -> LevelBadge("IN PROGRESS", LevelBadge.Tone.QUIET)
        level.isAwaitingReview -> LevelBadge("EXAM PASSED", LevelBadge.Tone.ACHIEVED)
        level.status == LevelStatus.COMPLETED -> LevelBadge("COMPLETE", LevelBadge.Tone.ACHIEVED)
        level.status == LevelStatus.LOCKED -> LevelBadge("LOCKED", LevelBadge.Tone.QUIET)
        level.lessonsDone > 0 -> LevelBadge("IN PROGRESS", LevelBadge.Tone.QUIET)
        else -> LevelBadge("UP NEXT", LevelBadge.Tone.QUIET)
    }
}

/**
 * Map view speaks the journey's words (Cycle 3 close walk E4/E5; iOS
 * LevelsMapWords): it said "CONTINUE YOUR JOURNEY · Level 1" and "Complete
 * Level 1 to unlock" while Level 1's every module was done and its exam was
 * next.
 */
object LevelsMapWords {
    data class Card(val kicker: String, val title: String, val line: String?)

    /** The continue card for the member's level: the journey's next step once
     *  the modules are done; "continue" only while there are modules to walk. */
    fun continueCard(level: PathwayLevel, journey: Journey?): Card {
        if (journey == null || journey.levelNumber != level.levelNumber || journey.stage == JourneyStage.LEARNING) {
            return Card("CONTINUE YOUR JOURNEY", "Level ${level.levelNumber}: ${level.title}", null)
        }
        return Card(journey.kicker.uppercase(), journey.next.title, journey.next.line.takeIf { it.isNotBlank() })
    }

    /** What opens a locked level: the step before it, in the journey's words. */
    fun lockLine(levelNumber: Int, journey: Journey?): String {
        val prev = levelNumber - 1
        if (journey == null || journey.levelNumber != prev) return "Complete Level $prev to unlock"
        return when (journey.stage) {
            JourneyStage.LEARNING -> "Complete Level $prev to unlock"
            JourneyStage.EXAM_READY -> "Pass the Level $prev exam — then your leader opens Level $levelNumber"
            JourneyStage.EXAM_SOON -> "The Level $prev exam opens soon — then your leader opens Level $levelNumber"
            JourneyStage.AWAITING_USHER, JourneyStage.FINISHED -> "Your leader will open Level $levelNumber — you'll get a notice"
        }
    }
}
