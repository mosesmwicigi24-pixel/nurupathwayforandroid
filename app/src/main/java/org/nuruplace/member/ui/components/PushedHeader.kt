// A pushed page's one header (EXPERIENCE.md §8.1 rule 2: back · kicker ·
// title · one line), in the cream band every page wears — iOS
// NuruPushedHeader, the same shape. The final walk found the inbox as a white
// band with no kicker, the receipt with no kicker, and the announcement's
// "ANNOUNCEMENT" as a white pill at the far right (final walk C16).
package org.nuruplace.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.icons.Lucide
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif

/** [trailing]: one quiet action at the back row's right (the inbox's "Mark
 *  all read"); [line]: a composable line under the title when it animates. */
@Composable
fun PushedHeader(
    kicker: String,
    title: String,
    onBack: () -> Unit,
    line: String? = null,
    lineContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    GrowCreamHeader {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(Nuru.white)
                        .border(1.dp, Nuru.border, RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = "Back") { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.ArrowLeft, "Back", tint = Nuru.navy, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            if (kicker.isNotBlank()) Text(kicker.uppercase(), style = NuruType.kicker, color = Nuru.eyebrow, modifier = Modifier.padding(top = 14.dp))
            Text(title, style = nuruSerif(26, FontWeight.SemiBold), color = Nuru.navy, modifier = Modifier.padding(top = 4.dp))
            when {
                lineContent != null -> Box(Modifier.padding(top = 6.dp)) { lineContent() }
                !line.isNullOrBlank() -> Text(line, style = nuruSans(13, FontWeight.Normal), color = Nuru.ink600, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
