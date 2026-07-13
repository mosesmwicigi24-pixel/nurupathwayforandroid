// Home-dashboard DTOs — next-best-action, tailored verse, streak. RhythmToday
// lives in Dtos.kt. Ported from the iOS Models/Home.swift.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

@Serializable
data class NextActionParams(val moduleId: String? = null, val levelNumber: Int? = null)

@Serializable
data class NextAction(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val ctaLabel: String = "",
    val route: String = "",
    val accent: String = "",
    val priority: Int = 0,
    val params: NextActionParams? = null,
)

@Serializable
data class NextActionEnvelope(val action: NextAction? = null)

@Serializable
data class TailoredVerse(
    val reference: String = "",
    val version: String = "WEB",
    val theme: String? = null,
    val reason: String? = null,
    // The season Nuru sensed (title-cased library theme) when mood-driven.
    val mood: String? = null,
    val text: String? = null,
    // The day's tableau photograph (server-curated, theme-matched).
    val art: VerseArt? = null,
)

@Serializable
data class VerseArt(
    val url: String = "",
    val alt: String = "",
)

/** GET/POST /me/home/verse/reactions — community reactions on today's verse
 *  (counts per emoji + mine + total; exactly one per member per day — switching moves it). */
@Serializable
data class VerseReactions(
    val counts: Map<String, Int> = emptyMap(),
    val mine: String? = null,
    val total: Int = 0,
)

@Serializable
data class VerseReactionBody(val emoji: String)

@Serializable
data class Streak(val current: Int = 0, val longest: Int = 0)

@Serializable
data class Badge(
    val code: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",   // journey | consistency | community | service
    val iconKey: String? = null,
    val awardedAt: String? = null,
)

@Serializable
data class Achievements(val streak: Streak = Streak(), val badges: List<Badge> = emptyList())

@Serializable
data class RhythmBody(val kind: String)

/** GET /me/home/greeting — Nuru's daily one-line word for this member. */
@Serializable
data class DailyGreeting(val greeting: String = "")

// --- The liturgy Home + community intelligence (Phase 4) ---
@Serializable
data class HomeLiturgy(
    val part: String = "morning",
    val season: String = "ordinary",
    val isSunday: Boolean = false,
    val line: String = "",
    val scriptureRef: String? = null,
)

@Serializable
data class CommunityMoment(
    val momentId: String = "",
    val userId: String = "",
    val fullName: String = "",
    val avatarUrl: String? = null,
    val kind: String = "",
    val title: String = "",
    val occurredAt: String = "",
    val amenCount: Int = 0,
    val heartCount: Int = 0,
    val fireCount: Int = 0,
    val myBlessing: String? = null,
)

@Serializable
data class BlessBody(val kind: String)

@Serializable
data class BlessRes(val blessed: Boolean = false, val kind: String = "")

// --- Wave 1 echoes: the app remembers you ---
@Serializable
data class HomeEcho(
    val kind: String = "",
    val body: String = "",
    val quote: String? = null,
    val ref: String? = null,
)

@Serializable
data class HomeEchoEnvelope(val echo: HomeEcho? = null)

