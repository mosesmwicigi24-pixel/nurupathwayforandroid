// One quiet line for a quick action the server did not record (EXPERIENCE.md
// §7.4 "no success before the server says so", §4's words): a Pray, a
// reaction, a follow, a join, a connection request — taps with no form and no
// button of their own for a failure line to sit above. Cycle 3's audit found
// about twenty such writes failing in silence; each now says so, once, here,
// in "Couldn't save that." + §4's sentence (or "Couldn't send that.").
//
// A write with a form (a comment, a post, a message, a recording) keeps what
// the member entered and says why beside it instead — it never comes here.
package org.nuruplace.member.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import kotlin.coroutines.cancellation.CancellationException
import org.nuruplace.member.ui.icons.Lucide

object QuickNotice {
    /** One line on screen; a new one replaces it. */
    data class Entry(val id: Long, val line: String)

    var current: Entry? by mutableStateOf(null)
        private set
    private var next = 0L

    fun show(line: String) {
        next += 1
        current = Entry(next, line)
    }

    fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }

    /** How long a line stays before it goes by itself. */
    const val SHOWN_MS = 5_000L
}

/**
 * Run a quick write. If the server refused it or never answered, say so —
 * "[lead] " + §4's sentence — as one [QuickNotice] line, and return null.
 * A cancel (the member left the screen) says nothing. [onFailure] undoes
 * anything shown ahead of the answer.
 */
suspend fun <T> noticeOnFailure(
    context: Context?,
    lead: String = ApiException.SAVE_FAILED,
    onFailure: () -> Unit = {},
    block: suspend () -> T,
): T? = try {
    block()
} catch (c: CancellationException) {
    throw c
} catch (e: Exception) {
    onFailure()
    QuickNotice.show(ApiException.failureLine(lead, e, context))
    null
}

/** The shell's one place for [QuickNotice] — above the bottom bar, clear of
 *  the gesture bar. */
@Composable
fun QuickNoticeHost(modifier: Modifier = Modifier) {
    val entry = QuickNotice.current
    LaunchedEffect(entry?.id) {
        val shown = entry ?: return@LaunchedEffect
        delay(QuickNotice.SHOWN_MS)
        QuickNotice.dismiss(shown.id)
    }
    AnimatedVisibility(
        visible = entry != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val shown = entry ?: return@AnimatedVisibility
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Nuru.navy)
                .clickable { QuickNotice.dismiss(shown.id) }
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Lucide.AlertCircle, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(18.dp))
            Text(shown.line, style = NuruType.body, color = Color.White)
        }
    }
}
