// Settings — notification preferences (push/email/sms) + two-factor auth
// management (enroll with TOTP secret + verify, or disable). Port of the iOS
// SettingsView + MfaEnrollSheet.
package org.nuruplace.member.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.MfaCodeBody
import org.nuruplace.member.data.net.MfaEnrollment
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.NotificationPreferences
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<NotificationPreferences?>(null) }
    LaunchedEffect(Unit) { prefs = runCatching { Net.client.api.notificationPreferences() }.getOrNull() }

    fun save(p: NotificationPreferences) {
        prefs = p
        scope.launch { runCatching { Net.client.api.updateNotificationPreferences(p) } }
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings", kicker = "Profile", onBack = onBack)
        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            prefs?.let { p ->
                NuruCard {
                    Kicker("Notifications")
                    Spacer(Modifier.height(Spacing.sm))
                    PrefRow("Push", p.pushEnabled) { save(p.copy(pushEnabled = it)) }
                    PrefRow("Email", p.emailEnabled) { save(p.copy(emailEnabled = it)) }
                    PrefRow("SMS", p.smsEnabled) { save(p.copy(smsEnabled = it)) }
                }
            }
            NuruCard(modifier = Modifier.clickable { onOpen("firebase-account") }) {
                Kicker("Firebase account")
                Spacer(Modifier.height(Spacing.xs))
                Text("Email / password sign-in (add-alongside)", style = NuruType.caption, color = Nuru.ink600)
            }
            NuruCard { TextSizeSection() }
            NuruCard { LocationSection() }
            NuruCard { ChangePasswordSection() }
            NuruCard { TwoFactorSection() }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun PrefRow(label: String, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NuruType.body, color = Nuru.ink, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onToggle)
    }
}

private val TEXT_SIZES = listOf("Small" to 0.9f, "Default" to 1.0f, "Large" to 1.15f)

@Composable
private fun TextSizeSection() {
    Kicker("Text size")
    Spacer(Modifier.height(Spacing.sm))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TEXT_SIZES.forEach { (label, scale) ->
            val selected = kotlin.math.abs(org.nuruplace.member.data.AppPrefs.textScale - scale) < 0.01f
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(Radii.control))
                    .background(if (selected) Nuru.navyDeep else Nuru.surface)
                    .clickable { org.nuruplace.member.data.AppPrefs.updateTextScale(scale) }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = NuruType.cardCta, color = if (selected) Nuru.onNavy else Nuru.ink600)
            }
        }
    }
    Spacer(Modifier.height(Spacing.xs))
    Text("Adjusts text size across the whole app.", style = NuruType.micro, color = Nuru.ink400)
}

@Composable
@Suppress("MissingPermission")
private fun LocationSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun pushFix() {
        scope.launch {
            val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            val loc = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    com.google.android.gms.tasks.Tasks.await(
                        client.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, null),
                    )
                }.getOrNull()
            }
            if (loc != null) runCatching { Net.client.api.shareLocation(org.nuruplace.member.data.net.LocationBody(loc.latitude, loc.longitude)) }
        }
    }

    val askPerm = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { org.nuruplace.member.data.AppPrefs.updateShareLocation(true); pushFix() }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Kicker("Share my approximate location")
            Spacer(Modifier.height(Spacing.xs))
            Text("Helps you connect with believers near you. Approximate only; turn it off anytime.", style = NuruType.micro, color = Nuru.ink600)
        }
        Spacer(Modifier.height(Spacing.sm))
        Switch(checked = org.nuruplace.member.data.AppPrefs.shareLocation, onCheckedChange = { want ->
            if (want) {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) { org.nuruplace.member.data.AppPrefs.updateShareLocation(true); pushFix() }
                else askPerm.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                org.nuruplace.member.data.AppPrefs.updateShareLocation(false)
                scope.launch { runCatching { Net.client.api.stopSharingLocation() } }
            }
        })
    }
}

@Composable
private fun ChangePasswordSection() {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    Kicker("Change password")
    Spacer(Modifier.height(Spacing.sm))
    OutlinedTextField(current, { current = it }, label = { Text("Current password") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(Spacing.xs))
    OutlinedTextField(next, { next = it }, label = { Text("New password") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(Spacing.xs))
    OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm new password") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
    msg?.let { Spacer(Modifier.height(Spacing.xs)); Text(it, style = NuruType.caption, color = if (it.startsWith("Password")) Nuru.successText else Nuru.danger) }
    Spacer(Modifier.height(Spacing.sm))
    PrimaryButton("Update password", loading = busy, enabled = current.isNotBlank() && next.length >= 8, onClick = {
        when {
            next.length < 8 -> msg = "New password must be at least 8 characters."
            next != confirm -> msg = "New passwords don't match."
            !busy -> {
                busy = true
                scope.launch {
                    try {
                        Net.client.api.changePassword(org.nuruplace.member.data.net.ChangePasswordBody(current.trim(), next))
                        msg = "Password updated."; current = ""; next = ""; confirm = ""
                    } catch (ex: Exception) {
                        msg = org.nuruplace.member.data.net.ApiException.message(ex)
                    } finally { busy = false }
                }
            }
        }
    })
}

@Composable
private fun TwoFactorSection() {
    val scope = rememberCoroutineScope()
    var enrollment by remember { mutableStateOf<MfaEnrollment?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    Kicker("Two-factor authentication")
    Spacer(Modifier.height(Spacing.sm))
    val e = enrollment
    if (e == null) {
        Text("Add an authenticator app for a second layer of security.", style = NuruType.caption, color = Nuru.ink600)
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton("Set up two-factor", loading = busy, onClick = {
            if (!busy) { busy = true; scope.launch { enrollment = runCatching { Net.client.api.enrollMfa() }.getOrNull(); busy = false } }
        })
    } else {
        Text("Add this secret to your authenticator app, then enter the 6-digit code:", style = NuruType.caption, color = Nuru.ink600)
        Spacer(Modifier.height(Spacing.xs))
        Text(e.secret, style = NuruType.cardTitle, color = Nuru.goldLo)
        Spacer(Modifier.height(Spacing.sm))
        OutlinedTextField(code, { code = it }, label = { Text("6-digit code") }, singleLine = true, shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
        msg?.let { Spacer(Modifier.height(Spacing.xs)); Text(it, style = NuruType.caption, color = if (it.startsWith("2FA")) Nuru.successText else Nuru.danger) }
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton("Verify & enable", loading = busy, enabled = code.length >= 6, onClick = {
            if (!busy) {
                busy = true
                scope.launch {
                    try { Net.client.api.verifyMfa(MfaCodeBody(code.trim())); msg = "2FA is now on."; enrollment = null; code = "" }
                    catch (ex: Exception) { msg = org.nuruplace.member.data.net.ApiException.message(ex) }
                    finally { busy = false }
                }
            }
        })
    }
}
