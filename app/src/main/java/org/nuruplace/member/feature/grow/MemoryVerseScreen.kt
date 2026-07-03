// Memory verses — the member's set with per-verse mastery. A type-from-memory
// practice scores the attempt locally (word overlap) and posts it; the SERVER
// decides mastery (≥90%) and keeps the best score. Port of the iOS MemoryVerseView.
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.nuruplace.member.data.net.MemoryVerseRow
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PracticeBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

/** Word-overlap match the member sees while practicing; the server re-derives mastery. */
private fun matchPct(target: String, attempt: String): Int {
    fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9\\s]"), "").split(Regex("\\s+")).filter { it.isNotBlank() }
    val want = norm(target)
    if (want.isEmpty()) return 0
    val got = norm(attempt).toSet()
    val hit = want.count { got.contains(it) }
    return (hit * 100 / want.size)
}

@Composable
fun MemoryVerseScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.memoryVerses().data }) { verses: List<MemoryVerseRow>, reload ->
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Memory verses", kicker = "Hide His Word", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(verses, key = { it.memoryVerseId }) { v -> VerseCard(v, onPracticed = reload) }
            }
        }
    }
}

@Composable
private fun VerseCard(v: MemoryVerseRow, onPracticed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var practicing by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pct = matchPct(v.verseText, attempt)

    NuruCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.reference, style = NuruType.rowTitle, color = Nuru.goldLo, modifier = Modifier.weight(1f))
            if (v.isMastered) {
                Box(Modifier.background(Nuru.successBg, RoundedCornerShape(Radii.pill)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Mastered", style = NuruType.micro, color = Nuru.successText, fontWeight = FontWeight.Bold)
                }
            } else v.weekNumber?.let {
                Text("Week $it", style = NuruType.micro, color = Nuru.ink400)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(v.verseText, style = NuruType.bodyLg, color = Nuru.ink)

        if (practicing) {
            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = attempt,
                onValueChange = { attempt = it },
                label = { Text("Type it from memory") },
                minLines = 2,
                shape = RoundedCornerShape(Radii.control),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xs))
            Text("$pct% match${if (pct >= 90) " · mastered!" else ""}", style = NuruType.micro, color = if (pct >= 90) Nuru.success else Nuru.ink600)
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton(
                label = "Save practice",
                loading = busy,
                enabled = attempt.isNotBlank(),
                onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try {
                                Net.client.api.practiceVerse(PracticeBody(v.memoryVerseId, pct))
                                practicing = false; attempt = ""
                                onPracticed()
                            } catch (_: Exception) {
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
            )
        } else {
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(if (v.isMastered) "Practice again" else "Practice", onClick = { practicing = true })
        }
    }
}
