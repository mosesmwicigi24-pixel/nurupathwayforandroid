// The notification permission, asked at the right moment (pathway docs/
// EXPERIENCE.md §7.2 #12). Android 13+ shows nothing a push or a reminder
// posts until the member allows it. It used to be asked cold — the moment
// the signed-in shell first appeared (PushRegistration), before the member
// had turned anything on. Now it is asked only when the member turns on
// something that needs it — push notifications in Settings, a pledge's "Remind
// me before it's due", the radio's "Remind me when we're live" — with one
// line saying why, then the system's own prompt. Refused for good, the same
// line offers the phone's settings instead (the system prompt would no
// longer show). What was turned on stays on either way: the server keeps
// sending, and the phone shows it once allowed.
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
        AlertDialog(
            onDismissRequest = { pending = null },
            text = { Text(why, style = NuruType.body, color = Nuru.ink) },
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
                    Text(if (step == NotificationAskStep.SETTINGS) "Open settings" else "Allow", style = NuruType.cardCta, color = Nuru.navy)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Not now", style = NuruType.cardCta, color = Nuru.ink600) }
            },
        )
    }
    return remember(context) {
        NotificationAsk { why ->
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val rationale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
            val step = notificationAskStep(Build.VERSION.SDK_INT, granted, AppPrefs.notificationsAsked, rationale)
            if (step != NotificationAskStep.NONE) pending = why to step
        }
    }
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
