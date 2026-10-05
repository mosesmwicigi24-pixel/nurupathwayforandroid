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

object ModuleWords {
    /** The trail row's line under a module's title (Pathway). */
    fun trailCaption(m: LevelModule): String {
        val done = m.status == ModuleStatus.COMPLETED
        val next = m.status == ModuleStatus.NEXT && !m.examOpensSoon
        return when {
            m.isExam && done -> "Level exam · passed"
            m.examOpensSoon -> "Level exam · opens soon"
            m.isExam && next -> "Level exam · ready — tap to begin"
            m.isExam -> "Finish every module to unlock the exam"
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
