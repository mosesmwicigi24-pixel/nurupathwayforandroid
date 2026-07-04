// Login — a faithful port of the iOS LoginView. A solid navy (#081C36) screen,
// thirds layout: gold cross-in-tile + "Nuru Place" serif wordmark + keyline in the
// upper region; dark translucent inset fields, a gold "Remember me" checkbox and a
// gold "Log in" button in the lower region. Modes: sign-in · register · forgot · mfa.
package org.nuruplace.member.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.ForgotBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.RegisterBody
import org.nuruplace.member.ui.theme.Fraunces
import org.nuruplace.member.ui.theme.Inter
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
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
    var showPw by remember { mutableStateOf(false) }
    var remember_ by remember { mutableStateOf(true) }
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
        Modifier.fillMaxSize().background(Nuru.navyCeremony)
            .imePadding().padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Upper third — brand (Nuru Place · missionary caption) ──
        Spacer(Modifier.weight(0.55f))
        CrossMark()
        Text(
            "Nuru Place",
            style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, letterSpacing = (-1.08).sp),
            color = Color.White,
            modifier = Modifier.padding(top = Spacing.base),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(top = 14.dp),
        ) {
            Box(Modifier.width(28.dp).height(1.dp).alpha(0.6f).background(Brush.horizontalGradient(listOf(Color.Transparent, Nuru.gold))))
            Box(Modifier.size(4.dp).clip(CircleShape).background(Nuru.gold.copy(alpha = 0.7f)))
            Box(Modifier.width(28.dp).height(1.dp).alpha(0.6f).background(Brush.horizontalGradient(listOf(Nuru.gold, Color.Transparent))))
        }
        Text(
            "A MISSIONARY SENDING CHURCH",
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.8.sp),
            color = Color.White.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 12.dp),
        )

        // ── Flexible gap → pushes the form into the lower third ──
        Spacer(Modifier.weight(1f))

        if (mfaToken != null || mode != Mode.SIGNIN) {
            SecondaryHeader(
                heading = when {
                    mfaToken != null -> "Two-factor code"
                    mode == Mode.REGISTER -> "Create account"
                    else -> "Reset password"
                },
                subhead = when {
                    mfaToken != null -> "Enter the 6-digit code from your authenticator app, or a recovery code."
                    mode == Mode.REGISTER -> "Begin your discipleship journey on Pathway."
                    else -> "Enter your account email and we'll send you a reset link."
                },
                onBack = { mode = Mode.SIGNIN; mfaToken = null; error = null; info = null },
            )
            Spacer(Modifier.height(Spacing.xl))
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            when {
                mfaToken != null -> DarkField("VERIFICATION CODE", code, { code = it }, Icons.Outlined.Lock, "123456", keyboardType = KeyboardType.Number)
                mode == Mode.REGISTER -> {
                    DarkField("FULL NAME", fullName, { fullName = it }, Icons.Outlined.Person, "Your name")
                    DarkField("EMAIL ADDRESS", email, { email = it }, Icons.Outlined.MailOutline, "name@email.com", keyboardType = KeyboardType.Email)
                    DarkField("PASSWORD", password, { password = it }, Icons.Outlined.Lock, "At least 8 characters", isPassword = true, showPw = showPw, onTogglePw = { showPw = !showPw })
                }
                mode == Mode.FORGOT -> DarkField("EMAIL ADDRESS", email, { email = it }, Icons.Outlined.MailOutline, "name@email.com", keyboardType = KeyboardType.Email)
                else -> {
                    DarkField("EMAIL ADDRESS", email, { email = it }, Icons.Outlined.MailOutline, "name@email.com", keyboardType = KeyboardType.Email)
                    DarkField("PASSWORD", password, { password = it }, Icons.Outlined.Lock, "••••••••", isPassword = true, showPw = showPw, onTogglePw = { showPw = !showPw })
                }
            }

            if (mode == Mode.SIGNIN && mfaToken == null) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { remember_ = !remember_ },
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                                .background(if (remember_) Nuru.gold else Color.Transparent)
                                .border(1.5.dp, if (remember_) Nuru.gold else Color.White.copy(alpha = 0.30f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (remember_) Icon(Icons.Filled.Check, null, tint = Nuru.navy, modifier = Modifier.size(13.dp)) }
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Remember me", style = NuruType.caption, color = Nuru.onNavyDim)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Forgot your password?",
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                        color = Nuru.gold,
                        modifier = Modifier.clickable { mode = Mode.FORGOT; error = null },
                    )
                }
            }

            error?.let { Text(it, style = NuruType.caption, color = Nuru.danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            info?.let { if (error == null) Text(it, style = NuruType.caption, color = Nuru.gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

            GoldButton(
                label = when {
                    mfaToken != null -> if (busy) "Verifying…" else "Verify & sign in"
                    mode == Mode.SIGNIN -> if (busy) "Signing in…" else "Log in"
                    mode == Mode.REGISTER -> if (busy) "Creating…" else "Create account"
                    else -> if (busy) "Sending…" else "Send reset link"
                },
                loading = busy,
                enabled = true, // iOS keeps the gold button bright; validate on tap
                onClick = {
                    val v: String? = when {
                        mfaToken != null -> if (code.trim().length < 6) "Enter your 6-digit code." else null
                        mode == Mode.SIGNIN -> if (email.isBlank() || password.isBlank()) "Enter your email and password." else null
                        mode == Mode.REGISTER -> when {
                            fullName.isBlank() -> "Enter your full name."
                            email.isBlank() -> "Enter your email."
                            password.length < 8 -> "Password must be at least 8 characters."
                            else -> null
                        }
                        else -> if (email.isBlank()) "Enter your account email." else null
                    }
                    if (v != null) error = v else run {
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
                                info = "If an account exists for that email, a reset link is on its way."
                            }
                        }
                    }
                },
            )
        }

        // Footer
        Spacer(Modifier.height(Spacing.xl))
        if (mfaToken == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (mode == Mode.SIGNIN) {
                    Text("Don't have an account?", style = NuruType.caption, color = Nuru.onNavyDim)
                    Text("Sign up", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.sp), color = Nuru.gold,
                        modifier = Modifier.clickable { mode = Mode.REGISTER; error = null })
                } else {
                    Text("Remembered it?", style = NuruType.caption, color = Nuru.onNavyDim)
                    Text("Log in", style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.sp), color = Nuru.gold,
                        modifier = Modifier.clickable { mode = Mode.SIGNIN; error = null; info = null })
                }
            }
        }
        Spacer(Modifier.height(44.dp))
    }
}

/** The gold cross in a dark rounded tile — exact Figma mark (upright + arm + faded cap). */
@Composable
private fun CrossMark() {
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(22.dp))
            .background(Nuru.gold.copy(alpha = 0.10f))
            .border(1.dp, Nuru.gold.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 4.dp, height = 26.dp).clip(RoundedCornerShape(2.dp)).background(Nuru.gold))
            Box(Modifier.offset(y = (-2).dp).size(width = 22.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Nuru.gold))
            Box(Modifier.offset(y = (-13).dp).size(width = 8.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Nuru.gold.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun SecondaryHeader(heading: String, subhead: String, onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable { onBack() }.padding(bottom = Spacing.md),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Text("Back", style = NuruType.caption, color = Color.White)
        }
        Text(heading, style = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Bold, fontSize = 26.sp), color = Color.White, textAlign = TextAlign.Center)
        Text(subhead, style = NuruType.caption, color = Nuru.onNavyDim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Dark translucent inset field — white@6% fill, white@14% stroke, gold when focused. */
@Composable
private fun DarkField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String,
    isPassword: Boolean = false,
    showPw: Boolean = false,
    onTogglePw: (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Radii.control)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.6.sp),
            color = Nuru.onNavyDim,
        )
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .background(Color.White.copy(alpha = if (focused) 0.09f else 0.06f))
                .border(1.dp, if (focused) Nuru.gold.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f), shape)
                .padding(horizontal = Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (focused) Nuru.gold.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.40f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    textStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, color = Color.White),
                    singleLine = true,
                    cursorBrush = SolidColor(Nuru.gold),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    visualTransformation = if (isPassword && !showPw) PasswordVisualTransformation() else VisualTransformation.None,
                    interactionSource = interaction,
                    decorationBox = { inner ->
                        if (value.isEmpty()) Text(placeholder, style = TextStyle(fontFamily = Inter, fontSize = 16.sp), color = Color.White.copy(alpha = 0.40f))
                        inner()
                    },
                )
            }
            if (isPassword && onTogglePw != null) {
                Box(Modifier.size(36.dp).clickable { onTogglePw() }, contentAlignment = Alignment.Center) {
                    Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password", tint = Color.White.copy(alpha = 0.40f), modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

/** Full-width gold primary button — "Log in". */
@Composable
private fun GoldButton(label: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(Spacing.buttonLg).clip(RoundedCornerShape(Radii.control))
            .alpha(if (enabled) 1f else 0.5f)
            .background(Nuru.goldGradient)
            .clickable(enabled = enabled && !loading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        else Text(label, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = Color.White)
    }
}
