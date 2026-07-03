// Resources library — books, talks, articles. Port of the iOS ResourcesLibraryView.
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ResourceRow
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.components.ScreenHeader
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun ResourcesScreen(onBack: () -> Unit) {
    AsyncContent(load = { Net.client.api.resources().data }) { rows: List<ResourceRow>, _ ->
        Column(Modifier.fillMaxSize().background(Nuru.paper)) {
            ScreenHeader("Resources", kicker = "Grow deeper", onBack = onBack)
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(rows, key = { it.resourceId }) { r ->
                    NuruCard {
                        Kicker(r.kind)
                        Text(r.title, style = NuruType.cardTitle, color = Nuru.ink)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.author, style = NuruType.caption, color = Nuru.ink600, modifier = Modifier.weight(1f))
                            Text(r.durationLabel, style = NuruType.micro, color = Nuru.ink400)
                        }
                    }
                }
            }
        }
    }
}
