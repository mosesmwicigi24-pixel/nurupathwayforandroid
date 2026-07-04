// Profile shared foundation — the `PROF` palette + reusable primitives, ported from
// the iOS Profile/Settings screens (global `Nuru` + inline literals + the per-category
// badge/milestone/settings-row tints). The Account tab and Settings both use these.
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.ui.theme.Fraunces
import org.nuruplace.member.ui.theme.Inter

/** iOS Profile palette — global Nuru tokens + the inline literals used across profile/settings. */
object PROF {
    // Surfaces
    val paper = Color(0xFFF6F4EE)
    val white = Color(0xFFFFFFFF)
    val surface = Color(0xFFFBF8F1)

    // Navy / gold
    val navy = Color(0xFF0B1F33)
    val navyMid = Color(0xFF315F8C)
    val gold = Color(0xFFC89B3C)
    val goldTint = Color(0xFFFFF4C7)
    val goldChipBg = Color(0xFFFFF4DA)
    val tintBlue = Color(0xFFE8EEF7)

    // Golds (three distinct — do not collapse)
    val eyebrow = Color(0xFF9A7A2A)     // "ACCOUNT" / "PREFERENCES"
    val kicker = Color(0xFFA8861C)      // section titles, score values
    val verify = Color(0xFF8A6D18)      // verify text, band label, language chip

    // Ink / text
    val ink = Color(0xFF0B0B0C)
    val rowLabel = Color(0xFF74808F)    // overline labels + chevrons + locked
    val sub = Color(0xFF5B6472)         // subtitles / meta
    val ink600 = Color(0xFF59667C)
    val border = Color(0x1A0A2540)      // #0A2540 @ 10%

    // Status
    val success = Color(0xFF16A34A)
    val successBg = Color(0xFFDCFCE7)
    val successText = Color(0xFF166534)
    val danger = Color(0xFFD4183D)
    val locked = Color(0xFFF3F4F6)      // locked badge/milestone fill

    // Gradients
    val cream = Brush.linearGradient(listOf(Color(0xFFF6F4EF), Color(0xFFEFE8DA)))
    val goldSolid = gold
    val certSeal = Brush.linearGradient(listOf(gold.copy(alpha = 0.20f), gold.copy(alpha = 0.09f)))
}

/** Per-category badge visuals (iOS `PBadgeItem.style`). */
data class BadgeStyle(val icon: ImageVector, val color: Color, val tint: Color)

fun badgeStyle(category: String?): BadgeStyle = when (category?.lowercase()?.trim()) {
    "journey" -> BadgeStyle(Icons.Filled.AutoAwesome, Color(0xFFC89B3C), Color(0xFFFFF4DA))
    "consistency" -> BadgeStyle(Icons.Filled.LocalFireDepartment, Color(0xFF16A34A), Color(0xFFDCFCE7))
    "community" -> BadgeStyle(Icons.Filled.Group, Color(0xFF0EA5E9), Color(0xFFE0F2FE))
    "service" -> BadgeStyle(Icons.Filled.VolunteerActivism, Color(0xFFA855F7), Color(0xFFF3E8FF))
    else -> BadgeStyle(Icons.Filled.Verified, Color(0xFFC89B3C), Color(0xFFFFF4C7))
}

/** Settings row icon tile tint (bg, fg). */
data class RowTint(val bg: Color, val fg: Color)

val TINT_PASSWORD = RowTint(Color(0xFFEEF2FF), Color(0xFF6366F1))
val TINT_2FA_OFF = RowTint(Color(0xFFFEF3C7), Color(0xFFD97706))
val TINT_2FA_ON = RowTint(Color(0x2216A34A), Color(0xFF16A34A))
val TINT_SESSIONS = RowTint(Color(0xFFFCE7F3), Color(0xFFDB2777))
val TINT_NOTIF = RowTint(PROF.gold.copy(alpha = 0.08f), Color(0xFFA8861C))
val TINT_LANGUAGE = RowTint(Color(0xFFE0F2FE), Color(0xFF0EA5E9))
val TINT_HELP = RowTint(Color(0xFFDCFCE7), Color(0xFF16A34A))
val TINT_PRIVACY = RowTint(Color(0xFFEEF2FF), Color(0xFF6366F1))

fun pInter(size: Int, weight: FontWeight = FontWeight.Normal, kerning: Float = 0f) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp)

fun pSerif(size: Int, weight: FontWeight = FontWeight.SemiBold, kerning: Float = 0f) =
    TextStyle(fontFamily = Fraunces, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp, lineHeight = (size * 1.2f).sp)

/** Country-code → flag emoji (regional indicators). */
fun flagEmoji(code: String?): String {
    val c = code?.trim()?.uppercase() ?: return ""
    if (c.length != 2 || !c.all { it in 'A'..'Z' }) return ""
    val a = 0x1F1E6 + (c[0] - 'A')
    val b = 0x1F1E6 + (c[1] - 'A')
    return String(Character.toChars(a)) + String(Character.toChars(b))
}

/** Cream header chrome (Profile + Settings). Gradient + gold glow + 28dp bottom corners + hairline. */
@Composable
fun ProfCreamHeaderBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(PROF.cream),
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(listOf(PROF.gold.copy(alpha = 0.22f), Color.Transparent), center = Offset(840f, -30f), radius = 560f),
            ),
        )
        content()
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(PROF.border))
    }
}

val FingerprintIcon: ImageVector = Icons.Filled.Fingerprint
