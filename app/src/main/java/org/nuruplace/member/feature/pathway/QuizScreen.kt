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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
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
import org.nuruplace.member.ui.components.GrowCreamHeader
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
    onPassed: (() -> Unit)? = null,   // level exam: route to the level-complete ceremony
) {
    AsyncContent(key = title, load = { loadQuestions() }) { questions, _ ->
        if (questions.isEmpty()) {
            EmptyQuiz(onDone)
        } else {
            QuizFlow(title, questions, submit, onDone, onPassed)
        }
    }
}

@Composable
private fun QuizFlow(
    title: String,
    questions: List<QuizQuestion>,
    submit: suspend (List<QuizAnswer>, String) -> QuizVerdict,
    onDone: () -> Unit,
    onPassed: (() -> Unit)?,
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
        // A passed level exam continues into the level-complete ceremony; a passed
        // module quiz (or manual review) just returns to the pathway.
        val onContinue = if (v.isPassed && onPassed != null) onPassed else onDone
        ResultScreen(v, onDone = onContinue, onRetry = {
            verdict = null; error = null; values.clear(); checks.clear(); idx = 0; mutationId = newId()
        })
        return
    }

    val q = questions[idx]
    Column(
        Modifier.fillMaxSize().background(Nuru.coolPaper).imePadding(),
    ) {
        GrowCreamHeader {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(top = Spacing.lg, bottom = Spacing.base),
            ) {
                Kicker(title)
                // Gold progress dots — active dot widened, completed gold (Figma).
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    questions.indices.forEach { i ->
                        Box(
                            Modifier.height(7.dp).width(if (i == idx) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(Radii.pill))
                                .background(if (i <= idx) Nuru.gold else Nuru.navy.copy(alpha = 0.18f)),
                        )
                    }
                }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            Text("Question ${idx + 1} of ${questions.size}", style = NuruType.kicker, color = Nuru.ink400)
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
    when {
        v.isPassed || v.requiresManualReview -> PassResult(v, onDone)
        else -> FailResult(v, onDone, onRetry)
    }
}

/** Pass / manual-review ceremony — dark ground, concentric gold rings + medal,
 *  a big gold score, and a gold "Continue Pathway" CTA (Figma pass result). */
@Composable
private fun PassResult(v: QuizVerdict, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Nuru.ceremonyGradient).padding(Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(170.dp).clip(CircleShape).border(1.dp, Nuru.gold.copy(alpha = 0.18f), CircleShape))
            Box(Modifier.size(138.dp).clip(CircleShape).border(1.dp, Nuru.gold.copy(alpha = 0.33f), CircleShape))
            Box(
                Modifier.size(110.dp).clip(CircleShape).background(Nuru.gold.copy(alpha = 0.09f))
                    .border(2.dp, Nuru.gold.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(if (v.requiresManualReview) "✍️" else "🏅", style = NuruType.display) }
        }
        Spacer(Modifier.height(Spacing.xl))
        if (!v.requiresManualReview) {
            Text("${v.score}%", style = NuruType.display, color = Nuru.gold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(
            if (v.requiresManualReview) "Submitted for review" else "Module Passed",
            style = NuruType.title, color = Nuru.onNavy, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (v.requiresManualReview) "A mentor will review your written answers." else "Excellent work — this module is now complete.",
            style = NuruType.body, color = Nuru.onNavyDim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Box(Modifier.fillMaxWidth()) { PrimaryButton("Continue Pathway", onClick = onDone) }
    }
}

/** Fail — light ground, review encouragement + Review/Retry (Figma fail result). */
@Composable
private fun FailResult(v: QuizVerdict, onDone: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Nuru.coolPaper).padding(Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(100.dp).clip(CircleShape).background(Nuru.navy.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
            Text("📖", style = NuruType.display)
        }
        Spacer(Modifier.height(Spacing.lg))
        Text("${v.score}%", style = NuruType.display, color = Nuru.ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.xs))
        Text("Almost there", style = NuruType.title, color = Nuru.ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "You need ${v.passMark}% to pass. Take a moment to review the lesson — you've got this.",
            style = NuruType.body, color = Nuru.ink600, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Box(Modifier.fillMaxWidth()) { PrimaryButton("Review lesson", onClick = onDone) }
        Spacer(Modifier.height(Spacing.sm))
        Box(Modifier.clickable { onRetry() }.padding(Spacing.md)) {
            Text("Retry quiz", style = NuruType.cardCta, color = Nuru.navy)
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

// The quiz/exam submit's client_mutation_id — the backend requires a real UUID
// (client_mutation_id: z.string().uuid()); a "mut-…" string fails validation and
// the whole submit is rejected ("Request body failed validation"). Matches iOS
// (UUID().uuidString) and keeps replays idempotent (§2.1/§3.6).
private fun newId(): String = java.util.UUID.randomUUID().toString()
