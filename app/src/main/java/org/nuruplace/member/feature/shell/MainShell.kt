// Authed shell — bottom bar (Home · Pathway · Plans · You · Live) over a
// NavHost. Pathway carries its own stack: Levels → Level → Module →
// Quiz/Exam. "You" (L4 tab restructure, docs/LIVE_STREAMING.md) fuses the
// old Chat/Events/Give/Profile tabs behind one segmented capsule — see
// YouScreen.kt. "Live" is broadcaster-only (canGoLive(me)) and hosts Go Live
// + Replays — see LiveTabScreen.kt. Port of the iOS RootView tab shell.
package org.nuruplace.member.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.nuruplace.member.auth.AuthStore
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.SubmitBody
import org.nuruplace.member.feature.grow.DevotionalScreen
import org.nuruplace.member.feature.grow.GrowHubScreen
import org.nuruplace.member.feature.grow.MemoryVerseScreen
import org.nuruplace.member.feature.grow.PlanDayScreen
import org.nuruplace.member.feature.grow.PlanDetailScreen
import org.nuruplace.member.feature.grow.PlanSegmentScreen
import org.nuruplace.member.feature.grow.ReadingPlansScreen
import org.nuruplace.member.feature.grow.VerseLibraryScreen
import org.nuruplace.member.feature.community.ChatThreadScreen
import org.nuruplace.member.feature.community.CommunityHubScreen
import org.nuruplace.member.feature.community.PrayerWallDetailScreen
import org.nuruplace.member.feature.events.AllEventsCalendarScreen
import org.nuruplace.member.feature.events.EventDetailScreen
import org.nuruplace.member.feature.events.NotificationsScreen
import org.nuruplace.member.feature.give.GivingReceiptScreen
import org.nuruplace.member.feature.give.GivingStatementScreen
import org.nuruplace.member.feature.home.HomeScreen
import org.nuruplace.member.feature.profile.AssistantScreen
import org.nuruplace.member.feature.profile.GiftsScreen
import org.nuruplace.member.feature.profile.ResourcesScreen
import org.nuruplace.member.feature.pathway.LevelDetailScreen
import org.nuruplace.member.feature.pathway.LevelsMapScreen
import org.nuruplace.member.feature.pathway.PathwayHubScreen
import org.nuruplace.member.feature.pathway.ModuleScreen
import org.nuruplace.member.feature.pathway.QuizScreen
import org.nuruplace.member.feature.pathway.QuizVerdict
import org.nuruplace.member.feature.live.AppLiveBar
import org.nuruplace.member.feature.live.LiveDiscoveryCenter
import org.nuruplace.member.feature.live.liveNowRoute
import org.nuruplace.member.ui.components.CelebrationHost
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** The "You" tab (L4 tab restructure) is really FOUR routes wearing one tab —
 *  "you" itself (the bottom-bar's own destination, always Chat-default) plus
 *  the four old standalone routes, each of which now pre-selects its segment
 *  in the SAME [YouScreen] (see MainShell's NavHost below and
 *  docs/LIVE_STREAMING.md L4). Every nav call site that still targets one of
 *  these four by name — FCM pushes, NotificationsScreen.routeFor, Home's
 *  onSelectTab, CommunityHubScreen, shortcuts.xml — keeps landing correctly
 *  with zero edits, and the bottom bar must recognize all five as "the You
 *  tab is active" for highlighting/back-stack purposes. */
private const val YOU_TAB_ROUTE = "you"
private val YOU_ALIAS_ROUTES = setOf(YOU_TAB_ROUTE, "chat", "events", "give", "profile")
private fun isYouRoute(route: String?) = route != null && route in YOU_ALIAS_ROUTES

private val BASE_TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("pathway", "Pathway", Icons.Filled.MenuBook),
    Tab("plans", "Plans", Icons.Filled.Bookmark),
    Tab(YOU_TAB_ROUTE, "You", Icons.Filled.Person),
)
private val LIVE_TAB = Tab("live", "Live", Icons.Filled.Videocam)

/** "Live" only appears for members holding the "live:go" grant (client-side
 *  advisory gate, §5.4 — the server is the real authority on every /live
 *  route's write regardless). Everyone else gets 4 tabs and watches through
 *  Home/cell surfaces, per docs/LIVE_STREAMING.md L4. */
private fun tabsFor(me: MeResponse?): List<Tab> =
    if (org.nuruplace.member.feature.live.canGoLive(me)) BASE_TABS + LIVE_TAB else BASE_TABS

@Composable
fun MainShell(auth: AuthStore, me: MeResponse?) {
    // Register this device for FCM push once we're in the authed shell (§D-M9).
    org.nuruplace.member.data.firebase.PushRegistration()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabs = remember(me) { tabsFor(me) }
    val onTab = tabs.any { it.route == route } || isYouRoute(route)
    val rootView = androidx.compose.ui.platform.LocalView.current

    // Chat's total-unread — hoisted here (not inside YouScreen) so it survives
    // the "you"/"chat"/"events"/"give"/"profile" destination being disposed
    // and recreated by nav (none of this app's tabs use saveState/
    // restoreState), and so it can badge the bottom-bar "You" icon even while
    // a DIFFERENT segment (or a wholly different tab) is on screen. Eagerly
    // best-effort fetched once per session so the badge is right from the
    // first frame, then kept fresh in real time by ChatInboxScreen's own
    // onUnreadChange while the member is actually looking at Chat.
    var chatUnread by remember { mutableIntStateOf(0) }
    LaunchedEffect(me) {
        if (me != null) {
            runCatching { Net.client.api.chatInbox() }
                .getOrNull()?.let { inbox -> chatUnread = inbox.conversations.sumOf { it.unread } }
        }
    }

    // GET /chat/pastoral/eligibility, cached per session (PastorEligibility) —
    // ORed with SuperAdmin below so the Pastoral Inbox segment shows for an
    // assigned non-SuperAdmin pastor too (Chat Redesign C4, closing the gap
    // PARITY_AUDIT.md's 2026-07-18 entry flagged: "an assigned non-SuperAdmin
    // pastor has no client-visible signal to key on").
    val pastorEligible by androidx.compose.runtime.produceState(initialValue = false, me) {
        value = if (me != null) org.nuruplace.member.data.PastorEligibility.isPastor() else false
    }

    // Screen-view telemetry (POST /me/activity/screens) — silent, fire-and-forget
    // (iOS RootView.onChange(of: tabs.selected) + ScreenTracker parity). Tracks
    // every nav-graph destination change, not just tab switches.
    androidx.compose.runtime.LaunchedEffect(route) {
        route?.let { org.nuruplace.member.data.ScreenTracker.record(it.lowercase()) }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        org.nuruplace.member.data.ScreenTracker.appDidEnterBackground()
    }

    // Launcher-shortcut / notification destination (long-press icon → Radio,
    // Pathway, Prayer Wall, Give; nuru://join/{token} deep links). Keyed off
    // PendingDest.route (Compose state) rather than Unit, so a WARM dispatch —
    // the activity already alive, onNewIntent firing without a fresh
    // setContent — re-triggers this exactly like a cold-start read would.
    // Consumed once per value so recompositions don't re-navigate.
    LaunchedEffect(org.nuruplace.member.PendingDest.route) {
        org.nuruplace.member.PendingDest.consume()?.let { dest ->
            nav.navigate(dest) { launchSingleTop = true }
        }
    }

    // Location-first onboarding: invite ONCE right after first login; members
    // already sharing get a silent geotag refresh every open instead.
    var showLocationInvite by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!org.nuruplace.member.data.AppPrefs.locationInviteShown && !org.nuruplace.member.data.AppPrefs.shareLocation) {
            kotlinx.coroutines.delay(1200) // let Home land first
            org.nuruplace.member.data.AppPrefs.locationInviteShown = true
            showLocationInvite = true
        }
    }
    if (showLocationInvite) LocationInviteDialog(onDismiss = { showLocationInvite = false })
    RefreshLocationIfSharing()

    // Nuru Live discovery — "invite loudly, never hijack": a gentle app-wide
    // poll (no existing app-level poll to piggyback on Android, unlike iOS's
    // ChatBadge timer, so this is the one) that feeds the app-wide LIVE bar
    // below and whatever GET /live/now re-check a routed notification tap
    // needs. Plain LaunchedEffect(Unit) — alive only while the authed shell
    // itself is composed, same idiom HomeScreen's own live poll already uses.
    LaunchedEffect(Unit) {
        while (true) {
            LiveDiscoveryCenter.refresh()
            kotlinx.coroutines.delay(75_000)
        }
    }
    val liveStreamsNow by LiveDiscoveryCenter.streams.collectAsState()
    // Every screen but Home, while a stream is watchable and the player it
    // would open isn't already the thing on screen.
    val showAppLiveBar = liveStreamsNow.isNotEmpty() && route != "home" &&
        route?.startsWith("live-player") != true

    Scaffold(
        containerColor = Nuru.paper,
        bottomBar = {
            // The LIVE bar sits ABOVE the bottom nav (when the bottom nav is
            // even showing — it also floats on non-tab screens like a module
            // reader, since "not on Home" is the only scope rule).
            Column {
                if (showAppLiveBar) {
                    AppLiveBar(stream = liveStreamsNow.first()) {
                        val stream = liveStreamsNow.first()
                        LiveDiscoveryCenter.markSeen(stream.streamId)
                        nav.navigate(liveNowRoute(stream))
                    }
                }
                if (onTab) {
                    NavigationBar(containerColor = Nuru.white) {
                        tabs.forEach { tab ->
                            // The "You" tab is active on ANY of its five aliased
                            // routes (see isYouRoute) — every other tab is a
                            // single literal route.
                            val isSelected = if (tab.route == YOU_TAB_ROUTE) isYouRoute(route) else route == tab.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (!isSelected) {
                                        org.nuruplace.member.ui.components.Haptics.tick(rootView)
                                        nav.navigate(tab.route) {
                                            popUpTo("home"); launchSingleTop = true
                                        }
                                    }
                                },
                                icon = {
                                    // Chat unread carries onto the You tab's icon too
                                    // (docs/LIVE_STREAMING.md L4) — same count the
                                    // Chat segment's own label badges inside You.
                                    if (tab.route == YOU_TAB_ROUTE && chatUnread > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = Nuru.gold, contentColor = Nuru.navyDeep) {
                                                    Text(chatUnread.coerceAtMost(99).toString())
                                                }
                                            },
                                        ) {
                                            Icon(tab.icon, tab.label, modifier = Modifier.size(22.dp))
                                        }
                                    } else {
                                        Icon(tab.icon, tab.label, modifier = Modifier.size(22.dp))
                                    }
                                },
                                label = { Text(tab.label, style = NuruType.micro.copy(fontSize = 10.sp), maxLines = 1, softWrap = false) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Nuru.navyDeep,
                                    selectedTextColor = Nuru.navyDeep,
                                    indicatorColor = Nuru.goldTint,
                                    unselectedIconColor = Nuru.ink400,
                                    unselectedTextColor = Nuru.ink400,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") {
                HomeScreen(
                    me,
                    onSignOut = { auth.signOut() },
                    onOpenNotifications = { nav.navigate("notifications") },
                    onOpenGive = { nav.navigate("give") },
                    onNavigate = { nav.navigate(it) },
                    onSelectTab = { r -> nav.navigate(r) { popUpTo("home"); launchSingleTop = true } },
                )
            }
            composable("pathway") {
                PathwayHubScreen(
                    me = me,
                    onOpenLevel = { nav.navigate("level/$it") },
                    onOpenModule = { nav.navigate("module/$it") },
                    onOpenExam = { nav.navigate("exam/$it") },
                    // The hub row says "Your Discipleship Hub" — route it there
                    // (iOS PathwayDisciplershipRow → discipleshipHub), not to Mentor.
                    onOpenMentor = { nav.navigate("discipleship") },
                    onOpenMap = { nav.navigate("pathway-map") },
                    onOpenWalk = { nav.navigate("your-walk") },
                )
            }
            composable("your-walk") {
                org.nuruplace.member.feature.pathway.YourWalkScreen(onBack = { nav.popBackStack() })
            }
            composable("pathway-map") {
                LevelsMapScreen(me = me, onOpenLevel = { nav.navigate("level/$it") }, onBack = { nav.popBackStack() })
            }
            composable("grow") { GrowHubScreen(onOpen = { nav.navigate(it) }) }
            composable("devotional") { DevotionalScreen(onBack = { nav.popBackStack() }) }
            composable("memory-verses") { MemoryVerseScreen(onBack = { nav.popBackStack() }) }
            composable("plans") {
                ReadingPlansScreen(
                    onOpenPlan = { nav.navigate("plan/$it") },
                    onOpenNotifications = { nav.navigate("notifications") },
                    onOpenReadWithFriend = { nav.navigate("read-with-friend") },
                )
            }
            // Read with a Friend (spec §3/§6) — my active shared plans, group
            // detail (roster + progress), and the invite-preview screen a
            // nuru://join/{token} deep link or notification tap lands on.
            composable("read-with-friend") {
                org.nuruplace.member.feature.grow.ReadWithFriendHubScreen(
                    myUserId = me?.profile?.userId ?: "",
                    onBack = { nav.popBackStack() },
                    onOpenGroup = { nav.navigate("read-with-friend/$it") },
                )
            }
            composable(
                "read-with-friend/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { entry ->
                org.nuruplace.member.feature.grow.ReadingGroupDetailScreen(
                    groupId = entry.arguments?.getString("groupId") ?: "",
                    myUserId = me?.profile?.userId ?: "",
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "reading/join/{token}",
                arguments = listOf(navArgument("token") { type = NavType.StringType }),
            ) { entry ->
                val token = entry.arguments?.getString("token") ?: ""
                org.nuruplace.member.feature.grow.ReadingInvitePreviewScreen(
                    token = token,
                    onClose = { if (!nav.popBackStack()) nav.navigate("plans") { popUpTo("home") } },
                    onOpenGroup = { groupId ->
                        nav.navigate("read-with-friend/$groupId") { popUpTo("plans") { inclusive = false } }
                    },
                )
            }
            composable(
                "plan/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                PlanDetailScreen(planId = id, onBack = { nav.popBackStack() }, onOpenDay = { d -> nav.navigate("plan/$id/day/$d") })
            }
            composable(
                "plan/{id}/day/{n}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("n") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                val n = entry.arguments?.getInt("n") ?: 1
                PlanDayScreen(
                    planId = id, dayNumber = n, onBack = { nav.popBackStack() },
                    onOpenPart = { tag, i -> nav.navigate("plan/$id/day/$n/part/$tag/$i") },
                    onTalkItOver = { nav.navigate("plan/$id/day/$n/talk") },
                    onPlanComplete = { nav.navigate("plan/$id/keepsake") },
                )
            }
            composable(
                "plan/{id}/day/{n}/part/{tag}/{i}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType }, navArgument("n") { type = NavType.IntType },
                    navArgument("tag") { type = NavType.StringType }, navArgument("i") { type = NavType.IntType },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                val n = entry.arguments?.getInt("n") ?: 1
                val tag = entry.arguments?.getString("tag") ?: "word"
                val i = entry.arguments?.getInt("i") ?: 0
                org.nuruplace.member.feature.grow.PlanPartReaderScreen(planId = id, dayNumber = n, part = tag, index = i, onBack = { nav.popBackStack() })
            }
            composable(
                "plan/{id}/day/{n}/talk",
                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("n") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                val n = entry.arguments?.getInt("n") ?: 1
                org.nuruplace.member.feature.grow.TalkItOverScreen(planId = id, dayNumber = n, onBack = { nav.popBackStack() })
            }
            composable(
                "plan/{id}/keepsake",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                val kp = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<String, Int>?>(null) }
                androidx.compose.runtime.LaunchedEffect(id) {
                    kp.value = runCatching { org.nuruplace.member.data.net.Net.client.api.plan(id) }.getOrNull()?.let { it.title to it.days.size }
                }
                kp.value?.let { (title, days) ->
                    org.nuruplace.member.feature.grow.PlanKeepsakeScreen(planTitle = title, days = days) {
                        nav.popBackStack("plans", inclusive = false)
                    }
                }
            }
            composable(
                "plan/{id}/day/{n}/seg/{i}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("n") { type = NavType.IntType }, navArgument("i") { type = NavType.IntType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                val n = entry.arguments?.getInt("n") ?: 1
                val i = entry.arguments?.getInt("i") ?: 0
                PlanSegmentScreen(planId = id, dayNumber = n, index = i, onBack = { nav.popBackStack() }, onContinue = { next -> nav.navigate("plan/$id/day/$n/seg/$next") })
            }
            composable("discipleship") {
                org.nuruplace.member.feature.discipleship.DiscipleshipHubScreen(
                    onBack = { nav.popBackStack() },
                    // ?ctx=discipler — the hero always resolves the thread via
                    // GET /chat/discipler/conversation now, so it gets the SAME
                    // privacy banner + admin-invisibility as the My Discipler tab.
                    onOpenChat = { conversationId -> nav.navigate("chat/$conversationId?ctx=discipler") },
                )
            }
            // Discipler-facing (Instructor+; server enforces role + scope).
            composable("disciples") {
                org.nuruplace.member.feature.discipleship.DisciplerRosterScreen(
                    onBack = { nav.popBackStack() },
                    onOpenStudent = { id -> nav.navigate("disciples/$id") },
                )
            }
            composable(
                "disciples/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                org.nuruplace.member.feature.discipleship.DisciplerDossierScreen(
                    studentId = entry.arguments?.getString("id") ?: "",
                    onBack = { nav.popBackStack() },
                    onOpenChat = { conversationId -> nav.navigate("chat/$conversationId") },
                )
            }
            composable("verses") { VerseLibraryScreen(onBack = { nav.popBackStack() }) }
            composable("community") { CommunityHubScreen(onOpen = { nav.navigate(it) }) }
            // My Prayer Room — the single destination that replaced the separate
            // "prayers" (journal) and "prayer-wall" (wall) routes; ?tab picks
            // which of its two tabs opens first. A specific post still opens
            // its own detail screen directly ("prayer-wall/{id}" below).
            composable(
                "prayer-room?tab={tab}",
                arguments = listOf(navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                val initialTab = when (entry.arguments?.getString("tab")) {
                    "corporate" -> org.nuruplace.member.feature.community.PrayerRoomTab.Corporate
                    "selah" -> org.nuruplace.member.feature.community.PrayerRoomTab.Selah
                    "prayer-points" -> org.nuruplace.member.feature.community.PrayerRoomTab.PrayerPoints
                    // "answered" no longer has a top-level slot — it folds into
                    // Private's own Active/Answered chips (PrayerRoomScreen.kt).
                    else -> org.nuruplace.member.feature.community.PrayerRoomTab.Private
                }
                org.nuruplace.member.feature.community.PrayerRoomScreen(
                    initialTab = initialTab,
                    onBack = { nav.popBackStack() },
                    onOpenPost = { nav.navigate("prayer-wall/$it") },
                )
            }
            composable(
                "prayer-wall/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                PrayerWallDetailScreen(postId = entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            // "You" tab (L4 tab restructure) — ONE screen (YouScreen) behind
            // FIVE routes: the canonical bottom-tab destination ("you",
            // always Chat-default) plus the four old standalone routes,
            // each pre-selecting its own segment so every existing
            // nav.navigate("chat"/"events"/"give"/"profile") call site
            // (FCM pushes, NotificationsScreen, Home's onSelectTab,
            // CommunityHubScreen, shortcuts.xml) keeps landing correctly.
            // isStaff/pastoralEligible mirror the OLD "chat" composable's
            // gating exactly (product decision, 2026-07 / Chat Redesign C4).
            composable(YOU_TAB_ROUTE) {
                YouScreen(
                    initial = YouSegment.Chat,
                    me = me,
                    isStaff = me?.profile?.role == "SuperAdmin",
                    pastoralEligible = me?.profile?.role == "SuperAdmin" || pastorEligible,
                    chatUnread = chatUnread,
                    onChatUnreadChange = { chatUnread = it },
                    onNavigate = { nav.navigate(it) },
                    onSignOut = { auth.signOut() },
                )
            }
            composable("chat") {
                YouScreen(
                    initial = YouSegment.Chat,
                    me = me,
                    isStaff = me?.profile?.role == "SuperAdmin",
                    pastoralEligible = me?.profile?.role == "SuperAdmin" || pastorEligible,
                    chatUnread = chatUnread,
                    onChatUnreadChange = { chatUnread = it },
                    onNavigate = { nav.navigate(it) },
                    onSignOut = { auth.signOut() },
                )
            }
            composable(
                "broadcast/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                org.nuruplace.member.feature.community.BroadcastDetailScreen(
                    broadcastId = entry.arguments?.getString("id") ?: "",
                    onBack = { nav.popBackStack() },
                    // A response's private thread — same route the inbox itself uses.
                    onOpenThread = { nav.navigate("chat/$it") },
                )
            }
            composable("new-message") {
                org.nuruplace.member.feature.community.NewMessageScreen(
                    onBack = { nav.popBackStack() },
                    onOpenThread = { nav.navigate("chat/$it") { popUpTo("chat") } },
                )
            }
            composable(
                "chat/{id}?ctx={ctx}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    // "discipler" | "pastoral" | absent — the tab that resolved
                    // the thread already knows its flavour (GET /chat/
                    // conversations/{id} carries no `type`), so it rides the
                    // route args into the thread's privacy chrome + local gate.
                    navArgument("ctx") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                ChatThreadScreen(
                    conversationId = entry.arguments?.getString("id") ?: "",
                    onBack = { nav.popBackStack() },
                    threadContext = entry.arguments?.getString("ctx"),
                )
            }
            composable("events") {
                YouScreen(
                    initial = YouSegment.Events,
                    me = me,
                    isStaff = me?.profile?.role == "SuperAdmin",
                    pastoralEligible = me?.profile?.role == "SuperAdmin" || pastorEligible,
                    chatUnread = chatUnread,
                    onChatUnreadChange = { chatUnread = it },
                    onNavigate = { nav.navigate(it) },
                    onSignOut = { auth.signOut() },
                )
            }
            composable("events-calendar") {
                AllEventsCalendarScreen(onBack = { nav.popBackStack() }, onOpenEvent = { id, end -> nav.navigate("event/$id?end=${android.net.Uri.encode(end ?: "")}") })
            }
            composable(
                // The end time travels as nav state: GET /events/{id} has no end field
                // on the wire — the calendar occurrence's end_at is the source (as iOS).
                "event/{id}?end={end}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("end") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                EventDetailScreen(
                    eventId = entry.arguments?.getString("id") ?: "",
                    endAt = entry.arguments?.getString("end")?.takeIf { it.isNotBlank() },
                    onBack = { nav.popBackStack() },
                    onCheckIn = { nav.navigate("checkin/$it") },
                )
            }
            composable(
                "checkin/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                org.nuruplace.member.feature.events.CheckInScannerScreen(eventId = entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("notifications") {
                NotificationsScreen(onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) })
            }
            composable("give") {
                YouScreen(
                    initial = YouSegment.Give,
                    me = me,
                    isStaff = me?.profile?.role == "SuperAdmin",
                    pastoralEligible = me?.profile?.role == "SuperAdmin" || pastorEligible,
                    chatUnread = chatUnread,
                    onChatUnreadChange = { chatUnread = it },
                    onNavigate = { nav.navigate(it) },
                    onSignOut = { auth.signOut() },
                )
            }
            composable("schedules") { org.nuruplace.member.feature.give.SchedulesScreen(onBack = { nav.popBackStack() }) }
            composable("announcements") {
                org.nuruplace.member.feature.events.AnnouncementsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("announcement/$it") })
            }
            composable(
                "announcement/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                org.nuruplace.member.feature.events.AnnouncementDetailScreen(announcementId = entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("statement") {
                GivingStatementScreen(onBack = { nav.popBackStack() }, onOpenReceipt = { nav.navigate("receipt/$it") })
            }
            composable(
                "receipt/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                GivingReceiptScreen(transactionId = entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("profile") {
                YouScreen(
                    initial = YouSegment.Profile,
                    me = me,
                    isStaff = me?.profile?.role == "SuperAdmin",
                    pastoralEligible = me?.profile?.role == "SuperAdmin" || pastorEligible,
                    chatUnread = chatUnread,
                    onChatUnreadChange = { chatUnread = it },
                    onNavigate = { nav.navigate(it) },
                    onSignOut = { auth.signOut() },
                )
            }
            // "Live" tab (L4, broadcaster-only — MainShell's `tabs` list only
            // includes it when canGoLive(me), so this destination is simply
            // unreachable via the bottom bar for anyone else; a stale deep
            // link here just renders LiveTabScreen's own defensive fallback).
            composable("live") {
                org.nuruplace.member.feature.live.LiveTabScreen(me = me, onNavigate = { nav.navigate(it) })
            }
            // Nuru Live discovery — the lightweight forwarding destination a
            // routed live_stream_started notification tap lands on (see
            // NuruMessagingService.destFor). The push payload alone lacks
            // kind/viewers/startedAt, so this re-fetches GET /live/now itself
            // and forwards straight into the newest watchable stream's player
            // (never rendered long enough to need its own back-stack entry —
            // it immediately replaces itself), or back to Home (which shows
            // its own banner/mini-window) if nothing is watchable anymore.
            composable("live-now") {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    LiveDiscoveryCenter.refresh()
                    val newest = LiveDiscoveryCenter.newestWatchable
                    if (newest != null) {
                        LiveDiscoveryCenter.markSeen(newest.streamId)
                        nav.navigate(liveNowRoute(newest)) { popUpTo("home") }
                    } else {
                        nav.navigate("home") { popUpTo("home") { inclusive = true } }
                    }
                }
                Box(Modifier.fillMaxSize().background(Nuru.paper), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Nuru.gold)
                }
            }
            composable(
                "level-complete/{n}",
                arguments = listOf(navArgument("n") { type = NavType.IntType }),
            ) { entry ->
                org.nuruplace.member.feature.pathway.LevelCompleteScreen(
                    levelNumber = entry.arguments?.getInt("n") ?: 1,
                    onContinue = { nav.popBackStack() },
                )
            }
            composable("radio") { org.nuruplace.member.feature.radio.LiveRadioScreen(onBack = { nav.popBackStack() }) }
            composable("gifts") { GiftsScreen(onBack = { nav.popBackStack() }) }
            composable("resources") { ResourcesScreen(onBack = { nav.popBackStack() }) }
            composable("assistant") { AssistantScreen(onBack = { nav.popBackStack() }) }
            composable("settings") { org.nuruplace.member.feature.profile.SettingsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }) }
            composable("firebase-account") { org.nuruplace.member.feature.profile.FirebaseAccountScreen(onBack = { nav.popBackStack() }) }
            composable("mentor") { org.nuruplace.member.feature.profile.MentorScreen(onBack = { nav.popBackStack() }) }
            composable("cell-info") {
                org.nuruplace.member.feature.home.CellInfoScreen(
                    me = me,
                    onBack = { nav.popBackStack() },
                    onNavigate = { nav.navigate(it) },
                )
            }
            // Nuru Live (L2, viewer-only) — the full-screen player is one
            // destination fed entirely via query args (Home's banner, the
            // cell card, and Replays rows all build this route through
            // liveNowRoute()/liveRecordingRoute() so the resolved media url
            // and heartbeat/live-ness travel with the navigation, not a
            // second fetch).
            composable(
                "live-player?streamId={streamId}&url={url}&fallbackUrl={fallbackUrl}&title={title}&kind={kind}&live={live}&startedAt={startedAt}&viewers={viewers}&startedByName={startedByName}",
                arguments = listOf(
                    navArgument("streamId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("fallbackUrl") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("kind") { type = NavType.StringType; nullable = true; defaultValue = "video" },
                    navArgument("live") { type = NavType.StringType; nullable = true; defaultValue = "false" },
                    navArgument("startedAt") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("viewers") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("startedByName") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val a = entry.arguments
                org.nuruplace.member.feature.live.LivePlayerScreen(
                    url = a?.getString("url").orEmpty(),
                    fallbackUrl = a?.getString("fallbackUrl")?.takeIf { it.isNotBlank() },
                    title = a?.getString("title").orEmpty(),
                    kind = a?.getString("kind") ?: "video",
                    live = a?.getString("live") == "true",
                    streamId = a?.getString("streamId")?.takeIf { it.isNotBlank() },
                    startedAt = a?.getString("startedAt"),
                    initialViewerCount = a?.getInt("viewers") ?: 0,
                    startedByName = a?.getString("startedByName")?.takeIf { it.isNotBlank() },
                    myUserId = me?.profile?.userId,
                    onBack = { nav.popBackStack() },
                    onOpenReplays = { nav.navigate("live-replays") { popUpTo("home") } },
                )
            }
            composable("live-replays") {
                org.nuruplace.member.feature.live.LiveReplaysScreen(
                    onBack = { nav.popBackStack() },
                    onOpenRecording = { row -> nav.navigate(org.nuruplace.member.feature.live.liveRecordingRoute(row)) },
                )
            }
            // Nuru Live (L3, broadcaster) — the setup sheet (Home/CellInfo)
            // mints the stream server-side and navigates here with the
            // result; this route never re-fetches, it just carries what the
            // sheet already has (same query-arg convention as live-player).
            composable(
                "live-broadcast?streamId={streamId}&rtmpUrl={rtmpUrl}&streamKey={streamKey}&title={title}&kind={kind}",
                arguments = listOf(
                    navArgument("streamId") { type = NavType.StringType },
                    navArgument("rtmpUrl") { type = NavType.StringType },
                    navArgument("streamKey") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = "" },
                    navArgument("kind") { type = NavType.StringType; nullable = true; defaultValue = "video" },
                ),
            ) { entry ->
                val a = entry.arguments
                org.nuruplace.member.feature.live.LiveBroadcastScreen(
                    streamId = a?.getString("streamId").orEmpty(),
                    rtmpUrl = a?.getString("rtmpUrl").orEmpty(),
                    streamKey = a?.getString("streamKey").orEmpty(),
                    title = a?.getString("title").orEmpty(),
                    kind = a?.getString("kind") ?: "video",
                    // Pop back to wherever the member came from (Home or
                    // CellInfoScreen) — never a fixed destination.
                    onEnded = { nav.popBackStack() },
                )
            }
            composable(
                "score/{pillar}",
                arguments = listOf(navArgument("pillar") { type = NavType.StringType }),
            ) { entry ->
                org.nuruplace.member.feature.profile.ScoreDetailScreen(
                    initialPillar = entry.arguments?.getString("pillar") ?: "word",
                    onBack = { nav.popBackStack() },
                )
            }

            composable(
                "level/{n}",
                arguments = listOf(navArgument("n") { type = NavType.IntType }),
            ) { entry ->
                val n = entry.arguments?.getInt("n") ?: 1
                LevelDetailScreen(
                    levelNumber = n,
                    onBack = { nav.popBackStack() },
                    onOpenModule = { nav.navigate("module/$it") },
                    onTakeExam = { nav.navigate("exam/$it") },
                    // Same destination as the hub's "Your Discipleship Hub" row
                    // (iOS AppRoute.discipleshipHub parity) — the discipler's
                    // real DM lives behind this screen.
                    onOpenDiscipler = { nav.navigate("discipleship") },
                )
            }
            composable(
                "module/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                ModuleScreen(
                    moduleId = id,
                    onBack = { nav.popBackStack() },
                    onTakeQuiz = { nav.navigate("quiz/$it") },
                    onCompleted = { nav.popBackStack() },
                )
            }
            composable(
                "quiz/{moduleId}",
                arguments = listOf(navArgument("moduleId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("moduleId") ?: ""
                QuizScreen(
                    title = "Quiz",
                    loadQuestions = { Net.client.api.quiz(id).questions },
                    submit = { answers, mut ->
                        val r = Net.client.api.submitQuiz(id, SubmitBody(mut, answers))
                        QuizVerdict(r.scoreAchieved, r.passMark, r.isPassed, r.requiresManualReview)
                    },
                    onDone = { nav.popBackStack() },
                    moduleId = id,
                )
            }
            composable(
                "exam/{n}",
                arguments = listOf(navArgument("n") { type = NavType.IntType }),
            ) { entry ->
                val n = entry.arguments?.getInt("n") ?: 1
                QuizScreen(
                    title = "Level $n exam",
                    loadQuestions = { Net.client.api.levelExam(n).questions },
                    submit = { answers, mut ->
                        val r = Net.client.api.submitLevelExam(n, SubmitBody(mut, answers))
                        QuizVerdict(r.scoreAchieved, r.passMark, r.isPassed, r.requiresManualReview)
                    },
                    onDone = { nav.popBackStack() },
                    onPassed = { nav.navigate("level-complete/$n") { popUpTo("pathway") } },
                )
            }
            }
            // Human moments — confetti/banner celebrations, topmost overlay (renders
            // nothing while idle). Fired via CelebrationCenter from any screen.
            CelebrationHost()
        }
    }
}

@Composable
private fun Placeholder(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().background(Nuru.paper).padding(Spacing.screen),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = NuruType.title, color = Nuru.ink)
        Spacer(Modifier.height(Spacing.sm))
        Text(subtitle, style = NuruType.body, color = Nuru.ink600)
    }
}

