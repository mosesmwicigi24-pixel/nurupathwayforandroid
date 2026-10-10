package org.nuruplace.member.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The featured video's shape is remembered per media_asset_id, so the next
 *  Home paints the frame right the first time (owner, 2026-10-06). */
class AppPrefsVideoRatioTest {
    @Test fun `a video's shape round-trips through the store, per asset`() {
        val store = MemoryPrefs()
        AppPrefs.attach(store)
        assertNull(AppPrefs.videoRatio("asset-a"))

        AppPrefs.rememberVideoRatio("asset-a", 0.5625f)
        // A fresh attach (process restart) reads it back; another asset is untouched.
        AppPrefs.attach(store)
        assertEquals(0.5625f, AppPrefs.videoRatio("asset-a")!!, 0.0001f)
        assertNull(AppPrefs.videoRatio("asset-b"))
    }

    @Test fun `nothing unusable is kept`() {
        val store = MemoryPrefs()
        AppPrefs.attach(store)
        AppPrefs.rememberVideoRatio("", 0.5625f)
        AppPrefs.rememberVideoRatio("asset-a", Float.NaN)
        AppPrefs.rememberVideoRatio("asset-a", 0f)
        AppPrefs.rememberVideoRatio("asset-a", Float.POSITIVE_INFINITY)
        assertEquals(0, store.values.size)
        assertNull(AppPrefs.videoRatio(""))
    }
}
