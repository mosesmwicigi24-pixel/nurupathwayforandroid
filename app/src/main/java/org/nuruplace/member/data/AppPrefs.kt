// Lightweight, non-sensitive app preferences (plain SharedPreferences — tokens
// live in the encrypted TokenVault). Backs the in-app text-size control: the
// scale is a Compose state so changing it re-composes the whole tree instantly,
// and is persisted so it survives restarts. Mirrors the iOS @AppStorage
// textScale + Nuru.textScaleKey.
package org.nuruplace.member.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppPrefs {
    private const val FILE = "nuru_member_prefs"
    private const val KEY_TEXT_SCALE = "nuru.textScale"
    private const val KEY_SHARE_LOCATION = "nuru.privacy.shareLocation"
    private const val KEY_LOCATION_INVITE = "nuru.locationInviteShown"
    private const val KEY_RADIO_REMIND_PREFIX = "nuru.radio.remind."

    private lateinit var prefs: SharedPreferences

    /** App-wide font scale (1.0 = default). Observed by NuruTheme. */
    var textScale by mutableFloatStateOf(1.0f)
        private set

    /** Opt-in approximate-location sharing (server keeps only a coarse geohash). */
    var shareLocation by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.0f)
        shareLocation = prefs.getBoolean(KEY_SHARE_LOCATION, false)
    }

    fun updateTextScale(scale: Float) {
        textScale = scale
        if (::prefs.isInitialized) prefs.edit().putFloat(KEY_TEXT_SCALE, scale).apply()
    }

    fun updateShareLocation(on: Boolean) {
        shareLocation = on
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_SHARE_LOCATION, on).apply()
    }

    /** One-time location-first onboarding invite (shown right after first login). */
    var locationInviteShown: Boolean
        get() = ::prefs.isInitialized && prefs.getBoolean(KEY_LOCATION_INVITE, false)
        set(v) { if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_LOCATION_INVITE, v).apply() }

    /** "Remind me when we're live" — per-program toggle (iOS RemindMeCTA parity,
     *  keyed by radio program id so it naturally resets once a new show is up next). */
    fun isRadioReminderSet(programId: String): Boolean =
        ::prefs.isInitialized && prefs.getBoolean(KEY_RADIO_REMIND_PREFIX + programId, false)

    fun setRadioReminder(programId: String, on: Boolean) {
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_RADIO_REMIND_PREFIX + programId, on).apply()
    }
}
