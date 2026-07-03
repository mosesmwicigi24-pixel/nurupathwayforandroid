// Announcements — the member's announcements list + a detail view (image gallery
// + body, marked opened). Port of the iOS announcements + AnnouncementDetailView.
package org.nuruplace.member.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.nuruplace.member.ui.components.FitImage
import org.nuruplace.member.data.net.AnnouncementDetail
import org.nuruplace.member.data.net.MyAnnouncement
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.relTime

@Composable
fun AnnouncementsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    AsyncContent(load = { Net.client.api.myAnnouncements().data }) { rows: List<MyAnnouncement>, _ ->
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Announcements", kicker = "From your church", onBack = onBack)
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No announcements yet.", style = NuruType.body, color = Nuru.ink600) }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(rows, key = { it.announcementId }) { a ->
                        NuruCard(modifier = Modifier.clickable { onOpen(a.announcementId) }) {
                            a.primaryImageUrl?.let {
                                FitImage(it)
                                Spacer(Modifier.height(Spacing.sm))
                            }
                            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!a.opened) { Box(Modifier.size(8.dp).clip(CircleShape).background(Nuru.gold)); Spacer(Modifier.size(Spacing.sm)) }
                                Text(a.title, style = NuruType.cardTitle, color = Nuru.ink, fontWeight = if (a.opened) FontWeight.Normal else FontWeight.SemiBold)
                            }
                            Text(a.body, style = NuruType.body, color = Nuru.ink600, maxLines = 2)
                            Text(relTime(a.sentAt), style = NuruType.micro, color = Nuru.ink400)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnnouncementDetailScreen(announcementId: String, onBack: () -> Unit) {
    LaunchedEffect(announcementId) { runCatching { Net.client.api.openAnnouncement(announcementId) } }
    AsyncContent(key = announcementId, load = { Net.client.api.announcement(announcementId) }) { a: AnnouncementDetail, _ ->
        Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
            a.primaryImageUrl?.let { FitImage(it) }
                ?: ScreenHeader(a.title, kicker = "Announcement", onBack = onBack)
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                if (a.primaryImageUrl != null) Text(a.title, style = NuruType.title, color = Nuru.ink)
                Text(a.body, style = NuruType.bodyLg, color = Nuru.ink)
                a.videoUrl?.let { org.nuruplace.member.ui.components.InlineVideo(it, modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(org.nuruplace.member.ui.theme.Radii.card))) }
                a.images.drop(1).forEach { FitImage(it) }
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}
