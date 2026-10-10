// The Sunday Letter — weekly pastoral letter from the intelligence layer. Home
// shows it in navy in every state (the knock while unread, a quiet card once
// read, and before one exists); it opens as the editorial page
// (EditorialLetter.kt, owner 2026-10-07), and past letters live in the
// letters archive (LettersArchiveScreen).
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.nuruplace.member.ui.components.NuruDialog
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

/** Home, when no letter has arrived yet. The ritual IS the pull: telling a
 *  member when their letter comes is honest anticipation, and it beats showing
 *  nothing at all — which is what Home did before. The read card's quiet navy
 *  with its own words (owner, 2026-10-07: colour option A), the countdown to
 *  Sunday 6 pm East Africa Time folded into its line — a gold badge beside it
 *  was a second gold pill on Home (the next step's is the one). The caller
 *  decides where the tap goes (Home: the You tab — with no letter yet, the
 *  letters archive would be empty). */
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

/**
 * The letter, full screen — the editorial page (owner, 2026-10-07: the
 * editorial Sunday Letter; EditorialLetter.kt). Marks the letter read on open.
 *
 * [onNextStep]: the letter's one next step (letterStepDest). [onWriteBack]:
 * the member's own pastoral conversation, opened (POST /chat/pastoral, which
 * makes it the first time) — its id. [onOpenLetters]: the letters archive,
 * with the letter to open there. [archive]: the member's letters when the
 * caller already has them; otherwise they're fetched for "Last week".
 */
@Composable
fun LetterDialog(
    letter: PastoralLetter,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onNextStep: (route: String, moduleId: String?) -> Unit = { _, _ -> },
    onWriteBack: (conversationId: String) -> Unit = {},
    onOpenLetters: (letterId: String?) -> Unit = {},
    archive: List<PastoralLetter>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(letter.letterId) {
        if (letter.isUnread) {
            runCatching { Net.client.api.markLetterRead(letter.letterId) }
            onRead()
        }
    }
    // The letter before this one, for "Last week" — none shown until known.
    var letters by remember(letter.letterId) { mutableStateOf(archive) }
    LaunchedEffect(letter.letterId, archive) {
        if (archive == null) letters = runCatching { Net.client.api.letters().data }.getOrNull()
    }
    val previous = letters?.let { EditorialLetter.previousOf(it, letter) }
    val pdfUrl = remember(letter.pdfUrl) { EditorialLetter.pdfUrl(org.nuruplace.member.BuildConfig.API_BASE_URL, letter.pdfUrl) }
    var writeBack by remember(letter.letterId) { mutableStateOf(LetterRowState()) }
    var pdf by remember(letter.letterId) { mutableStateOf(LetterRowState()) }

    fun openPastoral() {
        if (writeBack.busy) return
        writeBack = LetterRowState(busy = true)
        scope.launch {
            runCatching { Net.client.api.openPastoralThread() }
                .onSuccess { res ->
                    if (res.conversationId.isBlank()) {
                        writeBack = LetterRowState(error = "Couldn't open this conversation.")
                    } else {
                        writeBack = LetterRowState()
                        // As the Talk with My Pastor tab: this phone learns the
                        // thread, and an archived thread is reopened.
                        org.nuruplace.member.data.AppPrefs.pastoralConversationId = res.conversationId
                        org.nuruplace.member.data.AppPrefs.pastoralArchived = false
                        onWriteBack(res.conversationId)
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // The Talk with My Pastor tab's own words.
                    writeBack = LetterRowState(
                        error = when {
                            org.nuruplace.member.feature.community.isNoPastor(e) -> "No pastor is available for your congregation right now — please check back later."
                            org.nuruplace.member.feature.community.isMinorBlocked(e) -> "Direct messages aren't available yet for your account."
                            else -> org.nuruplace.member.data.net.ApiException.failureLine("Couldn't open this conversation.", e, context)
                        },
                    )
                }
        }
    }

    fun keepPdf() {
        val url = pdfUrl ?: return
        if (pdf.busy) return
        pdf = LetterRowState(busy = true)
        scope.launch {
            // Fetched through the authed client into cache/shared and opened
            // through the FileProvider — never a ?token= address.
            val failure = org.nuruplace.member.feature.give.openPdfAuthed(context, EditorialLetter.pdfFileName(letter.weekOf)) {
                Net.client.api.letterPdf(url)
            }
            pdf = LetterRowState(error = failure?.let { EditorialLetter.pdfErrorLine(org.nuruplace.member.feature.give.isOffline(it, context)) })
        }
    }

    // NuruDialog: the letter reads at the member's own text size (a plain
    // Dialog's window dropped it — NuruDialog.kt).
    NuruDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            EditorialLetterPage(
                letter = letter,
                previous = previous,
                writeBack = writeBack,
                pdf = if (pdfUrl != null) pdf else null,
                onWriteBack = ::openPastoral,
                onKeepPdf = ::keepPdf,
                onOpenPrevious = { previous?.let { onOpenLetters(it.letterId) } },
                onShareLine = { line ->
                    // The ONE line the letter offers — never the private letter.
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, line) }
                    runCatching { context.startActivity(Intent.createChooser(send, "Share this line")) }
                },
                onNextStep = { step -> onNextStep(step.route, step.params?.moduleId) },
            )
            // Always within reach, over the page as it scrolls — so a solid
            // circle of the paper's own colour, with a hairline and a soft
            // shadow: it sits above the words passing under it, never among
            // them (at the largest size the title runs beneath it).
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp)
                    .size(36.dp)
                    .shadow(4.dp, CircleShape, ambientColor = Color(0x2E0B1F33), spotColor = Color(0x2E0B1F33))
                    .clip(CircleShape).background(Color(0xFFFBF6EC))
                    .border(1.dp, Color(0x1A0B1F33), CircleShape)
                    .clickable(onClickLabel = "Close") { onDismiss() },
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.X, "Close", tint = Color(0xFF0B1F33), modifier = Modifier.size(18.dp)) }
        }
    }
}
