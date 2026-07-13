// Chat thread — a space or DM conversation. Ported from the iOS ChatThreadView
// (Aurora bubbles): cream header (space "#" tile / DM gold-ring avatar), pinned topic
// strip, day-grouped message list with incoming/outgoing bubbles (text · image · voice
// · reactions · quoted reply · read ticks), a quick-reply row, and the composer.
package org.nuruplace.member.feature.community

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.nuruplace.member.data.net.ChatMessage
import org.nuruplace.member.data.net.ChatThreadDetail
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ReactBody
import org.nuruplace.member.data.net.SendMessageBody
import org.nuruplace.member.data.net.SendVoiceBody
import org.nuruplace.member.ui.components.AiDraftButton
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.FitImage
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.LiveWave
import org.nuruplace.member.ui.components.WaveformBars
import org.nuruplace.member.ui.components.voiceClock
import org.nuruplace.member.util.VoicePlayer
import org.nuruplace.member.util.VoiceRecorder
import java.util.UUID

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun ChatThreadScreen(conversationId: String, onBack: () -> Unit) {
    LaunchedEffect(conversationId) { runCatching { Net.client.api.markChatRead(conversationId) } }
    var myName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { myName = runCatching { Net.client.api.me().profile.fullName }.getOrDefault("") }

    AsyncContent(key = conversationId, load = { Net.client.api.chatConversation(conversationId) }) { thread: ChatThreadDetail, reload ->
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val view = LocalView.current
        var draft by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val messages = thread.messages
        val recorder = remember { VoiceRecorder() }
        val player = remember { VoicePlayer() }
        DisposableEffect(Unit) { onDispose { recorder.cancel(); player.release() } }
        val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) recorder.start(context)
        }
        fun startRecording() {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                recorder.start(context)
            } else {
                askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        fun sendVoice() {
            if (busy) return
            val f = recorder.stop() ?: return
            Haptics.confirm(view) // a voice note committed — the weightier tick
            val duration = recorder.elapsedSec.coerceAtLeast(1)
            val wave = recorder.waveformFor()
            busy = true
            scope.launch {
                try {
                    val url = withContext(Dispatchers.IO) {
                        val part = MultipartBody.Part.createFormData("file", f.name, f.readBytes().toRequestBody("audio/mp4".toMediaTypeOrNull()))
                        Net.client.api.uploadVoiceNote(part).url
                    }
                    if (url.isNotBlank()) {
                        val meta = buildJsonObject {
                            put("duration", duration)
                            putJsonArray("waveform") { wave.forEach { add(it) } }
                        }
                        Net.client.api.sendChatVoice(
                            conversationId,
                            SendVoiceBody(UUID.randomUUID().toString(), "Voice message", "voice", url, meta, UUID.randomUUID().toString()),
                        )
                        reload()
                    }
                } catch (_: Exception) {
                } finally {
                    busy = false
                    withContext(Dispatchers.IO) { runCatching { f.delete() } }
                }
            }
        }

        fun send(text: String) {
            val t = text.trim()
            if (t.isBlank() || busy) return
            Haptics.tap(view) // light tap — the message left your hands
            busy = true
            scope.launch {
                try {
                    Net.client.api.sendChatMessage(conversationId, SendMessageBody(UUID.randomUUID().toString(), t, "text", UUID.randomUUID().toString()))
                    draft = ""
                    reload()
                } catch (_: Exception) {
                } finally { busy = false }
            }
        }
        fun react(m: ChatMessage, emoji: String) {
            scope.launch { try { Net.client.api.toggleChatReaction(m.messageId, ReactBody(emoji)); reload() } catch (_: Exception) {} }
        }
        fun join() { scope.launch { try { Net.client.api.joinChatSpace(conversationId); reload() } catch (_: Exception) {} } }

        LaunchedEffect(messages.size) { if (messages.isNotEmpty()) runCatching { listState.animateScrollToItem(messages.lastIndex) } }

        Column(Modifier.fillMaxSize().background(CHAT.threadBg).imePadding()) {
            ThreadHeader(thread, onBack)
            if (thread.kind == "space" && !thread.joined) {
                Row(Modifier.fillMaxWidth().background(CHAT.canvas).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                    Box(
                        Modifier.clip(Capsule).background(CHAT.selectedSeg).clickable { join() }.padding(horizontal = 20.dp, vertical = 8.dp),
                    ) { Text("Join this space", style = cInter(12, FontWeight.SemiBold), color = Color.White) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().background(CHAT.canvas),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            ) {
                itemsIndexed(messages, key = { _, m -> m.messageId }) { i, m ->
                    val prevDay = if (i > 0) chatDayKey(messages[i - 1].createdAt) else ""
                    val thisDay = chatDayKey(m.createdAt)
                    if (thisDay != prevDay) DaySeparator(m.createdAt)
                    val runHead = i == 0 || messages[i - 1].authorUserId != m.authorUserId || chatDayKey(messages[i - 1].createdAt) != thisDay
                    MessageRow(m, thread.kind, runHead, player) { emoji -> react(m, emoji) }
                }
            }

            // Quick replies
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Amen 🙏", "Praying for you 💛", "On my way 🚶", "Thank you 🤍").forEach { q ->
                    Box(
                        Modifier.clip(Capsule).background(CHAT.white.copy(alpha = 0.9f)).border(1.dp, CHAT.border, Capsule)
                            .clickable { send(q) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(q, style = cInter(13, FontWeight.Medium), color = CHAT.navy) }
                }
            }

            // Composer — swaps to the live recording strip while a voice note is
            // being captured (red pulsing dot + m:ss + live wave + cancel/send).
            if (recorder.isRecording) {
                Row(
                    Modifier.fillMaxWidth().background(CHAT.white.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
                        initialValue = 0.35f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "recDot",
                    )
                    Box(Modifier.size(10.dp).clip(Capsule).background(Color(0xFFD4183D).copy(alpha = pulse)))
                    Text(voiceClock(recorder.elapsedSec), style = cInter(12, FontWeight.SemiBold), color = CHAT.navy)
                    LiveWave(recorder.levels.toList(), color = CHAT.gold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(38.dp).clip(Capsule).background(CHAT.white).border(1.dp, CHAT.border, Capsule)
                            .clickable { recorder.cancel() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, "Cancel recording", tint = CHAT.meta, modifier = Modifier.size(16.dp)) }
                    Box(
                        Modifier.size(44.dp).clip(Capsule).background(CHAT.gold).clickable { sendVoice() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send voice note", tint = Color.White, modifier = Modifier.size(17.dp)) }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().background(CHAT.white.copy(alpha = 0.92f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(CHAT.bubbleInk), contentAlignment = Alignment.Center) {
                        Text(if (myName.isBlank()) "You" else chatInitials(myName), style = cInter(10, FontWeight.Bold), color = Color.White)
                    }
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(CHAT.paper).border(1.dp, CHAT.border, RoundedCornerShape(24.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Add, null, tint = CHAT.meta, modifier = Modifier.size(19.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (draft.isBlank()) Text("Message", style = cInter(12), color = CHAT.faint)
                            BasicTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                textStyle = cInter(12).copy(color = CHAT.navy),
                                maxLines = 6,
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(CHAT.gold),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // ✨ Nuru drafting — summarizes the last messages and proposes an
                        // editable reply; "Use draft" only fills the composer (member sends).
                        AiDraftButton(
                            recentMessages = messages
                                .filter { it.msgType != "system" }
                                .takeLast(5)
                                .map { m ->
                                    val author = if (m.mine) "You" else m.authorName.ifBlank { "Member" }
                                    author to when (m.msgType) {
                                        "voice" -> m.body.ifBlank { "(voice note)" }
                                        "image" -> m.body.ifBlank { "(photo)" }
                                        "video", "file" -> m.body.ifBlank { "(shared a file)" }
                                        else -> m.body
                                    }
                                },
                            conversationId = conversationId,
                        ) { text -> draft = text }
                        Icon(Icons.Filled.EmojiEmotions, null, tint = CHAT.meta, modifier = Modifier.size(19.dp))
                    }
                    val hasDraft = draft.isNotBlank()
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(999.dp))
                            .then(if (hasDraft) Modifier.background(CHAT.storyRing) else Modifier.background(CHAT.bubbleInk))
                            .clickable { if (hasDraft) send(draft) else startRecording() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (hasDraft) Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(17.dp))
                        else Icon(Icons.Filled.Mic, "Record voice note", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadHeader(thread: ChatThreadDetail, onBack: () -> Unit) {
    Column {
        ChatCreamHeaderBox {
            Row(
                Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(999.dp)).background(CHAT.white).border(1.dp, CHAT.border, RoundedCornerShape(999.dp)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CHAT.navy, modifier = Modifier.size(18.dp)) }

                if (thread.kind == "dm") {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(999.dp)).background(CHAT.storyRing), contentAlignment = Alignment.Center) {
                        ChatCircleAvatar(thread.title ?: "", size = 34.dp)
                    }
                } else {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2E7D6B)), contentAlignment = Alignment.Center) {
                        Text("#", style = cInter(18, FontWeight.Bold), color = Color.White)
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(thread.title ?: "Conversation", style = cSerif(19, FontWeight.SemiBold, -0.3f), color = CHAT.navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (thread.kind == "dm") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🕊️", fontSize = 10.sp)
                            Text("Walking together in faith", style = cSerif(12, FontWeight.Medium).copy(fontStyle = FontStyle.Italic), color = CHAT.eyebrow, maxLines = 1)
                        }
                    } else {
                        Text(
                            "${if (thread.isPublic) "Public" else "Private"} space · ${thread.memberCount} members",
                            style = cInter(11), color = CHAT.ink600,
                        )
                    }
                }

                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(CHAT.white).border(1.dp, CHAT.gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.AutoAwesome, null, tint = CHAT.eyebrow, modifier = Modifier.size(18.dp)) }
            }
        }
        if (thread.kind == "space" && !thread.topic.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().background(CHAT.white.copy(alpha = 0.55f)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Flag, null, tint = CHAT.ink600, modifier = Modifier.size(13.dp))
                Text(thread.topic!!, style = cInter(12), color = CHAT.ink600)
            }
        }
    }
}

@Composable
private fun DaySeparator(iso: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(CHAT.hairline))
        Text(chatDayDivider(iso), style = cInter(10, FontWeight.Bold, 2.2f), color = CHAT.dayGold, modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(CHAT.hairline))
    }
}

@Composable
private fun MessageRow(m: ChatMessage, kind: String, runHead: Boolean, player: VoicePlayer, onReact: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!m.mine) {
            if (runHead) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).background(chatSenderAccent(m.authorName, false)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!m.authorAvatar.isNullOrBlank()) {
                        AsyncImage(model = m.authorAvatar, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(chatInitials(m.authorName), style = cInter(9, FontWeight.Bold), color = Color.White)
                    }
                }
            } else {
                Spacer(Modifier.width(28.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            val shape = RoundedCornerShape(
                topStart = 22.dp, topEnd = 22.dp,
                bottomStart = if (m.mine) 22.dp else 8.dp,
                bottomEnd = if (m.mine) 8.dp else 22.dp,
            )
            Column(
                Modifier.clip(shape)
                    .then(if (m.mine) Modifier.background(CHAT.bubbleInk) else Modifier.background(CHAT.bubbleLight))
                    .border(1.dp, if (m.mine) Color.White.copy(alpha = 0.08f) else CHAT.hairline, shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!m.mine && kind == "space" && runHead) {
                    val accent = chatSenderAccent(m.authorName, false)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(999.dp)).background(accent))
                        Text(m.authorName, style = cInter(12, FontWeight.Bold), color = accent)
                    }
                }
                if (!m.replyBody.isNullOrBlank()) {
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(if (m.mine) Color.White.copy(alpha = 0.10f) else CHAT.navy.copy(alpha = 0.05f))
                            .padding(8.dp),
                    ) {
                        Text(m.replyAuthor ?: "", style = cInter(11, FontWeight.Bold), color = if (m.mine) CHAT.gold else CHAT.navy)
                        Text(m.replyBody!!, style = cInter(11), color = if (m.mine) Color.White.copy(alpha = 0.75f) else CHAT.quoteBody, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                when (m.msgType) {
                    "image" -> {
                        FitImage(m.attachmentUrl, modifier = Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(12.dp)))
                        if (m.body.isNotBlank()) Text(m.body, style = cInter(13).copy(lineHeight = 18.sp), color = if (m.mine) Color.White else CHAT.textDark)
                    }
                    "voice" -> {
                        // Waveform + play/pause from attachment_meta { duration, waveform[] };
                        // read defensively — old messages may lack either key.
                        val wave = (m.attachmentMeta?.get("waveform") as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                            .orEmpty()
                        val metaDur = (m.attachmentMeta?.get("duration") as? JsonPrimitive)?.intOrNull
                        val playing = player.playingId == m.messageId
                        val hasAudio = !m.attachmentUrl.isNullOrBlank()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier.size(30.dp).clip(Capsule)
                                    .background(if (m.mine) CHAT.gold else CHAT.navy.copy(alpha = 0.08f))
                                    .clickable(enabled = hasAudio) { m.attachmentUrl?.let { player.toggle(m.messageId, it) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (playing) "Pause voice note" else "Play voice note",
                                    tint = if (m.mine) CHAT.navy else CHAT.navy.copy(alpha = 0.8f),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            WaveformBars(
                                levels = wave,   // empty → 24 mid-height placeholder bars
                                color = if (m.mine) Color.White.copy(alpha = 0.45f) else CHAT.navy.copy(alpha = 0.30f),
                                playedFraction = if (playing) player.progress else 0f,
                                playedColor = CHAT.gold,
                                maxBarHeight = 22.dp,
                            )
                            val shownDur = metaDur ?: player.durationSec.takeIf { playing && it > 0 }
                            if (shownDur != null) {
                                Text(voiceClock(shownDur), style = cInter(10, FontWeight.SemiBold), color = if (m.mine) Color.White.copy(alpha = 0.7f) else CHAT.meta)
                            }
                        }
                    }
                    "video", "file" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Movie, null, tint = if (m.mine) Color.White else CHAT.textDark, modifier = Modifier.size(14.dp))
                        Text(if (m.body.isNotBlank()) m.body else "Shared a video", style = cInter(13), color = if (m.mine) Color.White else CHAT.textDark)
                    }
                    else -> Text(m.body, style = cInter(13).copy(lineHeight = 18.sp), color = if (m.mine) Color.White else CHAT.textDark)
                }
                // Prayer chip — the server tags prayer-request messages (ai_tag = "prayer");
                // iOS renders "🙏 I'm praying" on incoming ones, tapping = a 🙏 reaction.
                if (!m.mine && m.aiTag == "prayer") {
                    val pray = m.reactions.firstOrNull { it.emoji == "🙏" }
                    val active = pray?.mine == true
                    Row(
                        Modifier.clip(Capsule)
                            .background(if (active) CHAT.gold.copy(alpha = 0.12f) else CHAT.white)
                            .border(1.dp, if (active) CHAT.gold.copy(alpha = 0.5f) else CHAT.hairline, Capsule)
                            .clickable { onReact("🙏") }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            when {
                                (pray?.count ?: 0) > 0 -> "🙏 Praying · ${pray?.count}"
                                else -> "🙏 I'm praying"
                            },
                            style = cInter(11, FontWeight.Bold),
                            color = if (active) CHAT.goldDeep else CHAT.navy,
                        )
                    }
                }
                if (m.reactions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        m.reactions.forEach { r ->
                            Row(
                                Modifier.clip(Capsule)
                                    .background(if (r.mine) CHAT.gold.copy(alpha = 0.14f) else CHAT.navy.copy(alpha = 0.06f))
                                    .then(if (r.mine) Modifier.border(1.dp, CHAT.gold.copy(alpha = 0.4f), Capsule) else Modifier)
                                    .clickable { onReact(r.emoji) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(r.emoji, fontSize = 11.sp)
                                Text(r.count.toString(), style = cInter(9, FontWeight.Bold), color = if (r.mine) CHAT.navy else CHAT.ink600)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (m.isEdited) {
                        Text("edited", style = cInter(9).copy(fontStyle = FontStyle.Italic), color = if (m.mine) Color.White.copy(alpha = 0.5f) else CHAT.meta)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(chatMsgTime(m.createdAt), style = cInter(10), color = if (m.mine) Color.White.copy(alpha = 0.6f) else CHAT.meta)
                    if (m.mine) {
                        Spacer(Modifier.width(4.dp))
                        Text("✓✓", style = cInter(9, FontWeight.SemiBold, -1f), color = if ((m.readCount ?: 0) > 0) CHAT.gold else Color.White.copy(alpha = 0.55f))
                    }
                }
            }
        }
    }
}
