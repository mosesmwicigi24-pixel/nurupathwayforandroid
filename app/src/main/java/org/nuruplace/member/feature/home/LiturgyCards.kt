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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import org.nuruplace.member.ui.components.voiceClock
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType

private val LitGold = Color(0xFFE8CA6C)
// One-hierarchy palette (iOS build 79 parity): the "selah" rule + closing
// scripture reference read in this gold; the charge/companion-verse whisper
// sits one shade paler so the large white line stays the only shout.
private val LitRuleGold = Color(0xFFE0B85E)
private val LitWhisper = Color(0xFFF2DDA0)



@Composable
fun LiturgyCard(canManageRecordings: Boolean = false) {
    var lit by remember { mutableStateOf<HomeLiturgy?>(null) }
    LaunchedEffect(Unit) { lit = runCatching { Net.client.api.homeLiturgy() }.getOrNull() }

    // Spoken liturgy (LiturgyVoice.kt) — bound as soon as the card mounts so
    // the engine is already warm by the time a member reads the line and
    // decides to tap Listen. Registered unconditionally, BEFORE the `lit`
    // null-return below, so these hooks run on every composition regardless
    // of whether the fetch has resolved yet.
    val context = LocalContext.current
    LaunchedEffect(Unit) { LiturgyVoice.bind(context) }
    val voiceState by LiturgyVoice.state.collectAsState()
    // Nuru Live's own "what's watchable right now" state (LiveDiscoveryCenter,
    // fed by Home's own /live/now poll) — re-checking controlOffered whenever
    // it changes is what makes the "decline while broadcasting live" guard
    // (LiturgyVoice.isChurchBroadcastingLive) actually take effect promptly
    // while the member is already sitting on Home, not just on the next
    // ON_RESUME (see LiturgyVoice.kt's controlOffered doc).
    val discoveryStreams by org.nuruplace.member.feature.live.LiveDiscoveryCenter.streams.collectAsState()
    var offerVoice by remember { mutableStateOf(false) }
    LaunchedEffect(voiceState.status, discoveryStreams) { offerVoice = LiturgyVoice.controlOffered(context) }
    // Re-check on resume: the realistic way TalkBack's on/off state changes
    // mid-session is leaving the app for Settings and coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { offerVoice = LiturgyVoice.controlOffered(context) }
    // Stop speaking (not a full teardown) the moment this screen leaves the
    // foreground — backgrounding the app or navigating to another
    // destination both fire ON_STOP for this composable's lifecycle owner
    // (same idiom as ChatThreadScreen.kt's pastoral re-lock).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { LiturgyVoice.stop() }
    // Full engine teardown once the card actually leaves the composition —
    // THIS is what prevents leaking the TextToSpeech engine.
    DisposableEffect(Unit) { onDispose { LiturgyVoice.release() } }

    // Admin-only entry point into "his voice" per-band recorder (see
    // LiturgyRecorderSheet.kt). Role gate lives at the call site
    // (HomeScreen.kt threads `me?.profile?.role == Admin/SuperAdmin` in as
    // [canManageRecordings]) — this composable just decides whether to show
    // the door and hold the sheet's open/closed state.
    var showRecorder by remember { mutableStateOf(false) }

    val l = lit ?: return
    // Two genuinely separate controls (design correction 2026-08-12 — see
    // LiturgySpeech.kt's LiturgyPlaybackSource doc): Listen always reads
    // TODAY'S text via synthesis; the pastor's-own-word control, offered
    // only when a playable recording exists for THIS band, plays his
    // standing recording — never presented as a reading of today's line.
    // Each control hides its SIBLING while it is the one actively speaking
    // (voiceState.activeSource), rather than trying to "switch" mid-tap —
    // simpler, and matches this file's existing "hide, don't disable"
    // posture for a control that can't do anything useful right now.
    val recordedUrl = recordedLiturgyUrlIfPlayable(l.recordedAudioUrl)
    val listenSpeaking = voiceState.status == LiturgyVoiceStatus.SPEAKING &&
        voiceState.activeSource == LiturgyPlaybackSource.SYNTHESIS
    val recordedSpeaking = voiceState.status == LiturgyVoiceStatus.SPEAKING &&
        voiceState.activeSource == LiturgyPlaybackSource.RECORDED
    val listenOffered = offerVoice &&
        (voiceState.activeSource == null || voiceState.activeSource == LiturgyPlaybackSource.SYNTHESIS)
    val recordedOffered = offerVoice && recordedUrl != null &&
        (voiceState.activeSource == null || voiceState.activeSource == LiturgyPlaybackSource.RECORDED)
    val onToggleListen: (() -> Unit)? = if (listenOffered) ({ LiturgyVoice.toggleListen(context, l) }) else null
    val onToggleRecorded: (() -> Unit)? =
        if (recordedOffered) ({ LiturgyVoice.toggleRecorded(context, recordedUrl!!) }) else null
    if (showRecorder) {
        LiturgyRecorderSheet(onDismiss = { showRecorder = false })
    }
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
        // A captioned photograph (owner's revision, 2026-08-24 — iOS parity):
        // the image OWNS the top of the card and the words sit BELOW it, like
        // a caption. The old tableau floated the prayer line over the photo
        // under a navy veil — and on a long day (line + charge + companion
        // verse) the words swallowed the photograph entirely. Text now grows
        // the card DOWNWARD; the photo is never hidden, whatever the server sends.
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
            Box(Modifier.fillMaxWidth().height(176.dp)) {
                AsyncImage(
                    model = art.url, contentDescription = art.alt, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // A soft top scrim so the kicker reads on any photograph.
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.45f),
                            0.55f to Color.Transparent,
                        ),
                    ),
                )
                Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                    LitKicker(
                        Modifier,
                        partEmoji, partLabel, l.isSunday, l.season, onArt = true, textShadow = textShadow,
                        speaking = listenSpeaking, onToggleVoice = onToggleListen,
                        canManageRecordings = canManageRecordings, onOpenRecorder = { showRecorder = true },
                    )
                    if (onToggleRecorded != null) {
                        Spacer(Modifier.height(8.dp))
                        RecordedWordChip(
                            speaking = recordedSpeaking, onArt = true,
                            durationSec = l.recordedAudioDurationSec, onToggle = onToggleRecorded,
                        )
                    }
                }
            }
            // The caption: ONE hierarchy — the hour's word LARGE, a gold rule
            // (the selah), then small golden lines closing on a SINGLE
            // scripture — never two large lines, never two references.
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF0F2A47), Color(0xFF0A1C33))))
                    .padding(18.dp),
            ) {
                Text(
                    l.line,
                    style = NuruType.rowTitle.copy(fontSize = 19.sp, lineHeight = 25.sp),
                    color = Color.White,
                )
                Spacer(Modifier.height(7.dp))
                Box(Modifier.width(34.dp).height(1.5.dp).background(LitRuleGold.copy(alpha = 0.8f)))
                Spacer(Modifier.height(7.dp))
                l.charge?.takeIf { it.isNotBlank() }?.let { charge ->
                    Text(
                        charge,
                        style = NuruType.rowTitle.copy(
                            fontSize = 13.5.sp, lineHeight = 18.sp,
                            fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal,
                        ),
                        color = LitWhisper,
                    )
                    Spacer(Modifier.height(7.dp))
                }
                val vl = l.verseLine
                if (vl != null && vl.text.isNotBlank()) {
                    Text(
                        "\u201c${vl.text}\u201d",
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
                            style = NuruType.micro.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, shadow = textShadow),
                            color = LitRuleGold,
                        )
                    }
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
            LitKicker(
                Modifier, partEmoji, partLabel, l.isSunday, l.season, onArt = false, textShadow = textShadow,
                speaking = listenSpeaking, onToggleVoice = onToggleListen,
                canManageRecordings = canManageRecordings, onOpenRecorder = { showRecorder = true },
            )
            if (onToggleRecorded != null) {
                Spacer(Modifier.height(8.dp))
                RecordedWordChip(
                    speaking = recordedSpeaking, onArt = false,
                    durationSec = l.recordedAudioDurationSec, onToggle = onToggleRecorded,
                )
            }
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

/** The hour + Nuru Pathway brand row — shared by the tableau and the classic
 *  card. [onToggleVoice] null means the spoken-liturgy control isn't offered
 *  right now (engine still warming up, unusable on this device, or a
 *  spoken-feedback accessibility service is already active — see
 *  LiturgyVoice.controlOffered) and nothing is rendered for it: hiding the
 *  control beats showing a button that would do nothing. */
@Composable
private fun LitKicker(
    modifier: Modifier = Modifier,
    partEmoji: String,
    partLabel: String,
    isSunday: Boolean,
    season: String,
    onArt: Boolean,
    textShadow: Shadow,
    speaking: Boolean = false,
    onToggleVoice: (() -> Unit)? = null,
    canManageRecordings: Boolean = false,
    onOpenRecorder: (() -> Unit)? = null,
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
        // "His voice" recorder door — Admin/SuperAdmin only (see LiturgyCard's
        // canManageRecordings/HomeScreen.kt's role check), small and
        // unobtrusive next to the member-facing Listen control. Never shown
        // to anyone else; opens LiturgyRecorderSheet's plain per-band list.
        if (canManageRecordings && onOpenRecorder != null) {
            LiturgyRecorderEntryButton(onArt = onArt, onOpen = onOpenRecorder)
            Spacer(Modifier.width(6.dp))
        }
        if (onToggleVoice != null) {
            LiturgyListenButton(speaking = speaking, onArt = onArt, onToggle = onToggleVoice)
        }
    }
}

/** The admin-only door into [LiturgyRecorderSheet] — same compact icon-only
 *  shape as [LiturgyListenButton] so it reads as a sibling control, not a
 *  louder call to action; contentDescription carries the real label since
 *  the glyph alone (a mic) would otherwise read ambiguously next to Listen's
 *  own speaker icon. */
@Composable
private fun LiturgyRecorderEntryButton(onArt: Boolean, onOpen: () -> Unit) {
    val view = LocalView.current
    val bg = if (onArt) Color.White.copy(alpha = 0.18f) else LitGold.copy(alpha = 0.18f)
    val tint = if (onArt) Color.White else LitRuleGold
    Box(
        Modifier
            .size(26.dp)
            .pressScale()
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { Haptics.tap(view); onOpen() }
            .semantics { contentDescription = "Record the liturgy in your own voice" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** Tap to hear the hour's line + Scripture read aloud (LiturgyVoice.kt, an
 *  on-device TextToSpeech voice — never auto-plays). Icon-only, matching the
 *  kicker's compact row; contentDescription carries the actual label so
 *  TalkBack announces a clear action ("Listen…" / "Stop…") rather than a
 *  bare icon name. */
@Composable
private fun LiturgyListenButton(speaking: Boolean, onArt: Boolean, onToggle: () -> Unit) {
    val view = LocalView.current
    val bg = if (onArt) Color.White.copy(alpha = 0.18f) else LitGold.copy(alpha = 0.18f)
    val tint = if (onArt) Color.White else LitRuleGold
    Box(
        Modifier
            .size(26.dp)
            .pressScale()
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable { Haptics.tap(view); onToggle() }
            .semantics {
                contentDescription = if (speaking) {
                    "Stop listening to the hour's liturgy"
                } else {
                    "Listen to the hour's liturgy"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (speaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** The pastor's own recording for THIS band — a control genuinely SEPARATE
 *  from Listen (design correction 2026-08-12, see LiturgySpeech.kt's
 *  [LiturgyPlaybackSource] doc for the full "why"): the recording is a
 *  STANDING per-band asset, not a reading of today's specific liturgy text,
 *  so it carries its own label ("A word for this hour") and its own
 *  duration rather than living behind the Listen icon. Rendered ONLY when
 *  [onToggle] is non-null at the call site (LiturgyCard already resolves
 *  that from `recordedLiturgyUrlIfPlayable` — absent/malformed both mean
 *  "omit entirely," never a disabled/empty state, since mixed per-band
 *  coverage is the permanent normal case). Deliberately carries no member
 *  name or other identity — the backend never sends who recorded it, and
 *  nothing member-facing here should imply it does. */
@Composable
private fun RecordedWordChip(speaking: Boolean, onArt: Boolean, durationSec: Int?, onToggle: () -> Unit) {
    val view = LocalView.current
    val bg = if (onArt) Color.White.copy(alpha = 0.16f) else LitGold.copy(alpha = 0.14f)
    val tint = if (onArt) Color.White else LitRuleGold
    val label = if (speaking) {
        "Playing his word for this hour…"
    } else {
        "A word for this hour" + (durationSec?.takeIf { it > 0 }?.let { " · ${voiceClock(it)}" } ?: "")
    }
    Row(
        Modifier
            .pressScale()
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .clickable { Haptics.tap(view); onToggle() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics {
                contentDescription = if (speaking) {
                    "Stop his own word for this hour"
                } else {
                    "Hear his own word for this hour" + (durationSec?.takeIf { it > 0 }?.let { ", ${voiceClock(it)}" } ?: "")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (speaking) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(label, style = NuruType.micro.copy(fontWeight = FontWeight.Bold), color = tint, maxLines = 1)
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
