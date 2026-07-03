// Shared UI primitives — the Compose port of the iOS app's Card / PButton /
// BrandMark / NuruField. One soft card shadow, gold-crossed wordmark, labeled
// fields. Kept deliberately small; feature screens compose from these.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

/** White card that floats on ONE soft shadow (never stack shadows). */
@Composable
fun NuruCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(Spacing.base),
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.card),
        color = Nuru.white,
        border = BorderStroke(1.dp, Nuru.border),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(padding)) { content() }
    }
}

/** Primary navy-gradient CTA (matches iOS PButton .primary). */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.buttonLg)
            .clip(RoundedCornerShape(Radii.button))
            .background(if (enabled && !loading) Nuru.primaryButton else androidx.compose.ui.graphics.SolidColor(Nuru.ink300))
            .then(if (enabled && !loading) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (loading) "Please wait…" else label,
            style = NuruType.cardCta.copy(fontWeight = FontWeight.SemiBold),
            color = Nuru.onNavy,
        )
    }
}

/** Small gold-accent kicker/eyebrow label (uppercase, kerned by the caller). */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = NuruType.kicker, color = Nuru.goldLo, modifier = modifier)
}

/** The wordmark — a gold cross over the serif "Nuru" used on ceremony screens. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, onDark: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Nuru.goldGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text("✝", style = NuruType.title, color = Nuru.navyDeep)
        }
        Text(
            "Nuru",
            style = NuruType.display,
            color = if (onDark) Nuru.onNavy else Nuru.ink,
            modifier = Modifier.padding(top = Spacing.md),
        )
    }
}

/** Labeled text field with a leading icon — the port of NuruField. */
@Composable
fun NuruField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = NuruType.micro, color = Nuru.ink400, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = Nuru.ink400) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = NuruType.bodyLg,
            shape = RoundedCornerShape(Radii.control),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Nuru.inputBg,
                unfocusedContainerColor = Nuru.inputBg,
                focusedBorderColor = Nuru.gold,
                unfocusedBorderColor = Nuru.border,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
