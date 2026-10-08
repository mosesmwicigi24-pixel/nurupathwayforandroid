// A pushed page's header — now the one standard shape (EXPERIENCE.md §8.1 rule
// 2: back · kicker · title), the same as PushedHeader and iOS's
// NuruPushedHeader. It was a compact form with the kicker beside the back
// button; every caller moved with it in one change (final walk C16, owner's
// parity round 2026-10-08).
package org.nuruplace.member.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.nuruSerif

@Composable
fun ScreenHeader(
    title: String,
    kicker: String? = null,
    onBack: (() -> Unit)? = null,
) {
    if (onBack != null) {
        PushedHeader(kicker = kicker.orEmpty(), title = title, onBack = onBack)
        return
    }
    // No way back (a root page): the same band, kicker and title.
    GrowCreamHeader {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 22.dp)) {
            if (!kicker.isNullOrBlank()) Text(kicker.uppercase(), style = NuruType.kicker, color = Nuru.eyebrow)
            Text(title, style = nuruSerif(26, FontWeight.SemiBold), color = Nuru.navy, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
