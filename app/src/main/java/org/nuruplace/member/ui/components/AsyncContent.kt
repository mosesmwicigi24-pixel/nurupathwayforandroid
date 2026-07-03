// Tiny load/error/retry scaffold used across data-backed screens, so each screen
// stays focused on its content. Loads via a suspend lambda keyed on an id; the
// error state offers a retry.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun <T> AsyncContent(
    key: Any? = Unit,
    load: suspend () -> T,
    content: @Composable (value: T, reload: () -> Unit) -> Unit,
) {
    var state by remember(key) { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    val reload: () -> Unit = { attempt++ }

    LaunchedEffect(key, attempt) {
        state = LoadState.Loading
        state = try {
            LoadState.Ok(load())
        } catch (e: Exception) {
            LoadState.Err(ApiException.message(e))
        }
    }

    when (val s = state) {
        is LoadState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
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
        }
        is LoadState.Ok -> content(s.value, reload)
    }
}
