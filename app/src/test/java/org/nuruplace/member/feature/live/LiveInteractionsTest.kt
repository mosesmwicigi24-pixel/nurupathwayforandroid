package org.nuruplace.member.feature.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.LiveGuestRow
import org.nuruplace.member.data.net.LiveHandRow

/** Pure-logic coverage for the L5 interaction overlay (docs/LIVE_INTERACTIVE.md)
 *  — the TikTok-style count abbreviation and the "is this ME" matchers that
 *  drive the raised-hands chip / guest-invite card, kept as plain functions
 *  in LiveInteractions.kt specifically so they're unit-testable without a
 *  Compose/Robolectric harness. */
class LiveInteractionsTest {
    @Test fun `counts under 1000 render exactly`() {
        assertEquals("0", abbreviateCount(0))
        assertEquals("999", abbreviateCount(999))
    }

    @Test fun `counts in the thousands abbreviate with one decimal`() {
        assertEquals("1K", abbreviateCount(1000))
        assertEquals("1.2K", abbreviateCount(1200))
        assertEquals("9.9K", abbreviateCount(9999))
    }

    @Test fun `counts at 10K and above drop the decimal`() {
        assertEquals("10K", abbreviateCount(10_000))
        assertEquals("99K", abbreviateCount(99_999))
        assertEquals("999K", abbreviateCount(999_999))
    }

    @Test fun `counts in the millions abbreviate too`() {
        assertEquals("1M", abbreviateCount(1_000_000))
        assertEquals("1.5M", abbreviateCount(1_500_000))
    }

    @Test fun `negative counts never render negative`() {
        assertEquals("0", abbreviateCount(-5))
    }

    @Test fun `myHandRaised matches only my own user id`() {
        val hands = listOf(LiveHandRow(userId = "u1"), LiveHandRow(userId = "u2"))
        assertTrue(myHandRaised(hands, "u1"))
        assertFalse(myHandRaised(hands, "u3"))
        assertFalse(myHandRaised(hands, null))
        assertFalse(myHandRaised(emptyList(), "u1"))
    }

    @Test fun `myGuestStatus finds my own row by user id`() {
        val guests = listOf(
            LiveGuestRow(userId = "u1", status = "invited"),
            LiveGuestRow(userId = "u2", status = "accepted"),
        )
        assertEquals("invited", myGuestStatus(guests, "u1"))
        assertEquals("accepted", myGuestStatus(guests, "u2"))
        assertNull(myGuestStatus(guests, "u3"))
        assertNull(myGuestStatus(guests, null))
    }

    // ── clampDragOffset — the floating chat overlay's drag math (owner
    // taste pass, LiveFloatingChat) — pure so it's testable without a
    // Compose/Robolectric harness, same posture as everything above. ──

    private val container = Size(400f, 800f)
    private val element = Size(220f, 180f)

    @Test fun `an offset already inside the container is left untouched`() {
        val offset = Offset(50f, 60f)
        assertEquals(offset, clampDragOffset(offset, container, element))
    }

    @Test fun `a negative offset clamps to the top-left edge`() {
        assertEquals(Offset(0f, 0f), clampDragOffset(Offset(-40f, -25f), container, element))
    }

    @Test fun `an offset past the far edge clamps so the element stays fully visible`() {
        // container 400x800, element 220x180 -> max top-left is (180, 620)
        assertEquals(Offset(180f, 620f), clampDragOffset(Offset(9000f, 9000f), container, element))
    }

    @Test fun `an element larger than its container never gets a negative max`() {
        val hugeElement = Size(500f, 900f)
        assertEquals(Offset(0f, 0f), clampDragOffset(Offset(-10f, -10f), container, hugeElement))
        assertEquals(Offset(0f, 0f), clampDragOffset(Offset(999f, 999f), container, hugeElement))
    }

    // ── reactionEmoji — the ONE key->glyph mapping shared by the viewer's
    // rail, both screens' floating particles, and the broadcaster's
    // read-only LiveReactionCounts (2026-07-31 fix: the broadcaster surface
    // was rendering the raw backend key, e.g. literal text "fire", instead
    // of 🔥). Backend keys are a free string on purpose (LiveDtos.kt), so an
    // unrecognized key is an expected case that must fall back to a neutral
    // glyph — never raw text on screen. ──────────────────────────────────

    @Test fun `reactionEmoji maps every known backend key to its glyph`() {
        assertEquals("❤️", reactionEmoji("love"))
        assertEquals("🔥", reactionEmoji("fire"))
        assertEquals("👍", reactionEmoji("like"))
    }

    @Test fun `reactionEmoji never renders an unknown key as raw text`() {
        assertEquals("✨", reactionEmoji("unknown-future-reaction"))
        assertEquals("✨", reactionEmoji(""))
        // Especially: none of the known keys leak through verbatim for a
        // key that merely CONTAINS them or differs in case — the mapping is
        // exact-match only, so drift (a typo, a differently-cased key) still
        // gets the safe fallback instead of silently matching the wrong emoji.
        assertEquals("✨", reactionEmoji("Fire"))
        assertEquals("✨", reactionEmoji("fire2"))
    }

    // ── orderedReactionEntries — the broadcaster HUD's read-only counts row
    // (LiveReactionCounts) aggregation/ordering, split out as a pure function
    // specifically so this is testable without Compose. ──────────────────

    @Test fun `orderedReactionEntries drops zero and negative counts`() {
        val counts = mapOf("love" to 0, "fire" to 3, "like" to -1)
        assertEquals(listOf("fire" to 3), orderedReactionEntries(counts))
    }

    @Test fun `orderedReactionEntries puts known keys first in a stable order`() {
        val counts = mapOf("like" to 1, "fire" to 1, "love" to 1)
        assertEquals(listOf("love" to 1, "fire" to 1, "like" to 1), orderedReactionEntries(counts))
    }

    @Test fun `orderedReactionEntries sorts unknown keys alphabetically after the known set`() {
        val counts = mapOf("zeal" to 1, "fire" to 1, "amen" to 1)
        assertEquals(listOf("fire" to 1, "amen" to 1, "zeal" to 1), orderedReactionEntries(counts))
    }

    @Test fun `orderedReactionEntries on an empty or all-zero map is empty`() {
        assertEquals(emptyList<Pair<String, Int>>(), orderedReactionEntries(emptyMap()))
        assertEquals(emptyList<Pair<String, Int>>(), orderedReactionEntries(mapOf("love" to 0)))
    }
}
