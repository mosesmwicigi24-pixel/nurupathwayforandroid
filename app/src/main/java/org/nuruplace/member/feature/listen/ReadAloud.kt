// Read aloud — the on-device engine (member suggestion, 2026-11-21). Android's
// built-in TextToSpeech, never a cloud voice, for the same reasons the spoken
// liturgy chose it (LiturgyVoice.kt's header): no per-play cost, no key, works
// offline, nothing a member reads leaves the phone.
//
// LiturgyVoice reads ONE short utterance and is scoped to its card. A plan
// reading, a devotional, a letter or a lesson is long, so this engine adds what
// listening to something long needs:
//   - paragraphs (ListenScript.kt) queued in order, the current one tracked
//     from the engine's own onStart callbacks;
//   - pause and resume — TextToSpeech cannot pause mid-sentence, so pausing
//     stops and remembers the paragraph, and resuming starts that paragraph
//     again (the honest place to pick up);
//   - skip back / forward a paragraph, and a speed (AppPrefs.listenSpeed);
//   - it keeps reading with the screen locked or the app in the background,
//     because the whole reading is queued in the engine up front. It stops when
//     the member leaves the reading (the screen leaves the composition), not on
//     ON_STOP the way the liturgy card does — listening with the phone in a
//     pocket is the point.
// The guards are the liturgy's, shared: never over TalkBack (two voices), never
// while the church is broadcasting live, and audio focus is asked for politely
// (TRANSIENT: Nuru Radio pauses and comes back when the reading ends or pauses;
// a phone call pauses the reading).
package org.nuruplace.member.feature.listen

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.feature.home.LiturgyVoice
import org.nuruplace.member.feature.home.isLanguageUsable
import org.nuruplace.member.feature.home.isVoiceUsable
import org.nuruplace.member.feature.home.pickLiturgyVoiceLocale

object ReadAloud {
    enum class Status {
        /** The engine is still connecting (or nothing has bound it yet). */
        PREPARING,
        /** Ready, nothing loaded. */
        READY,
        PLAYING,
        PAUSED,
        /** No engine, or no English voice on this phone — Listen is not offered. */
        UNAVAILABLE,
    }

    /** [key] names the reading that is loaded (playing or paused) — null when
     *  none is. [index] is the paragraph being read, of [total]. */
    data class State(
        val status: Status = Status.PREPARING,
        val key: String? = null,
        val index: Int = 0,
        val total: Int = 0,
    ) {
        fun isFor(k: String): Boolean = key == k && (status == Status.PLAYING || status == Status.PAUSED)
    }

    /** The engine's rate under "1×": a little slower than its default, so a
     *  reading sounds like someone reading to you (the liturgy uses 0.88). */
    const val BASE_RATE = 0.92f
    private const val PITCH = 0.97f

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var tts: TextToSpeech? = null
    private var appContext: Context? = null
    private var users = 0
    private var focusRequest: AudioFocusRequest? = null
    @Volatile private var paragraphs: List<String> = emptyList()

    /** Bumped every time the queue is rebuilt, so a callback from words that
     *  were flushed (a skip, a pause, a new reading) is told apart from the
     *  current ones. Callbacks arrive on the engine's binder thread. */
    @Volatile private var generation = 0

    /** Connect + warm the engine (counted: every [bind] is matched by one
     *  [unbind]). Silent — nothing is read until the member taps Listen. */
    fun bind(context: Context) {
        users++
        if (tts != null) return
        val app = context.applicationContext
        appContext = app
        _state.value = State(Status.PREPARING)
        tts = TextToSpeech(app) { status -> onEngineInit(status) }
    }

    /** The screen that bound the engine is leaving. Its reading stops if it
     *  is the one loaded; the last one out shuts the engine down. */
    fun unbind(key: String?) {
        if (key != null && _state.value.key == key) stop()
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) release()
    }

    private fun onEngineInit(initStatus: Int) {
        val engine = tts
        if (engine == null || initStatus != TextToSpeech.SUCCESS) {
            _state.value = State(Status.UNAVAILABLE)
            return
        }
        val locale = pickLiturgyVoiceLocale(Locale.getDefault()) { candidate ->
            runCatching { isLanguageUsable(engine.isLanguageAvailable(candidate)) }.getOrDefault(false)
        }
        val languageResult = locale
            ?.let { runCatching { engine.setLanguage(it) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) }
            ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (!isVoiceUsable(initStatus, languageResult)) {
            _state.value = State(Status.UNAVAILABLE)
            return
        }
        engine.setPitch(PITCH)
        engine.setAudioAttributes(speechAttributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val (gen, i) = parse(utteranceId) ?: return
                if (gen == generation) _state.update { if (it.status == Status.PLAYING) it.copy(index = i) else it }
            }

            override fun onDone(utteranceId: String?) {
                val (gen, i) = parse(utteranceId) ?: return
                if (gen == generation && i >= paragraphs.lastIndex) finished()
            }

            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onError(utteranceId: String?) = errored(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = errored(utteranceId)

            // A stop is always ours (pause, skip, leave) — the caller has
            // already set the state it wants.
            override fun onStop(utteranceId: String?, interrupted: Boolean) {}
        })
        _state.value = State(Status.READY)
    }

    /** Whether Listen may be offered right now: a usable voice, no spoken-
     *  feedback service running (TalkBack already reads every word on these
     *  screens), and the church not live. Screens re-check on ON_RESUME and
     *  when the live streams change, as the liturgy card does. */
    fun offered(context: Context): Boolean {
        val usable = when (_state.value.status) {
            Status.READY, Status.PLAYING, Status.PAUSED -> true
            Status.PREPARING, Status.UNAVAILABLE -> false
        }
        return usable && !LiturgyVoice.isSpokenFeedbackActive(context) && !LiturgyVoice.isChurchBroadcastingLive()
    }

    /** The one button: start [key] from the top, pause it, or resume it. */
    fun toggle(context: Context, key: String, words: List<String>) {
        val s = _state.value
        when {
            s.isFor(key) && s.status == Status.PLAYING -> pause()
            s.isFor(key) && s.status == Status.PAUSED -> resume(context)
            else -> play(context, key, words, 0)
        }
    }

    fun play(context: Context, key: String, words: List<String>, from: Int = 0) {
        if (words.isEmpty() || !offered(context)) return
        paragraphs = words
        _state.value = State(Status.PAUSED, key, from.coerceIn(0, words.lastIndex), words.size)
        resume(context)
    }

    fun pause() {
        val s = _state.value
        if (s.status != Status.PLAYING) return
        generation++
        tts?.stop()
        appContext?.let { abandonFocus(it) }
        _state.value = s.copy(status = Status.PAUSED)
    }

    fun resume(context: Context) {
        val s = _state.value
        if (s.key == null || s.status != Status.PAUSED || paragraphs.isEmpty()) return
        if (LiturgyVoice.isSpokenFeedbackActive(context)) return
        if (!requestFocus(context)) return
        speakFrom(s.index)
    }

    /** One paragraph back or forward. While paused it only moves the place;
     *  while playing it reads from there. Past the end is the end. */
    fun skip(context: Context, delta: Int) {
        val s = _state.value
        if (s.key == null || paragraphs.isEmpty()) return
        val target = s.index + delta
        if (target > paragraphs.lastIndex) { stop(); return }
        val i = target.coerceAtLeast(0)
        when (s.status) {
            Status.PLAYING -> speakFrom(i)
            Status.PAUSED -> _state.value = s.copy(index = i)
            else -> Unit
        }
    }

    /** The next speed (ListenSpeed), remembered on this phone; a reading in
     *  progress picks it up from the paragraph it is on. */
    fun cycleSpeed() {
        AppPrefs.updateListenSpeed(ListenSpeed.next(AppPrefs.listenSpeed))
        val s = _state.value
        if (s.status == Status.PLAYING) speakFrom(s.index)
    }

    /** Stop and unload. Safe to call when nothing is loaded. */
    fun stop() {
        generation++
        tts?.stop()
        appContext?.let { abandonFocus(it) }
        paragraphs = emptyList()
        _state.update { if (it.status == Status.PLAYING || it.status == Status.PAUSED) State(Status.READY) else it.copy(key = null) }
    }

    private fun release() {
        generation++
        tts?.stop()
        tts?.shutdown()
        tts = null
        appContext?.let { abandonFocus(it) }
        appContext = null
        paragraphs = emptyList()
        _state.value = State(Status.PREPARING)
    }

    private fun speakFrom(index: Int) {
        val engine = tts ?: return
        val gen = ++generation
        engine.stop()
        engine.setSpeechRate(BASE_RATE * AppPrefs.listenSpeed)
        for (j in index..paragraphs.lastIndex) {
            engine.speak(paragraphs[j], if (j == index) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle.EMPTY, "nuru.read.$gen.$j")
        }
        _state.update { it.copy(status = Status.PLAYING, index = index) }
    }

    private fun finished() {
        appContext?.let { abandonFocus(it) }
        paragraphs = emptyList()
        _state.value = State(Status.READY)
    }

    private fun errored(utteranceId: String?) {
        val (gen, _) = parse(utteranceId) ?: return
        if (gen == generation) finished()
    }

    private fun parse(utteranceId: String?): Pair<Int, Int>? {
        val parts = utteranceId?.removePrefix("nuru.read.")?.split('.') ?: return null
        if (parts.size != 2) return null
        val gen = parts[0].toIntOrNull() ?: return null
        val i = parts[1].toIntOrNull() ?: return null
        return gen to i
    }

    // ---- Audio focus ----

    private val speechAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** A call, another app's audio, or Radio started again: pause, keeping the
     *  place, so the member picks up from the same paragraph with one tap. */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
        }
    }

    /** TRANSIENT, not MAY_DUCK: a reading is minutes long, and music ducked
     *  under someone reading for that long is noise. Radio's player pauses on
     *  a transient loss and comes back on its own when this is abandoned. */
    private fun requestFocus(context: Context): Boolean {
        if (focusRequest != null) return true
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(speechAttributes)
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(true)
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
}
