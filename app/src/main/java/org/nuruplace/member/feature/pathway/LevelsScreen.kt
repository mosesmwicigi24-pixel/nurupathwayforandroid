// Pathway hub — the member's level trail (GET /me/pathway). Locked levels (§1.9)
// render dimmed + non-tappable with the "Complete Level N to unlock" label; the
// server is authoritative, this only mirrors it. Port of the iOS PathwayView.
package org.nuruplace.member.feature.pathway

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun LevelsScreen(onOpenLevel: (Int) -> Unit) {
    AsyncContent(load = { Net.client.api.pathway() }) { summary: PathwaySummary ->
        LazyColumn(
            Modifier.fillMaxWidth().background(Nuru.paper),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.tabBarSpace),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(Nuru.heroGradient)
                        .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
                ) {
                    Kicker("Your journey")
                    Spacer(Modifier.height(Spacing.sm))
                    Text("Discipleship Pathway", style = NuruType.display, color = Nuru.onNavy)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Level ${summary.currentLevel} in progress", style = NuruType.body, color = Nuru.onNavyDim)
                }
            }
            items(summary.levels, key = { it.levelNumber }) { level ->
                LevelRow(
                    level = level,
                    currentLevel = summary.currentLevel,
                    onOpen = { onOpenLevel(level.levelNumber) },
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun LevelRow(level: PathwayLevel, currentLevel: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val locked = LevelGating.isLevelLocked(level.levelNumber, currentLevel, level.status)
    val done = level.status == LevelStatus.COMPLETED

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.card))
            .background(Nuru.white)
            .then(if (locked) Modifier.alpha(0.55f) else Modifier.clickable { onOpen() })
            .padding(Spacing.base),
        verticalAlignment = Alignment.Top,
    ) {
        // Level number / status disc
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(Radii.pill))
                .background(if (done) Nuru.successBg else if (locked) Nuru.inputBg else Nuru.goldTint),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Filled.Check, null, tint = Nuru.successText, modifier = Modifier.size(20.dp))
                locked -> Icon(Icons.Filled.Lock, null, tint = Nuru.ink400, modifier = Modifier.size(18.dp))
                else -> Text("${level.levelNumber}", style = NuruType.cardTitle, color = Nuru.goldLo)
            }
        }
        Spacer(Modifier.size(Spacing.base))
        Column(Modifier.weight(1f)) {
            level.theme?.let { Kicker(it) }
            Text(level.title, style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Spacing.xs))
            if (locked) {
                Text(LevelGating.lockedLevelLabel(currentLevel), style = NuruType.caption, color = Nuru.ink400)
            } else {
                Text(
                    "${level.completedModules}/${level.totalModules} modules · ${level.minutes} min",
                    style = NuruType.caption, color = Nuru.ink600,
                )
                Spacer(Modifier.height(Spacing.sm))
                ProgressBar(if (level.totalModules > 0) level.completedModules.toFloat() / level.totalModules else 0f)
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radii.pill)).background(Nuru.track)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp)
                .clip(RoundedCornerShape(Radii.pill)).background(Nuru.gold),
        )
    }
}
