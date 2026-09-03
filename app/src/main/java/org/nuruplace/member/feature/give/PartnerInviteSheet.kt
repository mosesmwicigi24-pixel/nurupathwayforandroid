package org.nuruplace.member.feature.give

// The partner invitation — the one place the app asks for money unprompted.
// The Android half of iOS PartnerInviteSheet.swift; keep the two in step.
//
// THIS SHEET DECIDES NOTHING. Whether to ask is settled entirely on the server
// (invitation.ts): never a partner, never a minor, never someone's first week,
// never in their quiet hours, three showings per campaign, a fortnight between
// waves. Duplicating any of that here is how the two apps drift, and the drift
// is always towards asking more often.
//
// Three deliberate restraints in the presentation itself:
//
//   · the primary button opens the Partners screen, NOT a payment sheet.
//     Nobody should be one tap from a charge they have not read about.
//   · dismissal is always available and never punished. "Don't ask again"
//     appears from the SECOND showing — offering a permanent no immediately
//     invites one from someone who simply had a busy morning.
//   · a match renders only when the payload names a pledger. The database and
//     the server both refuse an unpledged match; this is the third gate.

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.nuruplace.member.data.net.InviteCampaign
import org.nuruplace.member.data.net.InviteMatch
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.nuruSerif
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerInviteSheet(
    campaign: InviteCampaign,
    /** Higher on a repeat showing — that is when "don't ask again" appears. */
    showing: Int,
    onBecomePartner: () -> Unit,
    onDismiss: (permanent: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        // A swipe or a backdrop tap is a dismissal like any other, and is
        // reported as one. Silence is data too.
        onDismissRequest = { onDismiss(false) },
        sheetState = sheetState,
        containerColor = Nuru.paper,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            campaign.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                AsyncImage(
                    model = url, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                )
            }

            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(campaign.title, style = nuruSerif(27, FontWeight.Medium), color = Nuru.ink)
                Text(campaign.blurb, style = NuruType.bodyLg, color = Nuru.ink600)

                InviteProgress(campaign)
                campaign.match?.let { InviteMatchNote(it, campaign.currency) }
                InviteTiers(campaign)
                InviteActions(showing, onBecomePartner, onDismiss)
            }
        }
    }
}

@Composable
private fun InviteProgress(c: InviteCampaign) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(7.dp)
                .clip(RoundedCornerShape(4.dp)).background(Nuru.navy.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier.fillMaxWidth(c.progress).fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp)).background(Nuru.gold),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${c.currency} ${grouped(c.raisedMinor / 100)}",
                style = NuruType.label, color = Nuru.ink)
            Spacer(Modifier.width(6.dp))
            Text("of ${c.currency} ${grouped(c.goalMinor / 100)}",
                style = NuruType.caption, color = Nuru.ink600)
            Spacer(Modifier.weight(1f))
            // Honest about time without manufacturing panic.
            Text(
                when (c.daysLeft) {
                    0 -> "Ends today"
                    1 -> "1 day left"
                    else -> "${c.daysLeft} days left"
                },
                style = NuruType.caption, color = Nuru.ink600,
            )
        }
    }
}

@Composable
private fun InviteMatchNote(m: InviteMatch, currency: String) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(Nuru.goldChipBg).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The pledger is NAMED. An unnamed match is the claim this whole design
        // exists to prevent.
        Text(
            "${m.pledger} will match every gift up to $currency ${grouped(m.amountMinor / 100)}.",
            style = NuruType.body, color = Nuru.ink,
        )
    }
}

@Composable
private fun InviteTiers(c: InviteCampaign) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        c.tiers.forEach { t ->
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp)).background(Nuru.white)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${t.currency} ${grouped(t.amountMinor / 100)}",
                    style = nuruSerif(19, FontWeight.Medium), color = Nuru.ink,
                    modifier = Modifier.widthIn(min = 96.dp))
                // The meaning comes from the server, derived from one costing.
                // Never invented here.
                Text(t.meaning, style = NuruType.caption, color = Nuru.ink600)
            }
        }
        Text("A monthly gift. Change it or stop it whenever you need to.",
            style = NuruType.caption, color = Nuru.ink400)
    }
}

@Composable
private fun InviteActions(
    showing: Int,
    onBecomePartner: () -> Unit,
    onDismiss: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Opens the Partners screen, NOT a payment sheet.
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)).background(Nuru.navyDeep)
                .clickable { onBecomePartner() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Become a partner", style = NuruType.heading, color = Color.White)
        }

        Box(
            Modifier.fillMaxWidth().clickable { onDismiss(false) }.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Not now", style = NuruType.body, color = Nuru.ink600)
        }

        // Only from the second showing.
        if (showing >= 2) {
            Box(
                Modifier.fillMaxWidth().clickable { onDismiss(true) }.padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Don't ask again", style = NuruType.caption, color = Nuru.ink400,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

private fun grouped(v: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(v)
