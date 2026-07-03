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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.nuruplace.member.feature.grow.PlanDetailScreen
import org.nuruplace.member.feature.grow.PrayerJournalScreen
import org.nuruplace.member.feature.grow.ReadingPlansScreen
import org.nuruplace.member.feature.grow.VerseLibraryScreen
import org.nuruplace.member.feature.home.HomeScreen
import org.nuruplace.member.feature.pathway.LevelDetailScreen
import org.nuruplace.member.feature.pathway.LevelsScreen
import org.nuruplace.member.feature.pathway.ModuleScreen
import org.nuruplace.member.feature.pathway.QuizScreen
import org.nuruplace.member.feature.pathway.QuizVerdict
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("pathway", "Pathway", Icons.AutoMirrored.Filled.List),
    Tab("grow", "Grow", Icons.Filled.Favorite),
    Tab("community", "Community", Icons.Filled.Person),
    Tab("profile", "Profile", Icons.Filled.AccountBox),
)

@Composable
fun MainShell(auth: AuthStore, me: MeResponse?) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onTab = TABS.any { it.route == route }

    Scaffold(
        containerColor = Nuru.paper,
        bottomBar = {
            if (onTab) NavigationBar(containerColor = Nuru.white) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            if (route != tab.route) nav.navigate(tab.route) {
                                popUpTo("home"); launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label, style = NuruType.micro) },
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
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(me, onSignOut = { auth.signOut() }) }
            composable("pathway") { LevelsScreen(onOpenLevel = { nav.navigate("level/$it") }) }
            composable("grow") { GrowHubScreen(onOpen = { nav.navigate(it) }) }
            composable("devotional") { DevotionalScreen(onBack = { nav.popBackStack() }) }
            composable("memory-verses") { MemoryVerseScreen(onBack = { nav.popBackStack() }) }
            composable("plans") {
                ReadingPlansScreen(onBack = { nav.popBackStack() }, onOpenPlan = { nav.navigate("plan/$it") })
            }
            composable(
                "plan/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                PlanDetailScreen(planId = entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
            }
            composable("prayers") { PrayerJournalScreen(onBack = { nav.popBackStack() }) }
            composable("verses") { VerseLibraryScreen(onBack = { nav.popBackStack() }) }
            composable("community") { Placeholder("Community", "Prayer wall, chat and events land here.") }
            composable("profile") { ProfileScreen(me) { auth.signOut() } }

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
                )
            }
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

@Composable
private fun ProfileScreen(me: MeResponse?, onSignOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Nuru.paper).padding(Spacing.screen),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(me?.profile?.fullName ?: "Member", style = NuruType.title, color = Nuru.ink)
        me?.profile?.email?.let { Text(it, style = NuruType.body, color = Nuru.ink600) }
        Spacer(Modifier.height(Spacing.lg))
        TextButton(onClick = onSignOut) { Text("Sign out", style = NuruType.cardCta, color = Nuru.danger) }
    }
}
