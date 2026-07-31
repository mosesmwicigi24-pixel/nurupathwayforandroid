// Fixed Nuru brand palette for the widgets — deliberately NOT system
// light/dark-adaptive. Both widgets are a navy-and-gold "door" regardless of
// device theme, same as every other Nuru chrome surface (tab bar, headers).
// Mirrors ui/theme/NuruTheme.kt's navy/gold values (0x16273F/0xC9A227 are the
// exact tokens already used for the radio player panel + chat bubbles).
package org.nuruplace.member.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
// NOTE: androidx.glance.material3.ColorProviders is a top-level FUNCTION
// (light, dark) -> androidx.glance.color.ColorProviders — not a class in the
// material3 package. GlanceTheme itself lives in the glance CORE package
// (androidx.glance.GlanceTheme), not glance-material3 (verified against the
// 1.1.1 AARs — glance-material3 only ships Material3ThemesKt, which is this
// ColorProviders(light, dark) builder).
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider

object WidgetBrand {
    val navy = Color(0xFF16273F)
    val navyDeep = Color(0xFF0A1628)
    val gold = Color(0xFFC9A227)
    val goldHi = Color(0xFFE0B85E)
    val onNavy = Color.White
    val onNavyDim = Color.White.copy(alpha = 0.60f)
    val onNavyFaint = Color.White.copy(alpha = 0.40f)
    val ringTrack = Color.White.copy(alpha = 0.18f)
    val liveRed = Color(0xFFE5484D)

    // Glance's TextStyle/background APIs want a ColorProvider, not a raw
    // Compose Color — these are the ready-wrapped equivalents of the Colors
    // above, for text/foreground use (backgrounds take Color directly via
    // GlanceModifier.background(Color)).
    val navyProvider = ColorProvider(navy)
    val goldProvider = ColorProvider(gold)
    val goldHiProvider = ColorProvider(goldHi)
    val onNavyProvider = ColorProvider(onNavy)
    val onNavyDimProvider = ColorProvider(onNavyDim)
    val onNavyFaintProvider = ColorProvider(onNavyFaint)
    val navyDeepProvider = ColorProvider(navyDeep)
    val liveRedProvider = ColorProvider(liveRed)

    private val scheme: ColorScheme = lightColorScheme(
        primary = gold,
        onPrimary = navyDeep,
        background = navy,
        onBackground = onNavy,
        surface = navy,
        onSurface = onNavy,
    )

    /** Same scheme for both — the brand doesn't flip with system dark mode. */
    val colors = ColorProviders(light = scheme, dark = darkColorScheme(
        primary = gold, onPrimary = navyDeep, background = navy, onBackground = onNavy,
        surface = navy, onSurface = onNavy,
    ))
}
