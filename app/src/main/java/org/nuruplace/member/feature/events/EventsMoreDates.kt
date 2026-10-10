// A weekly service is one card (final walk C16; the C3 walk's E9): a series'
// later dates fold under its first card as compact MORE DATES rows — the day,
// the time and how far off, the member's own answer, and the way in. One
// weekly service filled about a screen per Sunday. iOS EventsGrouping and
// EventMoreDates, word for word.
package org.nuruplace.member.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.ui.icons.Lucide
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType

/** One series in a gathering list: its soonest date, and the later ones. */
data class EvGroup(val first: CalendarOccurrence, val more: List<CalendarOccurrence>)

/** Each series' later dates folded under its first. Groups keep the order of
 *  their first date; inside a group the dates keep the list's (soonest-first)
 *  order. A gathering with no series id stands alone. Pure. */
fun evGrouped(list: List<CalendarOccurrence>): List<EvGroup> {
    val order = mutableListOf<String>()
    val bySeries = LinkedHashMap<String, MutableList<CalendarOccurrence>>()
    for (o in list) {
        val key = o.seriesId.ifBlank { "occurrence:" + o.occurrenceId }
        if (key !in bySeries) order += key
        bySeries.getOrPut(key) { mutableListOf() } += o
    }
    return order.mapNotNull { k -> bySeries[k]?.let { all -> EvGroup(all.first(), all.drop(1)) } }
}

/** Search and the filters stand over a tab with gatherings, or while one is in
 *  use (so it can be cleared) — never over an empty day (final walk C16; iOS
 *  EventsViewModel.showsSearchAndFilters). */
fun eventsShowsSearchAndFilters(segmentCount: Int, search: String, category: String): Boolean =
    segmentCount > 0 || search.isNotBlank() || category != "All"

/** A MORE DATES row's line: "9:00 AM – 1:00 PM · In 10 days". */
fun evMoreDateLine(occ: CalendarOccurrence): String =
    listOf(evTimeRange(occ.startAt, occ.endAt), evCountdown(occ.startAt)).filter { it.isNotBlank() }.joinToString(" · ")

/** The later dates of one series, under its first card. */
@Composable
fun EvMoreDates(dates: List<CalendarOccurrence>, rsvps: Map<String, String>, onOpen: (CalendarOccurrence) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(EV.white).border(1.dp, EV.border, shape).padding(16.dp),
    ) {
        Text("MORE DATES", style = evInter(11, FontWeight.Bold, 1.4f), color = EV.eyebrowGold, modifier = Modifier.padding(bottom = 4.dp))
        dates.forEachIndexed { i, occ ->
            MoreDateRow(occ, rsvps[occ.occurrenceId]) { onOpen(occ) }
            if (i < dates.size - 1) {
                Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(EV.border))
            }
        }
    }
}

@Composable
private fun MoreDateRow(occ: CalendarOccurrence, rsvp: String?, onOpen: () -> Unit) {
    val day = evDateFull(occ.startAt)
    val line = evMoreDateLine(occ)
    val answer = when (rsvp) { "going" -> "GOING"; "maybe" -> "MAYBE"; else -> null }
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(vertical = 10.dp)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(occ.title.ifBlank { null }, day, line, answer?.lowercase()?.let { "you're $it" }).joinToString(", ")
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.goldChipBg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(evWeekdayShort(occ.startAt), style = evInter(11, FontWeight.Bold, 0.8f), color = Nuru.goldChipText)
            Text(evDayNum(occ.startAt), style = evSerif(15, FontWeight.SemiBold), color = EV.navy)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(day, style = NuruType.rowTitle, color = EV.navy)
            if (line.isNotBlank()) Text(line, style = evInter(11), color = EV.tertiary)
        }
        when (answer) {
            "GOING" -> Row(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(Brush.horizontalGradient(listOf(EV.goldLight, EV.gold)))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Lucide.Check, null, tint = EV.navy, modifier = Modifier.size(14.dp))
                Text("GOING", style = evInter(11, FontWeight.Bold, 1f), color = EV.navy)
            }
            "MAYBE" -> Text(
                "MAYBE", style = evInter(11, FontWeight.Bold, 1f), color = Color(0xFF92400E),
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFEF3C7)).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.width(0.dp))
        Icon(Lucide.ChevronRight, null, tint = EV.ink300, modifier = Modifier.size(14.dp))
    }
}
