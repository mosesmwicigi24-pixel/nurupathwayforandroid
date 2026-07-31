package org.nuruplace.member.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic coverage for [isExternalVideoHost] — the gate that decides
 *  whether a video URL plays in-app (InlineVideo) or hands off to the
 *  browser (openExternal). Added 2026-07-31 alongside the fix for a
 *  real-device bug: a self-hosted `/media/<uuid>.mov` upload was handed
 *  unconditionally to openExternal() by two plan-video cards, popping a bare
 *  "Download file again?" Chrome prompt instead of playing the clip — see
 *  VideoPlayer.kt's own doc for the full trace. */
class VideoPlayerTest {
    @Test fun `youtube watch url is external`() {
        assertTrue(isExternalVideoHost("https://www.youtube.com/watch?v=abc123"))
    }

    @Test fun `youtu-be short url is external`() {
        assertTrue(isExternalVideoHost("https://youtu.be/abc123"))
    }

    @Test fun `vimeo url is external`() {
        assertTrue(isExternalVideoHost("https://vimeo.com/12345678"))
    }

    @Test fun `bare host without www is still external`() {
        assertTrue(isExternalVideoHost("https://youtube.com/watch?v=abc123"))
    }

    @Test fun `self-hosted media upload is not external`() {
        assertFalse(
            isExternalVideoHost("https://pathway.nuruplace.org/media/2d97c240-f4de-414c-845c-f7d8041947f5.mov"),
        )
    }

    @Test fun `cloudinary direct video url is not external`() {
        assertFalse(isExternalVideoHost("https://res.cloudinary.com/nuru/video/upload/v1/clip.mp4"))
    }

    @Test fun `a lookalike host is not treated as external`() {
        // Guards the endsWith(".host") check from a naive substring match —
        // "evilyoutube.com" must NOT match "youtube.com".
        assertFalse(isExternalVideoHost("https://evilyoutube.com/watch?v=abc123"))
    }

    @Test fun `malformed url returns false rather than throwing`() {
        assertFalse(isExternalVideoHost("not a url at all"))
    }
}
