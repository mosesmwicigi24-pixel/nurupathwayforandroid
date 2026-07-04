// Discipler Dossier — the discipler-facing view of ONE disciple's full journey,
// composed from GET /disciples/{id} (Instructor+; the server scopes the caller
// to their own disciple set and answers 403 FORBIDDEN_SCOPE otherwise). Port of
// the web Hub console's per-student dossier: member card + Message CTA, the
// engagement read (eScore, band, last-active), "where they are" progression,
// growth scores, reflections in their pastoral fullness (bodies included), and
// a recent-activity trail. Pure reflection of server state (§1.9) — ushering a
// level happens elsewhere; this screen only tells the discipler it's their turn.
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
import org.nuruplace.member.data.net.ActivityEvent
import org.nuruplace.member.data.net.DmBody
import org.nuruplace.member.data.net.Dossier
import org.nuruplace.member.data.net.DossierEngagement
import org.nuruplace.member.data.net.DossierMember
import org.nuruplace.member.data.net.DossierReflection
import org.nuruplace.member.data.net.HubProgression
import org.nuruplace.member.data.net.HubScores
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.GrowCreamHeader
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
private val Blue = Color(0xFF2563EB)

@Composable
fun DisciplerDossierScreen(studentId: String, onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    var dossier by remember { mutableStateOf<Dossier?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var startingDm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        loading = true
        error = null
        runCatching { Net.client.api.disciple(studentId) }
            .onSuccess { dossier = it.data }
            .onFailure { error = it.message ?: "Couldn't load this disciple." }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(GrowPal.coolPaper)) {
        // Cream header — back circle + serif first name (matches DiscipleshipHub anatomy).
        GrowCreamHeader {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(GrowPal.white)
                        .border(1.dp, GrowPal.border, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = GrowPal.navy, modifier = Modifier.size(18.dp)) }
                val title = dossier?.member?.fullName?.let { firstName(it) }?.takeIf { it.isNotBlank() } ?: "Disciple"
                Text(title, style = gSerif(20, FontWeight.SemiBold), color = GrowPal.navy)
            }
        }

        val d = dossier
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                loading && d == null -> item { LoadingSkeleton() }
                d == null -> item {
                    ErrorCard(error ?: "Couldn't load this disciple.") { reloadKey++ }
                }
                else -> {
                    item {
                        MemberCard(d.member, busy = startingDm) {
                            val existing = d.dmConversationId
                            if (existing != null) {
                                onOpenChat(existing)
                            } else if (!startingDm) {
                                startingDm = true
                                scope.launch {
                                    runCatching { Net.client.api.createDm(DmBody(d.member.userId)) }
                                        .onSuccess { onOpenChat(it.conversationId) }
                                    startingDm = false
                                }
                            }
                        }
                    }
                    item { EngagementCard(d.engagement) }
                    item { WhereTheyAreCard(d.progression) }
                    item { GrowthCard(d.scores) }
                    if (d.reflections.isNotEmpty()) item { ReflectionsCard(d.reflections) }
                    if (d.recentActivity.isNotEmpty()) item { ActivityCard(d.recentActivity) }
                }
            }
        }
    }
}

// ── member card (avatar · name · cell · since) + gold Message CTA ─────────────

@Composable
private fun MemberCard(m: DossierMember, busy: Boolean, onMessage: () -> Unit) {
    DossierCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MemberAvatar(m.fullName, m.avatarUrl)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(m.fullName.ifBlank { "Disciple" }, style = gInter(17, FontWeight.Bold), color = GrowPal.ink)
                    m.cellName?.let { Text(it, style = gInter(12), color = GrowPal.ink600) }
                    val since = monthYear(m.establishedAt)?.let { "In your care since $it" }
                        ?: monthYear(m.joinedAt)?.let { "Joined $it" }
                    since?.let { Text(it, style = gInter(11), color = GrowPal.gold) }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(ControlShape).background(GrowPal.gold)
                    .clickable(enabled = !busy) { onMessage() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = GrowPal.navy, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text("Message", style = gInter(15, FontWeight.SemiBold), color = GrowPal.navy)
            }
        }
    }
}

@Composable
private fun MemberAvatar(name: String, url: String?) {
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

// ── engagement — circled eScore · band pill · last-active read ────────────────

@Composable
private fun EngagementCard(e: DossierEngagement) {
    DossierCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("ENGAGEMENT")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val ringColor = e.eScore?.let { scoreColor(it) } ?: GrowPal.border
                Box(
                    Modifier.size(56.dp).clip(CircleShape).border(2.dp, ringColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        e.eScore?.toString() ?: "—",
                        style = gSerif(20, FontWeight.SemiBold),
                        color = e.eScore?.let { scoreColor(it) } ?: GrowPal.ink400,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    e.band?.let { BandPill(it) }
                    val days = e.daysSinceLastActivity
                    if (days == null) {
                        Text("No activity yet", style = gInter(12), color = GrowPal.ink400)
                    } else {
                        Text(
                            "Last active ${days}d ago",
                            style = gInter(12, FontWeight.SemiBold),
                            color = if (days >= 7) Red else GrowPal.ink600,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BandPill(band: String) {
    val (label, color) = when (band.lowercase()) {
        "thriving" -> "Thriving" to Green
        "steady" -> "Steady" to Blue
        "watch" -> "Watch" to GrowPal.gold
        "at_risk" -> "At risk" to Red
        else -> band.replaceFirstChar { it.uppercase() } to GrowPal.ink600
    }
    Box(
        Modifier.clip(Capsule).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), Capsule)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(label, style = gInter(10, FontWeight.Bold), color = color)
    }
}

// ── "Where they are" — level · progress bar · streak · usher banner ───────────

@Composable
private fun WhereTheyAreCard(p: HubProgression) {
    DossierCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("WHERE THEY ARE")
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

            // The discipler's action — this student is waiting on YOU to usher them up.
            p.awaitingLevel?.let { level ->
                Row(
                    Modifier.fillMaxWidth().clip(ControlShape).background(GrowPal.gold)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🌿", fontSize = 18.sp)
                    Text(
                        "Awaiting YOUR usher into Level $level",
                        style = gInter(13, FontWeight.Bold), color = GrowPal.navy,
                    )
                }
            }
        }
    }
}

// ── growth scores — overall circled at top + five thin-bar rows ───────────────

@Composable
private fun GrowthCard(s: HubScores) {
    DossierCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("GROWTH SCORES")
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

// ── reflections — pastoral view: module · state pill · BODY · your feedback ───

@Composable
private fun ReflectionsCard(reflections: List<DossierReflection>) {
    DossierCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker("REFLECTIONS")
            reflections.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GrowPal.border))
                ReflectionRow(r)
            }
        }
    }
}

@Composable
private fun ReflectionRow(r: DossierReflection) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(r.moduleTitle, style = gInter(13, FontWeight.SemiBold), color = GrowPal.ink, maxLines = 2)
                Text("Level ${r.levelNumber}", style = gInter(10), color = GrowPal.ink400)
            }
            StatePill(r.state)
        }
        Text(relTime(r.submittedAt), style = gInter(10), color = GrowPal.ink400)
        // The reflection itself — the discipler reads the student's own words.
        val body = r.body
        if (!body.isNullOrEmpty()) {
            Box(
                Modifier.fillMaxWidth().clip(ControlShape).background(GrowPal.surface)
                    .border(1.dp, GrowPal.border, ControlShape)
                    .padding(10.dp),
            ) {
                Text("“$body”", style = gInter(12).copy(lineHeight = 17.sp), color = GrowPal.ink600)
            }
        }
        // What the discipler already said back.
        val notes = r.feedbackNotes
        if (!notes.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "YOUR FEEDBACK",
                    style = gInter(9, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
                    color = GrowPal.gold,
                )
                Text(notes, style = gInter(12).copy(lineHeight = 17.sp), color = GrowPal.ink600)
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

// ── recent activity — humanised kind · relTime, capped at 10 ──────────────────

@Composable
private fun ActivityCard(events: List<ActivityEvent>) {
    DossierCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker("RECENT ACTIVITY")
            events.take(10).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        humaniseKind(e.kind),
                        style = gInter(12, FontWeight.SemiBold), color = GrowPal.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(relTime(e.occurredAt), style = gInter(10), color = GrowPal.ink400)
                }
            }
            if (events.size > 10) {
                Text("+${events.size - 10} more", style = gInter(11), color = GrowPal.ink400)
            }
        }
    }
}

/** "module_completed" → "Module completed". */
private fun humaniseKind(kind: String): String =
    kind.replace('_', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Activity" }

// ── loading / error states ────────────────────────────────────────────────────

@Composable
private fun LoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DossierCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(GrowPal.surface))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(140.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(GrowPal.surface))
                    Box(Modifier.width(90.dp).height(9.dp).clip(RoundedCornerShape(4.dp)).background(GrowPal.surface))
                }
            }
        }
        DossierCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(90.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(GrowPal.surface))
                Box(Modifier.fillMaxWidth().height(68.dp).clip(ControlShape).background(GrowPal.surface))
            }
        }
        DossierCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(120.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(GrowPal.surface))
                Box(Modifier.fillMaxWidth().height(48.dp).clip(ControlShape).background(GrowPal.surface))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    // A failed load may be network or FORBIDDEN_SCOPE — say so plainly, offer retry.
    DossierCard {
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

// ── shared bits ───────────────────────────────────────────────────────────────

@Composable
private fun DossierCard(content: @Composable () -> Unit) {
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

/** Score → band color (mirrors iOS scoreColor: thriving/steady/watch/at-risk). */
private fun scoreColor(v: Int): Color = when {
    v >= 75 -> Green
    v >= 50 -> GrowPal.gold
    v >= 30 -> Color(0xFFEA8C00)
    else -> Red
}

// ── date helpers (match the hub screen formatting) ────────────────────────────

private fun parseDate(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(iso).toLocalDate() }
        .recoverCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }
        .recoverCatching { LocalDate.parse(iso.take(10)) }
        .getOrNull()
}

private fun monthYear(iso: String?): String? =
    parseDate(iso)?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
