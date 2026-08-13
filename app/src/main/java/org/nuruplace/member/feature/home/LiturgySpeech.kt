// Pure logic behind the spoken liturgy (LiturgyVoice.kt owns the actual
// TextToSpeech engine + AudioManager focus — nothing Android-framework-heavy
// lives here on purpose, so all of it is exercised by plain JUnit tests with
// no Robolectric/device needed, matching the house rule established by
// LiveDockLogic.kt / LiveInteractions.kt's abbreviateCount).
package org.nuruplace.member.feature.home

import android.speech.tts.TextToSpeech
import java.util.Locale
import org.nuruplace.member.data.net.HomeLiturgy

/**
 * Builds the words TextToSpeech actually reads for [liturgy] — the SAME
 * render order the card uses (LiturgyCards.kt's `LiturgyCard`): the hour's
 * line, the quieter charge if present, then the companion verse (its text,
 * then its reference read naturally) OR the bare scripture reference when
 * there's no companion verse (card's own either/or rule). Sentence-final
 * punctuation is added only where the source text is missing it, so the
 * synthesizer paces its pauses the way a person praying would, rather than
 * running everything into one breath.
 */
fun liturgySpeechScript(liturgy: HomeLiturgy): String {
    val parts = mutableListOf<String>()
    liturgy.line.trim().takeIf { it.isNotEmpty() }?.let { parts += it.withTrailingStop() }
    liturgy.charge?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it.withTrailingStop() }

    val verse = liturgy.verseLine?.takeIf { it.text.isNotBlank() }
    if (verse != null) {
        parts += verse.text.trim().withTrailingStop()
        parts += "${naturalizeScriptureReference(verse.reference)}."
    } else {
        liturgy.scriptureRef?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { parts += "${naturalizeScriptureReference(it)}." }
    }
    return parts.joinToString(" ")
}

private fun String.withTrailingStop(): String =
    if (isEmpty() || last() in ".!?\"”") this else "$this."

// "Book Chapter:Verse" or "Book Chapter:VerseStart-VerseEnd", anchored at the
// end of the string. Non-greedy book capture + matchEntire lets multi-word /
// numeral-prefixed book names ("1 Corinthians 13:4") resolve correctly via
// backtracking, since the chapter:verse suffix is the only fixed anchor.
private val REFERENCE_PATTERN = Regex("""^(.+?)\s+(\d+):(\d+)(?:-(\d+))?$""")

/**
 * "Psalm 23:1" -> "Psalm 23, verse 1"; "John 15:1-4" -> "John 15, verses 1 to
 * 4". The synthesizer already expands digits into words on its own (that's
 * the engine's job, not ours) — this only turns the chapter:verse PUNCTUATION
 * into words, so it reads as a natural pause instead of "colon" or a run-on
 * number. A reference that doesn't match the "Book Chapter:Verse[-Verse]"
 * shape (a bare "Psalm 23", a cross-chapter range, anything unexpected)
 * passes through unchanged rather than risk mangling it.
 */
fun naturalizeScriptureReference(reference: String): String {
    val trimmed = reference.trim()
    val match = REFERENCE_PATTERN.matchEntire(trimmed) ?: return trimmed
    val (book, chapter, verseStart, verseEnd) = match.destructured
    return if (verseEnd.isBlank()) {
        "$book $chapter, verse $verseStart"
    } else {
        "$book $chapter, verses $verseStart to $verseEnd"
    }
}

/**
 * Phase 2 — the pastor's own recorded voice, offered as a SEPARATE control
 * from Listen, never a silent substitute for it. Design correction (owner,
 * 2026-08-12): the recording is keyed per (congregation, band) — a STANDING
 * asset, deliberately not regenerated per day, because 49 recordings a week
 * is unsustainable. The liturgy TEXT, meanwhile, is recomposed every day
 * (new Scripture spine, new words each morning). If a recording simply
 * replaced synthesis behind one "Listen" button, a member reading today's
 * actual line on the card would hear the pastor saying something else
 * entirely — it would read as broken. So the two are kept as genuinely
 * separate affordances on the card (LiturgyCards.kt): Listen ALWAYS reads
 * today's actual text via synthesis; a second control, offered only when a
 * playable recording exists, is the pastor's standing word for that hour —
 * never presented as a reading of today's specific line. Both still share
 * the SAME playback engine below (audio focus, Radio ducking,
 * decline-while-broadcasting, silence under TalkBack) — only the labelling
 * and the two entry points differ. [LiturgyPlaybackSource] is ORTHOGONAL to
 * [LiturgyVoiceStatus] on purpose (that enum is the PLAYBACK STATE machine —
 * preparing/idle/speaking/unavailable — and stays true regardless of which
 * source is sounding; this enum only tracks WHICH one is currently active,
 * so each control can show its own Play/Stop state and the other can hide
 * itself while its sibling is speaking).
 */
enum class LiturgyPlaybackSource { SYNTHESIS, RECORDED }

/**
 * Whether the pastor's-own-word control should be offered at all right now:
 * true only when [recordedUrl] is present, non-blank once trimmed, and a
 * syntactically valid http(s) URL. Absent, blank, and malformed all mean the
 * SAME thing here — omit the control entirely (never a disabled/empty state:
 * mixed per-band coverage is the PERMANENT normal state, not a gap to fill).
 * A malformed value is deliberately NOT a trigger to fall back to playing
 * synthesis under this control's label — that would misrepresent the source
 * exactly the way this whole design exists to prevent (see the header
 * above); the member always has the independent, always-available Listen
 * control for today's text regardless of this function's result. Returns
 * the trimmed, validated URL itself (not just a Boolean) so callers never
 * have to re-derive it.
 */
fun recordedLiturgyUrlIfPlayable(recordedUrl: String?): String? {
    val trimmed = recordedUrl?.trim().orEmpty()
    return trimmed.takeIf { it.isNotEmpty() && isPlayableRecordingUrl(it) }
}

/** `java.net.URI` (plain JDK), not `android.net.Uri` — same reasoning as
 *  ui/components/VideoPlayer.kt's `isExternalVideoHost`: this file's house
 *  rule is plain-JUnit testability with no Robolectric/device, and
 *  `android.net.Uri` is a stub under this project's unit-test config
 *  (`unitTests.isReturnDefaultValues = true`) that silently returns null
 *  instead of throwing — a test using it could pass while validating
 *  nothing. Raw whitespace inside the string (a common "malformed URL that
 *  came through a text field" shape) is rejected explicitly, on top of
 *  whatever `URI`'s own parser already rejects, and only http/https with a
 *  non-blank host counts as playable. */
private fun isPlayableRecordingUrl(url: String): Boolean {
    if (url.any { it.isWhitespace() }) return false
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != "http" && scheme != "https") return false
    return !uri.host.isNullOrBlank()
}

/** Mirrors LiturgyVoice's UI-facing states: PREPARING while the engine is
 *  still warming up (control hidden — never show a button that might do
 *  nothing), IDLE/SPEAKING while it's confirmed usable, UNAVAILABLE once
 *  we've learned it can't serve this device (control hidden, permanently,
 *  until a fresh bind()). */
enum class LiturgyVoiceStatus { PREPARING, IDLE, SPEAKING, UNAVAILABLE }

/** Every trigger that can move [LiturgyVoiceStatus]. Kept as a closed set so
 *  [reduceLiturgyVoiceStatus] is an exhaustive `when` — a new trigger that
 *  forgets to say what it does to playback state fails to compile. */
sealed interface LiturgyVoiceEvent {
    /** onInit reported SUCCESS and a usable language was set. */
    data object EngineReady : LiturgyVoiceEvent

    /** onInit failed, or no usable language/voice data was found. */
    data object EngineUnavailable : LiturgyVoiceEvent

    /** The member tapped Listen or the pastor's-own-word control while it
     *  was idle — starts speaking. Which of the two is now active is tracked
     *  separately (see [LiturgyPlaybackSource]), not by this event. */
    data object TapToggle : LiturgyVoiceEvent

    /** The utterance finished on its own, errored, or was stopped. */
    data object UtteranceFinished : LiturgyVoiceEvent

    /** Audio focus was taken away mid-utterance (e.g. an incoming call). */
    data object FocusLost : LiturgyVoiceEvent

    /** The card left the screen (backgrounded, navigated away, disposed). */
    data object LeftScreen : LiturgyVoiceEvent
}

/**
 * The entire spoken-liturgy playback state machine, factored out so it is
 * unit-testable without a real TextToSpeech engine. [LiturgyVoice] calls this
 * for every transition instead of setting state directly, so what's tested
 * here is exactly what runs in production.
 */
fun reduceLiturgyVoiceStatus(current: LiturgyVoiceStatus, event: LiturgyVoiceEvent): LiturgyVoiceStatus =
    when (event) {
        LiturgyVoiceEvent.EngineUnavailable -> LiturgyVoiceStatus.UNAVAILABLE
        LiturgyVoiceEvent.EngineReady ->
            if (current == LiturgyVoiceStatus.UNAVAILABLE) current else LiturgyVoiceStatus.IDLE
        LiturgyVoiceEvent.TapToggle -> when (current) {
            LiturgyVoiceStatus.SPEAKING -> LiturgyVoiceStatus.IDLE
            LiturgyVoiceStatus.IDLE -> LiturgyVoiceStatus.SPEAKING
            LiturgyVoiceStatus.PREPARING, LiturgyVoiceStatus.UNAVAILABLE -> current
        }
        LiturgyVoiceEvent.UtteranceFinished, LiturgyVoiceEvent.FocusLost, LiturgyVoiceEvent.LeftScreen ->
            if (current == LiturgyVoiceStatus.UNAVAILABLE) current else LiturgyVoiceStatus.IDLE
    }

/** `TextToSpeech.isLanguageAvailable`/`setLanguage` share one result-code
 *  family: LANG_AVAILABLE(0)/LANG_COUNTRY_AVAILABLE(1)/
 *  LANG_COUNTRY_VAR_AVAILABLE(2) are usable; LANG_MISSING_DATA(-1)/
 *  LANG_NOT_SUPPORTED(-2) are not. */
fun isLanguageUsable(result: Int): Boolean = result >= TextToSpeech.LANG_AVAILABLE

/** True only when BOTH init succeeded AND a usable language/voice was found —
 *  either failure alone is enough to hide the control (spec: degrade to
 *  hiding it, never show a button that does nothing). */
fun isVoiceUsable(initStatus: Int, languageResult: Int): Boolean =
    initStatus == TextToSpeech.SUCCESS && isLanguageUsable(languageResult)

/**
 * Chooses which English locale to read the liturgy in, preferring options
 * that sound natural to a Kenyan-English-speaking congregation over the
 * synthesizer's US-English default: the device's own locale first (when it's
 * already English — respect the member's own setting), then en-KE if the
 * engine happens to ship it, then British English (closer to Kenyan English
 * convention than US), then en-ZA, then finally US as a last resort so the
 * feature still works rather than disappearing on an engine that only ships
 * US English voice data. [isAvailable] is the caller's
 * `TextToSpeech.isLanguageAvailable` check, injected so this stays pure and
 * testable with a fake.
 */
fun pickLiturgyVoiceLocale(deviceDefault: Locale, isAvailable: (Locale) -> Boolean): Locale? {
    val candidates = LinkedHashSet<Locale>().apply {
        if (deviceDefault.language.equals("en", ignoreCase = true)) add(deviceDefault)
        add(Locale("en", "KE"))
        add(Locale.UK)
        add(Locale("en", "ZA"))
        add(Locale.US)
    }
    return candidates.firstOrNull(isAvailable)
}
