// Login — port of the iOS LoginView. Sign-in (with the 2FA code step §5.3),
// Create-account (register), and Forgot-password modes, switchable via links.
package org.nuruplace.member.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.ForgotBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RegisterBody
import org.nuruplace.member.ui.components.BrandMark
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.NuruField
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

private enum class Mode { SIGNIN, REGISTER, FORGOT }

@Composable
fun LoginScreen(onAuthenticated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Mode.SIGNIN) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var mfaToken by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; info = null
        scope.launch {
            try { block() } catch (e: Exception) { error = ApiException.message(e) } finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Nuru.ceremonyGradient).verticalScroll(rememberScrollState())
            .imePadding().padding(horizontal = Spacing.screen, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xl))
        BrandMark(onDark = true)
        // Gold rule (line · dot · line) + missionary caption — Figma brand lockup.
        Spacer(Modifier.height(Spacing.md))
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm),
        ) {
            androidx.compose.foundation.layout.Box(Modifier.width(28.dp).height(1.dp).background(Nuru.gold.copy(alpha = 0.5f)))
            androidx.compose.foundation.layout.Box(Modifier.size(4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Nuru.gold.copy(alpha = 0.7f)))
            androidx.compose.foundation.layout.Box(Modifier.width(28.dp).height(1.dp).background(Nuru.gold.copy(alpha = 0.5f)))
        }
        Spacer(Modifier.height(Spacing.sm))
        Text("A MISSIONARY SENDING CHURCH", style = NuruType.micro, color = Nuru.onNavyFaint)
        Spacer(Modifier.height(Spacing.xl))

        NuruCard(padding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg)) {
            when {
                mfaToken != null -> {
                    Text("Two-factor code", style = NuruType.cardTitle, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Enter the 6-digit code from your authenticator app.", style = NuruType.caption, color = Nuru.ink600)
                    Spacer(Modifier.height(Spacing.base))
                    NuruField(code, { code = it }, "Code", Icons.Outlined.Lock, keyboardType = KeyboardType.Number)
                }
                mode == Mode.SIGNIN -> {
                    Text("Welcome back", style = NuruType.cardTitle, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.base))
                    NuruField(email, { email = it }, "Email", Icons.Outlined.MailOutline, keyboardType = KeyboardType.Email)
                    Spacer(Modifier.height(Spacing.md))
                    NuruField(password, { password = it }, "Password", Icons.Outlined.Lock, isPassword = true)
                }
                mode == Mode.REGISTER -> {
                    Text("Create your account", style = NuruType.cardTitle, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.base))
                    NuruField(fullName, { fullName = it }, "Full name", Icons.Outlined.Person)
                    Spacer(Modifier.height(Spacing.md))
                    NuruField(email, { email = it }, "Email", Icons.Outlined.MailOutline, keyboardType = KeyboardType.Email)
                    Spacer(Modifier.height(Spacing.md))
                    NuruField(password, { password = it }, "Password", Icons.Outlined.Lock, isPassword = true)
                }
                else -> {
                    Text("Reset your password", style = NuruType.cardTitle, color = Nuru.ink)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("We'll send a reset link to your email.", style = NuruType.caption, color = Nuru.ink600)
                    Spacer(Modifier.height(Spacing.base))
                    NuruField(email, { email = it }, "Email", Icons.Outlined.MailOutline, keyboardType = KeyboardType.Email)
                }
            }

            error?.let { Spacer(Modifier.height(Spacing.md)); Text(it, style = NuruType.caption, color = Nuru.danger) }
            info?.let { Spacer(Modifier.height(Spacing.md)); Text(it, style = NuruType.caption, color = Nuru.successText) }

            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                label = when {
                    mfaToken != null -> "Verify"
                    mode == Mode.SIGNIN -> "Sign in"
                    mode == Mode.REGISTER -> "Create account"
                    else -> "Send reset link"
                },
                loading = busy,
                enabled = when {
                    mfaToken != null -> code.length >= 6
                    mode == Mode.SIGNIN -> email.isNotBlank() && password.isNotBlank()
                    mode == Mode.REGISTER -> fullName.isNotBlank() && email.isNotBlank() && password.length >= 6
                    else -> email.isNotBlank()
                },
                onClick = {
                    run {
                        when {
                            mfaToken != null -> { Net.client.completeMfa(mfaToken!!, code.trim()); onAuthenticated() }
                            mode == Mode.SIGNIN -> {
                                val res = Net.client.login(email, password)
                                if (res.session != null) onAuthenticated()
                                else if (res.mfaRequired && res.mfaToken != null) mfaToken = res.mfaToken
                                else error = "Unexpected response. Please try again."
                            }
                            mode == Mode.REGISTER -> {
                                val s = Net.client.api.register(RegisterBody(fullName.trim(), email.trim(), password))
                                Net.client.vault.set(s.accessToken, s.refreshToken); onAuthenticated()
                            }
                            else -> {
                                Net.client.api.forgotPassword(ForgotBody(email.trim()))
                                info = "If that email exists, a reset link is on its way."
                            }
                        }
                    }
                },
            )

            if (mfaToken == null) {
                Spacer(Modifier.height(Spacing.sm))
                when (mode) {
                    Mode.SIGNIN -> {
                        TextButton(onClick = { mode = Mode.REGISTER; error = null }) { Text("Create an account", style = NuruType.cardCta, color = Nuru.goldLo) }
                        TextButton(onClick = { mode = Mode.FORGOT; error = null }) { Text("Forgot password?", style = NuruType.caption, color = Nuru.ink600) }
                    }
                    else -> TextButton(onClick = { mode = Mode.SIGNIN; error = null; info = null }) { Text("Back to sign in", style = NuruType.cardCta, color = Nuru.goldLo) }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}
