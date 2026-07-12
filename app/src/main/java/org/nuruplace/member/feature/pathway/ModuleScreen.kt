// Module (lesson reader) — a faithful port of the iOS ModuleView. Two states:
// CHROME-VISIBLE (default) shows a cream header card (back · LEVEL·MODULE · expand ·
// share · serif title · min-read/section chips · Read/Reflect step buttons), the
// scrolling markdown lesson, and a sticky bottom gate (progress · step chips ·
// "Start the quiz →" / "Mark complete"). IMMERSIVE (expand) hides the chrome for
// distraction-free reading with a floating collapse button and a right-edge progress
// rail. The gate: Read (scroll to end) + Reflect (save a reflection) unlock the CTA.
// A 30s engagement heartbeat reports reading seconds (fire-and-forget). Server owns
// completion + unlocking (§1.1). Uses a screen-local `ML` palette to match iOS 1:1.
package org.nuruplace.member.feature.pathway

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.CompleteBody
import org.nuruplace.member.data.net.ModuleDetail
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.SaveReflectionBody
import org.nuruplace.member.ui.theme.Fraunces
import org.nuruplace.member.ui.theme.Inter
import java.util.UUID

// Screen-local palette — iOS `enum ML` (ModuleView.swift), distinct from global Nuru.
private object ML {
    val navy = Color(0xFF0A1628)
    val cream = Color(0xFFF4F0E8)
    val surface = Color(0xFFFBF8F1)
    val gold = Color(0xFFC9A227)
    val goldDeep = Color(0xFFB6862F)
    val border = Color(0x140A2540)
    val track = Color(0x1A0A2540)
    val overline = Color(0xFF9A7A2A)
    val kicker = Color(0xFFA8861C)
    val secondary = Color(0xFF59667C)
    val lead = Color(0xFF3A4A5F)
    val bodyInk = Color(0xFF1B2733)
    val goldGrad = Brush.linearGradient(listOf(gold, goldDeep))
    val headerGrad = Brush.linearGradient(listOf(Color(0xFFF6F4EF), Color(0xFFEFE8DA)))
}

private fun ml(size: Int, w: FontWeight = FontWeight.Normal, ker: Float = 0f) =
    TextStyle(fontFamily = Inter, fontWeight = w, fontSize = size.sp, letterSpacing = ker.sp)
private fun mlSerif(size: Int, w: FontWeight = FontWeight.SemiBold, ker: Float = 0f, italic: Boolean = false) =
    TextStyle(fontFamily = Fraunces, fontWeight = w, fontSize = size.sp, letterSpacing = ker.sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)

@Composable
fun ModuleScreen(
    moduleId: String,
    onBack: () -> Unit,
    onTakeQuiz: (String) -> Unit,
    onCompleted: () -> Unit,
) {
    var m by remember(moduleId) { mutableStateOf<ModuleDetail?>(null) }
    var loadError by remember(moduleId) { mutableStateOf<String?>(null) }
    LaunchedEffect(moduleId) { m = runCatching { Net.client.api.module(moduleId) }.onFailure { loadError = ApiException.message(it) }.getOrNull() }

    val detail = m
    if (detail == null) {
        Box(Modifier.fillMaxSize().background(ML.cream), contentAlignment = Alignment.Center) {
            if (loadError != null) Text(loadError!!, style = ml(14), color = ML.secondary)
            else CircularProgressIndicator(color = ML.gold)
        }
        return
    }
    Loaded(detail, onBack, onTakeQuiz, onCompleted)
}

@Composable
private fun Loaded(m: ModuleDetail, onBack: () -> Unit, onTakeQuiz: (String) -> Unit, onCompleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var chromeHidden by remember { mutableStateOf(false) }
    var reflection by remember { mutableStateOf("") }
    var reflectSaved by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Living curriculum — "hear it another way": the same lesson re-rendered by
    // Nuru (simple / Kiswahili / story chips live inside the dialog). §1.9-gated
    // server-side.
    var explainOpen by remember { mutableStateOf(false) }
    // Finished modules fold the reflection to what was written; Edit unfolds it.
    var editingReflection by remember { mutableStateOf(false) }
    // Wave 2 — the freshly-shared voice note (until the next module fetch)
    // and whether this member may leave one (Instructor+, server-enforced).
    var localVoiceNote by remember { mutableStateOf<org.nuruplace.member.data.net.ModuleVoiceNote?>(null) }
    var canLeaveVoiceNote by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        canLeaveVoiceNote = runCatching { Net.client.api.me().profile.role }.getOrNull() in
            setOf("Instructor", "Admin", "SuperAdmin")
    }
    var showRevisit by remember { mutableStateOf(false) }
    if (showRevisit) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRevisit = false },
            containerColor = Color.White,
            title = { Text("You've completed this module", style = mlSerif(19, FontWeight.SemiBold), color = ML.navy) },
            text = { Text("It is sealed — but you can still revisit what you wrote or try the quiz again.", style = ml(13), color = ML.secondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showRevisit = false; editingReflection = true }) {
                    Text("Edit my reflection", style = ml(13, FontWeight.Bold), color = ML.navy)
                }
            },
            dismissButton = {
                if (m.requiresQuiz) {
                    androidx.compose.material3.TextButton(onClick = { showRevisit = false; onTakeQuiz(m.moduleId) }) {
                        Text("Retake the quiz", style = ml(13, FontWeight.Bold), color = ML.gold)
                    }
                }
            },
        )
    }
    if (explainOpen) ExplainDialog(m.moduleId, initialStyle = "simple", onDismiss = { explainOpen = false })

    // Read progress from the scroll position; latch "reached end" so scrolling back up
    // doesn't un-fill the Read step.
    val progress by remember { derivedStateOf { if (scroll.maxValue > 0) scroll.value.toFloat() / scroll.maxValue else 0f } }
    var reachedEnd by remember { mutableStateOf(false) }
    LaunchedEffect(progress) { if (progress >= 0.98f) reachedEnd = true }

    val readDone = reachedEnd
    val reflectDone = reflectSaved
    val doneCount = (if (readDone) 1 else 0) + (if (reflectDone) 1 else 0)
    val complete = readDone && reflectDone

    // Engagement heartbeat: +1s/s on screen, flush every 30s + on leave (fire-and-forget).
    var readingDelta by remember { mutableIntStateOf(0) }
    fun flush() {
        val secs = readingDelta
        if (secs <= 0) return
        readingDelta = 0
        Net.client.bgScope.launch {
            val body: JsonObject = buildJsonObject { put("reading_seconds", JsonPrimitive(secs.coerceAtMost(600))) }
            runCatching { Net.client.api.reportModuleEngagement(m.moduleId, body) }
        }
    }
    LaunchedEffect(m.moduleId) {
        var tick = 0
        while (true) { delay(1_000); readingDelta++; if (++tick >= 30) { tick = 0; flush() } }
    }
    DisposableEffect(m.moduleId) { onDispose { flush() } }

    val readMinutes = remember(m) { (m.pages.joinToString(" ").split(Regex("\\s+")).size / 200).coerceAtLeast(1) }
    val sectionCount = m.pages.size.coerceAtLeast(1)

    Box(Modifier.fillMaxSize().background(ML.cream)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            if (!chromeHidden) {
                Header(m, readMinutes, sectionCount, readDone, reflectDone, onBack = onBack, onExpand = { chromeHidden = true }, onExplain = { explainOpen = true }, onRetake = if (m.completed && m.requiresQuiz) ({ flush(); onTakeQuiz(m.moduleId) }) else null)
            }
            // Lesson content
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // A word from the member's own discipler — the human voice
                // before any produced media (Wave 2). Leaders can record.
                (localVoiceNote ?: m.voiceNote)?.let { VoiceNoteCard(it) }
                if (canLeaveVoiceNote) {
                    VoiceNoteLeaderRow(m.moduleId, localVoiceNote ?: m.voiceNote) { localVoiceNote = it }
                }
                m.keyVerses?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { KeyVerseCard(it) }
                m.pages.forEachIndexed { i, page ->
                    SectionHeader(i + 1, sectionCount)
                    MarkdownView(page)
                }
                Spacer(Modifier.height(8.dp))
                if (m.completed && !editingReflection) {
                    ReflectionFolded(text = reflection) { showRevisit = true }
                } else ReflectionCard(
                    value = reflection, onValue = { reflection = it; if (reflectSaved) reflectSaved = false },
                    saved = reflectSaved,
                    onSave = {
                        scope.launch {
                            runCatching { Net.client.api.submitModuleReflection(m.moduleId, SaveReflectionBody(reflection.trim().take(4000), UUID.randomUUID().toString())) }
                            reflectSaved = true
                        }
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
            if (!chromeHidden && !m.completed) {
                BottomGate(
                    doneCount = doneCount, readDone = readDone, reflectDone = reflectDone, complete = complete,
                    requiresQuiz = m.requiresQuiz, passMark = m.quizPassMark, busy = busy, error = error,
                    onStartQuiz = { flush(); onTakeQuiz(m.moduleId) },
                    onComplete = {
                        if (!busy) {
                            busy = true; error = null
                            scope.launch {
                                try {
                                    flush()
                                    Net.client.api.completeModule(m.moduleId, CompleteBody(reflectionText = reflection.trim().ifBlank { null }))
                                    onCompleted()
                                } catch (e: Exception) { error = ApiException.message(e) } finally { busy = false }
                            }
                        }
                    },
                )
            }
        }

        // Top reading progress bar (both states, flush to the top).
        Box(Modifier.fillMaxWidth().height(3.dp).background(ML.track.copy(alpha = 0.5f))) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).background(ML.goldGrad))
        }

        // Right-edge progress rail.
        PaceRail(progress, Modifier.align(Alignment.CenterEnd).fillMaxHeight())

        // Floating collapse button (immersive only).
        if (chromeHidden) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp).size(40.dp).clip(RoundedCornerShape(999.dp))
                    .background(ML.navy.copy(alpha = 0.82f)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                    .clickable { chromeHidden = false },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.CloseFullscreen, "Exit reading mode", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

// ─────────────────────────── Header (chrome-visible) ───────────────────────────

@Composable
private fun Header(m: ModuleDetail, readMinutes: Int, sectionCount: Int, readDone: Boolean, reflectDone: Boolean, onBack: () -> Unit, onExpand: () -> Unit, onExplain: () -> Unit, onRetake: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(ML.headerGrad)
            .padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 20.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("LEVEL ${m.levelNumber} · MODULE ${m.moduleSequenceNumber}", style = ml(11, FontWeight.Bold, 2f), color = ML.overline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SquareBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                // "Hear it another way" — Nuru re-renders this lesson.
                SquareBtn(Icons.Filled.AutoAwesome, "Hear it another way", onExplain)
                Spacer(Modifier.width(8.dp))
                SquareBtn(Icons.Filled.OpenInFull, "Reading mode", onExpand)
                Spacer(Modifier.width(8.dp))
                SquareBtn(Icons.Filled.Share, "Share") {}
            }
        }
        Text(m.title, style = mlSerif(24, FontWeight.Medium, -0.7f), color = ML.navy, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            MetaPill(Icons.Filled.Schedule, "≈ $readMinutes min read")
            Spacer(Modifier.width(8.dp))
            MetaPill(Icons.Filled.MenuBook, "$sectionCount section" + if (sectionCount == 1) "" else "s")
        }
        if (m.completed) {
            // Completed ribbon — ✓ COMPLETED · score · finish time · Retake.
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(999.dp))
                    .background(ML.gold.copy(alpha = 0.14f))
                    .border(1.dp, ML.gold.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, null, tint = ML.navy, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text("COMPLETED", style = ml(10, FontWeight.Bold, 1.4f), color = ML.navy)
                if (m.bestScore >= 0) { Spacer(Modifier.width(6.dp)); Text("· ${m.bestScore}%", style = ml(11, FontWeight.Bold), color = ML.gold) }
                m.finishedLine?.let { Spacer(Modifier.width(6.dp)); Text("· $it", style = ml(10), color = ML.secondary, maxLines = 1) }
                Spacer(Modifier.weight(1f))
                if (onRetake != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(ML.navy)
                            .clickable { onRetake() }.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("Retake", style = ml(11, FontWeight.Bold), color = Color.White) }
                }
            }
        } else Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(999.dp)).border(1.dp, ML.border, RoundedCornerShape(999.dp)),
        ) {
            Segment("Read", readDone, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(36.dp).background(ML.border))
            Segment("Reflect", reflectDone, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SquareBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, ML.border, RoundedCornerShape(16.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, cd, tint = ML.navy, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun MetaPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White).border(1.dp, ML.border, RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ML.secondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = ml(11), color = ML.secondary)
    }
}

@Composable
private fun Segment(label: String, done: Boolean, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(999.dp)).background(if (done) ML.gold.copy(alpha = 0.9f) else Color.Transparent).padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (done) { Icon(Icons.Filled.Check, null, tint = ML.navy, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)) }
        Text(label, style = ml(12, FontWeight.SemiBold), color = if (done) ML.navy else ML.secondary)
    }
}

// ─────────────────────────── Bottom gate ───────────────────────────

@Composable
private fun BottomGate(
    doneCount: Int, readDone: Boolean, reflectDone: Boolean, complete: Boolean,
    requiresQuiz: Boolean, passMark: Int, busy: Boolean, error: String?,
    onStartQuiz: () -> Unit, onComplete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(ML.cream).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ML.border))
        Text(
            if (complete) "All steps done 🎉" else "$doneCount of 2 steps done",
            style = ml(11, FontWeight.Bold), color = if (complete) ML.overline else ML.navy,
        )
        // The done state collapses to the celebratory line + CTA; in progress,
        // the chips alone carry per-step state (the old bar duplicated them and
        // cost a row of reading space).
        if (!complete) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepChip("Read", readDone)
                StepChip("Reflect", reflectDone)
            }
        }
        error?.let { Text(it, style = ml(11), color = Color(0xFFB91C1C)) }
        when {
            !complete -> Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, ML.border, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = ML.secondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (!readDone) "Read to the end to ${if (requiresQuiz) "unlock the quiz" else "continue"}" else "Add a reflection to ${if (requiresQuiz) "unlock the quiz" else "continue"}", style = ml(13, FontWeight.SemiBold), color = ML.secondary)
                }
            }
            requiresQuiz -> GoldCta(if (busy) "…" else "Start the quiz  →", busy) { onStartQuiz() }
            else -> GoldCta(if (busy) "…" else "Mark complete", busy) { onComplete() }
        }
    }
}

@Composable
private fun GateSeg(done: Boolean, modifier: Modifier) {
    Box(modifier.height(6.dp).clip(RoundedCornerShape(999.dp)).background(if (done) ML.gold else ML.track))
}

@Composable
private fun StepChip(label: String, done: Boolean) {
    Row(Modifier.clip(RoundedCornerShape(999.dp)).background(if (done) ML.gold.copy(alpha = 0.9f) else Color.White).border(1.dp, if (done) Color.Transparent else ML.border, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (done) { Icon(Icons.Filled.Check, null, tint = ML.navy, modifier = Modifier.size(11.dp)); Spacer(Modifier.width(3.dp)) }
        Text(label, style = ml(11, FontWeight.Bold), color = if (done) ML.navy else ML.secondary)
    }
}

@Composable
private fun GoldCta(label: String, busy: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(ML.goldGrad).clickable(enabled = !busy) { onClick() }, contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(color = ML.navy, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        else Text(label, style = ml(14, FontWeight.Bold), color = ML.navy)
    }
}

// ─────────────────────────── Progress rail ───────────────────────────

@Composable
private fun PaceRail(progress: Float, modifier: Modifier) {
    Canvas(modifier.width(17.dp)) {
        val x = size.width - 8.5.dp.toPx()
        val top = 66.dp.toPx(); val bottom = size.height - 92.dp.toPx()
        if (bottom <= top) return@Canvas
        val span = bottom - top
        val w = 3.dp.toPx()
        drawLine(ML.gold.copy(alpha = 0.12f), Offset(x, top), Offset(x, bottom), strokeWidth = w, cap = StrokeCap.Round)
        val dotY = top + span * progress.coerceIn(0f, 1f)
        drawLine(ML.gold.copy(alpha = 0.55f), Offset(x, top), Offset(x, dotY), strokeWidth = w, cap = StrokeCap.Round)
        drawCircle(ML.gold, radius = 4.5.dp.toPx(), center = Offset(x, dotY))
    }
}

// ─────────────────────────── Content: KEY VERSE + section + markdown ───────────────────────────

@Composable
private fun KeyVerseCard(verse: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ML.surface)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(ML.gold).align(Alignment.CenterStart))
        Column(Modifier.padding(16.dp)) {
            Text("KEY VERSE", style = ml(10, FontWeight.Bold, 1.8f), color = ML.kicker)
            Spacer(Modifier.height(8.dp))
            Text("“$verse”", style = mlSerif(17, FontWeight.Normal, italic = true), color = ML.navy)
        }
    }
}

@Composable
private fun SectionHeader(index: Int, total: Int) {
    Column {
        Text(if (total > 1) "SECTION · $index OF $total" else "SECTION", style = ml(10, FontWeight.Bold, 1.8f), color = ML.kicker)
        Spacer(Modifier.height(6.dp))
        Text("Section $index", style = mlSerif(23, FontWeight.SemiBold, -0.5f), color = ML.navy)
    }
}

/** Very small markdown renderer for the lesson body (headings, paragraphs, bullets,
 *  numbered lists, blockquote verse callouts, rules; inline bold/italic). */
@Composable
private fun MarkdownView(md: String) {
    val blocks = remember(md) { parseMarkdown(md) }
    var firstParaSeen = false
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Md.Heading -> Text(b.text, style = mlSerif(when (b.level) { 1 -> 22; 2 -> 19; 3 -> 17; else -> 16 }, FontWeight.SemiBold, -0.4f), color = ML.navy)
                is Md.Para -> {
                    val lead = !firstParaSeen; firstParaSeen = true
                    Text(inline(b.text), style = if (lead) ml(16, FontWeight.Medium) else ml(15), color = if (lead) ML.lead else ML.bodyInk, lineHeight = if (lead) 23.sp else 21.sp)
                }
                is Md.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    b.items.forEach { item ->
                        Row {
                            Box(Modifier.padding(top = 7.dp).size(5.dp).clip(RoundedCornerShape(999.dp)).background(ML.navy))
                            Spacer(Modifier.width(10.dp))
                            Text(inline(item), style = ml(15), color = ML.bodyInk, lineHeight = 21.sp)
                        }
                    }
                }
                is Md.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    b.items.forEachIndexed { i, item ->
                        Row {
                            Text("${i + 1}.", style = ml(14, FontWeight.Bold), color = ML.gold, modifier = Modifier.width(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(inline(item), style = ml(15), color = ML.bodyInk, lineHeight = 21.sp)
                        }
                    }
                }
                // Mirrors iOS MLTableBlock: horizontal scroller, 108dp min
                // columns, gold-tinted header band, hairline row + column
                // dividers, cream ground, 14dp rounding.
                is Md.Table -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    val minCol = 108.dp
                    Column(
                        Modifier.widthIn(min = minCol * b.header.size)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ML.surface)
                            .border(1.dp, ML.border, RoundedCornerShape(14.dp)),
                    ) {
                        Row(Modifier.background(ML.gold.copy(alpha = 0.14f)).height(IntrinsicSize.Min)) {
                            b.header.forEachIndexed { i, h ->
                                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(ML.border))
                                Text(
                                    inline(h), style = ml(13, FontWeight.Bold), color = ML.navy,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.weight(1f).widthIn(min = minCol)
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                )
                            }
                        }
                        b.rows.forEach { row ->
                            Box(Modifier.fillMaxWidth().height(1.dp).background(ML.border))
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                (0 until b.header.size).forEach { c ->
                                    if (c > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(ML.border))
                                    Text(
                                        inline(row.getOrNull(c) ?: ""), style = ml(13), color = ML.bodyInk,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.weight(1f).widthIn(min = minCol)
                                            .padding(horizontal = 10.dp, vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                is Md.Quote -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ML.surface)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(ML.gold).align(Alignment.CenterStart))
                    Text("“${b.text}”", style = mlSerif(17, FontWeight.Normal, italic = true), color = ML.navy, modifier = Modifier.padding(16.dp))
                }
                Md.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(ML.border))
            }
        }
    }
}

private sealed interface Md {
    data class Heading(val level: Int, val text: String) : Md
    data class Para(val text: String) : Md
    data class Bullet(val items: List<String>) : Md
    data class Numbered(val items: List<String>) : Md
    data class Quote(val text: String) : Md
    data class Table(val header: List<String>, val rows: List<List<String>>) : Md
    data object Rule : Md
}

private val NUM = Regex("^\\d+[.)]\\s+")

private fun parseMarkdown(text: String): List<Md> {
    val out = mutableListOf<Md>()
    val lines = text.replace("\r\n", "\n").split("\n")
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out.add(Md.Para(para.toString().trim())); para.clear() }
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val t = raw.trim()
        when {
            t.isEmpty() -> { flush(); i++ }
            t.startsWith("#") -> { flush(); val lvl = t.takeWhile { it == '#' }.length; out.add(Md.Heading(lvl.coerceIn(1, 6), t.dropWhile { it == '#' }.trim())); i++ }
            t == "---" || t == "***" || t == "___" -> { flush(); out.add(Md.Rule); i++ }
            // GFM pipe table: header row, separator row (---), then data rows.
            t.startsWith("|") && i + 1 < lines.size && lines[i + 1].trim().let { sep ->
                sep.startsWith("|") && sep.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").isEmpty() && sep.contains("-")
            } -> {
                flush()
                fun cells(row: String): List<String> = row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
                val header = cells(t)
                i += 2 // skip header + separator
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) { rows.add(cells(lines[i])); i++ }
                out.add(Md.Table(header, rows))
            }
            t.startsWith(">") -> { flush(); val sb = StringBuilder(); while (i < lines.size && lines[i].trim().startsWith(">")) { sb.append(lines[i].trim().removePrefix(">").trim()).append(" "); i++ }; out.add(Md.Quote(sb.toString().trim())) }
            t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ") -> { flush(); val items = mutableListOf<String>(); while (i < lines.size) { val x = lines[i].trim(); if (x.startsWith("- ") || x.startsWith("* ") || x.startsWith("• ")) { items.add(x.drop(2).trim()); i++ } else break }; out.add(Md.Bullet(items)) }
            NUM.containsMatchIn(t) -> { flush(); val items = mutableListOf<String>(); while (i < lines.size) { val x = lines[i].trim(); val mm = NUM.find(x); if (mm != null) { items.add(x.substring(mm.value.length).trim()); i++ } else break }; out.add(Md.Numbered(items)) }
            else -> { para.append(raw).append(" "); i++ }
        }
    }
    flush()
    return out
}

/** Inline **bold** / *italic* / _italic_ → AnnotatedString. */
private fun inline(text: String): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '*' && i + 1 < text.length && text[i + 1] == '*' -> {
                val end = text.indexOf("**", i + 2)
                if (end > 0) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }; i = end + 2 } else { append(c); i++ }
            }
            (c == '*' || c == '_') -> {
                val end = text.indexOf(c, i + 1)
                if (end > 0) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }; i = end + 1 } else { append(c); i++ }
            }
            else -> { append(c); i++ }
        }
    }
}

// ─────────────────────────── Reflection card ───────────────────────────

@Composable
private fun ReflectionFolded(text: String, onRevisit: () -> Unit) {
    Column {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                .border(1.dp, ML.gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR REFLECTION", style = ml(10, FontWeight.Bold, 1.8f), color = ML.kicker, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Check, null, tint = Color(0xFF16A34A), modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text("Saved", style = ml(10, FontWeight.Bold), color = Color(0xFF15803D))
            }
            Spacer(Modifier.height(10.dp))
            Text(if (text.isBlank()) "\u2014" else text, style = mlSerif(15, FontWeight.Normal), color = ML.bodyInk, lineHeight = 22.sp)
        }
        Spacer(Modifier.height(14.dp))
        // The module is sealed — changing anything is intentional, one quiet door.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .border(1.dp, ML.border, RoundedCornerShape(999.dp))
                    .clickable { onRevisit() }.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.MenuBook, null, tint = ML.secondary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("Revisit this module", style = ml(13, FontWeight.SemiBold), color = ML.secondary)
            }
        }
    }
}

@Composable
private fun ReflectionCard(value: String, onValue: (String) -> Unit, saved: Boolean, onSave: () -> Unit) {
    val canSave = value.trim().length >= 20
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, if (saved) ML.gold.copy(alpha = 0.5f) else ML.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("REFLECTION", style = ml(10, FontWeight.Bold, 1.8f), color = ML.kicker, modifier = Modifier.weight(1f))
            if (saved) { Icon(Icons.Filled.Check, null, tint = Color(0xFF16A34A), modifier = Modifier.size(11.dp)); Spacer(Modifier.width(3.dp)); Text("Saved", style = ml(10, FontWeight.Bold), color = Color(0xFF15803D)) }
        }
        Spacer(Modifier.height(8.dp))
        Text("What is God showing you today?", style = mlSerif(16, FontWeight.Medium, italic = true), color = ML.navy)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ML.surface).border(1.dp, ML.border, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (value.isEmpty()) Text("Write it down while it's fresh…", style = ml(13), color = ML.secondary.copy(alpha = 0.7f))
            BasicTextField(value, onValue, textStyle = ml(13).copy(color = ML.navy), cursorBrush = SolidColor(ML.gold), modifier = Modifier.fillMaxWidth().heightInCompat())
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ML.gold.copy(alpha = 0.08f)).border(1.dp, ML.gold.copy(alpha = 0.27f), RoundedCornerShape(16.dp))
                .clickable(enabled = canSave && !saved) { onSave() }.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (saved) "Reflection saved" else "Save reflection", style = ml(12, FontWeight.Bold), color = if (canSave) ML.goldDeep else ML.secondary) }
    }
}

private fun Modifier.heightInCompat(): Modifier = this.height(88.dp)
