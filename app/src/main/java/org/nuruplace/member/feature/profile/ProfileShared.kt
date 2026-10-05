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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif

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

// One badge look (§8.1 rules 1 and 7): the category's icon, navy on gold tint
// — they wore green, sky and purple by category, hues that say nothing.
fun badgeStyle(category: String?): BadgeStyle = when (category?.lowercase()?.trim()) {
    "journey" -> BadgeStyle(Icons.Filled.AutoAwesome, PROF.navy, PROF.goldTint)
    "consistency" -> BadgeStyle(Icons.Filled.LocalFireDepartment, PROF.navy, PROF.goldTint)
    "community" -> BadgeStyle(Icons.Filled.Group, PROF.navy, PROF.goldTint)
    "service" -> BadgeStyle(Icons.Filled.VolunteerActivism, PROF.navy, PROF.goldTint)
    else -> BadgeStyle(Icons.Filled.Verified, PROF.navy, PROF.goldTint)
}

/** Settings row icon tile tint (bg, fg). */
data class RowTint(val bg: Color, val fg: Color)

// Row icons sit on the gold-tint tile (EXPERIENCE.md §8.1 rules 1 and 7) —
// they were indigo, pink, sky and green: hues that said nothing. Only the
// two-factor tile keeps a state colour: amber while off ("recommended"),
// green once on.
private val TINT_GOLD = RowTint(PROF.goldTint, PROF.navy)
val TINT_PASSWORD = TINT_GOLD
val TINT_2FA_OFF = RowTint(Color(0xFFFEF3C7), Color(0xFFD97706))
val TINT_2FA_ON = RowTint(Color(0x2216A34A), Color(0xFF16A34A))
val TINT_SESSIONS = TINT_GOLD
val TINT_NOTIF = TINT_GOLD
val TINT_LANGUAGE = TINT_GOLD
val TINT_HELP = TINT_GOLD
val TINT_PRIVACY = TINT_GOLD

// Delegates to the canonical schema (ui/theme/TypeSchema.kt) — edit rhythm there.
fun pInter(size: Int, weight: FontWeight = FontWeight.Normal, kerning: Float = 0f) =
    nuruSans(size, weight, kerning.takeIf { it != 0f })

fun pSerif(size: Int, weight: FontWeight = FontWeight.SemiBold, kerning: Float = 0f) =
    nuruSerif(size, weight, kerning.takeIf { it != 0f })

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

// ── Values that look like data never leak (EXPERIENCE.md §8.1 rule 8) ──────

/** An empty profile value, said plainly — never "—" or a blank row. */
internal const val NOT_SET = "Not set"

/** The calendar date a date-only value carries: its leading "YYYY-MM-DD".
 *  The server sends a birthday as midnight UTC ("1990-01-01T00:00:00.000Z");
 *  reading it as an instant in the phone's zone would shift it a day (and
 *  printed raw it read "1989-12-31T21:00:00.000Z"). Null when there is none. */
internal fun calendarDateOf(raw: String?): java.time.LocalDate? {
    val m = Regex("""^\s*(\d{4})-(\d{2})-(\d{2})""").find(raw.orEmpty()) ?: return null
    val (y, mo, d) = m.destructured
    return runCatching { java.time.LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
}

/** A birthday as a member reads it: "1 Jan 1990"; "Not set" when empty. */
internal fun profileDateLabel(raw: String?): String =
    calendarDateOf(raw)?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
        ?: NOT_SET

/** The edit sheet's starting value for a birthday: "1990-01-01" — the form
 *  it asks for, so an unchanged birthday still saves. Empty when unset. */
internal fun profileDateEditValue(raw: String?): String = calendarDateOf(raw)?.toString().orEmpty()

/** The gender choices, as a member reads them and as the server stores them. */
internal val GENDER_OPTIONS = listOf(
    "Male" to "male",
    "Female" to "female",
    "Prefer not to say" to "prefer_not_to_say",
)

/** "Prefer not to say", never "prefer_not_to_say"; "Not set" when empty. */
internal fun profileGenderLabel(raw: String?): String {
    val v = raw?.trim().orEmpty()
    if (v.isEmpty()) return NOT_SET
    return GENDER_OPTIONS.firstOrNull { it.second == v.lowercase() }?.first
        ?: v.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Any other text value: itself, or "Not set" when empty. */
internal fun profileValue(raw: String?): String = raw?.trim()?.takeIf { it.isNotEmpty() } ?: NOT_SET
