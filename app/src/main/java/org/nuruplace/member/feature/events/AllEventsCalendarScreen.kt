// All events & calendar — port of the iOS CalendarView. A month grid (nav + today
// pill, weekday header, day cells with category dots) sits above the upcoming/day
// list. Selecting a day filters the list; otherwise it shows all upcoming events.
// Palette, cream sub-header, event card and date helpers come from EventsShared.kt.
package org.nuruplace.member.feature.events

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.util.isoPlusDays
import org.nuruplace.member.util.todayIso
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun AllEventsCalendarScreen(onBack: () -> Unit, onOpenEvent: (String, String?) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(EV.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        AsyncContent(load = { Net.client.api.calendar(todayIso(), isoPlusDays(60)).data }) { events: List<CalendarOccurrence>, _ ->
            AllEventsCalendarBody(events = events, onBack = onBack, onOpenEvent = onOpenEvent)
        }
    }
}

@Composable
private fun AllEventsCalendarBody(
    events: List<CalendarOccurrence>,
    onBack: () -> Unit,
    onOpenEvent: (String, String?) -> Unit,
) {
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now()

    val monthYearFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    val monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
    val monthDayFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    val upcomingCount = events.count { (evZdt(it.startAt)?.toLocalDate() ?: today) >= today }

    EvSubHeader(
        eyebrow = "EVENTS",
        title = "All events & calendar",
        subtitle = "$upcomingCount upcoming · ${visibleMonth.format(monthYearFmt)}",
        onBack = onBack,
    )

    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Month-grid card ─────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(EV.white)
                .border(1.dp, EV.border, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(visibleMonth.format(monthFmt), style = evSerif(18, FontWeight.SemiBold), color = EV.navy)
                Spacer(Modifier.size(6.dp))
                Text(visibleMonth.year.toString(), style = evSerif(18, FontWeight.Normal), color = EV.ink300)
                Spacer(Modifier.weight(1f))
                // TODAY pill
                Box(
                    Modifier
                        .clip(Capsule)
                        .background(EV.gold.copy(alpha = 0.10f))
                        .clickable {
                            visibleMonth = YearMonth.now()
                            selectedDate = null
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("TODAY", style = evInter(9, FontWeight.Bold, 1f), color = EV.overline) }
                Spacer(Modifier.size(8.dp))
                NavButton(Icons.Filled.ChevronLeft) { visibleMonth = visibleMonth.minusMonths(1) }
                Spacer(Modifier.size(8.dp))
                NavButton(Icons.Filled.ChevronRight) { visibleMonth = visibleMonth.plusMonths(1) }
            }

            // Weekday header
            Row(Modifier.fillMaxWidth()) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(d, style = evInter(9, FontWeight.Bold, 0.8f), color = EV.ink300)
                    }
                }
            }

            // Day grid
            val firstOfMonth = visibleMonth.atDay(1)
            val leadingBlanks = firstOfMonth.dayOfWeek.value % 7 // Mon=1..Sun=7 → Sunday-first
            val daysInMonth = visibleMonth.lengthOfMonth()
            val cells: List<Int?> = List(leadingBlanks) { null } + (1..daysInMonth).map { it }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    // pad the final row so trailing cells keep their column width
                    val padded = week + List(7 - week.size) { null }
                    padded.forEach { cell ->
                        Box(Modifier.weight(1f)) {
                            if (cell == null) {
                                Box(Modifier.height(42.dp))
                            } else {
                                DayCell(
                                    day = cell,
                                    visibleMonth = visibleMonth,
                                    selectedDate = selectedDate,
                                    today = today,
                                    events = events,
                                    onSelect = { date -> selectedDate = if (selectedDate == date) null else date },
                                )
                            }
                        }
                    }
                }
            }

            // Divider
            Box(Modifier.fillMaxWidth().height(1.dp).background(EV.border))

            // Legend
            val legendCats = events.mapNotNull { it.category }.distinct().ifEmpty { listOf("worship") }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                legendCats.forEach { cat ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(evCategory(cat)))
                        Text(
                            cat.replaceFirstChar { c -> c.uppercase() },
                            style = evInter(10, FontWeight.Medium),
                            color = EV.secondary,
                        )
                    }
                }
            }
        }

        // ── List header ─────────────────────────────────────────────────────
        val shown = if (selectedDate != null) {
            events.filter { evZdt(it.startAt)?.toLocalDate() == selectedDate }
        } else {
            events
                .filter { (evZdt(it.startAt)?.toLocalDate() ?: today) >= today }
                .sortedBy { it.startAt }
        }
        val listCount = shown.size

        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selectedDate != null) selectedDate!!.format(monthDayFmt).uppercase() else "UPCOMING",
                style = evInter(10, FontWeight.Bold, 1.5f),
                color = EV.overline,
            )
            Spacer(Modifier.weight(1f))
            Text("$listCount events", style = evInter(10, FontWeight.SemiBold), color = EV.tertiary)
        }

        // ── List ────────────────────────────────────────────────────────────
        if (shown.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(EV.white)
                    .border(1.dp, EV.border, RoundedCornerShape(22.dp))
                    .padding(vertical = 32.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(EV.gold.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CalendarMonth, null, tint = EV.gold, modifier = Modifier.size(22.dp)) }
                Text("Nothing scheduled", style = evInter(12, FontWeight.SemiBold), color = EV.navy)
                Text("Pick another day to see what's on.", style = evInter(11), color = EV.tertiary)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                shown.forEach { occ ->
                    EvCardView(occ, onClick = { onOpenEvent(occ.occurrenceId, occ.endAt) })
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    visibleMonth: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    events: List<CalendarOccurrence>,
    onSelect: (LocalDate) -> Unit,
) {
    val date = visibleMonth.atDay(day)
    val isSel = date == selectedDate
    val isToday = date == today
    val dayEvents = events.filter { evZdt(it.startAt)?.toLocalDate() == date }
    val cats = dayEvents.mapNotNull { it.category }.distinct().take(3)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                when {
                    isSel -> Modifier.background(EV.selectedDay)
                    isToday -> Modifier.border(1.5.dp, EV.gold, RoundedCornerShape(14.dp))
                    else -> Modifier
                },
            )
            .clickable { onSelect(date) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                day.toString(),
                style = evInter(12, if (isSel || isToday) FontWeight.Bold else FontWeight.SemiBold),
                color = when {
                    isSel -> Color.White
                    isToday -> EV.overline
                    else -> EV.navy
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (cats.isEmpty()) {
                    Box(Modifier.size(4.dp))
                } else {
                    cats.forEach { c ->
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSel) Color.White.copy(alpha = 0.9f) else evCategory(c)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(EV.tile)
            .border(1.dp, EV.border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = EV.secondary, modifier = Modifier.size(14.dp)) }
}
