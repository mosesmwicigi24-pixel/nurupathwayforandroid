// Prayer journal — the member's PRIVATE prayers (§5.4). Add, edit, mark
// answered, publish to Corporate, and delete. Writes are online-first for
// now. Port of the iOS PrayerJournalView (builds 78/80): the management
// surface is ONE row (Active/Answered chips + a gold "Add Prayer" pill), and
// every per-card action lives behind a single ⋮ overflow menu.
package org.nuruplace.member.feature.grow

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PrayerEntry
import org.nuruplace.member.data.net.PrayerUpsertBody
import org.nuruplace.member.data.offline.queuePayload
import org.nuruplace.member.data.offline.runOrQueue
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.CelebrationCenter
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.Moment
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Active = still-open private prayers; Answered = resolved ones. Public so
 *  PrayerRoomScreen can pin its "Answered" tab straight to this filter. */
enum class PrayerTab { Active, Answered }

private val ShareOrange = Color(0xFFF97316)
private val PrayerGold = Color(0xFFC9A227)

@Composable
fun PrayerJournalScreen(
    embedded: Boolean = false,
    onBack: () -> Unit = {},
    forcedTab: PrayerTab? = null,
) {
    AsyncContent(load = { Net.client.api.prayers().data }) { entries: List<PrayerEntry>, reload ->
        val scope = rememberCoroutineScope()
        var tab by remember { mutableStateOf(forcedTab ?: PrayerTab.Active) }
        var editing by remember { mutableStateOf<PrayerEntry?>(null) }
        var showComposer by remember { mutableStateOf(false) }
        // Entry ids shared to Corporate Prayer during this session — session-
        // local exactly like iOS's `sharedToWall` (the server has no separate
        // "is this shared" flag on the entry; re-sharing is idempotent).
        var sharedToWall by remember { mutableStateOf<Set<String>>(emptySet()) }
        // The owner's own name (profile, NOT "You" — iOS build 80 parity).
        var ownerName by remember { mutableStateOf("You") }
        LaunchedEffect(Unit) {
            ownerName = runCatching { Net.client.api.me().profile.fullName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "You"
        }

        val active = entries.filter { !it.isAnswered }
        val answered = entries.filter { it.isAnswered }
        val visible = if (tab == PrayerTab.Active) active else answered

        Column(Modifier.fillMaxSize().background(Nuru.paper).imePadding()) {
            // Embedded (My Prayer Room) supplies its own back button + title +
            // segmented control, so this screen drops its standalone header.
            if (!embedded) ScreenHeader("My Prayer Room", kicker = "Private", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item {
                    // ONE row: Active/Answered chips (hidden when force-pinned)
                    // + the gold "Add Prayer" pill — the whole management
                    // surface in a single place (iOS build 80 parity).
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (forcedTab == null) {
                            PrayerChip("Active (${active.size})", tab == PrayerTab.Active) { tab = PrayerTab.Active }
                            PrayerChip("Answered (${answered.size})", tab == PrayerTab.Answered) { tab = PrayerTab.Answered }
                        }
                        Spacer(Modifier.weight(1f))
                        AddPrayerPill { editing = null; showComposer = true }
                    }
                }
                if (visible.isEmpty()) {
                    item { EmptyPrayers(tab) }
                } else {
                    items(visible, key = { it.entryId }) { e ->
                        JournalCard(
                            entry = e,
                            ownerName = ownerName,
                            shared = sharedToWall.contains(e.entryId),
                            onReopenOrAnswer = {
                                scope.launch {
                                    val dto = PrayerUpsertBody(entryId = e.entryId, title = e.title, body = e.body, isAnswered = !e.isAnswered, clientMutationId = UUID.randomUUID().toString())
                                    runCatching { Net.client.offline.runOrQueue("prayer_entries", "upsert", queuePayload(dto)) { Net.client.api.upsertPrayer(dto) } }
                                    reload()
                                }
                            },
                            onEdit = { editing = e; showComposer = true },
                            onPublish = {
                                scope.launch {
                                    runCatching {
                                        Net.client.api.sharePrayerToWall(e.entryId)
                                        sharedToWall = sharedToWall + e.entryId
                                        CelebrationCenter.fire(Moment("prayer-share-${e.entryId}", "Shared to Corporate Prayer", "Your cell is standing with you 🙏", confetti = false))
                                    }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    val payload = kotlinx.serialization.json.buildJsonObject { put("entry_id", kotlinx.serialization.json.JsonPrimitive(e.entryId)) }
                                    runCatching { Net.client.offline.runOrQueue("prayer_entries", "delete", payload) { Net.client.api.deletePrayer(e.entryId) } }
                                    reload()
                                }
                            },
                        )
                    }
                }
            }
        }

        if (showComposer) {
            PrayerComposerDialog(
                draft = editing,
                onDismiss = { showComposer = false },
                onSave = { title, body, answered2 ->
                    scope.launch {
                        val dto = PrayerUpsertBody(
                            entryId = editing?.entryId ?: UUID.randomUUID().toString(),
                            title = title.trim().ifBlank { null },
                            body = body.trim(),
                            isAnswered = answered2,
                            clientMutationId = UUID.randomUUID().toString(),
                        )
                        runCatching { Net.client.offline.runOrQueue("prayer_entries", "upsert", queuePayload(dto)) { Net.client.api.upsertPrayer(dto) } }
                        showComposer = false
                        reload()
                    }
                },
            )
        }
    }
}

@Composable
private fun PrayerChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) Nuru.navy else Nuru.white)
            .border(1.dp, if (selected) Color.Transparent else Nuru.border, RoundedCornerShape(999.dp))
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, style = NuruType.chipLabel, color = if (selected) Color.White else Nuru.ink600, maxLines = 1)
    }
}

@Composable
private fun AddPrayerPill(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE0B85E), Color(0xFFC9A227))))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text("Add Prayer", style = NuruType.actionLabel, color = Color.White)
    }
}

@Composable
private fun EmptyPrayers(tab: PrayerTab) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (tab == PrayerTab.Active) "🤍" else "🙏", style = NuruType.display)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (tab == PrayerTab.Active) "No active prayers yet" else "No answered prayers yet",
            style = NuruType.rowTitle, color = Nuru.ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (tab == PrayerTab.Active) "Add what you're praying for — it stays private." else "Mark a prayer answered and it lands here.",
            style = NuruType.caption, color = Nuru.ink600,
        )
    }
}

@Composable
private fun JournalCard(
    entry: PrayerEntry,
    ownerName: String,
    shared: Boolean,
    onReopenOrAnswer: () -> Unit,
    onEdit: () -> Unit,
    onPublish: () -> Unit,
    onDelete: () -> Unit,
) {
    val view = LocalView.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmShare by remember { mutableStateOf(false) }

    // Green answered · orange on-the-wall · gold private — ONLY the avatar,
    // name and timestamp carry this color; title/body stay neutral (iOS
    // build 80 parity).
    val statusColor = when {
        entry.isAnswered -> Nuru.success
        shared -> ShareOrange
        else -> PrayerGold
    }
    val initials = remember(ownerName) {
        val parts = ownerName.split(" ").filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.toString() }
        if (parts.isEmpty()) "ME" else parts.joinToString("").uppercase()
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(22.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(statusColor), contentAlignment = Alignment.Center) {
                Text(initials, style = NuruType.chipLabel, color = Color.White)
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ownerName, style = NuruType.chipLabel, color = statusColor, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text("·", style = NuruType.micro, color = Nuru.ink400)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        relativeLabel(if (entry.isAnswered) entry.answeredAt ?: entry.createdAt else entry.createdAt),
                        style = NuruType.micro, color = statusColor,
                    )
                }
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Prayer options", tint = Nuru.ink400, modifier = Modifier.size(20.dp))
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (entry.isAnswered) {
                        DropdownMenuItem(
                            text = { Text("Reopen prayer") },
                            leadingIcon = { Icon(Icons.Filled.Replay, null, modifier = Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; Haptics.tap(view); onReopenOrAnswer() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Mark answered") },
                            leadingIcon = { Icon(Icons.Filled.Check, null, modifier = Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; Haptics.confirm(view); onReopenOrAnswer() },
                        )
                    }
                    if (!shared) {
                        DropdownMenuItem(
                            text = { Text("Publish to Corporate") },
                            leadingIcon = { Icon(Icons.Filled.Campaign, null, modifier = Modifier.size(20.dp)) },
                            onClick = { menuOpen = false; confirmShare = true },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(20.dp)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = Spacing.base)) {
            entry.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = NuruType.rowTitle, color = Nuru.ink) }
            Spacer(Modifier.height(2.dp))
            Text(entry.body, style = NuruType.body, color = Nuru.ink)
        }
        if (entry.isAnswered) {
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.base).clip(RoundedCornerShape(Radii.button))
                    .background(Nuru.successBg).padding(vertical = 10.dp, horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Favorite, null, tint = Nuru.successText, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Answered${entry.answeredNote?.let { " · $it" } ?: ""} 🙏", style = NuruType.cardCta.copy(fontWeight = FontWeight.SemiBold), color = Nuru.successText)
            }
        } else if (shared) {
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.padding(horizontal = Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Campaign, null, tint = ShareOrange, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text("On the Corporate wall", style = NuruType.micro, color = ShareOrange)
            }
        }
        Spacer(Modifier.height(Spacing.base))
    }

    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            title = { Text("Share to the prayer wall?", style = NuruType.rowTitle, color = Nuru.ink) },
            text = { Text("Your cell will see this and pray with you.", style = NuruType.body, color = Nuru.ink600) },
            confirmButton = {
                TextButton(onClick = { confirmShare = false; Haptics.confirm(view); onPublish() }) {
                    Text("Share to wall", style = NuruType.cardCta, color = Nuru.eyebrow)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmShare = false }) { Text("Keep it private", style = NuruType.cardCta, color = Nuru.ink600) }
            },
            containerColor = Nuru.white,
        )
    }
}

@Composable
private fun PrayerComposerDialog(draft: PrayerEntry?, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf(draft?.title ?: "") }
    var body by remember { mutableStateOf(draft?.body ?: "") }
    var answered by remember { mutableStateOf(draft?.isAnswered ?: false) }
    val bodyEmpty = body.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft == null) "New prayer" else "Edit prayer", style = NuruType.cardTitle, color = Nuru.navy) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, singleLine = true, shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(body, { body = it }, label = { Text("What are you praying for?") }, minLines = 3, shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.sm))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Answered", style = NuruType.cardCta, color = Nuru.navy, modifier = Modifier.weight(1f))
                    Switch(checked = answered, onCheckedChange = { answered = it }, colors = SwitchDefaults.colors(checkedTrackColor = PrayerGold))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !bodyEmpty, onClick = { onSave(title, body, answered) }) {
                Text(if (draft == null) "Save prayer" else "Save changes", style = NuruType.cardCta, color = if (bodyEmpty) Nuru.ink400 else PrayerGold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = NuruType.cardCta, color = Nuru.ink600) } },
        containerColor = Nuru.white,
    )
}

private fun relativeLabel(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    val then = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(then, today)
    return when {
        days < 1 -> "today"
        days == 1L -> "1 day ago"
        days < 7 -> "$days days ago"
        days < 14 -> "1 week ago"
        days < 31 -> "${days / 7} weeks ago"
        else -> instant.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d"))
    }
}
