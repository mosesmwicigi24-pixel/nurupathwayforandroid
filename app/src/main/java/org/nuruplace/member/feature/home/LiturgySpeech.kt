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

    /** The member tapped the Listen/Stop control. */
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
