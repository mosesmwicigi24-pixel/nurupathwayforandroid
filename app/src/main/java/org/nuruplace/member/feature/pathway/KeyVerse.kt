// A lesson's KEY VERSE: the verse's words with its reference under them
// (Cycle 3 close walk E22) — the card put the reference itself in quotes
// where the verse belongs ("“James 1:5”"). Pure, so KeyVerseTest pins it.
package org.nuruplace.member.feature.pathway

import org.nuruplace.member.feature.grow.ScriptureRefs
import org.nuruplace.member.feature.grow.passageVerses

/** What a module's key verse holds. */
sealed interface KeyVerseContent {
    /** Only a reference ("James 1:5"): its words are fetched (GET /scripture). */
    data class Fetch(val reference: String) : KeyVerseContent

    /** A whole passage ("John 1:1-18", five verses or more): the reference
     *  stands alone — the reader opens passages that long on a tap, too. */
    data class Passage(val reference: String) : KeyVerseContent

    /** The words themselves, with their reference when the author gave one. */
    data class Words(val text: String, val reference: String?) : KeyVerseContent
}

private val TRAILING_REF = Regex("""\s*[—–-]\s*([1-3]?\s?[A-Z][A-Za-z]+(?:\s+[A-Za-z]+)*\s+\d{1,3}:\d{1,3}(?:\s?[-–]\s?\d{1,3})?)\s*$""")

/** Reads an authored key verse: a bare reference, words with a trailing
 *  "— Book C:V", or words alone. */
fun keyVerseContent(raw: String): KeyVerseContent {
    val t = raw.trim()
    if (ScriptureRefs.isReference(t)) {
        val ref = ScriptureRefs.normalize(t)
        return if (ScriptureRefs.opensByDefault(ref)) KeyVerseContent.Fetch(ref) else KeyVerseContent.Passage(ref)
    }
    val m = TRAILING_REF.find(t)
    if (m != null) {
        val words = t.substring(0, m.range.first).trim().trim('"', '“', '”').trim()
        if (words.isNotEmpty()) return KeyVerseContent.Words(words, m.groupValues[1].trim())
    }
    return KeyVerseContent.Words(t.trim('"', '“', '”').trim(), null)
}

/** A fetched passage as one run of words — the verse numbers YouVersion
 *  leaves inline are dropped. Null when there is nothing to show. */
fun keyVerseWords(passageText: String, reference: String): String? =
    passageVerses(passageText, ScriptureRefs.startVerse(reference))
        .joinToString(" ") { it.body }
        .trim()
        .takeIf { it.isNotEmpty() }
