// Cell roster — the people behind the faces row on Cell info. One screen, two
// readings of the same list, decided by the SERVER (`can_shepherd`):
//
//   • an ordinary member sees people — face, name, who leads, which row is them;
//   • a shepherd additionally sees standing — score, attendance, risk band —
//     because the server sent those fields to them and to no one else.
//
// The privacy split is not a client decision: the shepherd fields are absent
// from an ordinary member's JSON, so a null score renders as "not shown", never
// as a zero. Nothing here fabricates a number.
//
// Every row carries an overflow "⋯" whose one action opens a DM with that
// person, through the app's existing consent path (POST /chat/dms, and the
// C3a connection request when the server answers CONSENT_REQUIRED).
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.CellRoster
import org.nuruplace.member.data.net.CellRosterMember
import org.nuruplace.member.data.net.DmBody
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RequestConnectionBody
import org.nuruplace.member.feature.community.isConsentRequired
import org.nuruplace.member.feature.community.isMinorBlocked
import org.nuruplace.member.feature.profile.AvatarCircle
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.ListSkeleton
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun CellRosterScreen(
    me: MeResponse? = null,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    // One DM attempt at a time — the row that's busy shows a spinner in place
    // of its "⋯" so a double-tap can't open two threads.
    var busyUserId by remember { mutableStateOf<String?>(null) }
    // The server refused a brand-new DM for want of an accepted connection
    // (Chat Redesign C3a) — offer the request instead of a dead end.
    var consentPromptFor by remember { mutableStateOf<CellRosterMember?>(null) }
    // Anything else worth saying out loud rather than swallowing.
    var notice by remember { mutableStateOf<String?>(null) }

    // Minors cannot hold direct messages at all (§D-M6, enforced server-side on
    // every DM-flavoured route). Rather than ship a button that always errors,
    // the Message item simply isn't offered to them.
    val canMessage = me?.profile?.isMinor != true

    fun message(m: CellRosterMember) {
        if (busyUserId != null) return
        busyUserId = m.userId
        scope.launch {
            runCatching { Net.client.api.createDm(DmBody(m.userId)).conversationId }
                .onSuccess { id ->
                    busyUserId = null
                    if (id.isNotBlank()) onOpenThread(id)
                }
                .onFailure { e ->
                    busyUserId = null
                    when {
                        isConsentRequired(e) -> consentPromptFor = m
                        isMinorBlocked(e) -> notice = "Direct messages aren't available yet for your account."
                        else -> notice = ApiException.message(e)
                    }
                }
        }
    }

    AsyncContent(
        load = { Net.client.api.cellRoster() },
        loading = {
            Column(Modifier.fillMaxSize().background(Nuru.paper)) {
                ScreenHeader("Members", kicker = "Your cell", onBack = onBack)
                ListSkeleton(rows = 6)
            }
        },
    ) { roster: CellRoster, _ ->
        RosterBody(
            roster = roster,
            canMessage = canMessage,
            busyUserId = busyUserId,
            onBack = onBack,
            onMessage = ::message,
        )
    }

    val prompt = consentPromptFor
    if (prompt != null) {
        AlertDialog(
            onDismissRequest = { consentPromptFor = null },
            containerColor = Nuru.white,
            title = { Text("Not connected yet", style = NuruType.cardTitle, color = Nuru.navy) },
            text = {
                Text(
                    "Send ${prompt.firstName.ifBlank { prompt.fullName }} a connection request first — you can chat once they accept.",
                    style = NuruType.body,
                    color = Nuru.ink600,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = prompt
                    consentPromptFor = null
                    if (busyUserId == null) {
                        busyUserId = target.userId
                        scope.launch {
                            runCatching {
                                Net.client.api.requestConnection(
                                    RequestConnectionBody(target.userId, clientMutationId = UUID.randomUUID().toString()),
                                )
                            }.onFailure { notice = ApiException.message(it) }
                            busyUserId = null
                        }
                    }
                }) { Text("Send request", style = NuruType.actionLabel, color = Nuru.goldLo) }
            },
            dismissButton = {
                TextButton(onClick = { consentPromptFor = null }) {
                    Text("Cancel", style = NuruType.cardCta, color = Nuru.ink600)
                }
            },
        )
    }

    val msg = notice
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { notice = null },
            containerColor = Nuru.white,
            title = { Text("Couldn't open the chat", style = NuruType.cardTitle, color = Nuru.navy) },
            text = { Text(msg, style = NuruType.body, color = Nuru.ink600) },
            confirmButton = {
                TextButton(onClick = { notice = null }) {
                    Text("OK", style = NuruType.actionLabel, color = Nuru.goldLo)
                }
            },
        )
    }
}

@Composable
private fun RosterBody(
    roster: CellRoster,
    canMessage: Boolean,
    busyUserId: String?,
    onBack: () -> Unit,
    onMessage: (CellRosterMember) -> Unit,
) {
    val members = rosterOrder(roster)
    val cellName = roster.cell?.name?.takeIf { it.isNotBlank() } ?: "Your cell"
    // Attendance is only ever a claim about meetings that happened. When the
    // server sent none for anyone, say that once, in words — never 0 of 0.
    val noMeetingsYet = roster.canShepherd && members.isNotEmpty() && members.all { it.attendance == null }

    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "${members.size} member" + (if (members.size == 1) "" else "s"),
            kicker = cellName,
            onBack = onBack,
        )
        Column(
            Modifier.fillMaxWidth().padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            if (members.isEmpty()) {
                NuruCard {
                    Text("No one here yet", style = NuruType.cardTitle, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "As your leader adds people to this cell, they'll appear here.",
                        style = NuruType.caption,
                        color = Nuru.ink600,
                    )
                }
                return@Column
            }

            if (noMeetingsYet) {
                Text("This cell hasn't met yet.", style = NuruType.caption, color = Nuru.ink600)
            }

            NuruCard(padding = PaddingValues(0.dp)) {
                members.forEachIndexed { i, m ->
                    MemberRow(
                        m = m,
                        showStanding = roster.canShepherd,
                        showAttendance = roster.canShepherd && !noMeetingsYet,
                        canMessage = canMessage && !m.isMe,
                        busy = busyUserId == m.userId,
                        onMessage = { onMessage(m) },
                    )
                    if (i < members.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Nuru.border))
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * Who comes first. For a shepherd, whoever needs attention rises: lowest score
 * first, people without a score last. `sortedWith` is stable, so within equal
 * scores (and for an ordinary member, always) the server's own leader-first
 * order is preserved untouched.
 */
internal fun rosterOrder(roster: CellRoster): List<CellRosterMember> =
    if (!roster.canShepherd) {
        roster.members
    } else {
        roster.members.sortedWith(compareBy(nullsLast<Int>()) { it.score })
    }

@Composable
private fun MemberRow(
    m: CellRosterMember,
    showStanding: Boolean,
    showAttendance: Boolean,
    canMessage: Boolean,
    busy: Boolean,
    onMessage: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarCircle(m.avatarUrl, m.firstName.ifBlank { m.fullName }, size = 44)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.fullName.ifBlank { m.firstName }.ifBlank { "Member" },
                    style = NuruType.rowTitle,
                    color = Nuru.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // fill = false so a short name doesn't push the chips to the
                    // far edge; a long one truncates instead of shoving them off.
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (m.isLeader) {
                    Spacer(Modifier.width(Spacing.sm))
                    Chip("Leader", fg = Nuru.goldChipText, bg = Nuru.goldChipBg)
                }
                if (m.isMe) {
                    Spacer(Modifier.width(Spacing.sm))
                    Chip("You", fg = Nuru.ink600, bg = Nuru.tintBlue)
                }
            }
            // Standing — shepherd only, and only for what the server actually
            // sent. A member with no score yet gets the row without the line.
            if (showStanding && m.score != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreBar(m.score, m.band)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("${m.score}", style = NuruType.micro, color = Nuru.ink, fontWeight = FontWeight.Bold)
                    if (showAttendance) {
                        Spacer(Modifier.width(Spacing.sm))
                        val a = m.attendance
                        Text(
                            if (a != null) "· ${a.present} of ${a.of}" else "· —",
                            style = NuruType.micro,
                            color = Nuru.ink400,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    BandPill(m.band)
                }
            }
        }
        if (canMessage) {
            Box {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .clickable(enabled = !busy) { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Nuru.gold, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Options for ${m.fullName}",
                            tint = Nuru.ink400,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Message") },
                        onClick = { menuOpen = false; onMessage() },
                    )
                }
            }
        }
    }
}

/** Compact 0–100 bar — 56dp wide, coloured by the same band as the pill so one
 *  colour carries one meaning. A glance, not a chart. */
@Composable
private fun ScoreBar(score: Int, band: String?) {
    val filled = (56f * score.coerceIn(0, 100) / 100f).dp
    Box(
        Modifier.width(56.dp).height(6.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.progressTrack),
    ) {
        if (filled > 0.dp) {
            Box(
                Modifier.width(filled).height(6.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(Nuru.bandColor(band)),
            )
        }
    }
}

/** Risk band chip — the canonical semantic pairs (success/info/warning/danger). */
@Composable
private fun BandPill(band: String?) {
    val label = when (band?.lowercase()) {
        "thriving" -> "Thriving"
        "steady" -> "Steady"
        "watch" -> "Watch"
        "at_risk", "at risk" -> "At risk"
        else -> return
    }
    Chip(label, fg = Nuru.bandColor(band), bg = bandBg(band))
}

/** Tint behind a band chip — the canonical *-Bg token that pairs with
 *  [Nuru.bandColor]'s foreground, so a band chip reads like every other chip. */
private fun bandBg(band: String?): Color = when (band?.lowercase()) {
    "thriving" -> Nuru.successBg
    "steady" -> Nuru.infoBg
    "watch" -> Nuru.warningBg
    "at_risk", "at risk" -> Nuru.dangerBg
    else -> Nuru.tintBlue
}

@Composable
private fun Chip(text: String, fg: Color, bg: Color) {
    Text(
        text.uppercase(),
        style = NuruType.micro,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(bg)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}
