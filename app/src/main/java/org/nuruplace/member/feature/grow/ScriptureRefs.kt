// Scripture woven into the plans — the passages themselves, not just their
// references (owner ask, 2026-09-04; iOS ScripturePassages.swift parity).
// This file is the pure half: finding citations in prose, splitting an
// authored Go Deeper line, normalising to the form GET /scripture accepts, and
// a session cache over that endpoint. The reader blocks live in PlanReaderKit.
package org.nuruplace.member.feature.grow

import org.nuruplace.member.data.net.Net
import org.nuruplace.member.data.net.ScripturePassage
import java.util.concurrent.ConcurrentHashMap

object ScriptureRefs {
    /** The 66 books plus the common alternates. Longest names go first in the
     *  pattern so "1 John 4:8" is never read as "John 4:8". */
    val books: List<String> = listOf(
        "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth",
        "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles", "Ezra",
        "Nehemiah", "Esther", "Job", "Psalms", "Psalm", "Proverbs", "Ecclesiastes",
        "Song of Songs", "Song of Solomon", "Isaiah", "Jeremiah", "Lamentations", "Ezekiel",
        "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk",
        "Zephaniah", "Haggai", "Zechariah", "Malachi",
        "Matthew", "Mark", "Luke", "John", "Acts", "Romans", "1 Corinthians", "2 Corinthians",
        "Galatians", "Ephesians", "Philippians", "Colossians", "1 Thessalonians",
        "2 Thessalonians", "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews", "James",
        "1 Peter", "2 Peter", "1 John", "2 John", "3 John", "Jude", "Revelation",
    )

    /** "Book C:V", "Book C:V-V" or "Book C:V-C:V"; en and em dashes tolerated. */
    val pattern: Regex by lazy {
        val names = books.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        Regex("""\b($names)\s+(\d{1,3}):(\d{1,3})(?:\s?[-–—]\s?(\d{1,3})(?::(\d{1,3}))?)?\b""")
    }

    data class Match(val range: IntRange, val reference: String)

    /** Every reference cited in a run of prose, in order. */
    fun detect(text: String): List<Match> =
        pattern.findAll(text).map { Match(it.range, normalize(it.value)) }.toList()

    /** True when the whole string is one reference and nothing else. */
    fun isReference(s: String): Boolean {
        val t = s.trim()
        val m = detect(t)
        return m.size == 1 && m[0].range.first == 0 && m[0].range.last == t.length - 1
    }

    /** "Proverbs 13:4; James 1:22–25" → ["Proverbs 13:4", "James 1:22-25"].
     *  A comma splits only when BOTH sides name a book — "Genesis 1:1, 3" stays
     *  one line. Pieces that are not references come back untouched, so an
     *  authored note ("Read the whole chapter") still renders. */
    fun split(refs: String): List<String> =
        refs.split(';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            .flatMap { piece ->
                val parts = piece.split(',').map { it.trim() }
                if (parts.size > 1 && parts.all(::isReference)) parts else listOf(piece)
            }
            .map { if (isReference(it)) normalize(it) else it }

    /** The form /scripture accepts: "Book C:V-V" — plain hyphen, single spaces. */
    fun normalize(ref: String): String = ref.trim()
        .replace('–', '-').replace('—', '-')
        .replace(Regex("""\s*-\s*"""), "-")
        .replace(Regex("""\s+"""), " ")

    /** How many verses a reference spans, when that can be read off it: a
     *  single verse is 1, "James 1:22-25" is 4. A cross-chapter span
     *  ("John 3:16-4:2") is not counted here and comes back null. */
    fun verseCount(ref: String): Int? {
        val m = pattern.find(normalize(ref)) ?: return null
        if (m.groups[5] != null) return null
        val start = m.groups[3]?.value?.toIntOrNull() ?: return null
        val end = m.groups[4]?.value?.toIntOrNull() ?: return 1
        return if (end >= start) end - start + 1 else null
    }

    /** Short passages open without a tap — anything under five verses. */
    fun opensByDefault(ref: String): Boolean = verseCount(ref)?.let { it <= 4 } ?: false

    /** "James 1:22-25" → "James 1": the prefix a single verse's own reference
     *  is built from ("James 1:23") when it is saved on its own. */
    fun chapterPrefix(ref: String): String? {
        val m = pattern.find(normalize(ref)) ?: return null
        return "${m.groupValues[1]} ${m.groupValues[2]}"
    }

    /** CANDIDATE verse numbers YouVersion leaves inline ("22 Do not merely
     *  listen… 23 Anyone… 24 and, after looking…"): a 1–3 digit token followed
     *  by a space and a word or an opening quote. Deliberately loose (a verse
     *  can begin lowercase); [passageVerses] keeps only the ones that run
     *  consecutively, which is what rejects "430 years" inside a verse. */
    val verseNumber: Regex by lazy { Regex("""(?<=^|\s)(\d{1,3})(?=\s[A-Za-z“"'(\[])""") }

    /** The verse a reference starts at ("James 1:22-25" → 22) — the number
     *  the first verse of its passage text carries. */
    fun startVerse(ref: String): Int? = pattern.find(normalize(ref))?.groups?.get(3)?.value?.toIntOrNull()
}

/** Session cache over GET /scripture, on top of the server's month-long one. */
object ScriptureStore {
    private val cache = ConcurrentHashMap<String, ScripturePassage>()

    suspend fun passage(ref: String): Result<ScripturePassage> {
        val key = ScriptureRefs.normalize(ref)
        cache[key]?.let { return Result.success(it) }
        return runCatching { Net.client.api.scripture(key) }.onSuccess { cache[key] = it }
    }
}
