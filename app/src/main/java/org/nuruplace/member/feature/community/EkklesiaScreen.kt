// Ekklesia — the intercessory watch, the Prayer Room's fifth tab. The watch
// itself at the top (who stands, what waits, join or leave), then the needs:
// bodies and the act of interceding for intercessors; titles only, and the
// door in, for everyone else. Any member may bring a need. Port target for
// iOS EkklesiaView (same strings, same order).
package org.nuruplace.member.feature.community

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.EkklesiaFace
import org.nuruplace.member.data.net.EkklesiaGroup
import org.nuruplace.member.data.net.EkklesiaOverview
import org.nuruplace.member.data.net.EkklesiaPreview
import org.nuruplace.member.data.net.EkklesiaRequest
import org.nuruplace.member.data.net.EkklesiaRequestBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.ListSkeleton
import org.nuruplace.member.ui.components.NuruModalBottomSheet
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.StateCard
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif
import org.nuruplace.member.ui.components.noticeOnFailure
import org.nuruplace.member.ui.icons.Lucide
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.util.relTime
import java.util.UUID

private val Capsule = RoundedCornerShape(999.dp)
private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun EkklesiaScreen(
    /** On a pushed Prayer Room the list's end clears the gesture bar itself. */
    clearNavigationBar: Boolean = false,
    onOpenRequest: (String) -> Unit,
) {
    var status by remember { mutableStateOf("active") }
    var composing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(GrowPal.paper).verticalScroll(rememberScrollState())) {
        AsyncContent(key = status, loading = { ListSkeleton(rows = 4) }, load = { Net.client.api.ekklesia(status) }) { ov: EkklesiaOverview, reload ->
            val g = ov.group
            Column(
                Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 32.dp)
                    .then(if (clearNavigationBar) Modifier.navigationBarsPadding() else Modifier),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WatchCard(
                    g,
                    onJoin = {
                        scope.launch {
                            noticeOnFailure(context) { Net.client.api.ekklesiaJoin() }?.let { EkklesiaPulse.invalidate(); reload() }
                        }
                    },
                    onLeave = {
                        scope.launch {
                            noticeOnFailure(context) { Net.client.api.ekklesiaLeave() }?.let { EkklesiaPulse.invalidate(); reload() }
                        }
                    },
                )
                BringNeedPrompt { composing = true }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("Active", g.activeCount, status == "active") { status = "active" }
                    StatusPill("Answered", g.answeredCount, status == "answered") { status = "answered" }
                }
                when {
                    !g.isMember -> {
                        if (ov.preview.isEmpty()) {
                            StateCard(
                                title = if (status == "answered") "No answered needs yet" else "The watch is quiet",
                                line = if (status == "answered") "When a need closes with a testimony, it rests here." else "Bring a need, or stand ready for the next one.",
                                glyph = Lucide.Flame,
                                actionLabel = "Stand in the gap",
                                onAction = {
                                    scope.launch {
                                        noticeOnFailure(context) { Net.client.api.ekklesiaJoin() }?.let { EkklesiaPulse.invalidate(); reload() }
                                    }
                                },
                            )
                        } else {
                            ov.preview.forEach { PreviewCard(it) }
                            Text(
                                "Join the watch to read each need and intercede. Only intercessors see what is written.",
                                style = gInter(12), color = GrowPal.ink400,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    ov.requests.isEmpty() -> StateCard(
                        title = if (status == "answered") "No answered needs yet" else "Nothing waits on the watch",
                        line = if (status == "answered") "When a need closes with a testimony, it rests here." else "The first need brought here will reach every intercessor at once.",
                        glyph = Lucide.Flame,
                        actionLabel = "Bring a need",
                        onAction = { composing = true },
                    )
                    else -> ov.requests.forEach { r ->
                        RequestCard(
                            r,
                            onOpen = { onOpenRequest(r.requestId) },
                            onIntercede = {
                                scope.launch {
                                    noticeOnFailure(context) { Net.client.api.ekklesiaIntercede(r.requestId) }?.let { EkklesiaPulse.invalidate(); reload() }
                                }
                            },
                        )
                    }
                }
            }
            if (composing) BringNeedSheet(onDismiss = { composing = false }, onBrought = { EkklesiaPulse.invalidate(); reload() })
        }
    }
}

// ── the watch ───────────────────────────────────────────────────────────────

@Composable
private fun WatchCard(g: EkklesiaGroup, onJoin: () -> Unit, onLeave: () -> Unit) {
    var leaving by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(CardShape)
            .background(Brush.linearGradient(listOf(Color(0xFF0A1628), Color(0xFF16273F))))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EKKLESIA · INTERCESSORS", style = gInter(10, FontWeight.SemiBold, 1.8f), color = GrowPal.gold)
                Text(g.mission, style = gSerif(22, FontWeight.SemiBold), color = Color.White, modifier = Modifier.padding(top = 4.dp))
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(GrowPal.gold.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.Flame, null, tint = GrowPal.gold, modifier = Modifier.size(22.dp)) }
        }
        Text(
            "The church on its knees for the church. A need brought here reaches every intercessor, and is prayed again tomorrow.",
            style = gInter(12).copy(lineHeight = 17.sp), color = Color.White.copy(alpha = 0.72f),
        )
        // Counts — never a zero (the watch is honest, not boastful).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
            if (g.memberCount > 0) Stat(g.memberCount, if (g.memberCount == 1) "intercessor" else "intercessors")
            if (g.activeCount > 0) Stat(g.activeCount, if (g.activeCount == 1) "need" else "needs")
            if (g.prayersToday > 0) Stat(g.prayersToday, "prayers today")
        }
        if (g.faces.isNotEmpty()) FacesRow(g.faces, g.memberCount)
        if (g.isMember) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                Box(Modifier.clip(Capsule).background(GrowPal.gold.copy(alpha = 0.18f)).border(1.dp, GrowPal.gold.copy(alpha = 0.5f), Capsule).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(
                        buildString {
                            append(if (g.myRole == "leader") "You lead the watch" else "You stand on the watch")
                            if (g.myDays > 0) append(" · ${g.myDays} ${if (g.myDays == 1) "day" else "days"}")
                        },
                        style = gInter(12, FontWeight.SemiBold), color = GrowPal.gold,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (leaving) "Tap again to leave" else "Leave",
                    style = gInter(11, FontWeight.Medium), color = Color.White.copy(alpha = if (leaving) 0.9f else 0.45f),
                    modifier = Modifier.clickable { if (leaving) { leaving = false; onLeave() } else leaving = true }.padding(6.dp),
                )
            }
            if (g.needsMeToday > 0) {
                Text(EkklesiaWords.memberLine(g.needsMeToday, g.urgentCount), style = gInter(12, FontWeight.Medium), color = GrowPal.gold)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Box(
                    Modifier.clip(Capsule).background(GrowPal.gold).clickable(onClickLabel = "Join the watch") { onJoin() }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Text("Stand in the gap", style = gInter(13, FontWeight.Bold), color = GrowPal.navyDeep) }
                Spacer(Modifier.width(12.dp))
                Text("Join as an intercessor", style = gInter(11), color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun Stat(n: Int, label: String) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$n", style = gSerif(18, FontWeight.SemiBold), color = GrowPal.gold)
        Text(label, style = gInter(11), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 2.dp))
    }
}

@Composable
private fun FacesRow(faces: List<EkklesiaFace>, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        faces.take(6).forEachIndexed { i, f ->
            Box(Modifier.offset(x = (-8 * i).dp)) { Face(f.name, f.avatar, 28.dp, ring = true) }
        }
        val more = total - faces.take(6).size
        if (more > 0) Text("+$more", style = gInter(11, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.7f), modifier = Modifier.offset(x = (-8 * (faces.take(6).size - 1)).dp + 6.dp))
    }
}

@Composable
internal fun Face(name: String, url: String?, size: androidx.compose.ui.unit.Dp, ring: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape).background(GrowPal.goldChipBg)
            .then(if (ring) Modifier.border(1.5.dp, Color(0xFF0E1D33), CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" },
                style = gInter((size.value * 0.36f).toInt(), FontWeight.SemiBold), color = Nuru.navy,
            )
        }
    }
}

// ── bringing a need ─────────────────────────────────────────────────────────

@Composable
private fun BringNeedPrompt(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(GrowPal.goldTint.copy(alpha = 0.55f))
            .border(1.dp, GrowPal.gold.copy(alpha = 0.25f), shape)
            .clickable(onClickLabel = "Bring a need") { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(GrowPal.white), contentAlignment = Alignment.Center) {
            Icon(Lucide.Plus, contentDescription = null, tint = GrowPal.navy, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Bring a need", style = gInter(14, FontWeight.SemiBold), color = GrowPal.navy)
            Text("Every intercessor hears it at once.", style = gInter(11), color = GrowPal.ink600)
        }
        Icon(Lucide.ChevronRight, contentDescription = null, tint = GrowPal.ink400, modifier = Modifier.size(14.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BringNeedSheet(onDismiss: () -> Unit, onBrought: () -> Unit) {
    NuruModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = GrowPal.white,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var title by remember { mutableStateOf("") }
        var forWhom by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var urgent by remember { mutableStateOf(false) }
        var posting by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        // Stable ids across retries: a second tap cannot bring the need twice.
        val ids = remember { UUID.randomUUID().toString() to UUID.randomUUID().toString() }
        Column(
            Modifier.imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Bring a need to the watch", style = gSerif(18, FontWeight.SemiBold), color = GrowPal.navy)
            Text("Say who it is for and what to pray. Every intercessor is told once; they carry it until it is answered.", style = gInter(12), color = GrowPal.ink600)
            Field(title, { title = it }, "What to pray for (a short title)")
            Field(forWhom, { forWhom = it }, "For whom (optional)")
            Field(body, { body = it }, "The need, as you would tell the watch…", minHeight = 110.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.clip(Capsule)
                        .background(if (urgent) Nuru.warningBg else GrowPal.coolPaper)
                        .border(1.dp, if (urgent) Nuru.warning else GrowPal.border, Capsule)
                        .clickable { urgent = !urgent }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Lucide.Zap, null, tint = if (urgent) Nuru.warning else GrowPal.ink400, modifier = Modifier.size(14.dp))
                        Text(if (urgent) "Urgent" else "Mark urgent", style = gInter(12, FontWeight.SemiBold), color = if (urgent) Nuru.warning else GrowPal.ink600)
                    }
                }
                Text("Urgent needs rise to the top and say so in the push.", style = gInter(11), color = GrowPal.ink400, modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, style = gInter(12, FontWeight.Medium), color = GrowPal.danger) }
            PrimaryButton(
                label = if (posting) "Bringing…" else "Bring it to the watch",
                enabled = title.isNotBlank() && body.isNotBlank() && !posting,
                loading = posting,
                onClick = {
                    posting = true; error = null
                    scope.launch {
                        val res = runCatching {
                            Net.client.api.ekklesiaBring(
                                EkklesiaRequestBody(
                                    requestId = ids.first, title = title.trim(), body = body.trim(),
                                    forWhom = forWhom.trim().ifBlank { null },
                                    urgency = if (urgent) "urgent" else "normal", clientMutationId = ids.second,
                                ),
                            )
                        }
                        posting = false
                        res.onSuccess { onBrought(); onDismiss() }
                            .onFailure { error = org.nuruplace.member.data.net.ApiException.message(it, context) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, minHeight: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GrowPal.coolPaper)
            .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp)).padding(12.dp),
    ) {
        if (value.isBlank()) Text(hint, style = gInter(14), color = GrowPal.ink400)
        BasicTextField(
            value, onChange,
            textStyle = gInter(14).copy(color = GrowPal.ink),
            modifier = Modifier.fillMaxWidth().then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier),
        )
    }
}

// ── the needs ───────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(label: String, count: Int, on: Boolean, onSelect: () -> Unit) {
    Box(
        Modifier.clip(Capsule)
            .background(if (on) GrowPal.navy else GrowPal.white)
            .then(if (on) Modifier else Modifier.border(1.dp, GrowPal.border, Capsule))
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = gInter(12, FontWeight.Bold), color = if (on) Color.White else GrowPal.ink600)
            if (count > 0) {
                Box(Modifier.clip(Capsule).background(if (on) GrowPal.gold else GrowPal.goldChipBg).padding(horizontal = 7.dp, vertical = 1.dp)) {
                    Text("$count", style = gInter(10, FontWeight.Bold), color = if (on) GrowPal.navyDeep else GrowPal.goldChipText)
                }
            }
        }
    }
}

@Composable
internal fun UrgencyChip(urgent: Boolean, pinned: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (pinned) Box(Modifier.clip(Capsule).background(GrowPal.goldChipBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text("PINNED", style = gInter(9, FontWeight.Bold, 1.2f), color = GrowPal.goldChipText)
        }
        if (urgent) Box(Modifier.clip(Capsule).background(Nuru.warningBg).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text("URGENT", style = gInter(9, FontWeight.Bold, 1.2f), color = Nuru.warning)
        }
    }
}

@Composable
private fun PreviewCard(p: EkklesiaPreview) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(GrowPal.white).border(1.dp, GrowPal.border, CardShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        UrgencyChip(urgent = p.urgency == "urgent", pinned = false)
        Text(p.title, style = gSerif(16, FontWeight.SemiBold), color = GrowPal.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Lucide.Lock, null, tint = GrowPal.ink400, modifier = Modifier.size(12.dp))
            Text(
                EkklesiaWords.countsLine(p.intercessorCount, 0, 0)?.let { "$it · ${relTime(p.createdAt)}" } ?: relTime(p.createdAt),
                style = gInter(11), color = GrowPal.ink400,
            )
        }
    }
}

@Composable
internal fun IntercedeButton(r: EkklesiaRequest, onIntercede: () -> Unit, modifier: Modifier = Modifier) {
    val done = r.iPrayedToday
    Box(
        modifier.clip(Capsule)
            .background(if (done) GrowPal.white else GrowPal.gold)
            .border(1.dp, if (done) GrowPal.gold else Color.Transparent, Capsule)
            .then(if (done) Modifier else Modifier.clickable(onClickLabel = "I interceded today") { onIntercede() })
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(if (done) Lucide.Check else Lucide.Flame, null, tint = if (done) GrowPal.goldLo else GrowPal.navyDeep, modifier = Modifier.size(14.dp))
            Text(
                when {
                    done && r.myPrayerDays > 1 -> "Prayed today · ${r.myPrayerDays} days"
                    done -> "Prayed today"
                    else -> "I interceded"
                },
                style = gInter(12, FontWeight.Bold), color = if (done) GrowPal.goldLo else GrowPal.navyDeep,
            )
        }
    }
}

@Composable
private fun RequestCard(r: EkklesiaRequest, onOpen: () -> Unit, onIntercede: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(GrowPal.white)
            .border(1.dp, if (r.isUrgent && !r.isAnswered) Nuru.warning.copy(alpha = 0.35f) else GrowPal.border, CardShape)
            .clickable { onOpen() }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Face(r.authorName, r.authorAvatar, 32.dp)
            Column(Modifier.weight(1f)) {
                Text(r.authorName, style = gInter(12, FontWeight.Bold), color = GrowPal.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relTime(r.createdAt), style = gInter(11), color = GrowPal.ink400)
            }
            if (r.isAnswered) {
                Box(Modifier.clip(Capsule).background(GrowPal.successBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Lucide.CheckCircle, null, tint = GrowPal.successText, modifier = Modifier.size(14.dp))
                        Text("Answered", style = gInter(11, FontWeight.Medium), color = GrowPal.successText)
                    }
                }
            } else UrgencyChip(r.isUrgent, r.isPinned)
        }
        Text(r.title, style = gSerif(17, FontWeight.SemiBold), color = GrowPal.ink, modifier = Modifier.padding(top = 10.dp))
        r.forWhom?.takeIf { it.isNotBlank() }?.let {
            Text("For $it", style = gInter(12, FontWeight.Medium), color = GrowPal.goldChipText, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            r.body, style = gInter(13).copy(lineHeight = 18.sp), color = GrowPal.ink600,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
        )
        r.answeredNote?.takeIf { it.isNotBlank() }?.let {
            Text("“$it”", style = gInter(12).copy(lineHeight = 17.sp), color = GrowPal.successText, modifier = Modifier.padding(top = 6.dp))
        }
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!r.isAnswered) IntercedeButton(r, onIntercede)
            EkklesiaWords.countsLine(r.intercessorCount, r.prayerCount, r.updateCount)?.let {
                Text(it, style = gInter(11), color = GrowPal.ink400, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}
