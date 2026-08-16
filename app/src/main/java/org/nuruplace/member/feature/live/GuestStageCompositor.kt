// Nuru Live L6c/L6d — "the congregation SEES the stage", not just the host's
// camera. L6b already got guest AUDIO onto the outgoing RTMP mix
// (GuestAudioMixer.kt) and gave the HOST a local rail of guest video tiles
// (WhepSubscriber.kt + GuestStageUi.kt's rail) — but that rail is pure
// Compose UI, rendered only on the broadcaster's own screen; nothing about it
// reaches the RTMP frame the congregation actually watches. This file is the
// missing link: it composites live guest video INTO the same RootEncoder GL
// pipeline that captures the host's camera (or shared screen), so the single
// outgoing stream carries a Zoom-style stage — a large main tile plus a
// vertical guest rail — not a guest silently replacing the host.
//
// ── THE MECHANISM (verified against pinned sources, not guessed) ──────────
//
// RootEncoder 2.5.9's GL pipeline (library/.../view/GlStreamInterface.kt +
// encoder/.../render/MainRender.kt, both fetched via `unzip` on the pinned
// library-2.5.9.aar/encoder-2.5.9.aar): every draw() call first renders the
// active VideoSource (camera or screen) into an off-screen FBO at the LOCKED
// encoder canvas size, then walks a list of BaseFilterRender objects, each
// one drawing a FULL-SCREEN quad textured with (a) the previous stage's
// output as background and (b) its own "object" texture composited on top
// wherever its (percent-based) position/scale maps to — i.e. every filter's
// OUTPUT QUAD covers the whole canvas; only the OBJECT's texture-coordinate
// varying (computed from the filter's Sprite transform) decides, per pixel,
// whether that pixel shows the object or falls through to the background
// (verified straight from the decompiled encoder/.../res/raw/
// object_fragment.glsl / surface_fragment.glsl source: `if
// (vTextureObjectCoord outside [0,1]) { background } else { object }`). This
// is RootEncoder's own sanctioned sticker/watermark mechanism
// (BaseObjectFilterRender, SurfaceFilterRender) — not a hack.
//
// Position/scale/alpha (BaseObjectFilterRender.setPosition/setScale/setAlpha)
// are percent-of-canvas (Sprite.java's own doc comment: "0,0 top-left,
// 100,100 bottom-right"), so tile geometry is resolution- AND
// orientation-agnostic — it rides whatever canvas size StreamBase.
// prepareVideo locked in (portrait broadcasts get a swapped
// encoderWidth/Height per StreamBase.kt's `if (rotation==90||270)
// glInterface.setEncoderSize(height,width)`, fetched and confirmed), so this
// NEVER touches or needs to know the actual pixel resolution, satisfying
// "never change encoded dimensions mid-publish." [bind] queries and logs
// GlStreamInterface.getEncoderSize() as proof.
//
// Getting a WebRTC VideoTrack's decoded frames INTO that Surface: WebRTC's
// own org.webrtc.EglRenderer (verified via `javap` against the resolved
// io.github.webrtc-sdk:android:144.7559.09 classes.jar) `implements
// VideoSink` (so `videoTrack.addSink(eglRenderer)` just works, additively,
// exactly like GuestAudioMixer's AudioTrackSink precedent).
//
// ── DEFECT A ROOT CAUSE (2026-07-31 owner report, "portrait seems lost a
// bit" the moment a guest joins) — PROVEN, not assumed ────────────────────
// BaseObjectFilterRender/Sprite do a NAIVE FULL-TEXTURE-TO-SPRITE-RECT
// mapping with ZERO aspect-ratio correction (verified: the object's texture
// coordinate is sampled directly from the sprite's own transformed vertices,
// no separate crop/UV math anywhere in Sprite.java or
// BaseObjectFilterRender.java). So whatever a guest's WebRTC EglRenderer
// wrote into the object SurfaceTexture — at WHATEVER aspect that frame
// naturally is (e.g. a phone held landscape, ~16:9) — gets stretched to
// exactly fill the sprite's rect, with no aspect preservation at all. The
// FIX is upstream of RootEncoder entirely: WebRTC's own `EglRenderer.
// setLayoutAspectRatio(float)` (verified present on the pinned SDK) makes
// the EglRenderer itself CENTER-CROP the incoming frame to a given aspect
// before ever writing to the Surface — so as long as that target aspect
// matches the destination sprite rect's own aspect (computed from
// [mainTileRect]/[railTileRects] below), RootEncoder's later naive stretch
// introduces ZERO further distortion (matching-aspect content stretched into
// a matching-aspect rect is the identity transform). [ensureRenderer] wires
// this once per slot, matching that slot's FIXED role (main vs rail — see
// below), never touching the canvas/encoder dimensions themselves.
//
// ── DEFECT B ROOT CAUSE (owner report, "the host video is completely
// replaced by invited guest") — PROVEN, not assumed ────────────────────────
// The previous version of this file auto-promoted a guest (loudest, else
// most-recent) into the full-canvas "speaker" slot the instant ANY guest was
// live — with no way to opt out. Since the host has no dedicated compositor
// slot at all (the host's camera IS the base/background layer the filter
// chain draws on top of — free, zero-cost, always correct aspect), a guest
// filling that same full-canvas rect necessarily covered the host
// completely. The FIX: slot 0 is now driven ONLY by an explicit
// [StageSpotlightState] the host sets by tapping a guest tile (never
// auto-promoted) — see [setSpotlight]. Nobody spotlighted (the default,
// including "a guest just joined") means slot 0 stays empty and the host's
// own camera shows through the background layer untouched, exactly as it
// did before ANY guest existed.
//
// ── FIXED SLOTS, NOT PER-GUEST FILTERS ─────────────────────────────────────
// A naive design would add/remove a filter per guest as they join/leave or
// as the spotlight changes. That breaks compositing order: MainRender chains
// filters by INSERTION order (reOrderFilters()'s `previousTexId` links), so
// whichever filter was added first always renders "underneath" — if the
// spotlight swapped to a guest who joined EARLIER than someone now relegated
// to the rail, that earlier-inserted rail tile would end up drawn UNDER the
// (now full-frame) main-slot overlay and vanish — a real ordering bug, not a
// hypothetical. Instead this file pre-allocates [TOTAL_SLOT_COUNT] FIXED
// slots once per broadcast (slot 0 = main role, always drawn first/
// underneath, using the STOCK SurfaceFilterRender since it's a plain
// full-bleed rect with no chrome; slots 1..[RAIL_SLOT_COUNT] = rail, always
// drawn after/on top, using [StageTileFilterRender] for its rounded-corner +
// border chrome) and only ever REASSIGNS which guestId's VideoTrack feeds a
// given slot's already-created EglRenderer (removeSink the old track,
// addSink the new one) — the GL filter objects themselves, and their
// Surface/SurfaceTexture, live for the whole broadcast. Reassigning a slot's
// role is then just a plain Kotlin field write + two addSink/removeSink
// calls — no GL churn, no flicker, no ordering bug possible. RAIL_SLOT_COUNT
// (== MAX_GUESTS) is sized so "every OTHER live guest, plus one Host
// placeholder tile when spotlighting" ([railItems]) never exceeds capacity —
// see that function's own doc for the exact accounting.
package org.nuruplace.member.feature.live

import android.graphics.Color
import android.util.Log
import android.view.Surface
import com.pedro.encoder.input.gl.render.filters.`object`.BaseObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.SurfaceFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender
import com.pedro.library.view.GlStreamInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoTrack

private const val TAG = "GuestStageCompositor"

private const val REFLOW_INTERVAL_MS = 1_200L
private const val QUICK_RETRY_MS = 300L // catches a just-attached guest whose GL surface wasn't ready yet on the first reflow
// internal, not private: LiveStageView.kt's Compose stage (the host's own
// on-screen preview) animates its tile geometry over the SAME duration, so
// a spotlight swap looks identical in timing on both the composited
// broadcast and the host's own screen — one constant, not two numbers that
// could drift apart.
internal const val SWAP_ANIM_MS = 250L // task brief: "~250ms, match the app's existing motion grammar" (LiveInteractions.kt/LivePlayerScreen.kt's own tween durations)
private const val SWAP_ANIM_STEPS = 12

/** How many guests can be live on stage at once — the pre-existing L6 cap,
 *  defined in LiveBroadcastInteractions.kt (guest-invite UI reads it too). */
internal const val RAIL_SLOT_COUNT = MAX_GUESTS
private const val TOTAL_SLOT_COUNT = RAIL_SLOT_COUNT + 1 // + slot 0, the main/speaker role

// Rail tile geometry — fractions of canvas width/height, stacked down the
// RIGHT edge (task brief: "guest thumbnails down the RIGHT side, vertically
// stacked, roughly 22% of canvas width"). RAIL_TILE_ASPECT MUST stay in sync
// with stage_rail_fragment.glsl's own `ASPECT` constant (see
// StageTileFilterRender.kt's header for why that's baked into the shader
// rather than passed as a uniform).
internal const val RAIL_TILE_ASPECT = 0.75f // width:height, matches GuestStageUi.kt's existing 96:128dp look
private const val RAIL_WIDTH_FRACTION = 0.22f
// Margins/spacing (2026-07-31 owner parity pass) — matched EXACTLY to iOS's
// LiveStageLayout.swift (`marginFraction = 0.03` applied uniformly to
// top/bottom/right, `spacingFraction = 0.018`) rather than Android's own
// earlier, independently-chosen numbers, so the rail reads at the same
// visual proportions on both platforms. The overflow-proof algebra in
// [railTileRects] doesn't depend on the specific values (see that
// function's own doc + GuestStageCompositorTest's in-bounds coverage), so
// this is a pure visual-parity change, not a behavioral one.
private const val RAIL_MARGIN_TOP_FRACTION = 0.03f
private const val RAIL_MARGIN_BOTTOM_FRACTION = 0.03f
private const val RAIL_MARGIN_RIGHT_FRACTION = 0.03f
private const val RAIL_GAP_FRACTION = 0.018f

// Speaker-slot name plate — small, bottom-left, deliberately subtle (this is
// the "main" view, not a lower-third graphic).
private const val SPEAKER_PLATE_W = 30f
private const val SPEAKER_PLATE_H = 4.5f
private const val RAIL_PLATE_H_FRACTION = 0.16f // fraction of a rail tile's OWN height

// Best-effort "is this guest muted" proxy — GuestAudioMixer's smoothed level
// is a mean absolute 16-bit PCM sample magnitude (0..32767-ish, see that
// file's own doc), NOT a true mute-state signal (no such signal is currently
// plumbed from a guest's own mute button to the host/compositor). A guest
// with no level entry at all (never sent audio) is treated as unknown, not
// muted — only a LIVE, present, near-zero level counts.
private const val MUTED_LEVEL_THRESHOLD = 60f

// Debounce shape for [GuestStageCompositor.mutedGuests] — PORTED from iOS's
// BroadcastController.updateMuteHeuristic() cadence/streak exactly (3000ms
// poll, 3 consecutive quiet polls before flagging muted); the per-sample
// THRESHOLD above is intentionally NOT the same number as iOS's 0.02 — see
// [GuestStageCompositor.mutedGuests]'s doc for why the two scales differ.
private const val MUTE_HEURISTIC_POLL_MS = 3_000L
private const val MUTE_HEURISTIC_STREAK = 3

/** A percent-of-canvas-agnostic pixel rectangle — the shared, pure layout
 *  vocabulary both this file (which converts to RootEncoder's percent-of-
 *  canvas Sprite space) and GuestStageUi.kt's Compose rail (which uses these
 *  same pixel numbers directly against its own measured container) build
 *  from. ONE algorithm feeding both surfaces is what keeps the host's own
 *  preview and the composited broadcast from drifting apart (task brief:
 *  "no drift between preview and broadcast"). */
internal data class TileRect(val x: Float, val y: Float, val width: Float, val height: Float)

/** The main/speaker tile — ALWAYS the full canvas, regardless of guest count
 *  (note the signature takes no guest-count parameter at all: there is
 *  nothing for it to vary with). Proves, by construction, that adding guests
 *  can never resize or reshape the main tile — see GuestStageCompositorTest's
 *  "canvas dimensions never change with guest count" coverage. */
internal fun mainTileRect(canvasWidth: Float, canvasHeight: Float): TileRect =
    TileRect(0f, 0f, canvasWidth, canvasHeight)

/** Up to [RAIL_SLOT_COUNT] tile rects stacked down the canvas's right edge,
 *  top to bottom, in the SAME order as the ids they'll be assigned to (task
 *  brief: "stable ordering"). Tiles keep [RAIL_TILE_ASPECT] and their natural
 *  ~[RAIL_WIDTH_FRACTION]-of-canvas-width size until that many of them would
 *  overflow the available vertical space, at which point they shrink
 *  UNIFORMLY (both axes, so the aspect — and therefore
 *  [ensureRenderer]'s aspect-fill correctness — never changes) just enough
 *  to fit every one of them fully on-canvas (task brief: "must stack without
 *  overflowing the frame — shrink... gracefully rather than clipping"). Pure
 *  math: no overlap and full-bounds containment both fall out of the
 *  algebra (see GuestStageCompositorTest), not a runtime clamp. */
internal fun railTileRects(itemCount: Int, canvasWidth: Float, canvasHeight: Float): List<TileRect> {
    if (itemCount <= 0 || canvasWidth <= 0f || canvasHeight <= 0f) return emptyList()
    val n = itemCount.coerceAtMost(RAIL_SLOT_COUNT)
    val marginTop = canvasHeight * RAIL_MARGIN_TOP_FRACTION
    val marginBottom = canvasHeight * RAIL_MARGIN_BOTTOM_FRACTION
    val marginRight = canvasWidth * RAIL_MARGIN_RIGHT_FRACTION
    val gap = canvasHeight * RAIL_GAP_FRACTION
    val availableHeight = (canvasHeight - marginTop - marginBottom - gap * (n - 1)).coerceAtLeast(0f)
    val naturalWidth = canvasWidth * RAIL_WIDTH_FRACTION
    val naturalHeight = naturalWidth / RAIL_TILE_ASPECT
    val perTileMax = availableHeight / n
    val scale = if (naturalHeight > 0f) (perTileMax / naturalHeight).coerceIn(0f, 1f) else 0f
    val height = naturalHeight * scale
    val width = naturalWidth * scale
    val x = canvasWidth - marginRight - width
    return (0 until n).map { i -> TileRect(x = x, y = marginTop + i * (height + gap), width = width, height = height) }
}

/** The compositor's whole spotlight state, reduced to one nullable id —
 *  small enough to be the SAME state GuestStageCompositor (the RTMP source
 *  of truth) and LiveBroadcastScreen's Compose rail both read, so the two
 *  can never drift (task brief: "the compositor is the source of truth" /
 *  "no drift between preview and broadcast"). [GuestStageCompositor.spotlight]
 *  is that shared StateFlow. */
internal data class StageSpotlightState(val spotlightGuestId: String? = null)

internal fun promoteSpotlight(state: StageSpotlightState, guestId: String): StageSpotlightState =
    state.copy(spotlightGuestId = guestId)

internal fun demoteSpotlight(state: StageSpotlightState): StageSpotlightState =
    if (state.spotlightGuestId == null) state else state.copy(spotlightGuestId = null)

/** Tapping an already-spotlighted guest's OWN tile again reverts to host —
 *  task brief: "reversible by tapping again (or tapping the host's
 *  thumbnail)". */
internal fun toggleSpotlight(state: StageSpotlightState, guestId: String): StageSpotlightState =
    if (state.spotlightGuestId == guestId) demoteSpotlight(state) else promoteSpotlight(state, guestId)

/** The spotlighted guest leaving mid-broadcast must fall back to host, not
 *  freeze on a now-dead track or silently show nothing in the main tile —
 *  task brief: "guest leaves while spotlighted → falls back to host". Called
 *  from both [GuestStageCompositor.detachVideo] (the compositor's own guest
 *  set) and wherever the Compose side's live-guest set changes. */
internal fun reconcileSpotlightOnGuestsChanged(state: StageSpotlightState, liveGuestIds: Set<String>): StageSpotlightState =
    if (state.spotlightGuestId != null && state.spotlightGuestId !in liveGuestIds) demoteSpotlight(state) else state

/** Resolves the rail's actual content, in DISPLAY order, for a given live-
 *  guest set + spotlight state. `null` in the returned list means "the Host
 *  placeholder tile", never a guest. Accounting for why this never exceeds
 *  [RAIL_SLOT_COUNT]: with nobody spotlighted, the rail is every live guest
 *  (<= RAIL_SLOT_COUNT, the pre-existing guest cap). Spotlighting one guest
 *  removes exactly one id from the "live guests" pool (it moves to the main
 *  tile) and adds exactly one Host placeholder entry — net unchanged, so the
 *  rail item count is <= RAIL_SLOT_COUNT either way. The Host placeholder
 *  (when present) is always FIRST — a fixed, predictable position, not
 *  something that shuffles as other guests join/leave. */
internal fun railItems(liveGuestIds: List<String>, spotlightGuestId: String?): List<String?> {
    if (spotlightGuestId == null || spotlightGuestId !in liveGuestIds) return liveGuestIds
    return listOf(null) + liveGuestIds.filter { it != spotlightGuestId }
}

/** Host-side only: composites live guest video into the SAME RootEncoder GL
 *  pipeline that captures the host's camera/screen, so the outgoing RTMP
 *  stream — not just the host's own HUD — carries the Zoom-style stage. See
 *  this file's header for the full verified mechanism. Bound once per VIDEO
 *  broadcast (LiveBroadcastService.startBroadcast, mirroring GuestAudioMixer.
 *  reset()'s "no stale guest from a previous session" discipline) and torn
 *  down on cleanupEngine(). A no-op for audio-only broadcasts (never bound —
 *  attachVideo/detachVideo calls simply queue against a null glInterface and
 *  are dropped on reset(), since GenericOnlyAudio has no GL pipeline at all). */
object GuestStageCompositor {

    private class Slot(val filter: BaseObjectFilterRender, val nameTag: TextObjectFilterRender, val layoutAspect: Float) {
        var renderer: EglRenderer? = null
        var guestId: String? = null
        var track: VideoTrack? = null
        var isHostPlaceholder: Boolean = false
        var lastPlateText: String = ""
        var fadeJob: Job? = null

        fun surface(): Surface? = when (filter) {
            is SurfaceFilterRender -> filter.surface
            is StageTileFilterRender -> filter.surface
            else -> null
        }
    }

    // Fixed-size once bound (TOTAL_SLOT_COUNT slots — see file header on why
    // fixed slots, not per-guest filters). Index 0 is the main/speaker role;
    // 1..RAIL_SLOT_COUNT are rail slots.
    private val slots = mutableListOf<Slot>()

    // Insertion order == join order, needed for [railItems]'s stable
    // ordering.
    private val liveTracks = LinkedHashMap<String, VideoTrack>()
    private val names = HashMap<String, String>()

    private val _spotlight = MutableStateFlow(StageSpotlightState())

    /** The SAME state LiveBroadcastScreen's Compose rail reads to lay itself
     *  out — see this object's own doc and [StageSpotlightState]'s for why a
     *  single shared source of truth is what keeps the host's own preview
     *  and the composited broadcast from drifting apart. */
    internal val spotlight: StateFlow<StageSpotlightState> = _spotlight.asStateFlow()

    // ── Mic-muted heuristic (owner parity request, 2026-07-31: "match iOS's
    // exact behaviour and threshold so the two apps show the same thing at
    // the same moment") ─────────────────────────────────────────────────────
    // iOS's BroadcastController.updateMuteHeuristic() polls WhepSubscriber's
    // WebRTC-stats audioLevel every 3s (the pulse-poll cadence) and only
    // flags a guest muted after MUTE_HEURISTIC_STREAK=3 CONSECUTIVE quiet
    // polls (~9s sustained silence) — a debounce specifically so one quiet
    // moment mid-sentence never flickers the rail icon. This is the SAME
    // debounce shape (poll cadence + consecutive-streak count), ported
    // faithfully — the raw per-sample THRESHOLD is deliberately NOT the same
    // number (0.02) as iOS: Android's signal is GuestAudioMixer's smoothed
    // mean-absolute-16-bit-PCM-sample level (0..32767ish, see
    // MUTED_LEVEL_THRESHOLD's own doc), not WebRTC's normalized 0..1
    // audioLevel stat, so the two thresholds live on different scales by
    // construction — matching the NUMBER would be a coincidence, not
    // parity. What IS ported exactly is the cadence (3000ms) and the streak
    // (3), so a guest muted on one platform reads as muted on the other
    // within the same ~9s window. [mutedGuests] is a StateFlow (not a
    // polling API) specifically so [plateNameFor] (the composited RTMP name
    // plate) and LiveStageView.kt's Compose rail chrome read the EXACT same
    // computed value at the exact same instant — one debounce, not two
    // independently-drifting guesses.
    private val muteStreaks = HashMap<String, Int>()
    private val _mutedGuests = MutableStateFlow<Set<String>>(emptySet())
    val mutedGuests: StateFlow<Set<String>> = _mutedGuests.asStateFlow()
    private var muteHeuristicJob: Job? = null

    @Volatile private var glInterface: GlStreamInterface? = null
    @Volatile private var screenSource = false
    @Volatile private var canvasWidth: Float = 0f
    @Volatile private var canvasHeight: Float = 0f
    private var reflowJob: Job? = null
    private var lastLoggedGuestCount = -1
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    @Synchronized
    fun bind(gl: GlStreamInterface) {
        unbindLocked()
        glInterface = gl
        val size = runCatching { gl.getEncoderSize() }.getOrNull()
        canvasWidth = size?.x?.toFloat() ?: 0f
        canvasHeight = size?.y?.toFloat() ?: 0f
        // Defect A's proof, per the task brief: log the actual encoder
        // dimensions the compositor is about to composite into, so a claim
        // of "portrait geometry unchanged with guests" is verifiable from
        // logcat, not just asserted.
        Log.i(TAG, "bind: encoder canvas = ${canvasWidth.toInt()}x${canvasHeight.toInt()} (0 guests)")
        repeat(TOTAL_SLOT_COUNT) { index ->
            val nameTag = TextObjectFilterRender()
            val filter: BaseObjectFilterRender
            val aspect: Float
            if (index == 0) {
                filter = SurfaceFilterRender()
                aspect = if (canvasHeight > 0f) canvasWidth / canvasHeight else 9f / 16f
            } else {
                filter = StageTileFilterRender()
                aspect = RAIL_TILE_ASPECT
            }
            // Hidden until a guest/placeholder is actually assigned to this
            // slot — BaseObjectFilterRender.setAlpha, a plain field write
            // consumed at the next draw(), NOT a GL call itself (safe off
            // the GL thread — see the per-call comments below).
            filter.setAlpha(0f)
            nameTag.setAlpha(0f)
            gl.addFilter(filter)
            gl.addFilter(nameTag)
            slots += Slot(filter, nameTag, aspect)
        }
        reflowJob = scope.launch {
            while (true) {
                delay(REFLOW_INTERVAL_MS)
                reflow()
            }
        }
        // Independent of [reflowJob]'s own cadence (1200ms, plus an extra
        // 300ms quick-retry tick on attach — see QUICK_RETRY_MS) — the mute
        // heuristic needs its OWN steady 3000ms clock so its 3-consecutive-
        // polls debounce means what it says (an irregular cadence would
        // make "3 polls" a fuzzy amount of wall-clock time instead of the
        // ~9s iOS parity target — see [mutedGuests]'s doc).
        muteHeuristicJob = scope.launch {
            while (true) {
                delay(MUTE_HEURISTIC_POLL_MS)
                updateMuteHeuristic()
            }
        }
    }

    /** Screen/Document mode (LiveBroadcastService.switchToScreenSource/
     *  useCameraSource) — task brief item 4 (pre-existing, unchanged by L6d):
     *  the shared screen is large and guests are the rail; NO guest is ever
     *  spotlighted while sharing. Also clears any existing spotlight so
     *  returning to camera afterwards doesn't silently resurrect a stale
     *  one. */
    @Synchronized
    fun setScreenSource(isScreen: Boolean) {
        screenSource = isScreen
        if (isScreen) _spotlight.value = demoteSpotlight(_spotlight.value)
        reflow()
    }

    /** LiveBroadcastScreen pushes guestId->fullName here whenever its pulse
     *  poll refreshes (LiveGuestRow carries the name; this compositor only
     *  ever sees WebRTC tracks + ids, never the REST guest row). Best-effort —
     *  a guest whose name hasn't arrived yet just reads "Guest" on their
     *  plate. */
    @Synchronized
    fun updateNames(map: Map<String, String>) {
        names.clear()
        names.putAll(map)
    }

    /** The SINGLE entry point that moves the spotlight — LiveStageView.kt's
     *  Compose stage calls this from BOTH the host's own tile tap
     *  (`guestId = null`) and every guest tile's tap (`guestId = that
     *  guest's id`), exactly mirroring iOS's `BroadcastController.
     *  tapStageTile(_:)` (task brief: "iOS uses a single tapStageTile entry
     *  point; mirror that"). Driving the SAME [spotlight] StateFlow that
     *  [reflow] reads for the composited RTMP frame means the host's own
     *  preview and what the congregation sees can never independently
     *  drift — there is no second copy of "who's spotlighted" anywhere in
     *  this app. A no-op while screen-sharing (see [setScreenSource]'s doc —
     *  the same pre-existing product decision this file always had, just
     *  now expressed through the spotlight state instead of an
     *  unconditional "never speaker" flag). */
    @Synchronized
    fun tapStageTile(guestId: String?) {
        if (screenSource) return
        _spotlight.value = if (guestId == null) demoteSpotlight(_spotlight.value) else toggleSpotlight(_spotlight.value, guestId)
        reflow()
    }

    /** Called by WhepSubscriber.onTrack for the VideoTrack case, additively
     *  alongside the existing Compose-rail attach and GuestAudioMixer's audio
     *  attach — this is a THIRD independent consumer of the same remote
     *  track, not a replacement for either.
     *
     *  MAIN-THREAD ONLY, enforced here via withContext (not just assumed of
     *  the caller): RootEncoder's SurfaceFilterRender/StageTileFilterRender —
     *  the Surface [ensureRenderer] binds a WebRTC EglRenderer to —
     *  documents, verbatim in its own source (encoder/.../object/
     *  SurfaceFilterRender.java): "This surface must be rendered using an
     *  api called on main thread to avoid possible errors." WhepSubscriber.
     *  onTrack fires on WebRTC's own signaling thread, never Main, so this
     *  used to violate that contract directly — suspending here and hopping
     *  onto Main.immediate closes that regardless of which thread calls in. */
    suspend fun attachVideo(guestId: String, track: VideoTrack) = withContext(Dispatchers.Main.immediate) {
        synchronized(this@GuestStageCompositor) { liveTracks[guestId] = track }
        reflow()
        delay(QUICK_RETRY_MS)
        reflow()
    }

    /** Called by WhepSubscriber.stop() — mirrors GuestAudioMixer.detach() and
     *  [attachVideo]'s main-thread contract. AWAITED (suspend, not
     *  fire-and-forget) by the caller so its addSink/removeSink work is
     *  GUARANTEED complete before WhepSubscriber disposes the underlying
     *  native track right after — see WhepSubscriber.stopLocked()'s doc for
     *  why that ordering is the whole point. Also reconciles the spotlight
     *  (task brief: "guest leaves while spotlighted → falls back to
     *  host"). */
    suspend fun detachVideo(guestId: String) = withContext(Dispatchers.Main.immediate) {
        synchronized(this@GuestStageCompositor) {
            liveTracks.remove(guestId)
            _spotlight.value = reconcileSpotlightOnGuestsChanged(_spotlight.value, liveTracks.keys)
            // A departed guest's mute icon must never linger on a tile
            // that no longer exists — drop it immediately rather than
            // waiting out the next 3s poll (which would filter it out
            // anyway, but there's no reason to let a stale flag survive
            // even briefly).
            if (muteStreaks.remove(guestId) != null || guestId in _mutedGuests.value) {
                _mutedGuests.value = _mutedGuests.value - guestId
            }
        }
        reflow()
    }

    /** Called at broadcast start (before [bind], same "no stale guest" spot
     *  as GuestAudioMixer.reset()) and at cleanupEngine() (broadcast end). */
    @Synchronized
    fun reset() = unbindLocked()

    private fun unbindLocked() {
        reflowJob?.cancel()
        reflowJob = null
        muteHeuristicJob?.cancel()
        muteHeuristicJob = null
        val gl = glInterface
        slots.forEach { slot ->
            slot.fadeJob?.cancel()
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
        canvasWidth = 0f
        canvasHeight = 0f
        lastLoggedGuestCount = -1
        _spotlight.value = StageSpotlightState()
        muteStreaks.clear()
        _mutedGuests.value = emptySet()
        glInterface = null
    }

    /** Runs every [MUTE_HEURISTIC_POLL_MS] — see [mutedGuests]'s doc for the
     *  full iOS-parity reasoning. Recomputes from the current live-guest set
     *  every tick (cheap: at most [RAIL_SLOT_COUNT] guests) rather than
     *  incrementally, same "simpler to reason about" call [reflow] already
     *  makes for slot layout. */
    @Synchronized
    private fun updateMuteHeuristic() {
        val liveIds = liveTracks.keys
        var changed = false
        val next = _mutedGuests.value.toMutableSet()
        for (id in liveIds) {
            val quiet = isLikelyMuted(id)
            val streak = if (quiet) (muteStreaks[id] ?: 0) + 1 else 0
            muteStreaks[id] = streak
            val likelyMuted = streak >= MUTE_HEURISTIC_STREAK
            if (likelyMuted && next.add(id)) changed = true
            if (!likelyMuted && next.remove(id)) changed = true
        }
        muteStreaks.keys.retainAll(liveIds)
        val stale = next - liveIds
        if (stale.isNotEmpty()) {
            next -= stale
            changed = true
        }
        if (changed) _mutedGuests.value = next
    }

    // ── slot assignment + layout ────────────────────────────────────────

    @Synchronized
    private fun reflow() {
        val gl = glInterface ?: return
        val ids = liveTracks.keys.toList()
        if (ids.size != lastLoggedGuestCount) {
            // Defect A's proof, per the task brief, at the exact moment a
            // guest attaches/detaches — the canvas dims must read IDENTICAL
            // to [bind]'s own log no matter how many guests are live.
            Log.i(TAG, "reflow: guest count ${lastLoggedGuestCount.coerceAtLeast(0)} -> ${ids.size}; encoder canvas STILL ${canvasWidth.toInt()}x${canvasHeight.toInt()}")
            lastLoggedGuestCount = ids.size
        }
        val spotlightId = if (screenSource) null else _spotlight.value.spotlightGuestId?.takeIf { it in liveTracks }
        val items = railItems(ids, spotlightId) // null entries = Host placeholder — see that function's doc

        assignSlot(0, spotlightId, isHostPlaceholder = false)
        items.forEachIndexed { i, id -> assignSlot(i + 1, id, isHostPlaceholder = id == null && spotlightId != null) }
        for (i in (items.size + 1)..RAIL_SLOT_COUNT) assignSlot(i, null, isHostPlaceholder = false)

        // Wire up any slot whose renderer couldn't be created yet last time
        // (SurfaceFilterRender/StageTileFilterRender's Surface is only valid
        // once its addFilter() has actually been processed on the GL render
        // thread — queued/async, see GlStreamInterface.addFilter's
        // filterQueue). Idempotent: a slot whose renderer already exists is
        // skipped entirely. The Host placeholder has no VideoTrack at all —
        // it's drawn purely via its name-tag text mechanism, so it never
        // needs an EglRenderer.
        slots.forEach { slot ->
            if (slot.guestId != null && slot.renderer == null) {
                ensureRenderer(slot)
                val renderer = slot.renderer
                if (renderer != null) slot.track?.let { t -> runCatching { t.addSink(renderer) } }
            }
        }

        // Rail geometry is sized to the number of items ACTUALLY on stage
        // this tick (railItems' own count, including the Host placeholder
        // when present) — not the full RAIL_SLOT_COUNT capacity — so tiles
        // sit at their natural size while few guests are live and only
        // shrink as more join (task brief: "shrink... gracefully"), then
        // grow back the moment someone leaves.
        val railRects = railTileRects(items.size, canvasWidth, canvasHeight)
        slots.forEachIndexed { i, slot -> positionSlot(i, slot, railRects) }
    }

    /** Reassigns a slot's content and returns whether that changed (used to
     *  decide whether [positionSlot] should fade this slot in rather than
     *  snap it — task brief: "no flicker on swap"). `guestId == null &&
     *  isHostPlaceholder` means the Host placeholder tile; `guestId == null
     *  && !isHostPlaceholder` means empty/hidden. */
    private fun assignSlot(index: Int, guestId: String?, isHostPlaceholder: Boolean) {
        val slot = slots.getOrNull(index) ?: return
        val identityChanged = slot.guestId != guestId || slot.isHostPlaceholder != isHostPlaceholder
        if (!identityChanged) return
        val oldRenderer = slot.renderer
        if (slot.guestId != null && oldRenderer != null) {
            slot.track?.let { t -> runCatching { t.removeSink(oldRenderer) } }
        }
        slot.guestId = guestId
        slot.isHostPlaceholder = isHostPlaceholder
        slot.track = guestId?.let { liveTracks[it] }
        slot.lastPlateText = ""
        if (guestId != null && oldRenderer != null) {
            slot.track?.let { t -> runCatching { t.addSink(oldRenderer) } }
        }
        // Fade this slot's new content in (or fade it away to nothing) over
        // SWAP_ANIM_MS instead of an instant alpha pop — task brief: "no
        // flicker on swap" / "Animate the swap smoothly". Geometry (scale/
        // position) is set immediately at its FINAL value by [positionSlot]
        // right after this returns; only alpha is animated, which is safe
        // off the GL thread the same way the existing instant setAlpha(0f)/
        // setAlpha(1f) calls always were (BaseObjectFilterRender.setAlpha is
        // a plain field write, consumed at the next draw() — see this file's
        // header).
        slot.fadeJob?.cancel()
        val active = guestId != null || isHostPlaceholder
        slot.fadeJob = scope.launch { animateAlpha(slot, target = if (active) 1f else 0f) }
    }

    private suspend fun animateAlpha(slot: Slot, target: Float) {
        val stepMs = SWAP_ANIM_MS / SWAP_ANIM_STEPS
        for (step in 1..SWAP_ANIM_STEPS) {
            val t = step.toFloat() / SWAP_ANIM_STEPS
            val eased = 1f - (1f - t) * (1f - t) // ease-out, matches this app's LinearOutSlowInEasing-flavored motion grammar
            val a = target * eased + (1f - eased) * (1f - target)
            slot.filter.setAlpha(a)
            slot.nameTag.setAlpha(if (target > 0f) a else 0f)
            delay(stepMs)
        }
        slot.filter.setAlpha(target)
        slot.nameTag.setAlpha(if (target > 0f) 1f else 0f)
    }

    /** Surface is only non-null once SurfaceFilterRender/StageTileFilterRender's
     *  initGlFilter() has actually run on RootEncoder's GL thread (async
     *  relative to gl.addFilter() at [bind] time) — returns early (no-op)
     *  until then; [reflow]'s periodic retry picks it up on the next tick.
     *  Also wires Defect A's fix: [EglRenderer.setLayoutAspectRatio] to this
     *  slot's FIXED role aspect (main = canvas aspect, rail =
     *  [RAIL_TILE_ASPECT]) — see this file's header for why matching the
     *  destination rect's aspect here is what makes RootEncoder's later
     *  naive stretch introduce zero distortion. */
    private fun ensureRenderer(slot: Slot) {
        if (slot.renderer != null) return
        val surface = slot.surface() ?: return
        val renderer = EglRenderer("nuru-guest-stage")
        runCatching {
            renderer.init(LiveWebRtc.eglBase.eglBaseContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
            renderer.createEglSurface(surface)
            renderer.setLayoutAspectRatio(slot.layoutAspect)
        }.onFailure { e ->
            Log.w(TAG, "ensureRenderer failed for slot (guestId=${slot.guestId}) — will retry next reflow tick", e)
            runCatching { renderer.release() }
            return
        }
        slot.renderer = renderer
    }

    private fun positionSlot(index: Int, slot: Slot, railRects: List<TileRect>) {
        if (slot.guestId == null && !slot.isHostPlaceholder) {
            // Alpha is owned by the fade coroutine started in [assignSlot]
            // when this slot last changed — don't fight it here.
            return
        }
        if (index == 0) {
            val rect = mainTileRect(canvasWidth, canvasHeight)
            applyRectPercent(slot.filter, rect)
            slot.nameTag.setScale(SPEAKER_PLATE_W, SPEAKER_PLATE_H)
            slot.nameTag.setPosition(2f, 100f - SPEAKER_PLATE_H - 2f)
            setPlateText(slot, plateNameFor(slot.guestId))
            return
        }
        val railIndex = index - 1
        val rect = railRects.getOrNull(railIndex) ?: return
        if (slot.isHostPlaceholder) {
            // Reuses the ALREADY-VERIFIED text-plate mechanism (the same one
            // every name tag already draws with) at full-tile size instead
            // of a thin strip — this file has no way to sample the host's
            // own camera a second time as an independent object-filter
            // texture (RootEncoder exposes no such hook — see the PARITY_AUDIT
            // entry for the full reasoning), so the composited rail's "Host"
            // entry is an honest, clearly-labeled placeholder, not a second
            // (impossible) live feed. GuestStageUi.kt's local rail shows the
            // SAME placeholder in the SAME slot for that reason — no drift.
            applyRectPercent(slot.nameTag, rect)
            if (slot.lastPlateText != "Host") {
                slot.lastPlateText = "Host"
                runCatching { slot.nameTag.setText("Host", 64f, Color.WHITE, Color.argb(210, 20, 24, 33)) }
            }
            return
        }
        applyRectPercent(slot.filter, rect)
        val plateHeightPercent = rect.height * RAIL_PLATE_H_FRACTION / canvasHeight * 100f
        val xPercent = rect.x / canvasWidth * 100f
        val wPercent = rect.width / canvasWidth * 100f
        val yPercent = (rect.y + rect.height) / canvasHeight * 100f - plateHeightPercent
        slot.nameTag.setScale(wPercent, plateHeightPercent)
        slot.nameTag.setPosition(xPercent, yPercent)
        setPlateText(slot, plateNameFor(slot.guestId))
    }

    private fun applyRectPercent(filter: BaseObjectFilterRender, rect: TileRect) {
        if (canvasWidth <= 0f || canvasHeight <= 0f) return
        filter.setScale(rect.width / canvasWidth * 100f, rect.height / canvasHeight * 100f)
        filter.setPosition(rect.x / canvasWidth * 100f, rect.y / canvasHeight * 100f)
    }

    private fun plateNameFor(guestId: String?): String {
        val base = names[guestId].orEmpty().ifBlank { "Guest" }.take(16)
        // Reads the SAME debounced [mutedGuests] the Compose rail chrome
        // reads (see that property's doc) — never a separate instantaneous
        // guess, so the composited broadcast's name plate and the host's
        // own on-screen stage can't show conflicting mute state.
        val looksMuted = guestId != null && guestId in _mutedGuests.value
        return if (looksMuted) "🔇 $base" else base // 🔇 — best-effort, see MUTED_LEVEL_THRESHOLD's doc
    }

    /** Single-sample "is this guest's current level below the muted
     *  threshold" check — the raw signal [updateMuteHeuristic] debounces
     *  into [mutedGuests] over [MUTE_HEURISTIC_STREAK] consecutive polls.
     *  Not exposed publicly on its own: a caller wanting "is this guest
     *  muted" should read [mutedGuests], the debounced answer both this
     *  compositor's own name plate and LiveStageView.kt's Compose rail
     *  chrome actually use — see [mutedGuests]'s doc for why a single
     *  shared debounce (not two independent instantaneous guesses) is the
     *  point. */
    private fun isLikelyMuted(guestId: String): Boolean {
        val level = GuestAudioMixer.currentLevels()[guestId]
        return level != null && level < MUTED_LEVEL_THRESHOLD
    }

    private fun setPlateText(slot: Slot, name: String) {
        if (slot.lastPlateText == name) return
        slot.lastPlateText = name
        // setText() only prepares a CPU-side Bitmap (TextStreamObject.load,
        // plain android.graphics.Canvas) and flags shouldLoad — the actual
        // GL texture upload happens later inside drawFilter() on the render
        // thread (BaseObjectFilterRender, verified source), so this is safe
        // to call from here (reflow runs on Dispatchers.Main.immediate,
        // never the GL thread).
        runCatching { slot.nameTag.setText(name, 56f, Color.WHITE, Color.argb(150, 0, 0, 0)) }
    }
}
