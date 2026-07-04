// Discipleship Hub — the student's warm home for their discipleship relationship,
// composed from the single read-aggregation GET /me/discipleship. Port of the iOS
// DiscipleshipHubView: discipler card, hero "Message" CTA (1:1 DM), "Where you
// are" progression, "Your growth" scores, recent reflections, and meeting notes.
// Pure reflection of server state (§1.9) — nothing here advances a level.
package org.nuruplace.member.feature.discipleship

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Discipleship
import org.nuruplace.member.data.net.DmBody
import org.nuruplace.member.data.net.HubDiscipler
import org.nuruplace.member.data.net.HubNote
import org.nuruplace.member.data.net.HubProgression
import org.nuruplace.member.data.net.HubReflection
import org.nuruplace.member.data.net.HubScores
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif
import org.nuruplace.member.util.relTime
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardShape = RoundedCornerShape(24.dp)
private val ControlShape = RoundedCornerShape(14.dp)
private val Capsule = RoundedCornerShape(999.dp)
private val Green = Color(0xFF16A34A)
private val Red = Color(0xFFDC2626)

@Composable
fun DiscipleshipHubScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    var hub by remember { mutableStateOf<Discipleship?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var startingDm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        runCatching { Net.client.api.discipleship() }
            .onSuccess { hub = it.data }
            .onFailure { error = it.message ?: "Couldn't load your discipleship." }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(GrowPal.coolPaper)) {
        // Navy header — back circle + serif title (matches PrayerWallDetail anatomy).
        Row(
            Modifier.fillMaxWidth().background(GrowPal.navy)
                .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Text("Discipleship", style = gSerif(20, FontWeight.SemiBold), color = Color.White)
        }

        val h = hub
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                loading && h == null -> item { LoadingSkeleton() }
                h == null && error != null -> item {
                    ErrorCard(error ?: "Couldn't load your discipleship.") { reloadKey++ }
                }
                h?.discipler == null -> item { EmptyCard() }
                else -> {
                    val d = h.discipler!!
                    item { DisciplerCard(d) }
                    item {
                        if (h.canMessage) {
                            MessageHero(
                                firstName = firstName(d.fullName),
                                busy = startingDm,
                            ) {
                                val existing = h.dmConversationId
                                if (existing != null) {
                                    onOpenChat(existing)
                                } else if (!startingDm) {
                                    startingDm = true
                                    scope.launch {
                                        runCatching { Net.client.api.createDm(DmBody(d.userId)) }
                                            .onSuccess { onOpenChat(it.conversationId) }
                                        startingDm = false
                                    }
                                }
                            }
                        } else {
                            MinorNote(firstName(d.fullName))
                        }
                    }
                    item { WhereYouAreCard(h.progression) }
                    if (hasAnyScore(h.scores)) item { GrowthCard(h.scores) }
                    if (h.reflections.isNotEmpty()) item { ReflectionsCard(h.reflections) }
                    item { NotesCard(h.notes, h.nextMeetingAt) }
                }
            }
        }
    }
}

// ── discipler card (avatar · name · role · cell · since) ──────────────────────

@Composable
private fun DisciplerCard(d: HubDiscipler) {
    HubCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DisciplerAvatar(d.fullName, d.avatarUrl)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.fullName, style = gInter(17, FontWeight.Bold), color = GrowPal.ink)
                Text(d.roleLabel.uppercase(), style = gInter(10, FontWeight.SemiBold).copy(letterSpacing = 1.2.sp), color = GrowPal.gold)
                d.cellName?.let { Text(it, style = gInter(12), color = GrowPal.ink600) }
                monthYear(d.establishedAt)?.let {
                    Text("Walking with you since $it", style = gInter(11), color = GrowPal.gold)
                }
            }
        }
    }
}

@Composable
private fun DisciplerAvatar(name: String, url: String?) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = name, modifier = Modifier.size(56.dp).clip(CircleShape))
    } else {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(GrowPal.goldChipBg),
            contentAlignment = Alignment.Center,
        ) {
            val initials = name.split(" ").filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }
            Text(initials.ifEmpty { "?" }, style = gSerif(20, FontWeight.SemiBold), color = GrowPal.gold)
        }
    }
}

// ── hero — Message {first name}, or the minor note ────────────────────────────

@Composable
private fun MessageHero(firstName: String, busy: Boolean, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(ControlShape).background(GrowPal.gold)
            .clickable(enabled = !busy) { onTap() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = GrowPal.navy, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text("Message $firstName", style = gInter(15, FontWeight.SemiBold), color = GrowPal.navy)
    }
}

@Composable
private fun MinorNote(firstName: String) {
    // Minors can't be messaged — point them to the in-person gathering.
    HubCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(ControlShape).background(GrowPal.goldChipBg),
                contentAlignment = Alignment.Center,
            ) { Text("🤝", fontSize = 18.sp) }
            Text(
                "Talk with $firstName at your next cell gathering.",
                style = gInter(14), color = GrowPal.ink,
            )
        }
    }
}

// ── "Where you are" — level · progress bar · streak · awaiting banner ─────────

@Composable
private fun WhereYouAreCard(p: HubProgression) {
    HubCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("WHERE YOU ARE")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Level ${p.currentLevel}", style = gSerif(20, FontWeight.SemiBold), color = GrowPal.ink)
                Text(p.levelTitle, style = gInter(13), color = GrowPal.ink600, maxLines = 1, modifier = Modifier.weight(1f))
                if (p.streakDays > 0) {
                    Box(
                        Modifier.clip(Capsule).background(GrowPal.goldChipBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("🔥 ${p.streakDays}-day", style = gInter(11, FontWeight.Bold), color = GrowPal.gold)
                    }
                }
            }

            // Modules progress bar.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val pct = if (p.modulesTotal > 0) p.modulesCompleted.toFloat() / p.modulesTotal else 0f
                ThinBar(pct, GrowPal.gold, height = 8.dp)
                Text(
                    "${p.modulesCompleted} of ${p.modulesTotal} modules complete",
                    style = gInter(11), color = GrowPal.ink400,
                )
            }

            if (p.awaitingReview) {
                Row(
                    Modifier.fillMaxWidth().clip(ControlShape).background(GrowPal.goldChipBg)
                        .border(1.dp, GrowPal.gold.copy(alpha = 0.35f), ControlShape)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🌿", fontSize = 18.sp)
                    Text(
                        "Awaiting your discipler's blessing for Level ${p.awaitingLevel ?: p.currentLevel}",
                        style = gInter(12, FontWeight.SemiBold), color = GrowPal.ink,
                    )
                }
            }
        }
    }
}

// ── "Your growth" — overall circled at top + five thin-bar rows ───────────────

@Composable
private fun GrowthCard(s: HubScores) {
    HubCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("YOUR GROWTH")
            s.overall?.let { overall ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape)
                            .border(2.dp, scoreColor(overall), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$overall", style = gSerif(20, FontWeight.SemiBold), color = scoreColor(overall))
                    }
                    Text("overall", style = gInter(13), color = GrowPal.ink600)
                }
            }
            listOf(
                "Word" to s.word,
                "Prayer" to s.prayer,
                "Habits" to s.habits,
                "Curriculum" to s.curriculum,
                "Attendance" to s.attendance,
            ).forEach { (label, value) -> ScoreRow(label, value) }
        }
    }
}

@Composable
private fun ScoreRow(label: String, value: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = gInter(12, FontWeight.SemiBold), color = GrowPal.ink, modifier = Modifier.width(84.dp))
        Box(Modifier.weight(1f)) {
            ThinBar(
                pct = (value ?: 0) / 100f,
                color = if (value != null) scoreColor(value) else GrowPal.border,
                height = 6.dp,
            )
        }
        Text(
            value?.toString() ?: "—",
            style = gInter(12, FontWeight.Bold),
            color = if (value != null) scoreColor(value) else GrowPal.ink400,
            modifier = Modifier.width(28.dp),
        )
    }
}

@Composable
private fun ThinBar(pct: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(Capsule).background(GrowPal.border)) {
        Box(
            Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).height(height)
                .clip(Capsule).background(color),
        )
    }
}

// ── reflections — module · level · state pill · discipler feedback ────────────

@Composable
private fun ReflectionsCard(reflections: List<HubReflection>) {
    HubCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("RECENT REFLECTIONS")
            reflections.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GrowPal.border))
                ReflectionRow(r)
            }
        }
    }
}

@Composable
private fun ReflectionRow(r: HubReflection) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(r.moduleTitle, style = gInter(13, FontWeight.SemiBold), color = GrowPal.ink, maxLines = 2)
                Text("Level ${r.levelNumber}", style = gInter(10), color = GrowPal.ink400)
            }
            StatePill(r.state)
        }
        Text(relTime(r.submittedAt), style = gInter(10), color = GrowPal.ink400)
        // The discipler's feedback to the student, when present.
        val notes = r.feedbackNotes
        if (!notes.isNullOrEmpty()) {
            Box(
                Modifier.fillMaxWidth().clip(ControlShape).background(GrowPal.surface)
                    .border(1.dp, GrowPal.border, ControlShape)
                    .padding(10.dp),
            ) {
                Text("“$notes”", style = gInter(12).copy(lineHeight = 17.sp), color = GrowPal.ink600)
            }
        }
    }
}

@Composable
private fun StatePill(state: String) {
    val (label, color) = when (state.lowercase()) {
        "approved" -> "Approved" to Green
        "pending" -> "Pending" to GrowPal.gold
        "returned" -> "Returned" to Red
        "deferred" -> "Deferred" to GrowPal.ink600
        else -> state.replaceFirstChar { it.uppercase() } to GrowPal.ink600
    }
    Box(
        Modifier.clip(Capsule).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), Capsule)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(label, style = gInter(10, FontWeight.Bold), color = color)
    }
}

// ── meeting notes — next meeting + note rows ──────────────────────────────────

@Composable
private fun NotesCard(notes: List<HubNote>, nextMeetingAt: String?) {
    HubCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("MEETING NOTES")
            if (notes.isEmpty() && nextMeetingAt == null) {
                Text(
                    "Notes from your one-on-ones will appear here.",
                    style = gInter(13), color = GrowPal.ink600,
                )
            } else {
                nextMeetingAt?.let {
                    Row(
                        Modifier.fillMaxWidth().clip(ControlShape).background(GrowPal.surface)
                            .border(1.dp, GrowPal.border, ControlShape)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("📅", fontSize = 16.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(shortDate(it) ?: it, style = gInter(13, FontWeight.SemiBold), color = GrowPal.ink)
                            Text("Your next meeting", style = gInter(11), color = GrowPal.ink600)
                        }
                    }
                }
                notes.forEachIndexed { i, n ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GrowPal.border))
                    NoteRow(n)
                }
            }
        }
    }
}

@Composable
private fun NoteRow(n: HubNote) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                n.topic.ifEmpty { "Session" },
                style = gInter(12, FontWeight.Bold), color = GrowPal.ink,
                modifier = Modifier.weight(1f),
            )
            shortDate(n.metAt)?.let { Text(it, style = gInter(10), color = GrowPal.ink400) }
        }
        Text(n.body, style = gInter(12).copy(lineHeight = 17.sp), color = GrowPal.ink600)
        n.nextMeetingAt?.let { next ->
            shortDate(next)?.let { Text("Next: $it", style = gInter(10), color = GrowPal.ink400) }
        }
    }
}

// ── loading / error / empty states ────────────────────────────────────────────

@Composable
private fun LoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HubCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(GrowPal.surface))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(140.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(GrowPal.surface))
                    Box(Modifier.width(90.dp).height(9.dp).clip(RoundedCornerShape(4.dp)).background(GrowPal.surface))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(52.dp).clip(ControlShape).background(GrowPal.surface))
        HubCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(90.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(GrowPal.surface))
                Box(Modifier.fillMaxWidth().height(68.dp).clip(ControlShape).background(GrowPal.surface))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    // A failed load is not "no discipler" — say so, offer retry.
    HubCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = gInter(13), color = GrowPal.ink600, modifier = Modifier.fillMaxWidth())
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(ControlShape).background(GrowPal.surface)
                    .border(1.dp, GrowPal.border, ControlShape)
                    .clickable { onRetry() },
                contentAlignment = Alignment.Center,
            ) {
                Text("Try again", style = gInter(13, FontWeight.SemiBold), color = GrowPal.navy)
            }
        }
    }
}

@Composable
private fun EmptyCard() {
    // No pairing yet — warm, not an error.
    HubCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(48.dp).clip(ControlShape).background(GrowPal.goldChipBg),
                contentAlignment = Alignment.Center,
            ) { Text("🤝", fontSize = 22.sp) }
            Text(
                "You'll be paired with a discipler soon",
                style = gInter(17, FontWeight.Bold), color = GrowPal.ink,
            )
            Text(
                "When your leader walks you into a discipleship relationship, your meetings, notes, and feedback will live here.",
                style = gInter(13).copy(lineHeight = 19.sp), color = GrowPal.ink600,
            )
        }
    }
}

// ── shared bits ───────────────────────────────────────────────────────────────

@Composable
private fun HubCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(GrowPal.white)
            .border(1.dp, GrowPal.border, CardShape)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Kicker(text: String) {
    Text(text, style = gInter(10, FontWeight.Bold).copy(letterSpacing = 1.8.sp), color = GrowPal.gold)
}

private fun firstName(full: String) = full.split(" ").firstOrNull { it.isNotBlank() } ?: full

/** True when at least one score is computed — drives whether the growth card shows. */
private fun hasAnyScore(s: HubScores): Boolean =
    s.overall != null || s.word != null || s.prayer != null ||
        s.habits != null || s.curriculum != null || s.attendance != null

/** Score → band color (mirrors iOS scoreColor: thriving/steady/watch/at-risk). */
private fun scoreColor(v: Int): Color = when {
    v >= 75 -> Green
    v >= 50 -> GrowPal.gold
    v >= 30 -> Color(0xFFEA8C00)
    else -> Red
}

// ── date helpers (match the iOS formatting) ───────────────────────────────────

private fun parseDate(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(iso).toLocalDate() }
        .recoverCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }
        .recoverCatching { LocalDate.parse(iso.take(10)) }
        .getOrNull()
}

private fun monthYear(iso: String?): String? =
    parseDate(iso)?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))

private fun shortDate(iso: String?): String? =
    parseDate(iso)?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
