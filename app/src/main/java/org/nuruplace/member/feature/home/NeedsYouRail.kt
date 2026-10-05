// Home — "What needs you today": the server-ranked rail of things waiting on
// this member (GET /me/home/nudges; data/net/HomeDtos.kt HomeNudge). One
// nudge fills the width like the old reflection strip; two or more become a
// horizontal rail of 280dp cards under a small count pill. HomeScreen owns the
// fetch, the route mapping and the fallback to the old ReflectionStrip when
// the endpoint is unreachable or empty — this file is only the rendering.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.firebase.NOTIFICATIONS_CARD_LINE
import org.nuruplace.member.data.firebase.NOTIFICATIONS_CARD_TITLE
import org.nuruplace.member.data.net.HomeNudge
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.pressScale
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.icons.Lucide

// The letter nudge keeps the Sunday Letter's own gold wax-seal disc (LetterScreen.kt).
private val NudgeSealGrad = Brush.linearGradient(listOf(Color(0xFFE8CA6C), Color(0xFFB6862F)))

/** [lead]: a card of the app's own that goes first (Android's "Turn on
 *  notifications", EXPERIENCE.md §7.3), full width above the server's. */
@Composable
fun NeedsYouRail(nudges: List<HomeNudge>, lead: (@Composable () -> Unit)? = null, onOpen: (HomeNudge) -> Unit) {
    if (nudges.isEmpty() && lead == null) return
    val count = nudges.size + if (lead != null) 1 else 0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WHAT NEEDS YOU TODAY", style = nuruSans(11, FontWeight.Bold, tracking = 1.4f), color = Nuru.eyebrow)
            if (count > 1) {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.homeNavy).padding(horizontal = 7.dp, vertical = 2.dp),
                ) { Text("$count", style = nuruSans(11, FontWeight.Bold), color = Nuru.goldSoft) }
            }
        }
        lead?.invoke()
        if (nudges.isEmpty()) {
            // The lead alone.
        } else if (nudges.size == 1) {
            NudgeCard(nudges[0], Modifier.fillMaxWidth()) { onOpen(nudges[0]) }
        } else {
            // Index in the key: ids are server-minted but a defensive rail must
            // never crash on a duplicate/blank one.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(nudges, key = { i, n -> "$i:${n.id}" }) { _, n ->
                    NudgeCard(n, Modifier.width(280.dp)) { onOpen(n) }
                }
            }
        }
    }
}

@Composable
private fun NudgeCard(n: HomeNudge, modifier: Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.pressScale().clip(shape).background(Nuru.priorityBg)
            .border(1.dp, Nuru.gold.copy(alpha = 0.33f), shape)
            .clickable { Haptics.tap(view); onClick() }
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            NudgeIconTile(n)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // A prompt is a card: its title is the card title, Fraunces 18
                // (§8.1 rule 3; Cycle 4 walk 02 — iOS cbc52c7 the same).
                Text(n.title, style = NuruType.cardTitle, color = Nuru.navy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // minLines keeps every card on the rail the same height whether
                // its body runs one line or two (or, defensively, is blank).
                Text(n.body, style = nuruSans(11), color = Nuru.metaGray, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            dueLabel(n.due)?.let { DueChip(it) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.homeNavy).padding(horizontal = 12.dp, vertical = 7.dp)) {
                Text(n.ctaLabel.ifBlank { "Open" }, style = nuruSans(11, FontWeight.SemiBold), color = Nuru.gold, maxLines = 1)
            }
        }
    }
}

/** "Turn on notifications" (Android only, EXPERIENCE.md §7.3) — one calm
 *  card, first among what needs the member today, while the phone has
 *  notifications off: Android carries real pushes (messages, Live invites,
 *  giving notices) and no longer asks at launch. Turn on asks the phone (or
 *  opens its settings once refused for good); Not now hides it 14 days. */
@Composable
fun NotificationsOffCard(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    val view = LocalView.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Nuru.priorityBg)
            .border(1.dp, Nuru.gold.copy(alpha = 0.33f), shape)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.white), contentAlignment = Alignment.Center) {
                Icon(Lucide.BellRing, null, tint = Nuru.goldDeep, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(NOTIFICATIONS_CARD_TITLE, style = NuruType.cardTitle, color = Nuru.navy)
                Text(NOTIFICATIONS_CARD_LINE, style = nuruSans(11), color = Nuru.metaGray)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(
                "Not now", style = nuruSans(11, FontWeight.SemiBold), color = Nuru.metaGray,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable { Haptics.tap(view); onNotNow() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.homeNavy)
                    .clickable { Haptics.tap(view); onTurnOn() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) { Text("Turn on", style = nuruSans(11, FontWeight.SemiBold), color = Nuru.gold, maxLines = 1) }
        }
    }
}

/** 40dp white rounded tile with the kind's glyph; the letter keeps its gold seal. */
@Composable
private fun NudgeIconTile(n: HomeNudge) {
    if (n.kind == "letter_unread") {
        Box(Modifier.size(40.dp).clip(CircleShape).background(NudgeSealGrad), contentAlignment = Alignment.Center) {
            Icon(Lucide.Mail, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        return
    }
    // A row icon is navy (§8.1 rule 7) — here on white, since the card itself
    // is the gentle prompt on gold tint (rule 5). It took the nudge's accent:
    // gold, green or sky.
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Nuru.white), contentAlignment = Alignment.Center) {
        Icon(nudgeIcon(n.kind), null, tint = Nuru.navy, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DueChip(label: String) {
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.goldChipBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, style = nuruSans(11, FontWeight.Bold), color = Nuru.goldChipText)
    }
}

private fun dueLabel(due: String?): String? = when (due?.lowercase()) {
    "today" -> "Today"
    "tomorrow" -> "Tomorrow"
    else -> null
}

private fun nudgeIcon(kind: String): ImageVector = when (kind) {
    "reflection_due" -> Lucide.NotebookPen
    "quiz_in_progress" -> Lucide.CircleHelp
    "level_review" -> Lucide.GraduationCap
    "letter_unread" -> Lucide.Mail
    "cell_gathering" -> Lucide.Users
    "plan_day_due" -> Lucide.BookOpen
    "reading_invite" -> Lucide.UserPlus
    "chat_unread" -> Lucide.MessageCircle
    else -> Lucide.Sparkles
}

