// Root switch — Login ↔ authed shell, driven by AuthStore, the port of the iOS
// RootView. Phase 0 is a single Home destination; the five-tab shell (Home ·
// Pathway · Grow · Community · Profile) arrives with those features.
package org.nuruplace.member.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.nuruplace.member.auth.AuthStore
import org.nuruplace.member.feature.login.LoginScreen
import org.nuruplace.member.ui.theme.Nuru

@Composable
fun RootScaffold(auth: AuthStore = viewModel()) {
    val state by auth.state.collectAsStateWithLifecycle()

    when {
        state.booting -> Box(
            Modifier.fillMaxSize().background(Nuru.ceremonyGradient),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = Nuru.gold) }

        !state.authenticated -> LoginScreen(onAuthenticated = { auth.onAuthenticated() })

        else -> MainShell(auth = auth, me = state.me)
    }
}
