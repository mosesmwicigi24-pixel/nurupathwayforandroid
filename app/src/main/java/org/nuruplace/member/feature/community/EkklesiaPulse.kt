// The watch's pulse — one small summary (GET /ekklesia/summary) shared by the
// Home card and every invitation, so four pages never ask four times. Read
// once per minute at most; any Ekklesia write invalidates it.
package org.nuruplace.member.feature.community

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.nuruplace.member.data.net.EkklesiaSummary
import org.nuruplace.member.data.net.Net

object EkklesiaPulse {
    var summary: EkklesiaSummary? by mutableStateOf(null)
        private set
    private var loadedAt = 0L
    private const val FRESH_MS = 60_000L

    /** Loads when stale; a failure keeps whatever was last known. */
    suspend fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && summary != null && now - loadedAt < FRESH_MS) return
        runCatching { Net.client.api.ekklesiaSummary() }.onSuccess { summary = it; loadedAt = now }
    }

    /** After a join, a leave, a need brought or an intercession. */
    fun invalidate() { loadedAt = 0L }

    /** On sign-out: the next member starts with no one else's watch. */
    fun clear() { summary = null; loadedAt = 0L }
}
