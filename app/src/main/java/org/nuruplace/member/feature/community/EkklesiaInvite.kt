// The invitation to intercede — one quiet row, placed on a few pages (Plans,
// the Pathway, Events) and never twice on one. A member who is not on the
// watch reads the day's line and a gold "Join the watch"; an intercessor
// reads the watch's real count for them today. Nothing shows until the
// watch has answered once, and nothing shows if it never does.
package org.nuruplace.member.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.components.GrowPal
import org.nuruplace.member.ui.components.gInter
import org.nuruplace.member.ui.icons.Lucide
import java.time.LocalDate

@Composable
fun EkklesiaInvite(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { EkklesiaPulse.refresh() }
    val s = EkklesiaPulse.summary ?: return
    val shape = RoundedCornerShape(20.dp)
    val member = s.isMember
    Row(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(
                if (member) Brush.linearGradient(listOf(Color(0xFF0A1628), Color(0xFF16273F)))
                else Brush.linearGradient(listOf(GrowPal.goldTint.copy(alpha = 0.55f), GrowPal.goldTint.copy(alpha = 0.35f))),
            )
            .border(1.dp, if (member) Color.White.copy(alpha = 0.10f) else GrowPal.gold.copy(alpha = 0.30f), shape)
            .clickable(onClickLabel = if (member) "Open the watch" else "Join the watch") { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (member) GrowPal.gold.copy(alpha = 0.18f) else GrowPal.white),
            contentAlignment = Alignment.Center,
        ) { Icon(Lucide.Flame, null, tint = if (member) GrowPal.gold else GrowPal.navy, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (member) "EKKLESIA · THE WATCH" else "EKKLESIA · INTERCESSORS",
                style = gInter(11, FontWeight.SemiBold, 1.6f),
                color = if (member) GrowPal.gold else GrowPal.goldChipText,
            )
            Text(
                if (member) EkklesiaWords.memberLine(s.needsMeToday, s.urgentCount)
                else EkklesiaWords.invitation(LocalDate.now().dayOfYear),
                style = gInter(13, FontWeight.Medium),
                color = if (member) Color.White else GrowPal.navy,
            )
            val under = if (member) null else EkklesiaWords.standing(s.memberCount)
            under?.let { Text(it, style = gInter(11), color = GrowPal.ink600) }
        }
        Text(
            if (member) "Intercede ›" else "Join ›",
            style = gInter(12, FontWeight.Bold),
            color = if (member) GrowPal.gold else GrowPal.navy,
        )
    }
}
