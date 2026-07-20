// The cream scripture card — parity with the iOS VerseQuoteCard. A single
// shared composable so every surface that sets Scripture apart from its
// surrounding copy (a Pathway lesson's blockquote, a Plan day's featured
// passage) reads as the same "this is God's word" moment instead of each
// screen inventing its own quote treatment. Warm parchment ground, a left
// gold accent bar, a large hanging gold opening-quote glyph, the verse set
// in Fraunces over deep navy, and an uppercase, gray, letter-spaced
// reference line underneath.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.nuruSans
import org.nuruplace.member.ui.theme.nuruSerif

/** Warm parchment — distinct from `Nuru.verseBg` (#FFF8E6, the lighter tint
 *  used by the older gold pull-quote cards); this is the specific cream this
 *  card is built to, matching the iOS card + the reference screenshot. */
private val VerseCream = Color(0xFFFBF3DF)

/**
 * A single Scripture excerpt set apart from its surroundings. [verse] is the
 * quoted text (no surrounding quote marks needed — the glyph carries that);
 * [reference] is the citation ("Galatians 1:10") and is rendered uppercase.
 *
 * Both text styles come from the canonical schema (nuruSans/nuruSerif), so
 * this card automatically respects the member's global text-size AND
 * line-spacing preferences (AppPrefs) — no local override needed.
 */
@Composable
fun VerseQuoteCard(verse: String, reference: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VerseCream),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(Nuru.gold))
        Column(Modifier.padding(start = 26.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) {
            // The hanging quote glyph overhangs the verse block above-left,
            // rather than sitting inline with the first line.
            Text(
                "“",
                style = nuruSerif(52, FontWeight.SemiBold),
                color = Nuru.gold,
                modifier = Modifier.offset(x = (-4).dp, y = 6.dp),
            )
            Text(
                verse,
                style = nuruSerif(18, FontWeight.Normal),
                color = Nuru.navy,
                modifier = Modifier.offset(y = (-22).dp),
            )
            if (reference.isNotBlank()) {
                Text(
                    reference.uppercase(),
                    style = nuruSans(11, FontWeight.SemiBold, tracking = 1.4f),
                    color = Nuru.ink600,
                    modifier = Modifier.padding(top = 10.dp).offset(y = (-22).dp),
                )
            }
        }
    }
}
