// Talk it Over — the shared plan-day conversation (iOS parity). The day's
// question(s) in a serif prompt card, the family's responses (avatar · name ·
// time · body · encouragement heart), and a pinned composer with an AI compose
// sparkle that drafts / polishes the member's response through the dedicated
// /growth/plans/:id/days/:n/talk/assist endpoint. Everyone walking the plan
// meets here. Backend: growth module (plan_day_talk_posts + likes).
package org.nuruplace.member.feature.grow

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.TalkAssistBody
import org.nuruplace.member.data.net.TalkPost
import org.nuruplace.member.data.net.TalkPostBody
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif
import org.nuruplace.member.util.relTime

@Composable
fun TalkItOverScreen(planId: String, dayNumber: Int, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<TalkPost>() }
    var loaded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var postError by remember { mutableStateOf(false) }
    var aiBusy by remember { mutableStateOf(false) }

    // The day's question(s) come from the plan day's talk segment.
    val prompt by produceState(initialValue = "") {
        value = runCatching {
            Net.client.api.plan(planId).days.firstOrNull { it.dayNumber == dayNumber }
                ?.segments?.firstOrNull { it.kind.lowercase() == "talk" }?.content ?: ""
        }.getOrDefault("")
    }

    suspend fun reload() {
        runCatching { Net.client.api.talkList(planId, dayNumber).data }.getOrNull()?.let {
            posts.clear(); posts.addAll(it)
        }
        loaded = true
    }
    LaunchedEffect(planId, dayNumber) { reload() }

    fun send() {
        val body = draft.trim().take(2000)
        if (body.isBlank() || posting) return
        posting = true
        scope.launch {
            runCatching { Net.client.api.talkPost(planId, dayNumber, TalkPostBody(body)) }
                .onSuccess { posts.add(it); draft = ""; postError = false }
                // The kept draft alone is too quiet a signal that the post
                // never left the phone — say it.
                .onFailure { postError = true }
            posting = false
        }
    }

    fun assist() {
        if (aiBusy) return
        aiBusy = true
        scope.launch {
            val mine = draft.trim().ifBlank { null }
            runCatching { Net.client.api.talkAssist(planId, dayNumber, TalkAssistBody(mine)).suggestion }
                .getOrNull()?.let { if (it.isNotBlank()) draft = it }
            aiBusy = false
        }
    }

    fun like(p: TalkPost) {
        scope.launch {
            runCatching { Net.client.api.talkLike(p.postId) }.getOrNull()?.let { r ->
                val i = posts.indexOfFirst { it.postId == p.postId }
                if (i >= 0) posts[i] = posts[i].copy(liked = r.liked, likeCount = r.likeCount)
            }
        }
    }

    // imePadding: the keyboard resizes the page instead of covering the pinned
    // composer (every other composer in the app already does this; this one
    // was the exception — owner report, 2026-09-04).
    Column(Modifier.fillMaxSize().background(GrowPal.paper).imePadding()) {
        // Navy header.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)).background(GrowPal.heroGradient)
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                Text("DAY $dayNumber", style = gInter(10, FontWeight.Bold, 1.6f), color = GrowPal.gold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(GrowPal.gold.copy(alpha = 0.16f)).border(1.dp, GrowPal.gold.copy(alpha = 0.4f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("💬", style = gInter(16))
                }
                Column {
                    Text("Talk it Over", style = gSerif(24, FontWeight.Medium), color = Color.White)
                    Text(if (posts.isEmpty()) "Be the first to respond" else "${posts.size} response${if (posts.size == 1) "" else "s"}",
                        style = gInter(11), color = Color.White.copy(alpha = 0.65f))
                }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (prompt.isNotBlank()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GrowPal.goldChipBg)
                            .border(1.dp, GrowPal.gold.copy(alpha = 0.3f), RoundedCornerShape(18.dp)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val lines = prompt.split("\n").map { it.trim().removePrefix("—").trim() }.filter { it.isNotBlank() }
                        Text(if (lines.size == 1) "TODAY'S QUESTION" else "TODAY'S QUESTIONS", style = gInter(11, FontWeight.Bold, 1.6f), color = GrowPal.goldChipText)
                        lines.forEach { Text(it, style = gSerif(16, FontWeight.Normal).copy(lineHeight = 22.sp), color = GrowPal.navy) }
                    }
                }
            }
            if (loaded && posts.isEmpty()) {
                item { EmptyTalk() }
            } else {
                items(posts, key = { it.postId }) { p -> TalkRow(p, onLike = { like(p) }) }
            }
        }

        if (postError) {
            Text(
                "Couldn't send — check your connection and try again.",
                style = gInter(11), color = Color(0xFFB91C1C),
                modifier = Modifier.fillMaxWidth().background(GrowPal.white).padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        // Pinned composer with AI sparkle.
        Row(
            Modifier.fillMaxWidth().background(GrowPal.white).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(GrowPal.surface).border(1.dp, GrowPal.border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (draft.isBlank()) Text("Write your response…", style = gInter(13), color = GrowPal.ink300)
                BasicTextField(
                    value = draft, onValueChange = { draft = it },
                    textStyle = gInter(13).copy(color = GrowPal.navy), maxLines = 5,
                    cursorBrush = SolidColor(GrowPal.gold), modifier = Modifier.fillMaxWidth(),
                )
            }
            // AI compose: empty box → an honest first-person starter; my words → polished.
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(GrowPal.gold.copy(alpha = 0.12f)).border(1.dp, GrowPal.gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).clickable(enabled = !aiBusy) { assist() },
                contentAlignment = Alignment.Center,
            ) {
                if (aiBusy) CircularProgressIndicator(color = GrowPal.goldLo, strokeWidth = 1.5.dp, modifier = Modifier.size(15.dp))
                else Icon(Icons.Filled.AutoAwesome, "Compose with AI", tint = GrowPal.goldLo, modifier = Modifier.size(18.dp))
            }
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(GrowPal.goldGrad).clickable(enabled = draft.isNotBlank() && !posting) { send() },
                contentAlignment = Alignment.Center,
            ) {
                if (posting) CircularProgressIndicator(color = GrowPal.navy, strokeWidth = 1.5.dp, modifier = Modifier.size(15.dp))
                else Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = GrowPal.navy, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EmptyTalk() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GrowPal.white).border(1.dp, GrowPal.border, RoundedCornerShape(18.dp)).padding(vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("💬", style = gInter(24))
        Text("No responses yet", style = gInter(13, FontWeight.SemiBold), color = GrowPal.navy)
        Text("Share what God is showing you — your voice encourages the family.", style = gInter(12), color = GrowPal.ink400, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun TalkRow(p: TalkPost, onLike: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GrowPal.white).border(1.dp, GrowPal.border, RoundedCornerShape(18.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Initials avatar (avatar image loading is a follow-up; initials are honest).
        Box(Modifier.size(36.dp).clip(CircleShape).background(GrowPal.goldGrad), contentAlignment = Alignment.Center) {
            Text(initials(p.name), style = gInter(12, FontWeight.Bold), color = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = gInter(13, FontWeight.SemiBold), color = GrowPal.navy, modifier = Modifier.weight(1f))
                Text(relTime(p.createdAt), style = gInter(10), color = GrowPal.ink300)
            }
            Text(p.body, style = gInter(13).copy(lineHeight = 18.sp), color = GrowPal.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clickable { onLike() }.padding(vertical = 4.dp)) {
                Icon(if (p.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Encourage", tint = if (p.liked) GrowPal.gold else GrowPal.ink300, modifier = Modifier.size(15.dp))
                if (p.likeCount > 0) Text("${p.likeCount}", style = gInter(11, FontWeight.SemiBold), color = if (p.liked) GrowPal.goldLo else GrowPal.ink400)
            }
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }.take(2)
    val s = parts.mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
    return s.ifBlank { "?" }
}
