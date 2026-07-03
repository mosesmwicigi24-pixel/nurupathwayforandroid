// Mentorship — the member's assigned discipler from GET /growth/mentor: the
// discipler card, the next-meeting card, and the conversation-history notes.
// Falls back to an invite card when no discipler is paired yet. Port of the iOS
// MentorView (message CTA omitted — DM deep-link lands with the chat work).
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.nuruplace.member.data.net.MentorInfo
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.fmtEventTime

@Composable
fun MentorScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.mentor() }) { info: MentorInfo, _ ->
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Your discipler", kicker = "Mentorship", onBack = onBack)
            Column(Modifier.fillMaxWidth().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                val m = info.mentor
                if (m == null) {
                    NuruCard {
                        Text("No discipler yet", style = NuruType.cardTitle, color = Nuru.ink)
                        Spacer(Modifier.height(Spacing.xs))
                        Text("When your leader pairs you with a discipler, you'll see them here along with your meeting notes.", style = NuruType.caption, color = Nuru.ink600)
                    }
                } else {
                    NuruCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarCircle(m.avatarUrl, m.fullName)
                            Spacer(Modifier.size(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Kicker("Your discipler")
                                Text(m.fullName, style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.Bold)
                                m.cellName?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                            }
                        }
                    }
                    val nextAt = info.nextMeetingAt
                    if (!nextAt.isNullOrBlank()) {
                        NuruCard {
                            Kicker("Next meeting")
                            Spacer(Modifier.height(Spacing.xs))
                            Text(fmtEventTime(nextAt), style = NuruType.cardTitle, color = Nuru.navyDeep)
                        }
                    }
                    if (info.notes.isNotEmpty()) {
                        NuruCard {
                            Kicker("Conversation history")
                            info.notes.forEach { note ->
                                Spacer(Modifier.height(Spacing.sm))
                                note.topic?.let { Text(it, style = NuruType.rowTitle, color = Nuru.ink) }
                                Text(note.note, style = NuruType.body, color = Nuru.ink600)
                                note.metAt?.let { Text(fmtEventTime(it), style = NuruType.micro, color = Nuru.ink400) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AvatarCircle(url: String?, name: String, size: Int = 48) {
    if (!url.isNullOrBlank()) {
        AsyncImage(model = url, contentDescription = name, modifier = Modifier.size(size.dp).clip(CircleShape))
    } else {
        Box(Modifier.size(size.dp).clip(CircleShape).background(Nuru.goldTint), contentAlignment = Alignment.Center) {
            Text(name.firstOrNull()?.uppercase() ?: "?", style = NuruType.title, color = Nuru.goldLo)
        }
    }
}
