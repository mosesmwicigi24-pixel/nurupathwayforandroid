// Quiz / Level-exam flow — server-assembled, server-scored (§1.3/§3.7). One
// question at a time across all five kinds (single, checkbox, short, paragraph,
// linear scale); answers are collected client-side and the VERDICT is entirely
// the server's. Shared by module quizzes and level exams via the two lambdas.
// Port of the iOS QuizView / LevelExamView. clientMutationId keeps the attempt
// idempotent on replay (§2.1/§3.6).
package org.nuruplace.member.feature.pathway

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.QKind
import org.nuruplace.member.data.net.QuizAnswer
import org.nuruplace.member.data.net.QuizQuestion
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

/** The server's verdict, unified from QuizResult / ExamResult. */
data class QuizVerdict(
    val score: Int,
    val passMark: Int,
    val isPassed: Boolean,
    val requiresManualReview: Boolean,
)

@Composable
fun QuizScreen(
    title: String,
    loadQuestions: suspend () -> List<QuizQuestion>,
    submit: suspend (answers: List<QuizAnswer>, clientMutationId: String) -> QuizVerdict,
    onDone: () -> Unit,
) {
    AsyncContent(key = title, load = { loadQuestions() }) { questions, _ ->
        if (questions.isEmpty()) {
            EmptyQuiz(onDone)
        } else {
            QuizFlow(title, questions, submit, onDone)
        }
    }
}

@Composable
private fun QuizFlow(
    title: String,
    questions: List<QuizQuestion>,
    submit: suspend (List<QuizAnswer>, String) -> QuizVerdict,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val values = remember { mutableStateMapOf<String, String>() }          // single / scale / text
    val checks = remember { mutableStateMapOf<String, Set<String>>() }     // checkbox
    var idx by remember { mutableIntStateOf(0) }
    var mutationId by remember { mutableStateOf(newId()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var verdict by remember { mutableStateOf<QuizVerdict?>(null) }

    fun answered(q: QuizQuestion): Boolean {
        if (q.required == false) return true
        return when (q.kind) {
            QKind.CHECKBOX -> checks[q.questionId]?.isNotEmpty() == true
            QKind.SHORT, QKind.PARAGRAPH -> values[q.questionId]?.isNotBlank() == true
            else -> values[q.questionId]?.isNotEmpty() == true
        }
    }

    fun doSubmit() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val answers = questions.map { q ->
                    val given = if (q.kind == QKind.CHECKBOX) {
                        JsonArray((checks[q.questionId] ?: emptySet()).map { JsonPrimitive(it) }).toString()
                    } else {
                        values[q.questionId] ?: ""
                    }
                    QuizAnswer(q.questionId, given)
                }
                verdict = submit(answers, mutationId)
            } catch (e: Exception) {
                error = ApiException.message(e)
            } finally {
                busy = false
            }
        }
    }

    verdict?.let { v ->
        ResultScreen(v, onDone = onDone, onRetry = {
            verdict = null; error = null; values.clear(); checks.clear(); idx = 0; mutationId = newId()
        })
        return
    }

    val q = questions[idx]
    Column(
        Modifier.fillMaxSize().background(Nuru.coolPaper),
    ) {
        Column(
            Modifier.fillMaxWidth().background(Nuru.navy).padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        ) {
            Kicker(title)
            Spacer(Modifier.height(Spacing.xs))
            Text("Question ${idx + 1} of ${questions.size}", style = NuruType.caption, color = Nuru.onNavyDim)
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            Text(q.questionText, style = NuruType.cardTitle, color = Nuru.ink)
            when (q.kind) {
                QKind.CHECKBOX -> q.choices().forEach { c ->
                    val on = checks[q.questionId]?.contains(c.id) == true
                    OptionRow(c.text, selected = on, multi = true) {
                        val cur = checks[q.questionId]?.toMutableSet() ?: mutableSetOf()
                        if (on) cur.remove(c.id) else cur.add(c.id)
                        checks[q.questionId] = cur
                    }
                }
                QKind.SINGLE -> q.choices().forEach { c ->
                    OptionRow(c.text, selected = values[q.questionId] == c.id, multi = false) {
                        values[q.questionId] = c.id
                    }
                }
                QKind.SCALE -> {
                    val s = q.scale()
                    if (s != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            (s.min..s.max).forEach { n ->
                                val on = values[q.questionId] == n.toString()
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape)
                                        .background(if (on) Nuru.navyDeep else Nuru.white)
                                        .border(1.dp, Nuru.border, CircleShape)
                                        .clickable { values[q.questionId] = n.toString() },
                                    contentAlignment = Alignment.Center,
                                ) { Text("$n", style = NuruType.heading, color = if (on) Nuru.onNavy else Nuru.ink) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.minLabel ?: "", style = NuruType.micro, color = Nuru.ink400)
                            Text(s.maxLabel ?: "", style = NuruType.micro, color = Nuru.ink400)
                        }
                    }
                }
                QKind.SHORT, QKind.PARAGRAPH -> OutlinedTextField(
                    value = values[q.questionId] ?: "",
                    onValueChange = { values[q.questionId] = it },
                    minLines = if (q.kind == QKind.PARAGRAPH) 4 else 1,
                    shape = RoundedCornerShape(Radii.control),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { Text(it, style = NuruType.caption, color = Nuru.danger) }
        }

        // Nav bar
        Row(
            Modifier.fillMaxWidth().background(Nuru.white).padding(Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (idx > 0) {
                Box(Modifier.weight(1f)) {
                    PrimaryButton("Back", onClick = { idx-- })
                }
            }
            Box(Modifier.weight(1f)) {
                if (idx < questions.size - 1) {
                    PrimaryButton("Next", enabled = answered(q), onClick = { if (answered(q)) idx++ })
                } else {
                    PrimaryButton("Submit", enabled = answered(q), loading = busy, onClick = { doSubmit() })
                }
            }
        }
    }
}

@Composable
private fun OptionRow(text: String, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control))
            .background(if (selected) Nuru.tintBlue else Nuru.white)
            .border(1.dp, if (selected) Nuru.navyDeep else Nuru.border, RoundedCornerShape(Radii.control))
            .clickable { onClick() }
            .padding(Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp)
                .clip(if (multi) RoundedCornerShape(6.dp) else CircleShape)
                .background(if (selected) Nuru.navyDeep else Nuru.white)
                .border(1.dp, if (selected) Nuru.navyDeep else Nuru.ink300, if (multi) RoundedCornerShape(6.dp) else CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = Nuru.onNavy, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.size(Spacing.md))
        Text(text, style = NuruType.body, color = Nuru.ink)
    }
}

@Composable
private fun ResultScreen(v: QuizVerdict, onDone: () -> Unit, onRetry: () -> Unit) {
    val passed = v.isPassed || v.requiresManualReview
    Column(
        Modifier.fillMaxSize().background(if (passed) Nuru.ceremonyGradient else Nuru.navyGradient)
            .padding(Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (v.requiresManualReview) "✍️" else if (v.isPassed) "🎉" else "🔁", style = NuruType.display)
        Spacer(Modifier.height(Spacing.base))
        Text(
            when {
                v.requiresManualReview -> "Submitted for review"
                v.isPassed -> "Passed!"
                else -> "Not quite yet"
            },
            style = NuruType.title, color = Nuru.onNavy, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (v.requiresManualReview) "A mentor will review your written answers."
            else "You scored ${v.score}% · pass mark ${v.passMark}%",
            style = NuruType.body, color = Nuru.onNavyDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        if (!v.isPassed && !v.requiresManualReview) {
            PrimaryButton("Try again", onClick = onRetry)
            Spacer(Modifier.height(Spacing.sm))
        }
        Box(Modifier.clickable { onDone() }.padding(Spacing.md)) {
            Text("Done", style = NuruType.cardCta, color = Nuru.gold)
        }
    }
}

@Composable
private fun EmptyQuiz(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Nuru.paper).padding(Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("This assessment has no questions yet.", style = NuruType.body, color = Nuru.ink600, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.md))
        Box(Modifier.clickable { onDone() }.padding(Spacing.md)) {
            Text("Go back", style = NuruType.cardCta, color = Nuru.gold)
        }
    }
}

// A stable-ish unique id without Math.random/UUID import noise.
private var idCounter = 1000
private fun newId(): String = "mut-${idCounter++}-${System.nanoTime()}"
