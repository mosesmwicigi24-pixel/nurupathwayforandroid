// Wire DTOs — Kotlin mirrors of the shared contract (packages/shared dto.ts),
// the same models the iOS app ports. The Json config uses SnakeCase naming so
// snake_case JSON maps to these camelCase properties automatically.
package org.nuruplace.member.data.net

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String? = null,
    val expiresIn: Int? = null,
)

/** /auth/login returns EITHER a session or a 2FA challenge — one lenient shape. */
@Serializable
data class LoginResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val mfaRequired: Boolean = false,
    val mfaToken: String? = null,
) {
    val session: Session?
        get() = if (accessToken != null && refreshToken != null) Session(accessToken, refreshToken) else null
}

@Serializable
data class UserProfile(
    val userId: String,
    val fullName: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val dateOfBirth: String? = null,
    val yearOfSalvation: Int? = null,
    val isBaptized: Boolean = false,
    val cellGroupId: String? = null,
    val congregationId: String? = null,
    val role: String = "Student",
    val timezone: String = "Africa/Nairobi",
    val locale: String = "en",
    val isMinor: Boolean = false,
    val gender: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val avatarUrl: String? = null,
    val mfaEnabled: Boolean? = null,
    // Full getMe wire shape (identity/service.ts) — served but previously dropped.
    // socials is a free-form JSON object ({platform: handle}); role_keys are the
    // RBAC grants behind `role`; account_status is active|suspended|….
    val socials: kotlinx.serialization.json.JsonObject? = null,
    val accountStatus: String? = null,
    @kotlinx.serialization.SerialName("require_2fa") val require2fa: Boolean? = null,
    val createdAt: String? = null,
    val roleKeys: List<String> = emptyList(),
    // Nuru Live (L3) — RBAC grants as flat strings (e.g. "live:go", "live:manage").
    // "permissions" has no internal camelCase hump for SnakeCase to rewrite, so
    // (like roleKeys → role_keys) no explicit @SerialName is needed — it maps
    // to the wire's "permissions" key automatically.
    val permissions: List<String> = emptyList(),
    val rowVersion: Int = 0,
)

@Serializable
data class EnrollmentSummary(
    val enrollmentId: String,
    val currentLevel: Int,
    val state: String,
    val startedAt: String? = null,
)

@Serializable
data class MeResponse(
    val profile: UserProfile,
    val enrollment: EnrollmentSummary? = null,
)

@Serializable
data class RhythmToday(
    val prayer: Boolean = false,
    val word: Boolean = false,
    val reflection: Boolean = false,
) {
    val doneCount: Int get() = (if (prayer) 1 else 0) + (if (word) 1 else 0) + (if (reflection) 1 else 0)
}

// Request bodies
@Serializable
data class LoginBody(val email: String, val password: String)

@Serializable
data class MfaBody(val mfaToken: String, val code: String)

@Serializable
data class RefreshBody(val refreshToken: String)

/** POST /auth/logout — best-effort refresh-token revocation on sign-out. */
@Serializable
data class LogoutBody(val refreshToken: String)

/** PATCH /me response — the server returns only the concurrency handle. */
@Serializable
data class UpdateMeRes(val userId: String, val rowVersion: Int)
