// Prayer wall — the congregation's shared requests, under a navy hero. Compose a
// request via the gold "+" sheet, tap 🙏 to pray (a toggle reaction), open a
// request for its comments. Port of the iOS PrayerWallView.
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.CreatePrayerBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PrayerWallPost
import org.nuruplace.member.data.net.ReactBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif
import org.nuruplace.member.util.relTime
import java.util.UUID

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun PrayerWallScreen(onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    var sort by remember { mutableStateOf("latest") }
    var composing by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(GrowPal.coolPaper).verticalScroll(rememberScrollState()),
    ) {
        // Navy hero
        Box(
            Modifier.fillMaxWidth().height(240.dp)
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
        ) {
            Box(Modifier.matchParentSize().background(GrowPal.heroGradient))
            Box(Modifier.matchParentSize().background(GrowPal.scrimNavy.copy(alpha = 0.55f)))
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .padding(horizontal = 24.dp).padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.40f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(GrowPal.gold)
                        .clickable { composing = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, null, tint = GrowPal.navyDeep, modifier = Modifier.size(18.dp)) }
            }
            Column(
                Modifier.align(Alignment.BottomStart).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("PRAY FOR ONE ANOTHER", style = gInter(11, FontWeight.Medium, 1.8f), color = GrowPal.gold)
                Text("Carry one another", style = gSerif(24, FontWeight.SemiBold), color = Color.White)
                Text(
                    "“Carry each other's burdens, and in this way you will fulfill the law of Christ.” — Galatians 6:2",
                    style = gInter(12), color = Color.White.copy(alpha = 0.55f), maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Body
        AsyncContent(key = sort, load = { Net.client.api.prayerWall(sort).data }) { posts: List<PrayerWallPost>, reload ->
            val scope = rememberCoroutineScope()
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Sort pills
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortPill("Latest", "latest", sort) { sort = it }
                    SortPill("Most prayed", "prayed", sort) { sort = it }
                }
                if (posts.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        Text("No prayer requests yet.", style = gInter(13), color = GrowPal.ink600)
                    }
                } else {
                    posts.forEach { p ->
                        PrayerCard(
                            p,
                            onOpen = { onOpenPost(p.postId) },
                            onPray = {
                                scope.launch {
                                    try {
                                        Net.client.api.prayerWallReact(p.postId, ReactBody("pray")); reload()
                                    } catch (_: Exception) {}
                                }
                            },
                        )
                    }
                }
            }

            if (composing) ComposeSheet(scope, onDismiss = { composing = false }, onPosted = { reload() })
        }
    }
}

@Composable
private fun SortPill(label: String, key: String, sort: String, onSelect: (String) -> Unit) {
    val on = sort == key
    Box(
        Modifier.clip(Capsule)
            .background(if (on) GrowPal.goldChipBg else GrowPal.white)
            .border(1.dp, if (on) GrowPal.gold else GrowPal.border, Capsule)
            .clickable { onSelect(key) }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = gInter(12, FontWeight.Bold), color = if (on) GrowPal.navyDeep else GrowPal.ink600)
    }
}

@Composable
private fun PrayerCard(p: PrayerWallPost, onOpen: () -> Unit, onPray: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GrowPal.white)
            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
            .clickable { onOpen() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(p.authorName, p.authorAvatar, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(p.authorName, style = gInter(12, FontWeight.Bold), color = GrowPal.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relTime(p.createdAt), style = gInter(11), color = GrowPal.ink400)
            }
            if (p.isAnswered) AnsweredChip()
        }
        p.title?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = gInter(16, FontWeight.Medium), color = GrowPal.ink, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            p.body,
            style = gInter(13).copy(lineHeight = 18.sp),
            color = GrowPal.ink600,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(Capsule)
                    .background(if (p.iPrayed) GrowPal.goldChipBg else GrowPal.surface)
                    .border(1.dp, if (p.iPrayed) GrowPal.gold else GrowPal.border, Capsule)
                    .clickable { onPray() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🙏", fontSize = 15.sp)
                    Text(
                        if (p.prayCount > 0) "${p.prayCount} praying" else "Pray",
                        style = gInter(12, FontWeight.Bold),
                        color = if (p.iPrayed) GrowPal.navyDeep else GrowPal.ink600,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.Message, null, tint = GrowPal.ink400, modifier = Modifier.size(14.dp))
                Text((p.commentCount ?: 0).toString(), style = gInter(11), color = GrowPal.ink400)
            }
        }
    }
}

@Composable
internal fun Avatar(name: String, url: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(GrowPal.tintBlue),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(
                initials(name),
                style = gInter((size.value * 0.4f).toInt().coerceAtLeast(9), FontWeight.SemiBold),
                color = GrowPal.navyMid,
            )
        }
    }
}

@Composable
private fun AnsweredChip() {
    Box(
        Modifier.clip(Capsule).background(GrowPal.successBg).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.CheckCircle, null, tint = GrowPal.successText, modifier = Modifier.size(11.dp))
            Text("Answered", style = gInter(11, FontWeight.Medium), color = GrowPal.successText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeSheet(scope: CoroutineScope, onDismiss: () -> Unit, onPosted: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = GrowPal.white) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Share a prayer", style = gSerif(18, FontWeight.SemiBold), color = GrowPal.navy)
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GrowPal.coolPaper)
                    .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                if (title.isBlank()) Text("Title (optional)", style = gInter(14), color = GrowPal.ink400)
                BasicTextField(
                    title, { title = it },
                    textStyle = gInter(14).copy(color = GrowPal.ink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GrowPal.coolPaper)
                    .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                if (body.isBlank()) Text("Share what's on your heart…", style = gInter(14), color = GrowPal.ink400)
                BasicTextField(
                    body, { body = it },
                    textStyle = gInter(14).copy(color = GrowPal.ink),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                )
            }
            Box(
                Modifier.fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (body.isNotBlank()) GrowPal.navyDeep else GrowPal.navyDeep.copy(alpha = 0.5f))
                    .clickable(enabled = body.isNotBlank()) {
                        val t = title
                        val b = body
                        scope.launch {
                            try {
                                Net.client.api.createPrayerWallPost(
                                    CreatePrayerBody(UUID.randomUUID().toString(), t.ifBlank { null }, b.trim(), UUID.randomUUID().toString()),
                                )
                                onPosted()
                            } catch (_: Exception) {}
                        }
                        onDismiss()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Post to wall", style = gInter(16, FontWeight.Medium), color = Color.White)
            }
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
