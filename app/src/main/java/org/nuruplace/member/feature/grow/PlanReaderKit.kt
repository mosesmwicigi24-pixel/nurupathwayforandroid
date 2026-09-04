// Plan reader kit — the shared pieces of the four-page Plans journey (iOS parity):
// a warm day/night reader palette, the two reading instruments (top gold progress
// hairline + right-rail pace dot), a lightweight "part finished" bus so a part
// ticks its hub row on return, and the palette-aware reader blocks (Scripture
// pull-quote, serif passage with gold verse numbers, prayer, media card,
// keynotes). Ported from the iOS PlanSegmentView + NuruReadingBar/NuruPaceRail.
package org.nuruplace.member.feature.grow

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableSharedFlow
import org.nuruplace.member.data.net.PlanDayUnlockAck
import org.nuruplace.member.data.net.ScripturePassage
import org.nuruplace.member.ui.components.InlineVideoPlayer
import org.nuruplace.member.ui.components.VerseQuoteCard
import org.nuruplace.member.ui.theme.Fraunces
import org.nuruplace.member.ui.theme.Inter
import org.nuruplace.member.ui.theme.scaledLineHeight

// ── Type helpers (exact-size brand faces) ──
internal fun rInter(size: Int, weight: FontWeight = FontWeight.Medium, kerning: Float = 0f) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp)

internal fun rSerif(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Int = 0, italic: Boolean = false) =
    TextStyle(
        fontFamily = Fraunces, fontWeight = weight, fontSize = size.sp,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        lineHeight = scaledLineHeight(if (lineHeight > 0) lineHeight else size * 1.35),
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

    /**
     * The LAST segment of a day's authoritative ack — the offline-sync race:
     * the day unlocks server-side the instant that completion lands, but a
     * screen re-fetching the plan can still lose the race against its own
     * write. `replay = 1` so a screen that was off the back stack when this
     * fired (Compose Navigation disposes destinations under a pushed route)
     * still sees it the moment it's recomposed on return — mirrors how the
     * iOS NavigationStack keeps a pushed screen's `.onReceive` subscription
     * alive underneath.
     */
    val dayUnlocked = MutableSharedFlow<PlanDayUnlockAck>(replay = 1, extraBufferCapacity = 4)
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
    // The cited reference a tap just opened — read in a sheet, in place.
    var openRef by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rLines(text).forEach { line ->
            if (line.number == null) {
                // Every reference the teaching cites ("(James 2:17)") is a gold
                // link; the passage opens below without leaving the page.
                RLinkedParagraph(line.text, pal) { openRef = it }
            } else {
                val annotated = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = pal.gold, baselineShift = BaselineShift(0.4f))) { append("${line.number}  ") }
                    withStyle(SpanStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = pal.ink)) { append(line.text) }
                }
                Text(annotated, style = rInter(16, FontWeight.Medium).copy(lineHeight = scaledLineHeight(25)))
            }
        }
    }
    openRef?.let { ref -> RScriptureSheet(ref, pal) { openRef = null } }
}

// ── Scripture woven in (iOS ScripturePassages.swift parity) ──
// Go Deeper references open into their passages; references cited inline in
// the teaching are links that open the passage in a sheet; a scripture segment
// with only a reference fetches its text. Text comes from GET /scripture
// through [ScriptureStore]; everything degrades to the reference alone.

internal fun passageCaption(p: ScripturePassage): String =
    p.version?.trim()?.takeIf { it.isNotEmpty() }?.let { "${p.reference} · $it" } ?: p.reference

/** Passage text with YouVersion's inline verse numbers set small, gold and
 *  raised, so the eye reads the words and merely notices the numbers. */
internal fun verseNumberedText(text: String, pal: ReaderPalette, size: Int): AnnotatedString = buildAnnotatedString {
    val body = SpanStyle(fontFamily = Fraunces, fontWeight = FontWeight.Normal, fontSize = size.sp, color = pal.ink)
    val number = SpanStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = pal.gold, baselineShift = BaselineShift(0.4f))
    var last = 0
    for (m in ScriptureRefs.verseNumber.findAll(text)) {
        if (m.range.first > last) withStyle(body) { append(text.substring(last, m.range.first)) }
        withStyle(number) { append(m.value) }
        last = m.range.last + 1
    }
    if (last < text.length) withStyle(body) { append(text.substring(last)) }
}

@Composable
internal fun RScripturePassageText(text: String, pal: ReaderPalette, size: Int = 16) {
    Text(verseNumberedText(text, pal, size), style = rSerif(size, FontWeight.Normal, (size * 1.55).toInt()))
}

/** Go Deeper: one reference, opened into its passage on tap. */
@Composable
internal fun RScriptureRefCard(reference: String, pal: ReaderPalette) {
    var open by remember(reference) { mutableStateOf(false) }
    var passage by remember(reference) { mutableStateOf<ScripturePassage?>(null) }
    var loading by remember(reference) { mutableStateOf(false) }
    var failed by remember(reference) { mutableStateOf(false) }
    var attempt by remember(reference) { mutableStateOf(0) }
    LaunchedEffect(open, attempt) {
        if (!open || passage != null) return@LaunchedEffect
        loading = true; failed = false
        ScriptureStore.passage(reference).onSuccess { passage = it }.onFailure { failed = true }
        loading = false
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(pal.gold.copy(alpha = 0.06f))
            .border(1.dp, pal.gold.copy(alpha = if (open) 0.3f else 0f), shape)
            .animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.MenuBook, null, tint = pal.goldDeep, modifier = Modifier.size(16.dp))
            Text(reference, style = rInter(13, FontWeight.SemiBold), color = pal.ink, modifier = Modifier.weight(1f))
            if (loading) CircularProgressIndicator(color = pal.goldDeep, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
            else Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Hide" else "Read", tint = pal.inkDim, modifier = Modifier.size(18.dp))
        }
        if (open) {
            passage?.let { p ->
                Row(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(pal.gold))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RScripturePassageText(p.text, pal)
                        Text(passageCaption(p).uppercase(), style = rInter(10, FontWeight.Bold, 1.2f), color = pal.inkDim)
                    }
                }
            }
            if (failed && passage == null) {
                Text(
                    "Couldn't load this passage — tap to try again.",
                    style = rInter(12, FontWeight.Medium), color = pal.inkDim,
                    modifier = Modifier.fillMaxWidth().clickable { attempt++ }.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                )
            }
        }
    }
}

/** The Go Deeper block: every reference as its own card; any authored note
 *  between them stays a plain line. */
@Composable
internal fun RGoDeeper(refs: String, pal: ReaderPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("GO DEEPER", style = rInter(11, FontWeight.Bold, 1.6f), color = pal.goldDeep)
        ScriptureRefs.split(refs).forEach { line ->
            if (ScriptureRefs.isReference(line)) RScriptureRefCard(line, pal)
            else Text(line, style = rInter(13, FontWeight.Medium).copy(lineHeight = scaledLineHeight(19)), color = pal.inkDim)
        }
    }
}

/** The day's pull-quote when the author gave a reference and no text: fetch
 *  the passage, show the reference alone until it lands (or if it never does). */
@Composable
internal fun RScriptureQuote(reference: String) {
    var passage by remember(reference) { mutableStateOf<ScripturePassage?>(null) }
    LaunchedEffect(reference) { ScriptureStore.passage(reference).onSuccess { passage = it } }
    VerseQuoteCard(verse = passage?.text ?: reference, reference = passage?.let(::passageCaption) ?: reference)
}

/** One paragraph of teaching; every reference it cites is a gold, underlined
 *  link that opens the passage in place. */
@Composable
internal fun RLinkedParagraph(text: String, pal: ReaderPalette, onReference: (String) -> Unit) {
    val open by rememberUpdatedState(onReference)
    val annotated = remember(text, pal.night) {
        buildAnnotatedString {
            val body = SpanStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = pal.ink)
            val link = TextLinkStyles(
                style = SpanStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = pal.goldDeep, textDecoration = TextDecoration.Underline),
            )
            var last = 0
            for (m in ScriptureRefs.detect(text)) {
                if (m.range.first > last) withStyle(body) { append(text.substring(last, m.range.first)) }
                withLink(LinkAnnotation.Clickable(tag = m.reference, styles = link, linkInteractionListener = { open(m.reference) })) {
                    append(text.substring(m.range.first, m.range.last + 1))
                }
                last = m.range.last + 1
            }
            if (last < text.length) withStyle(body) { append(text.substring(last)) }
        }
    }
    Text(annotated, style = rInter(16, FontWeight.Medium).copy(lineHeight = scaledLineHeight(25)))
}

/** A cited reference, read in place — the sheet a gold link in the teaching opens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RScriptureSheet(reference: String, pal: ReaderPalette, onDismiss: () -> Unit) {
    var passage by remember(reference) { mutableStateOf<ScripturePassage?>(null) }
    var failed by remember(reference) { mutableStateOf(false) }
    var attempt by remember(reference) { mutableStateOf(0) }
    LaunchedEffect(reference, attempt) {
        failed = false
        ScriptureStore.passage(reference).onSuccess { passage = it }.onFailure { failed = true }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = pal.bg) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(pal.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MenuBook, null, tint = pal.gold, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text("SCRIPTURE", style = rInter(10, FontWeight.Bold, 1.6f), color = pal.goldDeep)
                    Text(reference, style = rSerif(20, FontWeight.Medium), color = pal.ink)
                }
            }
            val p = passage
            when {
                p != null -> {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(pal.gold))
                        RScripturePassageText(p.text, pal, size = 17)
                    }
                    p.version?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        Text(it.uppercase(), style = rInter(10, FontWeight.Bold, 1.2f), color = pal.inkDim)
                    }
                }
                failed -> {
                    Text("Couldn't load this passage — check your connection and try again.", style = rInter(13), color = pal.inkDim)
                    Text("Try again", style = rInter(13, FontWeight.Bold), color = pal.goldDeep, modifier = Modifier.clickable { attempt++ }.padding(vertical = 4.dp))
                }
                else -> Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = pal.gold)
                }
            }
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

/** EVERY video plays in place — there is no hand-off left. [InlineVideoPlayer]
 *  picks the engine by provider: ExoPlayer for a direct/self-hosted/cloudinary/
 *  HLS URL, the provider's own inline iframe for YouTube/Vimeo. Two bugs met
 *  here: a self-hosted `/media/<uuid>.mov` handed to openExternal() popped a
 *  bare "Download file again?" Chrome prompt (2026-07-31), and a YouTube URL
 *  bounced the member out of the app entirely (fixed 2026-08-28 by dropping the
 *  external-host branch this card used to keep).
 *
 *  Portrait (9:15) note: the card fixes its own aspect and passes fillMaxSize,
 *  so the player's internal 16:9 cannot be satisfied and the incoming (tight)
 *  constraints win — the video fills the tall card and PlayerView / the
 *  provider iframe letterbox inside it, exactly as before this change. */
@Composable
internal fun RMediaCard(imageUrl: String?, videoUrl: String?, portrait: Boolean) {
    var playingInline by androidx.compose.runtime.remember(videoUrl) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(if (portrait) 9f / 15f else 16f / 9f)
            .clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF1A406B), Color(0xFF0B1F33), Color(0xFF00132F))))
            .clickable(enabled = !videoUrl.isNullOrEmpty() && !playingInline) { playingInline = true },
        contentAlignment = Alignment.Center,
    ) {
        if (playingInline && videoUrl != null) {
            InlineVideoPlayer(videoUrl, modifier = Modifier.fillMaxSize())
        } else {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFC89B3C)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = Color(0xFF0A1628), modifier = Modifier.size(30.dp))
            }
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
                Text(pt, style = rInter(14, FontWeight.Medium).copy(lineHeight = scaledLineHeight(20)), color = pal.ink)
            }
        }
    }
}
