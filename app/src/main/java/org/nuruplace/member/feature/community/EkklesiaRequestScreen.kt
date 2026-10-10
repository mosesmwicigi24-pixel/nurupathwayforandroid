// One need on the watch: the request in full, the act of interceding, who is
// standing on it, the words written back (updates and testimony), and the
// requester's or a leader's hand on it (answered, pinned, withdrawn).
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.EkklesiaAnsweredBody
import org.nuruplace.member.data.net.EkklesiaPinnedBody
import org.nuruplace.member.data.net.EkklesiaRequest
import org.nuruplace.member.data.net.EkklesiaRequestDetail
import org.nuruplace.member.data.net.EkklesiaUpdate
import org.nuruplace.member.data.net.EkklesiaUpdateBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.ListSkeleton
import org.nuruplace.member.ui.components.NuruModalBottomSheet
import org.nuruplace.member.ui.components.PrimaryButton
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
fun EkklesiaRequestScreen(requestId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { EkklesiaPulse.refresh() }
    val isLeader = EkklesiaPulse.summary?.isLeader == true
    val isMember = EkklesiaPulse.summary?.isMember == true
    var closing by remember { mutableStateOf(false) }
    var withdrawArmed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(GrowPal.paper)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(GrowPal.white)
                    .border(1.dp, GrowPal.border, RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Back") { onBack() },
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.ArrowLeft, "Back", tint = GrowPal.navy, modifier = Modifier.size(18.dp)) }
            Text("EKKLESIA · THE WATCH", style = gInter(10, FontWeight.SemiBold, 1.8f), color = GrowPal.goldChipText)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AsyncContent(loading = { ListSkeleton(rows = 4) }, load = { Net.client.api.ekklesiaRequest(requestId) }) { d: EkklesiaRequestDetail, reload ->
                val r = d.request
                val canClose = r.mine || isLeader
                Column(
                    Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RequestFull(r)
                    if (!r.isAnswered) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IntercedeButton(r, onIntercede = {
                                scope.launch {
                                    noticeOnFailure(context) { Net.client.api.ekklesiaIntercede(r.requestId) }?.let { EkklesiaPulse.invalidate(); reload() }
                                }
                            })
                            EkklesiaWords.countsLine(r.intercessorCount, r.prayerCount, 0)?.let {
                                Text(it, style = gInter(11), color = GrowPal.ink400)
                            }
                        }
                    }
                    if (d.intercessors.isNotEmpty()) Standing(d)
                    Updates(d.updates)
                    if (isMember || r.mine) WriteBack(r.requestId, onWritten = { reload() })
                    if (canClose) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ActionPill(if (r.isAnswered) "Reopen" else "Mark answered", Lucide.CheckCircle) {
                                if (r.isAnswered) {
                                    scope.launch {
                                        noticeOnFailure(context) { Net.client.api.ekklesiaAnswered(r.requestId, EkklesiaAnsweredBody(false)) }?.let { EkklesiaPulse.invalidate(); reload() }
                                    }
                                } else closing = true
                            }
                            if (isLeader && !r.isAnswered) ActionPill(if (r.isPinned) "Unpin" else "Pin", Lucide.Star) {
                                scope.launch {
                                    noticeOnFailure(context) { Net.client.api.ekklesiaPinned(r.requestId, EkklesiaPinnedBody(!r.isPinned)) }?.let { reload() }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (withdrawArmed) "Tap again to withdraw" else "Withdraw",
                                style = gInter(11, FontWeight.Medium), color = if (withdrawArmed) GrowPal.danger else GrowPal.ink400,
                                modifier = Modifier.clickable {
                                    if (!withdrawArmed) withdrawArmed = true
                                    else scope.launch {
                                        noticeOnFailure(context) { Net.client.api.ekklesiaWithdraw(r.requestId) }?.let { EkklesiaPulse.invalidate(); onBack() }
                                    }
                                }.padding(6.dp),
                            )
                        }
                    }
                }
                if (closing) AnsweredSheet(r.requestId, onDismiss = { closing = false }, onClosed = { EkklesiaPulse.invalidate(); reload() })
            }
        }
    }
}

@Composable
private fun RequestFull(r: EkklesiaRequest) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(GrowPal.white).border(1.dp, GrowPal.border, CardShape).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Face(r.authorName, r.authorAvatar, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(r.authorName, style = gInter(12, FontWeight.Bold), color = GrowPal.ink)
                Text("brought this ${relTime(r.createdAt)}", style = gInter(11), color = GrowPal.ink400)
            }
            if (r.isAnswered) {
                Box(Modifier.clip(Capsule).background(GrowPal.successBg).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Answered", style = gInter(11, FontWeight.Medium), color = GrowPal.successText)
                }
            } else UrgencyChip(r.isUrgent, r.isPinned)
        }
        Text(r.title, style = gSerif(22, FontWeight.SemiBold), color = GrowPal.navy, modifier = Modifier.padding(top = 12.dp))
        r.forWhom?.takeIf { it.isNotBlank() }?.let {
            Text("For $it", style = gInter(13, FontWeight.Medium), color = GrowPal.goldChipText, modifier = Modifier.padding(top = 2.dp))
        }
        Text(r.body, style = gInter(14).copy(lineHeight = 21.sp), color = GrowPal.ink, modifier = Modifier.padding(top = 10.dp))
        if (r.isAnswered) {
            Column(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GrowPal.successBg).padding(12.dp),
            ) {
                Text("ANSWERED${r.answeredAt?.let { " · ${relTime(it)}" } ?: ""}", style = gInter(10, FontWeight.Bold, 1.4f), color = GrowPal.successText)
                r.answeredNote?.takeIf { it.isNotBlank() }?.let {
                    Text("“$it”", style = gInter(13).copy(lineHeight = 19.sp), color = GrowPal.successText, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Standing(d: EkklesiaRequestDetail) {
    val r = d.request
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("STANDING ON THIS NEED", style = gInter(10, FontWeight.SemiBold, 1.6f), color = GrowPal.eyebrow)
        Column(
            Modifier.fillMaxWidth().clip(CardShape).background(GrowPal.white).border(1.dp, GrowPal.border, CardShape).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            d.intercessors.take(12).forEach { i ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Face(i.name, i.avatar, 28.dp)
                    Text(if (i.me) "You" else i.name, style = gInter(13, FontWeight.Medium), color = GrowPal.ink, modifier = Modifier.weight(1f))
                    Text(if (i.days == 1) "1 day" else "${i.days} days", style = gInter(11), color = GrowPal.ink400)
                }
            }
            if (r.intercessorCount > d.intercessors.size) {
                Text("and ${r.intercessorCount - d.intercessors.size} more", style = gInter(11), color = GrowPal.ink400)
            }
        }
    }
}

@Composable
private fun Updates(updates: List<EkklesiaUpdate>) {
    if (updates.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("WORD BACK TO THE WATCH", style = gInter(10, FontWeight.SemiBold, 1.6f), color = GrowPal.eyebrow)
        updates.forEach { u ->
            val testimony = u.kind == "testimony"
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(if (testimony) GrowPal.goldTint.copy(alpha = 0.5f) else GrowPal.white)
                    .border(1.dp, if (testimony) GrowPal.gold.copy(alpha = 0.35f) else GrowPal.border, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Face(u.authorName, u.authorAvatar, 26.dp)
                    Text(if (u.mine) "You" else u.authorName, style = gInter(12, FontWeight.Bold), color = GrowPal.ink, modifier = Modifier.weight(1f))
                    if (testimony) Text("TESTIMONY", style = gInter(9, FontWeight.Bold, 1.2f), color = GrowPal.goldChipText)
                    Text(relTime(u.createdAt), style = gInter(11), color = GrowPal.ink400)
                }
                Text(u.body, style = gInter(13).copy(lineHeight = 19.sp), color = GrowPal.ink, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun WriteBack(requestId: String, onWritten: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var testimony by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var ids by remember { mutableStateOf(UUID.randomUUID().toString() to UUID.randomUUID().toString()) }
    Column(
        Modifier.fillMaxWidth().imePadding().clip(CardShape).background(GrowPal.white).border(1.dp, GrowPal.border, CardShape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Write back to the watch", style = gInter(13, FontWeight.SemiBold), color = GrowPal.navy)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GrowPal.coolPaper)
                .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp)).padding(12.dp),
        ) {
            if (text.isBlank()) Text(if (testimony) "What God did…" else "How it is going, what to pray now…", style = gInter(14), color = GrowPal.ink400)
            BasicTextField(text, { text = it }, textStyle = gInter(14).copy(color = GrowPal.ink), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindPill("Update", !testimony) { testimony = false }
            KindPill("Testimony", testimony) { testimony = true }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(Capsule).background(if (text.isNotBlank() && !sending) GrowPal.gold else GrowPal.track)
                    .clickable(enabled = text.isNotBlank() && !sending) {
                        sending = true
                        scope.launch {
                            val ok = noticeOnFailure(context) {
                                Net.client.api.ekklesiaWriteBack(requestId, EkklesiaUpdateBody(ids.first, text.trim(), if (testimony) "testimony" else "update", ids.second))
                            }
                            sending = false
                            if (ok != null) { text = ""; ids = UUID.randomUUID().toString() to UUID.randomUUID().toString(); onWritten() }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Lucide.Send, null, tint = GrowPal.navyDeep, modifier = Modifier.size(14.dp))
                    Text(if (sending) "Sending…" else "Send", style = gInter(12, FontWeight.Bold), color = GrowPal.navyDeep)
                }
            }
        }
    }
}

@Composable
private fun KindPill(label: String, on: Boolean, onSelect: () -> Unit) {
    Box(
        Modifier.clip(Capsule).background(if (on) GrowPal.navy else GrowPal.white)
            .then(if (on) Modifier else Modifier.border(1.dp, GrowPal.border, Capsule))
            .clickable { onSelect() }.padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(label, style = gInter(11, FontWeight.Bold), color = if (on) Color.White else GrowPal.ink600) }
}

@Composable
private fun ActionPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.clip(Capsule).background(GrowPal.white).border(1.dp, GrowPal.border, Capsule)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = GrowPal.navy, modifier = Modifier.size(14.dp))
            Text(label, style = gInter(12, FontWeight.SemiBold), color = GrowPal.navy)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnsweredSheet(requestId: String, onDismiss: () -> Unit, onClosed: () -> Unit) {
    NuruModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = GrowPal.white,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var note by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        Column(Modifier.imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Answered", style = gSerif(18, FontWeight.SemiBold), color = GrowPal.navy)
            Text("A word of testimony for everyone who stood on this need. The watch keeps it.", style = gInter(12), color = GrowPal.ink600)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GrowPal.coolPaper)
                    .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp)).padding(12.dp),
            ) {
                if (note.isBlank()) Text("What God did (optional)", style = gInter(14), color = GrowPal.ink400)
                BasicTextField(note, { note = it }, textStyle = gInter(14).copy(color = GrowPal.ink), modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp))
            }
            PrimaryButton(
                label = if (saving) "Closing…" else "Mark answered", enabled = !saving, loading = saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val ok = noticeOnFailure(context) { Net.client.api.ekklesiaAnswered(requestId, EkklesiaAnsweredBody(true, note.trim().ifBlank { null })) }
                        saving = false
                        if (ok != null) { onClosed(); onDismiss() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
