// Profile / growth DTOs — spiritual gifts ("Your Calling"), resources library,
// Nuru assistant, and the member's growth scores. Ported from the iOS
// Models/Gifts.swift + Home.swift (ScoresSummary) + the feature-local DTOs.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

// --- Growth scores (GET /me/scores) ---
@Serializable
data class GrowthScore(val score: Int = 0, val band: String? = null)

@Serializable
data class ScoreOverall(val score: Int = 0, val band: String = "")

@Serializable
data class ScoresSummary(
    val overall: ScoreOverall = ScoreOverall(),
    val habits: GrowthScore = GrowthScore(),
    val curriculum: GrowthScore = GrowthScore(),
    val attendance: GrowthScore = GrowthScore(),
    val word: GrowthScore = GrowthScore(),
    val prayer: GrowthScore = GrowthScore(),
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

@Serializable
data class AssistantChatBody(val messages: List<AssistantMessage>)

@Serializable
data class AssistantReplyRes(val reply: String = "")
