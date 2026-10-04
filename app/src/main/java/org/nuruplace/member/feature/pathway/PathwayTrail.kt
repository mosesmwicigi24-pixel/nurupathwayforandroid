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
