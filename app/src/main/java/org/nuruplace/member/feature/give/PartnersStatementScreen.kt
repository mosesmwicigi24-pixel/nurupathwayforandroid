package org.nuruplace.member.feature.give

// The Partners statement — Give → Partners → Statement (owner 2026-09-25:
// "Have the statement separate for partners and give statements separate").
// Its own route ("partners-statement?year="), the Android half of the same
// design as iOS PartnersStatementView.swift; keep in step.
//
// Statement v2 (docs/PARTNERS_PROGRAMME.md §3d, 2026-09-25) leads with what
// the partnership did — laid out and worded as iOS PartnersStatementView:
//   BAR        pinned navy: back · share PDF (a spinner while it fetches) —
//              the hero scrolls under it, so the way back never scrolls away
//   HERO       "PARTNERS STATEMENT · 2026", "Thank you, Moses." (plain
//              "Thank you." without a name), "Partner since Sep 2026 ·
//              Builder", and the tiles when the server sends `impact`:
//              DISCIPLES CARRIED (never 0 — below the first it is the bar,
//              "KSh 5,000 of 20,000" and what it is toward, full width, with
//              KEPT and GIVEN side by side beneath; from one up, three
//              across), KEPT "5 of 6" · "commitments · 1 late" (hidden before
//              anything is due), GIVEN compact · "toward pledges" (a second
//              currency said in the caption); each tile read aloud whole
//   YEAR       chips, this year back to the join year, at most four — the
//              Partners tab's own list (partnerStatementYears); then the
//              "Couldn't refresh just now" line when a refetch failed
//   SUMMARY    Pledged / Paid / Remaining — only without the tiles (an older
//              server sends no `impact`)
//   FAITHFULNESS  twelve squares Jan→Dec (kept · late · missed · upcoming ·
//              none), read aloud as one sentence, and one line; hidden
//              without `months`
//   COMMITMENTS  "Remaining this year KSh X" at the header's right; one row
//              per pledge — name, promise, Behind / On track / Paused /
//              Fulfilled / Cancelled, "KSh 6,000 paid · 3 of 4 kept" and
//              "Church raised N%" on a need — a tap opens the pledge
//   SINCE YOU BEGAN  navy card, the church-wide season; hidden without `season`
//   PAYMENTS   one card, pledge-tied only: PROCESSING · "not yet counted"
//              first (counted in NO total), then "SEPTEMBER 2026" · subtotal
//              and its rows — "Sat 20" · pledge over method + receipt code ·
//              amount, tap → receipt — then TOTAL PAID 2026 at the foot
//   ACTIONS    Download PDF (navy — the money-document action) · "Giving
//              statement →" (outlined) · "Every gift, pledged or not, is on
//              your giving statement." The general statement is one tap
//              away, never mixed in here.
// Pull down to refresh the standing and the year.
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import org.nuruplace.member.data.net.Pledge
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.StatementPendingPayment
import org.nuruplace.member.data.net.StatementPledge
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.NuruRefreshBox
import org.nuruplace.member.ui.theme.Nuru
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import org.nuruplace.member.ui.icons.Lucide

private val Capsule = RoundedCornerShape(999.dp)

// Statement v2 palette (spec §3d). The eyebrow's light gold reads on navy
// (the count, the bar and SINCE YOU BEGAN take the theme's gold, as iOS);
// the strip's five marks: kept green, late gold, missed navy, upcoming white
// with a dashed ink-300 edge, none the muted track.
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
        // The PDF line spoke of the year that was on screen.
        pdfError = null
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
     *  (sharePdfAuthed). A miss reads as one quiet line in iOS's words
     *  (partnersPdfErrorLine) — a 404 for a member with no partners
     *  statement, offline, or anything else; nothing on screen changes. */
    fun sharePdf(context: Context) {
        if (pdfBusy) return
        val y = year
        val app = context.applicationContext
        viewModelScope.launch {
            pdfBusy = true; pdfError = null
            // The fetch's own failure, kept so the line can say which it was.
            var failure: Throwable? = null
            val ok = sharePdfAuthed(
                app, "nuru-partners-statement-$y.pdf", "Nuru Place partners statement $y",
                subject = "Nuru Place partners statement $y", chooserTitle = "Share statement",
            ) {
                try {
                    Net.client.api.partnersStatementPdf(y)
                } catch (e: Throwable) {
                    failure = e
                    throw e
                }
            }
            if (!ok) {
                val f = failure
                pdfError = partnersPdfErrorLine(status = (f as? HttpException)?.code(), offline = f != null && isOffline(f, app))
            }
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
    /** A COMMITMENTS row → that pledge's own page (iOS PartnersRoute.pledge). */
    onOpenPledge: (String) -> Unit,
    /** The general giving statement — the outlined button at the foot. */
    onOpenGivingStatement: () -> Unit,
    /** The signed-in member's name (MainShell's `me`) for "Thank you, <first
     *  name>."; plain "Thank you." when it is null or blank. */
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
    val tiles = s?.let(::heroTiles)
    // PROCESSING rows resolve by themselves: poll while the page is in front
    // and shows one. Keyed so ON_PAUSE (RESUMED → STARTED), a row appearing
    // or the last one settling restarts or ends it; leaving cancels it.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val hasPending = s != null && pendingPaymentRows(s).isNotEmpty()
    LaunchedEffect(visible, hasPending) {
        pollWhilePending(visible, hasPending = { vm.showsPendingRows() }, poll = { vm.pollPending() })
    }
    // The member pulled the page down; its spinner shows until the reads end.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(vm.loading) { if (!vm.loading) pulled = false }

    Column(Modifier.fillMaxSize().background(GIVE.paper)) {
        // Pinned: the way back never scrolls away, however tall the hero grows.
        StatementTopBar(
            busy = vm.pdfBusy,
            onBack = { Haptics.tap(view); onBack() },
            onShare = { Haptics.tap(view); vm.sharePdf(context) },
        )
        NuruRefreshBox(
            refreshing = pulled && vm.loading,
            onRefresh = { pulled = true; vm.load() },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                PartnersHero(year = year, thankYou = thankYouLine(memberName), standing = standingLine(p), tiles = tiles)
                Column(
                    Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        years.forEach { y ->
                            YearChip(y, on = y == year) { if (y != year) { Haptics.tick(view); vm.select(y) } }
                        }
                    }
                    if (vm.error != null && s != null) {
                        Text(
                            "Couldn't refresh just now — showing what we last had.", style = giInter(11), color = Nuru.ink400,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    when {
                        s == null && vm.loading -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), Alignment.Center) {
                            CircularProgressIndicator(color = GIVE.gold)
                        }
                        s == null -> Row(
                            Modifier.partnerCard(),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(vm.error ?: "We couldn't load your statement just now.", style = giInter(12), color = GIVE.ink600, modifier = Modifier.weight(1f))
                            Text(
                                "Try again", style = giInter(12, FontWeight.SemiBold), color = GIVE.gold,
                                modifier = Modifier.clickable { Haptics.tap(view); vm.loadYear(year) },
                            )
                        }
                        else -> {
                            val pledges = p?.pledges.orEmpty()
                            // No hero tiles (an older server sends no `impact`):
                            // the three numbers stay on their card.
                            if (tiles == null) SummaryCard(partnerStatementSummaries(year, s, pledges))
                            faithfulnessMarks(s.months)?.let { marks ->
                                val nextDue = if (year == today.year) nextPledgeDue(p, today) else null
                                FaithfulnessCard(marks, faithfulnessLine(s.faithfulness, marks, nextDue, today))
                            }
                            CommitmentsCard(year, partnerStatementPledges(year, s, pledges, today), today, onOpenPledge)
                            seasonLine(s.season)?.let { SeasonCard(it) }
                            PaymentsSection(year, pendingPaymentRows(s), paymentsByMonth(s.payments), pledges, onOpenReceipt)
                        }
                    }

                    StatementActions(
                        busy = vm.pdfBusy,
                        error = vm.pdfError,
                        onDownload = { Haptics.tap(view); vm.sharePdf(context) },
                        onOpenGivingStatement = { Haptics.tap(view); onOpenGivingStatement() },
                    )
                }
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

/** The pinned navy bar (iOS topBar): back, and share with its spinner while
 *  the PDF is being fetched. */
@Composable
private fun StatementTopBar(busy: Boolean, onBack: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(GIVE.navy).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SquareButton(Lucide.ArrowLeft, "Back", onClick = onBack)
        Spacer(Modifier.weight(1f))
        SquareButton(Lucide.Share2, "Share PDF", busy = busy, onClick = onShare)
    }
}

/** Header chrome on navy: a translucent rounded square. */
@Composable
private fun SquareButton(icon: ImageVector, label: String, busy: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(40.dp).clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable(enabled = !busy) { onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

/** The navy hero (spec §3d) under the pinned bar: the eyebrow, the
 *  thank-you, the standing, and — when the server sends `impact` — the tiles. */
@Composable
private fun PartnersHero(year: Int, thankYou: String, standing: String?, tiles: HeroTiles?) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(GIVE.navy)
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = 20.dp),
    ) {
        Text("PARTNERS STATEMENT · $year", style = giInter(11, FontWeight.Bold, 1.8f), color = HERO_GOLD)
        Text(thankYou, style = giSerif(26, FontWeight.SemiBold, -0.48f), color = Color.White, modifier = Modifier.padding(top = 6.dp))
        standing?.let {
            Text(it, style = giInter(12), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
        }
        tiles?.let { ImpactTiles(it, Modifier.padding(top = 16.dp)) }
    }
}

/** Three across while the disciples tile holds a count. Below the first
 *  disciple its sentence needs the width: that tile goes full width and Kept
 *  + Given sit side by side beneath it (iOS tileRow). Disciples count
 *  shillings only (the costing is in shillings); Given says every currency
 *  paid, the first large and the rest in its caption. */
@Composable
private fun ImpactTiles(t: HeroTiles, modifier: Modifier = Modifier) {
    when (val d = t.disciples) {
        is DisciplesTile.Toward -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TowardTile(d, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeptAndGiven(t)
            }
        }
        is DisciplesTile.Carried -> Row(
            modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroTile("DISCIPLES CARRIED", d.spoken, Modifier.weight(1f)) {
                Text(
                    "${d.count}", style = giSerif(26, FontWeight.SemiBold), color = GIVE.gold,
                    maxLines = 1, softWrap = false, modifier = Modifier.shrinkToFit(0.6f),
                )
                TileCaption("through a level")
            }
            KeptAndGiven(t)
        }
    }
}

/** Below the first disciple: the bar, "KSh 5,000 of 20,000" and what it is toward. */
@Composable
private fun TowardTile(d: DisciplesTile.Toward, modifier: Modifier) {
    HeroTile("DISCIPLES CARRIED", d.spoken, modifier) {
        Box(
            Modifier.padding(top = 6.dp).fillMaxWidth().height(5.dp)
                .clip(Capsule).background(Color.White.copy(alpha = 0.15f)),
        ) {
            // A sliver shows as soon as anything is given (iOS: never under 4).
            if (d.fraction > 0f) {
                Box(Modifier.widthIn(min = 4.dp).fillMaxWidth(d.fraction).fillMaxHeight().clip(Capsule).background(GIVE.gold))
            }
        }
        Text(
            d.ofLine, style = giInter(11, FontWeight.SemiBold), color = Color.White,
            maxLines = 1, softWrap = false, modifier = Modifier.padding(top = 4.dp).shrinkToFit(0.7f),
        )
        TileCaption(TOWARD_CAPTION)
    }
}

@Composable
private fun RowScope.KeptAndGiven(t: HeroTiles) {
    t.kept?.let { k ->
        HeroTile("KEPT", k.spoken, Modifier.weight(1f)) {
            // The count large and "of 6" small beside it, on one line shrunk
            // to the tile — never cut on a narrow phone.
            Row(Modifier.shrinkToFit(0.6f)) {
                Text(
                    "${k.kept}", style = giSerif(26, FontWeight.SemiBold), color = Color.White,
                    maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline(),
                )
                Text(
                    "of ${k.due}", style = giInter(12, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp).alignByBaseline(),
                )
            }
            TileCaption(k.caption)
        }
    }
    val g = t.given
    HeroTile("GIVEN", g.spoken, Modifier.weight(1f)) {
        // The currency small, the amount large, one line shrunk to the tile.
        Row(Modifier.shrinkToFit(0.6f)) {
            Text(
                tileCurrency(g.first.currency), style = giInter(11, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.75f),
                maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline(),
            )
            Text(
                compactAmount(g.first.minor.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
                style = giSerif(26, FontWeight.SemiBold), color = Color.White,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 3.dp).alignByBaseline(),
            )
        }
        TileCaption(g.caption)
    }
}

/** The Given tile's currency, small beside the amount. */
private fun tileCurrency(currency: String): String = when (currency) {
    GIVE_FORM_CURRENCY -> "KSh"
    USD_CURRENCY -> "US$"
    else -> currency
}

/** One translucent tile; the whole tile reads as one sentence to TalkBack. */
@Composable
private fun HeroTile(label: String, spoken: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label, style = giInter(11, FontWeight.SemiBold, 0.8f), color = Color.White.copy(alpha = 0.6f),
            maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 2.dp).shrinkToFit(0.65f),
        )
        content()
    }
}

@Composable
private fun TileCaption(text: String) {
    Text(text, style = giInter(11), color = Color.White.copy(alpha = 0.7f))
}

/** FAITHFULNESS: twelve squares Jan→Dec, read aloud as one sentence;
 *  "Jan"/"Dec" under the ends; one line. */
@Composable
private fun FaithfulnessCard(marks: List<MonthMark>, line: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("FAITHFULNESS")
        Column(Modifier.partnerCard(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = faithfulnessSpoken(marks) },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                marks.forEach { MonthSquare(it) }
            }
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                Text("Jan", style = giInter(11, FontWeight.Medium), color = Nuru.ink400)
                Spacer(Modifier.weight(1f))
                Text("Dec", style = giInter(11, FontWeight.Medium), color = Nuru.ink400)
            }
            line?.let { Text(it, style = giInter(11), color = GIVE.ink600, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun MonthSquare(mark: MonthMark) {
    val shape = RoundedCornerShape(5.dp)
    val base = Modifier.size(20.dp).clip(shape)
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GIVE.navy)
            .semantics(mergeDescendants = true) {}.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("SINCE YOU BEGAN", style = giInter(11, FontWeight.SemiBold, 1.6f), color = GIVE.gold)
        Text(line, style = giSerif(18, FontWeight.Medium), color = Color.White)
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

/** Pledged / Paid / Remaining — one line per currency under each, never
 *  one sum across them (Giving Cycle 5). Shown only without the hero's
 *  tiles (an older server sends no `impact`). */
@Composable
private fun SummaryCard(sums: List<CurrencyStatementSummary>) {
    StatementFigures(sums, Modifier.partnerCard())
}

/** COMMITMENTS (iOS commitmentsSection): one row per pledge — a tap opens
 *  it — and "Remaining this year KSh X" at the header's right when the
 *  server sends each pledge's remaining_year_minor. */
@Composable
private fun CommitmentsCard(year: Int, rows: List<StatementPledge>, today: LocalDate, onOpenPledge: (String) -> Unit) {
    val remaining = remainingThisYear(rows)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("COMMITMENTS")
            Spacer(Modifier.weight(1f))
            remaining?.let {
                Text("Remaining this year", style = giInter(11), color = Nuru.ink400, maxLines = 1)
                Text(
                    moneyTotals(it), style = giInter(11, FontWeight.SemiBold), color = GIVE.ink600,
                    maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 4.dp).shrinkToFit(0.8f),
                )
            }
        }
        if (rows.isEmpty()) {
            Box(Modifier.partnerCard()) { Text("No pledges in $year.", style = giInter(13), color = GIVE.ink600) }
        } else {
            Column(Modifier.partnerCard()) {
                rows.forEachIndexed { i, e ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Hairline() }
                    PledgeRow(e, today, onOpen = e.pledgeId.takeIf { it.isNotBlank() }?.let { id -> { onOpenPledge(id) } })
                }
            }
        }
    }
}

/** One pledge as the statement reports it (iOS StatementPledgeRow): its
 *  name and promise, the state chip, then what was paid and — on a
 *  department need — how far the whole church has got. */
@Composable
private fun PledgeRow(e: StatementPledge, today: LocalDate, onOpen: (() -> Unit)?) {
    val state = statementPledgeState(e, today)
    val (chipBg, chipFg) = when (state) {
        "Behind" -> GIVE.goldChipBg to GIVE.goldChipText
        "Paused", "Cancelled" -> GIVE.mutedBg to GIVE.ink600
        else -> GIVE.successBg to GIVE.successText
    }
    Column(
        Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(e.title.ifBlank { "Pledge" }, style = giInter(15, FontWeight.SemiBold), color = GIVE.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pledgeAmountLine(e, today), style = giInter(12), color = GIVE.ink600, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StateChip(state, chipBg, chipFg)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pledgeProgressLine(e), style = giInter(11), color = GIVE.ink600, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            churchRaisedLine(e)?.let { Text(it, style = giInter(11, FontWeight.SemiBold), color = GIVE.goldLo, maxLines = 1) }
        }
    }
}

/** PAYMENTS as one card (iOS paymentsSection): PROCESSING first — shown,
 *  counted nowhere — then each month with its subtotal, and at the foot the
 *  year's total, the sum of the rows above it. */
@Composable
private fun PaymentsSection(
    year: Int,
    pending: List<StatementPendingPayment>,
    months: List<StatementMonth>,
    pledges: List<Pledge>,
    onOpenReceipt: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("PAYMENTS")
        if (months.isEmpty() && pending.isEmpty()) {
            Box(Modifier.partnerCard()) { Text("No pledge payments in $year.", style = giInter(13), color = GIVE.ink600) }
        } else {
            Column(Modifier.partnerCard()) {
                if (pending.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("PROCESSING", style = giInter(11, FontWeight.Bold, 1.1f), color = Nuru.answeredText)
                        Spacer(Modifier.weight(1f))
                        Text("not yet counted", style = giInter(11), color = Nuru.ink400)
                    }
                    pending.forEachIndexed { i, pay ->
                        if (i > 0) Hairline()
                        PendingPledgePaymentRow(pay, statementPaymentTitle(pay.pledgeTitle, null, pay.pledgeId, pledges), onOpenReceipt)
                    }
                }
                months.forEachIndexed { gi, m ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = if (gi == 0 && pending.isEmpty()) 0.dp else 16.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(statementMonthLabel(m), style = giInter(11, FontWeight.Bold, 1.1f), color = GIVE.goldChipText)
                        Spacer(Modifier.weight(1f))
                        Text(moneyTotals(m.subtotals), style = giInter(11, FontWeight.SemiBold), color = GIVE.ink600, maxLines = 1)
                    }
                    m.payments.forEachIndexed { i, pay ->
                        if (i > 0) Hairline()
                        PaymentRow(pay, statementPaymentTitle(pay.pledgeTitle, pay.title, pay.pledgeId, pledges), onOpenReceipt)
                    }
                }
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(GIVE.navy.copy(alpha = 0.35f)))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL PAID $year", style = giInter(11, FontWeight.Bold, 1.2f), color = GIVE.navy)
                    Spacer(Modifier.weight(1f))
                    Text(
                        moneyTotals(statementYearTotals(months)), style = giSerif(18, FontWeight.Bold), color = GIVE.gold,
                        maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp).shrinkToFit(0.7f),
                    )
                }
            }
        }
    }
}

/** "Sat 20" · the pledge over "M-Pesa · UIKJ2713B5" · "KSh 2,000" (iOS
 *  StatementPaymentLine) — the month is the group's head above. Tap → its
 *  receipt. */
@Composable
private fun PaymentRow(pay: StatementPayment, title: String, onOpenReceipt: (String) -> Unit) {
    val meta = statementLineMeta(pay)
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = pay.transactionId.isNotBlank()) { onOpenReceipt(pay.transactionId) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(statementPaymentDay(pay.occurredAt), style = giInter(12, FontWeight.SemiBold), color = GIVE.ink600, maxLines = 1, modifier = Modifier.width(54.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) Text(meta, style = giInter(11), color = Nuru.ink400, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(money(pay.amountMinor, pay.currency), style = giInter(13, FontWeight.SemiBold), color = GIVE.navy, maxLines = 1)
    }
}

/** The PDF, and the way to the complete record (iOS actions): the general
 *  statement holds every gift, pledged or not — a partner must still reach it. */
@Composable
private fun StatementActions(busy: Boolean, error: String?, onDownload: () -> Unit, onOpenGivingStatement: () -> Unit) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DownloadPdfButton(busy = busy, onClick = onDownload)
        error?.let {
            Text(it, style = giInter(11), color = GIVE.danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        GivingStatementButton(onOpenGivingStatement)
        Text(
            "Every gift, pledged or not, is on your giving statement.", style = giInter(11), color = Nuru.ink400,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A secondary — white with a hairline and navy words (§8.1 rule 4; final
 *  walk C16: it was navy-filled). */
@Composable
private fun DownloadPdfButton(busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Capsule).background(GIVE.white).border(1.dp, GIVE.border, Capsule).clickable(enabled = !busy) { onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GIVE.navy, strokeWidth = 2.dp)
        } else {
            Icon(Lucide.Download, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(if (busy) "Preparing PDF…" else "Download PDF", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
    }
}

/** A secondary with a hairline — it wore a heavy navy outline and an arrow
 *  inside (§8.1 rule 4; final walk C16). */
@Composable
private fun GivingStatementButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Capsule).background(GIVE.white).border(1.dp, GIVE.border, Capsule).clickable { onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Giving statement", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
    }
}
