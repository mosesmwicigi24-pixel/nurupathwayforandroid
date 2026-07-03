// Your Calling — spiritual-gifts profile (top gifts, personas, serving tracks)
// and the Likert assessment when none exists yet. Server scores the assessment.
// Port of the iOS GiftsView.
package org.nuruplace.member.feature.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.GiftAnswerInput
import org.nuruplace.member.data.net.GiftQuestionSet
import org.nuruplace.member.data.net.GiftSubmitBody
import org.nuruplace.member.data.net.MyGifts
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.util.UUID

@Composable
fun GiftsScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.myGifts() }) { gifts: MyGifts, reload ->
        var assessing by remember { mutableStateOf(false) }
        if (assessing) {
            GiftAssessment(onDone = { assessing = false; reload() })
        } else {
            Column(Modifier.fillMaxSize().background(Nuru.paper)) {
                ScreenHeader("Your Calling", kicker = "Spiritual gifts", onBack = onBack)
                if (gifts.assessment == null) {
                    Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                        NuruCard {
                            Text("Discover how God has wired you", style = NuruType.cardTitle, color = Nuru.ink)
                            Spacer(Modifier.height(Spacing.sm))
                            Text("A short assessment surfaces your top spiritual gifts and where you might serve.", style = NuruType.body, color = Nuru.ink600)
                        }
                        PrimaryButton("Take the assessment", onClick = { assessing = true })
                    }
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        item { Kicker("Top gifts"); Text(gifts.assessment.topGifts.joinToString(" · ") { it.replaceFirstChar { c -> c.uppercase() } }, style = NuruType.title, color = Nuru.ink) }
                        items(gifts.personas, key = { it.giftKey }) { p ->
                            NuruCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    p.emoji?.let { Text(it, style = NuruType.title); Spacer(Modifier.size(Spacing.sm)) }
                                    Column {
                                        Text(p.personaName, style = NuruType.cardTitle, color = Nuru.ink)
                                        p.tagline?.let { Text(it, style = NuruType.caption, color = Nuru.goldLo) }
                                    }
                                }
                                Spacer(Modifier.height(Spacing.xs))
                                Text(p.summary, style = NuruType.body, color = Nuru.ink600)
                            }
                        }
                        if (gifts.suggestedTracks.isNotEmpty()) {
                            item { Kicker("Where you might serve") }
                            items(gifts.suggestedTracks, key = { it.trackKey }) { t ->
                                NuruCard {
                                    Text(t.title, style = NuruType.rowTitle, color = Nuru.ink)
                                    Text(t.description, style = NuruType.caption, color = Nuru.ink600)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GiftAssessment(onDone: () -> Unit) {
    AsyncContent(load = { Net.client.api.giftQuestions() }) { set: GiftQuestionSet, _ ->
        val scope = rememberCoroutineScope()
        val answers = remember { mutableStateMapOf<String, Int>() }
        var busy by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Gifts assessment", kicker = "Rate each 1–5", onBack = onDone)
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(set.data, key = { it.questionId }) { q ->
                    NuruCard {
                        Text(q.prompt, style = NuruType.body, color = Nuru.ink)
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            (1..5).forEach { n ->
                                val on = answers[q.questionId] == n
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(if (on) Nuru.navyDeep else Nuru.surface)
                                        .clickable { answers[q.questionId] = n },
                                    contentAlignment = Alignment.Center,
                                ) { Text("$n", style = NuruType.heading, color = if (on) Nuru.onNavy else Nuru.ink) }
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(Spacing.screen)) {
                PrimaryButton(
                    label = "Submit (${answers.size}/${set.data.size})",
                    loading = busy,
                    enabled = answers.size == set.data.size && set.data.isNotEmpty(),
                    onClick = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    Net.client.api.submitGifts(GiftSubmitBody(UUID.randomUUID().toString(), set.setId, answers.map { GiftAnswerInput(it.key, it.value) }))
                                    onDone()
                                } catch (_: Exception) {} finally { busy = false }
                            }
                        }
                    },
                )
            }
        }
    }
}
