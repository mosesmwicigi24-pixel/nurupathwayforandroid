// Plan segment — the day-step reader, ported pixel-faithfully from the iOS
// PlanSegmentView (NuruMember/Features/Grow/PlanSegmentView.swift). Presented as
// the Figma make's ReadingDayReader: a warm cream day header (muted-gold kicker,
// navy serif title, per-segment step chips), a reader canvas with serif passage text
// (small gold verse numbers when the content carries them), a gold pull-quote
// card for featured scripture, a REFLECTION prompt card for talk-it-over
// segments, an encouragement line, and a pinned completion CTA.
//
// This screen uses the GLOBAL `Nuru` palette (navy #0B1F33, gold #C89B3C, warm
// surface #FBF8F1) — NOT the Plans-tab `PL` palette — matching the iOS source.
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PlanSegment
import org.nuruplace.member.data.net.ReadingPlanDetail
import org.nuruplace.member.ui.components.GrowCreamHeader
import org.nuruplace.member.ui.components.openExternal
import org.nuruplace.member.ui.theme.Fraunces
import org.nuruplace.member.ui.theme.Inter
import org.nuruplace.member.ui.theme.Nuru

// ── Type helpers — build exact-size styles from the two brand faces, mirroring
//    the iOS `.fraunces(N, weight)` / `.inter(N, weight)` calls. ──
private fun inter(size: Int, weight: FontWeight = FontWeight.Normal, kerning: Float = 0f) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp)

private fun serif(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: Int = 0) =
    TextStyle(
        fontFamily = Fraunces,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = if (lineHeight > 0) lineHeight.sp else (size * 1.35).sp,
    )

/** Short chip label per segment kind — WATCH/READ header equivalents (title-case). */
private fun chipLabel(kind: String): String = when (kind.lowercase()) {
    "video" -> "Watch"
    "reading" -> "Read"
    "devotional" -> "Devotional"
    "talk" -> "Talk"
    "scripture" -> "Scripture"
    else -> kind.replaceFirstChar { it.uppercase() }
}

@Composable
fun PlanSegmentScreen(
    planId: String,
    dayNumber: Int,
    index: Int,
    onBack: () -> Unit,
    onContinue: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<ReadingPlanDetail?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(planId) {
        loading = true
        runCatching { Net.client.api.plan(planId) }.onSuccess { detail = it }
        loading = false
    }

    val d = detail
    val day = d?.days?.firstOrNull { it.dayNumber == dayNumber }
    val segments = day?.segments?.sortedBy { it.sort } ?: emptyList()
    val segment = segments.getOrNull(index)
    val planTitle = d?.title ?: ""

    // Local completion state so the current step chip turns gold on completion,
    // matching iOS. `completed` is set best-effort after the network call.
    var completed by remember(segment?.segmentId) { mutableStateOf(segment?.completed == true) }

    /** Best-effort, non-blocking completion — a no-op replay if already done. */
    suspend fun markComplete(seg: PlanSegment) {
        if (!completed) runCatching { Net.client.api.completeSegment(seg.segmentId) }
        completed = true
    }

    // Read-type segments count as viewed on open (video waits for a play/CTA).
    LaunchedEffect(segment?.segmentId) {
        val seg = segment ?: return@LaunchedEffect
        if (seg.kind.lowercase() != "video") markComplete(seg)
    }

    Box(Modifier.fillMaxSize().background(Nuru.surface)) {
        when {
            segment != null -> {
                Column(Modifier.fillMaxSize()) {
                    SegmentHeader(
                        dayNumber = dayNumber,
                        planTitle = planTitle,
                        segment = segment,
                        segments = segments,
                        index = index,
                        completed = completed,
                        onBack = onBack,
                    )

                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(top = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        SegmentBody(
                            segment = segment,
                            onPlay = { url -> openExternal(context, url) },
                        )
                        EncouragementRow()
                    }

                    BottomCta(
                        hasNext = index + 1 < segments.size,
                        completed = completed,
                        onContinue = {
                            scope.launch {
                                markComplete(segment)
                                onContinue(index + 1)
                            }
                        },
                        onDone = {
                            scope.launch {
                                markComplete(segment)
                                onBack()
                            }
                        },
                    )
                }
            }

            loading -> CircularProgressIndicator(
                color = Nuru.gold,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> Text(
                "Couldn't load this reading.",
                style = inter(13),
                color = Nuru.ink600,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

// ── Navy day header (kicker · serif title · per-segment step chips) ──

@Composable
private fun SegmentHeader(
    dayNumber: Int,
    planTitle: String,
    segment: PlanSegment,
    segments: List<PlanSegment>,
    index: Int,
    completed: Boolean,
    onBack: () -> Unit,
) {
    val caption = segment.reference?.takeIf { it.isNotEmpty() } ?: chipLabel(segment.kind)

    GrowCreamHeader {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderChip(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Nuru.navy,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "DAY $dayNumber · ${planTitle.uppercase()}",
                    style = inter(10, FontWeight.Bold, 1.8f),
                    color = Nuru.eyebrow,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                HeaderChip(onClick = { /* bookmark — visual parity with iOS */ }) {
                    Icon(
                        Icons.Filled.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = Nuru.gold,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                segment.title,
                style = serif(22, FontWeight.SemiBold),
                color = Nuru.navy,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                caption,
                style = inter(12),
                color = Nuru.ink600,
                modifier = Modifier.padding(top = 2.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                segments.forEachIndexed { idx, seg ->
                    StepChip(
                        label = chipLabel(seg.kind),
                        done = seg.completed || (idx == index && completed),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** One header step chip — gold with a check once its segment is done. */
@Composable
private fun StepChip(label: String, done: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (done) Nuru.gold else Nuru.navy.copy(alpha = 0.08f))
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Nuru.navy,
                modifier = Modifier.size(9.dp),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            label,
            style = inter(10, FontWeight.Bold),
            color = if (done) Nuru.navy else Nuru.ink600,
            maxLines = 1,
        )
    }
}

// ── Reader blocks per segment kind ──

@Composable
private fun SegmentBody(segment: PlanSegment, onPlay: (String) -> Unit) {
    val content = segment.content?.takeIf { it.isNotEmpty() }
    val reference = segment.reference?.takeIf { it.isNotEmpty() }

    when (segment.kind.lowercase()) {
        "video" -> {
            VideoCard(
                imageUrl = segment.imageUrl,
                videoUrl = segment.videoUrl,
                onPlay = onPlay,
            )
            content?.let { PassageText(it) }
        }

        "scripture" -> {
            // Scripture segments ARE the featured verse.
            PullQuoteCard(
                text = content ?: reference ?: segment.title,
                caption = reference ?: "Scripture",
                quoted = content != null,
            )
        }

        "talk" -> {
            if (content != null) {
                ReaderReflectionCard(prompt = content)
            } else if (reference != null) {
                PullQuoteCard(text = reference, caption = "Scripture", quoted = false)
            }
        }

        else -> {
            content?.let { PassageText(it) }
            if (reference != null && reference != segment.content) {
                PullQuoteCard(text = reference, caption = "Scripture", quoted = false)
            }
        }
    }
}

// ── 16:9 video card (real `videoUrl` only) ──

@Composable
private fun VideoCard(imageUrl: String?, videoUrl: String?, onPlay: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(24.dp))
            .background(Nuru.navyGradient)
            .clickable(enabled = !videoUrl.isNullOrEmpty()) { videoUrl?.let(onPlay) },
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Nuru.gold),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play",
                tint = Nuru.navy,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

// ── Serif passage copy. Lines that open with a verse number ("14 but whoever…")
//    render it as a small raised gold marker, matching the Figma reader. ──

private data class PassageLine(val number: String?, val text: String)

/**
 * Split into trimmed non-empty lines; peel a leading verse number ("14 ", "14. ",
 * "14) ", "14: ") when present — 1..3 digits.
 */
private fun passageLines(content: String): List<PassageLine> =
    content.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { raw ->
            val digits = raw.takeWhile { it.isDigit() }
            if (digits.length in 1..3) {
                var rest = raw.drop(digits.length)
                if (rest.firstOrNull()?.let { it in ".):" } == true) rest = rest.drop(1)
                if (rest.startsWith(" ")) {
                    return@map PassageLine(digits, rest.trim())
                }
            }
            PassageLine(null, raw)
        }

@Composable
private fun PassageText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        passageLines(text).forEach { line ->
            val annotated = buildAnnotatedString {
                line.number?.let { n ->
                    withStyle(
                        SpanStyle(
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Nuru.gold,
                            baselineShift = BaselineShift(0.4f),
                        ),
                    ) { append("$n  ") }
                }
                withStyle(
                    SpanStyle(fontFamily = Fraunces, fontWeight = FontWeight.Normal, fontSize = 16.sp, color = Nuru.navy),
                ) { append(line.text) }
            }
            Text(annotated, style = serif(16, FontWeight.Normal, lineHeight = 23))
        }
    }
}

// ── Gold pull-quote card — verse tint (#FFF8E6), gold spine + hairline, quote
//    glyph, serif text, uppercase reference caption. ──

@Composable
private fun PullQuoteCard(text: String, caption: String, quoted: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Nuru.verseBg)
            .border(1.dp, Nuru.gold.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Nuru.gold))
        Column(
            Modifier.weight(1f).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.FormatQuote,
                contentDescription = null,
                tint = Nuru.gold,
                modifier = Modifier.size(16.dp),
            )
            Text(
                if (quoted) "“$text”" else text,
                style = serif(18, FontWeight.Normal, lineHeight = 25),
                color = Nuru.navy,
            )
            Text(
                caption.uppercase(),
                style = inter(11, FontWeight.Bold, 1.4f),
                color = Nuru.ink600,
            )
        }
    }
}

// ── "REFLECTION" prompt card — serif prompt on a white card (talk-it-over). ──

@Composable
private fun ReaderReflectionCard(prompt: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Nuru.white)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "REFLECTION",
            style = inter(11, FontWeight.Bold, 1.4f),
            color = Nuru.goldLo,
        )
        Text(
            prompt,
            style = serif(15, FontWeight.Normal, lineHeight = 21),
            color = Nuru.navy,
        )
    }
}

// ── The reader's gentle sign-off line on a soft gold tint. ──

@Composable
private fun EncouragementRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Nuru.gold.copy(alpha = 0.08f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.VolunteerActivism,
            contentDescription = null,
            tint = Nuru.gold,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "Every faithful day adds up. There's no rush — just presence.",
            style = inter(13),
            color = Nuru.navy,
        )
    }
}

// ── Pinned completion CTA (surface fade + navy button) ──

@Composable
private fun BottomCta(
    hasNext: Boolean,
    completed: Boolean,
    onContinue: () -> Unit,
    onDone: () -> Unit,
) {
    val fade = Brush.verticalGradient(listOf(Nuru.surface.copy(alpha = 0f), Nuru.surface))
    Column(
        Modifier
            .fillMaxWidth()
            .background(fade)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 12.dp),
    ) {
        val label = when {
            hasNext -> "Continue"
            completed -> "Done"
            else -> "Mark complete"
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Nuru.navy)
                .clickable { if (hasNext) onContinue() else onDone() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = inter(14, FontWeight.Bold), color = Color.White)
        }
    }
}
