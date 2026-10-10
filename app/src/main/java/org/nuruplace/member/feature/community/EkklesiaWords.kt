// Ekklesia's words — the lines the invitations and the Home card say, kept
// pure so a test can read them. The invitation never nags: one line, chosen
// by the day, that names what intercession is; a member sees the watch's
// real count instead of an invitation.
package org.nuruplace.member.feature.community

object EkklesiaWords {
    /** Where every Ekklesia door lands: the Prayer Room on its fifth tab. */
    const val ROUTE = "prayer-room?tab=ekklesia"

    /** The lines a non-member meets, in rotation by the day of the year. */
    val INVITATIONS: List<String> = listOf(
        "Someone is carrying something heavy tonight. Stand with them.",
        "The watch prays the same need again tomorrow. That is what makes it a watch.",
        "Stand in the gap for a few minutes a day. Join Ekklesia.",
        "Before the healing came, somebody prayed. Be that somebody.",
        "Ekklesia is the church on its knees for the church. There is room for you.",
        "A need brought to the watch is never carried alone.",
    )

    /** One invitation for the day; `dayOfYear` keeps it steady all day. */
    fun invitation(dayOfYear: Int): String = INVITATIONS[Math.floorMod(dayOfYear, INVITATIONS.size)]

    /** The line beneath the invitation: who already stands, honestly (no zero counts). */
    fun standing(memberCount: Int): String? = when {
        memberCount <= 0 -> null
        memberCount == 1 -> "1 intercessor already stands."
        else -> "$memberCount intercessors already stand."
    }

    /** What an intercessor sees instead of an invitation. */
    fun memberLine(needsMeToday: Int, urgent: Int): String = when {
        needsMeToday <= 0 -> "You have stood with every need today."
        urgent > 0 && needsMeToday == 1 -> "1 urgent need waits on you today."
        urgent > 0 -> "$needsMeToday needs wait on you today, $urgent urgent."
        needsMeToday == 1 -> "1 need waits on you today."
        else -> "$needsMeToday needs wait on you today."
    }

    /** "12 interceding · 40 prayers · 2 updates" — only the parts that are not zero. */
    fun countsLine(intercessors: Int, prayers: Int, updates: Int): String? {
        val parts = buildList {
            if (intercessors > 0) add(if (intercessors == 1) "1 interceding" else "$intercessors interceding")
            if (prayers > intercessors) add("$prayers prayers")
            if (updates > 0) add(if (updates == 1) "1 update" else "$updates updates")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
