// Giving freshness (owner, 2026-09-26: a paid pledge stayed "KSh 0 paid ·
// Behind" on Partners): the rules for when a screen refetches — entry/resume
// within a freshness window, an event only when no fetch has started since —
// the debounced trigger every holder collects, which intent statuses raise
// the event, and the singleton itself.
package org.nuruplace.member.feature.give

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GivingEventsTest {
    @Test
    fun `entry and resume refetch unless a fetch started inside the freshness window`() {
        assertTrue(refetchOnEntry(nowMs = 10_000, lastFetchStartedMs = null))
        // The ON_RESUME that lands just behind the entry fetch adds nothing.
        assertFalse(refetchOnEntry(nowMs = 10_000, lastFetchStartedMs = 10_000 - GIVING_REFETCH_FRESH_MS + 1))
        assertTrue(refetchOnEntry(nowMs = 10_000, lastFetchStartedMs = 10_000 - GIVING_REFETCH_FRESH_MS))
        assertTrue(refetchOnEntry(nowMs = 60_000, lastFetchStartedMs = 1_000))
    }

    @Test
    fun `an event refetches unless a fetch has already started since it was raised`() {
        assertTrue(refetchAfterGivingEvent(eventAtMs = 5_000, lastFetchStartedMs = null))
        // The fetch on screen began before the gift — it cannot carry it.
        assertTrue(refetchAfterGivingEvent(eventAtMs = 5_000, lastFetchStartedMs = 4_999))
        // A pledge edit emits, then loads: that load carries the change.
        assertFalse(refetchAfterGivingEvent(eventAtMs = 5_000, lastFetchStartedMs = 5_000))
        assertFalse(refetchAfterGivingEvent(eventAtMs = 5_000, lastFetchStartedMs = 5_200))
    }

    @Test
    fun `a holder's freshness follows its own fetches`() {
        val f = GivingFreshness()
        assertTrue(f.shouldRefetchOnEntry(nowMs = 1_000))
        assertTrue(f.shouldRefetchAfterEvent(eventAtMs = 1_000))
        f.fetchStarted(nowMs = 1_000)
        assertFalse(f.shouldRefetchOnEntry(nowMs = 1_500))
        assertTrue(f.shouldRefetchOnEntry(nowMs = 1_000 + GIVING_REFETCH_FRESH_MS))
        assertFalse(f.shouldRefetchAfterEvent(eventAtMs = 900))
        assertTrue(f.shouldRefetchAfterEvent(eventAtMs = 1_001))
    }

    @Test
    fun `succeeded and pending intents raise the event, failed ones do not`() {
        listOf("succeeded", "settled", "completed", "pending", "processing", "requires_action", " Pending ").forEach {
            assertTrue(it, givingIntentAnnounces(it))
        }
        listOf("failed", "cancelled", "refunded", "", null).forEach {
            assertFalse("$it", givingIntentAnnounces(it))
        }
    }

    @Test
    fun `a burst of giving events reloads once, after the quiet period`() = runTest {
        val events = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
        var reloads = 0
        backgroundScope.launch { events.debouncedGivingReloads().collect { reloads++ } }
        runCurrent()

        // Intent created, then Done, then a pledge edit — within the window.
        events.tryEmit(Unit); advanceTimeBy(100)
        events.tryEmit(Unit); advanceTimeBy(100)
        events.tryEmit(Unit)
        advanceTimeBy(GIVING_EVENT_DEBOUNCE_MS - 1); runCurrent()
        assertEquals(0, reloads)
        advanceTimeBy(2); runCurrent()
        assertEquals(1, reloads)

        // A later, separate change is its own reload.
        events.tryEmit(Unit)
        advanceTimeBy(GIVING_EVENT_DEBOUNCE_MS + 1); runCurrent()
        assertEquals(2, reloads)
    }

    @Test
    fun `GivingEvents stamps the change and reaches a subscriber`() = runTest {
        var received = 0
        backgroundScope.launch { GivingEvents.changed.collect { received++ } }
        runCurrent()
        val before = givingClockMs()
        GivingEvents.emit()
        runCurrent()
        assertEquals(1, received)
        assertTrue(GivingEvents.lastChangedAtMs >= before)
    }

    // ── POLL: PROCESSING rows resolve by themselves ──

    @Test
    fun `polling runs only while visible and pending, and for at most two minutes`() {
        assertEquals(10_000L, PENDING_POLL_INTERVAL_MS)
        assertEquals(12, PENDING_POLL_MAX) // 12 × 10 s = two minutes
        assertTrue(keepPollingPending(visible = true, hasPending = true, pollsDone = 0))
        assertTrue(keepPollingPending(visible = true, hasPending = true, pollsDone = PENDING_POLL_MAX - 1))
        // Two minutes used up.
        assertFalse(keepPollingPending(visible = true, hasPending = true, pollsDone = PENDING_POLL_MAX))
        // Paused (the PIN prompt) or gone.
        assertFalse(keepPollingPending(visible = false, hasPending = true, pollsDone = 0))
        // Nothing left processing.
        assertFalse(keepPollingPending(visible = true, hasPending = false, pollsDone = 0))
    }

    @Test
    fun `a payment that stays pending is polled every 10 s, twelve times, then left alone`() = runTest {
        val at = mutableListOf<Long>()
        backgroundScope.launch { pollWhilePending(visible = true, hasPending = { true }, poll = { at += currentTime }) }
        advanceTimeBy(PENDING_POLL_INTERVAL_MS - 1); runCurrent()
        assertTrue(at.isEmpty()) // the first refetch waits a full interval
        advanceTimeBy(10 * 60_000L); runCurrent()
        assertEquals((1..PENDING_POLL_MAX).map { it * PENDING_POLL_INTERVAL_MS }, at)
    }

    @Test
    fun `polling stops as soon as no pending row is left`() = runTest {
        var pending = true
        var polls = 0
        backgroundScope.launch {
            pollWhilePending(visible = true, hasPending = { pending }, poll = { polls++; if (polls == 3) pending = false })
        }
        advanceTimeBy(10 * 60_000L); runCurrent()
        assertEquals(3, polls)

        // A row that settles DURING a wait (an event reload) costs no extra fetch.
        var pending2 = true
        var polls2 = 0
        backgroundScope.launch { pollWhilePending(visible = true, hasPending = { pending2 }, poll = { polls2++ }) }
        advanceTimeBy(PENDING_POLL_INTERVAL_MS / 2); runCurrent()
        pending2 = false
        advanceTimeBy(10 * 60_000L); runCurrent()
        assertEquals(0, polls2)
    }

    @Test
    fun `polling never starts when paused or with nothing pending, and stops when the page leaves`() = runTest {
        var polls = 0
        backgroundScope.launch { pollWhilePending(visible = false, hasPending = { true }, poll = { polls++ }) }
        backgroundScope.launch { pollWhilePending(visible = true, hasPending = { false }, poll = { polls++ }) }
        advanceTimeBy(10 * 60_000L); runCurrent()
        assertEquals(0, polls)

        // Leaving / ON_PAUSE cancels the LaunchedEffect that runs the loop.
        val job = backgroundScope.launch { pollWhilePending(visible = true, hasPending = { true }, poll = { polls++ }) }
        advanceTimeBy(2 * PENDING_POLL_INTERVAL_MS + 1); runCurrent()
        assertEquals(2, polls)
        job.cancel()
        advanceTimeBy(10 * 60_000L); runCurrent()
        assertEquals(2, polls)
    }
}
