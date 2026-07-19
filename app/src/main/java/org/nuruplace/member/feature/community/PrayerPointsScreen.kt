// AI Prayer Points (Prayer Room tab 4, docs: prayer-room-arc). Two surfaces
// over the intelligence layer's PrayerAiService, both consent-gated exactly
// like Profile's "Nuru Intelligence" switch (§1.1 — AI never gates/scores/
// touches money, it only ever writes words the member reviews):
//   • Assist — a Gemini-in-Gmail-style composer: seed a few points, get a
//     short draft prayer in the member's own voice (POST me/prayer/assist).
//   • Gather — the corpus generator: distills prayer points from the member's
//     OWN Selah thoughts + private prayers + published prayer-wall posts
//     (POST me/prayer/points). The member always edits/keeps/prays these
//     themselves — nothing here posts or sends anything. Port of iOS
//     PrayerPointsView.
package org.nuruplace.member.feature.community

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.AiConsentBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PrayerAssistBody
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

private val AiOrb = Brush.radialGradient(listOf(Color(0xFFC4B5FD), Color(0xFF7C3AED), Color(0xFF2A1259)))

@Composable
fun PrayerPointsScreen() {
    val view = LocalView.current
    var optedOut by remember { mutableStateOf<Boolean?>(null) }
    var consentBusy by remember { mutableStateOf(false) }
    var consentFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        optedOut = runCatching { Net.client.api.aiConsent().optOut }.getOrDefault(false)
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Nuru.paper),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text("Let Nuru help you pray", style = NuruType.title, color = Nuru.navy) }
        when (optedOut) {
            null -> item {
                Box(Modifier.fillMaxWidth().padding(top = Spacing.lg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Nuru.gold)
                }
            }
            true -> item {
                ConsentGateCard(busy = consentBusy, failed = consentFailed) {
                    scope.launch {
                        consentBusy = true; consentFailed = false
                        val ok = runCatching { Net.client.api.setAiConsent(AiConsentBody(optOut = false)) }.isSuccess
                        consentBusy = false
                        if (ok) { Haptics.confirm(view); optedOut = false } else { Haptics.reject(view); consentFailed = true }
                    }
                }
            }
            false -> {
                item { AssistComposerCard() }
                item { GatherPointsCard() }
            }
        }
    }
}

@Composable
private fun ConsentGateCard(busy: Boolean, failed: Boolean, enable: () -> Unit) {
    val view = LocalView.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(18.dp)).padding(Spacing.base),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(AiOrb), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("NURU INTELLIGENCE", style = NuruType.sectionLabel, color = Nuru.eyebrow)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text("Turn on AI personalization to use the prayer assistant.", style = NuruType.cardCta, color = Nuru.navy)
        Spacer(Modifier.height(4.dp))
        Text(
            "Nuru remembers your journey to walk with you personally. Your prayer journal is never read — ever. This is the same switch as your Sunday Letter and personal companion; turning it on here turns it on everywhere, and you can turn it off again anytime in Profile.",
            style = NuruType.caption, color = Nuru.ink600,
        )
        if (failed) {
            Spacer(Modifier.height(Spacing.sm))
            Text("Couldn't save that — check your connection and try again.", style = NuruType.caption, color = Nuru.danger)
        }
        Spacer(Modifier.height(Spacing.sm))
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp))
                .background(Nuru.gold)
                .clickable(enabled = !busy) { Haptics.tap(view); enable() },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
            else Text("Turn on AI personalization", style = NuruType.cardCta.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
    }
}

@Composable
private fun AssistComposerCard() {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var seed by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(18.dp)).padding(Spacing.base),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.FavoriteBorder, null, tint = Nuru.gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Draft a prayer", style = NuruType.cardTitle, color = Nuru.navy)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Jot a few seed points — Nuru drafts a short prayer in your own voice. You always edit it before you pray or keep it.",
            style = NuruType.caption, color = Nuru.ink600,
        )
        Spacer(Modifier.height(Spacing.sm))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.surface)
                .border(1.dp, Nuru.border, RoundedCornerShape(14.dp)).padding(Spacing.md),
        ) {
            if (seed.isBlank()) Text("e.g. wisdom for work, my sister's health, thankful for…", style = NuruType.body, color = Nuru.ink300)
            BasicTextField(
                value = seed, onValueChange = { seed = it },
                textStyle = NuruType.body.copy(color = Nuru.navy),
                cursorBrush = SolidColor(Nuru.gold),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(AiOrb)
                    .clickable(enabled = !busy) {
                        busy = true; failed = false; Haptics.tap(view)
                        scope.launch {
                            val res = runCatching { Net.client.api.prayerAssist(PrayerAssistBody(seed = seed.trim().ifBlank { null })).suggestion }
                            busy = false
                            res.onSuccess { draft = it }.onFailure { failed = true; Haptics.reject(view) }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp))
                    else Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (busy) "Drafting…" else "Draft with Nuru", style = NuruType.cardCta.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
            }
        }
        if (failed) {
            Text("Nuru couldn't reach the assistant just now — try again in a moment.", style = NuruType.caption, color = Nuru.danger)
        }
        if (draft.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Text("YOUR DRAFT", style = NuruType.sectionLabel, color = Nuru.eyebrow)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nuru.surface)
                    .border(1.dp, Nuru.border, RoundedCornerShape(14.dp)).padding(Spacing.md),
            ) {
                BasicTextField(
                    value = draft, onValueChange = { draft = it },
                    textStyle = NuruType.body.copy(color = Nuru.navy),
                    cursorBrush = SolidColor(Nuru.gold),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row {
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.white)
                        .border(1.dp, Nuru.border, RoundedCornerShape(999.dp))
                        .clickable { Haptics.tap(view); copyToClipboard(context, draft) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) { Text("Copy", style = NuruType.chipLabel, color = Nuru.navy) }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.clickable { Haptics.tap(view); draft = ""; seed = "" }.padding(horizontal = 6.dp, vertical = 9.dp),
                ) { Text("Discard", style = NuruType.chipLabel, color = Nuru.ink600) }
            }
        }
    }
}

@Composable
private fun GatherPointsCard() {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var points by remember { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var hasGathered by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Nuru.white)
            .border(1.dp, Nuru.border, RoundedCornerShape(18.dp)).padding(Spacing.base),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.List, null, tint = Nuru.gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Gather my prayer points", style = NuruType.cardTitle, color = Nuru.navy)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Nuru reads across your own Selah thoughts, private prayers, and things you've shared to the wall — and distills what to pray through today.",
            style = NuruType.caption, color = Nuru.ink600,
        )
        Spacer(Modifier.height(Spacing.sm))
        Box(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Nuru.gold)
                .clickable(enabled = !busy) {
                    busy = true; failed = false; Haptics.tap(view)
                    scope.launch {
                        val res = runCatching { Net.client.api.prayerPoints().points }
                        busy = false
                        hasGathered = true
                        res.onSuccess { points = it }.onFailure { failed = true; Haptics.reject(view) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) CircularProgressIndicator(color = Nuru.navy, modifier = Modifier.size(18.dp))
            else Text("Gather my prayer points", style = NuruType.cardCta.copy(fontWeight = FontWeight.Bold), color = Nuru.navy)
        }
        if (failed) {
            Spacer(Modifier.height(Spacing.sm))
            Text("Couldn't gather your points just now — try again in a moment.", style = NuruType.caption, color = Nuru.danger)
        } else if (hasGathered && points.isEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            Text("Nothing to gather yet — write a Selah thought or a prayer first, and come back.", style = NuruType.caption, color = Nuru.ink600)
        } else if (points.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            points.forEachIndexed { idx, p ->
                PrayerPointRow(idx, p, onEdit = { updated -> points = points.toMutableList().also { it[idx] = updated } }, onRemove = {
                    points = points.toMutableList().also { it.removeAt(idx) }
                })
            }
            Spacer(Modifier.height(Spacing.sm))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Nuru.white)
                    .border(1.dp, Nuru.border, RoundedCornerShape(999.dp))
                    .clickable {
                        Haptics.tap(view)
                        copyToClipboard(context, points.mapIndexed { i, p -> "${i + 1}. $p" }.joinToString("\n"))
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) { Text("Copy all", style = NuruType.chipLabel, color = Nuru.navy) }
        }
    }
}

@Composable
private fun PrayerPointRow(index: Int, text: String, onEdit: (String) -> Unit, onRemove: () -> Unit) {
    val view = LocalView.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text("${index + 1}", style = NuruType.label, color = Nuru.gold, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
        BasicTextField(
            value = text, onValueChange = onEdit,
            textStyle = NuruType.body.copy(color = Nuru.navy),
            cursorBrush = SolidColor(Nuru.gold),
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.size(22.dp).clip(CircleShape).clickable { Haptics.tap(view); onRemove() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, null, tint = Nuru.ink400, modifier = Modifier.size(11.dp)) }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Prayer", text))
}
