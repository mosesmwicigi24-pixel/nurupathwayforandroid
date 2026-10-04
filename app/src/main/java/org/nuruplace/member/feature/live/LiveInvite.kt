// A Live guest invite that RINGS (owner request 2026-09-28: "make calls ring
// and vibrate too" — Nuru has no calls, and the owner chose to ring the
// moment a broadcaster invites a member onto the stage of a live stream).
// The pure half: what a ring push carries, how long it rings, and where
// Join lands. LiveInviteNotifications posts it, IncomingInviteActivity is
// its full screen, LiveInviteReceiver answers it from the tray.
//
// The ring push is DATA-ONLY (workers/dispatch.ts fcmMessage, kind "ring"),
// so onMessageReceived runs for it even while the app is closed. Its data:
//   stream_id    the stream the member is invited onto
//   title        the STREAM's name ("Sunday service")
//   alert_title  the invite's heading ("You're invited to go live")
//   alert_body   its words (`You've been invited to join "Sunday service" as a guest.`)
//   body         = alert_body, for an app older than ringing
//   nuru_sound   "off" when the member turned Sound and vibration off
package org.nuruplace.member.feature.live

import android.app.NotificationManager
import android.media.AudioManager

/** How long an invite rings before it goes, quietly, to the tray. */
const val RING_TIMEOUT_MS = 30_000L

/** How long Join waits for the server to accept before it opens the stream
 *  anyway — the accept carries on behind it, and the player's own 5-second
 *  pulse picks the accepted state up whenever it lands. */
const val ACCEPT_WAIT_MS = 6_000L

data class LiveInvite(
    val streamId: String,
    /** The stream's name — the "caller". */
    val streamTitle: String,
    /** The invite's heading, e.g. "You're invited to go live". */
    val heading: String,
    /** Its sentence, e.g. `You've been invited to join "Sunday service" as a guest.` */
    val words: String,
    /** Muted: shown on the quiet channel, no ring, no buzz, no full screen. */
    val quiet: Boolean,
)

/** A ring push's data → its invite; null when it names no usable stream (a
 *  push with nothing to join is shown as an ordinary notification instead).
 *  Every words field falls back to the backend's own copy, so a thin
 *  payload still reads as a sentence. */
fun liveInviteFrom(data: Map<String, String>): LiveInvite? {
    val streamId = data["stream_id"]?.trim()?.takeIf(::isStreamId) ?: return null
    val stream = data["title"]?.trim().orEmpty().ifBlank { "Nuru Live" }
    return LiveInvite(
        streamId = streamId,
        streamTitle = stream,
        heading = data["alert_title"]?.trim().orEmpty().ifBlank { "You're invited to go live" },
        words = (data["alert_body"]?.trim()?.takeIf { it.isNotEmpty() } ?: data["body"]?.trim())
            .orEmpty().ifBlank { "You've been invited to join \"$stream\" as a guest." },
        quiet = data["nuru_sound"] == "off",
    )
}

/** Stream ids are UUIDs. Anything else is refused rather than spliced into
 *  the route it rides in (a "/" or "&" would break or bend the match). */
internal fun isStreamId(s: String): Boolean =
    s.isNotEmpty() && s.length <= 64 &&
        s.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

/** What is left of the ring that began at [postedAtMs]: the whole
 *  [timeoutMs] when fresh, nothing once it has run out, and never more than
 *  [timeoutMs] (a clock set backwards must not make it ring for longer). */
fun ringRemainingMs(postedAtMs: Long, nowMs: Long, timeoutMs: Long = RING_TIMEOUT_MS): Long =
    (timeoutMs - (nowMs - postedAtMs)).coerceIn(0L, timeoutMs)

/** MainShell's route into [streamId]'s player: the live-now forwarder, told
 *  which stream (the push alone can't build the player's route — no url,
 *  kind or start time — so the forwarder fetches GET /live/now first). Any
 *  notice that names its stream opens it this way — an invite, a stream
 *  starting (EXPERIENCE.md §7.3) — with the stream's [title] when the notice
 *  carries it, so a Live that has ended can still be named. */
fun liveInviteRoute(streamId: String, title: String? = null): String =
    "live-now?streamId=$streamId" + (title?.trim()?.takeIf { it.isNotEmpty() }?.let { "&title=${routeArg(it)}" }.orEmpty())

/** A route argument, percent-encoded (spaces as %20, which the nav graph
 *  decodes) — plain JVM, so the routes are testable off-device. */
private fun routeArg(s: String): String = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** Whether the full screen rings aloud and whether it buzzes. */
data class RingPlan(val sound: Boolean, val vibrate: Boolean)

/** The full screen takes the ring over from the notification (so the two
 *  never ring at once), and rings exactly as the Live-invites channel would
 *  — as the MEMBER has set it in the system settings: nothing through Do
 *  Not Disturb, nothing from a channel turned down below "make sound"
 *  ([importance] < IMPORTANCE_DEFAULT), no sound once its sound is None, no
 *  buzz once its vibration is off. Then the phone's own mode: silent stops
 *  both, vibrate stops the sound (the ring stream is muted there anyway);
 *  only normal rings aloud. */
fun ringPlan(
    importance: Int,
    channelHasSound: Boolean,
    channelVibrates: Boolean,
    ringerMode: Int,
    doNotDisturb: Boolean,
): RingPlan {
    if (doNotDisturb || importance < NotificationManager.IMPORTANCE_DEFAULT) return RingPlan(sound = false, vibrate = false)
    return RingPlan(
        sound = channelHasSound && ringerMode == AudioManager.RINGER_MODE_NORMAL,
        vibrate = channelVibrates && ringerMode != AudioManager.RINGER_MODE_SILENT,
    )
}
