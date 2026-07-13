// Home — the server-driven dashboard, a faithful port of the iOS HomeView feed.
// Order and styling mirror NuruMember/Features/Home/HomeView.swift + HomeCards.swift:
// cream header (date · bell/radio/progress · greeting · level jewel), then the card
// stack — reflection strip, For-You resume hero, today's rhythm, featured video,
// verse, prayer wall, reading/journal minis, this-week cell, disciplers, featured
// announcement, continue-level, progress ring, grow grid, upcoming, cohort, give.
package org.nuruplace.member.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Achievements
import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.data.net.CellSummary
import org.nuruplace.member.data.net.Discipler
import org.nuruplace.member.data.net.FeaturedAnnouncement
import org.nuruplace.member.data.net.FeaturedEvent
import org.nuruplace.member.data.net.FeaturedCell
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.NextAction
import org.nuruplace.member.data.net.PrayerWallPost
import org.nuruplace.member.data.net.RadioProgram
import org.nuruplace.member.data.net.ReadingPlanRow
import org.nuruplace.member.data.net.RhythmBody
import org.nuruplace.member.data.net.RhythmToday
import org.nuruplace.member.data.net.ScoresSummary
import org.nuruplace.member.data.net.TailoredVerse
import org.nuruplace.member.data.net.VerseReactionBody
import org.nuruplace.member.data.net.VerseReactions
import org.nuruplace.member.data.net.VerseUpsertBody
import org.nuruplace.member.data.net.WelcomeVideo
import org.nuruplace.member.ui.components.FitImage
import org.nuruplace.member.ui.components.HomeSkeleton
import org.nuruplace.member.ui.components.InlineVideo
import org.nuruplace.member.ui.components.CelebrationCenter
import org.nuruplace.member.ui.components.Moment
import org.nuruplace.member.ui.components.NuruRefreshBox
import org.nuruplace.member.ui.components.openExternal
import org.nuruplace.member.ui.components.pressScale
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    me: MeResponse?,
    onSignOut: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenGive: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onSelectTab: (String) -> Unit = onNavigate,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var rhythm by remember { mutableStateOf<RhythmToday?>(null) }
    var next by remember { mutableStateOf<NextAction?>(null) }
    var verse by remember { mutableStateOf<TailoredVerse?>(null) }
    var streak by remember { mutableStateOf<Achievements?>(null) }
    var featuredCell by remember { mutableStateOf<FeaturedCell?>(null) }
    var disciplers by remember { mutableStateOf<List<Discipler>>(emptyList()) }
    var welcomeVideo by remember { mutableStateOf<WelcomeVideo?>(null) }
    var announcement by remember { mutableStateOf<FeaturedAnnouncement?>(null) }
    var scores by remember { mutableStateOf<ScoresSummary?>(null) }
    var upcoming by remember { mutableStateOf<List<CalendarOccurrence>>(emptyList()) }
    var cohort by remember { mutableStateOf<CellSummary?>(null) }
    var plan by remember { mutableStateOf<ReadingPlanRow?>(null) }
    var prayers by remember { mutableStateOf<List<PrayerWallPost>>(emptyList()) }
    var radio by remember { mutableStateOf<RadioProgram?>(null) }
    var videoPlaying by remember { mutableStateOf(false) }
    var personalWord by remember { mutableStateOf<String?>(null) }
    var verseReactions by remember { mutableStateOf<VerseReactions?>(null) }
    var verseSaved by remember { mutableStateOf(false) }
    var featuredEvent by remember { mutableStateOf<FeaturedEvent?>(null) }
    var letter by remember { mutableStateOf<org.nuruplace.member.data.net.PastoralLetter?>(null) }
    var showLetter by remember { mutableStateOf(false) }

    // One tick per full load — pull-to-refresh bumps it to re-run the batch;
    // `loadedOnce` keeps the skeleton from ever returning after first paint.
    var refreshTick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(refreshTick) {
        rhythm = runCatching { Net.client.api.rhythmToday() }.getOrNull()
        // Nuru's daily word — a blessing written for THIS member (server-side,
        // grounded in their streak/level/prayers, cached per day). iOS parity.
        personalWord = runCatching { Net.client.api.homeGreeting().greeting }.getOrNull()?.takeIf { it.isNotBlank() }
        verseReactions = runCatching { Net.client.api.verseReactions() }.getOrNull()
        featuredEvent = runCatching { Net.client.api.featuredEvent().data }.getOrNull()
        next = runCatching { Net.client.api.nextAction().action }.getOrNull()
        verse = runCatching { Net.client.api.homeVerse() }.getOrNull()
        streak = runCatching { Net.client.api.achievements() }.getOrNull()
        welcomeVideo = runCatching { Net.client.api.welcomeVideo() }.getOrNull()
        scores = runCatching { Net.client.api.scores() }.getOrNull()
        letter = runCatching { Net.client.api.latestLetter().letter }.getOrNull()
        announcement = runCatching { Net.client.api.featuredAnnouncement().data }.getOrNull()
        featuredCell = runCatching { Net.client.api.featuredCell().data }.getOrNull()
        disciplers = runCatching { Net.client.api.disciplers().data }.getOrDefault(emptyList())
        cohort = runCatching { Net.client.api.cellSummary() }.getOrNull()
        plan = runCatching { Net.client.api.plans().data.firstOrNull { it.enrolled } ?: Net.client.api.plans().data.firstOrNull() }.getOrNull()
        prayers = runCatching { Net.client.api.prayerWall("latest").data }.getOrDefault(emptyList())
        radio = runCatching { Net.client.api.radioNowPlaying() }.getOrNull()
        val today = LocalDate.now()
        val from = today.toString()
        val to = today.plusDays(45).toString()
        upcoming = runCatching { Net.client.api.calendar(from, to).data.sortedBy { it.startAt } }.getOrDefault(emptyList())
        refreshing = false
        loadedOnce = true

        // Human moments — REAL server-truth milestones only; keys remember (once each).
        if ((rhythm?.doneCount ?: 0) >= 3) CelebrationCenter.fire(Moment("rhythm-$today", "Today's rhythm complete", "Prayer, Word and reflection — all before the day ended."))
        val days = streak?.streak?.current ?: 0
        if (days in setOf(3, 7, 14, 21, 30, 50, 100)) CelebrationCenter.fire(Moment("streak-$days", "$days-day rhythm streak!"))
        streak?.badges.orEmpty().filter { CelebrationCenter.seenOnce("seen-badges", it.code) }
            .forEach { CelebrationCenter.fire(Moment("badge-${it.code}", "${it.name} earned!", "Badges celebrate your growth — keep walking.")) }
    }

    val pendingSync by Net.client.offline.pending.collectAsState()
    val level = me?.enrollment?.currentLevel ?: 1
    val reflectionDue = rhythm?.reflection == false
    // Entrance choreography — the decision is captured once and the process
    // flag flips, so a later return to Home composes instantly.
    val entrance = remember { (!homeEntrancePlayed).also { homeEntrancePlayed = true } }

    NuruRefreshBox(refreshing = refreshing, onRefresh = { refreshing = true; refreshTick++ }) {
        Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
            HomeHeader(
                firstName = me?.profile?.fullName?.substringBefore(' ') ?: "friend",
                streak = streak?.streak?.current ?: 0,
                level = level,
                overallPct = scores?.overall?.score ?: 0,
                trend = scores?.trend,
                personalWord = personalWord,
                onBell = onOpenNotifications,
                onRadio = { onNavigate("radio") },
            )

            Column(
                // 20dp between feed cards (iOS build-31 warmth) — the 16dp base read
                // congested with this many cards; each one gets room to breathe.
                Modifier.fillMaxWidth().padding(horizontal = Spacing.base).padding(top = Spacing.base),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (pendingSync > 0) {
                    Text(
                        "⏳ $pendingSync change${if (pendingSync == 1) "" else "s"} waiting to sync",
                        style = NuruType.micro, color = Nuru.eyebrow,
                    )
                }
                // First paint with nothing loaded yet → hold the page's shape (no
                // spinner, no pop); cards stagger in once the wire answers.
                if (!loadedOnce && rhythm == null && next == null && verse == null && streak == null) {
                    HomeSkeleton()
                    Spacer(Modifier.height(Spacing.tabBarSpace))
                    return@Column
                }
                radio?.takeIf { it.live }?.let { OnAirCard(it) { onNavigate("radio") } }
                // 0 · Today's verse leads the feed — right under the header (owner ask):
                // the daily Word first, then the featured welcome video. ON AIR pinned above.
                verse?.let { v ->
                    Entrance(entrance, 6) {
                        VerseCard(
                            v = v,
                            reactions = verseReactions,
                            saved = verseSaved,
                            onReact = { emoji ->
                                scope.launch {
                                    runCatching { Net.client.api.reactToVerse(VerseReactionBody(emoji)) }
                                        .onSuccess { verseReactions = it }
                                }
                            },
                            onSave = {
                                if (!verseSaved) scope.launch {
                                    runCatching {
                                        Net.client.api.saveVerse(
                                            VerseUpsertBody(
                                                savedVerseId = java.util.UUID.randomUUID().toString(),
                                                reference = v.reference,
                                                version = v.version,
                                                verseText = v.text,
                                                clientMutationId = java.util.UUID.randomUUID().toString(),
                                            ),
                                        )
                                    }.onSuccess { verseSaved = true }
                                }
                            },
                            onShare = {
                                val text = listOfNotNull(v.text?.let { "“$it”" }, "${v.reference} · ${v.version}").joinToString("\n")
                                fun shareText() {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(send, "Share verse"))
                                }
                                val art = v.art?.takeIf { it.url.isNotBlank() }
                                if (art != null) {
                                    // Render the tableau to a real photograph; fall back to
                                    // the words if the render can't happen (offline, CDN hiccup).
                                    scope.launch {
                                        val ok = VerseImageShare.share(
                                            context, art,
                                            v.text ?: "Your word is a lamp to my feet, and a light for my path.",
                                            v.reference, v.version,
                                        )
                                        if (!ok) shareText()
                                    }
                                } else {
                                    shareText()
                                }
                            },
                        )
                    }
                }
                // 0 · Featured welcome video — right under the header (owner ask); it
                // IS the "start here" moment, so it leads the feed. The thin ON AIR
                // bar stays pinned above it while a broadcast is live.
                welcomeVideo?.let { w ->
                    Entrance(entrance, 0) {
                        FeaturedVideo(w, videoPlaying, onPlay = { playable ->
                            if (w.isExternal) Unit else videoPlaying = true
                        }, onExternal = { url -> })
                    }
                }
                // 0b · The Sunday Letter knock (unread only) — opens the stationery reader.
                letter?.takeIf { it.isUnread }?.let { lt ->
                    LetterKnockCard(lt) { showLetter = true }
                    if (showLetter) {
                        LetterDialog(lt, onDismiss = { showLetter = false }, onRead = { letter = lt.copy(readAt = "read") })
                    }
                }
                // 0c · The hour's prayer line (liturgy, Phase 4).
                Entrance(entrance, 0) { LiturgyCard() }
                // 0d · Today's echo — the app remembers you (Wave 1).
                Entrance(entrance, 1) { HomeEchoCard() }
                if (reflectionDue) next?.let { a -> Entrance(entrance, 2) { ReflectionStrip(a) { onNavigate(routeFor(a)) } } }
                next?.let { a -> Entrance(entrance, 3) { ResumeHero(a, level) { onNavigate(routeFor(a)) } } }
                rhythm?.let { r -> Entrance(entrance, 4) { RhythmCard(r, streak?.streak?.current ?: 0) } }
                if (rhythm != null) SelahDivider()   // — selah: a rest for the eye
                if (prayers.isNotEmpty()) Entrance(entrance, 7) { PrayerWallCard(prayers.first(), prayers.size, onOpenWall = { onNavigate("prayer-wall") }, onOpenPost = { onNavigate("prayer-wall/${it}") }) }
                // 5b · Celebrate the family (moments, Phase 4).
                CelebrationsRail()
                MinisRow(plan, prayers.firstOrNull(), onReading = { onNavigate("plans") }, onJournal = { onNavigate("prayers") })
                featuredCell?.let { c -> FeaturedCellCard(c) { onNavigate("cell-info") } }
                if (disciplers.isNotEmpty()) DisciplersCard(disciplers) { onNavigate("mentor") }
                announcement?.let { a -> FeaturedAnnouncementCard(a, onAll = { onNavigate("announcements") }, onOpen = { onNavigate("announcement/${a.announcementId}") }) }
                ContinueLevelCard(next, level) { onNavigate(next?.let { routeFor(it) } ?: "pathway") }
                scores?.let { ProgressCard(it, level) { onNavigate("pathway") } }
                if (scores != null) SelahDivider()   // — selah: a rest before Grow
                GrowSection(onNavigate)
                featuredEvent?.let { FeaturedGatheringCard(it) { onSelectTab("events") } }
                UpcomingSection(upcoming, onSeeAll = { onSelectTab("events") }, onEvent = { onNavigate("event/${it.occurrenceId}?end=${android.net.Uri.encode(it.endAt)}") })
                EncouragementCard(prayers.size)
                CohortSection(cohort) { onNavigate("cell-info") }
                GiveCard { onSelectTab("give") }
                Spacer(Modifier.height(Spacing.tabBarSpace))
            }
        }
    }
}

// ─────────────────────────── Header ───────────────────────────

@Composable
private fun HomeHeader(
    firstName: String,
    streak: Int,
    level: Int,
    overallPct: Int,
    trend: org.nuruplace.member.data.net.ScoreTrend? = null,
    personalWord: String? = null,
    onBell: () -> Unit,
    onRadio: () -> Unit,
) {
    val now = LocalDate.now()
    val kicker = buildString {
        append(now.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault()).uppercase())
        append(" · ")
        append(now.month.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).uppercase())
        append(" ${now.dayOfMonth} · EAT")
    }
    val greeting = when (LocalDate.now().let { java.time.LocalTime.now().hour }) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Nuru.headerGradient)
            // The Scaffold already insets content below the status bar, so only a
            // small top pad is needed here (not the iOS 60pt island clearance).
            .padding(horizontal = Spacing.base)
            .padding(top = Spacing.md, bottom = Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kicker, style = NuruType.kicker, color = Nuru.eyebrow, modifier = Modifier.weight(1f))
            CircleButton("🔔", Nuru.goldChipBg, onBell)
            Spacer(Modifier.width(Spacing.sm))
            CircleButton("📻", Nuru.dangerBg, onRadio)
            Spacer(Modifier.width(Spacing.sm))
            Box {
                ProgressRing(pct = overallPct, size = 42.dp, stroke = 4.dp, track = Nuru.successBg, arc = Nuru.gold) {
                    Text("$overallPct", style = NuruType.micro, color = Nuru.successText, fontWeight = FontWeight.Bold)
                }
                trend?.takeIf { it.delta != 0 }?.let { t ->
                    TrendBadge(t, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text("$greeting, $firstName.", style = NuruType.greeting, color = Nuru.navy)
        // Nuru's daily word (GET /me/home/greeting) — hanging gold quote + serif
        // voice, mirroring iOS HomePersonalWord. Absent until the wire answers.
        personalWord?.let { word ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                Text("“", style = NuruType.title, color = Nuru.gold)
                Spacer(Modifier.width(6.dp))
                Text(
                    word,
                    style = NuruType.body.copy(fontStyle = FontStyle.Italic, lineHeight = 20.sp),
                    color = Nuru.ink600,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // Level jewel capsule
        Row(
            Modifier.clip(RoundedCornerShape(999.dp))
                .background(Nuru.white)
                .border(1.dp, Nuru.gold.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Level $level", style = NuruType.micro, color = Nuru.navy, fontWeight = FontWeight.SemiBold)
            Text("  ·  🔥 $streak-day", style = NuruType.micro, color = Nuru.eyebrow)
        }
    }
}

@Composable
private fun CircleButton(glyph: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).background(bg)
            .border(1.dp, Nuru.gold.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, style = NuruType.body) }
}

// ─────────────────────────── Primitives ───────────────────────────

@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    pad: androidx.compose.ui.unit.Dp = Spacing.base,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth()
            .shadow(6.dp, shape, spotColor = Color(0x1A0A2540), ambientColor = Color(0x0F0A2540))
            .clip(shape)
            .background(Nuru.white)
            .border(1.dp, Nuru.border, shape)
            .padding(pad),
        content = content,
    )
}

@Composable
private fun NavyCard(
    modifier: Modifier = Modifier,
    pad: androidx.compose.ui.unit.Dp = Spacing.base,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth()
            .shadow(6.dp, shape, spotColor = Color(0x330A1628))
            .clip(shape)
            .background(Nuru.homeNavyGradient)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(pad),
        content = content,
    )
}

@Composable
private fun CardKicker(text: String, color: Color = Nuru.eyebrow) =
    Text(text.uppercase(), style = NuruType.kicker, color = color)

@Composable
private fun SectionLabel(text: String) =
    Text(text.uppercase(), style = NuruType.sectionLabel, color = Nuru.eyebrow, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs))

@Composable
private fun RowScopeLink(text: String, onClick: () -> Unit) =
    Text(text, style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onClick() })

@Composable
private fun ProgressBar(pct: Int, color: Color, track: Color = Nuru.progressTrack, height: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(999.dp)).background(track),
    ) {
        Box(Modifier.fillMaxWidth(fraction = (pct.coerceIn(0, 100)) / 100f).height(height).clip(RoundedCornerShape(999.dp)).background(color))
    }
}

@Composable
private fun ProgressRing(
    pct: Int,
    size: androidx.compose.ui.unit.Dp,
    stroke: androidx.compose.ui.unit.Dp,
    track: Color,
    arc: Color,
    center: @Composable () -> Unit,
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(arc, -90f, 360f * (pct.coerceIn(0, 100) / 100f), false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        center()
    }
}

/** A tiny ▲/▼ badge — points earned or lost vs the previous 28 days. */
@Composable
private fun TrendBadge(t: org.nuruplace.member.data.net.ScoreTrend, modifier: Modifier = Modifier) {
    val up = !t.isDown
    Row(
        modifier.clip(RoundedCornerShape(999.dp))
            .background(if (up) Color(0xFF16A34A) else Color(0xFFDC6B26))
            .border(1.dp, Color.White, RoundedCornerShape(999.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (up) "▲" else "▼") + kotlin.math.abs(t.delta),
            style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Avatar(url: String?, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(999.dp)).background(Nuru.inputBg), contentAlignment = Alignment.Center) {
        if (url != null) AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(999.dp)))
        else Text("🙂", style = NuruType.body)
    }
}

// ─────────────────────────── Cards ───────────────────────────

@Composable
private fun OnAirCard(r: RadioProgram, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Nuru.homeNavyGradient)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape).clickable { onOpen() }.padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Nuru.homeNavyDark), contentAlignment = Alignment.Center) {
            if (r.artworkUrl != null) AsyncImage(model = r.artworkUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            else Text("📻", style = NuruType.body)
        }
        Column(Modifier.weight(1f)) {
            Text("● ON AIR · NURU RADIO", style = NuruType.micro, color = Nuru.liveRed, fontWeight = FontWeight.Bold)
            Text(r.title, style = NuruType.cardCta, color = Nuru.onNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.gold), contentAlignment = Alignment.Center) {
            Text("▶", color = Nuru.homeNavy, style = NuruType.body)
        }
    }
}

@Composable
private fun ReflectionStrip(a: NextAction, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Nuru.priorityBg)
            .border(1.dp, Nuru.gold.copy(alpha = 0.33f), shape).clickable { onClick() }.padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.white), contentAlignment = Alignment.Center) {
            Text("💬", style = NuruType.body)
        }
        Column(Modifier.weight(1f)) {
            Text("Reflection due today", style = NuruType.cardCta, color = Nuru.navy, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(a.title, style = NuruType.micro, color = Nuru.faintGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.homeNavy).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("Start reflection", style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ResumeHero(a: NextAction, level: Int, onClick: () -> Unit) {
    NavyCard(pad = Spacing.base) {
        CardKicker("For you today", Nuru.goldSoft)
        Spacer(Modifier.height(Spacing.sm))
        Text(a.title, style = NuruType.featureTitle, color = Nuru.onNavy)
        a.body.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(it, style = NuruType.caption, color = Nuru.onNavyDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Spacing.md))
        Box(
            Modifier.fillMaxWidth().pressScale().clip(RoundedCornerShape(16.dp)).background(Nuru.goldGradient).clickable { onClick() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text((a.ctaLabel.ifBlank { "Continue" }) + "  ›", style = NuruType.cardCta, color = Nuru.homeNavy, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RhythmCard(r: RhythmToday, streak: Int) {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (r.doneCount >= 3) "Today's rhythm complete 🎉" else "Today's rhythm", style = NuruType.heading, color = Nuru.ink, modifier = Modifier.weight(1f))
            if (streak > 0) Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("🔥 $streak", style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        // Read-only by design: the chips REFLECT real acts (a prayer posted or
        // encouraged, Scripture engaged, a reflection written) — the server ticks
        // them from interaction events; they are not tappable checkboxes.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RhythmTile("Prayer", r.prayer, Modifier.weight(1f))
            RhythmTile("Word", r.word, Modifier.weight(1f))
            RhythmTile("Reflection", r.reflection, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RhythmTile(label: String, done: Boolean, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (done) Nuru.successBg else Nuru.goldChipBg)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (done) "✓" else "🕐", style = NuruType.body, color = if (done) Nuru.successText else Nuru.goldChipText)
        Spacer(Modifier.height(Spacing.xs))
        Text(label, style = NuruType.micro, color = if (done) Nuru.successText else Nuru.goldChipText, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FeaturedVideo(v: WelcomeVideo, playing: Boolean, onPlay: (String) -> Unit, onExternal: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp), spotColor = Color(0x1A0A2540))
            .clip(RoundedCornerShape(20.dp)).background(Color(0xFFEEF0F3)).border(1.dp, Nuru.border, RoundedCornerShape(20.dp)).padding(Spacing.base),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Nuru.gold), contentAlignment = Alignment.Center) { Text("✝", color = Nuru.white, style = NuruType.micro) }
            Spacer(Modifier.width(Spacing.sm))
            Text("Nuru Pathway", style = NuruType.cardCta, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
            Text("  ✔", style = NuruType.micro, color = Nuru.gold)
            Spacer(Modifier.weight(1f))
            Text("FEATURED", style = NuruType.kicker, color = Nuru.eyebrow)
        }
        Spacer(Modifier.height(Spacing.md))
        val playable = v.playUrl
        if (playing && !v.isExternal && playable != null) {
            InlineVideo(playable, modifier = Modifier.clip(RoundedCornerShape(16.dp)))
        } else {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (v.isExternal && playable != null) openExternal(ctx, playable)
                        else if (playable != null) onPlay(playable)
                    },
                contentAlignment = Alignment.Center,
            ) {
                FitImage(v.thumbnailUrl, contentDescription = v.caption)
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.gold), contentAlignment = Alignment.Center) { Text("▶", color = Nuru.homeNavy, style = NuruType.title) }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // Caption is the heading; the sub-line only earns its place when it adds
        // information (an uncaptioned video used to print the same line twice).
        Text(v.caption ?: "Start here — what the journey looks like", style = NuruType.heading, color = Nuru.ink)
        if (v.caption != null && v.caption != "Start here — what the journey looks like") {
            Text("Start here — what the journey looks like", style = NuruType.caption, color = Nuru.ink600)
        }
    }
}

// Fixed reaction palette — must match iOS HomeView.verseReactionEmojis; the
// server enum (home REACTIONS) is the source of truth.
private val VERSE_REACTIONS = listOf("❤️", "🙏", "🔥", "🙌", "👍")

@Composable
private fun VerseCard(
    v: TailoredVerse,
    reactions: VerseReactions? = null,
    saved: Boolean = false,
    onReact: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onShare: () -> Unit = {},
) {
    val shape = RoundedCornerShape(20.dp)
    val art = v.art?.takeIf { it.url.isNotBlank() }
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Nuru.verseBg).border(1.dp, Nuru.gold.copy(alpha = 0.25f), shape),
    ) {
        if (art != null) {
            // The tableau: the day's photograph carries the verse (owner ask —
            // "something beautiful to behold" breaking the wall of text).
            VerseTableauHeader(art = art, text = v.text, refLine = "${v.reference} · ${v.version}", version = v.version)
        } else {
            // No art (offline first paint / older backend): the classic cream reading.
            Column(Modifier.padding(horizontal = Spacing.base).padding(top = Spacing.base)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardKicker(v.mood?.takeIf { it.isNotBlank() }?.let { "📖  Verse for today · $it" } ?: "📖  Verse for today")
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.white).border(1.dp, Nuru.border, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Text(v.version, style = NuruType.micro, color = Nuru.ink600)
                    }
                }
                Spacer(Modifier.height(Spacing.md))
                v.text?.let { Text(it, style = NuruType.featureTitle, color = Nuru.navy) }
                Spacer(Modifier.height(Spacing.sm))
                Text("${v.reference} · ${v.version}", style = NuruType.caption, color = Nuru.metaGray, fontWeight = FontWeight.SemiBold)
            }
        }
      Column(Modifier.padding(Spacing.base)) {
        v.reason?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.sm))
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("✦ Chosen for your season — $it", style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold)
            }
        }
        // One row (iOS parity): reaction chips left, Save + Share pushed right.
        Spacer(Modifier.height(Spacing.md))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            VERSE_REACTIONS.forEach { e ->
                val count = reactions?.counts?.get(e) ?: 0
                val mine = reactions?.mine == e
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp))
                        .background(if (mine) Nuru.goldChipBg else Nuru.white)
                        .border(1.dp, if (mine) Nuru.gold else Nuru.border, RoundedCornerShape(999.dp))
                        .clickable { onReact(e) }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(e, style = NuruType.caption)
                    if (count > 0) {
                        Text("$count", style = NuruType.micro, color = if (mine) Nuru.goldChipText else Nuru.ink600, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Row(
                Modifier.pressScale().clip(RoundedCornerShape(999.dp)).background(Nuru.white)
                    .border(1.dp, if (saved) Nuru.gold else Nuru.border, RoundedCornerShape(999.dp))
                    .clickable { onSave() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(if (saved) "♥" else "♡", style = NuruType.caption, color = if (saved) Nuru.gold else Nuru.navy)
                Text(if (saved) "Saved" else "Save", style = NuruType.micro, color = if (saved) Nuru.gold else Nuru.navy, fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier.pressScale().clip(RoundedCornerShape(999.dp)).background(Nuru.white)
                    .border(1.dp, Nuru.border, RoundedCornerShape(999.dp))
                    .clickable { onShare() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("↗", style = NuruType.caption, color = Nuru.navy)
                Text("Share", style = NuruType.micro, color = Nuru.navy, fontWeight = FontWeight.SemiBold)
            }
        }
      }
    }
}

@Composable
private fun PrayerWallCard(post: PrayerWallPost, count: Int, onOpenWall: () -> Unit, onOpenPost: (String) -> Unit) {
    HomeCard(modifier = Modifier.clickable { onOpenPost(post.postId) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardKicker("Pray for one another")
            Spacer(Modifier.weight(1f))
            RowScopeLink("Open wall ›", onOpenWall)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.authorAvatar, 40.dp)
            Spacer(Modifier.width(Spacing.sm))
            Text(post.authorName, style = NuruType.cardCta, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(Spacing.sm))
        post.title?.let { Text(it, style = NuruType.rowTitle, color = Nuru.ink) }
        Text(post.body, style = NuruType.caption, color = Nuru.ink600, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.sm))
        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text("🤲 ${post.prayCount} praying" + (post.commentCount?.let { " · $it replies" } ?: ""), style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold)
        }
        if (count > 1) {
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(count.coerceAtMost(5)) { i ->
                    Box(Modifier.padding(2.dp).size(if (i == 0) 8.dp else 6.dp).clip(RoundedCornerShape(999.dp)).background(if (i == 0) Nuru.gold else Nuru.ink300))
                }
            }
        }
    }
}

@Composable
private fun MinisRow(plan: ReadingPlanRow?, journal: PrayerWallPost?, onReading: () -> Unit, onJournal: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // Reading plan
        MiniCard(Modifier.weight(1f).clickable { onReading() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Nuru.indigoBg), contentAlignment = Alignment.Center) { Text("📖", style = NuruType.body) }
                Spacer(Modifier.weight(1f))
                val pct = plan?.let { if (it.dayCount > 0) ((it.currentDay ?: 0) * 100 / it.dayCount) else 0 } ?: 0
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.indigoBg).padding(horizontal = 8.dp, vertical = 3.dp)) { Text("$pct%", style = NuruType.micro, color = Nuru.indigo, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(Spacing.sm))
            CardKicker("Reading plan", Nuru.indigo)
            Text(plan?.title ?: "Start a plan", style = NuruType.rowTitle, color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            plan?.let { Text("Day ${it.currentDay ?: 1} of ${it.dayCount}", style = NuruType.micro, color = Nuru.ink600) }
            Spacer(Modifier.height(Spacing.sm))
            ProgressBar(plan?.let { if (it.dayCount > 0) ((it.currentDay ?: 0) * 100 / it.dayCount) else 0 } ?: 0, Nuru.indigo, height = 6.dp)
        }
        // Prayer journal
        MiniCard(Modifier.weight(1f).clickable { onJournal() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Nuru.dangerBg), contentAlignment = Alignment.Center) { Text("🤍", style = NuruType.body) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.warningBg).padding(horizontal = 8.dp, vertical = 3.dp)) { Text("journal", style = NuruType.micro, color = Nuru.answeredText, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(Spacing.sm))
            CardKicker("Prayer journal", Nuru.danger)
            Text(journal?.title ?: "Your prayers", style = NuruType.rowTitle, color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(journal?.body ?: "Keep a record of what you're praying for.", style = NuruType.micro, color = Nuru.ink600, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniCard(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.shadow(6.dp, shape, spotColor = Color(0x140A2540)).clip(shape).background(Nuru.white).border(1.dp, Nuru.border, shape).padding(Spacing.base),
        content = content,
    )
}

@Composable
private fun FeaturedCellCard(c: FeaturedCell, onClick: () -> Unit) {
    HomeCard(modifier = Modifier.clickable { onClick() }, pad = 0.dp) {
        Box {
            FitImage(c.imageUrl, modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)))
            Box(Modifier.padding(Spacing.md).clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("● This week", style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.padding(Spacing.base)) {
            CardKicker("This week at Nuru")
            Spacer(Modifier.height(Spacing.xs))
            Text(c.name, style = NuruType.featureTitle, color = Nuru.ink)
            c.disciplerName?.let { Text(it + (c.disciplerRole?.let { r -> " · $r" } ?: ""), style = NuruType.caption, color = Nuru.ink600) }
            Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                c.focus?.let { Chip(it) }
                c.levelLabel?.let { Chip(it) }
            }
            c.meets?.let {
                Spacer(Modifier.height(Spacing.md))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nuru.verseBg).padding(Spacing.sm)) {
                    Text("📅  $it" + (c.nextSession?.let { n -> " · Next: $n" } ?: ""), style = NuruType.caption, color = Nuru.navy, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                c.room?.let { Text("📍 $it", style = NuruType.micro, color = Nuru.ink400) }
                Spacer(Modifier.weight(1f))
                Text(if (c.members > 0) "👥 ${c.members} members" else "👥 Be the first to join 🔥", style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DisciplersCard(list: List<Discipler>, onView: () -> Unit) {
    HomeCard(modifier = Modifier.clickable { onView() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardKicker("Meet your discipler")
            Spacer(Modifier.weight(1f))
            RowScopeLink("View ›", onView)
        }
        val d = list.first()
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.Top) {
            Avatar(d.avatarUrl, 44.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(d.fullName, style = NuruType.cardCta, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
                Text(d.roleLabel.uppercase(), style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.Bold)
                d.message?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(Spacing.xs))
                    Text("“$it”", style = NuruType.caption, color = Nuru.ink600, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FeaturedAnnouncementCard(a: FeaturedAnnouncement, onAll: () -> Unit, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs)) {
            SectionLabel("Featured announcement")
            Spacer(Modifier.weight(1f))
            RowScopeLink("View all", onAll)
        }
        HomeCard(modifier = Modifier.clickable { onOpen() }, pad = 0.dp) {
            FitImage(a.primaryImageUrl, modifier = Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)))
            Column(Modifier.padding(Spacing.base)) {
                Text(a.title, style = NuruType.featureTitle, color = Nuru.ink)
                Spacer(Modifier.height(Spacing.xs))
                Text(a.body, style = NuruType.caption, color = Nuru.ink600, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.sentAt?.let { fmtDate(it) } ?: "", style = NuruType.micro, color = Nuru.ink400)
                    Spacer(Modifier.weight(1f))
                    Text("Read more ›", style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ContinueLevelCard(next: NextAction?, level: Int, onClick: () -> Unit) {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.goldGradient), contentAlignment = Alignment.Center) { Text("▶", color = Nuru.homeNavy, style = NuruType.heading) }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                CardKicker("Continue · Level $level")
                Text(next?.title ?: "Foundations of Faith", style = NuruType.rowTitle, color = Nuru.ink)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nuru.homeNavy).clickable { onClick() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Continue  ›", style = NuruType.cardCta, color = Nuru.gold, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun ProgressCard(s: ScoresSummary, level: Int, onView: () -> Unit) {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your progress", style = NuruType.heading, color = Nuru.ink, modifier = Modifier.weight(1f))
            RowScopeLink("View pathway", onView)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(s.overall.score, 64.dp, 6.dp, Nuru.goldChipBg, Nuru.gold) {
                Text("${s.overall.score}", style = NuruType.rowTitle, color = Nuru.ink)
            }
            Spacer(Modifier.width(Spacing.base))
            Column {
                CardKicker("Overall growth")
                Text(s.overall.band.ifBlank { "Sprouting" }.replaceFirstChar { it.uppercase() }, style = NuruType.featureTitle, color = Nuru.gold)
                val t = s.trend
                if (t != null) {
                    val caption = when {
                        t.delta == 0 -> "Holding steady vs last 28 days"
                        t.isDown -> "▼ Down ${kotlin.math.abs(t.delta)} vs last 28 days"
                        else -> "▲ Up ${t.delta} vs last 28 days"
                    }
                    Text(caption, style = NuruType.caption, color = if (t.isDown) Color(0xFFDC6B26) else if (t.isUp) Color(0xFF16A34A) else Nuru.ink600)
                } else {
                    Text("Your rhythm across the disciplines", style = NuruType.caption, color = Nuru.ink600)
                }
            }
        }
        Spacer(Modifier.height(Spacing.base))
        val d = s.trend?.domains
        ScoreBar("Habits", s.habits.score, Nuru.gold, d?.get("habits"))
        ScoreBar("Word", s.word.score, Nuru.scoreWord, d?.get("word"))
        ScoreBar("Prayer", s.prayer.score, Nuru.scorePrayer, d?.get("prayer"))
        ScoreBar("Curriculum", s.curriculum.score, Nuru.homeNavy, d?.get("curriculum"))
        ScoreBar("Attendance", s.attendance.score, Nuru.success, d?.get("attendance"))
    }
}

@Composable
private fun ScoreBar(label: String, value: Int, color: Color, delta: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NuruType.caption, color = Nuru.ink600, modifier = Modifier.width(84.dp))
        Box(Modifier.weight(1f)) { ProgressBar(value, color, height = 8.dp) }
        // A whisper of movement vs the previous 28 days, next to the score.
        if (delta != null && delta != 0) {
            Text(
                (if (delta < 0) "▼" else "▲") + kotlin.math.abs(delta),
                style = NuruType.micro, fontWeight = FontWeight.Bold,
                color = if (delta < 0) Color(0xFFDC6B26) else Color(0xFF16A34A),
                modifier = Modifier.width(28.dp), textAlign = TextAlign.End,
            )
        } else {
            Spacer(Modifier.width(28.dp))
        }
        Text("$value", style = NuruType.caption, color = Nuru.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun GrowSection(onNavigate: (String) -> Unit) {
    Column {
        SectionLabel("Grow your faith")
        HomeCard(pad = Spacing.md) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GrowTile("Devotional", "Today's devotional", "🌞", Nuru.goldChipBg, Nuru.eyebrow, Modifier.weight(1f)) { onNavigate("devotional") }
                GrowTile("Reading plan", "Continue your plan", "📖", Nuru.indigoBg, Nuru.indigo, Modifier.weight(1f)) { onNavigate("plans") }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GrowTile("Hide His Word", "Memorize Scripture", "❝", Nuru.warningBg, Nuru.hideWordFg, Modifier.weight(1f)) { onNavigate("memory-verses") }
                GrowTile("Your Calling", "Discover your gifts", "✨", Nuru.callingBg, Nuru.callingFg, Modifier.weight(1f)) { onNavigate("gifts") }
            }
            Spacer(Modifier.height(Spacing.sm))
            GrowTile("Prayer Wall", "Pray with the family", "🤲", Nuru.dangerBg, Nuru.danger, Modifier.fillMaxWidth()) { onNavigate("prayer-wall") }
        }
    }
}

@Composable
private fun GrowTile(title: String, sub: String, glyph: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Nuru.surface).border(1.dp, Nuru.border, RoundedCornerShape(16.dp)).clickable { onClick() }.padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) { Text(glyph, color = fg, style = NuruType.body) }
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(title, style = NuruType.cardCta, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
            Text(sub, style = NuruType.micro, color = Nuru.ink600)
        }
    }
}

@Composable
private fun FeaturedGatheringCard(ev: FeaturedEvent, onOpen: () -> Unit) {
    // The ONE admin-featured event (portal "feature on homepage" toggle) —
    // previously served by GET /home/featured-event but rendered by no client.
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Nuru.white).border(1.dp, Nuru.border, shape).clickable { onOpen() }) {
        ev.primaryImageUrl?.let {
            AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.padding(Spacing.base)) {
            CardKicker("⭐ Featured gathering")
            Spacer(Modifier.height(Spacing.sm))
            Text(ev.title, style = NuruType.featureTitle, color = Nuru.navy)
            ev.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = NuruType.caption, color = Nuru.ink600, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(Spacing.sm))
            val whenText = runCatching {
                java.time.LocalDateTime.parse(ev.dtstartLocal.take(19))
                    .format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a"))
            }.getOrDefault(ev.dtstartLocal)
            Text(
                listOfNotNull(whenText, ev.location?.takeIf { it.isNotBlank() }).joinToString("  ·  "),
                style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun UpcomingSection(events: List<CalendarOccurrence>, onSeeAll: () -> Unit, onEvent: (CalendarOccurrence) -> Unit) {
    Column {
        SectionLabel("Upcoming")
        HomeCard {
            val ym = YearMonth.now()
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardKicker(ym.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()))
                Spacer(Modifier.weight(1f))
                RowScopeLink("See all", onSeeAll)
            }
            Spacer(Modifier.height(Spacing.sm))
            MiniMonth(ym, events)
            events.firstOrNull()?.let { e ->
                Spacer(Modifier.height(Spacing.md))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Nuru.surface).clickable { onEvent(e) }.padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.inputBg), contentAlignment = Alignment.Center) {
                        e.primaryImageUrl?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) } ?: Text("📅", style = NuruType.title)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text("● ${fmtEvent(e.startAt)}", style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.SemiBold)
                        Text(e.title, style = NuruType.rowTitle, color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (e.going > 0) "${e.going} going" else (e.location ?: ""), style = NuruType.micro, color = Nuru.ink600)
                    }
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.homeNavy).padding(horizontal = 14.dp, vertical = 8.dp)) { Text("RSVP", style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(ym: YearMonth, events: List<CalendarOccurrence>) {
    val today = LocalDate.now()
    val eventDays = remember(events) {
        events.mapNotNull { parseZdt(it.startAt) }.filter { it.year == ym.year && it.monthValue == ym.monthValue }.map { it.dayOfMonth }.toSet()
    }
    val first = ym.atDay(1)
    val lead = (first.dayOfWeek.value + 6) % 7 // Monday-first offset
    val days = ym.lengthOfMonth()
    val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
    Column {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { Text(it, style = NuruType.micro, color = Nuru.ink400, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(Spacing.xs))
        var day = 1
        val rows = ((lead + days + 6) / 7)
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val cell = r * 7 + c
                    if (cell < lead || day > days) {
                        Box(Modifier.weight(1f).height(40.dp))
                    } else {
                        val d = day
                        val isToday = today.year == ym.year && today.monthValue == ym.monthValue && today.dayOfMonth == d
                        Column(Modifier.weight(1f).height(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(30.dp).clip(RoundedCornerShape(999.dp)).background(if (isToday) Nuru.homeNavy else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) { Text("$d", style = NuruType.caption, color = if (isToday) Nuru.onNavy else Nuru.ink) }
                            if (eventDays.contains(d)) Box(Modifier.size(4.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.gold))
                        }
                        day++
                    }
                }
            }
        }
    }
}

@Composable
private fun EncouragementCard(prayerCount: Int) {
    if (prayerCount <= 0) return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Nuru.surface).border(1.dp, Nuru.border, RoundedCornerShape(20.dp)).padding(Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(40.dp).background(Nuru.gold))
        Spacer(Modifier.width(Spacing.md))
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.white), contentAlignment = Alignment.Center) { Text("✦", color = Nuru.gold, style = NuruType.heading) }
        Spacer(Modifier.width(Spacing.md))
        Text("Your community lifted $prayerCount prayers — stand with one of them today.", style = NuruType.body, color = Nuru.navy)
    }
}

@Composable
private fun CohortSection(cohort: CellSummary?, onOpen: () -> Unit) {
    Column {
        SectionLabel("Your cell")
        HomeCard {
            val cell = cohort?.cell
            Text(cell?.name ?: "You're not in a cell yet", style = NuruType.cardCta, color = Nuru.ink600)
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile("Leader", cell?.leader?.name ?: "—", Modifier.weight(1f))
                StatTile("Next gathering", cell?.next?.startAt?.let { fmtDate(it) } ?: "TBA", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatTile("Members", "${cell?.members ?: 0}", Modifier.weight(1f))
                StatTile("Attendance", "${cell?.attendance?.attended ?: 0}/${cell?.attendance?.expected ?: 0}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.md))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.white).border(1.dp, Nuru.border, RoundedCornerShape(14.dp)).clickable { onOpen() }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Open community  →", style = NuruType.cardCta, color = Nuru.ink, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Nuru.surface).padding(Spacing.md)) {
        Text(label.uppercase(), style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.xs))
        Text(value, style = NuruType.rowTitle, color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GiveCard(onGive: () -> Unit) {
    NavyCard(pad = Spacing.screen) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.gold), contentAlignment = Alignment.Center) { Text("🤲", style = NuruType.title) }
                Spacer(Modifier.height(Spacing.md))
                CardKicker("Support God's work", Nuru.goldSoft)
                Spacer(Modifier.height(Spacing.xs))
                Text("Sow into something eternal", style = NuruType.featureTitle, color = Nuru.onNavy, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Every gift carries the gospel further — raising disciples, sustaining the mission, and lighting the way for the next person to find Christ. Give cheerfully, as the Lord leads.",
                    style = NuruType.caption, color = Nuru.onNavyDim, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.base))
                Box(
                    Modifier.fillMaxWidth().pressScale().clip(RoundedCornerShape(16.dp)).background(Nuru.goldGradient).clickable { onGive() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("🤲  Give now  ›", style = NuruType.cardCta, color = Nuru.homeNavy, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(Spacing.sm))
                Text("Tithe & offering · M-Pesa, card and more", style = NuruType.micro, color = Nuru.onNavyFaint)
            }
        }
    }
}

// ─────────────────────────── entrance ───────────────────────────

// The Home entrance plays ONCE per process — back-navigation and tab returns
// must not replay the choreography.
private var homeEntrancePlayed = false

/** Fade + 12dp rise, 40ms stagger by [index]; a plain pass-through once the
 *  moment has passed. graphicsLayer only — zero layout shift after settling. */
@Composable
private fun Entrance(play: Boolean, index: Int, content: @Composable () -> Unit) {
    if (!play) { content(); return }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(index * 40L); settled = true }
    val t by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "homeEntrance",
    )
    Box(Modifier.graphicsLayer { alpha = t; translationY = (1f - t) * 12.dp.toPx() }) { content() }
}

// ─────────────────────────── helpers ───────────────────────────

/** Map a next-action route to an in-app destination. */
private fun routeFor(a: NextAction): String = when (a.route) {
    "module" -> a.params?.moduleId?.let { "module/$it" } ?: "pathway"
    "level", "pathway" -> "pathway"
    "devotional" -> "devotional"
    "memory_verse", "verse" -> "memory-verses"
    "prayer", "reflection" -> "prayers"
    "give" -> "give"
    else -> "pathway"
}

private fun parseZdt(s: String?): ZonedDateTime? {
    if (s.isNullOrBlank()) return null
    return runCatching { java.time.OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
        ?: runCatching { java.time.Instant.parse(s).atZone(ZoneId.systemDefault()) }.getOrNull()
}

private fun fmtEvent(s: String?): String =
    parseZdt(s)?.format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())) ?: ""

private fun fmtDate(s: String?): String =
    parseZdt(s)?.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())) ?: ""
