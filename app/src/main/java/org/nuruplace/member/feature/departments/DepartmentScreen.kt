// One department (docs/PARTNERS_PROGRAMME.md §4) — route "department/{id}".
// Hero (photo, name, purpose, meets, leader), my standing ("I'd like to serve
// here" → the leader decides in the portal; "Requested — waiting"; Leave in
// the overflow once active), then three sections behind a segmented capsule:
//
//   Posts   — newest first; the leader composes (body + optional image link)
//             and can remove a post (the server lets a leader remove ANY post
//             in their department; posts carry no author id, so no "own"
//             filter is possible client-side).
//   Needs   — server-computed progress; "Give to this need" opens the Give
//             tab preset with need_id (GiveTabScreen via MainShell's give-need
//             route). The leader also sees pending/closed needs and can
//             submit one (the office approves before members can give).
//   Members — avatars; requests to serve are decided in the portal (the
//             request push carries no user_id), so the leader sees a note.
//
// GET /departments/{id} is the only read; every write reloads it.
package org.nuruplace.member.feature.departments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.Department
import org.nuruplace.member.data.net.DepartmentMember
import org.nuruplace.member.data.net.DepartmentNeed
import org.nuruplace.member.data.net.DepartmentNeedBody
import org.nuruplace.member.data.net.DepartmentPost
import org.nuruplace.member.data.net.DepartmentPostBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.feature.community.CHAT
import org.nuruplace.member.feature.community.Segment
import org.nuruplace.member.feature.give.GivePreset
import org.nuruplace.member.feature.give.NEED_GIFT_FUND
import org.nuruplace.member.feature.give.money
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import org.nuruplace.member.util.relTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Capsule = RoundedCornerShape(999.dp)
private val CardShape = RoundedCornerShape(14.dp)

private const val SECTION_POSTS = 0
private const val SECTION_NEEDS = 1
private const val SECTION_MEMBERS = 2

@Composable
fun DepartmentScreen(
    departmentId: String,
    onBack: () -> Unit,
    /** "Give to this need" — MainShell opens the Give tab preset for it. */
    onGiveToNeed: (GivePreset) -> Unit,
) {
    AsyncContent(
        key = departmentId,
        load = { Net.client.api.department(departmentId) },
        refreshable = true,
    ) { d, reload ->
        DepartmentBody(d, reload, onBack, onGiveToNeed)
    }
}

@Composable
private fun DepartmentBody(d: Department, reload: () -> Unit, onBack: () -> Unit, onGiveToNeed: (GivePreset) -> Unit) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var section by rememberSaveable(d.departmentId) { mutableIntStateOf(SECTION_POSTS) }
    // One in-flight write at a time; a failure is said once under the action.
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var submittingNeed by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<DepartmentPost?>(null) }

    /** Run a write, then reload the page; surface a failure without losing the page. */
    fun act(block: suspend () -> Unit, onDone: () -> Unit = {}) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                block()
                onDone()
                reload()
            } catch (e: Exception) {
                error = ApiException.message(e)
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        // ── Hero ──
        Box(Modifier.fillMaxWidth().height(250.dp)) {
            DepartmentPhoto(d.imageUrl, Modifier.fillMaxSize(), shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(
                    Brush.verticalGradient(0f to Nuru.navyDeep.copy(alpha = 0.25f), 0.45f to Color.Transparent, 1f to Nuru.navyDeep.copy(alpha = 0.88f)),
                ),
            )
            // No statusBarsPadding here: MainShell pads the NavHost with the
            // Scaffold's inner padding, which already carries the status-bar
            // inset (adding it again would inset this row twice).
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroCircleButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Nuru.navy, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                if (d.isActive) {
                    Box {
                        HeroCircleButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Nuru.navy, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Leave this department", style = NuruType.body, color = Nuru.danger) },
                                onClick = { menuOpen = false; confirmLeave = true },
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomStart).padding(horizontal = Spacing.screen, vertical = Spacing.base),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(d.name, style = nuruSerif(26, FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                d.meets?.takeIf { it.isNotBlank() }?.let { meets ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = Nuru.goldHi, modifier = Modifier.size(13.dp))
                        Text(meets, style = NuruType.caption, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
        }

        Column(
            Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.base, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (d.purpose.isNotBlank()) Text(d.purpose, style = NuruType.bodyLg, color = Nuru.ink600)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.leaderName != null) {
                    PersonAvatar(d.leaderAvatar, d.leaderName, 28.dp)
                    Column {
                        Text("LED BY", style = NuruType.micro, color = Nuru.goldLo)
                        Text(d.leaderName, style = NuruType.label, color = Nuru.ink)
                    }
                    Spacer(Modifier.weight(1f))
                }
                Icon(Icons.Filled.Groups, contentDescription = null, tint = Nuru.ink400, modifier = Modifier.size(15.dp))
                Text(servingCount(d.memberCount), style = NuruType.caption, color = Nuru.ink600)
            }
            if (d.fit) DeptChip("Good fit for you${matchedGiftsSuffix(d)}", Nuru.goldChipBg, Nuru.goldChipText, Icons.Filled.AutoAwesome)

            // ── My standing ──
            StandingBlock(
                d = d, busy = busy, error = error,
                onServe = { Haptics.tap(view); act({ Net.client.api.requestToServe(d.departmentId) }) },
                onWithdraw = { act({ Net.client.api.leaveDepartment(d.departmentId) }) },
            )

            // ── Sections ──
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .clip(Capsule)
                    .background(CHAT.white.copy(alpha = 0.7f))
                    .border(1.dp, CHAT.border, Capsule)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Segment("Posts", Icons.Filled.Campaign, d.posts.size.takeIf { it > 0 }, section == SECTION_POSTS) { section = SECTION_POSTS }
                Segment("Needs", Icons.Filled.VolunteerActivism, d.needs.size.takeIf { it > 0 }, section == SECTION_NEEDS) { section = SECTION_NEEDS }
                Segment("Members", Icons.Filled.Groups, d.members.size.takeIf { it > 0 }, section == SECTION_MEMBERS) { section = SECTION_MEMBERS }
            }

            when (section) {
                SECTION_POSTS -> PostsSection(d, busy, onCompose = { composing = true }, onRemove = { removing = it })
                SECTION_NEEDS -> NeedsSection(d, onSubmit = { submittingNeed = true }, onGive = { n ->
                    Haptics.tap(view)
                    onGiveToNeed(
                        GivePreset(
                            fundId = NEED_GIFT_FUND,
                            amountMinor = (n.targetMinor - n.raisedMinor).takeIf { it > 0 },
                            needId = n.needId,
                            title = n.title,
                        ),
                    )
                })
                else -> MembersSection(d)
            }
        }
    }

    // ── Dialogs & sheets ──
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave ${d.name}?", style = NuruType.cardTitle, color = Nuru.navy) },
            text = { Text("You can ask to serve here again any time.", style = NuruType.body, color = Nuru.ink600) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; act({ Net.client.api.leaveDepartment(d.departmentId) }) }) {
                    Text("Leave", style = NuruType.cardCta, color = Nuru.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Stay", style = NuruType.cardCta, color = Nuru.ink600) }
            },
        )
    }
    removing?.let { post ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this post?", style = NuruType.cardTitle, color = Nuru.navy) },
            text = { Text("Members will no longer see it.", style = NuruType.body, color = Nuru.ink600) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    act({ Net.client.api.deleteDepartmentPost(d.departmentId, post.postId) })
                }) { Text("Remove", style = NuruType.cardCta, color = Nuru.danger) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("Keep it", style = NuruType.cardCta, color = Nuru.ink600) }
            },
        )
    }
    if (composing) {
        PostComposerSheet(
            busy = busy,
            onDismiss = { composing = false },
            onPost = { body, imageUrl ->
                act({ Net.client.api.createDepartmentPost(d.departmentId, DepartmentPostBody(body, imageUrl)) }) { composing = false }
            },
        )
    }
    if (submittingNeed) {
        NeedSheet(
            busy = busy,
            onDismiss = { submittingNeed = false },
            onSubmit = { body ->
                act({ Net.client.api.submitDepartmentNeed(d.departmentId, body) }) { submittingNeed = false; section = SECTION_NEEDS }
            },
        )
    }
}

@Composable
private fun HeroCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Nuru.white)
            .border(1.dp, Nuru.border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ── My standing ─────────────────────────────────────────────────────────────

@Composable
private fun StandingBlock(d: Department, busy: Boolean, error: String?, onServe: () -> Unit, onWithdraw: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            d.isActive -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                DeptChip("You serve here", Nuru.successBg, Nuru.successText, Icons.Filled.Verified)
                if (d.isLeader || d.myRole == "leader") DeptChip("Leader", Nuru.goldChipBg, Nuru.goldChipText)
                if (busy) CircularProgressIndicator(color = Nuru.gold, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            }
            d.isRequested -> Column(
                Modifier.fillMaxWidth().clip(CardShape).background(Nuru.goldChipBg).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Requested — waiting for the leader", style = NuruType.heading, color = Nuru.goldChipText)
                Text("${d.leaderName ?: "The leader"} will see your request and welcome you in.", style = NuruType.caption, color = Nuru.goldChipText.copy(alpha = 0.85f))
                Text(
                    if (busy) "Please wait…" else "Withdraw request",
                    style = NuruType.cardCta, color = Nuru.ink600,
                    modifier = Modifier.padding(top = 4.dp).clickable(enabled = !busy) { onWithdraw() },
                )
            }
            else -> {
                PrimaryButton("I'd like to serve here", onClick = onServe, enabled = d.isOpenToJoin && !busy, loading = busy)
                if (!d.isOpenToJoin) {
                    Text(
                        "Not taking new members right now — check back, or speak to ${d.leaderName ?: "the leader"}.",
                        style = NuruType.caption, color = Nuru.ink400, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        error?.let { Text(it, style = NuruType.caption, color = Nuru.danger) }
    }
}

// ── Posts ───────────────────────────────────────────────────────────────────

@Composable
private fun PostsSection(d: Department, busy: Boolean, onCompose: () -> Unit, onRemove: (DepartmentPost) -> Unit) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (d.isLeader) LeaderPill("Write a post") { Haptics.tap(view); onCompose() }
        if (d.posts.isEmpty()) {
            SectionEmpty(if (d.isLeader) "No posts yet — tell your team what's coming up." else "No posts yet.")
        } else {
            d.posts.forEach { p -> PostCard(p, canRemove = d.isLeader && !busy) { onRemove(p) } }
        }
    }
}

@Composable
private fun PostCard(p: DepartmentPost, canRemove: Boolean, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Nuru.white).border(1.dp, Nuru.border, CardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PersonAvatar(p.authorAvatar, p.authorName, 28.dp)
            Column(Modifier.weight(1f)) {
                Text(p.authorName ?: "Leader", style = NuruType.label, color = Nuru.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relTime(p.createdAt).ifBlank { " " }, style = NuruType.micro, color = Nuru.ink400)
            }
            if (canRemove) {
                Text(
                    "Remove", style = nuruSans(11, FontWeight.SemiBold), color = Nuru.danger,
                    modifier = Modifier.clip(CircleShape).clickable { onRemove() }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Text(p.body, style = NuruType.body, color = Nuru.ink)
        p.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(10.dp)).background(Nuru.progressTrack),
            )
        }
    }
}

/** Leader compose: body (required) + an optional image link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostComposerSheet(busy: Boolean, onDismiss: () -> Unit, onPost: (body: String, imageUrl: String?) -> Unit) {
    var body by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    val trimmedBody = body.trim()
    val url = imageUrl.trim()
    val urlOk = url.isBlank() || url.startsWith("http://") || url.startsWith("https://")
    val valid = trimmedBody.isNotEmpty() && trimmedBody.length <= 2000 && urlOk
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nuru.paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Write a post", style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
            Text("Members of this department are notified.", style = NuruType.caption, color = Nuru.ink600)
            OutlinedTextField(
                value = body, onValueChange = { body = it.take(2000) },
                placeholder = { Text("What's happening in the team?", style = NuruType.body, color = Nuru.ink400) },
                minLines = 4, textStyle = NuruType.bodyLg.copy(color = Nuru.ink),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = imageUrl, onValueChange = { imageUrl = it.take(500) }, singleLine = true,
                placeholder = { Text("Image link (optional)", style = NuruType.body, color = Nuru.ink400) },
                isError = !urlOk, textStyle = NuruType.body.copy(color = Nuru.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!urlOk) Text("An image link starts with http:// or https://", style = NuruType.caption, color = Nuru.danger)
            SheetButton(if (busy) "Posting…" else "Post", enabled = valid && !busy, busy = busy) { onPost(trimmedBody, url.ifBlank { null }) }
        }
    }
}

// ── Needs ───────────────────────────────────────────────────────────────────

@Composable
private fun NeedsSection(d: Department, onSubmit: () -> Unit, onGive: (DepartmentNeed) -> Unit) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (d.isLeader) LeaderPill("Submit a need") { Haptics.tap(view); onSubmit() }
        if (d.needs.isEmpty()) {
            SectionEmpty(if (d.isLeader) "No needs yet — submit one and the office will review it." else "No open needs right now.")
        } else {
            d.needs.forEach { n -> NeedCard(n, showStatus = d.isLeader) { onGive(n) } }
        }
    }
}

@Composable
private fun NeedCard(n: DepartmentNeed, showStatus: Boolean, onGive: () -> Unit) {
    val (chipText, chipBg, chipFg) = needChip(n)
    val done = n.reached || n.status == "closed"
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Nuru.white).border(1.dp, Nuru.border, CardShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(n.title, style = nuruSerif(17, FontWeight.SemiBold), color = Nuru.ink, modifier = Modifier.weight(1f))
            if (showStatus || n.reached) DeptChip(chipText, chipBg, chipFg)
        }
        if (n.why.isNotBlank()) Text(n.why, style = NuruType.body, color = Nuru.ink600)
        LinearProgressIndicator(
            progress = { (n.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = if (done) Nuru.success else Nuru.gold, trackColor = Nuru.track,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${money(n.raisedMinor, n.currency)} of ${money(n.targetMinor, n.currency)}", style = NuruType.caption, color = Nuru.ink600)
            Text(
                when {
                    n.reached -> "Reached"
                    n.deadline != null -> "By ${deadlineLabel(n.deadline)}"
                    else -> "${n.percent}%"
                },
                style = NuruType.caption, color = Nuru.ink600,
            )
        }
        if (n.isOpen) {
            Text(
                "Give to this need", style = nuruSans(12, FontWeight.SemiBold), color = Color.White,
                modifier = Modifier.clip(CircleShape).background(Nuru.navyDeep).clickable { onGive() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun needChip(n: DepartmentNeed): Triple<String, Color, Color> = when {
    n.reached -> Triple("Reached", Nuru.successBg, Nuru.successText)
    n.status == "approved" -> Triple("Open", Nuru.successBg, Nuru.successText)
    n.status == "pending" -> Triple("Awaiting approval", Nuru.goldChipBg, Nuru.goldChipText)
    n.status == "rejected" -> Triple("Not approved", Nuru.dangerBg, Nuru.danger)
    else -> Triple("Closed", Nuru.inputBg, Nuru.ink600)
}

private val deadlineFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private fun deadlineLabel(iso: String): String =
    runCatching { LocalDate.parse(iso.take(10)).format(deadlineFmt) }.getOrDefault(iso.take(10))

/** Leader submit: title, why, amount (KSh), optional deadline (yyyy-MM-dd). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeedSheet(busy: Boolean, onDismiss: () -> Unit, onSubmit: (DepartmentNeedBody) -> Unit) {
    var title by remember { mutableStateOf("") }
    var why by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    val amountMajor = amountText.filter { it.isDigit() }.take(8).toIntOrNull() ?: 0
    val deadlineOk = deadline.isBlank() || runCatching { LocalDate.parse(deadline.trim()) }.isSuccess
    val valid = title.trim().length in 3..120 && why.trim().length in 10..1500 && amountMajor in 1..50_000_000 && deadlineOk
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Nuru.paper) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Submit a need", style = nuruSerif(22, FontWeight.Medium), color = Nuru.ink)
            Text("The office approves a need before members can give to it.", style = NuruType.caption, color = Nuru.ink600)
            Text("WHAT IS NEEDED", style = NuruType.micro, color = Nuru.goldLo)
            OutlinedTextField(
                value = title, onValueChange = { title = it.take(120) }, singleLine = true,
                placeholder = { Text("e.g. New sound desk", style = NuruType.body, color = Nuru.ink400) },
                textStyle = NuruType.bodyLg.copy(color = Nuru.ink), modifier = Modifier.fillMaxWidth(),
            )
            Text("WHY", style = NuruType.micro, color = Nuru.goldLo)
            OutlinedTextField(
                value = why, onValueChange = { why = it.take(1500) }, minLines = 3,
                placeholder = { Text("What it makes possible for the church", style = NuruType.body, color = Nuru.ink400) },
                textStyle = NuruType.body.copy(color = Nuru.ink), modifier = Modifier.fillMaxWidth(),
            )
            Text("TARGET", style = NuruType.micro, color = Nuru.goldLo)
            OutlinedTextField(
                value = amountText, onValueChange = { v -> amountText = v.filter { it.isDigit() }.take(8) }, singleLine = true,
                prefix = { Text("KSh ", style = NuruType.body, color = Nuru.ink600) },
                textStyle = nuruSerif(22, FontWeight.Medium).copy(color = Nuru.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("BY WHEN (OPTIONAL)", style = NuruType.micro, color = Nuru.goldLo)
            OutlinedTextField(
                value = deadline, onValueChange = { deadline = it.take(10) }, singleLine = true,
                placeholder = { Text("YYYY-MM-DD", style = NuruType.body, color = Nuru.ink400) },
                isError = !deadlineOk, textStyle = NuruType.body.copy(color = Nuru.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!deadlineOk) Text("Use the form 2026-12-31.", style = NuruType.caption, color = Nuru.danger)
            SheetButton(if (busy) "Submitting…" else "Submit for approval", enabled = valid && !busy, busy = busy) {
                onSubmit(
                    DepartmentNeedBody(
                        title = title.trim(), why = why.trim(), targetMinor = amountMajor * 100,
                        deadline = deadline.trim().ifBlank { null },
                    ),
                )
            }
        }
    }
}

// ── Members ─────────────────────────────────────────────────────────────────

@Composable
private fun MembersSection(d: Department) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d.isLeader) {
            Text(
                "Requests to serve are approved in the portal, or by tapping a request notification.",
                style = NuruType.caption, color = Nuru.ink600,
                modifier = Modifier.fillMaxWidth().clip(CardShape).background(Nuru.goldChipBg).padding(12.dp),
            )
        }
        if (d.members.isEmpty()) {
            SectionEmpty("No members yet — be the first to serve here.")
        } else {
            Column(
                Modifier.fillMaxWidth().clip(CardShape).background(Nuru.white).border(1.dp, Nuru.border, CardShape).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                d.members.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m -> MemberTile(m, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberTile(m: DepartmentMember, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            PersonAvatar(m.avatarUrl, m.fullName, 52.dp)
            if (m.role == "leader") {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(18.dp).clip(CircleShape).background(Nuru.gold).border(2.dp, Nuru.white, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Verified, contentDescription = "Leader", tint = Nuru.navyDeep, modifier = Modifier.size(10.dp)) }
            }
        }
        Text(m.fullName, style = NuruType.micro, color = Nuru.ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ── Small shared pieces ─────────────────────────────────────────────────────

@Composable
private fun LeaderPill(label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Nuru.navyDeep).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Filled.Add, null, tint = Nuru.gold, modifier = Modifier.size(13.dp))
        Text(label, style = nuruSans(12, FontWeight.SemiBold), color = Color.White)
    }
}

@Composable
private fun SectionEmpty(text: String) {
    Text(
        text, style = NuruType.body, color = Nuru.ink600,
        modifier = Modifier.fillMaxWidth().clip(CardShape).background(Nuru.white).border(1.dp, Nuru.border, CardShape).padding(16.dp),
    )
}

@Composable
private fun SheetButton(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Nuru.navyDeep, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp).size(16.dp))
        Text(label, style = NuruType.cardCta)
    }
}
