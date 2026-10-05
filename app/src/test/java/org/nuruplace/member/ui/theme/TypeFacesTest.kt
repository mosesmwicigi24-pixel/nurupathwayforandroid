// EXPERIENCE.md §8.3 — "The faces resolve." Every NuruType style is drawn in
// Inter or Fraunces, the two faces bundled in res/font; each family is built
// only from its own files; and §8.1 rule 3's roles hold their sizes, so the
// same role reads at the same size on both apps.
package org.nuruplace.member.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nuruplace.member.R

class TypeFacesTest {

    /** Every `NuruType.x` style, by name — read through its getter, as call sites do. */
    private fun nuruTypeStyles(): Map<String, TextStyle> =
        NuruType::class.java.declaredMethods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == TextStyle::class.java }
            .associate { m -> m.name.removePrefix("get").replaceFirstChar { it.lowercase() } to m.invoke(NuruType) as TextStyle }

    @Test
    fun `every NuruType style is Inter or Fraunces`() {
        val styles = nuruTypeStyles()
        assertTrue("NuruType should expose its styles (found ${styles.keys})", styles.size >= 15)
        for ((name, style) in styles) {
            assertTrue(
                "NuruType.$name is drawn in ${style.fontFamily} — it must be Inter or Fraunces",
                style.fontFamily === Inter || style.fontFamily === Fraunces,
            )
        }
    }

    @Test
    fun `every NuruType size is on the scale`() {
        for ((name, style) in nuruTypeStyles()) {
            val size = style.fontSize.value.toDouble()
            assertTrue("NuruType.$name is $size sp — off §8.1 rule 3's scale", size in TypeSourceScan.SCALE)
        }
    }

    @Test
    fun `Inter and Fraunces are built only from their bundled files`() {
        fun files(family: FontFamily): Set<Int> = (family as FontListFontFamily).fonts.map { (it as ResourceFont).resId }.toSet()
        assertEquals(setOf(R.font.inter_regular, R.font.inter_medium, R.font.inter_semibold, R.font.inter_bold), files(Inter))
        assertEquals(setOf(R.font.fraunces_regular, R.font.fraunces_medium, R.font.fraunces_semibold, R.font.fraunces_bold), files(Fraunces))
    }

    @Test
    fun `the factories draw in the app's faces`() {
        assertSame(Inter, nuruSans(14).fontFamily)
        assertSame(Fraunces, nuruSerif(18).fontFamily)
    }

    @Test
    fun `the roles hold their sizes — rule 3`() {
        fun check(role: String, style: TextStyle, family: FontFamily, sizes: IntRange, weight: FontWeight? = null) {
            assertSame("$role's face", family, style.fontFamily)
            assertTrue("$role is ${style.fontSize} — rule 3 says $sizes", style.fontSize.value.toInt() in sizes && style.fontSize.value % 1f == 0f)
            if (weight != null) assertEquals("$role's weight", weight, style.fontWeight)
        }
        check("kicker", NuruType.kicker, Inter, 11..11, FontWeight.Bold)
        assertEquals("the kicker's tracking", 1.4.sp, NuruType.kicker.letterSpacing)
        check("screen title", NuruType.display, Fraunces, 26..28)
        check("card title", NuruType.cardTitle, Fraunces, 18..18, FontWeight.SemiBold)
        check("content row title", NuruType.rowTitle, Fraunces, 15..15, FontWeight.SemiBold)
        check("control row title", NuruType.controlTitle, Inter, 14..14, FontWeight.Medium)
        check("body", NuruType.body, Inter, 13..14)
        check("reading body", NuruType.bodyLg, Inter, 16..16)
        check("meta", NuruType.micro, Inter, 11..11)
    }
}
