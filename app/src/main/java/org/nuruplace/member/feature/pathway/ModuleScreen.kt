// Module — the lesson (GET /modules/{id}) + its completion path. When the server
// pre-splits the body into `content_pages`, the lesson renders as a swipeable
// paged reader with a top progress bar and a page indicator; a 30s engagement
// heartbeat (reading seconds + resume page) POSTs deltas to /modules/{id}/
// engagement (fire-and-forget — telemetry must never block reading), and the
// reader resumes on the last-read page. A non-quiz module finishes with an
// optional reflection + "Mark complete"; a quiz module routes to the graded quiz.
// Server owns completion + unlocking (§1.1). Port of the iOS ModuleView.
package org.nuruplace.member.feature.pathway

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.CompleteBody
import org.nuruplace.member.data.net.ModuleDetail
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun ModuleScreen(
    moduleId: String,
    onBack: () -> Unit,
    onTakeQuiz: (String) -> Unit,
    onCompleted: () -> Unit,
) {
    AsyncContent(key = moduleId, load = { Net.client.api.module(moduleId) }) { m: ModuleDetail, _ ->
        val scope = rememberCoroutineScope()
        var reflection by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        val pages = m.pages
        val pagerState = rememberPagerState(pageCount = { pages.size })

        // --- Engagement: reading seconds + resume page (server accumulates) ---
        var readingDelta by remember { mutableIntStateOf(0) }   // secs since last flush
        var lastSentPage by remember { mutableIntStateOf(0) }

        fun engagementBody(reading: Int?, page: Int?): JsonObject = buildJsonObject {
            reading?.let { if (it > 0) put("reading_seconds", JsonPrimitive(it.coerceAtMost(600))) }
            page?.let { put("last_page", JsonPrimitive(it)) }
        }

        // Drain observed reading time (+ a changed page) into ONE fire-and-forget
        // POST. Uses the app-level bgScope so the final flush survives screen exit.
        fun flushEngagement() {
            val reading = readingDelta
            val page = (pagerState.currentPage + 1).takeIf { it != lastSentPage }
            if (reading <= 0 && page == null) return
            readingDelta = 0
            page?.let { lastSentPage = it }
            Net.client.bgScope.launch {
                runCatching { Net.client.api.reportModuleEngagement(m.moduleId, engagementBody(reading.takeIf { it > 0 }, page)) }
            }
        }

        // Resume on the last-read page.
        LaunchedEffect(m.moduleId) {
            val e = runCatching { Net.client.api.moduleEngagement(m.moduleId) }.getOrNull()
            if (e != null && e.lastPage in 1..pages.size) {
                lastSentPage = e.lastPage
                pagerState.scrollToPage(e.lastPage - 1)
            }
        }
        // Reading clock: +1s/tick while on screen; heartbeat every 30s.
        LaunchedEffect(m.moduleId) {
            var tick = 0
            while (true) {
                delay(1_000)
                readingDelta++
                if (++tick >= 30) { tick = 0; flushEngagement() }
            }
        }
        // Final beat on leave.
        DisposableEffect(m.moduleId) { onDispose { flushEngagement() } }

        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            // Whole-module reading progress bar.
            if (pages.size > 1) {
                LinearProgressIndicator(
                    progress = { (pagerState.currentPage + 1f) / pages.size },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Nuru.gold,
                    trackColor = Nuru.goldTint,
                )
            }
            Column(
                Modifier.fillMaxWidth().background(Nuru.heroGradient)
                    .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nuru.onNavy)
                    }
                    Kicker("Level ${m.levelNumber} · Module ${m.moduleSequenceNumber}")
                }
                Text(m.title, style = NuruType.title, color = Nuru.onNavy, modifier = Modifier.padding(start = Spacing.xs))
            }

            // Lesson body: a swipeable pager when the server paginated, else one
            // scrolling page. Each page scrolls independently.
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { index ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                    if (index == 0) {
                        m.keyVerses?.takeIf { it.isNotEmpty() }?.let { verses ->
                            NuruCard {
                                Kicker("Key verses")
                                Spacer(Modifier.height(Spacing.sm))
                                verses.forEach { Text(it, style = NuruType.body, color = Nuru.ink) }
                            }
                        }
                    }
                    Text(pages[index], style = NuruType.bodyLg, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.xxl))
                }
            }

            // Footer: page indicator (multi-page) + completion / quiz controls.
            Column(
                Modifier.fillMaxWidth().background(Nuru.white).padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (pages.size > 1) {
                    Text("Page ${pagerState.currentPage + 1} of ${pages.size}", style = NuruType.micro, color = Nuru.ink400)
                }
                error?.let { Text(it, style = NuruType.caption, color = Nuru.danger) }
                if (m.requiresQuiz) {
                    PrimaryButton("Take the quiz (pass ${m.quizPassMark}%)", onClick = { flushEngagement(); onTakeQuiz(m.moduleId) })
                } else {
                    OutlinedTextField(
                        value = reflection,
                        onValueChange = { reflection = it },
                        label = { Text("Reflection (optional)") },
                        minLines = 2,
                        shape = RoundedCornerShape(Radii.control),
                        keyboardOptions = KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton(
                        label = "Mark complete",
                        loading = busy,
                        onClick = {
                            if (!busy) {
                                busy = true; error = null
                                scope.launch {
                                    try {
                                        flushEngagement()
                                        Net.client.api.completeModule(
                                            m.moduleId,
                                            CompleteBody(reflectionText = reflection.trim().ifBlank { null }),
                                        )
                                        onCompleted()
                                    } catch (e: Exception) {
                                        error = ApiException.message(e)
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
