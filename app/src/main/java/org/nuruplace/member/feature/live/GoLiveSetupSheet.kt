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
import android.util.Log
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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
    // The old binary gate — still exactly what decides whether the personal-
    // cell fallback name (below) is worth fetching at all.
    val personalCellFallbackEligible = (lockedScope == "cell") || (lockedScope == null && isCellLiveEligible(me))
    val personalCellId = me?.profile?.cellGroupId

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

    // Audience picker (owner taste pass) — the single-cell fallback row's
    // label names the broadcaster's own cell by reusing the exact same fetch
    // CellInfoScreen already makes (GET /me/cell-summary); the wire contract
    // itself is unchanged (scope "church"|"cell" + cell_id) — this is purely
    // a nicer label than "My cell" for the cell they're actually about to go
    // live to.
    var cellName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(personalCellFallbackEligible) {
        if (personalCellFallbackEligible) {
            cellName = runCatching { Net.client.api.cellSummary().cell?.name }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    // 2026-07-31 parity fix — a leader who oversees SEVERAL cells couldn't
    // pick which one to broadcast to; this sheet only offered a binary
    // "Everyone" vs a single "My cell". Reuses the existing discipler-roster
    // fetch (GET /disciples, already scoped server-side to the caller's own
    // `leader_assignments` — never a new endpoint) to discover every DISTINCT
    // cell this member leads. Best-effort: a plain member's `/disciples` call
    // 403s (Instructor+ only) and that's fine — `deriveCellOptions` falls
    // back to the personal-membership single-cell option below when
    // `myLedCells` stays empty. Skipped entirely when `lockedScope` is set
    // (the Cell Info entry point forces scope=cell to the member's own cell
    // and never shows a picker at all, same as iOS's `forcedCell` case).
    var myLedCells by remember { mutableStateOf<List<LedCell>>(emptyList()) }
    // Gates the "you have no eligible scope" message below so a leader with
    // NO personal cell (only led cells, discovered async) doesn't see a
    // false-negative flash before the roster fetch resolves.
    var rosterLoaded by remember { mutableStateOf(lockedScope != null) }
    LaunchedEffect(lockedScope) {
        if (lockedScope == null) {
            myLedCells = runCatching { Net.client.api.disciples() }.getOrNull()?.data
                ?.let { ledCellsFromRoster(it) } ?: emptyList()
        }
        rosterLoaded = true
    }

    val cellOptions = deriveCellOptions(myLedCells, personalCellId, cellName)
    val cellSectionAvailable = lockedScope == "cell" || cellOptions.isNotEmpty()

    var selectedCellId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cellOptions) {
        selectedCellId = defaultSelectedCellId(cellOptions, selectedCellId)
    }

    // WebRTC warm-up (2026-07-31 host-process-death incident) — proactively
    // runs WebRTC's native init HERE, off the guest-join critical path and
    // well before this host's own broadcast even starts, rather than letting
    // the first-ever guest join be the FIRST time PeerConnectionFactory
    // touches native code. See LiveWebRtc.kt's [warmUp] doc: a failure here
    // is just a log line on a setup sheet the host hasn't submitted yet, not
    // a broadcast dying mid-stream. Fire-and-forget on a background
    // dispatcher — this sheet's own submit flow never awaits it.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            LiveWebRtc.warmUp(context).onFailure { e ->
                Log.w("GoLiveSetupSheet", "WebRTC warm-up failed — guest video will be unavailable this broadcast", e)
            }
        }
    }

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
                cellId = resolveCellIdForBody(selectedScope, selectedCellId, cellOptions, personalCellId),
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

            // Defensive: canGoLive() only checks the "live:go" grant, not
            // scope eligibility — a member could in principle hold that
            // grant with no cellGroupId and no staff role. Rather than let
            // Start silently send scope=cell with a null cell_id (a
            // confusing 422 VALIDATION_FAILED), say so plainly.
            if (lockedScope == null && !churchEligible && !cellSectionAvailable && rosterLoaded) {
                Text(
                    "There's no cell or church-wide scope available for your account yet. Ask an admin to check your Go Live access.",
                    style = NuruType.body, color = Nuru.ink600,
                )
                return@Column
            }

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

            // Audience — hidden entirely when locked (Cell Info's entry
            // point always forces "cell"); otherwise every scope this member
            // is actually eligible for gets its own row so it reads like a
            // real choice, not a cramped chip pair. The wire contract is
            // unchanged either way: scope "church"|"cell" (+ cell_id).
            if (lockedScope == null) {
                Spacer(Modifier.height(Spacing.base))
                Text("Audience".uppercase(), style = NuruType.micro, color = Nuru.ink400)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (churchEligible) {
                        AudienceOption(
                            title = "Everyone",
                            subtitle = "All connected members",
                            selected = selectedScope == "church",
                            onClick = { selectedScope = "church" },
                        )
                    }
                    if (cellSectionAvailable) {
                        AudienceOption(
                            title = "A cell / class",
                            subtitle = cellAudienceSubtitle(cellOptions),
                            selected = selectedScope == "cell",
                            onClick = { selectedScope = "cell" },
                        )
                    }
                }

                // The specific-cell picker — only surfaces once "A cell /
                // class" is the active audience AND there's an actual choice
                // to make (a leader of >1 cell). A single option keeps
                // today's plain AudienceOption subtitle above; this never
                // renders an empty or one-item picker (see deriveCellOptions
                // for why zero cells can't reach this branch at all — it
                // gates cellSectionAvailable itself). Makes the target
                // unmistakable BEFORE Start is tappable — a real congregation
                // is on the other end of "Everyone".
                if (selectedScope == "cell" && cellOptions.size > 1) {
                    Spacer(Modifier.height(Spacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        cellOptions.forEach { cell ->
                            CellChoiceRow(
                                name = cell.name,
                                selected = selectedCellId == cell.id,
                                onClick = { selectedCellId = cell.id },
                            )
                        }
                    }
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

/** A full-width, selectable "who sees this" row — the Audience picker.
 *  Mirrors KindChip's selected/unselected navy-vs-input-bg treatment but as
 *  a row (title + subtitle + a gold check dot) rather than a small chip,
 *  since each option needs room to say WHO it reaches. */
@Composable
private fun AudienceOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.control))
            .background(if (selected) Nuru.navyDeep else Nuru.inputBg)
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NuruType.cardCta, color = if (selected) Nuru.onNavy else Nuru.ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = NuruType.caption, color = if (selected) Nuru.onNavyDim else Nuru.ink400)
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (selected) Nuru.gold else Color.Transparent)
                .border(1.dp, if (selected) Nuru.gold else Nuru.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Nuru.homeNavy, modifier = Modifier.size(13.dp))
        }
    }
}

/** The "A cell / class" AudienceOption's subtitle — names the specific cell
 *  when there's exactly one, points at the picker below when there's a real
 *  choice to make, and falls back to the old generic copy only in the
 *  (unreachable in practice — see [deriveCellOptions]) zero-cell case. */
private fun cellAudienceSubtitle(cellOptions: List<LedCell>): String = when {
    cellOptions.size > 1 -> "Choose which cell below"
    cellOptions.size == 1 -> "Only ${cellOptions.first().name} sees this"
    else -> "Only your cell sees this"
}

/** One row in the specific-cell picker, shown only once a leader of several
 *  cells has chosen "A cell / class" as their audience — makes the exact
 *  broadcast target unmistakable before Start is even tappable. Gold-chip
 *  treatment (vs. AudienceOption's navy) on purpose: a visually distinct,
 *  nested "second choice" under the audience row it belongs to, using the
 *  same chip language already established elsewhere in this sheet. */
@Composable
private fun CellChoiceRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.control))
            .background(if (selected) Nuru.goldChipBg else Nuru.surface)
            .border(1.dp, if (selected) Nuru.gold.copy(alpha = 0.5f) else Nuru.border, RoundedCornerShape(Radii.control))
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Groups, contentDescription = null, tint = Nuru.goldChipText, modifier = Modifier.size(14.dp))
        Text(
            name,
            style = NuruType.cardCta,
            color = Nuru.ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Nuru.gold, modifier = Modifier.size(15.dp))
    }
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
