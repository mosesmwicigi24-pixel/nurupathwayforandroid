// Event detail — port of iOS EventDetailView. Hero + meta card + about + roster +
// RSVP + "Who's coming" buzz feed + live check-in. Uses the shared `EV` palette /
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
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

/** Pill / capsule corner. */
private val Capsule = RoundedCornerShape(999.dp)

/** now ∈ [start − 1h, start + 4h] — the check-in window. */
private fun isEventLive(occursAt: String?): Boolean {
    val start = occursAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    val now = Instant.now()
    return !now.isBefore(start.minusSeconds(3600)) && !now.isAfter(start.plusSeconds(4 * 3600))
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
        val setRsvp: (String) -> Unit = { status ->
            scope.launch {
                val payload = kotlinx.serialization.json.buildJsonObject {
                    put("event_id", kotlinx.serialization.json.JsonPrimitive(eventId))
                    put("status", kotlinx.serialization.json.JsonPrimitive(status))
                }
                Net.client.offline.runOrQueue("event_rsvps", "set", payload) {
                    Net.client.api.rsvp(eventId, RsvpBody(status))
                }
                reload()
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(EV.paper)
                .imePadding()
                .verticalScroll(rememberScrollState()),
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

                RsvpCard(e, setRsvp)

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
                ) { Icon(Icons.Filled.ChevronLeft, "Back", tint = Color.White, modifier = Modifier.size(22.dp)) }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onShare() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Share, "Share", tint = Color.White, modifier = Modifier.size(17.dp)) }
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
                                .background(evCategory(c).copy(alpha = 0.9f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text(c.uppercase(), style = evInter(10, FontWeight.Bold, 1.4f), color = Color.White) }
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
                                Text("LIVE", style = evInter(10, FontWeight.Bold, 1.4f), color = Color.White)
                            }
                        }
                    }
                }
                Text(e.title, style = evSerif(24, FontWeight.SemiBold, -0.72f), color = Color.White)
            }
        }
    }
}

// ── Meta card ─────────────────────────────────────────────────────────────────

@Composable
private fun MetaCard(e: EventDetail, endAt: String?, onAddToCalendar: () -> Unit, onShare: () -> Unit) {
    val accent = evCategory(e.category)
    val peopleLabel = (e.rsvpCounts.going ?: 0).let { if (it == 1) "1 person" else "$it people" }
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
                MetaTile(Modifier.weight(1f), Icons.Filled.CalendarMonth, "DATE", evDateFull(e.occursAt), accent)
                MetaTile(Modifier.weight(1f), Icons.Filled.Schedule, "TIME", if (endAt != null) evTimeRange(e.occursAt, endAt) else evTime(e.occursAt), accent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaTile(Modifier.weight(1f), Icons.Filled.Place, "WHERE", e.location ?: "—", accent)
                MetaTile(Modifier.weight(1f), Icons.Filled.Person, "GOING", peopleLabel, accent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(Modifier.weight(1f), Icons.Filled.CalendarMonth, "Add to calendar", onAddToCalendar)
            ActionButton(Modifier.weight(1f), Icons.Filled.Share, "Share", onShare)
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
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp)) }
        Column {
            Text(label, style = evInter(9, FontWeight.Bold, 1.3f), color = EV.tertiary)
            Text(value, style = evInter(11, FontWeight.SemiBold), color = EV.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        style = evInter(10, FontWeight.SemiBold),
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
private fun RsvpCard(e: EventDetail, setRsvp: (String) -> Unit) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RsvpOption(Modifier.weight(1f), "Going", "going", EV.going, e.myRsvp, setRsvp)
            RsvpOption(Modifier.weight(1f), "Maybe", "maybe", EV.maybe, e.myRsvp, setRsvp)
            RsvpOption(Modifier.weight(1f), "Can't", "declined", EV.declined, e.myRsvp, setRsvp)
        }
        if (e.myRsvp == "going") {
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Check, null, tint = EV.goingText, modifier = Modifier.size(13.dp))
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

// ── Buzz card ("Who's coming") ────────────────────────────────────────────────

@Composable
private fun BuzzCard(eventId: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(EV.white)
            .border(1.dp, EV.borderSoft, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
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

            fun setPicked(raw: ByteArray?) {
                if (raw == null) return
                downscaleJpeg(raw, 1600)?.let { (jpeg, bmp) ->
                    pickedBytes = jpeg
                    pickedPreview = bmp.asImageBitmap()
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
                    val bodyText = draft.trim()
                    val bytes = pickedBytes
                    scope.launch {
                        runCatching {
                            var imageUrl: String? = null
                            if (bytes != null) {
                                val part = MultipartBody.Part.createFormData(
                                    "file", "post.jpg", bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()),
                                )
                                imageUrl = Net.client.api.uploadPostImage(part).url.ifBlank { null }
                            }
                            Net.client.api.createEventPost(
                                eventId,
                                EventPostBody(UUID.randomUUID().toString(), bodyText.ifBlank { null }, imageUrl, UUID.randomUUID().toString()),
                            )
                        }
                        draft = ""; pickedBytes = null; pickedPreview = null; busy = false
                        reloadBuzz()
                    }
                }
            }
            val react: (String, String) -> Unit = { postId, kind ->
                scope.launch {
                    runCatching { Net.client.api.reactToEventPost(eventId, postId, EventReactBody(kind)) }
                    reloadBuzz()
                }
            }

            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Group, null, tint = EV.overline, modifier = Modifier.size(12.dp))
                    EVOverline("Who's coming")
                }
                Box(
                    Modifier
                        .clip(Capsule)
                        .background(EV.buzzing)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).clip(Capsule).background(Color.White))
                        Text(
                            if (posts.isEmpty()) "Buzzing" else "Buzzing · ${posts.size}",
                            style = evInter(10, FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }

            // Composer — a roomy card: "You" header, a big text area, an optional
            // photo preview, then the attach (+) · flame · Post action row.
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(EV.composerTop, EV.tile)))
                    .border(1.dp, EV.gold.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(999.dp))
                            .background(Brush.linearGradient(listOf(EV.navyInk, Color(0xFF163655)))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                    Text("You", style = evInter(11, FontWeight.Bold), color = EV.ink.copy(alpha = 0.8f))
                }

                Box(
                    Modifier.fillMaxWidth().heightIn(min = 76.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.7f))
                        .border(1.dp, EV.gold.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                ) {
                    if (draft.isBlank()) {
                        Text("Hype the room — say you're coming 🔥", style = evInter(14), color = EV.placeholder)
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = evInter(14).copy(color = EV.ink),
                        maxLines = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                pickedPreview?.let { bmp ->
                    Box(Modifier.fillMaxWidth()) {
                        Image(
                            bitmap = bmp, contentDescription = "Attached photo", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(14.dp)),
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { pickedBytes = null; pickedPreview = null },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(15.dp)) }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(Color.White)
                                .border(1.dp, EV.navyBase.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                                .clickable { attachMenu = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Add, "Add a photo", tint = EV.navyInk, modifier = Modifier.size(18.dp)) }
                        DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Take photo") },
                                leadingIcon = { Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    attachMenu = false
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) launchCamera() else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Choose photo") },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(20.dp)) },
                                onClick = {
                                    attachMenu = false
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                        }
                    }
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(999.dp)).background(Color.White)
                            .border(1.dp, EV.navyBase.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                            .clickable { draft += "🔥" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.LocalFireDepartment, "Add fire", tint = EV.goldDetail, modifier = Modifier.size(17.dp)) }

                    Spacer(Modifier.weight(1f))

                    val faded = (draft.isBlank() && pickedBytes == null) || busy
                    Row(
                        Modifier.clip(Capsule).background(EV.goldCta).height(40.dp)
                            .then(if (faded) Modifier else Modifier.clickable { post() })
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(if (busy) "Posting" else "Post", style = evInter(12, FontWeight.Bold), color = EV.ink.copy(alpha = if (faded) 0.55f else 1f))
                        if (!busy) Icon(Icons.AutoMirrored.Filled.Send, "Post", tint = EV.ink.copy(alpha = if (faded) 0.55f else 1f), modifier = Modifier.size(15.dp))
                    }
                }
            }

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
                    ) { Text("GOING", style = evInter(8, FontWeight.Bold, 1f), color = EV.goingText) }
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
        Text(count.toString(), style = evInter(10, FontWeight.Bold), color = if (on) EV.goldDeep else EV.secondary)
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
            Icon(Icons.Filled.QrCodeScanner, null, tint = EV.ink, modifier = Modifier.size(16.dp))
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
            Icon(Icons.Filled.Lock, null, tint = EV.gold, modifier = Modifier.size(14.dp))
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
