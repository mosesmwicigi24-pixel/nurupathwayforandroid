// Session state — the Android counterpart of the iOS AuthStore / RN authSlice.
// Holds the signed-in flag + the /me profile and drives Login ↔ the tab shell.
// On launch, a surviving token in the vault is treated as a live session (a stale
// access token is refreshed on the first request), so we never gate the app on
// the /me round-trip.
package org.nuruplace.member.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.nuruplace.member.data.BroadcastLock
import org.nuruplace.member.data.PastoralLock
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net

class AuthStore : ViewModel() {
    data class State(
        val booting: Boolean = true,
        val authenticated: Boolean = false,
        val me: MeResponse? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        Net.client.onSessionExpired = { signOut() }
        bootstrap()
    }

    private fun bootstrap() = viewModelScope.launch {
        if (Net.client.vault.hasSession) {
            _state.update { it.copy(authenticated = true, booting = false) }
            loadProfile()
        } else {
            _state.update { it.copy(booting = false) }
        }
    }

    /** Called by the Login flow once a full session is in the vault. */
    fun onAuthenticated() = viewModelScope.launch {
        _state.update { it.copy(authenticated = true) }
        loadProfile()
    }

    private suspend fun loadProfile() {
        val me = runCatching { Net.client.api.me() }.getOrNull()
        if (me != null) _state.update { it.copy(me = me) }
    }

    fun signOut() {
        // Best-effort server-side revocation (POST /auth/logout, unauthenticated,
        // takes the refresh token in the body). Capture the token BEFORE clearing;
        // never block sign-out on the network — local sign-out always proceeds.
        val rt = Net.client.vault.refreshToken
        Net.client.vault.clear()
        // A fingerprint-enrolled Broadcast password belongs to THIS account —
        // never let it silently carry over to whoever signs in next on this
        // device.
        BroadcastLock.wipe()
        // Same reasoning, for the pastoral privacy gate (Chat Redesign C3b) —
        // its cached conversation id and mute/archive flags are per-account too.
        PastoralLock.resetForSignOut()
        _state.update { it.copy(authenticated = false, me = null) }
        if (rt != null) {
            viewModelScope.launch {
                runCatching { Net.client.api.logout(org.nuruplace.member.data.net.LogoutBody(rt)) }
            }
        }
    }
}
