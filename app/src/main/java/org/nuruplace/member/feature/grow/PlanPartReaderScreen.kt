// Plan part reader — page 4 of the four-page journey (Plans → Days → Day hub →
// THIS). One PART of the day at a time (Watch/Listen · The Word · Respond) on a
// warm day/night canvas, with the reading progress hairline + right-rail pace
// dot. "Finished" ticks every segment in the part (server-backed), tells the hub
// (PlanProgressBus), and pops back. The Respond part carries the day's reflection.
// Ported from the iOS PlanSegmentView grouped-part reader.
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PlanSegment
import org.nuruplace.member.data.net.ReadingPlanDetail
import org.nuruplace.member.data.net.SaveReflectionBody
import org.nuruplace.member.ui.components.VerseQuoteCard
import org.nuruplace.member.ui.theme.scaledLineHeight
import java.util.UUID

private fun rankOf(s: PlanSegment): Int = when (s.kind.lowercase()) {
    "video", "audio" -> 0
    "scripture" -> 1
    "talk" -> 3
    "reading" -> 5
    else -> if (s.title.lowercase().startsWith("pray")) 4 else 2
}

@Composable
fun PlanPartReaderScreen(planId: String, dayNumber: Int, part: String, index: Int, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val pal = ReaderPalette(ReaderMode.night)

    var detail by remember { mutableStateOf<ReadingPlanDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(planId) {
        runCatching { Net.client.api.plan(planId) }.onSuccess { detail = it }
        loading = false
    }

    val day = detail?.days?.firstOrNull { it.dayNumber == dayNumber }
    val sorted = (day?.segments ?: emptyList()).sortedWith(compareBy({ rankOf(it) }, { it.sort }))
    val group: List<PlanSegment> = when (part) {
        "word" -> sorted.filter { rankOf(it) in listOf(1, 2, 5) }
        "respond" -> sorted.filter { rankOf(it) == 4 }
        else -> listOfNotNull(sorted.getOrNull(index) ?: sorted.firstOrNull { rankOf(it) == 0 })
    }
    val partName = when (part) {
        "word" -> "The Word"
        "respond" -> "Respond"
        else -> if (group.firstOrNull()?.kind?.lowercase() == "audio") "Listen" else "Watch"
    }

    LaunchedEffect(group.map { it.segmentId }) {
        done = group.isNotEmpty() && group.all { it.completed }
    }

    val scroll = rememberScrollState()
    val progress by remember { derivedStateOf { if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f } }

    fun finish() {
        if (saving) return
        if (done) { onBack(); return }
        saving = true
        scope.launch {
            var lastAck: org.nuruplace.member.data.net.SegmentCompleteResult? = null
            group.filterNot { it.completed }.forEach { seg ->
                runCatching { Net.client.api.completeSegment(seg.segmentId) }.onSuccess { lastAck = it }
                PlanProgressBus.finished.tryEmit(seg.segmentId)
            }
            // The LAST segment's ack is the server's authoritative word on
            // whether this day just sealed and the next one opened — computed
            // in the same transaction as the write. Broadcasting it lets the
            // day hub skip waiting on an extra "Seal the day" tap, and the
            // plan overview tell a genuine lock apart from a completion still
            // landing through the sync path.
            lastAck?.let { ack ->
                if (ack.dayComplete) {
                    PlanProgressBus.dayUnlocked.tryEmit(
                        org.nuruplace.member.data.net.PlanDayUnlockAck(
                            planId = planId, dayNumber = ack.dayNumber,
                            nextDayNumber = ack.nextDayNumber, nextDayUnlocked = ack.nextDayUnlocked,
                        ),
                    )
                }
            }
            done = true; saving = false
            onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(pal.bg)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // Navy header — back · part name · night toggle.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0B1F33), Color(0xFF00132F))))
                    .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReaderChip(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Text("DAY $dayNumber", style = rInter(10, FontWeight.Bold, 1.6f), color = pal.gold, modifier = Modifier.weight(1f))
                    ReaderChip(onClick = { ReaderMode.night = !ReaderMode.night }) {
                        Icon(if (ReaderMode.night) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Reader mode", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Text(partName, style = rSerif(24, FontWeight.Medium), color = Color.White, modifier = Modifier.padding(top = 10.dp))
                group.firstOrNull { it.kind.lowercase() == "scripture" }?.reference?.let {
                    Text(it, style = rInter(11), color = Color.White.copy(alpha = 0.65f))
                }
            }
            // Reading progress hairline, flush under the header.
            ReadingProgressBar(progress, pal)

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = pal.gold) }
                group.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing to read here.", style = rInter(13), color = pal.inkDim) }
                else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        PartContent(part = part, group = group, pal = pal)
                        if (part == "respond") ReflectionBox(planId, dayNumber, pal)
                        EncouragementLine(pal)
                    }
                    // Right-rail pace dot (only when the part scrolls).
                    if (scroll.maxValue > 40) {
                        PaceRail(progress, pal, Modifier.align(Alignment.CenterEnd).padding(vertical = 8.dp))
                    }
                }
            }

            // Finished CTA.
            val fade = Brush.verticalGradient(listOf(pal.bg.copy(alpha = 0f), pal.bg))
            Column(Modifier.fillMaxWidth().background(fade).padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(pal.gold, Color(0xFFB6862F)))).clickable { finish() },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (saving) CircularProgressIndicator(color = pal.navy, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else {
                        Icon(Icons.Filled.Check, null, tint = pal.navy, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (done) "Done" else when (part) {
                                "word" -> "I've read today's Word"; "respond" -> "Amen — finished"
                                else -> if (partName == "Listen") "Finished listening" else "Finished watching"
                            },
                            style = rInter(14, FontWeight.Bold), color = pal.navy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PartContent(part: String, group: List<PlanSegment>, pal: ReaderPalette) {
    when (part) {
        "word" -> group.forEach { seg ->
            when (seg.kind.lowercase()) {
                // The day's passage — the shared cream card (parity with the
                // Pathway lesson's scripture blockquotes). Authored text wins; a
                // bare reference fetches its passage so the card never reads
                // "Proverbs 13:4" and nothing else.
                "scripture" -> {
                    val content = seg.content?.takeIf { it.isNotEmpty() }
                    val ref = seg.reference
                    when {
                        content != null -> VerseQuoteCard(verse = content, reference = ref ?: "Scripture")
                        ref != null && ScriptureRefs.isReference(ref) -> RScriptureQuote(ref)
                        else -> VerseQuoteCard(verse = ref ?: seg.title, reference = ref ?: "Scripture")
                    }
                }
                // Go Deeper: the passages themselves, one card per reference.
                "reading" -> seg.content?.takeIf { it.isNotEmpty() }?.let { RGoDeeper(it, pal) }
                else -> seg.content?.takeIf { it.isNotEmpty() }?.let { RPassage(it, pal) }
            }
        }
        "respond" -> group.forEach { seg ->
            seg.content?.takeIf { it.isNotEmpty() }?.let { RPrayer(it, pal) }
        }
        else -> {
            val seg = group.first()
            RMediaCard(seg.imageUrl, seg.videoUrl, portrait = true)
            if (seg.title.isNotEmpty()) Text(seg.title, style = rSerif(20, FontWeight.Medium), color = pal.ink)
            seg.content?.takeIf { it.isNotEmpty() }?.let { RKeynotes(it, pal) }
        }
    }
}

@Composable
private fun ReflectionBox(planId: String, dayNumber: Int, pal: ReaderPalette) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var hasSaved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }
    // Why the last save did not land — shown under the button, in words. A
    // silent failure reads as "Update does nothing" (owner report, 2026-09-04).
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(planId, dayNumber) {
        runCatching { Net.client.api.dayReflection(planId, dayNumber).data?.body }.getOrNull()?.let {
            if (text.isEmpty()) { text = it; hasSaved = true }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.card)
            .border(1.dp, pal.border, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("REFLECTION", style = rInter(11, FontWeight.Bold, 1.6f), color = pal.goldDeep)
        Text("What is God showing you today?", style = rSerif(16, FontWeight.Normal, 22, italic = true), color = pal.ink)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.bg).border(1.dp, pal.border, RoundedCornerShape(14.dp)).padding(12.dp)) {
            if (text.isBlank()) Text("Write it down while it's fresh…", style = rInter(13), color = pal.inkDim)
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = rInter(13, FontWeight.Medium).copy(color = pal.ink), maxLines = 8,
                cursorBrush = SolidColor(pal.gold), modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(pal.gold.copy(alpha = 0.12f))
                .border(1.dp, pal.gold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .clickable(enabled = text.isNotBlank() && !saving) {
                    scope.launch {
                        saving = true; error = null
                        val result = runCatching {
                            Net.client.api.saveDayReflection(planId, dayNumber, SaveReflectionBody(text.trim().take(4000), UUID.randomUUID().toString()))
                        }
                        saving = false
                        result.onSuccess { row ->
                            hasSaved = true
                            // The server's copy is what the next visit will show —
                            // mirror it now, so what you see is what was kept.
                            if (row.body.isNotEmpty()) text = row.body
                            justSaved = true
                            delay(2_200)
                            justSaved = false
                        }.onFailure {
                            error = "Couldn't save your reflection — check your connection and try again."
                        }
                    }
                }.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (saving) CircularProgressIndicator(color = pal.goldDeep, strokeWidth = 1.5.dp, modifier = Modifier.size(14.dp))
            else Text(
                when {
                    justSaved -> "Saved ✓"
                    hasSaved -> "Update"
                    else -> "Save reflection"
                },
                style = rInter(12, FontWeight.Bold), color = pal.goldDeep,
            )
        }
        error?.let { Text(it, style = rInter(12, FontWeight.Medium), color = Color(0xFFB91C1C)) }
    }
}

@Composable
private fun EncouragementLine(pal: ReaderPalette) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.gold.copy(alpha = 0.08f)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🕊️", style = rInter(16))
        Text("Every faithful day adds up. There's no rush — just presence.", style = rInter(13, FontWeight.Medium), color = pal.ink)
    }
}

@Composable
private fun ReaderChip(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
