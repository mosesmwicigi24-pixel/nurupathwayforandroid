// "You" tab — ONE outer segmented capsule over four segments (docs/
// PARTNERS_PROGRAMME.md §0): Community · Departments · Profile · Settings.
// Each segment renders its EXISTING screen unmodified so every inner
// navigation/behavior (thread open, prayer room, badge gallery, sign-out,
// notification prefs, …) is preserved exactly as it was.
//
// History: the L4 tab restructure (docs/LIVE_STREAMING.md) first fused Chat,
// Events, Give and Profile here. The Partners programme restructure moved
// Events and Give back out to their own bottom tabs (EventsScreen,
// GiveTabScreen) and promoted Settings from behind Profile's gear; Phase 3
// (spec §4, §6) filled the Departments segment (feature/departments).
//
// The "chat", "departments" and "profile" routes stay registered in
// MainShell's NavHost and render THIS screen pre-selecting the matching
// segment — every existing nav.navigate("chat"/"profile") call site (FCM
// pushes, NotificationsScreen.routeFor, HomeScreen.onSelectTab,
// CommunityHubScreen) keeps working.
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
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import org.nuruplace.member.feature.departments.DepartmentsSegment
import org.nuruplace.member.feature.profile.ProfileScreen
import org.nuruplace.member.feature.profile.SettingsScreen
import org.nuruplace.member.ui.theme.Spacing

private val Capsule = RoundedCornerShape(999.dp)

/** The four segments — `route` is the NavHost route that pre-selects it (see
 *  MainShell) for Chat, Departments and Profile; "settings" is ALSO the
 *  pushed full-screen route behind Profile's gear. */
enum class YouSegment(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "Community", Icons.Filled.Groups),   // name + route stay: deep links resolve to them
    Departments("departments", "Departments", Icons.Filled.Diversity3),
    Profile("profile", "Profile", Icons.Filled.Person),
    Settings("settings", "Settings", Icons.Filled.Settings),
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
    // segment the member was actually on. Landing fresh on one of the aliased
    // routes always re-seeds to THAT segment.
    var segment by rememberSaveable(initial) { mutableStateOf(initial) }

    Column(Modifier.fillMaxSize()) {
        // Outer segmented capsule — the EXACT same capsule/Segment idiom
        // ChatScreen's own inner tabs use, one level up (and the same one the
        // Give tab wears, GiveTabScreen). Horizontal-scroll (not equal-weight)
        // for a consistent capsule size per segment regardless of label length.
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
                YouSegment.Chat -> org.nuruplace.member.feature.community.CommunitySegment(
                    chatUnread = chatUnread,
                    talk = {
                        ChatInboxScreen(
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
                    },
                    pray = {
                        org.nuruplace.member.feature.community.PrayerRoomScreen(
                            embedded = true,
                            onOpenPost = { onNavigate("prayer-wall/$it") },
                        )
                    },
                )
                YouSegment.Departments -> DepartmentsSegment(onOpen = { onNavigate("department/$it") })
                YouSegment.Profile -> ProfileScreen(me, onOpen = { onNavigate(it) }, onSignOut = onSignOut)
                // Embedded: the capsule is the chrome, so no cream header/back.
                YouSegment.Settings -> SettingsScreen(onBack = {}, onOpen = { onNavigate(it) }, embedded = true)
            }
        }
    }
}
