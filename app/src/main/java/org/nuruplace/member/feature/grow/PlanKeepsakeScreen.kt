// Plan keepsake — the milestone celebration shown when a member finishes the
// WHOLE plan (iOS PlanKeepsakeView parity). A gold seal on the ceremony navy, a
// blessing (Matthew 25:23), the plan name + day count, and a "Continue your
// journey" CTA. The emotional payoff that makes members start the next plan.
package org.nuruplace.member.feature.grow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PlanKeepsakeScreen(planTitle: String, days: Int, onContinue: () -> Unit) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(600)) }
    val t = enter.value
    val navy = Brush.linearGradient(listOf(Color(0xFF0E2A4A), Color(0xFF081C36), Color(0xFF03101F)))
    val gold = Color(0xFFC9A227)

    Column(
        Modifier.fillMaxSize().background(navy).padding(horizontal = 24.dp).padding(top = 64.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().alpha(t), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            // Gold seal.
            Box(Modifier.size(150.dp).scale(0.85f + 0.15f * t), contentAlignment = Alignment.Center) {
                Box(Modifier.size(150.dp).clip(CircleShape).border(1.dp, gold.copy(alpha = 0.15f), CircleShape))
                Box(Modifier.size(122.dp).clip(CircleShape).border(1.dp, gold.copy(alpha = 0.3f), CircleShape))
                Box(Modifier.size(96.dp).clip(CircleShape).background(gold.copy(alpha = 0.10f)).border(2.dp, gold, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✦", style = rSerif(40), color = gold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("PLAN COMPLETE", style = rInter(11, FontWeight.Bold, 2f), color = gold)
            Spacer(Modifier.height(8.dp))
            Text(planTitle, style = rSerif(26, FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("$days day${if (days == 1) "" else "s"} walked with God", style = rInter(13), color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(20.dp))
            Text(
                "“Well done, good and faithful servant… enter into the joy of your Lord.”",
                style = rSerif(15, FontWeight.Normal, 22, italic = true), color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp),
            )
            Text("Matthew 25:23", style = rInter(11, FontWeight.Bold, 1.4f), color = gold, modifier = Modifier.padding(top = 8.dp))
        }
        Box(
            Modifier.fillMaxWidth().alpha(t).heightIn(min = 54.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFE5BC3A), Color(0xFFC9A227), Color(0xFFA8861C)))).clickable { onContinue() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue your journey", style = rInter(15, FontWeight.Bold), color = Color(0xFF0A1628))
        }
    }
}
