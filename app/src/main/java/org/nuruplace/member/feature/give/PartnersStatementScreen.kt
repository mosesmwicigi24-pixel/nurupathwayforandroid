package org.nuruplace.member.feature.give

// The Partners statement — Give → Partners → Statement (owner 2026-09-25:
// "Have the statement separate for partners and give statements separate").
// Its own route ("partners-statement?year="), the Android half of the same
// design as iOS PartnersStatementView.swift; keep in step.
//
// Statement v2 (docs/PARTNERS_PROGRAMME.md §3d, 2026-09-25) leads with what
// the partnership did. One navy hero (back · share), then:
//   HERO       "PARTNERS STATEMENT · 2026", "Thank you, Moses.", "Partner
//              since Sep 2026 · Builder", and three tiles when the server
//              sends `impact`: Disciples carried (never 0 — below the first
//              it is progress toward it), Kept N of M (hidden before anything
//              is due), Given (compact; full amount read aloud)
//   YEAR       chips, this year back to the join year, at most four — the
//              Partners tab's own list (partnerStatementYears)
//   SUMMARY    Pledged / Paid / Remaining — the server's numbers when it
//              sends them, else PartnerStatementMath over the same payments
//   FAITHFULNESS  twelve squares Jan→Dec (kept · late · missed · upcoming ·
//              none) and one line; hidden without `months`
//   COMMITMENTS  one row per pledge: name, amount line, state chip,
//              "KSh 6,000 paid · 3 of 4 kept" or "KSh 20,000 paid", and
//              "Church raised N%" on a need; "Remaining this year" in the header
//   SINCE YOU BEGAN  navy card, the church-wide season; hidden without `season`
//   PAYMENTS   pledge-tied only. First, when the server sends `pending`, one
//              PROCESSING card — day · pledge · amber "Waiting for M-Pesa" ·
//              amount — counted in NO total; then one card per month with its
//              subtotal; a row is day · pledge · method + receipt code ·
//              amount, tap → receipt
//   TOTAL      the year's foot
//   Download PDF (navy — the money-document action) · Giving statement
//   (outlined) — the general statement is one tap away, never mixed in here.
//
// Every number is derived in PartnersStatementLogic.kt (pure, pinned by
// PartnersStatementLogicTest) from GET /giving/statements?year= and
// GET /giving/partnership; the PDF is GET /giving/partners/statement.pdf
// fetched through the authed client and handed out by the manifest
// FileProvider, exactly as the receipt's share does.
//
// Freshness (GivingEvents.kt): the standing and the year's statement refetch
// on every entry and every resume, keeping the page on screen while they do,
// and the ViewModel reloads on GivingEvents. While a PROCESSING row is on
// screen the page also polls — every 10 s for at most two minutes, stopping
// when none is left, when it leaves or on ON_PAUSE — so an M-Pesa payment
// settles in front of the member (GivingEvents.pollWhilePending).

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.StatementFaithfulness
import org.nuruplace.member.data.net.StatementImpact
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.StatementPendingPayment
import org.nuruplace.member.data.net.StatementPledge
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private val Capsule = RoundedCornerShape(999.dp)

// Statement v2 palette (spec §3d). The hero's gold reads on navy; the strip's
// five marks: kept green, late gold, missed navy, upcoming white with a dashed
// ink-300 edge, none the muted track.
private val HERO_GOLD = Color(0xFFE6CA68)
private val MARK_KEPT = Color(0xFF16A34A)
private val MARK_DASH = Color(0xFFB5BDC9)
private val MARK_NONE = Color(0xFFEEF1F5)

/** MainShell route for the partners statement on `year` (PARTNERS_STATEMENT_ROUTE there). */
fun partnersStatementRoute(year: Int): String = "partners-statement?year=$year"

class PartnersStatementViewModel : ViewModel() {
    var partnership by mutableStateOf<Partnership?>(null); private set
    /** GET /giving/statements?year= by year; a year loads when its chip is tapped. */
    var statements by mutableStateOf<Map<Int, GivingStatement>>(emptyMap()); private set
    var year by mutableIntStateOf(LocalDate.now().year); private set
    /** True while ANY statement fetch is in flight — a chip tapped mid-load
     *  must not flash the "couldn't load" card when the other fetch lands. */
    var loading by mutableStateOf(false); private set
    private var inFlight = 0
    var error by mutableStateOf<String?>(null); private set
    var pdfBusy by mutableStateOf(false); private set
    /** A failed PDF fetch, said once under the button; cleared on the next try. */
    var pdfError by mutableStateOf<String?>(null); private set

    private val freshness = GivingFreshness()
    /** The route's year is applied once — a resume after the member tapped
     *  another chip must not snap back to it. */
    private var started = false
    // Latest request wins (entry, resume and event fetches can overlap).
    private var partnershipSeq = 0
    private val yearSeq = mutableMapOf<Int, Int>()

    init {
        viewModelScope.launch {
            GivingEvents.changed.debouncedGivingReloads().collect {
                if (started && freshness.shouldRefetchAfterEvent()) load()
            }
        }
    }

    /** First showing: land on the route's year, then fetch. */
    fun start(initialYear: Int?) {
        if (!started) {
            started = true
            initialYear?.let { year = it }
        }
        refresh()
    }

    /** Entry / resume (stale-while-revalidate): the standing and the shown
     *  year, unless a fetch has just started. A no-op before [start] — the
     *  resume that lands first must not fetch the wrong year. */
    fun refresh() {
        if (started && freshness.shouldRefetchOnEntry()) load()
    }

    fun load() {
        freshness.fetchStarted()
        val seq = ++partnershipSeq
        viewModelScope.launch {
            begin()
            val p = runCatching { Net.client.api.partnership() }
            if (seq == partnershipSeq) {
                p.getOrNull()?.let { partnership = it }
                p.exceptionOrNull()?.let { error = ApiException.message(it) }
            }
            end()
        }
        loadYear(year)
    }

    fun select(y: Int) {
        year = y
        if (statements[y] == null) loadYear(y)
    }

    /** The shown year's statement has PROCESSING rows. */
    fun showsPendingRows(): Boolean = statements[year]?.let { pendingPaymentRows(it).isNotEmpty() } == true

    /** One POLL tick: skipped while a fetch is still in flight (a slow
     *  network never stacks requests) or has just started; otherwise the
     *  same guarded load() as every other trigger. */
    fun pollPending() {
        if (!loading) refresh()
    }

    fun loadYear(y: Int) {
        val seq = (yearSeq[y] ?: 0) + 1
        yearSeq[y] = seq
        viewModelScope.launch {
            begin()
            val r = runCatching { Net.client.api.statements(y) }
            if (yearSeq[y] == seq) {
                r.onSuccess { statements = statements + (y to it) }
                    .onFailure { error = ApiException.message(it) }
            }
            end()
        }
    }

    private fun begin() { inFlight++; loading = true; error = null }

    private fun end() { inFlight--; loading = inFlight > 0 }

    /** GET /giving/partners/statement.pdf?year= through the authed client →
     *  cache/shared/nuru-partners-statement-<year>.pdf → the share sheet
     *  (sharePdfAuthed). A miss — offline, or 404 for a member who was never
     *  a partner — reads as one quiet line; nothing on screen changes. */
    fun sharePdf(context: Context) {
        if (pdfBusy) return
        val y = year
        val app = context.applicationContext
        viewModelScope.launch {
            pdfBusy = true; pdfError = null
            val ok = sharePdfAuthed(
                app, "nuru-partners-statement-$y.pdf", "Nuru Place partners statement $y",
                subject = "Nuru Place partners statement $y", chooserTitle = "Share statement",
            ) { Net.client.api.partnersStatementPdf(y) }
            if (!ok) pdfError = "Couldn't fetch the PDF just now. What you see here is unchanged."
            pdfBusy = false
        }
    }
}

@Composable
fun PartnersStatementScreen(
    /** The year the Partners tab was showing; null lands on this year. */
    initialYear: Int?,
    onBack: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    /** The general giving statement — the outlined button at the foot. */
    onOpenGivingStatement: () -> Unit,
    /** The signed-in member's name (MainShell's `me`) for "Thank you, <first
     *  name>."; the line is left out when it is null or blank. */
    memberName: String? = null,
    // Scoped to this destination: it survives a receipt and back (the page
    // stays while it refetches), and its GivingEvents collector dies with it.
    vm: PartnersStatementViewModel = viewModel(),
) {
    val context = LocalContext.current
    val view = LocalView.current
    // Stale-while-revalidate on every entry and every return to the
    // foreground; refresh() fetches once when the two land together.
    LaunchedEffect(Unit) { vm.start(initialYear) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val p = vm.partnership
    val today = LocalDate.now()
    val years = partnerStatementYears(today.year, partnerDate(p?.membership?.joinedAt ?: p?.since)?.year)
    val year = vm.year
    val s = vm.statements[year]
    // PROCESSING rows resolve by themselves: poll while the page is in front
    // and shows one. Keyed so ON_PAUSE (RESUMED → STARTED), a row appearing
    // or the last one settling restarts or ends it; leaving cancels it.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val hasPending = s != null && pendingPaymentRows(s).isNotEmpty()
    LaunchedEffect(visible, hasPending) {
        pollWhilePending(visible, hasPending = { vm.showsPendingRows() }, poll = { vm.pollPending() })
    }

    Column(Modifier.fillMaxSize().background(GIVE.paper).verticalScroll(rememberScrollState())) {
        PartnersHero(
            year = year,
            firstName = receiptFirstName(memberName, null),
            standing = standingLine(p),
            impact = s?.impact,
            faithfulness = s?.faithfulness,
            onBack = onBack,
            onShare = { Haptics.tap(view); vm.sharePdf(context) },
        )
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                years.forEach { y -> YearChip(y, on = y == year) { Haptics.tick(view); vm.select(y) } }
            }

            when {
                s == null && vm.loading -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                    CircularProgressIndicator(color = GIVE.gold)
                }
                s == null -> Column(Modifier.partnerCard(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(vm.error ?: "We couldn't load your statement just now.", style = giInter(13), color = GIVE.sub)
                    TextButton(onClick = { vm.loadYear(year) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Try again", style = giInter(13, FontWeight.SemiBold), color = GIVE.gold)
                    }
                }
                else -> {
                    val pledges = p?.pledges.orEmpty()
                    SummaryCard(partnerStatementSummary(year, s, pledges))
                    faithfulnessMarks(s.months)?.let { marks ->
                        val nextDue = if (year == today.year) nextPledgeDue(p, today) else null
                        FaithfulnessCard(marks, faithfulnessLine(marks, nextDue, today))
                    }
                    CommitmentsCard(partnerStatementPledges(year, s, pledges, today))
                    seasonLine(s.season)?.let { SeasonCard(it) }
                    PaymentsSection(year, pendingPaymentRows(s), paymentsByMonth(s.payments), onOpenReceipt)
                }
            }
            if (vm.error != null && s != null) {
                Text("Couldn't refresh just now — showing what we last had.", style = giInter(11), color = GIVE.tertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadPdfButton(busy = vm.pdfBusy) { Haptics.tap(view); vm.sharePdf(context) }
                vm.pdfError?.let {
                    Text(it, style = giInter(11), color = GIVE.danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                GivingStatementButton(onOpenGivingStatement)
            }
        }
    }
}

/** "Partner since Sep 2026 · Builder" — nothing until the standing has loaded. */
private fun standingLine(p: Partnership?): String? {
    if (p == null) return null
    val since = (p.membership?.joinedAt ?: p.since)?.let(PartnerFormat::monthYear)
    val tier = p.tier?.name?.takeIf { it.isNotBlank() }
    return listOfNotNull(since?.let { "Partner since $it" } ?: "Partner", tier).joinToString(" · ")
}

// ── Pieces ───────────────────────────────────────────────────────────────────

/** The navy hero (spec §3d): back · share, the eyebrow, the thank-you, the
 *  standing, and — when the server sends `impact` — the three tiles. */
@Composable
private fun PartnersHero(
    year: Int,
    firstName: String?,
    standing: String?,
    impact: StatementImpact?,
    faithfulness: StatementFaithfulness?,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(GIVE.statementHeader),
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(listOf(GIVE.gold.copy(alpha = 0.26f), Color.Transparent), center = Offset(820f, 40f), radius = 420f),
            ),
        )
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.weight(1f))
                HeroButton(Icons.Filled.Share, "Share statement PDF", onShare)
            }
            Text(
                "PARTNERS STATEMENT · $year", style = giInter(10, FontWeight.Bold, 2.2f), color = HERO_GOLD,
                modifier = Modifier.padding(top = 14.dp),
            )
            firstName?.let {
                Text("Thank you, $it.", style = giSerif(24, FontWeight.SemiBold, -0.48f), color = Color.White, modifier = Modifier.padding(top = 6.dp))
            }
            standing?.let {
                Text(it, style = giInter(12), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
            }
            impact?.let { ImpactTiles(it, faithfulness, Modifier.padding(top = 16.dp)) }
        }
    }
}

/** Header chrome on navy — the giving statement's translucent circle. */
@Composable
private fun HeroButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

/** Disciples carried · Kept · Given. The disciples tile widens while it holds
 *  the progress sentence (below the first disciple) so the row stays level. */
@Composable
private fun ImpactTiles(impact: StatementImpact, faithfulness: StatementFaithfulness?, modifier: Modifier = Modifier) {
    val disciples = disciplesTile(impact)
    val kept = keptTileValue(faithfulness)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (disciples) {
            is DisciplesTile.Carried -> HeroTile(
                "Disciples carried",
                "${disciples.count} disciple${if (disciples.count == 1) "" else "s"} carried through a level",
                Modifier.weight(1f),
            ) {
                Text("${disciples.count}", style = giSerif(26, FontWeight.SemiBold), color = HERO_GOLD)
                TileCaption("through a level")
            }
            is DisciplesTile.Toward -> HeroTile("Disciples carried", disciples.text, Modifier.weight(1.5f)) {
                Box(
                    Modifier.padding(top = 8.dp, bottom = 6.dp).fillMaxWidth().height(4.dp)
                        .clip(Capsule).background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(Modifier.fillMaxWidth(disciples.fraction).fillMaxHeight().clip(Capsule).background(HERO_GOLD))
                }
                TileCaption(disciples.text)
            }
        }
        kept?.let {
            HeroTile("Kept", "Kept $it commitments", Modifier.weight(1f)) {
                Text(it, style = giSerif(22, FontWeight.SemiBold), color = Color.White, maxLines = 1)
                TileCaption("commitments")
            }
        }
        HeroTile("Given", "Given ${ksh(impact.paidMinor)} toward pledges", Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 12.sp)) { append("KSh ") }
                    append(compactAmount(impact.paidMinor))
                },
                style = giSerif(22, FontWeight.SemiBold), color = Color.White, maxLines = 1,
            )
            TileCaption("toward pledges")
        }
    }
}

/** One translucent tile; the whole tile reads as one sentence to TalkBack. */
@Composable
private fun HeroTile(label: String, spoken: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(10.dp),
    ) {
        Text(label, style = giInter(10, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun TileCaption(text: String) {
    Text(text, style = giInter(10), color = Color.White.copy(alpha = 0.6f))
}

/** FAITHFULNESS: twelve squares Jan→Dec, "Jan"/"Dec" under the ends, one line. */
@Composable
private fun FaithfulnessCard(marks: List<MonthMark>, line: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("FAITHFULNESS")
        Column(Modifier.partnerCard()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                marks.forEachIndexed { i, m -> MonthSquare(i + 1, m) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Jan", style = giInter(9), color = GIVE.tertiary)
                Spacer(Modifier.weight(1f))
                Text("Dec", style = giInter(9), color = GIVE.tertiary)
            }
            line?.let { Text(it, style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

private fun markSpoken(m: MonthMark): String = when (m) {
    MonthMark.Kept -> "kept on time"
    MonthMark.Late -> "kept late"
    MonthMark.Missed -> "missed"
    MonthMark.Upcoming -> "upcoming"
    MonthMark.None -> "nothing due"
}

@Composable
private fun MonthSquare(month: Int, mark: MonthMark) {
    val shape = RoundedCornerShape(5.dp)
    val name = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val base = Modifier.size(20.dp).clip(shape).semantics { contentDescription = "$name, ${markSpoken(mark)}" }
    Box(
        when (mark) {
            MonthMark.Kept -> base.background(MARK_KEPT)
            MonthMark.Late -> base.background(GIVE.gold)
            MonthMark.Missed -> base.background(GIVE.navy)
            MonthMark.None -> base.background(MARK_NONE)
            MonthMark.Upcoming -> base.background(GIVE.white).drawBehind {
                val w = 1.dp.toPx()
                drawRoundRect(
                    color = MARK_DASH,
                    topLeft = Offset(w / 2, w / 2),
                    size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(5.dp.toPx() - w / 2),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                )
            }
        },
    )
}

/** SINCE YOU BEGAN — the church-wide season, never this member's money
 *  traced to an outcome (Partnership's own rule). */
@Composable
private fun SeasonCard(line: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GIVE.statementHeader).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SINCE YOU BEGAN", style = giInter(9, FontWeight.SemiBold, 1.6f), color = HERO_GOLD)
        Text(line, style = giSerif(17, FontWeight.Medium), color = Color.White)
    }
}

@Composable
private fun YearChip(year: Int, on: Boolean, onClick: () -> Unit) {
    Text(
        "$year", style = giInter(12, FontWeight.SemiBold), color = if (on) Color.White else GIVE.navy,
        modifier = Modifier.clip(Capsule).background(if (on) GIVE.navy else GIVE.white)
            .border(1.dp, if (on) GIVE.navy else GIVE.border, Capsule)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SummaryCard(sum: StatementSummary) {
    Row(Modifier.partnerCard()) {
        SummaryColumn("PLEDGED", ksh(sum.pledgedMinor), GIVE.navy, Modifier.weight(1f))
        SummaryColumn("PAID", ksh(sum.paidMinor), GIVE.successText, Modifier.weight(1f))
        SummaryColumn("REMAINING", ksh(sum.remainingMinor), GIVE.goldLo, Modifier.weight(1f))
    }
}

/** COMMITMENTS (was YOUR PLEDGES): "Remaining this year KSh X" at the
 *  header's right when the server sends each pledge's remaining_year_minor. */
@Composable
private fun CommitmentsCard(rows: List<StatementPledge>) {
    val remaining = remainingThisYear(rows)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("COMMITMENTS")
            Spacer(Modifier.weight(1f))
            remaining?.let {
                Text("Remaining this year ${ksh(it)}", style = giInter(11, FontWeight.SemiBold), color = GIVE.goldLo)
            }
        }
        Column(Modifier.partnerCard()) {
            if (rows.isEmpty()) {
                Text("No pledges counted this year.", style = giInter(13), color = GIVE.sub)
            } else {
                rows.forEachIndexed { i, e ->
                    if (i > 0) Hairline()
                    PledgeRow(e)
                }
            }
        }
    }
}

/** active → green Active · paused → grey · fulfilled → green · cancelled → grey. */
private fun pledgeStateChip(status: String): Triple<String, Color, Color> = when (status) {
    "paused" -> Triple("Paused", GIVE.mutedBg, GIVE.ink600)
    "fulfilled" -> Triple("Fulfilled", GIVE.successBg, GIVE.successText)
    "cancelled" -> Triple("Cancelled", GIVE.mutedBg, GIVE.ink600)
    else -> Triple("Active", GIVE.successBg, GIVE.successText)
}

@Composable
private fun PledgeRow(e: StatementPledge) {
    val (chipText, chipBg, chipFg) = pledgeStateChip(e.status)
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(e.title.ifBlank { "General partnership" }, style = giInter(14, FontWeight.SemiBold), color = GIVE.navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pledgeAmountLine(e), style = giInter(12), color = GIVE.sub, modifier = Modifier.padding(top = 2.dp))
            }
            StateChip(chipText, chipBg, chipFg)
        }
        Text(pledgeProgressLine(e), style = giInter(11, FontWeight.Medium), color = GIVE.ink600)
        churchRaisedLine(e)?.let { Text(it, style = giInter(10, FontWeight.Medium), color = GIVE.tertiary) }
    }
}

@Composable
private fun PaymentsSection(
    year: Int,
    pending: List<StatementPendingPayment>,
    months: List<StatementMonth>,
    onOpenReceipt: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("PAYMENTS")
        // Started, not settled — shown first, counted nowhere.
        if (pending.isNotEmpty()) PendingCard(pending, onOpenReceipt)
        if (months.isEmpty()) {
            if (pending.isEmpty()) {
                Box(Modifier.partnerCard()) { Text("No pledge payments in $year.", style = giInter(13), color = GIVE.sub) }
            }
        } else {
            months.forEach { m -> MonthCard(m, onOpenReceipt) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TOTAL PAID $year", style = giInter(11, FontWeight.Bold, 1.4f), color = GIVE.navy)
                Spacer(Modifier.weight(1f))
                Text(ksh(statementYearTotal(months)), style = giSerif(18, FontWeight.Bold), color = GIVE.gold)
            }
        }
    }
}

private fun monthLabel(m: StatementMonth): String =
    if (m.undated) "UNDATED" else Month.of(m.month).getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH)

/** One month: its name and subtotal, then the rows newest first. */
@Composable
private fun MonthCard(m: StatementMonth, onOpenReceipt: (String) -> Unit) {
    Column(Modifier.partnerCard()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(monthLabel(m), style = giInter(11, FontWeight.Bold, 1.1f), color = GIVE.overline)
            Spacer(Modifier.weight(1f))
            Text(ksh(m.subtotalMinor), style = giInter(12, FontWeight.SemiBold), color = GIVE.sub)
        }
        m.payments.forEachIndexed { i, pay ->
            if (i > 0) Hairline()
            PaymentRow(pay, onOpenReceipt)
        }
    }
}

/** "25" · "School fees" over "M-Pesa · UIKJ2713B5" · "KSh 2,000" — the method
 *  when the row carries one, else the fund by the server's name, else by code. */
@Composable
private fun PaymentRow(pay: StatementPayment, onOpenReceipt: (String) -> Unit) {
    val day = partnerDate(pay.occurredAt)?.dayOfMonth?.toString() ?: "—"
    val what = pay.pledgeTitle?.takeIf { it.isNotBlank() } ?: pay.title?.takeIf { it.isNotBlank() } ?: "Pledge"
    val via = listOfNotNull(
        pay.method?.takeIf { it.isNotBlank() }?.let(::giveMethodLabel)
            ?: pay.fundName?.takeIf { it.isNotBlank() }
            ?: pay.fund?.takeIf { it.isNotBlank() }?.let { giveFund(it).name },
        pay.receiptCode?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = pay.transactionId.isNotBlank()) { onOpenReceipt(pay.transactionId) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(day, style = giInter(13, FontWeight.SemiBold), color = GIVE.tertiary, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(what, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (via.isNotBlank()) Text(via, style = giInter(11), color = GIVE.sub, modifier = Modifier.padding(top = 2.dp))
        }
        Text(ksh(pay.amountMinor), style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
    }
}

/** PROCESSING — pledge payments the server has not settled yet: "Not counted
 *  yet" at the head, then day · pledge · amber chip · amount (muted: it is in
 *  no total). Tap → the receipt, which says Processing too. */
@Composable
private fun PendingCard(rows: List<StatementPendingPayment>, onOpenReceipt: (String) -> Unit) {
    Column(Modifier.partnerCard()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PROCESSING", style = giInter(11, FontWeight.Bold, 1.1f), color = GIVE.overline)
            Spacer(Modifier.weight(1f))
            Text("Not counted yet", style = giInter(12, FontWeight.SemiBold), color = GIVE.sub)
        }
        rows.forEachIndexed { i, pay ->
            if (i > 0) Hairline()
            PendingRow(pay, onOpenReceipt)
        }
    }
}

@Composable
private fun PendingRow(pay: StatementPendingPayment, onOpenReceipt: (String) -> Unit) {
    val day = partnerDate(pay.at)?.dayOfMonth?.toString() ?: "—"
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = pay.transactionId.isNotBlank()) { onOpenReceipt(pay.transactionId) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(day, style = giInter(13, FontWeight.SemiBold), color = GIVE.tertiary, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                pay.pledgeTitle?.takeIf { it.isNotBlank() } ?: "Pledge",
                style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            StateChip(pendingChipText(pay.method), Nuru.warningBg, Nuru.answeredText)
        }
        Text(money(pay.amountMinor, pay.currency), style = giInter(13, FontWeight.SemiBold), color = GIVE.tertiary)
    }
}

/** Navy fill = the money-document action (spec §3 button roles). */
@Composable
private fun DownloadPdfButton(busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Capsule).background(GIVE.navy).clickable(enabled = !busy) { onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(if (busy) "Preparing PDF…" else "Download PDF", style = giInter(14, FontWeight.SemiBold), color = Color.White)
    }
}

/** Navy outline = secondary: the general giving statement, one tap away. */
@Composable
private fun GivingStatementButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Capsule).background(GIVE.white).border(1.5.dp, GIVE.navy, Capsule).clickable { onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Description, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Giving statement", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
    }
}
