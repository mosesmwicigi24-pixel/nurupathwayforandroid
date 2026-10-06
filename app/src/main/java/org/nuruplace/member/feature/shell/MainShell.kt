// Authed shell — bottom bar (Home · Pathway · Plans · Events · Give · You,
// docs/PARTNERS_PROGRAMME.md §0) over a NavHost. Pathway carries its own
// stack: Levels → Level → Module → Quiz/Exam. "Give" is a two-segment capsule
// (Give · Partners — GiveTabScreen.kt); "You" is a four-segment capsule
// (Community · Departments · Profile · Settings — YouScreen.kt). Live is no
// longer a tab: broadcasters (canGoLive(me)) get a "Broadcast" card at the
// top of Events (BroadcastCard.kt) and the "live" studio route stays
// reachable for deep links. Port of the iOS RootView tab shell.
package org.nuruplace.member.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.zIndex
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
import org.nuruplace.member.feature.grow.ReadingPlansScreen
import org.nuruplace.member.feature.grow.VerseLibraryScreen
import org.nuruplace.member.feature.community.ChatThreadScreen
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
import org.nuruplace.member.ui.icons.Lucide

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** Some tabs are really SEVERAL routes wearing one tab. "You" is "you" itself
 *  (always Community-default) plus the old standalone "chat"/"profile" routes,
 *  each pre-selecting its segment in the SAME [YouScreen]; "Give" is "give"
 *  (opens on Give) plus "partners" (opens on Partners) in the SAME
 *  [GiveTabScreen]. Every nav call site that targets one of these by name —
 *  FCM pushes, NotificationsScreen.routeFor, Home's onSelectTab/onOpenGive —
 *  keeps landing correctly with zero edits, and the
 *  bottom bar must recognize every alias as "that tab is active". */
private const val YOU_TAB_ROUTE = "you"
private const val GIVE_TAB_ROUTE = "give"
private const val EVENTS_TAB_ROUTE = "events"
/** The Give tab opened on Give with a department need preset (docs/
 *  PARTNERS_PROGRAMME.md §4) — built by feature/give/GiveShared.giveToNeedRoute. */
private const val GIVE_NEED_ROUTE = "give-need/{needId}?amount={amount}&title={title}&currency={currency}"
/** The Give tab on one gift's result — a giving_gift_failed push (Giving
 *  Cycle 3), built by feature/give/GivingRoutes.giftRoute. */
private const val GIVE_GIFT_ROUTE = "give-gift/{id}"
/** The Give tab on Partners with one pledge open — a Partners notice, a
 *  pledge's collector saying "Change it on the pledge", or a pledge made
 *  without its automatic collection (Giving Cycle 5); built by
 *  feature/give/GivingRoutes.pledgeRoute. */
private const val PARTNERS_PLEDGE_ROUTE = "partners-pledge/{pledgeId}"
private val YOU_ALIAS_ROUTES = setOf(YOU_TAB_ROUTE, "chat", "profile", "departments")
private val GIVE_ALIAS_ROUTES = setOf(GIVE_TAB_ROUTE, "partners", GIVE_NEED_ROUTE, GIVE_GIFT_ROUTE, PARTNERS_PLEDGE_ROUTE)
private val EVENTS_ALIAS_ROUTES = setOf(EVENTS_TAB_ROUTE)

/** The Live forwarder a tapped Live notice lands on (see its composable). */
private const val LIVE_NOW_ROUTE = "live-now?streamId={streamId}&title={title}"

/** Where the Live forwarder stopped short of the player. */
private sealed interface LiveNowOutcome {
    /** The stream is over (or nothing is live): "This Live has ended". */
    data object Ended : LiveNowOutcome
    /** GET /live/now didn't answer — said in the state language (§4). */
    data class Failed(val message: org.nuruplace.member.data.net.StateMessage) : LiveNowOutcome
}

/** The partners statement for one year (docs/PARTNERS_PROGRAMME.md §3; owner
 *  2026-09-25) — built by feature/give/PartnersStatementScreen.partnersStatementRoute. */
private const val PARTNERS_STATEMENT_ROUTE = "partners-statement?year={year}"

/** Pushed sub-routes that belong to a tab for HIGHLIGHTING (they carry their
 *  own back button and no bottom bar, exactly as before — see `onTab`). */
/** The recurring-gifts list, optionally with one schedule open (a schedule
 *  push, Giving Cycle 4) — built by feature/give/GivingRoutes.scheduleRoute;
 *  a plain nav.navigate("schedules") still matches. */
private const val SCHEDULES_ROUTE = "schedules?open={open}"
private val GIVE_SUB_ROUTES = setOf("statement", SCHEDULES_ROUTE, "receipt/{id}", PARTNERS_STATEMENT_ROUTE)
private val EVENTS_SUB_ROUTES = setOf("events-calendar", "event/{id}?end={end}", "checkin/{id}", "announcements", "announcement/{id}", "attendance", "service-checkin")

/** Which bottom tab a NavHost route belongs to, or null for none. */
private fun tabRouteFor(route: String?): String? = when {
    route == null -> null
    route in YOU_ALIAS_ROUTES -> YOU_TAB_ROUTE
    route in GIVE_ALIAS_ROUTES || route in GIVE_SUB_ROUTES -> GIVE_TAB_ROUTE
    route in EVENTS_ALIAS_ROUTES || route in EVENTS_SUB_ROUTES -> EVENTS_TAB_ROUTE
    else -> route
}

/** A tab's own top-level pages (its segments): re-tapping the tab there
 *  leaves them be. */
private val TAB_SEGMENT_ROUTES = YOU_ALIAS_ROUTES + setOf("partners")

/** The pages inside a tab that are flows or ceremonies — they cover the tab
 *  bar (the QR scanner, the check-in ceremony); the new pledge and the M-Pesa
 *  stages cover it themselves (TabBarCover). */
private val TAB_FLOW_ROUTES = setOf("checkin/{id}", "service-checkin")

/** Tab-LEVEL routes — the ones the bottom bar shows on. One rule for the pages
 *  inside a tab (Cycle 3 close walk E25): a detail page keeps the tab bar — the
 *  pledge page had it while the receipt and the statements didn't — and a
 *  flow or a ceremony covers it. */
private val TAB_LEVEL_ROUTES = YOU_ALIAS_ROUTES + GIVE_ALIAS_ROUTES + EVENTS_ALIAS_ROUTES +
    GIVE_SUB_ROUTES + (EVENTS_SUB_ROUTES - TAB_FLOW_ROUTES)

private val BASE_TABS = listOf(
    Tab("home", "Home", Lucide.Home),
    Tab("pathway", "Pathway", Lucide.BookOpen),
    Tab("plans", "Plans", Lucide.BookMarked),
    Tab(EVENTS_TAB_ROUTE, "Events", Lucide.Calendar),
    Tab(GIVE_TAB_ROUTE, "Give", Lucide.HandHeart),
    Tab(YOU_TAB_ROUTE, "You", Lucide.User),
)

@Composable
fun MainShell(auth: AuthStore, me: MeResponse?) {
    // Register this device for FCM push once we're in the authed shell (§D-M9).
    org.nuruplace.member.data.firebase.PushRegistration()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabs = BASE_TABS
    val onTab = tabs.any { it.route == route } || route in TAB_LEVEL_ROUTES
    val rootView = androidx.compose.ui.platform.LocalView.current

    // Chat's total-unread — hoisted here (not inside YouScreen) so it survives
    // the "you"/"chat"/"profile" destination being disposed
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
    // The bells' one count (InboxUnread, EXPERIENCE.md §7.2 #4): asked every
    // time the app comes to the foreground — and nobody's once signed out.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) {
        org.nuruplace.member.ui.components.InboxUnread.refreshSoon()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { org.nuruplace.member.ui.components.InboxUnread.clear() }
    }

    // Launcher-shortcut / notification destination (long-press icon → Radio,
    // Pathway, Prayer Wall, Give; nuru://join/{token} deep links). Keyed off
    // PendingDest.route (Compose state) rather than Unit, so a WARM dispatch —
    // the activity already alive, onNewIntent firing without a fresh
    // setContent — re-triggers this exactly like a cold-start read would.
    // Consumed once per value so recompositions don't re-navigate.
    LaunchedEffect(org.nuruplace.member.PendingDest.route) {
        org.nuruplace.member.PendingDest.consume()?.let { dest ->
            // A destination this build doesn't have — a stale pinned shortcut,
            // another app's intent naming a route that's gone — is logged and
            // dropped, and the member stays where they are. It crashed the app
            // ("Navigation destination that matches route … cannot be found").
            try {
                nav.navigate(dest) { launchSingleTop = true }
            } catch (e: IllegalArgumentException) {
                android.util.Log.w("MainShell", "No destination \"$dest\" in this build — ignored", e)
            }
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
    val onLiveBroadcast = route?.startsWith("live-broadcast") == true
    // Every screen but Home, while a stream is watchable and the player it
    // would open isn't already the thing on screen — and never while this
    // member is themselves mid-broadcast (they'd otherwise see "someone
    // else is live, join" while already live themselves). `liveStreamsNow`
    // is already self-filtered by LiveDiscoveryCenter.ingest(), but the
    // `.first()`-is-self check below is a defensive guard on top of that
    // (2026-07-31 device report — see LiveDiscoveryCenter.kt's header):
    // this bar must never be able to offer a broadcaster their OWN stream.
    val showAppLiveBar = liveStreamsNow.isNotEmpty() && route != "home" &&
        route?.startsWith("live-player") != true && !onLiveBroadcast &&
        liveStreamsNow.first().streamId != org.nuruplace.member.feature.live.BroadcastController.activeSelfStreamId()

    // Broadcast Studio's "tap to return" pill (requirement #2) — the same
    // AppLiveBar idiom, one screen over: shown on every screen but the
    // broadcast screen itself while BroadcastController has a session
    // running (LiveBroadcastService keeps it alive across navigation).
    val broadcastState by org.nuruplace.member.feature.live.BroadcastController.state.collectAsState()
    val activeBroadcast = broadcastState.session
        ?.takeIf { broadcastState.phase != org.nuruplace.member.feature.live.BroadcastPhase.SUMMARY }
    val showBroadcastBar = activeBroadcast != null && !onLiveBroadcast
    var broadcastElapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(broadcastState.startedAtMillis) {
        val started = broadcastState.startedAtMillis ?: return@LaunchedEffect
        while (true) {
            broadcastElapsed = ((System.currentTimeMillis() - started) / 1000).toInt()
            kotlinx.coroutines.delay(1_000)
        }
    }

    // A full-screen flow over the tab bar (TabBarCover — the new pledge,
    // EXPERIENCE.md §7.3): no bottom chrome at all while it is open.
    val covered = org.nuruplace.member.ui.components.TabBarCover.active
    Scaffold(
        containerColor = Nuru.paper,
        bottomBar = {
            // Both bars sit ABOVE the bottom nav (when the bottom nav is
            // even showing — they also float on non-tab screens like a
            // module reader, since "not on Home" is the only scope rule).
            if (!covered) Column {
                if (showBroadcastBar) {
                    org.nuruplace.member.feature.live.BroadcastReturnBar(
                        session = activeBroadcast!!, elapsedSec = broadcastElapsed,
                    ) {
                        nav.navigate(org.nuruplace.member.feature.live.liveBroadcastRoute(activeBroadcast))
                    }
                }
                if (showAppLiveBar) {
                    AppLiveBar(stream = liveStreamsNow.first()) {
                        val stream = liveStreamsNow.first()
                        LiveDiscoveryCenter.markSeen(stream.streamId)
                        nav.navigate(liveNowRoute(stream))
                    }
                }
                // On a page with no tab bar the bottom-most strip sat on the
                // screen's edge — under the gesture handle, or behind the
                // three buttons (§7.1 rule 3). Its colour runs on under the
                // system bar, so its words sit clear of it.
                if (!onTab && (showAppLiveBar || showBroadcastBar)) {
                    Spacer(
                        Modifier.fillMaxWidth()
                            .background(if (showAppLiveBar) Nuru.navy else Nuru.danger.copy(alpha = 0.92f))
                            .windowInsetsBottomHeight(androidx.compose.foundation.layout.WindowInsets.navigationBars),
                    )
                }
                if (onTab) {
                    NavigationBar(containerColor = Nuru.white) {
                        tabs.forEach { tab ->
                            // You/Give/Events are active on any of their aliased
                            // (and pushed sub-) routes — see tabRouteFor.
                            val isSelected = tabRouteFor(route) == tab.route
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    // Another tab — or this tab from one of its
                                    // detail pages (they keep the bar now): back
                                    // to the tab's own page.
                                    if (!isSelected || route != tab.route && route !in TAB_SEGMENT_ROUTES) {
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
                                label = { Text(tab.label, style = NuruType.micro, maxLines = 1, softWrap = false) },
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
            // Broadcast Studio's full-bleed stage (requirement #1) needs the
            // video edge-to-edge, including under the status/nav bars — it
            // handles its own WindowInsets per HUD element (LiveBroadcastScreen's
            // LiveHudOverlay) rather than being pre-inset by Scaffold's padding
            // the way every other destination is.
            val contentPadding = if (onLiveBroadcast) androidx.compose.foundation.layout.PaddingValues(0.dp) else pad
            // While the server is away and screens show their last good copies, say so once, here.
            org.nuruplace.member.ui.components.ServerReachBanner(
                Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()).zIndex(1f),
            )
            // A quick action the server didn't record says so here, once
            // (EXPERIENCE.md §7.4) — above the bars, clear of the gesture bar.
            val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding()
            org.nuruplace.member.ui.components.QuickNoticeHost(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = maxOf(contentPadding.calculateBottomPadding(), navBottom) + 12.dp)
                    .zIndex(2f),
            )
            // The bars below already take the system bar's room: a page that
            // pads for the system bar itself (a composer, a reader's button)
            // isn't lifted twice over a live strip, and a keyboard lifts a
            // page only as far as the bars don't already (consumeWindowInsets,
            // the bottom edge only — the top is the pages' own business).
            val shellBottom = androidx.compose.foundation.layout.PaddingValues(bottom = contentPadding.calculateBottomPadding())
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(contentPadding).consumeWindowInsets(shellBottom)) {
            composable("home") {
                HomeScreen(
                    me,
                    onSignOut = { auth.signOut() },
                    onOpenNotifications = { nav.navigate("notifications") },
                    // The partner invite's "Become a partner" → the Give tab on
                    // Partners (the programme), not the giving form.
                    onOpenGive = { nav.navigate("partners") },
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
                    onOpenNotifications = { nav.navigate("notifications") },
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
                    // "Open chat" after a friends-first invite — the DM the
                    // server posted it into.
                    onOpenChat = { nav.navigate("chat/$it") },
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
                PlanDetailScreen(
                    planId = id,
                    onBack = { nav.popBackStack() },
                    onOpenDay = { d -> nav.navigate("plan/$id/day/$d") },
                    onOpenChat = { nav.navigate("chat/$it") },
                )
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
            // "You" tab — ONE screen (YouScreen) behind THREE routes: the
            // canonical bottom-tab destination ("you", always Community-
            // default) plus the old standalone "chat"/"profile" routes, each
            // pre-selecting its own segment so every existing
            // nav.navigate("chat"/"profile") call site (FCM pushes,
            // NotificationsScreen, Home's onSelectTab)
            // keeps landing correctly. isStaff/pastoralEligible mirror the
            // OLD "chat" composable's gating exactly (product decision,
            // 2026-07 / Chat Redesign C4).
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
            // Departments (docs/PARTNERS_PROGRAMME.md §4) — the You tab opened
            // on its Departments segment; serve_request_* / department_*
            // pushes without a department_id land here.
            composable("departments") {
                YouScreen(
                    initial = YouSegment.Departments,
                    me = me,
                    isStaff = me?.profile?.role == "SuperAdmin",
                    pastoralEligible = me?.profile?.role == "SuperAdmin" || pastorEligible,
                    chatUnread = chatUnread,
                    onChatUnreadChange = { chatUnread = it },
                    onNavigate = { nav.navigate(it) },
                    onSignOut = { auth.signOut() },
                )
            }
            // One department — posts, needs, members; "I'd like to serve
            // here". A need's "Give to this need" opens the Give tab preset.
            composable(
                "department/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                org.nuruplace.member.feature.departments.DepartmentScreen(
                    departmentId = entry.arguments?.getString("id") ?: "",
                    onBack = { nav.popBackStack() },
                    onGiveToNeed = { nav.navigate(org.nuruplace.member.feature.give.giveToNeedRoute(it)) },
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
                    // Invite cards + in-app join links in bubbles → reading/join/{token}.
                    onNavigate = { nav.navigate(it) },
                )
            }
            // "Events" tab (docs/PARTNERS_PROGRAMME.md §0) — its own bottom-bar
            // destination again. Broadcasters get the "Broadcast" card at the
            // top (client-side advisory gate, §5.4 — the server is the real
            // authority on every /live write regardless).
            composable(EVENTS_TAB_ROUTE) {
                val broadcaster = org.nuruplace.member.feature.live.canGoLive(me)
                org.nuruplace.member.feature.events.EventsScreen(
                    onOpenEvent = { id, end -> nav.navigate("event/$id?end=${android.net.Uri.encode(end ?: "")}") },
                    onOpenCalendar = { nav.navigate("events-calendar") },
                    onOpenAnnouncement = { nav.navigate("announcement/$it") },
                    onOpenAnnouncements = { nav.navigate("announcements") },
                    onOpenNotifications = { nav.navigate("notifications") },
                    onOpenAttendance = { nav.navigate("attendance") },
                    broadcastCard = if (broadcaster) {
                        { org.nuruplace.member.feature.live.BroadcastCard(me = me, onNavigate = { nav.navigate(it) }) }
                    } else null,
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
            // --- Church service attendance (§3.3) ---
            // The scanner needs no service id: the QR carries it, so a member
            // arriving at church opens one screen and points the camera.
            composable("service-checkin") {
                org.nuruplace.member.feature.attendance.ServiceCheckInScreen(
                    memberName = me?.profile?.fullName.orEmpty(),
                    memberPhone = me?.profile?.phoneNumber.orEmpty(),
                    memberEmail = me?.profile?.email,
                    onBack = { nav.popBackStack() },
                    onSeeStreak = {
                        nav.popBackStack()
                        nav.navigate("attendance")
                    },
                )
            }
            composable("attendance") {
                org.nuruplace.member.feature.attendance.AttendanceScreen(
                    onBack = { nav.popBackStack() },
                    onCheckIn = { nav.navigate("service-checkin") },
                )
            }
            composable("notifications") {
                NotificationsScreen(onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) })
            }
            // "Give" tab (docs/PARTNERS_PROGRAMME.md §0) — ONE screen
            // (GiveTabScreen) behind two routes: "give" opens on the Give
            // segment, "partners" (below) on the Partners programme.
            composable(GIVE_TAB_ROUTE) {
                org.nuruplace.member.feature.give.GiveTabScreen(
                    initial = org.nuruplace.member.feature.give.GiveSegment.Give,
                    onNavigate = { nav.navigate(it) },
                )
            }
            composable(
                SCHEDULES_ROUTE,
                arguments = listOf(navArgument("open") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                org.nuruplace.member.feature.give.SchedulesScreen(
                    onBack = { nav.popBackStack() },
                    openScheduleId = entry.arguments?.getString("open")?.takeIf { it.isNotBlank() },
                    onOpenPledge = { nav.navigate(org.nuruplace.member.feature.give.pledgeRoute(it)) },
                )
            }
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
                GivingStatementScreen(
                    onBack = { nav.popBackStack() },
                    onOpenReceipt = { nav.navigate("receipt/$it") },
                    // PARTNER PLEDGES' "Partners statement →" (spec §3d). A
                    // partners statement already below (its foot opened this
                    // one) is replaced, never stacked twice.
                    onOpenPartnersStatement = { y ->
                        nav.navigate(org.nuruplace.member.feature.give.partnersStatementRoute(y)) {
                            popUpTo(PARTNERS_STATEMENT_ROUTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            // The PARTNERS statement (owner 2026-09-25: "have the statement
            // separate for partners") — Pledged / Paid / Remaining, the
            // pledges, pledge-tied payments by month and its own PDF, for the
            // year the Partners tab was showing. Its foot opens "statement".
            composable(
                PARTNERS_STATEMENT_ROUTE,
                arguments = listOf(navArgument("year") { type = NavType.IntType; defaultValue = 0 }),
            ) { entry ->
                org.nuruplace.member.feature.give.PartnersStatementScreen(
                    initialYear = entry.arguments?.getInt("year")?.takeIf { it > 0 },
                    onBack = { nav.popBackStack() },
                    onOpenReceipt = { nav.navigate("receipt/$it") },
                    // A COMMITMENTS row → that pledge's own page (iOS).
                    onOpenPledge = { nav.navigate(org.nuruplace.member.feature.give.pledgeRoute(it)) },
                    onOpenGivingStatement = { nav.navigate("statement") },
                    memberName = me?.profile?.fullName,
                )
            }
            // Partners — the Give tab opened on its Partners segment (the
            // Home invite's "Become a partner", pledge nudges and pushes land
            // here). The money reporting stays on Give.
            composable("partners") {
                org.nuruplace.member.feature.give.GiveTabScreen(
                    initial = org.nuruplace.member.feature.give.GiveSegment.Partners,
                    onNavigate = { nav.navigate(it) },
                )
            }
            // Partners with one pledge open (Giving Cycle 5): its payments,
            // what the member told the office, and its actions.
            composable(
                PARTNERS_PLEDGE_ROUTE,
                arguments = listOf(navArgument("pledgeId") { type = NavType.StringType }),
            ) { entry ->
                org.nuruplace.member.feature.give.GiveTabScreen(
                    initial = org.nuruplace.member.feature.give.GiveSegment.Partners,
                    onNavigate = { nav.navigate(it) },
                    openPledgeId = entry.arguments?.getString("pledgeId")?.takeIf { it.isNotBlank() },
                )
            }
            // Give, preset for a department need (spec §4): fund "gift", the
            // need's remaining amount, and need_id carried into the intent.
            composable(
                GIVE_NEED_ROUTE,
                arguments = listOf(
                    navArgument("needId") { type = NavType.StringType },
                    navArgument("amount") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                    // The need's currency (Giving Cycle 5) — absent = shillings.
                    navArgument("currency") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val needId = entry.arguments?.getString("needId") ?: ""
                org.nuruplace.member.feature.give.GiveTabScreen(
                    initial = org.nuruplace.member.feature.give.GiveSegment.Give,
                    onNavigate = { nav.navigate(it) },
                    initialPreset = org.nuruplace.member.feature.give.GivePreset(
                        fundId = org.nuruplace.member.feature.give.NEED_GIFT_FUND,
                        amountMinor = entry.arguments?.getInt("amount")?.takeIf { it > 0 },
                        needId = needId.ifBlank { null },
                        title = entry.arguments?.getString("title")?.takeIf { it.isNotBlank() },
                        currency = entry.arguments?.getString("currency")?.takeIf { it.isNotBlank() },
                    ),
                )
            }
            // Give, opened on one gift's result (Giving Cycle 3): a
            // giving_gift_failed push lands here — the reason, the hint and
            // Try again — and the member stays on the Give tab after.
            composable(
                GIVE_GIFT_ROUTE,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                org.nuruplace.member.feature.give.GiveTabScreen(
                    initial = org.nuruplace.member.feature.give.GiveSegment.Give,
                    onNavigate = { nav.navigate(it) },
                    followTransactionId = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() },
                )
            }
            composable(
                "receipt/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                GivingReceiptScreen(
                    transactionId = entry.arguments?.getString("id") ?: "",
                    onBack = { nav.popBackStack() },
                    // "View statement": back down to the statement when it is
                    // below us (one of its rows opened this receipt), else push
                    // it — never two statements on the stack.
                    onOpenStatement = { nav.navigate("statement") { popUpTo("statement"); launchSingleTop = true } },
                    // The Pledge row opens that pledge's own page (iOS pledgeRow).
                    onOpenPledge = { nav.navigate(org.nuruplace.member.feature.give.pledgeRoute(it)) },
                )
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
            // The broadcaster's studio (formerly the "Live" tab — since the
            // Partners programme restructure it is reached from Events'
            // Broadcast card's "My Broadcasts" row and by deep link/push; a
            // non-broadcaster landing here just renders LiveTabScreen's own
            // defensive fallback, the server gating every /live write).
            composable("live") {
                org.nuruplace.member.feature.live.LiveTabScreen(me = me, onNavigate = { nav.navigate(it) })
            }
            // Nuru Live discovery — the lightweight forwarding destination a
            // tapped Live notice lands on: a push, or its row in the inbox —
            // one router (NuruMessagingService.destFor, EXPERIENCE.md §7.2
            // #3). A notice alone lacks kind/viewers/startedAt, so this
            // re-fetches GET /live/now itself and forwards straight into the
            // player — replacing itself, so Back returns to wherever the tap
            // came from (the inbox, Home) — or, once the stream is over, says
            // so calmly: "This Live has ended" and its name (LiveEndedState),
            // never a bounce to Home. `?streamId=` names the stream to open —
            // a Live notice's own stream (`&title=` its name), or a ringing
            // invite's Join (LiveInvite.kt),
            // which has already accepted by the time it lands here, so the
            // player's first pulse finds this member on the stage; without it,
            // the newest watchable one. A named stream that has ended is never
            // swapped for some other stream. A fetch that fails says what
            // happened (§4) — never "ended" on a guess.
            composable(
                LIVE_NOW_ROUTE,
                arguments = listOf(
                    navArgument("streamId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    // The Live's name, from the notice — said if it has ended.
                    navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                val wanted = entry.arguments?.getString("streamId")
                val wantedName = entry.arguments?.getString("title")
                val context = androidx.compose.ui.platform.LocalContext.current
                // null while looking; then the Live has ended, or the fetch failed.
                var outcome by remember { androidx.compose.runtime.mutableStateOf<LiveNowOutcome?>(null) }
                var attempt by remember { mutableIntStateOf(0) }
                androidx.compose.runtime.LaunchedEffect(wanted, attempt) {
                    outcome = null
                    // Already watching the invited stream: go back to THAT
                    // player, whose own pulse picks the accept up. Opening a
                    // second one would dispose the first — and a player that
                    // had already started for the stage leaves it as it goes.
                    val below = nav.previousBackStackEntry
                    if (!wanted.isNullOrBlank() && below?.destination?.route?.startsWith("live-player") == true &&
                        below.arguments?.getString("streamId") == wanted
                    ) {
                        nav.popBackStack()
                        return@LaunchedEffect
                    }
                    val failed = LiveDiscoveryCenter.refresh()
                    if (failed != null) {
                        outcome = LiveNowOutcome.Failed(org.nuruplace.member.data.net.ApiException.state(failed, context))
                        return@LaunchedEffect
                    }
                    val target = org.nuruplace.member.feature.live.liveForwardTarget(LiveDiscoveryCenter.streams.value, wanted)
                    if (target != null) {
                        LiveDiscoveryCenter.markSeen(target.streamId)
                        nav.navigate(liveNowRoute(target)) { popUpTo(LIVE_NOW_ROUTE) { inclusive = true } }
                    } else {
                        outcome = LiveNowOutcome.Ended
                    }
                }
                when (val o = outcome) {
                    LiveNowOutcome.Ended -> org.nuruplace.member.feature.live.LiveEndedState(name = wantedName, onBack = { nav.popBackStack() })
                    is LiveNowOutcome.Failed -> Box(
                        Modifier.fillMaxSize().background(Nuru.paper).padding(Spacing.screen),
                        contentAlignment = Alignment.Center,
                    ) {
                        org.nuruplace.member.ui.components.FailedState(o.message, onRetry = { attempt++ }, onBack = { nav.popBackStack() })
                    }
                    null -> Box(Modifier.fillMaxSize().background(Nuru.paper), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = Nuru.gold)
                    }
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
            composable("mentor") { org.nuruplace.member.feature.profile.MentorScreen(onBack = { nav.popBackStack() }) }
            // "Ask to be connected" (EXPERIENCE.md §9.2 #12) — YOUR WEEK's
            // Cell row for a member with no cell. Already in one: the cell page.
            composable("cell-connect") {
                org.nuruplace.member.feature.home.CellConnectScreen(
                    onBack = { nav.popBackStack() },
                    onInCell = { nav.navigate("cell-info") { popUpTo("cell-connect") { inclusive = true } } },
                    onOpenThread = { nav.navigate("chat/$it?ctx=pastoral") },
                )
            }
            composable("cell-info") {
                org.nuruplace.member.feature.home.CellInfoScreen(
                    me = me,
                    onBack = { nav.popBackStack() },
                    onNavigate = { nav.navigate(it) },
                )
            }
            // The cell's people (behind Cell info's faces row). "Message" opens
            // the ordinary DM thread — the same `chat/{id}` destination the
            // inbox, the new-message directory and the discipler dossier use.
            composable("cell-roster") {
                org.nuruplace.member.feature.home.CellRosterScreen(
                    me = me,
                    onBack = { nav.popBackStack() },
                    onOpenThread = { nav.navigate("chat/$it") },
                )
            }
            // Nuru Live (L2, viewer-only) — the full-screen player is one
            // destination fed entirely via query args (Home's banner, the
            // cell card, and Replays rows all build this route through
            // liveNowRoute()/liveRecordingRoute() so the resolved media url
            // and heartbeat/live-ness travel with the navigation, not a
            // second fetch).
            composable(
                "live-player?streamId={streamId}&url={url}&fallbackUrl={fallbackUrl}&title={title}&kind={kind}&live={live}&startedAt={startedAt}&viewers={viewers}&startedByName={startedByName}&startedByAvatarUrl={startedByAvatarUrl}",
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
                    navArgument("startedByAvatarUrl") { type = NavType.StringType; nullable = true; defaultValue = null },
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
                    startedByAvatarUrl = a?.getString("startedByAvatarUrl")?.takeIf { it.isNotBlank() },
                    myUserId = me?.profile?.userId,
                    myFullName = me?.profile?.fullName,
                    myAvatarUrl = me?.profile?.avatarUrl,
                    onBack = { nav.popBackStack() },
                    onOpenReplays = { nav.navigate("live-replays") { popUpTo("home") } },
                )
            }
            // Optional scope: the cell page opens its own cell's replays
            // ("live-replays?scope=cell&cellId=…", EXPERIENCE.md §7.4 #16, as
            // iOS); a bare "live-replays" is every replay, as before.
            composable(
                "live-replays?scope={scope}&cellId={cellId}&cellName={cellName}",
                arguments = listOf(
                    navArgument("scope") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("cellId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("cellName") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                org.nuruplace.member.feature.live.LiveReplaysScreen(
                    onBack = { nav.popBackStack() },
                    onOpenRecording = { row -> nav.navigate(org.nuruplace.member.feature.live.liveRecordingRoute(row)) },
                    scope = entry.arguments?.getString("scope"),
                    cellId = entry.arguments?.getString("cellId"),
                    cellName = entry.arguments?.getString("cellName"),
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
                    myUserId = me?.profile?.userId,
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
                    load = { org.nuruplace.member.feature.pathway.QuizSet(Net.client.api.quiz(id).questions) },
                    submit = { answers, mut ->
                        val r = Net.client.api.submitQuiz(id, SubmitBody(mut, answers))
                        QuizVerdict(r.scoreAchieved, r.passMark, r.isPassed, r.requiresManualReview)
                    },
                    onDone = { nav.popBackStack() },
                    moduleId = id,
                    draftKey = "module:$id",
                )
            }
            composable(
                "exam/{n}",
                arguments = listOf(navArgument("n") { type = NavType.IntType }),
            ) { entry ->
                val n = entry.arguments?.getInt("n") ?: 1
                QuizScreen(
                    // One name (EXPERIENCE.md §9.1 rule 1); its front door
                    // names the count and the pass mark the server sends (rule 2).
                    title = org.nuruplace.member.feature.pathway.ExamWords.name(n),
                    load = {
                        Net.client.api.levelExam(n).let { e ->
                            org.nuruplace.member.feature.pathway.QuizSet(e.questions, e.questionCount, e.passMark)
                        }
                    },
                    examLevel = n,
                    submit = { answers, mut ->
                        val r = Net.client.api.submitLevelExam(n, SubmitBody(mut, answers))
                        QuizVerdict(r.scoreAchieved, r.passMark, r.isPassed, r.requiresManualReview)
                    },
                    onDone = { nav.popBackStack() },
                    onPassed = { nav.navigate("level-complete/$n") { popUpTo("pathway") } },
                    draftKey = "level:$n",
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

