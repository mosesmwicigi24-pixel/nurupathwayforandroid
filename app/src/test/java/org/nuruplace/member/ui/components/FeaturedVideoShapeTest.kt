package org.nuruplace.member.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's featured video takes its own shape (owner, 2026-10-06: a portrait
 *  video was pillarboxed inside a fixed 16:9 frame). */
class FeaturedVideoShapeTest {
    private val eps = 0.0005f

    @Test fun `a portrait video is tall, a landscape one wide`() {
        assertEquals(1080f / 1920f, VideoShape.displayAspect(1080, 1920)!!, eps)
        assertEquals(1920f / 1080f, VideoShape.displayAspect(1920, 1080)!!, eps)
    }

    @Test fun `a rotation left to the app turns the shape a quarter`() {
        // Landscape-encoded, shown upright: the player reports 1920×1080 with
        // 90° (or 270°) still to apply → portrait.
        assertEquals(1080f / 1920f, VideoShape.displayAspect(1920, 1080, 1f, 90)!!, eps)
        assertEquals(1080f / 1920f, VideoShape.displayAspect(1920, 1080, 1f, 270)!!, eps)
        assertEquals(1080f / 1920f, VideoShape.displayAspect(1920, 1080, 1f, -90)!!, eps)
        // Half a turn changes nothing; neither does a rotation already applied.
        assertEquals(1920f / 1080f, VideoShape.displayAspect(1920, 1080, 1f, 180)!!, eps)
        assertEquals(1080f / 1920f, VideoShape.displayAspect(1080, 1920, 1f, 0)!!, eps)
    }

    @Test fun `non-square pixels count`() {
        // Anamorphic 1440×1080 with 4:3 pixels is seen at 16:9.
        assertEquals(16f / 9f, VideoShape.displayAspect(1440, 1080, 4f / 3f)!!, eps)
        // A missing or broken pixel ratio is square.
        assertEquals(16f / 9f, VideoShape.displayAspect(1920, 1080, 0f)!!, eps)
        assertEquals(16f / 9f, VideoShape.displayAspect(1920, 1080, Float.NaN)!!, eps)
    }

    @Test fun `an unknown size is no shape`() {
        assertNull(VideoShape.displayAspect(0, 1080))
        assertNull(VideoShape.displayAspect(1920, 0))
        assertNull(VideoShape.displayAspect(-1, -1)) // a Drawable with no intrinsic size
    }

    @Test fun `the frame is held between 9 by 20 and 21 by 9`() {
        assertEquals(9f / 20f, VideoShape.clamp(0.2f), eps)
        assertEquals(21f / 9f, VideoShape.clamp(4f), eps)
        assertEquals(0.5625f, VideoShape.clamp(0.5625f), eps)
        assertEquals(VideoShape.DEFAULT, VideoShape.clamp(Float.NaN), eps)
        assertEquals(VideoShape.DEFAULT, VideoShape.clamp(Float.POSITIVE_INFINITY), eps)
        assertEquals(VideoShape.DEFAULT, VideoShape.clamp(0f), eps)
    }

    @Test fun `the player's report wins, then the remembered shape, then the poster, then 16 by 9`() {
        val portrait = 9f / 16f
        val square = 1f
        val wide = 16f / 9f
        assertEquals(VideoShape.DEFAULT, VideoShape.frame(null, null, null), eps)
        assertEquals(portrait, VideoShape.frame(null, null, portrait), eps)
        // An admin poster in another shape never overrides what the video said last time.
        assertEquals(square, VideoShape.frame(null, square, wide), eps)
        // While it plays, the video itself decides.
        assertEquals(portrait, VideoShape.frame(portrait, square, wide), eps)
        // Clamped either way.
        assertEquals(9f / 20f, VideoShape.frame(0.3f, null, null), eps)
    }

    @Test fun `a pixel of rounding is not a new shape`() {
        assertTrue(VideoShape.differs(null, 1f))
        assertFalse(VideoShape.differs(0.5625f, 0.5630f))
        assertTrue(VideoShape.differs(0.5625f, 1.7778f))
    }
}
