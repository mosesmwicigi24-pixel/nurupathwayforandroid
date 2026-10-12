// Push registration — runs once inside the authed shell: ensures the notification
// channels, fetches the current FCM token and registers it with the backend
// (POST /me/devices). No-ops when Firebase isn't configured or there's no
// backend session. Add-alongside. It no longer asks for POST_NOTIFICATIONS:
// that is asked when the member turns on something that needs it, with one
// line saying why (NotificationAsk.kt, EXPERIENCE.md §7.2 #12) — never cold.
package org.nuruplace.member.data.firebase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import org.nuruplace.member.data.net.Net
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
fun PushRegistration() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!FirebaseAuthService.isConfigured(context)) return@LaunchedEffect
        if (!Net.client.vault.hasSession) return@LaunchedEffect
        NotificationChannels.ensure(context)

        // The token registers whether or not this phone may DISPLAY pushes
        // yet (Android 13+'s permission, asked only when the member turns on
        // something that needs it), so the server can target the device.
        val token = runCatching { fcmToken() }.getOrNull() ?: return@LaunchedEffect
        runCatching { Net.client.api.registerDevice(NuruMessagingService.deviceBody(context, token)) }
    }
}

private suspend fun fcmToken(): String = suspendCancellableCoroutine { cont ->
    FirebaseMessaging.getInstance().token
        .addOnSuccessListener { cont.resume(it) }
        .addOnFailureListener { cont.resumeWithException(it) }
}
