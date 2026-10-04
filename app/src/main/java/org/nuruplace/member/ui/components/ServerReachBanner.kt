// One line at the top of the shell while Nuru Place cannot be reached and the
// screens are showing their last good copies (data/net/ServerReach). Honest
// about whose fault it is — the phone's network when it has none ("You're
// offline", the state language), else the server's — and gone the moment the
// wire answers.
package org.nuruplace.member.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.NetworkStatus
import org.nuruplace.member.data.net.ServerReach
import org.nuruplace.member.data.net.StateLanguage
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType

@Composable
fun ServerReachBanner(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    AnimatedVisibility(visible = ServerReach.staleSince != null, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        // The phone itself offline: the state language's own words (§4,
        // "You're offline · Showing what you last saw…"). Online: the server
        // is the one away, and the line says so.
        Text(
            if (!NetworkStatus.isOnline(context)) StateLanguage.offline(hasSavedCopy = true).sentence
            else "Nuru Place can't be reached right now — showing what you last saw. We'll keep trying.",
            style = NuruType.micro, fontWeight = FontWeight.SemiBold, color = Nuru.navyDeep, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF4DA)).padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
