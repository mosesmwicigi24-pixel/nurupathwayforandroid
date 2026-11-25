// Read aloud — the pure half (member suggestion, 2026-11-21: "a voice-reading
// feature that can read the written content aloud … listen to the teachings and
// articles instead of having to read everything"). ReadAloud.kt owns the
// on-device TextToSpeech engine; everything decidable ahead of time — what is
// said, in what order, cut where — lives here so plain JUnit covers it
// (ListenScriptTest), the house rule LiturgySpeech.kt set.
//
// A reading is a list of PARAGRAPHS: the unit the player counts ("3 of 12"),
// skips by, and resumes from after a pause. TextToSpeech has no real
// pause-and-resume mid-sentence, so a paragraph is the honest place to pick up
// again. Each one is also kept well under the engine's input limit
// (TextToSpeech.getMaxSpeechInputLength(), 4000 on every engine we know).
package org.nuruplace.member.feature.listen

import org.nuruplace.member.feature.grow.ScriptureRefs
import org.nuruplace.member.feature.grow.passageVerses

/** Metadata only: selecting a voice must never require a network connection. */
internal data class ReadingVoiceCandidate(
    val name: String,
    val language: String,
    val quality: Int,
    val requiresNetwork: Boolean,
    val installed: Boolean,
)

internal fun selectReadingVoice(voices: List<ReadingVoiceCandidate>, preferredLanguage: String, currentName: String? = null): ReadingVoiceCandidate? =
    voices.asSequence()
        .filter { it.installed && !it.requiresNetwork && it.language.substringBefore('-').equals("en", ignoreCase = true) }
        .sortedWith(compareByDescending<ReadingVoiceCandidate> { it.quality }
            .thenBy { if (it.language.equals(preferredLanguage, ignoreCase = true)) 0 else 1 }
            .thenBy { if (it.name == currentName) 0 else 1 }
            .thenBy { it.name })
        .firstOrNull()

/** One block of a reading, as the screen shows it. */
sealed interface ListenPiece {
    /** A title or section name — read once, with a pause after it. */
    data class Heading(val text: String) : ListenPiece

    /** Authored prose: plain text or the lessons' light markdown. Blank lines
     *  separate paragraphs. */
    data class Prose(val text: String) : ListenPiece

    /** A Bible passage. Its reference is read first ("Romans 8, verses 28 to
     *  30"), then the verses WITHOUT their inline numbers — "22 Do not merely
     *  listen 23 Anyone who…" read aloud is a list of numbers. */
    data class Scripture(val text: String, val reference: String? = null, val startVerse: Int? = null) : ListenPiece
}

object ListenScript {
    /** Comfortably under every engine's input limit, and short enough that a
     *  skip or a resume never repeats more than a few sentences. */
    const val MAX_CHARS = 1200

    /** The paragraphs read aloud for [pieces], in order. Empty when there is
     *  nothing to say — the screen then offers no Listen control at all. */
    fun paragraphs(pieces: List<ListenPiece>, maxChars: Int = MAX_CHARS): List<String> =
        pieces.flatMap { piece ->
            when (piece) {
                is ListenPiece.Heading -> listOfNotNull(speakable(piece.text)?.withStop())
                is ListenPiece.Prose -> prose(piece.text)
                is ListenPiece.Scripture -> scripture(piece)
            }
        }.flatMap { chunk(it, maxChars) }

    private fun prose(text: String): List<String> =
        text.replace("\r\n", "\n").split(Regex("""\n\s*\n"""))
            .mapNotNull { para ->
                // Lines inside one paragraph run together; a list item or a
                // heading line keeps its own pause.
                val lines = para.lines().mapNotNull { line ->
                    val listLike = LIST_MARKER.containsMatchIn(line) || HEADING_MARKER.containsMatchIn(line)
                    speakable(line)?.let { if (listLike) it.withStop() else it }
                }
                lines.joinToString(" ").trim().takeIf { it.isNotEmpty() }
            }

    private fun scripture(p: ListenPiece.Scripture): List<String> {
        val out = mutableListOf<String>()
        p.reference?.trim()?.takeIf { it.isNotEmpty() }?.let { out += "${naturalReference(it)}." }
        val verses = passageVerses(p.text, p.startVerse).map { it.body.trim() }.filter { it.isNotEmpty() }
        if (verses.isNotEmpty()) out += collapse(verses.joinToString(" "))
        return out
    }

    /** A line of authored text as it should sound: markdown marks, links and
     *  tags gone, references read as words, whitespace collapsed. Null when
     *  nothing is left (a rule, an image, an empty line). */
    fun speakable(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty() || RULE.matches(s) || TABLE_RULE.matches(s)) return null
        // A table row reads as its cells, one pause between each.
        if (s.startsWith("|")) {
            s = s.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            if (s.isNotEmpty() && s.last() !in ".!?") s += "."
        }
        s = s.replace(IMAGE, "")
            .replace(LINK) { it.groupValues[1] }
            .replace(TAG, " ")
            .replace(HEADING_MARKER, "")
            .replace(QUOTE_MARKER, "")
            .replace(LIST_MARKER, "")
            .replace(BOLD, "$2")
            .replace(ITALIC_STAR, "$1")
            .replace(ITALIC_UNDERSCORE, "$1")
            .replace("`", "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
        s = naturalizeReferences(s)
        return collapse(s).takeIf { it.any(Char::isLetterOrDigit) }
    }

    /** Every Bible reference inside a run of prose, read as words: "as Romans
     *  8:28 says" → "as Romans 8, verse 28 says". Only a real book name
     *  counts (ScriptureRefs.books), so "the 10:30 service" stays as it is. */
    fun naturalizeReferences(text: String): String =
        ScriptureRefs.pattern.replace(text) { m -> naturalReference(m.value) }

    /** "Psalm 23:1" → "Psalm 23, verse 1"; "John 15:1-4" → "John 15, verses 1
     *  to 4"; "John 3:16-4:2" → "John 3, verse 16 to chapter 4, verse 2". Not
     *  a reference → unchanged. */
    fun naturalReference(reference: String): String {
        val m = ScriptureRefs.pattern.matchEntire(ScriptureRefs.normalize(reference)) ?: return reference.trim()
        val book = m.groupValues[1]
        val chapter = m.groupValues[2]
        val verse = m.groupValues[3]
        val second = m.groups[4]?.value
        val secondVerse = m.groups[5]?.value
        return when {
            second == null -> "$book $chapter, verse $verse"
            secondVerse != null -> "$book $chapter, verse $verse to chapter $second, verse $secondVerse"
            else -> "$book $chapter, verses $verse to $second"
        }
    }

    /** [text] cut at sentence ends so no piece is longer than [maxChars]; a
     *  single sentence longer than that is cut at its last space. */
    fun chunk(text: String, maxChars: Int = MAX_CHARS): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val sentences = SENTENCE_END.split(text).map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() { if (current.isNotEmpty()) { out += current.toString().trim(); current.clear() } }
        for (sentence in sentences) {
            var rest = sentence
            while (rest.length > maxChars) {
                flush()
                val cut = rest.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
                out += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (current.isNotEmpty() && current.length + 1 + rest.length > maxChars) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(rest)
        }
        flush()
        return out
    }

    /** A stable key for one reading's words — the player tells "the same
     *  reading, still open" from "a different one" by it. */
    fun key(paragraphs: List<String>): String = paragraphs.joinToString("\u0001").hashCode().toString(16)

    private fun collapse(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun String.withStop(): String = if (isEmpty() || last() in ".!?:;\"”’)") this else "$this."

    private val RULE = Regex("""^([-*_]\s*){3,}$""")
    private val TABLE_RULE = Regex("""^\|?[\s:|-]*-[\s:|-]*$""")
    private val IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val TAG = Regex("""<[^>]+>""")
    private val HEADING_MARKER = Regex("""^\s*#{1,6}\s+""")
    private val QUOTE_MARKER = Regex("""^\s*>\s?""")
    private val LIST_MARKER = Regex("""^\s*(?:[-*+•]|\d{1,3}[.)])\s+""")
    private val BOLD = Regex("""(\*\*|__)(.+?)\1""")
    private val ITALIC_STAR = Regex("""(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])""")
    private val ITALIC_UNDERSCORE = Regex("""(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])""")
    private val SENTENCE_END = Regex("""(?<=[.!?…]["”’)]?)\s+""")
}

/** The listening speeds the player cycles through, slowest first. 1.0 is the
 *  engine's normal pace; the engine is set a little slower underneath it (see
 *  ReadAloud.BASE_RATE), so "1×" reads like a person, not an announcement. */
object ListenSpeed {
    val steps = listOf(0.8f, 1.0f, 1.25f, 1.5f)

    fun next(speed: Float): Float {
        val i = steps.indices.minByOrNull { kotlin.math.abs(steps[it] - speed) } ?: 1
        return steps[(i + 1) % steps.size]
    }

    /** "0.8×", "1×", "1.25×", "1.5×". */
    fun label(speed: Float): String {
        val s = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')
        return "$s×"
    }
}
