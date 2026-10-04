// Answers a Live invite from the tray (LiveInviteNotifications): "Not now" /
// "Decline" declines it on the server and clears it; the ring's end (its 30 s
// ran out, or it was swiped away) leaves it in the tray, quietly. Not
// exported — only this app's own PendingIntents reach it. Join is never
// handled here: it opens IncomingInviteActivity directly, because Android 12+
// forbids a receiver that a notification started from starting an activity.
package org.nuruplace.member.feature.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.nuruplace.member.data.net.LiveGuestRespondBody
import org.nuruplace.member.data.net.Net

private const val TAG = "LiveInvite"

/** POST /live/streams/{id}/guests/respond — the same call and body as the
 *  player's own invite card (LivePlayerScreen's GuestStageBanner). A failure
 *  is logged, not retried: the invite simply stays open on the server, where
 *  the broadcaster still sees it pending and the player's card still offers
 *  it (a 404 means it was already answered, withdrawn, or the stream ended). */
internal suspend fun respondToInvite(streamId: String, accept: Boolean): Result<Unit> =
    runCatching { Net.client.api.postLiveGuestRespond(streamId, LiveGuestRespondBody(accept)) }
        .onFailure { Log.w(TAG, "couldn't ${if (accept) "accept" else "decline"} the Live invite for $streamId", it) }

class LiveInviteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val invite = LiveInviteNotifications.inviteFrom(intent) ?: return
        when (intent.action) {
            LiveInviteNotifications.ACTION_DECLINE -> {
                // Clear first — the ring stops the instant the member answers,
                // whatever the network does next.
                LiveInviteNotifications.cancel(context, invite.streamId)
                val pending = goAsync()
                Net.client.bgScope.launch {
                    try {
                        // Inside a receiver's own time limit; a decline that
                        // hasn't landed by then leaves the invite open.
                        withTimeoutOrNull(DECLINE_WAIT_MS) { respondToInvite(invite.streamId, accept = false) }
                            ?: Log.w(TAG, "the Live invite decline for ${invite.streamId} timed out")
                    } finally {
                        pending.finish()
                    }
                }
            }
            LiveInviteNotifications.ACTION_RING_ENDED -> LiveInviteNotifications.postQuiet(context, invite)
        }
    }

    private companion object {
        const val DECLINE_WAIT_MS = 8_000L
    }
}
