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
import org.nuruplace.member.ui.components.CappedFontScale
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
import org.nuruplace.member.ui.icons.Lucide

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

    // Deterministic roster/buzz avatar accents: navy or gold only (§8.1
    // rule 1) — seven hues before, red and amber among them.
    val avatarPalette = listOf(Color(0xFF0A1628), Color(0xFFA87F2E))

    fun avatarAccent(seed: String): Color =
        avatarPalette[(seed.hashCode().let { if (it < 0) -it else it }) % avatarPalette.size]
}

/** An event's accent — gold for every category. A category is its word, on
 *  rule 6's pills (EXPERIENCE.md §8.1 rules 1 and 6); it was a hue each
 *  (indigo cells, sky leaders, green youth, orange marketplace). */
@Suppress("UNUSED_PARAMETER")
fun evCategory(cat: String?): Color = EV.gold

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

/** Sub-page header (Calendar / Announcements / an announcement): the pushed page's header. */
@Composable
fun EvSubHeader(eyebrow: String, title: String, subtitle: String, onBack: () -> Unit) =
    // The pushed page's one header — back · kicker · title · one line (§8.1
    // rule 2; final walk C16: "ANNOUNCEMENT" sat as a white pill at the far
    // right, over a gold underline) — iOS EvSubHeader → NuruPushedHeader.
    org.nuruplace.member.ui.components.PushedHeader(kicker = eyebrow, title = title, line = subtitle, onBack = onBack)

// ─────────────────────────────────────────────────────────────────────────────
// Shared event card — used by the Events tab ("Today's gatherings") and the
// Calendar "Upcoming" list. Port of iOS EventCardView.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EvCardView(occ: CalendarOccurrence, onClick: () -> Unit, modifier: Modifier = Modifier, myRsvp: String? = null) {
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
            // Date badge (top-start). A gathering in another month carries
            // its month (§8.1 rule 8; final walk C2: "SUN 11", "WED 25" on
            // cards weeks out), as iOS. A figure in a fixed tile keeps the
            // everyday size (§9.6 #4); the card's countdown carries the day,
            // and grows.
            val month = evOtherMonth(occ.startAt)
            CappedFontScale(1f) {
                Column(
                    Modifier.align(Alignment.TopStart).padding(12.dp).size(width = 48.dp, height = if (month == null) 48.dp else 62.dp)
                        .clip(RoundedCornerShape(16.dp)).background(EV.white),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(evWeekdayShort(occ.startAt), style = evInter(11, FontWeight.Bold, 0.8f), color = accent, maxLines = 1, softWrap = false)
                    Text(evDayNum(occ.startAt), style = evSerif(18, FontWeight.SemiBold), color = EV.navy, maxLines = 1, softWrap = false)
                    if (month != null) {
                        Text(month, style = evInter(11, FontWeight.Bold, 0.8f), color = EV.secondary, maxLines = 1, softWrap = false)
                    }
                }
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
                    Icon(Lucide.Clock4, null, tint = if (urgent) EV.navy else EV.white, modifier = Modifier.size(14.dp))
                    Text(cd, style = evInter(11, FontWeight.Bold), color = if (urgent) EV.navy else EV.white)
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
                ) { Text("RESCHEDULED", style = evInter(11, FontWeight.Bold, 1f), color = EV.navy) }
            }
            // Category tag (bottom-end)
            occ.category?.takeIf { it.isNotBlank() }?.let { c ->
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp)
                        // The word on a white pill (rule 6) — no category hue.
                        .clip(RoundedCornerShape(999.dp)).background(EV.white)
                        .border(1.dp, EV.border, RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text(c.uppercase(), style = evInter(11, FontWeight.Bold, 1f), color = EV.navy) }
            }
        }
        // Body
        Column(Modifier.padding(16.dp)) {
            // An event's title wraps to two lines, never cut (§8.1 rule 9).
            Text(occ.title, style = evSerif(15, FontWeight.SemiBold), color = EV.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            occ.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = evInter(11), color = EV.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EvMetaBit(Lucide.Clock4, evTimeRange(occ.startAt, occ.endAt))
                occ.location?.takeIf { it.isNotBlank() }?.let { EvMetaBit(Lucide.MapPin, it) }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(EV.border))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Lucide.User, null, tint = EV.secondary, modifier = Modifier.size(14.dp))
                Text(
                    evGoingLine(occ.going, mine = myRsvp == "going") ?: "Be the first to RSVP",
                    style = evInter(11, FontWeight.SemiBold), color = EV.secondary,
                )
            }
        }
    }
}

@Composable
private fun EvMetaBit(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = EV.secondary, modifier = Modifier.size(14.dp))
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

/** An end we actually BELIEVE, or null: absent, unparsable, not after the
 *  start, or an implausible span (> 12h — even a kesha ends) are untrusted.
 *  A wrong time on a church invitation costs real attendance (owner,
 *  2026-08-24; iOS parity — Ev.trustedEnd). */
fun evTrustedEnd(start: String?, end: String?): java.time.ZonedDateTime? {
    val s = evZdt(start) ?: return null
    val e = evZdt(end) ?: return null
    if (!e.isAfter(s)) return null
    if (java.time.Duration.between(s, e).toHours() > 12) return null
    return e
}

fun evTimeRange(start: String?, end: String?): String {
    val s = evTime(start)
    if (s.isBlank()) return ""
    val e = evTrustedEnd(start, end)?.format(TIME_FMT) ?: return s
    return if (e == s) s else "$s – $e"
}

/** "Sun 5 Jul" — the one date form (§8.1 rule 8); it read "Sunday, July 5". */
fun evDateFull(iso: String?): String =
    evZdt(iso)?.let { org.nuruplace.member.util.NuruDates.day(it.toLocalDate()) }.orEmpty()

/** "SUN" */
fun evWeekdayShort(iso: String?): String =
    evZdt(iso)?.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))?.uppercase().orEmpty()

/** "5" */
fun evDayNum(iso: String?): String = evZdt(iso)?.dayOfMonth?.toString().orEmpty()

/** Who is going, said from the member's side (Q5; final walk C5, Android
 *  #12): their own RSVP is theirs — "You're going", not "1 going". Null when
 *  nobody is going yet. */
fun evGoingLine(going: Int, mine: Boolean): String? = when {
    mine && going <= 1 -> "You're going"
    mine -> "You and ${evOthers(going - 1)} are going"
    going > 0 -> "$going going"
    else -> null
}

/** The gathering's GOING tile: "You", "You and 2 others", "3 people". Null
 *  when nobody is going yet. */
fun evGoingTile(going: Int, mine: Boolean): String? = when {
    mine && going <= 1 -> "You"
    mine -> "You and ${evOthers(going - 1)}"
    going == 1 -> "1 person"
    going > 1 -> "$going people"
    else -> null
}

private fun evOthers(n: Int): String = if (n == 1) "1 other" else "$n others"

/** "NOV" when [iso] falls in a month other than [today]'s, for the card's
 *  date chip; null in this month (§8.1 rule 8; iOS Ev.otherMonthLabel). */
fun evOtherMonth(iso: String?, today: LocalDate = LocalDate.now(EV_ZONE)): String? =
    evZdt(iso)?.toLocalDate()?.let { evOtherMonth(it, today) }

internal fun evOtherMonth(date: LocalDate, today: LocalDate): String? =
    if (date.year == today.year && date.month == today.month) null
    else date.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).uppercase()

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
