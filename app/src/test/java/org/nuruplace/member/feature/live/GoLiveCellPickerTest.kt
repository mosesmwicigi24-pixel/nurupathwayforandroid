package org.nuruplace.member.feature.live

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.CreateLiveStreamBody
import org.nuruplace.member.data.net.MeResponse
import org.nuruplace.member.data.net.RosterRow
import org.nuruplace.member.data.net.UserProfile

/**
 * Pure-logic coverage for the 2026-07-31 iOS→Android parity fix: a leader who
 * oversees several cells could only pick between "Everyone" and a single
 * generic "My cell" in GoLiveSetupSheet — with no way to choose WHICH cell.
 * These functions (GoLiveShared.kt) now drive that picker; kept as plain
 * top-level functions specifically so this is unit-testable without a
 * Compose or network harness, matching this file's siblings
 * (LiveDiscoveryCenterTest.kt's `filterOutSelfStream`).
 */
class GoLiveCellPickerTest {

    private fun roster(vararg cells: Pair<String, String?>) =
        cells.mapIndexed { i, (id, name) -> RosterRow(userId = "u$i", cellGroupId = id, cellName = name) }

    // ── ledCellsFromRoster ──────────────────────────────────────────────

    @Test fun `roster with no cell rows yields no led cells`() {
        val rows = listOf(RosterRow(userId = "u1", cellGroupId = null))
        assertTrue(ledCellsFromRoster(rows).isEmpty())
    }

    @Test fun `roster dedupes repeated cell ids and keeps first-seen order`() {
        val rows = roster("cell-a" to "Alpha", "cell-b" to "Beta", "cell-a" to "Alpha (dup)")
        val cells = ledCellsFromRoster(rows)
        assertEquals(listOf(LedCell("cell-a", "Alpha"), LedCell("cell-b", "Beta")), cells)
    }

    @Test fun `roster falls back to a generic name when cellName is blank`() {
        val rows = roster("cell-a" to null, "cell-b" to "  ")
        val cells = ledCellsFromRoster(rows)
        assertEquals(listOf(LedCell("cell-a", "Cell"), LedCell("cell-b", "Cell")), cells)
    }

    @Test fun `roster skips rows with a blank cell id`() {
        val rows = roster("" to "Alpha", "   " to "Beta")
        assertTrue(ledCellsFromRoster(rows).isEmpty())
    }

    // ── deriveCellOptions — zero / one / many cells ─────────────────────

    @Test fun `zero cells — no led cells and no personal cell yields an empty list, never a placeholder`() {
        val options = deriveCellOptions(emptyList(), personalCellId = null, personalCellName = null)
        assertTrue(options.isEmpty())
    }

    @Test fun `zero cells — blank personal cell id is treated as absent`() {
        val options = deriveCellOptions(emptyList(), personalCellId = "  ", personalCellName = "Whatever")
        assertTrue(options.isEmpty())
    }

    @Test fun `exactly one cell — a single led cell is returned as-is`() {
        val led = listOf(LedCell("cell-a", "Alpha Cell"))
        val options = deriveCellOptions(led, personalCellId = "cell-z", personalCellName = "My Own Cell")
        // Led cells win over the personal-membership fallback whenever present.
        assertEquals(led, options)
    }

    @Test fun `exactly one cell — falls back to personal membership with a real name`() {
        val options = deriveCellOptions(emptyList(), personalCellId = "cell-z", personalCellName = "Grace Cell")
        assertEquals(listOf(LedCell("cell-z", "Grace Cell")), options)
    }

    @Test fun `exactly one cell — falls back to the generic label when no name is known yet`() {
        val options = deriveCellOptions(emptyList(), personalCellId = "cell-z", personalCellName = null)
        assertEquals(listOf(LedCell("cell-z", "My cell")), options)
    }

    @Test fun `many cells — a leader of several cells gets every one of them, roster wins over personal fallback`() {
        val led = listOf(LedCell("cell-a", "Alpha"), LedCell("cell-b", "Beta"), LedCell("cell-c", "Gamma"))
        val options = deriveCellOptions(led, personalCellId = "cell-a", personalCellName = "Alpha")
        assertEquals(3, options.size)
        assertEquals(led, options)
    }

    // ── defaultSelectedCellId ────────────────────────────────────────────

    @Test fun `no options yet — nothing is selected`() {
        assertNull(defaultSelectedCellId(emptyList(), current = null))
    }

    @Test fun `first load — defaults to the first option`() {
        val options = listOf(LedCell("cell-a", "Alpha"), LedCell("cell-b", "Beta"))
        assertEquals("cell-a", defaultSelectedCellId(options, current = null))
    }

    @Test fun `a still-valid current selection is preserved, not reset to the first option`() {
        val options = listOf(LedCell("cell-a", "Alpha"), LedCell("cell-b", "Beta"))
        assertEquals("cell-b", defaultSelectedCellId(options, current = "cell-b"))
    }

    @Test fun `a selection that's no longer in the list falls back to the first option`() {
        val options = listOf(LedCell("cell-a", "Alpha"), LedCell("cell-b", "Beta"))
        assertEquals("cell-a", defaultSelectedCellId(options, current = "cell-stale"))
    }

    // ── resolveCellIdForBody — what actually reaches the API ────────────

    @Test fun `church scope always resolves to a real null, regardless of any cell state`() {
        assertNull(
            resolveCellIdForBody(
                scope = "church",
                selectedCellId = "cell-a",
                cellOptions = listOf(LedCell("cell-a", "Alpha")),
                personalCellId = "cell-a",
            ),
        )
    }

    @Test fun `cell scope with a picker selection sends exactly that cell id`() {
        val id = resolveCellIdForBody(
            scope = "cell",
            selectedCellId = "cell-b",
            cellOptions = listOf(LedCell("cell-a", "Alpha"), LedCell("cell-b", "Beta")),
            personalCellId = "cell-a",
        )
        assertEquals("cell-b", id)
    }

    @Test fun `cell scope with no explicit selection falls back to the first option (single-cell case never shows a picker)`() {
        val id = resolveCellIdForBody(
            scope = "cell",
            selectedCellId = null,
            cellOptions = listOf(LedCell("cell-a", "Alpha")),
            personalCellId = null,
        )
        assertEquals("cell-a", id)
    }

    @Test fun `cell scope with no options at all falls back to the personal membership id`() {
        val id = resolveCellIdForBody(
            scope = "cell",
            selectedCellId = null,
            cellOptions = emptyList(),
            personalCellId = "cell-personal",
        )
        assertEquals("cell-personal", id)
    }

    @Test fun `cell scope with nothing to resolve to yields null (never sends a bogus id)`() {
        val id = resolveCellIdForBody(
            scope = "cell",
            selectedCellId = null,
            cellOptions = emptyList(),
            personalCellId = null,
        )
        assertNull(id)
    }

    // ── Wire shape: a real JSON null for church, the real id for cell ───
    // Mirrors LiveDtoTest.kt's Json config (ApiClient's own: encodeDefaults
    // = true, snake_case). encodeDefaults is exactly what turns Kotlin's
    // `cellId = null` into an EXPLICIT `"cell_id":null` in the request body
    // instead of an omitted key — the Android half of the backend's
    // `.nullish()` cell_id fix (c65c353): the server distinguishes "cell_id
    // omitted" from "cell_id explicitly null" and a plain `null` default
    // that got dropped by serialization would silently regress this.
    @OptIn(ExperimentalSerializationApi::class)
    private val wireJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    @Test fun `church-wide request body encodes cell_id as an explicit JSON null, never an omitted key`() {
        val body = CreateLiveStreamBody(
            scope = "church",
            cellId = resolveCellIdForBody("church", selectedCellId = "stale", cellOptions = emptyList(), personalCellId = null),
            title = "Sunday Service",
            kind = "video",
        )
        val encoded = wireJson.encodeToString(CreateLiveStreamBody.serializer(), body)
        assertTrue("expected an explicit null, got: $encoded", encoded.contains("\"cell_id\":null"))
    }

    @Test fun `cell-scoped request body encodes the exact selected cell id`() {
        val body = CreateLiveStreamBody(
            scope = "cell",
            cellId = resolveCellIdForBody(
                "cell",
                selectedCellId = "cell-b",
                cellOptions = listOf(LedCell("cell-a", "Alpha"), LedCell("cell-b", "Beta")),
                personalCellId = "cell-a",
            ),
            title = "Cell Night",
            kind = "audio",
        )
        val encoded = wireJson.encodeToString(CreateLiveStreamBody.serializer(), body)
        assertTrue("expected cell-b in the body, got: $encoded", encoded.contains("\"cell_id\":\"cell-b\""))
    }

    // ── isChurchLiveEligible — 2026-07-31 owner-directed iOS parity: widened
    // from {Admin, SuperAdmin} to {Instructor, Admin, SuperAdmin}, matching
    // the backend's OWN authority (IdentityService.STAFF_ROLES,
    // packages/backend/src/modules/identity/service.ts) exactly, which iOS's
    // LiveBroadcastEligibility.churchEligible already used. See
    // GoLiveShared.kt's own doc for the full owner-decision paper trail. ────

    private fun profile(role: String, permissions: List<String> = listOf("live:go")) =
        MeResponse(profile = UserProfile(userId = "u1", fullName = "Test User", role = role, permissions = permissions))

    @Test fun `Instructor is church-eligible — the 2026-07-31 owner-directed parity widening`() {
        assertTrue(isChurchLiveEligible(profile("Instructor")))
    }

    @Test fun `Admin and SuperAdmin remain church-eligible`() {
        assertTrue(isChurchLiveEligible(profile("Admin")))
        assertTrue(isChurchLiveEligible(profile("SuperAdmin")))
    }

    @Test fun `a plain Student is never church-eligible`() {
        assertFalse(isChurchLiveEligible(profile("Student")))
    }

    @Test fun `an Instructor without the live-go permission is not church-eligible`() {
        assertFalse(isChurchLiveEligible(profile("Instructor", permissions = emptyList())))
    }

    @Test fun `no profile at all is never church-eligible`() {
        assertFalse(isChurchLiveEligible(null))
    }
}
