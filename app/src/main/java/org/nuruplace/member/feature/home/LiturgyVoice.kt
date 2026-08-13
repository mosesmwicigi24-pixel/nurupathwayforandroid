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
// Phase 2 (that day is now, see LiturgySpeech.kt's LiturgyPlaybackSource /
// recordedLiturgyUrlIfPlayable for the full design-correction rationale): the
// backend now serves `recorded_audio_url` on the same payload when the
// current band has one (null most of the time by design — mixed per-band
// coverage is the PERMANENT normal state, not a gap). Recorded playback is
// offered as a control genuinely SEPARATE from Listen — never a silent
// swap-in behind it — because the recording is a STANDING per-band asset
// while the liturgy TEXT is recomposed daily; conflating the two would have
// the pastor's voice reading words that don't match what's on screen. Both
// controls share this ONE object's playback engine (audio focus, Radio
// ducking, decline-while-broadcasting, TalkBack silence) via
// [toggleListen]/[toggleRecorded] — see each function's doc. Recorded
// playback uses android.media.MediaPlayer directly (the same setDataSource/
// setOnPreparedListener/setOnCompletionListener/setOnErrorListener/
// prepareAsync idiom as util/VoiceRecorder.kt's VoicePlayer, this app's
// existing remote-URL voice-note player for chat/prayer-wall) rather than
// instantiating VoicePlayer itself — that class is Compose-state-driven
// (progress polling for a played-fraction UI) and doesn't request audio
// focus at all, whereas this object's whole job is sharing ONE audio-focus
// session between BOTH controls so recorded playback ducks Nuru Radio
// exactly like synthesis already does.
package org.nuruplace.member.feature.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityManager
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.nuruplace.member.data.net.HomeLiturgy
import org.nuruplace.member.feature.live.LiveDiscoveryCenter

object LiturgyVoice {
    /** [activeSource] is non-null exactly while [status] is SPEAKING — which
     *  of the two controls (Listen vs. the pastor's-own-word) is the one
     *  actually sounding right now, so each can show its own Play/Stop icon
     *  and LiturgyCards.kt can hide the OTHER control while its sibling is
     *  active (see LiturgySpeech.kt's [LiturgyPlaybackSource] doc for why
     *  the two are kept orthogonal to the status enum). */
    data class State(
        val status: LiturgyVoiceStatus = LiturgyVoiceStatus.PREPARING,
        val activeSource: LiturgyPlaybackSource? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var appContext: Context? = null
    private var focusRequest: AudioFocusRequest? = null

    private const val UTTERANCE_ID = "nuru.liturgy.hour"

    /** [source] only matters (and is only non-null) for [LiturgyVoiceEvent.TapToggle]
     *  transitioning INTO SPEAKING — [reduceLiturgyVoiceStatus] proves TapToggle
     *  is the only event that can ever produce a SPEAKING result, so every other
     *  event clears activeSource back to null here. */
    private fun apply(event: LiturgyVoiceEvent, source: LiturgyPlaybackSource? = null) {
        val newStatus = reduceLiturgyVoiceStatus(_state.value.status, event)
        _state.value = State(
            status = newStatus,
            activeSource = if (newStatus == LiturgyVoiceStatus.SPEAKING) source ?: _state.value.activeSource else null,
        )
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

    /** Whether EITHER control may be offered right now: the engine must be
     *  confirmed usable, no spoken-feedback accessibility service (TalkBack
     *  or equivalent) may be running — see [isSpokenFeedbackActive] — AND the
     *  church isn't broadcasting live right now — see [isChurchBroadcastingLive].
     *  These are shared ENVIRONMENT guards, identical for both Listen and the
     *  pastor's-own-word control; LiturgyCards.kt layers per-control
     *  visibility on top (hiding the OTHER control while [state]'s
     *  activeSource shows its sibling is already speaking, and hiding the
     *  recorded control entirely when no playable URL exists this hour — see
     *  LiturgySpeech.kt's `recordedLiturgyUrlIfPlayable`). The card re-checks
     *  this on every ON_RESUME (the realistic way TalkBack toggles
     *  mid-session is leaving the app for Settings and coming back) and
     *  whenever LiveDiscoveryCenter's streams change (the realistic way a
     *  church broadcast starts while Home is already open); LiturgyVoice
     *  itself doesn't hold a live a11y-change listener. */
    fun controlOffered(context: Context): Boolean {
        val offerable = when (_state.value.status) {
            LiturgyVoiceStatus.IDLE, LiturgyVoiceStatus.SPEAKING -> true
            LiturgyVoiceStatus.PREPARING, LiturgyVoiceStatus.UNAVAILABLE -> false
        }
        return offerable && !isSpokenFeedbackActive(context) && !isChurchBroadcastingLive()
    }

    /** Tap handler for Listen — ALWAYS reads TODAY'S actual liturgy text via
     *  synthesis, regardless of whether a recording exists for this band.
     *  Never plays the recording under this control: the recording is a
     *  standing per-band asset, not a daily one, so offering it here would
     *  have the pastor's voice reading words that don't match what's on
     *  screen (see this file's header). A no-op unless idle or already the
     *  one speaking — the card only renders this control when it should be
     *  tappable (see [controlOffered] and LiturgyCards.kt's `listenOffered`),
     *  so a live tap reaching here in the SPEAKING-but-other-source state
     *  shouldn't normally happen; this is just the defensive fallback. */
    fun toggleListen(context: Context, liturgy: HomeLiturgy) {
        when (_state.value.status) {
            LiturgyVoiceStatus.SPEAKING -> if (_state.value.activeSource == LiturgyPlaybackSource.SYNTHESIS) stop()
            LiturgyVoiceStatus.IDLE -> startListen(context, liturgy)
            LiturgyVoiceStatus.PREPARING, LiturgyVoiceStatus.UNAVAILABLE -> Unit
        }
    }

    /** Tap handler for the pastor's-own-word control — ALWAYS plays the
     *  recording at [recordedUrl] (already validated by the caller via
     *  `recordedLiturgyUrlIfPlayable` before this control was even offered).
     *  Never reads today's text as a fallback: an unplayable recording means
     *  this control isn't offered at all (see LiturgySpeech.kt's doc) —
     *  Listen remains independently available regardless. Same
     *  idle/already-speaking/defensive-no-op shape as [toggleListen]. */
    fun toggleRecorded(context: Context, recordedUrl: String) {
        when (_state.value.status) {
            LiturgyVoiceStatus.SPEAKING -> if (_state.value.activeSource == LiturgyPlaybackSource.RECORDED) stop()
            LiturgyVoiceStatus.IDLE -> startRecorded(context, recordedUrl)
            LiturgyVoiceStatus.PREPARING, LiturgyVoiceStatus.UNAVAILABLE -> Unit
        }
    }

    /** Every guard below applies identically to Listen — TalkBack, audio
     *  focus — matching [startRecorded]'s shape exactly, since the
     *  member-facing contract (never auto-play, duck Radio not cut it,
     *  silent under TalkBack, stop cleanly on a focus loss) has to hold for
     *  both controls alike. */
    private fun startListen(context: Context, liturgy: HomeLiturgy) {
        // TalkBack (or any spoken-feedback service) already gives this
        // member speech for every plain Text on this card — never lay our
        // own voice on top of it.
        if (isSpokenFeedbackActive(context)) return
        val script = liturgySpeechScript(liturgy)
        if (script.isBlank()) return
        if (!requestFocus(context)) return
        val engine = tts
        if (engine == null) {
            appContext?.let { abandonFocus(it) }
            return
        }
        engine.speak(script, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        apply(LiturgyVoiceEvent.TapToggle, LiturgyPlaybackSource.SYNTHESIS)
    }

    /** One-shot remote-URL playback of the pastor's recording — same
     *  USAGE_MEDIA/CONTENT_TYPE_SPEECH attributes as the TTS engine (so the
     *  same audio-focus ducking against Radio applies), same
     *  prepare-async/complete/error idiom as VoiceRecorder.kt's VoicePlayer.
     *  Completion AND error both route to [finishSpeaking] — the same
     *  transition TTS completion uses — so the state machine treats both
     *  controls uniformly; a runtime playback failure (bad network, a 404)
     *  simply returns to IDLE, never silence, and never falls back to
     *  reading today's text under this control's label (see
     *  LiturgySpeech.kt's header for why that fallback would be wrong here). */
    private fun startRecorded(context: Context, recordedUrl: String) {
        if (isSpokenFeedbackActive(context)) return
        if (!requestFocus(context)) return
        if (!startRecordedPlayback(recordedUrl)) {
            appContext?.let { abandonFocus(it) }
            return
        }
        apply(LiturgyVoiceEvent.TapToggle, LiturgyPlaybackSource.RECORDED)
    }

    private fun startRecordedPlayback(url: String): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(speechAudioAttributes())
            mp.setDataSource(url)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { finishSpeaking() }
            mp.setOnErrorListener { _, _, _ -> finishSpeaking(); true }
            mediaPlayer = mp
            mp.prepareAsync()
            true
        } catch (e: Exception) {
            runCatching { mp.release() }
            mediaPlayer = null
            false
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
        mediaPlayer = null
    }

    /** Stop mid-utterance — member tapped Stop, navigated away, the app
     *  backgrounded, or focus was taken. Does NOT tear down the engine; see
     *  [release] for that. Safe to call when nothing is speaking, for either
     *  control. */
    fun stop() {
        tts?.stop()
        releaseMediaPlayer()
        appContext?.let { abandonFocus(it) }
        apply(LiturgyVoiceEvent.LeftScreen)
    }

    private fun finishSpeaking() {
        releaseMediaPlayer()
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
        releaseMediaPlayer()
        appContext?.let { abandonFocus(it) }
        appContext = null
        _state.value = State(LiturgyVoiceStatus.PREPARING)
    }

    // ---- Audio focus — never stomp on Radio or a live broadcast ----

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // TextToSpeech has no meaningful "pause and resume
                // mid-sentence" (and neither does a one-shot MediaPlayer, for
                // the same reason) — the honest, non-jarring behaviour on a
                // real interruption (an incoming call) is to stop cleanly and
                // let the member tap the control again once it's over, not
                // silently resume from wherever it left off. Stops BOTH
                // controls — a recorded playback is just as much an
                // interruption target as synthesis.
                tts?.stop()
                releaseMediaPlayer()
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

    /** "Decline while broadcasting live" — Nuru Live's own player requests no
     *  audio focus at all (see [requestFocus]'s doc above), so the ducking
     *  this object already does for Radio can't coordinate with a live
     *  service the same way; declining to offer either control at all is the
     *  substitute guard, mirroring iOS's equivalent decline for this same
     *  edge case. Reads [LiveDiscoveryCenter.streams] directly — the app's
     *  ONE existing "what's watchable right now" source of truth (already
     *  self-broadcaster-filtered by LiveDiscoveryCenter.ingest; see
     *  HomeScreen.kt's `churchLive`) — rather than threading a new parameter
     *  through every call site. Cell-scope streams don't count here: this is
     *  specifically about the CHURCH's own service being live. */
    private fun isChurchBroadcastingLive(): Boolean =
        LiveDiscoveryCenter.streams.value.any { it.scope == "church" }
}
