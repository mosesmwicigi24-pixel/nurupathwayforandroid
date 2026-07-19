// Selah — My Thoughts (Prayer Room tab 3, docs: prayer-room-arc). Owner spec
// 2026-07-18: "Selah — a word from the Psalms meaning pause and reflect. This
// is your quiet page: write what's on your heart. Only you can see it." A
// private, rich-text + pen journal — no leader/admin read path exists for this
// domain, by design (§5.4). Port of iOS SelahView; follows PrayerJournalScreen's
// AsyncContent + runOrQueue idiom rather than a separate ViewModel class.
package org.nuruplace.member.feature.community

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.Thought
import org.nuruplace.member.data.net.ThoughtUpsertBody
import org.nuruplace.member.data.offline.queuePayload
import org.nuruplace.member.data.offline.runOrQueue
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val EXPLAINER_PREF = "nuru_selah"
private const val EXPLAINER_KEY = "explainer_dismissed"

private fun explainerDismissed(context: Context): Boolean =
    context.getSharedPreferences(EXPLAINER_PREF, Context.MODE_PRIVATE).getBoolean(EXPLAINER_KEY, false)

private fun dismissExplainer(context: Context) {
    context.getSharedPreferences(EXPLAINER_PREF, Context.MODE_PRIVATE).edit().putBoolean(EXPLAINER_KEY, true).apply()
}

@Composable
fun SelahScreen() {
    val context = LocalContext.current
    val view = LocalView.current

    AsyncContent(load = { Net.client.api.thoughts().data.sortedByDescending { it.createdAt } }, refreshable = true) { thoughts: List<Thought>, reload ->
        val scope = rememberCoroutineScope()
        var editing by remember { mutableStateOf<SelahDraft?>(null) }
        var dismissedExplainer by remember { mutableStateOf(explainerDismissed(context)) }

        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text("Pause. Reflect. Write.", style = NuruType.title, color = Nuru.navy)
            }
            if (!dismissedExplainer) {
                item {
                    ExplainerCard {
                        Haptics.tap(view); dismissExplainer(context); dismissedExplainer = true
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    NewThoughtPill { Haptics.tap(view); editing = SelahDraft() }
                }
            }
            if (thoughts.isEmpty()) {
                item { EmptyThoughts { editing = SelahDraft() } }
            } else {
                items(thoughts, key = { it.thoughtId }) { t ->
                    ThoughtRowCard(t) { Haptics.tap(view); editing = SelahDraft(t) }
                }
            }
        }

        editing?.let { draft ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { editing = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                SelahEditorScreen(
                    draft = draft,
                    onDismiss = { editing = null },
                    onSave = { d ->
                        scope.launch {
                            val dto = ThoughtUpsertBody(
                                thoughtId = d.thoughtId,
                                title = d.title.ifBlank { null },
                                body = d.body,
                                bodySpans = d.spans.ifEmpty { null },
                                drawingUrls = d.drawingUrls,
                                clientMutationId = UUID.randomUUID().toString(),
                            )
                            runCatching {
                                Net.client.offline.runOrQueue("member_thoughts", "upsert", queuePayload(dto)) {
                                    Net.client.api.upsertThought(dto)
                                }
                            }
                            editing = null
                            reload()
                        }
                    },
                    onDelete = if (draft.isNew) null else {
                        {
                            scope.launch {
                                val payload = buildJsonObject { put("thought_id", JsonPrimitive(draft.thoughtId)) }
                                runCatching {
                                    Net.client.offline.runOrQueue("member_thoughts", "delete", payload) {
                                        Net.client.api.deleteThought(draft.thoughtId)
                                    }
                                }
                                editing = null
                                reload()
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NewThoughtPill(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE0B85E), Color(0xFFC9A227))))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text("New Thought", style = NuruType.actionLabel, color = Color.White)
    }
}

@Composable
private fun ExplainerCard(onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii_card)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(Radii_card)).padding(Spacing.base),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Nuru.surface),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.EditNote, null, tint = Nuru.gold, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(
            "Selah — a word from the Psalms meaning pause and reflect. This is your quiet page: write what's on your heart. Only you can see it.",
            style = NuruType.caption, color = Nuru.ink600,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.size(24.dp).clip(CircleShape).clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "Dismiss", tint = Nuru.ink400, modifier = Modifier.size(13.dp)) }
    }
}

@Composable
private fun EmptyThoughts(onCompose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii_card)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(Radii_card))
            .clickable { onCompose() }
            .padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.EditNote, null, tint = Nuru.gold, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Selah. Pause here — write your first thought.",
            style = NuruType.rowTitle, color = Nuru.navy,
            modifier = Modifier.padding(horizontal = Spacing.lg),
        )
    }
}

@Composable
private fun ThoughtRowCard(thought: Thought, onOpen: () -> Unit) {
    val preview = remember(thought.body) {
        val flat = thought.body.replace("\n", " ").trim()
        if (flat.length > 140) flat.take(140) + "…" else flat
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(18.dp))
            .clickable { onOpen() }
            .padding(Spacing.base),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                thought.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                style = NuruType.rowTitle, color = Nuru.navy, maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(relativeThoughtLabel(thought.updatedAt), style = NuruType.micro, color = Nuru.ink400)
        }
        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(preview, style = NuruType.body, color = Nuru.ink600, maxLines = 2)
        }
    }
}

private val Radii_card = 18.dp

private fun relativeThoughtLabel(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    val then = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(then, today)
    return when {
        days < 1 -> "today"
        days == 1L -> "1 day ago"
        days < 7 -> "$days days ago"
        else -> instant.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
