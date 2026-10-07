// The Sunday Letter — weekly pastoral letter from the intelligence layer,
// presented as warm stationery over a navy backdrop (parity with iOS
// LetterView): gold wax seal, serif voice, scripture pill, share. The Home
// "knock" card appears while the latest letter is unread.
package org.nuruplace.member.feature.home

import android.content.Intent
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PastoralLetter
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import org.nuruplace.member.ui.theme.scaledLineHeight
import org.nuruplace.member.ui.icons.Lucide

private val SealGrad = Brush.linearGradient(listOf(Color(0xFFE8CA6C), Color(0xFFB6862F)))

/** Letter body voice — the serif family at reading size. lineHeight is left
 *  UNSET on purpose: NuruTheme rebuilds typography from AppPrefs.lineSpacing,
 *  and hard-coding it here would silently ignore the member's own spacing
 *  choice on the one screen most worth reading comfortably. Font size rides
 *  AppPrefs.textScale through the theme's Density the same way. */
// The letter is long reading: the one 16 sp reading body (§8.2 #21), in the
// letter's Fraunces, with a reader's leading. A getter, so the member's
// line-spacing choice is read each time it draws.
private val LetterBody get() = NuruType.rowTitle.copy(fontSize = 16.sp, lineHeight = scaledLineHeight(25), fontWeight = FontWeight.Normal)

/** Home, when no letter has arrived yet. The ritual IS the pull: telling a
 *  member when their letter comes is honest anticipation, and it beats showing
 *  nothing at all — which is what Home did before. The read card's quiet navy
 *  with its own words (owner, 2026-10-07: colour option A), the countdown to
 *  Sunday 6 pm East Africa Time folded into its line — a gold badge beside it
 *  was a second gold pill on Home (the next step's is the one). Tap opens the
 *  You tab — no letters-archive screen exists yet (GET /me/letters has no
 *  route), so the caller decides where "your letters" lives. */
@Composable
fun LetterAwaitingCard(onClick: () -> Unit = {}) {
    val countdown = remember { letterCountdownLabel() }
    LetterQuietCard("Your letter arrives Sunday evening", LetterWords.arrivalLine(countdown), onClick)
}

/** "Today" / "Tomorrow" / "In N days" until the next Sunday 6 pm in East
 *  Africa Time — the letter's fixed delivery hour, whatever zone the phone is
 *  in. A Sunday already past 6 pm counts toward NEXT Sunday. */
internal fun letterCountdownLabel(now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Africa/Nairobi"))): String {
    var target = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(18, 0).atZone(now.zone)
    if (!target.isAfter(now)) target = target.plusWeeks(1)
    return when (val days = ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "In $days days"
    }
}

/** Home, once the letter has been read — a quiet way back in. Without this the
 *  letter became unreachable the moment it was opened, which is a poor fate for
 *  the most personal thing the app produces. Navy, as every state of the letter
 *  is — the church's voice (owner, 2026-10-07: colour option A): its own title,
 *  "Sun 4 Oct · Read again", a gold chevron. It was a white row. */
@Composable
fun LetterReadRow(letter: PastoralLetter, onOpen: () -> Unit) {
    LetterQuietCard(letter.displayTitle ?: "Your Sunday Letter", LetterWords.readLine(letter.weekOf), onOpen)
}

/** The letter's navy ground (owner, 2026-10-07: colour option A). */
private val LetterNavy = Brush.linearGradient(listOf(Color(0xFF11253F), Color(0xFF0A1628)))
private val LetterGold = Color(0xFFE8CA6C)
private val LetterGoldGlow = Color(0xFFE6CA68)
private val LetterMeta = Color(0xFFB9C4D4)
private val LetterStroke = Color(0xFFC9A227)

/** The quiet navy letter card — read, and before a letter exists (iOS
 *  HomeLetterQuietCard): the gold envelope, "THE SUNDAY LETTER", a white
 *  title, one line, a gold chevron; a gold hairline at 0.35 and a soft navy
 *  shadow. */
@Composable
private fun LetterQuietCard(title: String, line: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color(0x2E0B1F33), spotColor = Color(0x2E0B1F33))
            .clip(shape)
            .background(LetterNavy)
            .border(1.dp, LetterStroke.copy(alpha = 0.35f), shape)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(SealGrad), contentAlignment = Alignment.Center) {
            Icon(Lucide.Mail, null, tint = Color(0xFF1E2A1F), modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("THE SUNDAY LETTER", style = NuruType.kicker, color = LetterGold)
            org.nuruplace.member.ui.components.WholeWordsText(title, style = nuruSerif(16, FontWeight.SemiBold), color = Color.White)
            Text(line, style = nuruSans(12), color = LetterMeta)
        }
        Icon(Lucide.ChevronRight, null, tint = LetterGoldGlow, modifier = Modifier.size(18.dp))
    }
}

/** The letter's quiet lines — the same words as iOS HomeLetterWords. */
internal object LetterWords {
    private val DAY = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH)
    private val DAY_YEAR = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.ENGLISH)

    /** "Sun 4 Oct · Read again": the letter's Sunday (`week_of`), a calendar
     *  date shown as sent, never shifted by a time zone (§8.1 rule 8), with
     *  its year when it isn't this year; "Read again" alone if it can't be read. */
    fun readLine(weekOf: String, today: java.time.LocalDate = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Nairobi"))): String {
        val day = runCatching { java.time.LocalDate.parse(weekOf.take(10)) }.getOrNull() ?: return "Read again"
        return "${day.format(if (day.year == today.year) DAY else DAY_YEAR)} · Read again"
    }

    /** "Written for your week · In 4 days": before a letter exists, its own
     *  words with the countdown to Sunday evening in its one line. */
    fun arrivalLine(countdown: String): String = "Written for your week · $countdown"
}

/** Home knock — shows while this week's letter is unread. */
@Composable
fun LetterKnockCard(letter: PastoralLetter, onOpen: () -> Unit) {
    Row(
        // The knock (owner, 2026-10-07: colour option A — iOS's navy knock):
        // the gold hairline at 0.55 with a gold glow, the gold envelope.
        Modifier.fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(18.dp), ambientColor = LetterStroke.copy(alpha = 0.3f), spotColor = LetterStroke.copy(alpha = 0.55f))
            .clip(RoundedCornerShape(18.dp))
            .background(LetterNavy)
            .border(1.dp, LetterStroke.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable { onOpen() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(SealGrad), contentAlignment = Alignment.Center) {
            Icon(Lucide.Mail, null, tint = Color(0xFF1E2A1F), modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("THE SUNDAY LETTER", style = NuruType.kicker, color = LetterGold)
            Text("A letter was written for you", style = nuruSerif(16, FontWeight.SemiBold), color = Color.White)
            // v2: the title is the hook — "A letter about the week you kept
            // going" earns a tap where "Your Sunday Letter" does not. Falls
            // back to the scripture for pre-v2 letters, never to a blank.
            (letter.displayTitle ?: letter.displayScripture)?.let {
                // Two lines at the everyday sizes; past them the whole line —
                // it stopped at "you marked an…" at the largest (§9.6 #4).
                Text(
                    it, style = nuruSans(12), color = LetterMeta,
                    maxLines = if (org.nuruplace.member.ui.components.largeText()) Int.MAX_VALUE else 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(LetterGold).padding(horizontal = 14.dp, vertical = 8.dp),
        ) { Text("Open", style = NuruType.micro, color = Color(0xFF1E2A1F), fontWeight = FontWeight.Bold) }
    }
}

/** Full-screen stationery reader; marks the letter read on open. */
@Composable
fun LetterDialog(
    letter: PastoralLetter,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onNextStep: (route: String, moduleId: String?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(letter.letterId) {
        if (letter.isUnread) {
            runCatching { Net.client.api.markLetterRead(letter.letterId) }
            onRead()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A1628), Color(0xFF081020)))),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // v2 hero — the themed illustration with the letter's own title
                // overlaid. Pre-v2 letters have no theme; resolve() falls back
                // rather than blanking, so this is safe for every row.
                Box(Modifier.fillMaxWidth().padding(top = 44.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(22.dp))) {
                        LetterHero(letter.artKey, height = 190.dp)
                    }
                    letter.displayTitle?.let { t ->
                        Text(
                            t,
                            style = NuruType.cardTitle,
                            color = Color(0xFFFFFDF6),
                            modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
                            maxLines = 3,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Gold wax seal, overlapping the paper.
                Box(
                    Modifier.size(68.dp).clip(CircleShape).background(SealGrad).offset(y = 0.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(54.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape))
                    Text("N", style = NuruType.display, color = Color(0xFF1E2A1F))
                }
                Column(
                    Modifier.offset(y = (-34).dp).fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFFFFDF6), Color(0xFFF8F3E6))))
                        .border(1.dp, Color(0xFFC9A227).copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                        .padding(horizontal = 24.dp)
                        .padding(top = 44.dp, bottom = 22.dp),
                ) {
                    Text("THE SUNDAY LETTER", style = NuruType.micro, color = Color(0xFFA8861C), fontWeight = FontWeight.Bold)
                    // "Week of Sun 4 Oct" — it read the server's raw "2026-10-04" (§8.1 rule 8).
                    org.nuruplace.member.util.NuruDates.day(letter.weekOf)?.let { Text("Week of $it", style = NuruType.caption, color = Color(0xFF74808F)) }
                    letter.scriptureRef?.let { ref ->
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFDF5E5))
                                .border(1.dp, Color(0xFFF2E2BD), RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(Lucide.BookOpen, null, tint = Color(0xFFA8861C), modifier = Modifier.size(14.dp))
                            Text(ref, style = NuruType.caption, color = Color(0xFF8A6B1F), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Their actual name. Omitted entirely when absent — a
                    // generic "Dear member" is worse than no salutation.
                    letter.displaySalutation?.let { sal ->
                        Text(sal, style = LetterBody, color = Color(0xFF2A3441), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(letter.body, style = LetterBody, color = Color(0xFF2A3441))

                    // "This week" — the true, concrete moments. This is the
                    // part that makes the letter feel known rather than
                    // written-at, so it is prominent but quiet. Omitted whole
                    // when the model had nothing true to say.
                    if (letter.moments.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("THIS WEEK", style = NuruType.micro, color = Color(0xFFA8861C), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        letter.moments.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                            ) {
                                Box(
                                    Modifier.padding(top = 7.dp).size(5.dp)
                                        .clip(CircleShape).background(Color(0xFFC9A227)),
                                )
                                Text(m, style = NuruType.body, color = Color(0xFF4A5563))
                            }
                        }
                    }

                    // ONE next step, never a menu. Server-computed, so it can
                    // only point at something that actually exists.
                    letter.nextStep?.let { step ->
                        Spacer(Modifier.height(20.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF11253F))
                                .clickable { onNextStep(step.route, step.params?.moduleId) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(step.label, style = NuruType.cardCta, color = Color(0xFFF3E6C8), fontWeight = FontWeight.SemiBold)
                            Icon(Lucide.ArrowRight, null, tint = Color(0xFFC9A227), modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFFDF5E5))
                                .clickable {
                                    scope.launch {
                                        // v2: share the ONE line the letter
                                        // offers, not the whole private
                                        // letter. Falls back to the old
                                        // behaviour for pre-v2 letters, which
                                        // carry no share_line.
                                        val text = letter.shareLine
                                            ?: ((letter.displayScripture?.let { "📖 $it\n\n" } ?: "") + letter.body)
                                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                                        runCatching { context.startActivity(Intent.createChooser(send, "Share your letter")) }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Lucide.Share2, null, tint = Color(0xFF8A6B1F), modifier = Modifier.size(14.dp))
                            Text("Share", style = NuruType.caption, color = Color(0xFF8A6B1F), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp)
                    .size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.X, "Close", tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}
