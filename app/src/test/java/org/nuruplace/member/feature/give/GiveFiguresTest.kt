// Give's figures, said as money (final walk C11, C16): "KSh 1,000" as it's
// typed, never "KSh 1000"; and green only for money that was paid.
package org.nuruplace.member.feature.give

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class GiveFiguresTest {
    @Test fun `the shillings group in thousands as they're typed`() {
        assertEquals("", groupThousands(""))
        assertEquals("5", groupThousands("5"))
        assertEquals("200", groupThousands("200"))
        assertEquals("1,000", groupThousands("1000"))
        assertEquals("25,000", groupThousands("25000"))
        assertEquals("2,000,000", groupThousands("2000000"))
    }

    @Test fun `the field shows the grouping while it holds the digits, the cursor on the same digit`() {
        val t = ThousandsGrouping.filter(AnnotatedString("25000"))
        assertEquals("25,000", t.text.text)
        val m = t.offsetMapping
        // Before "2", after "25" (the comma follows), and at the end.
        assertEquals(0, m.originalToTransformed(0))
        assertEquals(3, m.originalToTransformed(2))
        assertEquals(6, m.originalToTransformed(5))
        // And back: a cursor after the comma is after the second digit.
        assertEquals(2, m.transformedToOriginal(3))
        assertEquals(5, m.transformedToOriginal(6))
        assertEquals(0, ThousandsGrouping.filter(AnnotatedString("")).offsetMapping.originalToTransformed(0))
    }

    @Test fun `PAID is green only for money paid — KSh 0 is a plain navy figure (iOS PaidTint)`() {
        assertEquals(GIVE.navy, paidTint(0))
        assertEquals(GIVE.successText, paidTint(20_000))
    }
}
