// Nuru Live — Broadcast Studio's app-wide broadcaster brain, the Android
// twin of RadioController: owns exactly one binding to LiveBroadcastService
// so LiveBroadcastScreen, the in-app "tap to return" pill (MainShell), and
// the notification's own re-open action all observe the SAME broadcast
// state no matter which of them is currently on screen. The UI never talks
// to the Service directly — it observes [state] and calls the action
// methods here.
package org.nuruplace.member.feature.live

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object BroadcastController {
    private val _state = MutableStateFlow(BroadcastState())
    val state: StateFlow<BroadcastState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var service: LiveBroadcastService? = null
    private var mirrorJob: Job? = null
    private var appContext: Context? = null

    private data class PendingStart(
        val streamId: String,
        val rtmpUrl: String,
        val streamKey: String,
        val title: String,
        val kind: String,
    )
    private var pendingStart: PendingStart? = null

    // Explicit ServiceConnection type annotation is load-bearing, not
    // style: onServiceConnected() below self-references `connection` (to
    // unbind once the session ends) — without the explicit type, Kotlin's
    // inferencer trips over the circular "infer this property's type from
    // its own initializer, which references the property" and fails with
    // "Type checking has run into a recursive problem".
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? LiveBroadcastService.LocalBinder)?.service ?: return
            service = svc
            pendingStart?.let { p ->
                svc.startBroadcast(p.streamId, p.rtmpUrl, p.streamKey, p.title, p.kind)
                pendingStart = null
            }
            mirrorJob?.cancel()
            mirrorJob = scope.launch {
                svc.state.collect { s -> _state.value = s }
            }
            // Separate one-shot watcher, not folded into the mirror loop
            // above, to sidestep self-cancelling a coroutine from inside its
            // own collect{} (a real Kotlin type-inference recursion trap
            // with StateFlow.collect). The Service already called
            // stopSelf() to end its "started" life the moment it reached
            // SUMMARY — but a service with an active binding lingers until
            // unbound too, so this drops the binding once that happens
            // instead of leaking as a dead-weight bound instance for the
            // rest of the app session.
            scope.launch {
                svc.state.first { it.phase == BroadcastPhase.SUMMARY }
                runCatching { appContext?.unbindService(connection) }
                service = null
                mirrorJob?.cancel()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Only fires on an unexpected process death, not a normal
            // unbind (that path is handled explicitly above).
            service = null
            mirrorJob?.cancel()
            _state.value = BroadcastState()
        }
    }

    /** Idempotent: re-entering the broadcast screen for the SAME streamId
     *  while already bound+running just re-attaches (LiveBroadcastService.
     *  startBroadcast is itself a no-op for a matching streamId) — this is
     *  the "returning to the app re-attaches to the running engine" path.
     *  A cold call (nothing bound yet) starts the foreground service and
     *  queues the start for once the binding completes. */
    fun start(context: Context, streamId: String, rtmpUrl: String, streamKey: String, title: String, kind: String) {
        val app = context.applicationContext
        appContext = app
        val intent = Intent(app, LiveBroadcastService::class.java)
        ContextCompat.startForegroundService(app, intent)
        val svc = service
        if (svc != null) {
            svc.startBroadcast(streamId, rtmpUrl, streamKey, title, kind)
        } else {
            pendingStart = PendingStart(streamId, rtmpUrl, streamKey, title, kind)
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun attachPreview(view: SurfaceView) { service?.broadcaster?.startPreview(view) }
    fun detachPreview() { service?.broadcaster?.stopPreview() }

    fun toggleMute() { service?.toggleMute() }
    fun switchCamera() { service?.switchCamera() }
    fun switchToScreenSource(resultCode: Int, data: Intent) { service?.switchToScreenSource(resultCode, data) }
    fun switchToCameraSource() { service?.switchToCameraSource() }
    fun manualRetry() { service?.manualRetry() }
    fun end() { service?.endBroadcast(viaNotification = false) }
}
