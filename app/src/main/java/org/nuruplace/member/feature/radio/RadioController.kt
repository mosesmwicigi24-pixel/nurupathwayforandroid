package org.nuruplace.member.feature.radio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide radio brain — the Android twin of iOS's `RadioCenter.shared`. Owns a
 * single MediaController bound to [RadioService], so audio, the media
 * notification and the lock-screen controls all keep living when the radio
 * screen closes or the app is backgrounded. The UI observes [state] and calls
 * tune/toggle/mute; it never holds a player of its own.
 */
object RadioController {
    data class State(
        val url: String? = null,
        val playing: Boolean = false,
        val muted: Boolean = false,
        val title: String = "",
        val artist: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var controller: MediaController? = null
    private var lastVolume = 1f

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { push() }
        override fun onPlaybackStateChanged(playbackState: Int) { push() }
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) { push() }
    }

    /** Connect to the service once (idempotent). Call from the radio screen. */
    fun bind(context: Context) {
        if (controller != null) return
        val token = SessionToken(context.applicationContext, ComponentName(context.applicationContext, RadioService::class.java))
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            push()
        }, MoreExecutors.directExecutor())
    }

    /** Play [url] (live HLS or a recording), tagging the notification metadata. */
    fun tune(url: String, title: String, artist: String) {
        val c = controller ?: return
        if (currentUrl() != url) {
            c.setMediaItem(
                MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder().setTitle(title).setArtist(artist).build(),
                    )
                    .build(),
            )
            c.prepare()
        }
        c.play()
    }

    /** Toggle the given url: pause if it's the one playing, else (re)tune it. */
    fun toggle(url: String, title: String, artist: String) {
        val c = controller ?: return
        if (currentUrl() == url && c.isPlaying) c.pause() else tune(url, title, artist)
    }

    fun pause() { controller?.pause() }

    fun toggleMute() {
        val c = controller ?: return
        if (c.volume > 0f) { lastVolume = c.volume; c.volume = 0f } else c.volume = lastVolume
        push()
    }

    private fun currentUrl(): String? =
        controller?.currentMediaItem?.localConfiguration?.uri?.toString()

    private fun push() {
        val c = controller
        _state.value = State(
            url = currentUrl(),
            playing = c?.isPlaying == true,
            muted = (c?.volume ?: 1f) == 0f,
            title = c?.mediaMetadata?.title?.toString().orEmpty(),
            artist = c?.mediaMetadata?.artist?.toString().orEmpty(),
        )
    }
}
