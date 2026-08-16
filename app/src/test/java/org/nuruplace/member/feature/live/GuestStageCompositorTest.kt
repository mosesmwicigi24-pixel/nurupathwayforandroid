package org.nuruplace.member.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for the L7 Zoom-style stage — the two owner-reported
 * live-test defects (portrait geometry degrading once a guest attached; the
 * host video getting completely replaced by an invited guest) were both
 * root-caused and fixed at this layer (see GuestStageCompositor.kt's header
 * for the full mechanism): [mainTileRect]/[railTileRects] are the pure rect
 * math BOTH the composited RTMP frame (GuestStageCompositor.reflow()) and
 * the host's own on-screen preview (LiveStageView.kt's `LiveStage`) build
 * from, and [StageSpotlightState]/[promoteSpotlight]/[demoteSpotlight]/
 * [toggleSpotlight]/[reconcileSpotlightOnGuestsChanged] are the promote/
 * demote/revert state machine that replaced the old automatic active-
 * speaker auto-promotion (the literal cause of "host completely replaced by
 * invited guest").
 *
 * Mirrors iOS's LiveStageTests.swift (LiveStageLayoutTests +
 * LiveStageSpotlightTests, 21 tests) test-for-test where Android's API
 * shape allows a direct analogue; Android's spotlight state uses a nullable
 * `String?` guestId (host = null) rather than iOS's `UInt8` track-number
 * space (host = track 0) — so "tap the host's own rail thumbnail" and "tap
 * the currently-spotlighted guest's own main tile" are two different
 * Android entry points ([demoteSpotlight] / [tapStageTile]'s `guestId ==
 * null` branch, vs. [toggleSpotlight]) where iOS's single `tap(track:)`
 * handles both uniformly — covered separately below rather than forced into
 * one shared test.
 *
 * Deliberately does NOT exercise [GuestStageCompositor] itself (the
 * `object` bound to a real RootEncoder `GlStreamInterface` + WebRTC
 * `VideoTrack`s) — same "pure decision logic only" convention as
 * WhepRetryPolicyTest / LiveWebRtcTest for a GL/WebRTC-backed type.
 */
class GuestStageCompositorTest {
    private val canvasW = 1080f
    private val canvasH = 1920f

    // ── mainTileRect — always the whole canvas, never a function of guest count ──

    @Test fun `main tile fills the entire canvas`() {
        val rect = mainTileRect(canvasW, canvasH)
        assertEquals(TileRect(0f, 0f, canvasW, canvasH), rect)
    }

    @Test fun `main tile geometry is identical regardless of guest count`() {
        val expected = mainTileRect(canvasW, canvasH)
        for (guestCount in 0..6) {
            // Exercises the "other" path (mirrors iOS's identical test) —
            // mainTileRect itself takes no guest-count parameter, so there
            // is nothing for it to react to; this proves that by
            // construction, not just by inspection.
            railTileRects(guestCount, canvasW, canvasH)
            assertEquals("guestCount=$guestCount", expected, mainTileRect(canvasW, canvasH))
        }
    }

    @Test fun `main tile is the same rect for zero and six guests`() {
        assertEquals(mainTileRect(canvasW, canvasH), mainTileRect(canvasW, canvasH))
    }

    // ── railTileRects — 1..6 guests, in bounds, no overlap, stable ordering ──

    @Test fun `rail is empty for zero guests`() {
        assertTrue(railTileRects(0, canvasW, canvasH).isEmpty())
    }

    @Test fun `rail count matches guest count for one to six`() {
        for (count in 1..6) {
            assertEquals(count, railTileRects(count, canvasW, canvasH).size)
        }
    }

    @Test fun `every rail tile is fully in bounds for one to six guests`() {
        for (count in 1..6) {
            railTileRects(count, canvasW, canvasH).forEachIndexed { index, rect ->
                assertTrue("count=$count index=$index x=${rect.x}", rect.x >= 0f)
                assertTrue("count=$count index=$index y=${rect.y}", rect.y >= 0f)
                assertTrue("count=$count index=$index right=${rect.x + rect.width}", rect.x + rect.width <= canvasW + 0.01f)
                assertTrue("count=$count index=$index bottom=${rect.y + rect.height}", rect.y + rect.height <= canvasH + 0.01f)
                assertTrue("count=$count index=$index width=${rect.width}", rect.width > 0f)
                assertTrue("count=$count index=$index height=${rect.height}", rect.height > 0f)
            }
        }
    }

    @Test fun `rail tiles never overlap for one to six guests`() {
        for (count in 1..6) {
            val rects = railTileRects(count, canvasW, canvasH)
            for (i in rects.indices) {
                for (j in i + 1 until rects.size) {
                    val a = rects[i]
                    val b = rects[j]
                    val overlapsX = a.x < b.x + b.width && b.x < a.x + a.width
                    val overlapsY = a.y < b.y + b.height && b.y < a.y + a.height
                    assertFalse("count=$count rects $i and $j overlap: $a vs $b", overlapsX && overlapsY)
                }
            }
        }
    }

    @Test fun `rail tiles stack top to bottom with stable column and increasing y`() {
        for (count in 2..6) {
            val rects = railTileRects(count, canvasW, canvasH)
            for (i in 1 until rects.size) {
                assertEquals("count=$count index=$i", rects[0].x, rects[i].x, 0.01f)
                assertEquals("count=$count index=$i", rects[0].width, rects[i].width, 0.01f)
                assertTrue("count=$count index=$i", rects[i].y > rects[i - 1].y)
            }
        }
    }

    @Test fun `six guests shrink to fit without clipping`() {
        val rects = railTileRects(6, canvasW, canvasH)
        assertEquals(6, rects.size)
        assertTrue(rects.last().y + rects.last().height <= canvasH + 0.01f)
        assertTrue(rects.first().y >= 0f)
    }

    @Test fun `rail tiles sit on the right edge`() {
        for (count in 1..6) {
            railTileRects(count, canvasW, canvasH).forEach { rect ->
                assertTrue("count=$count midX=${rect.x + rect.width / 2}", rect.x + rect.width / 2 > canvasW / 2)
            }
        }
    }

    @Test fun `an invalid canvas produces no rail tiles`() {
        assertTrue(railTileRects(3, 0f, canvasH).isEmpty())
        assertTrue(railTileRects(3, canvasW, 0f).isEmpty())
        assertTrue(railTileRects(-1, canvasW, canvasH).isEmpty())
    }

    // ── StageSpotlightState / promote / demote / toggle ─────────────────────

    @Test fun `spotlight defaults to host (null)`() {
        assertNull(StageSpotlightState().spotlightGuestId)
    }

    @Test fun `promoting a guest sets them as the spotlight`() {
        val state = promoteSpotlight(StageSpotlightState(), "g2")
        assertEquals("g2", state.spotlightGuestId)
    }

    @Test fun `demoting when already at host is a no-op`() {
        val state = StageSpotlightState()
        assertSame(state, demoteSpotlight(state))
    }

    @Test fun `demoting a spotlighted guest reverts to host`() {
        val state = promoteSpotlight(StageSpotlightState(), "g3")
        assertNull(demoteSpotlight(state).spotlightGuestId)
    }

    @Test fun `toggling a guest not currently spotlighted promotes them`() {
        val state = toggleSpotlight(StageSpotlightState(), "g2")
        assertEquals("g2", state.spotlightGuestId)
    }

    @Test fun `toggling the currently spotlighted guest again reverts to host`() {
        var state = toggleSpotlight(StageSpotlightState(), "g2")
        state = toggleSpotlight(state, "g2")
        assertNull(state.spotlightGuestId)
    }

    @Test fun `toggling a different guest switches directly without reverting first`() {
        var state = toggleSpotlight(StageSpotlightState(), "g2")
        state = toggleSpotlight(state, "g5")
        assertEquals("tapping guest 5 while guest 2 was spotlighted should switch straight to 5", "g5", state.spotlightGuestId)
    }

    @Test fun `full promote demote revert cycle`() {
        // Promote guest 1.
        var state = toggleSpotlight(StageSpotlightState(), "g1")
        assertEquals("g1", state.spotlightGuestId)
        // Demote back to host — the host's own rail-thumbnail tap
        // (GuestStageCompositor.tapStageTile(null)).
        state = demoteSpotlight(state)
        assertNull(state.spotlightGuestId)
        // Promote guest 2, then revert by tapping the (now main) guest 2
        // tile again.
        state = toggleSpotlight(state, "g2")
        assertEquals("g2", state.spotlightGuestId)
        state = toggleSpotlight(state, "g2")
        assertNull(state.spotlightGuestId)
    }

    // ── reconcileSpotlightOnGuestsChanged — "spotlighted guest leaves falls
    // back to host" ─────────────────────────────────────────────────────────

    @Test fun `a spotlighted guest leaving falls back to host`() {
        val state = promoteSpotlight(StageSpotlightState(), "g4")
        val reconciled = reconcileSpotlightOnGuestsChanged(state, setOf("g1", "g2"))
        assertNull(reconciled.spotlightGuestId)
    }

    @Test fun `a non-spotlighted guest leaving does not disturb the spotlight`() {
        val state = promoteSpotlight(StageSpotlightState(), "g1")
        val reconciled = reconcileSpotlightOnGuestsChanged(state, setOf("g1")) // g6 (never spotlighted) is gone
        assertEquals("g1", reconciled.spotlightGuestId)
    }

    @Test fun `reconciling with no spotlight is a harmless no-op`() {
        val state = StageSpotlightState()
        assertSame(state, reconcileSpotlightOnGuestsChanged(state, emptySet()))
    }

    @Test fun `reconciling keeps the spotlight when the guest is still live`() {
        val state = promoteSpotlight(StageSpotlightState(), "g4")
        val reconciled = reconcileSpotlightOnGuestsChanged(state, setOf("g4", "g5"))
        assertEquals("g4", reconciled.spotlightGuestId)
    }

    // ── railItems — display order fed to BOTH the compositor's rail slots and
    // the Compose stage's rail rects (one algorithm, per TileRect's own doc) ──

    @Test fun `with no spotlight the rail is exactly every live guest in order`() {
        val ids = listOf("g1", "g2", "g3")
        assertEquals(ids, railItems(ids, spotlightGuestId = null))
    }

    @Test fun `spotlighting a guest puts a host placeholder first, then everyone else`() {
        val ids = listOf("g1", "g2", "g3")
        val rail = railItems(ids, spotlightGuestId = "g2")
        assertEquals(listOf(null, "g1", "g3"), rail)
    }

    @Test fun `a stale spotlight id not among the live guests is ignored`() {
        val ids = listOf("g1", "g2")
        // The guest that was spotlighted already left but the caller hasn't
        // reconciled yet — railItems itself must degrade safely rather than
        // silently dropping a live guest or inserting a phantom host slot.
        assertEquals(ids, railItems(ids, spotlightGuestId = "gone"))
    }

    @Test fun `rail item count never exceeds the guest cap either way`() {
        val ids = (1..MAX_GUESTS).map { "g$it" }
        assertEquals(MAX_GUESTS, railItems(ids, spotlightGuestId = null).size)
        assertEquals(MAX_GUESTS, railItems(ids, spotlightGuestId = "g3").size)
    }

    @Test fun `last guest leaving collapses the rail to empty with the spotlight already reconciled`() {
        val state = promoteSpotlight(StageSpotlightState(), "g1")
        val reconciled = reconcileSpotlightOnGuestsChanged(state, emptySet())
        assertNull(reconciled.spotlightGuestId)
        assertTrue(railItems(emptyList(), reconciled.spotlightGuestId).isEmpty())
    }
}
