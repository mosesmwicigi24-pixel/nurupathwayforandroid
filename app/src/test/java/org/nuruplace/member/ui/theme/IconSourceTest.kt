package org.nuruplace.member.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.ui.icons.Lucide

/**
 * One icon family at three sizes (EXPERIENCE.md §8.1 rule 7): Lucide — the
 * glyphs iOS draws from its bundled font — at 14, 18 or 22.
 *
 * When Cycle 4 began the app drew Material icons (152 kinds, 746 uses) at 24
 * sizes from 7 to 64 dp, 18 of them at the 24 dp default. A glyph of 24 dp or
 * more is display art (a receipt's hero, a state card's emblem, a live
 * heart) — listed, and held from growing. A size the code works out (a
 * component's own size parameter) is listed by file.
 */
class IconSourceTest {
    private val sources = TypeSourceScan.load()

    /** Components that take an icon size as a parameter and pass it on. */
    private val sizeParameterFiles = setOf(
        "org/nuruplace/member/ui/components/InboxBell.kt",           // the bell: 18 by default, one look everywhere
        "org/nuruplace/member/feature/radio/LiveRadioScreen.kt",     // GlassSquare(icon, size): called at 22 and 18
        "org/nuruplace/member/feature/profile/SettingsScreen.kt",    // its Icon(icon, tint, size) helper
    )

    /** Display art (≥ 24 dp) — the count may fall, never rise. */
    private val DISPLAY_ART_CEILING = 27

    private data class Site(val file: String, val line: Int, val size: String)

    private fun iconSites(): List<Site> = sources
        .filterNot { it.path.contains("/member/widget/") || it.path.contains("/member/ui/icons/") }
        .flatMap { src ->
            Regex("""(?<!fun )(?<![A-Za-z0-9_.])Icon\(""").findAll(src.code).map { m ->
                var depth = 1; var j = m.range.last + 1
                while (j < src.code.length && depth > 0) {
                    when (src.code[j]) { '(' -> depth++; ')' -> depth-- }
                    j++
                }
                val args = src.code.substring(m.range.last + 1, j - 1)
                val size = Regex("""\bsize\(\s*([0-9.]+)\.dp\s*\)""").find(args)?.groupValues?.get(1)
                    ?: Regex(""",\s*([0-9.]+)\.dp\s*,?\s*$""").find(args)?.groupValues?.get(1)
                    ?: if (Regex("""\bsize\(""").containsMatchIn(args) || Regex(""",\s*size\s*$""").containsMatchIn(args)) "computed" else "default"
                Site(src.path, src.lineOf(m.range.first), size)
            }.toList()
        }

    @Test fun `every icon is 14, 18 or 22 — or listed display art`() {
        val sites = iconSites()
        assertTrue("no icons found — is the scan reading the sources?", sites.size > 100)
        val wrong = sites.filter { s ->
            when (s.size) {
                "default" -> true                                           // Material's 24 dp default
                "computed" -> s.file !in sizeParameterFiles
                else -> s.size.toDouble().let { it < 24.0 && it !in setOf(14.0, 18.0, 22.0) }
            }
        }
        assertEquals("icons off 14 / 18 / 22:\n" + wrong.joinToString("\n"), 0, wrong.size)
        val art = sites.filter { it.size != "default" && it.size != "computed" && it.size.toDouble() >= 24.0 }
        assertTrue(
            "display art grew to ${art.size} (ceiling $DISPLAY_ART_CEILING):\n" + art.joinToString("\n"),
            art.size <= DISPLAY_ART_CEILING,
        )
    }

    @Test fun `no Material icons — Lucide is the one family`() {
        val material = sources.filter { src ->
            Regex("""\bIcons\.(Filled|Outlined|Rounded|Sharp|TwoTone|Default|AutoMirrored)\b""").containsMatchIn(src.code)
        }.map { it.path }
        assertEquals("Material icons still drawn in:\n" + material.joinToString("\n"), 0, material.size)
    }

    @Test fun `every Lucide glyph builds — the font's outline in a 1000-unit em`() {
        val glyphs = Lucide::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType.name.endsWith("ImageVector") }
        assertTrue("found ${glyphs.size} glyphs", glyphs.size >= 100)
        for (g in glyphs) {
            val v = g.invoke(Lucide) as androidx.compose.ui.graphics.vector.ImageVector
            assertEquals(g.name, 1000f, v.viewportWidth)
            assertEquals(g.name, 1000f, v.viewportHeight)
            val path = v.root.firstOrNull() as androidx.compose.ui.graphics.vector.VectorPath
            assertTrue("${g.name} has no outline", path.pathData.size > 2)
        }
    }
}
