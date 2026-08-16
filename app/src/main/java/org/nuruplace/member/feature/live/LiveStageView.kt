// Nuru Live L7 — the broadcaster's own Zoom-style stage: one full-bleed main
// tile (whoever's spotlighted — host by default) plus a rail of thumbnails
// down the right edge for everyone else. Tapping a rail thumbnail promotes
// it to main and demotes whoever was main into the rail; tapping the guest
// CURRENTLY spotlighted (their own now-full-bleed main tile) reverts to
// host — GuestStageCompositor.tapStageTile's own state machine handles both
// cases (and "host tapped while already main" as a safe no-op) without this
// file needing to special-case anything itself; see that function's doc for
// the single entry point both this view and the composited RTMP frame
// drive, mirroring iOS's `BroadcastController.tapStageTile(_:)` exactly.
//
// Replaces the old GuestStageUi.kt `HostGuestRail` — a fixed, independently
// DRAGGABLE strip that never reflected who was spotlighted (there was no
// spotlight concept at all) and was drawn OVER a raw, always-full-camera
// background. That mismatch is exactly the "preview doesn't match what
// viewers receive" defect the owner reported: the composited broadcast
// could show a guest full-frame while the host's own screen kept showing
// raw camera + a small rail regardless. Mirrors iOS's own L7 removal of
// GuestTileRail.swift in favor of LiveStageView.swift for the same reason.
//
// Geometry comes from GuestStageCompositor.mainTileRect/railTileRects — the
// SAME pure functions the actual composited broadcast frame uses (see
// GuestStageCompositor.reflow()), just fed THIS Box's own measured screen
// size instead of the encoder's locked 1080x1920 canvas. See TileRect's own
// doc for why one algorithm, two canvas sizes, is what keeps the host's own
// preview and what the congregation receives from independently drifting.
// (iOS keeps this math in a separate LiveStageLayout.swift shared by both
// LiveStageCompositor and LiveStageView; Android's already lived, well-
// documented and tested, inside GuestStageCompositor.kt since before this
// file existed — folding it in here rather than relocating it is a file-
// organization difference only, not a behavioral one.)
//
// Video surfaces (the host's own SurfaceView, each guest's WebRtcSurfaceView)
// are NEVER recreated on a spotlight swap — every tile's whole call site is
// wrapped in a stable key() (host / guest userId), so Compose preserves the
// underlying AndroidView identity across recomposition; only the outer
// tile's Modifier.offset/size/corner-radius animate. That is what keeps a
// swap flicker-free and black-frame-free on join/leave/promote/demote (task
// quality bar), matching iOS's identical claim for LiveStageView.swift
// (MTHKViewRepresentable/WebRTCVideoView instances persist for the whole
// broadcast).
package org.nuruplace.member.feature.live

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType

private const val HOST_TILE_KEY = "__host__"

/**
 * The Zoom-style stage — host + up to [MAX_GUESTS] guests, one full-bleed
 * main tile and a right-edge rail for everyone else.
 *
 * @param guestTiles mirrors LiveBroadcastScreen's existing per-guest state.
 * @param spotlightGuestId [GuestStageCompositor.spotlight]'s current value —
 *   the caller collects it (same pattern as `BroadcastController.state`) so
 *   recomposition timing is explicit at the call site.
 * @param mutedGuestIds [GuestStageCompositor.mutedGuests]'s current value —
 *   the SAME debounced set the composited broadcast's own name plate reads,
 *   never a second independently-computed guess (see that property's doc).
 * @param onTapHost/onTapGuest should call [GuestStageCompositor.tapStageTile]
 *   — this composable never touches the compositor itself, keeping it pure
 *   Compose, easy to reason about in isolation.
 * @param hostVideo renders the host's own camera preview (the existing
 *   RootEncoder SurfaceView AndroidView) full-bleed within whatever size
 *   this composable gives it.
 */
@Composable
fun LiveStage(
    guestTiles: List<HostGuestTileState>,
    spotlightGuestId: String?,
    mutedGuestIds: Set<String>,
    onTapHost: () -> Unit,
    onTapGuest: (String) -> Unit,
    modifier: Modifier = Modifier,
    hostVideo: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // Defensive cap mirroring the old HostGuestRail's own `tiles.take(MAX_GUESTS)`
    // — guestTiles' upstream source isn't itself capped at the UI layer.
    val tiles = guestTiles.take(MAX_GUESTS)

    Box(modifier.fillMaxSize().onGloballyPositioned { containerSize = it.size }) {
        if (containerSize == IntSize.Zero) return@Box
        val canvasWidth = containerSize.width.toFloat()
        val canvasHeight = containerSize.height.toFloat()
        val mainRect = mainTileRect(canvasWidth, canvasHeight)
        val liveGuestIds = tiles.map { it.guest.userId }
        val effectiveSpotlight = spotlightGuestId?.takeIf { it in liveGuestIds }
        // SAME function GuestStageCompositor.reflow() calls for the
        // composited RTMP frame's rail — stable ordering, Host placeholder
        // (null) first when a guest is spotlighted. See that function's doc.
        val rail = railItems(liveGuestIds, effectiveSpotlight)
        val railRects = railTileRects(rail.size, canvasWidth, canvasHeight)
        val hostRailIndex = rail.indexOf(null)

        key(HOST_TILE_KEY) {
            val isMain = effectiveSpotlight == null
            val rect = if (isMain) mainRect else railRects.getOrNull(hostRailIndex) ?: mainRect
            StageTile(
                rect = rect,
                isMain = isMain,
                density = density,
                onTap = onTapHost,
                description = if (isMain) "You — main video" else "You — tap to make main video",
            ) {
                Box(Modifier.fillMaxSize()) { hostVideo() }
                // "You" never shows a mute indicator — mirrors iOS's own
                // `railChrome(name: "You", isMuted: false)` exactly: the
                // broadcaster's own mute state is already visible via the
                // HUD's mic button, no need for a second, redundant signal
                // on their own thumbnail.
                if (!isMain) StageTileChrome(name = "You", muted = false)
            }
        }

        tiles.forEach { tile ->
            val userId = tile.guest.userId
            key(userId) {
                val isMain = effectiveSpotlight == userId
                val railIndex = rail.indexOf(userId)
                val rect = if (isMain) mainRect else railRects.getOrNull(railIndex) ?: mainRect
                val isMuted = userId in mutedGuestIds
                StageTile(
                    rect = rect,
                    isMain = isMain,
                    density = density,
                    onTap = { onTapGuest(userId) },
                    description = buildString {
                        append(tile.guest.fullName.ifBlank { "Guest" })
                        append(if (isMain) " — main video" else " — tap to make main video")
                        if (isMuted) append(", muted")
                    },
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        // Even a guest whose WHEP subscribe is still
                        // retrying (or has failed) still gets its renderer
                        // wired (harmless — no track feeds it until one
                        // actually arrives), so a LATER retry that succeeds
                        // without recomposing this tile still lights up on
                        // its own.
                        WebRtcSurfaceView(
                            modifier = Modifier.fillMaxSize(),
                            onRendererReady = tile.onRendererReady,
                            onRendererReleased = tile.onRendererReleased,
                        )
                        GuestConnectionOverlay(state = tile.connectionState, compact = !isMain, onRetry = tile.onRetry)
                    }
                    if (!isMain) StageTileChrome(name = tile.guest.fullName.ifBlank { "Guest" }, muted = isMuted)
                }
            }
        }
    }
}

/**
 * One animated tile — geometry (position/size/corner-radius) tweens over
 * [SWAP_ANIM_MS] whenever [rect]/[isMain] change. [content] (the actual
 * video surface) is NEVER recreated by this composable itself — the
 * caller's own [key] around the whole tile call site is what preserves it
 * across recomposition (see [LiveStage]'s header). A higher `zIndex` on
 * rail tiles than the main tile is essential, not cosmetic: the main tile
 * is full-canvas, so without it a rail tile declared BEFORE the main tile
 * (e.g. the host's own tile, always declared first, when a GUEST is
 * spotlighted) would be painted UNDER the main tile and simply vanish.
 */
@Composable
private fun StageTile(
    rect: TileRect,
    isMain: Boolean,
    density: Density,
    onTap: () -> Unit,
    description: String,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = tween<Dp>(SWAP_ANIM_MS.toInt(), easing = LinearOutSlowInEasing)
    val animX by animateDpAsState(with(density) { rect.x.toDp() }, spec, label = "stageTileX")
    val animY by animateDpAsState(with(density) { rect.y.toDp() }, spec, label = "stageTileY")
    val animW by animateDpAsState(with(density) { rect.width.toDp() }, spec, label = "stageTileW")
    val animH by animateDpAsState(with(density) { rect.height.toDp() }, spec, label = "stageTileH")
    // Mirrors StageTileFilterRender's own baked-in shader constant
    // (stage_rail_fragment.glsl's `CORNER_RADIUS = 0.16`, a fraction of the
    // TILE's own width) rather than iOS's canvas-fraction radius — this
    // keeps the host's own local preview rounding pixel-consistent with
    // what the GL compositor actually bakes into the RTMP frame, which
    // matters more here than numeric parity with iOS's different rendering
    // pipeline (HaishinKit screen objects vs. RootEncoder GL filters — see
    // docs/PARITY_AUDIT.md).
    val cornerPx = if (isMain) 0f else rect.width * 0.16f
    val animCorner by animateDpAsState(with(density) { cornerPx.toDp() }, spec, label = "stageTileCorner")
    val shape = RoundedCornerShape(animCorner)

    Box(
        Modifier
            .offset { IntOffset(animX.roundToPx(), animY.roundToPx()) }
            .size(width = animW, height = animH)
            .zIndex(if (isMain) 0f else 1f)
            .then(if (!isMain) Modifier.shadow(8.dp, shape) else Modifier)
            .clip(shape)
            .then(if (!isMain) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.35f), shape) else Modifier)
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
            .semantics { contentDescription = description },
        content = content,
    )
}

/** Honest per-guest connection states — Connecting reads as "still trying",
 *  never the alarming "Couldn't connect", for as long as WhepSubscriber's
 *  own retry loop is within its window; only Error gets the warning chip,
 *  WITH a tappable Retry affordance. [compact] shrinks text for rail tiles
 *  vs. the much bigger main tile (mirrors iOS's `guestFailureOverlay(...,
 *  compact:)`). */
@Composable
private fun GuestConnectionOverlay(state: WhepConnectionState, compact: Boolean, onRetry: () -> Unit) {
    when (state) {
        is WhepConnectionState.Connecting -> {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Text(
                    "Connecting…",
                    style = if (compact) NuruType.micro else NuruType.body,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        is WhepConnectionState.Error -> {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRetry),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "⚠︎ Couldn't connect",
                        style = if (compact) NuruType.micro else NuruType.body,
                        color = Color.White, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = if (compact) 6.dp else 24.dp),
                    )
                    Text(
                        "Tap to retry", style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        is WhepConnectionState.Live -> {} // nothing extra — the renderer's own video is the whole story.
    }
}

/** Bottom-aligned name + mic-muted chrome — RAIL tiles only; the main tile
 *  is full-bleed and chrome-free (task brief requirement C), matching
 *  iOS's identical `railChrome`. */
@Composable
private fun BoxScope.StageTileChrome(name: String, muted: Boolean) {
    Row(
        Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (muted) {
            Icon(Icons.Filled.MicOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
        Text(
            name, style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
