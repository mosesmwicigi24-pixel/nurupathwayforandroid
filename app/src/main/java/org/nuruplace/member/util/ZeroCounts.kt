// No zero counts (pathway docs/EXPERIENCE.md §7.4 #9): a summary says only the
// counts that are above zero, and a summary with none says nothing — "0
// updates across 0 spaces" told a member nothing. Pure, so ZeroCountsTest pins
// each line; iOS's ZeroCounts says the same words. Progress readouts ("0 of 3
// parts read", "0 of 10 modules") are progress, not summaries, and keep their 0.
package org.nuruplace.member.util

object ZeroCounts {
    /** "3 updates", "1 update" — null at zero. */
    fun count(n: Int, one: String, many: String): String? = if (n > 0) "$n ${if (n == 1) one else many}" else null

    /** The parts that are there, joined " · " — "" when none are. */
    fun join(vararg parts: String?): String = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")

    /** Community's assistant card: "The AI assistant · 3 updates across 2
     *  spaces" — the role alone when nothing is waiting. */
    fun assistantLine(unread: Int, spaces: Int): String {
        val updates = count(unread, "update", "updates") ?: return "The AI assistant"
        val across = count(spaces, "space", "spaces") ?: return "The AI assistant · $updates"
        return "The AI assistant · $updates across $across"
    }

    /** Home's prayer card pill: "4 praying · 2 replies" — null (no pill) when
     *  no one has prayed or replied yet. */
    fun prayerLine(praying: Int, replies: Int): String? =
        join(count(praying, "praying", "praying"), count(replies, "reply", "replies")).ifEmpty { null }

    /** The calendar's header: "12 upcoming · October 2026", or the month alone. */
    fun calendarHeader(upcoming: Int, month: String): String = join(count(upcoming, "upcoming", "upcoming"), month)

    /** A label with its count: "RAISED HANDS · 3", "Answered (2)" style
     *  callers pick the shape — this gives the label alone at zero. */
    fun labelled(label: String, n: Int, shape: (String, Int) -> String = { l, c -> "$l · $c" }): String =
        if (n > 0) shape(label, n) else label
}
