// Tiny load/error/retry scaffold used across data-backed screens, so each screen
// stays focused on its content. Loads via a suspend lambda keyed on an id; the
// error state offers a retry. Screens can hand in a `loading` skeleton (instead
// of the spinner) and opt into pull-to-refresh with `refreshable = true`.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ok<T>(val value: T) : LoadState<T>
    data class Err(val message: String) : LoadState<Nothing>
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

@Composable
fun <T> AsyncContent(
    key: Any? = Unit,
    load: suspend () -> T,
    loading: (@Composable () -> Unit)? = null,
    refreshable: Boolean = false,
    content: @Composable (value: T, reload: () -> Unit) -> Unit,
) {
    var state by remember(key) { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    var refreshing by remember(key) { mutableStateOf(false) }
    val reload: () -> Unit = { attempt++ }

    LaunchedEffect(key, attempt) {
        // A reload while content is already showing refreshes IN PLACE (pull-to-
        // refresh, post-mutation reloads) — no jarring swap back to the loading
        // state; a failed refresh quietly keeps the content it has.
        val keep = state is LoadState.Ok
        if (keep) refreshing = attempt > 0 else state = LoadState.Loading
        try {
            state = LoadState.Ok(load())
        } catch (e: Exception) {
            if (!keep) state = LoadState.Err(ApiException.message(e))
        } finally {
            refreshing = false
        }
    }

    // The error (and loading) states must NEVER trap the member: screens whose
    // whole body is AsyncContent lose their own header here, so we always offer
    // a way back via the activity's back dispatcher (fixes the "failed quiz /
    // missing event → restart the app" trap).
    val backDispatcher =
        androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    when (val s = state) {
        is LoadState.Loading -> loading?.invoke() ?: Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Nuru.gold)
        }
        is LoadState.Err -> Column(
            Modifier.fillMaxSize().padding(Spacing.screen),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.message, style = NuruType.body, color = Nuru.ink600, textAlign = TextAlign.Center)
            TextButton(onClick = { attempt++ }) {
                Text("Try again", style = NuruType.cardCta, color = Nuru.gold)
            }
            TextButton(onClick = { backDispatcher?.onBackPressed() }) {
                Text("‹ Go back", style = NuruType.cardCta, color = Nuru.ink600)
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
