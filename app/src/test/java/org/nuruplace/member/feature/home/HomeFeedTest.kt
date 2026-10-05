// What Home's feed offers (pathway docs/EXPERIENCE.md §7, Cycle 3): an action
// shows only when the server says it can succeed (rule 2).
package org.nuruplace.member.feature.home

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.FeaturedAnnouncement
import org.nuruplace.member.data.net.FeaturedEvent
import org.nuruplace.member.data.net.HomeEventRow
import org.nuruplace.member.data.net.HomeNudge
import org.nuruplace.member.data.net.LevelStatus
import org.nuruplace.member.data.net.PathwayLevel
import org.nuruplace.member.data.net.PathwaySummary

class HomeFeedTest {
    // ── the exam's nudge (§7.2 #1) ──

    /** The server's level_review nudge: route "level_exam", the level in params. */
    private fun examNudge(level: Int = 1) = HomeNudge(
        id = "level_review:$level", kind = "level_review", title = "Level $level review is open",
        ctaLabel = "Start review", route = "level_exam", params = buildJsonObject { put("levelNumber", level) },
    )

    private fun pathway(available: Boolean?) = PathwaySummary(
        currentLevel = 1,
        levels = listOf(
            PathwayLevel(1, "Foundations of Faith", totalModules = 20, completedModules = 20, status = LevelStatus.COMPLETED, examAvailable = available),
            PathwayLevel(2, "Inner Transformation", status = LevelStatus.LOCKED),
        ),
    )

    @Test fun `the exam's nudge shows only while that exam can be taken`() {
        assertTrue(nudgeOffered(examNudge(), pathway(available = true)))
        // Published with nothing to ask: the exam would only refuse.
        assertFalse(nudgeOffered(examNudge(), pathway(available = false)))
        // An older server says nothing: the nudge stands, as before.
        assertTrue(nudgeOffered(examNudge(), pathway(available = null)))
        // No pathway read: the server sent the nudge — it stands.
        assertTrue(nudgeOffered(examNudge(), null))
        // Its kind alone names it too.
        assertFalse(nudgeOffered(examNudge().copy(route = ""), pathway(available = false)))
    }

    @Test fun `every other nudge is unaffected`() {
        val reflection = HomeNudge(id = "reflection_due", kind = "reflection_due", route = "devotional")
        assertTrue(nudgeOffered(reflection, pathway(available = false)))
    }

    // ── the featured event, never twice on one screen (§7.2 #9) ──

    private val ann = FeaturedAnnouncement(announcementId = "a1", title = "Harvest week")
    private val retreat = FeaturedEvent(seriesId = "s-retreat", title = "Leaders' Retreat")
    private fun occ(id: String, series: String) = HomeEventRow(occurrenceId = id, seriesId = series, title = id)

    @Test fun `the featured gathering with its own card never slides in the carousel too`() {
        val events = listOf(occ("retreat-fri", "s-retreat"), occ("youth", "s-youth"), occ("prayer", "s-prayer"), occ("choir", "s-choir"))
        val pages = featuredPages(ann, retreat, events, ownCardSeries = retreat.seriesId)
        assertEquals(
            listOf(FeaturedPage.Ann(ann), FeaturedPage.Occ(events[1]), FeaturedPage.Occ(events[2]), FeaturedPage.Occ(events[3])),
            pages,
        )
        assertTrue(pages.none { it is FeaturedPage.Fev })
    }

    @Test fun `without a card of its own, the featured gathering slides as before`() {
        val events = listOf(occ("retreat-fri", "s-retreat"), occ("youth", "s-youth"))
        assertEquals(
            listOf(FeaturedPage.Ann(ann), FeaturedPage.Fev(retreat), FeaturedPage.Occ(events[1])),
            featuredPages(ann, retreat, events, ownCardSeries = null),
        )
        // No featured gathering at all: the announcement and the next three events.
        val four = (1..4).map { occ("e$it", "s$it") }
        assertEquals(listOf(FeaturedPage.Ann(ann)) + four.take(3).map { FeaturedPage.Occ(it) }, featuredPages(ann, null, four, ownCardSeries = null))
    }
}

class FeaturedUpcomingTest {
    private val ev = org.nuruplace.member.data.net.FeaturedEvent(seriesId = "s", title = "Pathway Discipleship Classes", dtstartLocal = "2026-08-30T14:00:00")
    @org.junit.Test fun `a featured gathering shows only while it is ahead (Cycle 3 closing walk B1)`() {
        val oct5 = java.time.LocalDateTime.of(2026, 10, 5, 12, 0)
        org.junit.Assert.assertFalse(featuredIsUpcoming(ev, oct5))
        org.junit.Assert.assertTrue(featuredIsUpcoming(ev.copy(dtstartLocal = "2026-10-11T14:00:00"), oct5))
        org.junit.Assert.assertFalse(featuredIsUpcoming(ev.copy(dtstartLocal = ""), oct5))
    }
}
