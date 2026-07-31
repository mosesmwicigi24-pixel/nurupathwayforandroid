// Nuru Live L6b — "congregation hears guests" (reach goal, docs/
// LIVE_INTERACTIVE.md task brief item 5). Two verified, independent
// mechanisms make this wireable without faking anything:
//
// 1. WEBRTC PLAYOUT-SIDE PCM TAP: `org.webrtc.AudioTrack.addSink(AudioTrackSink)`
//    (verified against the resolved io.github.webrtc-sdk:android:144.7559.09
//    classes.jar via javap: `org.webrtc.AudioTrack.addSink(AudioTrackSink)`,
//    `AudioTrackSink.onData(ByteBuffer, bitsPerSample, sampleRate,
//    numberOfChannels, numberOfFrames, absoluteCaptureTimestampMs)`) is a
//    per-REMOTE-TRACK tap on the DECODED audio, independent of the
//    AudioDeviceModule/playout path — it fires in addition to normal
//    speaker playback, not instead of it. This is what the task brief calls
//    "webrtc exposes playout PCM": JavaAudioDeviceModule's own
//    SamplesReadyCallback is capture-side only (confirmed, not used here),
//    but AudioTrackSink at the MediaStreamTrack level is genuinely
//    playout/received-side and was the piece worth re-checking against the
//    actual resolved artifact rather than assuming from memory.
//
// 2. ROOTENCODER'S GENERIC AudioSource BASE (verified against the pinned
//    2.5.9 tag, encoder/.../input/sources/audio/AudioSource.kt): it is a
//    plain `abstract class` with `start(getMicrophoneData: GetMicrophoneData)`
//    / `stop()` / `isRunning()` and NOTHING codec- or device-specific — a
//    subclass is free to call `getMicrophoneData.inputPCMData(Frame)`
//    whenever it wants, with whatever PCM bytes it wants. RootEncoder's own
//    MixAudioSource.kt (same tag) proves the pattern is sanctioned, but its
//    particular implementation leans on Android's
//    AudioPlaybackCaptureConfiguration (Q+, requires a SEPARATE
//    screen-share-style MediaProjection consent prompt just for audio, and
//    the OS hard-blocks capturing USAGE_VOICE_COMMUNICATION content, which
//    is WebRTC's own default AudioAttributes usage) — not a fit here. This
//    file instead defines [MixingMicrophoneSource], a from-scratch
//    AudioSource that captures the broadcaster's own mic exactly like
//    RootEncoder's stock MicrophoneSource.kt (same MicrophoneManager
//    wrapping), then additively mixes in whatever's queued from
//    [GuestAudioMixer] before forwarding each Frame — no MediaProjection,
//    no OS capture restriction, no second consent prompt.
//
// Net effect: every accepted guest's WhepSubscriber registers an
// AudioTrackSink here (see WhepSubscriber.kt); LiveBroadcastEngine.kt swaps
// the broadcaster's RootEncoder audio source for [MixingMicrophoneSource]
// so the SAME outgoing RTMP audio track carries mic + every live guest,
// mixed. The host also hears guests locally regardless (WebRTC plays
// received audio through the default output automatically — addSink is
// additive, never exclusive).
package org.nuruplace.member.feature.live

import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.CustomAudioEffect
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.audio.MicrophoneManager
import com.pedro.encoder.input.sources.audio.AudioSource
import org.webrtc.AudioTrackSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Bounded per-guest mono PCM queue, resampled to [MIX_SAMPLE_RATE] on the
 *  way in so the mixer's hot path (pulled once per ~10-20ms mic callback) is
 *  just addition — no resampling math on that thread. */
private class GuestQueue {
    // ~2s of headroom at the mix rate — generous jitter buffer without
    // letting a guest whose audio stalls (bad network, left stage without a
    // clean detach) grow unbounded; oldest samples drop on overflow so
    // latency self-corrects rather than accumulating forever.
    private val maxSamples = MIX_SAMPLE_RATE * 2
    private val buffer = ArrayDeque<Short>()

    @Synchronized
    fun push(samples: ShortArray) {
        for (s in samples) {
            if (buffer.size >= maxSamples) buffer.removeFirst()
            buffer.addLast(s)
        }
    }

    @Synchronized
    fun pull(count: Int, out: IntArray) {
        for (i in 0 until count) {
            out[i] += (buffer.removeFirstOrNull() ?: 0).toInt()
        }
    }

    @Synchronized
    fun clear() = buffer.clear()
}

private const val MIX_SAMPLE_RATE = AUDIO_SAMPLE_RATE // 44_100 — matches LiveBroadcastEngine's mic capture rate.

/** Process-wide (one broadcast at a time — Broadcast Studio is single-
 *  session already) registry of live guests' decoded audio, feeding
 *  [MixingMicrophoneSource]. [reset] is called at broadcast start/end so a
 *  stale guest from a previous session can never bleed into a new one. */
object GuestAudioMixer {
    private val queues = ConcurrentHashMap<String, GuestQueue>()

    // L6c — per-guest smoothed speaking level, piggybacked on the SAME
    // decoded/resampled mono PCM already computed in [push] for the mix (no
    // separate tap, no extra decoding work). GuestStageCompositor.kt reads
    // this to pick which live guest's video tile should be the "active
    // speaker" in the composited RTMP frame. Units are mean absolute 16-bit
    // sample magnitude (0..32767ish) smoothed with a simple IIR average so a
    // single loud syllable doesn't cause the composited layout to flicker
    // between guests every ~20ms callback.
    private val levels = ConcurrentHashMap<String, Float>()

    fun reset() {
        queues.values.forEach { it.clear() }
        queues.clear()
        levels.clear()
    }

    /** Called by WhepSubscriber once a guest's remote AudioTrack arrives.
     *  Returns the sink to `addSink()` on that track; `detach` removes the
     *  guest's queue (call from the same place the WHEP subscription tears
     *  down, e.g. guest removed/left/stream ended). */
    fun attach(guestId: String): AudioTrackSink {
        queues.getOrPut(guestId) { GuestQueue() }
        return GuestSink(guestId)
    }

    fun detach(guestId: String) {
        queues.remove(guestId)
        levels.remove(guestId)
    }

    /** Snapshot of every guest's current smoothed speaking level — read by
     *  GuestStageCompositor's reflow loop, never written from outside [push]. */
    fun currentLevels(): Map<String, Float> = levels.toMap()

    /** Downmixes to mono + linearly resamples to [MIX_SAMPLE_RATE] before
     *  queuing — WebRTC's decoded output is commonly 48kHz (its internal
     *  pipeline rate) but this is written generically off whatever
     *  `sourceSampleRate`/`channels` AudioTrackSink.onData actually reports,
     *  since that's allowed to vary by codec/negotiation. */
    private fun push(guestId: String, pcm: ShortArray, sourceSampleRate: Int, channels: Int) {
        val queue = queues[guestId] ?: return
        if (pcm.isEmpty() || channels <= 0) return
        val frameCount = pcm.size / channels
        val mono = ShortArray(frameCount)
        for (i in 0 until frameCount) {
            var sum = 0
            for (c in 0 until channels) sum += pcm[i * channels + c]
            mono[i] = (sum / channels).toShort()
        }
        val resampled = if (sourceSampleRate == MIX_SAMPLE_RATE || frameCount == 0) {
            mono
        } else {
            val outCount = (frameCount.toLong() * MIX_SAMPLE_RATE / sourceSampleRate).toInt().coerceAtLeast(0)
            ShortArray(outCount) { i ->
                val srcPos = i.toDouble() * sourceSampleRate.toDouble() / MIX_SAMPLE_RATE.toDouble()
                val srcIdx = srcPos.toInt().coerceIn(0, frameCount - 1)
                val nextIdx = (srcIdx + 1).coerceAtMost(frameCount - 1)
                val frac = srcPos - srcIdx
                (mono[srcIdx] + (mono[nextIdx] - mono[srcIdx]) * frac).roundToInt().toShort()
            }
        }
        queue.push(resampled)
        if (resampled.isNotEmpty()) {
            var sum = 0.0
            for (s in resampled) sum += kotlin.math.abs(s.toInt())
            val mean = (sum / resampled.size).toFloat()
            val prev = levels[guestId] ?: 0f
            // 60/40 IIR smoothing — same shape as GuestQueue's jitter buffer
            // philosophy (self-correcting, no unbounded state) but here just
            // damping frame-to-frame noise rather than latency.
            levels[guestId] = prev * 0.6f + mean * 0.4f
        }
    }

    /** Sums [count] mono samples (at [MIX_SAMPLE_RATE]) across every active
     *  guest, clipped to 16-bit range. Called once per mic Frame from
     *  [MixingMicrophoneSource] — starved guests just contribute silence
     *  (0) for that slice rather than blocking. */
    fun pullMixed(count: Int): ShortArray {
        if (queues.isEmpty()) return ShortArray(count)
        val acc = IntArray(count)
        queues.values.forEach { it.pull(count, acc) }
        return ShortArray(count) { i -> acc[i].coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
    }

    private class GuestSink(private val guestId: String) : AudioTrackSink {
        override fun onData(
            audioData: ByteBuffer,
            bitsPerSample: Int,
            sampleRate: Int,
            numberOfChannels: Int,
            numberOfFrames: Int,
            absoluteCaptureTimestampMs: Long,
        ) {
            // WebRTC's Java ADM always delivers 16-bit PCM on this callback
            // in practice (verified: WebRtcAudioTrack's own sink invocation
            // hard-codes 16-bit) — defensive check anyway since AudioTrackSink
            // itself is a generic interface, not documented as 16-bit-only.
            if (bitsPerSample != 16) return
            val shorts = ShortArray(numberOfFrames * numberOfChannels)
            audioData.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            push(guestId, shorts, sampleRate, numberOfChannels)
        }
    }
}

/** RootEncoder AudioSource — captures the broadcaster's own mic exactly like
 *  the stock MicrophoneSource.kt (same MicrophoneManager wrapping, verified
 *  against the pinned 2.5.9 source), but mixes in [GuestAudioMixer]'s queued
 *  guest audio on every Frame before forwarding upstream. Swapped in as the
 *  broadcast's audio source from the START of a video broadcast
 *  (LiveBroadcastEngine.kt's buildBroadcaster) and preserved across mute/
 *  unmute (VideoBroadcaster.setMuted swaps to/from this, never back to a
 *  plain MicrophoneSource) so guest mixing never silently drops. */
class MixingMicrophoneSource(
    private val audioSource: Int = android.media.MediaRecorder.AudioSource.DEFAULT,
) : AudioSource(), GetMicrophoneData {

    private val microphone = MicrophoneManager(this)
    private var preferredDevice: android.media.AudioDeviceInfo? = null

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        val result = microphone.createMicrophone(audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor)
        if (!result) throw IllegalArgumentException("Some parameters specified are not valid")
        return true
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        if (!isRunning()) {
            val result = microphone.createMicrophone(audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor)
            if (!result) throw IllegalArgumentException("Failed to create microphone audio source")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                microphone.setPreferredDevice(preferredDevice)
            }
            microphone.start()
        }
    }

    override fun stop() {
        if (isRunning()) {
            this.getMicrophoneData = null
            microphone.stop()
        }
    }

    override fun isRunning(): Boolean = microphone.isRunning
    override fun release() {}

    fun mute() = microphone.mute()
    fun unMute() = microphone.unMute()
    fun isMuted(): Boolean = microphone.isMuted
    fun setAudioEffect(effect: CustomAudioEffect) = microphone.setCustomAudioEffect(effect)

    /** Mic capture callback (fires on MicrophoneManager's own capture thread)
     *  — mix guest PCM into this Frame's bytes in place, then forward the
     *  SAME Frame object upstream exactly as MicrophoneSource.kt does. */
    override fun inputPCMData(frame: Frame) {
        val channels = if (isStereo) 2 else 1
        val numFrames = frame.size / 2 / channels
        if (numFrames > 0) {
            val guestMix = GuestAudioMixer.pullMixed(numFrames)
            for (i in 0 until numFrames) {
                val g = guestMix[i].toInt()
                if (g != 0) {
                    for (c in 0 until channels) {
                        val idx = frame.offset + (i * channels + c) * 2
                        if (idx + 1 >= frame.offset + frame.size) continue
                        val lo = frame.buffer[idx].toInt() and 0xFF
                        val hi = frame.buffer[idx + 1].toInt()
                        val original = ((hi shl 8) or lo).toShort().toInt()
                        val mixed = (original + g).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        frame.buffer[idx] = (mixed and 0xFF).toByte()
                        frame.buffer[idx + 1] = (mixed shr 8).toByte()
                    }
                }
            }
        }
        getMicrophoneData?.inputPCMData(frame)
    }
}
