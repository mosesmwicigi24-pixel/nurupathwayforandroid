// The Pathway hub's rail and trail (pathway docs/EXPERIENCE.md §6.3, Cycle 2)
// — pure, so PathwayTrailTest pins them:
//
//  · the rail marks "You" on the member's own level — the journey's, whatever
//    its status: still walking it, every module done, its exam passed — and
//    "Next" on the level after it while that one is still locked (iOS has
//    always marked the journey's level; Android marked only an "active" one,
//    so a member at their exam saw neither mark);
//  · once the member is past learning their level, its module list folds
//    into one row — "20 of 20 modules done · Show" — that expands;
//  · the exam row at the foot of the trail is gone while the hero above
//    already shows the exam step.
package org.nuruplace.member.feature.pathway

import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.PathwayLevel

/** The rail's two marks, as indexes into the rail's levels; null = none. */
internal data class RailMarks(val you: Int?, val next: Int?)

/** "You" on [current] (the journey's level) — else, without a journey, the
 *  active level; "Next" on the level after it while that one is locked. */
internal fun railMarks(levels: List<PathwayLevel>, current: Int?): RailMarks {
    val you = current?.let { c -> levels.indexOfFirst { it.levelNumber == c } }?.takeIf { it >= 0 }
        ?: levels.indexOfFirst { it.status == LevelStatus.ACTIVE }.takeIf { it >= 0 }
    val next = you?.let { it + 1 }?.takeIf { it < levels.size && levels[it].status == LevelStatus.LOCKED }
    return RailMarks(you, next)
}

/** The member's own level, once they are past learning it (every module
 *  done — the exam ready, or soon, or passed): its list folds into one row.
 *  Any other level's list, and a level still being learned, stays open. */
internal fun trailFolds(journey: Journey?, levelNumber: Int, modules: List<LevelModule>): Boolean =
    journey != null && journey.stage != JourneyStage.LEARNING && journey.levelNumber == levelNumber && modules.isNotEmpty()

/** The folded row's words, before its "Show": "20 of 20 modules done" — the
 *  level's lessons; its exam is a step, not a module. */
internal fun foldedTrailLine(modules: List<LevelModule>): String {
    val lessons = modules.filter { !it.isExam }
    val done = lessons.count { it.completed || it.status == ModuleStatus.COMPLETED }
    return "$done of ${lessons.size} modules done"
}

/** The hero shows the exam step — the journey at its exam on this level —
 *  so the trail's own exam row, at its foot, is not shown again. */
internal fun examRowHidden(journey: Journey?, levelNumber: Int): Boolean =
    journey != null && journey.stage == JourneyStage.EXAM_READY && journey.levelNumber == levelNumber

// ── Counts with nothing to count (§8.1 rule 8, §3; Cycle 4 walk) ──
// A level with nothing published read "0 of 0 done" over "Modules open as you
// progress." — a count of nothing. It is §3's "Level N is being prepared"; a
// level not begun says what lies ahead ("10 modules", §9.2 #4); then the count.
// The same words as iOS PathwayTrail (8bbcf82), each in its own screen's shape.

private fun levelCountLine(levelNumber: Int, done: Int, total: Int, counted: (done: Int, total: Int) -> String): String = when {
    total <= 0 -> "Level $levelNumber is being prepared"
    done <= 0 -> "$total module${if (total == 1) "" else "s"}"
    else -> counted(minOf(done, total), total)
}

/** The count under a level's name over its list on Pathway: "10 of 10 done". */
internal fun sectionCountLine(level: PathwayLevel): String =
    levelCountLine(level.levelNumber, level.lessonsDone, level.lessonCount) { d, t -> "$d of $t done" }

/** Map view's level card: "3/10 modules". */
internal fun cardCountLine(level: PathwayLevel): String =
    levelCountLine(level.levelNumber, level.lessonsDone, level.lessonCount) { d, t -> "$d/$t modules" }

/** The level page's progress line: "3 of 10 modules". */
internal fun levelPageCountLine(levelNumber: Int, done: Int, total: Int): String =
    levelCountLine(levelNumber, done, total) { d, t -> "$d of $t modules" }

/** An empty list's line: a level with nothing published keeps §3's promise;
 *  a level whose lessons exist but aren't open to the member opens as they go. */
internal fun emptyListLine(lessonCount: Int): String =
    if (lessonCount > 0) "Modules open as you progress." else "Its modules open soon — we'll let you know."
