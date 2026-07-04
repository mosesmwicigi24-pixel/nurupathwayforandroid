// Verse library — the member's saved verses. Add (reference + text/note) and
// delete. Writes are online-first for now. Port of the iOS VerseLibraryView.
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.offline.queuePayload
import org.nuruplace.member.data.offline.runOrQueue
import org.nuruplace.member.data.net.SavedVerse
import org.nuruplace.member.data.net.VerseUpsertBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import java.util.UUID

@Composable
fun VerseLibraryScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.verses().data }) { verses: List<SavedVerse>, reload ->
        val scope = rememberCoroutineScope()
        var reference by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }

        Column(Modifier.fillMaxSize().background(Nuru.paper).imePadding()) {
            ScreenHeader("Verse library", kicker = "Saved", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    NuruCard {
                        OutlinedTextField(reference, { reference = it }, label = { Text("Reference (e.g. John 3:16)") }, singleLine = true, shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedTextField(text, { text = it }, label = { Text("Verse text or note (optional)") }, minLines = 2, shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Spacing.sm))
                        PrimaryButton("Save verse", loading = busy, enabled = reference.isNotBlank(), onClick = {
                            if (!busy) {
                                busy = true
                                scope.launch {
                                    try {
                                        val dto = VerseUpsertBody(
                                            savedVerseId = UUID.randomUUID().toString(),
                                            reference = reference.trim(),
                                            verseText = text.trim().ifBlank { null },
                                            clientMutationId = UUID.randomUUID().toString(),
                                        )
                                        Net.client.offline.runOrQueue("saved_verses", "save", queuePayload(dto)) { Net.client.api.saveVerse(dto) }
                                        reference = ""; text = ""; reload()
                                    } catch (_: Exception) {} finally { busy = false }
                                }
                            }
                        })
                    }
                }
                items(verses, key = { it.savedVerseId }) { v ->
                    NuruCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Kicker(v.reference)
                                v.verseText?.let { Text(it, style = NuruType.body, color = Nuru.ink) }
                                v.note?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                            }
                            IconButton(onClick = {
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val payload = kotlinx.serialization.json.buildJsonObject { put("saved_verse_id", kotlinx.serialization.json.JsonPrimitive(v.savedVerseId)) }
                                            Net.client.offline.runOrQueue("saved_verses", "delete", payload) { Net.client.api.deleteVerse(v.savedVerseId) }
                                            reload()
                                        } catch (_: Exception) {} finally { busy = false }
                                    }
                                }
                            }) { Icon(Icons.Filled.Delete, "Delete", tint = Nuru.ink400) }
                        }
                    }
                }
            }
        }
    }
}
