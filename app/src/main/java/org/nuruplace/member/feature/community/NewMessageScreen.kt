// Compose screen for starting a new conversation — ported from the iOS Nuru Connect
// "New Message" sheet. Searches the directory of people (chatPeople) and opens a DM
// on tap (createDm → onOpenThread). Shares the CHAT palette + primitives with the hub
// and thread via ChatShared.kt (same package, no import needed).
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ChatPerson
import org.nuruplace.member.data.net.ConnectionRequestRow
import org.nuruplace.member.data.net.ConnectionRow
import org.nuruplace.member.data.net.DmBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RequestConnectionBody
import org.nuruplace.member.ui.components.AsyncContent
import java.util.UUID

private val Capsule = RoundedCornerShape(999.dp)

/** Directory + where the caller stands with each person (Chat Redesign C3a). */
private data class NewMessageData(
    val people: List<ChatPerson>,
    val connections: List<ConnectionRow>,
    val incoming: List<ConnectionRequestRow>,
    val outgoing: List<ConnectionRequestRow>,
)

@Composable
fun NewMessageScreen(onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var busyUserId by remember { mutableStateOf<String?>(null) }
    var consentPromptFor by remember { mutableStateOf<ChatPerson?>(null) }

    Column(Modifier.fillMaxSize().background(CHAT.paper).imePadding()) {
        // ── Cream header ──
        ChatCreamHeaderBox {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CHAT.white)
                            .border(1.dp, CHAT.border, CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CHAT.navy,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("NEW MESSAGE", style = cInter(11, FontWeight.Bold, 1.98f), color = CHAT.eyebrow)
                        Text(
                            "Start a conversation",
                            style = cSerif(24, FontWeight.SemiBold, -0.48f),
                            color = CHAT.navy,
                        )
                    }
                }
            }
        }

        // ── Search field ──
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CHAT.white)
                .border(1.dp, CHAT.border, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = CHAT.faint, modifier = Modifier.size(16.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = cInter(14).copy(color = CHAT.navy),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(CHAT.gold),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isBlank()) {
                        Text("Search people", style = cInter(14), color = CHAT.faint)
                    }
                    inner()
                },
            )
        }

        // ── People list — Chat Redesign C3a: what tapping a row does depends
        // on where the pair stand (connect / cancel / open the DM). ──
        AsyncContent(
            key = query,
            load = {
                val people = Net.client.api.chatPeople(query.ifBlank { null }).people
                val connections = runCatching { Net.client.api.listConnections().data }.getOrDefault(emptyList())
                val incoming = runCatching { Net.client.api.listConnectionRequests("incoming").data }.getOrDefault(emptyList())
                val outgoing = runCatching { Net.client.api.listConnectionRequests("outgoing").data }.getOrDefault(emptyList())
                NewMessageData(people, connections, incoming, outgoing)
            },
        ) { (people, connections, incoming, outgoing), reload ->
            fun startDm(person: ChatPerson) {
                if (busyUserId != null) return
                busyUserId = person.userId
                scope.launch {
                    runCatching { Net.client.api.createDm(DmBody(person.userId)).conversationId }
                        .onSuccess { id -> busyUserId = null; if (id.isNotBlank()) onOpenThread(id) }
                        .onFailure { e ->
                            busyUserId = null
                            if (isConsentRequired(e)) consentPromptFor = person
                        }
                }
            }
            fun sendConnectionRequest(person: ChatPerson) {
                if (busyUserId != null) return
                busyUserId = person.userId
                scope.launch {
                    runCatching { Net.client.api.requestConnection(RequestConnectionBody(person.userId, clientMutationId = UUID.randomUUID().toString())) }
                    busyUserId = null
                    reload()
                }
            }
            fun cancelConnectionRequest(person: ChatPerson) {
                val req = outgoing.firstOrNull { it.userId == person.userId } ?: return
                if (busyUserId != null) return
                busyUserId = person.userId
                scope.launch {
                    runCatching { Net.client.api.cancelConnectionRequest(req.requestId) }
                    busyUserId = null
                    reload()
                }
            }

            if (people.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No people found.", style = cInter(13), color = CHAT.ink500)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(CHAT.white)
                                .border(1.dp, CHAT.border, RoundedCornerShape(24.dp)),
                        ) {
                            people.forEachIndexed { idx, person ->
                                val state = connectionStateFor(person.userId, connections, incoming, outgoing)
                                PersonRow(
                                    person, idx, state, busy = busyUserId == person.userId,
                                    onMessage = { startDm(it) }, onConnect = { sendConnectionRequest(it) },
                                    onCancelRequest = { cancelConnectionRequest(it) },
                                )
                                if (idx < people.lastIndex) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(CHAT.border),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stale-cache recovery: offer the connection request instead of a dead-end error.
        val prompt = consentPromptFor
        if (prompt != null) {
            AlertDialog(
                onDismissRequest = { consentPromptFor = null },
                containerColor = CHAT.white,
                title = { Text("Not connected yet", style = cSerif(18, FontWeight.SemiBold), color = CHAT.navy) },
                text = {
                    Text(
                        "Send ${prompt.fullName} a connection request first — you can chat once they accept.",
                        style = cInter(13).copy(lineHeight = 19.sp),
                        color = CHAT.ink600,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (busyUserId == null) {
                            busyUserId = prompt.userId
                            scope.launch {
                                runCatching { Net.client.api.requestConnection(RequestConnectionBody(prompt.userId, clientMutationId = UUID.randomUUID().toString())) }
                                busyUserId = null
                            }
                        }
                        consentPromptFor = null
                    }) {
                        Text("Send request", style = cInter(13, FontWeight.Bold), color = CHAT.goldDeep)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { consentPromptFor = null }) {
                        Text("Cancel", style = cInter(13, FontWeight.Medium), color = CHAT.ink500)
                    }
                },
            )
        }
    }
}
