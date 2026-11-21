// Read aloud — the controls every reader shares (member suggestion,
// 2026-11-21). A reader calls rememberListen(paragraphs) once, puts
// ListenIcon in its header button, and ListenBar above its bottom action:
//
//   header   [Listen]            → tap: read from the top
//   bar      ◁ ⏸ ▷  Reading aloud · 3 of 12  [1×] ✕
//
// The bar shows only while this reading is loaded (playing or paused). The
// colours come from the reader's own palette, so it sits on the day page and
// the night page alike.
package org.nuruplace.member.feature.listen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.feature.live.LiveDiscoveryCenter
import org.nuruplace.member.ui.icons.Lucide
import org.nuruplace.member.ui.theme.Inter

/** What a reader needs to draw its Listen controls and act on them. */
@Stable
class ListenController internal constructor(
    /** Listen may be shown at all: there is something to read, a voice, no
     *  TalkBack, the church not live. */
    val available: Boolean,
    /** This reading is loaded (playing or paused) — the bar is up. */
    val active: Boolean,
    val playing: Boolean,
    val index: Int,
    val total: Int,
    val speed: Float,
    private val onToggle: () -> Unit,
    private val onSkip: (Int) -> Unit,
    private val onSpeed: () -> Unit,
    private val onStop: () -> Unit,
) {
    fun toggle() = onToggle()
    fun back() = onSkip(-1)
    fun forward() = onSkip(1)
    fun cycleSpeed() = onSpeed()
    fun stop() = onStop()

    /** What the header button says to TalkBack-less screen readers and long-press tooltips. */
    val label: String get() = when {
        active && playing -> "Pause reading aloud"
        active -> "Resume reading aloud"
        else -> "Listen — read this aloud"
    }
}

/** Binds the engine for as long as the calling screen is on screen. [paragraphs]
 *  is the reading as ListenScript built it; an empty list offers nothing. */
@Composable
fun rememberListen(paragraphs: List<String>): ListenController {
    val context = LocalContext.current
    val key = remember(paragraphs) { ListenScript.key(paragraphs) }

    DisposableEffect(Unit) {
        ReadAloud.bind(context)
        onDispose { ReadAloud.unbind(null) }
    }
    // Leaving the reading — or its words changing under it — stops it, so a
    // voice never goes on reading something no longer on screen.
    DisposableEffect(key) {
        onDispose { if (ReadAloud.state.value.key == key) ReadAloud.stop() }
    }

    val state by ReadAloud.state.collectAsState()
    val streams by LiveDiscoveryCenter.streams.collectAsState()
    var offered by remember { mutableStateOf(false) }
    LaunchedEffect(state.status, streams) { offered = ReadAloud.offered(context) }
    // TalkBack is switched on in Settings: coming back is when it changes.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { offered = ReadAloud.offered(context) }

    val active = state.isFor(key)
    return ListenController(
        available = paragraphs.isNotEmpty() && (offered || active),
        active = active,
        playing = active && state.status == ReadAloud.Status.PLAYING,
        index = if (active) state.index else 0,
        total = paragraphs.size,
        speed = AppPrefs.listenSpeed,
        onToggle = { ReadAloud.toggle(context, key, paragraphs) },
        onSkip = { delta -> ReadAloud.skip(context, delta) },
        onSpeed = { ReadAloud.cycleSpeed() },
        onStop = { ReadAloud.stop() },
    )
}

/** The colours of the bar on a reader's page. */
data class ListenColors(
    val bar: Color,
    val ink: Color,
    val inkDim: Color,
    val accent: Color,
    val onAccent: Color,
    val border: Color,
)

/** The header button's glyph: headphones to start, pause while reading,
 *  play while paused. */
@Composable
fun ListenIcon(c: ListenController, tint: Color, small: Boolean = false) {
    val icon: ImageVector = when {
        c.active && c.playing -> Lucide.Pause
        c.active -> Lucide.Play
        else -> Lucide.Headphones
    }
    // The icon sizes are the house's three (14 · 18 · 22, IconSourceTest).
    Icon(icon, c.label, tint = tint, modifier = if (small) Modifier.size(14.dp) else Modifier.size(18.dp))
}

/** The player, shown while this reading is loaded. Put it directly above the
 *  reader's bottom action; it animates in and out. */
@Composable
fun ListenBar(c: ListenController, colors: ListenColors, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = c.active,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp))
                .background(colors.bar).border(1.dp, colors.border, RoundedCornerShape(20.dp)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarButton(Lucide.ChevronLeft, "Previous paragraph", colors.inkDim, enabled = c.index > 0) { c.back() }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(colors.accent).clickable { c.toggle() }
                        .semantics { contentDescription = if (c.playing) "Pause" else "Play" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (c.playing) Lucide.Pause else Lucide.Play, null, tint = colors.onAccent, modifier = Modifier.size(22.dp))
                }
                BarButton(Lucide.ChevronRight, "Next paragraph", colors.inkDim, enabled = true) { c.forward() }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (c.playing) "Reading aloud" else "Paused",
                        style = barStyle(13, FontWeight.SemiBold), color = colors.ink, maxLines = 1,
                    )
                    Text(
                        "${c.index + 1} of ${c.total}",
                        style = barStyle(11), color = colors.inkDim, maxLines = 1,
                    )
                }
                Box(
                    Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).border(1.dp, colors.border, RoundedCornerShape(15.dp))
                        .clickable { c.cycleSpeed() }.padding(horizontal = 10.dp)
                        .semantics { contentDescription = "Speed ${ListenSpeed.label(c.speed)}. Tap to change" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ListenSpeed.label(c.speed), style = barStyle(12, FontWeight.Bold), color = colors.ink)
                }
                BarButton(Lucide.X, "Stop reading aloud", colors.inkDim, enabled = true) { c.stop() }
            }
            // How far through the reading, by paragraph.
            Box(Modifier.fillMaxWidth().height(3.dp).background(colors.border)) {
                val f = if (c.total > 0) (c.index + 1).toFloat() / c.total else 0f
                Box(Modifier.fillMaxWidth(f).height(3.dp).background(colors.accent))
            }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
    }
}

private fun barStyle(size: Int, weight: FontWeight = FontWeight.Medium) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp)
