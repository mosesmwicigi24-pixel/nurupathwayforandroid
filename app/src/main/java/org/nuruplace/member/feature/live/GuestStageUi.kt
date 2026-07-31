// Nuru Live L6b — the visual pieces shared by the guest's own self-preview
// PiP (LivePlayerScreen) and the host's guest tile rail (LiveBroadcastScreen):
// a SurfaceViewRenderer AndroidView wrapper that inits/releases against
// LiveWebRtc.eglBase, plus the small chrome around it. Kept separate from
// LiveInteractions.kt/LiveBroadcastInteractions.kt (L5 surfaces) since this
// is genuinely new L6b UI, not an extension of the reaction/hand/chat rail.
// Drag mechanics (clampDragOffset, the offset{}/pointerInput(detectDragGestures)
// idiom) intentionally mirror LiveInteractions.kt's LiveFloatingChat exactly.
package org.nuruplace.member.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import org.nuruplace.member.data.net.LiveGuestRow
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/** The guest's own on-stage publish state, driven by WhipPublisher —
 *  LivePlayerScreen owns the transitions (see its "L6b guest publish" block),
 *  this file just renders each state honestly (never a static "on stage
 *  soon" placeholder regardless of what's actually happening). */
sealed class GuestStageState {
    data object Idle : GuestStageState()
    data object Connecting : GuestStageState()
    data object Live : GuestStageState()
    data class Error(val message: String) : GuestStageState()
}

/** A WebRTC video surface — inits against the shared EglBase on creation,
 *  releases on removal from composition (AndroidView's onRelease, the
 *  correct place per Compose's own interop contract — never a
 *  DisposableEffect racing against the view's own lifecycle). Callers attach/
 *  detach it to a track (`videoTrack.addSink(renderer)`) themselves once
 *  they hold a reference via [onRendererReady]. */
@Composable
fun WebRtcSurfaceView(
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
    onRendererReady: (SurfaceViewRenderer) -> Unit,
    onRendererReleased: (SurfaceViewRenderer) -> Unit = {},
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(LiveWebRtc.eglBase.eglBaseContext, null)
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(mirror)
                onRendererReady(this)
            }
        },
        onRelease = { renderer ->
            onRendererReleased(renderer)
            renderer.release()
        },
    )
}

/** The guest's own draggable self-preview PiP — rounded, small, with a mic
 *  toggle baked into its corner. Clamped to whatever [modifier] sizes as its
 *  container (mirrors LiveFloatingChat's exact clamp idiom, LiveInteractions.
 *  kt), so the caller should size this to the full stage. */
@Composable
fun GuestSelfPreviewPiP(
    muted: Boolean,
    onToggleMute: () -> Unit,
    onRendererReady: (SurfaceViewRenderer) -> Unit,
    onRendererReleased: (SurfaceViewRenderer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var pipOffset by remember { mutableStateOf<Offset?>(null) }

    Box(modifier.fillMaxSize().onGloballyPositioned { containerSize = it.size }) {
        if (containerSize == IntSize.Zero) return@Box
        val containerPx = Size(containerSize.width.toFloat(), containerSize.height.toFloat())
        val widthPx = with(density) { 96.dp.toPx() }
        val heightPx = with(density) { 128.dp.toPx() }
        val elementPx = Size(widthPx, heightPx)
        val topMarginPx = with(density) { 100.dp.toPx() }
        val endMarginPx = with(density) { 16.dp.toPx() }
        val default = Offset(containerPx.width - widthPx - endMarginPx, topMarginPx)
        val current = clampDragOffset(pipOffset ?: default, containerPx, elementPx)
        val widthDp = with(density) { widthPx.toDp() }
        val heightDp = with(density) { heightPx.toDp() }

        Box(
            Modifier
                .offset { IntOffset(current.x.roundToInt(), current.y.roundToInt()) }
                .size(width = widthDp, height = heightDp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .pointerInput(containerSize) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val base = pipOffset ?: default
                        pipOffset = clampDragOffset(base + drag, containerPx, elementPx)
                    }
                },
        ) {
            WebRtcSurfaceView(
                modifier = Modifier.fillMaxSize(),
                mirror = true,
                onRendererReady = onRendererReady,
                onRendererReleased = onRendererReleased,
            )
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                    .clickable { onToggleMute() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (muted) "Unmute" else "Mute",
                    tint = Color.White, modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** The red "Leave stage" pill — same visual weight as the broadcaster HUD's
 *  End button (Nuru.danger), since leaving the stage is the guest's
 *  equivalent "stop" action. */
@Composable
fun LeaveStagePill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Nuru.danger)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Leave stage", style = NuruType.cardCta, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** An honest transient-state chip for the guest's own screen while joining
 *  the stage — replaces the old static "On stage soon" placeholder now that
 *  there's a real connection to report on. Never shown once actually live
 *  (the self-preview PiP speaks for itself at that point). */
@Composable
fun GuestConnectingChip(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("🎤", fontSize = 13.sp)
        Text(label, style = NuruType.micro, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

// ── Host side: per-guest tile state ─────────────────────────────────────────
// L7 — the draggable HostGuestRail that used to live here (a fixed strip
// scrolled/dragged independently of the actual video) is GONE, replaced by
// LiveStageView.kt's Zoom-style stage: one full-bleed main tile (whoever's
// spotlighted) + a rail down the right edge for everyone else, tap-to-expand
// reversible — see that file's header for why (mirrors iOS's L7 removal of
// GuestTileRail.swift in favor of LiveStageView.swift). HostGuestTileState
// itself is unchanged and still the shape LiveBroadcastScreen.kt builds per
// accepted guest; only the composable that CONSUMED it moved.

data class HostGuestTileState(
    val guest: LiveGuestRow,
    val onRendererReady: (SurfaceViewRenderer) -> Unit,
    val onRendererReleased: (SurfaceViewRenderer) -> Unit,
    // Straight off WhepSubscriber's own retry loop (2026-07-31 fix) — the
    // tile reads as Connecting (with the guest's name and a subtle
    // indicator, never "Couldn't connect") for as long as the subscriber is
    // still within its retry window, since production MediaMTX logs proved
    // a guest's WHIP publish routinely lands seconds after the host's WHEP
    // subscribe first tries and is told "no stream is available" — that is
    // completely normal, not a failure. Only becomes Error, with a Retry
    // affordance, once the retry window has genuinely elapsed or the
    // failure is non-retryable (e.g. bad credentials).
    val connectionState: WhepConnectionState = WhepConnectionState.Connecting,
    val onRetry: () -> Unit = {},
)
