// Give — the member giving tab, ported pixel-for-pixel from the iOS GivingView.
// Money is server-authoritative + ONLINE-ONLY (§5.6): pick a fund + amount +
// method, then create a REAL intent server-side (never fabricated, never queued).
// M-Pesa STK push is the default; PayPal returns an approve URL; SOON methods are
// disabled. Recurring gifts create schedules; the active-schedules rail lets you
// review + cancel one in a bottom sheet. All shared tokens/tables live in
// GiveShared.kt (same package — no import).
package org.nuruplace.member.feature.give

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.GiveBody
import org.nuruplace.member.data.net.GivingIntentResult
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PayPalCaptureBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.CelebrationCenter
import org.nuruplace.member.ui.components.Moment
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val Capsule = RoundedCornerShape(999.dp)

private fun freqLabel(f: Int): String = when (f) { 0 -> "one-time"; 1 -> "weekly"; else -> "monthly" }

/** Parse an ISO-8601 timestamp's year, best-effort. */
private fun isoYear(iso: String?): Int? =
    iso?.takeIf { it.isNotBlank() }?.let { s ->
        runCatching { OffsetDateTime.parse(s).year }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10)).year }.getOrNull()
            ?: s.take(4).toIntOrNull()
    }

/** "12 Mar 2026" from an ISO timestamp, best-effort — falls back to the raw string's date part. */
private fun prettyDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    return runCatching { OffsetDateTime.parse(iso).toLocalDate().format(fmt) }.getOrNull()
        ?: runCatching { LocalDate.parse(iso.take(10)).format(fmt) }.getOrNull()
        ?: iso.take(10)
}

private fun firstChargeDate(freq: Int): String =
    LocalDate.now().plusDays(if (freq == 1) 7L else 30L)
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GivingScreen(onBack: () -> Unit, onOpenStatement: () -> Unit, onOpenSchedules: () -> Unit = {}) {
    AsyncContent(
        load = {
            val hist = runCatching { Net.client.api.givingHistory().data }.getOrDefault(emptyList())
            val sched = runCatching { Net.client.api.schedules().data }.getOrDefault(emptyList())
            val phone = runCatching { Net.client.api.me().profile.phoneNumber }.getOrNull().orEmpty()
            Triple(hist, sched, phone)
        },
    ) { (history, schedules, phone), reload ->
        GiveTab(history, schedules, phone, reload, onOpenStatement)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiveTab(
    history: List<org.nuruplace.member.data.net.GivingRecord>,
    schedules: List<GivingSchedule>,
    phone: String,
    reload: () -> Unit,
    onOpenStatement: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var fundId by remember { mutableStateOf("tithe") }
    var amountMajor by remember { mutableIntStateOf(1000) }
    var freq by remember { mutableIntStateOf(2) } // 0 once / 1 weekly / 2 monthly
    val methods = remember { mutableStateListOf(*GIVE_METHODS.toTypedArray()) }
    var selectedMethod by remember { mutableStateOf("mpesa") }
    var coverFee by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<GivingIntentResult?>(null) }
    var sheetSchedule by remember { mutableStateOf<GivingSchedule?>(null) }

    // Full-screen generosity ceremony once an intent is created.
    result?.let { r ->
        GiveResult(r, onDone = { result = null })
        return
    }

    val thisYear = LocalDate.now().year
    val yearTotalMinor = history
        .filter { it.status in listOf("succeeded", "settled") && (isoYear(it.settledAt ?: it.createdAt) == thisYear) }
        .sumOf { it.amountMinor }

    val activeSchedules = schedules.filter { it.status == "active" }

    fun submit() {
        val method = methods.firstOrNull { it.id == selectedMethod } ?: return
        busy = true; error = null
        scope.launch {
            try {
                result = Net.client.api.giving(
                    GiveBody(
                        fundId,
                        amountMajor * 100,
                        "KES",
                        method.provider ?: "mpesa",
                        phone.ifBlank { null },
                        UUID.randomUUID().toString(),
                    ),
                )
            } catch (e: Exception) {
                error = ApiException.message(e)
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(GIVE.paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // ── Header ──
            GiveCreamHeaderBox {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp)) {
                    Text("GIVE", style = giInter(9, FontWeight.Bold, 1.62f), color = GIVE.eyebrow)
                    Text("Sow into the Kingdom", style = giSerif(24, FontWeight.SemiBold, -0.48f), color = GIVE.navy, modifier = Modifier.padding(top = 4.dp))
                    Text("Generosity is worship — a quiet, joyful act.", style = giInter(11), color = GIVE.sub, modifier = Modifier.padding(top = 4.dp))
                    Row(
                        Modifier.padding(top = 14.dp).clip(Capsule).background(GIVE.white)
                            .border(1.dp, GIVE.gold.copy(alpha = 0.45f), Capsule)
                            .clickable { onOpenStatement() }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
                        Text("${ksh(yearTotalMinor)} given this year", style = giInter(13, FontWeight.SemiBold), color = GIVE.eyebrow)
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(13.dp))
                    }
                }
            }

            // ── Body ──
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Funds row
                Text("CHOOSE A FUND", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GIVE_FUNDS.forEach { f ->
                        val on = f.id == fundId
                        Column(
                            Modifier.width(124.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (on) GIVE.priorityBg else GIVE.white)
                                .border(if (on) 2.dp else 1.dp, if (on) GIVE.gold else GIVE.border, RoundedCornerShape(16.dp))
                                .clickable { fundId = f.id }
                                .padding(12.dp),
                        ) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(f.tint), contentAlignment = Alignment.Center) {
                                Icon(f.icon, contentDescription = null, tint = f.fg, modifier = Modifier.size(17.dp))
                            }
                            Text(f.name, style = giInter(13, FontWeight.SemiBold, -0.13f), color = GIVE.navy, modifier = Modifier.padding(top = 8.dp))
                            Text(f.tagline, style = giInter(10), color = GIVE.sub, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                // Amount card
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(22.dp)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("AMOUNT", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.tertiary)
                    Row(
                        Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("KSh", style = giInter(14, FontWeight.Medium), color = GIVE.tertiary)
                        Text("%,d".format(amountMajor), style = giSerif(42, FontWeight.SemiBold, -1.2f), color = GIVE.navy)
                    }
                    Text("${giveFund(fundId).name} · ${freqLabel(freq)}", style = giInter(11), color = GIVE.sub, modifier = Modifier.padding(top = 4.dp))
                    Row(
                        Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GIVE_PRESETS.forEach { p ->
                            val on = amountMajor == p
                            Text(
                                "%,d".format(p),
                                style = giInter(13, FontWeight.SemiBold),
                                color = if (on) Color.White else GIVE.navy,
                                modifier = Modifier.clip(Capsule)
                                    .background(if (on) GIVE.navy else GIVE.surface)
                                    .then(if (on) Modifier else Modifier.border(1.dp, GIVE.border, Capsule))
                                    .clickable { amountMajor = p }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                        Text(
                            "Custom",
                            style = giInter(13, FontWeight.Bold),
                            color = GIVE.gold,
                            modifier = Modifier.clip(Capsule).background(GIVE.white)
                                .border(1.dp, GIVE.gold, Capsule)
                                .clickable { amountMajor += 500 }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

                // Frequency segmented
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GIVE.track).padding(4.dp),
                ) {
                    listOf("One-time", "Weekly", "Monthly").forEachIndexed { i, label ->
                        val on = freq == i
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                                .then(if (on) Modifier.background(GIVE.white) else Modifier)
                                .clickable { freq = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, style = giInter(13, FontWeight.SemiBold), color = if (on) GIVE.navy else GIVE.sub)
                        }
                    }
                }

                // Recurring summary
                if (freq != 0) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GIVE.priorityBg)
                            .border(1.dp, GIVE.gold.copy(alpha = 0.25f), RoundedCornerShape(18.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(GIVE.goldTile), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text("${kshMajor(amountMajor)} every ${if (freq == 1) "week" else "month"}", style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
                            Text(
                                "First charge ${firstChargeDate(freq)} · then every ${if (freq == 1) "week" else "month"}. Cancel anytime.",
                                style = giInter(11), color = GIVE.sub,
                            )
                        }
                    }
                }

                // Pay methods
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CHOOSE HOW TO PAY", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = GIVE.tertiary, modifier = Modifier.size(11.dp))
                        Text("Reorder", style = giInter(11), color = GIVE.tertiary)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.forEachIndexed { i, m ->
                        val soon = m.provider == null
                        val on = m.id == selectedMethod && !soon
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .background(if (on) GIVE.priorityBg else GIVE.white)
                                .border(if (on) 1.5.dp else 1.dp, if (on) GIVE.gold else GIVE.border, RoundedCornerShape(18.dp))
                                .then(if (soon) Modifier.alpha(0.7f) else Modifier.clickable { selectedMethod = m.id })
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // badge
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(m.badgeBg), contentAlignment = Alignment.Center) {
                                if (m.badgeText != null) {
                                    Text(
                                        m.badgeText,
                                        style = giInter(if (m.badgeText.length > 3) 8 else 11, FontWeight.Bold, -0.2f),
                                        color = m.badgeFg,
                                        textAlign = TextAlign.Center,
                                    )
                                } else {
                                    Icon(m.badgeIcon!!, contentDescription = null, tint = m.badgeFg, modifier = Modifier.size(18.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(m.label, style = giInter(14, FontWeight.SemiBold, -0.14f), color = GIVE.navy)
                                if (on) {
                                    Text(
                                        if (m.id == "mpesa" || m.id == "airtel") phone.ifBlank { m.sub } else m.sub,
                                        style = giInter(11), color = GIVE.sub,
                                    )
                                }
                            }
                            if (soon) {
                                Box(Modifier.clip(Capsule).background(GIVE.goldChipBg).padding(horizontal = 9.dp, vertical = 4.dp)) {
                                    Text("SOON", style = giInter(10, FontWeight.Bold, 0.5f), color = GIVE.goldChipText)
                                }
                            }
                            if (on) {
                                Box(Modifier.size(24.dp).clip(CircleShape).background(GIVE.gold), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(13.dp))
                                }
                            }
                            // reorder arrows
                            Column {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp, contentDescription = "Move up", tint = GIVE.ink300,
                                    modifier = Modifier.size(14.dp).clickable {
                                        if (i > 0) { val t = methods[i]; methods[i] = methods[i - 1]; methods[i - 1] = t }
                                    },
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown, contentDescription = "Move down", tint = GIVE.ink300,
                                    modifier = Modifier.size(14.dp).clickable {
                                        if (i < methods.lastIndex) { val t = methods[i]; methods[i] = methods[i + 1]; methods[i + 1] = t }
                                    },
                                )
                            }
                            Icon(Icons.Filled.DragIndicator, contentDescription = null, tint = Color(0xFFC4C9D0), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Cover-fee row
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(GIVE.white)
                        .border(1.dp, GIVE.border, RoundedCornerShape(18.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Cover the transaction fee", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
                        Text("Adds ${kshMajor(giveFee(amountMajor))} — 100% reaches the fund", style = giInter(11), color = GIVE.sub)
                    }
                    Switch(
                        checked = coverFee,
                        onCheckedChange = { coverFee = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = GIVE.gold, checkedThumbColor = Color.White),
                    )
                }

                // Active schedules rail
                if (activeSchedules.isNotEmpty()) {
                    Text("ACTIVE SCHEDULES", style = giInter(9, FontWeight.SemiBold, 1.6f), color = GIVE.overline)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        activeSchedules.forEach { s ->
                            Column(
                                Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.white)
                                    .border(1.dp, GIVE.border, RoundedCornerShape(16.dp))
                                    .clickable { sheetSchedule = s }
                                    .padding(12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(12.dp))
                                    Text(s.frequency.uppercase(), style = giInter(11, FontWeight.Bold, 1.4f), color = GIVE.overline)
                                }
                                Text(ksh(s.amountMinor), style = giInter(15, FontWeight.Bold, -0.15f), color = GIVE.navy, modifier = Modifier.padding(top = 4.dp))
                                Text(s.fund.replaceFirstChar { it.uppercase() }, style = giInter(13), color = GIVE.sub)
                                Text("Next ${prettyDate(s.nextRunAt)}", style = giInter(11), color = GIVE.tertiary)
                            }
                        }
                    }
                }
            }
        }

        // ── Sticky CTA ──
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(GIVE.paper)
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        ) {
            error?.let {
                Text(it, style = giInter(12), color = GIVE.danger, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.white)
                    .border(1.dp, GIVE.gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable(enabled = amountMajor > 0 && !busy) { submit() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = GIVE.gold, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Processing…", style = giInter(14, FontWeight.SemiBold), color = GIVE.gold)
                } else if (freq != 0) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Schedule ${kshMajor(amountMajor)} / ${if (freq == 1) "week" else "month"}", style = giInter(14, FontWeight.SemiBold), color = GIVE.gold)
                } else {
                    Text("Give ${kshMajor(amountMajor)}", style = giInter(14, FontWeight.SemiBold), color = GIVE.gold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(14.dp))
                }
            }
        }

        // ── Recurring-gift bottom sheet ──
        sheetSchedule?.let { s ->
            ModalBottomSheet(onDismissRequest = { sheetSchedule = null }) {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recurring gift", style = giSerif(18, FontWeight.SemiBold, -0.36f), color = GIVE.navy)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(GIVE.surface).clickable { sheetSchedule = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = GIVE.navy, modifier = Modifier.size(15.dp))
                        }
                    }
                    Row(
                        Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(GIVE.gold.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Autorenew, contentDescription = null, tint = GIVE.gold, modifier = Modifier.size(19.dp))
                        }
                        Column {
                            Text(ksh(s.amountMinor), style = giInter(17, FontWeight.Bold), color = GIVE.navy)
                            Text(
                                "Every ${if (s.frequency == "weekly") "week" else "month"} · ${s.fund.replaceFirstChar { it.uppercase() }}",
                                style = giInter(12), color = GIVE.sub,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SheetDetailRow("Fund", s.fund.replaceFirstChar { it.uppercase() })
                    SheetDivider()
                    SheetDetailRow("Amount", ksh(s.amountMinor))
                    SheetDivider()
                    SheetDetailRow("Frequency", s.frequency.replaceFirstChar { it.uppercase() })
                    SheetDivider()
                    SheetDetailRow("Next charge", prettyDate(s.nextRunAt))
                    SheetDivider()
                    SheetDetailRow("Method", s.method.ifBlank { "—" }.replaceFirstChar { it.uppercase() })
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.cancelBg)
                            .border(1.dp, GIVE.cancelBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                scope.launch {
                                    try { Net.client.api.cancelSchedule(s.scheduleId); reload() } catch (_: Exception) {}
                                }
                                sheetSchedule = null
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("Cancel schedule", style = giInter(13, FontWeight.Bold), color = GIVE.cancelText)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetDetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = giInter(12), color = GIVE.sub)
        Text(value, style = giInter(13, FontWeight.SemiBold), color = GIVE.navy)
    }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(GIVE.border))
}

/** Full-screen generosity ceremony once an intent is created. */
@Composable
private fun GiveResult(r: GivingIntentResult, onDone: () -> Unit) {
    val context = LocalContext.current
    // PayPal is a two-step settle: approve in the browser, then POST
    // /giving/paypal/capture with the order id (the intent's provider_ref) —
    // without the capture the gift never settles (money §5.6: online-only).
    val scope = rememberCoroutineScope()
    var captured by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    // Human moment — only once the server says the gift SUCCEEDED: an intent that
    // comes back succeeded/settled, or a confirmed PayPal capture. Never on a
    // pending intent — server truth only (§5.6).
    LaunchedEffect(captured, r.status) {
        if (captured || r.status.lowercase() in setOf("succeeded", "settled")) {
            CelebrationCenter.fire(Moment("gift-${r.providerRef ?: r.transactionId}", "Thank you for sowing", "Every gift carries the gospel further."))
        }
    }
    Box(Modifier.fillMaxSize().background(GIVE.paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Success badge — gold@0.18 halo + gold core + navy check.
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(GIVE.gold.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(GIVE.gold), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = GIVE.navy, modifier = Modifier.size(34.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Thank you for your generosity",
                style = giSerif(24, FontWeight.Medium, -0.48f),
                color = GIVE.navy,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            val sub = when {
                captured -> "Gift confirmed — receipt on its way. 🎉"
                r.provider == "mpesa" -> "Check your phone to approve the M-Pesa prompt."
                r.approveUrl != null -> "Continue on PayPal, then confirm below."
                else -> "Your gift is being processed."
            }
            Text(sub, style = giInter(13), color = if (captured) GIVE.successText else GIVE.sub, textAlign = TextAlign.Center)

            if (r.approveUrl != null && !captured) {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.navy)
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.approveUrl)))
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Continue on PayPal", style = giInter(14, FontWeight.SemiBold), color = Color.White)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(GIVE.white).border(1.dp, GIVE.gold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable(enabled = !capturing && !r.providerRef.isNullOrBlank()) {
                            capturing = true; captureError = null
                            scope.launch {
                                runCatching { Net.client.api.capturePayPal(PayPalCaptureBody(r.providerRef!!)) }
                                    .onSuccess { captured = true }
                                    .onFailure { captureError = org.nuruplace.member.data.net.ApiException.message(it) }
                                capturing = false
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (capturing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = GIVE.gold, strokeWidth = 2.dp)
                    } else {
                        Text("I've approved — confirm gift", style = giInter(14, FontWeight.SemiBold), color = GIVE.gold)
                    }
                }
                captureError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = giInter(11), color = GIVE.danger, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(GIVE.gold)
                    .clickable { onDone() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Done", style = giInter(14, FontWeight.SemiBold), color = GIVE.navy)
            }
        }
    }
}
