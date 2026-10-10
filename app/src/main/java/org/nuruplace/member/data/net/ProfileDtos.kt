// Profile / growth DTOs — spiritual gifts ("Your Calling"), resources library,
// Nuru assistant, and the member's growth scores. Ported from the iOS
// Models/Gifts.swift + Home.swift (ScoresSummary) + the feature-local DTOs.
package org.nuruplace.member.data.net

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

// --- Growth scores (GET /me/scores) ---
@Serializable
data class GrowthScore(val score: Int = 0, val band: String? = null)

@Serializable
data class ScoreOverall(val score: Int = 0, val band: String = "")

/** Rolling growth trend — this 28-day window vs the previous 28 days. */
@Serializable
data class ScoreTrend(
    val windowDays: Int = 28,
    val previous: Int = 0,
    val delta: Int = 0,
    val direction: String = "flat", // "up" | "down" | "flat"
    val domains: Map<String, Int> = emptyMap(),
) {
    val isUp: Boolean get() = direction == "up"
    val isDown: Boolean get() = direction == "down"
}

@Serializable
data class ScoresSummary(
    val overall: ScoreOverall = ScoreOverall(),
    val habits: GrowthScore = GrowthScore(),
    val curriculum: GrowthScore = GrowthScore(),
    val attendance: GrowthScore = GrowthScore(),
    val word: GrowthScore = GrowthScore(),
    val prayer: GrowthScore = GrowthScore(),
    val trend: ScoreTrend? = null,
)

// --- Spiritual gifts ---
@Serializable
data class GiftAssessment(
    val assessmentId: String = "",
    val scores: Map<String, Double> = emptyMap(),
    val topGifts: List<String> = emptyList(),
    val submittedAt: String = "",
    val personaSummary: String? = null,
)

@Serializable
data class GiftPersona(
    val giftKey: String,
    val title: String = "",
    val personaName: String = "",
    val tagline: String? = null,
    val summary: String = "",
    val strengths: List<String> = emptyList(),
    val serving: List<String> = emptyList(),
    val emoji: String? = null,
    val color: String? = null,
)

@Serializable
data class ServingTrack(
    val trackKey: String,
    val title: String = "",
    val description: String = "",
    val giftKeys: List<String> = emptyList(),
    val matchCount: Int = 0,
)

@Serializable
data class MyGifts(
    val assessment: GiftAssessment? = null,
    val personas: List<GiftPersona> = emptyList(),
    val suggestedTracks: List<ServingTrack> = emptyList(),
)

@Serializable
data class GiftQuestion(val questionId: String, val giftKey: String = "", val prompt: String = "")

@Serializable
data class GiftQuestionSet(val setId: String = "", val aiInfluenced: Boolean = false, val data: List<GiftQuestion> = emptyList())

@Serializable
data class GiftAnswerInput(val questionId: String, val value: Int)

@Serializable
data class GiftSubmitBody(val clientMutationId: String, val setId: String, val answers: List<GiftAnswerInput>)

// --- Resources library ---
@Serializable
data class ResourceRow(
    val resourceId: String,
    val title: String = "",
    val author: String = "",
    val kind: String = "",
    val durationLabel: String = "",
    val url: String? = null,
)

// --- Nuru assistant ---
@Serializable
data class AssistantMessage(val role: String, val text: String)

@Serializable
data class AssistantHistoryRes(val messages: List<AssistantMessage> = emptyList())

// conversation_id / context_limit ground the reply on a chat the member can
// access (composer AI drafting). The server's zod fields are plain `.optional()`
// (explicit null is rejected), so unset values must be OMITTED from the JSON —
// EncodeDefault(NEVER) overrides the client's global encodeDefaults = true.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AssistantChatBody(
    val messages: List<AssistantMessage>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val conversationId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val contextLimit: Int? = null,
)

@Serializable
data class AssistantReplyRes(val reply: String = "")

// --- Certificates (GET /certificates — the caller's issued certificates) ---
@Serializable
data class Certificate(
    val certificateId: String = "",
    val levelNumber: Int? = null,
    val verificationCode: String = "",
    val pdfObjectKey: String = "",
    val issuedAt: String = "",
    val downloadUrl: String = "",
)

// --- Sunday Letters + AI consent (intelligence layer, Phase 1) ---
/** One step the letter invites the member to take next. Server-COMPUTED from
 *  real enrollment/progress — never written by the model — so it can't point
 *  at something that doesn't exist. `route` is only "module" or "pathway"
 *  today; unknown routes are tolerated and simply don't render a button. */
@Serializable
data class LetterNextStepParams(
    // The server writes params in camelCase (letters.ts: { moduleId }); the
    // global snake_case strategy reads "module_id" — and it renames
    // @SerialName names too — so the letter's "Module X is waiting" never
    // found its lesson. @JsonNames adds the camelCase key as written.
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.json.JsonNames("moduleId") val moduleId: String? = null,
)

@Serializable
data class LetterNextStep(
    val label: String = "",
    val route: String = "",
    val params: LetterNextStepParams? = null,
)

/** Sunday Letter v2 (backend migration 186). Every v2 field can arrive ABSENT
 *  (pre-v2 rows predate the columns) or as an explicit JSON null — kotlinx
 *  distinguishes those two, and treating them differently is exactly the bug
 *  class that cost us guest video earlier (an explicit `"cell_id": null` was
 *  rejected by a schema that only tolerated absence). So: every new field is
 *  nullable WITH a default, and the accessors below normalise blank strings to
 *  null too, because a whitespace title is as unrenderable as a missing one. */
@Serializable
data class PastoralLetter(
    val letterId: String = "",
    val weekOf: String = "",
    val body: String = "",
    val scriptureRef: String? = null,
    val createdAt: String = "",
    val readAt: String? = null,
    // --- v2 (all null on letters written before migration 186) ---
    val title: String? = null,
    val salutation: String? = null,
    val theme: String? = null,
    val imageKey: String? = null,
    // The wire's shape (backend intelligence/letters.ts rowFromDb, since v2
    // #410): `highlights` is the week's moments — an ARRAY of strings — and
    // `next_step` / `share_line` sit beside it. They were read as one object
    // {moments, next_step, share_line}, so every v2 letter failed to decode
    // and Home said "Your letter arrives Sunday evening" over a letter that
    // was waiting (and "Read it" opened the You tab). As iOS reads them.
    @kotlinx.serialization.SerialName("highlights") val highlightLines: List<String> = emptyList(),
    @kotlinx.serialization.SerialName("next_step") val rawNextStep: LetterNextStep? = null,
    @kotlinx.serialization.SerialName("share_line") val rawShareLine: String? = null,
    // --- v3, the editorial letter (pathway#512, owner 2026-10-07). All
    // DERIVED by the server, none written by the model, and all optional
    // here: a server without #512 sends none of them, and the letter then
    // falls back to what v2 carries (EditorialLetter's fallbacks). ---
    /** This letter's place in the member's series, from 1. */
    val issueNo: Int? = null,
    val readingMinutes: Int? = null,
    /** The body split on blank lines (the model writes two). */
    val paragraphs: List<String> = emptyList(),
    /** The verse in full, from the church's daily-verse library. */
    val scripture: LetterScripture? = null,
    /** From the eye-checked nature library; the bundled theme art is the fallback. */
    val photo: LetterPhoto? = null,
    /** True counts of the letter's own week, 0 to 3, never a zero. */
    val figures: List<LetterFigure> = emptyList(),
    val signedBy: LetterSignedBy? = null,
    /** GET it (authed) for the one-page A4 letter — a path on the API's host. */
    val pdfUrl: String? = null,
) {
    val isUnread: Boolean get() = readAt == null

    /** Blank-safe accessors — a pre-v2 letter (or a model that returned an
     *  empty string) must fall back honestly rather than render an empty
     *  heading. Never invent a title; the caller decides what to show. */
    val displayTitle: String? get() = title?.trim()?.takeIf { it.isNotEmpty() }
    val displaySalutation: String? get() = salutation?.trim()?.takeIf { it.isNotEmpty() }
    val displayScripture: String? get() = scriptureRef?.trim()?.takeIf { it.isNotEmpty() }

    /** Which illustration to draw. Falls back through image_key → theme →
     *  the resolver's own default, so this is never empty. */
    val artKey: String
        get() = imageKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: theme?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""

    val moments: List<String>
        get() = highlightLines.mapNotNull { it.trim().takeIf(String::isNotEmpty) }

    val nextStep: LetterNextStep?
        get() = rawNextStep?.takeIf { it.label.isNotBlank() && it.route.isNotBlank() }

    val shareLine: String? get() = rawShareLine?.trim()?.takeIf { it.isNotEmpty() }
}

/** v3: the verse the letter rests on — its words when the church's library
 *  has them (`text` null: the reference alone, as before). */
@Serializable
data class LetterScripture(val ref: String = "", val text: String? = null, val version: String? = null)

/** v3: the letter's photograph and the words that go with it. */
@Serializable
data class LetterPhoto(val id: String = "", val url: String = "", val alt: String = "", val caption: String = "")

/** v3: one true figure of the week — "5 of 7" · "days in the Word". */
@Serializable
data class LetterFigure(val value: String = "", val label: String = "")

/** v3: who signs the letter — "Pastor Moses" · "Nuru Place". */
@Serializable
data class LetterSignedBy(val name: String = "", val role: String = "")

@Serializable
data class LatestLetterRes(val letter: PastoralLetter? = null)

@Serializable
data class LetterReadRes(val letterId: String = "", val readAt: String = "")

@Serializable
data class AiConsentRes(val optOut: Boolean = false)

@Serializable
data class AiConsentBody(val optOut: Boolean)
