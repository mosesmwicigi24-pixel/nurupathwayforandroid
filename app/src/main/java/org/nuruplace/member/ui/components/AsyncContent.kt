// The one look for a screen's loading, empty and failed states — pathway
// docs/EXPERIENCE.md §4 ("one shared view renders loading, empty and error
// states, full width, on every screen") — and the tiny load/error/retry
// scaffold most data-backed screens use, so each screen stays focused on its
// content. AsyncContent loads via a suspend lambda keyed on an id; a failure
// shows the shared state card in the state language (StateLanguage.kt), never
// raw server or exception text. Screens can hand in a `loading` skeleton
// (instead of the spinner) and opt into pull-to-refresh with `refreshable = true`.
// Screens that load on their own (the Pathway hub, Plans) use StateCard /
// FailedState directly, so every state looks and reads the same.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.StateAction
import org.nuruplace.member.data.net.StateMessage
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import kotlin.coroutines.cancellation.CancellationException

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ok<T>(val value: T) : LoadState<T>
    data class Err(val message: StateMessage) : LoadState<Nothing>
}

/** The brand pull-to-refresh surface — Material3 PullToRefreshBox with the gold
 *  indicator on white. Wrap the screen's scrollable; `onRefresh` should flip
 *  `refreshing` true and kick the reload (and the load path flips it back). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuruRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                color = Nuru.gold,
                containerColor = Nuru.white,
            )
        },
        content = content,
    )
}

/**
 * The shared state card (§4): full width, the app's white card — a title, a
 * line, one primary action and, where needed, a quiet secondary one. Every
 * loading-failed, empty and offline state renders through this, so a member
 * meets one look and one voice everywhere.
 */
@Composable
fun StateCard(
    title: String,
    modifier: Modifier = Modifier,
    line: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    NuruCard(modifier = modifier.fillMaxWidth(), padding = PaddingValues(Spacing.lg)) {
        Text(title, style = NuruType.cardTitle, color = Nuru.ink)
        line?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(it, style = NuruType.body, color = Nuru.ink600)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.base))
            PrimaryButton(actionLabel, onClick = onAction)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(Spacing.xs))
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                Text(secondaryLabel, style = NuruType.cardCta, color = Nuru.ink600)
            }
        }
    }
}

/**
 * A failed load in the state language: [message]'s title and line, and its
 * one action — Try again ([onRetry]), Sign in (back to the sign-in screen by
 * the existing sign-out path) or Go back ([onBack], else the system back).
 * [onBack] also adds a quiet "Go back" under any other action, for a screen
 * that would otherwise trap the member (no header of its own).
 */
@Composable
fun FailedState(
    message: StateMessage,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val backDispatcher =
        androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val back: () -> Unit = onBack ?: { backDispatcher?.onBackPressed() }
    val primary: (() -> Unit)? = when (message.action) {
        StateAction.RETRY -> onRetry
        StateAction.SIGN_IN -> ::signInAgain
        StateAction.BACK -> back
        StateAction.NONE -> null
    }
    StateCard(
        title = message.title,
        line = message.line,
        modifier = modifier,
        actionLabel = message.action.label.takeIf { primary != null },
        onAction = primary,
        secondaryLabel = StateAction.BACK.label.takeIf { onBack != null && message.action != StateAction.BACK },
        onSecondary = back.takeIf { onBack != null && message.action != StateAction.BACK },
    )
}

/** Sign in again: the app's existing sign-out-to-login path (AuthStore.signOut
 *  via ApiClient.onSessionExpired) — the same one Settings' Sign out takes. */
fun signInAgain() {
    Net.client.onSessionExpired?.invoke()
}

/** [offerBack]: the failed state adds a quiet "Go back" (default) — a tab
 *  root, with nowhere to go back to, turns it off. */
@Composable
fun <T> AsyncContent(
    key: Any? = Unit,
    load: suspend () -> T,
    loading: (@Composable () -> Unit)? = null,
    refreshable: Boolean = false,
    offerBack: Boolean = true,
    content: @Composable (value: T, reload: () -> Unit) -> Unit,
) {
    var state by remember(key) { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    var refreshing by remember(key) { mutableStateOf(false) }
    val reload: () -> Unit = { attempt++ }
    val context = LocalContext.current

    LaunchedEffect(key, attempt) {
        // A reload while content is already showing refreshes IN PLACE (pull-to-
        // refresh, post-mutation reloads) — no jarring swap back to the loading
        // state; a failed refresh quietly keeps the content it has.
        val keep = state is LoadState.Ok
        if (keep) refreshing = attempt > 0 else state = LoadState.Loading
        try {
            state = LoadState.Ok(load())
        } catch (e: CancellationException) {
            throw e   // the screen left (or a newer load began) — not a failure
        } catch (e: Exception) {
            if (!keep) state = LoadState.Err(ApiException.state(e, context))
        } finally {
            refreshing = false
        }
    }

    when (val s = state) {
        is LoadState.Loading -> loading?.invoke() ?: Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Nuru.gold)
        }
        // The error state must NEVER trap the member: screens whose whole body
        // is AsyncContent lose their own header here, so it always offers a
        // way back via the activity's back dispatcher (fixes the "failed quiz
        // / missing event → restart the app" trap).
        is LoadState.Err -> Box(Modifier.fillMaxSize().padding(Spacing.screen), contentAlignment = Alignment.Center) {
            val backDispatcher =
                androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                FailedState(
                    s.message,
                    onRetry = { attempt++ },
                    onBack = if (offerBack) ({ backDispatcher?.onBackPressed() }) else null,
                )
            }
        }
        is LoadState.Ok ->
            if (refreshable) {
                NuruRefreshBox(refreshing = refreshing, onRefresh = reload) { content(s.value, reload) }
            } else {
                content(s.value, reload)
            }
    }
}
