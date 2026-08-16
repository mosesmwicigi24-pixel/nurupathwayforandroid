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
import android.util.Log
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

private const val TAG = "BroadcastController"

object BroadcastController {
    private val _state = MutableStateFlow(BroadcastState())
    val state: StateFlow<BroadcastState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var service: LiveBroadcastService? = null
    private var mirrorJob: Job? = null
    private var appContext: Context? = null

    // ── Preview attach race (2026-07-31 device report: RTMP publish healthy
    // — packets flowing, SurfaceView alive per SurfaceFlinger — but the
    // broadcaster's own preview stayed black for 20+ seconds) ─────────────
    //
    // ROOT CAUSE: LiveBroadcastScreen's SurfaceView.Callback.surfaceCreated()
    // calls attachPreview() the moment the OS hands it a valid Surface —
    // which races LiveBroadcastService's own async startup: start() below
    // either binds (bindService is async) or, even once bound, startBroadcast
    // still has to buildBroadcaster() (open the camera, prepareVideo/
    // prepareAudio — not instant). Before this fix, attachPreview() dialed
    // `service?.broadcaster?.startPreview(view)` — a null-safe chain that
    // silently no-oped whenever either side of that chain wasn't ready yet.
    // SurfaceHolder.Callback.surfaceCreated() fires EXACTLY ONCE per surface
    // lifecycle (verified against RootEncoder 2.5.9's own StreamBase.
    // startPreview(SurfaceView) — the non-autoHandle overload this app uses
    // takes a one-shot `surfaceView.holder.surface` snapshot, no listener of
    // its own), so nothing ever retried the attach once the engine DID
    // become ready a moment later. Meanwhile startStream() (called from
    // startBroadcast(), independent of any preview) kept publishing just
    // fine — exactly the "encoder healthy, preview dead" split the device
    // logs showed.
    //
    // FIX: remember whatever SurfaceView Compose most recently handed us and
    // (re)try the attach both there AND at every point `service.broadcaster`
    // can transition from null to non-null (the end of the pendingStart
    // branch in onServiceConnected, and the "already bound" branch of
    // start() below) — closing the race regardless of which side wins it.
    // Broadcaster.startPreview() already no-ops via RootEncoder's own
    // `isOnPreview` flag (LiveBroadcastEngine.kt's VideoBroadcaster), so a
    // redundant reattach call is always harmless.
    private var pendingPreviewView: SurfaceView? = null

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
            // Engine just became reachable through `svc` (fresh startBroadcast
            // above, or a rebind to an already-running one) — if Compose
            // already tried (and no-oped) an attachPreview() before this
            // connection completed, this is where that race gets closed.
            reattachPendingPreview(svc)
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
            reattachPendingPreview(svc)
        } else {
            pendingStart = PendingStart(streamId, rtmpUrl, streamKey, title, kind)
            app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    /** Called from LiveBroadcastScreen's SurfaceHolder.Callback.surfaceCreated
     *  — see this object's header comment for the race this closes. Always
     *  remembers [view] (even when the engine isn't ready yet) so a later
     *  [reattachPendingPreview] can pick it up. */
    fun attachPreview(view: SurfaceView) {
        pendingPreviewView = view
        val broadcaster = service?.broadcaster
        Log.d(TAG, "attachPreview: view=$view engineReady=${broadcaster != null}")
        if (broadcaster == null) {
            Log.d(TAG, "attachPreview: engine not ready yet — deferring, will reattach once it is")
            return
        }
        runCatching { broadcaster.startPreview(view) }
            .onSuccess { Log.d(TAG, "attachPreview: startPreview() succeeded") }
            .onFailure { e -> Log.w(TAG, "attachPreview: startPreview() threw", e) }
    }

    fun detachPreview() {
        Log.d(TAG, "detachPreview: view=$pendingPreviewView")
        runCatching { service?.broadcaster?.stopPreview() }
            .onFailure { e -> Log.w(TAG, "detachPreview: stopPreview() threw", e) }
        pendingPreviewView = null
    }

    /** Attaches whatever SurfaceView Compose most recently handed [attachPreview]
     *  (if any) now that [svc]'s engine is confirmed reachable — the other
     *  half of the race fix documented on this object's header. Safe to call
     *  speculatively (no-ops via `pendingPreviewView`/`broadcaster` being
     *  null, and RootEncoder's own `isOnPreview` guard against a double
     *  start — see LiveBroadcastEngine.kt's VideoBroadcaster.startPreview). */
    private fun reattachPendingPreview(svc: LiveBroadcastService) {
        val view = pendingPreviewView ?: return
        val broadcaster = svc.broadcaster ?: return
        Log.d(TAG, "reattachPendingPreview: engine ready now, attaching deferred view=$view")
        runCatching { broadcaster.startPreview(view) }
            .onSuccess { Log.d(TAG, "reattachPendingPreview: startPreview() succeeded") }
            .onFailure { e -> Log.w(TAG, "reattachPendingPreview: startPreview() threw", e) }
    }

    /** The stream_id THIS device is currently broadcasting, if any — used by
     *  LiveDiscoveryCenter so a broadcaster is never offered their OWN
     *  stream as something to "Join live" (see that file's header for the
     *  2026-07-31 device report this fixes; Android twin of the iOS
     *  self-stream discovery fix). Deliberately excludes SUMMARY: by then
     *  the broadcast is genuinely over and its stream_id should behave like
     *  any other ended stream (it also naturally never appears in a live
     *  GET /live/now response by then, but this keeps the guard consistent
     *  rather than relying on that alone). */
    fun activeSelfStreamId(): String? = selfStreamIdFrom(state.value)

    fun toggleMute() { service?.toggleMute() }
    fun switchCamera() { service?.switchCamera() }
    fun switchToScreenSource(resultCode: Int, data: Intent) { service?.switchToScreenSource(resultCode, data) }
    fun switchToCameraSource() { service?.switchToCameraSource() }
    fun manualRetry() { service?.manualRetry() }
    fun end() { service?.endBroadcast(viaNotification = false) }
}
