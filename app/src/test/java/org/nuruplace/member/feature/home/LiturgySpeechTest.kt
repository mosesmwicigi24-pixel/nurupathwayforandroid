package org.nuruplace.member.feature.home

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.HomeLiturgy
import org.nuruplace.member.data.net.VerseLine

/** Pure-logic coverage for the spoken liturgy (LiturgyVoice.kt owns the real
 *  TextToSpeech engine + AudioManager focus, which need a device/emulator —
 *  everything actually decidable ahead of time lives in LiturgySpeech.kt and
 *  is covered here without either). */
class LiturgySpeechTest {

    // ---- naturalizeScriptureReference ----

    @Test fun `a single verse reference reads as book, chapter, verse N`() {
        assertEquals("Psalm 23, verse 1", naturalizeScriptureReference("Psalm 23:1"))
    }

    @Test fun `a verse range reads as verses N to M`() {
        assertEquals("John 15, verses 1 to 4", naturalizeScriptureReference("John 15:1-4"))
    }

    @Test fun `a numeral-prefixed multi-word book name resolves correctly`() {
        assertEquals("1 Corinthians 13, verses 4 to 7", naturalizeScriptureReference("1 Corinthians 13:4-7"))
    }

    @Test fun `a bare chapter reference with no verse passes through unchanged`() {
        assertEquals("Psalm 23", naturalizeScriptureReference("Psalm 23"))
    }

    @Test fun `an unrecognized shape passes through unchanged rather than being mangled`() {
        assertEquals("John 15:1-16:2", naturalizeScriptureReference("John 15:1-16:2"))
    }

    @Test fun `surrounding whitespace is trimmed`() {
        assertEquals("Psalm 23, verse 1", naturalizeScriptureReference("  Psalm 23:1  "))
    }

    // ---- liturgySpeechScript ----

    @Test fun `the line alone is spoken when nothing else is present`() {
        val liturgy = HomeLiturgy(line = "The Lord is my shepherd")
        assertEquals("The Lord is my shepherd.", liturgySpeechScript(liturgy))
    }

    @Test fun `line then charge then the companion verse and its reference, in card order`() {
        val liturgy = HomeLiturgy(
            line = "Rise and greet the morning",
            charge = "Walk in the light given you",
            scriptureRef = "Psalm 23:1",
            verseLine = VerseLine(reference = "Psalm 23:1", text = "The Lord is my shepherd, I lack nothing"),
        )
        assertEquals(
            "Rise and greet the morning. Walk in the light given you. " +
                "The Lord is my shepherd, I lack nothing. Psalm 23, verse 1.",
            liturgySpeechScript(liturgy),
        )
    }

    @Test fun `the bare scripture reference is used only when there is no companion verse`() {
        val liturgy = HomeLiturgy(line = "Come before the evening hour", scriptureRef = "John 15:1-4")
        assertEquals("Come before the evening hour. John 15, verses 1 to 4.", liturgySpeechScript(liturgy))
    }

    @Test fun `a blank companion verse text falls back to the bare reference`() {
        val liturgy = HomeLiturgy(
            line = "Rest in the night watch",
            scriptureRef = "Psalm 23:1",
            verseLine = VerseLine(reference = "Psalm 23:1", text = "   "),
        )
        assertEquals("Rest in the night watch. Psalm 23, verse 1.", liturgySpeechScript(liturgy))
    }

    @Test fun `existing sentence punctuation is not doubled up`() {
        val liturgy = HomeLiturgy(line = "Rejoice always! Pray without ceasing.")
        assertEquals("Rejoice always! Pray without ceasing.", liturgySpeechScript(liturgy))
    }

    @Test fun `a blank charge is skipped entirely`() {
        val liturgy = HomeLiturgy(line = "The morning has broken", charge = "   ")
        assertEquals("The morning has broken.", liturgySpeechScript(liturgy))
    }

    @Test fun `an entirely empty liturgy speaks nothing`() {
        assertEquals("", liturgySpeechScript(HomeLiturgy(line = "")))
    }

    // ---- isVoiceUsable / isLanguageUsable (TextToSpeech result-code gate) ----

    @Test fun `init failure alone is enough to mark the voice unusable`() {
        assertFalse(isVoiceUsable(initStatus = android.speech.tts.TextToSpeech.ERROR, languageResult = android.speech.tts.TextToSpeech.LANG_AVAILABLE))
    }

    @Test fun `missing language data marks the voice unusable even on init success`() {
        assertFalse(
            isVoiceUsable(
                initStatus = android.speech.tts.TextToSpeech.SUCCESS,
                languageResult = android.speech.tts.TextToSpeech.LANG_MISSING_DATA,
            ),
        )
    }

    @Test fun `language not supported marks the voice unusable`() {
        assertFalse(
            isVoiceUsable(
                initStatus = android.speech.tts.TextToSpeech.SUCCESS,
                languageResult = android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED,
            ),
        )
    }

    @Test fun `init success plus an available language is usable`() {
        assertTrue(
            isVoiceUsable(
                initStatus = android.speech.tts.TextToSpeech.SUCCESS,
                languageResult = android.speech.tts.TextToSpeech.LANG_AVAILABLE,
            ),
        )
    }

    @Test fun `country and country-variant availability also count as usable`() {
        assertTrue(isLanguageUsable(android.speech.tts.TextToSpeech.LANG_COUNTRY_AVAILABLE))
        assertTrue(isLanguageUsable(android.speech.tts.TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE))
    }

    // ---- pickLiturgyVoiceLocale (Kenyan-English-sensible choice, not forced US) ----

    @Test fun `an English device default is preferred over every fallback`() {
        val available = setOf(Locale.UK, Locale.US, Locale("en", "KE"), Locale.UK)
        val chosen = pickLiturgyVoiceLocale(Locale.UK) { it in available }
        assertEquals(Locale.UK, chosen)
    }

    @Test fun `a non-English device default is skipped in favour of en-KE`() {
        val available = setOf(Locale("en", "KE"), Locale.US)
        val chosen = pickLiturgyVoiceLocale(Locale.FRENCH) { it in available }
        assertEquals(Locale("en", "KE"), chosen)
    }

    @Test fun `british English is preferred over US when the device default is not English`() {
        val available = setOf(Locale.UK, Locale.US)
        val chosen = pickLiturgyVoiceLocale(Locale.GERMANY) { it in available }
        assertEquals(Locale.UK, chosen)
    }

    @Test fun `US English is used only as the last resort`() {
        val available = setOf(Locale.US)
        val chosen = pickLiturgyVoiceLocale(Locale.JAPAN) { it in available }
        assertEquals(Locale.US, chosen)
    }

    @Test fun `no available English locale at all yields null, not a crash`() {
        val chosen = pickLiturgyVoiceLocale(Locale.JAPAN) { false }
        assertNull(chosen)
    }

    // ---- reduceLiturgyVoiceStatus (the whole playback state machine) ----

    @Test fun `engine ready moves preparing to idle`() {
        assertEquals(
            LiturgyVoiceStatus.IDLE,
            reduceLiturgyVoiceStatus(LiturgyVoiceStatus.PREPARING, LiturgyVoiceEvent.EngineReady),
        )
    }

    @Test fun `engine unavailable moves preparing to unavailable`() {
        assertEquals(
            LiturgyVoiceStatus.UNAVAILABLE,
            reduceLiturgyVoiceStatus(LiturgyVoiceStatus.PREPARING, LiturgyVoiceEvent.EngineUnavailable),
        )
    }

    @Test fun `tapping while idle starts speaking`() {
        assertEquals(
            LiturgyVoiceStatus.SPEAKING,
            reduceLiturgyVoiceStatus(LiturgyVoiceStatus.IDLE, LiturgyVoiceEvent.TapToggle),
        )
    }

    @Test fun `tapping while speaking stops`() {
        assertEquals(
            LiturgyVoiceStatus.IDLE,
            reduceLiturgyVoiceStatus(LiturgyVoiceStatus.SPEAKING, LiturgyVoiceEvent.TapToggle),
        )
    }

    @Test fun `a tap while still preparing is a no-op`() {
        assertEquals(
            LiturgyVoiceStatus.PREPARING,
            reduceLiturgyVoiceStatus(LiturgyVoiceStatus.PREPARING, LiturgyVoiceEvent.TapToggle),
        )
    }

    @Test fun `a tap while unavailable is a no-op`() {
        assertEquals(
            LiturgyVoiceStatus.UNAVAILABLE,
            reduceLiturgyVoiceStatus(LiturgyVoiceStatus.UNAVAILABLE, LiturgyVoiceEvent.TapToggle),
        )
    }

    @Test fun `utterance finishing returns speaking to idle`() {
        assertEquals(
            LiturgyVoiceStatus.IDLE,
            reduceLiturgyVoiceStatus(LiturgyVoiceStatus.SPEAKING, LiturgyVoiceEvent.UtteranceFinished),
        )
    }

    @Test fun `losing audio focus mid-utterance returns to idle, not a crash or a stuck state`() {
        assertEquals(
            LiturgyVoiceStatus.IDLE,
            reduceLiturgyVoiceStatus(LiturgyVoiceStatus.SPEAKING, LiturgyVoiceEvent.FocusLost),
        )
    }

    @Test fun `leaving the screen while speaking returns to idle`() {
        assertEquals(
            LiturgyVoiceStatus.IDLE,
            reduceLiturgyVoiceStatus(LiturgyVoiceStatus.SPEAKING, LiturgyVoiceEvent.LeftScreen),
        )
    }

    @Test fun `once unavailable, nothing but a fresh bind (EngineReady is never sent) revives it`() {
        // EngineUnavailable is the only event LiturgyVoice ever sends after
        // marking a device UNAVAILABLE within one bind() cycle — this pins
        // down that every OTHER event leaves UNAVAILABLE exactly where it is,
        // so a stray late callback can never flicker a dead control back on.
        for (event in listOf(
            LiturgyVoiceEvent.EngineReady,
            LiturgyVoiceEvent.TapToggle,
            LiturgyVoiceEvent.UtteranceFinished,
            LiturgyVoiceEvent.FocusLost,
            LiturgyVoiceEvent.LeftScreen,
        )) {
            assertEquals(
                "event=$event",
                LiturgyVoiceStatus.UNAVAILABLE,
                reduceLiturgyVoiceStatus(LiturgyVoiceStatus.UNAVAILABLE, event),
            )
        }
    }
}
