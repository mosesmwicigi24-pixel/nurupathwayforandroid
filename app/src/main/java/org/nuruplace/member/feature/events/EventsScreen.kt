// Events — upcoming occurrences (GET /calendar window) and the event detail with
// RSVP (going / maybe / can't-go) + who's-going roster. Ports the iOS EventsView +
// EventDetailView (buzz-feed posts deferred).
package org.nuruplace.member.feature.events

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.data.net.EventDetail
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RsvpBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.fmtEventTime
import org.nuruplace.member.util.isoPlusDays
import org.nuruplace.member.util.todayIso

@Composable
fun EventsScreen(onBack: () -> Unit, onOpenEvent: (String) -> Unit) {
    AsyncContent(load = { Net.client.api.calendar(todayIso(), isoPlusDays(60)).data }) { events: List<CalendarOccurrence>, _ ->
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Events", kicker = "What's on", onBack = onBack)
            if (events.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No upcoming events.", style = NuruType.body, color = Nuru.ink600)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(events, key = { it.occurrenceId }) { e ->
                        NuruCard(padding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.clickable { onOpenEvent(e.occurrenceId) }) {
                            e.primaryImageUrl?.let {
                                AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(topStart = Radii.card, topEnd = Radii.card)))
                            }
                            Column(Modifier.padding(Spacing.base)) {
                                e.category?.let { Kicker(it) }
                                Text(e.title, style = NuruType.cardTitle, color = Nuru.ink)
                                Text(fmtEventTime(e.startAt), style = NuruType.caption, color = Nuru.goldLo)
                                e.location?.let { Text("📍 $it", style = NuruType.micro, color = Nuru.ink600) }
                                if (e.going > 0) Text("${e.going} going", style = NuruType.micro, color = Nuru.ink400)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventDetailScreen(eventId: String, onBack: () -> Unit, onCheckIn: (String) -> Unit = {}) {
    AsyncContent(key = eventId, load = { Net.client.api.event(eventId) }) { e: EventDetail, reload ->
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        fun setRsvp(status: String) {
            if (!busy) {
                busy = true
                scope.launch { try { Net.client.api.rsvp(eventId, RsvpBody(status)); reload() } catch (_: Exception) {} finally { busy = false } }
            }
        }
        Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
            e.primaryImageUrl?.let {
                AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().height(220.dp))
            } ?: ScreenHeader(e.title, kicker = e.category ?: "Event", onBack = onBack)

            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                if (e.primaryImageUrl != null) {
                    Text(e.title, style = NuruType.title, color = Nuru.ink)
                }
                Text(fmtEventTime(e.occursAt), style = NuruType.heading, color = Nuru.goldLo)
                e.location?.let { Text("📍 $it", style = NuruType.body, color = Nuru.ink600) }

                // RSVP
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    RsvpChip("Going", "going", e.myRsvp, ::setRsvp, Modifier.weight(1f))
                    RsvpChip("Maybe", "maybe", e.myRsvp, ::setRsvp, Modifier.weight(1f))
                    RsvpChip("Can't go", "declined", e.myRsvp, ::setRsvp, Modifier.weight(1f))
                }
                Text("${e.rsvpCounts.going ?: 0} going · ${e.rsvpCounts.maybe ?: 0} maybe", style = NuruType.micro, color = Nuru.ink400)

                // Check-in — opens the QR scanner while the event is live, else a locked notice.
                if (isEventLive(e.occursAt)) {
                    org.nuruplace.member.ui.components.PrimaryButton("Check in", onClick = { onCheckIn(eventId) })
                } else {
                    Text("🔒 Check-in opens when the event is live", style = NuruType.caption, color = Nuru.ink400)
                }

                e.description?.let { Text(it, style = NuruType.bodyLg, color = Nuru.ink) }

                e.attendees?.takeIf { it.isNotEmpty() }?.let { roster ->
                    Kicker("Who's going")
                    roster.forEach { Text("• ${it.fullName}", style = NuruType.body, color = Nuru.ink) }
                }

                // Buzz feed ("Who's coming" wall)
                EventBuzz(eventId)
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }
}

@Composable
private fun EventBuzz(eventId: String) {
    org.nuruplace.member.ui.components.AsyncContent(key = "buzz-$eventId", load = { Net.client.api.eventPosts(eventId).data }) { posts, reload ->
        val scope = rememberCoroutineScope()
        var draft by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        Column {
            Kicker("Buzz")
            Spacer(Modifier.height(Spacing.sm))
            androidx.compose.material3.OutlinedTextField(
                draft, { draft = it }, placeholder = { Text("Say you're coming…") },
                shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            org.nuruplace.member.ui.components.PrimaryButton("Post", loading = busy, enabled = draft.isNotBlank(), onClick = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            Net.client.api.createEventPost(eventId, org.nuruplace.member.data.net.EventPostBody(java.util.UUID.randomUUID().toString(), draft.trim(), java.util.UUID.randomUUID().toString()))
                            draft = ""; reload()
                        } catch (_: Exception) {} finally { busy = false }
                    }
                }
            })
            Spacer(Modifier.height(Spacing.sm))
            posts.forEach { p ->
                NuruCard {
                    Text(p.authorName, style = NuruType.label, color = Nuru.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    p.body?.let { Text(it, style = NuruType.body, color = Nuru.ink) }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        BuzzReact("🎉 ${p.cheerCount}", p.myReaction == "cheer") { react(scope, eventId, p.postId, "cheer", reload) }
                        BuzzReact("❤️ ${p.loveCount}", p.myReaction == "love") { react(scope, eventId, p.postId, "love", reload) }
                    }
                }
            }
        }
    }
}

/** Live window for check-in: from 1h before start to 4h after (no end time on
 *  the wire). Matches the iOS "check-in opens when the event is live" gate. */
private fun isEventLive(occursAt: String): Boolean {
    val start = runCatching { java.time.Instant.parse(occursAt) }.getOrNull() ?: return false
    val now = java.time.Instant.now()
    return now.isAfter(start.minusSeconds(3600)) && now.isBefore(start.plusSeconds(4 * 3600))
}

private fun react(scope: kotlinx.coroutines.CoroutineScope, eventId: String, postId: String, kind: String, reload: () -> Unit) {
    scope.launch { try { Net.client.api.reactToEventPost(eventId, postId, org.nuruplace.member.data.net.EventReactBody(kind)); reload() } catch (_: Exception) {} }
}

@Composable
private fun BuzzReact(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Radii.pill))
            .background(if (on) Nuru.goldTint else Nuru.surface)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, style = NuruType.micro, color = if (on) Nuru.goldChipText else Nuru.ink600) }
}

@Composable
private fun RsvpChip(label: String, value: String, current: String?, onSet: (String) -> Unit, modifier: Modifier = Modifier) {
    val on = current == value
    Box(
        modifier
            .clip(RoundedCornerShape(Radii.button))
            .background(if (on) Nuru.primaryButton else androidx.compose.ui.graphics.SolidColor(Nuru.surface))
            .clickable { onSet(value) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NuruType.cardCta, color = if (on) Nuru.onNavy else Nuru.ink, fontWeight = FontWeight.SemiBold)
    }
}
