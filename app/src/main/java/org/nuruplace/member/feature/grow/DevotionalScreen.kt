// Devotional — today's word + a reflection the member can save (which also ticks
// the Reflection rhythm, server-side). Port of the iOS DevotionalView — cream
// header, verse rail, reflection field with word/char gate, footer actions row,
// and the encouragement strip.
package org.nuruplace.member.feature.grow

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.Devotional
import org.nuruplace.member.data.net.DevotionalReflectionBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.GrowCreamHeader
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.VerseQuoteCard
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.components.gSerif
import org.nuruplace.member.ui.theme.scaledLineHeight
import org.nuruplace.member.ui.icons.Lucide

@Composable
fun DevotionalScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.devotional() }) { d: Devotional, _ ->
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        // The reading theme (data/ReadingTheme.kt): the day page is the Grow
        // cream it always was; night is the readers' dark sepia page.
        val pal = DevotionalPalette(ReaderMode.night)
        // Read aloud (feature/listen): title, verse, body, then the question.
        val listenWords = remember(d) { org.nuruplace.member.feature.listen.ListenScript.paragraphs(devotionalListenPieces(d)) }
        val listen = org.nuruplace.member.feature.listen.rememberListen(listenWords)

        var reflection by remember(d.devotionalId) { mutableStateOf(d.myReflection ?: "") }
        var saved by remember(d.devotionalId) { mutableStateOf(d.myReflection != null) }
        var busy by remember(d.devotionalId) { mutableStateOf(false) }
        var error by remember(d.devotionalId) { mutableStateOf<String?>(null) }
        // A quiet like (owner, 2026-10-05): this phone only, never "Saved" —
        // nothing is saved anywhere until saving exists as its own feature.
        var liked by remember(d.devotionalId) { mutableStateOf(false) }

        fun submit() {
            if (busy) return
            busy = true
            error = null
            scope.launch {
                try {
                    Net.client.api.saveDevotionalReflection(
                        DevotionalReflectionBody(d.devotionalId, reflection.trim()),
                    )
                    saved = true
                } catch (e: Exception) {
                    error = ApiException.message(e)
                } finally {
                    busy = false
                }
            }
        }

        fun shareDevotional() {
            try {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, d.title)
                }
                context.startActivity(Intent.createChooser(send, null))
            } catch (_: Exception) {
                // Sharing is best-effort; ignore if no handler is available.
            }
        }

        Box(Modifier.fillMaxSize().background(pal.paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            GrowCreamHeader(radius = 30.dp, night = pal.night) {
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DevotionalSquare(pal, onClick = onBack) {
                            Icon(
                                Lucide.ArrowLeft,
                                contentDescription = "Back",
                                tint = pal.navy,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (listen.available) {
                            DevotionalSquare(pal, onClick = { listen.toggle() }) {
                                org.nuruplace.member.feature.listen.ListenIcon(listen, tint = pal.navy)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        DevotionalSquare(pal, onClick = { ReaderMode.toggle() }) {
                            Icon(
                                if (pal.night) Lucide.Sun else Lucide.Moon,
                                contentDescription = if (pal.night) "Day page" else "Night page",
                                tint = pal.navy,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(
                        Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "DAY ${d.dayNumber} · DEVOTIONAL",
                            style = gInter(11, FontWeight.Bold, 1.6f),
                            color = pal.eyebrow,
                        )
                        Text(d.title, style = gSerif(28, FontWeight.SemiBold), color = pal.navy)
                        d.series?.let {
                            Text(it, style = gInter(13), color = pal.ink600)
                        }
                    }
                }
            }

            // ── Content ─────────────────────────────────────────────────────────
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Verse + body card
                if (d.scriptureText != null || d.body.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(pal.white)
                            .border(1.dp, pal.border, RoundedCornerShape(24.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        d.scriptureText?.let { verse ->
                            VerseQuoteCard(verse = verse, reference = d.scriptureRef ?: "", night = pal.night)
                        }
                        if (d.body.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                d.body.split("\n\n").forEach { para ->
                                    Text(
                                        para,
                                        style = gInter(16).copy(lineHeight = scaledLineHeight(24)),
                                        color = pal.ink,
                                    )
                                }
                            }
                        }
                    }
                }

                // Reflection card
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(pal.white)
                        .border(1.dp, pal.border, RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "REFLECTION",
                            style = gInter(11, FontWeight.Bold, 1.4f),
                            color = pal.overline,
                        )
                        Spacer(Modifier.weight(1f))
                        if (saved) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Lucide.Check,
                                    contentDescription = null,
                                    tint = pal.gold,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    "Submitted",
                                    style = gInter(11, FontWeight.Bold),
                                    color = pal.gold,
                                )
                            }
                        }
                    }

                    d.reflectionPrompt?.let {
                        Text(
                            it,
                            style = gInter(13).copy(lineHeight = scaledLineHeight(16)),
                            color = pal.ink600,
                        )
                    }

                    // Field — the whole box is the field (its decoration):
                    // before, only its first line took a tap.
                    BasicTextField(
                        value = reflection,
                        onValueChange = { reflection = it; saved = false },
                        textStyle = gInter(14).copy(color = pal.ink),
                        cursorBrush = SolidColor(pal.gold),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { field ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(pal.surface)
                                    .border(1.dp, pal.border, RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                            ) {
                                if (reflection.isBlank()) {
                                    Text(
                                        "Write your reflection…",
                                        style = gInter(14),
                                        color = pal.ink400,
                                    )
                                }
                                field()
                            }
                        },
                    )

                    // Hint
                    val trimmed = reflection.trim()
                    val words = if (trimmed.isBlank()) 0 else trimmed.split(Regex("\\s+")).size
                    Text(
                        when {
                            saved -> "$words words · saved to your journal"
                            trimmed.length >= 20 -> "$words words · ${trimmed.length} chars"
                            else -> "${20 - trimmed.length} more characters before you can submit"
                        },
                        style = gInter(11),
                        color = if (saved) pal.successText else pal.ink600,
                    )

                    // Submit
                    val canSubmit = reflection.trim().length >= 20 && !busy
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            // The page's one primary (§8.1 rule 4): gold, navy words.
                            .background(if (canSubmit) pal.gold else pal.navy.copy(alpha = 0.18f))
                            .clickable(enabled = canSubmit) { submit() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                color = pal.onGold,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                if (saved) "Update reflection" else "Submit reflection",
                                style = gInter(14, FontWeight.SemiBold),
                                color = if (canSubmit) pal.onGold else pal.navy.copy(alpha = 0.55f),
                            )
                        }
                    }

                    error?.let {
                        Text(it, style = gInter(11), color = Color(0xFFD4183D))
                    }
                }

                // Footer actions row
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(pal.white)
                        .border(1.dp, pal.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .toggleable(value = liked, role = Role.Switch) { liked = it }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            if (liked) Lucide.Heart else Lucide.Heart,
                            contentDescription = null,
                            tint = if (liked) pal.gold else pal.navy,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Like",
                            style = gInter(11, FontWeight.Medium),
                            color = if (liked) pal.gold else pal.navy,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { shareDevotional() }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            Lucide.Share2,
                            contentDescription = null,
                            tint = pal.navy,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Share",
                            style = gInter(11, FontWeight.Medium),
                            color = pal.navy,
                        )
                    }
                }

                // Encouragement strip
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(pal.gold.copy(alpha = 0.08f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Lucide.Heart,
                        contentDescription = null,
                        tint = pal.gold,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Every faithful day adds up. There's no rush — just presence.",
                        style = gInter(13).copy(lineHeight = scaledLineHeight(18)),
                        color = pal.navy,
                    )
                }
            }
        }
        // The Read aloud player floats over the page's foot (the content
        // leaves 96dp clear there), clear of the gesture bar.
        org.nuruplace.member.feature.listen.ListenBar(
            listen,
            pal.listenColors,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp),
        )
        }
    }
}

/** The devotional's colours on the day page (the Grow cream, exactly as before)
 *  and the night page (the readers' sepia — ReaderPalette). `navy` is the
 *  page's TEXT navy, so on the night page it is the light ink; words that sit
 *  on gold use [onGold], dark on both. */
private class DevotionalPalette(val night: Boolean) {
    private val r = ReaderPalette(night = true)
    val paper = if (night) r.bg else GrowPal.paper
    val white = if (night) r.card else GrowPal.white
    val surface = if (night) r.highlight else GrowPal.surface
    val border = if (night) r.border else GrowPal.border
    val navy = if (night) r.ink else GrowPal.navy
    val onGold = GrowPal.navy
    val gold = if (night) r.gold else GrowPal.gold
    val eyebrow = if (night) r.goldDeep else GrowPal.eyebrow
    val overline = if (night) r.goldDeep else GrowPal.overline
    val ink = if (night) r.ink else GrowPal.ink
    val ink600 = if (night) r.inkDim else GrowPal.ink600
    val ink400 = if (night) r.inkDim else GrowPal.ink400
    val successText = if (night) Color(0xFF86EFAC) else GrowPal.successText
    val listenColors get() = org.nuruplace.member.feature.listen.ListenColors(
        bar = white, ink = navy, inkDim = ink600, accent = gold, onAccent = onGold, border = border,
    )
}

/** The header's square buttons (back · listen · night page). */
@Composable
private fun DevotionalSquare(pal: DevotionalPalette, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(pal.white)
            .border(1.dp, pal.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
