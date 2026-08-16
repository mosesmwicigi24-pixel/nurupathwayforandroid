// The local privacy gate on "Talk with My Pastor" (Chat Redesign C3b,
// docs/CHAT_REDESIGN.md: "THE BIOMETRIC LOCK MOVES HERE (from Broadcast)").
//
// Unlike data/BroadcastLock.kt, this protects nothing the SERVER needs proof
// of — POST /chat/pastoral has no password step-up (confirmed by reading
// pastoral/index.ts: only the pastor-facing GET /chat/pastoral/inbox is
// step-up gated, not the member's own thread). So there is no password to
// carry out of hardware, no CryptoObject, no Keystore-bound key: this is a
// pure "is the person holding this phone allowed to see the screen right
// now" gate, matching the iOS pairing's `.deviceOwnerAuthentication` LAContext
// policy — biometric OR device passcode/PIN/pattern, never biometric-only
// (a member without enrolled biometrics must still be able to use the gate).
//
// `enabled` is the persisted per-device opt-in (AppPrefs.pastoralBiometricEnabled,
// off by default — a brand-new gate should never silently start locking a
// screen nobody asked to protect). `unlocked` is intentionally NOT persisted:
// it lives only in process memory, so a process restart always starts locked
// (covers "re-auth on restart" for free), and re-locks explicitly on the
// screen's own ON_STOP (app backgrounded — see ChatThreadScreen's
// LifecycleEventEffect) and on sign-out (AuthStore.signOut).
package org.nuruplace.member.data

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object PastoralLock {
    private val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /** Persisted per-device toggle — the ⋮ menu's "Enable/Disable biometric". */
    var enabled: Boolean
        get() = AppPrefs.pastoralBiometricEnabled
        set(v) { AppPrefs.pastoralBiometricEnabled = v; if (!v) unlocked = true }

    /** In-memory only — never written to disk. Compose-observable so the
     *  thread screen recomposes into/out of the gate the instant it changes. */
    var unlocked by mutableStateOf(false)
        private set

    /** Whether this device can satisfy [ALLOWED_AUTHENTICATORS] at all right
     *  now (biometric enrolled OR a device PIN/pattern/password is set). Used
     *  to decide whether the "Enable biometric lock" menu item is offered —
     *  offering a gate the device can never satisfy would strand the member
     *  outside their own pastoral thread. */
    fun available(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** "Lock now" — the ⋮ menu action, and every re-entry gate. */
    fun lock() { unlocked = false }

    /** Raise the OS prompt (fingerprint/face/PIN/pattern/password, whichever
     *  the device offers) and mark the gate open on success. No CryptoObject:
     *  there is nothing to decrypt, only a yes/no "is this the owner" answer. */
    suspend fun unlock(activity: FragmentActivity): Boolean {
        val ok = authenticate(activity)
        if (ok) unlocked = true
        return ok
    }

    /** Sign-out / account switch — the gate itself, the cached conversation id,
     *  and the mute/archive flags are all per-account, never inherited by
     *  whoever signs in next on this device. */
    fun resetForSignOut() {
        unlocked = false
        AppPrefs.clearPastoralState()
    }

    private suspend fun authenticate(activity: FragmentActivity): Boolean =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Talk with My Pastor")
                .setSubtitle("This conversation is private")
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
        }
}
