// Prayer wall detail — one request + its comments, under a navy header, with a
// pinned comment composer. Port of the iOS PrayerWallDetailView.
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PrayerCommentBody
import org.nuruplace.member.data.net.PrayerWallDetail
import org.nuruplace.member.data.net.ReactBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif
import org.nuruplace.member.util.relTime
import java.util.UUID

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun PrayerWallDetailScreen(postId: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(GrowPal.coolPaper)) {
        AsyncContent(key = postId, load = { Net.client.api.prayerWallGet(postId) }) { detail: PrayerWallDetail, reload ->
            val scope = rememberCoroutineScope()
            val p = detail.post

            // Header
            Row(
                Modifier.fillMaxWidth().background(GrowPal.navy)
                    .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Text("Prayer", style = gSerif(20, FontWeight.SemiBold), color = Color.White)
            }

            // Content
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(GrowPal.white)
                            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Avatar(p.authorName, p.authorAvatar, 40.dp)
                            Column {
                                Text(p.authorName, style = gInter(12, FontWeight.Bold), color = GrowPal.ink)
                                Text(relTime(p.createdAt), style = gInter(11), color = GrowPal.ink400)
                            }
                        }
                        p.title?.let {
                            Text(it, style = gSerif(18, FontWeight.SemiBold), color = GrowPal.navy, modifier = Modifier.padding(top = 8.dp))
                        }
                        Text(
                            p.body,
                            style = gInter(16).copy(lineHeight = 24.sp),
                            color = GrowPal.ink,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        // Quick-reaction bar — mirrors iOS PrayerWallDetailView: five fixed
                        // emoji chips fed by the wire's per-emoji reactions[] (count + mine).
                        Row(
                            Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            QUICK_REACTIONS.forEach { emoji ->
                                val r = p.reactions.firstOrNull { it.emoji == emoji }
                                val mine = r?.mine == true
                                Box(
                                    Modifier.clip(Capsule)
                                        .background(if (mine) GrowPal.goldChipBg else GrowPal.surface)
                                        .border(1.dp, if (mine) GrowPal.gold else GrowPal.border, Capsule)
                                        .clickable {
                                            scope.launch {
                                                try {
                                                    Net.client.api.prayerWallReact(p.postId, ReactBody(emoji)); reload()
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(emoji, fontSize = 15.sp)
                                        if ((r?.count ?: 0) > 0) {
                                            Text(
                                                "${r?.count}",
                                                style = gInter(11, FontWeight.Bold),
                                                color = if (mine) GrowPal.navyDeep else GrowPal.ink600,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                items(detail.comments, key = { it.commentId }) { c ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(GrowPal.white)
                            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Avatar(c.authorName, c.authorAvatar, 28.dp)
                            Column {
                                Text(c.authorName, style = gInter(12, FontWeight.Bold), color = GrowPal.ink)
                                Text(relTime(c.createdAt), style = gInter(11), color = GrowPal.ink400)
                            }
                        }
                        Text(
                            c.body,
                            style = gInter(13).copy(lineHeight = 18.sp),
                            color = GrowPal.ink600,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            // Composer bar
            var text by remember { mutableStateOf("") }
            Row(
                Modifier.fillMaxWidth().background(GrowPal.coolPaper)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GrowPal.white)
                        .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    if (text.isBlank()) Text("Encourage them…", style = gInter(14), color = GrowPal.ink400)
                    BasicTextField(
                        text, { text = it },
                        textStyle = gInter(14).copy(color = GrowPal.ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(GrowPal.navy)
                        .clickable {
                            if (text.isNotBlank()) {
                                val body = text
                                scope.launch {
                                    try {
                                        Net.client.api.prayerWallComment(
                                            postId,
                                            PrayerCommentBody(UUID.randomUUID().toString(), body.trim(), UUID.randomUUID().toString()),
                                        )
                                        reload()
                                    } catch (_: Exception) {}
                                }
                                text = ""
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

// Fixed quick-reaction palette — must match iOS PrayerWallDetailView.quickReactions.
// The server accepts arbitrary emoji but only 🙏 feeds pray_count (PRAY constant).
private val QUICK_REACTIONS = listOf("🙏", "❤️", "🕊️", "🙌", "✨")
