// Nuru Assistant — a discipleship AI companion. Loads prior turns, sends the
// running transcript, appends the reply. Port of the iOS NuruAssistantView.
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

    Column(Modifier.fillMaxSize().background(Nuru.paper)) {
        ScreenHeader("Nuru Assistant", kicker = "Ask anything", onBack = onBack)
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.screen)) {
                Text("Hi — I'm here to help with Scripture, your pathway and prayer. What's on your heart?", style = NuruType.body, color = Nuru.ink600)
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(messages.size) { i -> Bubble(messages[i]) }
            }
        }
        Row(Modifier.fillMaxWidth().background(Nuru.white).padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it }, placeholder = { Text("Ask Nuru…") }, shape = RoundedCornerShape(Radii.pill), modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.sm))
            PrimaryButton("Ask", loading = busy, enabled = draft.isNotBlank(), modifier = Modifier.width(84.dp), onClick = {
                if (!busy) {
                    val q = draft.trim()
                    messages.add(AssistantMessage("user", q))
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
            })
        }
    }
}

@Composable
private fun Bubble(m: AssistantMessage) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
                .background(if (mine) Nuru.myBubble else Nuru.white).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            Text(m.text, style = NuruType.body, color = Nuru.ink)
        }
    }
}
