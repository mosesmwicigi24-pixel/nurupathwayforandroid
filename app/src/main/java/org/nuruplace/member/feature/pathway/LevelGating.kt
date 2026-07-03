// Hard-lock gating (§1.9) — the Kotlin port of the iOS LevelGating / RN
// levelGating.ts. Pure, so it stays trivially testable. A level above the
// member's current_level is locked; the client must never offer a path into
// higher-level content. The server stays authoritative (the API also refuses),
// so when the server marks a level "locked" we honour that too.
package org.nuruplace.member.feature.pathway

import org.nuruplace.member.data.net.LevelStatus

object LevelGating {
    /** Whether a level should render as a non-tappable, dimmed locked card (§1.9). */
    fun isLevelLocked(levelNumber: Int, currentLevel: Int, serverStatus: LevelStatus?): Boolean {
        if (serverStatus == LevelStatus.LOCKED) return true
        return levelNumber > currentLevel
    }

    /** Member-facing label on a locked level card. */
    fun lockedLevelLabel(currentLevel: Int): String = "Complete Level $currentLevel to unlock"
}
