// Giving statement (history) + receipt (detail with the double-entry ledger).
// Port of the iOS GivingStatementView + GivingReceiptView. Uses the shared GIVE
// palette / helpers from GiveShared.kt (same package — no import needed).
package org.nuruplace.member.feature.give

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.GivingDetail
import org.nuruplace.member.data.net.GivingRecord
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
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
private fun parseNairobi(iso: String?): ZonedDateTime? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso).atZone(NAIROBI) }
        .recoverCatching { OffsetDateTime.parse(iso).atZoneSameInstant(NAIROBI) }
        .recoverCatching { LocalDateTime.parse(iso).atZone(NAIROBI) }
        .recoverCatching { LocalDate.parse(iso).atStartOfDay(NAIROBI) }
        .getOrNull()
}

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val FULL_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.ENGLISH)

private fun dayHeader(iso: String?): String =
    parseNairobi(iso)?.format(DAY_FMT)?.uppercase(Locale.ENGLISH) ?: "—"

private fun timeLabel(iso: String?): String =
    parseNairobi(iso)?.format(TIME_FMT) ?: ""

private fun fullDate(iso: String?): String =
    parseNairobi(iso)?.format(FULL_FMT) ?: "—"

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
@Composable
fun GivingStatementScreen(onBack: () -> Unit, onOpenReceipt: (String) -> Unit) {
    AsyncContent(load = { Net.client.api.givingHistory().data }) { records: List<GivingRecord>, _ ->
        var period by remember { mutableIntStateOf(0) } // 0 = This year, 1 = Last year

        val currentYear = LocalDate.now(NAIROBI).year
        val settled = records.filter { it.status == "succeeded" || it.status == "settled" }
        val targetYear = if (period == 0) currentYear else currentYear - 1
        val periodRecords = settled.filter { recordYear(it) == targetYear }
        val total = periodRecords.sumOf { it.amountMinor }

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
                    Text(
                        "Total given",
                        style = giInter(11),
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        ksh(total),
                        style = giSerif(34, FontWeight.SemiBold, -1f),
                        color = Color.White,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        "${periodRecords.size} gifts · ${if (period == 0) "this year" else "in ${currentYear - 1}"} · most recent first",
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

                // BY FUND card
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    Text("BY FUND", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    val byFund = periodRecords.groupBy { it.fund }
                    if (byFund.isEmpty()) {
                        Text(
                            "No settled gifts ${if (period == 0) "this year" else "in ${currentYear - 1}"}.",
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
                    // TOTAL GIVEN
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("TOTAL GIVEN", style = giInter(11, FontWeight.Bold, 1.4f), color = GIVE.navy)
                        Spacer(Modifier.weight(1f))
                        Text(ksh(total), style = giSerif(18, FontWeight.Bold), color = GIVE.gold)
                    }
                }

                // History grouped by day
                val grouped = periodRecords
                    .sortedByDescending { it.createdAt }
                    .groupBy { dayHeader(it.createdAt) }
                grouped.forEach { (dayKey, recs) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            dayKey,
                            style = giInter(11, FontWeight.Bold, 1.1f),
                            color = GIVE.overline,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        recs.forEach { r ->
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
                                    Text(
                                        "${timeLabel(r.createdAt)} · ${(r.method ?: "").replaceFirstChar { it.uppercase() }}",
                                        style = giInter(11),
                                        color = GIVE.tertiary,
                                    )
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
                    }
                }

                // Empty state
                if (periodRecords.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "No gifts ${if (period == 0) "this year" else "in ${currentYear - 1}"}.",
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

// ── GivingReceiptScreen — iOS GivingReceiptView ───────────────────────────────
@Composable
fun GivingReceiptScreen(transactionId: String, onBack: () -> Unit) {
    AsyncContent(key = transactionId, load = { Net.client.api.givingDetail(transactionId) }) { d: GivingDetail, _ ->
        Column(
            Modifier
                .fillMaxSize()
                .background(GIVE.paper)
                .verticalScroll(rememberScrollState()),
        ) {
            // Cream header
            GiveCreamHeaderBox {
                Row(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GIVE.white)
                            .border(1.dp, GIVE.border, RoundedCornerShape(16.dp))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GIVE.navy,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text("Receipt", style = giSerif(20, FontWeight.SemiBold), color = GIVE.navy)
                }
            }

            // Body
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Amount card
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(GIVE.successBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = null,
                            tint = GIVE.success,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Text(money(d.amountMinor, d.currency), style = giSerif(36, FontWeight.Bold), color = GIVE.ink)
                    Text("to ${giveFund(d.fund).name}", style = giInter(14), color = GIVE.sub)
                    StatusChip(d.status)
                }

                // Detail card
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp),
                ) {
                    DetailRow("Date", fullDate(d.createdAt), showDivider = true)
                    DetailRow("Method", (d.method ?: "").replaceFirstChar { it.uppercase() }, showDivider = true)
                    DetailRow("Currency", d.currency, showDivider = true)
                    DetailRow("Reference", d.receiptCode ?: d.providerRef ?: "—", showDivider = true)
                    DetailRow("Transaction", d.transactionId.take(8) + "…", showDivider = false)
                }

                // Ledger card
                if (d.ledger.isNotEmpty()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(GIVE.white)
                            .border(1.dp, GIVE.border, RoundedCornerShape(22.dp))
                            .padding(16.dp),
                    ) {
                        Text("LEDGER", style = giInter(11, FontWeight.Bold, 1.4f), color = GIVE.gold)
                        d.ledger.forEach { e ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    e.side.uppercase(Locale.ENGLISH),
                                    style = giInter(11, FontWeight.Medium),
                                    color = if (e.side == "debit") GIVE.ledgerDebit else GIVE.ledgerCredit,
                                    modifier = Modifier.width(56.dp),
                                )
                                Text(
                                    e.account,
                                    style = giInter(12),
                                    color = GIVE.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(money(e.amountMinor, d.currency), style = giInter(13, FontWeight.SemiBold), color = GIVE.ink)
                            }
                        }
                    }
                }

                // Download this gift's receipt PDF (authed fetch — never a token URL).
                val pdfScope = rememberCoroutineScope()
                val pdfCtx = androidx.compose.ui.platform.LocalContext.current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GIVE.gold)
                        .clickable {
                            pdfScope.launch {
                                openPdfAuthed(pdfCtx, "nuru-giving-receipt-${d.transactionId.take(8)}.pdf") {
                                    Net.client.api.givingReceiptPdf(d.transactionId)
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Download, null, tint = GIVE.navy, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Download receipt (PDF)", style = giInter(13, FontWeight.Bold), color = GIVE.navy)
                }
            }
        }
    }
}

// Authed PDF fetch → cache/shared → content:// viewer chooser (FileProvider is
// declared in the manifest; same plumbing as certificate downloads).
private suspend fun openPdfAuthed(
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

@Composable
private fun DetailRow(label: String, value: String, showDivider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = giInter(12), color = GIVE.tertiary)
            Spacer(Modifier.weight(1f))
            Text(
                value,
                style = giInter(13, FontWeight.SemiBold),
                color = GIVE.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDivider) HairlineDivider()
    }
}
