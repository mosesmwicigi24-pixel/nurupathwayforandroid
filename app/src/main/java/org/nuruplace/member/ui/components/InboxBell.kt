// One bell (pathway docs/EXPERIENCE.md §7.2 #4 — rule 8: signals tell the
// truth). Every tab header's bell — Home, Pathway, Plans, Events, Give, the
// You tab's Community — is this one: it opens the inbox, and it wears one
// gold dot only while the inbox has something unread. The dots used to be
// painted on (always there on Pathway, Plans, Events and Give, never on
// Home), and the Pathway bell opened nothing.
//
// One count behind every bell ([InboxUnread]): GET /me/notifications'
// `unread`, asked when the app comes to the foreground (MainShell), said by
// the inbox itself while it is open — its own read and every mark-read, the
// moment it is made — and asked again when the inbox closes. Each header
// keeps its own size and look; only the bell's behaviour is shared.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.theme.Nuru

/** The inbox's unread count — the one number every bell reads. */
object InboxUnread {
    // The inbox closing has no scope of its own left to ask from.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _count = MutableStateFlow<Int?>(null)

    /** Unread notices; null until the server has said (no dot on a guess). */
    val count: StateFlow<Int?> = _count.asStateFlow()

    /** Ask the server (one row is enough — `unread` counts them all). A
     *  failed read keeps what was known. */
    suspend fun refresh() {
        runCatching { Net.client.api.notifications(limit = 1).unread }.getOrNull()?.let(::set)
    }

    /** [refresh], without waiting for it. */
    fun refreshSoon() {
        scope.launch { refresh() }
    }

    /** What the inbox itself just said — its read, or a mark-read it made. */
    fun set(unread: Int) {
        _count.value = unread.coerceAtLeast(0)
    }

    /** Signed out: nobody's count. */
    fun clear() {
        _count.value = null
    }
}

/** Whether a bell wears its dot: only while something is unread. */
fun bellShowsDot(unread: Int?): Boolean = (unread ?: 0) > 0

/**
 * A tab header's bell: opens the inbox ([onClick]); one gold dot, [dotInset]
 * in from its top-right corner, only while [InboxUnread] says something is
 * unread. The rest is the header's own look — its size, shape, fill, border
 * and glyph tint.
 */
@Composable
fun InboxBell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape,
    container: Color = Nuru.white,
    border: Color = Nuru.border,
    tint: Color = Nuru.navy,
    iconSize: Dp = 18.dp,
    dotInset: Dp = 8.dp,
) {
    val unread by InboxUnread.count.collectAsState()
    val dot = bellShowsDot(unread)
    Box(
        modifier.size(size).clip(shape).background(container).border(1.dp, border, shape)
            .clickable(onClickLabel = "Open notifications") { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Notifications,
            contentDescription = if (dot) "Notifications — unread" else "Notifications",
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        if (dot) {
            Box(Modifier.align(Alignment.TopEnd).padding(dotInset).size(8.dp).clip(CircleShape).background(Nuru.gold))
        }
    }
}
