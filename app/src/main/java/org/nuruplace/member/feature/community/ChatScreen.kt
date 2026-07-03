// Chat — inbox (my conversations + discover spaces) and a thread (messages +
// composer). Real data; mine bubbles right/green, others left/white. Port of the
// iOS ChatView / ChatThreadView (read-only reactions/voice for now).
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ChatConversation
import org.nuruplace.member.data.net.ChatInbox
import org.nuruplace.member.data.net.ChatMessage
import org.nuruplace.member.data.net.ChatThreadDetail
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.SendMessageBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.relTime
import java.util.UUID

@Composable
fun ChatInboxScreen(onBack: () -> Unit, onOpenThread: (String) -> Unit, onNewMessage: () -> Unit = {}) {
    AsyncContent(load = { Net.client.api.chatInbox() }) { inbox: ChatInbox, _ ->
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Messages", kicker = "Community", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item {
                    NuruCard(modifier = Modifier.clickable { onNewMessage() }) {
                        Text("✏️  New message", style = NuruType.cardCta, color = Nuru.goldLo)
                    }
                }
                items(inbox.conversations, key = { it.conversationId }) { c -> ConversationRow(c, onOpenThread) }
                if (inbox.discoverSpaces.isNotEmpty()) {
                    item { Spacer(Modifier.size(Spacing.md)); Kicker("Discover spaces") }
                    items(inbox.discoverSpaces, key = { it.conversationId }) { s ->
                        NuruCard(modifier = Modifier.clickable { onOpenThread(s.conversationId) }) {
                            Text(s.title ?: "Space", style = NuruType.rowTitle, color = Nuru.ink)
                            s.topic?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                            Text("${s.memberCount} members", style = NuruType.micro, color = Nuru.ink400)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: ChatConversation, onOpen: (String) -> Unit) {
    NuruCard(modifier = Modifier.clickable { onOpen(c.conversationId) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title ?: (if (c.kind == "dm") "Direct message" else "Conversation"), style = NuruType.rowTitle, color = Nuru.ink)
                c.lastBody?.let { Text(it, style = NuruType.caption, color = Nuru.ink600, maxLines = 1) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(relTime(c.lastAt), style = NuruType.micro, color = Nuru.ink400)
                if (c.unread > 0) {
                    Spacer(Modifier.size(4.dp))
                    Box(Modifier.size(20.dp).clip(CircleShape).background(Nuru.gold), contentAlignment = Alignment.Center) {
                        Text("${c.unread}", style = NuruType.micro, color = Nuru.onNavy)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatThreadScreen(conversationId: String, onBack: () -> Unit) {
    LaunchedEffect(conversationId) { runCatching { Net.client.api.markChatRead(conversationId) } }
    AsyncContent(key = conversationId, load = { Net.client.api.chatConversation(conversationId) }) { thread: ChatThreadDetail, reload ->
        val scope = rememberCoroutineScope()
        var draft by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(thread.messages.size) {
            if (thread.messages.isNotEmpty()) listState.animateScrollToItem(thread.messages.size - 1)
        }
        fun toggleReact(messageId: String) {
            scope.launch { try { Net.client.api.toggleChatReaction(messageId, org.nuruplace.member.data.net.ReactBody("❤️")); reload() } catch (_: Exception) {} }
        }
        Column(Modifier.fillMaxSize().background(Nuru.chatPaper)) {
            ScreenHeader(thread.title ?: "Conversation", kicker = "${thread.memberCount} members", onBack = onBack)
            if (!thread.joined && thread.isPublic) {
                Box(Modifier.fillMaxWidth().padding(Spacing.md)) {
                    PrimaryButton("Join this space", loading = busy, onClick = {
                        if (!busy) { busy = true; scope.launch { try { Net.client.api.joinChatSpace(conversationId); reload() } catch (_: Exception) {} finally { busy = false } } }
                    })
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(thread.messages, key = { it.messageId }) { m -> MessageBubble(m, onReact = { toggleReact(m.messageId) }) }
            }
            Row(Modifier.fillMaxWidth().background(Nuru.white).padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(draft, { draft = it }, placeholder = { Text("Message…") }, shape = RoundedCornerShape(Radii.pill), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.sm))
                PrimaryButton("Send", loading = busy, enabled = draft.isNotBlank(), modifier = Modifier.width(88.dp), onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try {
                                Net.client.api.sendChatMessage(conversationId, SendMessageBody(UUID.randomUUID().toString(), draft.trim(), "text", UUID.randomUUID().toString()))
                                draft = ""; reload()
                            } catch (_: Exception) {} finally { busy = false }
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessage, onReact: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(16.dp))
                .background(if (m.mine) Nuru.myBubble else Nuru.white)
                .clickable { onReact() }
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            if (!m.mine) Text(m.authorName, style = NuruType.micro, color = Nuru.goldLo, fontWeight = FontWeight.Bold)
            Text(if (m.msgType == "text") m.body else "🎤 ${m.msgType}", style = NuruType.body, color = Nuru.ink)
            if (m.reactions.isNotEmpty()) {
                Text(m.reactions.joinToString(" ") { "${it.emoji}${it.count}" }, style = NuruType.micro, color = Nuru.ink600)
            }
            Text(relTime(m.createdAt), style = NuruType.micro, color = Nuru.ink400, modifier = Modifier.align(Alignment.End))
        }
    }
}

/** New-message directory — the congregation members the caller may DM. Tapping
 *  one creates (or returns) the 1:1 conversation and opens it. */
@Composable
fun NewMessageScreen(onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    AsyncContent(key = query, load = { Net.client.api.chatPeople(query.ifBlank { null }).people }) { people, _ ->
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("New message", kicker = "Community", onBack = onBack)
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search members…") }, singleLine = true, shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen))
            Spacer(Modifier.size(Spacing.sm))
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(people, key = { it.userId }) { person ->
                    NuruCard(modifier = Modifier.clickable {
                        scope.launch {
                            val id = runCatching { Net.client.api.createDm(org.nuruplace.member.data.net.DmBody(person.userId)).conversationId }.getOrNull()
                            if (!id.isNullOrBlank()) onOpenThread(id)
                        }
                    }) {
                        Text(person.fullName, style = NuruType.rowTitle, color = Nuru.ink)
                        person.role?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                    }
                }
            }
        }
    }
}
