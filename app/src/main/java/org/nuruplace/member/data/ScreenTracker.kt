// Screen-view dwell telemetry (POST /me/activity/screens) — silent by
// contract, port of the iOS ScreenTracker (RootView.swift ~221). record(screen)
// closes the previous screen's dwell (duration = time since it was entered) and
// starts timing the new one; buffered rows flush fire-and-forget when ~10
// accumulate or the app backgrounds. Analytics must never surface errors or
// block UI — flush failures drop the batch silently (the server is
// best-effort about telemetry too).
package org.nuruplace.member.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ScreenActivityBody
import org.nuruplace.member.data.net.ScreenEvent
import java.time.Instant
import java.util.UUID

object ScreenTracker {
    private data class Current(val screen: String, val since: Long)

    private var current: Current? = null
    private val buffer = mutableListOf<ScreenEvent>()
    private const val FLUSH_AT = 10
    private const val MAX_DWELL_MS = 86_400_000L   // the server clamps at 24h
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The member is now on [screen] (a route/tab name). Closes the previous
     *  dwell into the buffer and starts the new one. */
    @Synchronized
    fun record(screen: String) {
        val now = System.currentTimeMillis()
        closeCurrent(now)
        current = Current(screen.take(40), now)
        if (buffer.size >= FLUSH_AT) flush()
    }

    /** Call when the app backgrounds: close the open dwell and flush what we have. */
    @Synchronized
    fun appDidEnterBackground() {
        closeCurrent(System.currentTimeMillis())
        flush()
    }

    private fun closeCurrent(now: Long) {
        val c = current ?: return
        current = null
        val ms = now - c.since
        if (ms <= 0) return
        buffer.add(
            ScreenEvent(
                screen = c.screen,
                durationMs = ms.coerceAtMost(MAX_DWELL_MS).toInt(),
                occurredAt = Instant.ofEpochMilli(now).toString(),
                clientEventId = UUID.randomUUID().toString().lowercase(),
            ),
        )
    }

    /** Fire-and-forget batch post. Silent by contract — no thrown errors, no
     *  retries, no UI. The server accepts up to 200 rows per batch. */
    private fun flush() {
        if (buffer.isEmpty()) return
        val batch = buffer.take(200)
        repeat(batch.size) { buffer.removeAt(0) }
        scope.launch {
            runCatching { Net.client.api.screenActivity(ScreenActivityBody(batch)) }
        }
    }
}
