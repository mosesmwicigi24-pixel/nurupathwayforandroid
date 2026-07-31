// Nuru Live — Broadcast Studio's source switcher (Camera / Screen / Document,
// requirement #3). Engine-side this is just CAMERA vs SCREEN (see
// LiveBroadcastEngine.kt's BroadcastSource) — "Document" is a Compose-only
// mode layered on top of SCREEN: a full-screen in-app PDF pager the
// MediaProjection capture picks up because it mirrors whatever's actually on
// Nuru's own screen. The broadcaster swipes pages; the congregation sees
// exactly what's rendered here.
package org.nuruplace.member.feature.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

/** The three broadcast sources the sheet offers. Distinct from the engine's
 *  own [BroadcastSource] (CAMERA/SCREEN only) — DOCUMENT here maps to
 *  SCREEN at the engine layer, see the file header. */
internal enum class SourceChoice { CAMERA, SCREEN, DOCUMENT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveSourceSheet(
    current: SourceChoice,
    onDismiss: () -> Unit,
    onPick: (SourceChoice) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.lg)) {
            Text("Broadcast source", style = NuruType.title, color = Nuru.ink)
            Spacer(Modifier.height(Spacing.md))
            SourceOptionRow(
                icon = Icons.Filled.Videocam, label = "Camera",
                subtitle = "Your camera, front or back",
                selected = current == SourceChoice.CAMERA,
                onClick = { onPick(SourceChoice.CAMERA) },
            )
            SourceOptionRow(
                icon = Icons.Filled.ScreenShare, label = "Screen share",
                subtitle = "Share whatever's on your screen. Your mic keeps streaming.",
                selected = current == SourceChoice.SCREEN,
                onClick = { onPick(SourceChoice.SCREEN) },
            )
            SourceOptionRow(
                icon = Icons.Filled.Description, label = "Document",
                subtitle = "Pick a PDF and swipe through it live",
                selected = current == SourceChoice.DOCUMENT,
                onClick = { onPick(SourceChoice.DOCUMENT) },
            )
        }
    }
}

@Composable
private fun SourceOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) Nuru.goldTint else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = Spacing.sm, horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (selected) Nuru.gold else Nuru.inputBg),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (selected) Nuru.homeNavy else Nuru.ink600, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(label, style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = NuruType.caption, color = Nuru.ink400)
        }
    }
}

/** Shown over the (camera-less) preview while [BroadcastSource.SCREEN] is
 *  active and it's NOT the Document pager underneath it — lets the
 *  broadcaster confirm what's live and get back to camera in one tap.
 *
 *  KNOWN, DELIBERATE LIMIT: MediaProjection mirrors the WHOLE display
 *  compositor output, so there is no way to draw broadcaster-only chrome
 *  that viewers won't also see while screen-sharing (unlike camera mode,
 *  where the HUD lives only in Compose, entirely separate from the raw
 *  sensor frames RootEncoder encodes). Rather than hide End entirely behind
 *  "switch back to camera first" — a real safety gap for a live broadcast
 *  tool — this chip+End pill stays minimal but visible, same tradeoff every
 *  mobile screen-share tool with an in-app control accepts. */
@Composable
internal fun ScreenModeControls(
    muted: Boolean,
    onToggleMute: () -> Unit,
    onSwitchToCamera: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { onToggleMute() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = "Mute",
                tint = Color.White, modifier = Modifier.size(20.dp),
            )
        }
        Row(
            Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.6f))
                .clickable { onSwitchToCamera() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.ScreenShare, contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(16.dp))
            Text("Sharing your screen", style = NuruType.cardCta, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("· switch to camera", style = NuruType.caption, color = Color.White.copy(alpha = 0.7f))
        }
        MinimalEndPill(onEnd)
    }
}

@Composable
private fun MinimalEndPill(onEnd: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.danger).clickable { onEnd() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { Text("End", style = NuruType.cardCta, color = Color.White, fontWeight = FontWeight.Bold) }
}

/** One open PDF for the life of Document mode — a thin wrapper over
 *  PdfRenderer since it (a) needs the fd/renderer closed explicitly and
 *  (b) only allows ONE Page open at a time, so every render is
 *  open→render→close under a Mutex (HorizontalPager can, in principle,
 *  compose more than one page's LaunchedEffect around a fast swipe). */
private class PdfDocumentSession(private val pfd: ParcelFileDescriptor, private val renderer: PdfRenderer) {
    val pageCount: Int get() = renderer.pageCount
    private val mutex = Mutex()

    suspend fun render(pageIndex: Int, widthPx: Int, heightPx: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(pageIndex).use { page ->
                // Fit the PDF's own page aspect ratio into the requested
                // width, matching it height-wise — the pager crops/letterboxes
                // via ContentScale.Fit below, this just avoids over-rendering.
                val scale = widthPx.toFloat() / page.width.toFloat()
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfDocumentSession? {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            return PdfDocumentSession(pfd, PdfRenderer(pfd))
        }
    }
}

/**
 * Full-screen PDF pager — Broadcast Studio's Document mode. Lives entirely
 * in Compose (the engine only knows "screen source is active"); MediaProjection
 * captures this exact UI, so what's rendered here is what the congregation
 * sees. Swipe to turn pages.
 *
 * Owner layout redesign (2026-08-01): the page indicator used to float top-
 * center, separate from the mute/End/exit row at bottom-right — two floating
 * chrome groups instead of one. It now lives in the SAME bottom-right dock
 * as those controls ("the document page indicator... belongs in this dock
 * too, not floating"), on a soft gradient scrim rather than its own opaque
 * pill, matching the viewer/guest screen's grammar (LiveChrome.kt).
 */
@Composable
internal fun DocumentPagerScreen(uri: Uri, muted: Boolean, onToggleMute: () -> Unit, onExit: () -> Unit, onEnd: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var session by remember { mutableStateOf<PdfDocumentSession?>(null) }
    var openFailed by remember { mutableStateOf(false) }

    DisposableEffect(uri) {
        val s = PdfDocumentSession.open(context, uri)
        session = s
        openFailed = s == null
        onDispose { s?.close() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val s = session
        // Hoisted here (rather than written into a second mutableState from
        // inside the `when` below) so pageCount/currentPage stay Compose's
        // own derived state, readable by the dock further down without a
        // manual state-write-during-composition — [pagerState] is only ever
        // non-null once [s] is, exactly mirroring that same null-check.
        val pagerState = if (s != null) rememberPagerState(pageCount = { s.pageCount }) else null
        when {
            openFailed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Couldn't open that PDF.", style = NuruType.body, color = Color.White)
            }
            s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Nuru.gold)
            }
            else -> {
                HorizontalPager(state = pagerState!!, modifier = Modifier.fillMaxSize()) { page ->
                    PdfPage(session = s, pageIndex = page)
                }
            }
        }
        val pageLabel = if (s != null && pagerState != null) "Page ${pagerState.currentPage + 1} / ${s.pageCount}" else null

        // ONE bottom-right dock — page indicator, mute, End, exit, on a
        // gradient scrim rather than a heavy opaque bar.
        Box(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(140.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
        )
        Column(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(Spacing.md),
            horizontalAlignment = Alignment.End,
        ) {
            pageLabel?.let {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(it, style = NuruType.micro, color = Color.White, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(Spacing.sm))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { onToggleMute() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = "Mute",
                        tint = Color.White, modifier = Modifier.size(20.dp),
                    )
                }
                MinimalEndPill(onEnd)
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onExit() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, contentDescription = "Exit document, back to camera", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun PdfPage(session: PdfDocumentSession, pageIndex: Int) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val widthPx = remember(config) { with(density) { config.screenWidthDp.dp.roundToPx() } }
    val heightPx = remember(config) { with(density) { config.screenHeightDp.dp.roundToPx() } }

    LaunchedEffect(pageIndex, widthPx, heightPx) {
        bitmap = runCatching { session.render(pageIndex, widthPx, heightPx) }.getOrNull()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            androidx.compose.foundation.Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "Document page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(color = Nuru.gold)
        }
    }
}
