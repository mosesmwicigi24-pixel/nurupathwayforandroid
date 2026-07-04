// Your Calling — spiritual-gifts profile (top gifts, personas, serving tracks)
// and the one-at-a-time Likert assessment when none exists yet. Server scores it.
// Port of the iOS GiftsView (QuestionPane + ResultsPane).
package org.nuruplace.member.feature.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.GiftAnswerInput
import org.nuruplace.member.data.net.GiftPersona
import org.nuruplace.member.data.net.GiftQuestion
import org.nuruplace.member.data.net.GiftQuestionSet
import org.nuruplace.member.data.net.GiftSubmitBody
import org.nuruplace.member.data.net.MyGifts
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ServingTrack
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.GrowCreamHeader
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun GiftsScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.myGifts() }) { gifts: MyGifts, reload ->
        var assessing by remember { mutableStateOf(gifts.assessment == null) }

        Column(Modifier.fillMaxSize().background(GrowPal.paper)) {
            if (assessing) {
                GiftAssessment(
                    onDone = { assessing = false; reload() },
                    onBack = { if (gifts.assessment != null) assessing = false else onBack() },
                )
            } else if (gifts.assessment != null) {
                ResultsPane(gifts = gifts, onBack = onBack, onRetake = { assessing = true })
            } else {
                IntroPane(onBack = onBack, onStart = { assessing = true })
            }
        }
    }
}

// --- Back tile shared by the header rows -------------------------------------

@Composable
private fun BackTile(onBack: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GrowPal.white)
            .border(1.dp, GrowPal.border, RoundedCornerShape(12.dp))
            .clickable { onBack() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GrowPal.navy, modifier = Modifier.size(18.dp))
    }
}

// --- The one-at-a-time pager (iOS QuestionPane) ------------------------------

@Composable
private fun GiftAssessment(onDone: () -> Unit, onBack: () -> Unit) {
    AsyncContent(key = "gq", load = { Net.client.api.giftQuestions() }) { set: GiftQuestionSet, _ ->
        val scope = rememberCoroutineScope()
        val answers = remember { mutableStateMapOf<String, Int>() }
        var step by remember { mutableIntStateOf(0) }
        var busy by remember { mutableStateOf(false) }

        val submit: () -> Unit = {
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        Net.client.api.submitGifts(
                            GiftSubmitBody(
                                java.util.UUID.randomUUID().toString(),
                                set.setId,
                                answers.map { GiftAnswerInput(it.key, it.value) },
                            ),
                        )
                        onDone()
                    } catch (_: Exception) {
                    } finally {
                        busy = false
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            GrowCreamHeader(radius = 24.dp) {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BackTile(onBack)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (step < set.data.size) "QUESTION ${step + 1} OF ${set.data.size}" else "YOUR GIFTS",
                            style = gInter(11, FontWeight.Bold, 1.4f),
                            color = GrowPal.eyebrow,
                        )
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(40.dp)) // symmetry spacer
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Spiritual gifts", style = gSerif(24, FontWeight.SemiBold), color = GrowPal.navy)
                }
            }

            Column(Modifier.padding(20.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(GrowPal.white)
                        .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (step < set.data.size) {
                        val q: GiftQuestion = set.data[step]

                        // Progress dashes
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(set.data.size) { i ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(Capsule)
                                        .background(if (i <= step) GrowPal.gold else GrowPal.track),
                                )
                            }
                        }

                        Text(
                            q.prompt,
                            style = gSerif(20, FontWeight.Medium).copy(lineHeight = 27.sp),
                            color = GrowPal.navy,
                        )

                        // Likert options — strongest first
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "Strongly agree" to 5,
                                "Agree" to 4,
                                "Sometimes" to 3,
                                "Rarely" to 2,
                                "Not me" to 1,
                            ).forEach { (label, value) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(GrowPal.surface)
                                        .border(1.dp, GrowPal.border, RoundedCornerShape(16.dp))
                                        .clickable {
                                            answers[q.questionId] = value
                                            if (step < set.data.size - 1) step++ else submit()
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(label, style = gInter(13, FontWeight.Medium), color = GrowPal.navy)
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GrowPal.ink400, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Results summary (iOS ResultsPane) ---------------------------------------

@Composable
private fun ResultsPane(gifts: MyGifts, onBack: () -> Unit, onRetake: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(GrowPal.paper)) {
        GrowCreamHeader {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackTile(onBack)
                    Spacer(Modifier.weight(1f))
                    Text("YOUR GIFTS", style = gInter(11, FontWeight.Bold, 1.4f), color = GrowPal.eyebrow)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(40.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Your Calling", style = gSerif(24, FontWeight.SemiBold), color = GrowPal.navy)
            }
        }

        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopGiftsCard(gifts)

            gifts.personas.forEach { p -> PersonaCard(p) }

            if (gifts.suggestedTracks.isNotEmpty()) ServingTracksCard(gifts.suggestedTracks)

            // Retake
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GrowPal.white)
                    .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp))
                    .clickable { onRetake() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Retake assessment", style = gInter(13, FontWeight.SemiBold), color = GrowPal.navy)
            }
        }
    }
}

@Composable
private fun TopGiftsCard(gifts: MyGifts) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GrowPal.white)
            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(GrowPal.gold.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GrowPal.gold, modifier = Modifier.size(22.dp))
        }
        Text("YOUR TOP GIFTS", style = gInter(10, FontWeight.Bold, 1.8f), color = GrowPal.goldLo)
        gifts.assessment?.personaSummary?.let {
            Text(it, style = gInter(12), color = GrowPal.ink600, textAlign = TextAlign.Center)
        }

        // GiftBars — top 3 scores
        val bars = gifts.assessment?.scores?.entries?.sortedByDescending { it.value }?.take(3) ?: emptyList()
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            bars.forEachIndexed { i, e ->
                val pct = (e.value * 100).toInt().coerceIn(0, 100)
                val col = listOf(Color(0xFF6366F1), Color(0xFFDC2626), GrowPal.gold)[i % 3]
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.key.replaceFirstChar { it.uppercase() }, style = gInter(13, FontWeight.SemiBold), color = GrowPal.navy)
                        Spacer(Modifier.weight(1f))
                        Text("$pct%", style = gInter(11), color = GrowPal.ink600)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(Capsule).background(GrowPal.track)) {
                        Box(Modifier.fillMaxWidth(fraction = pct / 100f).height(8.dp).clip(Capsule).background(col))
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonaCard(p: GiftPersona) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GrowPal.white)
            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.emoji?.let { Text(it, fontSize = 20.sp) }
            Text(p.personaName.ifBlank { p.title }, style = gSerif(18, FontWeight.SemiBold), color = GrowPal.navy)
        }
        p.tagline?.let { Text(it, style = gInter(12, FontWeight.SemiBold), color = GrowPal.gold) }
        Text(p.summary, style = gInter(13).copy(lineHeight = 18.sp), color = GrowPal.ink600)
    }
}

@Composable
private fun ServingTracksCard(tracks: List<ServingTrack>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GrowPal.white)
            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("WHERE YOU MIGHT SERVE", style = gInter(10, FontWeight.Bold, 1.8f), color = GrowPal.goldLo)
        tracks.forEach { t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GrowPal.surface)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(GrowPal.gold),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = GrowPal.navy, modifier = Modifier.size(13.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = gInter(12, FontWeight.Medium), color = GrowPal.navy)
                    if (t.description.isNotBlank()) Text(t.description, style = gInter(11), color = GrowPal.ink600)
                }
            }
        }
    }
}

// --- Intro (only reached after cancel when no assessment exists) -------------

@Composable
private fun IntroPane(onBack: () -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(GrowPal.paper)) {
        GrowCreamHeader {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BackTile(onBack)
                    Spacer(Modifier.weight(1f))
                    Text("YOUR GIFTS", style = gInter(11, FontWeight.Bold, 1.4f), color = GrowPal.eyebrow)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(40.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Your Calling", style = gSerif(24, FontWeight.SemiBold), color = GrowPal.navy)
            }
        }

        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(GrowPal.white)
                    .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Discover how God has wired you", style = gSerif(18, FontWeight.SemiBold), color = GrowPal.navy)
                Text(
                    "A short assessment surfaces your top spiritual gifts and where you might serve.",
                    style = gInter(13).copy(lineHeight = 18.sp),
                    color = GrowPal.ink600,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GrowPal.goldGrad)
                    .clickable { onStart() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Take the assessment", style = gInter(13, FontWeight.SemiBold), color = GrowPal.navy)
            }
        }
    }
}
