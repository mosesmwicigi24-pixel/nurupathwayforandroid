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

    /** Whether the page in front has been given a saved copy since it opened
     *  (EXPERIENCE.md §9.7 M3): only then does the banner say "Showing what
     *  you last saw" — never over skeletons or a spinner still waiting, nor
     *  over a page with nothing saved, whose own card says so. */
    var savedOnThisPage by mutableStateOf(false)
        private set

    /** A read was answered from the last good copy because the wire failed. */
    fun servedStale() {
        if (staleSince == null) staleSince = System.currentTimeMillis()
        savedOnThisPage = true
    }

    /** A response came back from the wire — the server is reachable again. */
    fun reached() { if (staleSince != null) staleSince = null }

    /** A new page came to the front: nothing it shows is a saved copy yet. */
    fun newPage() { savedOnThisPage = false }
}

/** The banner's line (EXPERIENCE.md §4, §9.7 M3): the cause — the phone
 *  offline, or the server away — and "showing what you last saw" only when
 *  the page in front was given a saved copy. */
fun staleBannerLine(phoneOffline: Boolean, savedOnPage: Boolean): String = when {
    phoneOffline && savedOnPage -> StateLanguage.offline(hasSavedCopy = true).sentence
    phoneOffline -> "${StateLanguage.OFFLINE_TITLE}."
    savedOnPage -> "Nuru Place can't be reached right now — showing what you last saw. We'll keep trying."
    else -> "Nuru Place can't be reached right now. We'll keep trying."
}
