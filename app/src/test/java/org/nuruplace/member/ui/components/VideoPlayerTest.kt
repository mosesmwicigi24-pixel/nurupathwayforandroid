package org.nuruplace.member.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.WelcomeVideo

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

/** The 2026-08-26 fix: the Home featured card played nothing in place — it
 *  opened a browser that offered to DOWNLOAD the .mp4. Root cause proved from
 *  the backend: an uploaded video is stored as `video_source = 'direct'` with
 *  `external_url` = our own public /media/<uuid>.mp4, and the old
 *  `WelcomeVideo.isExternal` read `externalUrl != null` as "needs a browser".
 *  These tests pin BOTH halves of the fix: what needs a web embed, and what a
 *  web embed actually is. */
class VideoEmbedTest {
    private fun uploaded(url: String) = WelcomeVideo(videoSource = "direct", externalUrl = url)

    @Test fun `an uploaded direct video never needs a web embed`() {
        // The exact prod shape that used to open the download prompt.
        val v = uploaded("https://pathway.nuruplace.org/media/2d97c240-f4de-414c-845c-f7d8041947f5.mp4")
        assertFalse(v.needsWebEmbed)
        assertNull(videoEmbedUrl(v.playUrl!!, v.videoSource, v.externalVideoId))
    }

    @Test fun `cloudinary and private hosted sources play with exoplayer`() {
        assertFalse(WelcomeVideo(videoSource = "cloudinary", url = "https://res.cloudinary.com/a/b.mp4").needsWebEmbed)
        assertFalse(WelcomeVideo(videoSource = "private", url = "https://cdn.example/x.m3u8").needsWebEmbed)
    }

    @Test fun `youtube and vimeo sources need a web embed`() {
        assertTrue(WelcomeVideo(videoSource = "youtube", externalUrl = "https://youtu.be/abc123").needsWebEmbed)
        assertTrue(WelcomeVideo(videoSource = "vimeo", externalUrl = "https://vimeo.com/12345678").needsWebEmbed)
    }

    @Test fun `youtube embed uses the inline autoplay player page`() {
        assertEquals(
            "https://www.youtube.com/embed/abc123?playsinline=1&autoplay=1&modestbranding=1&rel=0",
            videoEmbedUrl("https://www.youtube.com/watch?v=abc123", "youtube"),
        )
    }

    @Test fun `the server's external video id wins over url parsing`() {
        assertEquals(
            "https://www.youtube.com/embed/REALID?playsinline=1&autoplay=1&modestbranding=1&rel=0",
            videoEmbedUrl("https://www.youtube.com/watch?v=abc123", "youtube", "REALID"),
        )
    }

    @Test fun `youtu-be short link yields the same embed`() {
        assertEquals(
            "https://www.youtube.com/embed/abc123?playsinline=1&autoplay=1&modestbranding=1&rel=0",
            videoEmbedUrl("https://youtu.be/abc123", null),
        )
    }

    @Test fun `vimeo embed autoplays inline`() {
        assertEquals(
            "https://player.vimeo.com/video/12345678?autoplay=1&playsinline=1",
            videoEmbedUrl("https://vimeo.com/12345678", "vimeo"),
        )
    }

    @Test fun `a provider is sniffed from the host when the source is missing`() {
        assertEquals(
            "https://player.vimeo.com/video/99?autoplay=1&playsinline=1",
            videoEmbedUrl("https://vimeo.com/99"),
        )
        assertNull(videoEmbedUrl("https://pathway.nuruplace.org/media/x.mp4"))
    }

    @Test fun `a lookalike host is not embedded`() {
        assertNull(videoEmbedUrl("https://evilyoutube.com/watch?v=abc123"))
    }

    @Test fun `a malformed url returns null rather than throwing`() {
        assertNull(videoEmbedUrl("not a url at all"))
    }
}
