// The editorial Sunday Letter (owner, 2026-10-07: the editorial Sunday Letter —
// board "A · The editorial letter" on the canvas). The letter laid out like a
// designed page: a masthead ("No. 6 · Sunday 4 October 2026"), the week's
// photograph, the title and its dek, the salutation, the body with a gold drop
// cap, the member's one line as a gold pull quote, "YOUR WEEK, IN GRACE" (the
// week's true figures and moments), the verse in full, the one next step as
// Home's navy band, a handwritten signature, and three ways on: write back,
// keep it as a PDF, last week's letter.
//
// Everything the server added for it (v3, pathway#512) is derived, never
// written by the model, and optional here: a letter without it falls back to
// what v2 carries — the bundled theme art for the photograph, the date without
// an issue number, the reference without the verse's words, the church's name
// in type where a handwriting would be. The rules are pure (EditorialLetter,
// dropCapSplit, dropCapLines), so EditorialLetterTest pins them.
//
// Type: the board's own editorial sizes — the nameplate 34, the title 30, the
// salutation 21, the body 17/28, the pull quote 23, the figures 34, the quote
// mark 44, the signature 52, the drop cap 58 — listed in TypeScaleSourceTest
// as this page's alone. Every other size is on §8.1 rule 3's scale. The
// nameplate, the quote mark, the signature and the seal's N are drawn like
// pictures and stop growing at the everyday ceiling; the letter's words grow
// with the phone's text size, and past the everyday sizes the drop cap is set
// in line (a cap two lines of 34 sp deep would crowd the line beside it).
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.nuruplace.member.R
import org.nuruplace.member.data.net.LetterFigure
import org.nuruplace.member.data.net.LetterNextStep
import org.nuruplace.member.data.net.LetterPhoto
import org.nuruplace.member.data.net.LetterScripture
import org.nuruplace.member.data.net.LetterSignedBy
import org.nuruplace.member.data.net.PastoralLetter
import org.nuruplace.member.ui.components.CappedFontScale
import org.nuruplace.member.ui.components.EVERYDAY_MAX_FONT_SCALE
import org.nuruplace.member.ui.components.WholeWordsText
import org.nuruplace.member.ui.components.largeText
import org.nuruplace.member.ui.icons.Lucide
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import org.nuruplace.member.ui.theme.scaledLineHeight
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ── The page's colours (board A) ─────────────────────────────────────────────
private val Paper = Color(0xFFFBF6EC)
private val Ink = Color(0xFF1B2430)
private val Navy = Color(0xFF0B1F33)
private val GoldText = Color(0xFFA87F2E)
private val GoldRule = Color(0xFFC89B3C)
private val Muted = Color(0xFF5B6678)
private val Hairline = Color(0x1A0B1F33)
private val FigureRule = Color(0x59A87F2E)
private val VerseGround = Color(0xFFFFF4DA)
private val ChevronInk = Color(0xFFB5BDC9)
private val SealInk = Color(0xFF1E2A1F)
private val ErrorInk = Color(0xFFB42318)
private val Seal = Brush.linearGradient(listOf(Color(0xFFE8CA6C), Color(0xFFB6862F)))

/** The letter's Fraunces: the app's own uprights, with Fraunces' true italic
 *  (the PDF's face, res/font/fraunces_italic.ttf — see assets/licenses) for
 *  the letter's italics. Only this page: the app's Fraunces is unchanged. */
private val LetterFraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_medium, FontWeight.Medium),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_bold, FontWeight.Bold),
    Font(R.font.fraunces_italic, FontWeight.Normal, FontStyle.Italic),
)

/** The signature's handwriting — Mrs Saint Delafield (OFL), as on the PDF. */
private val SignatureFace = FontFamily(Font(R.font.mrs_saint_delafield, FontWeight.Normal))

// ── The page's type (getters: the member's line spacing is read each draw) ──
private val NameplateStyle get() = nuruSerif(34, FontWeight.Medium).copy(fontFamily = LetterFraunces, fontStyle = FontStyle.Italic)
private val TitleStyle get() = nuruSerif(30, FontWeight.SemiBold).copy(fontFamily = LetterFraunces, lineHeight = scaledLineHeight(36))
private val SalutationStyle get() = nuruSerif(21).copy(fontFamily = LetterFraunces, fontStyle = FontStyle.Italic)
private val BodyStyle get() = nuruSerif(17).copy(fontFamily = LetterFraunces, lineHeight = scaledLineHeight(28), letterSpacing = 0.sp)
private val DropCapStyle get() = nuruSerif(58, FontWeight.SemiBold).copy(fontFamily = LetterFraunces, lineHeight = TextUnit.Unspecified, letterSpacing = 0.sp)
private val PullQuoteStyle get() = nuruSerif(23).copy(fontFamily = LetterFraunces, fontStyle = FontStyle.Italic, lineHeight = scaledLineHeight(31))
private val FigureStyle get() = nuruSerif(34, FontWeight.Medium).copy(fontFamily = LetterFraunces, lineHeight = scaledLineHeight(34))
private val QuoteMarkStyle get() = nuruSerif(44).copy(fontFamily = LetterFraunces)
private val VerseStyle get() = nuruSerif(18).copy(fontFamily = LetterFraunces, fontStyle = FontStyle.Italic, lineHeight = scaledLineHeight(27))
private val CaptionStyle get() = nuruSerif(12).copy(fontFamily = LetterFraunces, fontStyle = FontStyle.Italic, lineHeight = scaledLineHeight(17))
private val GraceStyle get() = nuruSerif(15).copy(fontFamily = LetterFraunces, fontStyle = FontStyle.Italic)
private val SignatureStyle get() = TextStyle(fontFamily = SignatureFace, fontSize = 52.sp, lineHeight = 56.sp)
private val KickerStyle get() = nuruSans(11, FontWeight.Bold, tracking = 1.8f)
private val MomentStyle get() = nuruSans(13).copy(lineHeight = scaledLineHeight(19))

/** How many lines of the body sit beside the drop cap: board A's cap — 58 sp
 *  beside 17/28 — has its top at the first line's capitals and its foot on
 *  the second line's baseline (three lines deep at 58 sp would need ~97 sp). */
internal const val DROP_CAP_LINES = 2

/** "YOUR WEEK, IN GRACE" holds at most three figures, in three columns. */
internal const val FIGURE_COLUMNS = 3

/** A paragraph's opening letter, set as a drop cap, and the words after it. */
internal data class DropCap(val initial: String, val rest: String)

/**
 * The drop cap of [paragraph]: its first letter (with any opening quotation
 * mark before it) and the rest, the rest's leading space dropped ("I have" →
 * "I" · "have"). Null — set the paragraph plainly — when it opens with
 * anything but a letter (a figure: "1" · "0 lessons" would split a number),
 * or when nothing would sit beside the cap.
 */
internal fun dropCapSplit(paragraph: String): DropCap? {
    val text = paragraph.trimStart()
    var i = 0
    while (i < text.length && text[i] in "\"'“‘«([") i++
    if (i >= text.length) return null
    val cp = text.codePointAt(i)
    if (!Character.isLetter(cp)) return null
    val end = i + Character.charCount(cp)
    val rest = text.substring(end).trimStart()
    if (rest.isBlank()) return null
    return DropCap(text.substring(0, end), rest)
}

/**
 * The text beside a drop cap and the text below it: the first [depth] lines
 * of [rest] as laid out beside the cap ([lineEnds] — each line's end offset,
 * TextLayoutResult.getLineEnd), then the remainder, set full width. A rest
 * that fits beside the cap leaves nothing below.
 */
internal fun dropCapLines(rest: String, lineEnds: List<Int>, depth: Int = DROP_CAP_LINES): Pair<String, String> {
    if (depth <= 0) return "" to rest
    if (lineEnds.size <= depth) return rest.trimEnd() to ""
    val at = lineEnds[depth - 1].coerceIn(0, rest.length)
    return rest.substring(0, at).trimEnd() to rest.substring(at).trimStart()
}

/** The letter's rules — what each part says, and what it falls back to. */
internal object EditorialLetter {
    private val SUNDAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val DAY_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

    /** The letter's Sunday (`week_of`), a calendar date shown as sent — never
     *  shifted by a time zone (§8.1 rule 8). */
    private fun sunday(weekOf: String): LocalDate? = runCatching { LocalDate.parse(weekOf.trim().take(10)) }.getOrNull()

    /** "No. 6 · Sunday 4 October 2026" — the issue and the letter's Sunday;
     *  either alone when the other is missing (a letter before v3 has no
     *  number); empty when neither is known. */
    fun mastheadLine(issueNo: Int?, weekOf: String): String =
        listOfNotNull(issueNo?.takeIf { it > 0 }?.let { "No. $it" }, sunday(weekOf)?.format(SUNDAY)).joinToString(" · ")

    /** "Your week, read back to you · 2 min" — without the minutes when the
     *  server didn't count them. */
    fun dek(readingMinutes: Int?): String =
        readingMinutes?.takeIf { it > 0 }?.let { "Your week, read back to you · $it min" } ?: "Your week, read back to you"

    /** The letter's paragraphs: v3's own; else the body split on blank lines,
     *  as the server splits it (letterExtras.paragraphsOf). */
    fun paragraphs(letter: PastoralLetter): List<String> {
        val own = letter.paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        if (own.isNotEmpty()) return own
        val body = letter.body.trim()
        if (body.isEmpty()) return emptyList()
        val parts = body.split(Regex("""\n\s*\n""")).map { it.replace(Regex("""\s*\n\s*"""), " ").trim() }.filter { it.isNotEmpty() }
        return parts.ifEmpty { listOf(body) }
    }

    /** The photograph to load — an https address — or null, and the theme art
     *  is the picture. */
    fun photoUrl(photo: LetterPhoto?): String? = photo?.url?.trim()?.takeIf { it.startsWith("https://") }

    /** The photograph's caption — said only under the photograph itself. */
    fun caption(photo: LetterPhoto?): String? = photo?.caption?.trim()?.takeIf { it.isNotEmpty() }

    /** The share line as a pull quote, in its own quotation marks once. */
    fun pullQuote(line: String): String {
        val t = line.trim()
        return if (t.startsWith("“") || t.startsWith("\"")) t else "“$t”"
    }

    /** The week's figures worth a column: a value and a label each, at most three. */
    fun figures(letter: PastoralLetter): List<LetterFigure> =
        letter.figures.filter { it.value.isNotBlank() && it.label.isNotBlank() }.take(FIGURE_COLUMNS)

    /** "YOUR WEEK, IN GRACE" shows when the week has a figure or a moment. */
    fun showWeekInGrace(letter: PastoralLetter): Boolean = figures(letter).isNotEmpty() || letter.moments.isNotEmpty()

    /** The verse's words, when the church's library had them. */
    fun verseText(scripture: LetterScripture?): String? = scripture?.text?.trim()?.takeIf { it.isNotEmpty() }

    /** The verse's reference: v3's, else v2's `scripture_ref`. */
    fun verseRef(letter: PastoralLetter): String? =
        letter.scripture?.ref?.trim()?.takeIf { it.isNotEmpty() } ?: letter.displayScripture

    /** "Philippians 4:6 · ESV" under the words; the reference alone when the
     *  words or the version aren't known. */
    fun verseKicker(letter: PastoralLetter): String? {
        val ref = verseRef(letter) ?: return null
        val version = letter.scripture?.version?.trim()?.takeIf { it.isNotEmpty() }
        return if (verseText(letter.scripture) != null && version != null) "$ref · $version" else ref
    }

    /** The handwritten name — only when the server says who signed. */
    fun signerName(signedBy: LetterSignedBy?): String? = signedBy?.name?.trim()?.takeIf { it.isNotEmpty() }

    /** Under the name: the role; and the church's name where no one signed. */
    fun signerRole(signedBy: LetterSignedBy?): String = signedBy?.role?.trim()?.takeIf { it.isNotEmpty() } ?: "Nuru Place"

    /** The reply row says where the reply really goes: the member's own
     *  pastoral thread reaches their ASSIGNED pastor, who may not be the one
     *  who signed (owner, 2026-10-07 — as iOS). */
    const val WRITE_BACK = "Write back to your pastor"

    /** The next step's pill: a lesson is begun; Pathway (or anything else) is opened. */
    fun stepVerb(route: String): String = if (route == "module") "Begin" else "Open"

    /** The member's letter before [current]: the newest with an earlier Sunday. */
    fun previousOf(all: List<PastoralLetter>, current: PastoralLetter): PastoralLetter? {
        val week = current.weekOf.trim().take(10)
        if (week.isEmpty()) return null
        return all.filter { it.letterId != current.letterId && it.weekOf.trim().take(10).let { w -> w.isNotEmpty() && w < week } }
            .maxByOrNull { it.weekOf.trim().take(10) }
    }

    /** "Last week: Two prayers, answered" — and its own Sunday when it wasn't
     *  last week ("Sun 20 Sep: …"): never "last week" over an older letter. */
    fun previousLabel(previous: PastoralLetter, current: PastoralLetter): String {
        val title = previous.displayTitle ?: "Your Sunday Letter"
        val prev = sunday(previous.weekOf)
        val cur = sunday(current.weekOf)
        return when {
            prev != null && cur != null && prev.plusWeeks(1) == cur -> "Last week: $title"
            prev != null -> "${prev.format(if (cur == null || prev.year == cur.year) DAY else DAY_YEAR)}: $title"
            else -> "Earlier: $title"
        }
    }

    /**
     * The PDF's address: `pdf_url` resolved against the API's own address — a
     * path from the host's root ("/v1/me/letters/…/pdf" stays one /v1/), a
     * relative path under the API base. Null when there is none, or when it
     * would leave the API's own scheme, host and port: the client signs every
     * request with the member's token.
     */
    fun pdfUrl(apiBase: String, pdfUrl: String?): String? {
        val link = pdfUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val base = apiBase.trim().toHttpUrlOrNull() ?: return null
        val url = base.resolve(link) ?: return null
        if (url.scheme != base.scheme || url.host != base.host || url.port != base.port) return null
        return url.toString()
    }

    /** "sunday-letter-2026-10-04.pdf", as the server names it. */
    fun pdfFileName(weekOf: String): String =
        "sunday-letter-" + weekOf.trim().take(10).filter { it.isDigit() || it == '-' }.ifEmpty { "letter" } + ".pdf"

    /** The PDF row's line when the PDF couldn't be had (§4's words). */
    fun pdfErrorLine(offline: Boolean): String =
        if (offline) org.nuruplace.member.feature.give.PDF_OFFLINE else "The PDF isn't available right now — your letter is all here."
}

/** A footer row's moment: working, or what went wrong. */
internal data class LetterRowState(val busy: Boolean = false, val error: String? = null)

/**
 * The editorial letter, the whole page (board A). [pdf] null: no PDF to keep
 * (a letter before v3); [previous] null: no earlier letter.
 */
@Composable
internal fun EditorialLetterPage(
    letter: PastoralLetter,
    previous: PastoralLetter?,
    writeBack: LetterRowState,
    pdf: LetterRowState?,
    onWriteBack: () -> Unit,
    onKeepPdf: () -> Unit,
    onOpenPrevious: () -> Unit,
    onShareLine: (String) -> Unit,
    onNextStep: (LetterNextStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paragraphs = remember(letter) { EditorialLetter.paragraphs(letter) }
    Column(modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState())) {
        Masthead(letter)
        Hero(letter)
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                letter.displayTitle?.let { WholeWordsText(it, style = TitleStyle, color = Navy) }
                Text(EditorialLetter.dek(letter.readingMinutes), style = nuruSans(13), color = Muted)
            }
            // Their own name. Omitted when absent — a generic "Dear member" is
            // worse than no salutation.
            letter.displaySalutation?.let { Text(it, style = SalutationStyle, color = Navy) }
            paragraphs.firstOrNull()?.let { DropCapParagraph(it) }
            // The one line the letter offers to share — the whole letter is private.
            letter.shareLine?.let { line -> PullQuote(line) { onShareLine(line) } }
            paragraphs.drop(1).forEach { Text(it, style = BodyStyle, color = Ink) }
            if (EditorialLetter.showWeekInGrace(letter)) WeekInGrace(EditorialLetter.figures(letter), letter.moments)
            VerseCard(letter)
            // ONE next step, never a menu — server-computed, so it only points
            // at something that exists.
            letter.nextStep?.let { step ->
                NavyStepBand(
                    kicker = "ONE STEP FOR THIS WEEK",
                    title = step.label,
                    line = "",
                    verb = EditorialLetter.stepVerb(step.route),
                    icon = Lucide.BookOpen,
                    onClick = { onNextStep(step) },
                )
            }
            Signature(letter.signedBy)
            Column(Modifier.fillMaxWidth().rule(top = true)) {
                FooterRow(Lucide.Reply, EditorialLetter.WRITE_BACK, writeBack, onWriteBack)
                if (pdf != null) FooterRow(Lucide.FileText, "Keep this letter as a PDF", pdf, onKeepPdf)
                previous?.let { FooterRow(Lucide.BookOpen, EditorialLetter.previousLabel(it, letter), LetterRowState(), onOpenPrevious) }
            }
        }
    }
}

/** A 1 dp hairline along the top or the bottom edge. */
private fun Modifier.rule(top: Boolean = false, bottom: Boolean = false, color: Color = Hairline): Modifier = drawBehind {
    val w = 1.dp.toPx()
    if (top) drawLine(color, Offset(0f, w / 2), Offset(size.width, w / 2), w)
    if (bottom) drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

@Composable
private fun Masthead(letter: PastoralLetter) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Seal), contentAlignment = Alignment.Center) {
                CappedFontScale(1f) { Text("N", style = nuruSerif(15, FontWeight.SemiBold), color = SealInk) }
            }
            Text("NURU PLACE", style = KickerStyle, color = GoldText)
        }
        CappedFontScale(EVERYDAY_MAX_FONT_SCALE) {
            WholeWordsText("The Sunday Letter", style = NameplateStyle, color = Navy, textAlign = TextAlign.Center)
        }
        // The gold double rule.
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(GoldRule))
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(GoldRule))
        }
        EditorialLetter.mastheadLine(letter.issueNo, letter.weekOf).takeIf { it.isNotEmpty() }?.let {
            Text(it.uppercase(Locale.ENGLISH), style = nuruSans(11, tracking = 1.2f), color = Muted, textAlign = TextAlign.Center)
        }
    }
}

/** The week's photograph, full width; the theme art beneath it shows while it
 *  loads and stays when there is none or it can't be had. */
@Composable
private fun Hero(letter: PastoralLetter) {
    val url = EditorialLetter.photoUrl(letter.photo)
    var failed by remember(url) { mutableStateOf(false) }
    // Clipped: the theme art's glow reaches past its own edge.
    Box(Modifier.fillMaxWidth().height(270.dp).clipToBounds()) {
        LetterHero(letter.artKey, height = 270.dp, scrim = false)
        if (url != null && !failed) {
            AsyncImage(
                model = url,
                contentDescription = letter.photo?.alt?.trim()?.takeIf { it.isNotEmpty() },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(270.dp),
                onState = { if (it is AsyncImagePainter.State.Error) failed = true },
            )
        }
    }
    val caption = EditorialLetter.caption(letter.photo)
    if (url != null && !failed && caption != null) {
        Text(caption, style = CaptionStyle, color = Muted, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp))
    }
    Spacer(Modifier.height(6.dp))
}

/** The first paragraph with its gold drop cap: the cap's capitals level with
 *  the first line's, its foot on the [DROP_CAP_LINES]th line's baseline, the
 *  lines beside it set short and the rest full width. Read aloud whole. */
@Composable
private fun DropCapParagraph(paragraph: String) {
    val cap = dropCapSplit(paragraph)
    if (cap == null) {
        Text(paragraph, style = BodyStyle, color = Ink)
        return
    }
    if (largeText()) {
        // Past the everyday sizes: the gold initial in line, nothing crowded.
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = GoldText, fontWeight = FontWeight.SemiBold)) { append(cap.initial) }
                append(cap.rest)
            },
            style = BodyStyle, color = Ink,
            modifier = Modifier.clearAndSetSemantics { contentDescription = paragraph },
        )
        return
    }
    val body = BodyStyle
    val capStyle = DropCapStyle
    BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = paragraph }) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val capLayout = remember(cap.initial, capStyle, density) {
            measurer.measure(cap.initial, capStyle, softWrap = false, maxLines = 1, density = density)
        }
        val gap = with(density) { 8.dp.roundToPx() }
        val beside = (constraints.maxWidth - capLayout.size.width - gap).coerceAtLeast(1)
        val restLayout = remember(cap.rest, body, beside, density) {
            measurer.measure(cap.rest, body, constraints = Constraints(maxWidth = beside), density = density)
        }
        val (besideText, belowText) = remember(restLayout) {
            dropCapLines(cap.rest, (0 until restLayout.lineCount).map { restLayout.getLineEnd(it) })
        }
        // The cap's foot on the DROP_CAP_LINES-th line's baseline: that line's
        // own when the text reaches it, else one line pitch (the style's line
        // height — line 0's box is trimmed at its top, so it isn't a pitch)
        // per line below the first.
        val pitch = with(density) { body.lineHeight.toPx() }
        val capBaseline = if (restLayout.lineCount >= DROP_CAP_LINES) {
            restLayout.getLineBaseline(DROP_CAP_LINES - 1)
        } else {
            restLayout.getLineBaseline(0) + pitch * (DROP_CAP_LINES - 1)
        }
        val capY = (capBaseline - capLayout.firstBaseline).roundToInt()
        val depth = with(density) { (capBaseline + restLayout.getLineBottom(0) - restLayout.getLineBaseline(0)).toDp() }
        Column {
            Box(Modifier.fillMaxWidth().heightIn(min = depth)) {
                Text(
                    besideText, style = body, color = Ink,
                    modifier = Modifier.padding(start = with(density) { (capLayout.size.width + gap).toDp() }),
                )
                // Placed by its baseline and taking no height of its own: the
                // paragraph is as tall as its lines (at least the cap's depth).
                Text(
                    cap.initial, style = capStyle, color = GoldText, softWrap = false, maxLines = 1,
                    modifier = Modifier.layout { measurable, c ->
                        val p = measurable.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                        layout(p.width, 0) { p.place(0, capY) }
                    },
                )
            }
            if (belowText.isNotEmpty()) Text(belowText, style = body, color = Ink)
        }
    }
}

/** The share line between gold rules, with "Share this line". */
@Composable
private fun PullQuote(line: String, onShare: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .rule(top = true, bottom = true, color = GoldRule)
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WholeWordsText(EditorialLetter.pullQuote(line), style = PullQuoteStyle, color = GoldText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val pill = RoundedCornerShape(999.dp)
            Row(
                Modifier.clip(pill).border(1.dp, GoldText.copy(alpha = 0.5f), pill)
                    .clickable(onClickLabel = "Share this line") { onShare() }
                    .heightIn(min = 36.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Lucide.Share2, contentDescription = null, tint = GoldText, modifier = Modifier.size(14.dp))
                Text("Share this line", style = nuruSans(13, FontWeight.SemiBold), color = GoldText)
            }
        }
    }
}

/** "YOUR WEEK, IN GRACE": the week's figures (when it has any) and its
 *  moments, each with a gold check. */
@Composable
private fun WeekInGrace(figures: List<LetterFigure>, moments: List<String>) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(1.dp, Hairline, shape)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("YOUR WEEK, IN GRACE", style = KickerStyle, color = GoldText)
        if (figures.isNotEmpty()) FigureGrid(figures)
        if (moments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                moments.forEach { m ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Lucide.Check, contentDescription = null, tint = GoldText, modifier = Modifier.padding(top = 2.dp).size(14.dp))
                        Text(m, style = MomentStyle, color = Ink)
                    }
                }
            }
        }
    }
}

/** Three columns, each figure with a gold rule at its left, the rules as tall
 *  as the tallest cell; past the everyday sizes, one figure under another. */
@Composable
private fun FigureGrid(figures: List<LetterFigure>) {
    if (largeText()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            figures.forEach { FigureCell(it, Modifier.fillMaxWidth().leftRule()) }
        }
        return
    }
    Layout(
        content = { figures.forEach { FigureCell(it, Modifier) } },
        modifier = Modifier.fillMaxWidth().drawBehind {
            val w = 1.dp.toPx()
            for (i in figures.indices) {
                val x = size.width * i / FIGURE_COLUMNS + w / 2
                drawLine(FigureRule, Offset(x, 0f), Offset(x, size.height), w)
            }
        },
    ) { measurables, constraints ->
        val column = constraints.maxWidth / FIGURE_COLUMNS
        val placeables = measurables.map { it.measure(Constraints(minWidth = column, maxWidth = column)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, p -> p.placeRelative(i * column, 0) }
        }
    }
}

/** A gold rule down the left edge (a stacked figure). */
private fun Modifier.leftRule(): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawLine(FigureRule, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
}

@Composable
private fun FigureCell(figure: LetterFigure, modifier: Modifier) {
    Column(modifier.padding(horizontal = 6.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WholeWordsText(figure.value, style = FigureStyle, color = Navy)
        Text(figure.label, style = nuruSans(12).copy(lineHeight = scaledLineHeight(16)), color = Muted)
    }
}

/** The verse in full, on its cream card — the reference alone, as the letter
 *  has always shown it, when the words aren't known. */
@Composable
private fun VerseCard(letter: PastoralLetter) {
    val kicker = EditorialLetter.verseKicker(letter) ?: return
    val text = EditorialLetter.verseText(letter.scripture)
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(VerseGround).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (text != null) {
            CappedFontScale(EVERYDAY_MAX_FONT_SCALE) {
                Box(Modifier.height(30.dp)) {
                    Text("“", style = QuoteMarkStyle, color = GoldRule, modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true))
                }
            }
            Text(text, style = VerseStyle, color = Navy)
            Text(kicker.uppercase(Locale.ENGLISH), style = KickerStyle, color = GoldText)
        } else {
            Text("SCRIPTURE", style = KickerStyle, color = GoldText)
            Text(kicker, style = nuruSerif(18, FontWeight.SemiBold), color = Navy)
        }
    }
}

/** "With grace," and the hand that signed — the church's name in type when
 *  the letter doesn't say who signed (never a handwriting it didn't carry). */
@Composable
private fun Signature(signedBy: LetterSignedBy?) {
    Column(Modifier.padding(top = 6.dp)) {
        Text("With grace,", style = GraceStyle, color = Muted)
        val name = EditorialLetter.signerName(signedBy)
        if (name != null) {
            CappedFontScale(EVERYDAY_MAX_FONT_SCALE) { Text(name, style = SignatureStyle, color = Navy) }
            Text(EditorialLetter.signerRole(signedBy).uppercase(Locale.ENGLISH), style = KickerStyle, color = GoldText)
        } else {
            Text(EditorialLetter.signerRole(signedBy), style = nuruSerif(16, FontWeight.SemiBold), color = Navy)
        }
    }
}

@Composable
private fun FooterRow(icon: ImageVector, label: String, state: LetterRowState, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().rule(bottom = true)
            .clickable(enabled = !state.busy, onClickLabel = label) { onClick() }
            .padding(horizontal = 2.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = GoldText, modifier = Modifier.size(18.dp))
            Text(label, style = nuruSans(14), color = Navy, modifier = Modifier.weight(1f))
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(18.dp), color = GoldText, strokeWidth = 2.dp)
            } else {
                Icon(Lucide.ChevronRight, contentDescription = null, tint = ChevronInk, modifier = Modifier.size(18.dp))
            }
        }
        state.error?.let { Text(it, style = nuruSans(12), color = ErrorInk, modifier = Modifier.padding(start = 30.dp, top = 6.dp)) }
    }
}
