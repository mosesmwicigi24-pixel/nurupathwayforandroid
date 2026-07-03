// Firebase account — a self-contained proof that Firebase Email/Password auth +
// Firestore work end-to-end, ADDED ALONGSIDE the custom-backend session (it does
// not touch or gate the main app login). Register / sign in / sign out against
// FirebaseAuth; on success, upsert + read back a `members/{uid}` Firestore doc so
// both halves are exercised. Concrete Firebase-owned features build on this.
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import org.nuruplace.member.data.firebase.FirebaseAuthService
import org.nuruplace.member.data.firebase.FirestoreRepo
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun FirebaseAccountScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configured = remember { FirebaseAuthService.isConfigured(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var signedInEmail by remember { mutableStateOf(FirebaseAuthService.currentUser?.email) }
    var profileEcho by remember { mutableStateOf<String?>(null) }

    // On a successful auth, upsert + read back the member's Firestore doc.
    fun afterAuth() {
        signedInEmail = FirebaseAuthService.currentUser?.email
        val uid = FirebaseAuthService.currentUser?.uid ?: return
        scope.launch {
            FirestoreRepo.set("members", uid, mapOf("owner_uid" to uid, "email" to signedInEmail, "updated_at" to System.currentTimeMillis()))
            profileEcho = FirestoreRepo.get("members", uid)?.let { "Firestore members/$uid → ${it["email"]}" }
        }
    }

    fun run(action: suspend () -> Unit, ok: String) {
        if (busy) return
        busy = true; status = null
        scope.launch {
            try { action(); status = ok; afterAuth() }
            catch (e: Exception) { status = e.message ?: "Failed." }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        ScreenHeader("Firebase account", kicker = "Email / password", onBack = onBack)
        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.base)) {
            if (!configured) {
                NuruCard { Text("Firebase isn't configured on this build.", style = NuruType.body, color = Nuru.ink600) }
                return@Column
            }

            NuruCard {
                Kicker("Signed in")
                Spacer(Modifier.height(Spacing.xs))
                Text(signedInEmail ?: "Not signed in", style = NuruType.cardTitle, color = Nuru.ink)
                profileEcho?.let { Text(it, style = NuruType.micro, color = Nuru.success) }
            }

            NuruCard {
                Kicker("Email & password")
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(password, { password = it }, label = { Text("Password (min 6)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(Radii.control), modifier = Modifier.fillMaxWidth())
                status?.let { Spacer(Modifier.height(Spacing.sm)); Text(it, style = NuruType.caption, color = if (it.contains("✓") || it.startsWith("Signed") || it.startsWith("Account")) Nuru.successText else Nuru.danger) }
                Spacer(Modifier.height(Spacing.md))
                PrimaryButton("Sign in", loading = busy, enabled = email.isNotBlank() && password.length >= 6, onClick = {
                    run({ FirebaseAuthService.signIn(email, password) }, "Signed in ✓")
                })
                Spacer(Modifier.height(Spacing.sm))
                PrimaryButton("Create account", loading = busy, enabled = email.isNotBlank() && password.length >= 6, onClick = {
                    run({ FirebaseAuthService.register(email, password) }, "Account created ✓")
                })
            }

            if (signedInEmail != null) {
                PrimaryButton("Sign out", onClick = { FirebaseAuthService.signOut(); signedInEmail = null; profileEcho = null; status = "Signed out." })
            }
        }
    }
}
