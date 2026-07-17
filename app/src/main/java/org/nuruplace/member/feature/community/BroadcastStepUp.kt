// Shared step-up machinery for the Broadcast surfaces (composer, sent list,
// response wall) — §5.3's fresh-password confirmation. Being signed in is not
// the same claim as "the person holding this phone is the pastor", so before
// speaking to the whole church in your name — or reading what it wrote back —
// the password is asked for once more, good for 15 minutes. Every 403
// password_required from a broadcast route funnels through here: the
// fingerprint fast path (data/BroadcastLock.kt) is tried first when it's
// enrolled, falling back to the typed sheet on cancel/failure/first time —
// exactly iOS's BroadcastLock + PasswordConfirmSheet pairing.
package org.nuruplace.member.feature.community

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.nuruplace.member.data.BroadcastLock
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.ConfirmPasswordBody
import org.nuruplace.member.data.net.Net
import retrofit2.HttpException

/** True for the specific 403 the broadcast routes use to ask "confirm it's
 *  you" — distinct from an ordinary 403 FORBIDDEN_SCOPE refusal. */
fun isPasswordRequired(e: Throwable): Boolean {
    val http = e as? HttpException ?: return false
    if (http.code() != 403) return false
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    return "password_required" in body
}

/** Unwrap a possibly-wrapped Context (Compose's LocalContext is often a
 *  ContextThemeWrapper) down to the hosting FragmentActivity. Null off-Activity
 *  (a preview, a service) — biometrics simply aren't offered then; the typed
 *  sheet still works everywhere. */
tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

internal sealed interface ConfirmOutcome {
    data object Success : ConfirmOutcome
    data class Failure(val message: String) : ConfirmOutcome
}

/** POST auth/confirm-password and, on success, swap ONLY the access token —
 *  the refresh token stays. A 401 means the password is wrong for THIS
 *  account right now, so any fingerprint-enrolled copy of it is stale — wipe
 *  it rather than let it keep failing silently. Shared by every broadcast
 *  surface's step-up. */
internal suspend fun confirmPasswordWithServer(password: String): ConfirmOutcome = try {
    val res = Net.client.api.confirmPassword(ConfirmPasswordBody(password))
    Net.client.vault.set(res.accessToken, Net.client.vault.refreshToken)
    ConfirmOutcome.Success
} catch (e: Exception) {
    val code = (e as? HttpException)?.code()
    if (code == 401) BroadcastLock.wipe()
    ConfirmOutcome.Failure(
        when (code) {
            401 -> "That password isn't right."
            429 -> ApiException.message(e)
            else -> "Couldn't confirm. Please try again."
        },
    )
}

/** One step-up sheet per screen (composer + list share one instance; the
 *  detail screen owns its own). Holds the password-sheet state, tries the
 *  fingerprint fast path first, and replays whatever action asked for it once
 *  the server confirms. */
class BroadcastStepUp(private val scope: CoroutineScope, private val activity: FragmentActivity?) {
    var asking by mutableStateOf(false)
        private set
    var password by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** Bound to the sheet's "unlock with fingerprint next time" switch —
     *  defaults on wherever it's offered, mirroring iOS's PasswordConfirmSheet. */
    var rememberWithFingerprint by mutableStateOf(false)
    private var retry: (() -> Unit)? = null

    /** Only worth asking when nothing is enrolled yet — once it is, this stays
     *  hidden until a 401 (password changed) or a new-fingerprint invalidation
     *  clears it. */
    val canOfferFingerprint: Boolean
        get() = activity != null && !BroadcastLock.isEnrolled && BroadcastLock.biometricAvailable(activity)

    /** Call from a failed call's 403 password_required branch. Tries the
     *  fingerprint fast path first when one is enrolled; opens the typed sheet
     *  (remembering [action] to replay) on cancel, failure, or when nothing is
     *  enrolled at all. */
    fun requestStepUp(action: () -> Unit) {
        retry = action
        if (activity != null && BroadcastLock.isEnrolled) {
            scope.launch {
                val pw = BroadcastLock.unlock(activity)
                val confirmed = pw != null && confirmPasswordWithServer(pw) is ConfirmOutcome.Success
                if (confirmed) {
                    val a = retry
                    retry = null
                    a?.invoke()
                } else {
                    openTypedSheet()
                }
            }
        } else {
            openTypedSheet()
        }
    }

    private fun openTypedSheet() {
        rememberWithFingerprint = canOfferFingerprint
        asking = true
    }

    fun cancel() {
        if (busy) return
        asking = false
        error = null
        password = ""
    }

    fun confirm() {
        if (password.isBlank() || busy) return
        busy = true
        error = null
        val typed = password
        val offerNow = rememberWithFingerprint && activity != null
        scope.launch {
            when (val outcome = confirmPasswordWithServer(typed)) {
                is ConfirmOutcome.Success -> {
                    // The password is CONFIRMED RIGHT — the only moment it's
                    // safe to put behind the owner's fingerprint for next time.
                    if (offerNow) BroadcastLock.enroll(activity!!, typed)
                    password = ""
                    asking = false
                    busy = false
                    val a = retry
                    retry = null
                    a?.invoke()
                }
                is ConfirmOutcome.Failure -> {
                    busy = false
                    error = outcome.message
                }
            }
        }
    }
}

@Composable
fun rememberBroadcastStepUp(): BroadcastStepUp {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current.findFragmentActivity()
    return remember { BroadcastStepUp(scope, activity) }
}

/** The "confirm it's you" sheet itself — shared styling for every caller. */
@Composable
fun BroadcastStepUpDialog(stepUp: BroadcastStepUp, reason: String) {
    if (!stepUp.asking) return
    AlertDialog(
        onDismissRequest = { stepUp.cancel() },
        containerColor = CHAT.white,
        title = { Text("Confirm it's you", style = cSerif(18, FontWeight.SemiBold), color = CHAT.navy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(reason, style = cInter(13).copy(lineHeight = 19.sp), color = CHAT.ink600)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CHAT.paper)
                        .border(1.dp, CHAT.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (stepUp.password.isBlank()) Text("Password", style = cInter(13), color = CHAT.faint)
                    BasicTextField(
                        value = stepUp.password,
                        onValueChange = { stepUp.password = it },
                        textStyle = cInter(13).copy(color = CHAT.navy),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        cursorBrush = SolidColor(CHAT.gold),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val e = stepUp.error
                if (e != null) Text(e, style = cInter(12, FontWeight.Medium), color = Color(0xFFB42318))
                if (stepUp.canOfferFingerprint) {
                    Row(
                        Modifier.fillMaxWidth().clickable { stepUp.rememberWithFingerprint = !stepUp.rememberWithFingerprint },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Unlock with fingerprint next time", style = cInter(13, FontWeight.Medium), color = CHAT.navy)
                        Switch(
                            checked = stepUp.rememberWithFingerprint,
                            onCheckedChange = { stepUp.rememberWithFingerprint = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = CHAT.gold, checkedThumbColor = Color.White),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { stepUp.confirm() }, enabled = stepUp.password.isNotBlank() && !stepUp.busy) {
                Text(if (stepUp.busy) "Confirming…" else "Confirm", style = cInter(13, FontWeight.Bold), color = CHAT.goldDeep)
            }
        },
        dismissButton = {
            TextButton(onClick = { stepUp.cancel() }, enabled = !stepUp.busy) {
                Text("Cancel", style = cInter(13, FontWeight.Medium), color = CHAT.ink500)
            }
        },
    )
}

/** "Just now" / "2h" / "Tue" / "5 Jul" — the broadcast list/detail's own
 *  vocabulary (iOS BroadcastViews.relativeDay parity; distinct from the
 *  inbox-row `chatRowTime`, which shows a clock time for anything today). */
fun broadcastRelativeTime(iso: String?): String {
    val z = chatZdt(iso) ?: return ""
    val seconds = java.time.Duration.between(z.toInstant(), java.time.Instant.now()).seconds
    return when {
        seconds < 90 -> "Just now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        else -> chatRowTime(iso)
    }
}
