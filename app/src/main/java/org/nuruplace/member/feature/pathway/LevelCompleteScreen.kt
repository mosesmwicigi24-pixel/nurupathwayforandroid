// Level complete — the level-up certificate ceremony (Figma LevelComplete).
// A dark ground, a concentric gold-ring certificate motif, the level name, an
// "Awarded to {name}" citation, and a translucent Next-Level card with a gold
// "Begin Level N" CTA. Shown after the level EXAM is passed; the real
// certificate + verification code live in Profile. Gentle scale/fade entrance.
package org.nuruplace.member.feature.pathway

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.LevelScore
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.ui.components.AsyncContent
import org.nuruplace.member.ui.components.PrimaryButton
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Radii
import org.nuruplace.member.ui.theme.Spacing

private data class CompleteBundle(val name: String, val level: PathwayLevel?, val next: PathwayLevel?, val score: LevelScore?)

@Composable
fun LevelCompleteScreen(levelNumber: Int, onContinue: () -> Unit) {
    AsyncContent(
        key = levelNumber,
        load = {
            val name = runCatching { Net.client.api.me().profile.fullName }.getOrNull() ?: "Disciple"
            val levels = runCatching { Net.client.api.pathway().levels }.getOrDefault(emptyList())
            val score = runCatching { Net.client.api.levelScore(levelNumber) }.getOrNull()
            CompleteBundle(name, levels.firstOrNull { it.levelNumber == levelNumber }, levels.firstOrNull { it.levelNumber == levelNumber + 1 }, score)
        },
    ) { b: CompleteBundle, _ ->
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(600)) }
        val t = enter.value

        Column(
            Modifier.fillMaxSize().background(Nuru.ceremonyGradient).padding(horizontal = Spacing.screen).padding(top = Spacing.xxl, bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().alpha(t),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Concentric ring certificate motif.
                Box(Modifier.size(210.dp).scale(0.85f + 0.15f * t), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(210.dp).clip(CircleShape).border(1.dp, Nuru.gold.copy(alpha = 0.12f), CircleShape))
                    Box(Modifier.size(182.dp).clip(CircleShape).border(1.dp, Nuru.gold.copy(alpha = 0.25f), CircleShape))
                    Box(
                        Modifier.size(154.dp).clip(CircleShape).background(Nuru.gold.copy(alpha = 0.08f)).border(2.dp, Nuru.gold, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✦", style = NuruType.display, color = Nuru.gold)
                            Text("COMPLETED", style = NuruType.micro, color = Nuru.gold, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
                Text("CERTIFICATE OF COMPLETION", style = NuruType.kicker, color = Nuru.gold)
                Spacer(Modifier.height(Spacing.sm))
                Text(b.level?.title ?: "Level $levelNumber", style = NuruType.display, color = Nuru.onNavy, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.sm))
                Text("Awarded to ${b.name}\nfor completing Level $levelNumber", style = NuruType.body, color = Nuru.onNavyDim, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.lg))
                Row(Modifier.width(200.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Box(Modifier.weight(1f).height(1.dp).background(Nuru.gold.copy(alpha = 0.3f)))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Nuru.gold.copy(alpha = 0.5f)))
                    Box(Modifier.weight(1f).height(1.dp).background(Nuru.gold.copy(alpha = 0.3f)))
                }
                Spacer(Modifier.height(Spacing.md))
                Text("Nuru Place Pathway", style = NuruType.caption, color = Nuru.onNavyFaint)
                b.score?.let { s ->
                    Spacer(Modifier.height(Spacing.lg))
                    LevelScoreBreakdown(s)
                }
            }

            // Next-level card + CTA.
            val next = b.next
            if (next != null) {
                Column(
                    Modifier.fillMaxWidth().alpha(t).clip(RoundedCornerShape(Radii.control))
                        .background(Nuru.onNavy.copy(alpha = 0.05f)).border(1.dp, Nuru.gold.copy(alpha = 0.2f), RoundedCornerShape(Radii.control))
                        .padding(Spacing.base),
                ) {
                    Text("NEXT LEVEL", style = NuruType.micro, color = Nuru.onNavyDim)
                    Text(next.title, style = NuruType.cardTitle, color = Nuru.onNavy, fontWeight = FontWeight.Bold)
                    Text("${next.totalModules} modules" + (next.minutes.takeIf { it > 0 }?.let { " · ≈ ${it} min" } ?: ""), style = NuruType.caption, color = Nuru.onNavyFaint)
                }
                Spacer(Modifier.height(Spacing.md))
                Box(Modifier.fillMaxWidth().alpha(t)) {
                    PrimaryButton(if (next.status != LevelStatus.LOCKED) "Begin Level ${next.levelNumber}" else "Continue", onClick = onContinue)
                }
            } else {
                Box(Modifier.fillMaxWidth().alpha(t)) { PrimaryButton("Continue", onClick = onContinue) }
            }
        }
    }
}

/** The level's mastery out of 100 — exam (50) + module quizzes (30) + app
 *  participation (20). Gold on the ceremony navy, so the member sees where the
 *  100 came from. */
@Composable
private fun LevelScoreBreakdown(s: LevelScore) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.control))
            .background(Nuru.onNavy.copy(alpha = 0.05f))
            .border(1.dp, Nuru.gold.copy(alpha = 0.25f), RoundedCornerShape(Radii.control))
            .padding(Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR LEVEL SCORE", style = NuruType.micro, color = Nuru.gold.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${s.total}", style = NuruType.cardTitle, color = Nuru.gold, fontWeight = FontWeight.Bold)
            Text(" / 100", style = NuruType.caption, color = Nuru.onNavyFaint)
        }
        ScoreRow("Exam", s.exam.score, s.exam.of)
        ScoreRow("Modules", s.modules.score, s.modules.of)
        ScoreRow("Participation", s.participation.score, s.participation.of)
    }
}

@Composable
private fun ScoreRow(label: String, got: Int, of: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = NuruType.caption, color = Nuru.onNavyDim)
            Spacer(Modifier.weight(1f))
            Text("$got / $of", style = NuruType.caption, color = Nuru.gold, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Nuru.onNavy.copy(alpha = 0.10f))) {
            val frac = if (of > 0) (got.toFloat() / of).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(frac).height(5.dp).clip(CircleShape).background(Nuru.gold))
        }
    }
}
