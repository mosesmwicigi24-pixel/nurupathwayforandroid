// FCM push (§D-M9) — cross-platform push via Firebase Cloud Messaging (free on
// Spark). onNewToken registers the device token with the custom backend
// (POST /me/devices, which already accepts it); onMessageReceived posts a local
// notification for foreground/data messages. The backend's dispatcher sends via
// the FCM HTTP v1 API. Add-alongside: this does not change the app's JWT session.
package org.nuruplace.member.data.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.nuruplace.member.MainActivity
import org.nuruplace.member.R
import org.nuruplace.member.data.net.DeviceBody
import org.nuruplace.member.data.net.Net

class NuruMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Only register when there is a signed-in backend session (a JWT).
        if (!Net.client.vault.hasSession) return
        scope.launch { runCatching { Net.client.api.registerDevice(deviceBody(this@NuruMessagingService, token)) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Nuru Pathway"
        val body = message.notification?.body ?: message.data["body"] ?: return
        ensureChannel(this)
        // Cold-tap deep link: compute the in-app destination from the push data and
        // hand it to MainActivity (PendingDest) so a tray tap lands on the target,
        // not just Home.
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        destFor(message.data)?.let { intent.putExtra("nuru.dest", it) }
        val pending = PendingIntent.getActivity(
            this, System.identityHashCode(message), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.identityHashCode(message), notif)
    }

    companion object {
        const val CHANNEL_ID = "nuru_default"

        /** Push data → in-app nav route (mirrors NotificationsScreen.routeFor).
         *  Null → open Home (nothing to deep-link to). */
        fun destFor(data: Map<String, String>): String? {
            data["moduleId"]?.takeIf { it.isNotBlank() }?.let { return "module/$it" }
            data["announcementId"]?.takeIf { it.isNotBlank() }?.let { return "announcement/$it" }
            data["levelNumber"]?.takeIf { it.isNotBlank() }?.let { return "level/$it" }
            val t = (data["template"] ?: "").lowercase()
            return when {
                "prayer" in t -> "prayer-wall"
                "verse" in t || "memory" in t -> "memory-verses"
                "devotional" in t -> "devotional"
                "give" in t || "giving" in t || "payment" in t -> "give"
                "event" in t -> "events"
                "badge" in t || "certificate" in t || "cert" in t -> "profile"
                "reflection" in t || "level" in t -> "pathway"
                else -> null
            }
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Nuru Pathway", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Reflections, encouragement, events and reminders"
                    },
                )
            }
        }

        fun deviceBody(context: Context, token: String) = DeviceBody(
            platform = "android",
            appVersion = org.nuruplace.member.BuildConfig.VERSION_NAME,
            model = Build.MODEL,
            pushToken = token,
            network = networkKind(context),
        )

        /** One-shot network sample for the device census: "wifi" | "cellular" |
         *  "other" (connected via something else), null when offline/unknown.
         *  Best-effort colour only — must never block or fail registration. */
        private fun networkKind(context: Context): String? = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.activeNetwork?.let(cm::getNetworkCapabilities) ?: return@runCatching null
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                else -> "other"
            }
        }.getOrNull()
    }
}
