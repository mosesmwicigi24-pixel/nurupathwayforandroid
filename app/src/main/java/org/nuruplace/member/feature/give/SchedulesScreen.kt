// Recurring gifts — the member's giving schedules with a cancel action. Port of
// the iOS schedules list.
package org.nuruplace.member.feature.give

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.GivingSchedule
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.relTime

@Composable
fun SchedulesScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.schedules().data }) { schedules: List<GivingSchedule>, reload ->
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Recurring gifts", kicker = "Give", onBack = onBack)
            if (schedules.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No recurring gifts set up.", style = NuruType.body, color = Nuru.ink600) }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(schedules, key = { it.scheduleId }) { s -> ScheduleCard(s, onChanged = reload) }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(s: GivingSchedule, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val active = s.status == "active"
    NuruCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmtMoney(s.amountMinor, s.currency) + " · ${s.frequency}", style = NuruType.cardTitle, color = Nuru.ink)
                Text("${s.fund.replaceFirstChar { it.uppercase() }} · ${s.method}", style = NuruType.caption, color = Nuru.ink600)
                if (active) Text("Next: ${relTime(s.nextRunAt)}", style = NuruType.micro, color = Nuru.goldLo)
                else Text("Cancelled", style = NuruType.micro, color = Nuru.ink400, fontWeight = FontWeight.SemiBold)
            }
            if (active) {
                TextButton(onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch { try { Net.client.api.cancelSchedule(s.scheduleId); onChanged() } catch (_: Exception) {} finally { busy = false } }
                    }
                }) { Text("Cancel", style = NuruType.cardCta, color = Nuru.danger) }
            }
        }
    }
}
