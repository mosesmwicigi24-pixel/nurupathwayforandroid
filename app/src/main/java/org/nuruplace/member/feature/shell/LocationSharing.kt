// Location sharing, server first (EXPERIENCE.md §7.4 — owner decision
// 2026-10-05): the switch moves only once the server has the change. On a
// failure it stays as it was and says why — "Couldn't save that." and §4's
// sentence — so a member never reads "Off" while the server still holds their
// area, or "On" when nothing reached it. Settings' switch and the first-run
// invite both go through here.
package org.nuruplace.member.feature.shell

import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nuruplace.member.data.net.ApiException
import org.nuruplace.member.data.net.LocationBody
import org.nuruplace.member.data.net.Net
import kotlin.coroutines.cancellation.CancellationException

/** A coarse position, for the server's ~1 km area. */
data class CoarseFix(val latitude: Double, val longitude: Double)

/** What became of a change to sharing. */
sealed interface LocationShareResult {
    /** The server has it: sharing is now [sharing]. */
    data class Saved(val sharing: Boolean) : LocationShareResult

    /** Nothing changed on the server; [line] says why. */
    data class Failed(val line: String) : LocationShareResult
}

object LocationSharing {
    /** The phone gave no position (location off, no signal indoors): nothing
     *  was sent, so nothing was saved. */
    const val NO_FIX_LINE = "Couldn't save that. This phone couldn't find its location — try again in a moment."

    /**
     * Turn sharing [want] on or off, server first. Pure over its effects so
     * LocationSharingTest can pin it: [fix] takes one coarse position, [share]
     * and [stop] are the two calls, [failureLine] words a failed call.
     */
    suspend fun change(
        want: Boolean,
        fix: suspend () -> CoarseFix?,
        share: suspend (CoarseFix) -> Unit,
        stop: suspend () -> Unit,
        failureLine: (Throwable) -> String,
    ): LocationShareResult {
        return try {
            if (want) {
                val at = fix() ?: return LocationShareResult.Failed(NO_FIX_LINE)
                share(at)
            } else {
                stop()
            }
            LocationShareResult.Saved(want)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationShareResult.Failed(failureLine(e))
        }
    }

    /** [change] with the phone's location and the real API. */
    suspend fun change(context: Context, want: Boolean): LocationShareResult = change(
        want = want,
        fix = { coarseFix(context) },
        share = { Net.client.api.shareLocation(LocationBody(it.latitude, it.longitude)) },
        stop = { Net.client.api.stopSharingLocation() },
        failureLine = { ApiException.saveFailureLine(it, context) },
    )

    /** One balanced-power position, or null when the phone gives none. The
     *  caller has the coarse-location permission. */
    @Suppress("MissingPermission")
    suspend fun coarseFix(context: Context): CoarseFix? = withContext(Dispatchers.IO) {
        runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            Tasks.await(client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null))
        }.getOrNull()?.let { CoarseFix(it.latitude, it.longitude) }
    }
}
