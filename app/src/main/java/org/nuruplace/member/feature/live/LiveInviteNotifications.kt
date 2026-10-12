// Posting a Live guest invite (LiveInvite.kt): the RING — an incoming-call
// notification on the Live-invites channel that rings and buzzes over and
// over (FLAG_INSISTENT) for 30 s, with a full-screen IncomingInviteActivity
// when the phone is locked or asleep — or, when the member muted Sound and
// vibration, the same invite QUIETLY on the quiet channel.
//
// Full screen and Android 14+: USE_FULL_SCREEN_INTENT (declared in the
// manifest) is granted at install on Android 13 and below, but from 14 Google
// Play grants it by default only to calling and alarm apps; for this app it
// is off unless the member switches it on in the phone's settings (on a
// Pixel: Special app access › Full screen notifications).
// canUseFullScreenIntent() says which; without it the invite still rings and
// buzzes as an insistent heads-up notification, it just can't take over a
// locked screen. The Play Console asks for a declaration of this
// permission's use at upload.
//
// When the ring ends unanswered — its 30 s ran out, or the member swiped it
// away or silenced the full screen — the invite stays in the tray, quietly:
// it is still open on the server until the member answers it or the stream
// ends, and the ring must not be the only place it was ever shown.
package org.nuruplace.member.feature.live

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import org.nuruplace.member.MainActivity
import org.nuruplace.member.R
import org.nuruplace.member.data.firebase.NotificationChannels
import org.nuruplace.member.ui.theme.Nuru

object LiveInviteNotifications {
    const val ACTION_SHOW = "org.nuruplace.member.live.invite.SHOW"
    const val ACTION_JOIN = "org.nuruplace.member.live.invite.JOIN"
    const val ACTION_DECLINE = "org.nuruplace.member.live.invite.DECLINE"
    const val ACTION_RING_ENDED = "org.nuruplace.member.live.invite.RING_ENDED"

    private const val EXTRA_STREAM_ID = "nuru.invite.streamId"
    private const val EXTRA_STREAM_TITLE = "nuru.invite.streamTitle"
    private const val EXTRA_HEADING = "nuru.invite.heading"
    private const val EXTRA_WORDS = "nuru.invite.words"
    private const val EXTRA_QUIET = "nuru.invite.quiet"
    /** When the ring began (epoch ms) — the full screen rings only what is left of the 30 s. */
    const val EXTRA_POSTED_AT = "nuru.invite.postedAt"

    /** One ringing notification and one quiet one per stream, told apart by
     *  id and keyed to the stream by tag, so two invites never overwrite
     *  each other and answering one clears exactly its own. */
    private const val RING_ID = 4_201
    private const val QUIET_ID = 4_202
    private fun tag(streamId: String) = "live-invite:$streamId"

    /** Ring [invite] — or, muted, show it quietly. */
    fun post(context: Context, invite: LiveInvite, postedAt: Long = System.currentTimeMillis()) {
        if (invite.quiet) postQuiet(context, invite) else postRing(context, invite, postedAt)
    }

    private fun postRing(context: Context, invite: LiveInvite, postedAt: Long) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val canFullScreen = Build.VERSION.SDK_INT < 34 || mgr.canUseFullScreenIntent()
        val show = activityIntent(context, invite, ACTION_SHOW, postedAt)
        val join = activityIntent(context, invite, ACTION_JOIN, postedAt)
        val decline = receiverIntent(context, invite, ACTION_DECLINE)
        val builder = NotificationCompat.Builder(context, NotificationChannels.LIVE_INVITE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Nuru.navy.toArgb())
            .setContentTitle(invite.heading)
            .setContentText(invite.words)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Android 7 and below; the channel decides above
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(RING_TIMEOUT_MS)
            .setContentIntent(show)
            // Fires when the system ends it — the 30 s ran out, or it was
            // swiped away — never when this app cancels it on an answer.
            .setDeleteIntent(receiverIntent(context, invite, ACTION_RING_ENDED))
        if (canFullScreen) builder.setFullScreenIntent(show, true)
        val ringPlain = {
            builder.setStyle(null).addAction(0, "Not now", decline).addAction(0, "Join", join)
            mgr.notify(tag(invite.streamId), RING_ID, builder.insistent())
        }
        if (Build.VERSION.SDK_INT >= 31 && canFullScreen) {
            // A call-style notification must carry a full-screen intent (or
            // be a foreground service's) or Android refuses to post it — so
            // only when the full screen is allowed. Its two buttons are the
            // system's own, labelled "Decline" and "Answer"; the full screen
            // and every other form of the invite say "Not now" and "Join".
            val caller = Person.Builder().setName(invite.streamTitle).setImportant(true).build()
            builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, join))
            // Refused all the same (the platform throws, here inside the push
            // service): ring the plain form rather than crash or stay silent.
            runCatching { mgr.notify(tag(invite.streamId), RING_ID, builder.insistent()) }
                .onFailure {
                    Log.w("LiveInvite", "the call-style invite was refused — ringing the plain one", it)
                    ringPlain()
                }
        } else {
            ringPlain()
        }
    }

    /** Rings and buzzes over and over until answered, cancelled or timed out. */
    private fun NotificationCompat.Builder.insistent(): Notification =
        build().apply { flags = flags or Notification.FLAG_INSISTENT }

    /** The invite without a ring: muted members' invites, and every invite
     *  whose ring ended unanswered. No time-out — it is open until the member
     *  answers it (Join / Not now) or taps it, which opens the stream without
     *  answering, where the player's own invite card is waiting. */
    fun postQuiet(context: Context, invite: LiveInvite) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, "open:${invite.streamId}".hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("nuru.dest", liveInviteRoute(invite.streamId, invite.streamTitle)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.QUIET)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Nuru.navy.toArgb())
            .setContentTitle(invite.heading)
            .setContentText(invite.words)
            .setStyle(NotificationCompat.BigTextStyle().bigText(invite.words))
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Not now", receiverIntent(context, invite, ACTION_DECLINE))
            .addAction(0, "Join", activityIntent(context, invite, ACTION_JOIN, System.currentTimeMillis()))
            .build()
        mgr.notify(tag(invite.streamId), QUIET_ID, notification)
    }

    /** Clears both forms of [streamId]'s invite. Cancelling the ring is what
     *  stops its sound and buzz — and, being this app's own cancel, it never
     *  fires the ring-ended intent. */
    fun cancel(context: Context, streamId: String) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.cancel(tag(streamId), RING_ID)
        mgr.cancel(tag(streamId), QUIET_ID)
    }

    /** The invite back out of an intent this file built (through the same
     *  parser as the push, so the same checks and fallbacks); null for any
     *  other intent. */
    fun inviteFrom(intent: Intent?): LiveInvite? {
        intent ?: return null
        return liveInviteFrom(
            buildMap {
                intent.getStringExtra(EXTRA_STREAM_ID)?.let { put("stream_id", it) }
                intent.getStringExtra(EXTRA_STREAM_TITLE)?.let { put("title", it) }
                intent.getStringExtra(EXTRA_HEADING)?.let { put("alert_title", it) }
                intent.getStringExtra(EXTRA_WORDS)?.let { put("alert_body", it) }
                if (intent.getBooleanExtra(EXTRA_QUIET, false)) put("nuru_sound", "off")
            },
        )
    }

    private fun Intent.putInvite(invite: LiveInvite): Intent = this
        .putExtra(EXTRA_STREAM_ID, invite.streamId)
        .putExtra(EXTRA_STREAM_TITLE, invite.streamTitle)
        .putExtra(EXTRA_HEADING, invite.heading)
        .putExtra(EXTRA_WORDS, invite.words)
        .putExtra(EXTRA_QUIET, invite.quiet)

    /** IncomingInviteActivity is not exported: only these PendingIntents
     *  reach it, so Join's accept can never be triggered by another app. */
    private fun activityIntent(context: Context, invite: LiveInvite, action: String, postedAt: Long): PendingIntent =
        PendingIntent.getActivity(
            context, "$action:${invite.streamId}".hashCode(),
            Intent(context, IncomingInviteActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putInvite(invite)
                .putExtra(EXTRA_POSTED_AT, postedAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun receiverIntent(context: Context, invite: LiveInvite, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, "$action:${invite.streamId}".hashCode(),
            Intent(context, LiveInviteReceiver::class.java).setAction(action).putInvite(invite),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
