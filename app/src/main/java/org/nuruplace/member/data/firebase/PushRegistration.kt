// Push registration — runs once inside the authed shell: ensures the notification
// channel, asks for POST_NOTIFICATIONS on Android 13+, fetches the current FCM
// token and registers it with the backend (POST /me/devices). No-ops when
// Firebase isn't configured or there's no backend session. Add-alongside.
package org.nuruplace.member.data.firebase

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import org.nuruplace.member.data.net.Net
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun PushRegistration() {
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* token registers regardless */ }

    LaunchedEffect(Unit) {
        if (!FirebaseAuthService.isConfigured(context)) return@LaunchedEffect
        if (!Net.client.vault.hasSession) return@LaunchedEffect
        NuruMessagingService.ensureChannel(context)

        // Android 13+ needs the runtime notification permission to DISPLAY pushes;
        // token registration proceeds either way so the server can target the device.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val token = runCatching { fcmToken() }.getOrNull() ?: return@LaunchedEffect
        runCatching { Net.client.api.registerDevice(NuruMessagingService.deviceBody(token)) }
    }
}

private suspend fun fcmToken(): String = suspendCancellableCoroutine { cont ->
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { cont.resume(it) }
        .addOnFailureListener { cont.resumeWithException(it) }
}
