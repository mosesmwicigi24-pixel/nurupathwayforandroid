// Prayer journal — the member's PRIVATE prayers (§5.4). Add, mark answered (with a
// note) and delete. Writes are online-first for now. Port of the iOS PrayerJournalView.
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PrayerEntry
import org.nuruplace.member.data.net.PrayerUpsertBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import java.util.UUID

@Composable
fun PrayerJournalScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.prayers().data }) { entries: List<PrayerEntry>, reload ->
        val scope = rememberCoroutineScope()
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Prayer journal", kicker = "Private", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    NuruCard {
                        OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, singleLine = true, shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedTextField(body, { body = it }, label = { Text("What are you praying for?") }, minLines = 2, shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Spacing.sm))
                        PrimaryButton("Add prayer", loading = busy, enabled = body.isNotBlank(), onClick = {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    try {
                                        Net.client.api.upsertPrayer(
                                            PrayerUpsertBody(
                                                entryId = UUID.randomUUID().toString(),
                                                title = title.trim().ifBlank { null },
                                                body = body.trim(),
                                                clientMutationId = UUID.randomUUID().toString(),
                                            ),
                                        )
                                        title = ""; body = ""; reload()
                                    } catch (_: Exception) {} finally { busy = false }
                                }
                            }
                        })
                    }
                }
                items(entries, key = { it.entryId }) { e -> PrayerCard(e, onChanged = reload) }
            }
        }
    }
}

@Composable
private fun PrayerCard(e: PrayerEntry, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    NuruCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                e.title?.let { Text(it, style = NuruType.rowTitle, color = Nuru.ink) }
                Text(e.body, style = NuruType.body, color = Nuru.ink)
                if (e.isAnswered) {
                    Spacer(Modifier.height(2.dp))
                    Text("Answered${e.answeredNote?.let { " · $it" } ?: ""}", style = NuruType.micro, color = Nuru.successText)
                }
            }
            IconButton(onClick = {
                if (!busy) {
                    busy = true
                    scope.launch { try { Net.client.api.deletePrayer(e.entryId); onChanged() } catch (_: Exception) {} finally { busy = false } }
                }
            }) { Icon(Icons.Filled.Delete, "Delete", tint = Nuru.ink400) }
        }
        if (!e.isAnswered) {
            TextButton(onClick = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            Net.client.api.upsertPrayer(
                                PrayerUpsertBody(entryId = e.entryId, title = e.title, body = e.body, isAnswered = true, clientMutationId = UUID.randomUUID().toString()),
                            )
                            onChanged()
                        } catch (_: Exception) {} finally { busy = false }
                    }
                }
            }) {
                Icon(Icons.Filled.Check, null, tint = Nuru.success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Mark answered", style = NuruType.cardCta, color = Nuru.success)
            }
        }
    }
}
