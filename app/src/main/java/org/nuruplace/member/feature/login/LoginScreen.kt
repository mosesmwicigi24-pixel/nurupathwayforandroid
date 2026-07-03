// Login — the port of the iOS LoginView sign-in mode: gold-crossed wordmark on a
// navy ceremony gradient, labeled email/password fields, and the 2FA code step
// when the account has it enabled. Server-authoritative: the backend decides 2FA
// (§5.3); this screen just carries the challenge token to the second call.
package org.nuruplace.member.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.BrandMark
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.NuruField
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun LoginScreen(onAuthenticated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var mfaToken by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val token = mfaToken
                if (token == null) {
                    val res = Net.client.login(email, password)
                    if (res.session != null) onAuthenticated()
                    else if (res.mfaRequired && res.mfaToken != null) mfaToken = res.mfaToken
                    else error = "Unexpected response. Please try again."
                } else {
                    Net.client.completeMfa(token, code.trim())
                    onAuthenticated()
                }
            } catch (e: Exception) {
                error = ApiException.message(e)
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Nuru.ceremonyGradient)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = Spacing.screen, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xl))
        BrandMark(onDark = true)
        Spacer(Modifier.height(Spacing.sm))
        Text("Discipleship Pathway", style = NuruType.caption, color = Nuru.onNavyDim)
        Spacer(Modifier.height(Spacing.xl))

        NuruCard(padding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg)) {
            if (mfaToken == null) {
                Text("Welcome back", style = NuruType.cardTitle, color = Nuru.ink)
                Spacer(Modifier.height(Spacing.base))
                NuruField(email, { email = it }, "Email", Icons.Outlined.MailOutline, keyboardType = KeyboardType.Email)
                Spacer(Modifier.height(Spacing.md))
                NuruField(password, { password = it }, "Password", Icons.Outlined.Lock, isPassword = true)
            } else {
                Text("Two-factor code", style = NuruType.cardTitle, color = Nuru.ink)
                Spacer(Modifier.height(Spacing.xs))
                Text("Enter the 6-digit code from your authenticator app.", style = NuruType.caption, color = Nuru.ink600)
                Spacer(Modifier.height(Spacing.base))
                NuruField(code, { code = it }, "Code", Icons.Outlined.Lock, keyboardType = KeyboardType.Number)
            }

            error?.let {
                Spacer(Modifier.height(Spacing.md))
                Text(it, style = NuruType.caption, color = Nuru.danger)
            }

            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                label = if (mfaToken == null) "Sign in" else "Verify",
                onClick = { submit() },
                enabled = if (mfaToken == null) email.isNotBlank() && password.isNotBlank() else code.length >= 6,
                loading = busy,
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}
