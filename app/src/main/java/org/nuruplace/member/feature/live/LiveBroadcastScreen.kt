// Nuru Live — the broadcaster's own screen (L3). Publishes RTMP to the
// self-hosted MediaMTX server via RootEncoder. Video kind shows the camera
// preview; audio kind shows the Radio-style waveform backdrop. Both share one
// LIVE HUD (pulsing dot, elapsed timer, "N watching"), mute/flip controls, an
// End confirmation, connection-drop retry with backoff, and a summary screen.
package org.nuruplace.member.feature.live

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.common.onMainThreadHandler
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.library.generic.GenericOnlyAudio
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.streamclient.StreamBaseClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.LivePulsingDot
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

private const val VIDEO_WIDTH = 1920
private const val VIDEO_HEIGHT = 1080
private const val VIDEO_BITRATE = 6_000_000 // ~6 Mbps, per docs/LIVE_STREAMING.md
private const val AUDIO_SAMPLE_RATE = 44_100
private const val AUDIO_BITRATE = 128_000
private val RETRY_BACKOFFS_MS = longArrayOf(2_000, 5_000, 10_000)
private const val MAX_RETRIES = 3

private enum class Phase { CONNECTING, LIVE, RECONNECTING, FAILED, ENDING, SUMMARY }

/** Uniform wrapper over RootEncoder's two unrelated base classes
 *  (StreamBase for GenericStream/video, OnlyAudioBase for GenericOnlyAudio/
 *  audio-only) — they share no common supertype and differ in small ways
 *  (mute mechanism, prepareAudio's parameter order) that this hides so the
 *  rest of the screen doesn't need to branch on `kind` everywhere. */
private interface Broadcaster {
    val isStreaming: Boolean
    fun startStream(url: String)
    fun stopStream()
    fun release()
    fun setMuted(muted: Boolean)
    fun client(): StreamBaseClient
    fun startPreview(view: SurfaceView) {}
    fun stopPreview() {}
    fun switchCamera() {}
}

private class VideoBroadcaster(val stream: GenericStream) : Broadcaster {
    override val isStreaming get() = stream.isStreaming
    override fun startStream(url: String) = stream.startStream(url)
    override fun stopStream() { stream.stopStream() }
    override fun release() = stream.release()
    // StreamBase (video) has no disableAudio()/enableAudio() — mute by
    // swapping the audio source itself (verified against the resolved
    // 2.5.9 tag: NoAudioSource exists there; the newer SilenceAudioSource
    // used by master does not, so this is deliberately NOT used).
    override fun setMuted(muted: Boolean) {
        stream.changeAudioSource(if (muted) NoAudioSource() else MicrophoneSource())
    }
    override fun client() = stream.getStreamClient()
    override fun startPreview(view: SurfaceView) { if (!stream.isOnPreview) stream.startPreview(view) }
    override fun stopPreview() { if (stream.isOnPreview) stream.stopPreview() }
    override fun switchCamera() { (stream.videoSource as? Camera2Source)?.switchCamera() }
}

private class AudioBroadcaster(val stream: GenericOnlyAudio) : Broadcaster {
    override val isStreaming get() = stream.isStreaming
    override fun startStream(url: String) = stream.startStream(url)
    override fun stopStream() { stream.stopStream() }
    // OnlyAudioBase exposes no release() in this version — stopStream() is
    // the documented full teardown for the audio-only path.
    override fun release() {}
    override fun setMuted(muted: Boolean) { if (muted) stream.disableAudio() else stream.enableAudio() }
    override fun client() = stream.getStreamClient()
}

private class BroadcasterHolder { var value: Broadcaster? = null }

@Composable
fun LiveBroadcastScreen(
    streamId: String,
    rtmpUrl: String,
    streamKey: String,
    title: String,
    kind: String, // "video" | "audio"
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isVideo = kind == "video"

    // ── RTMP publish URL construction — READ THIS BEFORE TOUCHING ──
    //
    // MediaMTX's simple query-based publish auth expects the FINAL, fully
    // reconstructed RTMP request to look like ".../church?user=<id>&pass=<key>"
    // (path, then query). ffmpeg achieves this by treating the whole
    // "church?user=..&pass=.." as ONE opaque connect string with an EMPTY
    // stream name/playpath — this matches MediaMTX's own documented "Server=
    // rtmp://host/mypath, Stream Key=(blank)" OBS convention.
    //
    // RootEncoder does NOT behave the same way if you hand it a URL with a
    // real, un-escaped '?'. I verified this by reading the actual resolved
    // library's source (RootEncoder 2.5.9's common/UrlParser.kt) together
    // with MediaMTX's RTMP server (gortmplib's server_conn.go buildURL()):
    // RootEncoder's UrlParser splits the URL into an RTMP "app" (sent in the
    // connect command) and a separate "streamName"/playpath (sent in the
    // publish command). For a FLAT single-segment path like "church" plus a
    // query string, UrlParser puts "church" in appName (clean) and
    // "user=..&pass=.." in streamName, WITHOUT the leading '?' (its
    // getStreamName() strips it via removePrefix("?")). MediaMTX's gortmplib
    // then reconstructs the wire path as `"/" + app + "/" + streamName`
    // whenever streamName is non-empty — i.e. it ALWAYS re-joins app and
    // streamName with a literal "/", never a "?". The result for church is
    // "/church/user=..&pass=.." — no '?' survives ANYWHERE in that string, so
    // MediaMTX's Go URL parser sees an empty query and the credentials are
    // silently dropped. I confirmed this end-to-end with a small transliteration
    // of both files plus a real `java.net.URI` test (java.net.URI is what
    // UrlParser.parse() actually calls). The NESTED "cell/{cellId}" scope is
    // unaffected — its extra path segment happens to keep the '?' embedded
    // mid-token in streamName, so gortmplib's re-join reconstructs it
    // correctly by coincidence. Church (flat) is the one that breaks.
    //
    // THE FIX: percent-encode the '?' as "%3F" before handing the string to
    // RootEncoder. `java.net.URI` decodes %3F back to a literal '?' when you
    // read `.getPath()`, but — critically — does NOT treat it as a genuine
    // query delimiter, so `.getQuery()` comes back null. Verified live
    // against java.net.URI: for "rtmp://host:1935/church%3Fuser=X&pass=Y",
    // getPath() == "/church?user=X&pass=Y" and getQuery() == null. Fed
    // through UrlParser with query == null, the WHOLE "church?user=X&pass=Y"
    // string becomes one opaque appName with an EMPTY streamName — exactly
    // the ffmpeg-equivalent shape — so gortmplib never inserts a spurious
    // "/" and the '?' survives exactly where MediaMTX expects it. This is
    // harmless for the nested cell/{cellId} case too (still splits
    // correctly). Both `streamId` (a UUID) and `streamKey` (32 hex chars)
    // are URL-safe as-is, so only the '?' itself needs escaping.
    val publishUrl = remember(rtmpUrl, streamId, streamKey) {
        "$rtmpUrl%3Fuser=$streamId&pass=$streamKey"
    }

    var phase by remember { mutableStateOf(Phase.CONNECTING) }
    var muted by remember { mutableStateOf(false) }
    var viewerCount by remember { mutableIntStateOf(0) }
    var peakViewers by remember { mutableIntStateOf(0) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var startedAtMillis by remember { mutableStateOf<Long?>(null) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var failReason by remember { mutableStateOf<String?>(null) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var endedInBackground by remember { mutableStateOf(false) }
    // Set right before WE call stopStream() ourselves (End flow, background
    // teardown) so the ConnectChecker's onDisconnect() doesn't mistake an
    // intentional stop for a dropped connection and try to reconnect.
    var endingIntentionally by remember { mutableStateOf(false) }

    val broadcasterHolder = remember { BroadcasterHolder() }

    fun handleDrop(reason: String) {
        if (endingIntentionally || phase == Phase.ENDING || phase == Phase.SUMMARY) return
        if (retryAttempt < MAX_RETRIES) {
            val delayMs = RETRY_BACKOFFS_MS.getOrElse(retryAttempt) { RETRY_BACKOFFS_MS.last() }
            retryAttempt += 1
            phase = Phase.RECONNECTING
            val willRetry = runCatching { broadcasterHolder.value?.client()?.reTry(delayMs, reason, null) }.getOrDefault(false) == true
            if (!willRetry) {
                runCatching { broadcasterHolder.value?.stopStream() }
                phase = Phase.FAILED
                failReason = reason
            }
        } else {
            runCatching { broadcasterHolder.value?.stopStream() }
            phase = Phase.FAILED
            failReason = reason
        }
    }

    val connectChecker = remember {
        object : ConnectChecker {
            override fun onConnectionStarted(url: String) {}
            override fun onConnectionSuccess() {
                onMainThreadHandler {
                    retryAttempt = 0
                    if (startedAtMillis == null) startedAtMillis = System.currentTimeMillis()
                    phase = Phase.LIVE
                }
            }
            override fun onConnectionFailed(reason: String) {
                onMainThreadHandler { handleDrop(reason) }
            }
            override fun onDisconnect() {
                onMainThreadHandler {
                    if (!endingIntentionally && phase == Phase.LIVE) handleDrop("Disconnected")
                }
            }
            override fun onAuthError() {
                onMainThreadHandler {
                    runCatching { broadcasterHolder.value?.stopStream() }
                    phase = Phase.FAILED
                    failReason = "Authentication failed."
                }
            }
            override fun onAuthSuccess() {}
            override fun onNewBitrate(bitrate: Long) {}
        }
    }

    // Built + prepared once for this screen's lifetime. Video: 1920x1080@30,
    // hardware H.264 ~6 Mbps, back camera + mic. Audio: 44.1kHz stereo AAC
    // 128kbps mic only, no camera at all (GenericOnlyAudio never touches
    // Camera2/CameraX).
    //
    // Codec: explicitly H.264, not HEVC. docs/LIVE_STREAMING.md's aspiration
    // is "HEVC where the device supports hardware encode, else H.264", but
    // RTMP-transported HEVC requires the newer "Enhanced RTMP" extension
    // (confirmed in RootEncoder's own README/feature list — H.265 is listed
    // under RTMP only "using RTMP enhanced"). Whether the deployed MediaMTX
    // version correctly ingests + re-muxes Enhanced-RTMP HEVC into HLS for
    // the ALREADY-SHIPPED L2 viewers (media3 ExoPlayer) is unverified without
    // a live server to test against — a wrong codec here would silently
    // break playback for every viewer, a much worse failure than slightly
    // larger H.264 frames. H.264 is universally supported by every RTMP
    // server generation including MediaMTX, so it's the safe default; ~6
    // Mbps 1080p H.264 already looks excellent. Flagged here rather than
    // guessed toward the riskier option.
    var prepareOk by remember { mutableStateOf(false) }
    val broadcaster = remember {
        if (isVideo) {
            val s = GenericStream(context, connectChecker)
            s.getGlInterface().autoHandleOrientation = true
            s.setVideoCodec(VideoCodec.H264)
            // rotation=90 on a 1920x1080 base yields a natural 1080x1920
            // portrait broadcast (the RootEncoder "rotation" sample's
            // documented vertical-orientation convention) — how people
            // actually hold a phone to go live.
            val ok = runCatching {
                s.prepareVideo(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_BITRATE, fps = 30, rotation = 90) &&
                    s.prepareAudio(AUDIO_SAMPLE_RATE, true, AUDIO_BITRATE)
            }.getOrDefault(false)
            prepareOk = ok
            VideoBroadcaster(s).also { broadcasterHolder.value = it }
        } else {
            val s = GenericOnlyAudio(connectChecker)
            val ok = runCatching { s.prepareAudio(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, true) }.getOrDefault(false)
            prepareOk = ok
            AudioBroadcaster(s).also { broadcasterHolder.value = it }
        }
    }

    LaunchedEffect(broadcaster) {
        if (prepareOk) {
            runCatching { broadcaster.startStream(publishUrl) }
                .onFailure { phase = Phase.FAILED; failReason = it.message ?: "Couldn't start the stream." }
        } else {
            phase = Phase.FAILED
            failReason = "Camera or microphone couldn't be configured on this device."
        }
    }

    DisposableEffect(broadcaster) {
        onDispose {
            runCatching { if (broadcaster.isStreaming) broadcaster.stopStream() }
            runCatching { broadcaster.release() }
        }
    }

    // Keep the screen on for the whole broadcast; always cleared on exit,
    // including an abnormal teardown, via DisposableEffect.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // "N watching" — poll GET /live/now every 15s while actually live,
    // filtered to our own stream_id, tracking a running peak client-side
    // (there's no dedicated peak field returned at end time).
    LaunchedEffect(streamId) {
        while (true) {
            delay(15_000)
            if (phase == Phase.LIVE) {
                val row = runCatching { Net.client.api.getLiveNow().data }.getOrNull()
                    ?.firstOrNull { it.streamId == streamId }
                if (row != null) {
                    viewerCount = row.viewerCount
                    if (row.viewerCount > peakViewers) peakViewers = row.viewerCount
                }
            }
        }
    }

    // Elapsed mm:ss, ticking only once we're actually connected.
    LaunchedEffect(startedAtMillis) {
        val started = startedAtMillis ?: return@LaunchedEffect
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - started) / 1000).toInt()
            delay(1_000)
        }
    }

    fun endStream(background: Boolean) {
        showEndConfirm = false
        if (endingIntentionally) return
        endingIntentionally = true
        endedInBackground = background
        phase = Phase.ENDING
        runCatching { broadcaster.stopStream() }
        scope.launch {
            // Idempotent + best-effort — never block the summary screen on
            // the network the way sign-out never blocks on logout (AuthStore
            // precedent). The row still gets cleaned up eventually even if
            // this particular call drops.
            runCatching { Net.client.api.postLiveStreamEnd(streamId) }
            phase = Phase.SUMMARY
        }
    }

    // App-background behavior. VIDEO: end gracefully rather than try to keep
    // publishing camera frames from the background (Android would stop
    // camera capture almost immediately anyway once backgrounded without a
    // camera-type foreground service, so a half-alive publish is worse than
    // an honest end). AUDIO: the task allows either choice (Android CAN keep
    // a foreground-service-backed audio publish alive, mirroring how Radio's
    // player uses one) — this implementation ends on background for BOTH
    // kinds, matching video's behavior, for time-budget reasons: wiring a
    // dedicated foreground-service audio publish path (own Service class,
    // manifest entry, mic foreground-service type) is real, non-trivial
    // scope beyond this pass. Documented tradeoff, not an oversight.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (phase == Phase.LIVE || phase == Phase.RECONNECTING || phase == Phase.CONNECTING) {
            endStream(background = true)
        }
    }

    // Hardware/gesture back must never silently abandon a live broadcast —
    // route it through the same confirmation as the End button.
    BackHandler(enabled = phase == Phase.LIVE || phase == Phase.RECONNECTING || phase == Phase.CONNECTING) {
        showEndConfirm = true
    }

    fun manualRetry() {
        retryAttempt = 0
        failReason = null
        phase = Phase.CONNECTING
        runCatching { broadcaster.startStream(publishUrl) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            phase == Phase.SUMMARY -> SummaryView(
                title = title,
                elapsedSec = elapsedSec,
                peakViewers = peakViewers,
                endedInBackground = endedInBackground,
                onDone = onEnded,
            )
            phase == Phase.FAILED -> FailedView(
                reason = failReason,
                onRetry = ::manualRetry,
                onEnd = { endStream(background = false) },
            )
            else -> {
                if (isVideo) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(h: SurfaceHolder) { broadcaster.startPreview(this@apply) }
                                    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                    override fun surfaceDestroyed(h: SurfaceHolder) { broadcaster.stopPreview() }
                                })
                            }
                        },
                    )
                } else {
                    AudioHud(title)
                }

                LiveHudOverlay(
                    phase = phase,
                    title = title,
                    kind = kind,
                    elapsedSec = elapsedSec,
                    viewerCount = viewerCount,
                    retryAttempt = retryAttempt,
                    muted = muted,
                    isVideo = isVideo,
                    onToggleMute = { muted = !muted; broadcaster.setMuted(muted) },
                    onFlipCamera = { broadcaster.switchCamera() },
                    onEndTapped = { showEndConfirm = true },
                )
            }
        }

        if (showEndConfirm) {
            AlertDialog(
                onDismissRequest = { showEndConfirm = false },
                title = { Text("End this broadcast?") },
                text = { Text("Viewers watching now will see the stream end. This can't be undone.") },
                confirmButton = {
                    Text(
                        "End", style = NuruType.cardCta, color = Nuru.danger, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { endStream(background = false) }.padding(Spacing.sm),
                    )
                },
                dismissButton = {
                    Text(
                        "Cancel", style = NuruType.cardCta, color = Nuru.ink600,
                        modifier = Modifier.clickable { showEndConfirm = false }.padding(Spacing.sm),
                    )
                },
            )
        }
    }
}

@Composable
private fun AudioHud(title: String) {
    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(20.dp))
            Text(title.ifBlank { "Nuru Live" }, style = NuruType.title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            AudioWaveform(Modifier.width(180.dp).height(28.dp))
        }
    }
}

@Composable
private fun LiveHudOverlay(
    phase: Phase,
    title: String,
    kind: String,
    elapsedSec: Int,
    viewerCount: Int,
    retryAttempt: Int,
    muted: Boolean,
    isVideo: Boolean,
    onToggleMute: () -> Unit,
    onFlipCamera: () -> Unit,
    onEndTapped: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LivePulsingDot()
                Text(
                    when (phase) {
                        Phase.RECONNECTING -> "RECONNECTING…"
                        Phase.CONNECTING -> "CONNECTING…"
                        else -> "LIVE"
                    },
                    style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold,
                )
                if (phase == Phase.LIVE) {
                    Text("·", style = NuruType.micro, color = Color.White.copy(alpha = 0.5f))
                    Text(mmss(elapsedSec), style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
                    Text("·", style = NuruType.micro, color = Color.White.copy(alpha = 0.5f))
                    Text("$viewerCount watching", style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            HudIconButton(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", onToggleMute)
            if (isVideo) HudIconButton(Icons.Filled.Cameraswitch, "Flip camera", onFlipCamera)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.danger).clickable { onEndTapped() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) { Text("End", style = NuruType.cardCta, color = Color.White, fontWeight = FontWeight.Bold) }
        }
        title.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(it, style = NuruType.body, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun HudIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun FailedView(reason: String?, onRetry: () -> Unit, onEnd: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.lg)) {
            Text("Connection lost", style = NuruType.title, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                reason ?: "Nuru Live couldn't stay connected after several tries.",
                style = NuruType.body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Retry", onClick = onRetry)
            Spacer(Modifier.height(12.dp))
            Text(
                "End broadcast", style = NuruType.cardCta, color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.clickable { onEnd() }.padding(Spacing.sm),
            )
        }
    }
}

@Composable
private fun SummaryView(title: String, elapsedSec: Int, peakViewers: Int, endedInBackground: Boolean, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.lg)) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Nuru.gold.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                if (endedInBackground) "Nuru Live ended" else "You were live!",
                style = NuruType.title, color = Color.White, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            if (endedInBackground) {
                Text(
                    "The broadcast ended because Nuru left the foreground.",
                    style = NuruType.body, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "${mmss(elapsedSec)} · peak $peakViewers watching",
                style = NuruType.body, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
            )
            title.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = NuruType.caption, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Done", onClick = onDone)
        }
    }
}

private fun mmss(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
