// Events tab — the iOS "Gathered together" screen. Cream header + week strip + dark
// calendar card + segmented control + search + category chips + gatherings list, then
// "Series you follow" (followed only), "More series" and "Announcements" rails. It
// opens on the first tab with something in it (§7.4 #6). Shared chrome (palette, cream
// header, event card, date fns) lives in EventsShared.kt (same package — no import needed).
//
// One header (pathway docs/EXPERIENCE.md §6.2): "EVENTS", the title, and one line —
// "Next: «title» · EEE d MMM" or "Nothing planned this week" (EventsHeader.kt). A
// quiet week (§6.5, nothing in range) is quiet: the week strip, one calm card, and the
// calendar and check-in as two compact rows — the tabs, search and filters show only
// when there is something to filter.
package org.nuruplace.member.feature.events

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nuruplace.member.ui.components.EVERYDAY_MAX_FONT_SCALE
import org.nuruplace.member.ui.components.CappedFontScale
import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.data.net.EventSeries
import org.nuruplace.member.data.net.MyAnnouncement
import org.nuruplace.member.data.net.MyRsvp
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.InboxBell
import org.nuruplace.member.ui.components.ListSkeleton
import org.nuruplace.member.ui.components.noticeOnFailure
import org.nuruplace.member.util.isoPlusDays
import org.nuruplace.member.util.relTime
import org.nuruplace.member.util.todayIso
import org.nuruplace.member.ui.theme.NuruType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.nuruplace.member.ui.icons.Lucide

private val Capsule = RoundedCornerShape(999.dp)

/** Events-tab bundle — calendar + series + announcements + the member's own RSVPs. */
private data class EventsData(
    val events: List<CalendarOccurrence>,
    val series: List<EventSeries>,
    val anns: List<MyAnnouncement>,
    val rsvps: List<MyRsvp>,
)

@Composable
fun EventsScreen(
    onOpenEvent: (String, String?) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAttendance: () -> Unit,
    /** Broadcasters only (docs/PARTNERS_PROGRAMME.md §0): the "Broadcast" card
     *  MainShell mounts at the top of the body — Go Live / return / My
     *  Broadcasts (feature/live/BroadcastCard.kt). Null for everyone else. */
    broadcastCard: (@Composable () -> Unit)? = null,
) {
    // A tab root: its failed state offers Try again, not a "Go back" to nowhere.
    // Held by the tab (§7.2 #8): Back from an event finds the same list at the
    // same scroll, refreshed in place.
    AsyncContent(loading = { ListSkeleton(rows = 7) }, refreshable = true, offerBack = false, heldAs = "Events", load = {
        // The calendar IS the page: when it fails the member is told so (§4)
        // — it used to read as a week with nothing on. The rest stay accents.
        val cal = Net.client.api.calendar(todayIso(), isoPlusDays(60)).data
        val series = runCatching { Net.client.api.eventSeries().data }.getOrDefault(emptyList())
        val anns = runCatching { Net.client.api.myAnnouncements().data }.getOrDefault(emptyList())
        // The member's own RSVPs (GET /me/rsvps) — feeds the going-count + My RSVPs tab.
        val rsvps = runCatching { Net.client.api.myRsvps().data }.getOrDefault(emptyList())
        EventsData(cal, series, anns, rsvps)
    }) { (events, series, anns, rsvps), reload ->
        val scope = rememberCoroutineScope()
        val eventsContext = androidx.compose.ui.platform.LocalContext.current
        val today = remember { LocalDate.now(EV_ZONE) }
        // What the member picked stays picked across Back (§7 rule 5).
        var selectedDay by rememberSaveable { mutableStateOf(today) }
        var category by rememberSaveable { mutableStateOf("All") }
        var query by rememberSaveable { mutableStateOf("") }

        // date helpers over events
        fun occDate(occ: CalendarOccurrence): LocalDate? = evZdt(occ.startAt)?.toLocalDate()
        val thisWeek = events.count { val d = occDate(it); d != null && inThisWeek(d, today) }
        val rsvpMap = rsvps.associate { it.eventId to it.status }
        val going = rsvpMap.values.count { it == "going" }
        val upcoming = events.count { val d = occDate(it); d != null && !d.isBefore(today) }
        // Opens on the first tab with something in it (EXPERIENCE.md §7.4 #6) —
        // it opened on "Today (0)" with the gatherings under Upcoming. What the
        // member then picks stays picked across Back (§7 rule 5).
        var segment by rememberSaveable {
            mutableStateOf(
                firstEventsTab(
                    todayCount = events.count { occDate(it) == today },
                    upcomingCount = upcoming,
                    rsvpCount = events.count { rsvpMap.containsKey(it.occurrenceId) },
                ),
            )
        } // 0=Today, 1=Upcoming, 2=My RSVPs
        // A quiet week is quiet (EXPERIENCE.md §6.5): nothing in range → the
        // calm card, and no tabs, search or filters with nothing to filter.
        val quiet = eventsQuiet(events, today)

        Column(
            Modifier
                .fillMaxSize()
                .background(EV.paper)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 1. Header ──────────────────────────────────────────────────────
            // One header on every tab (EXPERIENCE.md §6.2): the eyebrow, the
            // serif title, one line of what matters now, the bell at the right.
            EvCreamHeaderBox {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            // §8.1 rules 2–3: the kicker (Inter 11 bold, tracking
                            // 1.4), the title, one Inter line at body size.
                            Text("EVENTS", style = NuruType.kicker, color = EV.eyebrowGold)
                            Text("Gathered together", style = evSerif(28, FontWeight.SemiBold), color = EV.navy)
                            Text(eventsHeaderLine(events, today), style = evInter(13), color = EV.secondary)
                        }
                        // The one bell (§7.2 #4): a dot only while something is unread.
                        InboxBell(onClick = onOpenNotifications)
                    }
                    // The counts only when there is something to count — a quiet
                    // week's header line already says it, and a chip never
                    // reads "0 you're going" (EXPERIENCE.md §7.4 #9).
                    if (!quiet && (thisWeek > 0 || going > 0)) {
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (thisWeek > 0) HeaderPill(eventsSoonPill(thisWeek), Lucide.CalendarDays)
                            if (going > 0) HeaderPill("$going you're going", Lucide.Check)
                        }
                    }
                }
            }

            // ── 2. Body ────────────────────────────────────────────────────────
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Broadcast (live:go members only) — Live moved into Events.
                broadcastCard?.invoke()

                // Week strip card
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
                        .border(1.dp, EV.border, RoundedCornerShape(22.dp)).padding(12.dp),
                ) {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)).uppercase(),
                            style = evInter(11, FontWeight.Bold, 1.4f), color = EV.overline,
                        )
                        Spacer(Modifier.weight(1f))
                        // A date picked on the strip brings the Today tab
                        // forward — the strip filters that tab; on Upcoming
                        // a date tap would change nothing (§7.4 #6, as iOS).
                        // A text action is gold (§8.1 rule 4; final walk C16: navy).
                        Text(
                            "TODAY", style = evInter(11, FontWeight.Bold, 1f), color = EV.gold,
                            modifier = Modifier.clickable { selectedDay = today; segment = EVENTS_TAB_TODAY },
                        )
                    }
                    // This week, whole and in view: today through the seventh
                        // day after (§6) — the same days "N this week" counts.
                    // Seven fixed cells: their figures keep an everyday size
                    // and never break — "10" read "1" over "0" at the largest
                    // text (final walk C3, Android #17); the cards below grow.
                    CappedFontScale(EVERYDAY_MAX_FONT_SCALE) {
                    Row(
                        Modifier.padding(top = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val strip = thisWeekDays(today)
                        strip.forEach { date ->
                            val selected = date == selectedDay
                            val isToday = date == today
                            val hasEvents = events.any { occDate(it) == date }
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(
                                        when {
                                            selected -> EV.navy
                                            isToday -> EV.gold.copy(alpha = 0.12f)
                                            else -> Color.Transparent
                                        },
                                    )
                                    .clickable { selectedDay = date; segment = EVENTS_TAB_TODAY }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)).uppercase(),
                                    style = evInter(11, FontWeight.SemiBold, 0.8f),
                                    color = if (selected) Color.White.copy(alpha = 0.65f) else EV.tertiary,
                                    maxLines = 1, softWrap = false,
                                )
                                Text(
                                    date.dayOfMonth.toString(),
                                    style = evSerif(16, FontWeight.SemiBold),
                                    color = if (selected) Color.White else EV.navy,
                                    maxLines = 1, softWrap = false,
                                )
                                Box(
                                    Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape)
                                        .background(if (hasEvents) EV.gold else Color.Transparent),
                                )
                            }
                        }
                    }
                    }
                }

                if (quiet) {
                    // A quiet week (§6.5): one calm card, then the calendar and
                    // check-in as two compact rows — nothing to filter.
                    QuietWeekCard()
                    QuietEntries(onOpenCalendar = onOpenCalendar, onOpenAttendance = onOpenAttendance)
                } else {
                    // CALENDAR — a white card: the tab's one navy feature card is
                    // the church attendance card below (§8.1 rule 1), as iOS.
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
                            .border(1.dp, EV.border, RoundedCornerShape(22.dp))
                            .clickable { onOpenCalendar() },
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(org.nuruplace.member.ui.theme.Nuru.goldTint)
                                    .border(1.dp, EV.gold.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Lucide.CalendarDays, null, tint = EV.navy, modifier = Modifier.size(22.dp)) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("CALENDAR", style = evInter(11, FontWeight.Bold, 1.4f), color = EV.overline)
                                Text("All events & calendar", style = evSerif(15, FontWeight.SemiBold), color = EV.navy)
                                Text(
                                    org.nuruplace.member.util.ZeroCounts.join("See the whole month at a glance", org.nuruplace.member.util.ZeroCounts.count(upcoming, "upcoming", "upcoming")),
                                    style = evInter(11), color = EV.secondary,
                                )
                            }
                            Icon(Lucide.ChevronRight, null, tint = EV.secondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    // CHURCH ATTENDANCE — a paper card, as the CALENDAR card above
                    // (owner, 2026-10-08, option A): navy is the church's voice
                    // and each tab's next step only.
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
                            .border(1.dp, EV.border, RoundedCornerShape(22.dp))
                            .clickable { onOpenAttendance() },
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(org.nuruplace.member.ui.theme.Nuru.goldTint)
                                    .border(1.dp, EV.gold.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Lucide.ScanQrCode, null, tint = EV.navy, modifier = Modifier.size(22.dp)) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("CHURCH ATTENDANCE", style = evInter(11, FontWeight.Bold, 1.4f), color = EV.overline)
                                Text("Check in to a service", style = evSerif(15, FontWeight.SemiBold), color = EV.navy)
                                Text(
                                    "Scan the QR at church · see your streak",
                                    style = evInter(11), color = EV.secondary,
                                )
                            }
                            Icon(Lucide.ChevronRight, null, tint = EV.secondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Segmented control
                    val segTodayCount = events.count { occDate(it) == selectedDay }
                    val segUpcomingCount = upcoming
                    val segRsvpCount = events.count { rsvpMap.containsKey(it.occurrenceId) }
                    Row(
                        Modifier.fillMaxWidth().clip(Capsule).background(EV.white)
                            .border(1.dp, EV.border, Capsule).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SegmentPill("Today", segTodayCount, segment == 0, Modifier.weight(1f)) { segment = 0 }
                        SegmentPill("Upcoming", segUpcomingCount, segment == 1, Modifier.weight(1f)) { segment = 1 }
                        SegmentPill("My RSVPs", segRsvpCount, segment == 2, Modifier.weight(1f)) { segment = 2 }
                    }

                    // Search and the filters stand over something to search
                    // (final walk C16; iOS): over an empty day they offered
                    // nothing. A search or filter in use stays, so the way back
                    // is there.
                    val segmentCount = when (segment) { 0 -> segTodayCount; 1 -> segUpcomingCount; else -> segRsvpCount }
                    if (eventsShowsSearchAndFilters(segmentCount, query, category)) {
                    // Search field
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EV.white)
                            .border(1.dp, EV.border, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Lucide.Search, null, tint = EV.tertiary, modifier = Modifier.size(14.dp))
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                textStyle = evInter(13).copy(color = EV.navy),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (query.isBlank()) {
                                        Text("Search events by name or place", style = evInter(13), color = EV.tertiary)
                                    }
                                    inner()
                                },
                            )
                        }
                    }

                    // Category chips
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("All", "Worship", "Cell", "Leaders", "Youth").forEach { c ->
                            val on = category == c
                            // Rule 6: navy when selected, for every category.
                            val color = EV.navy
                            Text(
                                c,
                                style = evInter(12, if (on) FontWeight.SemiBold else FontWeight.Medium),
                                color = if (on) Color.White else EV.secondary,
                                modifier = Modifier.clip(Capsule)
                                    .background(if (on) color else EV.white)
                                    .then(if (on) Modifier else Modifier.border(1.dp, EV.border, Capsule))
                                    .clickable { category = c }
                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                            )
                        }
                    }
                    }

                    // "Today's gatherings" header
                    val sectionTitle = when {
                        selectedDay == today && segment == 0 -> "Today's gatherings"
                        segment == 1 -> "Coming up"
                        segment == 2 -> "Your RSVPs"
                        else -> "Events on " + org.nuruplace.member.util.NuruDates.day(selectedDay, today)
                    }
                    // One route to the calendar (§9.6 rule 3; final walk C5,
                    // Android #23): the CALENDAR card above. This header's
                    // "All & calendar ›" and the empty card's "View calendar"
                    // were the second and third.
                    Text(sectionTitle, style = evSerif(18, FontWeight.SemiBold), color = EV.ink)

                    // Filtered list
                    val filtered = events.filter { occ ->
                        val d = occDate(occ)
                        val segOk = when (segment) {
                            0 -> d == selectedDay
                            1 -> d != null && !d.isBefore(today)
                            else -> rsvpMap.containsKey(occ.occurrenceId)
                        }
                        val catOk = category == "All" || occ.category?.equals(category, ignoreCase = true) == true
                        val q = query.trim()
                        val queryOk = q.isBlank() ||
                            occ.title.contains(q, ignoreCase = true) ||
                            (occ.location?.contains(q, ignoreCase = true) == true)
                        segOk && catOk && queryOk
                    }

                    if (filtered.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
                                .border(1.dp, EV.border, RoundedCornerShape(22.dp))
                                .padding(vertical = 32.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(EV.tile),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Lucide.CalendarDays, null, tint = EV.gold, modifier = Modifier.size(22.dp)) }
                            val (emptyTitle, emptyLine) = eventsEmptyWords(segment, hasFilters = category != "All" || query.isNotBlank())
                            Text(emptyTitle, style = evInter(12, FontWeight.SemiBold), color = EV.navy)
                            Text(emptyLine, style = evInter(11), color = EV.tertiary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    } else {
                        // A series' later dates fold under its first card (final
                        // walk C16; iOS): one weekly service filled a screen per Sunday.
                        evGrouped(filtered).forEach { g ->
                            EvCardView(g.first, onClick = { onOpenEvent(g.first.occurrenceId, g.first.endAt) }, myRsvp = rsvpMap[g.first.occurrenceId])
                            if (g.more.isNotEmpty()) {
                                EvMoreDates(g.more, rsvpMap) { occ -> onOpenEvent(occ.occurrenceId, occ.endAt) }
                            }
                        }
                    }
                }

                // ── SERIES YOU FOLLOW · MORE SERIES ───────────────────────────
                // "Series you follow" holds only what the member follows; the
                // rest sit under "More series", each with + Follow (§7.4 #7).
                // Which series are offered is the server's call: repeating
                // series that meet again, plus any the member follows, to
                // unfollow (§9.2 #11).
                val (followed, more) = seriesRails(series)
                // A follow the server didn't take says so (§7.4) — it was silent.
                val toggleFollow: (EventSeries) -> Unit = { s ->
                    scope.launch {
                        noticeOnFailure(eventsContext, lead = if (s.following) "Couldn't unfollow that series." else "Couldn't follow that series.") {
                            Net.client.api.toggleSeriesFollow(s.seriesId)
                        }?.let { reload() }
                    }
                }
                if (followed.isNotEmpty()) {
                    SeriesRail("SERIES YOU FOLLOW", followed, onSeeAll = onOpenCalendar, onToggle = toggleFollow)
                }
                if (more.isNotEmpty()) {
                    SeriesRail("MORE SERIES", more, onSeeAll = if (followed.isEmpty()) onOpenCalendar else null, onToggle = toggleFollow)
                }

                // ── ANNOUNCEMENTS ─────────────────────────────────────────────
                if (anns.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
                            .border(1.dp, EV.border, RoundedCornerShape(22.dp)).padding(16.dp),
                    ) {
                        Row(
                            Modifier.padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Lucide.Megaphone, null, tint = EV.overline, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            EVOverline("ANNOUNCEMENTS")
                            Spacer(Modifier.weight(1f))
                            Text(
                                "See all", style = evInter(11, FontWeight.SemiBold), color = EV.gold, // a text action is gold (§8.1 rule 4)
                                modifier = Modifier.clickable { onOpenAnnouncements() },
                            )
                        }
                        anns.forEachIndexed { i, a ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(EV.border))
                            Row(
                                Modifier.clickable { onOpenAnnouncement(a.announcementId) }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(EV.gold.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val img = a.primaryImageUrl
                                    if (img != null) {
                                        AsyncImage(
                                            model = img, contentDescription = null,
                                            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(),
                                        )
                                    } else {
                                        Icon(Lucide.Megaphone, null, tint = EV.gold, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            a.title, style = evInter(13, FontWeight.Medium), color = EV.navy,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        Icon(Lucide.BadgeCheck, null, tint = EV.gold, modifier = Modifier.size(14.dp))
                                    }
                                    Text(
                                        org.nuruplace.member.ui.components.LightMarkdown.plain(a.body), style = evInter(11), color = EV.secondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(relTime(a.sentAt), style = evInter(11), color = EV.tertiary)
                                    if (!a.opened) Box(Modifier.size(6.dp).clip(CircleShape).background(EV.gold))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Local building blocks
// ─────────────────────────────────────────────────────────────────────────────

/** The quiet week's one calm card (EXPERIENCE.md §6.5). */
@Composable
private fun QuietWeekCard() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
            .border(1.dp, EV.border, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(EV.tile),
            contentAlignment = Alignment.Center,
        ) { Icon(Lucide.CalendarDays, null, tint = EV.gold, modifier = Modifier.size(18.dp)) }
        Text(
            EVENTS_QUIET_LINE,
            style = evInter(12), color = EV.secondary, modifier = Modifier.weight(1f),
        )
    }
}

/** The calendar and check-in entries as two compact rows — the quiet week's
 *  doors to the whole calendar and to a service's check-in (§6.5). */
/** The empty list's words — no button: the calendar has its one route, the
 *  CALENDAR card above (final walk C5). The same words as iOS
 *  EventsViewModel.emptyTitle / emptyCaption. */
internal fun eventsEmptyWords(segment: Int, hasFilters: Boolean): Pair<String, String> = when {
    hasFilters -> "No events match" to "Try a different search or category."
    segment == 2 -> "No RSVPs yet" to "Tap an event to say you'll be there."
    else -> "Nothing on this day" to "The calendar above holds every gathering."
}

@Composable
private fun QuietEntries(onOpenCalendar: () -> Unit, onOpenAttendance: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
            .border(1.dp, EV.border, RoundedCornerShape(22.dp)),
    ) {
        QuietEntryRow(Lucide.CalendarDays, "All events & calendar", onOpenCalendar)
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(EV.border))
        QuietEntryRow(Lucide.ScanQrCode, "Check in to a service", onOpenAttendance)
    }
}

@Composable
private fun QuietEntryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(EV.goldTile),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = EV.navy, modifier = Modifier.size(18.dp)) }
        // A menu entry is a control row (§8.1 rule 3): Inter 14 medium.
        Text(title, style = NuruType.controlTitle, color = EV.navy, modifier = Modifier.weight(1f))
        Icon(Lucide.ChevronRight, null, tint = EV.tertiary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun HeaderPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.clip(Capsule).background(EV.white).border(1.dp, EV.border, Capsule)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = EV.gold, modifier = Modifier.size(14.dp))
        Text(text, style = evInter(11, FontWeight.Bold), color = EV.secondary)
    }
}

@Composable
private fun SegmentPill(label: String, count: Int, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(Capsule).background(if (on) EV.navy else Color.Transparent)
            .clickable { onClick() }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = evInter(11, FontWeight.SemiBold), color = if (on) Color.White else EV.secondary)
        // No zero counts (§7.4 #9): a tab with nothing in it shows no badge.
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.clip(Capsule).background(if (on) EV.gold else EV.tile).padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(count.toString(), style = evInter(11, FontWeight.Bold), color = EV.navy)
            }
        }
    }
}

@Composable
private fun FollowButton(following: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(Capsule).background(if (following) EV.navy else Color.Transparent)
            .then(if (following) Modifier else Modifier.border(1.dp, EV.border, Capsule))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (following) Lucide.Check else Lucide.Plus, null,
            tint = if (following) Color.White else EV.navy, modifier = Modifier.size(14.dp),
        )
        Text(
            if (following) "Following" else "Follow",
            style = evInter(11, FontWeight.SemiBold), color = if (following) Color.White else EV.navy,
        )
    }
}

/** One rail of series — its overline, then each series with its line and the
 *  follow toggle. [onSeeAll]: the "See all" link, on the first rail shown. */
@Composable
private fun SeriesRail(
    overline: String,
    series: List<EventSeries>,
    onSeeAll: (() -> Unit)?,
    onToggle: (EventSeries) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
            .border(1.dp, EV.border, RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Row(
            Modifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Sparkles, null, tint = EV.overline, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            EVOverline(overline)
            Spacer(Modifier.weight(1f))
            onSeeAll?.let { open ->
                Text(
                    "See all", style = evInter(11, FontWeight.SemiBold), color = EV.gold, // a text action is gold (§8.1 rule 4)
                    modifier = Modifier.clickable { open() },
                )
            }
        }
        series.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(EV.border))
            Row(
                Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                        .background(org.nuruplace.member.ui.theme.Nuru.goldTint),
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.CalendarSync, contentDescription = null, tint = EV.navy, modifier = Modifier.size(18.dp)) }
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Wraps to two lines, never cut (§8.1 rule 9) — "Welcome to
                        // Ablaze Worship E…". A series is a thing: the content row
                        // title, Fraunces 15 (rule 3; iOS f09a46c the same).
                        Text(
                            s.title, style = org.nuruplace.member.ui.theme.NuruType.rowTitle, color = EV.navy,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (s.following && s.newCount > 0) {
                            Box(
                                Modifier.clip(Capsule).background(EV.gold.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("${s.newCount} new", style = evInter(11, FontWeight.Bold), color = EV.chipText)
                            }
                        }
                    }
                    Text(
                        seriesLine(s.cadence, s.nextAt?.let { evTime(it) }), style = evInter(11), color = EV.secondary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                FollowButton(s.following) { onToggle(s) }
            }
        }
    }
}
