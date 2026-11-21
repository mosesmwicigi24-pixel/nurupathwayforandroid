package org.nuruplace.member.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reading theme (member suggestion, 2026-11-21): how the readers are lit,
 *  and the Read aloud speed — both this phone's, both kept across restarts. */
class ReadingThemeTest {
    @Test fun `automatic follows the phone, day and night do not`() {
        assertTrue(ReadingTheme.AUTO.isNight(systemDark = true))
        assertFalse(ReadingTheme.AUTO.isNight(systemDark = false))
        assertFalse(ReadingTheme.DAY.isNight(systemDark = true))
        assertTrue(ReadingTheme.NIGHT.isNight(systemDark = false))
    }

    @Test fun `an unknown or missing key is automatic`() {
        assertEquals(ReadingTheme.AUTO, ReadingTheme.from(null))
        assertEquals(ReadingTheme.AUTO, ReadingTheme.from("sepia"))
        assertEquals(ReadingTheme.NIGHT, ReadingTheme.from("night"))
    }

    @Test fun `the reader's button chooses the other page explicitly`() {
        assertEquals(ReadingTheme.DAY, ReadingTheme.toggled(currentlyNight = true))
        assertEquals(ReadingTheme.NIGHT, ReadingTheme.toggled(currentlyNight = false))
    }

    @Test fun `the choice and the speed round-trip through the store`() {
        val store = MemoryPrefs()
        AppPrefs.attach(store)
        assertEquals(ReadingTheme.AUTO.key, AppPrefs.readingTheme)
        assertEquals(1.0f, AppPrefs.listenSpeed, 0.0001f)

        AppPrefs.updateReadingTheme(ReadingTheme.NIGHT)
        AppPrefs.updateListenSpeed(1.25f)
        AppPrefs.attach(store) // a restart
        assertEquals(ReadingTheme.NIGHT.key, AppPrefs.readingTheme)
        assertEquals(1.25f, AppPrefs.listenSpeed, 0.0001f)
        assertTrue(ReadingTheme.night)
    }

    @Test fun `automatic resolves against the phone's dark mode`() {
        AppPrefs.attach(MemoryPrefs())
        AppPrefs.systemDark = true
        assertTrue(ReadingTheme.night)
        AppPrefs.systemDark = false
        assertFalse(ReadingTheme.night)
    }
}
