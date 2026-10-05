// Cell info — the destination behind the "This week at Nuru" featured-cell card.
// Renders entirely from GET /me/cell-summary (cell-truth, pathway#453): the
// member's OWN cell — leader, faces, meeting rhythm, next gathering, honest
// attendance (CellAttendance.kt), a shepherd's note for the cell's leader, and
// a way forward: "Watch replays" (the cell's) and "Open community ›" (the
// cell's own room) — EXPERIENCE.md §7.4 #16. Port of the iOS CellInfoView.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.CellSummary
import org.nuruplace.member.data.net.ChatConversation
import org.nuruplace.member.data.net.LiveNowRow
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.feature.live.GoLiveButton
import org.nuruplace.member.feature.live.GoLiveSetupSheet
import org.nuruplace.member.feature.live.canGoLive
import org.nuruplace.member.feature.live.liveBroadcastRoute
import org.nuruplace.member.feature.live.liveNowRoute
import org.nuruplace.member.feature.profile.AvatarCircle
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.LiveStreamBanner
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.components.pressScale
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.util.fmtEventTime
import org.nuruplace.member.ui.icons.Lucide

@Composable
fun CellInfoScreen(me: MeResponse? = null, onBack: () -> Unit, onNavigate: (String) -> Unit = {}) {
    var cell by remember { mutableStateOf<CellSummary.Cell?>(null) }
    // Nuru Live (L2) — ONE call to the same GET /live/now Home uses; the
    // server already scopes cell-scope rows to the caller's own cell, so no
    // extra client-side cellId matching (or a second endpoint) is needed here.
    var liveNow by remember { mutableStateOf<List<LiveNowRow>>(emptyList()) }
    // The cell's own room in Community (the server keeps every member in it)
    // — where "Open community" lands; null → the You tab's Community.
    var cellRoomId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        cell = runCatching { Net.client.api.cellSummary().cell }.getOrNull()
        liveNow = runCatching { Net.client.api.getLiveNow().data }.getOrDefault(emptyList())
        cellRoomId = runCatching { cellRoom(Net.client.api.chatInbox().conversations, cell?.name) }.getOrNull()
    }
    val cellLive = liveNow.firstOrNull { it.scope == "cell" }
    // Nuru Live (L3) — the cell entry point is forced to scope=cell (this
    // member's own cellGroupId); only meaningful once we actually have a
    // cell, so it's hidden entirely rather than shown disabled when we
    // don't (there's nothing useful to broadcast INTO without one yet).
    var showGoLiveSheet by remember { mutableStateOf(false) }

    val name = cell?.name?.takeIf { it.isNotBlank() } ?: "Your cell"
    // This cell's replays — the same list, scoped to the cell (iOS parity).
    val cellReplays = cell?.cellGroupId?.takeIf { it.isNotBlank() }
        ?.let { id -> "live-replays?scope=cell&cellId=$id&cellName=${android.net.Uri.encode(name)}" }
        ?: "live-replays"
    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        ScreenHeader(name, kicker = "Your cell", onBack = onBack)
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            if (canGoLive(me) && me?.profile?.cellGroupId != null && cellLive == null) {
                GoLiveButton(onClick = { showGoLiveSheet = true })
            }
            cellLive?.let { row ->
                LiveStreamBanner(
                    row = row,
                    onOpen = { onNavigate(liveNowRoute(row)) },
                    onReplays = { onNavigate(cellReplays) },
                )
            }
            val c = cell
            if (c == null) {
                NuruCard {
                    Text("No cell yet", style = NuruType.cardTitle, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("When your leader adds you to a discipleship cell, you'll see your leader, meeting rhythm and gatherings here.", style = NuruType.caption, color = Nuru.ink600)
                }
                return@Column
            }

            NuruCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // No leader yet: an empty seat — not an avatar made of the
                    // cell's own initial ("D" for Dev Cell A; walk E18).
                    if (c.leader != null) {
                        AvatarCircle(c.leader.avatarUrl, c.leader.name, size = 52)
                    } else {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Nuru.inputBg)
                                .border(1.dp, Nuru.border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Lucide.Armchair, contentDescription = "No leader yet", tint = Nuru.ink400, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Kicker("Cell leader")
                        Text(c.leader?.name ?: "Not assigned yet", style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.Bold)
                        c.leader?.role?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                    }
                }
            }

            val facesShown = c.roster?.takeIf { it.count > 0 }?.let {
                MembersFacesRow(it, c.members, onOpen = { onNavigate("cell-roster") })
                true
            } ?: false

            // Meeting rhythm — the OWN cell's server-derived (or admin-typed)
            // rhythm. When nothing is on the calendar and no real series
            // exists, say so honestly instead of hiding the card.
            val notScheduled = c.next == null && c.rhythmSource != "series"
            if (c.meets != null || c.room != null || c.next != null || notScheduled) {
                NuruCard {
                    Kicker("Meeting rhythm")
                    c.meets?.let { RhythmRow("Meets", it) }
                    c.next?.let { RhythmRow("Next session", fmtEventTime(it.startAt)) }
                    c.room?.let { RhythmRow("Where", it) }
                    if (notScheduled) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text("Not scheduled yet", style = NuruType.body, color = Nuru.ink600)
                    }
                }
            }

            c.next?.let { n ->
                NuruCard {
                    Kicker("Next gathering")
                    Spacer(Modifier.height(Spacing.xs))
                    Text(fmtEventTime(n.startAt), style = NuruType.cardTitle, color = Nuru.navyDeep)
                    n.location?.let { Text(it, style = NuruType.caption, color = Nuru.ink600) }
                }
            }

            // Shepherd's note — server sends leaderView ONLY to the cell's
            // leader (and only with ≥2 recent meetings), so rendering it is
            // already scoped correctly.
            c.leaderView?.takeIf { it.count > 0 }?.let { lv ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radii.card),
                    color = Nuru.priorityBg,
                    border = BorderStroke(1.dp, Nuru.border),
                    shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(Spacing.base)) {
                        Kicker("Shepherd's note")
                        Spacer(Modifier.height(Spacing.xs))
                        val verb = if (lv.count == 1) "hasn't" else "haven't"
                        val names = lv.names.joinToString(", ")
                        Text(
                            "${lv.count} $verb made the last two gatherings" + (if (names.isNotBlank()) " — $names" else ""),
                            style = NuruType.body,
                            color = Nuru.ink,
                        )
                    }
                }
            }

            // Attendance with the server's meaning (EXPERIENCE.md §7.4 #16), the
            // same words as iOS: the member's part in the cell's real recent
            // meetings, then the cell's turnout over them — or "Your cell
            // hasn't met yet". It read "48% · last 8 meetings" here and
            // "0/8 · you, this month" on iOS (8 = a scoring baseline).
            NuruCard {
                Kicker("Attendance")
                Spacer(Modifier.height(Spacing.xs))
                val met = CellAttendanceWords.hasMet(c.attendance.you, c.turnout)
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CellAttendanceWords.lines(c.attendance.you, c.turnout).forEach { line ->
                            Text(
                                line, style = NuruType.body,
                                color = if (met) Nuru.ink else Nuru.ink600,
                                fontWeight = if (met) FontWeight.Bold else FontWeight.SemiBold,
                            )
                        }
                    }
                    // The cell's trend, in the state colours (§8.1 rule 1).
                    when (c.turnout?.takeIf { it.meetings > 0 }?.trend) {
                        "up" -> Text("↑", style = NuruType.body, color = Nuru.success, fontWeight = FontWeight.Bold)
                        "down" -> Text("↓", style = NuruType.body, color = Nuru.danger, fontWeight = FontWeight.Bold)
                        "steady" -> Text("→", style = NuruType.body, color = Nuru.ink600, fontWeight = FontWeight.Bold)
                        else -> Unit
                    }
                }
            }

            // The member count once (walk E18): the faces row above says
            // "5 members", so the stats don't say it again; nothing left to
            // show, no card.
            val showMembersStat = !facesShown
            if (showMembersStat || c.levelLabel != null || c.focus != null) {
                NuruCard {
                    Kicker("Cell stats")
                    Spacer(Modifier.height(Spacing.xs))
                    if (showMembersStat) StatRow("Members", c.members.toString())
                    c.levelLabel?.let { StatRow("Level", it) }
                    c.focus?.let { StatRow("Focus", it) }
                }
            }

            // A way forward (EXPERIENCE.md §7.4 #16 — the page ended in stats):
            // the cell's replays, and its own room in Community, as iOS.
            NuruCard(modifier = Modifier.pressScale().clickable { onNavigate(cellReplays) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Nuru.goldTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Lucide.Clapperboard, null, tint = Nuru.navy, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(Spacing.md))
                    Text("Watch replays", style = NuruType.controlTitle, color = Nuru.ink, modifier = Modifier.weight(1f))
                    Icon(Lucide.ChevronRight, null, tint = Nuru.ink300, modifier = Modifier.size(22.dp))
                }
            }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(Radii.control))
                    .background(Nuru.navyDeep)
                    .clickable { onNavigate(cellRoomId?.let { "chat/$it" } ?: "you") },
                contentAlignment = Alignment.Center,
            ) {
                Text("Open community ›", style = NuruType.cardCta, color = Nuru.white)
            }
        }
    }

    if (showGoLiveSheet) {
        GoLiveSetupSheet(
            me = me,
            lockedScope = "cell",
            onDismiss = { showGoLiveSheet = false },
            onStarted = { created, streamTitle, kind, _ ->
                showGoLiveSheet = false
                onNavigate(liveBroadcastRoute(created, streamTitle, kind))
            },
        )
    }
}

/**
 * Faces of the cell — up to 5 overlapping avatars (photo or initials) with a
 * "+N" bubble for the rest, same idiom as the pathway FootprintsStrip. Tapping
 * opens the full roster (owner, 2026-08-26: "when you click at the members,
 * they open…").
 */
@Composable
private fun MembersFacesRow(roster: CellSummary.Roster, members: Int, onOpen: () -> Unit) {
    NuruCard(modifier = Modifier.pressScale().clickable { onOpen() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shown = roster.faces.take(5)
            Row {
                shown.forEachIndexed { i, f ->
                    Box(Modifier.offset(x = (-8 * i).dp).border(1.5.dp, Nuru.white, CircleShape)) {
                        AvatarCircle(f.avatarUrl, f.firstName, size = 34)
                    }
                }
                val extra = (roster.count - shown.size).coerceAtLeast(0)
                if (extra > 0) {
                    Box(
                        Modifier.offset(x = (-8 * shown.size).dp)
                            .border(1.5.dp, Nuru.white, CircleShape)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Nuru.goldTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+$extra", style = NuruType.caption, color = Nuru.goldLo, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.size(Spacing.md))
            Text(
                "$members member" + (if (members == 1) "" else "s"),
                style = NuruType.caption,
                color = Nuru.ink600,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Lucide.ChevronRight,
                contentDescription = "Open the roster",
                tint = Nuru.ink300,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun RhythmRow(label: String, value: String) {
    Spacer(Modifier.height(Spacing.sm))
    Text(label, style = NuruType.micro, color = Nuru.ink400)
    Text(value, style = NuruType.body, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NuruType.body, color = Nuru.ink600, modifier = Modifier.weight(1f))
        Text(value, style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.Bold)
    }
}

/**
 * The cell's own room in Community — "Open community" lands there, the cell's
 * space as iOS's lands on the cell's board (EXPERIENCE.md §7.4 #16). The server
 * keeps every member in their cell's group room, titled "‹cell› cell"; the
 * title match wins, else the only group room. Null (no room, or more than one
 * with none named for this cell) → the You tab's Community.
 */
internal fun cellRoom(conversations: List<ChatConversation>, cellName: String?): String? {
    val rooms = conversations.filter { it.kind == "group" }
    val named = cellName?.takeIf { it.isNotBlank() }?.let { n -> rooms.firstOrNull { it.title == "$n cell" } }
    return (named ?: rooms.singleOrNull())?.conversationId
}
