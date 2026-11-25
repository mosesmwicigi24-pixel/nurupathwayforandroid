// How the reading screens are lit (member suggestion, 2026-11-21: "dark mode
// would be particularly useful for reading at night"). The readers — a plan's
// parts, the devotional, the Sunday letter and a Pathway lesson — draw on a
// ReaderPalette that has a warm day page and a dark sepia night page; this is
// the member's choice between them, made in Settings → Display or with the
// moon/sun button on any reader, and remembered on this phone (AppPrefs).
// Automatic, the default, follows the phone's own dark mode.
package org.nuruplace.member.data

enum class ReadingTheme(val key: String, val label: String) {
    AUTO("auto", "Automatic"),
    DAY("day", "Day"),
    NIGHT("night", "Night");

    fun isNight(systemDark: Boolean): Boolean = when (this) {
        AUTO -> systemDark
        DAY -> false
        NIGHT -> true
    }

    companion object {
        /** An unknown or missing key is Automatic. */
        fun from(key: String?): ReadingTheme = entries.firstOrNull { it.key == key } ?: AUTO

        /** A reader's moon/sun button: the opposite of the page showing now,
         *  chosen explicitly — so tapping it on an Automatic night page gives
         *  Day, and Settings then shows Day. */
        fun toggled(currentlyNight: Boolean): ReadingTheme = if (currentlyNight) DAY else NIGHT

        val current: ReadingTheme get() = from(AppPrefs.readingTheme)

        /** The page to draw right now. Reads Compose state (AppPrefs), so a
         *  composable that reads it redraws the moment either changes. */
        val night: Boolean get() = current.isNight(AppPrefs.systemDark)
    }
}
