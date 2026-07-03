// Home — the server-driven dashboard: greeting + streak, today's rhythm ring
// (prayer · word · reflection, tappable to complete), the next-best-action hero,
// and the tailored "Verse for today". Port of the iOS HomeView.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Achievements
import org.nuruplace.member.data.net.NextAction
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RhythmBody
import org.nuruplace.member.data.net.RhythmToday
import org.nuruplace.member.data.net.TailoredVerse
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun HomeScreen(
    me: org.nuruplace.member.data.net.MeResponse?,
    onSignOut: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenGive: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var rhythm by remember { mutableStateOf<RhythmToday?>(null) }
    var next by remember { mutableStateOf<NextAction?>(null) }
    var verse by remember { mutableStateOf<TailoredVerse?>(null) }
    var streak by remember { mutableStateOf<Achievements?>(null) }

    LaunchedEffect(Unit) {
        rhythm = runCatching { Net.client.api.rhythmToday() }.getOrNull()
        next = runCatching { Net.client.api.nextAction().action }.getOrNull()
        verse = runCatching { Net.client.api.homeVerse() }.getOrNull()
        streak = runCatching { Net.client.api.achievements() }.getOrNull()
    }

    fun tick(kind: String) {
        scope.launch { rhythm = runCatching { Net.client.api.completeRhythm(RhythmBody(kind)) }.getOrNull() ?: rhythm }
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(Nuru.heroGradient).padding(horizontal = Spacing.screen, vertical = Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker("Nuru Pathway", modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenNotifications) { Text("🔔", style = NuruType.title) }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text("Hi, ${me?.profile?.fullName?.substringBefore(' ') ?: "friend"}", style = NuruType.display, color = Nuru.onNavy)
            streak?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text("🔥 ${it.streak.current}-day streak · Level ${me?.enrollment?.currentLevel ?: 1}", style = NuruType.body, color = Nuru.onNavyDim)
            }
        }

        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            // Today's rhythm
            rhythm?.let { r ->
                NuruCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Kicker("Today's rhythm")
                        Spacer(Modifier.weight(1f))
                        Text("${r.doneCount}/3", style = NuruType.micro, color = Nuru.goldLo)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        RhythmPill("Prayer", r.prayer) { tick("prayer") }
                        RhythmPill("Word", r.word) { tick("word") }
                        RhythmPill("Reflection", r.reflection) { tick("reflection") }
                    }
                }
            }

            // Next best action
            next?.let { a ->
                NuruCard {
                    Kicker(a.accent.ifBlank { "Next step" })
                    Spacer(Modifier.height(Spacing.xs))
                    Text(a.title, style = NuruType.cardTitle, color = Nuru.ink)
                    Text(a.body, style = NuruType.body, color = Nuru.ink600)
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(a.ctaLabel.ifBlank { "Continue" }, onClick = { onNavigate(routeFor(a)) })
                }
            }

            // Verse for today
            verse?.let { v ->
                NuruCard {
                    Kicker("Verse for today")
                    Spacer(Modifier.height(Spacing.sm))
                    v.text?.let { Text(it, style = NuruType.bodyLg, color = Nuru.ink) }
                    Spacer(Modifier.height(Spacing.xs))
                    Text("${v.reference} · ${v.version}", style = NuruType.caption, color = Nuru.goldLo)
                    v.reason?.let { Text(it, style = NuruType.micro, color = Nuru.ink400) }
                }
            }

            NuruCard(modifier = Modifier.clickable { onOpenGive() }) {
                Kicker("Give")
                Spacer(Modifier.height(Spacing.sm))
                Text("Return to God what is His — tithe & offerings.", style = NuruType.body, color = Nuru.ink600)
            }
            TextButton(onClick = onSignOut) { Text("Sign out", style = NuruType.cardCta, color = Nuru.danger) }
            Spacer(Modifier.height(Spacing.tabBarSpace))
        }
    }
}

/** Map a next-action route to an in-app destination. */
private fun routeFor(a: NextAction): String = when (a.route) {
    "module" -> a.params?.moduleId?.let { "module/$it" } ?: "pathway"
    "level", "pathway" -> "pathway"
    "devotional" -> "devotional"
    "memory_verse", "verse" -> "memory-verses"
    "prayer" -> "prayers"
    "give" -> "give"
    else -> "pathway"
}

@Composable
private fun RhythmPill(label: String, done: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Radii.pill))
            .background(if (done) Nuru.successBg else Nuru.surface)
            .border(1.dp, if (done) Nuru.success else Nuru.border, RoundedCornerShape(Radii.pill))
            .clickable(enabled = !done) { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            (if (done) "✓ " else "") + label,
            style = NuruType.cardCta,
            color = if (done) Nuru.successText else Nuru.ink,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
