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
import org.nuruplace.member.data.AppPrefs
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
import org.nuruplace.member.feature.pathway.JourneyState
import org.nuruplace.member.ui.components.HomeSkeleton
import org.nuruplace.member.ui.components.InlineVideoPlayer
import org.nuruplace.member.ui.components.VideoPosterFrame
import org.nuruplace.member.ui.components.VideoShape
import org.nuruplace.member.ui.components.CelebrationCenter
import org.nuruplace.member.ui.components.LiveStreamBanner
import org.nuruplace.member.ui.components.Moment
import org.nuruplace.member.ui.components.NuruRefreshBox
import org.nuruplace.member.ui.components.pressScale
import org.nuruplace.member.ui.components.rememberHeld
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
import java.util.Locale
import org.nuruplace.member.ui.icons.Lucide

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
    // Home's server data is HELD by the "home" destination (rememberHeld),
    // not the composition: a full-screen route pushed over Home — the exam, a
    // module, an event, a pledge — no longer throws it away, so Back returns
    // to the same page at the same scroll and refreshes it in place: no
    // skeleton, no score of "0" (EXPERIENCE.md §7.2 #8). Transient UI state
    // (a sheet open, the video playing) still starts fresh.
    var rhythm by rememberHeld("Home.rhythm") { mutableStateOf<RhythmToday?>(null) }
    var verse by rememberHeld("Home.verse") { mutableStateOf<TailoredVerse?>(null) }
    var streak by rememberHeld("Home.streak") { mutableStateOf<Achievements?>(null) }
    var welcomeVideo by rememberHeld("Home.welcomeVideo") { mutableStateOf<WelcomeVideo?>(null) }
    var announcement by rememberHeld("Home.announcement") { mutableStateOf<FeaturedAnnouncement?>(null) }
    var scores by rememberHeld("Home.scores") { mutableStateOf<ScoresSummary?>(null) }
    var upcoming by rememberHeld("Home.upcoming") { mutableStateOf<List<CalendarOccurrence>>(emptyList()) }
    // Whether the church calendar has ever answered — until it has, YOUR
    // WEEK's Events row says it didn't load, never "No gatherings this week"
    // (EXPERIENCE.md §9.7 M4).
    var upcomingAnswered by rememberHeld("Home.upcomingAnswered") { mutableStateOf(false) }
    var homeEvents by rememberHeld("Home.homeEvents") { mutableStateOf<List<HomeEventRow>>(emptyList()) }
    var cohort by rememberHeld("Home.cohort") { mutableStateOf<CellSummary?>(null) }
    // A member with no cell who asked to be connected — when (§9.2 #12).
    var cellAskedAt by rememberHeld("Home.cellAskedAt") { mutableStateOf<String?>(null) }
    // YOUR WEEK's own reads (EXPERIENCE.md §6.1): the plans, the member's
    // RSVPs, the partnership (its DUE rows and pledges) and the recurring
    // gifts. Null = never answered — that row says its "none" form; a failed
    // refresh keeps the last answer.
    var plans by rememberHeld("Home.plans") { mutableStateOf<List<ReadingPlanRow>?>(null) }
    var rsvps by rememberHeld("Home.rsvps") { mutableStateOf<List<MyRsvp>?>(null) }
    var partnership by rememberHeld("Home.partnership") { mutableStateOf<Partnership?>(null) }
    var gifts by rememberHeld("Home.gifts") { mutableStateOf<List<GivingSchedule>?>(null) }
    var prayers by rememberHeld("Home.prayers") { mutableStateOf<List<PrayerWallPost>>(emptyList()) }
    // The member's discipler (GET /growth/mentor) — null until the server
    // answers; with none, Home says "No discipler yet — your leader will pair
    // you" once (§9.2 #8). With one, their photo (or initials) and name.
    var discipler by rememberHeld("Home.discipler") { mutableStateOf<HomeDiscipler?>(null) }
    var radio by rememberHeld("Home.radio") { mutableStateOf<RadioProgram?>(null) }
    var videoPlaying by remember { mutableStateOf(false) }
    var personalWord by rememberHeld("Home.personalWord") { mutableStateOf<String?>(null) }
    var verseReactions by rememberHeld("Home.verseReactions") { mutableStateOf<VerseReactions?>(null) }
    var verseSaved by rememberHeld("Home.verseSaved") { mutableStateOf(false) }
    // A save in flight — one tap, one saved verse (each tap minted a new id).
    var verseSaving by remember { mutableStateOf(false) }
    val homeView = androidx.compose.ui.platform.LocalView.current
    var featuredEvent by rememberHeld("Home.featuredEvent") { mutableStateOf<FeaturedEvent?>(null) }
    // The member's journey (docs/EXPERIENCE.md §3): the pathway summary and
    // the current level's trail — one truth for the pill, the continue card
    // and the progress line, in the words the Pathway hub uses.
    var pathway by rememberHeld("Home.pathway") { mutableStateOf<PathwaySummary?>(null) }
    var currentTrail by rememberHeld("Home.currentTrail") { mutableStateOf<List<LevelModule>?>(null) }
    // The rails GET /giving/methods says can take a gift — the giving card
    // names only those (null = no answer yet: no rail named). A failed
    // refresh keeps the last answer.
    var giveRails by rememberHeld("Home.giveRails") { mutableStateOf<org.nuruplace.member.data.net.GivingMethodsRes?>(null) }
    var letter by rememberHeld("Home.letter") { mutableStateOf<org.nuruplace.member.data.net.PastoralLetter?>(null) }
    var showLetter by remember { mutableStateOf(false) }
    // "What needs you today" (GET /me/home/nudges) — empty = nothing waiting OR
    // the endpoint is unreachable; either way the old reflection strip stands in.
    var nudges by rememberHeld("Home.nudges") { mutableStateOf<List<HomeNudge>>(emptyList()) }
    // Nuru Live (L2, viewer-only) — GET /live/now returns church streams
    // always plus cell streams scoped to the caller's own cell; Home only
    // ever renders the church-scope one (CellInfoScreen renders the cell one
    // off this SAME shape from its own fetch).
    var liveNow by rememberHeld("Home.liveNow") { mutableStateOf<List<LiveNowRow>>(emptyList()) }
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

    // One tick per full load — pull-to-refresh bumps it to re-run the batch,
    // and so does every return to Home (the tick starts again at 0 while the
    // data is held). Each read refreshes its part IN PLACE: a part that fails
    // keeps what is on screen — never blanked, never back to "0". `loadedOnce`
    // keeps the skeleton from ever returning after first paint.
    var refreshTick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var loadedOnce by rememberHeld("Home.loadedOnce") { mutableStateOf(false) }
    // Nothing Home is about could be read — the journey, the day's rhythm, the
    // verse, the plans: §4's state card says what happened, in place of a page
    // of guesses ("No gatherings this week", "Find your cell" for a member in
    // one) and a shimmer that never ends (EXPERIENCE.md §9.4).
    var homeFailure by remember { mutableStateOf<org.nuruplace.member.data.net.StateMessage?>(null) }
    LaunchedEffect(refreshTick) {
        // YOUR WEEK's giving and RSVPs, side by side with everything below —
        // they add no wait to the page; the card is drawn once all answer.
        val partnershipRead = async { runCatching { Net.client.api.partnership() }.getOrNull() }
        val giftsRead = async { runCatching { Net.client.api.schedules().data }.getOrNull() }
        val rsvpsRead = async { runCatching { Net.client.api.myRsvps().data }.getOrNull() }
        // The bell's one count, beside them — pulled down, it asks again too.
        launch { org.nuruplace.member.ui.components.InboxUnread.refresh() }
        rhythm = runCatching { Net.client.api.rhythmToday() }.getOrElse { rhythm }
        nudges = runCatching { Net.client.api.nudges().nudges }.getOrElse { nudges }
        // Nuru's daily word — a blessing written for THIS member (server-side,
        // grounded in their streak/level/prayers, cached per day). iOS parity.
        personalWord = runCatching { Net.client.api.homeGreeting().greeting.takeIf { it.isNotBlank() } }.getOrElse { personalWord }
        verseReactions = runCatching { Net.client.api.verseReactions() }.getOrElse { verseReactions }
        // Only a gathering still ahead (Cycle 3's closing walk, B1): a series
        // that ended on 6 Sep was featured as "Sun, Aug 30 · 2:00 PM".
        featuredEvent = runCatching { Net.client.api.featuredEvent().data?.takeIf { featuredIsUpcoming(it) } }.getOrElse { featuredEvent }
        verse = runCatching { Net.client.api.homeVerse() }.getOrElse { verse }
        streak = runCatching { Net.client.api.achievements() }.getOrElse { streak }
        welcomeVideo = runCatching { Net.client.api.welcomeVideo() }.getOrElse { welcomeVideo }
        scores = runCatching { Net.client.api.scores() }.getOrElse { scores }
        val pathwayRead = runCatching { Net.client.api.pathway() }
        pathway = pathwayRead.getOrElse { pathway }
        currentTrail = JourneyState.derive(pathway)?.levelNumber
            ?.let { n -> runCatching { Net.client.api.levelModules(n).data }.getOrElse { currentTrail } }
        letter = runCatching { Net.client.api.latestLetter().letter }.getOrElse { letter }
        announcement = runCatching { Net.client.api.featuredAnnouncement().data }.getOrElse { announcement }
        cohort = runCatching { Net.client.api.cellSummary() }.getOrElse { cohort }
        if (cohort?.cell == null) {
            cellAskedAt = runCatching { Net.client.api.cellConnection().request?.requestedAt }.getOrElse { cellAskedAt }
        }
        plans = runCatching { Net.client.api.plans().data }.getOrNull() ?: plans
        // Home's own prayer-wall preview endpoint (iOS HomeView.prayerWallHome
        // parity) — distinct from the community/prayer-wall feed's sort query.
        prayers = runCatching { Net.client.api.prayerWallHome().data }.getOrElse { prayers }
        discipler = runCatching { HomeDiscipler(Net.client.api.mentor().mentor) }.getOrElse { discipler }
        radio = runCatching { Net.client.api.radioNowPlaying() }.getOrElse { radio }
        runCatching { Net.client.api.getLiveNow().data }.onSuccess { rows ->
            liveNow = rows
            org.nuruplace.member.feature.live.LiveDiscoveryCenter.ingest(rows)
        }
        val today = LocalDate.now()
        val from = today.toString()
        val to = today.plusDays(45).toString()
        runCatching { Net.client.api.calendar(from, to).data.sortedBy { it.startAt } }
            .onSuccess { upcoming = it; upcomingAnswered = true }
        giveRails = runCatching { Net.client.api.givingMethods() }.getOrNull() ?: giveRails
        // Curated Home rows — server-capped at 5, soonest-first; never re-sort/cap client-side.
        homeEvents = runCatching { Net.client.api.homeEvents().data }.getOrElse { homeEvents }
        partnership = partnershipRead.await() ?: partnership
        gifts = giftsRead.await() ?: gifts
        rsvps = rsvpsRead.await() ?: rsvps
        homeFailure = pathwayRead.exceptionOrNull()
            ?.takeIf { pathway == null && rhythm == null && verse == null && plans == null }
            ?.let { org.nuruplace.member.data.net.ApiException.state(it, context) }
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
    // "Turn on notifications" — Home's one card while the phone has them off.
    val notificationsCard = org.nuruplace.member.data.firebase.rememberNotificationsCard()
    // Unknown until the member is (Cycle 3's closing walk, B11): never a
    // "Level 1" or a "friend" that may not be true — a Level 3 member read
    // "Level 1" until /me landed.
    val level = me?.enrollment?.currentLevel
    val journey = remember(pathway, currentTrail) { JourneyState.derive(pathway, currentTrail) }
    // YOUR WEEK (§6.1): the five rows, in the journey's order.
    val week = remember(journey, plans, upcoming, upcomingAnswered, homeEvents, rsvps, partnership, gifts, giveRails, cohort, cellAskedAt, me) {
        listOf(
            YourWeek.pathway(journey, me?.enrollment?.currentLevel),
            YourWeek.plans(plans, sealedHere = org.nuruplace.member.feature.grow.PlanDayLog.sealedToday()),
            YourWeek.events(upcoming.takeIf { upcomingAnswered }, homeEvents, rsvps, ZonedDateTime.now(EV_ZONE)),
            YourWeek.giving(partnership, gifts, giveRailsLine(giveRails), LocalDate.now(EV_ZONE)),
            YourWeek.cell(cohort, cellAskedAt),
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
                firstName = me?.profile?.fullName?.substringBefore(' ')?.takeIf { it.isNotBlank() },
                level = journey?.levelNumber ?: level,
                levelUnknown = homeFailure != null,
                journeyPill = journey?.pill,
                // Unknown until the scores answer — never a "0" that isn't true.
                growthScore = scores?.overall?.score,
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
                homeFailure?.let { failed ->
                    org.nuruplace.member.ui.components.FailedState(failed, onRetry = { refreshing = true; refreshTick++ })
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
                            // "Saved" and the success haptic on the server's
                            // ack; a failure is felt and the button stays
                            // "Save" (EXPERIENCE.md §7.4: no success before the
                            // server; iOS the same). It dropped failures silently.
                            onSave = {
                                if (!verseSaved && !verseSaving) {
                                    verseSaving = true
                                    scope.launch {
                                        val r = runCatching {
                                            Net.client.api.saveVerse(
                                                VerseUpsertBody(
                                                    savedVerseId = java.util.UUID.randomUUID().toString(),
                                                    reference = v.reference,
                                                    version = v.version,
                                                    verseText = v.text,
                                                    clientMutationId = java.util.UUID.randomUUID().toString(),
                                                ),
                                            )
                                        }
                                        r.exceptionOrNull()?.let { if (it is kotlin.coroutines.cancellation.CancellationException) throw it }
                                        verseSaving = false
                                        if (r.isSuccess) {
                                            verseSaved = true
                                            org.nuruplace.member.ui.components.Haptics.confirm(homeView)
                                        } else {
                                            org.nuruplace.member.ui.components.Haptics.reject(homeView)
                                        }
                                    }
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
                // What the opening will hold, worked out before it is drawn (the
                // order below never changes): "What needs you today" — the
                // exam's own nudge only while that exam can be taken (§7.2 #1),
                // never one that repeats a YOUR WEEK row (§9.1 rule 3), and a
                // first day's side task held back (§9.1 rule 4) — or the
                // reflection strip; and the Live-now card.
                val shownNudges = nudges.filter {
                    nudgeOffered(it, pathway) && !YourWeek.repeats(it, week, letterCard = letter != null) && !YourWeek.heldOnFirstDay(it, journey)
                }
                // Android only (§7.3): while the phone has notifications off,
                // "Turn on notifications" goes first.
                val notifyLead: (@Composable () -> Unit)? = if (notificationsCard.shown) {
                    { NotificationsOffCard(onTurnOn = notificationsCard.turnOn, onNotNow = notificationsCard.notNow) }
                } else null
                val needsRail = shownNudges.isNotEmpty() || notifyLead != null
                val reflectionStrip = shownNudges.isEmpty() && reflectionDue && !YourWeek.firstDay(journey)
                val liveNow = liveNowInfo(upcoming)
                // A quiet divider when dark cards would touch (owner, 2026-10-07):
                // the navy letter on the liturgy's photograph on a first day, the
                // navy Live-now card right on the letter. Nothing moves.
                val quietBefore = HomeQuiet.dividersBefore(
                    HomeQuiet.opening(
                        verseArt = verse?.art?.url?.isNotBlank() == true, video = welcomeVideo != null,
                        liveNow = liveNow != null, needsRail = needsRail, reflectionStrip = reflectionStrip,
                    ),
                )
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
                liveNow?.let { info ->
                    Entrance(entrance, 1) {
                        LiveNowCard(info) { onNavigate("event/${info.occ.occurrenceId}?end=${android.net.Uri.encode(info.occ.endAt)}") }
                    }
                }
                // The Sunday Letter knock — three states (knock / quiet row / awaiting).
                // Awaiting opens the You tab: with no letter yet the letters
                // archive would be empty, and You is where "yours" lives.
                if (HomeEdge.LETTER in quietBefore) SelahDivider()
                if (letter == null) Entrance(entrance, 2) { LetterAwaitingCard(onClick = { onSelectTab("profile") }) }
                letter?.takeIf { !it.isUnread }?.let { lt ->
                    LetterReadRow(lt) { showLetter = true }
                    if (showLetter) {
                        LetterDialog(
                            lt, onDismiss = { showLetter = false }, onRead = {},
                            onNextStep = { route, moduleId -> showLetter = false; openWeek(letterStepDest(route, moduleId)) },
                            onWriteBack = { id -> showLetter = false; onNavigate("chat/$id?ctx=pastoral") },
                            onOpenLetters = { id -> showLetter = false; onNavigate(lettersRoute(id)) },
                        )
                    }
                }
                letter?.takeIf { it.isUnread }?.let { lt ->
                    LetterKnockCard(lt) { showLetter = true }
                    if (showLetter) {
                        LetterDialog(
                            lt, onDismiss = { showLetter = false }, onRead = { letter = lt.copy(readAt = "read") },
                            onNextStep = { route, moduleId -> showLetter = false; openWeek(letterStepDest(route, moduleId)) },
                            onWriteBack = { id -> showLetter = false; onNavigate("chat/$id?ctx=pastoral") },
                            onOpenLetters = { id -> showLetter = false; onNavigate(lettersRoute(id)) },
                        )
                    }
                }
                // "What needs you today" — the server-ranked rail (GET /me/home/
                // nudges). If the endpoint fails or has nothing, the old single-
                // purpose reflection strip stands in, so Home never loses the
                // nudge that ticks the rhythm. The unread-letter nudge opens the
                // same letter sheet the knock card does, in place.
                // (shownNudges, notifyLead: worked out above, with the opening.)
                if (needsRail) {
                    Entrance(entrance, 2) {
                        NeedsYouRail(shownNudges, lead = notifyLead) { n ->
                            if (n.route == "letter" && letter != null) showLetter = true
                            else onNavigate(nudgeRouteFor(n))
                        }
                    }
                }
                if (reflectionStrip) {
                    // Reflection due — deep-links to the devotional's reflection
                    // composer, the one act that ticks the rhythm and clears this.
                    Entrance(entrance, 2) { ReflectionStrip { onNavigate("devotional") } }
                }
                // The hour's word — BELOW the reflection strip (owner's order).
                if (HomeEdge.LITURGY in quietBefore) SelahDivider()
                Entrance(entrance, 0) {
                    LiturgyCard(canManageRecordings = me?.profile?.role in setOf("Admin", "SuperAdmin"))
                }
                // 3 · YOUR WEEK — one card, each pillar's next thing (§6.1). It
                // took the place of the "For you today" hero, the continue-level
                // card, the plan-resume banner, the minis row, the this-week and
                // "Your cell" cards, and the upcoming list.
                // Its reads answer with the first load: until then the card
                // holds its shape — a row never says "Didn't load" while it is
                // still loading (final walk M4), nor guesses a "none" form.
                Entrance(entrance, 3) { if (loadedOnce) YourWeekCard(week) { openWeek(it) } else YourWeekSkeleton() }
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
                    // The featured gathering has its own card just below:
                    // never twice on one screen (§7.2 #9).
                    ownCardSeries = featuredEvent?.seriesId,
                    onAll = { onNavigate("events-calendar") },
                    onOpenAnnouncement = { id -> onNavigate("announcement/$id") },
                    onOpenEvent = { id -> onNavigate("event/$id?end=") },
                )
                featuredEvent?.let { FeaturedGatheringCard(it) { onSelectTab("events") } }
                // 6 · Growing — the scores (with the journey's one-line step),
                // grow your faith, and the encouragement.
                scores?.let { ProgressCard(it) { onNavigate("pathway") } }
                if (scores != null) SelahDivider()   // — selah: a rest before Grow
                GrowSection(onNavigate, discipler)
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
    /** Null until the member is known — the greeting then stands alone. */
    firstName: String?,
    /** Null until the member is known — a shimmer, never a guess. */
    level: Int?,
    /** The member couldn't be read: no pill at all, rather than a shimmer
     *  that never resolves (§9.4). */
    levelUnknown: Boolean = false,
    /** The journey's pill (§3) — "12 of 20 modules", "Exam ready", … — null until it loads. */
    journeyPill: String?,
    /** The overall growth score, 0–100 — a score, never a percent; null
     *  until it is known (the ring waits, empty, with no number). */
    growthScore: Int?,
    trend: org.nuruplace.member.data.net.ScoreTrend? = null,
    personalWord: String? = null,
    onBell: () -> Unit,
    onScan: () -> Unit,
    churchLive: LiveNowRow? = null,
    onLive: () -> Unit = {},
) {
    // iOS's words (HomeGreeting, §8.2 #13): Sunday is the Lord's Day —
    // "Happy Lord's Day, Ada." — and late evening is "Rest well".
    // The church's clock, as the liturgy card's (§9.3 rule 3).
    val now = java.time.LocalDateTime.now(HomeGreeting.CHURCH_ZONE)
    val kicker = HomeGreeting.kicker(now.toLocalDate())
    val greeting = HomeGreeting.greeting(now)
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
            // Nuru Live (L2) — a church stream is live right now. Same 40dp
            // circle language as the buttons either side of it, so the row reads
            // as one family; the pulsing red ring (not a static border) is what
            // says "this one is happening right now". Conditional, so the resting
            // header stays at three.
            if (churchLive != null) {
                LiveHeaderChip(onClick = onLive)
                Spacer(Modifier.width(Spacing.sm))
            }
            // The growth score — "45", never "45%" (§3) — once there is one:
            // not while it loads, and not a "0" ring on a first day (§9.2 #4;
            // iOS HomeHeaderWords.showsScore). Left of the bell: the bell is
            // the far right on every tab (§8.1 rule 2; Cycle 4 walk 01). The
            // wider gap clears the trend badge that hangs off its corner.
            if ((growthScore ?: 0) > 0) {
                // A figure in a fixed ring keeps the everyday size; the page's
                // own words carry the score too, and they grow (§9.6 #4).
                org.nuruplace.member.ui.components.CappedFontScale(1f) { Box {
                    ProgressRing(pct = growthScore ?: 0, size = 42.dp, stroke = 4.dp, track = Nuru.track, arc = Nuru.gold) {
                        // One face for the score wherever it's a ring's figure —
                        // Fraunces, as on the progress card below (§8.1 rule 3;
                        // Cycle 4 walk 01/06) — in the gold chip ink iOS uses:
                        // a score isn't a state, so not green (rule 1).
                        growthScore?.let { Text("$it", style = nuruSerif(12, FontWeight.SemiBold), color = Nuru.goldChipText) }
                    }
                    trend?.takeIf { it.delta != 0 }?.let { t ->
                        TrendBadge(t, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp))
                    }
                } }
                Spacer(Modifier.width(Spacing.md))
            }
            // The one bell (EXPERIENCE.md §7.2 #4, §8.1 rule 7): the inbox, a
            // dot only while something is unread — the same bell, at the far
            // right, on every tab (rule 2).
            org.nuruplace.member.ui.components.InboxBell(onClick = onBell)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(if (firstName != null) "$greeting, $firstName." else "$greeting.", style = NuruType.greeting, color = Nuru.navy)
        // Nuru's daily word (GET /me/home/greeting) — hanging gold quote + serif
        // voice, mirroring iOS HomePersonalWord. Absent until the wire answers.
        personalWord?.let { word ->
            // Owner (2026-08-26): the quoted head-card word steps down one point
            // — the hanging gold glyph and the line it opens. Sizes are written
            // out (not NuruType.title / NuruType.body) precisely because this
            // pair moves together and independently of those shared tokens.
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                Text("“", style = nuruSerif(22, FontWeight.Medium), color = Nuru.gold)
                Spacer(Modifier.width(6.dp))
                Text(
                    word,
                    style = nuruSans(13).copy(fontStyle = FontStyle.Italic, lineHeight = scaledLineHeight(19)),
                    color = Nuru.ink600,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        // Level jewel capsule — the level and the journey's pill (§3); the
        // streak is the rhythm card's (§9.2 #3). Until the level is known, a
        // shimmer in its place (B11).
        if (level == null) {
            if (!levelUnknown) org.nuruplace.member.ui.components.SkeletonBlock(height = 26.dp, width = 140.dp, corner = 999.dp)
        } else Row(
            Modifier.clip(RoundedCornerShape(999.dp))
                .background(Nuru.white)
                .border(1.dp, Nuru.gold.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Level $level", style = NuruType.micro, color = Nuru.navy, fontWeight = FontWeight.SemiBold)
            journeyPill?.let { Text("  ·  $it", style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.SemiBold) }
            // The streak is said once on Home, by the rhythm it counts (§9.2 #3).
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
        // The bell's circle and size (§8.1 rule 7, as iOS): white, a hairline,
        // the icon navy at 18 — it was a gold chip.
        Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(999.dp))
            .clickable(onClickLabel = "Scan to check in") { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Lucide.ScanQrCode,
            contentDescription = "Scan to check in",
            tint = Nuru.navy,
            modifier = Modifier.size(18.dp),
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
        Modifier.size(44.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.homeNavy)
            .border(2.dp, Nuru.liveRed.copy(alpha = ringAlpha), RoundedCornerShape(999.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(Lucide.Video, "Live now", tint = Nuru.white, modifier = Modifier.size(18.dp)) }
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
            // Nothing to draw at 0 — a round cap would leave a dot.
            if (pct > 0) drawArc(arc, -90f, 360f * (pct.coerceIn(0, 100) / 100f), false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
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
            // State colours only (§8.1 rule 1): up is green, down amber — it was orange.
            .background(if (up) Nuru.success else Nuru.warning)
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
        else Icon(Lucide.User, null, tint = Nuru.ink400, modifier = Modifier.size(18.dp))
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
            else Icon(Lucide.Radio, null, tint = Nuru.gold, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("● ON AIR · NURU RADIO", style = NuruType.micro, color = Nuru.liveRed, fontWeight = FontWeight.Bold)
            Text(r.title, style = NuruType.cardCta, color = Nuru.onNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                Icon(Lucide.Users, null, tint = Nuru.gold, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (info.startsInMin == null) "● HAPPENING NOW" else "STARTING SOON · ${info.startsInMin}m",
                style = NuruType.kicker,
                color = if (info.startsInMin == null) Nuru.liveRed else Nuru.gold,
                fontWeight = FontWeight.Bold,
            )
            Text(info.occ.title, style = NuruType.featureTitle, color = Nuru.onNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        // Lucide, not a colour emoji (§8.1 rule 7) — iOS's priority strip glyph.
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.white), contentAlignment = Alignment.Center) {
            Icon(Lucide.MessageSquareText, null, tint = Nuru.gold, modifier = Modifier.size(18.dp))
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
    // The week's one next step leads, as a navy band under the kicker; the
    // other rows follow, white, in their order (owner, 2026-10-07: colour
    // option A; YourWeek.cardOrder).
    val (step, rest) = remember(rows) { YourWeek.cardOrder(rows) }
    HomeCard(pad = 0.dp) {
        Box(Modifier.padding(start = Spacing.base, end = Spacing.base, top = Spacing.base, bottom = Spacing.xs)) {
            CardKicker("Your week")
        }
        step?.ask?.let { ask ->
            // Inset 10 dp from the card's edge, as drawn: wider than the rows.
            NextStepBand(step, ask, Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) { onOpen(step.dest) }
        }
        rest.forEachIndexed { i, row ->
            // Inset to the text, as the Partners rows draw it.
            if (i > 0) Box(Modifier.padding(start = 68.dp, end = Spacing.base).fillMaxWidth().height(1.dp).background(Nuru.border))
            WeekRowView(row) { onOpen(row.dest) }
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}

/** YOUR WEEK before its reads first answer: the card's kicker over five
 *  shimmering rows, in the card's own shape. */
@Composable
private fun YourWeekSkeleton() {
    HomeCard(pad = 0.dp) {
        Box(Modifier.padding(start = Spacing.base, end = Spacing.base, top = Spacing.base, bottom = Spacing.xs)) {
            CardKicker("Your week")
        }
        repeat(5) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                org.nuruplace.member.ui.components.SkeletonBlock(height = 36.dp, width = 36.dp, corner = 10.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    org.nuruplace.member.ui.components.SkeletonBlock(height = 14.dp, width = 160.dp, corner = 6.dp)
                    org.nuruplace.member.ui.components.SkeletonBlock(height = 12.dp, width = 110.dp, corner = 6.dp)
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
    }
}

/** Each pillar's one icon (§8.2 #2). */
private fun weekIcon(pillar: WeekPillar) = when (pillar) {
    WeekPillar.PATHWAY -> Lucide.BookOpen
    WeekPillar.PLANS -> Lucide.BookMarked
    WeekPillar.EVENTS -> Lucide.Calendar
    WeekPillar.GIVING -> Lucide.HandHeart
    WeekPillar.CELL -> Lucide.Users
}

/** The week's one next step (owner, 2026-10-07: colour option A, "navy for
 *  your next step"): a navy band inside the white card — a gold-tint tile with
 *  the pillar's gold icon, "YOUR NEXT STEP · ‹PILLAR›" in gold, what it acts
 *  on in white Fraunces, the row's line in #B9C4D4, and the screen's one gold
 *  primary pill with the row's verb. Past the everyday sizes the pill takes a
 *  line of its own, so no word is squeezed (§9.6 #4). As iOS's band. */
@Composable
private fun NextStepBand(row: WeekRow, ask: WeekAsk, modifier: Modifier, onClick: () -> Unit) =
    NavyStepBand(
        kicker = "YOUR NEXT STEP · ${row.form.pillar.name}",
        title = ask.subject,
        line = row.line,
        verb = ask.verb,
        icon = weekIcon(row.form.pillar),
        modifier = modifier,
        onClick = onClick,
    )

/** The navy "next step" band — YOUR WEEK's lead, and the Sunday Letter's
 *  "ONE STEP FOR THIS WEEK" (owner, 2026-10-07: the editorial Sunday Letter —
 *  "the same navy band as Home's next step"): a gold-tint tile with a gold
 *  icon, the [kicker] in gold, [title] in white Fraunces, the [line] in
 *  #B9C4D4, and the one gold primary pill with the [verb]. Past the everyday
 *  sizes the pill takes a line of its own, so no word is squeezed (§9.6 #4). */
@Composable
internal fun NavyStepBand(
    kicker: String,
    title: String,
    line: String,
    verb: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val gold = Color(0xFFE8CA6C)
    val meta = Color(0xFFB9C4D4)
    val shape = RoundedCornerShape(18.dp)
    val large = org.nuruplace.member.ui.components.largeText()
    val tile: @Composable () -> Unit = {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(gold.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = gold, modifier = Modifier.size(18.dp))
        }
    }
    val kickerText: @Composable () -> Unit = { Text(kicker, style = NuruType.kicker, color = gold) }
    val titleText: @Composable () -> Unit = {
        org.nuruplace.member.ui.components.WholeWordsText(
            title, style = NuruType.cardTitle, color = Color.White,
            maxLines = if (large) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
        )
    }
    val pill: @Composable () -> Unit = {
        Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldGradient).padding(horizontal = 16.dp, vertical = 9.dp)) {
            Text(verb, style = nuruSans(13, FontWeight.Bold), color = Nuru.navy, maxLines = 1, softWrap = false)
        }
    }
    Box(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF11253F), Color(0xFF0A1628))))
            .clickable(onClickLabel = verb) { onClick() }
            .padding(16.dp),
    ) {
        if (large) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tile(); kickerText(); titleText()
                if (line.isNotBlank()) Text(line, style = nuruSans(12), color = meta)
                pill()
            }
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                tile()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    kickerText(); titleText()
                    // The kicker and title take the band's width; the pill sits at
                    // the right of the line below.
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(line, style = nuruSans(12), color = meta, modifier = Modifier.weight(1f))
                        pill()
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekRowView(row: WeekRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = Spacing.base, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Nuru.goldChipBg), contentAlignment = Alignment.Center) {
            Icon(weekIcon(row.form.pillar), contentDescription = null, tint = Nuru.goldChipText, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = NuruType.rowTitle, color = Nuru.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.line.isNotBlank()) {
                // A prompt that didn't go through reads as Give draws it — the
                // server's words in the urgent colour (final walk M2).
                val failing = row.form == WeekForm.GIFT_FAILING
                Text(
                    row.line,
                    style = NuruType.caption, color = if (failing) Nuru.goldChipText else Nuru.ink600,
                    fontWeight = if (failing) FontWeight.SemiBold else null,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Text("›", style = NuruType.title, color = Nuru.ink300)
    }
}

@Composable
private fun RhythmCard(r: RhythmToday, streak: Int) {
    // The one streak, named and counted as Plans names and counts it
    // (EXPERIENCE.md §9.2 #3): it was "🔥 3" here, "🔥 3-day" in the header
    // and "1-day streak" on Plans.
    val days = org.nuruplace.member.feature.grow.StreakWords.days(streak, r.doneCount > 0)
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (r.doneCount >= 3) "Today's rhythm complete 🎉" else "Today's rhythm", style = NuruType.cardTitle, color = Nuru.ink, modifier = Modifier.weight(1f))
            if (days > 0) Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Flame, null, tint = Nuru.goldChipText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(org.nuruplace.member.feature.grow.StreakWords.label(days), style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        // Read-only by design: the chips REFLECT real acts (a prayer posted or
        // encouraged, Scripture engaged, a reflection written) — the server ticks
        // them from interaction events; they are not tappable checkboxes.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RhythmTile(RhythmTileWords.PRAYER, r.prayer, Modifier.weight(1f))
            RhythmTile(RhythmTileWords.WORD, r.word, Modifier.weight(1f))
            RhythmTile(RhythmTileWords.REFLECTION, r.reflection, Modifier.weight(1f))
        }
    }
}

/** Each rhythm tile in plain words (EXPERIENCE.md §8.2 #20): what the day
 *  holds — "Prayed", "Read", "Written" once it is done, "Not yet" until then.
 *  Never "Pending". The server ticks them from real acts. */
internal enum class RhythmTileWords(val label: String, val done: String) {
    PRAYER("Prayer", "Prayed"),
    WORD("Word", "Read"),
    REFLECTION("Reflection", "Written"),
    ;

    fun status(isDone: Boolean): String = if (isDone) done else NOT_YET

    companion object {
        const val NOT_YET = "Not yet"
    }
}

/** iOS's tile (HomeView rhythmTile): a check or a clock in a small circle,
 *  the discipline, and where it stands today in plain words. */
@Composable
private fun RhythmTile(words: RhythmTileWords, done: Boolean, modifier: Modifier) {
    val ink = if (done) Nuru.successText else Nuru.goldChipText
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (done) Nuru.successBg else Nuru.goldChipBg)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(999.dp)).background(if (done) Nuru.successText else Nuru.white),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (done) Lucide.Check else Lucide.Clock4, contentDescription = null,
                tint = if (done) Nuru.white else Nuru.goldLo, modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        // Whole words at the largest text — it read "Reflectio / n" (§9.6 #4).
        org.nuruplace.member.ui.components.WholeWordsText(words.label, style = NuruType.label.copy(fontWeight = FontWeight.SemiBold), color = ink, textAlign = TextAlign.Center)
        Text(words.status(done), style = NuruType.micro, color = ink.copy(alpha = 0.8f))
    }
}

// Featured welcome video — it plays IN PLACE, inside this card's inset box in
// the video's own shape (VideoShape.kt), for every source. See ui/components/VideoPlayer.kt for the browser/
// download bug this replaced, and ui/components/VideoPoster.kt for the poster
// frame we cut ourselves when the server sends no thumbnail_url.
@Composable
private fun FeaturedVideo(v: WelcomeVideo, playing: Boolean, onPlay: (String) -> Unit) {
    // White, with a hairline and the one soft shadow — every Home card's look
    // (owner, 2026-10-07: colour option A). It sat on an off-palette grey,
    // #EEF0F3.
    HomeCard {
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
        // The video takes its own shape (owner, 2026-10-06): a portrait video
        // was pillarboxed in a fixed 16:9 frame. The frame is the video's
        // width ÷ height at the card's full content width — the player's own
        // report, else the one remembered from an earlier load, else the
        // poster's size, else 16:9 (VideoShape.frame) — and the card grows
        // with it. The header and caption stay as they were.
        val assetId = v.mediaAssetId
        var rememberedShape by remember(assetId) { mutableStateOf(AppPrefs.videoRatio(assetId)) }
        var posterShape by remember(assetId) { mutableStateOf<Float?>(null) }
        var playerShape by remember(assetId) { mutableStateOf<Float?>(null) }
        val frameShape = VideoShape.frame(playerShape, rememberedShape, posterShape)
        val onPoster: (Int, Int) -> Unit = { w, h ->
            VideoShape.displayAspect(w, h)?.let { if (VideoShape.differs(posterShape, it)) posterShape = it }
        }
        if (playing && playable != null) {
            // Direct/cloudinary/HLS → ExoPlayer; youtube/vimeo → provider embed.
            // Either way it renders inside this box, with its own gold buffering
            // cue — no Intent, no browser, no download.
            InlineVideoPlayer(
                url = playable,
                source = v.videoSource,
                externalVideoId = v.externalVideoId,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                aspectRatio = frameShape,
                fillFrame = true,
                onVideoAspect = { shape ->
                    if (VideoShape.differs(playerShape, shape)) playerShape = shape
                    if (VideoShape.differs(rememberedShape, shape)) {
                        rememberedShape = shape
                        AppPrefs.rememberVideoRatio(assetId, shape)
                    }
                },
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(frameShape).clip(RoundedCornerShape(16.dp))
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
                        VideoPosterFrame(playable, Modifier.fillMaxSize(), contentDescription = v.caption, onSize = onPoster)
                    }
                } else {
                    AsyncImage(
                        model = v.thumbnailUrl,
                        contentDescription = v.caption,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        onSuccess = { s -> s.result.drawable.let { d -> onPoster(d.intrinsicWidth, d.intrinsicHeight) } },
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
                    // Lucide book-open, not a colour emoji (§8.1 rule 7; Cycle 4 walk 01) — as iOS.
                    Icon(Lucide.BookOpen, null, tint = Nuru.goldChipText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    CardKicker(v.mood?.takeIf { it.isNotBlank() }?.let { "Verse for today · $it" } ?: "Verse for today")
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
        // One line at the everyday sizes; past them it wraps, so Save and Share
        // are never behind a sideways swipe (§8.1 rule 9, §9.6 #4).
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
        // No zero counts (§7.4 #9): no pill until someone prays or replies.
        org.nuruplace.member.util.ZeroCounts.prayerLine(praying = post.prayCount, replies = post.commentCount ?: 0)?.let { counts ->
            Spacer(Modifier.height(Spacing.sm))
            // iOS's pill: the hand-heart and the counts — not a "🤲" typed in (§8.1 rule 7).
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.HandHeart, null, tint = Nuru.goldChipText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(counts, style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Featured carousel (owner's revision, 2026-08-24 — iOS parity): one sliding
// rail for everything the portal has marked or scheduled — the featured
// announcement, the featured gathering, and the next few events. Auto-advances
// gently; a swipe is always respected. "View all" opens the full events list.
internal sealed interface FeaturedPage {
    data class Ann(val a: FeaturedAnnouncement) : FeaturedPage
    data class Fev(val e: FeaturedEvent) : FeaturedPage
    data class Occ(val o: HomeEventRow) : FeaturedPage
}

/** The featured gathering is still ahead — its start, the church's wall time
 *  (Nairobi), is not past. An unreadable start is not shown. */
internal fun featuredIsUpcoming(ev: FeaturedEvent, now: java.time.LocalDateTime = java.time.LocalDateTime.now(EV_ZONE)): Boolean =
    runCatching { java.time.LocalDateTime.parse(ev.dtstartLocal.trim().take(19)) }.getOrNull()?.let { !it.isBefore(now) } == true

/** The carousel's slides: the featured announcement, the featured
 *  gathering, then the next three events — never an event that has its own
 *  card on the same screen (EXPERIENCE.md §7.2 #9). [ownCardSeries]: the
 *  series of the gathering shown in its own card (Home's featured-gathering
 *  card) — neither it nor another occurrence of it slides here too; the
 *  featured gathering's other occurrences never repeat it either. A series
 *  slides once, at its next date (as iOS) — a weekly gathering filled the
 *  three slides with itself. */
internal fun featuredPages(
    announcement: FeaturedAnnouncement?,
    featuredEvent: FeaturedEvent?,
    events: List<HomeEventRow>,
    ownCardSeries: String?,
): List<FeaturedPage> {
    val own = ownCardSeries?.takeIf { it.isNotBlank() }
    return buildList {
        announcement?.let { add(FeaturedPage.Ann(it)) }
        featuredEvent?.takeIf { own == null || it.seriesId != own }?.let { add(FeaturedPage.Fev(it)) }
        events.filter { it.seriesId != featuredEvent?.seriesId && (own == null || it.seriesId != own) }
            .distinctBy { it.seriesId.ifBlank { it.occurrenceId } }
            .take(3).forEach { add(FeaturedPage.Occ(it)) }
    }
}

@Composable
private fun FeaturedCarousel(
    announcement: FeaturedAnnouncement?,
    featuredEvent: FeaturedEvent?,
    events: List<HomeEventRow>,
    /** The gathering that has its own card below — never slid here too. */
    ownCardSeries: String?,
    onAll: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    val pages = remember(announcement, featuredEvent, events, ownCardSeries) {
        featuredPages(announcement, featuredEvent, events, ownCardSeries)
    }
    if (pages.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs)) {
            SectionLabel("Featured")
            Spacer(Modifier.weight(1f))
            RowScopeLink("View all", onAll)
        }
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pages.size })
        // Gentle auto-advance every 6s; pauses whenever a finger is on the rail
        // — and runs only while Home is the screen in front (EXPERIENCE.md
        // §9.7 M7): resumed, and its window holding the focus. The letter, a
        // sheet or a dialog over Home, a page pushed over it, the app in the
        // background — each stops it. It animated on behind the open letter
        // and was the main thread in three ANRs on the final walk.
        val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
            .currentStateFlow.collectAsState()
        val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
        val advances = carouselAdvances(
            pages.size,
            resumed = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED),
            windowFocused = windowFocused,
        )
        LaunchedEffect(pages.size, advances) {
            if (!advances) return@LaunchedEffect
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
                    title = page.a.title, body = org.nuruplace.member.ui.components.LightMarkdown.plain(page.a.body),
                    meta = page.a.sentAt?.let { fmtDate(it) }, cta = "Read more ›",
                ) { onOpenAnnouncement(page.a.announcementId) }
                is FeaturedPage.Fev -> FeaturedPageCard(
                    kicker = "FEATURED GATHERING", imageUrl = page.e.primaryImageUrl,
                    title = page.e.title, body = page.e.description ?: page.e.location.orEmpty(),
                    // One date form (§8.1 rule 8) — it showed the raw start.
                    meta = runCatching { java.time.LocalDateTime.parse(page.e.dtstartLocal.trim().take(19)) }.getOrNull()
                        ?.let { org.nuruplace.member.util.NuruDates.dayTime(it) },
                    cta = "See details ›",
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
            // Wraps to two lines, never cut (§8.1 rule 9).
            Text(title, style = NuruType.featureTitle, color = Nuru.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun ProgressCard(s: ScoresSummary, onView: () -> Unit) {
    HomeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your progress", style = NuruType.cardTitle, color = Nuru.ink, modifier = Modifier.weight(1f))
            RowScopeLink("View pathway", onView)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(s.overall.score, 64.dp, 6.dp, Nuru.goldChipBg, Nuru.gold) {
                org.nuruplace.member.ui.components.CappedFontScale(1f) { Text("${s.overall.score}", style = NuruType.rowTitle, color = Nuru.ink) }
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
                    Text(caption, style = NuruType.caption, color = if (t.isDown) Nuru.warning else if (t.isUp) Nuru.success else Nuru.ink600)
                } else {
                    Text("Your rhythm across the disciplines", style = NuruType.caption, color = Nuru.ink600)
                }
            }
        }
        Spacer(Modifier.height(Spacing.base))
        val d = s.trend?.domains
        val bars = listOf(
            ScoreLine("Habits", s.habits.score, Nuru.gold, d?.get("habits")),
            // Progress is gold (§8.1 rule 1) — each pillar wore its own hue.
            ScoreLine("Word", s.word.score, Nuru.gold, d?.get("word")),
            ScoreLine("Prayer", s.prayer.score, Nuru.gold, d?.get("prayer")),
            ScoreLine("Curriculum", s.curriculum.score, Nuru.gold, d?.get("curriculum")),
            ScoreLine("Attendance", s.attendance.score, Nuru.gold, d?.get("attendance")),
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
        // No next-step line here: YOUR WEEK's Pathway row says it, and
        // "View pathway" is this card's way there — Home points to each
        // pillar once (§6, §9.6 #1; iOS 3137194 the same).
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
        // Whole words at the largest text — "Curric / ulum", "Attend / ance" (§9.6 #4).
        org.nuruplace.member.ui.components.WholeWordsText(line.label, style = NuruType.caption, color = Nuru.ink600, modifier = Modifier.width(84.dp))
        Box(Modifier.weight(1f)) { ProgressBar(line.value, line.color, height = 8.dp) }
        // A whisper of movement vs the previous 28 days, next to the score.
        val delta = scoreDelta(line.delta)
        if (delta != null) {
            Text(
                delta,
                style = SCORE_DELTA_STYLE,
                color = if ((line.delta ?: 0) < 0) Nuru.warning else Nuru.success,
                maxLines = 1, softWrap = false,
                modifier = Modifier.width(deltaWidth), textAlign = TextAlign.End,
            )
        } else {
            Spacer(Modifier.width(deltaWidth))
        }
        // Past the everyday sizes the two columns are as wide as their figures,
        // and "▲94" ran into "94" — a gap keeps them two numbers.
        if (org.nuruplace.member.ui.components.largeText()) Spacer(Modifier.width(6.dp))
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
private fun GrowSection(onNavigate: (String) -> Unit, discipler: HomeDiscipler?) {
    Column {
        SectionLabel("Grow your faith")
        HomeCard(pad = Spacing.md) {
            // iOS's glyphs (sun · quote · hand-heart · sparkles), each on the
            // gold-tint tile (§8.1 rules 1 and 7): the tiles were amber, red
            // and purple — hues that say state, or nothing.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GrowTile("Devotional", "Today's devotional", Lucide.Sun, Modifier.weight(1f)) { onNavigate("devotional") }
                GrowTile("Hide His Word", "Memorize Scripture", Lucide.Quote, Modifier.weight(1f)) { onNavigate("memory-verses") }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                GrowTile("My Prayer Room", "Pray with the family", Lucide.HandHeart, Modifier.weight(1f)) { onNavigate("prayer-room?tab=corporate") }
                GrowTile("Your Calling", "Discover your gifts", Lucide.Sparkles, Modifier.weight(1f)) { onNavigate("gifts") }
            }
            // Unknown until GET /growth/mentor answers — never a guess.
            discipler?.let { d ->
                Spacer(Modifier.height(Spacing.sm))
                DisciplerRow(d.mentor) { onNavigate("mentor") }
            }
        }
    }
}

/** What Home knows of the member's discipler once the server answers:
 *  [mentor] null means none (GET /growth/mentor). */
internal data class HomeDiscipler(val mentor: org.nuruplace.member.data.net.MentorInfo.Mentor?)

/** "YOUR DISCIPLER · ‹their name›" with their photo (or initials) → Mentor —
 *  or, for a member who has none, the one place that says so: "No discipler
 *  yet — your leader will pair you" (EXPERIENCE.md §9.2 #8) beside the
 *  heart-handshake on a gold-tint tile, with nothing to tap. As iOS
 *  (HomeView's grow card); it was a blank gold disc (Cycle 4 walk 07). */
@Composable
private fun DisciplerRow(mentor: org.nuruplace.member.data.net.MentorInfo.Mentor?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Nuru.verseBg)
            .border(1.dp, Nuru.gold.copy(alpha = 0.2f), shape)
            .then(if (mentor != null) Modifier.clickable { onClick() } else Modifier)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mentor != null) {
            org.nuruplace.member.feature.community.Avatar(name = mentor.fullName, url = mentor.avatarUrl, size = 36.dp)
        } else {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.goldTint), contentAlignment = Alignment.Center) {
                Icon(Lucide.HeartHandshake, null, tint = Nuru.navy, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            if (mentor != null) {
                CardKicker("Your discipler")
                Text(
                    mentor.fullName.ifBlank { "Your discipler" },
                    style = NuruType.cardCta, color = Nuru.navy, fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(DISCIPLER_NONE, style = NuruType.cardCta, color = Nuru.navy, fontWeight = FontWeight.SemiBold)
            }
        }
        if (mentor != null) Icon(Lucide.ChevronRight, null, tint = Nuru.ink300, modifier = Modifier.size(18.dp))
    }
}

/** Where the Sunday Letter's one next step goes — the server sends "module"
 *  with a moduleId (that lesson) or "pathway" (letters.ts); anything else
 *  lands on Pathway, as iOS LetterView.navigate. It went nowhere: Home
 *  never wired the dialog's onNextStep (§9.1 rule 7, no dead ends). */
internal fun letterStepDest(route: String, moduleId: String?): WeekDest =
    if (route == "module" && !moduleId.isNullOrBlank()) WeekDest.Screen("module/$moduleId") else WeekDest.Tab("pathway")

/** Said once, on Home, to a member with no discipler (EXPERIENCE.md §9.2 #8). */
internal const val DISCIPLER_NONE = "No discipler yet — your leader will pair you"

@Composable
private fun GrowTile(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(Nuru.surface).border(1.dp, Nuru.border, RoundedCornerShape(16.dp)).clickable { onClick() }.padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A row icon: navy on a gold-tint tile (§8.1 rule 7).
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Nuru.goldTint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Nuru.navy, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        // The words wrap inside the tile — never cut (§8.1 rule 9, §8.2 #20).
        Column(Modifier.weight(1f)) {
            // Whole words at the largest text — "Devotio / nal" (§9.6 #4).
            org.nuruplace.member.ui.components.WholeWordsText(title, style = NuruType.cardCta.copy(fontWeight = FontWeight.SemiBold), color = Nuru.ink)
            org.nuruplace.member.ui.components.WholeWordsText(sub, style = NuruType.micro, color = Nuru.ink600)
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
            // One date form (§8.1 rule 8): "Sun 11 Oct · 2:00 PM" — never the
            // raw value when it can't be read.
            val whenText = runCatching { java.time.LocalDateTime.parse(ev.dtstartLocal.take(19)) }.getOrNull()
                ?.let { org.nuruplace.member.util.NuruDates.dayTime(it) }
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
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.white), contentAlignment = Alignment.Center) { Icon(Lucide.HandHeart, null, tint = Nuru.gold, modifier = Modifier.size(18.dp)) }
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
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(999.dp)).background(Nuru.gold), contentAlignment = Alignment.Center) { Icon(Lucide.HandHeart, null, tint = Nuru.homeNavy, modifier = Modifier.size(22.dp)) }
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
                ) {
                    // iOS's words: "Give now" and the chevron — no "🤲" or typed "›" (§8.1 rule 7).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Give now", style = NuruType.cardCta, color = Nuru.homeNavy, fontWeight = FontWeight.SemiBold)
                        Icon(Lucide.ChevronRight, null, tint = Nuru.homeNavy, modifier = Modifier.size(18.dp))
                    }
                }
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
    parseZdt(s)?.let { org.nuruplace.member.util.NuruDates.day(it.toInstant(), it.zone) } ?: ""

/** Whether Home's featured carousel turns by itself (EXPERIENCE.md §9.7 M7):
 *  two pages or more, Home resumed, and its window holding the focus — never
 *  behind the letter, a sheet, a dialog or another screen. */
internal fun carouselAdvances(pageCount: Int, resumed: Boolean, windowFocused: Boolean): Boolean =
    pageCount >= 2 && resumed && windowFocused
