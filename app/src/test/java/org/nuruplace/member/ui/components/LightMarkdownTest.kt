package org.nuruplace.member.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nuruplace.member.ui.components.LightMarkdown.Block
import org.nuruplace.member.ui.components.LightMarkdown.Run

/** An announcement reads as what it means, not as its marks (EXPERIENCE.md
 *  §8.1 rule 8) — the bodies the office writes, as they are written. */
class LightMarkdownTest {

    @Test fun `bold reads bold, with no asterisks`() {
        val runs = LightMarkdown.inline("Please note the new start time — **9:00 AM** from this Sunday.")
        assertEquals(
            listOf(Run("Please note the new start time — "), Run("9:00 AM", bold = true), Run(" from this Sunday.")),
            runs,
        )
    }

    @Test fun `a hashtag inside bold is words, and a hashtag line is not a heading`() {
        assertEquals(listOf(Run("the "), Run("#AndrewProject", bold = true), Run(" starts")), LightMarkdown.inline("the **#AndrewProject** starts"))
        val blocks = LightMarkdown.parse("#AndrewProject starts Sunday.")
        assertEquals(listOf(Block.Para(listOf(Run("#AndrewProject starts Sunday.")))), blocks)
    }

    @Test fun `italic with either mark, but not arithmetic or snake case`() {
        assertEquals(listOf(Run("come "), Run("early", italic = true)), LightMarkdown.inline("come *early*"))
        assertEquals(listOf(Run("come "), Run("early", italic = true)), LightMarkdown.inline("come _early_"))
        assertEquals(listOf(Run("5 * 3 * 2")), LightMarkdown.inline("5 * 3 * 2"))
        assertEquals(listOf(Run("see the snake_case_name here")), LightMarkdown.inline("see the snake_case_name here"))
    }

    @Test fun `an unclosed mark stays as typed`() {
        assertEquals(listOf(Run("**bold")), LightMarkdown.inline("**bold"))
        assertEquals(listOf(Run("a lone * star")), LightMarkdown.inline("a lone * star"))
    }

    @Test fun `a link reads as its words`() {
        assertEquals(
            listOf(Run("Register "), Run("here", url = "https://nuruplace.org/x"), Run(".")),
            LightMarkdown.inline("Register [here](https://nuruplace.org/x)."),
        )
    }

    @Test fun `a picture is left out and a quote mark is dropped`() {
        assertEquals(listOf(Run("See you there")), LightMarkdown.inline("![poster](https://x.org/p.png)See you there"))
        assertEquals(listOf(Block.Para(listOf(Run("Be still, and know")))), LightMarkdown.parse("> Be still, and know"))
    }

    @Test fun `paragraphs split on a blank line and keep their line breaks`() {
        val blocks = LightMarkdown.parse("Line one\nline two\n\nNext paragraph")
        assertEquals(
            listOf(Block.Para(listOf(Run("Line one\nline two"))), Block.Para(listOf(Run("Next paragraph")))),
            blocks,
        )
    }

    @Test fun `bullets, steps and a heading`() {
        val blocks = LightMarkdown.parse("# This Sunday\n- Prayer at **8:30**\n- Service at 9\n\n1. Park\n2) Sign in")
        assertEquals(
            listOf(
                Block.Heading(listOf(Run("This Sunday"))),
                Block.Bullets(listOf(listOf(Run("Prayer at "), Run("8:30", bold = true)), listOf(Run("Service at 9")))),
                Block.Steps(listOf(listOf(Run("Park")), listOf(Run("Sign in")))),
            ),
            blocks,
        )
    }

    @Test fun `the preview is the words alone`() {
        assertEquals(
            "This Sunday we gather at 9:00 AM at The Good News Mission. Come early for prayer.",
            LightMarkdown.plain("This Sunday we gather at **9:00 AM** at The Good News Mission. Come early for prayer."),
        )
        assertEquals("Today Prayer · Service Bring a friend", LightMarkdown.plain("# Today\n- Prayer\n- Service\n\nBring a friend"))
    }
}
