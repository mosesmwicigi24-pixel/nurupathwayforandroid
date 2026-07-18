// Grow hub — the daily-rhythm & Word home: entry cards into Devotional, Memory
// verses, Reading plans, Prayer journal and Verse library. Port of the iOS GrowView.
package org.nuruplace.member.feature.grow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

private data class GrowEntry(val route: String, val title: String, val subtitle: String, val icon: ImageVector)

@Composable
fun GrowHubScreen(onOpen: (String) -> Unit) {
    val entries = listOf(
        GrowEntry("devotional", "Today's devotional", "A word for today + reflect", Icons.AutoMirrored.Filled.List),
        GrowEntry("memory-verses", "Memory verses", "Hide His Word in your heart", Icons.Filled.Star),
        GrowEntry("plans", "Reading plans", "Journey through Scripture", Icons.AutoMirrored.Filled.List),
        GrowEntry("prayer-room", "My Prayer Room", "Your private prayers", Icons.Filled.Favorite),
        GrowEntry("verses", "Verse library", "Verses you've saved", Icons.Filled.Star),
    )
    Column(
        Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier.background(Nuru.heroGradient).padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        ) {
            Kicker("Grow")
            Spacer(Modifier.size(Spacing.sm))
            Text("Daily rhythm & the Word", style = NuruType.display, color = Nuru.onNavy)
        }
        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            entries.forEach { e ->
                NuruCard(modifier = Modifier.clickable { onOpen(e.route) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(Radii.control)).background(Nuru.goldTint),
                            contentAlignment = Alignment.Center,
                        ) { Icon(e.icon, null, tint = Nuru.goldLo, modifier = Modifier.size(22.dp)) }
                        Spacer(Modifier.size(Spacing.base))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = NuruType.cardTitle, color = Nuru.ink)
                            Text(e.subtitle, style = NuruType.caption, color = Nuru.ink600)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Nuru.ink300, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.size(Spacing.tabBarSpace))
        }
    }
}
