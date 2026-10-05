// EXPERIENCE.md §8.2 #21 — the sizes the app works out (an avatar's initials,
// a hero amount) and the Material slots it never styles itself are all on the
// scale. TypeScaleSourceTest trusts a `TypeScale.…` call; this proves it may.
package org.nuruplace.member.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScaleTest {
    private val scale = TypeSourceScan.SCALE

    @Test
    fun `the scale is the spec's — 11 12 13 14 15 16 18 22 26 28, nothing under 11`() {
        assertEquals(listOf(11, 12, 13, 14, 15, 16, 18, 22, 26, 28), TypeScale.steps)
        assertEquals(scale, TypeScale.steps.map { it.toDouble() }.toSet())
    }

    @Test
    fun `nearest answers only with a step, and never under 11`() {
        var sp = 0f
        while (sp <= 80f) {
            val step = TypeScale.nearest(sp)
            assertTrue("nearest($sp) = $step", step.toDouble() in scale)
            sp += 0.25f
        }
        assertEquals(11, TypeScale.nearest(9f))
        assertEquals(11, TypeScale.nearest(10f))
        assertEquals(18, TypeScale.nearest(17f)) // halfway goes up
        assertEquals(22, TypeScale.nearest(20f)) // halfway goes up
        assertEquals(28, TypeScale.nearest(52f))
    }

    @Test
    fun `avatar initials are on the scale at every diameter`() {
        for (d in 16..120) assertTrue("initials($d)", TypeScale.initials(d.toFloat()).toDouble() in scale)
        assertEquals(11, TypeScale.initials(24f))
        assertEquals(14, TypeScale.initials(36f))
        assertEquals(22, TypeScale.initials(52f))
    }

    @Test
    fun `a hero amount is the screen-title size and steps down as it grows`() {
        assertEquals(28, TypeScale.amount("1,000"))
        assertEquals(28, TypeScale.amount("100,000"))
        assertEquals(26, TypeScale.amount("1,000,000"))
        assertEquals(22, TypeScale.amount("100,000,000"))
        for (n in 0..20) assertTrue(TypeScale.amount("9".repeat(n)).toDouble() in scale)
    }

    @Test
    fun `Material's fifteen slots are in an app face and on the scale`() {
        val t = nuruTypography()
        val slots = mapOf(
            "displayLarge" to t.displayLarge, "displayMedium" to t.displayMedium, "displaySmall" to t.displaySmall,
            "headlineLarge" to t.headlineLarge, "headlineMedium" to t.headlineMedium, "headlineSmall" to t.headlineSmall,
            "titleLarge" to t.titleLarge, "titleMedium" to t.titleMedium, "titleSmall" to t.titleSmall,
            "bodyLarge" to t.bodyLarge, "bodyMedium" to t.bodyMedium, "bodySmall" to t.bodySmall,
            "labelLarge" to t.labelLarge, "labelMedium" to t.labelMedium, "labelSmall" to t.labelSmall,
        )
        for ((name, style) in slots) {
            assertTrue("$name's face", style.fontFamily === Inter || style.fontFamily === Fraunces)
            assertTrue("$name is ${style.fontSize}", style.fontSize.value.toDouble() in scale)
        }
        // A style-less Text inherits bodyLarge — the one 16 sp reading body.
        assertEquals(16f, t.bodyLarge.fontSize.value)
    }
}
