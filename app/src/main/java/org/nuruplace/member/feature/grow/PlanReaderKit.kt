// Plan reader kit — the shared pieces of the four-page Plans journey (iOS parity):
// a warm day/night reader palette, the two reading instruments (top gold progress
// hairline + right-rail pace dot), a lightweight "part finished" bus so a part
// ticks its hub row on return, and the palette-aware reader blocks (Scripture
// pull-quote, serif passage with gold verse numbers, prayer, media card,
// keynotes). Ported from the iOS PlanSegmentView + NuruReadingBar/NuruPaceRail.
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableSharedFlow
import org.nuruplace.member.ui.theme.Fraunces
import org.nuruplace.member.ui.theme.Inter

// ── Type helpers (exact-size brand faces) ──
internal fun rInter(size: Int, weight: FontWeight = FontWeight.Medium, kerning: Float = 0f) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp)

internal fun rSerif(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Int = 0, italic: Boolean = false) =
    TextStyle(
        fontFamily = Fraunces, fontWeight = weight, fontSize = size.sp,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        lineHeight = if (lineHeight > 0) lineHeight.sp else (size * 1.35).sp,
    )

// ── Reader palette — warm day, sepia night (iOS ReaderPalette parity) ──
internal class ReaderPalette(val night: Boolean) {
    val bg = if (night) Color(0xFF171411) else Color(0xFFF6F4EE)
    val card = if (night) Color(0xFF221E19) else Color(0xFFFFFFFF)
    val ink = if (night) Color(0xFFEBE3D3) else Color(0xFF0B1F33)
    val inkDim = if (night) Color(0xFF9A9280) else Color(0xFF59667C)
    val gold = if (night) Color(0xFFD9B65A) else Color(0xFFC89B3C)
    val goldDeep = if (night) Color(0xFFE0C06A) else Color(0xFFA8861C)
    val border = if (night) Color(0x1AFFFFFF) else Color(0x140A2540)
    val highlight = if (night) Color(0xFF2A241C) else Color(0xFFFFF8E6)
    val navy = Color(0xFF0A1628)   // CTA text on gold, constant
}

/** Persisted-for-the-session reader night mode (survives navigation; resets on
 *  cold start — a DataStore-backed toggle is a follow-up). */
internal object ReaderMode {
    var night by mutableStateOf(false)
}

/** A part finished in its reader → emit its segment ids so the day hub ticks the
 *  row on return (mirrors the iOS `.nuruPlanPartDone` NotificationCenter signal). */
internal object PlanProgressBus {
    val finished = MutableSharedFlow<String>(extraBufferCapacity = 16)
}

// ── Reading instruments ──

/** Top 3dp gold progress hairline (how far through the whole read). */
@Composable
internal fun ReadingProgressBar(progress: Float, pal: ReaderPalette) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(pal.gold.copy(alpha = 0.16f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(pal.gold))
    }
}

/** Right-rail eye-pacer: a gold dot bound to the scroll fraction, the rail filling
 *  behind it. Visible on any canvas (white-ringed dot). Spans between top/bottom
 *  insets so it never collides with the header or the bottom CTA. */
@Composable
internal fun PaceRail(progress: Float, pal: ReaderPalette, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.width(18.dp).fillMaxHeight()) {
        val topInset = 24.dp
        val bottomInset = 24.dp
        val span = maxHeight - topInset - bottomInset
        val dotY = topInset + span * p
        // Full track.
        Box(Modifier.align(Alignment.TopCenter).padding(top = topInset).width(4.dp).height(span).clip(CircleShape).background(pal.gold.copy(alpha = 0.22f)))
        // Read-so-far fill.
        Box(Modifier.align(Alignment.TopCenter).padding(top = topInset).width(4.dp).height(span * p).clip(CircleShape).background(pal.gold.copy(alpha = 0.85f)))
        // The dot — white-ringed.
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = dotY - 6.dp).size(12.dp).clip(CircleShape)
                .background(Color.White).padding(2.dp).clip(CircleShape).background(pal.gold),
        )
    }
}

// ── Palette-aware reader blocks ──

private data class RLine(val number: String?, val text: String)

private fun rLines(content: String): List<RLine> =
    content.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.map { raw ->
        val digits = raw.takeWhile { it.isDigit() }
        if (digits.length in 1..3) {
            var rest = raw.drop(digits.length)
            if (rest.firstOrNull()?.let { it in ".):" } == true) rest = rest.drop(1)
            if (rest.startsWith(" ")) return@map RLine(digits, rest.trim())
        }
        RLine(null, raw)
    }

@Composable
internal fun RPassage(text: String, pal: ReaderPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rLines(text).forEach { line ->
            val annotated = buildAnnotatedString {
                line.number?.let { n ->
                    withStyle(SpanStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = pal.gold, baselineShift = BaselineShift(0.4f))) { append("$n  ") }
                }
                withStyle(SpanStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = pal.ink)) { append(line.text) }
            }
            Text(annotated, style = rInter(16, FontWeight.Medium).copy(lineHeight = 25.sp))
        }
    }
}

@Composable
internal fun RPullQuote(text: String, caption: String, quoted: Boolean, pal: ReaderPalette) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.highlight)
            .border(1.dp, pal.gold.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(pal.gold))
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.FormatQuote, null, tint = pal.gold, modifier = Modifier.size(16.dp))
            val alreadyQuoted = text.trimStart().firstOrNull()?.let { it == '“' || it == '"' } == true
            Text(if (quoted && !alreadyQuoted) "“$text”" else text, style = rSerif(18, FontWeight.Normal, 25, italic = true), color = pal.ink)
            Text(caption.uppercase(), style = rInter(11, FontWeight.Bold, 1.4f), color = pal.inkDim)
        }
    }
}

@Composable
internal fun RPrayer(text: String, pal: ReaderPalette) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card)
            .border(1.dp, pal.border, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("PRAYER", style = rInter(11, FontWeight.Bold, 1.6f), color = pal.goldDeep)
        // A trailing "_blessing_" line renders italic.
        val parts = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        parts.forEach { line ->
            val blessing = line.startsWith("_") && line.endsWith("_")
            Text(
                if (blessing) line.trim('_') else line,
                style = rSerif(if (blessing) 14 else 15, FontWeight.Normal, 22, italic = blessing),
                color = if (blessing) pal.goldDeep else pal.ink,
            )
        }
    }
}

@Composable
internal fun RMediaCard(imageUrl: String?, videoUrl: String?, portrait: Boolean, onPlay: (String) -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(if (portrait) 9f / 15f else 16f / 9f)
            .clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF1A406B), Color(0xFF0B1F33), Color(0xFF00132F))))
            .clickable(enabled = !videoUrl.isNullOrEmpty()) { videoUrl?.let(onPlay) },
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFC89B3C)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Play", tint = Color(0xFF0A1628), modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
internal fun RKeynotes(content: String, pal: ReaderPalette) {
    val points = content.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.take(4)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("KEY POINTS", style = rInter(11, FontWeight.Bold, 1.6f), color = pal.goldDeep)
        points.forEach { pt ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(pal.gold))
                Text(pt, style = rInter(14, FontWeight.Medium).copy(lineHeight = 20.sp), color = pal.ink)
            }
        }
    }
}
