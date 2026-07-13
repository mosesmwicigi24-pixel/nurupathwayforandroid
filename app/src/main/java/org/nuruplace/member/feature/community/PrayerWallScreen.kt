// Prayer wall — the congregation's shared requests, under a navy hero. Compose a
// request via the gold "+" sheet, tap 🙏 to pray (a toggle reaction), open a
// request for its comments. Port of the iOS PrayerWallView.
package org.nuruplace.member.feature.community

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.nuruplace.member.data.net.CreatePrayerBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PrayerWallPost
import org.nuruplace.member.data.net.ReactBody
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.CelebrationCenter
import org.nuruplace.member.ui.components.ListSkeleton
import org.nuruplace.member.ui.components.NuruRefreshBox
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.LiveWave
import org.nuruplace.member.ui.components.Moment
import org.nuruplace.member.ui.components.WaveformBars
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif
import org.nuruplace.member.ui.components.voiceClock
import org.nuruplace.member.util.VoicePlayer
import org.nuruplace.member.util.VoiceRecorder
import org.nuruplace.member.util.relTime
import java.io.File
import java.util.UUID

private val Capsule = RoundedCornerShape(999.dp)

@Composable
fun PrayerWallScreen(onBack: () -> Unit, onOpenPost: (String) -> Unit) {
    var sort by remember { mutableStateOf("latest") }
    var composing by remember { mutableStateOf(false) }

    // Pull-to-refresh wraps the whole page (hero + wall); the reload handle is
    // born inside AsyncContent below, so it travels up through a plain ref.
    var refreshing by remember { mutableStateOf(false) }
    val reloadRef = remember { arrayOfNulls<() -> Unit>(1) }
    NuruRefreshBox(refreshing = refreshing, onRefresh = { reloadRef[0]?.let { refreshing = true; it() } }) {
        Column(
            Modifier.fillMaxSize().background(GrowPal.coolPaper).verticalScroll(rememberScrollState()),
        ) {
            // Navy hero
            Box(
                Modifier.fillMaxWidth().height(240.dp)
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
            ) {
                Box(Modifier.matchParentSize().background(GrowPal.heroGradient))
                Box(Modifier.matchParentSize().background(GrowPal.scrimNavy.copy(alpha = 0.55f)))
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth()
                        .padding(horizontal = 24.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.40f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(GrowPal.gold)
                            .clickable { composing = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, null, tint = GrowPal.navyDeep, modifier = Modifier.size(18.dp)) }
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("PRAY FOR ONE ANOTHER", style = gInter(11, FontWeight.Medium, 1.8f), color = GrowPal.gold)
                    Text("Carry one another", style = gSerif(24, FontWeight.SemiBold), color = Color.White)
                    Text(
                        "“Carry each other's burdens, and in this way you will fulfill the law of Christ.” — Galatians 6:2",
                        style = gInter(12), color = Color.White.copy(alpha = 0.55f), maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Body
            AsyncContent(key = sort, loading = { ListSkeleton(rows = 5) }, load = {
                // The `finally` also parks the pull-to-refresh spinner above.
                try { Net.client.api.prayerWall(sort).data } finally { refreshing = false }
            }) { posts: List<PrayerWallPost>, reload ->
                reloadRef[0] = reload
                val scope = rememberCoroutineScope()
                val player = remember { VoicePlayer() }
                DisposableEffect(Unit) { onDispose { player.release() } }
                Column(
                    Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Sort pills
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortPill("Latest", "latest", sort) { sort = it }
                        SortPill("Most prayed", "prayed", sort) { sort = it }
                    }
                    if (posts.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Text("No prayer requests yet.", style = gInter(13), color = GrowPal.ink600)
                        }
                    } else {
                        posts.forEach { p ->
                            PrayerCard(
                                p,
                                player = player,
                                onOpen = { onOpenPost(p.postId) },
                                onPray = {
                                    scope.launch {
                                        try {
                                            Net.client.api.prayerWallReact(p.postId, ReactBody("🙏")); reload()
                                        } catch (_: Exception) {}
                                    }
                                },
                            )
                        }
                    }
                }

                if (composing) ComposeSheet(scope, onDismiss = { composing = false }, onPosted = { reload() })
            }
        }
    }
}

@Composable
private fun SortPill(label: String, key: String, sort: String, onSelect: (String) -> Unit) {
    val on = sort == key
    Box(
        Modifier.clip(Capsule)
            .background(if (on) GrowPal.goldChipBg else GrowPal.white)
            .border(1.dp, if (on) GrowPal.gold else GrowPal.border, Capsule)
            .clickable { onSelect(key) }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = gInter(12, FontWeight.Bold), color = if (on) GrowPal.navyDeep else GrowPal.ink600)
    }
}

@Composable
private fun PrayerCard(p: PrayerWallPost, player: VoicePlayer, onOpen: () -> Unit, onPray: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GrowPal.white)
            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
            .clickable { onOpen() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(p.authorName, p.authorAvatar, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(p.authorName, style = gInter(12, FontWeight.Bold), color = GrowPal.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relTime(p.createdAt), style = gInter(11), color = GrowPal.ink400)
            }
            if (p.isAnswered) AnsweredChip()
        }
        p.title?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = gInter(16, FontWeight.Medium), color = GrowPal.ink, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            p.body,
            style = gInter(13).copy(lineHeight = 18.sp),
            color = GrowPal.ink600,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Voice prayer — playable waveform (audio_url + audio_waveform on the wire).
        if (!p.audioUrl.isNullOrBlank()) {
            val playing = player.playingId == p.postId
            Row(
                Modifier.padding(top = 8.dp).clip(Capsule).background(GrowPal.surface)
                    .border(1.dp, GrowPal.border, Capsule)
                    .clickable { player.toggle(p.postId, p.audioUrl) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (playing) "Pause voice prayer" else "Play voice prayer",
                    tint = GrowPal.navyDeep,
                    modifier = Modifier.size(16.dp),
                )
                WaveformBars(
                    levels = p.audioWaveform.orEmpty(),
                    color = GrowPal.navyMid.copy(alpha = 0.35f),
                    playedFraction = if (playing) player.progress else 0f,
                    playedColor = GrowPal.gold,
                    maxBarHeight = 18.dp,
                )
                if (playing && player.durationSec > 0) {
                    Text(voiceClock(player.durationSec), style = gInter(10, FontWeight.SemiBold), color = GrowPal.ink400)
                }
            }
        }
        Row(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.clip(Capsule)
                    .background(if (p.iPrayed) GrowPal.goldChipBg else GrowPal.surface)
                    .border(1.dp, if (p.iPrayed) GrowPal.gold else GrowPal.border, Capsule)
                    .clickable { onPray() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🙏", fontSize = 15.sp)
                    Text(
                        if (p.prayCount > 0) "${p.prayCount} praying" else "Pray",
                        style = gInter(12, FontWeight.Bold),
                        color = if (p.iPrayed) GrowPal.navyDeep else GrowPal.ink600,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.AutoMirrored.Filled.Message, null, tint = GrowPal.ink400, modifier = Modifier.size(14.dp))
                Text((p.commentCount ?: 0).toString(), style = gInter(11), color = GrowPal.ink400)
            }
        }
    }
}

@Composable
internal fun Avatar(name: String, url: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(GrowPal.tintBlue),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(
                initials(name),
                style = gInter((size.value * 0.4f).toInt().coerceAtLeast(9), FontWeight.SemiBold),
                color = GrowPal.navyMid,
            )
        }
    }
}

@Composable
private fun AnsweredChip() {
    Box(
        Modifier.clip(Capsule).background(GrowPal.successBg).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.CheckCircle, null, tint = GrowPal.successText, modifier = Modifier.size(11.dp))
            Text("Answered", style = gInter(11, FontWeight.Medium), color = GrowPal.successText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeSheet(scope: CoroutineScope, onDismiss: () -> Unit, onPosted: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = GrowPal.white) {
        val context = LocalContext.current
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var posting by remember { mutableStateOf(false) }
        // Voice prayer attachment — record, keep the file + its waveform, post.
        val recorder = remember { VoiceRecorder() }
        var attached by remember { mutableStateOf<File?>(null) }
        var attachedWave by remember { mutableStateOf<List<Int>>(emptyList()) }
        var attachedDur by remember { mutableStateOf(0) }
        DisposableEffect(Unit) { onDispose { recorder.cancel() } }
        val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) recorder.start(context)
        }
        fun startRecording() {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                recorder.start(context)
            } else {
                askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        Column(
            Modifier.imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Share a prayer", style = gSerif(18, FontWeight.SemiBold), color = GrowPal.navy)
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GrowPal.coolPaper)
                    .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                if (title.isBlank()) Text("Title (optional)", style = gInter(14), color = GrowPal.ink400)
                BasicTextField(
                    title, { title = it },
                    textStyle = gInter(14).copy(color = GrowPal.ink),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GrowPal.coolPaper)
                    .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                if (body.isBlank()) Text("Share what's on your heart…", style = gInter(14), color = GrowPal.ink400)
                BasicTextField(
                    body, { body = it },
                    textStyle = gInter(14).copy(color = GrowPal.ink),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                )
            }
            // Voice row — idle "Add voice" / live recording wave / attached preview.
            when {
                recorder.isRecording -> Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GrowPal.coolPaper)
                        .border(1.dp, GrowPal.gold, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val pulse by rememberInfiniteTransition(label = "prec").animateFloat(
                        initialValue = 0.35f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "precDot",
                    )
                    Box(Modifier.size(10.dp).clip(Capsule).background(GrowPal.danger.copy(alpha = pulse)))
                    Text(voiceClock(recorder.elapsedSec), style = gInter(12, FontWeight.Bold), color = GrowPal.navy)
                    LiveWave(recorder.levels.toList(), color = GrowPal.gold, modifier = Modifier.weight(1f), maxBarHeight = 24.dp)
                    Box(
                        Modifier.size(36.dp).clip(Capsule).background(GrowPal.white).border(1.dp, GrowPal.border, Capsule)
                            .clickable { recorder.cancel() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, "Discard recording", tint = GrowPal.ink400, modifier = Modifier.size(15.dp)) }
                    Box(
                        Modifier.size(36.dp).clip(Capsule).background(GrowPal.gold)
                            .clickable {
                                val f = recorder.stop()
                                if (f != null) {
                                    attached = f
                                    attachedWave = recorder.waveformFor()
                                    attachedDur = recorder.elapsedSec.coerceAtLeast(1)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, "Keep recording", tint = GrowPal.navyDeep, modifier = Modifier.size(16.dp)) }
                }
                attached != null -> Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GrowPal.goldChipBg)
                        .border(1.dp, GrowPal.gold, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Mic, null, tint = GrowPal.goldChipText, modifier = Modifier.size(16.dp))
                    WaveformBars(
                        levels = attachedWave,
                        color = GrowPal.goldLo.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                        maxBarHeight = 20.dp,
                    )
                    Text(voiceClock(attachedDur), style = gInter(11, FontWeight.Bold), color = GrowPal.goldChipText)
                    Box(
                        Modifier.size(28.dp).clip(Capsule).background(GrowPal.white).border(1.dp, GrowPal.border, Capsule)
                            .clickable {
                                attached?.delete()
                                attached = null
                                attachedWave = emptyList()
                                attachedDur = 0
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, "Remove voice prayer", tint = GrowPal.ink400, modifier = Modifier.size(13.dp)) }
                }
                else -> Row(
                    Modifier.clip(Capsule).background(GrowPal.coolPaper).border(1.dp, GrowPal.border, Capsule)
                        .clickable { startRecording() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Mic, null, tint = GrowPal.navyDeep, modifier = Modifier.size(15.dp))
                    Text("Add voice", style = gInter(12, FontWeight.Bold), color = GrowPal.navyDeep)
                }
            }
            val canPost = (body.isNotBlank() || attached != null) && !posting
            Box(
                Modifier.fillMaxWidth().height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canPost) GrowPal.navyDeep else GrowPal.navyDeep.copy(alpha = 0.5f))
                    .clickable(enabled = canPost) {
                        val t = title
                        val b = body
                        val f = attached
                        val wave = attachedWave
                        val pid = UUID.randomUUID().toString()
                        posting = true
                        scope.launch {
                            try {
                                var url: String? = null
                                if (f != null) {
                                    url = withContext(Dispatchers.IO) {
                                        val part = MultipartBody.Part.createFormData("file", f.name, f.readBytes().toRequestBody("audio/mp4".toMediaTypeOrNull()))
                                        Net.client.api.uploadVoiceNote(part).url.ifBlank { null }
                                    }
                                    if (url == null) return@launch   // upload failed — keep the sheet open to retry
                                }
                                Net.client.api.createPrayerWallPost(
                                    CreatePrayerBody(
                                        postId = pid,
                                        title = t.ifBlank { null },
                                        body = b.trim().ifBlank { "Voice prayer" },
                                        clientMutationId = UUID.randomUUID().toString(),
                                        audioUrl = url,
                                        audioWaveform = if (url != null && wave.isNotEmpty()) wave else null,
                                    ),
                                )
                                // Warm toast — the server accepted the post (key = its uuid).
                                CelebrationCenter.fire(Moment("wallpost-$pid", "Your prayer is on the wall", "Your cell is standing with you 🙏", confetti = false))
                                onPosted()
                                onDismiss()
                            } catch (_: Exception) {
                            } finally {
                                posting = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (posting) "Posting…" else "Post to wall", style = gInter(16, FontWeight.Medium), color = Color.White)
            }
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
