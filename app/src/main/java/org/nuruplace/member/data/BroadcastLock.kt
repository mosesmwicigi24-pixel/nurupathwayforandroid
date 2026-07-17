// The lock on the Broadcast door — the Android twin of iOS BroadcastLock.swift.
//
// The SERVER only opens the broadcast routes (composer, sent list, response
// wall) for a token carrying a fresh password confirmation (§5.3). A
// fingerprint can't answer that on its own — the server needs the actual
// password — so biometrics work the way a bank app's do: the FIRST typed
// unlock may store the password ENCRYPTED behind a Keystore AES key that only
// releases for the owner's CURRENT fingerprint set
// (setInvalidatedByBiometricEnrollment — enrolling a new print invalidates the
// key, the same guarantee as iOS's biometryCurrentSet: someone added to the
// phone later never inherits the Broadcast). From then on, a step-up raises
// BiometricPrompt, the Keystore-bound key decrypts the password, and it is
// confirmed with the server silently. The server stays authoritative —
// biometrics only ever carry the password out of hardware it can't leave.
//
// The password is NEVER stored in plaintext anywhere: AppPrefs holds only the
// AES-GCM ciphertext + its IV, and the key that could ever decrypt it lives in
// the Android Keystore, gated on a fresh biometric per use.
package org.nuruplace.member.data

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

object BroadcastLock {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "nuru.broadcast.stepup"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    /** Stored ciphertext present → a password is enrolled behind this device's
     *  current biometrics. Doesn't touch hardware — safe to read on every render. */
    val isEnrolled: Boolean get() = AppPrefs.broadcastBiometricEnrolled

    /** Whether this device can do STRONG biometrics (fingerprint/face backed
     *  by hardware) right now — used to decide whether to even offer "unlock
     *  with fingerprint next time". */
    fun biometricAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    /** Encrypt + store [password] behind the owner's CURRENT fingerprint set.
     *  Raises BiometricPrompt to authorize the encryption itself, so storing
     *  it is itself proof a real fingerprint was there. Returns false (and
     *  stores nothing) on any cancel/failure. */
    suspend fun enroll(activity: FragmentActivity, password: String): Boolean {
        wipe()   // a fresh key every time — mirrors iOS's remove-then-add
        val key = try { createKey() } catch (_: Exception) { return false }
        val cipher = try {
            Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (_: Exception) { return false }
        val authed = authenticate(activity, cipher, "Protect the Broadcast with your fingerprint") ?: return false
        val authedCipher = authed.cipher ?: return false
        return try {
            val bytes = authedCipher.doFinal(password.toByteArray(Charsets.UTF_8))
            AppPrefs.setBroadcastBiometric(
                cipher = Base64.encodeToString(bytes, Base64.NO_WRAP),
                iv = Base64.encodeToString(authedCipher.iv, Base64.NO_WRAP),
            )
            true
        } catch (_: Exception) {
            wipe()
            false
        }
    }

    /** Raise the fingerprint prompt and decrypt the stored password. Null
     *  means "fall back to the typed sheet" — cancelled, not recognised, or
     *  nothing enrolled. The stored copy is wiped only when it can never work
     *  again: the Keystore key was invalidated (a new fingerprint was
     *  enrolled) or the ciphertext itself won't decrypt. A mere cancel or
     *  a not-recognised face does NOT wipe it — that's just "not now". */
    suspend fun unlock(activity: FragmentActivity): String? {
        val ivB64 = AppPrefs.broadcastIv ?: return null
        val cipherB64 = AppPrefs.broadcastCipher ?: return null
        val key = existingKey() ?: run { wipe(); return null }
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val cipher = try {
            Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            wipe(); return null
        } catch (_: Exception) {
            wipe(); return null
        }
        val authed = authenticate(activity, cipher, "Open the Broadcast") ?: return null
        val authedCipher = authed.cipher ?: return null
        return try {
            String(authedCipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            wipe()
            null
        }
    }

    /** Drop the stored password — after a wrong-password 401 (it isn't the
     *  account's password anymore), a Keystore invalidation, or a fresh
     *  enrollment about to replace it. */
    fun wipe() {
        AppPrefs.clearBroadcastBiometric()
        runCatching {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        }
    }

    private fun createKey(): SecretKey {
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            // A new fingerprint enrolled = possibly a different person holding
            // the phone — the key (and so the password behind it) dies with
            // the old fingerprint set. Never inherited, exactly iOS's
            // biometryCurrentSet.
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
        kg.init(builder.build())
        return kg.generateKey()
    }

    private fun existingKey(): SecretKey? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    /** Raise BiometricPrompt bound to [cipher] and suspend until the system UI
     *  resolves — success (CryptoObject unlocked), or null on any cancel/
     *  error (a single non-match keeps the prompt open; only a terminal
     *  onAuthenticationError ends it). */
    private suspend fun authenticate(activity: FragmentActivity, cipher: Cipher, title: String): BiometricPrompt.CryptoObject? =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(result.cryptoObject)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(null)
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText("Use password")
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
        }
}
