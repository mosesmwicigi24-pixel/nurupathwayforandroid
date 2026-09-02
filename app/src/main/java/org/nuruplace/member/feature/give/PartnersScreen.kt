package org.nuruplace.member.feature.give

// The Partners page — recognition, not receipts. The Android half of the same
// design as iOS PartnersView.swift; keep the two in step.
//
// Receipts live in Giving (history, statements) and stay there. This screen
// answers a different question: what has my faithfulness added up to?
//
// Everything is derived server-side from the member's giving schedule, so this
// screen holds no second copy of the truth. Two rules from the design carry all
// the way into the copy, and both are easy to erode later:
//
//   · `kept` is cycles COLLECTED, never scheduled. The label reads "collected"
//     for exactly that reason — a partner whose June failed did not keep June.
//   · the season block is what the WHOLE CHURCH did while they partnered. Never
//     "your giving produced this": we cannot trace a shilling to a disciple.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.Partnership
import org.nuruplace.member.data.net.PartnerRhythm
import org.nuruplace.member.data.net.PartnerSeason
import org.nuruplace.member.data.net.PartnerTrouble
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.nuruSerif
import androidx.compose.ui.text.font.FontWeight
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class PartnersViewModel : ViewModel() {
    var partnership by mutableStateOf<Partnership?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var resuming by mutableStateOf(false); private set

    fun load() {
        viewModelScope.launch {
            loading = true; error = null
            partnership = runCatching { Net.client.api.partnership() }.getOrNull()
            if (partnership == null) error = "We couldn't load this just now."
            loading = false
        }
    }

    fun resume(scheduleId: String) {
        viewModelScope.launch {
            resuming = true
            val ok = runCatching { Net.client.api.resumeSchedule(scheduleId) }.isSuccess
            resuming = false
            if (ok) load() else error = "That didn't go through. Your giving is unchanged."
        }
    }
}

@Composable
fun PartnersScreen(vm: PartnersViewModel = remember { PartnersViewModel() }) {
    LaunchedEffect(Unit) { if (vm.partnership == null) vm.load() }

    Column(
        Modifier.fillMaxSize().background(Nuru.paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val p = vm.partnership
        when {
            p != null && p.isPartner -> {
                PartnerStanding(p)
                // Shown ONLY when there is something to say. A partner whose
                // giving is collecting cleanly never sees a warning-shaped block.
                p.trouble?.let { t ->
                    PartnerTroubleCard(t, vm.resuming) {
                        p.scheduleId?.let(vm::resume)
                    }
                }
                p.rhythm?.let { PartnerRhythmCard(it, p.currency) }
                p.sinceYouBegan?.let { PartnerSeasonCard(it) }
                Text(
                    "Your gifts, receipts and statements stay in Giving.",
                    style = NuruType.caption, color = Nuru.ink400,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            p != null -> PartnerNotice(
                if (p.everPartnered) "You have partnered before" else "Become a partner",
                if (p.everPartnered)
                    "Your partnership ended, and nothing is owed. If you would like to begin again, you can set up a monthly gift in Giving."
                else
                    "A partner decides in advance to keep giving, month after month, so the church can plan beyond what arrives on a Sunday. You can begin in Giving — and change it or stop whenever you need to.",
            )
            vm.loading -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), Alignment.Center) {
                CircularProgressIndicator(color = Nuru.gold)
            }
            vm.error != null -> PartnerNotice(
                "We couldn't load this just now",
                "Your giving is unaffected. Pull down to try again.",
            )
        }
    }
}

@Composable
private fun PartnerStanding(p: Partnership) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Nuru.white)
            .border(1.dp, Nuru.gold.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("YOUR STANDING", style = NuruType.micro, color = Nuru.goldLo)
        Text(
            p.since?.let { PartnerFormat.monthYear(it) }
                ?.let { "You have partnered since $it." }
                ?: "You are a partner of this church.",
            style = nuruSerif(26, FontWeight.Medium), color = Nuru.ink,
        )
        if (p.kept > 0) {
            Row(verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${p.kept}", style = nuruSerif(30, FontWeight.Medium), color = Nuru.gold)
                // "collected", never "kept" — the word carries the rule the
                // server enforces.
                Text(
                    if (p.kept == 1) "gift collected" else "gifts collected",
                    style = NuruType.body, color = Nuru.ink600,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PartnerTroubleCard(t: PartnerTrouble, resuming: Boolean, onResume: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Nuru.goldChipBg)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (t.paused) "Your giving is paused" else "One gift didn't go through",
            style = NuruType.heading, color = Nuru.ink,
        )
        // Plain, never alarming. Nothing is owed, and we say so first.
        Text(
            if (t.paused)
                "We tried a few times and couldn't collect it, so we stopped trying rather than keep charging you. Nothing is owed. Starting again picks up from your next gift — it will not collect the one that was missed."
            else
                "We couldn't collect your last gift. We'll try again shortly, and nothing is owed in the meantime.",
            style = NuruType.body, color = Nuru.ink600,
        )
        if (t.paused) {
            Button(
                onClick = onResume, enabled = !resuming,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Nuru.navyDeep, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (resuming) {
                    CircularProgressIndicator(
                        color = Color.White, strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 8.dp).size(16.dp))
                }
                Text(if (resuming) "Starting again…" else "Start it again", style = NuruType.label)
            }
        }
    }
}

@Composable
private fun PartnerRhythmCard(r: PartnerRhythm, currency: String) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Nuru.white).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("YOUR RHYTHM", style = NuruType.micro, color = Nuru.goldLo)
        Row(verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$currency ${PartnerFormat.grouped(r.amountMinor / 100)}",
                style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
            Text(if (r.frequency == "weekly") "each week" else "each month",
                style = NuruType.body, color = Nuru.ink600,
                modifier = Modifier.padding(bottom = 2.dp))
        }
        PartnerRow("Method", when (r.method) {
            "mpesa" -> "M-Pesa"; "airtel" -> "Airtel Money"; else -> "Card"
        })
        PartnerRow("Fund", r.fund.replaceFirstChar { it.uppercase() })
        // A paused schedule carries no next date, and we say the true thing
        // rather than showing a stale one.
        PartnerRow("Next gift", r.nextRunAt?.let { PartnerFormat.dayMonth(it) } ?: "Paused")
    }
}

@Composable
private fun PartnerRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = NuruType.body, color = Nuru.ink600)
        Text(value, style = NuruType.body, color = Nuru.ink)
    }
}

@Composable
private fun PartnerSeasonCard(s: PartnerSeason) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Nuru.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SINCE YOU BEGAN", style = NuruType.micro, color = Nuru.goldLo)
        // The framing IS the honesty. "Across the church" is load-bearing —
        // remove it and the page starts claiming what we cannot prove.
        Text("Across the church, in the season you have been partnering:",
            style = NuruType.body, color = Nuru.ink600)

        val empty = s.levelsCompleted == 0 && s.modulesCompleted == 0 && s.plansFinished == 0
        if (empty) {
            Text("It is early days. This will fill as the church walks on.",
                style = NuruType.body, color = Nuru.ink400)
        } else {
            if (s.levelsCompleted > 0) PartnerCount(
                s.levelsCompleted, "disciple finished a level", "disciples finished a level")
            if (s.modulesCompleted > 0) PartnerCount(
                s.modulesCompleted, "module completed", "modules completed")
            if (s.plansFinished > 0) PartnerCount(
                s.plansFinished, "reading plan finished", "reading plans finished")
        }
    }
}

@Composable
private fun PartnerCount(n: Int, one: String, many: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom) {
        Text("$n", style = nuruSerif(24, FontWeight.Medium), color = Nuru.gold,
            modifier = Modifier.widthIn(min = 44.dp))
        Text(if (n == 1) one else many, style = NuruType.body, color = Nuru.ink,
            modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun PartnerNotice(title: String, message: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp)
            .clip(RoundedCornerShape(14.dp)).background(Nuru.white).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = nuruSerif(24, FontWeight.Medium), color = Nuru.ink)
        Text(message, style = NuruType.bodyLg, color = Nuru.ink600)
    }
}

/**
 * Timestamps arrive from Postgres with OR without fractional seconds depending
 * on the column. Trying both is the difference between a real date and a screen
 * that quietly says "Paused" when nothing is paused.
 */
private object PartnerFormat {
    private val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )

    private fun parse(iso: String): Date? = patterns.firstNotNullOfOrNull { p ->
        runCatching {
            SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(iso)
        }.getOrNull()
    }

    fun monthYear(iso: String): String? =
        parse(iso)?.let { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(it) }

    fun dayMonth(iso: String): String =
        parse(iso)?.let { SimpleDateFormat("d MMMM", Locale.getDefault()).format(it) } ?: "Paused"

    fun grouped(n: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(n)
}
