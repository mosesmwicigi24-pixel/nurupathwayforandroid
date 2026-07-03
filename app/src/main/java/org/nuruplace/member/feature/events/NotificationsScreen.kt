// Notification center — the member's notifications with a mark-all-read action.
// Unread rows get a gold dot. Port of the iOS NotificationsView.
package org.nuruplace.member.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.MarkReadBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.NotificationRow
import org.nuruplace.member.data.net.NotificationsRes
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.relTime

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.notifications() }) { res: NotificationsRes, reload ->
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            Row(
                Modifier.fillMaxWidth().background(Nuru.navy).padding(Spacing.screen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Back", color = Nuru.onNavy, style = NuruType.cardCta) }
                Text("Notifications", style = NuruType.title, color = Nuru.onNavy, modifier = Modifier.weight(1f).padding(start = Spacing.sm))
                if (res.unread > 0) {
                    TextButton(onClick = {
                        scope.launch { try { Net.client.api.markNotificationsRead(MarkReadBody(null)); reload() } catch (_: Exception) {} }
                    }) { Text("Mark all read", color = Nuru.goldHi, style = NuruType.cardCta) }
                }
            }
            if (res.data.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("You're all caught up.", style = NuruType.body, color = Nuru.ink600) }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(res.data, key = { it.notificationId }) { n -> NotifCard(n) }
                }
            }
        }
    }
}

@Composable
private fun NotifCard(n: NotificationRow) {
    NuruCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n.isUnread) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Nuru.gold))
                Spacer(Modifier.size(Spacing.sm))
            }
            Column(Modifier.weight(1f)) {
                Text(n.payload?.title ?: n.template.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    style = NuruType.rowTitle, color = Nuru.ink, fontWeight = if (n.isUnread) FontWeight.SemiBold else FontWeight.Normal)
                n.payload?.body?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                Text(relTime(n.sentAt ?: n.scheduledFor), style = NuruType.micro, color = Nuru.ink400)
            }
        }
    }
}
