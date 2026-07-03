// Prayer wall — the congregation's shared requests. Compose a request, tap 🙏 to
// pray (a toggle reaction), open a request for its comments. Port of the iOS
// PrayerWallView + PrayerWallDetailView.
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.CreatePrayerBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PrayerWallPost
import org.nuruplace.member.data.net.ReactBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.relTime
import java.util.UUID

@Composable
fun PrayerWallScreen(onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    AsyncContent(load = { Net.client.api.prayerWall().data }) { posts: List<PrayerWallPost>, reload ->
        val scope = rememberCoroutineScope()
        var body by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Prayer wall", kicker = "Community", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    NuruCard {
                        OutlinedTextField(body, { body = it }, label = { Text("Share a prayer request…") }, minLines = 2, shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Spacing.sm))
                        PrimaryButton("Share request", loading = busy, enabled = body.isNotBlank(), onClick = {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    try {
                                        Net.client.api.createPrayerWallPost(CreatePrayerBody(UUID.randomUUID().toString(), null, body.trim(), UUID.randomUUID().toString()))
                                        body = ""; reload()
                                    } catch (_: Exception) {} finally { busy = false }
                                }
                            }
                        })
                    }
                }
                items(posts, key = { it.postId }) { p -> PostCard(p, onOpen = { onOpenPost(p.postId) }, onPrayed = reload) }
            }
        }
    }
}

@Composable
private fun PostCard(p: PrayerWallPost, onOpen: () -> Unit, onPrayed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    NuruCard(modifier = Modifier.clickable { onOpen() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.authorName, style = NuruType.label, color = Nuru.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(relTime(p.createdAt), style = NuruType.micro, color = Nuru.ink400)
        }
        p.title?.let { Text(it, style = NuruType.rowTitle, color = Nuru.ink) }
        Spacer(Modifier.height(2.dp))
        Text(p.body, style = NuruType.body, color = Nuru.ink)
        if (p.isAnswered) { Spacer(Modifier.height(Spacing.xs)); Text("🙌 Answered", style = NuruType.micro, color = Nuru.successText) }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(Radii.pill))
                    .background(if (p.iPrayed) Nuru.goldTint else Nuru.surface)
                    .border(1.dp, if (p.iPrayed) Nuru.gold else Nuru.border, RoundedCornerShape(Radii.pill))
                    .clickable {
                        if (!busy) {
                            busy = true
                            scope.launch { try { Net.client.api.prayerWallReact(p.postId, ReactBody("pray")); onPrayed() } catch (_: Exception) {} finally { busy = false } }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🙏 ${p.prayCount}", style = NuruType.micro, color = if (p.iPrayed) Nuru.goldChipText else Nuru.ink600)
            }
            p.commentCount?.let { Text("💬 $it", style = NuruType.micro, color = Nuru.ink400) }
        }
    }
}
