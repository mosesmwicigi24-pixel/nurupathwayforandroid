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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.net.BroadcastBody
import org.nuruplace.member.data.net.ChatConversation
import org.nuruplace.member.data.net.ChatPerson
import org.nuruplace.member.data.net.ConnectionRequestRow
import org.nuruplace.member.data.net.ConnectionRow
import org.nuruplace.member.data.net.DiscoverSpace
import org.nuruplace.member.data.net.HubDiscipler
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PastoralInboxRow
import org.nuruplace.member.data.net.RequestConnectionBody
import org.nuruplace.member.data.net.RequestJoinSpaceBody
import org.nuruplace.member.data.net.TailoredVerse
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.ListSkeleton
import java.time.LocalTime
import java.util.UUID

private val Capsule = RoundedCornerShape(999.dp)

/** Member tabs (4): My Space · Chat · My Discipler · Talk with My Pastor —
 *  plus two staff-only appends (Chat Redesign C3b, docs/CHAT_REDESIGN.md).
 *  An enum (not integer indices) because which tabs are VISIBLE varies by
 *  role — a fixed index would silently point at the wrong tab body once a
 *  conditional segment is hidden. */
private enum class ChatTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    MySpace("My Space", Icons.Filled.Group),
    Chat("Chat", Icons.AutoMirrored.Filled.Chat),
    MyDiscipler("My Discipler", Icons.Filled.Person),
    TalkWithPastor("Talk with My Pastor", Icons.Filled.Favorite),
    // Pastor/SuperAdmin-facing (client-loose-gated on Admin+; server is the
    // real authority — password step-up then FORBIDDEN_SCOPE for anyone who
    // has never held a pastor_assignments row and isn't SuperAdmin).
    PastoralInbox("Pastoral Inbox", Icons.Filled.People),
    // SuperAdmin only — unchanged from before this task, purely appended.
    Broadcast("Broadcast", Icons.Filled.Campaign),
}

/** Hub bundle — one load for inbox + people + greeting name + the tailored verse
 *  + connections (Chat Redesign C3a — "no unsolicited DMs") + My Discipler /
 *  Talk with My Pastor tab pre-checks (Chat Redesign C3b). */
private data class HubData(
    val inbox: org.nuruplace.member.data.net.ChatInbox,
    val people: List<ChatPerson>,
    val name: String,
    val verse: TailoredVerse?,
    val connections: List<ConnectionRow>,
    val incoming: List<ConnectionRequestRow>,
    val outgoing: List<ConnectionRequestRow>,
    /** Non-null only when a real assignment exists (`GET /me/discipleship`) —
     *  drives the My Discipler tab's active-vs-empty state without ever
     *  calling the lazily-CREATING `GET /chat/discipler/conversation` just to
     *  find out. */
    val discipler: HubDiscipler?,
    /** Best-effort resolve of the DISCIPLER thread — ONLY attempted when
     *  [discipler] is non-null (a real, already-established relationship), so
     *  this never surprise-creates a thread for someone with no discipler.
     *  Used solely to cross-reference `inbox.conversations` for an unread
     *  badge; the tap handler re-resolves independently. */
    val disciplerConversationId: String?,
    /** Cross-referenced from a PRIOR tap's cached id (AppPrefs) — never an
     *  eager `POST /chat/pastoral` call, which creates a thread as a side
     *  effect (see PARITY_AUDIT.md, 2026-07-18). Null until the member has
     *  opened Talk with My Pastor at least once on this device. */
    val pastoralConversationId: String?,
)

@Composable
fun ChatInboxScreen(
    onOpenThread: (String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenNotifications: () -> Unit,
    isStaff: Boolean = false,
    // Client-side gate for the pastor-facing "Pastoral Inbox" segment — the
    // caller (MainShell) wires this to SuperAdmin-only, matching Broadcast's
    // own precedent exactly ("SuperAdmin ONLY, not even Admin" — see the
    // `isStaff` doc below). The server is the real authority regardless
    // (password step-up, then FORBIDDEN_SCOPE for anyone who has never held
    // a pastor_assignments row and isn't SuperAdmin; pastoral/service.ts#inbox)
    // — an Admin-level pastor could still reach the route directly if one
    // ever exists, they'd just need a deep link; documented as a known
    // limit rather than widening this client gate speculatively.
    pastoralEligible: Boolean = false,
    onOpenBroadcast: (String) -> Unit = {},
    onOpenThreadWithContext: (String, String) -> Unit = { id, _ -> onOpenThread(id) },
) {
    AsyncContent(loading = { ListSkeleton(rows = 8) }, refreshable = true, load = {
        val inbox = Net.client.api.chatInbox()
        val people = runCatching { Net.client.api.chatPeople(null).people }.getOrDefault(emptyList())
        val name = runCatching { Net.client.api.me().profile.fullName }.getOrDefault("")
        // Verse for today comes from the same tailored-verse service Home uses —
        // the hub card must reflect the server's pick, not a hardcoded verse.
        val verse = runCatching { Net.client.api.homeVerse() }.getOrNull()
        // Chat Redesign C3a — "no unsolicited DMs": who I'm already connected
        // to, and who's asked / been asked. Best-effort so an older server
        // (or a hiccup) never blanks the whole tab.
        val connections = runCatching { Net.client.api.listConnections().data }.getOrDefault(emptyList())
        val incoming = runCatching { Net.client.api.listConnectionRequests("incoming").data }.getOrDefault(emptyList())
        val outgoing = runCatching { Net.client.api.listConnectionRequests("outgoing").data }.getOrDefault(emptyList())
        val discipler = runCatching { Net.client.api.discipleship().data.discipler }.getOrNull()
        val disciplerConversationId = if (discipler != null) {
            runCatching { Net.client.api.disciplerConversation().conversationId }.getOrNull()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val pastoralConversationId = AppPrefs.pastoralConversationId
        HubData(inbox, people, name, verse, connections, incoming, outgoing, discipler, disciplerConversationId, pastoralConversationId)
    }) { (inbox, people, name, verse, connections, incoming, outgoing, discipler, disciplerConversationId, pastoralConversationId), reload ->
        val scope = rememberCoroutineScope()
        var tab by remember { mutableStateOf(ChatTab.MySpace) }
        var query by remember { mutableStateOf("") }
        // Person a connection action is in flight for (Connect / cancel /
        // accept / decline) — mirrors `busy` DM-creation below.
        var connectingUserId by remember { mutableStateOf<String?>(null) }
        // Stale-cache recovery: createDm answered 403 CONSENT_REQUIRED for
        // someone the directory still showed as messageable.
        var consentPromptFor by remember { mutableStateOf<ChatPerson?>(null) }

        val conversations = inbox.conversations
        // "My Space" (Chat Redesign C3b) merges the old "#My Space" (kind=space)
        // and "My Groups" (kind=group) segments into one tab — the backend's
        // data model already unifies cell rooms + topical spaces under
        // type=SPACE (docs/CHAT_REDESIGN_PLAN.md §2.1/§4 Open Question 4), and
        // `kind` on the wire is exactly {space, group} for those two today.
        val spaces = conversations.filter { it.kind == "space" }
        // The discipler/pastoral threads live in their OWN tabs — keep the
        // known ids out of the Chat tab's DM list (iOS parity; the list
        // endpoint sends no `type`, so the locally-learned ids are the filter).
        val dms = conversations.filter {
            it.kind == "dm" &&
                it.conversationId != disciplerConversationId &&
                it.conversationId != pastoralConversationId
        }
        val groups = conversations.filter { it.kind == "group" }
        val mySpaceUnread = spaces.sumOf { it.unread } + groups.sumOf { it.unread }
        val disciplerUnread = disciplerConversationId?.let { id -> conversations.firstOrNull { it.conversationId == id }?.unread } ?: 0
        val pastoralUnread = pastoralConversationId?.let { id -> conversations.firstOrNull { it.conversationId == id }?.unread } ?: 0
        val totalUnread = conversations.sumOf { it.unread }

        // Spaces whose reviewed join request is pending a leader's decision
        // (session-local — the server notifies on accept/decline).
        var pendingJoinIds by remember { mutableStateOf(setOf<String>()) }
        fun join(conversationId: String) {
            scope.launch {
                // Immediate join first (public spaces, unchanged). Where the
                // server refuses it (a space that requires leader review), fall
                // back to filing a reviewed join request — pending, not failed.
                runCatching { Net.client.api.joinChatSpace(conversationId) }
                    .onSuccess { reload() }
                    .onFailure {
                        runCatching { Net.client.api.requestJoinSpace(conversationId, RequestJoinSpaceBody()) }
                            .onSuccess { res ->
                                if (res.status == "already_member") reload()
                                else pendingJoinIds = pendingJoinIds + conversationId
                            }
                    }
            }
        }
        fun startDm(person: ChatPerson) {
            scope.launch {
                runCatching { Net.client.api.createDm(org.nuruplace.member.data.net.DmBody(person.userId)).conversationId }
                    .onSuccess { id -> if (id.isNotBlank()) onOpenThread(id) }
                    .onFailure { e -> if (isConsentRequired(e)) consentPromptFor = person }
            }
        }
        fun sendConnectionRequest(person: ChatPerson) {
            if (connectingUserId != null) return
            connectingUserId = person.userId
            scope.launch {
                runCatching { Net.client.api.requestConnection(RequestConnectionBody(person.userId, clientMutationId = UUID.randomUUID().toString())) }
                connectingUserId = null
                reload()
            }
        }
        fun cancelConnectionRequest(req: ConnectionRequestRow) {
            if (connectingUserId != null) return
            connectingUserId = req.userId
            scope.launch {
                runCatching { Net.client.api.cancelConnectionRequest(req.requestId) }
                connectingUserId = null
                reload()
            }
        }
        fun acceptConnectionRequest(req: ConnectionRequestRow) {
            if (connectingUserId != null) return
            connectingUserId = req.userId
            scope.launch {
                runCatching { Net.client.api.acceptConnectionRequest(req.requestId) }
                connectingUserId = null
                reload()
            }
        }
        fun declineConnectionRequest(req: ConnectionRequestRow) {
            if (connectingUserId != null) return
            connectingUserId = req.userId
            scope.launch {
                runCatching { Net.client.api.declineConnectionRequest(req.requestId) }
                connectingUserId = null
                reload()
            }
        }

        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(CHAT.paper)
                    .imePadding()
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

                    // Segmented control — member tabs (4): My Space · Chat ·
                    // My Discipler · Talk with My Pastor, per the owner brief
                    // (docs/CHAT_REDESIGN.md). Staff append Pastoral Inbox
                    // and/or Broadcast; the list (not fixed integer indices)
                    // is what actually varies per role.
                    val tabs = remember(isStaff, pastoralEligible) {
                        buildList {
                            add(ChatTab.MySpace); add(ChatTab.Chat); add(ChatTab.MyDiscipler); add(ChatTab.TalkWithPastor)
                            if (pastoralEligible) add(ChatTab.PastoralInbox)
                            if (isStaff) add(ChatTab.Broadcast)
                        }
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .clip(Capsule)
                            .background(CHAT.white.copy(alpha = 0.7f))
                            .border(1.dp, CHAT.border, Capsule)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Chips count UNREAD messages, not conversations, and vanish at
                        // zero — "when they are open, the counter resets" (iOS parity).
                        tabs.forEach { t ->
                            val count = when (t) {
                                ChatTab.MySpace -> mySpaceUnread.takeIf { it > 0 }
                                // Unread DM messages + pending incoming connection requests —
                                // both are "things waiting on you in this tab".
                                ChatTab.Chat -> (dms.sumOf { it.unread } + incoming.size).takeIf { it > 0 }
                                ChatTab.MyDiscipler -> disciplerUnread.takeIf { it > 0 }
                                ChatTab.TalkWithPastor -> pastoralUnread.takeIf { it > 0 }
                                ChatTab.PastoralInbox, ChatTab.Broadcast -> null
                            }
                            Segment(label = t.label, icon = t.icon, count = count, selected = tab == t) { tab = t }
                        }
                    }

                    // Tab body
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (tab) {
                            ChatTab.MySpace -> MySpaceTab(
                                spaces = spaces,
                                groups = groups,
                                discover = inbox.discoverSpaces,
                                query = query,
                                onOpenThread = onOpenThread,
                                onJoin = { join(it) },
                                pendingJoinIds = pendingJoinIds,
                            )
                            ChatTab.Chat -> DmTab(
                                dms = dms,
                                people = people,
                                selfName = name,
                                query = query,
                                connections = connections,
                                incoming = incoming,
                                outgoing = outgoing,
                                connectingUserId = connectingUserId,
                                onOpenThread = onOpenThread,
                                onStartDm = { startDm(it) },
                                onConnect = { sendConnectionRequest(it) },
                                onCancelRequest = { cancelConnectionRequest(it) },
                                onAccept = { acceptConnectionRequest(it) },
                                onDecline = { declineConnectionRequest(it) },
                            )
                            ChatTab.MyDiscipler -> MyDisciplerTab(
                                discipler = discipler,
                                onOpenThread = { id -> onOpenThreadWithContext(id, "discipler") },
                            )
                            ChatTab.TalkWithPastor -> TalkWithPastorTab(
                                onOpenThread = { id -> onOpenThreadWithContext(id, "pastoral") },
                            )
                            ChatTab.PastoralInbox -> PastoralInboxTab(
                                onOpenThread = { id -> onOpenThreadWithContext(id, "pastoral") },
                            )
                            ChatTab.Broadcast -> BroadcastTab(onOpenBroadcast = onOpenBroadcast)
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
                    TextButton(onClick = { sendConnectionRequest(prompt); consentPromptFor = null }) {
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

// ── Segmented control segment — one per icon + unread badge (Chat Redesign
// C3b: up to 6 segments across roles, so the strip scrolls horizontally
// rather than the old fixed 4-wide `weight(1f)` even split). ──
@Composable
private fun Segment(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val on = selected
    Row(
        Modifier
            .clip(Capsule)
            .then(if (on) Modifier.background(CHAT.selectedSeg) else Modifier)
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = if (on) Color.White else CHAT.ink600, modifier = Modifier.size(14.dp))
            Text(label, style = cInter(12, FontWeight.SemiBold), color = if (on) Color.White else CHAT.ink600, maxLines = 1)
            if (count != null) {
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
}

// ── Tab: My Space (Chat Redesign C3b — merges the old "#My Space" (kind=space)
// and "My Groups" (kind=group) segments into one tab, per the owner brief:
// the backend's data model already unifies cell rooms + topical spaces under
// type=SPACE. Both kinds of content stay visible as their own sub-sections. ──
@Composable
private fun MySpaceTab(
    spaces: List<ChatConversation>,
    groups: List<ChatConversation>,
    discover: List<DiscoverSpace>,
    query: String,
    onOpenThread: (String) -> Unit,
    onJoin: (String) -> Unit,
    pendingJoinIds: Set<String> = emptySet(),
) {
    val filteredSpaces = spaces.filter { query.isBlank() || (it.title ?: "").contains(query, ignoreCase = true) }
    val filteredGroups = groups.filter { query.isBlank() || (it.title ?: "").contains(query, ignoreCase = true) }

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

    if (filteredGroups.isNotEmpty() || query.isBlank()) {
        Section("YOUR GROUPS", Icons.Filled.Group)
        if (filteredGroups.isEmpty()) {
            EmptyState("No groups yet.")
        } else {
            GroupedCard {
                filteredGroups.forEachIndexed { idx, c ->
                    if (idx > 0) Divider()
                    GroupRow(c, idx, onOpenThread)
                }
            }
        }
    }

    val filteredDiscover = discover.filter { query.isBlank() || (it.title ?: "").contains(query, ignoreCase = true) }
    if (filteredDiscover.isNotEmpty()) {
        Section("# DISCOVER SPACES")
        GroupedCard {
            filteredDiscover.forEachIndexed { idx, d ->
                if (idx > 0) Divider()
                DiscoverSpaceRow(d, idx, pending = d.conversationId in pendingJoinIds, onJoin = onJoin)
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
            // Bottom meta: overlapping member stack + count pill · space topic on the right
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MemberStack(avatarUrl = c.avatarUrl, title = c.title, idx = idx, memberCount = c.memberCount)
                val topic = c.topic
                if (!topic.isNullOrBlank()) {
                    Text(
                        topic,
                        style = cInter(10),
                        color = CHAT.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f).padding(start = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverSpaceRow(d: DiscoverSpace, idx: Int, pending: Boolean, onJoin: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChatSquircleAvatar("#", chatRowTint(idx + 2), size = 52.dp, radius = 18.dp, textSize = 22)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    d.title ?: "Space",
                    style = cInter(12, FontWeight.Medium),
                    color = CHAT.navy,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val category = d.category
                if (!category.isNullOrBlank()) {
                    Box(
                        Modifier
                            .clip(Capsule)
                            .background(CHAT.goldTint)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text(category.uppercase(), style = cInter(8, FontWeight.Bold, 0.8f), color = CHAT.goldDeep)
                    }
                }
            }
            val topic = d.topic
            if (!topic.isNullOrBlank()) {
                Text(
                    topic,
                    style = cInter(10),
                    color = CHAT.ink600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MemberStack(avatarUrl = null, title = d.title, idx = idx + 2, memberCount = d.memberCount)
            }
        }
        JoinButton(pending = pending) { onJoin(d.conversationId) }
    }
}

/**
 * "Join" / "Requested" — the review-gated flow (Chat Redesign C1/C2 backend,
 * already live: `POST /chat/spaces/{id}/join-requests`). A space requiring
 * leader review never joins immediately; it goes "pending" and the leader is
 * notified server-side (`space_join_requested`). Tapping again while pending
 * is a no-op (the button disables) — the unique partial index on the server
 * makes a repeat call harmless anyway, but there's no reason to fire it.
 */
@Composable
private fun JoinButton(pending: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(Capsule)
            // Color vs. Brush — two different background overloads, so the
            // branch has to pick the modifier, not the argument.
            .then(if (pending) Modifier.background(CHAT.surface) else Modifier.background(CHAT.selectedSeg))
            .height(30.dp)
            .then(if (pending) Modifier else Modifier.clickable { onClick() })
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (pending) {
            Icon(Icons.Filled.HourglassEmpty, contentDescription = null, tint = CHAT.ink500, modifier = Modifier.size(11.dp))
            Text("Requested", style = cInter(11, FontWeight.Bold), color = CHAT.ink500)
        } else {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
            Text("Join", style = cInter(11, FontWeight.Bold), color = Color.White)
        }
    }
}

// ── Tab: Chat (Chat Redesign C3b rename of the old "DM" segment — same
// consent-gated behaviour as before, just relabelled and reordered as the
// 2nd tab per the owner brief's Member tabs (4): My Space · Chat · My
// Discipler · Talk with My Pastor.) ──
@Composable
private fun DmTab(
    dms: List<ChatConversation>,
    people: List<ChatPerson>,
    selfName: String,
    query: String,
    connections: List<ConnectionRow>,
    incoming: List<ConnectionRequestRow>,
    outgoing: List<ConnectionRequestRow>,
    connectingUserId: String?,
    onOpenThread: (String) -> Unit,
    onStartDm: (ChatPerson) -> Unit,
    onConnect: (ChatPerson) -> Unit,
    onCancelRequest: (ConnectionRequestRow) -> Unit,
    onAccept: (ConnectionRequestRow) -> Unit,
    onDecline: (ConnectionRequestRow) -> Unit,
) {
    // Connection requests — incoming (needs YOUR action) leads outgoing
    // (waiting on someone else).
    if (query.isBlank() && (incoming.isNotEmpty() || outgoing.isNotEmpty())) {
        Section("CONNECTION REQUESTS", Icons.Filled.People)
        GroupedCard {
            incoming.forEachIndexed { idx, req ->
                if (idx > 0) Divider()
                IncomingRequestRow(req, busy = connectingUserId == req.userId, onAccept = { onAccept(req) }, onDecline = { onDecline(req) })
            }
            outgoing.forEachIndexed { idx, req ->
                if (idx > 0 || incoming.isNotEmpty()) Divider()
                OutgoingRequestRow(req, busy = connectingUserId == req.userId, onCancel = { onCancelRequest(req) })
            }
        }
    }

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
        EmptyState(if (query.isBlank()) "Connect with someone before starting a chat." else "No conversations match your search.")
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
                val state = connectionStateFor(p.userId, connections, incoming, outgoing)
                PersonRow(
                    p, idx, state, busy = connectingUserId == p.userId,
                    onMessage = { onStartDm(p) }, onConnect = { onConnect(p) },
                    onCancelRequest = {
                        outgoing.firstOrNull { it.userId == p.userId }?.let(onCancelRequest)
                    },
                )
            }
        }
    }
}

/** One "X wants to connect" row — accept/decline, no separate PersonRow tap target. */
@Composable
private fun IncomingRequestRow(req: ConnectionRequestRow, busy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChatSquircleAvatar(chatInitials(req.fullName), CHAT.gold, size = 52.dp, radius = 18.dp, textSize = 15, avatarUrl = req.avatarUrl)
        Column(Modifier.weight(1f)) {
            Text(req.fullName, style = cInter(12, FontWeight.Medium, -0.12f), color = CHAT.navy, maxLines = 1)
            Text("Wants to connect", style = cInter(11), color = CHAT.ink500)
        }
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CHAT.gold, strokeWidth = 2.dp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(CHAT.surface).clickable { onDecline() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, contentDescription = "Decline", tint = CHAT.ink600, modifier = Modifier.size(13.dp)) }
                Box(
                    Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(CHAT.storyRing).clickable { onAccept() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(13.dp)) }
            }
        }
    }
}

/** "Request sent — waiting to be accepted" — cancel only. */
@Composable
private fun OutgoingRequestRow(req: ConnectionRequestRow, busy: Boolean, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChatSquircleAvatar(chatInitials(req.fullName), CHAT.ink500, size = 52.dp, radius = 18.dp, textSize = 15, avatarUrl = req.avatarUrl)
        Column(Modifier.weight(1f)) {
            Text(req.fullName, style = cInter(12, FontWeight.Medium, -0.12f), color = CHAT.navy, maxLines = 1)
            Text("Request sent — waiting to be accepted", style = cInter(11), color = CHAT.ink500)
        }
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CHAT.gold, strokeWidth = 2.dp)
        } else {
            Box(
                Modifier.clip(Capsule).background(CHAT.surface).clickable { onCancel() }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text("Cancel", style = cInter(11, FontWeight.SemiBold), color = CHAT.ink600) }
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

/**
 * Directory row — what tapping it does depends on where the pair stand
 * (Chat Redesign C3a, "no unsolicited DMs"): connected opens the DM,
 * not-connected sends a request, a sent request cancels, a received request
 * and a block are display-only here (acted on from the requests section /
 * thread ⋮ menu respectively). Shared by [ChatScreen] (DM tab directory) and
 * [NewMessageScreen] (the "New message" picker) — not `private` for that reason.
 */
@Composable
fun PersonRow(
    p: ChatPerson,
    idx: Int,
    state: ConnectionState,
    busy: Boolean,
    onMessage: (ChatPerson) -> Unit,
    onConnect: (ChatPerson) -> Unit,
    onCancelRequest: (ChatPerson) -> Unit,
) {
    val tap: (() -> Unit)? = when (state) {
        is ConnectionState.Connected -> { { onMessage(p) } }
        is ConnectionState.NotConnected -> { { onConnect(p) } }
        is ConnectionState.RequestSent -> { { onCancelRequest(p) } }
        is ConnectionState.RequestReceived, is ConnectionState.Blocked -> null
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (tap != null && !busy) Modifier.clickable { tap() } else Modifier)
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
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CHAT.gold, strokeWidth = 2.dp)
        } else {
            PersonRowAffordance(state)
        }
    }
}

/** Right-edge affordance per connection state — same real estate the old
 *  always-message-icon used to occupy. */
@Composable
private fun PersonRowAffordance(state: ConnectionState) {
    when (state) {
        is ConnectionState.Connected -> Box(
            Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(CHAT.gold.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Message", tint = CHAT.gold, modifier = Modifier.size(15.dp)) }
        is ConnectionState.NotConnected -> Row(
            Modifier.clip(Capsule).background(CHAT.storyRing).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
            Text("Connect", style = cInter(11, FontWeight.Bold), color = Color.White)
        }
        is ConnectionState.RequestSent -> Row(
            Modifier.clip(Capsule).background(CHAT.surface).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Request sent", style = cInter(10, FontWeight.SemiBold), color = CHAT.ink500)
            Icon(Icons.Filled.Close, contentDescription = "Cancel request", tint = CHAT.faint, modifier = Modifier.size(12.dp))
        }
        is ConnectionState.RequestReceived -> Box(
            Modifier.clip(Capsule).background(CHAT.gold.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text("Wants to connect", style = cInter(10, FontWeight.Bold), color = CHAT.navy) }
        is ConnectionState.Blocked -> Icon(
            Icons.Filled.Close, contentDescription = null, tint = CHAT.faint,
            modifier = Modifier.size(20.dp),
        )
    }
}

// GroupRow is now rendered inline by MySpaceTab's "YOUR GROUPS" sub-section
// above (Chat Redesign C3b merge) — kept as its own function since it's a
// distinct row style (square tile + Group icon vs. the "#" squircle).
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

// ── Tab: My Discipler (Chat Redesign C3b). Active/has-content only when
// GET /me/discipleship's `discipler` field is non-null (an already-established
// assignment) — the empty state below is NOT an error, it's the ordinary
// unpaired state. Tapping resolves — lazily CREATING if needed — the
// DISCIPLER thread via GET /chat/discipler/conversation (never the Hub's own
// legacy `dm_conversation_id`, which is an ordinary/unstamped DM lookup, not
// the same conversation this endpoint resolves — see PARITY_AUDIT.md). ──
@Composable
private fun MyDisciplerTab(discipler: HubDiscipler?, onOpenThread: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Section("MY DISCIPLER", Icons.Filled.Person)

    if (discipler == null) {
        EmptyState("A discipler has not yet been assigned to you.")
        return
    }

    fun open() {
        if (opening) return
        opening = true
        error = null
        scope.launch {
            runCatching { Net.client.api.disciplerConversation().conversationId }
                .onSuccess { id -> opening = false; if (id.isNotBlank()) onOpenThread(id) }
                .onFailure { e ->
                    opening = false
                    error = when {
                        isNoDiscipler(e) -> "A discipler has not yet been assigned to you."
                        isMinorBlocked(e) -> "Direct messages aren't available yet for your account."
                        else -> "Couldn't open this conversation — please try again."
                    }
                }
        }
    }

    GroupedCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !opening) { open() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChatSquircleAvatar(chatInitials(discipler.fullName), CHAT.gold, size = 52.dp, radius = 18.dp, textSize = 15, avatarUrl = discipler.avatarUrl)
            Column(Modifier.weight(1f)) {
                Text(discipler.fullName.ifBlank { "Your discipler" }, style = cInter(13, FontWeight.SemiBold, -0.12f), color = CHAT.navy, maxLines = 1)
                val meta = listOfNotNull(discipler.roleLabel.takeIf { it.isNotBlank() }, discipler.cellName).joinToString(" · ")
                Text(meta.ifBlank { "Tap to start the conversation" }, style = cInter(11), color = CHAT.ink500, maxLines = 1)
            }
            if (opening) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CHAT.gold, strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Open conversation", tint = CHAT.gold, modifier = Modifier.size(18.dp))
            }
        }
    }
    Row(
        Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Lock, null, tint = CHAT.goldDeep, modifier = Modifier.size(12.dp))
        Text("Private between you and your assigned discipler.", style = cInter(11), color = CHAT.ink500)
    }
    error?.let { Text(it, style = cInter(12, FontWeight.Medium), color = Color(0xFFB42318), modifier = Modifier.padding(top = 8.dp)) }
}

// ── Tab: Talk with My Pastor (Chat Redesign C3b). Unlike My Discipler this
// tab is never structurally "empty" — POST /chat/pastoral always resolves to
// SOMEONE (assigned → congregation default → fallback SuperAdmin) except the
// rare "nothing resolves" case, so the entry card always renders; only the
// tap outcome can fail. The biometric lock itself lives on the opened thread
// (ChatThreadScreen's PastoralLockScreen), not here — this tab is the (always
// unlocked) door to it. ──
@Composable
private fun TalkWithPastorTab(onOpenThread: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var archived by remember { mutableStateOf(AppPrefs.pastoralArchived) }

    Section("TALK WITH MY PASTOR", Icons.Filled.Favorite)

    fun open() {
        if (opening) return
        opening = true
        error = null
        scope.launch {
            runCatching { Net.client.api.openPastoralThread() }
                .onSuccess { res ->
                    opening = false
                    if (res.conversationId.isNotBlank()) {
                        AppPrefs.pastoralConversationId = res.conversationId
                        onOpenThread(res.conversationId)
                    }
                }
                .onFailure { e ->
                    opening = false
                    error = when {
                        isNoPastor(e) -> "No pastor is available for your congregation right now — please check back later."
                        isMinorBlocked(e) -> "Direct messages aren't available yet for your account."
                        else -> "Couldn't open this conversation — please try again."
                    }
                }
        }
    }

    GroupedCard {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !opening) { if (archived) { archived = false; AppPrefs.pastoralArchived = false }; open() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(chatTileBrush(CHAT.gold)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp)) }
            Column(Modifier.weight(1f)) {
                Text("Talk with My Pastor", style = cInter(13, FontWeight.SemiBold, -0.12f), color = CHAT.navy, maxLines = 1)
                Text(
                    if (archived) "Archived — tap to reopen" else "A private word with your pastor",
                    style = cInter(11), color = CHAT.ink500, maxLines = 1,
                )
            }
            if (opening) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CHAT.gold, strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Open conversation", tint = CHAT.gold, modifier = Modifier.size(18.dp))
            }
        }
    }
    Row(
        Modifier.padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Lock, null, tint = CHAT.goldDeep, modifier = Modifier.size(12.dp))
        Text("Private pastoral conversation.", style = cInter(11), color = CHAT.ink500)
    }
    error?.let { Text(it, style = cInter(12, FontWeight.Medium), color = Color(0xFFB42318), modifier = Modifier.padding(top = 8.dp)) }
}

// ── Tab: Pastoral Inbox (Chat Redesign C3b) — the pastor/SuperAdmin-facing
// "Talk with Your Pastor" console. GET /chat/pastoral/inbox is password
// step-up gated exactly like Broadcast (§5.3), so it reuses the same
// BroadcastStepUp/BroadcastStepUpDialog machinery as-is rather than building
// a second step-up UI. FORBIDDEN_SCOPE (never held a pastor_assignments row
// and isn't SuperAdmin) is treated as an honest empty state, not a crash. ──
@Composable
private fun PastoralInboxTab(onOpenThread: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val stepUp = rememberBroadcastStepUp()
    var rows by remember { mutableStateOf<List<PastoralInboxRow>?>(null) }
    var forbidden by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            runCatching { Net.client.api.pastoralInbox().data }
                .onSuccess { rows = it; forbidden = false; failed = false }
                .onFailure { e ->
                    when {
                        isPasswordRequired(e) -> stepUp.requestStepUp { load() }
                        (e as? retrofit2.HttpException)?.code() == 403 -> forbidden = true
                        else -> failed = true
                    }
                }
        }
    }
    LaunchedEffect(Unit) { load() }

    Section("PASTORAL INBOX", Icons.Filled.People)

    val list = rows
    when {
        forbidden -> EmptyState("You don't currently have any members assigned to you pastorally.")
        failed -> EmptyState("Couldn't load the pastoral inbox — pull to retry.")
        list == null -> EmptyState("Loading…")
        list.isEmpty() -> EmptyState("No pastoral conversations yet.")
        else -> GroupedCard {
            list.forEachIndexed { idx, row ->
                if (idx > 0) Divider()
                PastoralInboxRowView(row, idx, onOpenThread)
            }
        }
    }

    BroadcastStepUpDialog(stepUp, reason = "Pastoral conversations are private. Confirm it's you to open the inbox.")
}

@Composable
private fun PastoralInboxRowView(row: PastoralInboxRow, idx: Int, onOpenThread: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenThread(row.conversationId) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ChatSquircleAvatar(chatInitials(row.memberName), chatRowTint(idx), size = 52.dp, radius = 18.dp, textSize = 15, avatarUrl = row.memberAvatarUrl)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.memberName.ifBlank { "Member" },
                    style = cInter(12, FontWeight.Medium, -0.12f),
                    color = CHAT.navy, modifier = Modifier.weight(1f), maxLines = 1,
                )
                Text(chatRowTime(row.lastAt), style = cInter(11), color = CHAT.faint)
            }
            row.lastBody?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = cInter(10), color = CHAT.ink600, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

// ── Tab 3: Broadcast (staff only — Instructor+) ──
// POST chat/broadcast fans the message out as INDIVIDUAL DMs from the sender;
// members reply 1:1, never as a group. Idempotent on client_mutation_id.
// Below the composer sits the sent list (GET chat/broadcasts) — both routes
// share the same §5.3 step-up sheet (BroadcastStepUp.kt), which tries the
// fingerprint unlock before falling back to typing the password.
@Composable
private fun BroadcastTab(onOpenBroadcast: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val stepUp = rememberBroadcastStepUp()
    var draft by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sentTo by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Held across a step-up so confirming and retrying resumes THIS send rather
    // than starting a second one — a fresh id is minted only once a send lands.
    var mutationId by remember { mutableStateOf<String?>(null) }

    var list by remember { mutableStateOf<org.nuruplace.member.data.net.BroadcastListRes?>(null) }
    var listFailed by remember { mutableStateOf(false) }
    var showAllSent by remember { mutableStateOf(false) }

    fun loadList(limit: Int = 4) {
        scope.launch {
            runCatching { Net.client.api.broadcasts(limit) }
                .onSuccess { list = it; listFailed = false }
                .onFailure { e ->
                    if (isPasswordRequired(e)) {
                        stepUp.requestStepUp { loadList(limit) }
                    } else if (list == null) {
                        listFailed = true
                    }
                }
        }
    }

    fun send() {
        confirming = false
        sending = true
        error = null
        val mid = mutationId ?: java.util.UUID.randomUUID().toString().also { mutationId = it }
        scope.launch {
            runCatching {
                Net.client.api.broadcast(BroadcastBody(body = draft.trim(), clientMutationId = mid))
            }.onSuccess { res ->
                sentTo = if (res.recipientCount > 0) res.recipientCount else res.sent
                draft = ""
                mutationId = null
                sending = false
                loadList(if (showAllSent) 100 else 4)   // the new one belongs at the top
            }.onFailure { e ->
                sending = false
                if (isPasswordRequired(e)) {
                    stepUp.requestStepUp { send() }    // draft + mutationId survive for the retry
                } else {
                    error = "Couldn't send the broadcast. Please try again."
                }
            }
        }
    }

    LaunchedEffect(Unit) { loadList() }

    Section("BROADCAST", Icons.Filled.Campaign)

    // Navy explainer card
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CHAT.selectedSeg),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CHAT.gold.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Campaign, contentDescription = null, tint = CHAT.goldLight, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Message every member", style = cSerif(15, FontWeight.SemiBold, -0.16f), color = Color.White)
                Text(
                    "Reaches every member as a personal message from you. Replies come back to you one-on-one.",
                    style = cInter(11).copy(lineHeight = 16.sp),
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    // Composer
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CHAT.white)
            .border(1.dp, CHAT.border, RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            textStyle = cInter(14).copy(color = CHAT.navy, lineHeight = 20.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
            decorationBox = { inner ->
                if (draft.isBlank()) {
                    Text("Write your message to everyone…", style = cInter(14), color = CHAT.faint)
                }
                inner()
            },
        )
    }

    // Gold send button
    val canSend = draft.isNotBlank() && !sending
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Capsule)
            .background(if (canSend) CHAT.storyRing else chatTileBrush(CHAT.gold.copy(alpha = 0.35f)))
            .clickable(enabled = canSend) { confirming = true }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (sending) "Sending…" else "Send to everyone",
            style = cInter(13, FontWeight.Bold),
            color = CHAT.navy,
        )
    }

    val delivered = sentTo
    if (delivered != null) {
        Text(
            "Delivered to $delivered members 🎉",
            style = cInter(12, FontWeight.SemiBold),
            color = CHAT.activeText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
    val err = error
    if (err != null) {
        Text(
            err,
            style = cInter(12, FontWeight.Medium),
            color = Color(0xFFB42318),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = CHAT.white,
            title = { Text("Broadcast to all members?", style = cSerif(18, FontWeight.SemiBold), color = CHAT.navy) },
            text = {
                Text(
                    "Every member receives this as a personal message from you.",
                    style = cInter(13).copy(lineHeight = 19.sp),
                    color = CHAT.ink600,
                )
            },
            confirmButton = {
                TextButton(onClick = { send() }) {
                    Text("Send", style = cInter(13, FontWeight.Bold), color = CHAT.goldDeep)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text("Cancel", style = cInter(13, FontWeight.Medium), color = CHAT.ink500)
                }
            },
        )
    }

    // Sent list — GET chat/broadcasts, under the composer (iOS BroadcastHome
    // parity). Its own 403s route through the SAME step-up sheet below.
    val sentList = list
    if (sentList != null && sentList.data.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CHAT.white)
                .border(1.dp, CHAT.border, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LAST POSTS SENT", style = cInter(11, FontWeight.Bold, 1.4f), color = CHAT.overline, modifier = Modifier.weight(1f))
                if (sentList.total > sentList.data.size) {
                    Text("${sentList.total} all-time", style = cInter(11), color = CHAT.ink600)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sentList.data.forEach { b -> BroadcastSentRow(b) { onOpenBroadcast(b.broadcastId) } }
            }
            if (!showAllSent && sentList.total > sentList.data.size) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showAllSent = true; loadList(100) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Show all ${sentList.total}", style = cInter(12, FontWeight.Bold), color = CHAT.goldDeep)
                }
            }
        }
    } else if (listFailed) {
        Text(
            "Couldn't load your broadcasts — pull to retry.",
            style = cInter(12),
            color = CHAT.ink600,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        )
    }

    // Step-up sheet — the server asked the password be confirmed again before
    // opening a broadcast route (send or the list above). Fingerprint-first
    // when enrolled (BroadcastStepUp), typed as the fallback.
    BroadcastStepUpDialog(stepUp, reason = "The Broadcast holds private conversations between you and every member. Confirm it's you to continue.")
}

/** One sent broadcast: its words, when, and the three numbers that matter. */
@Composable
private fun BroadcastSentRow(b: org.nuruplace.member.data.net.BroadcastSummary, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CHAT.paper)
            .border(1.dp, CHAT.border, RoundedCornerShape(16.dp))
            .clickable { onOpen() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            b.body.ifBlank { "📎 Attachment" },
            style = cSerif(14),
            color = CHAT.navy,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.People, contentDescription = null, tint = CHAT.goldDeep, modifier = Modifier.size(12.dp))
            Text("${b.recipientCount}", style = cInter(11, FontWeight.SemiBold), color = CHAT.ink600)
            Icon(Icons.Filled.DoneAll, contentDescription = null, tint = Color(0xFF2F80ED), modifier = Modifier.size(13.dp))
            Text("${b.seenCount} seen", style = cInter(11, FontWeight.SemiBold), color = CHAT.ink600)
            Text("${b.repliedCount} replied", style = cInter(11, FontWeight.SemiBold), color = CHAT.ink600)
            Spacer(Modifier.weight(1f))
            Text(broadcastRelativeTime(b.createdAt), style = cInter(11), color = CHAT.ink600)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = CHAT.ink600.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
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

/** Gold filled circle (capsule for 2+ digits) with a bold navy count — the reference unread badge. */
@Composable
private fun UnreadBadge(n: Int) {
    Box(
        Modifier
            .clip(Capsule)
            .background(CHAT.gold)
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(n.toString(), style = cInter(10, FontWeight.Bold), color = CHAT.navy)
    }
}

/**
 * Overlapping avatar cascade — identical 20dp circles offset by -7dp (~1/3 diameter),
 * each with a 2dp white ring, later circles drawn ON TOP of earlier (zIndex ramps up),
 * finished by a white count pill (same 20dp height, capsule, thin border, bold navy
 * number) overlapping the last circle at the same -7dp. Owner's reference cascade.
 * First circle shows the space photo (or its initial); the rest are tinted placeholders.
 */
@Composable
private fun MemberStack(avatarUrl: String?, title: String?, idx: Int, memberCount: Int) {
    val circles = minOf(3, maxOf(1, memberCount))
    Row(
        horizontalArrangement = Arrangement.spacedBy((-7).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(circles) { i ->
            Box(
                Modifier
                    .zIndex(i.toFloat())
                    .size(20.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(chatTileBrush(chatRowTint(idx + i)))
                    .border(2.dp, CHAT.white, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (i == 0 && !avatarUrl.isNullOrBlank()) {
                    AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                } else if (i == 0) {
                    Text((title ?: "#").trim().take(1).uppercase(), style = cInter(8, FontWeight.Bold), color = Color.White)
                }
            }
        }
        // Final element of the stack: the member-count pill, layered above the circles.
        Box(
            Modifier
                .zIndex(circles.toFloat())
                .height(20.dp)
                .clip(Capsule)
                .background(CHAT.white)
                .border(1.dp, CHAT.border, Capsule)
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(memberCount.toString(), style = cInter(9, FontWeight.Bold), color = CHAT.navy)
        }
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

/** Inbox-row preview: voice/photo glyph or "Author: body" (author for space/group rows only). */
private fun previewText(c: ChatConversation): String {
    when (c.lastType) {
        "voice", "audio" -> {
            val secs = c.lastDuration
            return if (secs != null && secs > 0) {
                "🎙 Voice message · ${secs / 60}:${"%02d".format(secs % 60)}"
            } else {
                "🎙 Voice message"
            }
        }
        "image" -> return "📷 Photo"
    }
    val body = c.lastBody.orEmpty()
    val author = c.lastAuthor
    return if (c.kind != "dm" && !author.isNullOrBlank()) "$author: $body" else body
}
