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
        // Pastoral privacy (Chat Redesign C3b): a push about the member's
        // pastoral thread never shows a content preview — generic copy only,
        // and nothing at all when the member muted it. DEFENSIVE today: the
        // backend currently sends NO push for any chat message (verified —
        // only space_join_*/connection events call notify()), so this branch
        // guards the future, not a live leak. It only applies where the client
        // renders the notification itself; a notification-payload push the OS
        // renders while the app is dead never reaches this code — real limit,
        // recorded in docs/PARITY_AUDIT.md.
        val template = (message.data["template"] ?: "").lowercase()
        val pastoralId = org.nuruplace.member.data.AppPrefs.pastoralConversationId
        val isPastoral = template.startsWith("pastoral") ||
            (pastoralId != null && message.data["conversationId"] == pastoralId)
        if (isPastoral && org.nuruplace.member.data.AppPrefs.pastoralMuted) return
        val title = if (isPastoral) "Nuru Pathway" else message.notification?.title ?: message.data["title"] ?: "Nuru Pathway"
        val body = if (isPastoral) {
            "You have a new private pastoral message."
        } else {
            message.notification?.body ?: message.data["body"] ?: return
        }
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
         *  Null → open Home (nothing to deep-link to).
         *
         *  NOTE on key casing: the backend dispatcher (workers/dispatch.ts)
         *  copies the notification's `payload` JSONB into the FCM `data` map
         *  VERBATIM — no snake_case→camelCase conversion happens for push
         *  (unlike the REST DTOs, which go through the Json SnakeCase naming
         *  strategy). Every `.schedule({ payload: {...} })` call site in the
         *  backend writes snake_case keys (e.g. reading-social's groups.ts:
         *  `{ group_id, invite_token, inviter_id, inviter_name }`), so THIS
         *  map's keys are snake_case on the wire — `invite_token`/`group_id`,
         *  not `inviteToken`/`groupId`. Read with a Friend's lookup below is
         *  keyed correctly; the pre-existing moduleId/announcementId/
         *  levelNumber lookups above appear to have the SAME bug (the backend
         *  payloads for those templates are also snake_case per
         *  assessment/moduleReflection.ts's `{ module_id, feedback }`) — left
         *  untouched here (out of scope for this feature; flagged separately). */
        fun destFor(data: Map<String, String>): String? {
            data["moduleId"]?.takeIf { it.isNotBlank() }?.let { return "module/$it" }
            data["announcementId"]?.takeIf { it.isNotBlank() }?.let { return "announcement/$it" }
            data["levelNumber"]?.takeIf { it.isNotBlank() }?.let { return "level/$it" }
            // Read with a Friend (reading-social R1) — a targeted invite ping
            // carries invite_token, so the tap opens the SAME invite-preview
            // screen a nuru://join/{token} deep link opens.
            data["invite_token"]?.takeIf { it.isNotBlank() }?.let { return "reading/join/$it" }
            val t = (data["template"] ?: "").lowercase()
            return when {
                "prayer" in t -> "prayer-room?tab=corporate"
                "verse" in t || "memory" in t -> "memory-verses"
                "devotional" in t -> "devotional"
                "give" in t || "giving" in t || "payment" in t -> "give"
                "event" in t -> "events"
                "badge" in t || "certificate" in t || "cert" in t -> "profile"
                "reflection" in t || "level" in t -> "pathway"
                // Other plan_group_* templates (accepted/joined/day-completed)
                // carry no redeemable token — land on the hub instead.
                "plan_group" in t -> "read-with-friend"
                else -> null
            }
        }

        fun ensureChannel(context: Context) {
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
