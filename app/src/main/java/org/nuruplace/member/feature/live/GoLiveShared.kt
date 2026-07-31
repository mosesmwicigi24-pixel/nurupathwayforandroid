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
import org.nuruplace.member.data.net.RosterRow
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

// ── Cell picker (2026-07-31 iOS→Android parity: a leader of several cells
//    couldn't pick WHICH one to broadcast to — GoLiveSetupSheet only offered
//    a binary "Everyone" vs a single "My cell"). Kept as plain, non-Composable
//    functions specifically so the eligibility/selection logic is unit-
//    testable without a Compose or network harness — same posture as
//    `filterOutSelfStream` in LiveDiscoveryCenter.kt. ──────────────────────

/** One cell the signed-in member may broadcast to under scope="cell" — either
 *  one of several cells they LEAD, or (fallback) the single cell they belong
 *  to personally. See [deriveCellOptions]. */
data class LedCell(val id: String, val name: String)

/** Dedupe a discipleship roster (GET /disciples — already scoped server-side
 *  to the caller's own `leader_assignments`, see MemberApi.disciples()) down
 *  to the distinct cells it names. That scoping is exactly what makes this a
 *  safe "which cells do I lead" signal: every cell surfaced here is one the
 *  server will actually authorize for scope=cell — never a false positive. */
fun ledCellsFromRoster(rows: List<RosterRow>): List<LedCell> {
    val seen = LinkedHashSet<String>()
    val cells = mutableListOf<LedCell>()
    for (row in rows) {
        val id = row.cellGroupId?.takeIf { it.isNotBlank() } ?: continue
        if (!seen.add(id)) continue
        cells.add(LedCell(id, row.cellName?.takeIf { it.isNotBlank() } ?: "Cell"))
    }
    return cells
}

/**
 * The cell choices to offer under "A cell / class". Mirrors iOS's
 * `GoLiveSetupSheet.cellOptions` exactly: prefer the cells this member
 * actually leads ([ledCells], from the roster fetch above — a leader of
 * several cells gets a real picker). Falls back to the member's own cell
 * MEMBERSHIP ([personalCellId]) as a single "My cell"-style option when the
 * roster comes back empty — a non-leader member, or `/disciples` 403ing for
 * a role below Instructor+ — which is exactly today's existing behavior,
 * unchanged for everyone this picker is new for. Never returns a
 * placeholder when there's truly nothing to offer (zero cells → empty list,
 * never an empty picker).
 */
fun deriveCellOptions(
    ledCells: List<LedCell>,
    personalCellId: String?,
    personalCellName: String?,
): List<LedCell> {
    if (ledCells.isNotEmpty()) return ledCells
    val id = personalCellId?.takeIf { it.isNotBlank() } ?: return emptyList()
    return listOf(LedCell(id, personalCellName?.takeIf { it.isNotBlank() } ?: "My cell"))
}

/** Which cell id should be pre-selected/kept selected as [cellOptions] loads
 *  in (the roster fetch is async, so this runs more than once as state
 *  settles). Keeps the current pick if it's still a valid option, otherwise
 *  defaults to the first one, or null when there's nothing to pick yet. */
fun defaultSelectedCellId(cellOptions: List<LedCell>, current: String?): String? {
    if (current != null && cellOptions.any { it.id == current }) return current
    return cellOptions.firstOrNull()?.id
}

/**
 * The `cell_id` to actually send on POST /live/streams. For scope="church"
 * this is a real Kotlin `null` — kotlinx's `encodeDefaults = true` (see
 * ApiClient.kt) serializes that as an EXPLICIT JSON `null`, never an omitted
 * key, matching the backend's `.nullish()` `cell_id` (c65c353). For
 * scope="cell" it prefers the picker's own selection, falling back through
 * the same chain [defaultSelectedCellId] would resolve to — so a picker that
 * never mounted (today's single-cell case) or a stale selection never blocks
 * Start, and a leader who never got to interact with the picker still sends
 * a real cell, never an accidental church-wide broadcast.
 */
fun resolveCellIdForBody(
    scope: String,
    selectedCellId: String?,
    cellOptions: List<LedCell>,
    personalCellId: String?,
): String? {
    if (scope != "cell") return null
    return selectedCellId
        ?: cellOptions.firstOrNull()?.id
        ?: personalCellId?.takeIf { it.isNotBlank() }
}

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
