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
// pushes, NotificationsScreen.routeFor, HomeScreen.onSelectTab) keeps
// working.
package org.nuruplace.member.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import org.nuruplace.member.ui.components.CappedFontScale
import org.nuruplace.member.ui.components.EVERYDAY_MAX_FONT_SCALE
import org.nuruplace.member.ui.components.FirstThatFits
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.icons.Lucide

private val Capsule = RoundedCornerShape(999.dp)

/** The four segments — `route` is the NavHost route that pre-selects it (see
 *  MainShell) for Chat, Departments and Profile; "settings" is ALSO a pushed
 *  full-screen route (kept for links). The Settings segment's gear is the
 *  tab's one gear (EXPERIENCE.md §6.2) — Profile no longer carries its own. */
enum class YouSegment(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "Community", Lucide.Users),   // name + route stay: deep links resolve to them
    Departments("departments", "Departments", Lucide.Users2),
    Profile("profile", "Profile", Lucide.User),
    Settings("settings", "Settings", Lucide.Settings),
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
            verticalAlignment = Alignment.CenterVertically,
            // The capsule at the start, the gear at the far right — where
            // every tab keeps its header's last button (§8.1 rule 2).
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Every segment in view at every size (final walk M10, C3): the
            // fullest form that fits — icons and words, then words alone,
            // then the chosen one's words with the others' icons — as iOS's
            // CapsuleSegmentBar. It scrolled, and cut "Profile" at its edge.
            // A bar's words stop at the everyday ceiling (§9.6 #4).
            val segments = YouSegment.entries.filter { it != YouSegment.Settings }
            val capsule: @Composable (form: Int) -> Unit = { form ->
                Row(
                    Modifier
                        .clip(Capsule)
                        .background(CHAT.white.copy(alpha = 0.7f))
                        .border(1.dp, CHAT.border, Capsule)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    segments.forEach { s ->
                        val count = if (s == YouSegment.Chat) chatUnread.takeIf { it > 0 } else null
                        val chosen = segment == s
                        Segment(
                            label = s.label, icon = s.icon, count = count, selected = chosen,
                            showIcon = form != 1, showLabel = form != 2 || chosen,
                        ) { segment = s }
                    }
                }
            }
            CappedFontScale(EVERYDAY_MAX_FONT_SCALE) {
                FirstThatFits(
                    Modifier.weight(1f, fill = false).padding(end = 8.dp),
                    candidates = listOf({ capsule(0) }, { capsule(1) }, { capsule(2) }),
                )
            }
            // Settings: the tab's one gear, pinned beside the bar — always in
            // view and reachable at every text size, and named for TalkBack
            // (EXPERIENCE.md §9.7 M10). It sat at the scrolling bar's end:
            // cut at the default size, scrolled away at Large, unlabelled.
            val onSettings = segment == YouSegment.Settings
            Box(
                Modifier.size(44.dp).clip(Capsule)
                    .then(if (onSettings) Modifier.background(CHAT.selectedSeg) else Modifier.background(CHAT.white.copy(alpha = 0.7f)))
                    .border(1.dp, CHAT.border, Capsule)
                    .clickable(onClickLabel = "Open Settings") { segment = YouSegment.Settings }
                    .semantics { selected = onSettings },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Settings, contentDescription = "Settings", tint = if (onSettings) Color.White else CHAT.ink600, modifier = Modifier.size(18.dp))
            }
        }

        Box(Modifier.weight(1f)) {
            when (segment) {
                // Community: the inbox and its one switcher (EXPERIENCE.md §9.2
                // #13) — the Talk | Pray row that stood over it is gone; Pray
                // is a door inside, to the prayer room's own page.
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
                    onOpenPrayerRoom = { onNavigate("prayer-room") },
                )
                YouSegment.Departments -> DepartmentsSegment(onOpen = { onNavigate("department/$it") })
                YouSegment.Profile -> ProfileScreen(me, onOpen = { onNavigate(it) }, onSignOut = onSignOut)
                // Embedded: the capsule is the chrome, so no cream header/back.
                YouSegment.Settings -> SettingsScreen(onBack = {}, onOpen = { onNavigate(it) }, embedded = true)
            }
        }
    }
}
