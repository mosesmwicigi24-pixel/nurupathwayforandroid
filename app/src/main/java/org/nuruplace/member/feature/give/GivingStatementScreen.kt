// Giving statement (history) + receipt (detail with the double-entry ledger).
// Port of the iOS GivingStatementView + GivingReceiptView.
package org.nuruplace.member.feature.give

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.GivingDetail
import org.nuruplace.member.data.net.GivingRecord
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.relTime

private fun statusColor(s: String) = when (s) {
    "succeeded", "settled" -> Nuru.successText
    "failed", "cancelled" -> Nuru.danger
    else -> Nuru.warning
}

@Composable
fun GivingStatementScreen(onBack: () -> Unit, onOpenReceipt: (String) -> Unit) {
    AsyncContent(load = { Net.client.api.givingHistory().data }) { records: List<GivingRecord>, _ ->
        val settled = records.filter { it.status == "succeeded" || it.status == "settled" }.sumOf { it.amountMinor }
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Giving statement", kicker = "Given: ${fmtMoney(settled, "KES")}", onBack = onBack)
            if (records.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No gifts yet.", style = NuruType.body, color = Nuru.ink600) }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(records, key = { it.transactionId }) { r ->
                        NuruCard(modifier = Modifier.clickable { onOpenReceipt(r.transactionId) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(fmtMoney(r.amountMinor, r.currency), style = NuruType.cardTitle, color = Nuru.ink)
                                    Text("${r.fund.replaceFirstChar { it.uppercase() }} · ${relTime(r.createdAt)}", style = NuruType.caption, color = Nuru.ink600)
                                }
                                Text(r.status, style = NuruType.micro, color = statusColor(r.status), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GivingReceiptScreen(transactionId: String, onBack: () -> Unit) {
    AsyncContent(key = transactionId, load = { Net.client.api.givingDetail(transactionId) }) { d: GivingDetail, _ ->
        Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().background(Nuru.ceremonyGradient).padding(horizontal = Spacing.screen, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Kicker("Receipt")
                Spacer(Modifier.height(Spacing.sm))
                Text(fmtMoney(d.amountMinor, d.currency), style = NuruType.display, color = Nuru.onNavy)
                Text("${d.fund.replaceFirstChar { it.uppercase() }} · ${d.status}", style = NuruType.body, color = Nuru.onNavyDim)
            }
            Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
                NuruCard {
                    ReceiptRow("Method", d.method ?: "—")
                    ReceiptRow("Reference", d.providerRef ?: d.transactionId.take(12))
                    ReceiptRow("Created", relTime(d.createdAt))
                    d.settledAt?.let { ReceiptRow("Settled", relTime(it)) }
                }
                if (d.ledger.isNotEmpty()) {
                    NuruCard {
                        Kicker("Ledger")
                        Spacer(Modifier.height(Spacing.sm))
                        d.ledger.forEach { e ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text("${e.side} · ${e.account}", style = NuruType.caption, color = Nuru.ink600, modifier = Modifier.weight(1f))
                                Text(fmtMoney(e.amountMinor, e.currency), style = NuruType.caption, color = Nuru.ink)
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().clickable { onBack() }.padding(Spacing.md), contentAlignment = Alignment.Center) {
                    Text("Done", style = NuruType.cardCta, color = Nuru.goldLo)
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = NuruType.caption, color = Nuru.ink400, modifier = Modifier.weight(1f))
        Text(value, style = NuruType.caption, color = Nuru.ink, fontWeight = FontWeight.Medium)
    }
}
