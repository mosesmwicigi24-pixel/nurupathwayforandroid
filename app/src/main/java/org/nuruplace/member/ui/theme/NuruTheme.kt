// Nuru Pathway design system — a Compose port of the native iOS app's NuruTheme
// (which itself ports packages/mobile/src/theme/tokens.ts). Same visual language
// across web, RN, iOS and now Android: warm paper, white cards on one soft shadow,
// gold used sparingly, Inter body + Fraunces display. Phone type scale.
package org.nuruplace.member.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.R

/** Brand palette — hex values identical to NuruTheme.swift / tokens.ts. */
object Nuru {
    // Surfaces
    val paper = Color(0xFFF6F4EE)
    val white = Color(0xFFFFFFFF)
    val surface = Color(0xFFFBF8F1)
    val coolPaper = Color(0xFFF7F9FC)
    val chatPaper = Color(0xFFE8E1D3)

    // Navy (brand)
    val navy = Color(0xFF0B1F33)
    val navyDeep = Color(0xFF00132F)
    val navy700 = Color(0xFF143559)
    val navyMid = Color(0xFF315F8C)
    val navyCeremony = Color(0xFF081C36)

    // Gold (accent — sparingly)
    val gold = Color(0xFFC89B3C)
    val goldHi = Color(0xFFE0B85E)
    val goldLo = Color(0xFFA87F2E)
    val goldGlow = Color(0xFFE6CA68)
    val goldLight = Color(0xFFE6C068)
    val goldTint = Color(0xFFFFF4C7)
    val goldChipBg = Color(0xFFFFF4DA)
    val goldChipText = Color(0xFF7A5A14)

    // Ink (text)
    val ink = Color(0xFF0B0B0C)
    val ink600 = Color(0xFF59667C)
    val ink400 = Color(0xFF6F7E93)
    val ink300 = Color(0xFFB5BDC9)
    val border = Color(0x1A0A2540)   // #0A2540 @ 10%
    val track = Color(0x1A0A2540)
    val inputBg = Color(0xFFEEF1F5)
    val tintBlue = Color(0xFFE8EEF7)

    // Status / feedback
    val success = Color(0xFF1E7F4F)
    val warning = Color(0xFFB45309)
    val danger = Color(0xFFD4183D)
    val successBg = Color(0xFFDCFCE7)
    val successText = Color(0xFF166534)
    val verseBg = Color(0xFFFFF8E6)
    val myBubble = Color(0xFFDDF4C6)   // chat outgoing bubble

    // On-navy text
    val onNavy = Color.White
    val onNavyDim = Color.White.copy(alpha = 0.55f)
    val onNavyFaint = Color.White.copy(alpha = 0.40f)

    // Gradients
    val navyGradient = Brush.linearGradient(listOf(navy700, navy, Color(0xFF07203A)))
    val heroGradient = Brush.linearGradient(listOf(Color(0xFF1A406B), navy, navyDeep))
    val ceremonyGradient = Brush.verticalGradient(listOf(Color(0xFF0E2A4A), navyCeremony, Color(0xFF03101F)))
    val goldGradient = Brush.verticalGradient(listOf(Color(0xFFE5BC3A), Color(0xFFC9A227), Color(0xFFA8861C)))
    val primaryButton = Brush.verticalGradient(listOf(Color(0xFF143559), Color(0xFF0A2540), Color(0xFF07203A)))

    fun bandColor(band: String?): Color = when (band?.lowercase()) {
        "thriving" -> Color(0xFF1E7F4F)
        "steady" -> Color(0xFF1B5FAE)
        "watch" -> Color(0xFFB45309)
        "at_risk", "at risk" -> Color(0xFFD4183D)
        else -> ink600
    }
}

/** Radii (8pt grid; mobile values). */
object Radii {
    val control = 14.dp
    val button = 14.dp
    val card = 24.dp
    val hero = 30.dp
    val pill = 999.dp
}

/** Spacing scale — mirrors NuruTheme.S. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val screen = 20.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val tabBarSpace = 96.dp
    val buttonLg = 56.dp
    val buttonMd = 48.dp
}

// Bundled OFL faces (res/font). PostScript-name parity with iOS/RN.
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_medium, FontWeight.Medium),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_bold, FontWeight.Bold),
)

/** Semantic type scale — matches NuruTheme's Font extension (Inter body · Fraunces display). */
object NuruType {
    val display = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp)
    val title = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp)
    val cardTitle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val rowTitle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val heading = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val bodyLg = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val micro = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
    val kicker = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp)
    val cardCta = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
}

private val NuruColorScheme = lightColorScheme(
    primary = Nuru.navyDeep,
    onPrimary = Nuru.onNavy,
    secondary = Nuru.gold,
    background = Nuru.paper,
    onBackground = Nuru.ink,
    surface = Nuru.white,
    onSurface = Nuru.ink,
    error = Nuru.danger,
)

private val NuruTypography = Typography(
    titleLarge = NuruType.title,
    titleMedium = NuruType.cardTitle,
    bodyLarge = NuruType.bodyLg,
    bodyMedium = NuruType.body,
    labelLarge = NuruType.cardCta,
    labelMedium = NuruType.label,
)

@Composable
fun NuruTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme() // brand is light-only for now
    // App-wide text-size control: scale every sp value by the user's preference
    // without disturbing dp layout metrics.
    val base = LocalDensity.current
    val scaled = Density(density = base.density, fontScale = base.fontScale * org.nuruplace.member.data.AppPrefs.textScale)
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = NuruColorScheme,
            typography = NuruTypography,
            content = content,
        )
    }
}
