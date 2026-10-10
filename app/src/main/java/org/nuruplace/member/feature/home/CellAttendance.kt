// The cell page's attendance words (pathway docs/EXPERIENCE.md §7.4 #16) —
// both apps say these (iOS CellAttendanceWords). `attendance.you` and
// `turnout` count the same real recent meetings (GET /me/cell-summary);
// `attendance.expected` is a scoring baseline (expected check-ins, 8
// everywhere) and is never shown — "0/8 · you, this month" set the member
// against a target no weekly cell meets. Pure, so CellAttendanceTest pins it.
package org.nuruplace.member.feature.home

import org.nuruplace.member.data.net.CellSummary
import kotlin.math.roundToInt

internal object CellAttendanceWords {
    const val NOT_MET = "Your cell hasn't met yet"

    /** "You: 3 of the last 8 meetings" · "You: 1 of 1 meeting". */
    fun you(y: CellSummary.You): String? {
        if (y.meetings <= 0) return null
        val attended = y.attended.coerceIn(0, y.meetings)
        return if (y.meetings == 1) "You: $attended of 1 meeting" else "You: $attended of the last ${y.meetings} meetings"
    }

    /** "The cell: 48% · last 8 meetings" · "The cell: 100% · 1 meeting". */
    fun cell(t: CellSummary.Turnout): String? {
        if (t.meetings <= 0) return null
        val pct = (t.rate * 100).roundToInt().coerceIn(0, 100)
        return "The cell: $pct% · " + if (t.meetings == 1) "1 meeting" else "last ${t.meetings} meetings"
    }

    /** The member's line, then the cell's — or "Your cell hasn't met yet".
     *  A server that predates `you` shows the cell's line alone. */
    fun lines(you: CellSummary.You?, turnout: CellSummary.Turnout?): List<String> =
        listOfNotNull(you?.let(::you), turnout?.let(::cell)).ifEmpty { listOf(NOT_MET) }

    /** Something to count: the lines are figures, not "hasn't met yet". */
    fun hasMet(you: CellSummary.You?, turnout: CellSummary.Turnout?): Boolean =
        (you?.let(::you) ?: turnout?.let(::cell)) != null
}
