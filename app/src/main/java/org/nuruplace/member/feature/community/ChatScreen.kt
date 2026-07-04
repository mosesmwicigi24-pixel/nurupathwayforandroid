// Chat hub — "Nuru Connect" inbox. Cream header + AI card + verse + segmented
// tabs (My Space / DM / My Groups) over grouped conversation cards, a DM story
// rail, discover-spaces and people directories, and a compose FAB. Port of the
// iOS ChatView inbox. Thread + compose live in sibling files (ChatThreadScreen /
// NewMessageScreen). Shared palette + primitives come from ChatShared.kt.
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ChatConversation
import org.nuruplace.member.data.net.ChatPerson
import org.nuruplace.member.data.net.DiscoverSpace
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.TailoredVerse
import org.nuruplace.member.ui.components.AsyncContent
import java.time.LocalTime

private val Capsule = RoundedCornerShape(999.dp)

/** Hub bundle — one load for inbox + people + greeting name + the tailored verse. */
private data class HubData(
    val inbox: org.nuruplace.member.data.net.ChatInbox,
    val people: List<ChatPerson>,
    val name: String,
    val verse: TailoredVerse?,
)

@Composable
fun ChatInboxScreen(
    onOpenThread: (String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    AsyncContent(load = {
        val inbox = Net.client.api.chatInbox()
        val people = runCatching { Net.client.api.chatPeople(null).people }.getOrDefault(emptyList())
        val name = runCatching { Net.client.api.me().profile.fullName }.getOrDefault("")
        // Verse for today comes from the same tailored-verse service Home uses —
        // the hub card must reflect the server's pick, not a hardcoded verse.
        val verse = runCatching { Net.client.api.homeVerse() }.getOrNull()
        HubData(inbox, people, name, verse)
    }) { (inbox, people, name, verse), reload ->
        val scope = rememberCoroutineScope()
        var tab by remember { mutableIntStateOf(0) }
        var query by remember { mutableStateOf("") }

        val conversations = inbox.conversations
        val spaces = conversations.filter { it.kind == "space" }
        val dms = conversations.filter { it.kind == "dm" }
        val groups = conversations.filter { it.kind == "group" }
        val totalUnread = conversations.sumOf { it.unread }

        fun join(conversationId: String) {
            scope.launch { runCatching { Net.client.api.joinChatSpace(conversationId) }; reload() }
        }
        fun startDm(person: ChatPerson) {
            scope.launch {
                val id = runCatching { Net.client.api.createDm(org.nuruplace.member.data.net.DmBody(person.userId)).conversationId }.getOrNull()
                if (!id.isNullOrBlank()) onOpenThread(id)
            }
        }

        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(CHAT.paper)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Header ──
                ChatCreamHeaderBox {
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val greeting = when (LocalTime.now().hour) {
                                    in 0..11 -> "GOOD MORNING"
                                    in 12..16 -> "GOOD AFTERNOON"
                                    else -> "GOOD EVENING"
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = CHAT.eyebrow, modifier = Modifier.size(12.dp))
                                    Text(
                                        greeting + " · " + name.substringBefore(' ').uppercase(),
                                        style = cInter(11, FontWeight.SemiBold, 2.4f),
                                        color = CHAT.eyebrow,
                                    )
                                }
                                Text(
                                    "Nuru Connect",
                                    style = cSerif(30, FontWeight.SemiBold, -0.6f),
                                    color = CHAT.navy,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Text(
                                    if (totalUnread > 0) "$totalUnread unread · ${spaces.size} spaces" else "You're all caught up",
                                    style = cInter(13),
                                    color = CHAT.ink600,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CHAT.white)
                                    .border(1.dp, CHAT.border, RoundedCornerShape(16.dp))
                                    .clickable { onOpenNotifications() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = CHAT.navy, modifier = Modifier.size(19.dp))
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(8.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(CHAT.gold),
                                )
                            }
                        }
                        // Search field
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp)
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
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (query.isBlank()) {
                                        Text("Search spaces, people, messages", style = cInter(14), color = CHAT.faint)
                                    }
                                    inner()
                                },
                            )
                            if (query.isNotBlank()) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear",
                                    tint = CHAT.faint,
                                    modifier = Modifier.size(14.dp).clickable { query = "" },
                                )
                            }
                        }
                    }
                }

                // ── Body ──
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    AiCard(totalUnread = totalUnread, spaceCount = spaces.size, onOpenAssistant = onOpenAssistant)

                    if (query.isBlank()) VerseCard(verse)

                    // Segmented control
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Capsule)
                            .background(CHAT.white.copy(alpha = 0.7f))
                            .border(1.dp, CHAT.border, Capsule)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Segment(0, "#My Space", spaces.size, tab) { tab = 0 }
                        Segment(1, "DM", dms.size, tab) { tab = 1 }
                        Segment(2, "My Groups", groups.size, tab) { tab = 2 }
                    }

                    // Tab body
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (tab) {
                            0 -> MySpaceTab(
                                spaces = spaces,
                                discover = inbox.discoverSpaces,
                                query = query,
                                onOpenThread = onOpenThread,
                                onJoin = { join(it) },
                            )
                            1 -> DmTab(
                                dms = dms,
                                people = people,
                                selfName = name,
                                query = query,
                                onOpenThread = onOpenThread,
                                onStartDm = { startDm(it) },
                            )
                            else -> GroupsTab(
                                groups = groups,
                                query = query,
                                onOpenThread = onOpenThread,
                            )
                        }
                    }
                }
            }

            // ── FAB ──
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .size(56.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(CHAT.storyRing)
                    .clickable { onNewMessage() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "New message", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── AI "Quick help from Nuru" card ──
@Composable
private fun AiCard(totalUnread: Int, spaceCount: Int, onOpenAssistant: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CHAT.aiCard)
            .border(1.5.dp, CHAT.aiBorderRing, RoundedCornerShape(24.dp))
            .clickable { onOpenAssistant() },
    ) {
        // Subtle corner glows — matchParentSize so they don't inflate the card height
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(CHAT.aiPurpleGlow.copy(alpha = 0.38f), Color.Transparent),
                    center = Offset(150f, 40f),
                    radius = 300f,
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(CHAT.aiGreenGlow.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(920f, 150f),
                    radius = 300f,
                ),
            ),
        )
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CHAT.aiOrb),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(CHAT.aiDot)
                        .border(2.dp, CHAT.navyInk, androidx.compose.foundation.shape.CircleShape),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Quick help from Nuru", style = cSerif(15, FontWeight.SemiBold, -0.16f), color = Color.White)
                    Box(
                        Modifier
                            .clip(Capsule)
                            .background(CHAT.aiBadge)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("AI", style = cInter(8, FontWeight.Bold, 1.1f), color = CHAT.navyInk)
                    }
                }
                Text(
                    "The AI assistant · $totalUnread updates across $spaceCount spaces",
                    style = cInter(11),
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Verse-for-today card ──
@Composable
private fun VerseCard(verse: TailoredVerse?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(CHAT.gold.copy(alpha = 0.08f), CHAT.gold.copy(alpha = 0.02f))))
            .border(1.dp, CHAT.gold.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CHAT.gold.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.FormatQuote, contentDescription = null, tint = CHAT.gold, modifier = Modifier.size(15.dp))
        }
        Column {
            Text("VERSE FOR TODAY", style = cInter(11, FontWeight.Bold, 1.4f), color = CHAT.eyebrow)
            Text(
                verse?.text?.takeIf { it.isNotBlank() }
                    ?: "The heartfelt counsel of a friend is as sweet as perfume and incense.",
                style = cSerif(13, FontWeight.Normal).copy(fontStyle = FontStyle.Italic, lineHeight = 19.sp),
                color = CHAT.navy,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                verse?.reference?.takeIf { it.isNotBlank() } ?: "Proverbs 27:9",
                style = cInter(10, FontWeight.Bold), color = CHAT.eyebrow, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ── Segmented control segment ──
@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(
    index: Int,
    label: String,
    count: Int,
    tab: Int,
    onSelect: () -> Unit,
) {
    val on = tab == index
    Row(
        Modifier
            .weight(1f)
            .clip(Capsule)
            .then(if (on) Modifier.background(CHAT.selectedSeg) else Modifier)
            .clickable { onSelect() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = cInter(12, FontWeight.SemiBold), color = if (on) Color.White else CHAT.ink600)
            Box(
                Modifier
                    .clip(Capsule)
                    .background(if (on) CHAT.gold else CHAT.surface)
                    .defaultMinSize(minWidth = 18.dp)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.toString(), style = cInter(10, FontWeight.Bold), color = if (on) CHAT.navy else CHAT.ink500)
            }
        }
    }
}

// ── Tab 0: My Space ──
@Composable
private fun MySpaceTab(
    spaces: List<ChatConversation>,
    discover: List<DiscoverSpace>,
    query: String,
    onOpenThread: (String) -> Unit,
    onJoin: (String) -> Unit,
) {
    val filteredSpaces = spaces.filter { query.isBlank() || (it.title ?: "").contains(query, ignoreCase = true) }

    Section("# YOUR SPACES")
    if (filteredSpaces.isEmpty()) {
        EmptyState("No spaces yet.")
    } else {
        GroupedCard {
            filteredSpaces.forEachIndexed { idx, c ->
                if (idx > 0) Divider()
                SpaceRow(c, idx, onOpenThread)
            }
        }
    }

    val filteredDiscover = discover.filter { query.isBlank() || (it.title ?: "").contains(query, ignoreCase = true) }
    if (filteredDiscover.isNotEmpty()) {
        Section("# DISCOVER SPACES")
        GroupedCard {
            filteredDiscover.forEachIndexed { idx, d ->
                if (idx > 0) Divider()
                DiscoverSpaceRow(d, idx, onJoin)
            }
        }
    }
}

@Composable
private fun SpaceRow(c: ChatConversation, idx: Int, onOpenThread: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenThread(c.conversationId) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChatSquircleAvatar("#", chatRowTint(idx), size = 52.dp, radius = 18.dp, textSize = 22, avatarUrl = c.avatarUrl)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title ?: "Space",
                    style = cInter(12, if (c.unread > 0) FontWeight.SemiBold else FontWeight.Medium, -0.12f),
                    color = CHAT.navy,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    chatRowTime(c.lastAt),
                    style = cInter(11, if (c.unread > 0) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (c.unread > 0) CHAT.gold else CHAT.faint,
                )
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(previewText(c), style = cInter(10), color = CHAT.ink600, modifier = Modifier.weight(1f), maxLines = 1)
                if (c.unread > 0) UnreadBadge(c.unread) else DoubleCheck()
            }
        }
    }
}

@Composable
private fun DiscoverSpaceRow(d: DiscoverSpace, idx: Int, onJoin: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChatSquircleAvatar("#", chatRowTint(idx + 2), size = 52.dp, radius = 18.dp, textSize = 22)
        Column(Modifier.weight(1f)) {
            Text(d.title ?: "Space", style = cInter(12, FontWeight.Medium), color = CHAT.navy, maxLines = 1)
            Text(
                listOfNotNull(d.topic ?: d.category, "${d.memberCount} members").joinToString(" · "),
                style = cInter(10),
                color = CHAT.ink600,
                maxLines = 1,
            )
        }
        FollowButton { onJoin(d.conversationId) }
    }
}

@Composable
private fun FollowButton(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(Capsule)
            .background(CHAT.storyRing)
            .height(30.dp)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        Text("Follow", style = cInter(11, FontWeight.Bold), color = Color.White)
    }
}

// ── Tab 1: DM ──
@Composable
private fun DmTab(
    dms: List<ChatConversation>,
    people: List<ChatPerson>,
    selfName: String,
    query: String,
    onOpenThread: (String) -> Unit,
    onStartDm: (ChatPerson) -> Unit,
) {
    // Story rail
    if (dms.isNotEmpty() && query.isBlank()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // "Your note"
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp)) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(CHAT.selectedSeg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(chatInitials(selfName), style = cInter(14, FontWeight.SemiBold), color = Color.White)
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(CHAT.storyRing)
                            .border(3.dp, CHAT.paper, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
                Text("Your note", style = cInter(10, FontWeight.Medium), color = CHAT.navy, modifier = Modifier.padding(top = 6.dp))
            }
            dms.forEach { dm ->
                Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(CHAT.storyRing)
                            .padding(2.5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(CHAT.paper)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ChatCircleAvatar(dm.title ?: "", size = 47.dp, avatarUrl = dm.avatarUrl)
                        }
                    }
                    Text(
                        (dm.title ?: "").substringBefore(' '),
                        style = cInter(10, FontWeight.Medium),
                        color = CHAT.navy,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    // Direct messages
    val filteredDms = dms.filter { query.isBlank() || (it.title ?: "").contains(query, ignoreCase = true) }
    Section("DIRECT MESSAGES", Icons.Filled.People)
    if (filteredDms.isEmpty()) {
        EmptyState("No direct messages yet.")
    } else {
        GroupedCard {
            filteredDms.forEachIndexed { idx, c ->
                if (idx > 0) Divider()
                DmRow(c, idx, onOpenThread)
            }
        }
    }

    // People directory
    val filteredPeople = people.filter { query.isBlank() || it.fullName.contains(query, ignoreCase = true) }
    if (filteredPeople.isNotEmpty()) {
        Section("PEOPLE", Icons.Filled.Group)
        GroupedCard {
            filteredPeople.forEachIndexed { idx, p ->
                if (idx > 0) Divider()
                PersonRow(p, idx, onStartDm)
            }
        }
    }
}

@Composable
private fun DmRow(c: ChatConversation, idx: Int, onOpenThread: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenThread(c.conversationId) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChatSquircleAvatar(chatInitials(c.title ?: ""), chatRowTint(idx + 3), size = 52.dp, radius = 18.dp, textSize = 15, avatarUrl = c.avatarUrl)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title ?: "Direct message",
                    style = cInter(12, if (c.unread > 0) FontWeight.SemiBold else FontWeight.Medium, -0.12f),
                    color = CHAT.navy,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    chatRowTime(c.lastAt),
                    style = cInter(11, if (c.unread > 0) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (c.unread > 0) CHAT.gold else CHAT.faint,
                )
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(previewText(c), style = cInter(10), color = CHAT.ink600, modifier = Modifier.weight(1f), maxLines = 1)
                if (c.unread > 0) UnreadBadge(c.unread) else DoubleCheck()
            }
        }
    }
}

@Composable
private fun PersonRow(p: ChatPerson, idx: Int, onStartDm: (ChatPerson) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            ChatSquircleAvatar(chatInitials(p.fullName), chatRowTint(idx + 1), size = 52.dp, radius = 18.dp, textSize = 15, avatarUrl = p.avatarUrl)
            if (p.level != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .clip(Capsule)
                        .background(CHAT.storyRing)
                        .border(1.5.dp, Color.White, Capsule)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text("L${p.level}", style = cInter(8, FontWeight.Bold), color = CHAT.navy)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(p.fullName, style = cInter(12, FontWeight.Medium, -0.12f), color = CHAT.navy, maxLines = 1)
            Text(
                listOfNotNull(p.role, p.congregation).joinToString(" · "),
                style = cInter(11),
                color = CHAT.ink500,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(CHAT.gold.copy(alpha = 0.10f))
                .clickable { onStartDm(p) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Message", tint = CHAT.gold, modifier = Modifier.size(15.dp))
        }
    }
}

// ── Tab 2: My Groups ──
@Composable
private fun GroupsTab(
    groups: List<ChatConversation>,
    query: String,
    onOpenThread: (String) -> Unit,
) {
    val filtered = groups.filter { query.isBlank() || (it.title ?: "").contains(query, ignoreCase = true) }
    Section("YOUR GROUPS", Icons.Filled.Group)
    if (filtered.isEmpty()) {
        EmptyState("No groups yet.")
    } else {
        GroupedCard {
            filtered.forEachIndexed { idx, c ->
                if (idx > 0) Divider()
                GroupRow(c, idx, onOpenThread)
            }
        }
    }
}

@Composable
private fun GroupRow(c: ChatConversation, idx: Int, onOpenThread: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenThread(c.conversationId) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(chatTileBrush(chatRowTint(idx))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title ?: "Group",
                    style = cInter(12, if (c.unread > 0) FontWeight.SemiBold else FontWeight.Medium, -0.12f),
                    color = CHAT.navy,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    chatRowTime(c.lastAt),
                    style = cInter(11, if (c.unread > 0) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (c.unread > 0) CHAT.gold else CHAT.faint,
                )
            }
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(previewText(c), style = cInter(10), color = CHAT.ink600, modifier = Modifier.weight(1f), maxLines = 1)
                if (c.unread > 0) UnreadBadge(c.unread) else DoubleCheck()
            }
        }
    }
}

// ── Shared building blocks (hub-private) ──
@Composable
private fun GroupedCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CHAT.white)
            .border(1.dp, CHAT.border, RoundedCornerShape(24.dp)),
        content = content,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CHAT.border))
}

@Composable
private fun Section(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier.padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = CHAT.overline, modifier = Modifier.size(13.dp)) }
        if (label.startsWith("#")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("#", style = cInter(12, FontWeight.Bold), color = CHAT.overline)
                Text(label.removePrefix("#").trim(), style = cInter(11, FontWeight.Bold, 1.4f), color = CHAT.overline)
            }
        } else {
            Text(label, style = cInter(11, FontWeight.Bold, 1.4f), color = CHAT.overline)
        }
    }
}

@Composable
private fun UnreadBadge(n: Int) {
    Box(
        Modifier
            .clip(Capsule)
            .background(CHAT.storyRing)
            .defaultMinSize(minWidth = 18.dp)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(n.toString(), style = cInter(9, FontWeight.Bold), color = Color.White)
    }
}

@Composable
private fun DoubleCheck() {
    Icon(Icons.Filled.DoneAll, contentDescription = null, tint = CHAT.doubleCheck, modifier = Modifier.size(14.dp))
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = cInter(13), color = CHAT.ink500)
    }
}

/** Inbox-row preview: voice/photo glyph or last body; for groups prefix the author. */
private fun previewText(c: ChatConversation): String {
    val base = when (c.lastType) {
        "voice" -> "🎤 Voice message"
        "image" -> "📷 Photo"
        else -> c.lastBody.orEmpty()
    }
    val author = c.lastAuthor
    return if (c.kind == "group" && !author.isNullOrBlank()) "$author: $base" else base
}
