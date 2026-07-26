// Nuru Live — the "Go Live" setup sheet (L3). Title + kind (video/audio) +
// scope (church/my cell), then POST /live/streams. Camera/mic permissions are
// requested and confirmed HERE, before the mint call — a hard denial must
// never leave an orphaned "live" row on the server with nothing actually
// publishing to it (owner ask, see docs/LIVE_STREAMING.md).
package org.nuruplace.member.feature.live

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.CreateLiveStreamBody
import org.nuruplace.member.data.net.CreatedLiveStream
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing
import retrofit2.HttpException

/**
 * @param lockedScope when non-null ("church" | "cell"), the picker is hidden
 * and the sheet always starts that scope — the cell entry point (Cell Info
 * screen) forces "cell" this way; Home's entry point passes null so the
 * member picks between whichever of church/my-cell they're eligible for.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GoLiveSetupSheet(
    me: MeResponse?,
    lockedScope: String? = null,
    onDismiss: () -> Unit,
    onStarted: (created: CreatedLiveStream, title: String, kind: String, scope: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val churchEligible = lockedScope == null && isChurchLiveEligible(me)
    val cellEligible = (lockedScope == "cell") || (lockedScope == null && isCellLiveEligible(me))
    val cellId = me?.profile?.cellGroupId

    var selectedScope by remember {
        mutableStateOf(lockedScope ?: if (churchEligible) "church" else "cell")
    }
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("video") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var conflict by remember { mutableStateOf(false) }

    // Permanently-denied permission state — mirrors CheckInScannerScreen's
    // exact pattern (askedOnce + shouldShowRequestPermissionRationale).
    var askedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val activity = context as? Activity

    fun requiredPermissions(): Array<String> =
        if (kind == "video") arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        else arrayOf(Manifest.permission.RECORD_AUDIO)

    fun hasAllPermissions(): Boolean = requiredPermissions().all {
        androidx.core.content.ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun submit() {
        error = null
        conflict = false
        submitting = true
        scope.launch {
            val body = CreateLiveStreamBody(
                scope = selectedScope,
                cellId = if (selectedScope == "cell") cellId else null,
                title = title.trim(),
                kind = kind,
            )
            try {
                val created = Net.client.api.postLiveStreams(body)
                submitting = false
                onStarted(created, title.trim(), kind, selectedScope)
            } catch (e: Exception) {
                submitting = false
                val code = (e as? HttpException)?.code()
                when (code) {
                    // Another broadcast is already running for this scope — a
                    // friendly, expected state, not an error dialog. Keep the
                    // sheet open so they can pick a different scope or cancel.
                    409 -> conflict = true
                    // Shouldn't happen (client already gates on live:go), but
                    // the server is the real authority — handle gracefully.
                    403 -> error = "You don't have permission to go live right now."
                    else -> error = ApiException.message(e, context)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        askedOnce = true
        if (results.values.all { it }) {
            submit()
        } else {
            val canAskAgain = requiredPermissions().any {
                activity?.shouldShowRequestPermissionRationale(it) ?: true
            }
            permanentlyDenied = !canAskAgain
        }
    }

    fun startTapped() {
        if (title.trim().length !in 1..200) {
            error = "Give the stream a title (1–200 characters)."
            return
        }
        error = null
        permanentlyDenied = false
        // Permission check/request happens BEFORE the mint call — a denial
        // here must never create an orphaned "live" row server-side.
        if (hasAllPermissions()) {
            submit()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Go Live", style = NuruType.title, color = Nuru.ink)
                Spacer(Modifier.weight(1f))
                Box2Close(onDismiss)
            }
            Spacer(Modifier.height(Spacing.md))

            if (permanentlyDenied) {
                PermissionDeniedBlock(kind, context)
                return@Column
            }

            Text("Title".uppercase(), style = NuruType.micro, color = Nuru.ink400)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 200) title = it },
                placeholder = { Text("What are we watching?", style = NuruType.bodyLg) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(Radii.control),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Nuru.inputBg,
                    unfocusedContainerColor = Nuru.inputBg,
                    focusedBorderColor = Nuru.gold,
                    unfocusedBorderColor = Nuru.border,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.base))
            Text("Kind".uppercase(), style = NuruType.micro, color = Nuru.ink400)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                KindChip("Video", Icons.Filled.Videocam, kind == "video") { kind = "video" }
                KindChip("Audio only", Icons.Filled.GraphicEq, kind == "audio") { kind = "audio" }
            }

            // Scope — hidden entirely when locked (Cell Info's entry point)
            // or when only one option is actually eligible; a picker only
            // appears when the member can genuinely choose between the two.
            if (lockedScope == null && churchEligible && cellEligible) {
                Spacer(Modifier.height(Spacing.base))
                Text("Where".uppercase(), style = NuruType.micro, color = Nuru.ink400)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    KindChip("Church", Icons.Filled.Videocam, selectedScope == "church") { selectedScope = "church" }
                    KindChip("My cell", Icons.Filled.Videocam, selectedScope == "cell") { selectedScope = "cell" }
                }
            }

            if (conflict) {
                Spacer(Modifier.height(Spacing.base))
                Text(
                    "Someone is already live there. Try a different scope, or check back shortly.",
                    style = NuruType.caption, color = Nuru.danger,
                )
            }
            error?.let {
                Spacer(Modifier.height(Spacing.base))
                Text(it, style = NuruType.caption, color = Nuru.danger)
            }

            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                label = "Start",
                onClick = ::startTapped,
                loading = submitting,
                enabled = !submitting,
            )
        }
    }
}

@Composable
private fun Box2Close(onDismiss: () -> Unit) {
    Row(
        Modifier.size(32.dp).clip(CircleShape).background(Nuru.inputBg).clickable { onDismiss() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Nuru.ink600, modifier = Modifier.size(15.dp)) }
}

@Composable
private fun KindChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Nuru.navyDeep else Nuru.inputBg)
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Nuru.onNavy else Nuru.ink600, modifier = Modifier.size(16.dp))
        Text(label, style = NuruType.cardCta, color = if (selected) Nuru.onNavy else Nuru.ink600, fontWeight = FontWeight.SemiBold)
    }
}

/** Camera/mic permanently denied ("don't ask again") — Settings deep-link,
 *  never a dead end. Mirrors CheckInScannerScreen's DeniedView. */
@Composable
private fun PermissionDeniedBlock(kind: String, context: android.content.Context) {
    val what = if (kind == "video") "camera and microphone" else "microphone"
    Column {
        Text("Access needed", style = NuruType.cardTitle, color = Nuru.ink)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Nuru needs $what access to go live. Turn it on in Settings and come back.",
            style = NuruType.body, color = Nuru.ink600,
        )
        Spacer(Modifier.height(Spacing.base))
        PrimaryButton(
            "Open Settings",
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
        )
    }
}
