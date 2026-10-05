// The member's journey state (pathway docs/EXPERIENCE.md §3, Cycle 1) — derived
// ONCE from GET /me/pathway (the current level's row: status, exam_published,
// exam_available, completed/total modules) and the next incomplete module, then shown in the
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
    /** Every module is done and the level's exam can be taken (published,
     *  with questions — `exam_available`). */
    EXAM_READY,
    /** Every module is done; the exam is not published yet, or has nothing
     *  to ask yet (§7.2 #1). */
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

/** Home's progress line: the bold fact, then the rest ("3 of 10 modules" ·
 *  " in Level 2"; or the step's own title — "Take the Level 1 exam"). */
data class JourneyLine(val bold: String, val rest: String) {
    val text: String get() = bold + rest
}

data class Journey(
    val stage: JourneyStage,
    /** The current level's number (the server's level_number). */
    val levelNumber: Int,
    val levelTitle: String,
    /** The current level's place on the road (1-based) and the road's length. */
    val levelPosition: Int,
    val levelCount: Int,
    /** The current level's lessons done and in all — the exam is its own
     *  step, never one of them (§8.2 #4). */
    val completedModules: Int,
    val totalModules: Int,
    /** "X of Y modules" · "Exam ready" · "Exam opens soon" · "Exam passed" · "Commissioned". */
    val pill: String,
    /** The next step's kicker — "Continue · Level 1", "Exam ready · Level 1"… */
    val kicker: String,
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

    /** "20 of 20 modules" — the current level's lessons; "Modules open soon"
     *  for a level with none published yet (never "0 of 0 modules"). */
    val modulesLine: String
        get() = when {
            totalModules <= 0 -> "Modules open soon"
            // A first day says what lies ahead, never a zero count (§9.2 #4, §7.4 #9).
            completedModules == 0 -> "$totalModules modules"
            else -> "$completedModules of $totalModules modules"
        }

    /** The Pathway header's one line (EXPERIENCE.md §8.2 #1): where the
     *  member is on the road and how far through the level — "Level 1 of 6 ·
     *  20 of 20 modules". The greeting belongs to Home alone. */
    val headerLine: String get() = "Level $levelPosition of $levelCount · $modulesLine"

    /** Home's progress line: the modules while they are being walked, else
     *  the step itself — never "0 modules left before Level 2". */
    val progressLine: JourneyLine
        get() = if (stage == JourneyStage.LEARNING && totalModules > 0)
            JourneyLine(modulesLine, " in Level $levelNumber")
        else JourneyLine(next.title, "")
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
        val nextRow = levels.getOrNull(position)
        val nextLevel = nextRow?.levelNumber ?: (n + 1)
        // The next level has no lessons yet (Levels 2–6 in production today):
        // nobody can open it, so no one is promised to (§9.1 rule 7).
        val nextPreparing = nextRow != null && nextRow.lessonCount <= 0

        val trail = currentModules?.filter { it.levelNumber == n }.orEmpty()
        // The exam container's own row: completed = exam passed; NEXT = every
        // content module done and the exam published (curriculum listModulesForLevel).
        val exam = trail.firstOrNull { it.isExam }
        val examPassed = exam?.completed == true
        val examOpen = exam != null && !exam.completed && exam.status == ModuleStatus.NEXT
        // Published is not enough: the exam is offered only when the server
        // says it can be taken — it has questions (`exam_available`, on the
        // level and on its exam row; absent = available, an older server).
        // An exam published with none answered 422 behind "Exam ready" (§7.2 #1).
        val examAvailable = current.examAvailable != false && exam?.examAvailable != false

        val stage = when {
            pastTheEnd -> JourneyStage.FINISHED
            current.isAwaitingReview || examPassed ->
                if (isLast) JourneyStage.FINISHED else JourneyStage.AWAITING_USHER
            current.status == LevelStatus.COMPLETED ->
                if (current.examPublished && examAvailable) JourneyStage.EXAM_READY else JourneyStage.EXAM_SOON
            // A level with an exam container counts it among its modules, so it
            // stays "active" at 20 of 21 — the open exam row is the truth.
            examOpen -> if (examAvailable) JourneyStage.EXAM_READY else JourneyStage.EXAM_SOON
            else -> JourneyStage.LEARNING
        }

        // Lessons, never the exam (§8.2 #4): production counts the exam
        // container in total_modules, so a finisher read "20 of 21".
        val done = current.lessonsDone
        val total = current.lessonCount
        // A level the member was ushered into before any module was published
        // (Levels 2–6 today): no "0 of 0 modules", no step to take yet.
        val preparing = stage == JourneyStage.LEARNING && total <= 0
        val pill = when (stage) {
            // Nothing done yet: what lies ahead ("10 modules"), never "0 of 10"
            // (§9.2 #4 — a first day has no zero counts).
            JourneyStage.LEARNING -> when {
                preparing -> "Modules open soon"
                done == 0 -> "$total modules"
                else -> "$done of $total modules"
            }
            JourneyStage.EXAM_READY -> "Exam ready"
            JourneyStage.EXAM_SOON -> "Exam opens soon"
            JourneyStage.AWAITING_USHER -> "Exam passed"
            JourneyStage.FINISHED -> "Commissioned"
        }
        val verb = if (done == 0) "Start" else "Continue"
        val kicker = when (stage) {
            JourneyStage.LEARNING -> if (preparing) "Modules open soon · Level $n" else "$verb · Level $n"
            JourneyStage.EXAM_READY -> "Exam ready · Level $n"
            JourneyStage.EXAM_SOON -> "Exam opens soon · Level $n"
            JourneyStage.AWAITING_USHER -> "Exam passed · Level $n"
            JourneyStage.FINISHED -> "Commissioned"
        }
        val next = when (stage) {
            JourneyStage.LEARNING ->
                if (preparing) JourneyStep(
                    title = "Level $n is being prepared",
                    line = "Its modules open soon — we'll let you know.",
                    action = null,
                )
                else learningStep(current, nextModule(trail), verb, done, total)
            JourneyStage.EXAM_READY -> JourneyStep(
                title = "Take the Level $n exam",
                // §3 names Level N+1; the last level has none — its exam opens
                // the way to the summit itself.
                line = if (isLast) "Every module is done — the exam opens the way to being sent."
                else "Every module is done — the exam opens the way to Level $nextLevel.",
                action = JourneyAction("Begin the exam", JourneyDestination.Exam(n)),
            )
            JourneyStage.EXAM_SOON -> JourneyStep(
                title = "Level $n complete",
                line = "Every module is done. The exam opens soon — we'll let you know.",
                action = null,
            )
            // "Level 2 is being prepared — we'll let you know" while it has no
            // lessons (EXPERIENCE.md §9.2 #7): Eli was told "Your leader will
            // open Level 2" for a level with none, and no cell has a leader.
            JourneyStage.AWAITING_USHER -> if (nextPreparing) JourneyStep(
                title = "Level $nextLevel is being prepared",
                line = "You passed the Level $n exam — we'll let you know when Level $nextLevel opens.",
                action = JourneyAction("See Level $n", JourneyDestination.Level(n)),
            ) else JourneyStep(
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

        // (levels before the current + the current level's fraction) / all
        // levels — the fraction is whole once every module is done.
        val fraction = when (stage) {
            JourneyStage.LEARNING -> if (total > 0) (done.toDouble() / total).coerceIn(0.0, 1.0) else 0.0
            else -> 1.0
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
            kicker = kicker,
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

    /** §3's learning row: "Continue (or Start, when X = 0): «module title»"
     *  — the verb leads the kicker and names the action, the module is the
     *  title. */
    private fun learningStep(level: PathwayLevel, module: LevelModule?, verb: String, done: Int, total: Int): JourneyStep {
        // A module still locked (its gate not yet met) opens the level page,
        // where the member sees what stands before it.
        val opensModule = module != null && module.status != ModuleStatus.LOCKED && !module.locked
        return JourneyStep(
            title = module?.title ?: level.title,
            line = (if (done == 0) "$total modules" else "$done of $total modules") + " in Level ${level.levelNumber}",
            action = JourneyAction(
                verb,
                if (opensModule) JourneyDestination.Module(module!!.moduleId) else JourneyDestination.Level(level.levelNumber),
            ),
        )
    }
}
