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
}

@Serializable
data class PathwayLevel(
    val levelNumber: Int,
    val title: String,
    val theme: String? = null,
    val description: String? = null,
    val totalModules: Int = 0,
    val completedModules: Int = 0,
    val minutes: Int = 0,
    val status: LevelStatus = LevelStatus.LOCKED,
    // The level's final exam is live only once an admin publishes it. Defaults
    // TRUE so payloads from a server that predates the gate keep showing the exam.
    val examPublished: Boolean = true,
)

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
) {
    /** The level's capstone exam container — a visible, locked-until-ready row that
     *  opens the level exam rather than a lesson reader. */
    val isExam: Boolean get() = evaluationKind == "exit_exam"
}

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
) {
    val requiresQuiz: Boolean get() = evaluationKind.lowercase().contains("quiz")

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

// --- Request bodies + generic envelope ----------------------------------
@Serializable
data class Envelope<T>(val data: List<T> = emptyList())

@Serializable
data class CompleteBody(val reflectionText: String? = null)

@Serializable
data class QuizAnswer(val questionId: String, val givenAnswer: String)

@Serializable
data class SubmitBody(val clientMutationId: String, val answers: List<QuizAnswer>)
