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
    private const val KEY_LINE_SPACING = "nuru.lineSpacing"
    private const val KEY_SHARE_LOCATION = "nuru.privacy.shareLocation"
    private const val KEY_LOCATION_INVITE = "nuru.locationInviteShown"
    private const val KEY_RADIO_REMIND_PREFIX = "nuru.radio.remind."
    private const val KEY_DISCIPLER_REMINDER_DISMISSED_PREFIX = "nuru.discipler.reminder.dismissedAt.level."
    // Broadcast fingerprint unlock (§5.3 step-up, data/BroadcastLock.kt): the
    // step-up password, AES-GCM encrypted behind a biometric-gated Keystore
    // key. NEVER plaintext — unusable without a fresh fingerprint unlocking
    // the key that produced this ciphertext.
    private const val KEY_BROADCAST_CIPHER = "nuru.broadcast.cipher"
    private const val KEY_BROADCAST_IV = "nuru.broadcast.iv"
    // Talk with My Pastor (Chat Redesign C3b) — a pure local privacy gate, no
    // password/crypto behind it (see data/PastoralLock.kt). Also the local,
    // client-side-only mute/archive flags the task spec calls for (no server
    // route exists for either).
    private const val KEY_PASTORAL_BIOMETRIC_ENABLED = "nuru.pastoral.biometricEnabled"
    private const val KEY_PASTORAL_CONVERSATION_ID = "nuru.pastoral.conversationId"
    private const val KEY_PASTORAL_MUTED = "nuru.pastoral.muted"
    private const val KEY_PASTORAL_ARCHIVED = "nuru.pastoral.archived"

    private lateinit var prefs: SharedPreferences

    /** App-wide font scale (1.0 = default). Observed by NuruTheme. */
    var textScale by mutableFloatStateOf(1.0f)
        private set

    /** App-wide line-spacing multiplier (1.0 = default), exactly parallel to
     *  [textScale]: TypeSchema.kt's nuruSans/nuruSerif factories read it when
     *  they build every style's `lineHeight`, so changing it recomposes the
     *  whole tree the same way a font-scale change does. */
    var lineSpacing by mutableFloatStateOf(1.0f)
        private set

    /** Opt-in approximate-location sharing (server keeps only a coarse geohash). */
    var shareLocation by mutableStateOf(false)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        textScale = prefs.getFloat(KEY_TEXT_SCALE, 1.0f)
        lineSpacing = prefs.getFloat(KEY_LINE_SPACING, 1.0f)
        shareLocation = prefs.getBoolean(KEY_SHARE_LOCATION, false)
    }

    fun updateTextScale(scale: Float) {
        textScale = scale
        if (::prefs.isInitialized) prefs.edit().putFloat(KEY_TEXT_SCALE, scale).apply()
    }

    fun updateLineSpacing(spacing: Float) {
        lineSpacing = spacing
        if (::prefs.isInitialized) prefs.edit().putFloat(KEY_LINE_SPACING, spacing).apply()
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

    /** Within-level "walk with your discipler" pop-up (LevelDetailScreen) — the
     *  quiet period after an explicit X dismissal. "At most once per session per
     *  level" is a separate, in-memory rule owned by the screen itself; this is
     *  only the persisted "don't nag" 24h window (iOS UserDefaults parity). */
    fun isDisciplerReminderDismissedRecently(levelNumber: Int): Boolean {
        if (!::prefs.isInitialized) return false
        val ts = prefs.getLong(KEY_DISCIPLER_REMINDER_DISMISSED_PREFIX + levelNumber, 0L)
        if (ts <= 0L) return false
        return System.currentTimeMillis() - ts < 24L * 60 * 60 * 1000
    }

    fun markDisciplerReminderDismissed(levelNumber: Int) {
        if (::prefs.isInitialized) {
            prefs.edit().putLong(KEY_DISCIPLER_REMINDER_DISMISSED_PREFIX + levelNumber, System.currentTimeMillis()).apply()
        }
    }

    // ── Broadcast fingerprint unlock (data/BroadcastLock.kt owns the crypto;
    // this is just its at-rest storage) ──
    val broadcastCipher: String? get() = if (::prefs.isInitialized) prefs.getString(KEY_BROADCAST_CIPHER, null) else null
    val broadcastIv: String? get() = if (::prefs.isInitialized) prefs.getString(KEY_BROADCAST_IV, null) else null
    val broadcastBiometricEnrolled: Boolean get() = !broadcastCipher.isNullOrBlank() && !broadcastIv.isNullOrBlank()

    fun setBroadcastBiometric(cipher: String, iv: String) {
        if (::prefs.isInitialized) prefs.edit().putString(KEY_BROADCAST_CIPHER, cipher).putString(KEY_BROADCAST_IV, iv).apply()
    }

    fun clearBroadcastBiometric() {
        if (::prefs.isInitialized) prefs.edit().remove(KEY_BROADCAST_CIPHER).remove(KEY_BROADCAST_IV).apply()
    }

    // ── Talk with My Pastor local privacy gate + client-only mute/archive ──

    /** Per-device opt-in — off by default (a new gate should never surprise-lock
     *  someone who hasn't asked for it). data/PastoralLock.kt owns the runtime
     *  unlock state; this is only the persisted on/off switch. */
    var pastoralBiometricEnabled: Boolean
        get() = ::prefs.isInitialized && prefs.getBoolean(KEY_PASTORAL_BIOMETRIC_ENABLED, false)
        set(v) { if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_PASTORAL_BIOMETRIC_ENABLED, v).apply() }

    /** Cached once resolved (first tap on the tab, or from the pastor-facing
     *  inbox) so a later Chat-hub load can cross-reference `GET
     *  /chat/conversations`'s unread count for the tab badge WITHOUT ever
     *  eagerly calling the create-or-open POST itself — see PARITY_AUDIT.md,
     *  2026-07-18 entry, for why eager resolution was rejected. */
    var pastoralConversationId: String?
        get() = if (::prefs.isInitialized) prefs.getString(KEY_PASTORAL_CONVERSATION_ID, null) else null
        set(v) { if (::prefs.isInitialized) prefs.edit().putString(KEY_PASTORAL_CONVERSATION_ID, v).apply() }

    var pastoralMuted: Boolean
        get() = ::prefs.isInitialized && prefs.getBoolean(KEY_PASTORAL_MUTED, false)
        set(v) { if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_PASTORAL_MUTED, v).apply() }

    var pastoralArchived: Boolean
        get() = ::prefs.isInitialized && prefs.getBoolean(KEY_PASTORAL_ARCHIVED, false)
        set(v) { if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_PASTORAL_ARCHIVED, v).apply() }

    /** Account sign-out — these are per-device but keyed to whoever is signed
     *  in right now; never let a pastoral cache/flag from account A leak into
     *  account B on a shared device. */
    fun clearPastoralState() {
        if (::prefs.isInitialized) {
            prefs.edit()
                .remove(KEY_PASTORAL_BIOMETRIC_ENABLED)
                .remove(KEY_PASTORAL_CONVERSATION_ID)
                .remove(KEY_PASTORAL_MUTED)
                .remove(KEY_PASTORAL_ARCHIVED)
                .apply()
        }
    }
}
