package org.nuruplace.member.feature.give

// The Partners statement — Give → Partners → Statement (owner 2026-09-25:
// "Have the statement separate for partners and give statements separate").
// Its own route ("partners-statement?year="), the Android half of the same
// design as iOS PartnersStatementView.swift; keep in step.
//
// One cream band (back · "Partners statement" · share), then:
//   YEAR       chips, this year back to the join year, at most four — the
//              Partners tab's own list (partnerStatementYears)
//   STANDING   "Partner since Sep 2026 · Builder", one muted line
//   SUMMARY    Pledged / Paid / Remaining — the server's numbers when it
//              sends them, else PartnerStatementMath over the same payments
//   PLEDGES    one row per pledge: name, amount line, state chip,
//              "KSh 6,000 paid · 3 of 4 kept" or "KSh 20,000 paid"
//   PAYMENTS   pledge-tied only, one card per month with its subtotal; a row
//              is day · pledge · method + receipt code · amount, tap → receipt
//   TOTAL      the year's foot
//   Download PDF (navy — the money-document action) · Giving statement
//   (outlined) — the general statement is one tap away, never mixed in here.
//
// Every number is derived in PartnersStatementLogic.kt (pure, pinned by
// PartnersStatementLogicTest) from GET /giving/statements?year= and
// GET /giving/partnership; the PDF is GET /giving/partners/statement.pdf
// fetched through the authed client and handed out by the manifest
// FileProvider, exactly as the receipt's share does.

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GivingStatement
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.StatementPayment
import org.nuruplace.member.data.net.StatementPledge
import org.nuruplace.member.ui.components.Haptics
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

private val Capsule = RoundedCornerShape(999.dp)

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

    fun load(initialYear: Int? = null) {
        initialYear?.let { year = it }
        val y = year
        viewModelScope.launch {
            begin()
            val p = runCatching { Net.client.api.partnership() }
            p.getOrNull()?.let { partnership = it }
            val s = runCatching { Net.client.api.statements(y) }
            s.getOrNull()?.let { statements = statements + (y to it) }
            (s.exceptionOrNull() ?: p.exceptionOrNull())?.let { error = ApiException.message(it) }
            end()
        }
    }

    fun select(y: Int) {
        year = y
        if (statements[y] == null) loadYear(y)
    }

    fun loadYear(y: Int) {
        viewModelScope.launch {
            begin()
            runCatching { Net.client.api.statements(y) }
                .onSuccess { statements = statements + (y to it) }
                .onFailure { error = ApiException.message(it) }
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
    vm: PartnersStatementViewModel = remember { PartnersStatementViewModel() },
) {
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(Unit) { if (vm.partnership == null && vm.statements.isEmpty()) vm.load(initialYear) }
    val p = vm.partnership
    val today = LocalDate.now()
    val years = partnerStatementYears(today.year, partnerDate(p?.membership?.joinedAt ?: p?.since)?.year)
    val year = vm.year
    val s = vm.statements[year]

    Column(Modifier.fillMaxSize().background(GIVE.paper).verticalScroll(rememberScrollState())) {
        GiveCreamHeaderBox {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReceiptHeaderButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
                Text("Partners statement", style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy)
                Spacer(Modifier.weight(1f))
                ReceiptHeaderButton(Icons.Filled.Share, "Share statement PDF") { Haptics.tap(view); vm.sharePdf(context) }
            }
        }
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                years.forEach { y -> YearChip(y, on = y == year) { Haptics.tick(view); vm.select(y) } }
            }
            standingLine(p)?.let { Text(it, style = giInter(12), color = GIVE.sub) }

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
                    PledgesCard(partnerStatementPledges(year, s, pledges, today))
                    PaymentsSection(year, paymentsByMonth(s.payments), onOpenReceipt)
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

@Composable
private fun PledgesCard(rows: List<StatementPledge>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("YOUR PLEDGES")
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
    }
}

@Composable
private fun PaymentsSection(year: Int, months: List<StatementMonth>, onOpenReceipt: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("PAYMENTS")
        if (months.isEmpty()) {
            Box(Modifier.partnerCard()) { Text("No pledge payments in $year.", style = giInter(13), color = GIVE.sub) }
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
