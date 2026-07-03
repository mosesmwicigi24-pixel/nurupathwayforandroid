// Settings — notification preferences (push/email/sms) + two-factor auth
// management (enroll with TOTP secret + verify, or disable). Port of the iOS
// SettingsView + MfaEnrollSheet.
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
fun SettingsScreen(onBack: () -> Unit) {
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
