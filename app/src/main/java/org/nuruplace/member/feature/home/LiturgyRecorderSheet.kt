// Admin-only (Admin/SuperAdmin — backend requireRole("Admin"), narrower than
// the Instructor+ gate used for module discipler voice notes) recorder for
// the pastor's own liturgy voice, per band. Design constraint (owner brief,
// see LiturgyVoice.kt's header for the member-facing half of this feature):
// seven bands a day is 49 recordings a week — nobody sustains that. Mixed
// coverage is the PERMANENT NORMAL state, not a gap he's working toward, so
// this sheet is a plain per-band list ("his voice" vs "still synthesised")
// for HIS OWN reference when he opens it — never a completion meter,
// progress bar, streak, or "N bands still need recording" nudge. Nothing
// here is pushed onto the member-facing card.
//
// Reuses util/VoiceRecorder.kt (already the recorder behind chat + the
// prayer wall) for capture, then POSTs file + duration_sec together in ONE
// request (admin/liturgy/recordings/{band} — an upsert; calling it again for
// the same band replaces the recording), unlike the two-step
// me/media/audio -> modules/{id}/voice-note pattern. The record/wave/
// keep-discard state machine mirrors PrayerWallScreen.kt's ComposeSheet.
package org.nuruplace.member.feature.home

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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.nuruplace.member.data.net.LiturgyRecordingStatus
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.LiveWave
import org.nuruplace.member.ui.components.voiceClock
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.util.VoiceRecorder

/** Server clock order — used only as a display fallback (the server's own
 *  GET admin/liturgy/recordings response already arrives in this order); if
 *  the response shape ever changes this still renders every row it got. */
private val LITURGY_BAND_ORDER =
    listOf("sunrise", "morning", "midday", "afternoon", "evening", "night", "midnight")

private fun bandLabel(band: String): String = when (band) {
    "sunrise" -> "Sunrise"
    "morning" -> "Morning"
    "midday" -> "Midday"
    "afternoon" -> "Afternoon"
    "evening" -> "Evening"
    "night" -> "Night"
    "midnight" -> "Midnight"
    else -> band.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiturgyRecorderSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        val scope = rememberCoroutineScope()
        var rows by remember { mutableStateOf<List<LiturgyRecordingStatus>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var expandedBand by remember { mutableStateOf<String?>(null) }

        suspend fun reload() {
            rows = runCatching { Net.client.api.liturgyRecordings().data }.getOrDefault(rows)
            loading = false
        }
        LaunchedEffect(Unit) { reload() }

        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            Text("His Voice — the daily liturgy", style = NuruType.rowTitle.copy(fontSize = 17.sp), color = Nuru.navy)
            Spacer(Modifier.height(4.dp))
            Text(
                "Record any hour in your own voice. Every other hour keeps reading in Nuru's voice — that's expected, not a gap to fill.",
                style = NuruType.caption,
                color = Nuru.ink600,
            )
            Spacer(Modifier.height(14.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Nuru.gold, modifier = Modifier.size(22.dp))
                }
            } else {
                val ordered = LITURGY_BAND_ORDER.mapNotNull { band -> rows.firstOrNull { it.band == band } }
                    .ifEmpty { rows }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ordered.forEach { row ->
                        BandRow(
                            row = row,
                            expanded = expandedBand == row.band,
                            onToggleExpanded = { expandedBand = if (expandedBand == row.band) null else row.band },
                            onSaved = { scope.launch { reload() }; expandedBand = null },
                            onDeleted = { scope.launch { reload() } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BandRow(
    row: LiturgyRecordingStatus,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val hasRecording = !row.audioUrl.isNullOrBlank()
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Nuru.tintBlue.copy(alpha = if (expanded) 1f else 0.55f))
            .border(1.dp, if (hasRecording) Nuru.gold.copy(alpha = 0.5f) else Nuru.border, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bandLabel(row.band), style = NuruType.body.copy(fontWeight = FontWeight.SemiBold), color = Nuru.navy)
                Spacer(Modifier.height(2.dp))
                if (hasRecording) {
                    Text(
                        "His voice · ${voiceClock(row.durationSec ?: 0)}",
                        style = NuruType.micro.copy(fontWeight = FontWeight.Bold),
                        color = Nuru.goldChipText,
                    )
                } else {
                    Text("Still synthesised", style = NuruType.micro, color = Nuru.ink400)
                }
            }
            if (hasRecording) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color.White)
                        .border(1.dp, Nuru.border, CircleShape)
                        .clickable {
                            scope.launch {
                                runCatching { Net.client.api.deleteLiturgyRecording(row.band) }
                                onDeleted()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        "Delete the recording for ${bandLabel(row.band)}",
                        tint = Nuru.ink400,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (expanded) Nuru.navy else Nuru.gold)
                    .clickable { onToggleExpanded() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    if (expanded) "Close" else if (hasRecording) "Re-record" else "Record",
                    style = NuruType.micro.copy(fontWeight = FontWeight.Bold),
                    color = if (expanded) Color.White else Nuru.navyDeep,
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            BandRecorder(band = row.band, onSaved = onSaved)
        }
    }
}

@Composable
private fun BandRecorder(band: String, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder() }
    var attached by remember { mutableStateOf<File?>(null) }
    var attachedDur by remember { mutableStateOf(0) }
    var uploading by remember { mutableStateOf(false) }
    DisposableEffect(band) { onDispose { recorder.release() } }
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
    Column(Modifier.fillMaxWidth()) {
        when {
            recorder.isRecording -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .border(1.dp, Nuru.gold, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pulse by rememberInfiniteTransition(label = "litrec").animateFloat(
                    initialValue = 0.35f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "litrecDot",
                )
                Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFD64545).copy(alpha = pulse)))
                Text(voiceClock(recorder.elapsedSec), style = NuruType.micro.copy(fontWeight = FontWeight.Bold), color = Nuru.navy)
                LiveWave(recorder.levels.toList(), color = Nuru.gold, modifier = Modifier.weight(1f), maxBarHeight = 22.dp)
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Nuru.tintBlue)
                        .clickable { recorder.cancel() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Discard the recording", tint = Nuru.ink400, modifier = Modifier.size(13.dp)) }
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Nuru.gold)
                        .clickable {
                            val f = recorder.stop()
                            if (f != null) {
                                attached = f
                                attachedDur = recorder.elapsedSec.coerceAtLeast(1)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, "Keep the recording", tint = Nuru.navyDeep, modifier = Modifier.size(14.dp)) }
            }
            attached != null -> {
                val f = attached
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Nuru.goldChipBg)
                        .border(1.dp, Nuru.gold, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Mic, null, tint = Nuru.goldChipText, modifier = Modifier.size(15.dp))
                    Text(
                        voiceClock(attachedDur),
                        style = NuruType.micro.copy(fontWeight = FontWeight.Bold),
                        color = Nuru.goldChipText,
                        modifier = Modifier.weight(1f),
                    )
                    if (uploading) {
                        CircularProgressIndicator(color = Nuru.goldChipText, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(Color.White)
                                .border(1.dp, Nuru.border, CircleShape)
                                .clickable { f?.delete(); attached = null; attachedDur = 0 },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, "Discard this take", tint = Nuru.ink400, modifier = Modifier.size(12.dp)) }
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.navy)
                                .clickable {
                                    if (f == null) return@clickable
                                    uploading = true
                                    scope.launch {
                                        val ok = runCatching {
                                            val filePart = MultipartBody.Part.createFormData(
                                                "file", f.name, f.asRequestBody("audio/mp4".toMediaTypeOrNull()),
                                            )
                                            val durationPart = attachedDur.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                                            Net.client.api.uploadLiturgyRecording(band, filePart, durationPart)
                                        }.isSuccess
                                        uploading = false
                                        if (ok) {
                                            f.delete()
                                            attached = null
                                            attachedDur = 0
                                            onSaved()
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) { Text("Save", style = NuruType.micro.copy(fontWeight = FontWeight.Bold), color = Color.White) }
                    }
                }
            }
            else -> Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .border(1.dp, Nuru.border, RoundedCornerShape(999.dp))
                    .clickable { startRecording() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Mic, null, tint = Nuru.navy, modifier = Modifier.size(15.dp))
                Text("Tap to record", style = NuruType.micro.copy(fontWeight = FontWeight.Bold), color = Nuru.navy)
            }
        }
    }
}
