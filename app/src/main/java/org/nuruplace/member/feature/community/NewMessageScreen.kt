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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ChatPerson
import org.nuruplace.member.data.net.DmBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun NewMessageScreen(onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val startDm: (ChatPerson) -> Unit = { person ->
        if (!busy) {
            busy = true
            scope.launch {
                val id = runCatching {
                    Net.client.api.createDm(DmBody(person.userId)).conversationId
                }.getOrNull()
                busy = false
                if (!id.isNullOrBlank()) onOpenThread(id)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(CHAT.paper)) {
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

        // ── People list ──
        AsyncContent(
            key = query,
            load = { Net.client.api.chatPeople(query.ifBlank { null }).people },
        ) { people: List<ChatPerson>, _ ->
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
                                PersonRow(idx, person, startDm)
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
    }
}

@Composable
private fun PersonRow(idx: Int, p: ChatPerson, onTap: (ChatPerson) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onTap(p) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            ChatSquircleAvatar(
                text = chatInitials(p.fullName),
                tint = chatRowTint(idx + 1),
                textSize = 15,
                avatarUrl = p.avatarUrl,
            )
            val level = p.level
            if (level != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .clip(Capsule)
                        .background(CHAT.storyRing)
                        .border(1.5.dp, Color.White, Capsule)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text("L$level", style = cInter(8, FontWeight.Bold), color = CHAT.navy)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(p.fullName, style = cInter(13, FontWeight.Medium, -0.12f), color = CHAT.navy)
            Text(
                listOfNotNull(p.role, p.congregation).joinToString(" · "),
                style = cInter(11),
                color = CHAT.ink500,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = CHAT.gold,
            modifier = Modifier.size(18.dp),
        )
    }
}
