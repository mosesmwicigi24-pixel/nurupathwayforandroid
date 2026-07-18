// Authed shell — five-tab bottom bar (Home · Pathway · Grow · Community ·
// Profile) over a NavHost. Pathway carries its own stack: Levels → Level →
// Module → Quiz/Exam. Grow/Community/Profile are placeholders until their
// phases land. Port of the iOS RootView tab shell.
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.nuruplace.member.feature.community.ChatInboxScreen
import org.nuruplace.member.feature.community.ChatThreadScreen
import org.nuruplace.member.feature.community.CommunityHubScreen
import org.nuruplace.member.feature.community.PrayerWallDetailScreen
import org.nuruplace.member.feature.events.AllEventsCalendarScreen
import org.nuruplace.member.feature.events.EventDetailScreen
import org.nuruplace.member.feature.events.EventsScreen
import org.nuruplace.member.feature.events.NotificationsScreen
import org.nuruplace.member.feature.give.GivingReceiptScreen
import org.nuruplace.member.feature.give.GivingScreen
import org.nuruplace.member.feature.give.GivingStatementScreen
import org.nuruplace.member.feature.home.HomeScreen
import org.nuruplace.member.feature.profile.AssistantScreen
import org.nuruplace.member.feature.profile.GiftsScreen
import org.nuruplace.member.feature.profile.ProfileScreen
import org.nuruplace.member.feature.profile.ResourcesScreen
import org.nuruplace.member.feature.pathway.LevelDetailScreen
import org.nuruplace.member.feature.pathway.LevelsMapScreen
import org.nuruplace.member.feature.pathway.PathwayHubScreen
import org.nuruplace.member.feature.pathway.ModuleScreen
import org.nuruplace.member.feature.pathway.QuizScreen
import org.nuruplace.member.feature.pathway.QuizVerdict
import org.nuruplace.member.ui.components.CelebrationHost
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("pathway", "Pathway", Icons.Filled.MenuBook),
    Tab("plans", "Plans", Icons.Filled.Bookmark),
    Tab("events", "Events", Icons.Filled.CalendarMonth),
    Tab("chat", "Chat", Icons.AutoMirrored.Filled.Chat),
    Tab("give", "Give", Icons.Filled.VolunteerActivism),
    Tab("profile", "Profile", Icons.Filled.Person),
)

@Composable
fun MainShell(auth: AuthStore, me: MeResponse?) {
    // Register this device for FCM push once we're in the authed shell (§D-M9).
    org.nuruplace.member.data.firebase.PushRegistration()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onTab = TABS.any { it.route == route }
    val rootView = androidx.compose.ui.platform.LocalView.current

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
    // Pathway, Prayer Wall, Give). Consumed once per intent.
    LaunchedEffect(Unit) {
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

    Scaffold(
        containerColor = Nuru.paper,
        bottomBar = {
            if (onTab) NavigationBar(containerColor = Nuru.white) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            if (route != tab.route) {
                                org.nuruplace.member.ui.components.Haptics.tick(rootView)
                                nav.navigate(tab.route) {
                                    popUpTo("home"); launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, tab.label, modifier = Modifier.size(22.dp)) },
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
                    onOpenChat = { conversationId -> nav.navigate("chat/$conversationId") },
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
                val initialTab = if (entry.arguments?.getString("tab") == "corporate") {
                    org.nuruplace.member.feature.community.PrayerRoomTab.Corporate
                } else {
                    org.nuruplace.member.feature.community.PrayerRoomTab.Private
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
            composable("chat") {
                ChatInboxScreen(
                    onOpenThread = { nav.navigate("chat/$it") },
                    onNewMessage = { nav.navigate("new-message") },
                    onOpenAssistant = { nav.navigate("assistant") },
                    onOpenNotifications = { nav.navigate("notifications") },
                    // Broadcast is between the shepherd and the individual — SuperAdmin
                    // only, not even Admins (product decision, 2026-07).
                    isStaff = me?.profile?.role == "SuperAdmin",
                    onOpenBroadcast = { nav.navigate("broadcast/$it") },
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
                "chat/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                ChatThreadScreen(conversationId = entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("events") {
                EventsScreen(
                    onOpenEvent = { id, end -> nav.navigate("event/$id?end=${android.net.Uri.encode(end ?: "")}") },
                    onOpenCalendar = { nav.navigate("events-calendar") },
                    onOpenAnnouncement = { nav.navigate("announcement/$it") },
                    onOpenAnnouncements = { nav.navigate("announcements") },
                    onOpenNotifications = { nav.navigate("notifications") },
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
                GivingScreen(onBack = { nav.popBackStack() }, onOpenStatement = { nav.navigate("statement") }, onOpenSchedules = { nav.navigate("schedules") })
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
            composable("profile") { ProfileScreen(me, onOpen = { nav.navigate(it) }, onSignOut = { auth.signOut() }) }
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
            composable("cell-info") { org.nuruplace.member.feature.home.CellInfoScreen(onBack = { nav.popBackStack() }) }
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

