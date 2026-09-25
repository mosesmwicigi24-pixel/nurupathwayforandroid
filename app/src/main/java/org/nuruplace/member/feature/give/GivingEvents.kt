// Giving freshness — one app-wide "giving changed" signal, and the pure rules
// for when a screen that shows giving money refetches.
//
// The bug this closes (owner, 2026-09-26): a KSh 1,000 pledge payment
// succeeded server-side, but the Partners tab and the Partners statement kept
// showing "KSh 0 paid · 0 of 1 kept · Behind" — both loaded ONCE and never
// again (`if (vm.partnership == null) vm.load()`), and nothing told them a gift
// had happened. The fix is three triggers, all stale-while-revalidate (cached
// numbers stay on screen while the refetch runs, never a spinner over them):
//
//   ENTRY    a screen showing partnership/statement money refetches every time
//            it is shown (Partners segment, Partners statement).
//   RESUME   …and on ON_RESUME while visible — the M-Pesa PIN prompt is its
//            own activity, so coming back from it is a resume.
//   EVENT    [GivingEvents.emit] after anything that changes the money or the
//            standing: a gift intent that comes back succeeded/pending, a
//            PayPal capture, the ceremony's Done, a schedule created or
//            cancelled, a pledge created / edited / paused / cancelled, a
//            partnership joined or resumed. Holders collect it DEBOUNCED
//            (a burst → one reload) — in their ViewModel, so a screen that is
//            not composed (Partners while the Give segment shows the
//            ceremony) is already fresh when it is shown again.
//
// The same shape as PlanProgressBus (feature/grow/PlanReaderKit.kt) — an
// `internal object` over a MutableSharedFlow — rather than a new event bus.
// Entry and resume share a freshness window so one arrival never fetches
// twice (a nav destination's ON_RESUME lands after its enter transition, just
// behind the entry fetch); an event is skipped only when a fetch has already
// STARTED since it was raised (that fetch carries the change).
//
// POLL     a fourth trigger, for PROCESSING rows only: while the Partners
//          statement is in front and shows a pending payment, it refetches
//          every 10 s for at most two minutes, stopping early when no pending
//          row is left, when the page leaves or on ON_PAUSE — so an M-Pesa
//          payment settles on screen without the member doing anything.
package org.nuruplace.member.feature.give

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce

/** How long a burst of giving events must fall quiet before holders reload. */
internal const val GIVING_EVENT_DEBOUNCE_MS = 500L

/** An entry or resume this soon after a fetch STARTED adds nothing. */
internal const val GIVING_REFETCH_FRESH_MS = 2_000L

/** PROCESSING rows: one refetch every 10 s… */
internal const val PENDING_POLL_INTERVAL_MS = 10_000L

/** …for at most two minutes. */
internal const val PENDING_POLL_WINDOW_MS = 120_000L

/** The refetches that fit the window: 12. */
internal const val PENDING_POLL_MAX = (PENDING_POLL_WINDOW_MS / PENDING_POLL_INTERVAL_MS).toInt()

/** Monotonic milliseconds (not wall time — a clock change must not matter).
 *  Plain JVM so the rules below run in unit tests. */
internal fun givingClockMs(): Long = System.nanoTime() / 1_000_000

/** "Something about this member's giving changed — refetch what you show." */
internal object GivingEvents {
    // DROP_OLDEST: every event means the same thing, so a slow collector
    // losing an older one loses nothing, and emit() never fails.
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    /** When the latest event was raised ([givingClockMs]); 0 before any. */
    @Volatile
    var lastChangedAtMs: Long = 0L
        private set

    fun emit() {
        lastChangedAtMs = givingClockMs()
        _changed.tryEmit(Unit)
    }
}

/** The debounced reload trigger every holder collects. */
@OptIn(FlowPreview::class)
internal fun Flow<Unit>.debouncedGivingReloads(): Flow<Unit> = debounce(GIVING_EVENT_DEBOUNCE_MS)

/** ENTRY / RESUME: refetch unless a fetch started within the freshness window. */
internal fun refetchOnEntry(nowMs: Long, lastFetchStartedMs: Long?): Boolean =
    lastFetchStartedMs == null || nowMs - lastFetchStartedMs >= GIVING_REFETCH_FRESH_MS

/** EVENT: refetch unless a fetch has started since the event was raised. */
internal fun refetchAfterGivingEvent(eventAtMs: Long, lastFetchStartedMs: Long?): Boolean =
    lastFetchStartedMs == null || lastFetchStartedMs < eventAtMs

/** Whether a gift intent's status means money is moving — succeeded, or on
 *  its way (processing / requires_action: an M-Pesa / Airtel PIN
 *  outstanding, a card confirming, PayPal awaiting approval) — and the
 *  screens that count it should refetch. A failed or cancelled intent
 *  changed nothing. */
internal fun givingIntentAnnounces(status: String?): Boolean =
    status?.trim()?.lowercase() in setOf("succeeded", "settled", "completed", "pending", "processing", "requires_action")

/** POLL: another refetch for PROCESSING rows only while the page is in front
 *  (RESUMED — ON_PAUSE stops it), still shows a pending row, and has not
 *  used its two minutes. */
internal fun keepPollingPending(visible: Boolean, hasPending: Boolean, pollsDone: Int): Boolean =
    visible && hasPending && pollsDone < PENDING_POLL_MAX

/** The POLL loop, run from a LaunchedEffect keyed on (visible, has pending
 *  rows) so leaving, pausing or a row appearing restarts or cancels it; the
 *  rule is re-read after every wait, so a row that settled meanwhile (an
 *  event reload) costs no extra fetch. `poll` goes through the holder's
 *  latest-request guard, so a slow answer never overwrites a newer one. */
internal suspend fun pollWhilePending(visible: Boolean, hasPending: () -> Boolean, poll: () -> Unit) {
    var polls = 0
    while (keepPollingPending(visible, hasPending(), polls)) {
        delay(PENDING_POLL_INTERVAL_MS)
        if (!keepPollingPending(visible, hasPending(), polls)) return
        poll()
        polls++
    }
}

/** One holder's bookkeeping for the rules above: when its last fetch started.
 *  Main-thread only (ViewModel and composition). */
internal class GivingFreshness {
    private var lastFetchStartedMs: Long? = null

    /** Call as a fetch STARTS — synchronously, before any suspension, so an
     *  event raised just before it is known to be covered. */
    fun fetchStarted(nowMs: Long = givingClockMs()) {
        lastFetchStartedMs = nowMs
    }

    fun shouldRefetchOnEntry(nowMs: Long = givingClockMs()): Boolean = refetchOnEntry(nowMs, lastFetchStartedMs)

    fun shouldRefetchAfterEvent(eventAtMs: Long = GivingEvents.lastChangedAtMs): Boolean =
        refetchAfterGivingEvent(eventAtMs, lastFetchStartedMs)
}
