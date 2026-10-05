// Event detail — port of iOS EventDetailView. Hero + meta card + about + roster +
// RSVP + "The wall" buzz feed + live check-in. Uses the shared `EV` palette /
// primitives from EventsShared.kt (same package, no import). Server-authoritative
// data via MemberApi; RSVP is an offline-queued write, buzz posts/reactions go
// straight through. The hero shows the full image un-cropped (height follows the
// image's natural aspect, capped at 60% of the screen and letterboxed on the navy
// gradient); the content column sits flush below it.
package org.nuruplace.member.feature.events

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.EventAttendee
import org.nuruplace.member.data.net.EventDetail
import org.nuruplace.member.data.net.EventPost
import org.nuruplace.member.data.net.EventPostBody
import org.nuruplace.member.data.net.EventReactBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RsvpBody
import org.nuruplace.member.data.offline.runOrQueue
import org.nuruplace.member.ui.components.AsyncContent
import java.time.Instant
import java.util.UUID
import org.nuruplace.member.ui.components.noticeOnFailure
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.icons.Lucide

/** Pill / capsule corner. */
private val Capsule = RoundedCornerShape(999.dp)

/** now ∈ [start − 1h, start + 4h] — the check-in window. */
private fun isEventLive(occursAt: String?): Boolean {
    val start = occursAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    val now = Instant.now()
    return !now.isBefore(start.minusSeconds(3600)) && !now.isAfter(start.plusSeconds(4 * 3600))
}

/** What one RSVP came to (EXPERIENCE.md §4): sent, waiting in the offline
 *  queue, or refused — never an exception out of the tap. */
internal sealed interface RsvpOutcome {
    object Sent : RsvpOutcome
    data class Queued(val status: String) : RsvpOutcome
    data class Failed(val line: String) : RsvpOutcome
}

/** Send one RSVP. [send] returns null when the write went to the offline
 *  queue (§1.7); a refusal (422 "RSVP is not enabled for this event", a 404,
 *  a 5xx) becomes [RsvpOutcome.Failed] in [failureLine]'s words. A cancel
 *  (the member left) is not a failure. */
internal suspend fun submitRsvp(status: String, send: suspend () -> Unit?, failureLine: (Throwable) -> String): RsvpOutcome =
    try {
        if (send() == null) RsvpOutcome.Queued(status) else RsvpOutcome.Sent
    } catch (c: kotlin.coroutines.cancellation.CancellationException) {
        throw c
    } catch (e: Exception) {
        RsvpOutcome.Failed(failureLine(e))
    }

/** First letters of up to two words, uppercase. */
private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

@Composable
fun EventDetailScreen(eventId: String, endAt: String? = null, onBack: () -> Unit, onCheckIn: (String) -> Unit = {}) {
    AsyncContent(key = eventId, load = { Net.client.api.event(eventId) }) { e: EventDetail, reload ->
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val shareIntent: () -> Unit = {
            runCatching {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, e.title)
                    putExtra(Intent.EXTRA_TEXT, e.title)
                }
                context.startActivity(Intent.createChooser(send, e.title))
            }
        }
        val addToCalendar: () -> Unit = {
            runCatching {
                val start = e.occursAt.let { runCatching { Instant.parse(it) }.getOrNull() }?.toEpochMilli()
                val insert = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, e.title)
                    e.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                    e.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                    if (start != null) {
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                        val end = endAt?.let { runCatching { Instant.parse(it) }.getOrNull() }?.toEpochMilli()
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end ?: (start + 2 * 3600 * 1000))
                    }
                }
                context.startActivity(insert)
            }
        }
        // An RSVP (EXPERIENCE.md §4, §7.4): one at a time; a refusal — "RSVP is
        // not enabled for this event", a 404, a 5xx — is said under the
        // choices in the server's own words (it used to escape the launch and
        // crash the app); offline, the choice waits in the queue (§1.7) and
        // the card says so instead of looking ignored.
        var rsvpBusy by remember(eventId) { mutableStateOf(false) }
        var rsvpQueued by remember(eventId) { mutableStateOf<String?>(null) }
        var rsvpError by remember(eventId) { mutableStateOf<String?>(null) }
        val setRsvp: (String) -> Unit = { status ->
            if (!rsvpBusy) {
                rsvpBusy = true
                rsvpError = null
                scope.launch {
                    val payload = kotlinx.serialization.json.buildJsonObject {
                        put("event_id", kotlinx.serialization.json.JsonPrimitive(eventId))
                        put("status", kotlinx.serialization.json.JsonPrimitive(status))
                    }
                    val outcome = submitRsvp(
                        status,
                        send = { Net.client.offline.runOrQueue("event_rsvps", "set", payload) { Net.client.api.rsvp(eventId, RsvpBody(status)) } },
                        failureLine = { ApiException.saveFailureLine(it, context) },
                    )
                    rsvpBusy = false
                    when (outcome) {
                        RsvpOutcome.Sent -> { rsvpQueued = null; reload() }
                        is RsvpOutcome.Queued -> rsvpQueued = outcome.status
                        is RsvpOutcome.Failed -> rsvpError = outcome.line
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(EV.paper)
                .imePadding()
                .verticalScroll(rememberScrollState()).imePadding(),
        ) {
            EventHero(e, onBack, shareIntent)

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MetaCard(e, endAt, onAddToCalendar = addToCalendar, onShare = shareIntent)

                e.description?.takeIf { it.isNotBlank() }?.let { AboutCard(it) }

                // Gallery strip — wire serves images=[primary,…gallery]; the hero
                // already shows images[0], so only extra shots earn the strip.
                e.images.drop(1).takeIf { it.isNotEmpty() }?.let { GalleryStrip(it) }

                e.attendees?.takeIf { it.isNotEmpty() }?.let { RosterCard(it) }

                RsvpCard(e, setRsvp, queued = rsvpQueued, busy = rsvpBusy, error = rsvpError)

                BuzzCard(eventId)

                CheckInFooter(e.occursAt, eventId, onCheckIn)

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun EventHero(e: EventDetail, onBack: () -> Unit, onShare: () -> Unit) {
    // Natural aspect (w/h) of the loaded hero image — null until Coil reports it.
    // Once known, the hero grows to show the FULL image un-cropped: height follows
    // the intrinsic aspect, capped at 60% of the screen for very tall posters
    // (letterboxed on the navy gradient, never cropped). While loading / no image
    // we keep the original 16:11 placeholder frame.
    // First NON-BLANK of the primary image and the gallery's first shot. A
    // bare null-check let primaryImageUrl = "" through — Coil rendered
    // nothing and the head fell back to plain navy even when the list row
    // was showing this event's photo (owner's screenshot, 2026-08-24; same
    // fix as iOS EventDetailView.imageUrl).
    val heroUrl = listOf(e.primaryImageUrl, e.images.firstOrNull())
        .firstOrNull { !it.isNullOrBlank() }
    var imageAspect by remember(heroUrl) { mutableStateOf<Float?>(null) }
    val maxHeroHeight = LocalConfiguration.current.screenHeightDp.dp * 0.6f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val heroHeight = imageAspect?.let { ar -> (maxWidth / ar).coerceAtMost(maxHeroHeight) }
        Box(
            Modifier
                .fillMaxWidth()
                .animateContentSize()
                .then(if (heroHeight != null) Modifier.height(heroHeight) else Modifier.aspectRatio(16f / 11f))
                .background(Brush.linearGradient(listOf(EV.navy700, EV.navy, evCategory(e.category)))),
        ) {
            if (heroUrl != null) {
                AsyncImage(
                    model = heroUrl,
                    contentDescription = e.title,
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        val size = state.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f) imageAspect = size.width / size.height
                    },
                    modifier = Modifier.matchParentSize(),
                )
            }
            // Scrim — kept for back/share + title/pill legibility over the image.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(EV.navyBase.copy(alpha = 0.55f), EV.navyBase.copy(alpha = 0.10f), EV.navyBase.copy(alpha = 0.85f)),
                        ),
                    ),
            )
            // Top chrome
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.ChevronLeft, "Back", tint = Color.White, modifier = Modifier.size(22.dp)) }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onShare() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.Share2, "Share", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            // Bottom overlay
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.category?.takeIf { it.isNotBlank() }?.let { c ->
                        Box(
                            Modifier
                                .clip(Capsule)
                                .background(EV.white)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text(c.uppercase(), style = evInter(11, FontWeight.Bold, 1.4f), color = EV.navy) }
                    }
                    if (isEventLive(e.occursAt)) {
                        Box(
                            Modifier
                                .clip(Capsule)
                                .background(EV.going)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(4.dp).clip(Capsule).background(Color.White))
                                Text("LIVE", style = evInter(11, FontWeight.Bold, 1.4f), color = Color.White)
                            }
                        }
                    }
                }
                Text(e.title, style = evSerif(26, FontWeight.SemiBold, -0.72f), color = Color.White)
            }
        }
    }
}

// ── Meta card ─────────────────────────────────────────────────────────────────

@Composable
private fun MetaCard(e: EventDetail, endAt: String?, onAddToCalendar: () -> Unit, onShare: () -> Unit) {
    val accent = evCategory(e.category)
    val going = e.rsvpCounts.going ?: 0
    val peopleLabel = if (going == 1) "1 person" else "$going people"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(EV.white)
            .border(1.dp, EV.borderSoft, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaTile(Modifier.weight(1f), Lucide.CalendarDays, "DATE", evDateFull(e.occursAt), accent)
                MetaTile(Modifier.weight(1f), Lucide.Clock4, "TIME", if (endAt != null) evTimeRange(e.occursAt, endAt) else evTime(e.occursAt), accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // The venue is a DOOR, not a caption (owner, 2026-08-24):
                // tapping it opens Maps so a newcomer gets directions.
                val mapsContext = LocalContext.current
                MetaTile(
                    Modifier.weight(1f).then(
                        e.location?.takeIf { it.isNotBlank() }?.let { loc ->
                            Modifier.clickable {
                                runCatching {
                                    mapsContext.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(loc))),
                                    )
                                }
                            }
                        } ?: Modifier,
                    ),
                    Lucide.MapPin, "WHERE", e.location?.takeIf { it.isNotBlank() } ?: "Not set", accent,
                )
                // No zero counts (EXPERIENCE.md §7.4 #9): nobody going yet
                // leaves WHERE the row — the RSVP card below asks.
                if (going > 0) MetaTile(Modifier.weight(1f), Lucide.User, "GOING", peopleLabel, accent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Modifier.weight(1f), Lucide.CalendarDays, "Add to calendar", onAddToCalendar)
            ActionButton(Modifier.weight(1f), Lucide.Share2, "Share", onShare)
        }
    }
}

@Composable
private fun MetaTile(modifier: Modifier, icon: ImageVector, label: String, value: String, accent: Color) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(EV.tile)
            .border(1.dp, EV.borderFaint, RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(12.dp))
                // A row icon: navy on gold tint (rule 7) — it took the
                // category's hue.
                .background(org.nuruplace.member.ui.theme.Nuru.goldTint),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = EV.navy, modifier = Modifier.size(14.dp)) }
        Column {
            Text(label, style = evInter(11, FontWeight.Bold, 1.3f), color = EV.tertiary)
            // Wraps rather than cuts (§8.1 rule 9): "The Good News Mi…" was the
            // venue a newcomer needed to find.
            Text(value, style = evInter(11, FontWeight.SemiBold), color = EV.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionButton(modifier: Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(EV.tile)
            .border(1.dp, EV.border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(icon, null, tint = EV.goldDetail, modifier = Modifier.size(14.dp))
        Text(label, style = evInter(12, FontWeight.Bold), color = EV.ink)
        Spacer(Modifier.weight(1f))
    }
}

// ── About card ────────────────────────────────────────────────────────────────

@Composable
private fun AboutCard(description: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(EV.white)
            .border(1.dp, EV.borderSoft, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        EVOverline("About this gathering")
        Spacer(Modifier.height(8.dp))
        Text(description, style = evInter(13).copy(lineHeight = 19.sp), color = EV.body)
    }
}

// ── Gallery strip ─────────────────────────────────────────────────────────────

@Composable
private fun GalleryStrip(urls: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(EV.white)
            .border(1.dp, EV.borderSoft, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        EVOverline("Gallery")
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(urls.size) { i ->
                AsyncImage(
                    model = urls[i],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(148.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            }
        }
    }
}

// ── Roster card ───────────────────────────────────────────────────────────────

@Composable
private fun RosterCard(attendees: List<EventAttendee>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(EV.white)
            .border(1.dp, EV.borderSoft, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EVOverline("Who's going")
            Text("${attendees.size} going", style = evInter(11, FontWeight.Bold), color = EV.ink)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            attendees.forEach { a ->
                Column(
                    Modifier.width(52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(EV.avatarAccent(a.userId), EV.avatarAccent(a.userId).copy(alpha = 0.71f)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (a.avatarUrl != null) {
                            AsyncImage(
                                model = a.avatarUrl,
                                contentDescription = a.fullName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(999.dp)),
                            )
                        } else {
                            Text(initials(a.fullName), style = evInter(14, FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        a.fullName.substringBefore(' '),
                        style = evInter(11, FontWeight.SemiBold),
                        color = EV.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── RSVP card ─────────────────────────────────────────────────────────────────

@Composable
private fun RsvpCard(e: EventDetail, setRsvp: (String) -> Unit, queued: String? = null, busy: Boolean = false, error: String? = null) {
    // A choice waiting in the offline queue shows as chosen; the server's own
    // answer otherwise.
    val mine = queued ?: e.myRsvp
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(EV.white)
            .border(1.dp, EV.borderSoft, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        EVOverline("Will you be there?")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.alpha(if (busy) 0.6f else 1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RsvpOption(Modifier.weight(1f), "Going", "going", EV.going, mine, setRsvp)
            RsvpOption(Modifier.weight(1f), "Maybe", "maybe", EV.maybe, mine, setRsvp)
            RsvpOption(Modifier.weight(1f), "Can't", "declined", EV.declined, mine, setRsvp)
        }
        if (error != null) {
            Text(error, style = evInter(12), color = Nuru.danger, modifier = Modifier.padding(top = 10.dp))
        } else if (queued != null) {
            Text(
                "You're offline — we'll send this when you're back.",
                style = evInter(12), color = EV.secondary, modifier = Modifier.padding(top = 10.dp),
            )
        } else if (e.myRsvp == "going") {
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Check, null, tint = EV.goingText, modifier = Modifier.size(14.dp))
                Text("Saved · we'll remind you the day before.", style = evInter(11, FontWeight.SemiBold), color = EV.goingText)
            }
        }
    }
}

@Composable
private fun RsvpOption(
    modifier: Modifier,
    label: String,
    status: String,
    tint: Color,
    myRsvp: String?,
    setRsvp: (String) -> Unit,
) {
    val on = myRsvp == status
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) tint else Color.Transparent)
            .then(if (on) Modifier else Modifier.border(1.dp, EV.border, RoundedCornerShape(16.dp)))
            .clickable { setRsvp(status) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = evInter(12, FontWeight.Bold), color = if (on) Color.White else EV.secondary)
    }
}

// ── Buzz card ("The wall") ────────────────────────────────────────────────────

@Composable
private fun BuzzCard(eventId: String) {
    // The section header (and its Buzzing pill) sits on the PAGE, not on the
    // card (owner, 2026-08-24) — matching how every other section announces
    // itself. The card holds only the voices and the chat bar.
    Column(Modifier.fillMaxWidth()) {
        AsyncContent(
            key = "buzz-$eventId",
            load = { Net.client.api.eventPosts(eventId).data },
        ) { posts: List<EventPost>, reloadBuzz ->
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            var draft by remember { mutableStateOf("") }
            var busy by remember { mutableStateOf(false) }
            var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
            var pickedPreview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            var attachMenu by remember { mutableStateOf(false) }
            // A post that didn't reach the wall keeps its words and photo and
            // says why (§7.4); its ids and an uploaded photo's address stay
            // with it, so posting again can't post it twice or upload twice.
            var postError by remember { mutableStateOf<String?>(null) }
            var postIds by remember { mutableStateOf<Pair<String, String>?>(null) }
            var uploadedUrl by remember { mutableStateOf<String?>(null) }

            fun setPicked(raw: ByteArray?) {
                if (raw == null) return
                downscaleJpeg(raw, 1600)?.let { (jpeg, bmp) ->
                    pickedBytes = jpeg
                    pickedPreview = bmp.asImageBitmap()
                    uploadedUrl = null
                }
            }
            val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
                if (uri != null) {
                    setPicked(runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull())
                }
            }
            var cameraTarget by remember { mutableStateOf<Uri?>(null) }
            val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                if (ok) {
                    setPicked(cameraTarget?.let { u -> runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() })
                }
            }
            fun launchCamera() {
                val file = File.createTempFile("evt_", ".jpg", context.cacheDir)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraTarget = uri
                cameraLauncher.launch(uri)
            }
            val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) launchCamera()
            }

            val post: () -> Unit = {
                if ((draft.isNotBlank() || pickedBytes != null) && !busy) {
                    busy = true
                    postError = null
                    val bodyText = draft.trim()
                    val bytes = pickedBytes
                    val ids = postIds ?: (UUID.randomUUID().toString() to UUID.randomUUID().toString()).also { postIds = it }
                    scope.launch {
                        try {
                            var imageUrl: String? = uploadedUrl
                            if (bytes != null && imageUrl == null) {
                                val part = MultipartBody.Part.createFormData(
                                    "file", "post.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()),
                                )
                                imageUrl = Net.client.api.uploadPostImage(part).url.ifBlank { null }
                                uploadedUrl = imageUrl
                            }
                            // This call answers with a Response, so a refusal
                            // never throws — it read as posted and the words
                            // were wiped. Its status decides now.
                            val res = Net.client.api.createEventPost(
                                eventId,
                                EventPostBody(ids.first, bodyText.ifBlank { null }, imageUrl, ids.second),
                            )
                            if (!res.isSuccessful) throw retrofit2.HttpException(res)
                            if (draft.trim() == bodyText) draft = ""
                            pickedBytes = null; pickedPreview = null; uploadedUrl = null; postIds = null
                            reloadBuzz()
                        } catch (c: kotlin.coroutines.cancellation.CancellationException) {
                            throw c
                        } catch (e: Exception) {
                            postError = ApiException.failureLine("Couldn't post that.", e, context)
                        } finally {
                            busy = false
                        }
                    }
                }
            }
            val react: (String, String) -> Unit = { postId, kind ->
                scope.launch {
                    noticeOnFailure(context) { Net.client.api.reactToEventPost(eventId, postId, EventReactBody(kind)) }
                        ?.let { reloadBuzz() }
                }
            }

            // Header — "The wall" (it was "Who's coming", beside the roster's
            // "Who's going"), and "Buzzing" only when someone has posted
            // (EXPERIENCE.md §7.4 #9): an empty wall isn't buzzing.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Lucide.Users, null, tint = EV.overline, modifier = Modifier.size(14.dp))
                    EVOverline("The wall")
                }
                if (posts.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(Capsule)
                            .background(EV.buzzing)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).clip(Capsule).background(Color.White))
                            Text(
                                "Buzzing · ${posts.size}",
                                style = evInter(11, FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            Column(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(EV.white)
                    .border(1.dp, EV.borderSoft, RoundedCornerShape(22.dp))
                    .padding(16.dp),
            ) {
            // Chat order (owner's revision, 2026-08-24 — iOS parity): the
            // room's voices come FIRST; your line to add sits BELOW them as a
            // compact chat bar — attach, the field, flame, send — the same
            // grammar as the message composer in Chat.
            if (posts.isEmpty()) {
                Text(
                    "Be the first to share a moment.",
                    style = evInter(13),
                    color = EV.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            } else {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    posts.forEach { p -> PostRow(p) { kind -> react(p.postId, kind) } }
                }
            }

            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(EV.composerTop, EV.tile)))
                    .border(1.dp, EV.gold.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                postError?.let { Text(it, style = evInter(12), color = Nuru.danger) }
                pickedPreview?.let { bmp ->
                    Box(Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = bmp, contentDescription = "Attached photo", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(14.dp)),
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { pickedBytes = null; pickedPreview = null; uploadedUrl = null },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Lucide.X, "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(Color.White)
                                .border(1.dp, EV.navyBase.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                                .clickable { attachMenu = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Lucide.Plus, "Add a photo", tint = EV.navyInk, modifier = Modifier.size(18.dp)) }
                        DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Take photo") },
                                leadingIcon = { Icon(Lucide.Camera, null, modifier = Modifier.size(22.dp)) },
                                onClick = {
                                    attachMenu = false
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) launchCamera() else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Choose photo") },
                                leadingIcon = { Icon(Lucide.Images, null, modifier = Modifier.size(22.dp)) },
                                onClick = {
                                    attachMenu = false
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                        }
                    }
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(19.dp)).background(Color.White.copy(alpha = 0.85f))
                            .border(1.dp, EV.gold.copy(alpha = 0.20f), RoundedCornerShape(19.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        if (draft.isBlank()) {
                            Text("Hype the room — say you're coming 🔥", style = evInter(14), color = EV.placeholder, maxLines = 1)
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = evInter(14).copy(color = EV.ink),
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(Color.White)
                            .border(1.dp, EV.navyBase.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                            .clickable { draft += "🔥" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Lucide.Flame, "Add fire", tint = EV.goldDetail, modifier = Modifier.size(18.dp)) }
                    val faded = (draft.isBlank() && pickedBytes == null) || busy
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(999.dp)).background(EV.goldCta)
                            .then(if (faded) Modifier else Modifier.clickable { post() }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Lucide.Send, "Post",
                            tint = EV.ink.copy(alpha = if (faded) 0.55f else 1f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun PostRow(p: EventPost, onReact: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EV.tile)
            .border(1.dp, EV.navyBase.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(EV.avatarAccent(p.authorUserId)),
            contentAlignment = Alignment.Center,
        ) { Text(initials(p.authorName), style = evInter(12, FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center) }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.authorName, style = evInter(12, FontWeight.Bold), color = EV.ink)
                if (p.rsvpStatus == "going") {
                    Box(
                        Modifier
                            .clip(Capsule)
                            .background(EV.going.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) { Text("GOING", style = evInter(11, FontWeight.Bold, 1f), color = EV.goingText) }
                }
            }
            p.body?.let { Text(it, style = evInter(13), color = EV.body) }
            // The attached photo — full width at its own aspect (FillWidth never
            // crops; the card grows to fit it).
            p.imageUrl?.let { url ->
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = url,
                    contentDescription = "Photo",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
            }
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReactChip("🎉", p.cheerCount, p.myReaction == "cheer") { onReact("cheer") }
                ReactChip("❤️", p.loveCount, p.myReaction == "love") { onReact("love") }
            }
        }
    }
}

@Composable
private fun ReactChip(emoji: String, count: Int, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(Capsule)
            .background(if (on) EV.gold.copy(alpha = 0.14f) else EV.white)
            .border(1.dp, if (on) EV.gold.copy(alpha = 0.45f) else EV.border, Capsule)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(emoji, fontSize = 12.sp)
        // The chip is the way to react; its count only once there is one
        // (no zero chips, EXPERIENCE.md §7.4 #9).
        if (count > 0) Text(count.toString(), style = evInter(11, FontWeight.Bold), color = if (on) EV.goldDeep else EV.secondary)
    }
}

// ── Check-in footer ───────────────────────────────────────────────────────────

@Composable
private fun CheckInFooter(occursAt: String, eventId: String, onCheckIn: (String) -> Unit) {
    if (isEventLive(occursAt)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EV.goldTile)
                .clickable { onCheckIn(eventId) }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.ScanQrCode, null, tint = EV.ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Check in", style = evInter(13, FontWeight.Bold), color = EV.ink)
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = EV.navyBase.copy(alpha = 0.18f),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f),
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                    )
                }
                .clip(RoundedCornerShape(16.dp))
                .background(EV.tile)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Lock, null, tint = EV.gold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text("Check-in opens when the event is live", style = evInter(13, FontWeight.SemiBold), color = Color(0xFF6A7686))
        }
    }
}

/** Decode, aspect-fit downscale (longest side ≤ maxDim), re-encode JPEG — keeps
 *  buzz-photo uploads well under the 5 MB cap. Returns (jpegBytes, bitmap) for
 *  upload + preview, or null if the bytes don't decode to an image. */
private fun downscaleJpeg(bytes: ByteArray, maxDim: Int): Pair<ByteArray, Bitmap>? {
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val longest = maxOf(src.width, src.height)
    val scaled = if (longest > maxDim && longest > 0) {
        val s = maxDim.toFloat() / longest
        Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true)
    } else {
        src
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return out.toByteArray() to scaled
}
