// Notification center — ported to the Figma NotificationsScreen. A white app bar
// with an unread count + "Mark all read" navy pill, then rows carrying one
// icon per notice family on the gold-tint tile (EXPERIENCE.md §8.2 #14) and
// reward rows (badge/certificate/level) in a gold gift treatment. Read-state
// contrast is the owner's amber/green design (2026-08-26, iOS parity): unread
// rows carry a GLOWING AMBER dot on a warm wash + amber accent bar; read rows a
// LUMINOUS GREEN dot beside a double tick. Mark-all and row-open flip
// OPTIMISTICALLY via a locallyRead override set — the page answers the tap
// instantly, the API call and a quiet reload confirm. A tapped notice goes
// through the push router (one router, EXPERIENCE.md §7.2 #3); one with
// nowhere to go shows only itself and Dismiss.
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.nuruplace.member.ui.components.NuruDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.MarkReadBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.NotificationRow
import org.nuruplace.member.data.net.NotificationsRes
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.InboxUnread
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.relTime
import org.nuruplace.member.ui.icons.Lucide

/** A notice's push data: its template and the payload keys the push router
 *  reads, spelled as the dispatcher copies them into a push (snake_case —
 *  workers/dispatch.ts copies the payload JSONB verbatim). */
internal fun noticeData(n: NotificationRow): Map<String, String> = buildMap {
    put("template", n.template)
    val p = n.payload ?: return@buildMap
    p.moduleId?.let { put("module_id", it) }
    p.announcementId?.let { put("announcement_id", it) }
    p.levelNumber?.let { put("level_number", it.toString()) }
    p.inviteToken?.let { put("invite_token", it) }
    p.departmentId?.let { put("department_id", it) }
    p.transactionId?.let { put("transaction_id", it) }
    p.failureCode?.let { put("failure_code", it) }
    p.scheduleId?.let { put("schedule_id", it) }
    p.promptAt?.let { put("prompt_at", it) }
    p.pledgeId?.let { put("pledge_id", it) }
    p.streamId?.let { put("stream_id", it) }
    // A Live notice's title is its stream's name — said if it has ended.
    p.title?.let { put("title", it) }
}

/** A notice's in-app destination — the SAME router a tapped push uses
 *  (NuruMessagingService.destFor; EXPERIENCE.md §7.2 #3), so a notice lands
 *  exactly where its push does: a Live notice on its stream (the player, or
 *  "This Live has ended"), a giving notice on its gift or pledge, a level on
 *  that level. It used to be a second copy of the rules that had drifted —
 *  no Live, no invite token, no plan_group — so those opened a generic sheet.
 *  Null when there is nowhere to go: the caller shows the notice itself. */
internal fun noticeRoute(n: NotificationRow): String? =
    org.nuruplace.member.data.firebase.NuruMessagingService.destFor(noticeData(n))

/** A row's headline: a giving notice's own words (feature/give/
 *  GivingNotificationCopy.kt — the push's, from dispatch.ts) ahead of the
 *  payload's `title`, which on the Partners notices is the pledge's NAME;
 *  else the payload's title; else the template, humanised. */
private fun titleOf(n: NotificationRow): String =
    org.nuruplace.member.feature.give.givingNotificationTitle(n.template, n.payload)
        ?: n.payload?.title
        ?: n.template.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** A row's line: the payload's own body when it carries one (dispatch.ts
 *  prefers it too), else a giving notice's words; null when neither. */
private fun bodyOf(n: NotificationRow): String? =
    n.payload?.body?.takeIf { it.isNotEmpty() }
        ?: org.nuruplace.member.feature.give.givingNotificationBody(n.template, n.payload)

/** What a notice is about — one family per kind of notice, one icon each
 *  (EXPERIENCE.md §8.1 rule 7, §8.2 #14), the same families as iOS's inbox.
 *  Every icon sits on the gold-tint tile; the rewards keep their gold one.
 *  It used to be an emoji per category, and a Live notice — matching none —
 *  fell to the default bell (iOS: its gear). Internal so NotificationToneTest
 *  pins the families and their order. */
internal enum class NoticeFamily(val icon: ImageVector, val reward: Boolean = false) {
    /** live_stream_started · live_guest_invite — the broadcast. */
    LIVE(Lucide.Radio),
    BADGE(Lucide.BadgeCheck, reward = true),
    CERTIFICATE(Lucide.Award, reward = true),
    /** level_completed · level_ushered — the road moving on. */
    LEVEL(Lucide.TrendingUp, reward = true),
    /** reflection_approved · _returned · _deferred — the discipler's word. */
    REFLECTION(Lucide.MessageSquareText),
    /** department_* · serve_request_* — a team to serve on. */
    DEPARTMENT(Lucide.Handshake),
    /** giving_* · pledge_* · payment_* — the Give tab's hand and heart. */
    GIVING(Lucide.HandHeart),
    /** event_* — a gathering. */
    EVENT(Lucide.CalendarDays),
    /** announcement — the church's megaphone. */
    ANNOUNCEMENT(Lucide.Megaphone),
    /** plan_group_* — reading with a friend. */
    PLAN(Lucide.BookMarked),
    /** sunday_letter — the letter. */
    LETTER(Lucide.Mail),
    /** space_* · connection_* · community_* · prayer_* — the family. */
    COMMUNITY(Lucide.Users),
    /** check_in_* — a service's check-in. */
    CHECK_IN(Lucide.ScanQrCode),
    /** security · login · password · mfa — the account. */
    SECURITY(Lucide.Shield),
    /** Anything else — a notice. */
    OTHER(Lucide.Bell),
}

/** A giving or Partners notice — giving_* · pledge_* · payment_*. Checked
 *  before the event rule: pledge_reminder_manual is a giving notice, not a
 *  calendar reminder (Giving Cycle 10). */
internal fun isGivingNotice(template: String): Boolean {
    val t = template.lowercase()
    return t.startsWith("giving") || t.startsWith("pledge") || t.startsWith("payment")
}

/** The family a template belongs to — by its prefix, in this order. */
internal fun noticeFamily(template: String): NoticeFamily {
    val t = template.trim().lowercase()
    return when {
        t.startsWith("live_") -> NoticeFamily.LIVE
        t.startsWith("badge") -> NoticeFamily.BADGE
        t.startsWith("certificate") -> NoticeFamily.CERTIFICATE
        t.startsWith("level") -> NoticeFamily.LEVEL
        t.startsWith("reflection") -> NoticeFamily.REFLECTION
        t.startsWith("department") || t.startsWith("serve_request") -> NoticeFamily.DEPARTMENT
        isGivingNotice(t) -> NoticeFamily.GIVING
        t.startsWith("event") -> NoticeFamily.EVENT
        t.startsWith("announcement") -> NoticeFamily.ANNOUNCEMENT
        t.startsWith("plan_group") -> NoticeFamily.PLAN
        t.startsWith("sunday_letter") || t.startsWith("letter") -> NoticeFamily.LETTER
        t.startsWith("space_") || t.startsWith("connection_") || t.startsWith("community") || t.startsWith("prayer") -> NoticeFamily.COMMUNITY
        t.startsWith("check_in") -> NoticeFamily.CHECK_IN
        "security" in t || "login" in t || "password" in t || "mfa" in t -> NoticeFamily.SECURITY
        else -> NoticeFamily.OTHER
    }
}

/** A notice's icon on its tile: gold tint, gold-chip ink (§8.1 rules 1 and 7);
 *  a reward (badge, certificate, level) on solid gold, its icon navy. */
@Composable
private fun NoticeIconTile(family: NoticeFamily) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(Radii.control))
            .background(if (family.reward) Nuru.gold else Nuru.goldChipBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(family.icon, contentDescription = null, tint = if (family.reward) Nuru.navy else Nuru.goldChipText, modifier = Modifier.size(18.dp))
    }
}

// Owner's read-state palette (2026-08-26): amber for what still waits, luminous
// green for what's been received — the two states must contrast at a glance.
private val Amber = Color(0xFFF59E0B)
private val LumGreen = Color(0xFF22C55E)

@Composable
fun NotificationsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    // Leaving the inbox, every bell asks again (§7.2 #4) — whatever was read here.
    DisposableEffect(Unit) { onDispose { InboxUnread.refreshSoon() } }
    AsyncContent(load = { Net.client.api.notifications() }) { res: NotificationsRes, reload ->
        val scope = rememberCoroutineScope()
        val inboxContext = androidx.compose.ui.platform.LocalContext.current
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
        // Every bell's dot follows this page: its count, and each mark-read
        // the moment it is made; the server's word once it has answered.
        LaunchedEffect(unreadCount) { InboxUnread.set(unreadCount) }
        fun open(n: NotificationRow) {
            if (isUnread(n)) {
                locallyRead = locallyRead + n.notificationId
                scope.launch {
                    // Not marked on the server: it reads unread again, so the
                    // bell's dot keeps telling the truth (§7.1 rule 8).
                    runCatching { Net.client.api.markNotificationsRead(MarkReadBody(listOf(n.notificationId))) }
                        .onFailure { if (it !is kotlin.coroutines.cancellation.CancellationException) locallyRead = locallyRead - n.notificationId }
                    InboxUnread.refresh()
                }
            }
            val route = noticeRoute(n)
            if (route != null) onNavigate(route) else popup = n
        }
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            // The pushed page's one header (§8.1 rule 2; final walk C16: a
            // white band with no kicker) — INBOX · Notifications · the count,
            // as iOS; "Mark all read" a gold text action (rule 4), only while
            // something is unread.
            org.nuruplace.member.ui.components.PushedHeader(
                kicker = "Inbox",
                title = "Notifications",
                onBack = onBack,
                lineContent = {
                    // Animates with the OPTIMISTIC count — mark-all lands here immediately.
                    AnimatedContent(targetState = unreadCount, label = "notifUnreadCount") { u ->
                        // Words, no emoji (§8.1 rule 7; final walk C16) — as iOS.
                        Text(if (u > 0) "$u unread" else "All caught up", style = NuruType.caption, color = Nuru.ink600)
                    }
                },
                trailing = {
                    AnimatedVisibility(visible = unreadCount > 0, enter = fadeIn(), exit = fadeOut()) {
                        Row(
                            Modifier.clip(RoundedCornerShape(Radii.pill))
                                .clickable {
                                // Flip the whole page NOW, then tell the server and quietly confirm.
                                locallyRead = locallyRead + res.data.map { it.notificationId }
                                markedAll = true
                                scope.launch {
                                    org.nuruplace.member.ui.components.noticeOnFailure(
                                        inboxContext, lead = "Couldn't mark them read.",
                                        onFailure = { markedAll = false; locallyRead = emptySet() },
                                    ) { Net.client.api.markNotificationsRead(MarkReadBody(null)) }
                                    InboxUnread.refresh()
                                    reload()
                                }
                            }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Lucide.CheckCheck, contentDescription = null, tint = Nuru.goldLo, modifier = Modifier.size(14.dp))
                            Text("Mark all read", style = NuruType.micro, color = Nuru.goldLo, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )

            if (res.data.isEmpty()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(Radii.card)).background(Nuru.white), contentAlignment = Alignment.Center) {
                        // A Lucide glyph, not a typed mark (§8.1 rule 7).
                        Icon(Lucide.Sparkle, contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(22.dp))
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
        popup?.let { n -> NotifDetailPopup(n, onDismiss = { popup = null }) }
    }
}

/** A notice with nowhere to go (EXPERIENCE.md §7 rule 1): only the notice
 *  itself — its tone, its title, when, and its full words — and Dismiss. No
 *  greeting, no stats, no "Continue my journey": it used to open the Pathway
 *  from a notice that had nothing to do with it. */
@Composable
private fun NotifDetailPopup(n: NotificationRow, onDismiss: () -> Unit) {
    val family = noticeFamily(n.template)
    NuruDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.card)).background(Nuru.paper).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control)).background(Nuru.white)
                    .border(1.dp, Nuru.border, RoundedCornerShape(Radii.control)).padding(Spacing.base),
                verticalAlignment = Alignment.Top,
            ) {
                NoticeIconTile(family)
                Spacer(Modifier.size(Spacing.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(titleOf(n), style = NuruType.rowTitle, color = Nuru.ink, modifier = Modifier.weight(1f))
                        Spacer(Modifier.size(Spacing.sm))
                        Text(relTime(n.sentAt ?: n.scheduledFor), style = NuruType.micro, color = Nuru.ink400)
                    }
                    // Its full words — the row clips them to two lines.
                    bodyOf(n)?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                }
            }
            Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = Spacing.sm), contentAlignment = Alignment.Center) {
                Text("Dismiss", style = NuruType.cardCta, color = Nuru.ink600)
            }
        }
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
                    Icon(Lucide.Check, null, tint = LumGreen, modifier = Modifier.size(14.dp).offset(x = (-2.5).dp))
                    Icon(Lucide.Check, null, tint = LumGreen, modifier = Modifier.size(14.dp).offset(x = 2.5.dp))
                }
            }
        }
    }
}

@Composable
private fun NotifRow(n: NotificationRow, unread: Boolean, onClick: () -> Unit) {
    val family = noticeFamily(n.template)
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
            NoticeIconTile(family)
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    // A notice is a content row (§8.1 rule 3): Fraunces 15
                    // semibold; read rows quieten by colour.
                    Text(titleOf(n), style = NuruType.rowTitle, color = titleColor, modifier = Modifier.weight(1f))
                    Text(relTime(n.sentAt ?: n.scheduledFor), style = NuruType.micro, color = timeColor)
                }
                bodyOf(n)?.let { Text(it, style = NuruType.caption, color = bodyColor, maxLines = 2) }
                if (family.reward && unread) {
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
