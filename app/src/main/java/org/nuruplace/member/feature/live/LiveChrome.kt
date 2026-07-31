// Nuru Live — the ONE top row + ONE bottom dock (owner layout redesign,
// 2026-08-01; see LiveDockLogic.kt's header for the full "why"). Replaces
// LivePlayerScreen's old three-scattered-top-rows chrome (close X / LIVE
// badge row, a separate broadcaster-identity row, a separate bottom title
// box) and the floating right-edge action rail with two purpose-built
// composables:
//   - [LiveTopBar]: close, host identity, title, LIVE pill and counters, all
//     on ONE line, directly below the status bar.
//   - [LiveBottomDock]: every interactive control (reactions, raise hand,
//     chat, and — once a guest is accepted onto the stage — camera/switch-
//     camera/mic/speaker/leave), roughly the lower two inches of the screen.
// Both sit on a soft gradient scrim (never a heavy opaque bar) so the
// full-bleed video/document behind them stays legible — per the owner's
// design-quality note. Item choice/order comes from LiveDockLogic.kt's pure
// [liveDockItems]; this file only renders whatever list it's handed.
package org.nuruplace.member.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.nuruplace.member.ui.components.LivePulsingDot
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType

// ── Top bar ──────────────────────────────────────────────────────────────

/**
 * ONE line, directly below the status bar: close, host avatar + name, the
 * stream title, the LIVE pill, and viewer/raised-hand counters — replacing
 * the old three stacked/scattered rows. Sits on a downward gradient scrim
 * (transparent -> soft black) rather than an opaque bar, so full-bleed video
 * underneath stays legible.
 */
@Composable
fun LiveTopBar(
    onClose: () -> Unit,
    live: Boolean,
    hostName: String?,
    hostAvatarUrl: String?,
    title: String,
    viewerCount: Int,
    handCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp)) }

            Spacer(Modifier.width(8.dp))

            if (!hostAvatarUrl.isNullOrBlank() || !hostName.isNullOrBlank()) {
                HostAvatar(hostAvatarUrl, hostName ?: "Nuru Place")
                Spacer(Modifier.width(8.dp))
            }

            val label = listOfNotNull(hostName?.takeIf { it.isNotBlank() }, title.takeIf { it.isNotBlank() })
                .joinToString("  ·  ")
                .ifBlank { "Nuru Live" }
            Text(
                label, style = NuruType.body, color = Color.White, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.width(8.dp))

            if (live) LiveCountersChip(viewerCount, handCount)
        }
    }
}

@Composable
private fun HostAvatar(avatarUrl: String?, name: String) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(Nuru.gold), contentAlignment = Alignment.Center) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape))
        } else {
            Text(
                name.trim().split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                style = NuruType.micro, color = Nuru.homeNavy, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LiveCountersChip(viewerCount: Int, handCount: Int) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LivePulsingDot(size = 7.dp)
        Text("LIVE", style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
        Text("·", style = NuruType.micro, color = Color.White.copy(alpha = 0.5f))
        Icon(Icons.Filled.RemoveRedEye, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(12.dp))
        Text(abbreviateCount(viewerCount), style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
        if (handCount > 0) {
            Text("·", style = NuruType.micro, color = Color.White.copy(alpha = 0.5f))
            Text("✋", fontSize = 11.sp)
            Text(abbreviateCount(handCount), style = NuruType.micro, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

// ── Bottom dock ──────────────────────────────────────────────────────────

/** Everything a caller needs to know to render one dock — kept as a single
 *  data holder so [LiveBottomDock]'s own parameter list doesn't balloon as
 *  more per-item state gets added; the dock's ITEM SET/ORDER itself still
 *  comes from the pure [liveDockItems], this only carries the live values
 *  each rendered item needs (counts, toggle states). */
data class LiveDockState(
    val reactionCounts: Map<String, Int> = emptyMap(),
    val handRaised: Boolean = false,
    val chatOpen: Boolean = false,
    val cameraOn: Boolean = true,
    val micMuted: Boolean = false,
    val speakerOn: Boolean = true,
)

/**
 * ONE dock, roughly the lower two inches, holding every Live control per the
 * owner's spec. Internally laid out as up to two rows — audience controls
 * (reactions, raise hand, chat) on top, publish/leave controls (once a guest
 * is on stage) below — purely so a phone-width row never has to squeeze more
 * than five 48dp targets; visually it's one cohesive area on one gradient
 * scrim, not two separate floating groups.
 */
@Composable
fun LiveBottomDock(
    items: List<LiveDockItem>,
    state: LiveDockState,
    onReact: (String) -> Unit,
    onToggleHand: () -> Unit,
    onToggleChat: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onLeaveStage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val audienceItems = items.filter {
        it == LiveDockItem.REACT_LOVE || it == LiveDockItem.REACT_FIRE || it == LiveDockItem.REACT_LIKE ||
            it == LiveDockItem.RAISE_HAND || it == LiveDockItem.CHAT
    }
    val controlItems = items - audienceItems.toSet()

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(210.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (controlItems.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    controlItems.forEach { item ->
                        DockControlButton(item, state, onToggleCamera, onSwitchCamera, onToggleMic, onToggleSpeaker, onLeaveStage)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
                audienceItems.forEach { item ->
                    DockAudienceButton(item, state, onReact, onToggleHand, onToggleChat)
                }
            }
        }
    }
}

@Composable
private fun DockAudienceButton(
    item: LiveDockItem,
    state: LiveDockState,
    onReact: (String) -> Unit,
    onToggleHand: () -> Unit,
    onToggleChat: () -> Unit,
) {
    when (item) {
        LiveDockItem.REACT_LOVE -> DockGlyphButton(reactionEmoji("love"), abbreviateCount(state.reactionCounts["love"] ?: 0)) { onReact("love") }
        LiveDockItem.REACT_FIRE -> DockGlyphButton(reactionEmoji("fire"), abbreviateCount(state.reactionCounts["fire"] ?: 0)) { onReact("fire") }
        LiveDockItem.REACT_LIKE -> DockGlyphButton(reactionEmoji("like"), abbreviateCount(state.reactionCounts["like"] ?: 0)) { onReact("like") }
        LiveDockItem.RAISE_HAND -> DockGlyphButton("✋", null, filled = state.handRaised, description = if (state.handRaised) "Lower hand" else "Raise hand", onClick = onToggleHand)
        LiveDockItem.CHAT -> DockGlyphButton("💬", null, filled = state.chatOpen, description = "Live chat", onClick = onToggleChat)
        else -> {} // publish/leave controls render via DockControlButton
    }
}

@Composable
private fun DockControlButton(
    item: LiveDockItem,
    state: LiveDockState,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onLeaveStage: () -> Unit,
) {
    when (item) {
        LiveDockItem.CAMERA_TOGGLE -> DockIconButton(
            icon = if (state.cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
            description = if (state.cameraOn) "Turn camera off" else "Turn camera on",
            onClick = onToggleCamera,
        )
        LiveDockItem.SWITCH_CAMERA -> DockIconButton(Icons.Filled.Cameraswitch, "Switch camera", onSwitchCamera)
        LiveDockItem.MIC_TOGGLE -> DockIconButton(
            icon = if (state.micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
            description = if (state.micMuted) "Unmute" else "Mute",
            onClick = onToggleMic,
        )
        LiveDockItem.SPEAKER -> DockIconButton(
            icon = if (state.speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            description = if (state.speakerOn) "Switch to earpiece" else "Switch to speaker",
            onClick = onToggleSpeaker,
        )
        LiveDockItem.LEAVE_STAGE -> LeaveStagePill(onClick = onLeaveStage)
        else -> {} // audience controls render via DockAudienceButton
    }
}

@Composable
private fun DockIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(LiveChromeCircleSize).clip(CircleShape).background(LiveChromeCircleBg)
            .clickable { onClick() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun DockGlyphButton(glyph: String, count: String?, filled: Boolean = false, description: String? = null, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(LiveChromeCircleSize).clip(CircleShape)
                .background(if (filled) Nuru.gold.copy(alpha = 0.9f) else LiveChromeCircleBg)
                .clickable { onClick() }
                .let { if (description != null) it.semantics { contentDescription = description } else it },
            contentAlignment = Alignment.Center,
        ) { Text(glyph, fontSize = 22.sp) }
        count?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
