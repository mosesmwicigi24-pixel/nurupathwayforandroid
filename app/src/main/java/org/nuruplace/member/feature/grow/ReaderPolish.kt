// The reading experience, polished (owner ask, 2026-09-04; iOS ReaderPolish.swift
// parity) — the pure half: an honest read time from the words actually on the
// page (the day list's "~5 min read" was a fixed string), the three text-size
// steps the reader header cycles, and the split of a passage into its verses
// so a long press lands on ONE of them. The composables live in PlanReaderKit.
package org.nuruplace.member.feature.grow

import org.nuruplace.member.data.net.PlanSegment
import org.nuruplace.member.data.net.ReadingPlanDay
import kotlin.math.abs
import kotlin.math.roundToInt

object ReadTime {
    /** Unhurried devotional reading, not skimming. */
    const val WORDS_PER_MINUTE = 200.0

    fun words(text: String?): Int = text?.split(Regex("""\s+"""))?.count { it.isNotEmpty() } ?: 0

    fun minutes(words: Int): Int = maxOf(1, (words / WORDS_PER_MINUTE).roundToInt())

    /** Everything the member will read on the page — media segments carry
     *  keynotes, not the film, so they are left out. */
    fun minutes(segments: List<PlanSegment>): Int =
        minutes(segments.filter { it.kind.lowercase() !in setOf("video", "audio") }.sumOf { words(it.content) })

    fun minutes(day: ReadingPlanDay): Int {
        val segs = day.segments
        return if (!segs.isNullOrEmpty()) minutes(segs) else minutes(words(day.content))
    }
}

object ReaderTextScale {
    val steps = listOf(0.9f, 1.0f, 1.15f)

    /** The step after the current one, wrapping — a value that is not one of
     *  the steps (an old default) moves on from its nearest step. */
    fun next(scale: Float): Float {
        val i = steps.indices.minByOrNull { abs(steps[it] - scale) } ?: 1
        return steps[(i + 1) % steps.size]
    }

    fun label(scale: Float): String = when {
        scale < 0.95f -> "Small"
        scale > 1.05f -> "Large"
        else -> "Regular"
    }
}

/** One verse of a passage: its number (null when the passage has none) and its words. */
data class PassageVerse(val number: String?, val body: String)

/** The passage split at its verse numbers; a passage without numbers is one
 *  verse. Words before the first number (a heading) keep their place.
 *
 *  Verse numbers run consecutively, so a candidate counts only when it is the
 *  next expected one — seeded from [startVerse] (the reference's first verse)
 *  or, failing that, the first candidate. That is what lets "24 and, after
 *  looking…" split while "was 430 years" inside a verse stays prose. */
fun passageVerses(text: String, startVerse: Int? = null): List<PassageVerse> {
    val candidates = ScriptureRefs.verseNumber.findAll(text).toList()
    var expected = startVerse ?: candidates.firstOrNull()?.value?.toIntOrNull()
    val matches = candidates.filter { m ->
        val n = m.value.toIntOrNull()
        if (n != null && n == expected) { expected = n + 1; true } else false
    }
    if (matches.isEmpty()) {
        val t = text.trim()
        return if (t.isEmpty()) emptyList() else listOf(PassageVerse(null, t))
    }
    val out = mutableListOf<PassageVerse>()
    val lead = text.substring(0, matches[0].range.first).trim()
    if (lead.isNotEmpty()) out += PassageVerse(null, lead)
    matches.forEachIndexed { i, m ->
        val from = m.range.last + 1
        val to = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
        out += PassageVerse(m.value, text.substring(from, to).trim())
    }
    return out
}
