// The ringing Live invite, full screen (owner request 2026-09-28: "make calls
// ring and vibrate too" — the owner chose to ring a broadcaster's invite onto
// the stage). Opened three ways, all from LiveInviteNotifications:
//   - the ring's full-screen intent, when the phone is locked or asleep — it
//     shows over the lock screen and turns the screen on;
//   - a tap on the ringing notification;
//   - the notification's Join / Answer button, which joins straight away.
// It shows the stream's name, "is inviting you to join Live", and Join /
// Not now, in Nuru Live's navy and gold.
//
// It TAKES THE RING OVER from the notification: cancelling the notification
// is what silences the system's insistent ring, and InviteRinger then plays
// the channel's own sound and buzz from here — so the two never ring at
// once, and the ring stops the instant the member answers. It rings until
// Join, Not now, or the rest of the invite's 30 s; the power button silences
// it (as it does a call) and leaves the invite answerable on screen; Home or
// Back ends the ring and leaves the invite in the tray, quietly.
//
// Join = the player's own Accept, in order: unlock (a member who backs out
// of unlocking has joined nothing), accept on the server (POST
// /live/streams/{id}/guests/respond {accept:true}), then open the stream's
// player, whose first pulse finds the member on the stage and asks for the
// camera and microphone. Not now = the same endpoint with {accept:false}.
package org.nuruplace.member.feature.live

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.nuruplace.member.MainActivity
import org.nuruplace.member.data.firebase.NotificationChannels
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.LivePulsingDot
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruTheme
import org.nuruplace.member.ui.theme.NuruType

class IncomingInviteActivity : ComponentActivity() {
    private var invite by mutableStateOf<LiveInvite?>(null)
    private var joining by mutableStateOf(false)

    /** Answered, or gone to the tray: nothing more may ring or be posted. */
    private var settled = false
    private var postedAt = 0L
    private var ringOutJob: Job? = null
    private lateinit var ringer: InviteRinger

    /** The power button silences the ring, as it does a phone call's. The
     *  invite stays on screen, answerable, until its time runs out. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = ringer.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        ringer = InviteRinger(applicationContext)
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        onBackPressedDispatcher.addCallback(this) { if (!joining) ringOut() }
        if (!adopt(intent)) {
            finish()
            return
        }
        setContent {
            NuruTheme {
                invite?.let { IncomingInviteScreen(it, joining, onJoin = ::join, onNotNow = ::notNow) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        adopt(intent)
    }

    /** Takes up the invite [intent] carries: joins at once for Join, else
     *  takes the ring over for what is left of its 30 s. False when the
     *  intent carries none (and nothing was showing before it). */
    private fun adopt(intent: Intent?): Boolean {
        val next = LiveInviteNotifications.inviteFrom(intent) ?: return invite != null
        val current = invite
        // Already joining this very invite (Join tapped twice, from two places).
        if (joining && current?.streamId == next.streamId) return true
        if (current != null && !settled && current.streamId != next.streamId) {
            // A second invite while the first still rings: the first goes,
            // quietly, to the tray — the newest one is the one on screen.
            ringer.stop()
            LiveInviteNotifications.postQuiet(this, current)
        }
        ringOutJob?.cancel()
        invite = next
        settled = false
        joining = false
        postedAt = intent?.getLongExtra(LiveInviteNotifications.EXTRA_POSTED_AT, 0L)?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        if (intent?.action == LiveInviteNotifications.ACTION_JOIN) {
            join()
            return true
        }
        LiveInviteNotifications.cancel(this, next.streamId)
        val left = ringRemainingMs(postedAt, System.currentTimeMillis())
        if (left <= 0L) {
            ringOut()
            return true
        }
        ringer.start()
        ringOutJob = lifecycleScope.launch {
            delay(left)
            ringOut()
        }
        return true
    }

    private fun join() {
        val inv = invite ?: return
        if (joining) return
        settled = true
        joining = true
        ringOutJob?.cancel()
        ringer.stop()
        LiveInviteNotifications.cancel(this, inv.streamId)
        // Asking to unlock works only for an activity that is on screen, and
        // Join from the notification arrives before this one is — so wait
        // for it (a tap on the button here is already on screen: at once).
        lifecycleScope.launch { lifecycle.withResumed { unlockThenAccept(inv) } }
    }

    private fun unlockThenAccept(inv: LiveInvite) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = acceptAndOpen(inv)
                    override fun onDismissCancelled() = backToInvite()
                    override fun onDismissError() = backToInvite()
                },
            )
        } else {
            acceptAndOpen(inv)
        }
    }

    /** Unlocking was cancelled: the invite is still theirs to answer — on
     *  screen, without the ring — until its time runs out. */
    private fun backToInvite() {
        joining = false
        settled = false
        val left = ringRemainingMs(postedAt, System.currentTimeMillis())
        ringOutJob = lifecycleScope.launch {
            delay(left)
            ringOut()
        }
    }

    private fun acceptAndOpen(inv: LiveInvite) {
        lifecycleScope.launch {
            // Accept BEFORE the player opens, so its first pulse already has
            // this member on the stage. Never waited on for long: after
            // ACCEPT_WAIT_MS the stream opens anyway, with the accept still
            // running behind it (and the player's card offering Accept again
            // should it fail while the invite is still open).
            val accept = Net.client.bgScope.async { respondToInvite(inv.streamId, accept = true) }
            withTimeoutOrNull(ACCEPT_WAIT_MS) { accept.await() }
            startActivity(
                Intent(this@IncomingInviteActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("nuru.dest", liveInviteRoute(inv.streamId)),
            )
            finish()
        }
    }

    private fun notNow() {
        val inv = invite ?: return
        if (settled) return
        settled = true
        ringOutJob?.cancel()
        ringer.stop()
        LiveInviteNotifications.cancel(this, inv.streamId)
        Net.client.bgScope.launch { respondToInvite(inv.streamId, accept = false) }
        finish()
    }

    /** The ring ends unanswered — its time ran out, or the member went Home
     *  or Back: silence, and the invite goes to the tray, quietly. */
    private fun ringOut() {
        val inv = invite
        if (inv == null) {
            finish()
            return
        }
        if (settled) return
        settled = true
        ringOutJob?.cancel()
        ringer.stop()
        LiveInviteNotifications.postQuiet(this, inv)
        finish()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!joining) ringOut()
    }

    override fun onDestroy() {
        ringOutJob?.cancel()
        ringer.stop()
        runCatching { unregisterReceiver(screenOff) }
        // Destroyed unanswered (the system reclaimed it): the invite must
        // not vanish with it.
        if (!settled && !isChangingConfigurations) invite?.let { LiveInviteNotifications.postQuiet(this, it) }
        super.onDestroy()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

/** The invite's ring while its full screen is up: the Live-invites
 *  channel's own sound on a loop and its vibration on repeat — what the
 *  notification's FLAG_INSISTENT played before the screen took over — so a
 *  member who changed that channel's sound, or silenced it, gets their
 *  choice here too ([ringPlan] decides). Never throws: a ringer that fails
 *  leaves a silent, still-answerable invite, never a crashed one. */
internal class InviteRinger(private val context: Context) {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Android 8.x's Ringtone cannot loop: start it again whenever it stops. */
    private val keepRinging = object : Runnable {
        override fun run() {
            ringtone?.let { if (!it.isPlaying) it.play() }
            handler.postDelayed(this, 1_000)
        }
    }

    fun start() {
        stop()
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = nm?.getNotificationChannel(NotificationChannels.LIVE_INVITE)
            val filter = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            val plan = ringPlan(
                importance = channel?.importance ?: NotificationManager.IMPORTANCE_HIGH,
                channelHasSound = channel == null || channel.sound != null,
                channelVibrates = channel?.shouldVibrate() ?: true,
                ringerMode = context.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL,
                doNotDisturb = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            )
            if (plan.sound) ringAloud(channel?.sound ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            if (plan.vibrate) buzz(channel?.vibrationPattern ?: NotificationChannels.LIVE_INVITE_VIBRATION)
        }.onFailure { Log.w("LiveInvite", "couldn't ring the Live invite", it) }
    }

    fun stop() {
        handler.removeCallbacks(keepRinging)
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun ringAloud(uri: Uri) {
        val tone = RingtoneManager.getRingtone(context, uri) ?: return
        tone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= 28) tone.isLooping = true else handler.postDelayed(keepRinging, 1_000)
        tone.play()
        ringtone = tone
    }

    private fun buzz(pattern: LongArray) {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        if (v == null || !v.hasVibrator()) return
        // Repeat from the start until cancelled.
        val effect = VibrationEffect.createWaveform(pattern, 0)
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
        }
        vibrator = v
    }
}

@Composable
private fun IncomingInviteScreen(invite: LiveInvite, joining: Boolean, onJoin: () -> Unit, onNotNow: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Nuru.homeNavyGradient)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            RingingBadge()
            Spacer(Modifier.height(32.dp))
            Text(
                invite.heading.uppercase(),
                style = NuruType.kicker,
                color = Nuru.gold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                invite.streamTitle,
                style = NuruType.display,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "is inviting you to join Live",
                style = NuruType.bodyLg,
                color = Nuru.onNavyDim,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1.4f))
            if (joining) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Nuru.gold, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Joining…", style = NuruType.cardCta, color = Color.White)
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AnswerButton("Not now", Icons.Filled.Close, SolidColor(Color.White.copy(alpha = 0.14f)), Color.White, onNotNow)
                    AnswerButton("Join", Icons.Filled.Videocam, Nuru.goldGradient, Nuru.homeNavy, onJoin)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The gold camera mark, a LIVE pill, and a gold ring that keeps rippling
 *  outward while the invite rings. */
@Composable
private fun RingingBadge() {
    val ripple = rememberInfiniteTransition(label = "inviteRipple")
    val t by ripple.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "inviteRippleT",
    )
    Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.55f * t
                    scaleY = 1f + 0.55f * t
                    alpha = 1f - t
                }
                .border(2.dp, Nuru.gold, CircleShape),
        )
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(Nuru.goldGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Videocam, contentDescription = null, tint = Nuru.homeNavy, modifier = Modifier.size(46.dp))
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-14).dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Nuru.liveRed)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LivePulsingDot(color = Color.White, size = 6.dp)
            Spacer(Modifier.width(5.dp))
            Text("LIVE", style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AnswerButton(label: String, icon: ImageVector, fill: Brush, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = label) { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = NuruType.cardCta, color = Color.White)
    }
}
