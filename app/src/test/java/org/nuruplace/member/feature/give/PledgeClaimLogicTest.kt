// "I paid another way" (Giving Cycle 5) — the claim form's rules, pinned
// against what the server takes (partners.ts createClaim): the amount in the
// PLEDGE's currency (whole shillings, or dollars and cents), paid today or
// within the last year on the Nairobi calendar, a note of at most 300
// characters; and the three words a claim's state is said in. Also the
// pledge flow's "First collection: 5 October".
package org.nuruplace.member.feature.give

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.data.net.ClaimBody
import org.nuruplace.member.data.net.PledgeClaim
import java.time.Instant
import java.time.LocalDate

class PledgeClaimLogicTest {
    private val today = LocalDate.of(2026, 9, 28)

    private fun ready(plan: ClaimPlan): ClaimBody = (plan as ClaimPlan.Ready).body
    private fun refused(plan: ClaimPlan): String = (plan as ClaimPlan.Invalid).message

    // ── the day it was paid: today back a year, Nairobi ──

    @Test
    fun `a payment can be told from today back a year, and no further either way`() {
        assertTrue(claimDateAllowed(today, today))
        assertTrue(claimDateAllowed(today.minusDays(365), today))
        assertFalse(claimDateAllowed(today.minusDays(366), today))
        assertFalse(claimDateAllowed(today.plusDays(1), today))
        assertEquals(LocalDate.of(2025, 9, 28)..today, claimDateRange(today))
        // Across a leap day the bound is still 365 days back.
        assertEquals(LocalDate.of(2027, 3, 1), claimDateRange(LocalDate.of(2028, 2, 29)).start)
    }

    @Test
    fun `today is the church's — 21 30 UTC is already tomorrow in Nairobi`() {
        val nairobiToday = partnerToday(Instant.parse("2026-09-27T21:30:00Z"))
        assertEquals(LocalDate.of(2026, 9, 28), nairobiToday)
        // A payment made "today" in Nairobi is allowed even though UTC is still yesterday.
        assertTrue(claimDateAllowed(LocalDate.of(2026, 9, 28), nairobiToday))
        assertEquals(LocalDate.of(2026, 9, 27), partnerToday(Instant.parse("2026-09-27T20:59:59Z")))
    }

    @Test
    fun `a day outside the year is refused before anything is sent`() {
        assertEquals(
            "Choose the day you paid — today or within the last year.",
            refused(planClaim("3000", "KES", today.plusDays(1), "", today)),
        )
        assertEquals(
            "Choose the day you paid — today or within the last year.",
            refused(planClaim("3000", "KES", today.minusDays(400), "", today)),
        )
        assertEquals("2025-09-28", ready(planClaim("3000", "KES", today.minusDays(365), "", today)).paidOn)
    }

    // ── the amount, in the pledge's currency ──

    @Test
    fun `a shilling pledge is told in whole shillings`() {
        assertEquals(300_000, ready(planClaim("3000", "KES", today, "", today)).amountMinor)
        assertEquals(300_000, ready(planClaim(" 3,000 ", "KES", today, "", today)).amountMinor)
        assertEquals("Whole shillings only — no cents.", refused(planClaim("3000.50", "KES", today, "", today)))
        assertEquals("Enter the amount you paid.", refused(planClaim("", "KES", today, "", today)))
        assertEquals("Enter the amount you paid.", refused(planClaim("0", "KES", today, "", today)))
        assertEquals("Enter the amount you paid.", refused(planClaim("-5", "KES", today, "", today)))
        assertEquals("Enter the amount you paid.", refused(planClaim("3k", "KES", today, "", today)))
        assertEquals("That's more than one payment can be.", refused(planClaim("10000001", "KES", today, "", today)))
    }

    @Test
    fun `a dollar pledge is told in dollars, cents allowed`() {
        assertEquals(2_550, ready(planClaim("25.50", "USD", today, "", today)).amountMinor)
        assertEquals(2_550, ready(planClaim("25.5", "usd", today, "", today)).amountMinor)
        assertEquals(2_500, ready(planClaim("25", "USD", today, "", today)).amountMinor)
        assertEquals("Enter the amount in US dollars, like 25.50.", refused(planClaim("25.555", "USD", today, "", today)))
        assertEquals("Enter the amount you paid.", refused(planClaim("0.00", "USD", today, "", today)))
    }

    @Test
    fun `the currency is always the pledge's, never the member's choice`() {
        assertEquals("KES", ready(planClaim("100", "KES", today, "", today)).currency)
        assertEquals("USD", ready(planClaim("100", "usd", today, "", today)).currency)
        // A pledge with no currency on the wire is a shilling pledge.
        assertEquals("KES", ready(planClaim("100", " ", today, "", today)).currency)
    }

    // ── the note ──

    @Test
    fun `a note is optional, trimmed, and at most 300 characters`() {
        assertNull(ready(planClaim("100", "KES", today, "   ", today)).note)
        assertEquals("Paid at the office", ready(planClaim("100", "KES", today, "  Paid at the office  ", today)).note)
        assertEquals(300, ready(planClaim("100", "KES", today, "a".repeat(300), today)).note?.length)
        assertEquals("Keep the note to 300 characters.", refused(planClaim("100", "KES", today, "a".repeat(301), today)))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the claim travels snake_case, and a missing note is left out`() {
        val json = Json { encodeDefaults = true; namingStrategy = JsonNamingStrategy.SnakeCase }
        val bare = json.parseToJsonElement(json.encodeToString(ready(planClaim("3000", "KES", today, "", today)))).jsonObject
        assertEquals(setOf("amount_minor", "currency", "paid_on"), bare.keys)
        assertEquals("300000", bare["amount_minor"].toString())
        assertEquals("\"2026-09-28\"", bare["paid_on"].toString())
        val noted = json.parseToJsonElement(json.encodeToString(ready(planClaim("3000", "KES", today, "Bank", today)))).jsonObject
        assertEquals("\"Bank\"", noted["note"].toString())
    }

    // ── where a claim stands ──

    @Test
    fun `a claim is said to be checking, recorded, or unmatched`() {
        assertEquals("The office is checking it", claimStatusLine("pending"))
        assertEquals("Recorded — thank you", claimStatusLine("confirmed"))
        assertEquals("The office couldn't match it", claimStatusLine("rejected"))
        // Anything the app does not know yet reads as still being checked.
        assertEquals("The office is checking it", claimStatusLine(null))
        assertEquals("The office is checking it", claimStatusLine("reviewing"))
        assertEquals(ClaimTone.Recorded, claimTone(" Confirmed "))
        assertEquals(ClaimTone.Unmatched, claimTone("REJECTED"))
        assertEquals(ClaimTone.Waiting, claimTone("pending"))
    }

    @Test
    fun `a claim row says the amount and the day it was paid`() {
        assertEquals(
            "KSh 3,000 · paid 12 Sep 2026",
            claimRowLine(PledgeClaim(claimId = "c1", amountMinor = 300_000, currency = "KES", paidOn = "2026-09-12")),
        )
        assertEquals(
            "US$ 25.50 · paid 1 Oct 2026",
            claimRowLine(PledgeClaim(claimId = "c2", amountMinor = 2_550, currency = "USD", paidOn = "2026-10-01")),
        )
        assertEquals("KSh 500", claimRowLine(PledgeClaim(claimId = "c3", amountMinor = 50_000, paidOn = null)))
        assertEquals("KSh 500", claimRowLine(PledgeClaim(claimId = "c4", amountMinor = 50_000, paidOn = "garbled")))
    }

    // ── "First collection: 5 October" ──

    @Test
    fun `the first collection is said as a day, the year only when it is not this one`() {
        assertEquals("First collection: 5 October", firstCollectionLine(LocalDate.of(2026, 9, 28), 5))
        assertEquals("5 October", firstCollectionDay(LocalDate.of(2026, 9, 28), 5))
        // Its due day is today: next month, never today.
        assertEquals("First collection: 28 October", firstCollectionLine(LocalDate.of(2026, 9, 28), 28))
        // Day 28 at the month's end.
        assertEquals("First collection: 28 October", firstCollectionLine(LocalDate.of(2026, 9, 30), 28))
        // December rolls into January — and says the year.
        assertEquals("First collection: 5 January 2027", firstCollectionLine(LocalDate.of(2026, 12, 5), 5))
        assertEquals("First collection: 20 December", firstCollectionLine(LocalDate.of(2026, 12, 5), 20))
        assertEquals(LocalDate.of(2027, 1, 5), firstCollectionDate(LocalDate.of(2026, 12, 5), 5))
    }
}
