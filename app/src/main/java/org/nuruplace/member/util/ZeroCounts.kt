// No zero counts (pathway docs/EXPERIENCE.md §7.4 #9): a summary says only the
// counts that are above zero, and a summary with none says nothing — "0
// updates across 0 spaces" told a member nothing. Pure, so ZeroCountsTest pins
// each line; iOS's ZeroCounts says the same words.
package org.nuruplace.member.util

object ZeroCounts {
    /** "3 updates", "1 update" — null at zero. */
    fun count(n: Int, one: String, many: String): String? = if (n > 0) "$n ${if (n == 1) one else many}" else null

    /** Community's assistant card: "The AI assistant · 3 updates across 2
     *  spaces" — the role alone when nothing is waiting. */
    fun assistantLine(unread: Int, spaces: Int): String {
        val updates = count(unread, "update", "updates") ?: return "The AI assistant"
        val across = count(spaces, "space", "spaces") ?: return "The AI assistant · $updates"
        return "The AI assistant · $updates across $across"
    }
}
