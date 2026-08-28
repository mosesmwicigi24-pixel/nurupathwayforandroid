// Notification center — ported to the Figma NotificationsScreen. A white app bar
// with an unread count + "Mark all read" navy pill, then rows carrying a
// category-toned icon chip (info · success · warning · security — §B2) and
// reward rows (badge/certificate/level) in a gold gift treatment. Read-state
// contrast is the owner's amber/green design (2026-08-26, iOS parity): unread
// rows carry a GLOWING AMBER dot on a warm wash + amber accent bar; read rows a
// LUMINOUS GREEN dot beside a double tick. Mark-all and row-open flip
// OPTIMISTICALLY via a locallyRead override set — the page answers the tap
// instantly, the API call and a quiet reload confirm. Deep-links mirror iOS.
package org.nuruplace.member.feature.events

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.MarkReadBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.NotificationRow
import org.nuruplace.member.data.net.NotificationsRes
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.relTime

/** Map a notification to its EXACT in-app destination (mirrors iOS deep-links).
 *  Returns null when there is no in-app target — the caller then opens the
 *  personalized read-and-continue popup instead of doing nothing. */
private fun routeFor(n: NotificationRow): String? {
    n.payload?.moduleId?.let { return "module/$it" }
    n.payload?.announcementId?.let { return "announcement/$it" }
    val t = n.template.lowercase()
    // Level notifications land on the EXACT level (was the bare hub).
    n.payload?.levelNumber?.let { return "level/$it" }
    return when {
        "prayer" in t -> "prayer-room?tab=corporate"
        "verse" in t || "memory" in t -> "memory-verses"
        "devotional" in t -> "devotional"
        "give" in t || "giving" in t || "payment" in t -> "give"
        "event" in t -> "events"
        "badge" in t || "certificate" in t || "cert" in t -> "profile"
        "reflection" in t -> "pathway"
        else -> null
    }
}

/** The four backend notification categories → (glyph, foreground, tint). §B2. */
private data class Tone(val glyph: String, val fg: Color, val bg: Color, val reward: Boolean = false)

private fun toneFor(template: String): Tone {
    val t = template.lowercase()
    return when {
        "badge" in t -> Tone("🏅", Nuru.navy, Nuru.gold, reward = true)
        "certificate" in t || "cert" in t -> Tone("📜", Nuru.navy, Nuru.gold, reward = true)
        "level" in t || "advanced" in t -> Tone("📈", Nuru.navy, Nuru.gold, reward = true)
        "reflection" in t && ("return" in t || "revis" in t) -> Tone("✍️", Nuru.warning, Nuru.warningBg)     // warning
        "event" in t || "reminder" in t -> Tone("📅", Nuru.info, Nuru.infoBg)                                 // info
        "announcement" in t || "announce" in t -> Tone("📣", Nuru.info, Nuru.infoBg)                          // info
        "system" in t || "security" in t || "login" in t || "password" in t -> Tone("⚙️", Nuru.ink600, Nuru.inputBg) // security
        "prayer" in t || "verse" in t || "devotional" in t || "give" in t -> Tone("🌿", Nuru.info, Nuru.infoBg)
        else -> Tone("🔔", Nuru.success, Nuru.successBg)                                                       // success default
    }
}

// Owner's read-state palette (2026-08-26): amber for what still waits, luminous
// green for what's been received — the two states must contrast at a glance.
private val Amber = Color(0xFFF59E0B)
private val LumGreen = Color(0xFF22C55E)

@Composable
fun NotificationsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    AsyncContent(load = { Net.client.api.notifications() }) { res: NotificationsRes, reload ->
        val scope = rememberCoroutineScope()
        // The notification opened in the read-and-continue popup (unroutable ones).
        var popup by remember { mutableStateOf<NotificationRow?>(null) }
        // Optimistic read overrides — the page answers the tap INSTANTLY (the old
        // flow waited a full network round-trip before anything moved, which read
        // as "mark all read does nothing"). The server reload then confirms.
        var locallyRead by remember { mutableStateOf(setOf<String>()) }
        var markedAll by remember { mutableStateOf(false) }
        // Fresh server data carries the truth — quietly drop the overrides.
        LaunchedEffect(res) { locallyRead = emptySet(); markedAll = false }
        fun isUnread(n: NotificationRow) = n.isUnread && n.notificationId !in locallyRead
        val unreadCount =
            if (markedAll) 0
            else (res.unread - res.data.count { it.isUnread && it.notificationId in locallyRead }).coerceAtLeast(0)
        fun open(n: NotificationRow) {
            if (isUnread(n)) {
                locallyRead = locallyRead + n.notificationId
                scope.launch {
                    runCatching { Net.client.api.markNotificationsRead(MarkReadBody(listOf(n.notificationId))) }
                }
            }
            val route = routeFor(n)
            if (route != null) onNavigate(route) else popup = n
        }
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            // White app bar with count + Mark-all pill.
            Row(
                Modifier.fillMaxWidth().background(Nuru.white).padding(horizontal = Spacing.sm).padding(top = Spacing.lg, bottom = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nuru.navy) }
                Column(Modifier.weight(1f)) {
                    Text("Notifications", style = NuruType.cardTitle, color = Nuru.ink)
                    // Animates with the OPTIMISTIC count — mark-all lands here immediately.
                    AnimatedContent(targetState = unreadCount, label = "notifUnreadCount") { u ->
                        Text(if (u > 0) "$u unread" else "All caught up ✨", style = NuruType.caption, color = Nuru.ink600)
                    }
                }
                AnimatedVisibility(visible = unreadCount > 0, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.navy)
                            .clickable {
                                // Flip the whole page NOW, then tell the server and quietly confirm.
                                locallyRead = locallyRead + res.data.map { it.notificationId }
                                markedAll = true
                                scope.launch {
                                    runCatching { Net.client.api.markNotificationsRead(MarkReadBody(null)) }
                                    reload()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("✓ Mark all read", style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.SemiBold) }
                }
            }

            if (res.data.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(Radii.card)).background(Nuru.white), contentAlignment = Alignment.Center) {
                        Text("✦", style = NuruType.display, color = Nuru.gold)
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Text("You're all caught up", style = NuruType.cardTitle, color = Nuru.ink)
                    Text("New encouragement, reflections, and reminders land here.", style = NuruType.caption, color = Nuru.ink600)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(res.data, key = { it.notificationId }) { n ->
                        NotifRow(n, unread = isUnread(n), onClick = { open(n) })
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Nuru.border))
                    }
                }
            }
        }
        popup?.let { n ->
            NotifDetailPopup(n, onContinue = { popup = null; onNavigate("pathway") }, onDismiss = { popup = null })
        }
    }
}

/** Live quick-stats + name for the popup card (best-effort). */
private data class PopupStats(val name: String, val streak: Int, val level: String?, val plan: String?)

/** The read-and-continue popup for notifications with no in-app target (iOS
 *  build-33 parity): greets by name, carries the message, shows live quick stats
 *  (streak · level · plan day), a word of encouragement and a gold "Continue my
 *  journey" that opens the Pathway. */
@Composable
private fun NotifDetailPopup(n: NotificationRow, onContinue: () -> Unit, onDismiss: () -> Unit) {
    val stats by produceState<PopupStats?>(initialValue = null) {
        value = runCatching {
            val name = Net.client.api.me().profile.fullName.split(" ").firstOrNull() ?: "Friend"
            val streak = runCatching { Net.client.api.achievements().streak.current }.getOrDefault(0)
            val pw = runCatching { Net.client.api.pathway() }.getOrNull()
            val level = pw?.let { s -> s.levels.firstOrNull { it.status == LevelStatus.ACTIVE } ?: s.levels.firstOrNull { it.levelNumber == s.currentLevel } }
            val levelStr = level?.let { "Level ${it.levelNumber} · ${it.completedModules}/${it.totalModules}" }
            val plan = runCatching { Net.client.api.plans().data.firstOrNull { it.enrolled && it.completedAt == null } }.getOrNull()
            val planStr = plan?.let { "Day ${it.currentDay ?: 1} of ${it.dayCount}" }
            PopupStats(name, streak, levelStr, planStr)
        }.getOrNull()
    }
    val name = stats?.name ?: "friend"
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Nuru.paper).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Grace and peace, $name.", style = NuruType.display, color = Nuru.navy)
            // The notification itself, in a white card.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control)).background(Nuru.white)
                    .border(1.dp, Nuru.border, RoundedCornerShape(Radii.control)).padding(Spacing.base),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(n.payload?.title ?: n.template.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
                n.payload?.body?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
            }
            stats?.let { s ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatChip("🔥", if (s.streak > 0) "${s.streak} days with God" else "Begin today")
                    s.level?.let { StatChip("📖", it) }
                    s.plan?.let { StatChip("🔖", it) }
                }
            }
            Text(encouragementFor(n), style = NuruType.body, color = Nuru.ink600)
            PrimaryButton("Continue my journey", onClick = onContinue)
            Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = Spacing.sm), contentAlignment = Alignment.Center) {
                Text("Dismiss", style = NuruType.caption, color = Nuru.ink400)
            }
        }
    }
}

@Composable
private fun StatChip(glyph: String, label: String) {
    Box(Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.goldTint).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("$glyph  $label", style = NuruType.micro, color = Nuru.goldChipText, fontWeight = FontWeight.SemiBold)
    }
}

private fun encouragementFor(n: NotificationRow): String {
    val t = n.template.lowercase()
    return when {
        "nudge" in t || "miss" in t -> "The road is still yours. One small step today — a verse, a prayer, a page — and you're walking again."
        "badge" in t || "certificate" in t || "level" in t -> "God is faithful — and so were you. Keep walking; there's more ahead."
        else -> "Every step counts. Keep going — God isn't finished with you."
    }
}

/** Amber for what still waits, green for what's been received (owner's design,
 *  2026-08-26; iOS statusCluster parity): unread → a 9dp glowing amber dot in a
 *  20dp amber halo; read → a 7dp luminous green dot beside a green double tick. */
@Composable
private fun StatusCluster(unread: Boolean) {
    Crossfade(targetState = unread, label = "notifStatus") { u ->
        if (u) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Amber.copy(alpha = 0.22f)))
                // The soft glow — a translucent ring between halo and core.
                Box(Modifier.size(14.dp).clip(CircleShape).background(Amber.copy(alpha = 0.30f)))
                Box(Modifier.size(9.dp).clip(CircleShape).background(Amber))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(11.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(LumGreen.copy(alpha = 0.25f)))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(LumGreen))
                }
                // Double tick — two overlapping checks, the "received" cue.
                Box(Modifier.size(width = 17.dp, height = 12.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = LumGreen, modifier = Modifier.size(12.dp).offset(x = (-2.5).dp))
                    Icon(Icons.Filled.Check, null, tint = LumGreen, modifier = Modifier.size(12.dp).offset(x = 2.5.dp))
                }
            }
        }
    }
}

@Composable
private fun NotifRow(n: NotificationRow, unread: Boolean, onClick: () -> Unit) {
    val tone = toneFor(n.template)
    // All read-state visuals animate, so the optimistic flip is a visible settle.
    val rowBg by animateColorAsState(if (unread) Amber.copy(alpha = 0.07f) else Color.Transparent, label = "notifRowBg")
    val titleColor by animateColorAsState(if (unread) Nuru.ink else Nuru.ink600, label = "notifTitle")
    val timeColor by animateColorAsState(if (unread) Amber else Nuru.ink400, label = "notifTime")
    val bodyColor by animateColorAsState(if (unread) Nuru.ink600 else Nuru.ink400, label = "notifBody")
    val rowAlpha by animateFloatAsState(if (unread) 1f else 0.92f, label = "notifRowAlpha")
    val accentAlpha by animateFloatAsState(if (unread) 1f else 0f, label = "notifAccent")
    Box(Modifier.fillMaxWidth().alpha(rowAlpha).background(rowBg).clickable { onClick() }) {
        // Unread rows carry the amber accent bar on the leading edge.
        Box(
            Modifier.padding(vertical = Spacing.sm).size(width = 4.dp, height = 40.dp)
                .alpha(accentAlpha)
                .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(Amber)
                .align(Alignment.CenterStart),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.base), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(Radii.control)).background(tone.bg), contentAlignment = Alignment.Center) {
                Text(tone.glyph, style = NuruType.body)
            }
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        n.payload?.title ?: n.template.replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = NuruType.rowTitle, color = titleColor, modifier = Modifier.weight(1f),
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(relTime(n.sentAt ?: n.scheduledFor), style = NuruType.micro, color = timeColor)
                }
                n.payload?.body?.let { Text(it, style = NuruType.caption, color = bodyColor, maxLines = 2) }
                if (tone.reward && unread) {
                    Spacer(Modifier.height(Spacing.xs))
                    Box(Modifier.clip(RoundedCornerShape(Radii.pill)).background(Nuru.goldTint).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("🎁 Tap to open your gift", style = NuruType.micro, color = Nuru.goldChipText)
                    }
                }
            }
            Spacer(Modifier.size(Spacing.sm))
            Box(Modifier.padding(top = 4.dp)) { StatusCluster(unread) }
        }
    }
}
