package org.nuruplace.member.feature.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for the self-preview collapse/expand reducer
 *  (GuestStageUi.kt) — the owner's "minimizable, remembered within the
 *  session" requirement for the guest's self-preview PiP. */
class GuestStageUiTest {
    @Test fun `starts expanded by default`() {
        assertFalse(SelfPreviewUiState().collapsed)
    }

    @Test fun `collapse always ends collapsed, even if already collapsed`() {
        val expanded = SelfPreviewUiState(collapsed = false)
        val collapsed = SelfPreviewUiState(collapsed = true)
        assertTrue(reduceSelfPreview(expanded, SelfPreviewAction.Collapse).collapsed)
        assertTrue(reduceSelfPreview(collapsed, SelfPreviewAction.Collapse).collapsed)
    }

    @Test fun `expand always ends expanded, even if already expanded`() {
        val expanded = SelfPreviewUiState(collapsed = false)
        val collapsed = SelfPreviewUiState(collapsed = true)
        assertFalse(reduceSelfPreview(expanded, SelfPreviewAction.Expand).collapsed)
        assertFalse(reduceSelfPreview(collapsed, SelfPreviewAction.Expand).collapsed)
    }

    @Test fun `toggle flips whatever the current state is`() {
        val expanded = SelfPreviewUiState(collapsed = false)
        val onceToggled = reduceSelfPreview(expanded, SelfPreviewAction.Toggle)
        assertTrue(onceToggled.collapsed)
        val twiceToggled = reduceSelfPreview(onceToggled, SelfPreviewAction.Toggle)
        assertFalse(twiceToggled.collapsed)
    }
}
