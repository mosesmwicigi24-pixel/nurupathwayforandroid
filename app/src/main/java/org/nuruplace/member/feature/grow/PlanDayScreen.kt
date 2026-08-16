// Plan-day screen — the FRESH Figma DayReader ported from iOS `PlanDayView`
// (ReadingPlansView.swift 743–1024) + `PLSegmentRow` (ReadingPlanCards.swift
// 423–516). Navy-gradient header (back · "DAY N" · day title · gold
// progress), a scripture-style content card, the day's real segments as
// highlighted rows (tap → onOpenSegment), a server-backed reflection
// textarea (GET pre-fills; POST upserts; button flips to "Update"), and a
// sticky gold "Mark day complete" bar that celebrates with a real
// rocket-and-burst fireworks show (`PLFireworksBurst`, iOS
// `FireworksCelebration` parity). Server-authoritative gating/scoring
// (§1.1) — this screen only reflects what the API records.
package org.nuruplace.member.feature.grow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.font.FontWeight.Companion.Medium
import androidx.compose.ui.text.font.FontWeight.Companion.Normal
import androidx.compose.ui.text.font.FontWeight.Companion.SemiBold
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nuruplace.member.R
import org.nuruplace.member.data.net.CompleteDayBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PlanSegment
import org.nuruplace.member.data.net.ReadingPlanDay
import org.nuruplace.member.data.net.ReadingPlanDetail
import org.nuruplace.member.data.net.SaveReflectionBody
import org.nuruplace.member.ui.theme.Spacing
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Reading-plan DAY reader. Loads the plan detail, isolates [dayNumber], and
 * renders the header/scripture/segments/reflection stack over a pinned complete
 * bar. Segment taps bubble up via [onOpenSegment] (the index into the day's
 * segment list). The gold CTA marks the day complete server-side, then fires a
 * confetti burst; once complete it reads "Day complete 🎉" and taps [onBack].
 */
@Composable
fun PlanDayScreen(
    planId: String,
    dayNumber: Int,
    onBack: () -> Unit,
    onOpenPart: (part: String, index: Int) -> Unit = { _, _ -> },
    onTalkItOver: () -> Unit = {},
    onPlanComplete: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    var day by remember { mutableStateOf<ReadingPlanDay?>(null) }
    var segments by remember { mutableStateOf<List<PlanSegment>>(emptyList()) }
    val completedSegments = remember { mutableStateListOf<String>() }
    var dayCompleted by remember { mutableStateOf(false) }
    var justDone by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // Reflection state (server-backed; GET pre-fills, POST upserts).
    var reflectionText by remember { mutableStateOf("") }
    var hasSavedReflection by remember { mutableStateOf(false) }
    var reflectionSaving by remember { mutableStateOf(false) }
    var reflectionJustSaved by remember { mutableStateOf(false) }

    // Load the plan + isolate this day, then pre-fill the reflection.
    LaunchedEffect(planId, dayNumber) {
        val detail: ReadingPlanDetail? = runCatching { Net.client.api.plan(planId) }.getOrNull()
        val d = detail?.days?.firstOrNull { it.dayNumber == dayNumber }
        day = d
        val segs = d?.segments ?: emptyList()
        segments = segs
        completedSegments.clear()
        completedSegments.addAll(segs.filter { it.completed }.map { it.segmentId })
        dayCompleted = d?.completed == true

        val existing = runCatching { Net.client.api.dayReflection(planId, dayNumber) }.getOrNull()?.data?.body
        if (!existing.isNullOrEmpty()) {
            hasSavedReflection = true
            if (reflectionText.isEmpty()) reflectionText = existing
        }
    }
    // A part finished in its reader ticks its hub row the moment we return.
    LaunchedEffect(Unit) {
        PlanProgressBus.finished.collect { if (it !in completedSegments) completedSegments.add(it) }
    }
    // The offline-sync race: the day unlocks server-side the instant its LAST
    // segment's completion lands, but this screen would otherwise still wait
    // on an explicit "Seal the day" tap. Trust that segment's own
    // authoritative ack directly — "Continue the plan" works immediately,
    // with no extra tap and no race against a re-fetch.
    LaunchedEffect(planId, dayNumber) {
        PlanProgressBus.dayUnlocked.collect { ack ->
            if (ack.dayNumber == dayNumber && (ack.planId == null || ack.planId == planId)) {
                dayCompleted = true
            }
        }
    }

    val reference = day?.reference ?: ""
    val title = day?.title ?: "Day $dayNumber"
    val progress =
        if (dayCompleted) 1f
        else if (segments.isEmpty()) 0f
        else completedSegments.size.toFloat() / segments.size.toFloat()

    // First not-yet-completed segment → gets the "Start" pill.
    val nextId =
        if (dayCompleted) null
        else segments.firstOrNull { it.segmentId !in completedSegments }?.segmentId

    // The day's parts + the one still to do — shared by the hub rows AND the
    // footer CTA, so the gold button can only carry you into the work.
    val parts = hubParts(segments)
    val doneOf: (HubPart) -> Boolean = { p -> p.segs.all { it.segmentId in completedSegments || it.completed == true } }
    val nextPart = parts.firstOrNull { !doneOf(it) }

    Box(Modifier.fillMaxSize().background(PL.cream)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                DayHeader(dayNumber = dayNumber, reference = reference, title = title, progress = progress, onBack = onBack)
                // THE DAY HUB — the day distilled into a story arc: Watch/Listen →
                // The Word → Respond → Talk it Over (iOS PlanDayView parity). Each
                // part reads on its own focused page and ticks here on return.
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "TODAY'S JOURNEY · ${parts.size} PART${if (parts.size == 1) "" else "S"}",
                        style = plInter(11, Bold, 1.8f), color = PL.catText,
                    )
                    parts.forEach { p ->
                        HubRow(
                            part = p, done = doneOf(p), isNext = p === nextPart,
                            onClick = { if (p.tag == "talk") onTalkItOver() else onOpenPart(p.tag, p.firstIndex) },
                        )
                    }
                }
            }

            FooterBar(
                complete = dayCompleted || justDone,
                busy = busy,
                nextPartLabel = nextPart?.label,
                onOpenNext = {
                    nextPart?.let { p -> if (p.tag == "talk") onTalkItOver() else onOpenPart(p.tag, p.firstIndex) }
                },
                onComplete = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val ok = runCatching { Net.client.api.completePlanDay(planId, CompleteDayBody(dayNumber)) }.isSuccess
                            busy = false
                            if (ok) {
                                dayCompleted = true
                                justDone = true
                                // If this sealed the WHOLE plan, open the keepsake.
                                val allDone = runCatching { Net.client.api.plan(planId).days.all { it.completed == true } }.getOrDefault(false)
                                if (allDone) { delay(900); onPlanComplete() }
                                // The fireworks run ~5s, then retire (the completed
                                // button stays via dayCompleted — iOS parity).
                                delay(5300)
                                justDone = false
                            }
                        }
                    }
                },
                onBack = onBack,
            )
        }

        if (justDone) PLFireworksBurst()
    }
}

// MARK: navy header (fresh DayReader) — 788-829

@Composable
private fun DayHeader(
    dayNumber: Int,
    reference: String,
    title: String,
    progress: Float,
    onBack: () -> Unit,
) {
    val animatedProgress by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "dayProgress")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(PL.navyGrad)
            // Scaffold already insets content below the status bar; just a small top pad.
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("DAY $dayNumber", style = plInter(10, Bold, 1.8f), color = PL.gold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(36.dp))
        }
        Text(
            reference,
            style = plInter(11),
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            title,
            style = plSerif(23, SemiBold, -0.46f),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        // Thin 4dp progress bar — track white@20% capsule + gold fill.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.2f)),
        ) {
            if (animatedProgress > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(PL.goldProgressGrad),
                )
            }
        }
    }
}

// MARK: scripture / day content card — 833-852

@Composable
private fun VerseBlock(reference: String, content: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PL.highlight)
            .border(1.dp, PL.gold.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.MenuBook, null, tint = PL.refInk, modifier = Modifier.size(12.dp))
            Text(
                reference.uppercase(),
                style = plInter(11, Bold, 1.4f),
                color = PL.refInk,
            )
        }
        if (!content.isNullOrEmpty()) {
            Text(
                content,
                style = plSerif(17, Normal, -0.17f, italic = true)
                    .copy(lineHeight = org.nuruplace.member.ui.theme.scaledLineHeight(25)),
                color = PL.navy,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// MARK: segments (real day flow → onOpenSegment) — 856-881

@Composable
private fun SegmentsCard(
    segments: List<PlanSegment>,
    completedIds: List<String>,
    nextId: String?,
    onOpenSegment: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, PL.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        PLOverline("WORK THROUGH TODAY", color = PL.goldDeep, kerning = 1.4f)
        Column(
            Modifier.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            segments.forEachIndexed { index, seg ->
                PLSegmentRow(
                    segment = seg,
                    done = seg.segmentId in completedIds,
                    isNext = seg.segmentId == nextId,
                    onTap = { onOpenSegment(index) },
                )
            }
        }
    }
}

// MARK: plan-day segment row — ReadingPlanCards.swift 423-459

@Composable
private fun PLSegmentRow(
    segment: PlanSegment,
    done: Boolean,
    isNext: Boolean,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isNext) PL.highlight else PL.surface)
            .border(1.dp, if (isNext) PL.gold.copy(alpha = 0.27f) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 36dp icon tile.
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (done) PL.gold else Color.White)
                .then(if (done) Modifier else Modifier.border(1.dp, PL.border, RoundedCornerShape(12.dp))),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            } else {
                Icon(segmentIcon(segment.kind), null, tint = PL.gold, modifier = Modifier.size(15.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                segment.title,
                style = plInter(12, SemiBold),
                color = PL.navy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                segment.kind.replaceFirstChar { it.uppercase() },
                style = plInter(11),
                color = PL.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isNext) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PL.gold)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("Start", style = plInter(9, Bold), color = PL.navy)
            }
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PL.chev, modifier = Modifier.size(14.dp))
        }
    }
}

/** kind → glyph, mirroring iOS `segmentIcon` (883-892). */
private fun segmentIcon(kind: String): ImageVector = when (kind.lowercase()) {
    "video" -> Icons.Filled.PlayArrow
    "reading" -> Icons.Filled.MenuBook
    "devotional" -> Icons.Filled.WbSunny
    "talk" -> Icons.Filled.ChatBubbleOutline
    "scripture" -> Icons.Filled.FormatQuote
    else -> Icons.Filled.MenuBook
}

// MARK: reflection — the Figma textarea, backed by the real endpoint — 898-962

@Composable
private fun ReflectionCard(
    text: String,
    onTextChange: (String) -> Unit,
    hasSaved: Boolean,
    saving: Boolean,
    justSaved: Boolean,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(1.dp, PL.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PLOverline("REFLECTION", color = PL.goldDeep, kerning = 1.4f)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = justSaved, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Check, null, tint = Color(0xFF16A34A), modifier = Modifier.size(11.dp))
                    Text("Saved", style = plInter(10, Bold), color = Color(0xFF15803D))
                }
            }
        }
        Text(
            "What is God showing you today?",
            style = plSerif(16, Medium, -0.16f, italic = true),
            color = PL.navy,
            modifier = Modifier.padding(top = 8.dp),
        )
        // Multiline field — placeholder overlays an empty field.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PL.surface)
                .border(1.dp, PL.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (text.isEmpty()) {
                Text("Write it down while it's fresh…", style = plInter(13), color = PL.ink3)
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = plInter(13).copy(color = PL.navy),
                cursorBrush = SolidColor(PL.gold),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Save / Update button — disabled (dimmed) when empty.
        val empty = text.trim().isEmpty()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PL.gold.copy(alpha = 0.08f))
                .border(1.dp, PL.gold.copy(alpha = 0.27f), RoundedCornerShape(16.dp))
                .alpha(if (empty || saving) 0.5f else 1f)
                .clickable(enabled = !empty && !saving, onClick = onSave)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Edit, null, tint = PL.refInk, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (hasSaved) "Update" else "Save reflection",
                style = plInter(12, Bold),
                color = PL.refInk,
            )
        }
    }
}

// MARK: sticky footer — mark complete → confetti → tap to go back — 966-1023

@Composable
private fun FooterBar(
    complete: Boolean,
    busy: Boolean,
    /** The part still to do, if any — while one remains, the gold button is the
     *  way INTO it. A day is finished by DOING it, not by declaring it, so the
     *  button no longer offers to seal a day nobody has walked. (The server
     *  refuses that too: 409 CONTENT_INCOMPLETE.) */
    nextPartLabel: String?,
    onOpenNext: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(PL.border))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = Spacing.tabBarSpace)
                .shadow(10.dp, RoundedCornerShape(16.dp), spotColor = PL.gold.copy(alpha = 0.45f), ambientColor = PL.gold.copy(alpha = 0.45f))
                .clip(RoundedCornerShape(16.dp))
                .background(PL.goldCtaGrad)
                .height(48.dp)
                .clickable(enabled = !busy) {
                    when {
                        complete -> onBack()
                        nextPartLabel != null -> onOpenNext()
                        else -> onComplete()
                    }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                complete -> Text("Day complete 🎉", style = plInter(14, Bold), color = PL.navy)
                nextPartLabel != null -> {
                    Text("Continue · $nextPartLabel", style = plInter(14, Bold), color = PL.navy)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = PL.navy, modifier = Modifier.size(15.dp))
                }
                else -> {
                    Icon(Icons.Filled.Check, null, tint = PL.navy, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Seal the day", style = plInter(14, Bold), color = PL.navy)
                }
            }
        }
    }
}

// MARK: fireworks burst — iOS FireworksCelebration parity (ReadingPlansView.swift)

/** Golds the brand leans on + white + one warm accent — the same palette as
 *  iOS `FireworksCelebration`, legible on the navy day header and cream body alike. */
private object FireworkColors {
    val gold = Color(0xFFC89B3C)
    val goldLight = Color(0xFFE0B85E)
    val cream = Color(0xFFFFF4C7)
    val accent = Color(0xFFFB7185) // one accent spark color, used sparingly
    val palette = listOf(gold, gold, goldLight, goldLight, cream, Color.White, accent)
}

private const val SPARK_LIFE = 1.25f // seconds a spark stays visible once it bursts

/** A spark's fixed flight plan, resolved against its parent rocket's burst
 *  time + center each frame. */
private data class SparkSeed(
    val angle: Float,
    val reach: Float,      // radial travel, as a fraction of min(width, height)
    val size: Float,       // point diameter at the glowing head (dp)
    val color: Color,
    val lifeScale: Float,  // slight per-spark variance so the burst doesn't die all at once
)

private data class Rocket(
    val id: Int,
    val x: Float,          // 0..1, launch/burst x (kept fixed — real rockets fly straight up)
    val burstY: Float,     // 0..1 from the top — where the streak ends and the burst begins
    val t0: Float,         // launch time, seconds from celebration start
    val riseDuration: Float,
    val color: Color,
    val sparks: List<SparkSeed>,
)

/** 3–4 staggered rockets with 24–40 sparks apiece. */
private fun buildFireworks(): List<Rocket> {
    val count = Random.nextInt(3, 5) // 3..4
    var t0 = 0f
    val rockets = mutableListOf<Rocket>()
    repeat(count) { i ->
        val riseDuration = 0.45f + Random.nextFloat() * 0.17f
        val color = FireworkColors.palette.random()
        val sparkCount = Random.nextInt(24, 41)
        val sparks = (0 until sparkCount).map { s ->
            val angle = (s / sparkCount.toFloat()) * (2f * PI.toFloat()) + (Random.nextFloat() * 0.12f - 0.06f)
            SparkSeed(
                angle = angle,
                reach = 0.16f + Random.nextFloat() * 0.14f,
                size = 3f + Random.nextFloat() * 3.5f,
                color = FireworkColors.palette.random(),
                lifeScale = 0.85f + Random.nextFloat() * 0.3f,
            )
        }
        rockets.add(
            Rocket(
                id = i,
                x = 0.2f + Random.nextFloat() * 0.6f,       // middle 60% of the width
                burstY = 0.16f + Random.nextFloat() * 0.26f, // upper part of the screen
                t0 = t0, riseDuration = riseDuration, color = color, sparks = sparks,
            ),
        )
        // Stagger the next launch so its burst still has room to fade out
        // before the ~5s ceremony retires (justDone flips back after 5.3s).
        t0 += 0.8f + Random.nextFloat() * 0.25f
    }
    return rockets
}

/** True when the user has turned system animations off ("Remove animations"). */
@Composable
private fun rememberFireworksReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        val scale = android.provider.Settings.Global.getFloat(
            resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        )
        scale == 0f
    }
}

/**
 * The day-seal ceremony: 3–4 staggered rockets rise from the bottom on a
 * fading streak, then burst into two-dozen-plus radial sparks that arc
 * under a light gravity pull and leave their own fading trail behind — one
 * soft "pop" timed to each burst. A single Canvas + withFrameNanos loop
 * draws every rocket and spark (no per-particle composables) so this stays
 * 60fps-friendly. Reduce Motion swaps the whole thing for a static golden
 * glow and skips sound entirely.
 */
@Composable
private fun PLFireworksBurst() {
    val reduceMotion = rememberFireworksReduceMotion()

    if (reduceMotion) {
        // No rise, no burst, no sound — just a gentle golden presence that
        // confirms "something good just happened".
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(FireworkColors.cream.copy(alpha = 0.5f), FireworkColors.gold.copy(alpha = 0.18f), Color.Transparent),
                ),
            ),
        )
        return
    }

    val context = LocalContext.current
    val rockets = remember { buildFireworks() }
    val totalLife = remember(rockets) { (rockets.maxOfOrNull { it.t0 + it.riseDuration } ?: 0f) + SPARK_LIFE * 1.3f }
    var startNanos by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(rockets) {
        FireworksSound.prepareIfNeeded(context)
        startNanos = withFrameNanos { it }
        // One pop per burst, timed to the moment its sparks appear — sleeping
        // only the delta between bursts (not the absolute time).
        launch {
            var elapsed = 0f
            for (r in rockets) {
                val target = r.t0 + r.riseDuration
                val waitMs = ((target - elapsed) * 1000).toLong()
                if (waitMs > 0) delay(waitMs)
                elapsed = maxOf(elapsed, target)
                FireworksSound.pop(r.id)
            }
        }
        while (true) {
            val now = withFrameNanos { it }
            tick = (now - startNanos) / 1_000_000_000f
            if (tick > totalLife) break
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val t = tick
        val minDim = minOf(size.width, size.height)
        for (r in rockets) drawRocket(this, r, t, size.width, size.height, minDim)
    }
}

/** Draws one rocket: its rising streak, then (once burst) an ignition flash
 *  and its full spark field. */
private fun drawRocket(scope: androidx.compose.ui.graphics.drawscope.DrawScope, r: Rocket, t: Float, w: Float, h: Float, minDim: Float) {
    if (t < r.t0) return
    val burstTime = r.t0 + r.riseDuration

    if (t < burstTime) {
        drawRisingStreak(scope, r, t, burstTime, w, h)
        return
    }

    val bt = t - burstTime
    if (bt > SPARK_LIFE * 1.3f) return

    val center = Offset(r.x * w, r.burstY * h)

    // A quick bright flash at the moment of ignition — the "boom".
    if (bt < 0.12f) {
        val flashAlpha = 1f - bt / 0.12f
        scope.drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.85f * flashAlpha), r.color.copy(alpha = 0f)),
                center = center, radius = 46f,
            ),
            radius = 46f, center = center, blendMode = BlendMode.Plus,
        )
    }

    for (seed in r.sparks) drawSpark(scope, seed, center, minDim, bt)
}

/** The rising rocket: a short gradient streak fading to nothing at its
 *  tail, climbing from just under the screen to the burst point. */
private fun drawRisingStreak(scope: androidx.compose.ui.graphics.drawscope.DrawScope, r: Rocket, t: Float, burstTime: Float, w: Float, h: Float) {
    val p = ((t - r.t0) / r.riseDuration).coerceIn(0f, 1f)
    val launchY = 1.06f
    val headY = launchY + (r.burstY - launchY) * p
    val tailP = (p - 0.11f).coerceAtLeast(0f)
    val tailY = launchY + (r.burstY - launchY) * tailP
    val x = r.x * w

    val head = Offset(x, headY * h)
    val tail = Offset(x, tailY * h)
    scope.drawLine(
        brush = Brush.linearGradient(listOf(r.color.copy(alpha = 0f), r.color.copy(alpha = 0.95f)), start = tail, end = head),
        start = tail, end = head, strokeWidth = 3f, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
    )
    // A small bright ember at the very tip.
    scope.drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 2.5f, center = head, blendMode = BlendMode.Plus)
}

/** One radial spark: outward travel that decelerates, a slight downward
 *  gravity pull that grows with time (so late in life it visibly arcs), and
 *  a short fading trail (gradient line) behind the glowing head. */
private fun drawSpark(scope: androidx.compose.ui.graphics.drawscope.DrawScope, seed: SparkSeed, center: Offset, minDim: Float, bt: Float) {
    val life = SPARK_LIFE * seed.lifeScale
    if (bt < 0f || bt > life) return
    val lifeT = bt / life

    fun position(lt: Float): Offset {
        val clamped = lt.coerceIn(0f, 1f)
        val outEase = 1f - (1f - clamped).pow(2)      // fast start, decelerating outward push
        val radial = seed.reach * outEase
        val gravity = 0.16f * clamped * clamped         // slight pull, compounding late in life
        val dx = cos(seed.angle) * radial
        val dy = sin(seed.angle) * radial + gravity
        return Offset(center.x + dx * minDim, center.y + dy * minDim)
    }

    val fadeOut = if (lifeT > 0.7f) (1f - (lifeT - 0.7f) / 0.3f).coerceAtLeast(0f) else 1f
    if (fadeOut < 0.02f) return

    val head = position(lifeT)
    val tail = position(lifeT - 0.09f / life)

    scope.drawLine(
        brush = Brush.linearGradient(listOf(seed.color.copy(alpha = 0f), seed.color.copy(alpha = 0.8f * fadeOut)), start = tail, end = head),
        start = tail, end = head, strokeWidth = seed.size * 0.5f, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
    )
    // Glowing head — a soft radial fade reads as an additive spark rather
    // than a flat dot.
    val glowSize = seed.size * 2.2f
    scope.drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f * fadeOut), seed.color.copy(alpha = 0.55f * fadeOut), seed.color.copy(alpha = 0f)),
            center = head, radius = glowSize / 2f,
        ),
        radius = glowSize / 2f, center = head, blendMode = BlendMode.Plus,
    )
}

// MARK: firework "pop" sound (device-media-volume aware)

/**
 * Three short pop WAVs (res/raw), played one per burst via [SoundPool] —
 * loading decodes off the main thread, so a burst is never the first place
 * we touch disk I/O. `play()`'s left/right volumes are relative to the
 * current stream volume, so this always respects the device's media volume
 * rather than fighting it.
 */
private object FireworksSound {
    private var pool: SoundPool? = null
    private val soundIds = mutableListOf<Int>()
    private val loadedIds = mutableSetOf<Int>()
    @Volatile private var preparing = false

    fun prepareIfNeeded(context: Context) {
        if (preparing || pool != null) return
        preparing = true
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val built = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()
        built.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loadedIds.add(sampleId) }
        val appContext = context.applicationContext
        listOf(R.raw.pop1, R.raw.pop2, R.raw.pop3).forEach { res -> soundIds.add(built.load(appContext, res, 1)) }
        pool = built
    }

    /** Fire the pop for burst [index] (rotates through the three sounds so
     *  back-to-back bursts don't cut each other's tail off). */
    fun pop(index: Int) {
        val p = pool ?: return
        if (soundIds.isEmpty()) return
        val id = soundIds[index % soundIds.size]
        if (id in loadedIds) p.play(id, 0.5f, 0.5f, 1, 0, 1f)
    }
}

/** Entry to the shared "Talk it Over" conversation for this day (iOS parity). */
@Composable
private fun TalkItOverEntry(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PL.highlight)
            .border(1.dp, PL.gold.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(PL.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ChatBubbleOutline, null, tint = PL.goldDeep, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Talk it Over", style = plSerif(16, Medium), color = PL.navy)
            Text("Share what God is showing you with the family", style = plInter(12), color = PL.blurb)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PL.chev)
    }
}

// ── Day-hub story arc (iOS PlanDayView parity) ────────────────────────────────

internal data class HubPart(
    val id: String,
    val tag: String,          // "media" | "word" | "respond" | "talk"
    val label: String,
    val icon: ImageVector,
    val segs: List<PlanSegment>,
    val firstIndex: Int,
)

private fun rankHub(s: PlanSegment): Int = when (s.kind.lowercase()) {
    "video", "audio" -> 0
    "scripture" -> 1
    "talk" -> 3
    "reading" -> 5
    else -> if (s.title.lowercase().startsWith("pray")) 4 else 2
}

/** The day distilled into a story arc: media first (each its own row), then THE
 *  WORD (Scripture + teaching + Go Deeper), then RESPOND (prayer + reflection),
 *  then Talk it Over standalone. */
internal fun hubParts(segments: List<PlanSegment>): List<HubPart> {
    val sorted = segments.sortedWith(compareBy({ rankHub(it) }, { it.sort }))
    val parts = mutableListOf<HubPart>()
    sorted.forEachIndexed { i, s ->
        if (rankHub(s) == 0) {
            val audio = s.kind.lowercase() == "audio"
            parts += HubPart(s.segmentId, "media", if (audio) "Listen" else "Watch", Icons.Filled.PlayArrow, listOf(s), i)
        }
    }
    sorted.filter { rankHub(it) in listOf(1, 2, 5) }.let { word ->
        if (word.isNotEmpty()) parts += HubPart("word", "word", "The Word", Icons.Filled.MenuBook, word, sorted.indexOf(word.first()))
    }
    sorted.filter { rankHub(it) == 4 }.let { respond ->
        if (respond.isNotEmpty()) parts += HubPart("respond", "respond", "Respond", Icons.Filled.VolunteerActivism, respond, sorted.indexOf(respond.first()))
    }
    sorted.filter { rankHub(it) == 3 }.let { talk ->
        if (talk.isNotEmpty()) parts += HubPart("talk", "talk", "Talk it Over", Icons.Filled.ChatBubbleOutline, talk, sorted.indexOf(talk.first()))
    }
    return parts
}

private fun hubSub(part: HubPart, done: Boolean): String {
    if (done) return "Completed"
    return when (part.tag) {
        "media" -> if (part.label == "Listen") "Today's word + key takeaways" else "Begin with today's video"
        "word" -> part.segs.firstOrNull { it.kind.lowercase() == "scripture" }?.reference?.let { "$it · Scripture & teaching" } ?: "Scripture & teaching"
        "talk" -> "The family's conversation on today's word"
        else -> "Prayer · your reflection"
    }
}

@Composable
private fun HubRow(part: HubPart, done: Boolean, isNext: Boolean, onClick: () -> Unit) {
    val warm = done || isNext
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White)
            .border(1.dp, if (isNext) PL.gold.copy(alpha = 0.35f) else PL.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(999.dp))
                .background(if (warm) PL.gold.copy(alpha = 0.15f) else PL.blurb.copy(alpha = 0.07f))
                .border(1.dp, if (done) PL.gold.copy(alpha = 0.5f) else if (isNext) PL.gold.copy(alpha = 0.4f) else PL.border, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(part.icon, null, tint = if (warm) PL.goldDeep else PL.blurb, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(part.label, style = plInter(14, SemiBold), color = PL.navy)
            Text(hubSub(part, done), style = plInter(11), color = if (done) PL.goldDeep else PL.blurb, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            done -> Icon(Icons.Filled.CheckCircle, null, tint = PL.gold, modifier = Modifier.size(22.dp))
            isNext -> Box(Modifier.clip(RoundedCornerShape(999.dp)).background(PL.gold).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("Next", style = plInter(10, Bold), color = PL.navy)
            }
            else -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PL.chev, modifier = Modifier.size(20.dp))
        }
    }
}
