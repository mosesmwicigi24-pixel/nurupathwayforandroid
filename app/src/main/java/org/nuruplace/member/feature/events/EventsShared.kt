// Events shared foundation — the `EV` palette + reusable primitives, ported from the
// iOS Events screens (global `Nuru` enum + EventDetail's screen-local `EvD` enum +
// `Ev.categoryColor`). Every Events screen (tab · calendar · detail · announcement)
// uses these so the flow is pixel-consistent. Screen-specific cards live in their own
// screen files; only the truly-shared bits (palette, cream header, event card, date
// formatters) are here.
package org.nuruplace.member.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.data.net.CalendarOccurrence
import org.nuruplace.member.ui.components.FitImage
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

/**
 * iOS Events palette — merges the global `Nuru` tokens the tab/calendar use with the
 * screen-local `EvD` hexes EventDetail uses. Two golds on purpose: `gold` #C89B3C is
 * the global chrome gold; `goldDetail` #C9A227 is EventDetail's Figma gold.
 */
object EV {
    // Surfaces
    val paper = Color(0xFFF6F4EE)
    val white = Color(0xFFFFFFFF)
    val tile = Color(0xFFFBF8F1)          // inset tiles / secondary buttons / post rows

    // Navy
    val navy = Color(0xFF0B1F33)          // global chrome navy (tab header, chips, week-strip selected)
    val navyInk = Color(0xFF0A1628)       // EventDetail primary text / darker navy
    val navyDeep = Color(0xFF060F1C)      // card gradient bottom
    val navy700 = Color(0xFF143559)
    val navyBase = Color(0xFF0A2540)      // scrim / border / shadow base

    // Gold
    val gold = Color(0xFFC89B3C)          // global gold
    val goldDetail = Color(0xFFC9A227)    // EvD gold
    val goldDeep = Color(0xFFB6862F)      // gold gradient bottom
    val goldLight = Color(0xFFE6C068)     // "CALENDAR" eyebrow on navy
    val overline = Color(0xFFA8861C)      // muted gold overlines (week-strip month, rail headers, list header)
    val eyebrowGold = Color(0xFF9A7A2A)   // header eyebrow + sub-page eyebrow pill
    val chipText = Color(0xFF7A5A14)

    // Ink / text
    val ink = Color(0xFF0A1628)           // primary (EvD.ink)
    val body = Color(0xFF3A4A5F)          // about copy / roster names
    val secondary = Color(0xFF59667C)     // ink600 — subtitles / inactive
    val tertiary = Color(0xFF74808F)      // faint — meta labels / captions
    val ink300 = Color(0xFFB5BDC9)        // chevrons / faint year

    // Hairlines (#0A2540 at various alphas)
    val border = Color(0x1A0A2540)        // @0.10 — default hairline
    val borderSoft = Color(0x120A2540)    // @0.07 — card stroke
    val borderFaint = Color(0x0F0A2540)   // @0.06 — tile stroke

    // Status greens
    val going = Color(0xFF16A34A)
    val goingDeep = Color(0xFF15803D)
    val goingText = Color(0xFF166534)
    val liveDot = Color(0xFF22C55E)
    val liveBg = Color(0xFFDCFCE7)
    val liveText = Color(0xFF15803D)
    val maybe = Color(0xFFD97706)
    val declined = Color(0xFF74808F)

    // Composer
    val composerTop = Color(0xFFFFF8E6)
    val placeholder = Color(0xFF9A8C6A)

    // Gradients
    val creamHeader = Brush.linearGradient(listOf(Color(0xFFF6F4EF), Color(0xFFEFE8DA)))
    val navyCard = Brush.linearGradient(listOf(navy, navyDeep))          // CALENDAR dark card
    val goldTile = Brush.verticalGradient(listOf(Color(0xFFE5BC3A), goldDetail, Color(0xFFA8861C))) // icon tile / check-in
    val goldCta = Brush.linearGradient(listOf(goldDetail, goldDeep))     // Post / gold button
    val buzzing = Brush.linearGradient(listOf(going, goingDeep))
    val selectedDay = Brush.linearGradient(listOf(navy, navyDeep))       // month-grid selected cell

    // Deterministic roster/buzz avatar accents (hash % 7)
    val avatarPalette = listOf(
        Color(0xFF0A1628), Color(0xFFC9A227), Color(0xFF16A34A), Color(0xFF0EA5E9),
        Color(0xFFA855F7), Color(0xFFDC2626), Color(0xFFD97706),
    )

    fun avatarAccent(seed: String): Color =
        avatarPalette[(seed.hashCode().let { if (it < 0) -it else it }) % avatarPalette.size]
}

/** Category → accent color, ported from iOS `Ev.categoryColor`. */
fun evCategory(cat: String?): Color = when (cat?.lowercase()?.trim()) {
    "worship" -> Color(0xFFC89B3C)
    "cell" -> Color(0xFF6366F1)
    "leaders" -> Color(0xFF0EA5E9)
    "youth" -> Color(0xFF16A34A)
    "marketplace" -> Color(0xFFE07B39)
    else -> Color(0xFF59667C)
}

/** Inter text style — delegates to the canonical schema (ui/theme/TypeSchema.kt). */
fun evInter(size: Int, weight: FontWeight = FontWeight.Normal, kerning: Float = 0f) =
    nuruSans(size, weight, kerning.takeIf { it != 0f })

/** Fraunces (serif) style — delegates to the canonical schema (ui/theme/TypeSchema.kt). */
fun evSerif(size: Int, weight: FontWeight = FontWeight.Medium, kerning: Float = 0f) =
    nuruSerif(size, weight, kerning.takeIf { it != 0f })

/** Uppercased tracked overline (Inter bold). */
@Composable
fun EVOverline(text: String, color: Color = EV.overline, size: Int = 11, kerning: Float = 1.4f, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = evInter(size, FontWeight.Bold, kerning), color = color, modifier = modifier)

// ─────────────────────────────────────────────────────────────────────────────
// Cream header chrome — shared by the Events tab header and the sub-page headers
// (Calendar, AnnouncementDetail). Gradient cream + gold glow + 30dp bottom corners
// + a 1px bottom hairline. Content is provided by the caller inside the Box.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EvCreamHeaderBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(EV.creamHeader),
    ) {
        // Warm gold glow, top-right (iOS: gold@0.27 circle, blurred, offset(60,-80)).
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(EV.gold.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(840f, -30f),
                        radius = 560f,
                    ),
                ),
        )
        content()
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(EV.border))
    }
}

/** Sub-page header (Calendar / AnnouncementDetail): back · eyebrow pill · serif title · subtitle · gold accent bar. */
@Composable
fun EvSubHeader(eyebrow: String, title: String, subtitle: String, onBack: () -> Unit) {
    EvCreamHeaderBox {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(EV.white)
                        .border(1.dp, EV.border, RoundedCornerShape(16.dp)).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.ChevronLeft, "Back", tint = EV.navy, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(EV.white)
                        .border(1.dp, EV.border, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                ) { Text(eyebrow.uppercase(), style = evInter(9, FontWeight.Bold, 1.5f), color = EV.eyebrowGold) }
            }
            Text(title, style = evSerif(27, FontWeight.SemiBold), color = EV.navy, modifier = Modifier.padding(top = 16.dp))
            if (subtitle.isNotBlank()) Text(subtitle, style = evInter(12), color = EV.secondary, modifier = Modifier.padding(top = 6.dp))
            Box(
                Modifier.padding(top = 12.dp).width(48.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(EV.gold, EV.gold.copy(alpha = 0f)))),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared event card — used by the Events tab ("Today's gatherings") and the
// Calendar "Upcoming" list. Port of iOS EventCardView.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EvCardView(occ: CalendarOccurrence, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = evCategory(occ.category)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(EV.white)
            .border(1.dp, EV.border, RoundedCornerShape(22.dp)).clickable { onClick() },
    ) {
        // Cover — FitImage grows to natural aspect; overlays positioned on top.
        Box(Modifier.fillMaxWidth()) {
            FitImage(occ.primaryImageUrl, fallback = Brush.linearGradient(listOf(EV.navy700, EV.navy, accent)))
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, EV.navy.copy(alpha = 0.62f))),
                ),
            )
            // Date badge (top-start)
            Column(
                Modifier.align(Alignment.TopStart).padding(12.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(EV.white),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(evWeekdayShort(occ.startAt), style = evInter(8, FontWeight.Bold, 0.8f), color = accent)
                Text(evDayNum(occ.startAt), style = evSerif(17, FontWeight.SemiBold), color = EV.navy)
            }
            // Countdown chip (bottom-start)
            val cd = evCountdown(occ.startAt)
            if (cd.isNotBlank()) {
                val urgent = cd == "Today" || cd == "Tomorrow"
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .then(if (urgent) Modifier.background(EV.goldTile) else Modifier.background(EV.navy.copy(alpha = 0.5f)))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(Icons.Filled.Schedule, null, tint = if (urgent) EV.navy else EV.white, modifier = Modifier.size(10.dp))
                    Text(cd, style = evInter(8, FontWeight.Bold), color = if (urgent) EV.navy else EV.white)
                }
            }
            // Rescheduled pill (top-end) — wire truth: a moved occurrence arrives
            // with rescheduled=true and the NEW start/end already applied, so the
            // pill is the member's only cue the time changed. (Cancelled
            // occurrences never reach the client — dropped server-side.)
            if (occ.rescheduled) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(12.dp)
                        .clip(RoundedCornerShape(999.dp)).background(EV.goldTile)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text("RESCHEDULED", style = evInter(8, FontWeight.Bold, 1f), color = EV.navy) }
            }
            // Category tag (bottom-end)
            occ.category?.takeIf { it.isNotBlank() }?.let { c ->
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp)
                        .clip(RoundedCornerShape(999.dp)).background(accent.copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text(c.uppercase(), style = evInter(8, FontWeight.Bold, 1f), color = EV.white) }
            }
        }
        // Body
        Column(Modifier.padding(16.dp)) {
            Text(occ.title, style = evSerif(15, FontWeight.SemiBold), color = EV.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            occ.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = evInter(11), color = EV.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EvMetaBit(Icons.Filled.Schedule, evTimeRange(occ.startAt, occ.endAt))
                occ.location?.takeIf { it.isNotBlank() }?.let { EvMetaBit(Icons.Filled.Place, it) }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(EV.border))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Person, null, tint = EV.secondary, modifier = Modifier.size(15.dp))
                Text(
                    if (occ.going > 0) "${occ.going} going" else "Be the first to RSVP",
                    style = evInter(11, FontWeight.SemiBold), color = EV.secondary,
                )
            }
        }
    }
}

@Composable
private fun EvMetaBit(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = EV.secondary, modifier = Modifier.size(12.dp))
        Text(text, style = evInter(11), color = EV.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date/time formatters — everything shown in East Africa Time to match the iOS
// "East Africa Time" header. Robust ISO parsing (instant / offset / local / date).
// ─────────────────────────────────────────────────────────────────────────────

val EV_ZONE: ZoneId = ZoneId.of("Africa/Nairobi")

fun evZdt(iso: String?): ZonedDateTime? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso).atZone(EV_ZONE) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(EV_ZONE) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso).atZone(EV_ZONE) }.getOrNull()
        ?: runCatching { LocalDate.parse(iso).atStartOfDay(EV_ZONE) }.getOrNull()
}

private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun evTime(iso: String?): String = evZdt(iso)?.format(TIME_FMT).orEmpty()

fun evTimeRange(start: String?, end: String?): String {
    val s = evTime(start)
    val e = evTime(end)
    return when {
        s.isBlank() -> ""
        e.isBlank() || e == s -> s
        else -> "$s – $e"
    }
}

/** "Sunday, July 5" */
fun evDateFull(iso: String?): String =
    evZdt(iso)?.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)).orEmpty()

/** "SUN" */
fun evWeekdayShort(iso: String?): String =
    evZdt(iso)?.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))?.uppercase().orEmpty()

/** "5" */
fun evDayNum(iso: String?): String = evZdt(iso)?.dayOfMonth?.toString().orEmpty()

/** Today / Tomorrow / In N days ("" when past or unknown). */
fun evCountdown(iso: String?): String {
    val d = evZdt(iso)?.toLocalDate() ?: return ""
    val days = ChronoUnit.DAYS.between(LocalDate.now(EV_ZONE), d)
    return when {
        days < 0L -> ""
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }
}
