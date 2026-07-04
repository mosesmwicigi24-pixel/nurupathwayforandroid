// Memory verses — the member's set with per-verse mastery, a derived WORD SCORE
// dashboard, a milestone nudge, a "This week" hero, and the verse library. A
// type-from-memory practice scores the attempt locally (word overlap) and posts
// it; the SERVER decides mastery (≥90%) and keeps the best score. Native port of
// the iOS MemoryVerseView (cream header → word-score ring → milestone → this-week
// hero → library → practice sheet).
package org.nuruplace.member.feature.grow

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.MemoryVerseRow
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PracticeBody
import org.nuruplace.member.data.net.ScoreBreakdown
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.GrowCreamHeader
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif

private val Capsule = RoundedCornerShape(999.dp)

/** Word-overlap match the member sees while practicing; the server re-derives mastery. */
private fun matchPct(target: String, attempt: String): Int {
    fun norm(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    val want = norm(target)
    if (want.isEmpty()) return 0
    val got = norm(attempt).toSet()
    val hit = want.count { got.contains(it) }
    return (hit * 100 / want.size).coerceIn(0, 100)
}

/** Growth band label for a 0..100 word score (matches iOS). */
private fun bandName(score: Int): String = when {
    score < 25 -> "Seedling"
    score < 50 -> "Sprouting"
    score < 75 -> "Growing"
    else -> "Flourishing"
}

/**
 * Read a component value as a 0..1 fraction. The backend emits these as 0..100
 * integers (see scores/service.ts), so anything > 1 is treated as a percentage.
 * Tries lowercase then capitalized keys, then a local fallback (0..1).
 */
private fun ScoreBreakdown.frac(key: String, fallback: Double): Double {
    val raw = components[key]
        ?: components[key.replaceFirstChar { it.uppercase() }]
        ?: return fallback.coerceIn(0.0, 1.0)
    val v = if (raw > 1.0) raw / 100.0 else raw
    return v.coerceIn(0.0, 1.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryVerseScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var practicing by remember { mutableStateOf<MemoryVerseRow?>(null) }

    AsyncContent(
        load = {
            val verses = Net.client.api.memoryVerses().data
            val word = runCatching { Net.client.api.scoreDetail("word") }.getOrNull()
            verses to word
        },
    ) { (verses, word), reload ->
        Column(Modifier.fillMaxSize().background(GrowPal.paper)) {
            // ---- Cream header ----
            GrowCreamHeader {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(GrowPal.white)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GrowPal.navy, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("HIDE HIS WORD", style = gInter(11, FontWeight.Bold, 1.4f), color = GrowPal.eyebrow)
                        Text("Memory verses", style = gSerif(26, FontWeight.SemiBold), color = GrowPal.navy)
                    }
                }
            }

            // ---- Body ----
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WordScoreCard(word, verses)
                MilestoneCard(verses)
                if (verses.isNotEmpty()) ThisWeekCard(verses) { practicing = it }
                if (verses.isNotEmpty()) {
                    Text(
                        "YOUR VERSE LIBRARY",
                        style = gInter(10, FontWeight.SemiBold, 1.8f),
                        color = GrowPal.ink600,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                    verses.forEach { v -> LibraryVerse(v) { practicing = v } }
                }
            }
        }

        // ---- Practice sheet ----
        practicing?.let { v ->
            ModalBottomSheet(onDismissRequest = { practicing = null }) {
                PracticeSheet(
                    v = v,
                    onClose = { practicing = null },
                    onSave = { pct ->
                        scope.launch {
                            try {
                                Net.client.api.practiceVerse(PracticeBody(v.memoryVerseId, pct))
                                reload()
                            } catch (_: Exception) {
                            }
                        }
                        practicing = null
                    },
                )
            }
        }
    }
}

@Composable
private fun WordScoreCard(word: ScoreBreakdown?, verses: List<MemoryVerseRow>) {
    val score = (word?.score ?: 0).coerceIn(0, 100)

    // Local iOS-style fallbacks (0..1) when the endpoint is unavailable.
    val total = verses.size
    val mastered = verses.count { it.isMastered }
    val localConsistency = if (total == 0) 0.0 else mastered.toDouble() / total
    val localMemorization = if (total == 0) 0.0 else verses.sumOf { it.bestMatchPct }.toDouble() / total / 100.0
    val localBreadth = if (total == 0) 0.0 else (total / 10.0).coerceAtMost(1.0)

    val cons = word?.frac("consistency", localConsistency) ?: localConsistency
    val mem = word?.frac("memorization", localMemorization) ?: localMemorization
    val breadth = word?.frac("breadth", localBreadth) ?: localBreadth

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(GrowPal.white)
            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ScoreRing(score)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("WORD SCORE", style = gInter(11, FontWeight.Bold, 1.4f), color = GrowPal.gold)
                }
                Text(bandName(score), style = gSerif(18, FontWeight.SemiBold), color = GrowPal.ink)
                Bar("Consistency", cons)
                Bar("Memorization", mem)
                Bar("Breadth", breadth)
            }
        }
    }
}

@Composable
private fun ScoreRing(score: Int) {
    Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color = GrowPal.goldGlow,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(4.dp.toPx()),
            )
            drawArc(
                color = GrowPal.gold,
                startAngle = -90f,
                sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(score.toString(), style = gSerif(24, FontWeight.SemiBold), color = GrowPal.ink)
            Text("/100", style = gInter(10, FontWeight.Medium), color = GrowPal.ink600)
        }
    }
}

@Composable
private fun Bar(label: String, value: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = gInter(11, FontWeight.Medium), color = GrowPal.ink600, modifier = Modifier.width(84.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(Capsule).background(GrowPal.track)) {
            Box(
                Modifier.fillMaxWidth(fraction = value.toFloat().coerceIn(0f, 1f)).height(6.dp)
                    .clip(Capsule).background(GrowPal.gold),
            )
        }
    }
}

@Composable
private fun MilestoneCard(verses: List<MemoryVerseRow>) {
    val mastered = verses.count { it.isMastered }
    val next = ((mastered / 10) + 1) * 10
    val left = next - mastered
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(GrowPal.goldTint)
            .border(1.dp, GrowPal.goldLo, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(GrowPal.goldGlow), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GrowPal.gold, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (left == 1) "1 verse to your next milestone" else "$left verses to your next milestone",
                style = gInter(13, FontWeight.SemiBold),
                color = GrowPal.ink,
            )
            Text(
                "Master $left more to reach $next. Keep hiding His Word in your heart.",
                style = gInter(13),
                color = GrowPal.ink600,
            )
        }
    }
}

@Composable
private fun ThisWeekCard(verses: List<MemoryVerseRow>, onPractice: (MemoryVerseRow) -> Unit) {
    val cur = verses.firstOrNull { it.weekNumber != null } ?: verses.first()
    val day = java.time.LocalDate.now().dayOfWeek.value.coerceIn(1, 7)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(GrowPal.white)
            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("THIS WEEK", style = gInter(11, FontWeight.Bold, 1.4f), color = GrowPal.overline)
            Spacer(Modifier.weight(1f))
            Text("Day $day of 7", style = gInter(11), color = GrowPal.ink600)
        }
        Text(
            "“" + cur.verseText + "”",
            style = gSerif(20, FontWeight.Medium).copy(lineHeight = 28.sp),
            color = GrowPal.navy,
        )
        Text(cur.reference, style = gInter(12, FontWeight.Bold), color = GrowPal.gold)
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(GrowPal.gold)
                .clickable { onPractice(cur) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = GrowPal.navy, modifier = Modifier.size(14.dp))
                Text("Practice", style = gInter(13, FontWeight.Bold), color = GrowPal.navy)
            }
        }
    }
}

@Composable
private fun LibraryVerse(v: MemoryVerseRow, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GrowPal.white)
            .border(1.dp, GrowPal.border, RoundedCornerShape(16.dp)).clickable { onClick() }.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                v.reference,
                style = gInter(12, FontWeight.Bold),
                color = GrowPal.gold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            StatusChip(v)
        }
        Text(
            "“" + v.verseText + "”",
            style = gInter(13).copy(lineHeight = 18.sp),
            color = GrowPal.navy,
        )
    }
}

@Composable
private fun StatusChip(v: MemoryVerseRow) {
    if (v.isMastered) {
        Chip("Mastered", GrowPal.successBg, GrowPal.successText)
    } else {
        Chip(v.weekNumber?.let { "Week $it" } ?: "Learning", GrowPal.surface, GrowPal.ink600)
    }
}

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Box(Modifier.clip(Capsule).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, style = gInter(10, FontWeight.SemiBold), color = fg)
    }
}

@Composable
private fun PracticeSheet(v: MemoryVerseRow, onClose: () -> Unit, onSave: (Int) -> Unit) {
    var attempt by remember { mutableStateOf("") }
    val pct = matchPct(v.verseText, attempt)

    Column(
        Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Practice", style = gSerif(18, FontWeight.Medium), color = GrowPal.navy)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(GrowPal.surface).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = GrowPal.navy, modifier = Modifier.size(15.dp))
            }
        }
        Text(v.reference, style = gInter(11), color = GrowPal.ink600)

        Box(
            Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(RoundedCornerShape(14.dp))
                .background(GrowPal.surface).border(1.dp, GrowPal.border, RoundedCornerShape(14.dp)).padding(12.dp),
        ) {
            if (attempt.isBlank()) {
                Text("Type it from memory…", style = gInter(14), color = GrowPal.ink400)
            }
            BasicTextField(
                value = attempt,
                onValueChange = { attempt = it },
                textStyle = gInter(14).copy(color = GrowPal.ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(Modifier.fillMaxWidth().height(8.dp).clip(Capsule).background(GrowPal.track)) {
            Box(
                Modifier.fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f)).height(8.dp)
                    .clip(Capsule).background(GrowPal.gold),
            )
        }
        Text(
            "$pct% match" + if (pct >= 90) " · mastered!" else "",
            style = gInter(10),
            color = if (pct >= 90) GrowPal.successText else GrowPal.ink600,
        )

        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(GrowPal.goldGrad)
                .clickable { onSave(pct) },
            contentAlignment = Alignment.Center,
        ) {
            Text("Save practice", style = gInter(15, FontWeight.SemiBold), color = Color.White)
        }
    }
}
