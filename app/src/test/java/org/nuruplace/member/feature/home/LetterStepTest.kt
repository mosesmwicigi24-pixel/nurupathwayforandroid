package org.nuruplace.member.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Sunday Letter's one next step goes where it says (§9.1 rule 7, no dead
 *  ends) — the server's two routes (letters.ts), as iOS LetterView.navigate.
 *  Its button did nothing on Android: Home never wired the dialog's step. */
class LetterStepTest {
    @Test fun `a module step opens that lesson`() {
        assertEquals(WeekDest.Screen("module/m-42"), letterStepDest("module", "m-42"))
    }

    @Test fun `the pathway step, or anything without a lesson, opens Pathway`() {
        assertEquals(WeekDest.Tab("pathway"), letterStepDest("pathway", null))
        assertEquals(WeekDest.Tab("pathway"), letterStepDest("module", null))
        assertEquals(WeekDest.Tab("pathway"), letterStepDest("module", " "))
        assertEquals(WeekDest.Tab("pathway"), letterStepDest("something-new", null))
    }
}
