// Notification channels and the choice of one for each push (owner request
// 2026-09-28: "a beep sound / notification sound or vibrations on any message
// that comes in … a place you can mute it … make calls ring and vibrate too").
// The ids are part of the push contract — the backend names them in every
// Android push it renders (workers/dispatch.ts PUSH_CHANNEL) — and a channel
// is immutable once it is on a phone: from the moment it is created, its
// sound, vibration and importance belong to the member, who can change each
// one in the system settings and must never be overridden from here. That is
// why these are NEW ids rather than a retune of the single `nuru_default`
// every push used before, which is deleted.
package org.nuruplace.member.data.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log

/** `nuru_kind` on every push's data (dispatch.ts `pushKind`). */
object PushKind {
    /** chat_dm_message, chat_discipler_message, chat_pastoral_message, chat_broadcast. */
    const val MESSAGE = "message"
    /** live_guest_invite — data-only, so this app always rings it itself. */
    const val RING = "ring"
    /** Everything else. */
    const val UPDATE = "update"
}

object NotificationChannels {
    const val MESSAGES = "nuru_messages"
    const val UPDATES = "nuru_updates"
    const val QUIET = "nuru_quiet"
    const val LIVE_INVITE = "nuru_live_invite"

    /** The one channel every push used before 2026-09-28. */
    private const val LEGACY = "nuru_default"

    /** A long, call-like buzz: three one-second pulses and a breath. The
     *  ring notification repeats it (FLAG_INSISTENT) and so does
     *  IncomingInviteActivity while it is on screen. */
    val LIVE_INVITE_VIBRATION = longArrayOf(0, 1_000, 800, 1_000, 800, 1_000, 1_600)

    /** Creates the four channels (a no-op for any the phone already has,
     *  bar their names and descriptions) and deletes the legacy one. Called
     *  from NuruApp at every process start — which includes the start FCM
     *  makes to show a push while the app is closed, so the channel a push
     *  names exists before the system looks for it (an absent one would drop
     *  it onto the manifest's default). Never throws: a channel failure must
     *  not take the app's start down with it. */
    fun ensure(context: Context) {
        runCatching {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val notificationAudio = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            mgr.createNotificationChannels(
                listOf(
                    NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "New messages from your discipler, your pastor and your friends"
                        setSound(notificationSound, notificationAudio)
                        enableVibration(true)
                    },
                    NotificationChannel(UPDATES, "Updates and reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Reflections, encouragement, events, giving and reminders"
                        setSound(notificationSound, notificationAudio)
                        enableVibration(true)
                    },
                    NotificationChannel(QUIET, "Quiet notifications", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Everything, without a sound or a buzz, while Sound and vibration is off in Settings"
                        setSound(null, null)
                        enableVibration(false)
                    },
                    NotificationChannel(LIVE_INVITE, "Live invites", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Rings when a broadcaster invites you onto the stage of a Nuru Live stream"
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        enableVibration(true)
                        vibrationPattern = LIVE_INVITE_VIBRATION
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    },
                ),
            )
            mgr.deleteNotificationChannel(LEGACY)
        }.onFailure { Log.w("NotificationChannels", "couldn't create the notification channels", it) }
    }
}

/** The channel a push THIS app renders goes to — the same choice the backend
 *  makes for the ones the system renders: muted (`nuru_sound` "off") →
 *  quiet, whatever the kind; else a message → Messages, a ring → Live
 *  invites, anything else (an unknown or absent kind included) → Updates.
 *  An absent `nuru_sound` sounds: the member's setting defaults to on. */
fun channelFor(kind: String?, sound: String?): String = when {
    sound == "off" -> NotificationChannels.QUIET
    kind == PushKind.MESSAGE -> NotificationChannels.MESSAGES
    kind == PushKind.RING -> NotificationChannels.LIVE_INVITE
    else -> NotificationChannels.UPDATES
}

/** The conversation a chat push is about — `conversation_id`, which the
 *  backend writes on every chat push (chat/service.ts notifyMessage). The
 *  camelCase spelling is what this client used to look for; it never
 *  arrives, and is read only so nothing that relied on it breaks. */
fun pushConversationId(data: Map<String, String>): String? =
    (data["conversation_id"] ?: data["conversationId"])?.takeIf { it.isNotBlank() }

/** A push about the member's pastoral thread: its template
 *  (`chat_pastoral_message` — every push carries its template since
 *  2026-09-28), or its conversation when this device has learned which one
 *  is pastoral (AppPrefs.pastoralConversationId). */
fun isPastoralPush(data: Map<String, String>, pastoralConversationId: String?): Boolean {
    if ("pastoral" in data["template"].orEmpty().lowercase()) return true
    val id = pushConversationId(data) ?: return false
    return id == pastoralConversationId
}

/** A chat message for the thread the member has open right now: it lands
 *  IN that thread (a refresh and a light tick), never in the tray. Only a
 *  message — an update that happens to name the conversation (a space join
 *  request) is not something the open thread shows. */
fun landsInOpenThread(kind: String?, data: Map<String, String>, openConversationId: String?): Boolean =
    kind == PushKind.MESSAGE && openConversationId != null && pushConversationId(data) == openConversationId
