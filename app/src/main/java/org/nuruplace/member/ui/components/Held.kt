// Held state — Back returns you where you were (pathway docs/EXPERIENCE.md
// §7 rule 5: the same content at the same scroll; a refresh updates in place,
// with no skeleton and no number that isn't true yet).
//
// The root cause it fixes: NavHost composes only the destination on top. A
// screen whose data lived in `remember` lost it whenever a full-screen route
// (the exam, a module, an event, a pledge) was pushed over it — the screen
// left composition, and its data went with it — so Back rebuilt it from
// nothing: Home showed its skeleton, a growth score of "0" and the top of the
// page (the scroll offset rememberScrollState restores was clamped against
// the skeleton's short height). rememberSaveable can't keep server data (it
// must fit a Bundle). But the destination's back-stack entry outlives its
// composition — it stays on the stack under the pushed route — and owns a
// ViewModelStore: a value held there is back on the first frame of Back.
//
// [rememberHeld] is `remember` whose value lives at the destination instead
// of in the composition, under a key the caller names — "Home.rhythm",
// "LiturgyCard.lit" — unique within the destination (the composable's name
// first). Named, not derived from the call's place in the composition as
// rememberSaveable's are: a card inside a wrapper that composes differently
// the second time (Home's one-time entrance animation) would otherwise come
// back under a new place and lose its value. Dropped with the destination
// when it is popped. Outside a NavHost destination it is plain `remember`.
// GiveViewModel and PartnersViewModel hold the Give tab's data the same way.
package org.nuruplace.member.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/** The values a destination holds for its screen across routes pushed over it. */
class HeldValues : ViewModel() {
    private val values = HashMap<String, Pair<List<Any?>, Any?>>()

    /** The value held at [key] while its [inputs] are the same; else a new one from [init]. */
    @Suppress("UNCHECKED_CAST")
    fun <T> take(key: String, inputs: List<Any?>, init: () -> T): T {
        values[key]?.let { (heldInputs, value) -> if (heldInputs == inputs) return value as T }
        return init().also { values[key] = inputs to it }
    }
}

/**
 * `remember`, held by the screen's destination under [key]: the value
 * survives a route pushed over the screen and is there again on the first
 * frame back. [inputs], as `remember`'s keys: a change makes a new value.
 * Hold a screen's server data with it
 * (`var data by rememberHeld("Home.data") { mutableStateOf(…) }`) — not
 * transient UI state, which should start fresh.
 */
@Composable
fun <T> rememberHeld(key: String, vararg inputs: Any?, init: () -> T): T {
    val owner = LocalViewModelStoreOwner.current ?: return remember(*inputs) { init() }
    val held: HeldValues = viewModel(viewModelStoreOwner = owner)
    return remember(held, key, *inputs) { held.take(key, inputs.toList(), init) }
}
