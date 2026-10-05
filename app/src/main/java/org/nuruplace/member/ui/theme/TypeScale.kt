// The one type scale (EXPERIENCE.md §8.1 rule 3, §8.2 #21). Every text in the
// app is one of these sizes, and the same role is the same size on both apps:
//
//   11  kicker (Inter bold, tracking 1.4) · meta
//   12  label · chip
//   13  body (small)
//   14  body · control row title (Inter medium)
//   15  content row title (Fraunces semibold)
//   16  reading body — an announcement, a lesson, a passage
//   18  card title (Fraunces semibold)
//   22  title
//   26  screen title
//   28  screen title · a screen's hero figure
//
// Nothing is smaller than 11. TypeScaleSourceTest reads the code and fails on
// any size that isn't here; a size that has to be worked out (an avatar's
// initials, a growing amount) is worked out by a function below, which
// TypeScaleTest proves only ever answers with a step of the scale.
package org.nuruplace.member.ui.theme

import kotlin.math.abs

object TypeScale {
    /** Every size text may take, in sp. */
    val steps: List<Int> = listOf(11, 12, 13, 14, 15, 16, 18, 22, 26, 28)

    /** The step nearest [sp]; halfway goes to the larger, for legibility. */
    fun nearest(sp: Float): Int = steps.minWith(compareBy<Int> { abs(it - sp) }.thenByDescending { it })

    /** A person's initials inside an avatar [diameter] dp across — about two
     *  fifths of it, on the scale (a 36 dp face reads 14, a 52 dp face 22). */
    fun initials(diameter: Float): Int = nearest(diameter * 0.4f)

    /** A money figure that is its screen's hero — Give's amount, a pledge's
     *  amount, a receipt's amount — given as the number alone ("100,000", the
     *  currency mark beside it): the screen-title size, a step down as the
     *  figure grows so it stays on one line ("100,000" 28 · "1,000,000" 26 ·
     *  longer 22). */
    fun amount(number: String): Int = when {
        number.length <= 8 -> 28
        number.length <= 10 -> 26
        else -> 22
    }
}
