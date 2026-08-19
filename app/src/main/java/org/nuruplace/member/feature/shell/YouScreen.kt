// "You" tab (L4 tab restructure, docs/LIVE_STREAMING.md) — fuses the old
// Chat, Events, Give and Profile top-level tabs behind ONE outer segmented
// capsule (Chat is the default segment), each segment rendering its
// EXISTING screen completely unmodified so every inner navigation/behavior
// (thread open, event RSVP, giving receipt, badge gallery, sign-out, …) is
// preserved exactly as it was as a standalone tab. The four old routes
// ("chat"/"events"/"give"/"profile") stay registered in MainShell's NavHost
// and all render THIS screen pre-selecting the matching segment — every
// existing nav.navigate(...) call site, FCM push destination, and
// notification/shortcut route (NuruMessagingService.destFor,
// NotificationsScreen.routeFor, HomeScreen.onSelectTab, CommunityHubScreen,
// shortcuts.xml) keeps working with zero edits.
package org.nuruplace.member.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.feature.community.CHAT
import org.nuruplace.member.feature.community.ChatInboxScreen
import org.nuruplace.member.feature.community.Segment
import org.nuruplace.member.feature.events.EventsScreen
import org.nuruplace.member.feature.give.GivingScreen
import org.nuruplace.member.feature.profile.ProfileScreen
import org.nuruplace.member.ui.theme.Spacing

private val Capsule = RoundedCornerShape(999.dp)

/** The four fused segments — `route` doubles as the standalone NavHost route
 *  that pre-selects it (see MainShell), so the two never drift apart. */
enum class YouSegment(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    Events("events", "Events", Icons.Filled.CalendarMonth),
    Give("give", "Give", Icons.Filled.VolunteerActivism),
    Profile("profile", "Profile", Icons.Filled.Person),
}

@Composable
fun YouScreen(
    initial: YouSegment,
    me: MeResponse?,
    isStaff: Boolean,
    pastoralEligible: Boolean,
    chatUnread: Int,
    onChatUnreadChange: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    // rememberSaveable (not plain remember) so rotating the device or a
    // process restart (Android killing a backgrounded app) restores the
    // segment the member was actually on — same durability every other
    // rememberSaveable-backed choice in this app gets. Landing fresh on one
    // of the four aliased routes always re-seeds to THAT segment (matches
    // the old behavior of each being its own tab).
    var segment by rememberSaveable(initial) { mutableStateOf(initial) }

    Column(Modifier.fillMaxSize()) {
        // Outer segmented capsule — the EXACT same capsule/Segment idiom
        // ChatScreen's own inner tabs use (My Space/Chat/My Discipler/…), one
        // level up: this picks WHICH full screen renders below, that one
        // picks a section within Chat. Horizontal-scroll (not equal-weight)
        // for the same reason ChatScreen uses it — a consistent capsule size
        // per segment regardless of label length, never a stretched pill.
        Row(
            Modifier
                .fillMaxWidth()
                .background(CHAT.paper)
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .clip(Capsule)
                    .background(CHAT.white.copy(alpha = 0.7f))
                    .border(1.dp, CHAT.border, Capsule)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                YouSegment.entries.forEach { s ->
                    val count = if (s == YouSegment.Chat) chatUnread.takeIf { it > 0 } else null
                    Segment(label = s.label, icon = s.icon, count = count, selected = segment == s) { segment = s }
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (segment) {
                YouSegment.Chat -> ChatInboxScreen(
                    onOpenThread = { onNavigate("chat/$it") },
                    onNewMessage = { onNavigate("new-message") },
                    onOpenAssistant = { onNavigate("assistant") },
                    onOpenNotifications = { onNavigate("notifications") },
                    isStaff = isStaff,
                    pastoralEligible = pastoralEligible,
                    onOpenBroadcast = { onNavigate("broadcast/$it") },
                    onOpenThreadWithContext = { id, ctx -> onNavigate("chat/$id?ctx=$ctx") },
                    onUnreadChange = onChatUnreadChange,
                )
                YouSegment.Events -> EventsScreen(
                    onOpenEvent = { id, end -> onNavigate("event/$id?end=${android.net.Uri.encode(end ?: "")}") },
                    onOpenCalendar = { onNavigate("events-calendar") },
                    onOpenAnnouncement = { onNavigate("announcement/$it") },
                    onOpenAnnouncements = { onNavigate("announcements") },
                    onOpenNotifications = { onNavigate("notifications") },
                    onOpenAttendance = { onNavigate("attendance") },
                )
                YouSegment.Give -> GivingScreen(
                    onBack = {}, // embedded segment, not a pushed screen — no back affordance (unused by GivingScreen today anyway)
                    onOpenStatement = { onNavigate("statement") },
                    onOpenSchedules = { onNavigate("schedules") },
                )
                YouSegment.Profile -> ProfileScreen(me, onOpen = { onNavigate(it) }, onSignOut = onSignOut)
            }
        }
    }
}
