// Profile — identity + growth scores + a menu into Your Calling (gifts),
// Resources, the Nuru assistant, notifications, and sign-out. Port of the iOS
// Profile (RootView) + the scores summary.
package org.nuruplace.member.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ScoresSummary
import org.nuruplace.member.ui.components.Kicker
import org.nuruplace.member.ui.components.NuruCard
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

@Composable
fun ProfileScreen(me: MeResponse?, onOpen: (String) -> Unit, onSignOut: () -> Unit) {
    var scores by remember { mutableStateOf<ScoresSummary?>(null) }
    var achievements by remember { mutableStateOf<org.nuruplace.member.data.net.Achievements?>(null) }
    LaunchedEffect(Unit) {
        scores = runCatching { Net.client.api.scores() }.getOrNull()
        achievements = runCatching { Net.client.api.achievements() }.getOrNull()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var avatarUrl by remember(me) { mutableStateOf(me?.profile?.avatarUrl) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val part = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { bytes ->
                    MultipartBody.Part.createFormData("file", "avatar.jpg", bytes.toRequestBody("image/*".toMediaTypeOrNull()))
                }
            }
            if (part != null) {
                runCatching { Net.client.api.uploadAvatar(part).avatarUrl }.getOrNull()?.let { avatarUrl = it }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Nuru.paper).verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().background(Nuru.heroGradient).padding(horizontal = Spacing.screen, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Nuru.goldTint)
                    .clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(model = avatarUrl, contentDescription = "Profile photo", modifier = Modifier.size(72.dp).clip(CircleShape))
                } else {
                    Text(me?.profile?.fullName?.firstOrNull()?.uppercase() ?: "N", style = NuruType.display, color = Nuru.goldLo)
                }
            }
            Text("Tap to change photo", style = NuruType.micro, color = Nuru.onNavyDim, modifier = Modifier.padding(top = Spacing.xs))
            Spacer(Modifier.height(Spacing.sm))
            Text(me?.profile?.fullName ?: "Member", style = NuruType.title, color = Nuru.onNavy)
            me?.profile?.email?.let { Text(it, style = NuruType.caption, color = Nuru.onNavyDim) }
            // Role + place chips (real /me identity).
            val role = me?.profile?.role
            val place = listOfNotNull(me?.profile?.city, me?.profile?.countryCode).joinToString(" · ").ifBlank { null }
            if (role != null || place != null) {
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    role?.let { IdentityChip(it) }
                    place?.let { IdentityChip(it) }
                }
            }
        }

        Column(Modifier.padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            scores?.let { s ->
                NuruCard(modifier = Modifier.clickable { onOpen("score/word") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Kicker("Growth score")
                            Text("${s.overall.score}", style = NuruType.display, color = Nuru.navyDeep)
                            Text(s.overall.band, style = NuruType.caption, color = Nuru.ink600)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            ScoreLine("Word", s.word.score)
                            ScoreLine("Habits", s.habits.score)
                            ScoreLine("Curriculum", s.curriculum.score)
                            ScoreLine("Attendance", s.attendance.score)
                        }
                    }
                }
            }

            // Engagement band self-view — encouraging, never ranking (D-M8).
            scores?.let { s -> BandCard(s.overall.band, s.overall.score) }

            // Achievements — earned badges + streak (real /me/achievements).
            achievements?.let { a -> if (a.badges.isNotEmpty() || a.streak.current > 0) AchievementsCard(a) }

            MenuRow("Your Calling", "Spiritual gifts") { onOpen("gifts") }
            MenuRow("Resources", "Books, talks, articles") { onOpen("resources") }
            MenuRow("Nuru Assistant", "Ask a discipleship question") { onOpen("assistant") }
            MenuRow("Notifications", "Your inbox") { onOpen("notifications") }
            MenuRow("Settings", "Alerts & two-factor security") { onOpen("settings") }
            TextButton(onClick = onSignOut, modifier = Modifier.padding(top = Spacing.sm)) {
                Text("Sign out", style = NuruType.cardCta, color = Nuru.danger)
            }
            Spacer(Modifier.height(Spacing.tabBarSpace))
        }
    }
}

@Composable
private fun ScoreLine(label: String, value: Int) {
    Row {
        Text(label, style = NuruType.micro, color = Nuru.ink400)
        Spacer(Modifier.size(Spacing.sm))
        Text("$value", style = NuruType.micro, color = Nuru.ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    NuruCard(modifier = Modifier.clickable { onClick() }) {
        Text(title, style = NuruType.rowTitle, color = Nuru.ink)
        Text(subtitle, style = NuruType.caption, color = Nuru.ink600)
    }
}

@Composable
private fun IdentityChip(label: String) {
    Box(
        Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(Radii.pill))
            .background(Nuru.onNavy.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 3.dp),
    ) { Text(label, style = NuruType.micro, color = Nuru.onNavy) }
}

/** Encouraging engagement-band card — supportive copy, never a ranking (D-M8). */
@Composable
private fun BandCard(band: String, score: Int) {
    val (line, tint) = when (band.lowercase().replace(' ', '_')) {
        "thriving" -> "You're thriving — your roots run deep. Keep watering them." to Nuru.success
        "steady" -> "Steady and faithful. Small, consistent steps are forming you." to Nuru.info
        "watch" -> "A gentle nudge — a little rhythm this week will re-centre you." to Nuru.warning
        "at_risk" -> "Let's reconnect — small steps count. Heaven is cheering you on." to Nuru.danger
        else -> "Your journey is your own — one faithful day at a time." to Nuru.gold
    }
    NuruCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.size(Spacing.sm))
            Text(band.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = NuruType.kicker, color = tint)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(line, style = NuruType.body, color = Nuru.ink)
    }
}

/** Earned badges + streak. Medallions read as a keepsake row, not a scoreboard. */
@Composable
private fun AchievementsCard(a: org.nuruplace.member.data.net.Achievements) {
    NuruCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker("Achievements", modifier = Modifier.weight(1f))
            if (a.streak.current > 0) Text("🔥 ${a.streak.current}-day streak", style = NuruType.micro, color = Nuru.goldLo)
        }
        if (a.badges.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                a.badges.forEach { b -> BadgeMedallion(b) }
            }
        }
    }
}

@Composable
private fun BadgeMedallion(b: org.nuruplace.member.data.net.Badge) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Nuru.goldTint)
                .then(Modifier.border(1.dp, Nuru.gold.copy(alpha = 0.4f), CircleShape)),
            contentAlignment = Alignment.Center,
        ) { Text(badgeGlyph(b.category), style = NuruType.title) }
        Spacer(Modifier.height(Spacing.xs))
        Text(b.name, style = NuruType.micro, color = Nuru.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
    }
}

private fun badgeGlyph(category: String): String = when (category.lowercase()) {
    "consistency" -> "🔥"
    "community" -> "🤝"
    "service" -> "🙌"
    "journey" -> "🧭"
    else -> "🏅"
}
