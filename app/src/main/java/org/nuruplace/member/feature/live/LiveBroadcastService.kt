// Nuru Live — Broadcast Studio's foreground service (owner-directed rework,
// see the arc's task doc). Owns the RootEncoder engine for the WHOLE life of
// a broadcast: LiveBroadcastScreen only ever reaches it through
// BroadcastController, and — this is the point — the engine keeps running
// (camera/mic capture, encoding, RTMP publish) whether or not that screen is
// even in composition. Before this rework the engine lived inside the
// Compose screen itself and LifecycleEventEffect(ON_STOP) called
// endStream(background = true) the instant the app backgrounded — so
// leaving the live screen, switching tabs, or locking the phone always
// ended the broadcast. That behavior is REMOVED here, deliberately: this
// service is what makes "the stream must survive" true instead of aspirational.
package org.nuruplace.member.feature.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.pedro.common.ConnectChecker
import com.pedro.common.onMainThreadHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nuruplace.member.MainActivity
import org.nuruplace.member.R
import org.nuruplace.member.data.net.Net

class LiveBroadcastService : Service() {

    inner class LocalBinder : Binder() {
        val service: LiveBroadcastService get() = this@LiveBroadcastService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(BroadcastState())
    val state: StateFlow<BroadcastState> = _state.asStateFlow()

    /** The live RootEncoder engine for the current session, if any. Compose
     *  reaches this ONLY to attach/detach its SurfaceView preview
     *  (startPreview/stopPreview) — every state-changing action (mute,
     *  camera flip, source switch, end) goes through this Service's own
     *  methods below so the state machine has one writer. */
    var broadcaster: Broadcaster? = null
        private set

    private var mediaProjection: MediaProjection? = null
    // Set right before WE call stopStream() ourselves (End flow) so the
    // ConnectChecker's onDisconnect() doesn't mistake an intentional stop
    // for a dropped connection and try to reconnect.
    private var endingIntentionally = false

    private val notificationManager by lazy { getSystemService<NotificationManager>() }
    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nuru Live broadcast", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows while you're broadcasting Nuru Live."
                    setShowBadge(false)
                },
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            endBroadcast(viaNotification = true)
        }
        return START_NOT_STICKY
    }

    // ── Session lifecycle ────────────────────────────────────────────────

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {}
        override fun onConnectionSuccess() {
            onMainThreadHandler {
                _state.update {
                    it.copy(
                        phase = BroadcastPhase.LIVE,
                        retryAttempt = 0,
                        startedAtMillis = it.startedAtMillis ?: System.currentTimeMillis(),
                    )
                }
            }
        }
        override fun onConnectionFailed(reason: String) {
            onMainThreadHandler { handleDrop(reason) }
        }
        override fun onDisconnect() {
            onMainThreadHandler {
                if (!endingIntentionally && _state.value.phase == BroadcastPhase.LIVE) handleDrop("Disconnected")
            }
        }
        override fun onAuthError() {
            onMainThreadHandler {
                runCatching { broadcaster?.stopStream() }
                _state.update { it.copy(phase = BroadcastPhase.FAILED, failReason = "Authentication failed.") }
            }
        }
        override fun onAuthSuccess() {}
        override fun onNewBitrate(bitrate: Long) {}
    }

    private fun handleDrop(reason: String) {
        val s = _state.value
        if (endingIntentionally || s.phase == BroadcastPhase.ENDING || s.phase == BroadcastPhase.SUMMARY) return
        if (s.retryAttempt < MAX_RETRIES) {
            val delayMs = RETRY_BACKOFFS_MS.getOrElse(s.retryAttempt) { RETRY_BACKOFFS_MS.last() }
            _state.update { it.copy(phase = BroadcastPhase.RECONNECTING, retryAttempt = it.retryAttempt + 1) }
            val willRetry = runCatching { broadcaster?.client()?.reTry(delayMs, reason, null) }.getOrDefault(false) == true
            if (!willRetry) {
                runCatching { broadcaster?.stopStream() }
                _state.update { it.copy(phase = BroadcastPhase.FAILED, failReason = reason) }
            }
        } else {
            runCatching { broadcaster?.stopStream() }
            _state.update { it.copy(phase = BroadcastPhase.FAILED, failReason = reason) }
        }
    }

    /** Idempotent per streamId — a second call for the SAME session (the
     *  "returning to the app re-attaches to the running engine" path) is a
     *  no-op; a call for a genuinely different session tears down whatever
     *  was running first (defensive — shouldn't happen in practice since the
     *  server refuses a second concurrent broadcast per scope). */
    fun startBroadcast(streamId: String, rtmpUrl: String, streamKey: String, title: String, kind: String) {
        if (_state.value.session?.streamId == streamId) return
        if (_state.value.session != null) cleanupEngine()

        val session = BroadcastSession(streamId, rtmpUrl, streamKey, title, kind)
        _state.value = BroadcastState(session = session)
        endingIntentionally = false

        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(session), foregroundTypeFor(session, screenSharing = false))

        val (built, ok) = buildBroadcaster(applicationContext, connectChecker, kind)
        broadcaster = built
        if (ok) {
            runCatching { built.startStream(buildPublishUrl(rtmpUrl, streamId, streamKey)) }
                .onFailure { e -> _state.update { it.copy(phase = BroadcastPhase.FAILED, failReason = e.message ?: "Couldn't start the stream.") } }
        } else {
            _state.update { it.copy(phase = BroadcastPhase.FAILED, failReason = "Camera or microphone couldn't be configured on this device.") }
        }
    }

    fun toggleMute() {
        val muted = !_state.value.muted
        broadcaster?.setMuted(muted)
        _state.update { it.copy(muted = muted) }
    }

    fun switchCamera() {
        if (_state.value.source != BroadcastSource.CAMERA) return
        broadcaster?.switchCamera()
    }

    /** Screen (or Document, which rides on the same engine source — see
     *  LiveBroadcastEngine.kt) → the broadcaster already has MediaProjection
     *  consent by the time this is called (LiveBroadcastScreen's
     *  rememberLauncherForActivityResult owns the consent dialog; a Service
     *  can't launch one itself). */
    fun switchToScreenSource(resultCode: Int, data: Intent) {
        val session = _state.value.session ?: return
        _state.update { it.copy(switchingSource = true) }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(session), foregroundTypeFor(session, screenSharing = true))
        mediaProjection?.stop()
        val projection = mediaProjectionManager.getMediaProjection(resultCode, data)
        mediaProjection = projection
        val switched = runCatching { broadcaster?.useScreenSource(projection) }.isSuccess
        _state.update {
            it.copy(source = if (switched) BroadcastSource.SCREEN else BroadcastSource.CAMERA, switchingSource = false)
        }
    }

    fun switchToCameraSource() {
        val session = _state.value.session ?: return
        if (_state.value.source != BroadcastSource.SCREEN) return
        _state.update { it.copy(switchingSource = true) }
        runCatching { broadcaster?.useCameraSource() }
        mediaProjection?.stop()
        mediaProjection = null
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(session), foregroundTypeFor(session, screenSharing = false))
        _state.update { it.copy(source = BroadcastSource.CAMERA, switchingSource = false) }
    }

    fun manualRetry() {
        val session = _state.value.session ?: return
        _state.update { it.copy(phase = BroadcastPhase.CONNECTING, retryAttempt = 0, failReason = null) }
        runCatching { broadcaster?.startStream(buildPublishUrl(session.rtmpUrl, session.streamId, session.streamKey)) }
    }

    /** [viaNotification] just tags the eventual SUMMARY screen ("Nuru Live
     *  ended" vs "You were live!") if the member is still looking at it —
     *  the state machine itself is identical either way; this is the ONLY
     *  path that actually stops the stream (navigating away, backgrounding,
     *  or locking the phone never do — that's the whole point of this
     *  Service). */
    fun endBroadcast(viaNotification: Boolean) {
        val s = _state.value
        if (endingIntentionally || s.phase == BroadcastPhase.SUMMARY) return
        endingIntentionally = true
        val streamId = s.session?.streamId
        _state.update { it.copy(phase = BroadcastPhase.ENDING, endedViaNotification = viaNotification) }
        runCatching { broadcaster?.stopStream() }
        scope.launch {
            // Idempotent + best-effort — never block the summary screen on
            // the network, same precedent as sign-out never blocking on
            // logout. The row still gets cleaned up eventually even if this
            // particular call drops.
            if (streamId != null) runCatching { Net.client.api.postLiveStreamEnd(streamId) }
            _state.update { it.copy(phase = BroadcastPhase.SUMMARY) }
            cleanupEngine()
            ServiceCompat.stopForeground(this@LiveBroadcastService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanupEngine() {
        runCatching { if (broadcaster?.isStreaming == true) broadcaster?.stopStream() }
        runCatching { broadcaster?.release() }
        broadcaster = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onDestroy() {
        // Defensive — normally endBroadcast() already ran cleanupEngine()
        // and awaited the /end network call to completion before stopSelf()
        // got here. This only matters if the system kills the service
        // without an explicit End (e.g. force-stop).
        cleanupEngine()
        scope.cancel()
        super.onDestroy()
    }

    // ── Notification ────────────────────────────────────────────────────

    // FOREGROUND_SERVICE_TYPE_MICROPHONE/CAMERA need API 30 (R);
    // MEDIA_PROJECTION only needs API 29 (Q) but is always requested
    // alongside the other two here, so R is the real floor. Below that,
    // ServiceCompat.startForeground ignores the type param entirely (pre-R
    // foreground services aren't typed), which is correct — nothing to do.
    private fun foregroundTypeFor(session: BroadcastSession, screenSharing: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (session.isVideo && !screenSharing) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (screenSharing) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        return type
    }

    private fun buildNotification(session: BroadcastSession): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra("nuru.dest", liveBroadcastRoute(session))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val endIntent = PendingIntent.getService(
            this, 0,
            Intent(this, LiveBroadcastService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio_notification)
            .setContentTitle("● LIVE — ${session.title.ifBlank { "Nuru Live" }}")
            .setContentText("Tap to return to your broadcast.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, "End", endIntent)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "nuru_live_broadcast"
        const val NOTIFICATION_ID = 2001
        const val ACTION_END = "org.nuruplace.member.live.END_BROADCAST"
    }
}
