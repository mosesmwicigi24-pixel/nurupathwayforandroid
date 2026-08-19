// The scanner must ignore every QR that isn't a Nuru service code — a member
// pointing the camera at a random poster should keep scanning, not post junk to
// the server. Mirrors the backend's parseServiceQrPayload.
package org.nuruplace.member.feature.attendance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.nuruplace.member.data.net.AttendanceStreak

class ServiceQrParseTest {

    @Test
    fun `parses a service payload`() {
        val scan = parseServiceQr("nuru-service:11111111-1111-4111-8111-111111111111:abc123")
        assertEquals("11111111-1111-4111-8111-111111111111", scan?.serviceId)
        assertEquals("abc123", scan?.scanToken)
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertEquals("s1", parseServiceQr("  nuru-service:s1:tok \n")?.serviceId)
    }

    @Test
    fun `rejects codes that are not ours`() {
        assertNull(parseServiceQr("https://example.com/checkin"))
        assertNull(parseServiceQr("nuru-event:s1:tok"))
        assertNull(parseServiceQr("nuru-service:s1"))
        assertNull(parseServiceQr("nuru-service:s1:tok:extra"))
        assertNull(parseServiceQr(""))
    }

    @Test
    fun `rejects a payload with an empty id or token`() {
        assertNull(parseServiceQr("nuru-service::tok"))
        assertNull(parseServiceQr("nuru-service:s1:"))
    }

    @Test
    fun `streak note speaks plainly for each status`() {
        assertEquals(
            "This is your first check-in. Your streak starts here.",
            streakNote(AttendanceStreak(status = "new")),
        )
        assertEquals(
            "You've been here 4 services in a row.",
            streakNote(AttendanceStreak(status = "active", currentStreak = 4)),
        )
        assertEquals(
            "You're on the board — one service in a row.",
            streakNote(AttendanceStreak(status = "active", currentStreak = 1)),
        )
        assertEquals(
            "You missed the last service. Come this week and your streak restarts.",
            streakNote(AttendanceStreak(status = "at_risk", currentMissRun = 1)),
        )
        assertEquals(
            "You've missed 3 services in a row. Today is a good day to come back.",
            streakNote(AttendanceStreak(status = "broken", currentMissRun = 3)),
        )
    }

    @Test
    fun `short time pulls HH mm out of an ISO instant`() {
        assertEquals("09:14", shortTime("2026-03-01T09:14:22.000Z"))
        assertEquals("not-a-date", shortTime("not-a-date"))
    }
}
