// The notification permission, asked at the right moment (pathway docs/
// EXPERIENCE.md §7.2 #12, §7.3 — iOS NotificationPermission's words). Android
// 13+ shows nothing a push or a reminder posts until the member allows it. It
// used to be asked cold — the moment the signed-in shell first appeared
// (PushRegistration), before the member had turned anything on. Now it is
// asked only when the member turns on something that needs it — push in
// Settings, a pledge's "Remind me before it's due", the radio's "Remind me
// when we're live" — with the app's question first ("Allow notifications?" ·
// the one line saying why · Not now / Continue), then the system's own
// prompt. Refused for good: "Notifications are off" · "‹why› Turn them on for
// Nuru Pathway in Settings." · Open Settings. What was turned on stays on
// either way: the server keeps sending, and the phone shows it once allowed.
//
// Android only: while the phone has notifications off, Home carries one calm
// card — "Turn on notifications" · "So messages, Live invites and reminders
// reach this phone." · Turn on / Not now (hidden 14 days) — because Android
// carries real pushes (messages, Live invites, giving notices) and no longer
// asks at launch: a member who never touches a reminder would otherwise
// never be asked, and would silently get none of them.
package org.nuruplace.member.data.firebase

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.nuruplace.member.ui.components.NuruAlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType

/** What turning on something that needs notifications does on this phone. */
enum class NotificationAskStep {
    /** Nothing to ask: below Android 13, or already allowed. */
    NONE,

    /** The line saying why, then the system's own prompt. */
    PROMPT,

    /** Refused for good (asked before, and the system won't prompt again):
     *  the line, and the phone's settings for this app. */
    SETTINGS,
}

/** Pure, so NotificationAskTest pins it. [showRationale]: the system's
 *  shouldShowRequestPermissionRationale — false both before the first ask
 *  and once the member has refused for good; [askedBefore] tells them apart. */
fun notificationAskStep(sdkInt: Int, granted: Boolean, askedBefore: Boolean, showRationale: Boolean): NotificationAskStep = when {
    sdkInt < Build.VERSION_CODES.TIRAMISU || granted -> NotificationAskStep.NONE
    askedBefore && !showRationale -> NotificationAskStep.SETTINGS
    else -> NotificationAskStep.PROMPT
}

/** The one line saying why, per switch (§7.3 — the same words as iOS where
 *  iOS has the switch). */
object NotificationWhy {
    const val SETTINGS_PUSH = "So devotionals, events and reminders reach this phone."
    const val RADIO = "So we can tell you when Nuru Radio goes live."

    /** Android only — a pledge's "Remind me before it's due". */
    const val PLEDGE_REMINDER = "So your pledge reminders reach this phone."
}

/** The app's question before the phone's (iOS NotificationAsk): its title… */
fun notificationAskTitle(step: NotificationAskStep): String =
    if (step == NotificationAskStep.SETTINGS) "Notifications are off" else "Allow notifications?"

/** …and its line: the reason, or — refused for good — where to turn them on. */
fun notificationAskMessage(step: NotificationAskStep, why: String): String =
    if (step == NotificationAskStep.SETTINGS) "$why Turn them on for Nuru Pathway in Settings." else why

/** Home's card while the phone has notifications off (Android only, §7.3). */
const val NOTIFICATIONS_CARD_TITLE = "Turn on notifications"
const val NOTIFICATIONS_CARD_LINE = "So messages, Live invites and reminders reach this phone."

/** "Not now" hides the card this long. */
const val NOTIFICATIONS_CARD_SNOOZE_MS = 14L * 24 * 60 * 60 * 1000

/** Whether Home shows the card: Android 13+, notifications not allowed, and
 *  "Not now" not tapped in the last 14 days ([snoozedAtMs] 0 = never). */
fun notificationsCardShown(sdkInt: Int, granted: Boolean, snoozedAtMs: Long, nowMs: Long): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU && !granted &&
        (snoozedAtMs <= 0L || nowMs - snoozedAtMs >= NOTIFICATIONS_CARD_SNOOZE_MS)

/** Ask, from the moment something that needs notifications was turned on. */
fun interface NotificationAsk {
    /** [why]: the one line saying why this needs notifications. */
    fun ask(why: String)
}

/**
 * The ask for one screen: call [NotificationAsk.ask] right after the member
 * turns something on that needs notifications. It says nothing when they
 * are already allowed (or the phone predates the permission).
 */
@Composable
fun rememberNotificationAsk(): NotificationAsk {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Pair<String, NotificationAskStep>?>(null) }
    val prompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The member's answer stands; whatever was turned on stays on.
    }
    pending?.let { (why, step) ->
        NuruAlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(notificationAskTitle(step), style = NuruType.cardTitle, color = Nuru.navy) },
            text = { Text(notificationAskMessage(step, why), style = NuruType.body, color = Nuru.ink600) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    if (step == NotificationAskStep.SETTINGS) {
                        openNotificationSettings(context)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        AppPrefs.notificationsAsked = true
                        prompt.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(if (step == NotificationAskStep.SETTINGS) "Open Settings" else "Continue", style = NuruType.cardCta, color = Nuru.navy)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Not now", style = NuruType.cardCta, color = Nuru.ink600) }
            },
        )
    }
    return remember(context) {
        NotificationAsk { why ->
            val step = currentAskStep(context)
            if (step != NotificationAskStep.NONE) pending = why to step
        }
    }
}

/** Whether this phone may show notifications now. */
fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** What asking would take on this phone, right now. */
private fun currentAskStep(context: Context): NotificationAskStep {
    val rationale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
    return notificationAskStep(Build.VERSION.SDK_INT, notificationsAllowed(context), AppPrefs.notificationsAsked, rationale)
}

/** Home's card while the phone has notifications off: whether it shows, and
 *  its two answers. */
class NotificationsCard internal constructor(
    val shown: Boolean,
    /** Turn on: the phone's prompt — or, refused for good, its settings. */
    val turnOn: () -> Unit,
    /** Not now: hidden for 14 days. */
    val notNow: () -> Unit,
)

/** The card's state for Home, re-read whenever Home comes back to the front
 *  (the member may have allowed them in the phone's settings). */
@Composable
fun rememberNotificationsCard(): NotificationsCard {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationsAllowed(context)) }
    var snoozedAt by remember { mutableLongStateOf(AppPrefs.notificationsCardSnoozedAt) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = notificationsAllowed(context) }
    val prompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = notificationsAllowed(context)
    }
    return NotificationsCard(
        shown = notificationsCardShown(Build.VERSION.SDK_INT, granted, snoozedAt, System.currentTimeMillis()),
        turnOn = {
            when (currentAskStep(context)) {
                NotificationAskStep.NONE -> granted = true
                NotificationAskStep.SETTINGS -> openNotificationSettings(context)
                NotificationAskStep.PROMPT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    AppPrefs.notificationsAsked = true
                    prompt.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        },
        notNow = {
            val now = System.currentTimeMillis()
            AppPrefs.notificationsCardSnoozedAt = now
            snoozedAt = now
        },
    )
}

/** The phone's own page for this app's notifications. */
private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
