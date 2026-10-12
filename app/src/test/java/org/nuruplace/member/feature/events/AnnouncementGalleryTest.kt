// An announcement's pictures (pathway docs/EXPERIENCE.md §7.4 #12). Seen: the
// cover at the top of the page and again at its foot — GET /announcements/:id
// sends `images` as [cover, …gallery], and the page showed `images` under the
// body as well as the cover above it.
package org.nuruplace.member.feature.events

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nuruplace.member.data.net.AnnouncementDetail

class AnnouncementGalleryTest {
    private val cover = "https://res.cloudinary.com/x/cover.jpg"

    @Test fun `the cover shows once — the gallery is the rest`() {
        val a = AnnouncementDetail(
            announcementId = "89e2", primaryImageUrl = cover,
            galleryImageUrls = listOf("g1.jpg", "g2.jpg"), images = listOf(cover, "g1.jpg", "g2.jpg"),
        )
        assertEquals(listOf("g1.jpg", "g2.jpg"), announcementGallery(a))
    }

    @Test fun `a cover alone leaves no gallery`() {
        val a = AnnouncementDetail(announcementId = "89e2", primaryImageUrl = cover, images = listOf(cover))
        assertEquals(emptyList<String>(), announcementGallery(a))
    }

    @Test fun `no cover — every picture is the gallery, and an older server's gallery still shows`() {
        assertEquals(listOf("g1.jpg"), announcementGallery(AnnouncementDetail(announcementId = "a", images = listOf("g1.jpg"))))
        assertEquals(
            listOf("g1.jpg"),
            announcementGallery(AnnouncementDetail(announcementId = "a", primaryImageUrl = cover, galleryImageUrls = listOf("g1.jpg"))),
        )
    }
}
