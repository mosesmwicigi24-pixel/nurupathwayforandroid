// Nuru Live — broadcaster entry point (L3). A gold "Go Live" affordance shown
// ONLY to members whose /me profile carries the "live:go" RBAC grant. This
// gate is CLIENT-SIDE ADVISORY ONLY (§5.4) — the server enforces the real
// RBAC check on POST /live/streams regardless (403 FORBIDDEN_SCOPE) — so a
// wrong client guess here fails safely, it just never grants anything extra.
package org.nuruplace.member.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing

/** Does this member have the "live:go" grant at all? Gate every entry point
 *  on this before rendering anything. */
fun canGoLive(me: MeResponse?): Boolean =
    me?.profile?.permissions?.contains("live:go") == true

/**
 * Is CHURCH scope worth offering in the setup sheet's picker?
 *
 * The server is the only real authority on scope (RBAC grants are unscoped
 * for church-wide go-live vs. scoped to a leader's own cell via
 * `leader_assignments` — see docs/LIVE_STREAMING.md), and `GET /me` does not
 * currently return a single "is this an unscoped grant" boolean for the
 * client to key on. Absent that, this uses `role` as the closest available
 * proxy: paid staff (`Admin`/`SuperAdmin`) are the members realistically
 * holding an unscoped church-wide grant; `Instructor` (this app's stand-in
 * for a cell leader/mentor role) is treated as cell-scoped only. If this
 * guess is wrong for a given member the UI just shows an extra option that
 * the server will 403 on — never a security hole, just a worse empty state,
 * so it's an acceptable judgment call rather than a blocker.
 */
fun isChurchLiveEligible(me: MeResponse?): Boolean =
    canGoLive(me) && me?.profile?.role in setOf("Admin", "SuperAdmin")

/** Is MY-CELL scope offerable? Only if the profile actually has a cell. */
fun isCellLiveEligible(me: MeResponse?): Boolean =
    canGoLive(me) && me?.profile?.cellGroupId != null

/** The gold "Go Live" pill — identical rendering wherever it appears (Home's
 *  header area, CellInfoScreen). Callers gate visibility themselves. */
@Composable
fun GoLiveButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Nuru.goldGradient)
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Videocam, contentDescription = null, tint = Nuru.homeNavy, modifier = Modifier.size(16.dp))
        Text("Go Live", style = NuruType.cardCta, color = Nuru.homeNavy, fontWeight = FontWeight.Bold)
    }
}
