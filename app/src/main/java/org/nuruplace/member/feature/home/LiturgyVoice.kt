// On-device spoken liturgy — Android's built-in TextToSpeech, never a cloud
// service (owner brief: zero per-play cost for ~76 members, no new API key,
// works fully offline, no member audio ever leaves the device). Shaped like
// RadioController (app-wide `object` + StateFlow) but DELIBERATELY does not
// share its "keep playing across every screen" lifetime: reading a prayer
// aloud is scoped to stay only as long as the liturgy card is actually on
// screen. LiturgyCards.kt's `LiturgyCard` calls:
//   - stop()    on Lifecycle.Event.ON_STOP — backgrounding the app or
//               navigating away (same idiom ChatThreadScreen.kt uses to
//               re-lock the pastoral gate on ON_STOP).
//   - release() from DisposableEffect.onDispose — the composable actually
//               leaving the composition. This is what frees the
//               TextToSpeech engine; without it, every bind() leaks one.
//
// Phase 2 (explicitly NOT built here, per owner instruction): the strongest
// version of this feature is the pastor's own recorded voice — the backend
// already has media storage (MEDIA_STORAGE_DIR/data/media) and this app
// already records member audio for prayer-wall voice notes (util/
// VoiceRecorder.kt). `speak()` below takes the whole HomeLiturgy specifically
// so that, when a recorded-audio URL shows up on that payload one day, only
// this function's body needs to change (try RadioController-style
// MediaController playback of the recording first, fall back to synthesis) —
// no caller-facing rewrite of LiturgyCard or the state contract.
package org.nuruplace.member.feature.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.nuruplace.member.data.net.HomeLiturgy

object LiturgyVoice {
    data class State(val status: LiturgyVoiceStatus = LiturgyVoiceStatus.PREPARING)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var tts: TextToSpeech? = null
    private var appContext: Context? = null
    private var focusRequest: AudioFocusRequest? = null

    private const val UTTERANCE_ID = "nuru.liturgy.hour"

    private fun apply(event: LiturgyVoiceEvent) {
        _state.value = State(reduceLiturgyVoiceStatus(_state.value.status, event))
    }

    /** Connect + warm the engine once (idempotent) — call as soon as the
     *  liturgy card mounts, so the FIRST tap doesn't sit waiting on init.
     *  Never speaks anything by itself; binding is silent. */
    fun bind(context: Context) {
        if (tts != null) return
        val app = context.applicationContext
        appContext = app
        tts = TextToSpeech(app) { status -> onEngineInit(status) }
    }

    private fun onEngineInit(initStatus: Int) {
        val engine = tts
        if (engine == null || initStatus != TextToSpeech.SUCCESS) {
            apply(LiturgyVoiceEvent.EngineUnavailable)
            return
        }
        val locale = pickLiturgyVoiceLocale(Locale.getDefault()) { candidate ->
            runCatching { isLanguageUsable(engine.isLanguageAvailable(candidate)) }.getOrDefault(false)
        }
        val languageResult = locale
            ?.let { runCatching { engine.setLanguage(it) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) }
            ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (!isVoiceUsable(initStatus, languageResult)) {
            apply(LiturgyVoiceEvent.EngineUnavailable)
            return
        }
        // Unhurried and a little warmer than the default clipped-robot
        // rate/pitch — reads like a person praying, not an announcement.
        // Tuned by ear in review; NOT verified against a real device speaker
        // in this session (see the PR/report — this needs an on-device ear).
        engine.setSpeechRate(0.88f)
        engine.setPitch(0.96f)
        engine.setAudioAttributes(speechAudioAttributes())
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finishSpeaking()

            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onError(utteranceId: String?) = finishSpeaking()
            override fun onError(utteranceId: String?, errorCode: Int) = finishSpeaking()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finishSpeaking()
        })
        apply(LiturgyVoiceEvent.EngineReady)
    }

    /** Whether the Listen control should be shown right now: the engine must
     *  be confirmed usable AND no spoken-feedback accessibility service
     *  (TalkBack or equivalent) may be running — see [isSpokenFeedbackActive].
     *  The card re-checks this on every ON_RESUME (the realistic way TalkBack
     *  toggles mid-session is leaving the app for Settings and coming back);
     *  LiturgyVoice itself doesn't hold a live a11y-change listener. */
    fun controlOffered(context: Context): Boolean {
        val offerable = when (_state.value.status) {
            LiturgyVoiceStatus.IDLE, LiturgyVoiceStatus.SPEAKING -> true
            LiturgyVoiceStatus.PREPARING, LiturgyVoiceStatus.UNAVAILABLE -> false
        }
        return offerable && !isSpokenFeedbackActive(context)
    }

    /** Tap handler: speak [liturgy] if idle, stop if already speaking. A
     *  no-op in any other status — the card only renders the control once
     *  [controlOffered] is true, so a live tap reaching here otherwise
     *  shouldn't normally happen; this is just the defensive fallback. */
    fun toggle(context: Context, liturgy: HomeLiturgy) {
        when (_state.value.status) {
            LiturgyVoiceStatus.SPEAKING -> stop()
            LiturgyVoiceStatus.IDLE -> speak(context, liturgy)
            LiturgyVoiceStatus.PREPARING, LiturgyVoiceStatus.UNAVAILABLE -> Unit
        }
    }

    private fun speak(context: Context, liturgy: HomeLiturgy) {
        val engine = tts ?: return
        // TalkBack (or any spoken-feedback service) already gives this
        // member speech for every plain Text on this card — never lay our
        // own voice on top of it.
        if (isSpokenFeedbackActive(context)) return
        val script = liturgySpeechScript(liturgy)
        if (script.isBlank()) return
        if (!requestFocus(context)) return
        engine.speak(script, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        apply(LiturgyVoiceEvent.TapToggle)
    }

    /** Stop mid-utterance — member tapped Stop, navigated away, the app
     *  backgrounded, or focus was taken. Does NOT tear down the engine; see
     *  [release] for that. Safe to call when nothing is speaking. */
    fun stop() {
        tts?.stop()
        appContext?.let { abandonFocus(it) }
        apply(LiturgyVoiceEvent.LeftScreen)
    }

    private fun finishSpeaking() {
        appContext?.let { abandonFocus(it) }
        apply(LiturgyVoiceEvent.UtteranceFinished)
    }

    /** Full teardown — call from the liturgy card's DisposableEffect.onDispose.
     *  This is what actually prevents the "leaks the engine" failure mode:
     *  every bind() must be matched by a release() once the card leaves the
     *  composition, not just a stop(). Safe to call more than once. */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        appContext?.let { abandonFocus(it) }
        appContext = null
        _state.value = State(LiturgyVoiceStatus.PREPARING)
    }

    // ---- Audio focus — never stomp on Radio or a live broadcast ----

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // TextToSpeech has no meaningful "pause and resume
                // mid-sentence" — the honest, non-jarring behaviour on a real
                // interruption (an incoming call) is to stop cleanly and let
                // the member tap Listen again once it's over, not silently
                // resume from wherever it left off.
                tts?.stop()
                appContext?.let { abandonFocus(it) }
                apply(LiturgyVoiceEvent.FocusLost)
            }
        }
    }

    /** USAGE_MEDIA + CONTENT_TYPE_SPEECH — the same USAGE_MEDIA family
     *  RadioService's ExoPlayer uses, so the two duck against each other
     *  predictably under the platform's own audio-focus policy; SPEECH
     *  content type so it's never mistaken for the music stream itself. */
    private fun speechAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    /** TRANSIENT + MAY_DUCK: RadioService's ExoPlayer (USAGE_MEDIA /
     *  CONTENT_TYPE_MUSIC, `handleAudioFocus = true`) reacts to this by
     *  lowering its OWN volume for the duration rather than pausing — Radio
     *  keeps playing, just quieter, never silently cut off. media3's
     *  AudioFocusManager restores full volume automatically once we abandon
     *  focus below (see RadioService.kt's player-builder audio attributes;
     *  Nuru Live's own player doesn't request focus at all today, and its
     *  screen releases its player on dispose, so it isn't a background
     *  audio source this needs to coordinate with the same way). */
    private fun requestFocus(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAudioAttributes())
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(false)
            .build()
        val granted = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) focusRequest = request
        return granted
    }

    private fun abandonFocus(context: Context) {
        val am = context.getSystemService(AudioManager::class.java)
        focusRequest?.let { am?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** True when a spoken-feedback accessibility service (TalkBack, or any
     *  equivalent) is currently running. Every word on the liturgy card is a
     *  plain visible Text composable, so TalkBack alone already gives full
     *  access to the same content — hiding this control in that case loses
     *  nothing and avoids the "two voices at once" the control must never
     *  cause. */
    private fun isSpokenFeedbackActive(context: Context): Boolean {
        val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return runCatching {
            am.isEnabled &&
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty()
        }.getOrDefault(false)
    }
}
