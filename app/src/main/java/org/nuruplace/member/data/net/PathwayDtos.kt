// Pathway DTOs — Kotlin mirror of the Level/Module/Quiz/Exam contract, ported
// from the iOS Models/Pathway.swift + MemberAPI+Exam.swift. snake_case on the
// wire is handled by the global Json SnakeCase naming strategy.
package org.nuruplace.member.data.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/** Tolerates Postgres NUMERIC drift: node-pg serializes numerics as strings, so
 *  `quiz_pass_mark` arrives as "70.00" from prod but 70 locally. Never crashes. */
object FlexIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return 0
        val prim = el as? JsonPrimitive ?: return 0
        return prim.content.toDoubleOrNull()?.roundToInt() ?: 0
    }
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

// --- Levels --------------------------------------------------------------
// Unknown status → LOCKED (coerceInputValues + a default keeps a novel server
// vocabulary from blanking the whole page). @SerialName maps the lowercase wire.
@Serializable
enum class LevelStatus {
    @kotlinx.serialization.SerialName("completed") COMPLETED,
    @kotlinx.serialization.SerialName("active") ACTIVE,
    @kotlinx.serialization.SerialName("locked") LOCKED,
    /** The member passed this level's exam and waits for their discipler to
     *  usher them on (§1.9 usher gate). Until 2026-10-04 this decoded to
     *  LOCKED, so the member's own level read as locked. */
    @kotlinx.serialization.SerialName("awaiting_review") AWAITING_REVIEW,
}

@Serializable
data class PathwayLevel(
    val levelNumber: Int,
    val title: String,
    val theme: String? = null,
    val description: String? = null,
    val totalModules: Int = 0,
    val completedModules: Int = 0,
    // Lessons only — the exam is a step of its own, never "a module"
    // (EXPERIENCE.md §8.2 #4). total_modules counts a published exam
    // container, so a finisher read "20 of 21 done" beside "20 of 20 modules
    // done". Null from a server that predates them: the totals above stand.
    val lessonsTotal: Int? = null,
    val lessonsCompleted: Int? = null,
    val minutes: Int = 0,
    val status: LevelStatus = LevelStatus.LOCKED,
    // The level's final exam is live only once an admin publishes it. Defaults
    // TRUE so payloads from a server that predates the gate keep showing the exam.
    val examPublished: Boolean = true,
    // The exam can be TAKEN: published AND with at least one active question
    // in a published module of the level (EXPERIENCE.md §7.2 #1) — published
    // with none, the exam answers 422. Null from a server that predates it:
    // available, as before.
    val examAvailable: Boolean? = null,
    // The server's own flag beside `status: awaiting_review` (curriculum
    // getPathwaySummary) — exam passed, not yet ushered on.
    val awaitingReview: Boolean = false,
) {
    /** Exam passed, waiting on the discipler — by the status or its flag. */
    val isAwaitingReview: Boolean get() = status == LevelStatus.AWAITING_REVIEW || awaitingReview

    /** The server says the exam can be taken: published, and not said to be
     *  unavailable. Only then is it offered (§7 rule 2). */
    val examOffered: Boolean get() = examPublished && examAvailable != false

    /** Walked: every module done (the server's "completed"), or the exam
     *  passed and waiting to be ushered on. Drives the rail's seals, the
     *  milestone badges and the summit's road — never the journey's stage. */
    val walked: Boolean get() = status == LevelStatus.COMPLETED || isAwaitingReview

    /** The level's modules as a member counts them — its lessons, never the
     *  exam (§8.2 #4). Every "X of Y modules" reads these two. */
    val lessonCount: Int get() = lessonsTotal ?: totalModules

    /** Lessons done — never more than there are. */
    val lessonsDone: Int get() = (lessonsCompleted ?: completedModules).coerceAtMost(lessonCount.coerceAtLeast(0))
}

@Serializable
data class PathwaySummary(
    val currentLevel: Int = 1,
    val levels: List<PathwayLevel> = emptyList(),
)

/** A level's mastery out of 100 — exam (50) + module quizzes (30) + app
 *  participation (20). Server-computed; the client only renders it. */
@Serializable
data class LevelScore(
    val levelNumber: Int = 0,
    val total: Int = 0,
    val band: String = "",
    val exam: ExamScorePart = ExamScorePart(),
    val modules: ScorePart = ScorePart(),
    val participation: ScorePart = ScorePart(),
)

@Serializable
data class ScorePart(val score: Int = 0, val of: Int = 0, val rawPct: Int = 0)

@Serializable
data class ExamScorePart(val score: Int = 0, val of: Int = 0, val rawPct: Int = 0, val passed: Boolean = false)

// --- Modules -------------------------------------------------------------
@Serializable
enum class ModuleStatus {
    @kotlinx.serialization.SerialName("completed") COMPLETED,
    @kotlinx.serialization.SerialName("next") NEXT,
    @kotlinx.serialization.SerialName("locked") LOCKED,
}

@Serializable
data class LevelModule(
    val moduleId: String,
    val levelNumber: Int,
    val moduleSequenceNumber: Int,
    val title: String,
    val summary: String? = null,
    val estimatedMinutes: Int? = null,
    val evaluationKind: String = "none",
    @Serializable(with = FlexIntSerializer::class) val quizPassMark: Int = 0,
    val completed: Boolean = false,
    val status: ModuleStatus = ModuleStatus.LOCKED,
    val progress: Double = 0.0,
    val locked: Boolean = false,
    // The exam row's own word (only on `exit_exam`): its exam can be taken —
    // published and with questions (EXPERIENCE.md §7.2 #1). Null from an
    // older server, and on a lesson: available, as before.
    val examAvailable: Boolean? = null,
) {
    /** The level's capstone exam container — a visible, locked-until-ready row that
     *  opens the level exam rather than a lesson reader. */
    val isExam: Boolean get() = evaluationKind == "exit_exam"

    /** The exam row whose exam is not open yet (it has no questions): it
     *  reads "Opens soon" and opens nothing. Never a lesson, never a passed exam. */
    val examOpensSoon: Boolean get() = isExam && !completed && examAvailable == false
}

/** GET /levels/{n}/encouragements → { data: [...] } rows from level_encouragements
 *  (iOS MemberAPI+Exam.LevelEncouragement parity). Everything textual is nullable —
 *  content is authored gradually in the Content Studio, so the client renders
 *  whatever exists and weaves cards in trail order (afterModuleSequence, sortOrder). */
@Serializable
data class LevelEncouragement(
    val encouragementId: String,
    val levelNumber: Int = 0,
    val afterModuleSequence: Int = 0,
    val kind: String? = null,   // splash | cheer | sticker | note | celebration | nudge | verse
    val title: String? = null,
    val body: String? = null,
    val imageUrl: String? = null,
    val scriptureRef: String? = null,
    val emoji: String? = null,
    val isActive: Boolean? = null,
    val sortOrder: Int? = null,
)

@Serializable
data class ModuleDetail(
    val moduleId: String,
    val levelNumber: Int,
    val moduleSequenceNumber: Int,
    val title: String,
    val lessonContent: String = "",
    val summary: String? = null,
    val keyVerses: List<String>? = null,
    val videoUrl: String? = null,
    val evaluationKind: String = "none",
    val estimatedMinutes: Int? = null,
    @Serializable(with = FlexIntSerializer::class) val quizPassMark: Int = 0,
    val currentVersion: Int = 1,
    val locked: Boolean = false,
    // Server-authored pagination (additive; null on older servers). The reader
    // paginates on this split; lesson_content stays the whole body for fallback.
    val contentPages: List<String>? = null,
    // Per-member completion summary (additive) — when completed, the reader
    // collapses into a clean reading room.
    val completed: Boolean = false,
    val completedAt: String? = null,
    @Serializable(with = FlexIntSerializer::class) val bestScore: Int = -1,
    // A discipler's voice note on this lesson — one per congregation
    // (companion Wave 2, additive; null on older servers).
    val voiceNote: ModuleVoiceNote? = null,
) {
    val requiresQuiz: Boolean get() = evaluationKind.lowercase().contains("quiz")

    /** When the lesson was finished, said the one way (EXPERIENCE.md §8.1
     *  rule 8; final walk C2): "Mon 5 Oct · 10:08 AM" in the phone's zone —
     *  the year only when it isn't this year. completed_at comes as
     *  Postgres's text form ("2026-10-05 10:08:19.848184+03") or ISO; it
     *  read "5 Oct 2026 · 10:08", the server's wall clock. Null when unread. */
    val finishedLine: String? get() = org.nuruplace.member.util.NuruDates.dayTime(completedAt)

    /** Pages to render — the server split when present, else the whole body. */
    val pages: List<String> get() = contentPages?.takeIf { it.isNotEmpty() } ?: listOf(lessonContent)
}

// --- Module engagement (server-accumulated reading/audio/video seconds + resume page) ---
@Serializable
data class ModuleEngagement(
    @Serializable(with = FlexIntSerializer::class) val readingSeconds: Int = 0,
    @Serializable(with = FlexIntSerializer::class) val audioSeconds: Int = 0,
    @Serializable(with = FlexIntSerializer::class) val videoSeconds: Int = 0,
    @Serializable(with = FlexIntSerializer::class) val lastPage: Int = 0,
)

@Serializable
data class EngagementBody(
    val readingSeconds: Int? = null,
    val audioSeconds: Int? = null,
    val videoSeconds: Int? = null,
    val lastPage: Int? = null,
)

@Serializable
data class CompleteResult(
    val progressId: String,
    val moduleId: String,
    val isCompleted: Boolean = false,
    val duplicate: Boolean = false,
    val nextModuleUnlocked: Boolean = false,
)

// --- Quiz / Exam (server-assembled, server-scored, §1.3/§3.7) ------------
enum class QKind { SINGLE, CHECKBOX, SHORT, PARAGRAPH, SCALE }

data class QChoice(val id: String, val text: String)
data class QScale(val min: Int, val max: Int, val minLabel: String?, val maxLabel: String?)

@Serializable
data class QuizQuestion(
    val questionId: String,
    val qType: String = "",
    val questionText: String = "",
    val points: Int? = null,
    val required: Boolean? = null,
    val answerOptions: JsonElement? = null,
) {
    val kind: QKind
        get() = when (qType) {
            "checkbox" -> QKind.CHECKBOX
            "short_answer" -> QKind.SHORT
            "paragraph" -> QKind.PARAGRAPH
            "linear_scale" -> QKind.SCALE
            else -> QKind.SINGLE
        }

    /** Polymorphic answer_options: string[] | { choices:[{id,text}] } | { scale } | null. */
    fun choices(): List<QChoice> {
        val el = answerOptions ?: return emptyList()
        return runCatching {
            when (el) {
                is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.content }.map { QChoice(it, it) }
                is JsonObject -> el["choices"]?.jsonArray?.map {
                    val o = it.jsonObject
                    QChoice(o["id"]!!.jsonPrimitive.content, o["text"]!!.jsonPrimitive.content)
                } ?: emptyList()
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    fun scale(): QScale? {
        val obj = (answerOptions as? JsonObject)?.get("scale")?.jsonObject ?: return null
        return runCatching {
            QScale(
                min = obj["min"]!!.jsonPrimitive.content.toDouble().toInt(),
                max = obj["max"]!!.jsonPrimitive.content.toDouble().toInt(),
                minLabel = obj["min_label"]?.jsonPrimitive?.content,
                maxLabel = obj["max_label"]?.jsonPrimitive?.content,
            )
        }.getOrNull()
    }
}

@Serializable
data class AssembledQuiz(
    val moduleId: String = "",
    val questionCount: Int = 0,
    val questions: List<QuizQuestion> = emptyList(),
)

@Serializable
data class AssembledExam(
    val levelNumber: Int = 0,
    val questionCount: Int = 0,
    val questions: List<QuizQuestion> = emptyList(),
    /** The mark a pass needs, in percent — the exam's front door names it
     *  before question 1 (EXPERIENCE.md §9.1 rule 2; pathway d230e35). Null
     *  on an older server: the front door then names the count alone. */
    val passMark: Int? = null,
)

@Serializable
data class QuizResult(
    val attemptId: String = "",
    @Serializable(with = FlexIntSerializer::class) val scoreAchieved: Int = 0,
    val isPassed: Boolean = false,
    @Serializable(with = FlexIntSerializer::class) val passMark: Int = 0,
    val unlockedNextModuleId: String? = null,
    val requiresManualReview: Boolean = false,
    val duplicate: Boolean = false,
)

@Serializable
data class ExamResult(
    val examAttemptId: String = "",
    @Serializable(with = FlexIntSerializer::class) val scoreAchieved: Int = 0,
    val isPassed: Boolean = false,
    @Serializable(with = FlexIntSerializer::class) val passMark: Int = 0,
    val requiresManualReview: Boolean = false,
    val duplicate: Boolean = false,
)

// --- Living curriculum (intelligence Phase 3) ----------------------------
@Serializable
data class LessonExplanation(val style: String = "", val body: String = "", val cached: Boolean = false)

@Serializable
data class QuizRemediationRes(val attemptId: String = "", val body: String = "", val missed: Int = 0, val cached: Boolean = false)

// --- Request bodies + generic envelope ----------------------------------
@Serializable
data class Envelope<T>(val data: List<T> = emptyList())

@Serializable
data class CompleteBody(val reflectionText: String? = null)

@Serializable
data class QuizAnswer(val questionId: String, val givenAnswer: String)

@Serializable
data class SubmitBody(val clientMutationId: String, val answers: List<QuizAnswer>)

// --- Wave 2: "a word from your discipler" + cell reading presence ---
@Serializable
data class ModuleVoiceNote(
    val authorName: String = "",
    val avatarUrl: String? = null,
    val audioUrl: String = "",
    @Serializable(with = FlexIntSerializer::class) val durationSec: Int = 0,
)

@Serializable
data class VoiceNoteBody(val audioUrl: String, val durationSec: Int)

@Serializable
data class VoiceNoteRes(val noteId: String = "")

@Serializable
data class CommunityPresence(
    @Serializable(with = FlexIntSerializer::class) val count: Int = 0,
    val names: List<String> = emptyList(),
    val scope: String = "cell",
)

// --- Wave 3: footprints on the trail + Your Walk ---
@Serializable
data class Footprint(
    val firstName: String = "",
    val avatarUrl: String? = null,
    val completedAt: String = "",
)

@Serializable
data class FootprintsRes(
    @Serializable(with = FlexIntSerializer::class) val count: Int = 0,
    val scope: String = "cell",
    val footprints: List<Footprint> = emptyList(),
)

@Serializable
data class WalkEvent(
    val kind: String = "",     // began|module|reflection|level|certificate|verse|plan|badge
    val title: String = "",
    val detail: String? = null,
    val quote: String? = null,
    val occurredAt: String = "",
) {
    /** "2 Jul 2026" from the ISO/Postgres timestamp. */
    val dateLine: String get() {
        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(occurredAt) ?: return ""
        val (y, mo, d) = m.destructured
        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        val name = months.getOrNull(mo.toInt() - 1) ?: return ""
        return "${d.toInt()} $name $y"
    }
}

@Serializable
data class WalkRes(val data: List<WalkEvent> = emptyList())
