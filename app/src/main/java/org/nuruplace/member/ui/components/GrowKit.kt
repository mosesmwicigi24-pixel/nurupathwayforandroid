// GrowKit — shared palette + cream header + text helpers for the "grow your faith"
// screens (Devotional, Memory verses, Your Calling, Prayer Wall). Lives in
// ui.components so all three feature packages (grow, profile, community) can use it.
// Ported from the iOS grow screens (global Nuru enum + inline literals).
package org.nuruplace.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.ui.theme.Fraunces
import org.nuruplace.member.ui.theme.Inter

/** Grow-screens palette — Nuru tokens + the inline literals the iOS grow views use. */
object GrowPal {
    val paper = Color(0xFFF6F4EE)
    val white = Color(0xFFFFFFFF)
    val surface = Color(0xFFFBF8F1)
    val coolPaper = Color(0xFFF7F9FC)

    val navy = Color(0xFF0B1F33)
    val navyDeep = Color(0xFF00132F)
    val navyMid = Color(0xFF315F8C)

    val gold = Color(0xFFC89B3C)
    val goldGlow = Color(0xFFE6CA68)
    val goldTint = Color(0xFFFFF4C7)
    val goldChipBg = Color(0xFFFFF4DA)
    val goldChipText = Color(0xFF7A5A14)
    val goldLo = Color(0xFFA87F2E)
    val eyebrow = Color(0xFF9A7A2A)     // header eyebrows
    val overline = Color(0xFFA8861C)    // in-card overlines (REFLECTION / THIS WEEK / verse ref)

    val ink = Color(0xFF0B0B0C)
    val ink600 = Color(0xFF59667C)
    val ink400 = Color(0xFF6F7E93)
    val ink300 = Color(0xFFB5BDC9)
    val border = Color(0x1A0A2540)      // #0A2540 @ 10%
    val track = Color(0x1A0A2540)
    val tintBlue = Color(0xFFE8EEF7)

    val successBg = Color(0xFFDCFCE7)
    val successText = Color(0xFF166534)
    val danger = Color(0xFFD4183D)
    val scrimNavy = Color(0xFF081C36)

    val cream = Brush.linearGradient(listOf(Color(0xFFF6F4EF), Color(0xFFEFE8DA)))
    val heroGradient = Brush.linearGradient(listOf(Color(0xFF1A406B), Color(0xFF0B1F33), Color(0xFF00132F)))
    val goldGrad = Brush.verticalGradient(listOf(Color(0xFFE5BC3A), Color(0xFFC9A227), Color(0xFFA8861C)))
}

fun gInter(size: Int, weight: FontWeight = FontWeight.Normal, kerning: Float = 0f) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp)

fun gSerif(size: Int, weight: FontWeight = FontWeight.SemiBold, kerning: Float = 0f) =
    TextStyle(fontFamily = Fraunces, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp, lineHeight = (size * 1.22f).sp)

/** Cream header chrome (Devotional / Memory verses / Your Calling). Gradient + gold glow + rounded bottom + hairline. */
@Composable
fun GrowCreamHeader(modifier: Modifier = Modifier, radius: Dp = 24.dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = radius, bottomEnd = radius)).background(GrowPal.cream),
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(listOf(GrowPal.gold.copy(alpha = 0.20f), Color.Transparent), center = Offset(840f, -30f), radius = 560f),
            ),
        )
        content()
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(GrowPal.border))
    }
}
