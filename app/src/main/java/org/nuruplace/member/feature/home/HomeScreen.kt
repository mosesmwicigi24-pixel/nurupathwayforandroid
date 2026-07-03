// Home — the server-driven dashboard: greeting + streak, today's rhythm ring
// (prayer · word · reflection, tappable to complete), the next-best-action hero,
// and the tailored "Verse for today". Port of the iOS HomeView.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Achievements
import org.nuruplace.member.data.net.Discipler
import org.nuruplace.member.data.net.FeaturedCell
import org.nuruplace.member.data.net.Moment
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
    var featuredCell by remember { mutableStateOf<FeaturedCell?>(null) }
    var disciplers by remember { mutableStateOf<List<Discipler>>(emptyList()) }
    var moments by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var welcomeVideo by remember { mutableStateOf<org.nuruplace.member.data.net.WelcomeVideo?>(null) }
    var videoPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rhythm = runCatching { Net.client.api.rhythmToday() }.getOrNull()
        next = runCatching { Net.client.api.nextAction().action }.getOrNull()
        verse = runCatching { Net.client.api.homeVerse() }.getOrNull()
        streak = runCatching { Net.client.api.achievements() }.getOrNull()
        featuredCell = runCatching { Net.client.api.featuredCell().data }.getOrNull()
        disciplers = runCatching { Net.client.api.disciplers().data }.getOrDefault(emptyList())
        moments = runCatching { Net.client.api.moments().data }.getOrDefault(emptyList())
        welcomeVideo = runCatching { Net.client.api.welcomeVideo() }.getOrNull()
    }

    fun tick(kind: String) {
        scope.launch { rhythm = runCatching { Net.client.api.completeRhythm(RhythmBody(kind)) }.getOrNull() ?: rhythm }
    }

    val pendingSync by Net.client.offline.pending.collectAsState()

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
            // Priority strip — the top-of-home attention banner (Figma HomeTab).
            next?.let { a -> PriorityStrip(a, onClick = { onNavigate(routeFor(a)) }) }
            if (pendingSync > 0) {
                Text(
                    "⏳ $pendingSync change${if (pendingSync == 1) "" else "s"} waiting to sync",
                    style = NuruType.micro,
                    color = Nuru.goldLo,
                )
            }
            // Featured welcome video
            welcomeVideo?.let { v ->
                val ctx = androidx.compose.ui.platform.LocalContext.current
                NuruCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Kicker("Featured")
                        Spacer(Modifier.weight(1f))
                        v.caption?.let { Text(it, style = NuruType.micro, color = Nuru.ink400, maxLines = 1) }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    val playable = v.playUrl
                    when {
                        videoPlaying && !v.isExternal && playable != null ->
                            org.nuruplace.member.ui.components.InlineVideo(playable, modifier = Modifier.clip(RoundedCornerShape(Radii.card)))
                        else -> Box(
                            Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(Radii.card)).background(Nuru.navyDeep)
                                .clickable {
                                    if (v.isExternal && playable != null) org.nuruplace.member.ui.components.openExternal(ctx, playable)
                                    else if (playable != null) videoPlaying = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            v.thumbnailUrl?.let { AsyncImage(model = it, contentDescription = v.caption, modifier = Modifier.fillMaxWidth().height(200.dp)) }
                            Text("▶", style = NuruType.display, color = Nuru.gold)
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Start here — what the journey looks like", style = NuruType.caption, color = Nuru.ink600)
                }
            }

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

            // (Next best action now surfaces as the top priority strip.)

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

            // This week at Nuru — featured cell
            featuredCell?.let { c ->
                NuruCard(modifier = Modifier.clickable { onNavigate("cell-info") }) {
                    c.imageUrl?.let {
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(Radii.card)))
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    Kicker("This week at Nuru")
                    Spacer(Modifier.height(Spacing.xs))
                    Text(c.name, style = NuruType.cardTitle, color = Nuru.ink)
                    c.disciplerName?.let { d ->
                        Text(d + (c.disciplerRole?.let { " · $it" } ?: ""), style = NuruType.caption, color = Nuru.ink600)
                    }
                    c.meets?.let {
                        Spacer(Modifier.height(Spacing.xs))
                        Text("📅 $it" + (c.nextSession?.let { n -> " · Next: $n" } ?: ""), style = NuruType.micro, color = Nuru.goldLo)
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(if (c.members > 0) "${c.members} members" else "Be the first to join 🔥", style = NuruType.micro, color = Nuru.ink400)
                }
            }

            // Meet your discipler
            if (disciplers.isNotEmpty()) {
                NuruCard(modifier = Modifier.clickable { onNavigate("mentor") }) {
                    Kicker("Meet your discipler")
                    disciplers.take(3).forEach { d ->
                        Spacer(Modifier.height(Spacing.sm))
                        Text(d.fullName, style = NuruType.rowTitle, color = Nuru.ink)
                        Text(d.roleLabel.uppercase(), style = NuruType.micro, color = Nuru.goldLo)
                        d.message?.takeIf { it.isNotBlank() }?.let {
                            Text("“$it”", style = NuruType.caption, color = Nuru.ink600, maxLines = 3)
                        }
                    }
                }
            }

            // Moments — curated photo strip
            if (moments.isNotEmpty()) {
                Kicker("Moments")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    moments.take(10).forEach { m ->
                        AsyncImage(
                            model = m.imageUrl,
                            contentDescription = m.caption,
                            modifier = Modifier.size(140.dp).clip(RoundedCornerShape(Radii.card)),
                        )
                    }
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

/** Top-of-home attention banner — Figma HomeTab PriorityStrip. Rounded-18, warm
 *  cream fill with a gold hairline, a white icon chip, and a navy CTA pill with
 *  gold text. Data-bound to the member's next best action. */
@Composable
private fun PriorityStrip(a: NextAction, onClick: () -> Unit) {
    val (glyph, cta) = priorityFor(a)
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFFFFAEC))
            .border(1.dp, Nuru.gold.copy(alpha = 0.33f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.white),
            contentAlignment = Alignment.Center,
        ) { Text(glyph, style = NuruType.body) }
        Column(Modifier.weight(1f)) {
            Text(a.title, style = NuruType.cardCta, color = Nuru.navy, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(a.body.ifBlank { a.accent }, style = NuruType.micro, color = Nuru.ink400, maxLines = 1)
        }
        Box(
            Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.navy).padding(horizontal = 12.dp, vertical = 6.dp),
        ) { Text(cta, style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold) }
    }
}

/** (icon glyph, CTA label) for a priority strip, keyed off the action's route —
 *  mirrors the Figma variant map (reflection / event / certificate / catch-up). */
private fun priorityFor(a: NextAction): Pair<String, String> = when {
    a.route == "reflection" || a.accent.contains("reflection", true) || a.accent.contains("feedback", true) ->
        "✍️" to (a.ctaLabel.ifBlank { "Start reflection" })
    a.route == "event" || a.accent.contains("event", true) -> "📅" to (a.ctaLabel.ifBlank { "View event" })
    a.accent.contains("certificate", true) -> "🏅" to "View certificate"
    else -> "✨" to (a.ctaLabel.ifBlank { "Continue" })
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
