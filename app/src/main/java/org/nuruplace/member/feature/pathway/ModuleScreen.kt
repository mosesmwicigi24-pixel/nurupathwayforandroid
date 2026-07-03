// Module — the lesson (GET /modules/{id}) + its completion path: a non-quiz
// module finishes with an optional reflection + "Mark complete"; a quiz module
// routes to the graded quiz. Server owns completion + unlocking (§1.1). Port of
// the iOS ModuleView.
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    AsyncContent(key = moduleId, load = { Net.client.api.module(moduleId) }) { m: ModuleDetail ->
        val scope = rememberCoroutineScope()
        var reflection by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        Column(
            Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState()),
        ) {
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

            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                m.keyVerses?.takeIf { it.isNotEmpty() }?.let { verses ->
                    NuruCard {
                        Kicker("Key verses")
                        Spacer(Modifier.height(Spacing.sm))
                        verses.forEach { Text(it, style = NuruType.body, color = Nuru.ink) }
                    }
                }
                // Lesson (markdown rendered as plain text for now; rich markdown lands later).
                Text(m.lessonContent, style = NuruType.bodyLg, color = Nuru.ink)

                error?.let { Text(it, style = NuruType.caption, color = Nuru.danger) }

                if (m.requiresQuiz) {
                    PrimaryButton("Take the quiz (pass ${m.quizPassMark}%)", onClick = { onTakeQuiz(m.moduleId) })
                } else {
                    OutlinedTextField(
                        value = reflection,
                        onValueChange = { reflection = it },
                        label = { Text("Reflection (optional)") },
                        minLines = 3,
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
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}
