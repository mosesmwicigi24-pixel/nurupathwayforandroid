// Level detail — the module trail for a level (GET /levels/{n}/modules). Locked
// modules are dimmed + non-tappable (§1.9, server-authoritative). A finished
// level offers the Level Exam. Port of the iOS LevelDetailView.
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.LevelModule
import org.nuruplace.member.data.net.ModuleStatus
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun LevelDetailScreen(
    levelNumber: Int,
    onBack: () -> Unit,
    onOpenModule: (String) -> Unit,
    onTakeExam: (Int) -> Unit,
) {
    AsyncContent(key = levelNumber, load = { Net.client.api.levelModules(levelNumber).data }) { modules: List<LevelModule>, _ ->
        val allDone = modules.isNotEmpty() && modules.all { it.completed }
        LazyColumn(
            Modifier.fillMaxWidth().background(Nuru.paper),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.tabBarSpace),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(Nuru.heroGradient)
                        .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Nuru.onNavy)
                        }
                        Spacer(Modifier.size(Spacing.xs))
                        Kicker("Level $levelNumber")
                    }
                    Text("Modules", style = NuruType.title, color = Nuru.onNavy, modifier = Modifier.padding(start = Spacing.xs))
                }
            }
            items(modules, key = { it.moduleId }) { m ->
                ModuleRow(
                    module = m,
                    onOpen = { onOpenModule(m.moduleId) },
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
            if (allDone) {
                item {
                    Column(Modifier.padding(Spacing.screen)) {
                        Text("You've finished every module in this level.", style = NuruType.body, color = Nuru.ink600)
                        Spacer(Modifier.height(Spacing.md))
                        PrimaryButton("Take the Level $levelNumber exam", onClick = { onTakeExam(levelNumber) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleRow(module: LevelModule, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val locked = module.locked || module.status == ModuleStatus.LOCKED
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.control))
            .background(Nuru.white)
            .then(if (locked) Modifier.alpha(0.55f) else Modifier.clickable { onOpen() })
            .padding(Spacing.base),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(Radii.pill))
                .background(if (module.completed) Nuru.successBg else if (locked) Nuru.inputBg else Nuru.goldTint),
            contentAlignment = Alignment.Center,
        ) {
            when {
                module.completed -> Icon(Icons.Filled.Check, null, tint = Nuru.successText, modifier = Modifier.size(18.dp))
                locked -> Icon(Icons.Filled.Lock, null, tint = Nuru.ink400, modifier = Modifier.size(16.dp))
                else -> Text("${module.moduleSequenceNumber}", style = NuruType.rowTitle, color = Nuru.goldLo)
            }
        }
        Spacer(Modifier.size(Spacing.base))
        Column(Modifier.weight(1f)) {
            Text(module.title, style = NuruType.rowTitle, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
            module.summary?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = NuruType.caption, color = Nuru.ink600, maxLines = 2)
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                module.estimatedMinutes?.let { Text("$it min", style = NuruType.micro, color = Nuru.ink400) }
                if (module.evaluationKind.lowercase().contains("quiz")) {
                    Text("Quiz · pass ${module.quizPassMark}%", style = NuruType.micro, color = Nuru.goldLo)
                }
            }
        }
    }
}
