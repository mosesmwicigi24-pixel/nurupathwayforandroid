// Nuru Live L6c — "the congregation SEES the stage", not just the host's
// camera. L6b already got guest AUDIO onto the outgoing RTMP mix
// (GuestAudioMixer.kt) and gave the HOST a local rail of guest video tiles
// (WhepSubscriber.kt + GuestStageUi.kt's HostGuestRail) — but that rail is
// pure Compose UI, rendered only on the broadcaster's own screen; nothing
// about it reaches the RTMP frame the congregation actually watches. This
// file is the missing link: it composites live guest video INTO the same
// RootEncoder GL pipeline that captures the host's camera (or shared
// screen), so the single outgoing stream carries speaker-large + rail.
//
// ── THE MECHANISM (verified against pinned sources, not guessed) ──────────
//
// RootEncoder 2.5.9's GL pipeline (library/.../view/GlStreamInterface.kt +
// encoder/.../render/MainRender.kt, both fetched via `gh api .../contents/
// <path>?ref=2.5.9`): every draw() call first renders the active VideoSource
// (camera or screen) into an off-screen FBO at the LOCKED encoder canvas
// size, then walks a list of BaseFilterRender objects, each one drawing a
// FULL-SCREEN quad textured with (a) the previous stage's output as
// background and (b) its own "object" texture composited on top wherever
// its (percent-based) position/scale maps to — i.e. every filter's output is
// the whole frame, chained. This is RootEncoder's own sanctioned
// sticker/watermark mechanism (BaseObjectFilterRender, object_fragment.glsl)
// — not a hack.
//
// SurfaceFilterRender (encoder/.../render/filters/object/SurfaceFilterRender
// .java) is the one object-filter subclass whose input is an arbitrary
// android.view.Surface (backed by its own SurfaceTexture) rather than a
// static bitmap — exactly what's needed to feed it LIVE frames. Position/
// scale/alpha (BaseObjectFilterRender.setPosition/setScale/setAlpha) are
// percent-of-canvas (Sprite.java's own doc comment: "0,0 top-left, 100,100
// bottom-right"), so tile geometry is resolution- AND orientation-agnostic —
// it rides whatever canvas size StreamBase.prepareVideo locked in (portrait
// broadcasts get a swapped encoderWidth/Height per StreamBase.kt's
// `if (rotation==90||270) glInterface.setEncoderSize(height,width)`, fetched
// and confirmed), so this NEVER touches or needs to know the actual pixel
// resolution, satisfying "never change encoded dimensions mid-publish."
//
// Getting a WebRTC VideoTrack's decoded frames INTO that Surface: WebRTC's
// own org.webrtc.EglRenderer (verified via `javap` against the resolved
// io.github.webrtc-sdk:android:144.7559.09 classes.jar — the same artifact
// this app already depends on) `implements VideoSink` (so `videoTrack.
// addSink(eglRenderer)` just works, additively, exactly like
// GuestAudioMixer's AudioTrackSink precedent) and exposes `createEglSurface
// (android.view.Surface)` to bind ANY Surface as its render target, plus
// `init(EglBase.Context, int[], RendererCommon.GlDrawer)` to share GL
// context with LiveWebRtc's existing EglBase (so the VideoFrame's
// hardware texture, itself decoded in that same shared context, is valid to
// sample). Critically, EglRenderer's target Surface and SurfaceFilterRender's
// consuming SurfaceTexture do NOT need to share a GL context with each
// OTHER — Android Surface/SurfaceTexture is a BufferQueue at the platform
// level, deliberately cross-process/cross-context safe. So RootEncoder's own
// internal GL context (GlStreamInterface's private SurfaceManager) and
// WebRTC's EglRenderer (LiveWebRtc.eglBase's context) can each run their own
// independent GL thread and still hand frames to each other through that
// Surface — no shared-context plumbing between the two libraries required
// beyond what LiveWebRtc already centralizes for WebRTC's own side.
//
// ── FIXED SLOTS, NOT PER-GUEST FILTERS ─────────────────────────────────────
//
// A naive design would add/remove a SurfaceFilterRender per guest as they
// join/leave or as the active speaker changes. That breaks compositing
// order: MainRender chains filters by INSERTION order (reOrderFilters()'s
// `previousTexId` links), so whichever filter was added first always
// renders "underneath". If the active speaker swapped to a guest who joined
// EARLIER than someone now relegated to the rail, that earlier-inserted
// rail tile would end up drawn UNDER the (now full-frame) speaker overlay
// and vanish — a real ordering bug, not a hypothetical. Instead this file
// pre-allocates MAX_GUESTS FIXED slots once per broadcast (slot 0 = speaker
// role, always drawn first/underneath; slots 1..5 = rail, always drawn
// after/on top) and only ever REASSIGNS which guestId's VideoTrack feeds a
// given slot's already-created EglRenderer (removeSink the old track,
// addSink the new one) — the GL filter objects themselves, and their
// Surface/SurfaceTexture, live for the whole broadcast. Reassigning a slot's
// role is then just a plain Kotlin field write + two addSink/removeSink
// calls — no GL churn, no flicker, no ordering bug possible.
package org.nuruplace.member.feature.live

import android.graphics.Color
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender
import com.pedro.library.view.GlStreamInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoTrack

private const val REFLOW_INTERVAL_MS = 1_200L
private const val QUICK_RETRY_MS = 300L // catches a just-attached guest whose GL surface wasn't ready yet on the first reflow

// Rail tile geometry — percent-of-canvas (see file header), stacked down the
// top-right edge. Sized close to GuestStageUi.kt's own 96x128dp host-side
// rail tiles (~0.75 aspect) for a visually consistent "tile" language between
// what the host sees locally and what the congregation sees composited.
private const val RAIL_TILE_W = 16f
private const val RAIL_TILE_H = 21f
private const val RAIL_GAP_Y = 2.5f
private const val RAIL_MARGIN_TOP = 8f
private const val RAIL_MARGIN_RIGHT = 3f
private const val RAIL_PLATE_H = 4.2f

// Speaker-slot name plate — small, bottom-left, deliberately subtle (this is
// the "speaker" view, not a lower-third graphic).
private const val SPEAKER_PLATE_W = 30f
private const val SPEAKER_PLATE_H = 4.5f

/** Host-side only: composites live guest video into the SAME RootEncoder GL
 *  pipeline that captures the host's camera/screen, so the outgoing RTMP
 *  stream — not just the host's own HUD — carries the guest stage. See this
 *  file's header for the full verified mechanism. Bound once per VIDEO
 *  broadcast (LiveBroadcastService.startBroadcast, mirroring GuestAudioMixer.
 *  reset()'s "no stale guest from a previous session" discipline) and torn
 *  down on cleanupEngine(). A no-op for audio-only broadcasts (never bound —
 *  attachVideo/detachVideo calls simply queue against a null glInterface and
 *  are dropped on reset(), since GenericOnlyAudio has no GL pipeline at all). */
object GuestStageCompositor {

    private class Slot(val filter: SurfaceFilterRender, val nameTag: TextObjectFilterRender) {
        var renderer: EglRenderer? = null
        var guestId: String? = null
        var track: VideoTrack? = null
        var lastPlateText: String = ""
    }

    // Fixed-size once bound (MAX_GUESTS slots — see file header on why fixed
    // slots, not per-guest filters). Index 0 is the speaker role.
    private val slots = mutableListOf<Slot>()

    // Insertion order == join order, needed for the "else most-recent guest"
    // fallback in the task brief's speaker-selection chain.
    private val liveTracks = LinkedHashMap<String, VideoTrack>()
    private val names = HashMap<String, String>()

    @Volatile private var glInterface: GlStreamInterface? = null
    @Volatile private var screenSource = false
    private var reflowJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    @Synchronized
    fun bind(gl: GlStreamInterface) {
        unbindLocked()
        glInterface = gl
        repeat(MAX_GUESTS) {
            val filter = SurfaceFilterRender()
            val nameTag = TextObjectFilterRender()
            // Hidden until a guest is actually assigned to this slot —
            // BaseObjectFilterRender.setAlpha, a plain field write consumed
            // at the next draw(), NOT a GL call itself (safe off the GL
            // thread — see the per-call comments below).
            filter.setAlpha(0f)
            nameTag.setAlpha(0f)
            gl.addFilter(filter)
            gl.addFilter(nameTag)
            slots += Slot(filter, nameTag)
        }
        reflowJob = scope.launch {
            while (true) {
                delay(REFLOW_INTERVAL_MS)
                reflow()
            }
        }
    }

    /** Screen/Document mode (LiveBroadcastService.switchToScreenSource/
     *  useCameraSource) — task brief item 4: "the shared screen is large and
     *  guests are the rail", i.e. NO guest is ever promoted to the speaker
     *  slot while sharing; every live guest goes in the rail instead. */
    @Synchronized
    fun setScreenSource(isScreen: Boolean) {
        screenSource = isScreen
        reflow()
    }

    /** LiveBroadcastScreen pushes guestId->fullName here whenever its pulse
     *  poll refreshes (LiveGuestRow carries the name; this compositor only
     *  ever sees WebRTC tracks + ids, never the REST row). Best-effort — a
     *  guest whose name hasn't arrived yet just reads "Guest" on their plate. */
    @Synchronized
    fun updateNames(map: Map<String, String>) {
        names.clear()
        names.putAll(map)
    }

    /** Called by WhepSubscriber.onTrack for the VideoTrack case, additively
     *  alongside the existing Compose-rail attach and GuestAudioMixer's audio
     *  attach — this is a THIRD independent consumer of the same remote
     *  track, not a replacement for either. */
    @Synchronized
    fun attachVideo(guestId: String, track: VideoTrack) {
        liveTracks[guestId] = track
        reflow()
        scope.launch { delay(QUICK_RETRY_MS); reflow() }
    }

    /** Called by WhepSubscriber.stop() — mirrors GuestAudioMixer.detach(). */
    @Synchronized
    fun detachVideo(guestId: String) {
        liveTracks.remove(guestId)
        reflow()
    }

    /** Called at broadcast start (before [bind], same "no stale guest" spot
     *  as GuestAudioMixer.reset()) and at cleanupEngine() (broadcast end). */
    @Synchronized
    fun reset() = unbindLocked()

    private fun unbindLocked() {
        reflowJob?.cancel()
        reflowJob = null
        val gl = glInterface
        slots.forEach { slot ->
            val renderer = slot.renderer
            if (renderer != null) {
                slot.track?.let { t -> runCatching { t.removeSink(renderer) } }
                runCatching { renderer.release() }
            }
            gl?.let {
                runCatching { it.removeFilter(slot.filter) }
                runCatching { it.removeFilter(slot.nameTag) }
            }
        }
        slots.clear()
        liveTracks.clear()
        names.clear()
        screenSource = false
        glInterface = null
    }

    // ── speaker selection ───────────────────────────────────────────────
    // "Active speaker = loudest guest if an audio-level signal is available,
    // else most-recent guest, else host large" (task brief, verbatim chain).
    // GuestAudioMixer.currentLevels() is the SAME smoothed level already
    // computed off the mixed-audio PCM path (GuestAudioMixer.kt) — no
    // separate audio tap.
    private fun pickSpeaker(ids: List<String>): String? {
        if (ids.isEmpty()) return null // "host large" — reflow() below adds no speaker-slot overlay at all
        val levels = GuestAudioMixer.currentLevels()
        val loudest = ids.mapNotNull { id -> levels[id]?.let { id to it } }.maxByOrNull { it.second }
        return loudest?.first ?: ids.last() // most-recently-joined (LinkedHashMap insertion order)
    }

    // ── slot assignment + layout ────────────────────────────────────────

    @Synchronized
    private fun reflow() {
        val gl = glInterface ?: return
        val ids = liveTracks.keys.toList()
        val speakerId = if (screenSource) null else pickSpeaker(ids)
        val railIds = ids.filter { it != speakerId }.take(MAX_GUESTS - 1)

        assignSlot(0, speakerId)
        railIds.forEachIndexed { i, id -> assignSlot(i + 1, id) }
        for (i in (railIds.size + 1) until slots.size) assignSlot(i, null)

        // Wire up any slot whose renderer couldn't be created yet last time
        // (SurfaceFilterRender's Surface is only valid once its addFilter()
        // has actually been processed on the GL render thread — queued/
        // async, see GlStreamInterface.addFilter's filterQueue). Idempotent:
        // a slot whose renderer already exists is skipped entirely.
        slots.forEach { slot ->
            if (slot.guestId != null && slot.renderer == null) {
                ensureRenderer(slot)
                val renderer = slot.renderer
                if (renderer != null) slot.track?.let { t -> runCatching { t.addSink(renderer) } }
            }
        }

        slots.forEachIndexed { i, slot -> positionSlot(i, slot) }
    }

    private fun assignSlot(index: Int, guestId: String?) {
        val slot = slots.getOrNull(index) ?: return
        if (slot.guestId == guestId) return
        val oldRenderer = slot.renderer
        if (slot.guestId != null && oldRenderer != null) {
            slot.track?.let { t -> runCatching { t.removeSink(oldRenderer) } }
        }
        slot.guestId = guestId
        slot.track = guestId?.let { liveTracks[it] }
        slot.lastPlateText = ""
        if (guestId != null && oldRenderer != null) {
            slot.track?.let { t -> runCatching { t.addSink(oldRenderer) } }
        }
    }

    /** Surface is only non-null once SurfaceFilterRender's initGlFilter() has
     *  actually run on RootEncoder's GL thread (async relative to gl.
     *  addFilter() at [bind] time) — returns early (no-op) until then;
     *  [reflow]'s periodic retry picks it up on the next tick. */
    private fun ensureRenderer(slot: Slot) {
        if (slot.renderer != null) return
        val surface = slot.filter.surface ?: return
        val renderer = EglRenderer("nuru-guest-stage")
        runCatching {
            renderer.init(LiveWebRtc.eglBase.eglBaseContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
            renderer.createEglSurface(surface)
        }.onFailure { return }
        slot.renderer = renderer
    }

    private fun positionSlot(index: Int, slot: Slot) {
        if (slot.guestId == null) {
            slot.filter.setAlpha(0f)
            slot.nameTag.setAlpha(0f)
            return
        }
        val name = names[slot.guestId].orEmpty().ifBlank { "Guest" }.take(16)
        if (index == 0) {
            // Speaker slot — near-fullscreen (see file header: this is the
            // "large" half of "speaker-large + rail"; it renders first/
            // underneath so any rail tile drawn after it stays visible).
            slot.filter.setScale(100f, 100f)
            slot.filter.setPosition(0f, 0f)
            slot.filter.setAlpha(1f)
            slot.nameTag.setScale(SPEAKER_PLATE_W, SPEAKER_PLATE_H)
            slot.nameTag.setPosition(2f, 100f - SPEAKER_PLATE_H - 2f)
            slot.nameTag.setAlpha(1f)
        } else {
            val railIndex = index - 1
            val x = 100f - RAIL_MARGIN_RIGHT - RAIL_TILE_W
            val y = RAIL_MARGIN_TOP + railIndex * (RAIL_TILE_H + RAIL_GAP_Y)
            slot.filter.setScale(RAIL_TILE_W, RAIL_TILE_H)
            slot.filter.setPosition(x, y)
            slot.filter.setAlpha(1f)
            slot.nameTag.setScale(RAIL_TILE_W, RAIL_PLATE_H)
            slot.nameTag.setPosition(x, y + RAIL_TILE_H - RAIL_PLATE_H)
            slot.nameTag.setAlpha(1f)
        }
        if (slot.lastPlateText != name) {
            slot.lastPlateText = name
            // setText() only prepares a CPU-side Bitmap (TextStreamObject.
            // load, plain android.graphics.Canvas) and flags shouldLoad —
            // the actual GL texture upload happens later inside drawFilter()
            // on the render thread (BaseObjectFilterRender, verified
            // source), so this is safe to call from here (reflow runs on
            // Dispatchers.Main.immediate, never the GL thread).
            runCatching { slot.nameTag.setText(name, 56f, Color.WHITE, Color.argb(150, 0, 0, 0)) }
        }
    }
}
