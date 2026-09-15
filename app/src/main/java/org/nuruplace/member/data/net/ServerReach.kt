// Whether Nuru Place can be reached right now — one process-wide flag the HTTP
// stack sets and the shell reads. The server has been vanishing for two to four
// hours at a time (pathway docs/DEPLOYMENT.md, incident ledger 2026-09-05→11).
// When a read is answered from its last good copy because the wire failed, the
// shell says so in one line instead of every screen going blank; the first
// response that comes back from the wire clears it.
package org.nuruplace.member.data.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ServerReach {
    /** Wall-clock millis of the first stale serve of the current outage, or
     *  null while the server answers. */
    var staleSince by mutableStateOf<Long?>(null)
        private set

    /** A read was answered from the last good copy because the wire failed. */
    fun servedStale() { if (staleSince == null) staleSince = System.currentTimeMillis() }

    /** A response came back from the wire — the server is reachable again. */
    fun reached() { if (staleSince != null) staleSince = null }
}
