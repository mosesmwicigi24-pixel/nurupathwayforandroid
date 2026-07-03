// Plans shared foundation — the `PL` palette + reusable primitives, ported from the
// iOS ReadingPlanCards.swift `enum PL`. Every Plans screen (list · detail · day ·
// segment) uses these so the flow is pixel-consistent. Screen-specific cards live in
// their own screen files; only the truly-shared bits are here.
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.nuruplace.member.ui.theme.Fraunces
import org.nuruplace.member.ui.theme.Inter

/** iOS `enum PL` palette — Plans-tab-specific (darker/cooler than the global Nuru navy/gold). */
object PL {
    val navy = Color(0xFF0A1628)
    val navyDeep = Color(0xFF060F1C)
    val gold = Color(0xFFC9A227)
    val goldLight = Color(0xFFE6C068)
    val goldDeep = Color(0xFFA8861C)
    val cream = Color(0xFFF4F0E8)
    val creamLo = Color(0xFFF1ECE1)
    val surface = Color(0xFFFBF8F1)
    val border = Color(0x140A2540)        // #0A2540 @ 8%
    val ink2 = Color(0xFF59667C)
    val ink3 = Color(0xFF74808F)
    val catText = Color(0xFF9A7A2A)        // "PLANS" overline, gift chip, tile category
    val blurb = Color(0xFF3A4A5F)          // about-plan body
    val highlight = Color(0xFFFFF8E6)      // next-row / verse card bg
    val refInk = Color(0xFF7A5A14)         // scripture-ref overline
    val bodyInk = Color(0xFF1B2733)
    val chev = Color(0xFFCBD5E1)
    val ctaDeep = Color(0xFFB6862F)

    val navyGrad = Brush.linearGradient(listOf(navy, navyDeep))
    val goldCtaGrad = Brush.linearGradient(listOf(gold, ctaDeep))
    val goldProgressGrad = Brush.horizontalGradient(listOf(gold, goldLight))
    val headerGrad = Brush.linearGradient(listOf(Color(0xFFF6F4EF), Color(0xFFEFE8DA)))
}

/** Inter text style (size in sp, optional weight/kerning/italic). */
fun plInter(size: Int, weight: FontWeight = FontWeight.Normal, kerning: Float = 0f, italic: Boolean = false) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)

/** Fraunces (serif) text style — default medium weight, like iOS `.fraunces()`. */
fun plSerif(size: Int, weight: FontWeight = FontWeight.Medium, kerning: Float = 0f, italic: Boolean = false) =
    TextStyle(fontFamily = Fraunces, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)

/** Uppercased gold section overline (Inter 9 bold, tracked). */
@Composable
fun PLOverline(text: String, color: Color = PL.goldDeep, kerning: Float = 1.62f, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = plInter(9, FontWeight.Bold, kerning), color = color, modifier = modifier)

/**
 * Plan cover image — FIXED-box crop (the caller sizes the Box). Fills with
 * ContentScale.Crop over a navy-gradient fallback, so a missing/loading image is a
 * branded block, never blank. (FitImage is natural-aspect; covers here are fixed.)
 */
@Composable
fun PLCover(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(PL.navyGrad)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

/** "N DAYS" white pill for card corners (already includes its 8dp inset). */
@Composable
fun PLDaysBadge(days: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.padding(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text("$days DAYS", style = plInter(8, FontWeight.Bold, 0.96f), color = PL.navy)
    }
}
