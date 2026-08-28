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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val border = Color(0x140B1F33)   // Figma --nuru-border: rgba(11,31,51,0.08)
    val track = Color(0x140B1F33)
    val inputBg = Color(0xFFEEF2F7)  // Figma --input-background
    val tintBlue = Color(0xFFE8EEF7) // Figma --secondary

    // Status / feedback — canonical Figma semantic tokens (theme.css --nuru-*).
    // Each pairs a saturated foreground with a soft tint bg for chips/banners.
    val success = Color(0xFF16A34A)      // --nuru-success
    val successBg = Color(0xFFDCFCE7)    // --nuru-success-bg
    val successText = Color(0xFF166534)
    val warning = Color(0xFFD97706)      // --nuru-warning
    val warningBg = Color(0xFFFEF3C7)    // --nuru-warning-bg
    val danger = Color(0xFFDC2626)       // --nuru-danger
    val dangerBg = Color(0xFFFEE2E2)     // --nuru-danger-bg
    val destructive = Color(0xFFD4183D)  // --destructive (destructive-action red)
    val info = Color(0xFF0EA5E9)         // --nuru-info
    val infoBg = Color(0xFFE0F2FE)       // --nuru-info-bg
    val verseBg = Color(0xFFFFF8E6)
    val myBubble = Color(0xFFDDF4C6)     // chat outgoing bubble

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

    // ── Home-local palette — ports `enum HomeFig` from the iOS HomeCards.swift so
    // the Home feed matches pixel-for-pixel. `homeNavy` (#0A1628) is intentionally
    // darker/cooler than the brand `navy` (#0B1F33) used in the tab bar / chrome. ──
    val homeNavy = Color(0xFF0A1628)
    val homeNavyDark = Color(0xFF060F1C)
    val eyebrow = Color(0xFF9A7A2A)        // section labels + card kickers
    val goldDeep = Color(0xFFB6862F)       // gold-gradient end
    val goldSoft = Color(0xFFE6C068)       // progress-bar highlight
    val metaGray = Color(0xFF5B6472)       // card secondary text
    val faintGray = Color(0xFF74808F)      // card tertiary text
    val priorityBg = Color(0xFFFFFAEC)     // priority strip / cream inset tint
    val progressTrack = Color(0xFFEEF0F3)  // progress-bar + video placeholder track
    val dayWord = Color(0xFF475569)        // daily-blessing italic body
    val liveRed = Color(0xFFDC2626)        // LIVE pill / radio dot
    // Mini-card + Grow-tile + score accents
    val indigo = Color(0xFF6366F1)         // reading-plan tile
    val indigoBg = Color(0xFFEEF2FF)
    val answeredText = Color(0xFF92400E)   // "N answered" chip text (bg = warningBg)
    val callingFg = Color(0xFFA855F7)      // "Your Calling" tile
    val callingBg = Color(0xFFF5E8FF)
    val hideWordFg = Color(0xFFB45309)     // "Hide His Word" tile (bg = warningBg)
    val scoreWord = Color(0xFF2F6FB0)      // Word growth bar
    val scorePrayer = Color(0xFFC98A3C)    // Prayer growth bar
    // Home dark-card gradient (#0A1628 → #060F1C) and its radial gold glow accent
    val homeNavyGradient = Brush.verticalGradient(listOf(homeNavy, homeNavyDark))
    val headerGradient = Brush.verticalGradient(listOf(Color(0xFFF6F4EF), Color(0xFFEFE8DA)))

    // Engagement bands (§B3): thriving / steady / watch / at_risk — mapped to the
    // canonical semantic palette so band chips read the same as every other chip.
    fun bandColor(band: String?): Color = when (band?.lowercase()) {
        "thriving" -> success
        "steady" -> info
        "watch" -> warning
        "at_risk", "at risk" -> danger
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

/**
 * Semantic type scale (Inter body · Fraunces display). Every style is built from
 * the canonical factories in TypeSchema.kt so line-height and letter-spacing follow
 * ONE schema — sizes/weights here are the scale, rhythm lives in TypeSchema.kt.
 *
 * Each style is a computed property (`get() =`), not a cached `val` — nuruSans/
 * nuruSerif read AppPrefs.lineSpacing when they build the TextStyle, so a plain
 * eagerly-initialized val would freeze the FIRST value forever. Computing it on
 * every access means a call site inside a composable re-reads the live pref on
 * every recomposition, the same live-update behavior AppPrefs.textScale gets via
 * LocalDensity in NuruTheme() below.
 */
object NuruType {
    val display get() = nuruSerif(28, FontWeight.Medium)
    val title get() = nuruSerif(22, FontWeight.Medium)
    val cardTitle get() = nuruSerif(18, FontWeight.SemiBold)
    val rowTitle get() = nuruSerif(15, FontWeight.SemiBold)
    val heading get() = nuruSans(16, FontWeight.Medium)
    val body get() = nuruSans(14)
    val bodyLg get() = nuruSans(16)
    val label get() = nuruSans(12, FontWeight.Medium)
    val caption get() = nuruSans(12)
    val micro get() = nuruSans(11, FontWeight.Medium)
    val kicker get() = nuruSans(11, FontWeight.Bold, tracking = 1.4f)
    val cardCta get() = nuruSans(14, FontWeight.SemiBold)
    // Section labels that sit OUTSIDE a card (GROW YOUR FAITH · UPCOMING · YOUR
    // COHORT) — iOS "Inter 11 bold, kerning 1.98", rendered in `Nuru.eyebrow`.
    val sectionLabel get() = nuruSans(11, FontWeight.Bold, tracking = 1.8f)
    // Fraunces feature-card headline (verse text, card titles) — iOS "Fraunces 18 semibold".
    val featureTitle get() = nuruSerif(18, FontWeight.SemiBold)
    // Featured-video caption — featureTitle two points down, and SERIF like every
    // other card title (owner, 2026-08-26: "all fonts should be the primary fonts
    // for the App and not mixed with the portal fonts"; iOS moved this line from
    // inter(18, semibold) to fraunces(16, semibold) in the same pass).
    // Four points off the old sans headline (owner, 2026-08-26) — iOS parity:
    // fraunces 14 semibold, full width, with real leading and air below.
    val videoCaption get() = nuruSerif(14, FontWeight.SemiBold)
    // Greeting — iOS "Fraunces 22 semibold, kerning −0.22".
    val greeting get() = nuruSerif(22, FontWeight.SemiBold, tracking = -0.22f)
    // iOS build 80 parity (nChipLabel/nActionLabel): segment/filter chips and
    // pill CTA / menu action labels inherit from here instead of each call site
    // declaring its own inline Inter size+weight.
    val chipLabel get() = nuruSans(12, FontWeight.SemiBold)
    val actionLabel get() = nuruSans(13, FontWeight.Bold)
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

@Composable
fun NuruTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme() // brand is light-only for now
    // App-wide text-size control: scale every sp value by the user's preference
    // without disturbing dp layout metrics.
    val base = LocalDensity.current
    val scaled = Density(density = base.density, fontScale = base.fontScale * org.nuruplace.member.data.AppPrefs.textScale)
    // App-wide line-spacing control: MaterialTheme.typography is a plain value
    // (not read live by descendants the way LocalDensity is), so it has to be
    // rebuilt HERE, at theme-build time, reading AppPrefs.lineSpacing so this
    // recomposes — and therefore every MaterialTheme.typography.* consumer
    // downstream repaints — exactly when the pref changes.
    // APP FONTS ONLY (owner, 2026-08-26: "all fonts should be the primary fonts
    // for the App and not mixed with the portal fonts"). Material3's MaterialTheme
    // does `ProvideTextStyle(typography.bodyLarge)`, so a style-less `Text(…)`
    // already inherits Inter — but every Material component that reads a slot we
    // had NOT overridden (AlertDialog title → headlineSmall, TextField
    // label/supporting → bodySmall, assist chips → labelSmall, …) fell through to
    // the M3 default, i.e. FontFamily.Default = the device's system face. Those
    // are the "foreign" letters. Every one of the 15 slots is now pinned to an
    // app family — display/headline/title serif (Fraunces), body/label sans
    // (Inter) — keeping M3's own metrics for the slots we don't otherwise style,
    // so nothing shifts except the typeface.
    val typography = remember(org.nuruplace.member.data.AppPrefs.lineSpacing) {
        val m3 = Typography()
        Typography(
            displayLarge = m3.displayLarge.copy(fontFamily = Fraunces),
            displayMedium = m3.displayMedium.copy(fontFamily = Fraunces),
            displaySmall = m3.displaySmall.copy(fontFamily = Fraunces),
            headlineLarge = m3.headlineLarge.copy(fontFamily = Fraunces),
            headlineMedium = m3.headlineMedium.copy(fontFamily = Fraunces),
            headlineSmall = m3.headlineSmall.copy(fontFamily = Fraunces),
            titleLarge = NuruType.title,
            titleMedium = NuruType.cardTitle,
            titleSmall = NuruType.rowTitle,
            bodyLarge = NuruType.bodyLg,
            bodyMedium = NuruType.body,
            bodySmall = NuruType.caption,
            labelLarge = NuruType.cardCta,
            labelMedium = NuruType.label,
            labelSmall = NuruType.micro,
        )
    }
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = NuruColorScheme,
            typography = typography,
            content = content,
        )
    }
}
