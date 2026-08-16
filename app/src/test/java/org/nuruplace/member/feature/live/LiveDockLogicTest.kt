package org.nuruplace.member.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the bottom-dock item eligibility/ordering
 *  (LiveDockLogic.kt) — see that file's header for the owner requirement
 *  this closes ("EVERY control lives" in one dock, never floating loose over
 *  the content). */
class LiveDockLogicTest {
    @Test fun `a pure viewer never sees publish-only controls`() {
        val items = liveDockItems(LiveDockRole.VIEWER)
        assertFalse(items.contains(LiveDockItem.CAMERA_TOGGLE))
        assertFalse(items.contains(LiveDockItem.SWITCH_CAMERA))
        assertFalse(items.contains(LiveDockItem.MIC_TOGGLE))
        assertFalse(items.contains(LiveDockItem.LEAVE_STAGE))
    }

    @Test fun `a viewer still gets the shared audience cluster and speaker`() {
        val items = liveDockItems(LiveDockRole.VIEWER)
        assertEquals(
            listOf(
                LiveDockItem.REACT_LOVE, LiveDockItem.REACT_FIRE, LiveDockItem.REACT_LIKE,
                LiveDockItem.RAISE_HAND, LiveDockItem.CHAT, LiveDockItem.SPEAKER,
            ),
            items,
        )
    }

    @Test fun `a video guest on stage gets every control in the owner-specified order`() {
        val items = liveDockItems(LiveDockRole.GUEST_ON_STAGE, isVideoKind = true)
        assertEquals(
            listOf(
                LiveDockItem.REACT_LOVE, LiveDockItem.REACT_FIRE, LiveDockItem.REACT_LIKE,
                LiveDockItem.RAISE_HAND, LiveDockItem.CHAT,
                LiveDockItem.CAMERA_TOGGLE, LiveDockItem.SWITCH_CAMERA, LiveDockItem.MIC_TOGGLE,
                LiveDockItem.SPEAKER, LiveDockItem.LEAVE_STAGE,
            ),
            items,
        )
    }

    @Test fun `an audio guest on stage never sees camera controls`() {
        val items = liveDockItems(LiveDockRole.GUEST_ON_STAGE, isVideoKind = false)
        assertFalse(items.contains(LiveDockItem.CAMERA_TOGGLE))
        assertFalse(items.contains(LiveDockItem.SWITCH_CAMERA))
        assertTrue(items.contains(LiveDockItem.MIC_TOGGLE))
        assertEquals(
            listOf(
                LiveDockItem.REACT_LOVE, LiveDockItem.REACT_FIRE, LiveDockItem.REACT_LIKE,
                LiveDockItem.RAISE_HAND, LiveDockItem.CHAT,
                LiveDockItem.MIC_TOGGLE,
                LiveDockItem.SPEAKER, LiveDockItem.LEAVE_STAGE,
            ),
            items,
        )
    }

    @Test fun `leave stage is exclusive to the on-stage guest`() {
        assertFalse(liveDockItems(LiveDockRole.VIEWER).contains(LiveDockItem.LEAVE_STAGE))
        assertTrue(liveDockItems(LiveDockRole.GUEST_ON_STAGE).contains(LiveDockItem.LEAVE_STAGE))
    }

    @Test fun `reaction order matches the rail's own known-key order`() {
        val items = liveDockItems(LiveDockRole.VIEWER)
        assertEquals(
            listOf(LiveDockItem.REACT_LOVE, LiveDockItem.REACT_FIRE, LiveDockItem.REACT_LIKE),
            items.take(3),
        )
    }
}
