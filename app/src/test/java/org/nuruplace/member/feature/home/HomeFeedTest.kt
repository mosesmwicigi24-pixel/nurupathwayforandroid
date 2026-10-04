// What Home's feed offers (pathway docs/EXPERIENCE.md §7, Cycle 3): an action
// shows only when the server says it can succeed (rule 2).
package org.nuruplace.member.feature.home

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
