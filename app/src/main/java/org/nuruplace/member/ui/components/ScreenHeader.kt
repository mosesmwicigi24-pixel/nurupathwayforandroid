// Standard screen header — warm cream gradient card (gold glow, rounded bottom
// corners, hairline) matching the Home header treatment, with an optional back
// button + kicker. Reused across the Grow / Community / Give feature screens.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun ScreenHeader(
    title: String,
    kicker: String? = null,
    onBack: (() -> Unit)? = null,
) {
    GrowCreamHeader {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Nuru.white)
                            .border(1.dp, Nuru.border, CircleShape)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = Nuru.navy, modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                }
                if (kicker != null) Text(kicker.uppercase(), style = NuruType.kicker, color = Nuru.eyebrow)
            }
            Text(
                title, style = NuruType.title, color = Nuru.navy,
                modifier = Modifier.padding(
                    start = if (onBack != null) Spacing.xs else 0.dp,
                    top = if (onBack != null || kicker != null) Spacing.sm else 0.dp,
                ),
            )
        }
    }
}
