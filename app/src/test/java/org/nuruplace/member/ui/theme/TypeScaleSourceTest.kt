// EXPERIENCE.md §8.3 — "The scale holds." Every text size the code decides is
// on §8.1 rule 3's scale (11 · 12 · 13 · 14 · 15 · 16 · 18 · 22 · 26 · 28),
// and no text is drawn in a system or default face. A ratchet: the counts
// below are today's, the test fails if either rises, and it fails if either
// falls without the ceiling coming down with it — so the number written here
// is always the true one, and it only ever moves toward zero.
//
// Allowed, and listed (§8.3: "icons and home-screen widgets are allowed and
// listed"):
//   • Icons are Material vectors sized in dp — not text, so not scanned.
//   • The home-screen widgets (widget/): Glance draws in the launcher's own
//     face at its own sizes.
//   • ORNAMENTS: a glyph drawn with a font that is an icon, not words — the
//     large quotation marks, the keepsake's star, Live's floating heart. They
//     go through `nuruOrnament(size)` and every use is counted below.
//   • The Selah editor's member-chosen faces (SelahRichEditor.kt): a member's
//     own writing in the face they picked — iOS offers Georgia and Noteworthy.
//   • Bitmaps: the verse share card (VerseTableau.kt) paints Inter and Fraunces
//     in pixels onto a 1080-wide image; the radio's emoji particles are painted
//     glyphs. Neither is laid-out text, so `Paint.textSize` is not scanned.
package org.nuruplace.member.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TypeScaleSourceTest {

    private companion object {
        /** Off-scale and computed text sizes today. May only fall; 0 is the goal (§8.3). */
        const val OFF_SCALE_CEILING = 177

        /** System or default faces used for text today. May only fall; 0 is the goal. */
        const val SYSTEM_FACE_CEILING = 1

        /** Ornament uses per file — each one an icon drawn with a font, never words. */
        val ORNAMENTS: Map<String, Int> = mapOf(
            "org/nuruplace/member/feature/grow/PlanKeepsakeScreen.kt" to 1,
            "org/nuruplace/member/feature/home/HomeScreen.kt" to 1,
            "org/nuruplace/member/feature/home/LiturgyCards.kt" to 1,
        )

        const val ORNAMENT = "nuruOrnament"
        val WIDGETS = listOf("org/nuruplace/member/widget/")
        val MEMBER_CHOSEN_FACES = listOf("org/nuruplace/member/feature/community/SelahRichEditor.kt")

        val sources by lazy { TypeSourceScan.load() }
    }

    private fun offScale(): List<TypeSourceScan.Finding> {
        val sites = TypeSourceScan.sizeSites(sources, excludedDirs = WIDGETS, excludedHelpers = setOf(ORNAMENT))
        return TypeSourceScan.offScale(sites)
    }

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
    fun `every text size is on the scale — a ratchet that only falls`() {
        ratchet("Off-scale text sizes", offScale(), OFF_SCALE_CEILING)
    }

    @Test
    fun `no text is drawn in a system or default face — a ratchet that only falls`() {
        ratchet(
            "System or default faces",
            TypeSourceScan.systemFaces(sources, excludedPaths = WIDGETS + MEMBER_CHOSEN_FACES),
            SYSTEM_FACE_CEILING,
        )
    }

    @Test
    fun `ornaments are listed — an icon drawn with a font, never words`() {
        val found = sources.associate { it.path to TypeSourceScan.calls(it, ORNAMENT).size }.filterValues { it > 0 }
        assertEquals("nuruOrnament uses by file — add a new one here only if it is an icon, not words", ORNAMENTS, found)
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
