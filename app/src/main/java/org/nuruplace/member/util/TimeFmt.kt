// Short relative time for chat/prayer timestamps ("2h", "3d"). minSdk 26 → java.time.
package org.nuruplace.member.util

import java.time.Duration
import java.time.Instant

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
