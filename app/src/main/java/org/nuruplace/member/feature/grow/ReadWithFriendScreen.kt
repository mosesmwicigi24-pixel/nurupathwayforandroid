// "Read with a Friend" — R3 client (spec §3/§6; docs/READING_SOCIAL_PLAN.md
// §4). Reuses the Plans tab's PL palette + card language (PlansShared.kt,
// PlanDetailScreen.kt) and the chat epic's connection graph
// (data/net/CommunityDtos.kt ConnectionRow, ChatCircleAvatar) for the friend
// picker — no second consent model. Wire shapes: data/net/ReadingSocialDtos.kt.
package org.nuruplace.member.feature.grow

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.ConnectionRow
import org.nuruplace.member.data.net.CreateReadingGroupBody
import org.nuruplace.member.data.net.CreateReadingInviteBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ReadingGroupMember
import org.nuruplace.member.data.net.ReadingGroupRow
import org.nuruplace.member.data.net.ReadingInviteAcceptResult
import org.nuruplace.member.data.net.ReadingInvitePreview
import org.nuruplace.member.data.net.ReadingInviteRow
import org.nuruplace.member.feature.community.ChatCircleAvatar
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Spacing
import java.util.UUID

// --- Shared helpers ---

/** The public https://pathway.nuruplace.org/join/{token} share URL — the
 *  entire payload for every share channel (WhatsApp/social/copy/QR). */
fun readingJoinUrl(token: String): String = "https://pathway.nuruplace.org/join/$token"

/** The rich invite message shared alongside the link (spec §6: "plan title ·
 *  N days · 'Join me reading <title> on Nuru Pathway'"). */
fun readingInviteMessage(planTitle: String, dayCount: Int, token: String): String =
    "Join me reading \"$planTitle\" on Nuru Pathway — a $dayCount-day plan. ${readingJoinUrl(token)}"

private fun shareText(context: android.content.Context, text: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(send, null))
    }
}

private fun firstName(name: String): String = name.trim().split(Regex("\\s+")).firstOrNull() ?: name

// ─────────────────────────────────────────────────────────────────────────────
// Hub — "my active shared groups"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReadWithFriendHubScreen(myUserId: String, onBack: () -> Unit, onOpenGroup: (String) -> Unit) {
    var groups by remember { mutableStateOf<List<ReadingGroupRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        runCatching { Net.client.api.myReadingGroups().data }
            .onSuccess { groups = it; error = null }
            .onFailure { error = ApiException.message(it) }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }
    val active = groups.filter { !it.isArchived }

    Column(Modifier.fillMaxSize().background(PL.cream).verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 44.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleBackBtn(onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("READ WITH A FRIEND", style = plInter(9, FontWeight.Bold, 1.6f), color = PL.catText)
                Text("Your shared plans", style = plSerif(22, FontWeight.SemiBold, -0.4f), color = PL.navy)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = Spacing.tabBarSpace + 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                loading && groups.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PL.gold)
                }
                active.isEmpty() -> ReadWithFriendEmptyState(onBrowse = onBack)
                else -> active.forEach { g -> ReadingGroupCard(group = g, myUserId = myUserId, onClick = { onOpenGroup(g.groupId) }) }
            }
            if (error != null && groups.isEmpty() && !loading) {
                Text(error!!, style = plInter(13), color = PL.ink2, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun ReadWithFriendEmptyState(onBrowse: () -> Unit) {
    val view = LocalView.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, PL.border, RoundedCornerShape(22.dp))
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(PL.gold.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.PersonAdd, null, tint = PL.gold, modifier = Modifier.size(26.dp)) }
        Text("Read together", style = plSerif(18, FontWeight.SemiBold), color = PL.navy)
        Text(
            "Invite a friend to any plan and keep each other going — see how far they've come and cheer them on.",
            style = plInter(13), color = PL.ink2, textAlign = TextAlign.Center,
        )
        Text(
            "Browse plans", style = plInter(13, FontWeight.Bold), color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(PL.navy)
                .clickable { Haptics.tap(view); onBrowse() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ReadingGroupCard(group: ReadingGroupRow, myUserId: String, onClick: () -> Unit) {
    val view = LocalView.current
    val others = group.otherMembers(myUserId)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, PL.border, RoundedCornerShape(20.dp))
            .clickable { Haptics.tap(view); onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))) { PLCover(group.plan.imageUrl, modifier = Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.plan.title, style = plSerif(15, FontWeight.SemiBold, -0.15f), color = PL.navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (others.isEmpty()) {
                    Text("Just you so far", style = plInter(11), color = PL.ink3, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Row(Modifier.padding(top = 4.dp)) {
                        others.take(5).forEachIndexed { i, m ->
                            Box(Modifier.padding(start = if (i == 0) 0.dp else (-8).dp)) {
                                ChatCircleAvatar(name = m.fullName, avatarUrl = m.avatarUrl, size = 24.dp, textSize = 10)
                            }
                        }
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = PL.ink3, modifier = Modifier.size(16.dp))
        }
        if (others.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                others.take(3).forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(firstName(m.fullName), style = plInter(12, FontWeight.SemiBold), color = PL.blurb)
                        Text(
                            " · Day ${maxOf(m.currentDay ?: 1, 1)} of ${group.plan.dayCount}",
                            style = plInter(12), color = PL.ink3,
                        )
                    }
                }
                if (others.size > 3) {
                    Text("+ ${others.size - 3} more", style = plInter(11, FontWeight.Bold), color = PL.goldDeep)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Group detail — roster + progress, invite, leave/archive
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReadingGroupDetailScreen(groupId: String, myUserId: String, onBack: () -> Unit) {
    var group by remember { mutableStateOf<ReadingGroupRow?>(null) }
    var pendingInvites by remember { mutableStateOf<List<ReadingInviteRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showFriendPicker by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current

    suspend fun reload() {
        loading = (group == null)
        runCatching { Net.client.api.readingGroup(groupId) }
            .onSuccess {
                group = it; error = null
                pendingInvites = runCatching { Net.client.api.listReadingInvites(groupId).data.filter(ReadingInviteRow::isPending) }.getOrDefault(emptyList())
            }
            .onFailure { error = ApiException.message(it) }
        loading = false
    }
    // "refreshing on appear" — the roster's progress is a moving target.
    LaunchedEffect(groupId) { reload() }

    fun toastFor(seconds: Long = 2400) {
        scope.launch { kotlinx.coroutines.delay(seconds); toast = null }
    }

    if (showFriendPicker) {
        FriendPickerSheet(
            alreadyIn = (group?.members ?: emptyList()).filter(ReadingGroupMember::isActive).map { it.userId }.toSet(),
            onDismiss = { showFriendPicker = false },
            onPick = { userId ->
                showFriendPicker = false
                scope.launch {
                    runCatching { Net.client.api.createReadingInvite(groupId, CreateReadingInviteBody(userId = userId, clientMutationId = UUID.randomUUID().toString())) }
                        .onSuccess { Haptics.confirm(view); toast = "Invite sent"; toastFor(); reload() }
                        .onFailure { toast = ApiException.message(it); toastFor() }
                }
            },
        )
    }
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave this shared plan?", style = plSerif(18, FontWeight.SemiBold), color = PL.navy) },
            text = { Text("You can rejoin later with a fresh invite.", style = plInter(13), color = PL.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    scope.launch {
                        runCatching { Net.client.api.leaveReadingGroup(groupId) }
                            .onSuccess { onBack() }
                            .onFailure { toast = ApiException.message(it); toastFor() }
                    }
                }) { Text("Leave", style = plInter(13, FontWeight.Bold), color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel", style = plInter(13), color = PL.ink2) } },
            containerColor = Color.White,
        )
    }

    Box(Modifier.fillMaxSize().background(PL.cream)) {
        val g = group
        when {
            g != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp)) { CircleBackBtn(onClick = onBack) }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = Spacing.tabBarSpace + 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GroupHeroCard(g)
                    GroupRosterCard(g, myUserId)
                    if (g.createdBy == myUserId && pendingInvites.isNotEmpty()) {
                        PendingInvitesCard(pendingInvites) { invite ->
                            scope.launch {
                                runCatching { Net.client.api.revokeReadingInvite(groupId, invite.inviteId) }
                                    .onFailure { toast = ApiException.message(it); toastFor() }
                                reload()
                            }
                        }
                    }
                    GroupActionsCard(
                        isCreator = g.createdBy == myUserId,
                        onInviteFriend = { showFriendPicker = true },
                        onShareLink = {
                            scope.launch {
                                runCatching {
                                    Net.client.api.createReadingInvite(groupId, CreateReadingInviteBody(clientMutationId = UUID.randomUUID().toString()))
                                }.onSuccess { invite ->
                                    Haptics.confirm(view)
                                    shareText(context, readingInviteMessage(g.plan.title, g.plan.dayCount, invite.token))
                                }.onFailure { toast = ApiException.message(it); toastFor() }
                            }
                        },
                        onArchive = {
                            scope.launch {
                                runCatching { Net.client.api.archiveReadingGroup(groupId) }
                                    .onFailure { toast = ApiException.message(it); toastFor() }
                                reload()
                            }
                        },
                        onLeave = { showLeaveConfirm = true },
                    )
                }
            }
            loading -> CircularProgressIndicator(color = PL.gold, modifier = Modifier.align(Alignment.Center))
            else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error ?: "Couldn't load this shared plan.", style = plInter(13), color = PL.ink2)
            }
        }
        toast?.let { ReadingToast(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun GroupHeroCard(g: ReadingGroupRow) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, PL.border, RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(18.dp))) { PLCover(g.plan.imageUrl, modifier = Modifier.fillMaxSize()) }
        Column {
            Text("READING TOGETHER", style = plInter(9, FontWeight.Bold, 1.5f), color = PL.goldDeep)
            Text(g.plan.title, style = plSerif(20, FontWeight.SemiBold, -0.4f), color = PL.navy)
            Text(
                "${g.plan.dayCount}-day plan · ${g.members.count(ReadingGroupMember::isActive)} reading together",
                style = plInter(12), color = PL.ink3,
            )
        }
    }
}

@Composable
private fun GroupRosterCard(g: ReadingGroupRow, myUserId: String) {
    val activeMembers = g.members.filter(ReadingGroupMember::isActive)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).border(1.dp, PL.border, RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        PLOverline("WHO'S READING", color = PL.goldDeep, kerning = 1.4f)
        Column(Modifier.padding(top = 10.dp)) {
            activeMembers.forEachIndexed { i, m ->
                MemberRow(m, g.plan.dayCount, isMe = m.userId == myUserId)
                if (i != activeMembers.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).padding(start = 52.dp).background(PL.border))
            }
        }
    }
}

@Composable
private fun MemberRow(m: ReadingGroupMember, dayCount: Int, isMe: Boolean) {
    val done = maxOf(m.daysDone, 0)
    val pct = if (dayCount > 0) (done.toFloat() / dayCount).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ChatCircleAvatar(name = m.fullName, avatarUrl = m.avatarUrl, size = 38.dp, textSize = 13)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isMe) "${m.fullName} (you)" else m.fullName,
                style = plInter(13, FontWeight.SemiBold), color = PL.navy, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(5.dp).clip(RoundedCornerShape(999.dp)).background(PL.border)) {
                Box(Modifier.fillMaxWidth(pct).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(PL.gold))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("Day ${maxOf(m.currentDay ?: 1, 1)} of $dayCount", style = plInter(11, FontWeight.Bold), color = PL.catText, maxLines = 1)
    }
}

@Composable
private fun PendingInvitesCard(invites: List<ReadingInviteRow>, onRevoke: (ReadingInviteRow) -> Unit) {
    val view = LocalView.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, PL.border, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PLOverline("PENDING INVITES", color = PL.goldDeep, kerning = 1.4f)
        invites.forEach { invite ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = PL.ink3, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (invite.isOpenLink) "Open link" else "Invite sent", style = plInter(12), color = PL.ink2, modifier = Modifier.weight(1f))
                Text(
                    "Revoke", style = plInter(11, FontWeight.Bold), color = Color.Red,
                    modifier = Modifier.clickable { Haptics.tap(view); onRevoke(invite) }.padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupActionsCard(
    isCreator: Boolean,
    onInviteFriend: () -> Unit,
    onShareLink: () -> Unit,
    onArchive: () -> Unit,
    onLeave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionRow(Icons.Filled.PersonAdd, "Invite a friend", PL.navy, onInviteFriend)
        ActionRow(Icons.Filled.Share, "Share join link", PL.navy, onShareLink)
        if (isCreator) ActionRow(Icons.Filled.Flag, "End this shared plan", PL.ink2, onArchive)
        ActionRow(Icons.AutoMirrored.Filled.Logout, "Leave", Color.Red, onLeave)
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, PL.border, RoundedCornerShape(16.dp))
            .clickable { Haptics.tap(view); onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = plInter(13, FontWeight.SemiBold), color = tint)
    }
}

@Composable
private fun ReadingToast(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = plInter(12, FontWeight.SemiBold), color = Color.White,
        modifier = modifier
            .padding(bottom = Spacing.tabBarSpace + 12.dp)
            .clip(RoundedCornerShape(999.dp)).background(PL.navy)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Friend picker (targeted invite) — reuses the chat connection graph
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FriendPickerSheet(alreadyIn: Set<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var connections by remember { mutableStateOf<List<ConnectionRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val view = LocalView.current

    LaunchedEffect(Unit) {
        connections = runCatching { Net.client.api.listConnections().data }.getOrDefault(emptyList())
        loading = false
    }
    val filtered = connections.filter { it.status == "accepted" && it.userId !in alreadyIn }
        .let { list -> if (query.isBlank()) list else list.filter { it.fullName.contains(query, ignoreCase = true) } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Invite a friend", style = plSerif(18, FontWeight.SemiBold), color = PL.navy)
            BasicTextField(
                value = query, onValueChange = { query = it },
                textStyle = plInter(14).copy(color = PL.navy),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    .clip(RoundedCornerShape(14.dp)).background(PL.surface).border(1.dp, PL.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search your connections", style = plInter(14), color = PL.ink3)
                    inner()
                },
            )
            Spacer(Modifier.height(8.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PL.gold)
                }
                filtered.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No connections yet", style = plInter(14, FontWeight.SemiBold), color = PL.navy)
                    Text(
                        "Connect with someone in Chat first, then invite them to read together.",
                        style = plInter(12), color = PL.ink2, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                else -> Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    filtered.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { Haptics.tap(view); onPick(c.userId) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChatCircleAvatar(name = c.fullName, avatarUrl = c.avatarUrl, size = 36.dp, textSize = 13)
                            Spacer(Modifier.width(12.dp))
                            Text(c.fullName, style = plInter(14, FontWeight.Medium), color = PL.navy)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Invite preview (deep link / notification) — accept or decline
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ReadingInvitePreviewScreen(token: String, onClose: () -> Unit, onOpenGroup: (String) -> Unit) {
    var preview by remember { mutableStateOf<ReadingInvitePreview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ReadingInviteAcceptResult?>(null) }
    var declined by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    LaunchedEffect(token) {
        loading = true
        runCatching { Net.client.api.readingInvitePreview(token) }
            .onSuccess { preview = it; error = null }
            .onFailure { error = ApiException.message(it) }
        loading = false
    }

    fun accept() {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { Net.client.api.acceptReadingInvite(token) }
                .onSuccess { Haptics.confirm(view); result = it }
                .onFailure { Haptics.reject(view); error = ApiException.message(it) }
            busy = false
        }
    }
    fun decline() {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { Net.client.api.declineReadingInvite(token) }
                .onSuccess { declined = true }
                .onFailure { error = ApiException.message(it) }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(PL.cream)) {
        val r = result
        val p = preview
        when {
            r != null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(PL.gold.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = PL.goldDeep, modifier = Modifier.size(30.dp))
                }
                Text("You're in!", style = plSerif(22, FontWeight.SemiBold), color = PL.navy)
                Text(
                    "You've joined the plan — go see who's reading with you.",
                    style = plInter(13), color = PL.ink2, textAlign = TextAlign.Center,
                )
                Text(
                    "Go to shared plan", style = plInter(14, FontWeight.Bold), color = Color.White,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PL.navy)
                        .clickable { onOpenGroup(r.groupId) }
                        .padding(vertical = 14.dp),
                    textAlign = TextAlign.Center,
                )
            }
            declined -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Close, null, tint = PL.ink3, modifier = Modifier.size(28.dp))
                Text("Invite declined", style = plSerif(18, FontWeight.SemiBold), color = PL.navy)
                Text("Close", style = plInter(13, FontWeight.Bold), color = PL.gold, modifier = Modifier.clickable(onClick = onClose))
            }
            p != null -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White).border(1.dp, PL.border, CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, null, tint = PL.navy, modifier = Modifier.size(18.dp)) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(22.dp))) { PLCover(p.plan.imageUrl, modifier = Modifier.fillMaxSize()) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${firstName(p.inviter.fullName)} invited you to read", style = plInter(13, FontWeight.SemiBold), color = PL.ink2)
                        Text(p.plan.title, style = plSerif(24, FontWeight.SemiBold, -0.5f), color = PL.navy, textAlign = TextAlign.Center)
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("${p.plan.dayCount} days", style = plInter(11, FontWeight.SemiBold), color = PL.ink2)
                            Text("${p.memberCount} reading", style = plInter(11, FontWeight.SemiBold), color = PL.ink2)
                        }
                    }
                    p.message?.takeIf { it.isNotBlank() }?.let { msg ->
                        Text(
                            "“$msg”", style = plSerif(14, FontWeight.Normal, italic = true), color = PL.blurb,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PL.highlight).padding(16.dp),
                        )
                    }
                    error?.let { Text(it, style = plInter(12), color = Color.Red, textAlign = TextAlign.Center) }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PL.goldCtaGrad)
                            .clickable(enabled = !busy) { Haptics.tap(view); accept() }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (busy) CircularProgressIndicator(color = PL.navy, modifier = Modifier.size(16.dp))
                        else Icon(Icons.Filled.MenuBook, null, tint = PL.navy, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Join & start reading", style = plInter(14, FontWeight.Bold), color = PL.navy)
                    }
                    Text(
                        "Not now", style = plInter(13, FontWeight.SemiBold), color = PL.ink2, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { Haptics.tap(view); decline() }.padding(vertical = 8.dp),
                    )
                }
            }
            loading -> CircularProgressIndicator(color = PL.gold, modifier = Modifier.align(Alignment.Center))
            else -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(error ?: "This invite link isn't available.", style = plInter(14), color = PL.ink2, textAlign = TextAlign.Center)
                Text("Close", style = plInter(13, FontWeight.Bold), color = PL.gold, modifier = Modifier.clickable(onClick = onClose))
            }
        }
    }
}

// --- Shared little pieces ---

@Composable
private fun CircleBackBtn(onClick: () -> Unit) {
    val view = LocalView.current
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White).border(1.dp, PL.border, CircleShape)
            .clickable { Haptics.tap(view); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = PL.navy, modifier = Modifier.size(18.dp)) }
}
