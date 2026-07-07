// THE font schema. Every text style in the app flows through these two
// factories so rhythm is consistent and future edits happen here only.
//
// Rules (change them HERE, nowhere else):
//   Line height     sans  → 1.4 × size (body rhythm)
//                   serif → 1.25 × size (display rhythm; Fraunces has tall ascenders)
//   Letter-spacing  sans ≥13sp → 0        (Inter is tuned for text sizes)
//                   sans <13sp → +0.1sp   (small-size legibility)
//                   serif      → −0.02em  (display tightening)
//                   explicit `tracking` (kickers/overlines) always wins.
//   Families        Inter (sans) + Fraunces (serif), bundled in res/font and
//                   defined in NuruTheme.kt — identical faces to iOS/RN/web.
//
// Per-surface helpers (gInter, pInter, evSerif, PW.t, …) are one-line delegates
// to these factories; NuruType styles are built from them too.
package org.nuruplace.member.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Canonical Inter (sans) style. `tracking` in sp; null = schema default.
 *  GLOBAL VOICE (owner-set, iOS build 28 parity): the DEFAULT body weight is
 *  Inter **Medium** — the Pathway module-reader weight — so every title,
 *  subtitle and paragraph app-wide carries the same warm reading presence.
 *  Callers that pass an explicit SemiBold/Bold are unaffected. */
fun nuruSans(size: Int, weight: FontWeight = FontWeight.Medium, tracking: Float? = null): TextStyle =
    TextStyle(
        fontFamily = Inter,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.4f).sp,
        letterSpacing = tracking?.sp ?: if (size < 13) 0.1.sp else 0.sp,
    )

/** Canonical Fraunces (serif) style. `tracking` in sp; null = schema default. */
fun nuruSerif(size: Int, weight: FontWeight = FontWeight.Normal, tracking: Float? = null): TextStyle =
    TextStyle(
        fontFamily = Fraunces,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.25f).sp,
        letterSpacing = tracking?.sp ?: (-0.02).em,
    )
