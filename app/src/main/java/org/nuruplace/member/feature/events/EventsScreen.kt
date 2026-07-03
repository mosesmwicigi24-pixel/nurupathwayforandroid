// Events tab — the iOS "Gathered together" screen. Cream header + week strip + dark
// calendar card + segmented control + search + category chips + gatherings list, then
// "Series you follow" and "Announcements" rails. Shared chrome (palette, cream header,
// event card, date fns) lives in EventsShared.kt (same package — no import needed).
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.data.net.EventSeries
import org.nuruplace.member.data.net.MyAnnouncement
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.util.isoPlusDays
import org.nuruplace.member.util.relTime
import org.nuruplace.member.util.todayIso
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun EventsScreen(
    onOpenEvent: (String) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenAnnouncement: (String) -> Unit,
    onOpenAnnouncements: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    AsyncContent(load = {
        val cal = runCatching { Net.client.api.calendar(todayIso(), isoPlusDays(60)).data }.getOrDefault(emptyList())
        val series = runCatching { Net.client.api.eventSeries().data }.getOrDefault(emptyList())
        val anns = runCatching { Net.client.api.myAnnouncements().data }.getOrDefault(emptyList())
        Triple(cal, series, anns)
    }) { (events, series, anns), reload ->
        val scope = rememberCoroutineScope()
        val today = remember { LocalDate.now(EV_ZONE) }
        var selectedDay by remember { mutableStateOf(today) }
        var segment by remember { mutableStateOf(0) } // 0=Today, 1=Upcoming, 2=My RSVPs
        var category by remember { mutableStateOf("All") }
        var query by remember { mutableStateOf("") }

        // date helpers over events
        fun occDate(occ: CalendarOccurrence): LocalDate? = evZdt(occ.startAt)?.toLocalDate()
        val thisWeek = events.count { val d = occDate(it); d != null && !d.isBefore(today) && d.isBefore(today.plusDays(7)) }
        val going = 0
        val upcoming = events.count { val d = occDate(it); d != null && !d.isBefore(today) }

        Column(
            Modifier
                .fillMaxSize()
                .background(EV.paper)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 1. Header ──────────────────────────────────────────────────────
            EvCreamHeaderBox {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("📅 EVENTS", style = evInter(11, FontWeight.Bold, 2f), color = EV.eyebrowGold)
                            Text("Gathered together", style = evSerif(28, FontWeight.SemiBold), color = EV.navy)
                            Text(
                                "Today · " + today.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)) + " · East Africa Time",
                                style = evInter(11), color = EV.secondary,
                            )
                        }
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(EV.white)
                                .border(1.dp, EV.border, RoundedCornerShape(16.dp)).clickable { onOpenNotifications() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Notifications, "Notifications", tint = EV.navy, modifier = Modifier.size(19.dp))
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(9.dp).size(8.dp).clip(CircleShape).background(EV.gold),
                            )
                        }
                    }
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeaderPill("$thisWeek this week", Icons.Filled.CalendarMonth)
                        HeaderPill("$going you're going", Icons.Filled.Check)
                    }
                }
            }

            // ── 2. Body ────────────────────────────────────────────────────────
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
                        Text(
                            "TODAY", style = evInter(10, FontWeight.Bold, 1f), color = EV.navy,
                            modifier = Modifier.clickable { selectedDay = today },
                        )
                    }
                    Row(
                        Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val strip = (-2..11).map { today.plusDays(it.toLong()) }
                        strip.forEach { date ->
                            val selected = date == selectedDay
                            val isToday = date == today
                            val hasEvents = events.any { occDate(it) == date }
                            Column(
                                Modifier.width(44.dp).clip(RoundedCornerShape(16.dp))
                                    .background(
                                        when {
                                            selected -> EV.navy
                                            isToday -> EV.gold.copy(alpha = 0.12f)
                                            else -> Color.Transparent
                                        },
                                    )
                                    .clickable { selectedDay = date }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.ENGLISH)).uppercase(),
                                    style = evInter(9, FontWeight.SemiBold, 0.8f),
                                    color = if (selected) Color.White.copy(alpha = 0.65f) else EV.tertiary,
                                )
                                Text(
                                    date.dayOfMonth.toString(),
                                    style = evSerif(16, FontWeight.SemiBold),
                                    color = if (selected) Color.White else EV.navy,
                                )
                                Box(
                                    Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape)
                                        .background(if (hasEvents) EV.gold else Color.Transparent),
                                )
                            }
                        }
                    }
                }

                // CALENDAR dark card
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.navyCard)
                        .clickable { onOpenCalendar() },
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(EV.goldTile),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.CalendarMonth, null, tint = EV.navy, modifier = Modifier.size(22.dp)) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("CALENDAR", style = evInter(9, FontWeight.Bold, 1.5f), color = EV.goldLight)
                            Text("All events & calendar", style = evSerif(15, FontWeight.SemiBold), color = Color.White)
                            Text(
                                "See the whole month at a glance · $upcoming upcoming",
                                style = evInter(11), color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }

                // Segmented control
                val segTodayCount = events.count { occDate(it) == selectedDay }
                val segUpcomingCount = upcoming
                val segRsvpCount = 0
                Row(
                    Modifier.fillMaxWidth().clip(Capsule).background(EV.white)
                        .border(1.dp, EV.border, Capsule).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SegmentPill("Today", segTodayCount, segment == 0, Modifier.weight(1f)) { segment = 0 }
                    SegmentPill("Upcoming", segUpcomingCount, segment == 1, Modifier.weight(1f)) { segment = 1 }
                    SegmentPill("My RSVPs", segRsvpCount, segment == 2, Modifier.weight(1f)) { segment = 2 }
                }

                // Search field
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EV.white)
                        .border(1.dp, EV.border, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Search, null, tint = EV.tertiary, modifier = Modifier.size(15.dp))
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
                        val color = if (c == "All") EV.navy else evCategory(c)
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

                // "Today's gatherings" header
                val sectionTitle = when {
                    selectedDay == today && segment == 0 -> "Today's gatherings"
                    segment == 1 -> "Coming up"
                    segment == 2 -> "Your RSVPs"
                    else -> "Events on " + selectedDay.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sectionTitle, style = evSerif(18, FontWeight.SemiBold), color = EV.ink)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clickable { onOpenCalendar() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text("All & calendar", style = evInter(11, FontWeight.SemiBold), color = EV.navy)
                        Icon(Icons.Filled.ChevronRight, null, tint = EV.navy, modifier = Modifier.size(12.dp))
                    }
                }

                // Filtered list
                val filtered = events.filter { occ ->
                    val d = occDate(occ)
                    val segOk = when (segment) {
                        0 -> d == selectedDay
                        1 -> d != null && !d.isBefore(today)
                        else -> false
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
                        ) { Icon(Icons.Filled.CalendarMonth, null, tint = EV.gold, modifier = Modifier.size(20.dp)) }
                        Text("Nothing on this day", style = evInter(12, FontWeight.SemiBold), color = EV.navy)
                        Text(
                            "Browse the full calendar to find a gathering.",
                            style = evInter(11), color = EV.tertiary,
                        )
                        Row(
                            Modifier.clip(Capsule).background(EV.navy).clickable { onOpenCalendar() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Filled.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Text("View calendar", style = evInter(11, FontWeight.SemiBold), color = Color.White)
                        }
                    }
                } else {
                    filtered.forEach { occ ->
                        EvCardView(occ, onClick = { onOpenEvent(occ.occurrenceId) })
                    }
                }

                // ── SERIES YOU FOLLOW ─────────────────────────────────────────
                if (series.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
                            .border(1.dp, EV.border, RoundedCornerShape(22.dp)).padding(16.dp),
                    ) {
                        Row(
                            Modifier.padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = EV.overline, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            EVOverline("SERIES YOU FOLLOW")
                            Spacer(Modifier.weight(1f))
                            Text(
                                "See all", style = evInter(11, FontWeight.SemiBold), color = EV.navy,
                                modifier = Modifier.clickable { onOpenCalendar() },
                            )
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
                                        .background(evCategory(s.category).copy(alpha = 0.12f))
                                        .border(1.dp, evCategory(s.category).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                )
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            s.title, style = evInter(13, FontWeight.Medium), color = EV.navy,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (s.following && s.newCount > 0) {
                                            Box(
                                                Modifier.clip(Capsule).background(EV.gold.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text("${s.newCount} new", style = evInter(8, FontWeight.Bold), color = EV.chipText)
                                            }
                                        }
                                    }
                                    Text(
                                        cadenceLine(s), style = evInter(11), color = EV.secondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                FollowButton(s.following) {
                                    scope.launch {
                                        try {
                                            Net.client.api.toggleSeriesFollow(s.seriesId)
                                            reload()
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                            Icon(Icons.Filled.Campaign, null, tint = EV.overline, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            EVOverline("ANNOUNCEMENTS")
                            Spacer(Modifier.weight(1f))
                            Text(
                                "See all", style = evInter(11, FontWeight.SemiBold), color = EV.navy,
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
                                        Icon(Icons.Filled.Campaign, null, tint = EV.gold, modifier = Modifier.size(15.dp))
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
                                        Icon(Icons.Filled.Verified, null, tint = EV.gold, modifier = Modifier.size(12.dp))
                                    }
                                    Text(
                                        a.body, style = evInter(11), color = EV.secondary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(relTime(a.sentAt), style = evInter(9), color = EV.tertiary)
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

@Composable
private fun HeaderPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.clip(Capsule).background(EV.white).border(1.dp, EV.border, Capsule)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = EV.gold, modifier = Modifier.size(11.dp))
        Text(text, style = evInter(10, FontWeight.Bold), color = EV.secondary)
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
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.clip(Capsule).background(if (on) EV.gold else EV.tile).padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(count.toString(), style = evInter(9, FontWeight.Bold), color = EV.navy)
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
            if (following) Icons.Filled.Check else Icons.Filled.Add, null,
            tint = if (following) Color.White else EV.navy, modifier = Modifier.size(12.dp),
        )
        Text(
            if (following) "Following" else "Follow",
            style = evInter(11, FontWeight.SemiBold), color = if (following) Color.White else EV.navy,
        )
    }
}

private fun cadenceLine(s: EventSeries): String =
    listOfNotNull(s.cadence.ifBlank { null }, s.nextAt?.let { evTime(it) }).joinToString(" · ")
