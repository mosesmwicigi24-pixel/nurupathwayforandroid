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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import org.nuruplace.member.ui.components.NuruModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.MemoryVerseRow
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PracticeBody
import org.nuruplace.member.data.net.ScoreBreakdown
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.GrowCreamHeader
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif
import org.nuruplace.member.ui.theme.scaledLineHeight
import org.nuruplace.member.ui.icons.Lucide

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


/** The Word score card's words — the server's own score (GET /me/scores/word,
 *  the one Home's "Your progress" shows; scoring is the server's, §1.1): its
 *  0–100 score, its band in the app's one score vocabulary ("Just beginning",
 *  "Growing", …) and its three parts, 0–100 on the wire, as 0..1 bars. The
 *  page named its own band ("Seedling") and, with no answer, drew a 0 and
 *  bars worked out from the verse list (Cycle 4 walk; iOS e950cbd). */
internal data class WordScoreWords(val score: Int, val band: String?, val consistency: Double, val memorization: Double, val breadth: Double)

internal fun wordScoreWords(b: ScoreBreakdown): WordScoreWords {
    fun part(key: String) = ((b.components[key] ?: 0.0) / 100.0).coerceIn(0.0, 1.0)
    return WordScoreWords(
        score = b.score.coerceIn(0, 100),
        band = b.band.trim().takeIf { it.isNotEmpty() },
        consistency = part("consistency"),
        memorization = part("memorization"),
        breadth = part("breadth"),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryVerseScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
                        Icon(Lucide.ArrowLeft, contentDescription = "Back", tint = GrowPal.navy, modifier = Modifier.size(18.dp))
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
                // The server's Word score, or no card until it answers — never a guess.
                word?.let { WordScoreCard(wordScoreWords(it)) }
                MilestoneCard(verses)
                if (verses.isNotEmpty()) ThisWeekCard(verses) { practicing = it }
                if (verses.isNotEmpty()) {
                    // A kicker is gold, Inter 11 bold (§8.1 rule 3; iOS cbc52c7 the same).
                    Text(
                        "YOUR VERSE LIBRARY",
                        style = org.nuruplace.member.ui.theme.NuruType.kicker,
                        color = org.nuruplace.member.ui.theme.Nuru.eyebrow,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                    verses.forEach { v -> LibraryVerse(v) { practicing = v } }
                }
            }
        }

        // ---- Practice sheet ----
        // "Save practice" waits for the server (EXPERIENCE.md §7.4 #2: no
        // success before the server): the sheet stays with progress on the
        // button and closes on the ack (then the list reloads); a failure stays
        // and says why above the button. It used to close first and drop any
        // failure, so a practice that never saved looked saved.
        practicing?.let { v ->
            var saving by remember(v.memoryVerseId) { mutableStateOf(false) }
            var saveError by remember(v.memoryVerseId) { mutableStateOf<String?>(null) }
            // Not dismissed mid-save: the outcome would have nowhere to show.
            // Fully open, never half: half-open, the keyboard covered the
            // "Save practice" button (and the line above it that says why).
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { it != SheetValue.Hidden || !saving },
            )
            NuruModalBottomSheet(onDismissRequest = { if (!saving) practicing = null }, sheetState = sheetState) {
                PracticeSheet(
                    v = v,
                    saving = saving,
                    error = saveError,
                    onClose = { if (!saving) practicing = null },
                    onSave = { pct ->
                        if (!saving) {
                            saving = true
                            saveError = null
                            scope.launch {
                                val r = runCatching { Net.client.api.practiceVerse(PracticeBody(v.memoryVerseId, pct)) }
                                val failure = r.exceptionOrNull()
                                if (failure is kotlin.coroutines.cancellation.CancellationException) throw failure
                                saving = false
                                if (failure == null) {
                                    practicing = null
                                    reload()
                                } else {
                                    saveError = "Couldn't save that. ${ApiException.state(failure, context).sentence}"
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WordScoreCard(w: WordScoreWords) {
    val score = w.score

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
                w.band?.let { Text(it, style = gSerif(18, FontWeight.SemiBold), color = GrowPal.ink) }
                Bar("Consistency", w.consistency)
                Bar("Memorization", w.memorization)
                Bar("Breadth", w.breadth)
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
            // A figure in a fixed ring keeps the everyday size (§9.6 #4).
            org.nuruplace.member.ui.components.CappedFontScale(1f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(score.toString(), style = gSerif(22, FontWeight.SemiBold), color = GrowPal.ink)
                    Text("/100", style = gInter(11, FontWeight.Medium), color = GrowPal.ink600)
                }
            }
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
            Icon(Lucide.Sparkles, contentDescription = null, tint = GrowPal.gold, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // A prompt's title is the card title, Fraunces 18 (§8.1 rule 3; iOS cbc52c7).
            Text(
                if (left == 1) "1 verse to your next milestone" else "$left verses to your next milestone",
                style = org.nuruplace.member.ui.theme.NuruType.cardTitle,
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
            style = gSerif(18, FontWeight.Medium).copy(lineHeight = scaledLineHeight(26)),
            color = GrowPal.navy,
        )
        Text(cur.reference, style = gInter(12, FontWeight.Bold), color = GrowPal.gold)
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(GrowPal.gold)
                .clickable { onPractice(cur) },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Lucide.Pencil, contentDescription = null, tint = GrowPal.navy, modifier = Modifier.size(14.dp))
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
            style = gInter(13).copy(lineHeight = scaledLineHeight(18)),
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
        Text(text, style = gInter(11, FontWeight.SemiBold), color = fg)
    }
}

@Composable
private fun PracticeSheet(v: MemoryVerseRow, saving: Boolean, error: String?, onClose: () -> Unit, onSave: (Int) -> Unit) {
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
                Icon(Lucide.X, contentDescription = "Close", tint = GrowPal.navy, modifier = Modifier.size(14.dp))
            }
        }
        Text(v.reference, style = gInter(11), color = GrowPal.ink600)

        // The whole box is the field (its decoration): before, the box was
        // 110dp tall and the field one line, so only the first line took a tap.
        BasicTextField(
            value = attempt,
            onValueChange = { attempt = it },
            textStyle = gInter(14).copy(color = GrowPal.ink),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(RoundedCornerShape(14.dp))
                        .background(GrowPal.surface).border(1.dp, GrowPal.border, RoundedCornerShape(14.dp)).padding(12.dp),
                ) {
                    if (attempt.isBlank()) {
                        Text("Type it from memory…", style = gInter(14), color = GrowPal.ink400)
                    }
                    field()
                }
            },
        )

        Box(Modifier.fillMaxWidth().height(8.dp).clip(Capsule).background(GrowPal.track)) {
            Box(
                Modifier.fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f)).height(8.dp)
                    .clip(Capsule).background(GrowPal.gold),
            )
        }
        Text(
            "$pct% match" + if (pct >= 90) " · mastered!" else "",
            style = gInter(11),
            color = if (pct >= 90) GrowPal.successText else GrowPal.ink600,
        )

        // Why the last save did not land (§4) — above the button that retries.
        error?.let { Text(it, style = gInter(12), color = Color(0xFFB91C1C)) }
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(GrowPal.goldGrad)
                .clickable(enabled = !saving) { onSave(pct) },
            contentAlignment = Alignment.Center,
        ) {
            if (saving) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Text("Save practice", style = gInter(15, FontWeight.SemiBold), color = Color.White)
        }
    }
}
