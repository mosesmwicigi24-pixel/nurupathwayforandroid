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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolunteerActivism
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

@Composable
fun DevotionalScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.devotional() }) { d: Devotional, _ ->
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        var reflection by remember(d.devotionalId) { mutableStateOf(d.myReflection ?: "") }
        var saved by remember(d.devotionalId) { mutableStateOf(d.myReflection != null) }
        var busy by remember(d.devotionalId) { mutableStateOf(false) }
        var error by remember(d.devotionalId) { mutableStateOf<String?>(null) }
        var savedFav by remember(d.devotionalId) { mutableStateOf(false) }

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

        Column(
            Modifier
                .fillMaxSize()
                .background(GrowPal.paper)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            GrowCreamHeader(radius = 30.dp) {
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 24.dp),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GrowPal.white)
                            .border(1.dp, GrowPal.border, RoundedCornerShape(16.dp))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GrowPal.navy,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(
                        Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "DAY ${d.dayNumber} · DEVOTIONAL",
                            style = gInter(11, FontWeight.Bold, 1.6f),
                            color = GrowPal.eyebrow,
                        )
                        Text(d.title, style = gSerif(28, FontWeight.SemiBold), color = GrowPal.navy)
                        d.series?.let {
                            Text(it, style = gInter(13), color = GrowPal.ink600)
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
                            .background(GrowPal.white)
                            .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        d.scriptureText?.let { verse ->
                            VerseQuoteCard(verse = verse, reference = d.scriptureRef ?: "")
                        }
                        if (d.body.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                d.body.split("\n\n").forEach { para ->
                                    Text(
                                        para,
                                        style = gInter(14).copy(lineHeight = scaledLineHeight(21)),
                                        color = GrowPal.ink,
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
                        .background(GrowPal.white)
                        .border(1.dp, GrowPal.border, RoundedCornerShape(24.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "REFLECTION",
                            style = gInter(11, FontWeight.Bold, 1.4f),
                            color = GrowPal.overline,
                        )
                        Spacer(Modifier.weight(1f))
                        if (saved) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = GrowPal.gold,
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(
                                    "Submitted",
                                    style = gInter(10, FontWeight.Bold),
                                    color = GrowPal.gold,
                                )
                            }
                        }
                    }

                    d.reflectionPrompt?.let {
                        Text(
                            it,
                            style = gInter(13).copy(lineHeight = scaledLineHeight(16)),
                            color = GrowPal.ink600,
                        )
                    }

                    // Field
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GrowPal.surface)
                            .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                    ) {
                        if (reflection.isBlank()) {
                            Text(
                                "Write your reflection…",
                                style = gInter(14),
                                color = GrowPal.ink400,
                            )
                        }
                        BasicTextField(
                            value = reflection,
                            onValueChange = { reflection = it; saved = false },
                            textStyle = gInter(14).copy(color = GrowPal.ink),
                            cursorBrush = SolidColor(GrowPal.gold),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

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
                        color = if (saved) GrowPal.successText else GrowPal.ink600,
                    )

                    // Submit
                    val canSubmit = reflection.trim().length >= 20 && !busy
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (canSubmit) GrowPal.navy else GrowPal.navy.copy(alpha = 0.18f))
                            .clickable(enabled = canSubmit) { submit() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                if (saved) "Update reflection" else "Submit reflection",
                                style = gInter(14, FontWeight.SemiBold),
                                color = if (canSubmit) Color.White else GrowPal.navy.copy(alpha = 0.55f),
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
                        .background(GrowPal.white)
                        .border(1.dp, GrowPal.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { savedFav = !savedFav }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            if (savedFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (savedFav) GrowPal.gold else GrowPal.navy,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            if (savedFav) "Saved" else "Save",
                            style = gInter(10, FontWeight.Medium),
                            color = if (savedFav) GrowPal.gold else GrowPal.navy,
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
                            Icons.Filled.Share,
                            contentDescription = null,
                            tint = GrowPal.navy,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Share",
                            style = gInter(10, FontWeight.Medium),
                            color = GrowPal.navy,
                        )
                    }
                }

                // Encouragement strip
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GrowPal.gold.copy(alpha = 0.08f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.VolunteerActivism,
                        contentDescription = null,
                        tint = GrowPal.gold,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Every faithful day adds up. There's no rush — just presence.",
                        style = gInter(13).copy(lineHeight = scaledLineHeight(18)),
                        color = GrowPal.navy,
                    )
                }
            }
        }
    }
}
