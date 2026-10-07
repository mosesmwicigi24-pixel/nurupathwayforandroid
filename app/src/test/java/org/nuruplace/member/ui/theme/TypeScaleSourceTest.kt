// EXPERIENCE.md §8.3 — "The scale holds." Every text size the code decides is
// on §8.1 rule 3's scale (11 · 12 · 13 · 14 · 15 · 16 · 18 · 22 · 26 · 28),
// and no text is drawn in a system or default face.
//
// It began as a ratchet on 2026-10-05 at 318 sizes off the scale and 3
// system faces, and fell to zero the same day, area by area. The ceilings
// stay written as numbers so the history reads plainly: they are 0, and any
// size or face off the rule fails the build.
//
// Allowed, and listed (§8.3: "icons and home-screen widgets are allowed and
// listed"):
//   • Icons are Material vectors sized in dp — not text, so not scanned. A
//     glyph that is a picture (Live's double-tap heart, the keepsake's seal)
//     is drawn as an icon; a glyph set in words' type (a quotation mark, an
//     emoji in a badge) keeps to the scale like any text, as on iOS.
//   • The home-screen widgets (widget/): Glance draws in the launcher's own
//     face at its own sizes.
//   • The Selah editor's member-chosen faces (SelahRichEditor.kt): a member's
//     own writing in the face they picked — iOS offers Georgia and Noteworthy.
//   • Bitmaps: the verse share card (VerseTableau.kt) paints Inter and Fraunces
//     in pixels onto a 1080-wide image; the radio's emoji particles are painted
//     glyphs. Neither is laid-out text, so `Paint.textSize` is not scanned.
//   • The editorial Sunday Letter (EditorialLetter.kt; owner, 2026-10-07: the
//     editorial Sunday Letter, board A): a designed page with its own display
//     type — the nameplate 34, the title 30, the salutation 21, the body 17,
//     the pull quote 23, the figures 34, the quote mark 44, the signature 52,
//     the drop cap 58. Those sizes, in that file only; every other size there
//     keeps to the scale, and the list must match what the file uses.
package org.nuruplace.member.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TypeScaleSourceTest {

    private companion object {
        /** Off-scale and computed text sizes: 318 at the start of Cycle 4, now none. */
        const val OFF_SCALE_CEILING = 0

        /** System or default faces used for text: 3 at the start of Cycle 4, now none. */
        const val SYSTEM_FACE_CEILING = 0

        val WIDGETS = listOf("org/nuruplace/member/widget/")

        const val EDITORIAL_LETTER = "org/nuruplace/member/feature/home/EditorialLetter.kt"
        val EDITORIAL_SIZES = setOf(17.0, 21.0, 23.0, 30.0, 34.0, 44.0, 52.0, 58.0)

        /** The size an off-scale finding names ("17 sp is off the scale — …"). */
        fun sizeOf(f: TypeSourceScan.Finding): Double? = f.detail.substringBefore(" sp is off the scale", "").toDoubleOrNull()
        val MEMBER_CHOSEN_FACES = listOf("org/nuruplace/member/feature/community/SelahRichEditor.kt")

        val sources by lazy { TypeSourceScan.load() }
    }

    private fun allOffScale(): List<TypeSourceScan.Finding> {
        val sites = TypeSourceScan.sizeSites(sources, excludedDirs = WIDGETS, excludedHelpers = emptySet())
        return TypeSourceScan.offScale(sites)
    }

    /** Off the scale, less the editorial letter's own listed sizes. */
    private fun offScale(): List<TypeSourceScan.Finding> =
        allOffScale().filterNot { it.file == EDITORIAL_LETTER && sizeOf(it) in EDITORIAL_SIZES }

    private fun ratchet(what: String, findings: List<TypeSourceScan.Finding>, ceiling: Int) {
        val listing = findings.joinToString("\n") { "  $it" }
        if (findings.size > ceiling) {
            fail("$what rose to ${findings.size} (ceiling $ceiling) — §8.1 rule 3's scale is 11·12·13·14·15·16·18·22·26·28:\n$listing")
        }
        if (findings.size < ceiling) {
            fail("$what fell to ${findings.size} — lower the ceiling from $ceiling to ${findings.size} so it can never rise back:\n$listing")
        }
    }

    @Test
    fun `every text size is on the scale`() {
        ratchet("Off-scale text sizes", offScale(), OFF_SCALE_CEILING)
    }

    @Test
    fun `the editorial letter's own sizes are exactly the listed ones`() {
        val used = allOffScale().filter { it.file == EDITORIAL_LETTER }.mapNotNull { sizeOf(it) }.toSet()
        assertEquals("the editorial letter's sizes off the scale", EDITORIAL_SIZES, used)
    }

    @Test
    fun `no text is drawn in a system or default face`() {
        ratchet(
            "System or default faces",
            TypeSourceScan.systemFaces(sources, excludedPaths = WIDGETS + MEMBER_CHOSEN_FACES),
            SYSTEM_FACE_CEILING,
        )
    }


    // ── The scanner itself: it must see what it claims to see ────────────────

    @Test
    fun `the scan finds the type helpers it must check`() {
        val helpers = TypeSourceScan.helpers(sources).keys
        for (name in listOf("nuruSans", "nuruSerif", "gInter", "giSerif", "cInter", "plInter", "evSerif", "pInter", "rSerif")) {
            assertTrue("the scan must treat $name as a type helper (found: $helpers)", name in helpers)
        }
    }

    @Test
    fun `the scan reads sizes the way the code writes them`() {
        val code = """
            package demo
            fun nuruSans(size: Int, weight: Int = 0): Any = TextStyle(fontFamily = Inter, fontSize = size.sp)
            fun aInter(size: Int) = nuruSans(size, 1)
            @Composable fun Badge(text: String, textSize: Int = 10) { Text(text, style = aInter(textSize)) }
            @Composable fun Screen(flag: Boolean) {
                // aInter(7) in a comment is not code
                Text("aInter(8) in a string is not code", style = aInter(9))
                Text("x", style = aInter(if (flag) 12 else 17))
                Text("y", fontSize = 20.sp)
                Badge("z", textSize = 15)
                Badge("w")
                val s = when { flag -> 13.sp; else -> 21.sp }
                Text("v", style = nuruSans(14).copy(fontSize = s))
                Text("u", style = aInter((40 * 0.4f).toInt()))
                Text("t", style = aInter(TypeScale.initials(40f)))
                Box(Modifier.size(24.dp))
            }
        """.trimIndent()
        val src = TypeSourceScan.Source("demo/Demo.kt", code)
        val findings = TypeSourceScan.offScale(TypeSourceScan.sizeSites(listOf(src), emptyList(), emptySet()))
        val details = findings.map { it.detail.substringBefore(" —") + "@" + it.line }
        assertEquals(
            listOf(
                "10 sp is off the scale@4", // Badge's default
                "9 sp is off the scale@7",
                "17 sp is off the scale@8",
                "20 sp is off the scale@9",
                "21 sp is off the scale@13",
                "computed size@14",
            ).sorted(),
            details.map { it.replace(Regex(""": .*@"""), "@") }.sorted(),
        )
    }

    @Test
    fun `the scan sees a system face and a TextStyle with no family`() {
        val code = """
            val a = TextStyle(fontSize = 12.sp)
            val b = TextStyle(fontFamily = Inter, fontSize = 12.sp)
            val c = SpanStyle(fontFamily = FontFamily.Monospace)
            val d = Typeface.create("cursive", Typeface.NORMAL)
            val e = Typeface.create(existing, Typeface.BOLD)
            // FontFamily.Serif in a comment is fine
        """.trimIndent()
        val findings = TypeSourceScan.systemFaces(listOf(TypeSourceScan.Source("demo/Faces.kt", code)), emptyList())
        assertEquals(listOf(1, 3, 4), findings.map { it.line }.sorted())
    }
}
