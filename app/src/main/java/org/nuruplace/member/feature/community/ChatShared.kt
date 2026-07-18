// Chat shared foundation — the `CHAT` palette + reusable primitives, ported from the
// iOS Nuru Connect screens (global `Nuru` + the thread-local `Aurora` enum + `rowTints`
// + `senderPalette`). The hub (ChatScreen), thread (ChatThreadScreen) and compose
// (NewMessageScreen) all use these so the surface is pixel-consistent.
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** iOS Nuru Connect palette — merges global `Nuru` tokens (inbox) with the thread-local `Aurora` enum. */
object CHAT {
    // Surfaces
    val paper = Color(0xFFF6F4EE)
    val white = Color(0xFFFFFFFF)
    val surface = Color(0xFFFBF8F1)

    // Navy
    val navy = Color(0xFF0B1F33)
    val navyInk = Color(0xFF0A1628)     // bubble gradient top / segmented selected
    val navyInk2 = Color(0xFF16273F)    // bubble gradient bottom

    // Gold
    val gold = Color(0xFFC89B3C)
    val goldHi = Color(0xFFE0B85E)
    val goldLo = Color(0xFFA87F2E)
    val goldLight = Color(0xFFE6C068)
    val goldTint = Color(0xFFFFF4C7)
    val goldDeep = Color(0xFFA8761A)    // gold text on light (prayer chip)
    val overline = Color(0xFFB08A1E)    // section overlines "# YOUR SPACES"
    val eyebrow = Color(0xFF9A7A2A)     // header eyebrow, verse kicker + reference

    // Ink / text
    val ink600 = Color(0xFF59667C)
    val ink500 = Color(0xFF6A7686)
    val faint = Color(0xFF74808F)
    val border = Color(0x1A0A2540)      // #0A2540 @ 10%
    val hairline = Color(0x140B1F33)    // #0B1F33 @ 8%

    // Status
    val activeGreen = Color(0xFF16A34A)
    val activeText = Color(0xFF15803D)
    val activeBg = Color(0x1716A34A)    // #16A34A @ 9%
    val online = Color(0xFF25D366)
    val doubleCheck = Color(0xFFBCC4CE)
    val tintBlue = Color(0xFFE8EEF7)
    val navyMid = Color(0xFF315F8C)

    // Thread (Aurora)
    val canvas = Color(0xFFE9E6DF)
    val bubbleLight = Color(0xFFF3F4F3)
    val textDark = Color(0xFF17283D)
    val meta = Color(0xFF9AA3AF)
    val quoteBody = Color(0xFF5B6472)
    val dayGold = Color(0xFFA8861C)

    // ── Gradients ──
    val cream = Brush.linearGradient(listOf(Color(0xFFF6F4EF), Color(0xFFEFE8DA)))
    val storyRing = Brush.linearGradient(listOf(Color(0xFFE6C068), Color(0xFFC89B3C), Color(0xFFB07D2E)))
    val selectedSeg = Brush.linearGradient(listOf(navyInk, navyInk2))
    val bubbleInk = Brush.linearGradient(listOf(navyInk, navyInk2))
    val threadBg = Brush.linearGradient(listOf(Color(0xFFF6F4EE), Color(0xFFF1ECE1)))

    // AI "Quick help from Nuru" card
    val aiCard = Brush.linearGradient(listOf(Color(0xFF2A1259), Color(0xFF0A1628), Color(0xFF053F30)))
    val aiBadge = Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF34D399)))
    val aiOrb = Brush.radialGradient(listOf(Color(0xFFC4B5FD), Color(0xFF7C3AED), Color(0xFF2A1259)))
    val aiBorderRing = Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFFC89B3C), Color(0xFF34D399)))
    val aiDot = Color(0xFF34D399)
    val aiPurpleGlow = Color(0xFF7C3AED)
    val aiGreenGlow = Color(0xFF10B981)

    // Per-space / per-person tile palette (index-cycled)
    val rowTints = listOf(
        Color(0xFFC89B3C), Color(0xFF6366F1), Color(0xFF0EA5E9),
        Color(0xFF16A34A), Color(0xFFDB2777), Color(0xFF0D9488),
    )
    // Colored sender-name accents inside incoming space bubbles (hash % 8)
    val senderPalette = listOf(
        Color(0xFF4F46E5), Color(0xFF0284C7), Color(0xFF0D9488), Color(0xFF059669),
        Color(0xFFDB2777), Color(0xFFC2410C), Color(0xFF7C3AED), Color(0xFFB45309),
    )
}

/** Tile gradient for a "#"/initials squircle (tint → tint@71%). */
fun chatTileBrush(tint: Color): Brush = Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.71f)))

/** Row tint cycled by index (space/dm/group/person offsets applied by caller). */
fun chatRowTint(index: Int): Color = CHAT.rowTints[((index % 6) + 6) % 6]

/** Colored sender accent — self is always gold; others hashed into senderPalette. */
fun chatSenderAccent(name: String, mine: Boolean): Color {
    if (mine) return CHAT.gold
    var h = 0
    for (c in name) h = h * 31 + c.code
    return CHAT.senderPalette[((h % 8) + 8) % 8]
}

// Delegates to the canonical schema (ui/theme/TypeSchema.kt) — edit rhythm there.
fun cInter(size: Int, weight: FontWeight = FontWeight.Normal, kerning: Float = 0f) =
    nuruSans(size, weight, kerning.takeIf { it != 0f })

fun cSerif(size: Int, weight: FontWeight = FontWeight.Medium, kerning: Float = 0f) =
    nuruSerif(size, weight, kerning.takeIf { it != 0f })

/** Up to two leading letters of the name, uppercased. */
fun chatInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

// ── Cream header chrome (hub + thread + compose share this) ──
@Composable
fun ChatCreamHeaderBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(CHAT.cream),
    ) {
        Box(
            Modifier.matchParentSize().background(
                Brush.radialGradient(
                    colors = listOf(CHAT.gold.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(840f, -30f),
                    radius = 560f,
                ),
            ),
        )
        content()
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(CHAT.border))
    }
}

/** Rounded-square avatar/tile — gradient tint fill with a "#" glyph or initials (or a photo). */
@Composable
fun ChatSquircleAvatar(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    radius: Dp = 18.dp,
    textSize: Int = 18,
    avatarUrl: String? = null,
) {
    Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(chatTileBrush(tint)), contentAlignment = Alignment.Center) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(text, style = cInter(textSize, FontWeight.Bold), color = Color.White)
        }
    }
}

/** Circular avatar (DM rows + story rail) — tintBlue disc + navyMid initials, or a photo. */
@Composable
fun ChatCircleAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    avatarUrl: String? = null,
    textSize: Int = 16,
) {
    Box(modifier.size(size).clip(CircleShape).background(CHAT.tintBlue), contentAlignment = Alignment.Center) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(chatInitials(name), style = cInter(textSize, FontWeight.SemiBold), color = CHAT.navyMid)
        }
    }
}

// ── Date/time formatters (device-local) ──
private val CHAT_ZONE: ZoneId = ZoneId.systemDefault()

fun chatZdt(iso: String?): ZonedDateTime? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso).atZone(CHAT_ZONE) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(CHAT_ZONE) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso).atZone(CHAT_ZONE) }.getOrNull()
        ?: runCatching { LocalDate.parse(iso).atStartOfDay(CHAT_ZONE) }.getOrNull()
}

private val T_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val T_WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val T_DAYMON = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val T_FULLDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val T_MONDAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** Inbox-row timestamp: today→time, this week→weekday, older→"d MMM". */
fun chatRowTime(iso: String?): String {
    val z = chatZdt(iso) ?: return ""
    val days = ChronoUnit.DAYS.between(z.toLocalDate(), LocalDate.now(CHAT_ZONE))
    return when {
        days <= 0L -> z.format(T_TIME)
        days in 1..6 -> z.format(T_WEEKDAY)
        else -> z.format(T_DAYMON)
    }
}

/** Message bubble timestamp "5:07 PM". */
fun chatMsgTime(iso: String?): String = chatZdt(iso)?.format(T_TIME).orEmpty()

/** Thread day divider: TODAY / YESTERDAY / weekday (this week) / "MMM d" (older), uppercased. */
fun chatDayDivider(iso: String?): String {
    val z = chatZdt(iso) ?: return ""
    val days = ChronoUnit.DAYS.between(z.toLocalDate(), LocalDate.now(CHAT_ZONE))
    return when (days) {
        0L -> "TODAY"
        1L -> "YESTERDAY"
        in 2..6 -> z.format(T_FULLDAY).uppercase()
        else -> z.format(T_MONDAY).uppercase()
    }
}

/** Day key for grouping consecutive messages under one divider. */
fun chatDayKey(iso: String?): String = chatZdt(iso)?.toLocalDate()?.toString() ?: ""

// ── Connections (Chat Redesign C3a — "no unsolicited DMs") ──

/** True for the specific 403 POST /chat/dms throws for a brand-new thread
 *  between two ordinary members with no accepted connection yet — distinct
 *  from an ordinary 403 FORBIDDEN_SCOPE refusal. Mirrors [isPasswordRequired]
 *  in BroadcastStepUp.kt. */
fun isConsentRequired(e: Throwable): Boolean {
    val http = e as? retrofit2.HttpException ?: return false
    if (http.code() != 403) return false
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    return "CONSENT_REQUIRED" in body
}

// ── My Discipler / Talk with My Pastor (Chat Redesign C3b) ──

/** True for GET /chat/discipler/conversation's specific 404 — no CURRENT
 *  discipler assignment exists (details.no_discipler) — distinct from an
 *  ordinary not-found. Drives the tab's empty state. */
fun isNoDiscipler(e: Throwable): Boolean {
    val http = e as? retrofit2.HttpException ?: return false
    if (http.code() != 404) return false
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    return "no_discipler" in body
}

/** True for POST /chat/pastoral's rare 404 — nothing resolves to a pastor at
 *  all (no assignment, no congregation default, no SuperAdmin to fall back
 *  to; details.no_pastor). */
fun isNoPastor(e: Throwable): Boolean {
    val http = e as? retrofit2.HttpException ?: return false
    if (http.code() != 404) return false
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    return "no_pastor" in body
}

/** True for the 403 FORBIDDEN_SCOPE every DM-flavoured route (discipler,
 *  pastoral, ordinary DM) throws for a minor — "Direct messages are
 *  unavailable for minors" (D-M6). Distinguishes the minor-safe note from an
 *  ordinary refusal. */
fun isMinorBlocked(e: Throwable): Boolean {
    val http = e as? retrofit2.HttpException ?: return false
    if (http.code() != 403) return false
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    return "FORBIDDEN_SCOPE" in body && "minor" in body.lowercase()
}

/** Where the caller stands with one other member right now — derived
 *  client-side from `GET /chat/connections` + `GET /chat/connections/
 *  requests?direction=...`, never sent by the server as a single field. */
sealed interface ConnectionState {
    data object NotConnected : ConnectionState
    data class RequestSent(val requestId: String) : ConnectionState
    data class RequestReceived(val requestId: String) : ConnectionState
    data object Connected : ConnectionState
    data object Blocked : ConnectionState
}

fun connectionStateFor(
    userId: String,
    connections: List<org.nuruplace.member.data.net.ConnectionRow>,
    incoming: List<org.nuruplace.member.data.net.ConnectionRequestRow>,
    outgoing: List<org.nuruplace.member.data.net.ConnectionRequestRow>,
): ConnectionState {
    if (connections.any { it.userId == userId && it.status == "accepted" }) return ConnectionState.Connected
    if (connections.any { it.userId == userId && it.status == "blocked" }) return ConnectionState.Blocked
    outgoing.firstOrNull { it.userId == userId }?.let { return ConnectionState.RequestSent(it.requestId) }
    incoming.firstOrNull { it.userId == userId }?.let { return ConnectionState.RequestReceived(it.requestId) }
    return ConnectionState.NotConnected
}
