// Event check-in — a CameraX preview with an ML Kit QR analyzer. A decoded code
// posts POST /events/{id}/attendance (idempotent on clientScanId); replays and
// second scans come back duplicate=true. Camera-permission denial gets a
// friendly Settings pointer. Port of the iOS CheckInScannerView.
package org.nuruplace.member.feature.events

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.CheckInBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.util.UUID
import java.util.concurrent.Executors

@Composable
fun CheckInScannerScreen(eventId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var handled by remember { mutableStateOf(false) }

    fun submit(token: String) {
        if (handled) return
        handled = true
        scope.launch {
            try {
                val r = Net.client.api.checkInEvent(eventId, CheckInBody(UUID.randomUUID().toString().lowercase(), token))
                status = if (r.duplicate) "Already checked in ✓" else "Checked in ✓"
            } catch (ex: Exception) {
                status = org.nuruplace.member.data.net.ApiException.message(ex)
                handled = false   // allow a retry on failure
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuru.navyDeep)) {
        ScreenHeader("Check in", kicker = "Scan the QR code", onBack = onBack)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                status != null -> Text(status!!, style = NuruType.title, color = Nuru.onNavy, modifier = Modifier.padding(Spacing.screen))
                granted -> CameraPreview(onCode = ::submit)
                else -> Column(Modifier.padding(Spacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera access is needed to scan the check-in code.", style = NuruType.body, color = Nuru.onNavyDim)
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton("Allow camera", onClick = { ask.launch(Manifest.permission.CAMERA) })
                }
            }
        }
        if (status != null) {
            Box(Modifier.fillMaxWidth().padding(Spacing.screen)) {
                PrimaryButton("Done", onClick = onBack)
            }
        }
    }
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit) {
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
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun scan(proxy: ImageProxy, scanner: com.google.mlkit.vision.barcode.BarcodeScanner, onCode: (String) -> Unit) {
    val media = proxy.image
    if (media == null) { proxy.close(); return }
    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { codes ->
            codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let { onCode(it) }
        }
        .addOnCompleteListener { proxy.close() }
}
