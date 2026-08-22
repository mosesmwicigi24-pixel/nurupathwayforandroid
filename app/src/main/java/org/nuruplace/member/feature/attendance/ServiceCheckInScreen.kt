// Church service attendance — the member arrives, opens the app, and scans the
// QR on the sanctuary screen. One scan carries BOTH the service id and its HMAC
// (`nuru-service:<service_id>:<token>`), so there is no "pick your service"
// step: point the camera and you're registered.
//
// After a successful scan the member confirms the contact details that go on the
// attendance record (name, phone, email — prefilled from their profile, editable
// because the phone on file is often not the one they carry), then submits. The
// server records the time of attending and returns the updated streak, which we
// show as the reward for showing up.
//
// Port parity: iOS ServiceCheckInView.swift.
package org.nuruplace.member.feature.attendance

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.AttendanceStreak
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ServiceCheckInBody
import org.nuruplace.member.data.net.ServiceCheckInResult
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.util.UUID
import java.util.concurrent.Executors

/** What a scanned church-service QR carries. */
data class ServiceScan(val serviceId: String, val scanToken: String)

/**
 * What the scanner recognized. The projected sanctuary code and a per-service
 * link identify a service directly; the standing door poster (`/jc/<code>`,
 * one code forever per congregation) names only the congregation — the SERVER
 * decides which service it means at scan time (GET /join/congregation/{code}).
 */
sealed interface ScannedServiceCode {
    data class Service(val scan: ServiceScan) : ScannedServiceCode
    data class StandingCode(val code: String) : ScannedServiceCode
}

/**
 * Parse every form a Nuru service QR ships in. Returns null for any other QR
 * so the scanner ignores unrelated codes instead of posting junk to the
 * server. Mirrors `parseServiceQrPayload` in the backend attendance module —
 * the legacy `nuru-service:` form MUST keep working: it is what the portal
 * still projects on the sanctuary screen.
 */
fun parseServiceQr(raw: String): ScannedServiceCode? {
    val text = raw.trim()

    // Legacy projected form: nuru-service:<service_id>:<token>
    val parts = text.split(":")
    if (parts.size == 3 && parts[0] == "nuru-service" && parts[1].isNotBlank() && parts[2].isNotBlank()) {
        return ScannedServiceCode.Service(ServiceScan(parts[1], parts[2]))
    }

    // URL forms — accepted from any host so a staging poster scans in a dev
    // build; the payload alone carries everything the flow needs. java.net.URI
    // rather than android.net.Uri so the grammar is testable on the plain JVM
    // and identical on every platform.
    val uri = runCatching { java.net.URI(text) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val seg = uri.path?.split("/")?.filter { it.isNotEmpty() } ?: return null

    // Per-service link: /j/<service_id>/<token>
    if (seg.size == 3 && seg[0] == "j" && seg[1].isNotBlank() && seg[2].isNotBlank()) {
        return ScannedServiceCode.Service(ServiceScan(seg[1], seg[2]))
    }
    // Standing poster: /jc/<code> (codes are 64 hex; 16 is the server's floor)
    if (seg.size == 2 && seg[0] == "jc" && seg[1].length >= 16) {
        return ScannedServiceCode.StandingCode(seg[1])
    }
    return null
}

/** "Sunday, 9:00 AM" from the API's ISO stamp — null if it doesn't parse,
 *  and the caller falls back to wording with no time in it. */
fun friendlyServiceTime(iso: String): String? = runCatching {
    java.time.Instant.parse(iso)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, h:mm a"))
}.getOrNull()

private enum class Phase { SCANNING, REGISTERING, SUBMITTING, DONE }

@Composable
fun ServiceCheckInScreen(
    memberName: String,
    memberPhone: String,
    memberEmail: String?,
    onBack: () -> Unit,
    onSeeStreak: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(Phase.SCANNING) }
    var scan by remember { mutableStateOf<ServiceScan?>(null) }
    var result by remember { mutableStateOf<ServiceCheckInResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // The registration form. Prefilled from the profile so the common case is
    // one tap, editable because the number on file is often not the one they carry.
    var name by remember { mutableStateOf(memberName) }
    var phone by remember { mutableStateOf(memberPhone) }
    var email by remember { mutableStateOf(memberEmail.orEmpty()) }

    // Minted once per captured code so a retry after a network failure is the
    // SAME scan to the server — a replay, not a second attendance row (§3.6).
    var scanId by remember { mutableStateOf(UUID.randomUUID().toString().lowercase()) }

    // The standing poster's "not yet" answer (or a resolve failure), shown in
    // place of the scan caption for a few seconds; the scanner stays live.
    var standingNote by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(false) }

    fun showStandingNote(text: String) {
        standingNote = text
        scope.launch {
            kotlinx.coroutines.delay(6_000)
            if (standingNote == text) standingNote = null
        }
    }

    fun onCode(raw: String) {
        if (phase != Phase.SCANNING || resolving || standingNote != null) return
        when (val parsed = parseServiceQr(raw)) {
            null -> return                            // not our code — keep scanning
            is ScannedServiceCode.Service -> {
                scan = parsed.scan
                scanId = UUID.randomUUID().toString().lowercase()
                phase = Phase.REGISTERING
            }
            is ScannedServiceCode.StandingCode -> {
                // The printed door poster: one code forever, resolved to
                // whatever service is open RIGHT NOW. Outside a window the
                // honest answer is "not yet", said kindly, with when to return.
                resolving = true
                scope.launch {
                    try {
                        val r = Net.client.api.resolveStandingCode(parsed.code)
                        val svc = r.service
                        if (r.open && svc != null) {
                            scan = ServiceScan(svc.serviceId, svc.scanToken)
                            scanId = UUID.randomUUID().toString().lowercase()
                            phase = Phase.REGISTERING
                        } else {
                            val next = r.next
                            showStandingNote(
                                if (next != null && friendlyServiceTime(next.startsAt) != null) {
                                    "Check-in isn't open right now. Join us for ${next.title}, ${friendlyServiceTime(next.startsAt)}."
                                } else {
                                    "Check-in isn't open right now — scan again when you arrive for a service."
                                },
                            )
                        }
                    } catch (ex: Exception) {
                        showStandingNote(ApiException.message(ex))
                    } finally {
                        resolving = false
                    }
                }
            }
        }
    }

    fun submit() {
        val s = scan ?: return
        phase = Phase.SUBMITTING
        error = null
        scope.launch {
            try {
                result = Net.client.api.checkInService(
                    s.serviceId,
                    ServiceCheckInBody(
                        clientScanId = scanId,
                        scanToken = s.scanToken,
                        fullName = name.trim().ifBlank { null },
                        phoneNumber = phone.trim().ifBlank { null },
                        email = email.trim().ifBlank { null },
                    ),
                )
                phase = Phase.DONE
            } catch (ex: Exception) {
                error = ApiException.message(ex)
                phase = Phase.REGISTERING   // same scanId — retrying is a replay
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuru.navyDeep)) {
        ScreenHeader(
            title = when (phase) {
                Phase.DONE -> "You're checked in"
                Phase.SCANNING -> "Check in"
                else -> "Your details"
            },
            kicker = when (phase) {
                Phase.DONE -> "SERVICE ATTENDANCE"
                Phase.SCANNING -> "Scan the service QR code"
                else -> "Confirm and register"
            },
            onBack = onBack,
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                phase == Phase.DONE && result != null ->
                    CheckedInPanel(result!!, onSeeStreak = onSeeStreak, onDone = onBack)

                phase == Phase.REGISTERING || phase == Phase.SUBMITTING ->
                    RegistrationPanel(
                        name = name, onName = { name = it },
                        phone = phone, onPhone = { phone = it },
                        email = email, onEmail = { email = it },
                        error = error,
                        submitting = phase == Phase.SUBMITTING,
                        onSubmit = ::submit,
                        onRescan = { scan = null; error = null; phase = Phase.SCANNING },
                    )

                granted -> CameraPreview(onCode = ::onCode, note = standingNote)

                else -> Column(
                    Modifier.fillMaxSize().padding(Spacing.screen),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Camera access is needed to scan the service check-in code.",
                        style = NuruType.body, color = Nuru.onNavyDim,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton("Allow camera", onClick = { ask.launch(Manifest.permission.CAMERA) })
                }
            }
        }
    }
}

// ---------------- Registration ----------------

@Composable
private fun RegistrationPanel(
    name: String, onName: (String) -> Unit,
    phone: String, onPhone: (String) -> Unit,
    email: String, onEmail: (String) -> Unit,
    error: String?,
    submitting: Boolean,
    onSubmit: () -> Unit,
    onRescan: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
    ) {
        Text(
            "These go on today's attendance record.",
            style = NuruType.body, color = Nuru.onNavyDim,
        )
        Spacer(Modifier.height(Spacing.lg))

        NavyField("Full name", name, onName)
        Spacer(Modifier.height(Spacing.md))
        NavyField("Phone number", phone, onPhone, keyboard = KeyboardType.Phone)
        Spacer(Modifier.height(Spacing.md))
        NavyField("Email (optional)", email, onEmail, keyboard = KeyboardType.Email)

        if (error != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(error, style = NuruType.body, color = Nuru.goldLight)
        }

        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(
            "Check in",
            onClick = onSubmit,
            enabled = name.isNotBlank() && phone.isNotBlank(),
            loading = submitting,
        )
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton("Scan a different code", onClick = onRescan, enabled = !submitting)
    }
}

@Composable
private fun NavyField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, style = NuruType.label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Nuru.onNavy,
            unfocusedTextColor = Nuru.onNavy,
            focusedBorderColor = Nuru.gold,
            unfocusedBorderColor = Nuru.onNavyFaint,
            focusedLabelColor = Nuru.gold,
            unfocusedLabelColor = Nuru.onNavyDim,
            cursorColor = Nuru.gold,
        ),
    )
}

// ---------------- Success ----------------

@Composable
private fun CheckedInPanel(result: ServiceCheckInResult, onSeeStreak: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (result.duplicate) "Already checked in ✓" else "Checked in ✓",
            style = NuruType.title, color = Nuru.onNavy,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(result.serviceTitle, style = NuruType.body, color = Nuru.onNavyDim)
        Text(shortTime(result.attendedAt), style = NuruType.caption, color = Nuru.onNavyFaint)

        Spacer(Modifier.height(Spacing.xl))
        StreakSummary(result.streak)

        Spacer(Modifier.height(Spacing.xl))
        PrimaryButton("See my attendance", onClick = onSeeStreak)
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton("Done", onClick = onDone)
    }
}

/** Current run · longest · breaks · failures — the four numbers, one row of tiles. */
@Composable
fun StreakSummary(streak: AttendanceStreak) {
    Column(Modifier.fillMaxWidth()) {
        Text("YOUR ATTENDANCE", style = NuruType.sectionLabel, color = Nuru.gold)
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatTile("Streak", streak.currentStreak.toString(), Modifier.weight(1f))
            StatTile("Longest", streak.longestStreak.toString(), Modifier.weight(1f))
            StatTile("Breaks", streak.breaks.toString(), Modifier.weight(1f))
            StatTile("Missed", streak.totalMissed.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(streakNote(streak), style = NuruType.body, color = Nuru.onNavyDim)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Nuru.onNavy.copy(alpha = 0.08f))
            .border(1.dp, Nuru.onNavyFaint, RoundedCornerShape(14.dp))
            .padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = NuruType.title, color = Nuru.gold)
        Spacer(Modifier.height(2.dp))
        Text(label, style = NuruType.micro, color = Nuru.onNavyDim)
    }
}

/** Plain-language reading of the streak — the numbers alone don't pastor anyone. */
fun streakNote(s: AttendanceStreak): String = when (s.status) {
    "new" -> "This is your first check-in. Your streak starts here."
    "active" -> if (s.currentStreak == 1) {
        "You're on the board — one service in a row."
    } else {
        "You've been here ${s.currentStreak} services in a row."
    }
    "at_risk" -> "You missed the last service. Come this week and your streak restarts."
    "broken" -> "You've missed ${s.currentMissRun} services in a row. Today is a good day to come back."
    else -> ""
}

/** "09:14" out of an ISO-8601 instant, without pulling in a date library. */
fun shortTime(iso: String): String {
    val t = iso.substringAfter('T', "")
    return if (t.length >= 5) t.substring(0, 5) else iso
}

// ---------------- Camera ----------------

@Composable
private fun CameraPreview(onCode: (String) -> Unit, note: String? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { proxy -> scan(proxy, scanner, onCode) }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )
        // The aiming window — gold frame, same language as the event scanner.
        Box(
            Modifier
                .size(260.dp)
                .border(3.dp, Nuru.gold, RoundedCornerShape(28.dp)),
        )
        Text(
            note ?: "Point at the check-in code on the screen",
            style = NuruType.caption,
            color = if (note == null) Nuru.onNavyDim else Nuru.gold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 360.dp).widthIn(max = 300.dp),
        )
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun scan(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onCode: (String) -> Unit,
) {
    val media = proxy.image
    if (media == null) { proxy.close(); return }
    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { codes ->
            codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let { onCode(it) }
        }
        .addOnCompleteListener { proxy.close() }
}
