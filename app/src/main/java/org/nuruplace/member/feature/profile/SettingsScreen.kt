// Settings — iOS-parity layout (PREFERENCES header + carded sections). Preserves
// all functional wiring: notification prefs (push/sound/email/sms), text-size via
// AppPrefs, approximate-location sharing, change-password, two-factor enroll/
// verify/disable and sign-out. Port of the iOS SettingsView. Shared palette +
// primitives live in ProfileShared.kt (same package).
package org.nuruplace.member.feature.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.AppPrefs
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.ChangePasswordBody
import org.nuruplace.member.data.net.MfaCodeBody
import org.nuruplace.member.data.net.MfaEnrollment
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.NotificationPreferences
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.icons.Lucide

/**
 * @param embedded true when hosted as the You tab's Settings segment
 *   (YouScreen, docs/PARTNERS_PROGRAMME.md §0) — the segment capsule sits
 *   above the header, so the header has no back button. The pushed
 *   "settings" route (kept for links) has its back button.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit = {}, embedded: Boolean = false) {
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<NotificationPreferences?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { prefs = runCatching { Net.client.api.notificationPreferences() }.getOrNull() }

    fun save(p: NotificationPreferences) {
        val previous = prefs
        prefs = p
        saveError = null
        scope.launch {
            // A toggle that shows a state the server never recorded is a lie —
            // revert and say so instead.
            runCatching { Net.client.api.updateNotificationPreferences(p) }
                .onFailure { prefs = previous; saveError = "Couldn't save your preferences. ${ApiException.message(it)}" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PROF.paper)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        // One header on every tab (EXPERIENCE.md §8.1 rule 2, §8.2 #1):
        // "PREFERENCES · Settings", as iOS — the You tab's Settings segment
        // had none. Embedded, the segment capsule sits above it, so no back.
        ProfCreamHeaderBox {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp)) {
                if (!embedded) {
                    Box(
                        Modifier
                            .padding(bottom = 16.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PROF.white)
                            .border(1.dp, PROF.border, RoundedCornerShape(16.dp))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Lucide.ArrowLeft, PROF.navy, 18.dp)
                    }
                }
                Text("PREFERENCES", style = NuruType.kicker, color = PROF.eyebrow)
                Text("Settings", style = pSerif(26, FontWeight.SemiBold), color = PROF.navy, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // ── Body ────────────────────────────────────────────────────────────
        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SecurityCard()
            NotificationsCard(prefs = prefs, onSave = ::save)
        saveError?.let { err ->
            Text(
                err,
                style = pInter(11), color = androidx.compose.ui.graphics.Color(0xFFB91C1C),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
            DisplayCard()
            LanguageCard()
            PrivacyCard()
            HelpCard()
            ActionsRow()
            // The build this phone runs (Cycle 3's closing walk, B12) — "v1.0"
            // was written in while the build was 2.59.0 (84), so support heard
            // the wrong version.
            Text(
                appVersionLine(org.nuruplace.member.BuildConfig.VERSION_NAME, org.nuruplace.member.BuildConfig.VERSION_CODE),
                style = pInter(11),
                color = PROF.rowLabel,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Cards
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SecurityCard() {
    var showPassword by remember { mutableStateOf(false) }
    SectionCard {
        SectionTitle(Lucide.Lock, "SECURITY & LOGIN")
        ActionRow(
            tile = { IconTile(Lucide.Key, TINT_PASSWORD) },
            title = "Change password",
            subtitle = "Keep your account secure",
            onClick = { showPassword = !showPassword },
        )
        AnimatedVisibility(visible = showPassword) {
            ChangePasswordSection()
        }
        RowDivider()
        TwoFactorRow()
        RowDivider()
        ActionRow(
            tile = { IconTile(Lucide.Smartphone, TINT_SESSIONS) },
            title = "Active sessions",
            subtitle = "This device",
            onClick = {},
        )
        // No "Firebase account" row (EXPERIENCE.md §8.2 #9): it signed in to a
        // second, Firebase-only email account that nothing reads — the app's
        // session is the church's own sign-in, and Firebase carries push only.
        // No internal name reaches a member (§8.1 rule 8).
    }
}

@Composable
private fun NotificationsCard(prefs: NotificationPreferences?, onSave: (NotificationPreferences) -> Unit) {
    val context = LocalContext.current
    // Turned on, push notifications need this phone's permission too — asked
    // now, with why, never cold on launch (EXPERIENCE.md §7.2 #12).
    val notifyAsk = org.nuruplace.member.data.firebase.rememberNotificationAsk()
    SectionCard {
        SectionTitle(Lucide.Bell, "NOTIFICATIONS")
        if (prefs != null) {
            ToggleRow(
                tile = { NeutralTile(Lucide.Bell) },
                title = "Push notifications",
                subtitle = "Devotionals, events, reminders",
                checked = prefs.pushEnabled,
                onCheckedChange = { on ->
                    onSave(prefs.copy(pushEnabled = on))
                    if (on) notifyAsk.ask(org.nuruplace.member.data.firebase.NotificationWhy.SETTINGS_PUSH)
                },
            )
            RowDivider()
            // Owner request 2026-09-28: a sound and a buzz on everything that
            // arrives, and a place to mute it. Server-side
            // (`sound_enabled`), so every push the member's phones get says
            // whether to sound — the app shows a muted one quietly, and a
            // Live invite stops ringing. Off still delivers everything.
            ToggleRow(
                tile = {
                    NeutralTile(if (prefs.soundEnabled) Lucide.Volume2 else Lucide.VolumeX)
                },
                title = "Sound and vibration",
                subtitle = if (prefs.soundEnabled) {
                    "A sound and a buzz when a message or notification arrives."
                } else {
                    "Notifications arrive quietly."
                },
                checked = prefs.soundEnabled,
                onCheckedChange = { onSave(prefs.copy(soundEnabled = it)) },
            )
            RowDivider()
            ToggleRow(
                tile = { NeutralTile(Lucide.Mail) },
                title = "Email",
                subtitle = "Weekly summary & receipts",
                checked = prefs.emailEnabled,
                onCheckedChange = { onSave(prefs.copy(emailEnabled = it)) },
            )
            RowDivider()
            ToggleRow(
                tile = { NeutralTile(Lucide.Phone) },
                title = "SMS",
                subtitle = "Critical updates only",
                checked = prefs.smsEnabled,
                onCheckedChange = { onSave(prefs.copy(smsEnabled = it)) },
            )
            RowDivider()
        }
        // The phone's own page for this app's notifications: each channel
        // (Messages, Updates and reminders, Live invites, Quiet) keeps its
        // own sound and vibration there, which the member may change.
        ActionRow(
            tile = { IconTile(Lucide.Bell, TINT_NOTIF) },
            title = "Notification settings",
            subtitle = "Manage sounds & toggles in phone settings",
            onClick = {
                runCatching {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    runCatching {
                        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallback)
                    }
                }
            },
        )
    }
}

private val TEXT_SIZES = listOf("Small" to 0.85f, "Default" to 1.0f, "Large" to 1.15f)
private val LINE_SPACINGS = listOf("Compact" to 0.85f, "Default" to 1.0f, "Relaxed" to 1.35f)

@Composable
private fun DisplayCard() {
    SectionCard {
        SectionTitle(Lucide.Sun, "DISPLAY")
        Text("Text size", style = NuruType.controlTitle, color = PROF.navy)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TEXT_SIZES.forEach { (label, scale) ->
                val selected = kotlin.math.abs(AppPrefs.textScale - scale) < 0.01f
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) PROF.goldChipBg else PROF.surface)
                        .border(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) PROF.gold else PROF.border,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { AppPrefs.updateTextScale(scale) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = pInter(14, if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (selected) PROF.navy else PROF.sub,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Adjusts text size across the whole app.", style = pInter(11), color = PROF.sub)

        Spacer(Modifier.height(16.dp))
        Text("Line spacing", style = NuruType.controlTitle, color = PROF.navy)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LINE_SPACINGS.forEach { (label, spacing) ->
                val selected = kotlin.math.abs(AppPrefs.lineSpacing - spacing) < 0.01f
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) PROF.goldChipBg else PROF.surface)
                        .border(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) PROF.gold else PROF.border,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { AppPrefs.updateLineSpacing(spacing) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = pInter(14, if (selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (selected) PROF.navy else PROF.sub,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Adjusts line spacing across the whole app.", style = pInter(11), color = PROF.sub)
    }
}

@Composable
private fun LanguageCard() {
    SectionCard {
        SectionTitle(Lucide.Languages, "LANGUAGE")
        ActionRow(
            tile = { IconTile(Lucide.Languages, TINT_LANGUAGE) },
            title = "Language",
            subtitle = "App language · English",
            onClick = {},
        )
    }
}

@Composable
private fun PrivacyCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Server first (EXPERIENCE.md §7.4, owner 2026-10-05): the switch shows
    // what the server holds. It moves once the server has the change; on a
    // failure it stays as it was and the line under it says why.
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    fun change(want: Boolean) {
        if (saving) return
        saving = true
        failure = null
        scope.launch {
            when (val r = org.nuruplace.member.feature.shell.LocationSharing.change(context, want)) {
                is org.nuruplace.member.feature.shell.LocationShareResult.Saved -> AppPrefs.updateShareLocation(r.sharing)
                is org.nuruplace.member.feature.shell.LocationShareResult.Failed -> failure = r.line
            }
            saving = false
        }
    }

    val askPerm = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) change(true)
    }

    SectionCard {
        SectionTitle(Lucide.MapPin, "PRIVACY")
        ToggleRow(
            tile = { NeutralTile(Lucide.MapPin) },
            title = "Share my approximate location",
            subtitle = "Helps you connect with believers near you. Approximate only; you can turn this off anytime.",
            checked = AppPrefs.shareLocation,
            enabled = !saving,
            failure = failure,
            onCheckedChange = { want ->
                if (want) {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) change(true) else askPerm.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                } else {
                    change(false)
                }
            },
        )
    }
}

@Composable
private fun HelpCard() {
    SectionCard {
        SectionTitle(Lucide.CircleHelp, "HELP & PRIVACY")
        ActionRow(
            tile = { IconTile(Lucide.CircleHelp, TINT_HELP) },
            title = "Help & support",
            subtitle = "FAQs, contact us",
            onClick = {},
        )
        RowDivider()
        ActionRow(
            tile = { IconTile(Lucide.Shield, TINT_PRIVACY) },
            title = "Privacy policy",
            subtitle = "How we handle your data",
            onClick = {},
        )
    }
}

@Composable
private fun ActionsRow() {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    // At least 56 dp, and as tall as a wrapped label needs — at the largest
    // text size "Delete account" takes two lines and was cut at 56 (§9.6 #4);
    // the two stay one height.
    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PROF.white)
                .border(1.dp, PROF.border, RoundedCornerShape(16.dp))
                // Asked first, as on iOS: the answer that ends the session wears
                // the destructive role (EXPERIENCE.md §8.1 rule 4, §8.2 #19).
                .clickable { confirmSignOut = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Lucide.LogOut, PROF.navy, 18.dp)
                Text("Sign out", style = pInter(14, FontWeight.SemiBold), color = PROF.navy)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFEF2F2))
                .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(16.dp))
                .clickable { confirmDelete = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Lucide.Trash2, PROF.danger, 18.dp)
                Text("Delete account", style = pInter(14, FontWeight.SemiBold), color = PROF.danger)
            }
        }
    }
    if (confirmSignOut) {
        org.nuruplace.member.ui.components.NuruAlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out of Nuru Pathway?", style = pInter(16, FontWeight.SemiBold), color = PROF.navy) },
            text = {
                Text("Your progress is saved — you can pick up right where you left off.", style = pInter(13), color = PROF.sub)
            },
            confirmButton = {
                Text(
                    "Sign out",
                    style = pInter(14, FontWeight.SemiBold),
                    color = PROF.danger,
                    modifier = Modifier.clickable {
                        confirmSignOut = false
                        // AuthStore.signOut() through the wired callback: it reads
                        // the refresh token FIRST, revokes it on the server
                        // (POST /auth/logout), then clears this phone. Clearing
                        // the vault here first — as this button did — left
                        // nothing to revoke, so the session lived on server-side.
                        // (No AuthStore yet — never so in a signed-in shell — still
                        // clears this phone.)
                        Net.client.onSessionExpired?.invoke() ?: Net.client.signOutLocally()
                    }.padding(12.dp),
                )
            },
            dismissButton = {
                Text(
                    "Stay signed in",
                    style = pInter(14, FontWeight.SemiBold),
                    color = PROF.navy,
                    modifier = Modifier.clickable { confirmSignOut = false }.padding(12.dp),
                )
            },
        )
    }
    if (confirmDelete) {
        org.nuruplace.member.ui.components.NuruAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account", style = pInter(16, FontWeight.SemiBold), color = PROF.navy) },
            text = {
                Text(
                    "Account deletion isn't available in the app yet. Please contact your discipler or church admin to remove your account.",
                    style = pInter(13),
                    color = PROF.sub,
                )
            },
            confirmButton = {
                Text(
                    "OK",
                    style = pInter(14, FontWeight.SemiBold),
                    color = PROF.navy,
                    modifier = Modifier.clickable { confirmDelete = false }.padding(12.dp),
                )
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Two-factor row — reuses enroll/verify (+ disable) wiring, local on/off state.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TwoFactorRow() {
    val scope = rememberCoroutineScope()
    var twoFAon by remember { mutableStateOf(false) }
    // Seed from the wire: GET /me carries mfa_enabled — the toggle must reflect
    // the account's real state, not always start "off".
    LaunchedEffect(Unit) {
        runCatching { Net.client.api.me().profile.mfaEnabled }.getOrNull()?.let { twoFAon = it }
    }
    var enrollment by remember { mutableStateOf<MfaEnrollment?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val mfaContext = androidx.compose.ui.platform.LocalContext.current

    ToggleRow(
        tile = { IconTile(FingerprintIcon, if (twoFAon) TINT_2FA_ON else TINT_2FA_OFF) },
        title = "Two-factor authentication",
        subtitle = if (twoFAon) "Active · Authenticator app" else "Not enabled · recommended",
        checked = twoFAon,
        onCheckedChange = { want ->
            msg = null
            if (want) {
                // Begin enrollment; the inline panel collects the verification code.
                expanded = true
                if (enrollment == null && !busy) {
                    busy = true
                    scope.launch {
                        val started = runCatching { Net.client.api.enrollMfa() }
                        enrollment = started.getOrNull()
                        // Why it didn't start (§4), not a bare "Try again".
                        started.exceptionOrNull()?.let {
                            expanded = false
                            msg = ApiException.failureLine("Couldn't start two-factor authentication.", it, mfaContext)
                        }
                        busy = false
                    }
                }
            } else if (twoFAon) {
                // Disable requires a current code — expand and prompt for it.
                expanded = true
            } else {
                expanded = false
                enrollment = null
                code = ""
            }
        },
    )

    AnimatedVisibility(visible = expanded) {
        Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
            val e = enrollment
            if (!twoFAon && e != null) {
                Text("Add this secret to your authenticator app, then enter the 6-digit code:", style = pInter(11), color = PROF.sub)
                Spacer(Modifier.height(6.dp))
                Text(e.secret, style = pInter(14, FontWeight.SemiBold), color = PROF.kicker)
            } else if (twoFAon) {
                Text("Enter a current 6-digit code to turn off two-factor authentication.", style = pInter(11), color = PROF.sub)
            } else {
                Text("Preparing your authenticator secret…", style = pInter(11), color = PROF.sub)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                code,
                { code = it },
                label = { Text("6-digit code") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            msg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = pInter(11), color = if (it.startsWith("2FA")) PROF.successText else PROF.danger)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    // The panel's one primary (§8.1 rule 4): gold, navy words.
                    .background(if (code.length >= 6 && !busy) PROF.gold else PROF.surface)
                    .border(1.dp, PROF.border, RoundedCornerShape(14.dp))
                    .clickable(enabled = code.length >= 6 && !busy) {
                        busy = true
                        scope.launch {
                            try {
                                if (twoFAon) {
                                    Net.client.api.disableMfa(MfaCodeBody(code.trim()))
                                    twoFAon = false; msg = null; expanded = false; enrollment = null; code = ""
                                } else {
                                    Net.client.api.verifyMfa(MfaCodeBody(code.trim()))
                                    twoFAon = true; msg = "2FA is now on."; expanded = false; enrollment = null; code = ""
                                }
                            } catch (ex: Exception) {
                                // A wrong code is a 401 in the server's words ("Invalid MFA code").
                                msg = ApiException.message(ex, credentials = true)
                            } finally {
                                busy = false
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (twoFAon) "Verify & disable" else "Verify & enable",
                    style = pInter(14, FontWeight.SemiBold),
                    color = if (code.length >= 6 && !busy) PROF.navy else PROF.sub,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Change password — inline expandable panel (preserves original validation).
// ════════════════════════════════════════════════════════════════════════════

private const val MIN_PASSWORD_LEN = 8

@Composable
private fun ChangePasswordSection() {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    // Success confirmation — replaces the form so the member gets a clear signal.
    if (success) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(PROF.successBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.CheckCircle, PROF.success, 26.dp)
            }
            Text(
                "Your password has been changed.",
                style = pInter(14, FontWeight.SemiBold),
                color = PROF.successText,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Use your new password next time you sign in.",
                style = pInter(11),
                color = PROF.sub,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }

    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(
            "Enter your current password, then choose a new one (at least $MIN_PASSWORD_LEN characters).",
            style = pInter(11),
            color = PROF.sub,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        PasswordField(
            value = current,
            onValueChange = { current = it; error = null },
            label = "Current password",
        )
        Spacer(Modifier.height(8.dp))
        PasswordField(
            value = next,
            onValueChange = { next = it; error = null },
            label = "New password",
        )
        Spacer(Modifier.height(8.dp))
        PasswordField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            label = "Confirm new password",
        )
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = pInter(11), color = PROF.danger)
        }
        Spacer(Modifier.height(10.dp))
        val filled = current.isNotBlank() && next.isNotBlank() && confirm.isNotBlank()
        val enabled = filled && !busy
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (enabled) PROF.gold else PROF.surface)
                .border(1.dp, PROF.border, RoundedCornerShape(14.dp))
                .clickable(enabled = enabled) {
                    when {
                        next.length < MIN_PASSWORD_LEN ->
                            error = "New password must be at least $MIN_PASSWORD_LEN characters."
                        next != confirm ->
                            error = "New passwords don't match."
                        else -> {
                            error = null
                            busy = true
                            scope.launch {
                                try {
                                    Net.client.api.changePassword(ChangePasswordBody(current.trim(), next))
                                    success = true
                                } catch (ex: Exception) {
                                    error = ApiException.message(ex, credentials = true)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = PROF.navy, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    "Change password",
                    style = pInter(14, FontWeight.SemiBold),
                    color = if (enabled) PROF.navy else PROF.sub,
                )
            }
        }
    }
}

/** Password input with a show/hide eye toggle, styled to the profile palette. */
@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = pInter(13)) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Lucide.EyeOff else Lucide.Eye,
                    PROF.sub,
                    22.dp,
                )
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PROF.gold,
            unfocusedBorderColor = PROF.border,
            focusedLabelColor = PROF.kicker,
            cursorColor = PROF.navy,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ════════════════════════════════════════════════════════════════════════════
// Reusable primitives (iOS row/tile/section chrome).
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(PROF.white)
            .border(1.dp, PROF.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(icon: ImageVector, label: String) {
    Row(
        Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, PROF.kicker, 14.dp)
        Text(label, style = pInter(11, FontWeight.Bold, 1.4f), color = PROF.kicker)
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: RowTint) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, tint.fg, 18.dp)
    }
}

/** Every Settings row icon is the same tile (§8.1 rule 7, as iOS): navy on
 *  gold tint — these were cream with a hairline. */
@Composable
private fun NeutralTile(icon: ImageVector) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PROF.goldTint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, PROF.navy, 18.dp)
    }
}

@Composable
private fun ActionRow(
    tile: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tile()
        Column(Modifier.weight(1f)) {
            // A control row's title (§8.1 rule 3): Inter 14 medium.
            Text(title, style = NuruType.controlTitle, color = PROF.navy)
            Text(subtitle, style = pInter(11), color = PROF.sub)
        }
        Icon(Lucide.ChevronRight, PROF.rowLabel, 18.dp)
    }
}

@Composable
private fun ToggleRow(
    tile: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    /** False while the change is on its way to the server. */
    enabled: Boolean = true,
    /** Why the last change didn't save (§4's words) — shown under the row. */
    failure: String? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tile()
            Column(Modifier.weight(1f)) {
                // A control row's title (§8.1 rule 3): Inter 14 medium.
                Text(title, style = NuruType.controlTitle, color = PROF.navy)
                Text(subtitle, style = pInter(11), color = PROF.sub)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = PROF.gold, checkedThumbColor = Color.White),
            )
        }
        failure?.let { Text(it, style = pInter(12), color = Nuru.danger, modifier = Modifier.padding(bottom = 8.dp)) }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PROF.border))
}

// Small typed Icon wrapper — keeps call sites terse (Icon(icon, color, size)).
@Composable
private fun Icon(icon: ImageVector, tint: Color, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.material3.Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

/** "Nuru Pathway · version 2.59.0 (84)" — the name and build this phone runs. */
internal fun appVersionLine(versionName: String, versionCode: Int): String = "Nuru Pathway · version $versionName ($versionCode)"
