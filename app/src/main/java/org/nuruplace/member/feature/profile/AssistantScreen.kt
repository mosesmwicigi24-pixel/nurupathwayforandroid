// Nuru Assistant — a discipleship AI companion. Loads prior turns, sends the
// running transcript, appends the reply. Port of the iOS NuruAssistantView.
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.AssistantChatBody
import org.nuruplace.member.data.net.AssistantMessage
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun AssistantScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<AssistantMessage>() }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val h = runCatching { Net.client.api.assistantHistory().messages }.getOrNull()
        if (h != null) { messages.clear(); messages.addAll(h) }
    }

    fun send(q: String) {
        if (busy || q.isBlank()) return
        messages.add(AssistantMessage("user", q.trim()))
        draft = ""; busy = true
        scope.launch {
            try {
                val reply = Net.client.api.assistantChat(AssistantChatBody(messages.toList())).reply
                messages.add(AssistantMessage("assistant", reply))
            } catch (_: Exception) {
                messages.add(AssistantMessage("assistant", "Sorry — I couldn't reach the assistant just now."))
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).imePadding()) {
        NuruHeader(onBack)
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Hi, I'm Nuru ✨ your AI companion. I can help with Scripture, your pathway and prayer. What's on your heart?", style = NuruType.body, color = Nuru.ink600)
                SuggestionChips(onPick = ::send)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(messages.size) { i -> Bubble(messages[i]) }
            }
        }
        Row(Modifier.fillMaxWidth().background(Nuru.white).padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it }, placeholder = { Text("Ask Nuru…") }, shape = RoundedCornerShape(Radii.pill), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.sm))
            PrimaryButton("Ask", loading = busy, enabled = draft.isNotBlank(), modifier = Modifier.width(84.dp), onClick = { send(draft) })
        }
    }
}

private val NuruPurple = androidx.compose.ui.graphics.Color(0xFF7C3AED)
private val NuruGreen = androidx.compose.ui.graphics.Color(0xFF10B981)
private val NuruOrb = androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFFA78BFA), NuruPurple, androidx.compose.ui.graphics.Color(0xFF2A1259)))
private val NuruHeaderGradient = androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF2A1259), Nuru.navyDeep, androidx.compose.ui.graphics.Color(0xFF053F30)))

@Composable
private fun NuruHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(NuruHeaderGradient)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .padding(horizontal = Spacing.md).padding(top = Spacing.lg, bottom = Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nuru.onNavy)
        }
        androidx.compose.foundation.layout.Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(Radii.control)).background(NuruOrb),
            contentAlignment = Alignment.Center,
        ) { Text("✨", style = NuruType.title) }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nuru", style = NuruType.cardTitle, color = Nuru.onNavy)
                Spacer(Modifier.width(Spacing.xs))
                androidx.compose.foundation.layout.Box(Modifier.clip(RoundedCornerShape(Radii.pill)).background(NuruGreen).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text("AI", style = NuruType.micro, color = Nuru.navy)
                }
            }
            Text("Online · ready when you are", style = NuruType.micro, color = Nuru.onNavyDim)
        }
    }
}

private data class Suggestion(val label: String, val color: androidx.compose.ui.graphics.Color)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChips(onPick: (String) -> Unit) {
    val suggestions = listOf(
        Suggestion("Summarize my week", Nuru.gold),
        Suggestion("Draft an encouragement", NuruPurple),
        Suggestion("Find a prayer to pray", NuruGreen),
        Suggestion("Plan my quiet time", Nuru.info),
    )
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        suggestions.forEach { s ->
            androidx.compose.foundation.layout.Box(
                Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.white)
                    .then(Modifier.androidx_border(s.color))
                    .androidx_click { onPick(s.label) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(s.label, style = NuruType.micro, color = Nuru.navy) }
        }
    }
}

private fun Modifier.androidx_border(color: androidx.compose.ui.graphics.Color) =
    this.border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(Radii.pill))

private fun Modifier.androidx_click(onClick: () -> Unit) =
    this.clickable { onClick() }

@Composable
private fun Bubble(m: AssistantMessage) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) {
            androidx.compose.foundation.layout.Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape).background(NuruOrb), contentAlignment = Alignment.Center) {
                Text("✨", style = NuruType.caption)
            }
            Spacer(Modifier.width(Spacing.sm))
        }
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (mine) Nuru.navy else Nuru.white)
                .then(if (mine) Modifier else Modifier.androidx_border(Nuru.border))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Text(m.text, style = NuruType.body, color = if (mine) Nuru.onNavy else Nuru.ink)
        }
    }
}
