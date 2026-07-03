// Prayer wall detail — one request + its comments, with a comment composer. Port
// of the iOS PrayerWallDetailView.
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PrayerCommentBody
import org.nuruplace.member.data.net.PrayerWallDetail
import org.nuruplace.member.ui.components.AsyncContent
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
fun PrayerWallDetailScreen(postId: String, onBack: () -> Unit) {
    AsyncContent(key = postId, load = { Net.client.api.prayerWallGet(postId) }) { detail: PrayerWallDetail, reload ->
        val scope = rememberCoroutineScope()
        var comment by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val p = detail.post

        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader(p.title ?: "Prayer request", kicker = p.authorName, onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    NuruCard {
                        Text(p.body, style = NuruType.bodyLg, color = Nuru.ink)
                        Spacer(Modifier.height(Spacing.xs))
                        Text("🙏 ${p.prayCount} praying · ${relTime(p.createdAt)}", style = NuruType.micro, color = Nuru.ink400)
                    }
                }
                items(detail.comments, key = { it.commentId }) { c ->
                    NuruCard {
                        Text(c.authorName, style = NuruType.label, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
                        Text(c.body, style = NuruType.body, color = Nuru.ink)
                        Text(relTime(c.createdAt), style = NuruType.micro, color = Nuru.ink400)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().background(Nuru.white).padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(comment, { comment = it }, placeholder = { Text("Encourage them…") }, singleLine = true, shape = RoundedCornerShape(Radii.control), modifier = Modifier.weight(1f))
                Spacer(Modifier.height(Spacing.sm))
                PrimaryButton("Send", loading = busy, enabled = comment.isNotBlank(), modifier = Modifier.width(96.dp), onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try {
                                Net.client.api.prayerWallComment(postId, PrayerCommentBody(UUID.randomUUID().toString(), comment.trim(), UUID.randomUUID().toString()))
                                comment = ""; reload()
                            } catch (_: Exception) {} finally { busy = false }
                        }
                    }
                })
            }
        }
    }
}
