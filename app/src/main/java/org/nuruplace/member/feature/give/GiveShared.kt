// Give shared foundation — the `GIVE` palette + fund/method tables + money helper +
// reusable primitives, ported from the iOS Give screens (global `Nuru` + inline
// literals + the `Fund`/`PayMethod` structs). The giving tab, statement and receipt
// all use these so the flow is pixel-consistent.
package org.nuruplace.member.feature.give

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Wallet
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

/** iOS Give palette — global Nuru tokens + the inline literals used across the give files. */
object GIVE {
    // Surfaces
    val paper = Color(0xFFF6F4EE)
    val white = Color(0xFFFFFFFF)
    val surface = Color(0xFFFBF8F1)
    val priorityBg = Color(0xFFFFFAEC)      // selected fund/method tint, recurring card

    // Navy
    val navy = Color(0xFF0B1F33)
    val navyDeep = Color(0xFF06182C)

    // Gold
    val gold = Color(0xFFC89B3C)
    val goldLo = Color(0xFFA87F2E)
    val goldChipBg = Color(0xFFFFF4DA)
    val goldChipText = Color(0xFF7A5A14)
    val overline = Color(0xFFA8861C)         // kickers, score/amount, BY FUND
    val eyebrow = Color(0xFF9A7A2A)          // "GIVE", pill text, day headers

    // Ink / text
    val ink = Color(0xFF0B0B0C)
    val ink600 = Color(0xFF59667C)
    val sub = Color(0xFF5B6472)
    val tertiary = Color(0xFF74808F)
    val ink300 = Color(0xFFB5BDC9)
    val border = Color(0x1A0A2540)           // #0A2540 @ 10%
    val track = Color(0x0F0A2540)            // @ 6% (segmented track)

    // Status
    val success = Color(0xFF1E7F4F)
    val successBg = Color(0xFFDCFCE7)
    val successText = Color(0xFF166534)
    val danger = Color(0xFFD4183D)
    val mutedBg = Color(0xFFEEF1F5)
    val ledgerDebit = Color(0xFF1B5FAE)
    val ledgerCredit = Color(0xFF1E7F4F)

    // Cancel-schedule destructive
    val cancelText = Color(0xFFDC2626)
    val cancelBg = Color(0xFFFEF2F2)
    val cancelBorder = Color(0xFFFECACA)

    // ── Gradients ──
    val cream = Brush.linearGradient(listOf(Color(0xFFF6F4EF), Color(0xFFEFE8DA)))
    val goldTile = Brush.verticalGradient(listOf(Color(0xFFE5BC3A), gold, Color(0xFFA8861C)))
    val statementHeader = Brush.linearGradient(listOf(navy.copy(alpha = 0.96f), navyDeep))
}

/** A fund designation with its icon + tints (iOS `Fund`). */
data class GiveFund(val id: String, val name: String, val tagline: String, val icon: ImageVector, val tint: Color, val fg: Color)

val GIVE_FUNDS = listOf(
    GiveFund("tithe", "Tithe", "A faithful portion", Icons.Filled.Percent, Color(0xFFFFF4DA), Color(0xFFC89B3C)),
    GiveFund("offering", "Offering", "Freewill worship", Icons.Filled.VolunteerActivism, Color(0xFFFEE2E2), Color(0xFFDC2626)),
    GiveFund("gift", "Gift", "A special gift", Icons.Filled.CardGiftcard, Color(0xFFF3E8FF), Color(0xFFA855F7)),
    GiveFund("mission", "Mission", "Reaching the lost", Icons.Filled.Public, Color(0xFFE0F2FE), Color(0xFF0EA5E9)),
    GiveFund("discipleship", "Discipleship", "Making disciples", Icons.Filled.MenuBook, Color(0xFFDCFCE7), Color(0xFF16A34A)),
)

fun giveFund(id: String?): GiveFund = GIVE_FUNDS.firstOrNull { it.id.equals(id?.trim(), true) }
    ?: GiveFund(id ?: "", (id ?: "Gift").replaceFirstChar { it.uppercase() }, "", Icons.Filled.CardGiftcard, Color(0xFFF3E8FF), Color(0xFFA855F7))

/** A payment method (iOS `PayMethod`). `provider == null` → "SOON" (disabled). */
data class GiveMethod(
    val id: String, val label: String, val sub: String,
    val badgeBg: Color, val badgeFg: Color, val badgeText: String?, val badgeIcon: ImageVector?,
    val provider: String?,
)

val GIVE_METHODS = listOf(
    GiveMethod("mpesa", "Pay with M-Pesa", "STK push to your phone", Color(0xFF16A34A), Color.White, "M-PESA", null, "mpesa"),
    GiveMethod("airtel", "Pay with Airtel Money", "Mobile money", Color(0xFFDC2626), Color.White, "AIRTEL", null, "airtel"),
    GiveMethod("equity", "Pay with Equity Bank", "Bank account", Color(0xFFA6093D), Color.White, null, Icons.Filled.AccountBalance, null),
    GiveMethod("card", "Pay with Card", "Visa · Mastercard", Color(0xFFEEF2FF), Color(0xFF6366F1), null, Icons.Filled.CreditCard, "card"),
    GiveMethod("wallet", "Apple / Google Pay", "Device wallet", Color(0xFFEEF2FF), Color(0xFF6366F1), null, Icons.Filled.Wallet, null),
    GiveMethod("paypal", "Pay with PayPal", "PayPal balance / linked", Color(0xFFE8F1FB), Color(0xFF0070BA), "PP", null, "paypal"),
)

val GIVE_PRESETS = listOf(200, 500, 1000, 2500, 5000)

/** Fee table (iOS `feeFor`) — KES major units in, fee (major) out. */
fun giveFee(amountMajor: Int): Int = when {
    amountMajor <= 100 -> 0
    amountMajor <= 500 -> 7
    amountMajor <= 1000 -> 13
    amountMajor <= 1500 -> 23
    amountMajor <= 2500 -> 33
    amountMajor <= 3500 -> 53
    amountMajor <= 5000 -> 57
    else -> Math.round(amountMajor * 0.012).toInt()
}

/** "KSh 1,000" from minor units (no decimals, grouped). */
fun ksh(minor: Int): String = "KSh " + "%,d".format(minor / 100)

/** "KSh 1,000" from major units. */
fun kshMajor(major: Int): String = "KSh " + "%,d".format(major)

fun giInter(size: Int, weight: FontWeight = FontWeight.Normal, kerning: Float = 0f) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp)

fun giSerif(size: Int, weight: FontWeight = FontWeight.SemiBold, kerning: Float = 0f) =
    TextStyle(fontFamily = Fraunces, fontWeight = weight, fontSize = size.sp, letterSpacing = kerning.sp, lineHeight = (size * 1.18f).sp)

/** Cream header chrome (giving tab + receipt). Gradient + gold glow + 24dp bottom corners + hairline. */
@Composable
fun GiveCreamHeaderBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(GIVE.cream),
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(listOf(GIVE.gold.copy(alpha = 0.20f), Color.Transparent), center = Offset(840f, -30f), radius = 560f),
            ),
        )
        content()
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(GIVE.border))
    }
}

/** Status → (label, bg, fg) for a giving record/receipt (iOS `statusChip`). */
fun giveStatus(status: String?): Triple<String, Color, Color> = when (status?.lowercase()?.trim()) {
    "succeeded", "settled", "completed" -> Triple("Succeeded", GIVE.successBg, GIVE.successText)
    "processing", "pending" -> Triple("Processing", GIVE.goldChipBg, GIVE.goldChipText)
    "failed" -> Triple("Failed", GIVE.danger.copy(alpha = 0.12f), GIVE.danger)
    "refunded", "cancelled" -> Triple("Refunded", GIVE.mutedBg, GIVE.ink600)
    else -> Triple("Processing", GIVE.goldChipBg, GIVE.goldChipText)
}
