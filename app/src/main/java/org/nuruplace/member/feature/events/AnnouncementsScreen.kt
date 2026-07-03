// Announcements — the member's "See all" announcements list + a detail view (hero,
// body, optional video + image gallery, marked opened). Port of the iOS
// AnnouncementsView + AnnouncementDetailView, on the EV events palette.
package org.nuruplace.member.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.nuruplace.member.data.net.AnnouncementDetail
import org.nuruplace.member.data.net.MyAnnouncement
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.FitImage
import org.nuruplace.member.util.relTime

// ─────────────────────────────────────────────────────────────────────────────
// Announcements list — the "See all" screen. Cream sub-page header + a column of
// image-topped cards. Unread rows carry a gold dot.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnnouncementsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(EV.paper)) {
        EvSubHeader(
            eyebrow = "ANNOUNCEMENTS",
            title = "Announcements",
            subtitle = "From your church",
            onBack = onBack,
        )
        AsyncContent(load = { Net.client.api.myAnnouncements().data }) { rows: List<MyAnnouncement>, _ ->
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No announcements yet.", style = evInter(14), color = EV.secondary)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rows, key = { it.announcementId }) { a ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(EV.white)
                                .border(1.dp, EV.border, RoundedCornerShape(22.dp))
                                .clickable { onOpen(a.announcementId) },
                        ) {
                            a.primaryImageUrl?.let {
                                FitImage(it, modifier = Modifier.clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)))
                            }
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!a.opened) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(EV.gold))
                                    }
                                    Text(a.title, style = evSerif(16, FontWeight.SemiBold), color = EV.ink, maxLines = 2)
                                }
                                Text(
                                    a.body,
                                    style = evInter(13),
                                    color = EV.secondary,
                                    maxLines = 2,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(
                                    relTime(a.sentAt),
                                    style = evInter(11),
                                    color = EV.tertiary,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Announcement detail — cream sub-page header + hero + body + optional video and
// horizontal image gallery. Marks the announcement opened on enter.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnnouncementDetailScreen(announcementId: String, onBack: () -> Unit) {
    LaunchedEffect(announcementId) { runCatching { Net.client.api.openAnnouncement(announcementId) } }
    AsyncContent(key = announcementId, load = { Net.client.api.announcement(announcementId) }) { a: AnnouncementDetail, _ ->
        val whenString = evZdt(a.sentAt)
            ?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
            ?: ""
        Column(
            Modifier.fillMaxSize().background(EV.paper).verticalScroll(rememberScrollState()),
        ) {
            EvSubHeader(
                eyebrow = "ANNOUNCEMENT",
                title = a.title.ifBlank { "Announcement" },
                subtitle = whenString,
                onBack = onBack,
            )
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Hero
                a.primaryImageUrl?.let {
                    FitImage(
                        it,
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, EV.border, RoundedCornerShape(22.dp)),
                    )
                }
                // Body (plain text)
                Text(a.body, style = evInter(16).copy(lineHeight = 24.sp), color = EV.ink)
                // Video
                a.videoUrl?.let {
                    Box(
                        Modifier.fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(EV.navyCard),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
                // Gallery rail
                val gallery = a.images.ifEmpty { a.galleryImageUrls ?: emptyList() }
                if (gallery.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        gallery.forEach { url ->
                            Box(Modifier.height(170.dp).clip(RoundedCornerShape(14.dp))) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.height(170.dp).widthIn(min = 240.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
