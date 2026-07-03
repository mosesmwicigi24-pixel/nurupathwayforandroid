// Secure token vault (§5.7) — the Android counterpart of the iOS KeychainStore /
// RN react-native-keychain. Stores ONLY the access + refresh token pair, in an
// AES-256 EncryptedSharedPreferences backed by the Android Keystore, so tokens
// are encrypted at rest and never leave the device.
package org.nuruplace.member.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenVault(context: Context) {
    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nuru_member_session",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var accessToken: String?
        get() = prefs.getString(KEY_AT, null)
        set(v) = prefs.edit().apply { if (v == null) remove(KEY_AT) else putString(KEY_AT, v) }.apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_RT, null)
        set(v) = prefs.edit().apply { if (v == null) remove(KEY_RT) else putString(KEY_RT, v) }.apply()

    val hasSession: Boolean get() = accessToken != null

    fun set(access: String?, refresh: String?) {
        accessToken = access
        refreshToken = refresh
    }

    fun clear() = set(null, null)

    private companion object {
        const val KEY_AT = "nuru.member.at"
        const val KEY_RT = "nuru.member.rt"
    }
}
