// Discipleship Hub DTOs — Kotlin mirror of iOS Models/Discipleship.swift and
// the wire truth in backend discipleship/service.ts (MyDiscipleship). Served as
// { data: … } by GET /me/discipleship; pure read (no side effects, §1.9 — the
// Hub reflects progression, it never advances it). Everything the wire may omit
// is a tolerant optional so a sparse payload (no discipler yet, uncomputed
// scores) still decodes.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

@Serializable
data class DiscipleshipEnv(val data: Discipleship)

@Serializable
data class Discipleship(
    /** Resolved discipler (explicit relationship edge, else the cell leader); null = unpaired. */
    val discipler: HubDiscipler? = null,
    /** Present only when a 1:1 DM already exists — a GET never creates one. */
    val dmConversationId: String? = null,
    /** False for minors — chat blocks DMs, so the Hub swaps the message CTA for a note. */
    val canMessage: Boolean = false,
    val progression: HubProgression = HubProgression(),
    val scores: HubScores = HubScores(),
    /** The member's own reflections, most-recent first (server caps at 20). */
    val reflections: List<HubReflection> = emptyList(),
    val nextMeetingAt: String? = null,
    val notes: List<HubNote> = emptyList(),
)

@Serializable
data class HubDiscipler(
    val userId: String = "",
    val fullName: String = "",
    val avatarUrl: String? = null,
    val roleLabel: String = "",
    val cellName: String? = null,
    val establishedAt: String? = null,
)

@Serializable
data class HubProgression(
    val currentLevel: Int = 1,
    val levelTitle: String = "",
    val streakDays: Int = 0,
    val modulesCompleted: Int = 0,
    val modulesTotal: Int = 0,
    /** A pending level_advancement exists — awaiting the discipler's usher. */
    val awaitingReview: Boolean = false,
    val awaitingLevel: Int? = null,
)

@Serializable
data class HubScores(
    val overall: Int? = null,
    val word: Int? = null,
    val prayer: Int? = null,
    val habits: Int? = null,
    val curriculum: Int? = null,
    val attendance: Int? = null,
)

@Serializable
data class HubReflection(
    val moduleId: String = "",
    val moduleTitle: String = "",
    val levelNumber: Int = 0,
    /** approved | pending | returned | deferred. */
    val state: String = "",
    val submittedAt: String = "",
    val feedbackNotes: String? = null,
)

@Serializable
data class HubNote(
    val topic: String = "",
    val body: String = "",
    val metAt: String = "",
    val nextMeetingAt: String? = null,
)
