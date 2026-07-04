// Voice-note engine shared by chat + the prayer wall. VoiceRecorder wraps
// MediaRecorder (AAC in .m4a, 64 kbps / 44.1 kHz) with Compose-friendly state:
// while recording, a 10 Hz sampler reads maxAmplitude and appends a normalised
// 0..100 level, which drives the live wave in the composer. waveformFor()
// bucket-averages those samples down to the server cap (the prayer-wall zod
// shape is ints 0..100, max 80 entries — we send 64). VoicePlayer is a small
// one-at-a-time MediaPlayer helper exposing playingId + progress for the
// played-fraction highlight on shared waveforms.
package org.nuruplace.member.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

class VoiceRecorder {
    var isRecording by mutableStateOf(false)
        private set

    /** Whole seconds recorded so far (kept after stop() for the upload meta). */
    var elapsedSec by mutableStateOf(0)
        private set

    /** Live amplitude samples, 0..100, one every 100 ms while recording. */
    val levels = mutableStateListOf<Int>()

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var sampler: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(context: Context) {
        if (isRecording) return
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val out = File(dir, "rec_${UUID.randomUUID()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
        } catch (_: Exception) {
            runCatching { r.release() }
            out.delete()
            return
        }
        recorder = r
        file = out
        levels.clear()
        elapsedSec = 0
        isRecording = true
        sampler = scope.launch {
            var ticks = 0
            while (isActive) {
                delay(100)
                // maxAmplitude = peak PCM value since the previous call (0..32767).
                // sqrt normalisation: quiet speech still registers as a visible bar
                // while loud peaks compress toward 100 — perceptually closer to how
                // loudness feels than a straight linear map.
                val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                levels.add((sqrt(amp / 32767.0) * 100).roundToInt().coerceIn(0, 100))
                ticks++
                elapsedSec = ticks / 10
            }
        }
    }

    /** Stops recording and returns the finished .m4a (null if it failed/empty). */
    fun stop(): File? {
        if (!isRecording) return null
        sampler?.cancel(); sampler = null
        isRecording = false
        val ok = runCatching { recorder?.stop() }.isSuccess
        runCatching { recorder?.release() }
        recorder = null
        val f = file
        file = null
        return if (ok && f != null && f.length() > 0) f else { f?.delete(); null }
    }

    /** Stops recording and discards the file. */
    fun cancel() {
        stop()?.delete()
    }

    /**
     * Bucket-averages the live samples down to at most [bars] entries for the
     * wire (server cap is 80 ints 0..100 — we send 64). Short recordings pass
     * through unchanged.
     */
    fun waveformFor(bars: Int = 64): List<Int> {
        val src = levels.toList()
        if (src.isEmpty()) return emptyList()
        if (src.size <= bars) return src.map { it.coerceIn(0, 100) }
        return List(bars) { i ->
            val from = i * src.size / bars
            val to = ((i + 1) * src.size / bars).coerceAtLeast(from + 1)
            src.subList(from, to).average().roundToInt().coerceIn(0, 100)
        }
    }
}

/**
 * One-at-a-time voice playback. toggle(id, url) starts (or stops, if the same
 * id is already playing); progress is polled at 10 Hz for the played-fraction
 * highlight. Release in a DisposableEffect at every call site.
 */
class VoicePlayer {
    var playingId by mutableStateOf<String?>(null)
        private set

    /** 0..1 through the currently-playing item. */
    var progress by mutableStateOf(0f)
        private set

    /** Whole-second duration of the playing item (0 until prepared). */
    var durationSec by mutableStateOf(0)
        private set

    private var player: MediaPlayer? = null
    private var poll: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun toggle(id: String, url: String) {
        if (playingId == id) { stop(); return }
        stop()
        val mp = MediaPlayer()
        player = mp
        playingId = id
        try {
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                durationSec = (it.duration / 1000).coerceAtLeast(0)
                it.start()
                poll = scope.launch {
                    while (isActive) {
                        delay(100)
                        val d = runCatching { mp.duration }.getOrDefault(0)
                        val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                        if (d > 0) progress = (pos.toFloat() / d).coerceIn(0f, 1f)
                    }
                }
            }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            mp.prepareAsync()
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        poll?.cancel(); poll = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingId = null
        progress = 0f
        durationSec = 0
    }

    fun release() = stop()
}
