// Profile's words (pathway docs/EXPERIENCE.md §8.1 rule 8, §8.2 #8): a date-
// only value is the calendar date the server sent — never shifted by the
// phone's time zone — and reads "1 Jan 1990"; an empty value reads "Not set".
// Seen on Android: "1989-12-31T21:00:00.000Z" printed raw under DATE OF BIRTH
// (the old local API ran in Nairobi time) and "—" for every empty field.
package org.nuruplace.member.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class ProfileValuesTest {
    @Test fun `a birthday reads 1 Jan 1990 — the date sent, as production sends it`() {
        assertEquals("1 Jan 1990", profileDateLabel("1990-01-01T00:00:00.000Z"))
        assertEquals("1 Jan 1990", profileDateLabel("1990-01-01"))
        assertEquals("31 Dec 1989", profileDateLabel("1989-12-31T21:00:00.000Z"))
        assertEquals("9 Sep 2001", profileDateLabel("2001-09-09T00:00:00Z"))
    }

    @Test fun `the phone's time zone never moves a birthday`() {
        val saved = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Los_Angeles", "Africa/Nairobi", "Pacific/Kiritimati", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(zone, "1 Jan 1990", profileDateLabel("1990-01-01T00:00:00.000Z"))
                assertEquals(zone, LocalDate.of(1990, 1, 1), calendarDateOf("1990-01-01T00:00:00.000Z"))
            }
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test fun `no birthday reads Not set, and nonsense is never printed`() {
        assertEquals("Not set", profileDateLabel(null))
        assertEquals("Not set", profileDateLabel(""))
        assertEquals("Not set", profileDateLabel("soon"))
        assertEquals("Not set", profileDateLabel("1990-13-40"))
        assertNull(calendarDateOf("1990-13-40"))
    }

    @Test fun `the edit sheet starts from the form it asks for`() {
        // YYYY-MM-DD — the old prefill was the raw timestamp, which failed
        // the field's own check, so an unchanged birthday could not be saved.
        assertEquals("1990-01-01", profileDateEditValue("1990-01-01T00:00:00.000Z"))
        assertEquals("", profileDateEditValue(null))
    }

    @Test fun `empty values read Not set — never a dash`() {
        assertEquals("Not set", profileValue(null))
        assertEquals("Not set", profileValue("   "))
        assertEquals("Nairobi", profileValue(" Nairobi "))
    }

    @Test fun `a gender reads in words, never as stored`() {
        assertEquals("Prefer not to say", profileGenderLabel("prefer_not_to_say"))
        assertEquals("Female", profileGenderLabel("female"))
        assertEquals("Male", profileGenderLabel("MALE"))
        assertEquals("Not set", profileGenderLabel(null))
        assertEquals("Non binary", profileGenderLabel("non_binary"))
    }

    // ── Whole at the largest text (final walk C3, Android #10) ──

    @org.junit.Test fun `an email address breaks only after its @, never mid-address`() {
        org.junit.Assert.assertEquals("student1@\u200Bdev.local", emailBreaks("student1@dev.local"))
        // Nothing else in it becomes a break, and an address without one is left alone.
        org.junit.Assert.assertEquals("a.b@\u200Bc.d.e", emailBreaks("a.b@c.d.e"))
        org.junit.Assert.assertEquals("not-an-address", emailBreaks("not-an-address"))
    }

    @org.junit.Test fun `a badge's name never breaks at its hyphen — "Seven-Day" holds together`() {
        org.junit.Assert.assertEquals("Seven\u2011Day Faithful", badgeLabel("Seven-Day Faithful"))
        org.junit.Assert.assertEquals("Thirty\u2011Day Faithful", badgeLabel("Thirty-Day Faithful"))
        org.junit.Assert.assertEquals("First Step", badgeLabel("First Step"))
    }
}

class AppVersionLineTest {
    @org.junit.Test fun `Settings says the build this phone runs (B12)`() {
        org.junit.Assert.assertEquals("Nuru Pathway · version 2.59.0 (84)", appVersionLine("2.59.0", 84))
        org.junit.Assert.assertEquals(org.nuruplace.member.BuildConfig.VERSION_NAME.isNotBlank(), true)
    }
}
