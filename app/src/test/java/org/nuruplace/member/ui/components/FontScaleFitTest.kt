package org.nuruplace.member.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/**
 * Text at the phone's largest text size (EXPERIENCE.md §9.6 #4): a word is
 * never broken — WholeWordsText steps a text's scale down only until its
 * widest word fits its line. The walk at font scale 2.0 read "Reflectio / n",
 * "Curric / ulum", "Devotio / nal", "Foun…" and "Trans…" (iOS's walk the
 * same: "Foundatio / ns of / Faith").
 */
class FontScaleFitTest {
    @Test fun `a word that fits keeps the member's size`() {
        assertEquals(2f, wholeWordsScale(current = 2f, widestWordPx = 180f, availablePx = 200f), 0f)
    }

    @Test fun `a word wider than its line steps the scale down in proportion`() {
        assertEquals(2f * (200f / 300f) * 0.98f, wholeWordsScale(current = 2f, widestWordPx = 300f, availablePx = 200f), 1e-5f)
    }

    @Test fun `never below the floor, and never above the member's own size`() {
        assertEquals(WHOLE_WORDS_FLOOR, wholeWordsScale(current = 2f, widestWordPx = 1000f, availablePx = 100f), 0f)
        assertEquals(0.8f, wholeWordsScale(current = 0.8f, widestWordPx = 300f, availablePx = 100f), 0f)
    }

    @Test fun `nothing known changes nothing`() {
        assertEquals(2f, wholeWordsScale(current = 2f, widestWordPx = 0f, availablePx = 100f), 0f)
        assertEquals(2f, wholeWordsScale(current = 2f, widestWordPx = 100f, availablePx = 0f), 0f)
        assertEquals(listOf("Inner", "Transformation"), wordsOf("  Inner \n Transformation "))
    }

    // ── Real widths: the app's own font files, measured on the JVM ──────────
    // (No java.awt on Android's unit-test classpath: a word's width is the sum
    // of its glyphs' advances, read from the font's own cmap and hmtx tables.
    // Kerning is left out, which can only overstate a width.)

    /** A TrueType font's advance widths, by character. */
    private class Advances(file: File) {
        private val b: ByteBuffer = ByteBuffer.wrap(file.readBytes())
        private val tables: Map<String, Int> = (0 until u16(4)).associate { i ->
            val rec = 12 + i * 16
            String(ByteArray(4) { b.get(rec + it) }, Charsets.US_ASCII) to b.getInt(rec + 8)
        }
        private val unitsPerEm = u16(tables.getValue("head") + 18)
        private val advances: IntArray = run {
            val n = u16(tables.getValue("hhea") + 34)
            val hmtx = tables.getValue("hmtx")
            IntArray(n) { u16(hmtx + it * 4) }
        }
        /** The format-4 (BMP) subtable of the Windows Unicode cmap. */
        private val cmap4: Int = run {
            val cmap = tables.getValue("cmap")
            (0 until u16(cmap + 2)).map { i -> cmap + 4 + i * 8 }
                .first { r -> u16(r) == 3 && u16(r + 2) in setOf(1, 10) && u16(cmap + b.getInt(r + 4)) == 4 }
                .let { r -> cmap + b.getInt(r + 4) }
        }
        private fun u16(at: Int) = b.getShort(at).toInt() and 0xFFFF

        private fun glyph(c: Int): Int {
            val segX2 = u16(cmap4 + 6)
            val ends = cmap4 + 14
            val starts = ends + segX2 + 2
            val deltas = starts + segX2
            val ranges = deltas + segX2
            for (i in 0 until segX2 / 2) {
                if (c > u16(ends + 2 * i)) continue
                val start = u16(starts + 2 * i)
                if (c < start) return 0
                val delta = u16(deltas + 2 * i)
                val range = u16(ranges + 2 * i)
                if (range == 0) return (c + delta) and 0xFFFF
                val g = u16(ranges + 2 * i + range + 2 * (c - start))
                return if (g == 0) 0 else (g + delta) and 0xFFFF
            }
            return 0
        }

        /** The text's width in ems. */
        fun em(text: String): Float =
            text.sumOf { ch -> advances[minOf(glyph(ch.code), advances.size - 1)] }.toFloat() / unitsPerEm
    }

    private companion object {
        /** The emulator's density (420 dpi) — px per dp. */
        const val DENSITY = 2.625f
        const val LARGEST = 2.0f
        fun face(file: String) = Advances(File("src/main/res/font/$file"))
        val interMedium by lazy { face("inter_medium.ttf") }
        val interSemiBold by lazy { face("inter_semibold.ttf") }
        val frauncesMedium by lazy { face("fraunces_medium.ttf") }
        val frauncesSemiBold by lazy { face("fraunces_semibold.ttf") }

        /** Production's six levels (their names and short names, 2026-10-05). */
        val LEVEL_WORDS = listOf(
            "Foundations of Faith", "Foundations", "Inner Transformation", "Transformation",
            "Foundations of Grace & Kingdom Perspective", "Grace", "Life & Power of the Holy Spirit", "Spirit",
            "Kingdom Culture, Leadership & Multiplication", "Leadership", "Level 6",
        ).flatMap(::wordsOf).distinct()
    }

    /** A place a text is set: its face, its size in sp and the width it has (dp, on a 411 dp phone). */
    private data class Site(val name: String, val font: Advances, val sp: Float, val widthDp: Float, val words: List<String>)

    private fun widthPx(word: String, font: Advances, sp: Float, scale: Float): Float = font.em(word) * sp * scale * DENSITY

    private val sites by lazy {
        listOf(
            Site("milestone tile", interSemiBold, 11f, 84f, LEVEL_WORDS),
            Site("Pathway's header title", frauncesSemiBold, 26f, 411f - 2 * 20f, LEVEL_WORDS),
            Site("the level page's hero", frauncesMedium, 28f, 411f - 2 * 20f, LEVEL_WORDS),
            Site("a rhythm tile", interSemiBold, 12f, 100f, listOf("Prayer", "Word", "Reflection")),
            Site("a score bar's label", interMedium, 12f, 84f, listOf("Habits", "Word", "Prayer", "Curriculum", "Attendance")),
            Site("a grow tile", interSemiBold, 14f, 100f, listOf("Devotional", "Hide", "His", "Word", "My", "Prayer", "Room", "Your", "Calling")),
        )
    }

    @Test fun `the font reader reads real widths`() {
        // An em-square's sanity: "i" is narrower than "W" in both faces, and a
        // ten-letter word is a few ems wide.
        assertTrue(interSemiBold.em("i") < interSemiBold.em("W"))
        assertTrue(frauncesSemiBold.em("i") < frauncesSemiBold.em("W"))
        assertTrue(interSemiBold.em("Reflection") in 4f..7f)
    }

    @Test fun `at the largest size every word is set whole in every place it shows`() {
        for (site in sites) {
            val availablePx = site.widthDp * DENSITY
            val widest = site.words.maxOf { widthPx(it, site.font, site.sp, LARGEST) }
            val scale = wholeWordsScale(LARGEST, widest, availablePx)
            // Widths are linear in the scale: the widest word, set at the stepped scale.
            val setPx = site.words.maxOf { widthPx(it, site.font, site.sp, scale) }
            assertTrue(
                "${site.name}: the widest word is ${setPx.toInt()} px at scale $scale, wider than ${availablePx.toInt()} px",
                setPx <= availablePx,
            )
        }
    }

    @Test fun `the check has teeth — at the largest size these words are wider than their places`() {
        val tile = sites.first { it.name == "milestone tile" }
        assertTrue(widthPx("Transformation", tile.font, tile.sp, LARGEST) > tile.widthDp * DENSITY)
        val bar = sites.first { it.name == "a score bar's label" }
        assertTrue(widthPx("Curriculum", bar.font, bar.sp, LARGEST) > bar.widthDp * DENSITY)
        val hero = sites.first { it.name == "the level page's hero" }
        assertTrue(widthPx("Transformation", hero.font, hero.sp, LARGEST) > hero.widthDp * DENSITY)
    }
}
