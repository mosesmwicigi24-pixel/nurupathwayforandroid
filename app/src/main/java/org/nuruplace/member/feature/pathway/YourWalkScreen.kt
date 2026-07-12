// Wave 3 — Your Walk + footprints. Port of iOS YourWalkView.swift.
//   • YourWalkScreen — the member's whole journey on one gold thread:
//     began, modules, reflections (serif excerpts), levels, certificates,
//     verses, plans, badges. Real rows, newest first, no AI.
//   • FootprintsStrip — cell-mates who already completed a module, shown at
//     the top of the lesson; renders nothing when the trail is fresh.
package org.nuruplace.member.feature.pathway

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nuruplace.member.data.net.FootprintsRes
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.WalkEvent
import org.nuruplace.member.feature.community.Avatar
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType

private val WkGold = Color(0xFFC89B3C)
private val WkNavy = Color(0xFF0B1F33)
private val WkBg = Color(0xFFFAF7F0)

@Composable
fun YourWalkScreen(onBack: () -> Unit) {
    var events by remember { mutableStateOf<List<WalkEvent>?>(null) }
    LaunchedEffect(Unit) {
        events = runCatching { Net.client.api.myWalk().data }.getOrDefault(emptyList())
    }

    Column(Modifier.fillMaxSize().background(WkBg).verticalScroll(rememberScrollState())) {
        // Navy hero
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0F2A47), Color(0xFF0A1C33))))
                .padding(horizontal = 20.dp)
                .padding(top = 56.dp, bottom = 22.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("YOUR WALK", style = NuruType.micro, color = WkGold, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
            Text("Look how far He has brought you", style = NuruType.title, color = Color.White)
            events?.takeIf { it.isNotEmpty() }?.let {
                Text("${it.size} moments, all real", style = NuruType.caption, color = Color.White.copy(alpha = 0.75f))
            }
        }

        when {
            events == null -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WkGold)
            }
            events!!.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(top = 80.dp, start = 40.dp, end = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = WkGold, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your walk begins with the next lesson you open.",
                    style = NuruType.body, color = Nuru.ink,
                )
            }
            else -> Column(Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)) {
                events!!.forEachIndexed { i, e ->
                    WalkNode(e, isFirst = i == 0, isLast = i == events!!.lastIndex)
                }
                Spacer(Modifier.height(90.dp))
            }
        }
    }
}

@Composable
private fun WalkNode(e: WalkEvent, isFirst: Boolean, isLast: Boolean) {
    val glyph: ImageVector = when (e.kind) {
        "began" -> Icons.Filled.Flag
        "module" -> Icons.AutoMirrored.Filled.MenuBook
        "reflection" -> Icons.Filled.Edit
        "level" -> Icons.Filled.School
        "certificate" -> Icons.Filled.Verified
        "verse" -> Icons.Filled.FormatQuote
        "plan" -> Icons.Filled.Bookmark
        else -> Icons.Filled.EmojiEvents
    }
    val milestone = e.kind in setOf("level", "certificate", "began")

    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(Modifier.width(30.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(2.dp).height(14.dp).background(if (isFirst) Color.Transparent else WkGold.copy(alpha = 0.35f)))
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(if (milestone) WkGold else Color.White)
                    .border(1.dp, if (milestone) Color.Transparent else WkGold.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(glyph, contentDescription = null, tint = if (milestone) WkNavy else Nuru.eyebrow, modifier = Modifier.size(15.dp))
            }
            if (!isLast) {
                Box(Modifier.width(2.dp).weight(1f).background(WkGold.copy(alpha = 0.35f)))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = 22.dp)) {
            Text(e.dateLine, style = NuruType.micro, color = Nuru.ink.copy(alpha = 0.45f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Text(e.title, style = NuruType.rowTitle.copy(fontSize = 15.sp), color = WkNavy, fontWeight = FontWeight.SemiBold)
            e.detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = NuruType.caption, color = Nuru.ink.copy(alpha = 0.65f))
            }
            e.quote?.takeIf { it.isNotBlank() }?.let { q ->
                Spacer(Modifier.height(2.dp))
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(2.5.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(WkGold.copy(alpha = 0.7f)))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "“$q”",
                        style = NuruType.rowTitle.copy(fontSize = 14.sp, lineHeight = 20.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal),
                        color = Nuru.navy,
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Footprints strip ───────────────────────────

@Composable
fun FootprintsStrip(moduleId: String) {
    var res by remember { mutableStateOf<FootprintsRes?>(null) }
    LaunchedEffect(moduleId) {
        res = runCatching { Net.client.api.moduleFootprints(moduleId) }.getOrNull()
    }
    val r = res ?: return
    if (r.count <= 0 || r.footprints.isEmpty()) return
    val names = r.footprints.map { it.firstName }
    val shown = when (names.size) {
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }
    val others = r.count - names.size
    val line = if (others > 0) {
        "$shown and $others other${if (others == 1) "" else "s"} walked here before you."
    } else "$shown walked here before you."

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.7f))
            .border(1.dp, WkGold.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row {
            r.footprints.take(4).forEachIndexed { i, f ->
                Box(Modifier.offset(x = (-8 * i).dp).border(1.5.dp, Color.White, CircleShape)) {
                    Avatar(name = f.firstName, url = f.avatarUrl, size = 26.dp)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(line, style = NuruType.caption, color = Nuru.ink.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
    }
}
