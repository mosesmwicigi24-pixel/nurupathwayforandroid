package org.nuruplace.member.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No other hues (EXPERIENCE.md §8.1 rule 1): paper, white, navy, gold and the
 * ink scale, with green, amber and red only for state. A source check over
 * every colour written in code — it found 59 outside the palette (indigo
 * cells, purple funds, a sky "steady", an orange "on the wall", the AI's
 * purple, six avatar hues…) and holds at zero. Home-screen widgets are their
 * own surface and are left out.
 */
class PaletteSourceTest {
    private val root = File("src/main/java/org/nuruplace/member")
    private val literal = Regex("""Color\(0x([0-9A-Fa-f]{8})\)""")

    /** The hue family of an opaque-ish colour, or null when it's ours. */
    private fun offPalette(argb: String): String? {
        val r = argb.substring(2, 4).toInt(16) / 255.0
        val g = argb.substring(4, 6).toInt(16) / 255.0
        val b = argb.substring(6, 8).toInt(16) / 255.0
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val l = (max + min) / 2
        val d = max - min
        val s = if (d == 0.0) 0.0 else d / (1 - kotlin.math.abs(2 * l - 1))
        if (s < 0.18 || l < 0.12 || l > 0.95) return null              // ink, paper, white
        val h = when (max) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }.let { if (it < 0) it + 360 else it }
        return when {
            h in 28.0..60.0 -> null                                      // gold, amber
            h in 20.0..28.0 && l < 0.40 -> null                          // deep amber (state text); orange is not ours
            h in 85.0..165.0 -> null                                     // green (state)
            h >= 345 || h <= 12 -> null                                  // red (state)
            h in 195.0..232.0 && (l < 0.40 || s < 0.45 || l > 0.90) -> null // navy, slate, navy tints
            else -> "hue ${h.toInt()}°"
        }
    }

    @Test fun `every colour in code is one of ours`() {
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val found = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("/member/widget/") }
            .flatMap { f ->
                f.readLines().asSequence().withIndex()
                    .filterNot { it.value.trimStart().startsWith("//") }
                    .flatMap { (i, line) ->
                        literal.findAll(line).mapNotNull { m ->
                            offPalette(m.groupValues[1])?.let { "${f.relativeTo(root)}:${i + 1} ${m.value} ($it)" }
                        }
                    }
            }
            .toList()
        assertEquals("colours outside §8.1 rule 1's palette:\n" + found.joinToString("\n"), 0, found.size)
    }

    @Test fun `the check knows our colours from the hues it bans`() {
        for (ours in listOf("FF0B1F33", "FFC89B3C", "FFFFF4C7", "FF16A34A", "FFD97706", "FFDC2626", "FFE8EEF7", "FF92400E", "FF59667C")) {
            assertEquals(ours, null, offPalette(ours))
        }
        for (theirs in listOf("FF6366F1", "FFA855F7", "FF0EA5E9", "FFDB2777", "FF0D9488", "FFF97316", "FF2F80ED")) {
            assertTrue(theirs, offPalette(theirs) != null)
        }
    }
}
