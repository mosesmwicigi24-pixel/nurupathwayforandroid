// My attendance — the streak (current run, longest, breaks, failures) over a
// service-by-service history where MISSES are visible. Showing the misses is the
// point: a streak number alone doesn't tell a member which Sunday they lost.
//
// Port parity: iOS AttendanceView.swift.
package org.nuruplace.member.feature.attendance

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.AttendanceHistoryEntry
import org.nuruplace.member.data.net.AttendanceStreak
import org.nuruplace.member.data.net.ChurchService
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun AttendanceScreen(onBack: () -> Unit, onCheckIn: () -> Unit) {
    var streak by remember { mutableStateOf<AttendanceStreak?>(null) }
    var history by remember { mutableStateOf<List<AttendanceHistoryEntry>>(emptyList()) }
    var openNow by remember { mutableStateOf<List<ChurchService>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            streak = Net.client.api.attendanceStreak()
            history = Net.client.api.attendanceHistory().data
            openNow = Net.client.api.openServices().data
        } catch (ex: Exception) {
            error = ApiException.message(ex)
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Nuru.navyDeep)) {
        ScreenHeader("My attendance", kicker = "CHURCH SERVICES", onBack = onBack)

        // A service open right now is the most useful thing on this screen —
        // surface it above the numbers so arriving members can act immediately.
        val scannable = openNow.firstOrNull { it.checkinOpen && !it.attended }
        if (scannable != null) {
            Column(Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.base)) {
                Text("${scannable.title} is open for check-in", style = NuruType.body, color = Nuru.onNavy)
                Spacer(Modifier.height(Spacing.sm))
                PrimaryButton("Scan to check in", onClick = onCheckIn)
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…", style = NuruType.body, color = Nuru.onNavyDim)
            }
            error != null -> Box(Modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
                Text(error!!, style = NuruType.body, color = Nuru.goldLight)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item {
                    streak?.let { StreakSummary(it) }
                    Spacer(Modifier.height(Spacing.lg))
                    Text("SERVICE BY SERVICE", style = NuruType.sectionLabel, color = Nuru.gold)
                    Spacer(Modifier.height(Spacing.sm))
                }
                if (history.isEmpty()) {
                    item {
                        Text(
                            "No services yet. Scan the QR at church and your record starts.",
                            style = NuruType.body, color = Nuru.onNavyDim,
                        )
                    }
                }
                items(history) { entry -> HistoryRow(entry) }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: AttendanceHistoryEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Nuru.onNavy.copy(alpha = 0.06f))
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Gold dot = present, hollow = a miss. Legible at a glance down the column.
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .then(
                    if (entry.attended) Modifier.background(Nuru.gold)
                    else Modifier.border(1.dp, Nuru.onNavyFaint, CircleShape),
                ),
        )
        Spacer(Modifier.size(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = NuruType.rowTitle, color = Nuru.onNavy)
            Text(entry.serviceDate, style = NuruType.caption, color = Nuru.onNavyDim)
        }
        Text(
            if (entry.attended) entry.attendedAt?.let { shortTime(it) } ?: "Present" else "Missed",
            style = NuruType.caption,
            color = if (entry.attended) Nuru.gold else Nuru.onNavyFaint,
        )
    }
}
