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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
// One-hierarchy palette (iOS build 79 parity): the "selah" rule + closing
// scripture reference read in this gold; the charge/companion-verse whisper
// sits one shade paler so the large white line stays the only shout.
private val LitRuleGold = Color(0xFFE0B85E)
private val LitWhisper = Color(0xFFF2DDA0)

/**
 * The photograph's height is now FIXED.
 *
 * It used to grow — base art height plus room for the charge line plus the
 * companion verse — because all three sat on the image and the stack had to be
 * given somewhere to go. That model could only ever hold, though, if the hour's
 * own line were short, and it is prose: a long morning reading pushed the block
 * up through the middle of the photograph until the type was "all over the
 * card" and the picture had become a texture behind a wall of words.
 *
 * The card is split instead (see LiturgyCard), so nothing on the art varies by
 * more than a line or two and the frame can simply stay still. A constant also
 * means every liturgy card in the feed is the same height, hour to hour, which
 * the growing model never managed.
 */
private val TableauArtHeight = 236.dp

/** The scripture is bottom-anchored, so capping it here is what keeps it inside
 *  the lower third of the photograph rather than climbing toward the kicker. */
private const val ScriptureMaxLines = 3

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
        // ── Two surfaces, one card — the verse-of-the-day anatomy ────────────
        //
        // Everything used to sit on the photograph: the hour's prose, the
        // charge, the companion verse, all stacked upward from the bottom edge.
        // With a real morning reading that stack filled the frame, and the
        // picture stopped being a picture.
        //
        // The fix is not to shuffle the stack but to INVERT what lands where.
        // The verse-of-the-day card has never had this problem, and the reason
        // is a rule worth naming: **the photograph carries the short thing, the
        // cream panel carries the long thing.** A verse is one or two lines by
        // nature, so it can be laid over art and trusted to stay put; prose
        // cannot, and belongs on a surface built for reading.
        //
        // So the scripture — the short thing — comes UP onto the art and rests
        // in its lower third, and the hour's prose goes DOWN onto cream inside
        // the same card, where no scrim is fighting it. The reading order the
        // card always had (a word, a rest, a whisper) is untouched; only the
        // surface under each part changes.
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(Nuru.verseBg)
                .border(1.dp, Nuru.gold.copy(alpha = 0.25f), shape),
        ) {
            // ── The hour's photograph ────────────────────────────────────────
            Box(Modifier.fillMaxWidth().height(TableauArtHeight)) {
                AsyncImage(
                    model = art.url, contentDescription = art.alt, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(Modifier.matchParentSize().background(DeepNavyBlockBrush))
                LitKicker(
                    Modifier.align(Alignment.TopStart).padding(18.dp),
                    partEmoji, partLabel, l.isSunday, l.season, onArt = true, textShadow = textShadow,
                )

                // The lower third: the scripture, and nothing else competing
                // with it. Bottom-anchored so it sits where the veil is
                // deepest, capped so it can never climb toward the kicker, and
                // stepped down a size when the verse is a long one — the same
                // three defences the verse tableau uses.
                val vl = l.verseLine?.takeIf { it.text.isNotBlank() }
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    if (vl != null) {
                        Text(
                            "“${vl.text}”",
                            style = NuruType.rowTitle.copy(
                                fontSize = if (vl.text.length > 150) 15.sp else 17.5.sp,
                                lineHeight = if (vl.text.length > 150) 21.sp else 24.sp,
                                shadow = textShadow,
                            ),
                            color = Color.White,
                            maxLines = ScriptureMaxLines,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            vl.reference.uppercase(),
                            style = NuruType.micro.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, shadow = textShadow),
                            color = LitRuleGold,
                        )
                    } else {
                        // No companion verse on the wire: the reference alone
                        // still closes the image, and the prose below is
                        // unaffected. The art is never left carrying prose.
                        l.scriptureRef?.let { ref ->
                            Text(
                                ref.uppercase(),
                                style = NuruType.micro.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, shadow = textShadow),
                                color = LitRuleGold,
                            )
                        }
                    }
                }
            }

            // ── The cream panel, inside the same card ────────────────────────
            // The hour's word, at full length and full contrast. This is the
            // part a member actually sits with, so it gets ink on cream rather
            // than white on a photograph.
            Column(Modifier.padding(18.dp)) {
                Text(
                    l.line,
                    style = NuruType.rowTitle.copy(fontSize = 17.sp, lineHeight = 25.sp),
                    color = Nuru.navy,
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(34.dp).height(1.5.dp).background(Nuru.gold.copy(alpha = 0.7f)))
                l.charge?.takeIf { it.isNotBlank() }?.let { charge ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        charge,
                        style = NuruType.rowTitle.copy(
                            fontSize = 13.5.sp, lineHeight = 19.sp,
                            fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal,
                        ),
                        color = Nuru.metaGray,
                    )
                }
            }
        }
    } else {
        // Offline / older backend: the classic navy card, content-sized. Same
        // one-hierarchy tree as the tableau branch, no shadow (no art behind it).
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0F2A47), Color(0xFF0A1C33))))
                .padding(18.dp),
        ) {
            LitKicker(Modifier, partEmoji, partLabel, l.isSunday, l.season, onArt = false, textShadow = textShadow)
            Spacer(Modifier.height(10.dp))
            Text(l.line, style = NuruType.rowTitle.copy(fontSize = 19.sp, lineHeight = 25.sp), color = Color.White)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(34.dp).height(1.5.dp).background(LitRuleGold.copy(alpha = 0.8f)))
            Spacer(Modifier.height(8.dp))
            l.charge?.takeIf { it.isNotBlank() }?.let { charge ->
                Text(
                    charge,
                    style = NuruType.rowTitle.copy(
                        fontSize = 13.5.sp, lineHeight = 18.sp,
                        fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal,
                    ),
                    color = LitWhisper,
                )
                Spacer(Modifier.height(8.dp))
            }
            val vl = l.verseLine
            if (vl != null && vl.text.isNotBlank()) {
                Text(
                    "“${vl.text}”",
                    style = NuruType.rowTitle.copy(
                        fontSize = 13.sp, lineHeight = 18.sp,
                        fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal,
                    ),
                    color = LitWhisper.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    vl.reference.uppercase(),
                    style = NuruType.micro.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                    color = LitRuleGold,
                )
            } else {
                l.scriptureRef?.let { ref ->
                    Text(
                        ref.uppercase(),
                        style = NuruType.micro.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
                        color = LitRuleGold,
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
