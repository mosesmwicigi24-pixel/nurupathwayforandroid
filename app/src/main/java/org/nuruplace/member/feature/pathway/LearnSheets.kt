// Living-curriculum dialogs (intelligence Phase 3) — port of iOS NuruLearnSheets.
//   • NuruCoachDialog — "Review with Nuru" after a failed quiz: a short review of
//     exactly what tripped this attempt, then straight back to the retry.
//   • ExplainDialog — the SAME lesson re-rendered (simple / Kiswahili / story).
// Warm stationery over navy, matching the Sunday Letter voice.
package org.nuruplace.member.feature.pathway

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

private val LSNavyTop = Color(0xFF0A1628)
private val LSNavyBottom = Color(0xFF081020)
private val LSGold = Color(0xFFE8CA6C)
private val LSPaper = Color(0xFFFFFDF6)
private val LSInkOnPaper = Color(0xFF2A3441)
private val LSBody = NuruType.rowTitle.copy(fontSize = 17.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal)

@Composable
fun NuruCoachDialog(moduleId: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(moduleId) {
        runCatching { Net.client.api.quizRemediation(moduleId) }
            .onSuccess { text = it.body }
            .onFailure { error = "Couldn't prepare your review — the lesson itself is still the best coach." }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LSNavyTop, LSNavyBottom)))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = LSGold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("REVIEW WITH NURU", style = NuruType.micro, color = LSGold, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Spacer(Modifier.weight(1f))
                    CloseDot(onDismiss)
                }
                Spacer(Modifier.height(16.dp))
                val body = text
                when {
                    body != null -> {
                        Text(
                            body, style = LSBody, color = LSInkOnPaper,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LSPaper).padding(20.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(LSGold, Color(0xFFB6862F))))
                                .clickable { onDismiss(); onRetry() }.padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("I'm ready — retry the quiz", style = NuruType.cardCta, color = Color(0xFF1E2A1F))
                        }
                    }
                    error != null -> Text(error!!, style = NuruType.body, color = Color.White.copy(alpha = 0.85f))
                    else -> LoadingRow("Nuru is looking at what tripped you…")
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
fun ExplainDialog(moduleId: String, initialStyle: String, onDismiss: () -> Unit) {
    var style by remember { mutableStateOf(initialStyle) }
    var text by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(moduleId, style) {
        text = null; failed = false
        runCatching { Net.client.api.explainLesson(moduleId, style) }
            .onSuccess { text = it.body }
            .onFailure { failed = true; ApiException.message(it) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LSNavyTop, LSNavyBottom)))) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("simple" to "Simple", "swahili" to "Kiswahili", "story" to "As a story").forEach { (key, label) ->
                        val active = style == key
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (active) LSGold else Color.White.copy(alpha = 0.10f))
                                .clickable { style = key }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(label, style = NuruType.micro, fontWeight = FontWeight.Bold,
                                 color = if (active) Color(0xFF1E2A1F) else Color.White.copy(alpha = 0.85f))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    CloseDot(onDismiss)
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    val body = text
                    when {
                        body != null -> Text(
                            body, style = LSBody, color = LSInkOnPaper,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LSPaper).padding(20.dp),
                        )
                        failed -> Text("Couldn't render this lesson right now.", style = NuruType.body, color = Color.White.copy(alpha = 0.85f))
                        else -> LoadingRow("Rendering the lesson…")
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
        }
    }
}

@Composable
private fun CloseDot(onDismiss: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(14.dp)) }
}

@Composable
private fun LoadingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        CircularProgressIndicator(color = LSGold, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = NuruType.body, color = Color.White.copy(alpha = 0.85f))
    }
}
