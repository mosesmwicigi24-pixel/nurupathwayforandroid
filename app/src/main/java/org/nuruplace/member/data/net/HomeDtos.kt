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
    // Seven-bands: a short encouragement quote that replaces the "Chosen for
    // your season" ribbon when present. Optional — absent on older backends.
    val encouragement: Encouragement? = null,
)

@Serializable
data class VerseArt(
    val url: String = "",
    val alt: String = "",
)

@Serializable
data class Encouragement(
    val text: String,
    val author: String,
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
    // The hour's tableau photograph (server-curated per part + day).
    val art: VerseArt? = null,
    // Seven-bands: the finer-grained time-of-day band the server used to pick
    // `art` (server-only concern today — no client image work reads this).
    val band: String? = null,
    // A second, quieter exhortation line rendered below `line` when present.
    val charge: String? = null,
    // An optional companion verse rendered in serif italic under the liturgy.
    val verseLine: VerseLine? = null,
    // Phase 2 — the pastor's own recorded voice for the CURRENT band. Both
    // null whenever that band has no recording (the normal case for most
    // bands, most of the time — mixed coverage is permanent, not a gap; see
    // LiturgyVoice.kt's header and LiturgySpeech.kt's recordedLiturgyUrlIfPlayable).
    // Offered on the card as a SEPARATE control from Listen, never a silent
    // substitute for it — the recording is a standing per-band asset, while
    // this whole liturgy is recomposed daily, so it must never be presented
    // as a reading of today's specific line. Never sent alongside who
    // recorded it — no member-facing identity here.
    val recordedAudioUrl: String? = null,
    val recordedAudioDurationSec: Int? = null,
)

@Serializable
data class VerseLine(
    val reference: String,
    val text: String,
)

// --- Admin-only: the pastor's recorded liturgy (band-scoped, upsert) ---
@Serializable
data class LiturgyRecordingUploadRes(
    val band: String = "",
    val audioUrl: String = "",
    val durationSec: Int = 0,
)

/** One row per band from GET admin/liturgy/recordings — ALWAYS 7 rows in
 *  clock order (sunrise..midnight); audioUrl/durationSec/recordedAt are null
 *  for a band with no recording yet. */
@Serializable
data class LiturgyRecordingStatus(
    val band: String = "",
    val audioUrl: String? = null,
    val durationSec: Int? = null,
    val recordedAt: String? = null,
)

@Serializable
data class DeleteLiturgyRecordingRes(val deleted: Boolean = false)

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

/** GET /home/events — up to 5 soonest curated occurrences for Home (server-capped, never client-capped). */
@Serializable
data class HomeEventRow(
    val occurrenceId: String,
    val seriesId: String = "",
    val title: String = "",
    val venue: String? = null,
    val startsAt: String = "",
    val primaryImageUrl: String? = null,
    val myRsvp: String? = null,
)

