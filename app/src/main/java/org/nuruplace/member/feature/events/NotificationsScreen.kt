// Notification center — ported to the Figma NotificationsScreen. A white app bar
// with an unread count + "Mark all read" navy pill, then rows carrying a
// category-toned icon chip (info · success · warning · security — §B2), a gold
// left-accent + pulsing dot when unread, and reward rows (badge/certificate/
// level) in a gold gift treatment. Deep-links mirror the iOS routing.
package org.nuruplace.member.feature.events

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.MarkReadBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.NotificationRow
import org.nuruplace.member.data.net.NotificationsRes
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.relTime

/** Map a notification to an in-app destination (mirrors the iOS deep-link routing). */
private fun routeFor(n: NotificationRow): String? {
    n.payload?.moduleId?.let { return "module/$it" }
    n.payload?.announcementId?.let { return "announcement/$it" }
    n.payload?.levelNumber?.let { return "pathway" }
    val t = n.template.lowercase()
    return when {
        "prayer" in t -> "prayer-wall"
        "verse" in t || "memory" in t -> "memory-verses"
        "devotional" in t -> "devotional"
        "give" in t || "giving" in t -> "give"
        "event" in t -> "events"
        else -> null
    }
}

/** The four backend notification categories → (glyph, foreground, tint). §B2. */
private data class Tone(val glyph: String, val fg: Color, val bg: Color, val reward: Boolean = false)

private fun toneFor(template: String): Tone {
    val t = template.lowercase()
    return when {
        "badge" in t -> Tone("🏅", Nuru.navy, Nuru.gold, reward = true)
        "certificate" in t || "cert" in t -> Tone("📜", Nuru.navy, Nuru.gold, reward = true)
        "level" in t || "advanced" in t -> Tone("📈", Nuru.navy, Nuru.gold, reward = true)
        "reflection" in t && ("return" in t || "revis" in t) -> Tone("✍️", Nuru.warning, Nuru.warningBg)     // warning
        "event" in t || "reminder" in t -> Tone("📅", Nuru.info, Nuru.infoBg)                                 // info
        "announcement" in t || "announce" in t -> Tone("📣", Nuru.info, Nuru.infoBg)                          // info
        "system" in t || "security" in t || "login" in t || "password" in t -> Tone("⚙️", Nuru.ink600, Nuru.inputBg) // security
        "prayer" in t || "verse" in t || "devotional" in t || "give" in t -> Tone("🌿", Nuru.info, Nuru.infoBg)
        else -> Tone("🔔", Nuru.success, Nuru.successBg)                                                       // success default
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    AsyncContent(load = { Net.client.api.notifications() }) { res: NotificationsRes, reload ->
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            // White app bar with count + Mark-all pill.
            Row(
                Modifier.fillMaxWidth().background(Nuru.white).padding(horizontal = Spacing.sm).padding(top = Spacing.lg, bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nuru.navy) }
                Column(Modifier.weight(1f)) {
                    Text("Notifications", style = NuruType.cardTitle, color = Nuru.ink)
                    Text(if (res.unread > 0) "${res.unread} unread" else "All caught up ✨", style = NuruType.caption, color = Nuru.ink600)
                }
                if (res.unread > 0) {
                    Box(
                        Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.navy)
                            .clickable {
                                scope.launch { try { Net.client.api.markNotificationsRead(MarkReadBody(null)); reload() } catch (_: Exception) {} }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("✓ Mark all read", style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold) }
                }
            }

            if (res.data.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(Radii.card)).background(Nuru.white), contentAlignment = Alignment.Center) {
                        Text("✦", style = NuruType.display, color = Nuru.gold)
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Text("You're all caught up", style = NuruType.cardTitle, color = Nuru.ink)
                    Text("New encouragement, reflections, and reminders land here.", style = NuruType.caption, color = Nuru.ink600)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(res.data, key = { it.notificationId }) { n ->
                        NotifRow(n, onClick = { routeFor(n)?.let(onNavigate) })
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Nuru.border))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifRow(n: NotificationRow, onClick: () -> Unit) {
    val tone = toneFor(n.template)
    Box(Modifier.fillMaxWidth().background(if (n.isUnread) Nuru.white else Color.Transparent).clickable { onClick() }) {
        if (n.isUnread) Box(Modifier.padding(vertical = Spacing.sm).size(width = 4.dp, height = 40.dp).clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(Nuru.gold).align(Alignment.CenterStart))
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.base), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(Radii.control)).background(tone.bg), contentAlignment = Alignment.Center) {
                Text(tone.glyph, style = NuruType.body)
            }
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        n.payload?.title ?: n.template.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = NuruType.rowTitle, color = Nuru.ink, modifier = Modifier.weight(1f),
                        fontWeight = if (n.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(relTime(n.sentAt ?: n.scheduledFor), style = NuruType.micro, color = Nuru.ink400)
                }
                n.payload?.body?.let { Text(it, style = NuruType.caption, color = Nuru.ink600, maxLines = 2) }
                if (tone.reward && n.isUnread) {
                    Spacer(Modifier.height(Spacing.xs))
                    Box(Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.goldTint).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("🎁 Tap to open your gift", style = NuruType.micro, color = Nuru.goldChipText)
                    }
                }
            }
            if (n.isUnread) {
                Spacer(Modifier.size(Spacing.sm))
                Box(Modifier.size(8.dp).clip(CircleShape).background(Nuru.gold))
            }
        }
    }
}
