// Standard navy screen header (gradient) with an optional back button + kicker.
// Reused across the Grow / Community / Give feature screens.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(
        Modifier.fillMaxWidth().background(Nuru.heroGradient)
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nuru.onNavy)
                }
            }
            if (kicker != null) Kicker(kicker)
        }
        Text(
            title, style = NuruType.title, color = Nuru.onNavy,
            modifier = Modifier.padding(start = if (onBack != null) Spacing.xs else 0.dp),
        )
    }
}
