// FCM push (§D-M9) — cross-platform push via Firebase Cloud Messaging (free on
// Spark). onNewToken registers the device token with the custom backend
// (POST /me/devices, which already accepts it); onMessageReceived posts a local
// notification for foreground/data messages. The backend's dispatcher sends via
// the FCM HTTP v1 API. Add-alongside: this does not change the app's JWT session.
//
// Sound (owner request 2026-09-28): every push says what it is (`nuru_kind`:
// message / update / ring) and whether the member wants it heard
// (`nuru_sound`: on / off — Settings' "Sound and vibration"). A push the
// SYSTEM shows while the app is in the background already names its channel
// (NotificationChannels.kt); this code shows the rest — any push while the
// app is open, and every ring — on the channel the same rule picks.
package org.nuruplace.member.data.firebase

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
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.OpenConversation
import org.nuruplace.member.data.net.DeviceBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.feature.live.LiveInviteNotifications
import org.nuruplace.member.feature.live.isStreamId
import org.nuruplace.member.feature.live.liveInviteFrom
import org.nuruplace.member.feature.live.liveInviteRoute

class NuruMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Only register when there is a signed-in backend session (a JWT).
        if (!Net.client.vault.hasSession) return
        scope.launch { runCatching { Net.client.api.registerDevice(deviceBody(this@NuruMessagingService, token)) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val kind = data["nuru_kind"]
        val sound = data["nuru_sound"]
        // Already made at process start (NuruApp); cheap to confirm, and a
        // notification posted to a channel that doesn't exist is dropped.
        NotificationChannels.ensure(this)

        // A Live guest invite RINGS. It is data-only, so this runs for it
        // even while the app is closed, and nothing shows it unless this does.
        if (kind == PushKind.RING) {
            // A signed-out phone never rings anyone's invite (its token can
            // outlive the session it was registered under).
            if (!Net.client.vault.hasSession) return
            val invite = liveInviteFrom(data)
            if (invite != null) {
                LiveInviteNotifications.post(this, invite)
                return
            }
            // No stream to join: fall through and show its words plainly.
        }

        // Pastoral privacy (Chat Redesign C3b): a push about the member's
        // pastoral thread never shows a content preview — generic copy only,
        // and nothing at all when the member muted it. Since Chat Redesign C4
        // the backend DOES push chat messages (chat/service.ts notifyMessage):
        // DIRECT, DISCIPLER and PASTORAL threads, a broadcast and every answer
        // in its response thread (on the DM template) — at most one push
        // outstanding per conversation, none for a conversation the member
        // muted, and none for group (space) messages. Its pastoral push is
        // already generic; this is the client's own guard on top. It only
        // applies where the client renders the notification itself; a
        // notification-payload push the OS renders while the app is in the
        // background never reaches this code — real limit, recorded in
        // docs/PARITY_AUDIT.md.
        val isPastoral = isPastoralPush(data, AppPrefs.pastoralConversationId)
        if (isPastoral && AppPrefs.pastoralMuted) return

        // A message for the thread the member has open lands IN that thread
        // — it refreshes, with a light tick unless they turned Sound and
        // vibration off — and never in the tray (OpenConversation).
        if (landsInOpenThread(kind, data, OpenConversation.id)) {
            pushConversationId(data)?.let { OpenConversation.arrived(it, buzz = sound != "off") }
            return
        }

        val title = if (isPastoral) "Nuru Pathway" else message.notification?.title ?: data["alert_title"] ?: data["title"] ?: "Nuru Pathway"
        val body = if (isPastoral) {
            "You have a new private pastoral message."
        } else {
            message.notification?.body ?: data["alert_body"] ?: data["body"] ?: return
        }
        // Cold-tap deep link: compute the in-app destination from the push data and
        // hand it to MainActivity (PendingDest) so a tray tap lands on the target,
        // not just Home.
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        destFor(data)?.let { intent.putExtra("nuru.dest", it) }
        val pending = PendingIntent.getActivity(
            this, System.identityHashCode(message), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // A ring that got this far has no stream to join — shown as an update.
        val channel = channelFor(kind?.takeIf { it != PushKind.RING }, sound)
        val notif = NotificationCompat.Builder(this, channel)
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
        /** Push data → in-app nav route (mirrors NotificationsScreen.routeFor).
         *  Null → open Home (nothing to deep-link to).
         *
         *  Since 2026-09-28 every push's data also carries its `template`
         *  (plus `nuru_kind` / `nuru_sound`), so the template rules at the
         *  bottom — written for it, but until then only ever handed "" by a
         *  real push — now route real pushes: badge → Profile, event_* →
         *  Events, prayer_chain → the Prayer Room, live_stream_started → the
         *  player, the plan_group_* without a token → Read with a Friend.
         *  Every push that carries one of the specific keys below routes by
         *  that key first, exactly as before; chat pushes still open Home.
         *
         *  NOTE on key casing: the backend dispatcher (workers/dispatch.ts)
         *  copies the notification's `payload` JSONB into the FCM `data` map
         *  VERBATIM — no snake_case→camelCase conversion happens for push
         *  (unlike the REST DTOs, which go through the Json SnakeCase naming
         *  strategy). Every `.schedule({ payload: {...} })` call site in the
         *  backend writes snake_case keys (e.g. reading-social's groups.ts:
         *  `{ group_id, invite_token, inviter_id, inviter_name }`), so THIS
         *  map's keys are snake_case on the wire — `invite_token`/`group_id`,
         *  not `inviteToken`/`groupId`. Every specific lookup below is therefore
         *  keyed in snake_case to match what the backend actually writes,
         *  verified against each `.schedule({ payload })` call site:
         *    `module_id`       — assessment/moduleReflection.ts (`reflection_*`)
         *    `announcement_id` — announcements/service.ts (`announcement`)
         *    `level_number`    — assessment/levelAdvancement.ts (`level_ushered`)
         *                        and workers/handlers.ts (`level_completed`)
         *    `invite_token`    — reading-social/{groups,invites}.ts (`plan_group_*`)
         *    `department_id`   — departments/service.ts (`serve_request_*`,
         *                        `department_post`, `department_need_*`)
         *  (Before 2026-07-20 the first three read camelCase keys that never
         *  matched, so those taps silently fell through to the template branch
         *  and lost their specific target.) */
        fun destFor(data: Map<String, String>): String? {
            data["module_id"]?.takeIf { it.isNotBlank() }?.let { return "module/$it" }
            data["announcement_id"]?.takeIf { it.isNotBlank() }?.let { return "announcement/$it" }
            data["level_number"]?.takeIf { it.isNotBlank() }?.let { return "level/$it" }
            // Read with a Friend (reading-social R1) — a targeted invite ping
            // carries invite_token, so the tap opens the SAME invite-preview
            // screen a nuru://join/{token} deep link opens.
            data["invite_token"]?.takeIf { it.isNotBlank() }?.let { return "reading/join/$it" }
            // Departments (departments/service.ts): serve_request_received /
            // _approved / _declined, department_post, department_need_* all
            // carry department_id → the department page (spec §4).
            data["department_id"]?.takeIf { it.isNotBlank() }?.let { return "department/$it" }
            // Giving (feature/give/GivingRoutes.kt): their own keys say where,
            // ahead of the template they now carry too — a gift that failed
            // where the member could not see it opens that gift, with Try
            // again; a failed or paused schedule opens that schedule; the
            // heads-up before a prompt opens Give; a pledge's notices, and
            // its collector's (covered / stopped), open that pledge.
            org.nuruplace.member.feature.give.givingPushRoute(data)?.let { return it }
            val t = (data["template"] ?: "").lowercase()
            // A Live guest invite (live/service.ts inviteGuest, payload
            // { stream_id, title }) opens ITS stream, not whichever is newest
            // — only a ring without a usable stream_id reaches the plain
            // "live" rule below. (A ring is normally shown by
            // LiveInviteNotifications, never through here.)
            if (t == "live_guest_invite") {
                data["stream_id"]?.trim()?.takeIf { isStreamId(it) }?.let { return liveInviteRoute(it) }
            }
            return when {
                "department" in t || "serve_request" in t -> "departments"
                // live_stream_started (packages/backend/src/modules/live/service.ts)
                // — a tapped push must land IN THE PLAYER, not just Home, and the
                // payload alone (stream_id/scope/cell_id/title) isn't enough to
                // build LiveRoutes.kt's live-player route (no kind/viewers/
                // startedAt) — "live-now" is a lightweight MainShell destination
                // that re-fetches GET /live/now and forwards to the newest
                // watchable stream (or Home if it already ended).
                "live" in t -> "live-now"
                "prayer" in t -> "prayer-room?tab=corporate"
                "verse" in t || "memory" in t -> "memory-verses"
                "devotional" in t -> "devotional"
                // pledge_due_soon / pledge_overdue / pledge_fulfilled (Partners
                // programme §3) → the Give tab on Partners, before the generic give.
                "pledge" in t || "partner" in t -> "partners"
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

        /** A tap on a push the SYSTEM put in the tray — the app was in the
         *  background or closed, so [onMessageReceived] never ran and no
         *  `nuru.dest` was attached. FCM then hands the push's data to the
         *  launch intent as extras, beside its own `google.*` keys, and the
         *  same [destFor] reads them (before this, every such tap opened
         *  Home). Null for any launch that is not a push tap. */
        fun trayTapDest(extras: Map<String, String>): String? =
            if ("google.message_id" in extras || "google.sent_time" in extras) destFor(extras) else null

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
