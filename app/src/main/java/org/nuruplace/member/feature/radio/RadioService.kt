package org.nuruplace.member.feature.radio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.nuruplace.member.MainActivity
import org.nuruplace.member.R

/**
 * Foreground media service so Nuru Radio keeps speaking when the screen closes
 * or the phone is backgrounded/locked — with a media notification and
 * lock-screen transport controls. Mirrors iOS's RadioCenter (which already
 * keeps the lock screen and Dynamic Island alive). The whole app shares ONE
 * ExoPlayer here, reached from the UI through a MediaController.
 *
 * - Streaming attributes: MOVIE usage tolerates live-latency stalls better than
 *   the default and holds audio focus like a media app should.
 * - Tapping the notification reopens the app (and, via the "radio" dest, the
 *   player) instead of a blank task.
 * - handleAudioBecomingNoisy pauses when headphones unplug — expected media UX.
 */
// DefaultMediaNotificationProvider is media3 @UnstableApi — the standard,
// documented way to brand the media notification; the opt-in acknowledges the
// API may shift between media3 releases (pinned in gradle), nothing more.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // The persistent status-bar presence: a live-radio media notification with
        // our own broadcast small-icon (a recognisable "on air" glyph, not a
        // generic music note) on the "Nuru Radio" channel. While audio plays this
        // notification is a FOREGROUND notification — it stays in the status bar no
        // matter where the member navigates (or leaves the app), and only clears
        // when they stop the radio.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("nuru_radio")
                .setChannelName(R.string.radio_channel_name)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply { setSmallIcon(R.drawable.ic_radio_notification) },
        )

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra("nuru.dest", "radio")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // When the user swipes the app away with nothing playing, stop the service;
    // if audio is still playing, let it keep going in the background.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
