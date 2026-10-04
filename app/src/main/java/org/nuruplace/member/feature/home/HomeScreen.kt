// Home — the server-driven dashboard, a faithful port of the iOS HomeView feed.
// Styling mirrors NuruMember/Features/Home/HomeView.swift + HomeCards.swift: the
// cream header (date · scan/bell/live/score · greeting · level jewel), then the
// feed in pathway docs/EXPERIENCE.md §6.1's order — each pillar has one home,
// and Home points to it once:
//
//   1. live and on-air banners, only while live;
//   2. the owner's opening, in his order — verse → featured video → the
//      Sunday letter → what needs you today (or the reflection strip) → the
//      liturgy;
//   3. YOUR WEEK — Pathway · Plans · Events · Giving · Cell (YourWeek.kt);
//   4. the day — today's rhythm (and today's echo);
//   5. the family — the prayer wall, celebrations, the featured carousel,
//      the featured gathering;
//   6. growing — your progress, grow your faith, the encouragement banner;
//   7. support God's work — the giving banner, only while the Giving row
//      says "Give" (a member already giving isn't asked twice).
//
// The week block replaced the "For you today" hero, the continue-level card,
// the minis row, the plan-resume banner, the this-week cell and "Your cell"
// cards, the disciplers card (now grow's "Your discipler") and the
// upcoming-events list — every place they opened is a week row or a tab.
package org.nuruplace.member.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Achievements
import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.data.net.CellSummary
import org.nuruplace.member.data.net.FeaturedAnnouncement
import org.nuruplace.member.data.net.FeaturedEvent
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.HomeEventRow
import org.nuruplace.member.data.net.HomeNudge
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.LiveNowRow
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.MyRsvp
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.PathwaySummary
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
import org.nuruplace.member.feature.give.giveRailsLine
import org.nuruplace.member.feature.pathway.JourneyLine
import org.nuruplace.member.feature.pathway.JourneyState
import org.nuruplace.member.ui.components.HomeSkeleton
import org.nuruplace.member.ui.components.InlineVideoPlayer
import org.nuruplace.member.ui.components.VideoPosterFrame
import org.nuruplace.member.ui.components.CelebrationCenter
import org.nuruplace.member.ui.components.LiveStreamBanner
import org.nuruplace.member.ui.components.Moment
import org.nuruplace.member.ui.components.NuruRefreshBox
import org.nuruplace.member.ui.components.pressScale
import org.nuruplace.member.feature.live.GoLiveButton
import org.nuruplace.member.feature.live.GoLiveSetupSheet
import org.nuruplace.member.feature.live.canGoLive
import org.nuruplace.member.feature.live.liveBroadcastRoute
import org.nuruplace.member.feature.live.liveNowRoute
import org.nuruplace.member.feature.events.EV_ZONE
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import org.nuruplace.member.ui.theme.scaledLineHeight
import java.time.LocalDate
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
    var verse by remember { mutableStateOf<TailoredVerse?>(null) }
    var streak by remember { mutableStateOf<Achievements?>(null) }
    var welcomeVideo by remember { mutableStateOf<WelcomeVideo?>(null) }
    var announcement by remember { mutableStateOf<FeaturedAnnouncement?>(null) }
    var scores by remember { mutableStateOf<ScoresSummary?>(null) }
    var upcoming by remember { mutableStateOf<List<CalendarOccurrence>>(emptyList()) }
    var homeEvents by remember { mutableStateOf<List<HomeEventRow>>(emptyList()) }
    var cohort by remember { mutableStateOf<CellSummary?>(null) }
    // YOUR WEEK's own reads (EXPERIENCE.md §6.1): the plans, the member's
    // RSVPs, the partnership (its DUE rows and pledges) and the recurring
    // gifts. Null = never answered — that row says its "none" form; a failed
    // refresh keeps the last answer.
    var plans by remember { mutableStateOf<List<ReadingPlanRow>?>(null) }
    var rsvps by remember { mutableStateOf<List<MyRsvp>?>(null) }
    var partnership by remember { mutableStateOf<Partnership?>(null) }
    var gifts by remember { mutableStateOf<List<GivingSchedule>?>(null) }
    var prayers by remember { mutableStateOf<List<PrayerWallPost>>(emptyList()) }
    var radio by remember { mutableStateOf<RadioProgram?>(null) }
    var videoPlaying by remember { mutableStateOf(false) }
    var personalWord by remember { mutableStateOf<String?>(null) }
    var verseReactions by remember { mutableStateOf<VerseReactions?>(null) }
    var verseSaved by remember { mutableStateOf(false) }
    var featuredEvent by remember { mutableStateOf<FeaturedEvent?>(null) }
    // The member's journey (docs/EXPERIENCE.md §3): the pathway summary and
    // the current level's trail — one truth for the pill, the continue card
    // and the progress line, in the words the Pathway hub uses.
    var pathway by remember { mutableStateOf<PathwaySummary?>(null) }
    var currentTrail by remember { mutableStateOf<List<LevelModule>?>(null) }
    // The rails GET /giving/methods says can take a gift — the giving card
    // names only those (null = no answer yet: no rail named). A failed
    // refresh keeps the last answer.
    var giveRails by remember { mutableStateOf<org.nuruplace.member.data.net.GivingMethodsRes?>(null) }
    var letter by remember { mutableStateOf<org.nuruplace.member.data.net.PastoralLetter?>(null) }
    var showLetter by remember { mutableStateOf(false) }
    // "What needs you today" (GET /me/home/nudges) — empty = nothing waiting OR
    // the endpoint is unreachable; either way the old reflection strip stands in.
    var nudges by remember { mutableStateOf<List<HomeNudge>>(emptyList()) }
    // Nuru Live (L2, viewer-only) — GET /live/now returns church streams
    // always plus cell streams scoped to the caller's own cell; Home only
    // ever renders the church-scope one (CellInfoScreen renders the cell one
    // off this SAME shape from its own fetch).
    var liveNow by remember { mutableStateOf<List<LiveNowRow>>(emptyList()) }
    // Nuru Live (L3) — the "Go Live" setup sheet; Home offers whichever of
    // church/my-cell the member is eligible for (see GoLiveShared.kt for the
    // exact eligibility rule and its reasoning).
    var showGoLiveSheet by remember { mutableStateOf(false) }

    // The partner invitation. Whether it may be shown is decided entirely by
    // the server; Home asks once and presents whatever comes back. Nothing
    // about WHEN to ask is decided here — see PartnerInviteSheet.kt.
    var partnerInvite by remember {
        mutableStateOf<org.nuruplace.member.data.net.InviteCampaign?>(null)
    }
    var partnerInviteShowing by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        val d = runCatching { Net.client.api.partnerInvite() }.getOrNull()
        if (d != null && d.show && d.campaign != null) {
            partnerInviteShowing = d.showing ?: 1
            partnerInvite = d.campaign
            // Rendered, not merely decided — this is what the cap counts.
            runCatching { Net.client.api.inviteShown(d.campaign.campaignId) }
        }
    }
    partnerInvite?.let { c ->
        org.nuruplace.member.feature.give.PartnerInviteSheet(
            campaign = c,
            showing = partnerInviteShowing,
            onBecomePartner = {
                scope.launch {
                    runCatching {
                        Net.client.api.inviteOutcome(
                            c.campaignId,
                            org.nuruplace.member.data.net.InviteOutcomeBody("opened"))
                    }
                }
                partnerInvite = null
                // Opens Give, where Partners lives. NOT a payment sheet.
                onOpenGive()
            },
            onDismiss = { permanent ->
                scope.launch {
                    runCatching {
                        Net.client.api.inviteOutcome(
                            c.campaignId,
                            org.nuruplace.member.data.net.InviteOutcomeBody(
                                if (permanent) "declined" else "dismissed"))
                    }
                }
                partnerInvite = null
            },
        )
    }

    // One tick per full load — pull-to-refresh bumps it to re-run the batch;
    // `loadedOnce` keeps the skeleton from ever returning after first paint.
    var refreshTick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(refreshTick) {
        // YOUR WEEK's giving and RSVPs, side by side with everything below —
        // they add no wait to the page; the card is drawn once all answer.
        val partnershipRead = async { runCatching { Net.client.api.partnership() }.getOrNull() }
        val giftsRead = async { runCatching { Net.client.api.schedules().data }.getOrNull() }
        val rsvpsRead = async { runCatching { Net.client.api.myRsvps().data }.getOrNull() }
        // The bell's one count, beside them — pulled down, it asks again too.
        launch { org.nuruplace.member.ui.components.InboxUnread.refresh() }
        rhythm = runCatching { Net.client.api.rhythmToday() }.getOrNull()
        nudges = runCatching { Net.client.api.nudges().nudges }.getOrDefault(emptyList())
        // Nuru's daily word — a blessing written for THIS member (server-side,
        // grounded in their streak/level/prayers, cached per day). iOS parity.
        personalWord = runCatching { Net.client.api.homeGreeting().greeting }.getOrNull()?.takeIf { it.isNotBlank() }
        verseReactions = runCatching { Net.client.api.verseReactions() }.getOrNull()
        featuredEvent = runCatching { Net.client.api.featuredEvent().data }.getOrNull()
        verse = runCatching { Net.client.api.homeVerse() }.getOrNull()
        streak = runCatching { Net.client.api.achievements() }.getOrNull()
        welcomeVideo = runCatching { Net.client.api.welcomeVideo() }.getOrNull()
        scores = runCatching { Net.client.api.scores() }.getOrNull()
        pathway = runCatching { Net.client.api.pathway() }.getOrNull()
        currentTrail = JourneyState.derive(pathway)?.levelNumber
            ?.let { n -> runCatching { Net.client.api.levelModules(n).data }.getOrNull() }
        letter = runCatching { Net.client.api.latestLetter().letter }.getOrNull()
        announcement = runCatching { Net.client.api.featuredAnnouncement().data }.getOrNull()
        cohort = runCatching { Net.client.api.cellSummary() }.getOrNull()
        plans = runCatching { Net.client.api.plans().data }.getOrNull() ?: plans
        // Home's own prayer-wall preview endpoint (iOS HomeView.prayerWallHome
        // parity) — distinct from the community/prayer-wall feed's sort query.
        prayers = runCatching { Net.client.api.prayerWallHome().data }.getOrDefault(emptyList())
        radio = runCatching { Net.client.api.radioNowPlaying() }.getOrNull()
        liveNow = runCatching { Net.client.api.getLiveNow().data }.getOrDefault(emptyList())
        org.nuruplace.member.feature.live.LiveDiscoveryCenter.ingest(liveNow)
        val today = LocalDate.now()
        val from = today.toString()
        val to = today.plusDays(45).toString()
        upcoming = runCatching { Net.client.api.calendar(from, to).data.sortedBy { it.startAt } }.getOrDefault(emptyList())
        giveRails = runCatching { Net.client.api.givingMethods() }.getOrNull() ?: giveRails
        // Curated Home rows — server-capped at 5, soonest-first; never re-sort/cap client-side.
        homeEvents = runCatching { Net.client.api.homeEvents().data }.getOrDefault(emptyList())
        partnership = partnershipRead.await() ?: partnership
        gifts = giftsRead.await() ?: gifts
        rsvps = rsvpsRead.await() ?: rsvps
        refreshing = false
        loadedOnce = true

        // Human moments — REAL server-truth milestones only; keys remember (once each).
        if ((rhythm?.doneCount ?: 0) >= 3) CelebrationCenter.fire(Moment("rhythm-$today", "Today's rhythm complete", "Prayer, Word and reflection — all before the day ended."))
        val days = streak?.streak?.current ?: 0
        if (days in setOf(3, 7, 14, 21, 30, 50, 100)) CelebrationCenter.fire(Moment("streak-$days", "$days-day rhythm streak!"))
        streak?.badges.orEmpty().filter { CelebrationCenter.seenOnce("seen-badges", it.code) }
            .forEach { CelebrationCenter.fire(Moment("badge-${it.code}", "${it.name} earned!", "Badges celebrate your growth — keep walking.")) }
    }

    // Defensive guard, on top of LiveDiscoveryCenter.ingest()'s own filter
    // below (this file's `liveNow` is fetched directly, not read from
    // LiveDiscoveryCenter.streams, so it needs its own exclusion too) — a
    // broadcaster must never see their OWN stream offered back to them as
    // "LIVE NOW · tap to watch" (2026-07-31 device report; see
    // LiveDiscoveryCenter.kt's header for the root cause and iOS parity).
    val churchLive = liveNow.firstOrNull {
        it.scope == "church" && it.streamId != org.nuruplace.member.feature.live.BroadcastController.activeSelfStreamId()
    }

    // Home-screen Radio/Live widgets (Glance) — Home is the first screen every
    // session lands on, so it's the earliest point a fresh snapshot can reach
    // the widget even if the member never opens Radio/Pathway this session.
    // LiveRadioScreen overwrites the radio half with richer data (listeners,
    // host, next program) once/if the member opens the player.
    LaunchedEffect(radio?.id, radio?.live, churchLive?.streamId) {
        org.nuruplace.member.widget.WidgetSnapshotStore.writeRadio(
            context = context,
            onAir = radio?.live == true,
            programTitle = radio?.title,
            host = radio?.speaker,
            listeners = radio?.peakListeners,
            nextProgramTitle = null,
        )
        org.nuruplace.member.widget.WidgetSnapshotStore.writeChurchLive(context, churchLive != null)
    }
    // An UNCONDITIONAL 60s re-check while Home is composed (this used to gate
    // on `churchLive != null` and only poll once a church stream was already
    // known live, but discovering a BRAND NEW stream is the whole point of
    // the mini-window pop-up, so it can't wait for one to already be known).
    // Every result is folded into the shared LiveDiscoveryCenter, which
    // decides whether to pop the mini-window (a stream_id this session
    // hasn't surfaced yet). Still a plain LaunchedEffect(Unit) — Compose
    // cancels it outright the moment Home leaves composition.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            liveNow = runCatching { Net.client.api.getLiveNow().data }.getOrDefault(liveNow)
            org.nuruplace.member.feature.live.LiveDiscoveryCenter.ingest(liveNow)
        }
    }

    val pendingSync by Net.client.offline.pending.collectAsState()
    val level = me?.enrollment?.currentLevel ?: 1
    val journey = remember(pathway, currentTrail) { JourneyState.derive(pathway, currentTrail) }
    // YOUR WEEK (§6.1): the five rows, in the journey's order.
    val week = remember(journey, plans, upcoming, homeEvents, rsvps, partnership, gifts, giveRails, cohort, me) {
        listOf(
            YourWeek.pathway(journey, me?.enrollment?.currentLevel),
            YourWeek.plans(plans),
            YourWeek.events(upcoming, homeEvents, rsvps, ZonedDateTime.now(EV_ZONE)),
            YourWeek.giving(partnership, gifts, giveRailsLine(giveRails), LocalDate.now(EV_ZONE)),
            YourWeek.cell(cohort),
        )
    }
    // A member already giving isn't asked twice: the banner only with "Give".
    val askToGive = week.any { it.form == WeekForm.GIVE }
    fun openWeek(dest: WeekDest) = when (dest) {
        is WeekDest.Screen -> onNavigate(dest.route)
        is WeekDest.Tab -> onSelectTab(dest.route)
        is WeekDest.Event -> onNavigate("event/${dest.occurrenceId}?end=${android.net.Uri.encode(dest.endAt.orEmpty())}")
    }
    val reflectionDue = rhythm?.reflection == false
    // Entrance choreography — the decision is captured once and the process
    // flag flips, so a later return to Home composes instantly.
    val entrance = remember { (!homeEntrancePlayed).also { homeEntrancePlayed = true } }

    Box(Modifier.fillMaxSize()) {
        NuruRefreshBox(refreshing = refreshing, onRefresh = { refreshing = true; refreshTick++ }) {
        Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
            HomeHeader(
                firstName = me?.profile?.fullName?.substringBefore(' ') ?: "friend",
                streak = streak?.streak?.current ?: 0,
                level = journey?.levelNumber ?: level,
                journeyPill = journey?.pill,
                growthScore = scores?.overall?.score ?: 0,
                trend = scores?.trend,
                personalWord = personalWord,
                onBell = onOpenNotifications,
                onScan = { onNavigate("service-checkin") },
                churchLive = churchLive,
                onLive = { churchLive?.let { onNavigate(liveNowRoute(it)) } },
            )

            Column(
                // 20dp between feed cards (iOS build-31 warmth) — the 16dp base read
                // congested with this many cards; each one gets room to breathe.
                Modifier.fillMaxWidth().padding(horizontal = Spacing.base).padding(top = Spacing.base),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Nuru Live (L3) — the broadcaster entry point, gold and gated
                // on the live:go RBAC grant, sitting right above the L2 LIVE
                // banner slot so a broadcaster sees both "go live" and
                // "who's live now" together.
                if (canGoLive(me)) {
                    Box(Modifier.fillMaxWidth()) { GoLiveButton(onClick = { showGoLiveSheet = true }) }
                }
                // Nuru Live — the very top of Home, above everything else,
                // whenever the church is live right now.
                churchLive?.let { row ->
                    LiveStreamBanner(
                        row = row,
                        onOpen = { onNavigate(liveNowRoute(row)) },
                        onReplays = { onNavigate("live-replays") },
                    )
                }
                if (pendingSync > 0) {
                    Text(
                        "⏳ $pendingSync change${if (pendingSync == 1) "" else "s"} waiting to sync",
                        style = NuruType.micro, color = Nuru.eyebrow,
                    )
                }
                // First paint with nothing loaded yet → hold the page's shape (no
                // spinner, no pop); cards stagger in once the wire answers.
                if (!loadedOnce && rhythm == null && verse == null && streak == null) {
                    HomeSkeleton()
                    // The Scaffold already insets the NavHost by the bottom bar,
                    // so only a breath of air is needed here, not tabBarSpace.
                    Spacer(Modifier.height(Spacing.base))
                    return@Column
                }
                radio?.takeIf { it.live }?.let { OnAirCard(it) { onNavigate("radio") } }
                // Owner's order (2026-08-25, stated exactly): verse for today →
                // featured video → the Sunday Letter → reflection due → the
                // liturgy. Everything else stays where it always was — the ONLY
                // move relative to the original feed is the liturgy stepping
                // BELOW the reflection strip (iOS HomeView parity).
                verse?.let { v ->
                    Entrance(entrance, 6) {
                        VerseCard(
                            v = v,
                            reactions = verseReactions,
                            saved = verseSaved,
                            onReact = { emoji ->
                                // Optimistic: reflect the tap at once (one per member/
                                // day — tapping my own removes it, a different one
                                // moves it), then reconcile; roll back on failure so a
                                // dropped request never blanks the counts (iOS parity).
                                val previous = verseReactions
                                val cur = verseReactions ?: VerseReactions()
                                val counts = cur.counts.toMutableMap()
                                fun drop(e: String) {
                                    val n = (counts[e] ?: 0) - 1
                                    if (n > 0) counts[e] = n else counts.remove(e)
                                }
                                val newMine: String?
                                if (cur.mine == emoji) {
                                    drop(emoji); newMine = null
                                } else {
                                    cur.mine?.let { drop(it) }
                                    counts[emoji] = (counts[emoji] ?: 0) + 1; newMine = emoji
                                }
                                verseReactions = VerseReactions(counts, newMine, counts.values.sum())
                                scope.launch {
                                    runCatching { Net.client.api.reactToVerse(VerseReactionBody(emoji)) }
                                        .onSuccess { verseReactions = it }
                                        .onFailure { verseReactions = previous }
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
                        FeaturedVideo(w, videoPlaying, onPlay = { videoPlaying = true })
                    }
                }
                // 0b · Live now — a worship-ish gathering happening right now or
                // starting within the hour (iOS HomeView.liveNowInfo/liveNowCard
                // parity). Driven by the same calendar occurrences as Upcoming;
                // no invented live-stream data, just a route to the real event.
                liveNowInfo(upcoming)?.let { info ->
                    Entrance(entrance, 1) {
                        LiveNowCard(info) { onNavigate("event/${info.occ.occurrenceId}?end=${android.net.Uri.encode(info.occ.endAt)}") }
                    }
                }
                // The Sunday Letter knock — three states (knock / quiet row / awaiting).
                // Awaiting opens the You tab: there is no letters-archive route
                // (GET /me/letters has no screen), and You is where "yours" lives.
                if (letter == null) Entrance(entrance, 2) { LetterAwaitingCard(onClick = { onSelectTab("profile") }) }
                letter?.takeIf { !it.isUnread }?.let { lt ->
                    LetterReadRow(lt) { showLetter = true }
                    if (showLetter) {
                        LetterDialog(lt, onDismiss = { showLetter = false }, onRead = {})
                    }
                }
                letter?.takeIf { it.isUnread }?.let { lt ->
                    LetterKnockCard(lt) { showLetter = true }
                    if (showLetter) {
                        LetterDialog(lt, onDismiss = { showLetter = false }, onRead = { letter = lt.copy(readAt = "read") })
                    }
                }
                // "What needs you today" — the server-ranked rail (GET /me/home/
                // nudges). If the endpoint fails or has nothing, the old single-
                // purpose reflection strip stands in, so Home never loses the
                // nudge that ticks the rhythm. The unread-letter nudge opens the
                // same letter sheet the knock card does, in place.
                // The exam's own nudge only while that exam can be taken (§7.2 #1).
                val shownNudges = nudges.filter { nudgeOffered(it, pathway) }
                if (shownNudges.isNotEmpty()) {
                    Entrance(entrance, 2) {
                        NeedsYouRail(shownNudges) { n ->
                            if (n.route == "letter" && letter != null) showLetter = true
                            else onNavigate(nudgeRouteFor(n))
                        }
                    }
                } else if (reflectionDue) {
                    // Reflection due — deep-links to the devotional's reflection
                    // composer, the one act that ticks the rhythm and clears this.
                    Entrance(entrance, 2) { ReflectionStrip { onNavigate("devotional") } }
                }
                // The hour's word — BELOW the reflection strip (owner's order).
                Entrance(entrance, 0) {
                    LiturgyCard(canManageRecordings = me?.profile?.role in setOf("Admin", "SuperAdmin"))
                }
                // 3 · YOUR WEEK — one card, each pillar's next thing (§6.1). It
                // took the place of the "For you today" hero, the continue-level
                // card, the plan-resume banner, the minis row, the this-week and
                // "Your cell" cards, and the upcoming list.
                Entrance(entrance, 3) { YourWeekCard(week) { openWeek(it) } }
                // 4 · The day — today's rhythm, then today's echo (the app
                // remembers you, Wave 1; nothing at all on a day without one).
                rhythm?.let { r -> Entrance(entrance, 4) { RhythmCard(r, streak?.streak?.current ?: 0) } }
                Entrance(entrance, 5) { HomeEchoCard() }
                if (rhythm != null) SelahDivider()   // — selah: a rest for the eye
                // 5 · The family. Both prayer-wall taps open My Prayer Room — the
                // wall preview on its Corporate tab, the post itself pushed
                // directly (deep-link parity).
                if (prayers.isNotEmpty()) Entrance(entrance, 7) { PrayerWallCard(prayers, onOpenWall = { onNavigate("prayer-room?tab=corporate") }, onOpenPost = { onNavigate("prayer-wall/${it}") }) }
                // 5b · Celebrate the family (moments, Phase 4).
                CelebrationsRail()
                FeaturedCarousel(
                    announcement = announcement,
                    featuredEvent = featuredEvent,
                    events = homeEvents,
                    onAll = { onNavigate("events-calendar") },
                    onOpenAnnouncement = { id -> onNavigate("announcement/$id") },
                    onOpenEvent = { id -> onNavigate("event/$id?end=") },
                )
                featuredEvent?.let { FeaturedGatheringCard(it) { onSelectTab("events") } }
                // 6 · Growing — the scores (with the journey's one-line step),
                // grow your faith, and the encouragement.
                scores?.let { ProgressCard(it, journey?.progressLine) { onNavigate("pathway") } }
                if (scores != null) SelahDivider()   // — selah: a rest before Grow
                GrowSection(onNavigate)
                EncouragementCard(prayers.size)
                // 7 · Support God's work — only while the Giving row says "Give".
                if (askToGive) GiveCard(railsLine = giveRailsLine(giveRails)) { onSelectTab("give") }
                // Scaffold already reserves the bottom bar; tabBarSpace here
                // double-counted it and left a hole under the Give card.
                Spacer(Modifier.height(Spacing.base))
            }
        }
        }

        // Nuru Live discovery — the mini-window pop-up: a MUTED autoplaying
        // preview docked above the tab bar (Scaffold already reserves that
        // space via its bottomBar content padding, so an aligned-bottom
        // overlay here floats right above it) for the first stream this
        // session hasn't seen yet. "Join live" opens the SAME full player the
        // banner's "Watch live" does (unmuted); ✕ collapses it to the
        // ordinary LIVE banner card above and never re-pops for this
        // stream_id again.
        val discoveryStreams by org.nuruplace.member.feature.live.LiveDiscoveryCenter.streams.collectAsState()
        val popupStreamId by org.nuruplace.member.feature.live.LiveDiscoveryCenter.popupStreamId.collectAsState()
        // Defensive guard on top of LiveDiscoveryCenter.ingest()'s own filter
        // — this is the exact site of the 2026-07-31 device report (Home's
        // "● LIVE test 2 [Join live]" mini-window offering the broadcaster
        // their own stream); belt-and-braces against `discoveryStreams` ever
        // carrying a self-stream row again, from here or a future caller.
        discoveryStreams.firstOrNull {
            it.streamId == popupStreamId && it.streamId != org.nuruplace.member.feature.live.BroadcastController.activeSelfStreamId()
        }?.let { popupStream ->
            org.nuruplace.member.feature.live.LiveMiniPopup(
                stream = popupStream,
                onJoin = {
                    org.nuruplace.member.feature.live.LiveDiscoveryCenter.markSeen(popupStream.streamId)
                    onNavigate(liveNowRoute(popupStream))
                },
                onDismiss = { org.nuruplace.member.feature.live.LiveDiscoveryCenter.dismissPopup(popupStream.streamId) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Spacing.base, vertical = Spacing.sm),
            )
        }
    }

    if (showGoLiveSheet) {
        GoLiveSetupSheet(
            me = me,
            lockedScope = null, // let the member pick between church/my cell
            onDismiss = { showGoLiveSheet = false },
            onStarted = { created, streamTitle, kind, _ ->
                showGoLiveSheet = false
                onNavigate(liveBroadcastRoute(created, streamTitle, kind))
            },
        )
    }
}

// ─────────────────────────── Header ───────────────────────────

@Composable
private fun HomeHeader(
    firstName: String,
    streak: Int,
    level: Int,
    /** The journey's pill (§3) — "12 of 20 modules", "Exam ready", … — null until it loads. */
    journeyPill: String?,
    /** The overall growth score, 0–100 — a score, never a percent. */
    growthScore: Int,
    trend: org.nuruplace.member.data.net.ScoreTrend? = null,
    personalWord: String? = null,
    onBell: () -> Unit,
    onScan: () -> Unit,
    churchLive: LiveNowRow? = null,
    onLive: () -> Unit = {},
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
            // Church check-in. First in the row because it is the most
            // time-critical thing a member does from this screen: they are
            // walking through the door and the QR is already on the wall.
            // Alibaba-style — one tap from the landing screen, no hunting
            // through tabs.
            ScanHeaderButton(onClick = onScan)
            Spacer(Modifier.width(Spacing.sm))
            // The one bell (EXPERIENCE.md §7.2 #4): the inbox, a dot only
            // while something is unread — in the scan button's gold circle.
            org.nuruplace.member.ui.components.InboxBell(
                onClick = onBell, size = 40.dp, shape = RoundedCornerShape(999.dp),
                container = Nuru.goldChipBg, border = Nuru.gold.copy(alpha = 0.35f),
                tint = Nuru.goldChipText, iconSize = 20.dp, dotInset = 6.dp,
            )
            Spacer(Modifier.width(Spacing.sm))
            // Nuru Live (L2) — a church stream is live right now. Same 40dp
            // circle language as the buttons either side of it, so the row reads
            // as one family; the pulsing red ring (not a static border) is what
            // says "this one is happening right now". Conditional, so the resting
            // header stays at three.
            if (churchLive != null) {
                LiveHeaderChip(onClick = onLive)
                Spacer(Modifier.width(Spacing.sm))
            }
            Box {
                // The growth score — "45", never "45%" (§3).
                ProgressRing(pct = growthScore, size = 42.dp, stroke = 4.dp, track = Nuru.successBg, arc = Nuru.gold) {
                    Text("$growthScore", style = NuruType.micro, color = Nuru.successText, fontWeight = FontWeight.Bold)
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
            // Owner (2026-08-26): the quoted head-card word steps down one point
            // — the hanging gold glyph and the line it opens. Sizes are written
            // out (not NuruType.title / NuruType.body) precisely because this
            // pair moves together and independently of those shared tokens.
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                Text("“", style = nuruSerif(21, FontWeight.Medium), color = Nuru.gold)
                Spacer(Modifier.width(6.dp))
                Text(
                    word,
                    style = nuruSans(13).copy(fontStyle = FontStyle.Italic, lineHeight = scaledLineHeight(19)),
                    color = Nuru.ink600,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // Level jewel capsule — the level, the journey's pill (§3), the streak
        // when there is one ("🔥 0-day" was no streak at all).
        Row(
            Modifier.clip(RoundedCornerShape(999.dp))
                .background(Nuru.white)
                .border(1.dp, Nuru.gold.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Level $level", style = NuruType.micro, color = Nuru.navy, fontWeight = FontWeight.SemiBold)
            journeyPill?.let { Text("  ·  $it", style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.SemiBold) }
            if (streak > 0) Text("  ·  🔥 $streak-day", style = NuruType.micro, color = Nuru.eyebrow)
        }
    }
}

/** Church check-in from the header — the same 40dp gold circle as the bell
 *  beside it (InboxBell), a real vector glyph because no emoji reads as "scan". Sits first in the row:
 *  a member using this is standing in the doorway with the QR already in front
 *  of them, so it must not cost a trip through the You tab to reach. */
@Composable
private fun ScanHeaderButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg)
            .border(1.dp, Nuru.gold.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .clickable(onClickLabel = "Scan to check in") { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.QrCodeScanner,
            contentDescription = "Scan to check in",
            tint = Nuru.goldChipText,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The header's LIVE entry point (owner ask: "re-imagine this part" of the
 *  bell/radio row) — the same 40dp circle as the bell, a dark navy fill
 *  (echoing [LiveStreamBanner]'s navy card) with a breathing red ring instead
 *  of a static border, so it visually says "live" before you even read it. */
@Composable
private fun LiveHeaderChip(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "liveHeaderPulse")
    val ringAlpha by t.animateFloat(
        initialValue = 0.9f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "liveHeaderRingAlpha",
    )
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.homeNavy)
            .border(2.dp, Nuru.liveRed.copy(alpha = ringAlpha), RoundedCornerShape(999.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text("📺", style = NuruType.body) }
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

// ─────────────────────────── Live now ───────────────────────────

/** A worship-ish calendar occurrence that is live right now, or that starts
 *  within the hour. [startsInMin] is null while live (iOS HomeView parity). */
private data class LiveNowInfo(val occ: CalendarOccurrence, val startsInMin: Int?)

private fun liveNowInfo(events: List<CalendarOccurrence>): LiveNowInfo? {
    val now = ZonedDateTime.now()
    for (occ in events) {
        if (!isWorshipish(occ)) continue
        val start = parseZdt(occ.startAt) ?: continue
        val end = parseZdt(occ.endAt) ?: start.plusHours(2)
        if (!start.isAfter(now) && !now.isAfter(end)) return LiveNowInfo(occ, null)
        val mins = java.time.Duration.between(now, start).toMinutes().toInt()
        if (mins in 1..60) return LiveNowInfo(occ, mins)
    }
    return null
}

private fun isWorshipish(occ: CalendarOccurrence): Boolean {
    val hay = "${occ.category.orEmpty()} ${occ.title}".lowercase()
    return listOf("worship", "service", "praise", "church").any { hay.contains(it) }
}

@Composable
private fun LiveNowCard(info: LiveNowInfo, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().pressScale().clip(shape).background(Nuru.homeNavyGradient)
            .border(1.dp, Nuru.liveRed.copy(alpha = 0.4f), shape).clickable { onOpen() }.padding(Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.homeNavyDark), contentAlignment = Alignment.Center) {
            if (info.occ.primaryImageUrl != null) {
                AsyncImage(model = info.occ.primaryImageUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                Text("⛪", style = NuruType.title)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (info.startsInMin == null) "● HAPPENING NOW" else "STARTING SOON · ${info.startsInMin}m",
                style = NuruType.kicker,
                color = if (info.startsInMin == null) Nuru.liveRed else Nuru.gold,
                fontWeight = FontWeight.Bold,
            )
            Text(info.occ.title, style = NuruType.featureTitle, color = Nuru.onNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            info.occ.location?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = NuruType.caption, color = Nuru.onNavyDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", style = NuruType.title, color = Nuru.gold)
    }
}

@Composable
private fun ReflectionStrip(onClick: () -> Unit) {
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
            Text("Write today's devotional reflection", style = NuruType.micro, color = Nuru.faintGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.homeNavy).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text("Start reflection", style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** YOUR WEEK (EXPERIENCE.md §6.1): one card, five rows in the journey's
 *  order — an icon, the next thing, one line of when or where it stands, a
 *  chevron; a tap opens that place. The rows are YourWeek's. */
@Composable
private fun YourWeekCard(rows: List<WeekRow>, onOpen: (WeekDest) -> Unit) {
    HomeCard(pad = 0.dp) {
        Box(Modifier.padding(start = Spacing.base, end = Spacing.base, top = Spacing.base, bottom = Spacing.xs)) {
            CardKicker("Your week")
        }
        rows.forEachIndexed { i, row ->
            // Inset to the text, as the Partners rows draw it.
            if (i > 0) Box(Modifier.padding(start = 68.dp, end = Spacing.base).fillMaxWidth().height(1.dp).background(Nuru.border))
            WeekRowView(row) { onOpen(row.dest) }
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}

@Composable
private fun WeekRowView(row: WeekRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = Spacing.base, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Nuru.goldChipBg), contentAlignment = Alignment.Center) {
            Icon(
                when (row.form.pillar) {
                    WeekPillar.PATHWAY -> Icons.AutoMirrored.Filled.MenuBook
                    WeekPillar.PLANS -> Icons.Filled.Bookmark
                    WeekPillar.EVENTS -> Icons.Filled.Event
                    WeekPillar.GIVING -> Icons.Filled.VolunteerActivism
                    WeekPillar.CELL -> Icons.Filled.Groups
                },
                contentDescription = null, tint = Nuru.goldChipText, modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = NuruType.rowTitle, color = Nuru.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.line.isNotBlank()) {
                Text(row.line, style = NuruType.caption, color = Nuru.ink600, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Text("›", style = NuruType.title, color = Nuru.ink300)
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

// Featured welcome video — it plays IN PLACE, inside this card's inset 16:9
// box, for every source. See ui/components/VideoPlayer.kt for the browser/
// download bug this replaced, and ui/components/VideoPoster.kt for the poster
// frame we cut ourselves when the server sends no thumbnail_url.
@Composable
private fun FeaturedVideo(v: WelcomeVideo, playing: Boolean, onPlay: (String) -> Unit) {
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
        if (playing && playable != null) {
            // Direct/cloudinary/HLS → ExoPlayer; youtube/vimeo → provider embed.
            // Either way it renders inside this box, with its own gold buffering
            // cue — no Intent, no browser, no download.
            InlineVideoPlayer(
                url = playable,
                source = v.videoSource,
                externalVideoId = v.externalVideoId,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)),
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
                    // iOS videoThumb's neutral bed (#D6DADE) — a shade darker than
                    // the card so the gold disc still reads while the poster loads.
                    .background(Color(0xFFD6DADE))
                    .clickable { if (playable != null) onPlay(playable) },
                contentAlignment = Alignment.Center,
            ) {
                if (v.thumbnailUrl.isNullOrBlank()) {
                    // No server thumbnail (uploaded videos carry none — no ffmpeg
                    // on the API host): cut a poster frame from the video itself,
                    // once, and keep it for the session.
                    if (!v.needsWebEmbed) {
                        VideoPosterFrame(playable, Modifier.fillMaxSize(), contentDescription = v.caption)
                    }
                } else {
                    AsyncImage(
                        model = v.thumbnailUrl,
                        contentDescription = v.caption,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }
                // Gold play disc + duration pill ride on top of whichever poster won.
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.gold), contentAlignment = Alignment.Center) { Text("▶", color = Nuru.homeNavy, style = NuruType.title) }
                v.durationSec?.takeIf { it > 0 }?.let { d ->
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp)).background(Nuru.homeNavy.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text("%d:%02d".format(d / 60, d % 60), style = NuruType.micro, color = Nuru.white, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // Caption is the heading; the sub-line only earns its place when it adds
        // information (an uncaptioned video used to print the same line twice).
        // The app's own SERIF title face two points down from featureTitle
        // (owner, 2026-08-26; iOS moved inter(18,semibold) → fraunces(16,semibold)):
        // this card was the one sans headline among serif card titles, so it read
        // as a foreign (portal) font.
        Text(
            v.caption ?: "Start here — what the journey looks like",
            style = NuruType.videoCaption.copy(lineHeight = 20.sp),
            color = Nuru.ink,
            modifier = Modifier.fillMaxWidth(),
        )
        if (v.caption != null && v.caption != "Start here — what the journey looks like") {
            Spacer(Modifier.height(7.dp))
            Text(
                "Start here — what the journey looks like",
                style = NuruType.caption, color = Nuru.ink600,
                modifier = Modifier.fillMaxWidth(),
            )
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
        // Seven-bands: an `encouragement` quote from the server replaces the
        // "Chosen for your season" ribbon when present; absent (older backend
        // or no encouragement chosen today) falls back to the ribbon unchanged.
        val encouragement = v.encouragement
        if (encouragement != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "“${encouragement.text}”",
                style = NuruType.rowTitle.copy(fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal),
                color = Nuru.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "— ${encouragement.author}",
                style = NuruType.micro,
                color = Nuru.gold, fontWeight = FontWeight.SemiBold,
            )
        } else {
            v.reason?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.sm))
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("✦ Chosen for your season — $it", style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold)
                }
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

/** "Pray for one another" — a small carousel of Home's own prayer-wall preview
 *  posts (iOS HomeView.prayerWallCard parity): a single post hugs its content,
 *  multiple posts page through a HorizontalPager with gold dots below (not the
 *  system indicator — invisible on cream, per the iOS comment this mirrors). */
@Composable
private fun PrayerWallCard(posts: List<PrayerWallPost>, onOpenWall: () -> Unit, onOpenPost: (String) -> Unit) {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardKicker("Pray for one another")
            Spacer(Modifier.weight(1f))
            RowScopeLink("Open wall ›", onOpenWall)
        }
        Spacer(Modifier.height(Spacing.md))
        if (posts.size == 1) {
            PrayerPostRow(posts[0], modifier = Modifier.clickable { onOpenPost(posts[0].postId) })
        } else {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { posts.size })
            androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { i ->
                val post = posts[i]
                PrayerPostRow(post, modifier = Modifier.clickable { onOpenPost(post.postId) })
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(posts.size) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        Modifier.padding(2.dp).height(6.dp).width(if (active) 16.dp else 6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Nuru.gold else Nuru.gold.copy(alpha = 0.22f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrayerPostRow(post: PrayerWallPost, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.authorAvatar, 32.dp)
            Spacer(Modifier.width(Spacing.sm))
            Text(post.authorName, style = NuruType.cardCta, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
        }
        post.title?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(it, style = NuruType.rowTitle, color = Nuru.ink)
        }
        Spacer(Modifier.height(6.dp))
        Text(post.body, style = NuruType.caption, color = Nuru.ink600, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.sm))
        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(
                "🤲 ${post.prayCount} praying" + (post.commentCount?.let { " · $it replies" } ?: ""),
                style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// Featured carousel (owner's revision, 2026-08-24 — iOS parity): one sliding
// rail for everything the portal has marked or scheduled — the featured
// announcement, the featured gathering, and the next few events. Auto-advances
// gently; a swipe is always respected. "View all" opens the full events list.
private sealed interface FeaturedPage {
    data class Ann(val a: FeaturedAnnouncement) : FeaturedPage
    data class Fev(val e: FeaturedEvent) : FeaturedPage
    data class Occ(val o: HomeEventRow) : FeaturedPage
}

@Composable
private fun FeaturedCarousel(
    announcement: FeaturedAnnouncement?,
    featuredEvent: FeaturedEvent?,
    events: List<HomeEventRow>,
    onAll: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val pages = remember(announcement, featuredEvent, events) {
        buildList {
            announcement?.let { add(FeaturedPage.Ann(it)) }
            featuredEvent?.let { add(FeaturedPage.Fev(it)) }
            events.filter { it.seriesId != featuredEvent?.seriesId }.take(3).forEach { add(FeaturedPage.Occ(it)) }
        }
    }
    if (pages.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs)) {
            SectionLabel("Featured")
            Spacer(Modifier.weight(1f))
            RowScopeLink("View all", onAll)
        }
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pages.size })
        // Gentle auto-advance every 6s; pauses whenever a finger is on the rail.
        LaunchedEffect(pages.size) {
            if (pages.size < 2) return@LaunchedEffect
            while (true) {
                kotlinx.coroutines.delay(6_000)
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage((pagerState.currentPage + 1) % pages.size)
                }
            }
        }
        androidx.compose.foundation.pager.HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { i ->
            when (val page = pages[i]) {
                is FeaturedPage.Ann -> FeaturedPageCard(
                    kicker = "ANNOUNCEMENT", imageUrl = page.a.primaryImageUrl,
                    title = page.a.title, body = page.a.body,
                    meta = page.a.sentAt?.let { fmtDate(it) }, cta = "Read more ›",
                ) { onOpenAnnouncement(page.a.announcementId) }
                is FeaturedPage.Fev -> FeaturedPageCard(
                    kicker = "FEATURED GATHERING", imageUrl = page.e.primaryImageUrl,
                    title = page.e.title, body = page.e.description ?: page.e.location.orEmpty(),
                    meta = page.e.dtstartLocal.takeIf { it.isNotBlank() }, cta = "See details ›",
                ) { onAll() }
                is FeaturedPage.Occ -> FeaturedPageCard(
                    kicker = "UPCOMING EVENT", imageUrl = page.o.primaryImageUrl,
                    title = page.o.title, body = page.o.venue.orEmpty(),
                    meta = fmtDate(page.o.startsAt), cta = "See details ›",
                ) { onOpenEvent(page.o.occurrenceId) }
            }
        }
        if (pages.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { i ->
                    val on = i == pagerState.currentPage
                    Box(
                        Modifier.padding(horizontal = 2.5.dp)
                            .size(width = if (on) 16.dp else 5.dp, height = 5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) Nuru.gold else Nuru.gold.copy(alpha = 0.25f)),
                    )
                }
            }
        }
    }
}

/// One shared page frame so every slide sits at the same height — image on top
/// (16:9, navy-gradient fallback) with a kicker chip, then title, two body
/// lines, and a footer.
@Composable
private fun FeaturedPageCard(
    kicker: String,
    imageUrl: String?,
    title: String,
    body: String,
    meta: String?,
    cta: String,
    onOpen: () -> Unit,
) {
    HomeCard(modifier = Modifier.clickable { onOpen() }, pad = 0.dp) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(Color(0xFF16273F), Color(0xFF0A1C33)),
                    ),
                ),
        ) {
            if (!imageUrl.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = imageUrl, contentDescription = title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Text(
                kicker, style = NuruType.micro.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp),
                color = Color.White,
                modifier = Modifier.padding(10.dp)
                    .clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Column(Modifier.padding(Spacing.base)) {
            Text(title, style = NuruType.featureTitle, color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.xs))
            Text(body, style = NuruType.caption, color = Nuru.ink600, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meta.orEmpty(), style = NuruType.micro, color = Nuru.ink400)
                Spacer(Modifier.weight(1f))
                Text(cta, style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProgressCard(s: ScoresSummary, journeyLine: JourneyLine?, onView: () -> Unit) {
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
        val bars = listOf(
            ScoreLine("Habits", s.habits.score, Nuru.gold, d?.get("habits")),
            ScoreLine("Word", s.word.score, Nuru.scoreWord, d?.get("word")),
            ScoreLine("Prayer", s.prayer.score, Nuru.scorePrayer, d?.get("prayer")),
            ScoreLine("Curriculum", s.curriculum.score, Nuru.homeNavy, d?.get("curriculum")),
            ScoreLine("Attendance", s.attendance.score, Nuru.success, d?.get("attendance")),
        )
        // The figures' columns (EXPERIENCE.md §6.6) are as wide as their
        // widest figure at the member's text size — never narrower than they
        // were — and the bar takes what is left (FairSplitRow's rule: each
        // side gets what it needs, the flexible one yields). A fixed 28dp
        // column broke "▲100" into "▲10" / "0"; every row's bar still lines up.
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        fun widest(figures: List<String>, style: TextStyle, floor: Dp): Dp = with(density) {
            figures.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width.toDp() }
                ?.let { maxOf(it, floor) } ?: floor
        }
        val deltaWidth = widest(bars.mapNotNull { scoreDelta(it.delta) }, SCORE_DELTA_STYLE, 28.dp)
        val valueWidth = widest(bars.map { "${it.value}" }, SCORE_VALUE_STYLE, 32.dp)
        bars.forEach { ScoreBar(it, deltaWidth, valueWidth) }
        // The journey's next step in one line (§3) — "3 of 10 modules in
        // Level 2", "Take the Level 1 exam" — never "0 modules left".
        journeyLine?.let { line ->
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nuru.surface).padding(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Nuru.goldChipBg), contentAlignment = Alignment.Center) {
                    Text("◎", style = NuruType.body, color = Nuru.goldChipText)
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Nuru.ink)) { append(line.bold) }
                        withStyle(SpanStyle(color = Nuru.ink600)) { append(line.rest) }
                    },
                    style = NuruType.caption,
                )
            }
        }
    }
}

/** One discipline's score, its colour and its movement vs the previous 28 days. */
private data class ScoreLine(val label: String, val value: Int, val color: Color, val delta: Int?)

/** "▲6" · "▼4" — a discipline's movement; null when it held steady. */
private fun scoreDelta(delta: Int?): String? =
    delta?.takeIf { it != 0 }?.let { (if (it < 0) "▼" else "▲") + kotlin.math.abs(it) }

private val SCORE_DELTA_STYLE: TextStyle get() = NuruType.micro.copy(fontWeight = FontWeight.Bold)
private val SCORE_VALUE_STYLE: TextStyle get() = NuruType.caption.copy(fontWeight = FontWeight.SemiBold)

/** A score row: the label, the bar (it yields), the movement and the score —
 *  the two figures one line each, in the columns ProgressCard measured. */
@Composable
private fun ScoreBar(line: ScoreLine, deltaWidth: Dp, valueWidth: Dp) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(line.label, style = NuruType.caption, color = Nuru.ink600, modifier = Modifier.width(84.dp))
        Box(Modifier.weight(1f)) { ProgressBar(line.value, line.color, height = 8.dp) }
        // A whisper of movement vs the previous 28 days, next to the score.
        val delta = scoreDelta(line.delta)
        if (delta != null) {
            Text(
                delta,
                style = SCORE_DELTA_STYLE,
                color = if ((line.delta ?: 0) < 0) Color(0xFFDC6B26) else Color(0xFF16A34A),
                maxLines = 1, softWrap = false,
                modifier = Modifier.width(deltaWidth), textAlign = TextAlign.End,
            )
        } else {
            Spacer(Modifier.width(deltaWidth))
        }
        Text(
            "${line.value}", style = SCORE_VALUE_STYLE, color = Nuru.ink,
            maxLines = 1, softWrap = false,
            modifier = Modifier.width(valueWidth), textAlign = TextAlign.End,
        )
    }
}

/** Grow your faith (EXPERIENCE.md §6.1): devotional, memory verses, prayer
 *  room, your calling — and your discipler, the row iOS's grow card has.
 *  The reading plan lives in YOUR WEEK (and the Plans tab); the discipler
 *  opens Mentor, as the old disciplers card did. */
@Composable
private fun GrowSection(onNavigate: (String) -> Unit) {
    Column {
        SectionLabel("Grow your faith")
        HomeCard(pad = Spacing.md) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GrowTile("Devotional", "Today's devotional", "🌞", Nuru.goldChipBg, Nuru.eyebrow, Modifier.weight(1f)) { onNavigate("devotional") }
                GrowTile("Hide His Word", "Memorize Scripture", "❝", Nuru.warningBg, Nuru.hideWordFg, Modifier.weight(1f)) { onNavigate("memory-verses") }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GrowTile("My Prayer Room", "Pray with the family", "🤲", Nuru.dangerBg, Nuru.danger, Modifier.weight(1f)) { onNavigate("prayer-room?tab=corporate") }
                GrowTile("Your Calling", "Discover your gifts", "✨", Nuru.callingBg, Nuru.callingFg, Modifier.weight(1f)) { onNavigate("gifts") }
            }
            Spacer(Modifier.height(Spacing.sm))
            DisciplerRow { onNavigate("mentor") }
        }
    }
}

/** "YOUR DISCIPLER · Meet your discipler" (iOS growCard's row) → Mentor. */
@Composable
private fun DisciplerRow(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Nuru.verseBg)
            .border(1.dp, Nuru.gold.copy(alpha = 0.2f), shape).clickable { onClick() }.padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.goldGradient))
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            CardKicker("Your discipler")
            Text("Meet your discipler", style = NuruType.cardCta, color = Nuru.navy, fontWeight = FontWeight.SemiBold)
        }
        Text("›", style = NuruType.title, color = Nuru.ink300)
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

/** [railsLine]: "Tithe & offering · M-Pesa" — only the rails that can take a
 *  gift here (giveRailsLine); it used to promise "M-Pesa, card and more". */
@Composable
private fun GiveCard(railsLine: String, onGive: () -> Unit) {
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
                Text(railsLine, style = NuruType.micro, color = Nuru.onNavyFaint)
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

/** Map a Home nudge (GET /me/home/nudges) to an in-app destination. Keyed on
 *  `route` with the `kind` as a fallback spelling; every branch has a landing
 *  so a row missing its param still goes somewhere sensible, never to a route
 *  the NavHost cannot match. */
private fun nudgeRouteFor(n: HomeNudge): String = when (n.route.ifBlank { n.kind }) {
    "devotional", "reflection_due" -> "devotional"
    "quiz", "quiz_in_progress" -> n.moduleId?.let { "quiz/$it" } ?: "pathway"
    "level_exam", "level_review" -> n.levelNumber?.let { "exam/$it" } ?: "pathway"
    "letter", "letter_unread" -> "profile"   // no letters-archive route yet; caller opens the sheet when the letter is loaded
    "cell", "cell_gathering" -> "cell-info"
    "plan", "plan_day_due" -> n.planId?.let { "plan/$it" } ?: "plans"
    "reading_invite" -> n.token?.let { "reading/join/$it" } ?: "read-with-friend"
    "chat", "chat_unread" -> n.conversationId?.let { "chat/$it" } ?: "chat"
    // Partners programme (docs/PARTNERS_PROGRAMME.md §3): a pledge due/overdue
    // nudge opens the Give tab on Partners, where Pay now lives.
    "partners", "pledge", "pledge_due", "pledge_due_soon", "pledge_overdue", "pledge_fulfilled" -> "partners"
    else -> "pathway"
}

/** Whether Home shows a nudge. The exam's own nudge ("Level 1 review is open"
 *  → the exam) only while the server says that exam can be taken: a level
 *  whose `exam_available` is false would only refuse it — the journey says
 *  "Exam opens soon" instead (EXPERIENCE.md §7.2 #1). Without the pathway
 *  read the nudge stands: the server sent it. Every other nudge, always. */
internal fun nudgeOffered(n: HomeNudge, pathway: PathwaySummary?): Boolean {
    if (n.route.ifBlank { n.kind } !in setOf("level_exam", "level_review")) return true
    val level = n.levelNumber ?: return true
    return pathway?.levels?.firstOrNull { it.levelNumber == level }?.examAvailable != false
}

private fun parseZdt(s: String?): ZonedDateTime? {
    if (s.isNullOrBlank()) return null
    return runCatching { java.time.OffsetDateTime.parse(s).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()
        ?: runCatching { java.time.Instant.parse(s).atZone(ZoneId.systemDefault()) }.getOrNull()
}

private fun fmtDate(s: String?): String =
    parseZdt(s)?.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())) ?: ""
