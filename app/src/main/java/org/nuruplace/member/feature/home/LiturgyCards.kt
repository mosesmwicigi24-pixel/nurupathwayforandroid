// The liturgy Home + celebrations rail (intelligence Phase 4) — port of iOS
// LiturgyCards.swift.
//   • LiturgyCard — the current part's prayer line (morning/midday/evening/
//     night), coloured by the church season. Self-loading; renders nothing
//     until the line arrives.
//   • CelebrationsRail — the congregation's recent milestones with one-tap
//     blessings (🙌 ❤️ 🔥), optimistic updates.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.BlessBody
import org.nuruplace.member.data.net.CommunityMoment
import org.nuruplace.member.data.net.HomeLiturgy
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import org.nuruplace.member.data.net.HomeEcho
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.feature.community.Avatar
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.components.pressScale
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType

private val LitGold = Color(0xFFE8CA6C)

@Composable
fun LiturgyCard() {
    var lit by remember { mutableStateOf<HomeLiturgy?>(null) }
    LaunchedEffect(Unit) { lit = runCatching { Net.client.api.homeLiturgy() }.getOrNull() }
    val l = lit ?: return
    val partLabel = when (l.part) {
        "morning" -> "MORNING"; "midday" -> "MIDDAY"; "evening" -> "EVENING"; else -> "NIGHT"
    }
    val partEmoji = when (l.part) {
        "morning" -> "🌅"; "midday" -> "☀️"; "evening" -> "🌆"; else -> "🌙"
    }
    // Home breathes with the hours: the card carries a photograph of the hour
    // it names (dawn/noon/dusk/night) under a navy scrim so the serif stays
    // legible. Image + scrim are matchParentSize inside the Box (never affect
    // layout) and clipped to the card — the ornament rule (iOS ios#72 parity).
    val art = l.art?.takeIf { it.url.isNotBlank() }
    val textShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 2f), blurRadius = 6f)
    if (art != null) {
        // Taller tableau: the hour + brand at the top, the prayer line resting at
        // the BOTTOM under the deep-navy block where the veil is deepest, so the
        // type reads clearly (owner ask). matchParentSize behind, clipped.
        Box(Modifier.fillMaxWidth().height(206.dp).clip(RoundedCornerShape(20.dp))) {
            AsyncImage(
                model = art.url, contentDescription = art.alt, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(Modifier.matchParentSize().background(DeepNavyBlockBrush))
            LitKicker(
                Modifier.align(Alignment.TopStart).padding(18.dp),
                partEmoji, partLabel, l.isSunday, l.season, onArt = true, textShadow = textShadow,
            )
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(
                    l.line,
                    style = NuruType.rowTitle.copy(fontSize = 19.sp, lineHeight = 27.sp, shadow = textShadow),
                    color = Color.White,
                )
                l.scriptureRef?.let { ref ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ref, style = NuruType.micro, color = Color.White.copy(alpha = 0.92f), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    } else {
        // Offline / older backend: the classic navy card, content-sized.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0F2A47), Color(0xFF0A1C33))))
                .padding(18.dp),
        ) {
            LitKicker(Modifier, partEmoji, partLabel, l.isSunday, l.season, onArt = false, textShadow = textShadow)
            Spacer(Modifier.height(10.dp))
            Text(l.line, style = NuruType.rowTitle.copy(fontSize = 18.sp, lineHeight = 26.sp), color = Color.White)
            l.scriptureRef?.let { ref ->
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        ref, style = NuruType.micro, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** The hour + Nuru Pathway brand row — shared by the tableau and the classic card. */
@Composable
private fun LitKicker(
    modifier: Modifier = Modifier,
    partEmoji: String,
    partLabel: String,
    isSunday: Boolean,
    season: String,
    onArt: Boolean,
    textShadow: Shadow,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(partEmoji, style = NuruType.body)
        Spacer(Modifier.width(7.dp))
        Text(
            if (isSunday) "SUNDAY · $partLabel" else "$partLabel · ${season.uppercase()}",
            style = NuruType.micro.copy(shadow = if (onArt) textShadow else null),
            color = if (onArt) Color(0xFFF2DDA0) else LitGold,
            fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Nuru.goldGradient),
            contentAlignment = Alignment.Center,
        ) { Text("✝", color = Color.White, style = NuruType.micro) }
        Spacer(Modifier.width(4.dp))
        Text(
            "Nuru Pathway",
            style = NuruType.micro.copy(shadow = if (onArt) textShadow else null),
            color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
        Text("  ✔", style = NuruType.micro, color = Color(0xFFF2DDA0))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun CelebrationsRail() {
    val scope = rememberCoroutineScope()
    var moments by remember { mutableStateOf<List<CommunityMoment>>(emptyList()) }
    LaunchedEffect(Unit) {
        moments = runCatching { Net.client.api.communityMoments().data }.getOrDefault(emptyList())
    }
    if (moments.isEmpty()) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎉", style = NuruType.caption)
            Spacer(Modifier.width(6.dp))
            Text("CELEBRATE THE FAMILY", style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            moments.forEach { m ->
                MomentCard(m) { kind ->
                    // Optimistic: move my blessing to the tapped kind.
                    moments = moments.map { cur ->
                        if (cur.momentId != m.momentId) cur
                        else {
                            var a = cur.amenCount; var h = cur.heartCount; var f = cur.fireCount
                            when (cur.myBlessing) { "amen" -> a--; "heart" -> h--; "fire" -> f-- }
                            when (kind) { "amen" -> a++; "heart" -> h++; "fire" -> f++ }
                            cur.copy(amenCount = a, heartCount = h, fireCount = f, myBlessing = kind)
                        }
                    }
                    scope.launch { runCatching { Net.client.api.blessMoment(m.momentId, BlessBody(kind)) } }
                }
            }
        }
    }
}

@Composable
private fun MomentCard(m: CommunityMoment, onBless: (String) -> Unit) {
    Column(
        Modifier.width(210.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
            .border(1.dp, Nuru.border, RoundedCornerShape(18.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name = m.fullName, url = m.avatarUrl, size = 30.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                m.fullName.substringBefore(' '),
                style = NuruType.caption, color = Nuru.ink, fontWeight = FontWeight.Bold, maxLines = 1,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            m.title,
            style = NuruType.rowTitle.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = Nuru.navy, maxLines = 2,
            modifier = Modifier.heightIn(min = 40.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BlessChip("🙌", m.amenCount, m.myBlessing == "amen") { onBless("amen") }
            BlessChip("❤️", m.heartCount, m.myBlessing == "heart") { onBless("heart") }
            BlessChip("🔥", m.fireCount, m.myBlessing == "fire") { onBless("fire") }
        }
    }
}

@Composable
private fun BlessChip(emoji: String, count: Int, mine: Boolean, onTap: () -> Unit) {
    val view = LocalView.current
    Row(
        Modifier.pressScale().clip(RoundedCornerShape(999.dp))
            .background(if (mine) LitGold.copy(alpha = 0.35f) else Nuru.tintBlue)
            .border(1.dp, if (mine) Color(0xFFC9A227).copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(999.dp))
            .clickable { Haptics.tap(view); onTap() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = NuruType.micro)
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text("$count", style = NuruType.micro, fontWeight = FontWeight.Bold, color = if (mine) Nuru.navy else Nuru.ink600)
        }
    }
}

// Wave 1 — the echo card: the app remembers you. One moment per day, chosen
// server-side from the member's own history; renders nothing on null.
@Composable
fun HomeEchoCard() {
    var echo by remember { mutableStateOf<HomeEcho?>(null) }
    LaunchedEffect(Unit) { echo = runCatching { Net.client.api.homeEcho().echo }.getOrNull() }
    val e = echo ?: return
    val kicker = when (e.kind) {
        "welcome_back" -> "WELCOME BACK"
        "anniversary" -> "REMEMBER THIS DAY"
        else -> "NURU REMEMBERS"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFFFF8E6))
            .border(1.dp, LitGold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✨", style = NuruType.caption)
            Spacer(Modifier.width(6.dp))
            Text(kicker, style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(e.body, style = NuruType.body, color = Nuru.ink)
        e.quote?.takeIf { it.isNotBlank() }?.let { q ->
            Spacer(Modifier.height(8.dp))
            Row {
                Box(Modifier.width(3.dp).height(IntrinsicSize.Min).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Nuru.gold))
                Spacer(Modifier.width(10.dp))
                Text(
                    "“$q”",
                    style = NuruType.rowTitle.copy(fontSize = 16.sp, lineHeight = 24.sp),
                    color = Nuru.navy,
                )
            }
        }
        e.ref?.takeIf { it.isNotBlank() }?.let { r ->
            Spacer(Modifier.height(6.dp))
            Text("— $r", style = NuruType.micro, color = Nuru.eyebrow, fontWeight = FontWeight.SemiBold)
        }
    }
}
