// Wave 2 — a discipler's voice on the lesson + cell reading presence.
// Port of iOS VoiceNoteCard.swift.
//   • VoiceNoteCard      — "A word from <leader>" atop the lesson: avatar,
//     play/pause (VoicePlayer/MediaPlayer), thin gold progress line.
//   • VoiceNoteLeaderRow — Instructor+ affordance: record up to 5 minutes
//     (VoiceRecorder m4a), listen back, then share with the congregation.
//   • CellPresenceLine   — "Leah and 2 others from your cell opened a lesson
//     this week." on the Pathway hub; renders nothing when nobody has.
package org.nuruplace.member.feature.pathway

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.nuruplace.member.data.net.ModuleVoiceNote
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.VoiceNoteBody
import org.nuruplace.member.feature.community.Avatar
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.util.VoicePlayer
import org.nuruplace.member.util.VoiceRecorder

private val VnGold = Color(0xFFC89B3C)
private val VnBg = Color(0xFFFFF8E6)

@Composable
fun VoiceNoteCard(note: ModuleVoiceNote) {
    val player = remember { VoicePlayer() }
    val firstName = note.authorName.substringBefore(' ').ifBlank { note.authorName }
    val playing = player.playingId == "voice-note"

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(VnBg)
            .border(1.dp, VnGold.copy(alpha = if (playing) 0.7f else 0.35f), RoundedCornerShape(16.dp))
            .clickable { player.toggle("voice-note", note.audioUrl) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = note.authorName, url = note.avatarUrl, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "A WORD FROM ${firstName.uppercase()}",
                    style = NuruType.micro, color = Nuru.eyebrow,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp,
                )
                Text(
                    "Voice note · ${maxOf(0, note.durationSec / 60)}m ${note.durationSec % 60}s",
                    style = NuruType.caption, color = Nuru.navy, fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(VnGold),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Nuru.navy, modifier = Modifier.size(18.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(VnGold.copy(alpha = 0.18f))) {
            Box(
                Modifier.fillMaxWidth(if (playing) player.progress.coerceIn(0f, 1f) else 0f)
                    .height(3.dp).clip(RoundedCornerShape(2.dp)).background(VnGold),
            )
        }
    }
}

@Composable
fun VoiceNoteLeaderRow(moduleId: String, existing: ModuleVoiceNote?, onShared: (ModuleVoiceNote) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(VnBg)
            .border(1.dp, VnGold.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = Nuru.eyebrow, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (existing == null) "Leave a word for your flock" else "Re-record your word",
            style = NuruType.caption, color = Nuru.eyebrow, fontWeight = FontWeight.SemiBold,
        )
    }
    if (open) {
        VoiceRecordDialog(moduleId = moduleId, existing = existing, onDismiss = { open = false }, onShared = {
            open = false
            onShared(it)
        })
    }
}

@Composable
private fun VoiceRecordDialog(
    moduleId: String,
    existing: ModuleVoiceNote?,
    onDismiss: () -> Unit,
    onShared: (ModuleVoiceNote) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder() }
    val preview = remember { VoicePlayer() }
    var recordedFile by remember { mutableStateOf<java.io.File?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recorder.start(ctx) else error = "Allow microphone access to record."
    }

    // Recorder hard-stop at 5 minutes (the server caps duration_sec at 300).
    LaunchedEffect(recorder.isRecording, recorder.elapsedSec) {
        if (recorder.isRecording && recorder.elapsedSec >= 300) recordedFile = recorder.stop()
    }

    AlertDialog(
        onDismissRequest = { if (!uploading) { recorder.stop(); preview.stop(); onDismiss() } },
        containerColor = Color.White,
        title = {
            Text(
                "A word for your flock",
                style = NuruType.rowTitle, color = Nuru.navy, fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    if (existing == null)
                        "Everyone in your congregation will hear this at the top of the lesson. Up to five minutes."
                    else "This replaces your current word on this module.",
                    style = NuruType.caption, color = Nuru.ink600,
                )
                when {
                    recorder.isRecording -> {
                        Text(
                            "%d:%02d".format(recorder.elapsedSec / 60, recorder.elapsedSec % 60),
                            style = NuruType.rowTitle.copy(fontSize = 34.sp), color = Nuru.navy,
                        )
                        VnPill(Icons.Filled.Stop, "Stop", Color(0xFFB3261E)) { recordedFile = recorder.stop() }
                    }
                    recordedFile != null -> {
                        Text(
                            "%d:%02d recorded".format(recorder.elapsedSec / 60, recorder.elapsedSec % 60),
                            style = NuruType.caption, color = Nuru.navy, fontWeight = FontWeight.SemiBold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            VnPill(
                                if (preview.playingId == "preview") Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "Listen back", Nuru.navy,
                            ) { recordedFile?.let { preview.toggle("preview", it.absolutePath) } }
                            VnPill(Icons.Filled.Mic, "Redo", Nuru.navy) {
                                preview.stop(); recordedFile = null; askMic.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    else -> VnPill(Icons.Filled.Mic, "Start recording", Nuru.navy) {
                        error = null
                        askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                error?.let { Text(it, style = NuruType.micro, color = Color(0xFFB3261E)) }
            }
        },
        confirmButton = {
            if (recordedFile != null) {
                TextButton(enabled = !uploading, onClick = {
                    val f = recordedFile ?: return@TextButton
                    uploading = true
                    scope.launch {
                        runCatching {
                            val part = MultipartBody.Part.createFormData(
                                "file", f.name, f.readBytes().toRequestBody("audio/mp4".toMediaTypeOrNull()),
                            )
                            val url = Net.client.api.uploadVoiceNote(part).url
                            val dur = recorder.elapsedSec.coerceIn(1, 300)
                            Net.client.api.setModuleVoiceNote(moduleId, VoiceNoteBody(url, dur))
                            url to dur
                        }.onSuccess { (url, dur) ->
                            val me = runCatching { Net.client.api.me().profile }.getOrNull()
                            onShared(
                                ModuleVoiceNote(
                                    authorName = me?.fullName ?: "Your discipler",
                                    avatarUrl = me?.avatarUrl,
                                    audioUrl = url,
                                    durationSec = dur,
                                ),
                            )
                        }.onFailure {
                            uploading = false
                            error = "Couldn't share the voice note. Try again."
                        }
                    }
                }) {
                    if (uploading) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = VnGold, strokeWidth = 2.dp)
                    } else {
                        Text("Share with your congregation", color = Nuru.navy, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !uploading, onClick = { recorder.stop(); preview.stop(); onDismiss() }) {
                Text("Cancel", color = Nuru.ink600)
            }
        },
    )
}

@Composable
private fun VnPill(icon: ImageVector, label: String, tint: Color, onTap: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
            .border(1.dp, Nuru.border, RoundedCornerShape(999.dp))
            .clickable { onTap() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = NuruType.caption, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/** "Leah, Sam and 1 other from your cell opened a lesson this week." */
@Composable
fun CellPresenceLine() {
    var line by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        line = runCatching {
            val p = Net.client.api.communityPresence()
            if (p.count <= 0) return@runCatching null
            val who = if (p.scope == "cell") "your cell" else "your congregation"
            val names = when {
                p.names.size > 1 -> p.names.dropLast(1).joinToString(", ") + " and " + p.names.last()
                else -> p.names.joinToString(", ")
            }
            val others = p.count - p.names.size
            when {
                others > 0 -> "$names and $others other${if (others == 1) "" else "s"} from $who opened a lesson this week."
                p.count == 1 -> "$names from $who opened a lesson this week. You're not walking alone."
                else -> "$names from $who opened a lesson this week."
            }
        }.getOrNull()
    }
    val text = line ?: return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(VnBg)
            .border(1.dp, VnGold.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Nuru.eyebrow, modifier = Modifier.size(14.dp).padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = NuruType.caption, color = Nuru.ink)
    }
}
