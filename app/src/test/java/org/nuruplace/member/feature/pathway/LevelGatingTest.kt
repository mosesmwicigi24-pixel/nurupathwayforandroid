package org.nuruplace.member.feature.pathway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.LevelStatus

class LevelGatingTest {
    @Test fun levelAboveCurrentIsLocked() {
        assertTrue(LevelGating.isLevelLocked(levelNumber = 3, currentLevel = 2, serverStatus = null))
    }

    @Test fun currentLevelIsUnlocked() {
        assertFalse(LevelGating.isLevelLocked(levelNumber = 2, currentLevel = 2, serverStatus = LevelStatus.ACTIVE))
    }

    @Test fun pastLevelIsUnlocked() {
        assertFalse(LevelGating.isLevelLocked(levelNumber = 1, currentLevel = 2, serverStatus = LevelStatus.COMPLETED))
    }

    @Test fun serverLockedAlwaysWinsEvenIfReachableByNumber() {
        // §1.9: honour a server "locked" even for a level <= current.
        assertTrue(LevelGating.isLevelLocked(levelNumber = 1, currentLevel = 5, serverStatus = LevelStatus.LOCKED))
    }

    @Test fun lockedLabelNamesTheCurrentLevel() {
        assertEquals("Complete Level 2 to unlock", LevelGating.lockedLevelLabel(currentLevel = 2))
    }
}
