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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

// ── Host side: the draggable rail of guest tiles ────────────────────────────

data class HostGuestTileState(
    val guest: LiveGuestRow,
    val onRendererReady: (SurfaceViewRenderer) -> Unit,
    val onRendererReleased: (SurfaceViewRenderer) -> Unit,
    // Non-null when this guest's WHEP subscribe failed — previously silently
    // swallowed (LiveBroadcastScreen's runCatching around sub.start() had
    // nothing shown for it), leaving an indefinite black tile the host had
    // no way to distinguish from "still connecting". Surfaced honestly here
    // instead, per the resilience requirement that a guest-subsystem failure
    // degrades gracefully (an error chip on ITS tile) rather than looking
    // like a silent hang or taking anything else down with it.
    val errorMessage: String? = null,
)

/** Up to [MAX_GUESTS] rounded ~96x128dp tiles (name label under each),
 *  horizontally scrollable since 6 tiles don't fit most phone widths, the
 *  whole strip draggable by its left-edge grip handle (mirrors
 *  LiveFloatingChat's "only the header/handle drags" idiom so a horizontal
 *  scroll gesture on the tiles themselves is never mistaken for a
 *  reposition drag). Renders nothing when [tiles] is empty. */
@Composable
fun HostGuestRail(tiles: List<HostGuestTileState>, modifier: Modifier = Modifier) {
    if (tiles.isEmpty()) return
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var railOffset by remember { mutableStateOf<Offset?>(null) }
    val scrollState = rememberScrollState()

    Box(modifier.fillMaxSize().onGloballyPositioned { containerSize = it.size }) {
        if (containerSize == IntSize.Zero) return@Box
        val containerPx = Size(containerSize.width.toFloat(), containerSize.height.toFloat())
        val tileWidthPx = with(density) { 96.dp.toPx() }
        val tileHeightPx = with(density) { 128.dp.toPx() }
        val gripWidthPx = with(density) { 18.dp.toPx() }
        val railWidthPx = (containerPx.width * 0.72f).coerceAtMost(gripWidthPx + tileWidthPx * 2.4f)
        val elementPx = Size(railWidthPx, tileHeightPx)
        val topMarginPx = with(density) { 110.dp.toPx() }
        val default = Offset((containerPx.width - railWidthPx) / 2f, topMarginPx)
        val current = clampDragOffset(railOffset ?: default, containerPx, elementPx)
        val railWidthDp = with(density) { railWidthPx.toDp() }
        val tileHeightDp = with(density) { tileHeightPx.toDp() }

        Row(
            Modifier
                .offset { IntOffset(current.x.roundToInt(), current.y.roundToInt()) }
                .size(width = railWidthDp, height = tileHeightDp),
        ) {
            Box(
                Modifier.width(18.dp).fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(containerSize) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val base = railOffset ?: default
                            railOffset = clampDragOffset(base + drag, containerPx, elementPx)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("⠿", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp) }

            Row(
                Modifier.weight(1f).fillMaxSize().horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tiles.take(MAX_GUESTS).forEach { tile ->
                    key(tile.guest.userId) {
                        GuestTile(tile)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestTile(tile: HostGuestTileState) {
    Box(
        Modifier.size(width = 96.dp, height = 128.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
    ) {
        // Even a guest whose WHEP subscribe failed still gets its renderer
        // wired (harmless — no track ever arrives to feed it), so a LATER
        // retry that succeeds without recomposing this tile still lights up.
        WebRtcSurfaceView(
            modifier = Modifier.fillMaxSize(),
            onRendererReady = tile.onRendererReady,
            onRendererReleased = tile.onRendererReleased,
        )
        if (tile.errorMessage != null) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚠︎ Couldn't connect", style = NuruType.micro, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
            }
        }
        Text(
            tile.guest.fullName.ifBlank { "Guest" },
            style = NuruType.micro, color = Color.White, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}
