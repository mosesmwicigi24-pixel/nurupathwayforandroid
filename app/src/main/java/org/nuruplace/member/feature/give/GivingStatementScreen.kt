// Giving statement (history) + receipt (receipt v2 — the green hero, details,
// where it went, share; no ledger).
// Port of the iOS GivingStatementView + GivingReceiptView. Uses the shared GIVE
// palette / helpers from GiveShared.kt (same package — no import needed).
package org.nuruplace.member.feature.give

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.data.net.GivingDetail
import org.nuruplace.member.data.net.GivingRecord
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Haptics
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NAIROBI: ZoneId = ZoneId.of("Africa/Nairobi")
private val Capsule = RoundedCornerShape(999.dp)

/** Parse an ISO timestamp into a Nairobi-zoned date-time, tolerating several shapes. */
internal fun parseNairobi(iso: String?): ZonedDateTime? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso).atZone(NAIROBI) }
        .recoverCatching { OffsetDateTime.parse(iso).atZoneSameInstant(NAIROBI) }
        .recoverCatching { LocalDateTime.parse(iso).atZone(NAIROBI) }
        .recoverCatching { LocalDate.parse(iso).atStartOfDay(NAIROBI) }
        .getOrNull()
}

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** "FRI, 25 SEP 2026" for a Nairobi calendar day; "—" for an unreadable one. */
private fun dayHeader(date: LocalDate?): String =
    date?.format(DAY_FMT)?.uppercase(Locale.ENGLISH) ?: "—"

private fun timeLabel(iso: String?): String =
    parseNairobi(iso)?.format(TIME_FMT) ?: ""

/** Year a record belongs to — prefer settledAt, fall back to createdAt. */
private fun recordYear(r: GivingRecord): Int? =
    (parseNairobi(r.settledAt) ?: parseNairobi(r.createdAt))?.year

// ── Status chip (shared visual for statement rows + receipt) ──────────────────
@Composable
private fun StatusChip(status: String?) {
    val (label, bg, fg) = giveStatus(status)
    Box(
        Modifier.clip(Capsule).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, style = giInter(11, FontWeight.Medium), color = fg)
    }
}

@Composable
private fun HairlineDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GIVE.border))
}

// ── GivingStatementScreen — iOS GivingStatementView ───────────────────────────
// Statement v2 (docs/PARTNERS_PROGRAMME.md §3d): complete but separate. The
// hero, BY FUND and the day list are GIFTS (rows without a pledge_id); pledge
// money sits in one collapsed PARTNER PLEDGES group after the day list, with
// its total and a link to the Partners statement. Gifts + Partner pledges =
// Total, and the hero says so whenever there is pledge money
// (GivingStatementLogic.kt, pinned by GivingStatementLogicTest).
@Composable
fun GivingStatementScreen(
    onBack: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    /** The Partners statement for a year — the PARTNER PLEDGES group's link. */
    onOpenPartnersStatement: (Int) -> Unit = {},
) {
    AsyncContent(load = { Net.client.api.givingHistory().data }) { records: List<GivingRecord>, _ ->
        var period by remember { mutableIntStateOf(0) } // 0 = This year, 1 = Last year
        var pledgesOpen by remember { mutableStateOf(false) }

        val currentYear = LocalDate.now(NAIROBI).year
        val settled = records.filter { it.status == "succeeded" || it.status == "settled" }
        val targetYear = if (period == 0) currentYear else currentYear - 1
        val periodRecords = settled.filter { recordYear(it) == targetYear }
        val split = givingSplit(periodRecords)
        val hero = givingHero(split)
        val group = pledgeGroup(split)
        val periodLabel = if (period == 0) "this year" else "in ${currentYear - 1}"

        Column(
            Modifier
                .fillMaxSize()
                .background(GIVE.paper)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Dark navy header ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(GIVE.statementHeader),
            ) {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.radialGradient(
                            listOf(GIVE.gold.copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(820f, 40f),
                            radius = 420f,
                        ),
                    ),
                )
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable { onBack() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "GIVING STATEMENT",
                            style = giInter(10, FontWeight.Bold, 2.2f),
                            color = GIVE.gold,
                        )
                        Spacer(Modifier.weight(1f))
                        // Statement PDF (giving/statement.pdf) — was shipped inert.
                        val pdfScope = rememberCoroutineScope()
                        val pdfCtx = androidx.compose.ui.platform.LocalContext.current
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                .clickable {
                                    pdfScope.launch {
                                        openPdfAuthed(pdfCtx, "nuru-giving-statement.pdf") {
                                            Net.client.api.givingStatementPdf()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Download statement PDF",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                    // "Total given" with no pledge money; else "Gifts" over
                    // the gifts, and one muted line that foots to the Total.
                    Text(
                        hero.label,
                        style = giInter(11),
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        ksh(hero.amountMinor),
                        style = giSerif(34, FontWeight.SemiBold, -1f),
                        color = Color.White,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    hero.pledgeLine?.let {
                        Text(
                            it,
                            style = giInter(12, FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        "${split.gifts.size} gift${if (split.gifts.size == 1) "" else "s"} · $periodLabel · most recent first",
                        style = giInter(11),
                        color = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // ── Body ──
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Period selector
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GIVE.track)
                        .padding(4.dp),
                ) {
                    listOf("This year", "Last year").forEachIndexed { i, label ->
                        val on = period == i
                        Box(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(if (on) Modifier.background(GIVE.white) else Modifier)
                                .clickable { period = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = giInter(13, FontWeight.SemiBold),
                                color = if (on) GIVE.navy else GIVE.sub,
                            )
                        }
                    }
                }

                // BY FUND card — gifts only; pledge money is in PARTNER PLEDGES.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    Text("BY FUND", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    val byFund = split.gifts.groupBy { it.fund }
                    if (byFund.isEmpty()) {
                        Text(
                            "No settled gifts $periodLabel.",
                            style = giInter(13),
                            color = GIVE.sub,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        val entries = byFund.entries.toList()
                        entries.forEachIndexed { idx, (fund, recs) ->
                            val f = giveFund(fund)
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(f.tint),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(f.icon, contentDescription = null, tint = f.fg, modifier = Modifier.size(15.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
                                    Text(
                                        "${recs.size} gift${if (recs.size == 1) "" else "s"}",
                                        style = giInter(11),
                                        color = GIVE.tertiary,
                                    )
                                }
                                Text(
                                    ksh(recs.sumOf { it.amountMinor }),
                                    style = giInter(13, FontWeight.SemiBold),
                                    color = GIVE.navy,
                                )
                            }
                            if (idx < entries.lastIndex) HairlineDivider()
                        }
                    }
                    // TOTAL GIVEN (no pledge money) · TOTAL GIFTS (gifts only)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(hero.footLabel, style = giInter(11, FontWeight.Bold, 1.4f), color = GIVE.navy)
                        Spacer(Modifier.weight(1f))
                        Text(ksh(split.giftsMinor), style = giSerif(18, FontWeight.Bold), color = GIVE.gold)
                    }
                }

                // Gifts grouped by day
                statementDays(split.gifts).forEach { day ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayHeader(day.date)
                        day.records.forEach { r -> StatementRecordRow(r, onOpenReceipt) }
                    }
                }

                // PARTNER PLEDGES — collapsed, after the gifts; only with pledge money.
                group?.let { g ->
                    PartnerPledgesGroup(
                        g,
                        open = pledgesOpen,
                        onToggle = { pledgesOpen = !pledgesOpen },
                        onOpenReceipt = onOpenReceipt,
                        onOpenPartnersStatement = { onOpenPartnersStatement(targetYear) },
                    )
                }

                // Empty state
                if (periodRecords.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "No gifts $periodLabel.",
                            style = giInter(13),
                            color = GIVE.sub,
                        )
                        Text(
                            "Statement reflects records held under Finance · receipts emailed per gift.",
                            style = giInter(11),
                            color = GIVE.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate?) {
    Text(
        dayHeader(date),
        style = giInter(11, FontWeight.Bold, 1.1f),
        color = GIVE.overline,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** One statement row — fund badge, fund name, pledge tag when pledge-tied,
 *  time · method, the member's own label, the receipt code, amount + status.
 *  Tap → receipt. The same row in the day list and in PARTNER PLEDGES. */
@Composable
private fun StatementRecordRow(r: GivingRecord, onOpenReceipt: (String) -> Unit) {
    val f = giveFund(r.fund)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GIVE.white)
            .border(1.dp, GIVE.border, RoundedCornerShape(18.dp))
            .clickable { onOpenReceipt(r.transactionId) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(f.tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(f.icon, contentDescription = null, tint = f.fg, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(f.name, style = giInter(14, FontWeight.Bold, -0.14f), color = GIVE.navy)
            // A gift that counted toward a pledge says which (wire
            // pledge_title, contract 2026-09-25); "Partner pledge" when an
            // older row carries only the id.
            if (isPledgeRecord(r)) PledgeTag(pledgeTagTitle(r))
            Text(
                "${timeLabel(r.createdAt)} · ${(r.method ?: "").replaceFirstChar { it.uppercase() }}",
                style = giInter(11),
                color = GIVE.tertiary,
            )
            // "Named giving" (custom sheet, optional): the
            // member's own label for this gift, when set.
            r.accountName?.takeIf { it.isNotBlank() }?.let {
                Text("“$it”", style = giInter(11, FontWeight.SemiBold), color = GIVE.sub)
            }
            r.receiptCode?.takeIf { it.isNotBlank() }?.let {
                Text("Ref $it", style = giInter(11, FontWeight.SemiBold), color = GIVE.eyebrow)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(r.amountMinor, r.currency), style = giInter(14, FontWeight.Bold), color = GIVE.navy)
            Spacer(Modifier.height(4.dp))
            StatusChip(r.status)
        }
    }
}

/** The collapsed PARTNER PLEDGES group (spec §3d): "KSh Y · N payments" and a
 *  chevron; open, the pledge-tied rows by day in the same row style, then a
 *  link to the Partners statement for the same year. A light-gold panel so
 *  it reads as one group apart from the gifts above. */
@Composable
private fun PartnerPledgesGroup(
    g: PledgeGroup,
    open: Boolean,
    onToggle: () -> Unit,
    onOpenReceipt: (String) -> Unit,
    onOpenPartnersStatement: () -> Unit,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GIVE.priorityBg)
            .border(1.dp, GIVE.border, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (open) "Hide partner pledges" else "Show partner pledges") {
                    Haptics.tick(view)
                    onToggle()
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("PARTNER PLEDGES", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                Text(
                    g.summary,
                    style = giInter(14, FontWeight.SemiBold),
                    color = GIVE.navy,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(
                if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = GIVE.navy,
                modifier = Modifier.size(22.dp),
            )
        }
        if (open) {
            Column(
                Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                g.days.forEach { day ->
                    DayHeader(day.date)
                    day.records.forEach { r -> StatementRecordRow(r, onOpenReceipt) }
                }
                Text(
                    "Partners statement →",
                    style = giInter(13, FontWeight.SemiBold),
                    color = GIVE.goldLo,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(Capsule)
                        .clickable { Haptics.tap(view); onOpenPartnersStatement() }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ── GivingReceiptScreen — receipt v2 (owner, 2026-09-25) ──────────────────────
// "Enhance the receipt format and feel, appealing and well coloured; I like the
// green; best UX." The green hero says the gift was received, the details card
// holds what a member actually needs (the M-Pesa code one tap from the
// clipboard), a line says where the money went, then Share / View statement
// and a verse. No ledger — members never see account codes. Every word comes
// from GiveReceiptCopy.kt (pure, pinned by GiveReceiptCopyTest); the server
// resolves the display names (fund_name, pledge, need, method_label,
// member_name) and the copy falls back to the local tables for an older one.
private val RECEIPT_GREEN = Color(0xFF16A34A)
private val RECEIPT_GREEN_BG = Color(0xFFDCFCE7)
private val RECEIPT_GREEN_TEXT = Color(0xFF166534)
private val RECEIPT_DATE = Color(0xFF8B95A5)
private val RECEIPT_LABEL = Color(0xFF68758A)
private val RECEIPT_RED = Color(0xFFDC2626)
private val RECEIPT_RED_BG = Color(0xFFFEE2E2)

@Composable
fun GivingReceiptScreen(transactionId: String, onBack: () -> Unit, onOpenStatement: () -> Unit = {}) {
    AsyncContent(
        key = transactionId,
        load = {
            val d = Net.client.api.givingDetail(transactionId)
            // "Thank you, <first name>." reads the server's member_name; an older
            // server sends none, so fall back to the signed-in profile. A failed
            // lookup only drops the line — never the receipt.
            val profileName = if (d.memberName.isNullOrBlank()) {
                runCatching { Net.client.api.me().profile.fullName }.getOrNull()
            } else {
                null
            }
            d to profileName
        },
    ) { loaded: Pair<GivingDetail, String?>, _ ->
        val (d, profileName) = loaded
        val context = LocalContext.current
        val view = LocalView.current
        val clipboard = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        var sharing by remember { mutableStateOf(false) }
        var shareError by remember { mutableStateOf<String?>(null) }
        var copied by remember { mutableStateOf<String?>(null) } // which row just hit the clipboard

        val chip = receiptStatusChip(d)
        val firstName = receiptFirstName(d.memberName, profileName)
        val (badgeBg, badgeFg, badgeIcon) = when (chip?.tone) {
            null -> Triple(RECEIPT_GREEN_BG, RECEIPT_GREEN, Icons.Filled.Verified)
            ReceiptTone.Waiting -> Triple(GIVE.goldChipBg, GIVE.goldChipText, Icons.Filled.Schedule)
            ReceiptTone.NotCompleted -> Triple(RECEIPT_RED_BG, RECEIPT_RED, Icons.Filled.Close)
            ReceiptTone.Refunded -> Triple(GIVE.mutedBg, GIVE.ink600, Icons.AutoMirrored.Filled.Undo)
        }

        fun copy(key: String, value: String) {
            clipboard.setText(AnnotatedString(value))
            Haptics.tick(view)
            copied = key
            scope.launch { delay(1_600); if (copied == key) copied = null }
        }

        // Share = the PDF (authed fetch → FileProvider) with a one-line summary;
        // when the PDF cannot be fetched the summary alone goes out and we say so.
        fun share() {
            if (sharing) return
            sharing = true
            shareError = null
            scope.launch {
                val text = receiptShareText(d)
                val ok = sharePdfAuthed(context, receiptFileName(d), text) { Net.client.api.givingReceiptPdf(d.transactionId) }
                if (!ok) {
                    shareTextOnly(context, text)
                    shareError = "Couldn't fetch the PDF just now — shared the summary instead."
                }
                sharing = false
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(GIVE.paper)
                .verticalScroll(rememberScrollState()),
        ) {
            // Cream header — back · Receipt · share
            GiveCreamHeaderBox {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ReceiptHeaderButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
                    Text("Receipt", style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy)
                    Spacer(Modifier.weight(1f))
                    ReceiptHeaderButton(Icons.Filled.Share, "Share receipt") { share() }
                }
            }

            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Hero — the green ──
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(badgeBg), contentAlignment = Alignment.Center) {
                        Icon(badgeIcon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(32.dp))
                    }
                    Text(
                        receiptEyebrow(chip),
                        style = giInter(10, FontWeight.SemiBold, 1.6f),
                        color = if (chip == null) RECEIPT_GREEN_TEXT else badgeFg,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    // A thank-you belongs to a gift that arrived or is on its way —
                    // not to one that failed or was refunded.
                    if (firstName != null && (chip == null || chip.tone == ReceiptTone.Waiting)) {
                        Text(
                            "Thank you, $firstName.",
                            style = giSerif(18, FontWeight.SemiBold),
                            color = GIVE.navy,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    val (mark, number) = receiptAmountParts(d.amountMinor, d.currency)
                    Row(Modifier.padding(top = 8.dp)) {
                        Text(
                            mark,
                            style = giInter(16, FontWeight.SemiBold),
                            color = GIVE.tertiary,
                            modifier = Modifier.alignByBaseline().padding(end = 6.dp),
                        )
                        Text(number, style = giSerif(40, FontWeight.SemiBold, -1f), color = GIVE.navy, modifier = Modifier.alignByBaseline())
                    }
                    Text(
                        receiptDestinationLine(d),
                        style = giInter(14),
                        color = GIVE.ink600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // "Named giving": the member's own label for this gift, when set.
                    d.accountName?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "“$it”",
                            style = giInter(13, FontWeight.SemiBold),
                            color = GIVE.eyebrow,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(receiptWhen(d), style = giInter(12), color = RECEIPT_DATE, modifier = Modifier.padding(top = 10.dp))
                    if (chip != null) {
                        Row(
                            Modifier
                                .padding(top = 14.dp)
                                .clip(Capsule)
                                .background(badgeBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(badgeIcon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(13.dp))
                            Text(chip.label, style = giInter(12, FontWeight.SemiBold), color = badgeFg)
                        }
                    }
                }

                // ── Details ──
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    ReceiptRow("Fund", receiptFundName(d))
                    // Plain for now: the pledge sheet lives inside PartnersScreen
                    // and has no route of its own to navigate to.
                    receiptPledgeTitle(d)?.let { ReceiptRow("Pledge", it) }
                    d.accountName?.takeIf { it.isNotBlank() }?.let { ReceiptRow("Gift name", it) }
                    ReceiptRow("Method", receiptMethodLabel(d))
                    receiptProviderRef(d)?.let { ref ->
                        ReceiptRow(receiptReferenceLabel(d), ref, mono = true, copied = copied == "ref") { copy("ref", ref) }
                    }
                    ReceiptRow("Date", receiptWhen(d))
                    ReceiptRow("Reference", receiptShortId(d), mono = true, copied = copied == "id", last = true) { copy("id", d.transactionId) }
                }

                // ── Where it went ──
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = RECEIPT_GREEN, modifier = Modifier.size(16.dp))
                    Text(receiptWhereItWent(d), style = giInter(12), color = GIVE.sub)
                }

                // ── Actions ──
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(Capsule)
                                .background(GIVE.navy)
                                .clickable(enabled = !sharing) { share() },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (sharing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Share receipt", style = giInter(14, FontWeight.SemiBold), color = Color.White)
                        }
                        Row(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(Capsule)
                                .background(GIVE.white)
                                .border(1.dp, GIVE.navy.copy(alpha = 0.22f), Capsule)
                                .clickable { onOpenStatement() },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View statement", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
                        }
                    }
                    shareError?.let {
                        Text(it, style = giInter(11), color = GIVE.danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }

                // ── Verse ──
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(GIVE.cream)
                        .border(1.dp, GIVE.border, RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "“God loves a cheerful giver.”",
                        style = giSerif(16, FontWeight.Normal).copy(fontStyle = FontStyle.Italic),
                        color = GIVE.navy,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "— 2 Corinthians 9:7",
                        style = giInter(11, FontWeight.SemiBold, 0.6f),
                        color = GIVE.eyebrow,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** "School fees pledge" — the small gold tag a pledge-tied gift wears on the
 *  giving statement. Shared with the partners statement (same package). */
@Composable
internal fun PledgeTag(title: String) {
    Text(
        "$title pledge",
        style = giInter(10, FontWeight.SemiBold),
        color = GIVE.goldChipText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 3.dp).clip(Capsule).background(GIVE.goldChipBg).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Header chrome button (back · share). Shared with the partners statement. */
@Composable
internal fun ReceiptHeaderButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GIVE.white)
            .border(1.dp, GIVE.border, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = GIVE.navy, modifier = Modifier.size(18.dp))
    }
}

/** One details row: label left, value right; `mono` for codes; `onCopy` adds
 *  the copy glyph, makes the row tappable, and swaps in "Copied" while [copied]. */
@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    mono: Boolean = false,
    copied: Boolean = false,
    last: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onCopy != null) Modifier.clickable { onCopy() } else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = giInter(13), color = RECEIPT_LABEL)
            Spacer(Modifier.width(16.dp))
            Text(
                value,
                style = giInter(14, FontWeight.SemiBold).let {
                    if (mono) it.copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp) else it
                },
                color = GIVE.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            if (onCopy != null) {
                Spacer(Modifier.width(8.dp))
                if (copied) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = RECEIPT_GREEN_TEXT, modifier = Modifier.size(13.dp))
                        Text("Copied", style = giInter(11, FontWeight.SemiBold), color = RECEIPT_GREEN_TEXT)
                    }
                } else {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label", tint = GIVE.tertiary, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (!last) HairlineDivider()
    }
}

// Authed PDF fetch → cache/shared → content:// viewer chooser (FileProvider is
// declared in the manifest; same plumbing as certificate downloads). Shared
// with the Partners statements section (same package).
internal suspend fun openPdfAuthed(
    context: android.content.Context,
    fileName: String,
    fetch: suspend () -> okhttp3.ResponseBody,
) {
    runCatching {
        val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            val file = java.io.File(dir, fileName)
            fetch().byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            file
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            android.content.Intent.createChooser(view, "Open PDF")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// Share this gift's receipt: the PDF is fetched through the authed client
// (never a ?token= URL), written to cache/shared, and handed out as a
// content:// URI through the manifest FileProvider
// (${applicationId}.fileprovider → res/xml/file_paths.xml cache-path "shared")
// as an ACTION_SEND application/pdf with the one-line summary as EXTRA_TEXT.
// False when the PDF could not be fetched or no app took the share — the
// caller then shares the summary alone and says so. Cancellation propagates.
internal suspend fun sharePdfAuthed(
    context: Context,
    fileName: String,
    text: String,
    subject: String = "Nuru Place gift receipt",
    chooserTitle: String = "Share receipt",
    fetch: suspend () -> okhttp3.ResponseBody,
): Boolean {
    val file = runCatching {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            java.io.File(dir, fileName).also { f ->
                fetch().byteStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            }
        }
    }.getOrElse { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        return false
    }
    return runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}

/** The summary alone (text/plain) — the fallback when the PDF is unavailable. */
internal fun shareTextOnly(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share receipt").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
