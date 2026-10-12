// Short relative time for chat/prayer timestamps ("2h", "3d"). minSdk 26 → java.time.
package org.nuruplace.member.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Today's date (yyyy-MM-dd) and a window `days` ahead — for the calendar query. */
fun todayIso(): String = LocalDate.now().toString()
fun isoPlusDays(days: Long): String = LocalDate.now().plusDays(days).toString()

/** An ISO instant as "Sat 5 Jul · 10:00 AM" in the device zone — the one
 *  date form and a 12-hour time (§8.1 rule 8; it read a 24-hour "10:00"). */
fun fmtEventTime(iso: String?): String = NuruDates.dayTime(iso).orEmpty()

fun relTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val d = Duration.between(then, Instant.now())
    val mins = d.toMinutes()
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 60 * 24 -> "${mins / 60}h"
        mins < 60 * 24 * 7 -> "${mins / (60 * 24)}d"
        else -> "${mins / (60 * 24 * 7)}w"
    }
}
