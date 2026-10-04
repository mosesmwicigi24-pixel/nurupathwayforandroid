// The member's journey state (pathway docs/EXPERIENCE.md §3, Cycle 1) — derived
// ONCE from GET /me/pathway (the current level's row: status, exam_published,
// completed/total modules) and the next incomplete module, then shown in the
// same words on the Home header pill, the Home continue card, the Home progress
// line, the Pathway hero, the Pathway ring and the summit.
//
// Modules a member earns alone; levels need a human discipler to usher them in
// (exam pass → pending advancement → awaiting_review → ushered). So a member who
// has finished every module is neither "100%" nor "commissioned": their next
// step is the level's exam, then their leader. Journey progress is counted in
// levels, never as a share of published modules — Levels 2–6 had none yet, so
// a Level 1 finisher read 100% and the summit fired at Level 1 of 6.
//
// Pure, so JourneyTest pins every stage. The same spec is built on iOS; both
// apps say exactly these words.
package org.nuruplace.member.feature.pathway

import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary
import kotlin.math.roundToInt

/** Where the member stands on the road (§3's table). */
enum class JourneyStage {
    /** The current level is open: modules to read. */
    LEARNING,
    /** Every module is done and the level's exam is published. */
    EXAM_READY,
    /** Every module is done; the exam is not published yet. */
    EXAM_SOON,
    /** The exam is passed; the member's leader opens the next level. */
    AWAITING_USHER,
    /** The LAST level's exam is passed — the summit. */
    FINISHED,
}

/** Where a next step goes — the app's existing destinations only. */
sealed interface JourneyDestination {
    data class Module(val moduleId: String) : JourneyDestination
    data class Exam(val levelNumber: Int) : JourneyDestination
    data class Level(val levelNumber: Int) : JourneyDestination
    /** Your Walk — the whole journey on one thread. */
    data object Walk : JourneyDestination

    /** The nav route (MainShell) that opens it. */
    val route: String
        get() = when (this) {
            is Module -> "module/$moduleId"
            is Exam -> "exam/$levelNumber"
            is Level -> "level/$levelNumber"
            Walk -> "your-walk"
        }
}

/** A next step's one action: its label and where it goes. */
data class JourneyAction(val label: String, val destination: JourneyDestination)

/** The member's next step: a title, one line, and at most one action. */
data class JourneyStep(val title: String, val line: String, val action: JourneyAction?)

data class Journey(
    val stage: JourneyStage,
    /** The current level's number (the server's level_number). */
    val levelNumber: Int,
    val levelTitle: String,
    /** The current level's place on the road (1-based) and the road's length. */
    val levelPosition: Int,
    val levelCount: Int,
    val completedModules: Int,
    val totalModules: Int,
    /** "X of Y modules" · "Exam ready" · "Exam opens soon" · "Exam passed" · "Commissioned". */
    val pill: String,
    val next: JourneyStep,
    /** Journey progress in levels, 0.0–1.0 — 1.0 only at the summit. */
    val progress: Double,
) {
    /** Whole percent of the journey. 100 only at the summit — a member with
     *  the last exam still ahead reads 99 at most. */
    val percent: Int
        get() = if (stage == JourneyStage.FINISHED) 100
        else (progress * 100).roundToInt().coerceIn(0, 99)

    /** The summit card and the commissioned celebration — only at the end. */
    val summitReached: Boolean get() = stage == JourneyStage.FINISHED

    /** The Home progress line: "Level 1 of 6 · 17% of your journey". */
    val progressLine: String get() = "Level $levelNumber of $levelCount · $percent% of your journey"
}

object JourneyState {
    /**
     * The journey from the pathway summary and the CURRENT level's module
     * trail (GET /levels/{n}/modules). Without the trail (not loaded, failed)
     * the stage still comes from the summary; only the learning step's module
     * title falls back to the level's own. Null when there are no levels.
     */
    fun derive(summary: PathwaySummary?, currentModules: List<LevelModule>? = null): Journey? {
        val s = summary ?: return null
        val levels = s.levels.sortedBy { it.levelNumber }
        if (levels.isEmpty()) return null
        val last = levels.last()
        // Ushered past the final level — beyond the road's end.
        val pastTheEnd = s.currentLevel > last.levelNumber
        val current = when {
            pastTheEnd -> last
            else -> levels.firstOrNull { it.levelNumber == s.currentLevel }
                ?: levels.firstOrNull { it.status == LevelStatus.ACTIVE }
                ?: levels.first()
        }
        val isLast = current.levelNumber == last.levelNumber
        val position = levels.indexOf(current) + 1
        val n = current.levelNumber
        val nextLevel = levels.getOrNull(position)?.levelNumber ?: (n + 1)

        val trail = currentModules?.filter { it.levelNumber == n }.orEmpty()
        // The exam container's own row: completed = exam passed; NEXT = every
        // content module done and the exam published (curriculum listModulesForLevel).
        val exam = trail.firstOrNull { it.isExam }
        val examPassed = exam?.completed == true
        val examOpen = exam != null && !exam.completed && exam.status == ModuleStatus.NEXT

        val stage = when {
            pastTheEnd -> JourneyStage.FINISHED
            current.isAwaitingReview || examPassed ->
                if (isLast) JourneyStage.FINISHED else JourneyStage.AWAITING_USHER
            current.status == LevelStatus.COMPLETED ->
                if (current.examPublished) JourneyStage.EXAM_READY else JourneyStage.EXAM_SOON
            // A level with an exam container counts it among its modules, so it
            // stays "active" at 20 of 21 — the open exam row is the truth.
            examOpen -> JourneyStage.EXAM_READY
            else -> JourneyStage.LEARNING
        }

        val done = current.completedModules
        val total = current.totalModules
        val pill = when (stage) {
            JourneyStage.LEARNING -> "$done of $total modules"
            JourneyStage.EXAM_READY -> "Exam ready"
            JourneyStage.EXAM_SOON -> "Exam opens soon"
            JourneyStage.AWAITING_USHER -> "Exam passed"
            JourneyStage.FINISHED -> "Commissioned"
        }
        val next = when (stage) {
            JourneyStage.LEARNING -> learningStep(current, nextModule(trail), done, total)
            JourneyStage.EXAM_READY -> JourneyStep(
                title = "Take the Level $n exam",
                // §3 names Level N+1; the last level has none — its exam opens
                // the way to the summit itself.
                line = if (isLast) "Every module is done — the exam opens the way to your commissioning."
                else "Every module is done — the exam opens the way to Level $nextLevel.",
                action = JourneyAction("Begin the exam", JourneyDestination.Exam(n)),
            )
            JourneyStage.EXAM_SOON -> JourneyStep(
                title = "Level $n complete",
                line = "Every module is done. The exam opens soon — we'll let you know.",
                action = null,
            )
            JourneyStage.AWAITING_USHER -> JourneyStep(
                title = "Level $nextLevel is next",
                line = "You passed the Level $n exam. Your leader will open Level $nextLevel — you'll get a notice.",
                action = JourneyAction("See Level $n", JourneyDestination.Level(n)),
            )
            JourneyStage.FINISHED -> JourneyStep(
                title = "You have been commissioned",
                line = "Sent to make disciples — Matthew 28:19",
                action = JourneyAction("See your journey", JourneyDestination.Walk),
            )
        }

        // (levels before the current + the current level's fraction) / all levels.
        val fraction = when (stage) {
            JourneyStage.AWAITING_USHER, JourneyStage.FINISHED -> 1.0
            else -> if (total > 0) (done.toDouble() / total).coerceIn(0.0, 1.0) else 0.0
        }
        val progress = if (stage == JourneyStage.FINISHED) 1.0
        else ((position - 1 + fraction) / levels.size).coerceIn(0.0, 1.0)

        return Journey(
            stage = stage,
            levelNumber = n,
            levelTitle = current.title,
            levelPosition = position,
            levelCount = levels.size,
            completedModules = done,
            totalModules = total,
            pill = pill,
            next = next,
            progress = progress,
        )
    }

    /** The module to continue with — the existing continue logic (the first
     *  open module, else the first unfinished) without its old last resort of
     *  a FINISHED module, and never the exam container (that is a stage). */
    internal fun nextModule(trail: List<LevelModule>): LevelModule? {
        val lessons = trail.filter { !it.isExam }.sortedBy { it.moduleSequenceNumber }
        return lessons.firstOrNull { it.status == ModuleStatus.NEXT && !it.completed }
            ?: lessons.firstOrNull { !it.completed }
    }

    private fun learningStep(level: PathwayLevel, module: LevelModule?, done: Int, total: Int): JourneyStep {
        val verb = if (done == 0) "Start" else "Continue"
        // A module still locked (its gate not yet met) opens the level page,
        // where the member sees what stands before it.
        val opensModule = module != null && module.status != ModuleStatus.LOCKED && !module.locked
        return JourneyStep(
            title = "$verb: ${module?.title ?: level.title}",
            line = "$done of $total modules in Level ${level.levelNumber}",
            action = JourneyAction(
                "Continue",
                if (opensModule) JourneyDestination.Module(module!!.moduleId) else JourneyDestination.Level(level.levelNumber),
            ),
        )
    }
}
