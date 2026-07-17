// Broadcast detail — the message pinned at the top, the church answering
// beneath it. Port of the iOS BroadcastDetailView (NuruMember/Features/Chat/
// BroadcastViews.swift): a navy header card carrying the sent words plus
// reach/seen/replies, a segmented Responses|Recipients switch, the response
// wall (each row opens the private thread that response lives in — a
// broadcast IS an individual DM to every member, so "open the thread" is a
// navigation, never a creation), and the full tick ledger. Same §5.3 step-up
// gate as the list and the send (BroadcastStepUp.kt).
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.BroadcastDetailRes
import org.nuruplace.member.data.net.BroadcastRecipientRow
import org.nuruplace.member.data.net.BroadcastResponseRow
import org.nuruplace.member.data.net.Net

private val Capsule = RoundedCornerShape(999.dp)

/** WhatsApp's own blue — one tick "delivered" (a server-side fact: a
 *  broadcast is written into every recipient's thread in one transaction), two
 *  ticks "seen" (they opened it). */
private val TickBlue = Color(0xFF2F80ED)

@Composable
fun BroadcastDetailScreen(broadcastId: String, onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val stepUp = rememberBroadcastStepUp()
    var detail by remember(broadcastId) { mutableStateOf<BroadcastDetailRes?>(null) }
    var failed by remember(broadcastId) { mutableStateOf(false) }
    var tab by remember(broadcastId) { mutableStateOf(0) }   // 0 = responses, 1 = recipients

    fun load() {
        scope.launch {
            runCatching { Net.client.api.broadcastDetail(broadcastId) }
                .onSuccess { detail = it; failed = false }
                .onFailure { e ->
                    if (isPasswordRequired(e)) {
                        stepUp.requestStepUp { load() }
                    } else if (detail == null) {
                        failed = true
                    }
                }
        }
    }
    LaunchedEffect(broadcastId) { load() }

    Column(Modifier.fillMaxSize().background(CHAT.paper)) {
        Row(
            Modifier.fillMaxWidth().background(CHAT.navy)
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp)) }
            Text("Broadcast", style = cSerif(20, FontWeight.SemiBold), color = Color.White)
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val d = detail
            PinnedBroadcastCard(body = d?.body.orEmpty(), createdAt = d?.createdAt)
            when {
                d != null -> {
                    TicksSummaryRow(deliveredCount = d.deliveredCount, seenCount = d.seenCount, repliedCount = d.responses.size)
                    DetailSegments(tab) { tab = it }
                    if (tab == 0) ResponsesWall(d.responses, onOpenThread) else RecipientsList(d.recipients)
                }
                failed -> Text(
                    "Couldn't load the responses — pull to retry.",
                    style = cInter(12),
                    color = CHAT.ink600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
                else -> Row(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = CHAT.gold)
                }
            }
        }
    }

    BroadcastStepUpDialog(stepUp, reason = "The Broadcast holds private conversations between you and every member. Confirm it's you to open them.")
}

/** The word itself, pinned — navy, serif, unmistakably THE message. */
@Composable
private fun PinnedBroadcastCard(body: String, createdAt: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CHAT.bubbleInk)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Campaign, contentDescription = null, tint = CHAT.goldLight, modifier = Modifier.size(13.dp))
            Text(
                "TO EVERY MEMBER",
                style = cInter(11, FontWeight.Bold, 1.4f),
                color = CHAT.goldLight,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            Text(broadcastRelativeTime(createdAt), style = cInter(11), color = Color.White.copy(alpha = 0.6f))
        }
        Text(
            body.ifBlank { "…" },
            style = cSerif(17),
            color = Color.White,
            lineHeight = 24.sp,
        )
    }
}

/** Delivered · seen — one honest line above the segmented switch below it. */
@Composable
private fun TicksSummaryRow(deliveredCount: Int, seenCount: Int, repliedCount: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CHAT.white)
            .border(1.dp, CHAT.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tick(one = true, label = "$deliveredCount delivered")
        Tick(one = false, label = "$seenCount seen")
        Spacer(Modifier.weight(1f))
        Text("$repliedCount replied", style = cInter(11, FontWeight.SemiBold), color = CHAT.ink600)
    }
}

/** [one] draws a single blue tick ("delivered" — a server-side fact, every
 *  broadcast lands in the recipient's thread in one transaction); otherwise
 *  the WhatsApp double tick ("seen" — they opened it). */
@Composable
private fun Tick(one: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(
            if (one) Icons.Filled.Done else Icons.Filled.DoneAll,
            contentDescription = null,
            tint = TickBlue,
            modifier = Modifier.size(14.dp),
        )
        Text(label, style = cInter(11, FontWeight.SemiBold), color = CHAT.ink600)
    }
}

@Composable
private fun DetailSegments(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Capsule)
            .background(CHAT.white.copy(alpha = 0.7f))
            .border(1.dp, CHAT.border, Capsule)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DetailSegment("Responses", selected == 0, Modifier.weight(1f)) { onSelect(0) }
        DetailSegment("Recipients", selected == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun DetailSegment(label: String, on: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    Row(
        modifier
            .clip(Capsule)
            .then(if (on) Modifier.background(CHAT.selectedSeg) else Modifier)
            .clickable { onSelect() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = cInter(12, FontWeight.SemiBold), color = if (on) Color.White else CHAT.ink600)
    }
}

@Composable
private fun ResponsesWall(responses: List<BroadcastResponseRow>, onOpenThread: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (responses.isEmpty()) "NO ANSWERS YET" else "THE CHURCH ANSWERS",
            style = cInter(11, FontWeight.Bold, 1.4f),
            color = CHAT.overline,
        )
        if (responses.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CHAT.white)
                    .border(1.dp, CHAT.border, RoundedCornerShape(18.dp))
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = CHAT.gold, modifier = Modifier.size(22.dp))
                Text(
                    "When someone writes back, their words gather here — and only you see them.",
                    style = cInter(12),
                    color = CHAT.ink600,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
        } else {
            responses.forEach { r -> ResponseRow(r) { onOpenThread(r.conversationId) } }
        }
    }
}

/** Who, when, what they said — and the door to the private thread it lives in. */
@Composable
private fun ResponseRow(r: BroadcastResponseRow, onOpenThread: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CHAT.white)
            .border(1.dp, CHAT.border, RoundedCornerShape(18.dp))
            .clickable { onOpenThread() }
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChatCircleAvatar(r.fullName, size = 38.dp, avatarUrl = r.avatarUrl)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.fullName, style = cInter(13, FontWeight.Bold), color = CHAT.navy, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(broadcastRelativeTime(r.createdAt), style = cInter(11), color = CHAT.ink600)
            }
            Text(
                r.body.ifBlank { "📎 Attachment" },
                style = cInter(13),
                color = CHAT.ink600,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (r.fromThem > 1) {
                Text(
                    "${r.fromThem} messages in your conversation",
                    style = cInter(10, FontWeight.SemiBold),
                    color = CHAT.goldDeep,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = "Open thread", tint = CHAT.ink600.copy(alpha = 0.5f), modifier = Modifier.padding(top = 10.dp).size(14.dp))
    }
}

@Composable
private fun RecipientsList(recipients: List<BroadcastRecipientRow>) {
    if (recipients.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CHAT.white)
                .border(1.dp, CHAT.border, RoundedCornerShape(18.dp))
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = CHAT.ink600, modifier = Modifier.size(20.dp))
            Text("No recipients recorded.", style = cInter(12), color = CHAT.ink600, modifier = Modifier.padding(top = 8.dp))
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CHAT.white)
            .border(1.dp, CHAT.border, RoundedCornerShape(16.dp)),
    ) {
        recipients.forEachIndexed { idx, r ->
            if (idx > 0) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(CHAT.border))
            RecipientRow(r)
        }
    }
}

@Composable
private fun RecipientRow(r: BroadcastRecipientRow) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChatCircleAvatar(r.fullName, size = 30.dp, avatarUrl = r.avatarUrl)
        Text(r.fullName, style = cInter(13, FontWeight.Medium), color = CHAT.navy, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(
            if (r.seen) Icons.Filled.DoneAll else Icons.Filled.Done,
            contentDescription = if (r.seen) "Seen" else "Delivered",
            tint = TickBlue,
            modifier = Modifier.size(14.dp),
        )
    }
}
