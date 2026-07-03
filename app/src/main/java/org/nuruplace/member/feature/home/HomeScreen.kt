// Home — Phase 0 slice: the navy greeting header + a first card. The full
// server-driven dashboard (rhythm ring, next-action hero, verse of the day,
// continue-your-pathway) is ported in Phase 1 alongside its endpoints.
package org.nuruplace.member.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun HomeScreen(me: MeResponse?, onSignOut: () -> Unit, onOpenNotifications: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Nuru.paper)
            .verticalScroll(rememberScrollState()),
    ) {
        // Navy greeting header
        Column(
            Modifier
                .fillMaxWidth()
                .background(Nuru.heroGradient)
                .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Kicker("Nuru Pathway", modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenNotifications) { Text("🔔", style = NuruType.title) }
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Hi, ${me?.profile?.fullName?.substringBefore(' ') ?: "friend"}",
                style = NuruType.display,
                color = Nuru.onNavy,
            )
            me?.enrollment?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text("Level ${it.currentLevel} · ${it.state}", style = NuruType.body, color = Nuru.onNavyDim)
            }
        }

        Column(
            Modifier.padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.base),
        ) {
            NuruCard {
                Kicker("Welcome")
                Spacer(Modifier.height(Spacing.sm))
                Text("Your daily rhythm, pathway and community land here.", style = NuruType.body, color = Nuru.ink600)
            }
            TextButton(onClick = onSignOut) {
                Text("Sign out", style = NuruType.cardCta, color = Nuru.danger)
            }
        }
    }
}
