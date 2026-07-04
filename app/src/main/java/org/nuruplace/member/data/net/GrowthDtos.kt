// Growth / daily-rhythm DTOs — Kotlin mirror of the devotional, memory-verse,
// reading-plan, prayer-journal and saved-verse contracts (ported from the iOS
// Models/Growth.swift). snake_case handled by the global Json naming strategy.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

// --- Devotional ---
@Serializable
data class Devotional(
    val devotionalId: String,
    val dayNumber: Int = 0,
    val series: String? = null,
    val title: String = "",
    val scriptureRef: String? = null,
    val scriptureText: String? = null,
    val body: String = "",
    val reflectionPrompt: String? = null,
    val audioUrl: String? = null,
    val videoUrl: String? = null,
    val myReflection: String? = null,
)

// --- Memory verses ---
@Serializable
data class MemoryVerseRow(
    val memoryVerseId: String,
    val reference: String = "",
    val verseText: String = "",
    val version: String = "WEB",
    val weekNumber: Int? = null,
    val status: String = "learning",   // learning | mastered
    val bestMatchPct: Int = 0,
) {
    val isMastered: Boolean get() = status == "mastered"
}

// --- Reading plans ---
@Serializable
data class ReadingPlanRow(
    val planId: String,
    val code: String? = null,
    val title: String = "",
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val dayCount: Int = 0,
    val currentDay: Int? = null,
    val completedDays: List<Int>? = null,
    val enrolled: Boolean = false,
    val completedAt: String? = null,
)

@Serializable
data class PlanSegment(
    val segmentId: String,
    val sort: Int = 0,
    val kind: String = "reading",   // devotional | scripture | video | talk | reading
    val title: String = "",
    val reference: String? = null,
    val content: String? = null,
    val videoUrl: String? = null,
    val imageUrl: String? = null,
    val completed: Boolean = false,
)

@Serializable
data class ReadingPlanDay(
    val dayNumber: Int,
    val reference: String = "",
    val title: String? = null,
    val content: String? = null,
    val segments: List<PlanSegment>? = null,
    val completed: Boolean? = null,
)

@Serializable
data class ReadingPlanDetail(
    val planId: String,
    val title: String = "",
    val subtitle: String? = null,
    val description: String? = null,
    val category: String? = null,
    val imageUrl: String? = null,
    val dayCount: Int = 0,
    val currentDay: Int? = null,
    val completedDays: List<Int>? = null,
    val enrolled: Boolean = false,
    val days: List<ReadingPlanDay> = emptyList(),
)

// --- Prayer journal ---
@Serializable
data class PrayerEntry(
    val entryId: String,
    val title: String? = null,
    val body: String = "",
    val isAnswered: Boolean = false,
    val answeredNote: String? = null,
    val answeredAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

// --- Saved verses (Verse Library) ---
@Serializable
data class SavedVerse(
    val savedVerseId: String,
    val reference: String = "",
    val version: String = "WEB",
    val verseText: String? = null,
    val note: String? = null,
    val createdAt: String = "",
)

// --- Request bodies / small responses ---
@Serializable
data class SavedFlag(val saved: Boolean = false)

@Serializable
data class DevotionalReflectionBody(val devotionalId: String, val body: String)

@Serializable
data class PracticeBody(val memoryVerseId: String, val matchPct: Int)

@Serializable
data class CompleteDayBody(val dayNumber: Int)

// --- Plan-day reflection (per-day journal; UPSERT, §2.1/§3.6 idempotency) ---
@Serializable
data class PlanDayReflection(
    val id: String? = null,
    val planId: String = "",
    val dayNumber: Int = 0,
    val body: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class PlanDayReflectionEnv(val data: PlanDayReflection? = null)

@Serializable
data class SaveReflectionBody(val body: String, val clientMutationId: String)

/** POST growth/segments/{id}/complete → tells us if finishing this segment completed the day. */
@Serializable
data class SegmentCompleteResult(
    val segmentId: String = "",
    val dayNumber: Int = 0,
    val dayCompleted: Boolean = false,
)

@Serializable
data class PrayerUpsertBody(
    val entryId: String,
    val title: String? = null,
    val body: String,
    val isAnswered: Boolean = false,
    val answeredNote: String? = null,
    val clientMutationId: String,
)

@Serializable
data class VerseUpsertBody(
    val savedVerseId: String,
    val reference: String,
    val version: String? = null,
    val verseText: String? = null,
    val note: String? = null,
    val clientMutationId: String,
)

/** POST /me/prayers/{id}/share-to-wall — 201 with the wall post id (idempotent). */
@Serializable
data class ShareToWallRes(val postId: String = "")
