// Nuru Live — L5/L6 broadcaster-only surfaces (docs/LIVE_INTERACTIVE.md): the
// raised-hands + guests bottom sheet opened from the broadcast HUD's ✋
// control, and a lightweight live-chat sheet opened from 💬. Both are ported
// from the iOS reference (LiveHandsGuestsSheet.swift, LiveChatSheet.swift) —
// iOS presents both as sheets (`.sheet(isPresented:)`), a deliberate choice
// there because the broadcaster is busy filming and a floating overlay over
// their own camera preview would be distracting; Android mirrors that same
// choice here (ModalBottomSheet) rather than reusing LiveChatOverlay's
// translucent-over-video viewer styling, which assumes a video SURFACE
// underneath it that the broadcaster's own preview already occupies.
package org.nuruplace.member.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.LiveGuestRow
import org.nuruplace.member.data.net.LiveHandRow
import org.nuruplace.member.data.net.LiveMessageRow
import org.nuruplace.member.data.net.LivePulse
import org.nuruplace.member.data.net.LiveSendMessageBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.minutesSince
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

/** L6 cap — mirrors the pinned wire contract (docs/LIVE_INTERACTIVE.md:
 *  "broadcaster invites up to 6 viewers as guests") and the iOS port's
 *  `LiveHandsGuestsSheet.maxGuests`. */
const val MAX_GUESTS = 6

/** True iff a guest row is still "in play" — invited or accepted — as
 *  opposed to declined/removed/ended. Same semantics as the iOS port's
 *  `LiveGuestRow.isActive` (Models/LiveInteractions.swift). */
val LiveGuestRow.isActive: Boolean get() = status == "invited" || status == "accepted"

/** "raised Xm ago" / "raised just now" — reuses the Live banner's own
 *  minutes-since parser rather than a second ISO-8601 parser. */
internal fun raisedAgoLabel(iso: String): String {
    val mins = minutesSince(iso) ?: return "just now"
    return if (mins < 1) "raised just now" else "raised ${mins}m ago"
}

// ── ✋ Raised hands + 🎤 guests sheet ────────────────────────────────────────

/**
 * "Lower" is LOCAL-ONLY (mirrors the iOS port's exact doc comment): the
 * pinned wire contract's `POST /hand {raised}` always targets the CALLING
 * user — there is no broadcaster-authority parameter to force someone
 * else's hand down. Tapping it just hides that row from THIS sheet for as
 * long as the sheet stays open; closing and reopening resets it (Compose
 * tears down [dismissedHandIds]'s `remember` scope on dismiss, exactly like
 * the iOS port's `@State` does on `.sheet` dismiss).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveHandsGuestsSheet(
    streamId: String,
    pulse: LivePulse?,
    onRefreshPulse: suspend () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var dismissedHandIds by remember { mutableStateOf(setOf<String>()) }
    var actioningUserId by remember { mutableStateOf<String?>(null) }

    val hands = (pulse?.hands ?: emptyList()).filter { it.userId !in dismissedHandIds }
    val guests = (pulse?.guests ?: emptyList()).filter { it.isActive }
    val atGuestCap = guests.size >= MAX_GUESTS

    fun invite(userId: String) {
        if (actioningUserId != null) return
        actioningUserId = userId
        scope.launch {
            runCatching { Net.client.api.postLiveGuestInvite(streamId, userId) }
            onRefreshPulse()
            actioningUserId = null
        }
    }

    fun remove(userId: String) {
        if (actioningUserId != null) return
        actioningUserId = userId
        scope.launch {
            runCatching { Net.client.api.deleteLiveGuest(streamId, userId) }
            onRefreshPulse()
            actioningUserId = null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.lg)
                .heightIn(max = 560.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Raised hands", style = NuruType.title, color = Nuru.ink)
                Spacer(Modifier.weight(1f))
                SheetCloseChip(onDismiss)
            }
            Spacer(Modifier.height(Spacing.md))

            Text("RAISED HANDS · ${hands.size}".uppercase(), style = NuruType.sectionLabel, color = Nuru.goldLo)
            Spacer(Modifier.height(Spacing.sm))
            if (hands.isEmpty()) {
                Text(
                    "No hands raised right now.", style = NuruType.body, color = Nuru.ink400,
                    modifier = Modifier.padding(vertical = Spacing.sm),
                )
            } else {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Nuru.white)
                        .padding(horizontal = Spacing.base),
                ) {
                    hands.forEachIndexed { i, hand ->
                        HandRow(
                            hand = hand,
                            guestStatus = myGuestStatus(pulse?.guests ?: emptyList(), hand.userId),
                            atGuestCap = atGuestCap,
                            busy = actioningUserId == hand.userId,
                            onInvite = { invite(hand.userId) },
                            onLower = { dismissedHandIds = dismissedHandIds + hand.userId },
                        )
                        if (i != hands.lastIndex) SheetDivider()
                    }
                }
            }

            if (guests.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                Text("GUESTS · ${guests.size}/$MAX_GUESTS".uppercase(), style = NuruType.sectionLabel, color = Nuru.goldLo)
                Spacer(Modifier.height(Spacing.sm))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Nuru.white)
                        .padding(horizontal = Spacing.base),
                ) {
                    guests.forEachIndexed { i, guest ->
                        GuestRow(guest = guest, busy = actioningUserId == guest.userId, onRemove = { remove(guest.userId) })
                        if (i != guests.lastIndex) SheetDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HandRow(
    hand: LiveHandRow,
    guestStatus: String?,
    atGuestCap: Boolean,
    busy: Boolean,
    onInvite: () -> Unit,
    onLower: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        InitialsAvatar(hand.avatarUrl, hand.fullName, 38.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(hand.fullName.ifBlank { "Member" }, style = NuruType.rowTitle, color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(raisedAgoLabel(hand.raisedAt), style = NuruType.caption, color = Nuru.ink400)
        }
        Spacer(Modifier.width(Spacing.sm))
        when (guestStatus) {
            "accepted" -> Text("Joining soon", style = NuruType.micro, color = Nuru.success, fontWeight = FontWeight.SemiBold)
            "invited" -> Text("Invited", style = NuruType.micro, color = Nuru.ink400, fontWeight = FontWeight.SemiBold)
            else -> Pill(
                label = if (atGuestCap) "6 guests max" else "Invite to join",
                bg = if (atGuestCap) Nuru.inputBg else Nuru.gold,
                fg = if (atGuestCap) Nuru.ink400 else Nuru.homeNavy,
                enabled = !atGuestCap && !busy,
                onClick = onInvite,
            )
        }
        Spacer(Modifier.width(Spacing.xs))
        Pill(label = "Lower", bg = Nuru.inputBg, fg = Nuru.ink400, enabled = true, onClick = onLower)
    }
}

@Composable
private fun GuestRow(guest: LiveGuestRow, busy: Boolean, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        InitialsAvatar(guest.avatarUrl, guest.fullName, 38.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(guest.fullName.ifBlank { "Member" }, style = NuruType.rowTitle, color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (guest.status == "accepted") "Joining soon — video in a later update" else "Invited — waiting to accept",
                style = NuruType.caption, color = if (guest.status == "accepted") Nuru.success else Nuru.ink400,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Pill(label = "Remove", bg = Nuru.dangerBg, fg = Nuru.danger, enabled = !busy, onClick = onRemove)
    }
}

@Composable
private fun Pill(label: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).background(bg)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) { Text(label, style = NuruType.micro, color = fg, fontWeight = FontWeight.Bold) }
}

// ── 💬 Live chat sheet ───────────────────────────────────────────────────

/** Deliberately NOT the full Aurora chat thread (ChatThreadScreen) — no read
 *  receipts, no offline queue, no edit/delete, just a 3s-polled (since-
 *  cursor) message list + composer, matching the viewer overlay's own
 *  3s cadence and the iOS sheet's identical scope. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveBroadcastChatSheet(streamId: String, myUserId: String?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<LiveMessageRow>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(streamId) {
        val initial = runCatching { Net.client.api.getLiveMessages(streamId) }.getOrNull()
        if (initial != null) {
            messages = initial.messages
            cursor = initial.messages.lastOrNull()?.sentAt
        }
        loading = false
        while (true) {
            delay(3_000)
            val res = runCatching { Net.client.api.getLiveMessages(streamId, cursor) }.getOrNull()
            if (res != null && res.messages.isNotEmpty()) {
                val existing = messages.map { it.messageId }.toSet()
                val fresh = res.messages.filter { it.messageId !in existing }
                cursor = res.messages.last().sentAt
                if (fresh.isNotEmpty()) messages = messages + fresh
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val body = draft.trim()
        if (body.isEmpty() || body.length > 500) return
        draft = ""
        scope.launch {
            val sent = runCatching { Net.client.api.postLiveMessage(streamId, LiveSendMessageBody(body)) }.getOrNull()
            if (sent != null && messages.none { it.messageId == sent.messageId }) {
                messages = messages + sent
                cursor = sent.sentAt
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 560.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Live chat", style = NuruType.title, color = Nuru.ink)
                Spacer(Modifier.weight(1f))
                SheetCloseChip(onDismiss)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = PaddingValues(vertical = Spacing.sm),
            ) {
                when {
                    loading -> item {
                        Box(Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Nuru.gold)
                        }
                    }
                    messages.isEmpty() -> item {
                        Text(
                            "Say hello 👋", style = NuruType.body, color = Nuru.ink400,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
                        )
                    }
                    else -> itemsIndexed(messages, key = { _, m -> m.messageId }) { _, m ->
                        ChatBubbleRow(m, mine = myUserId != null && m.userId == myUserId)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(Spacing.screen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 500) draft = it },
                    placeholder = { Text("Say something…", style = NuruType.body) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    shape = RoundedCornerShape(999.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Nuru.inputBg,
                        unfocusedContainerColor = Nuru.inputBg,
                        focusedBorderColor = Nuru.gold,
                        unfocusedBorderColor = Nuru.border,
                    ),
                    modifier = Modifier.weight(1f),
                )
                val canSend = draft.isNotBlank()
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (canSend) Nuru.gold else Nuru.gold.copy(alpha = 0.4f))
                        .clickable(enabled = canSend) { send() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Nuru.homeNavy, modifier = Modifier.size(17.dp)) }
            }
        }
    }
}

@Composable
private fun ChatBubbleRow(message: LiveMessageRow, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.width(240.dp)) {
            if (!mine) {
                Text(message.fullName.ifBlank { "Member" }, style = NuruType.micro, color = Nuru.ink600, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
            }
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(if (mine) Nuru.myBubble else Nuru.white)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) { Text(message.body, style = NuruType.body, color = Nuru.ink) }
        }
    }
}

// ── Shared small pieces ─────────────────────────────────────────────────

@Composable
private fun SheetCloseChip(onDismiss: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Nuru.white).clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Nuru.ink600, modifier = Modifier.size(14.dp)) }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Nuru.border))
}

/** Avatar with a gold-initials fallback — same visual language as
 *  LivePlayerScreen's `BroadcasterIdentityChip`, factored out here since
 *  it's needed by both hand and guest rows. */
@Composable
private fun InitialsAvatar(url: String?, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Nuru.gold), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
        } else {
            Text(
                name.trim().split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").ifBlank { "?" },
                style = NuruType.micro, color = Nuru.homeNavy, fontWeight = FontWeight.Bold,
            )
        }
    }
}
