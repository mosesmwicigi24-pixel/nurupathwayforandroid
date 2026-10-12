// The little markdown an announcement is written in — **bold**, *italic*,
// "- " bullets, "1. " steps, a "# " heading, a [link](https://…) — read as
// what it means, not as its marks (EXPERIENCE.md §8.1 rule 8: words that look
// like data never leak). The office types "**9:00 AM**" in the portal; the
// member read the asterisks.
//
// The reading itself is pure (LightMarkdownTest pins it); [MarkdownBody] only
// draws what it read, in the caller's body style — an announcement's is the
// one 16 sp reading body (§8.2 #21).
package org.nuruplace.member.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.nuruplace.member.ui.theme.NuruType

object LightMarkdown {
    /** A stretch of words and how it reads. */
    data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false, val url: String? = null)

    sealed interface Block {
        /** A paragraph; a single line break inside it stays a line break
         *  (the portal's text box is not a markdown editor). */
        data class Para(val runs: List<Run>) : Block
        data class Heading(val runs: List<Run>) : Block
        data class Bullets(val items: List<List<Run>>) : Block
        data class Steps(val items: List<List<Run>>) : Block
    }

    private val HEADING = Regex("""^#{1,6}\s+""")
    private val BULLET = Regex("""^[-*•]\s+""")
    private val STEP = Regex("""^\d{1,3}[.)]\s+""")
    private val LINK = Regex("""^\[([^\]\n]+)]\((https?://[^)\s]+)\)""")
    private val IMAGE = Regex("""^!\[[^\]\n]*]\([^)\s]*\)""")
    private val QUOTE = Regex("""^>\s?""")

    fun parse(md: String): List<Block> {
        val out = mutableListOf<Block>()
        val para = mutableListOf<String>()
        fun flush() {
            if (para.isNotEmpty()) out.add(Block.Para(inline(para.joinToString("\n"))))
            para.clear()
        }
        val lines = md.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val t = lines[i].trim()
            when {
                t.isEmpty() -> { flush(); i++ }
                HEADING.containsMatchIn(t) -> { flush(); out.add(Block.Heading(inline(t.replace(HEADING, "")))); i++ }
                BULLET.containsMatchIn(t) -> {
                    flush()
                    val items = mutableListOf<List<Run>>()
                    while (i < lines.size) {
                        val x = lines[i].trim()
                        if (BULLET.containsMatchIn(x)) { items.add(inline(x.replace(BULLET, ""))); i++ } else break
                    }
                    out.add(Block.Bullets(items))
                }
                STEP.containsMatchIn(t) -> {
                    flush()
                    val items = mutableListOf<List<Run>>()
                    while (i < lines.size) {
                        val x = lines[i].trim()
                        if (STEP.containsMatchIn(x)) { items.add(inline(x.replace(STEP, ""))); i++ } else break
                    }
                    out.add(Block.Steps(items))
                }
                // A "> " quote reads as its words (the server's notice
                // excerpt drops the mark the same way).
                else -> { para.add(t.replace(QUOTE, "")); i++ }
            }
        }
        flush()
        return out
    }

    /**
     * Inline marks: `**bold**` (or `__bold__`), `*italic*` (or `_italic_`),
     * `[words](https://…)`. A mark counts only when it closes on the same
     * stretch and hugs its words — "5 * 3", "snake_case" and a lone "*" stay
     * as typed.
     */
    fun inline(text: String): List<Run> {
        val runs = mutableListOf<Run>()
        val plain = StringBuilder()
        fun emitPlain() { if (plain.isNotEmpty()) { runs.add(Run(plain.toString())); plain.clear() } }
        var i = 0
        while (i < text.length) {
            val rest = text.substring(i)
            // A picture belongs in the announcement's gallery, not its words.
            val image = IMAGE.find(rest)
            if (image != null) {
                i += image.value.length
                continue
            }
            val link = LINK.find(rest)
            if (link != null) {
                emitPlain()
                runs.add(Run(link.groupValues[1], url = link.groupValues[2]))
                i += link.value.length
                continue
            }
            val c = text[i]
            if ((c == '*' || c == '_') && i + 1 < text.length && text[i + 1] == c) {
                val mark = "$c$c"
                val end = text.indexOf(mark, i + 2)
                val inner = if (end > i + 2) text.substring(i + 2, end) else null
                if (inner != null && hugs(inner) && opensAt(text, i, c)) {
                    emitPlain()
                    inline(inner).forEach { runs.add(it.copy(bold = true)) }
                    i = end + 2
                    continue
                }
            } else if (c == '*' || c == '_') {
                val end = text.indexOf(c, i + 1)
                val inner = if (end > i + 1) text.substring(i + 1, end) else null
                if (inner != null && hugs(inner) && opensAt(text, i, c) && closesAt(text, end, c)) {
                    emitPlain()
                    inline(inner).forEach { runs.add(it.copy(italic = true)) }
                    i = end + 1
                    continue
                }
            }
            plain.append(c)
            i++
        }
        emitPlain()
        return merge(runs)
    }

    /** The words inside a mark start and end with a non-space. */
    private fun hugs(inner: String): Boolean =
        inner.isNotEmpty() && !inner.first().isWhitespace() && !inner.last().isWhitespace()

    /** An underscore opens only at a word's start ("snake_case" is a word). */
    private fun opensAt(text: String, i: Int, c: Char): Boolean =
        c != '_' || i == 0 || !text[i - 1].isLetterOrDigit()

    private fun closesAt(text: String, end: Int, c: Char): Boolean =
        c != '_' || end + 1 >= text.length || !text[end + 1].isLetterOrDigit()

    private fun merge(runs: List<Run>): List<Run> {
        val out = mutableListOf<Run>()
        for (r in runs) {
            val last = out.lastOrNull()
            if (last != null && last.bold == r.bold && last.italic == r.italic && last.url == null && r.url == null) {
                out[out.size - 1] = last.copy(text = last.text + r.text)
            } else out.add(r)
        }
        return out
    }

    /** The words alone, for a two-line preview: no marks, one line per
     *  paragraph joined by spaces. */
    fun plain(md: String): String = parse(md).joinToString(" ") { b ->
        when (b) {
            is Block.Para -> b.runs.joinToString("") { it.text }.replace("\n", " ")
            is Block.Heading -> b.runs.joinToString("") { it.text }
            is Block.Bullets -> b.items.joinToString(" · ") { item -> item.joinToString("") { it.text } }
            is Block.Steps -> b.items.joinToString(" · ") { item -> item.joinToString("") { it.text } }
        }
    }.trim()
}

/** [runs] as styled text: bold in [boldWeight], links in [linkColor],
 *  underlined and tappable. */
fun markdownText(runs: List<LightMarkdown.Run>, linkColor: Color, boldWeight: FontWeight = FontWeight.Bold): AnnotatedString =
    buildAnnotatedString {
        runs.forEach { r ->
            val style = SpanStyle(
                fontWeight = if (r.bold) boldWeight else null,
                fontStyle = if (r.italic) FontStyle.Italic else null,
            )
            if (r.url != null) {
                withLink(
                    LinkAnnotation.Url(
                        r.url,
                        TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    ),
                ) { withStyle(style) { append(r.text) } }
            } else {
                withStyle(style) { append(r.text) }
            }
        }
    }

/**
 * An announcement's body (or any short piece the office writes) drawn from
 * its markdown, in [style] — the caller's body style; a heading takes the
 * card title (Fraunces 18).
 */
@Composable
fun MarkdownBody(
    md: String,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
    headingStyle: TextStyle = NuruType.cardTitle,
) {
    val blocks = remember(md) { LightMarkdown.parse(md) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { b ->
            when (b) {
                is LightMarkdown.Block.Para -> Text(markdownText(b.runs, linkColor), style = style, color = color)
                is LightMarkdown.Block.Heading -> Text(markdownText(b.runs, linkColor), style = headingStyle, color = color)
                is LightMarkdown.Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.items.forEach { item ->
                        Row {
                            Text("•", style = style, color = color)
                            Spacer(Modifier.width(10.dp))
                            Text(markdownText(item, linkColor), style = style, color = color)
                        }
                    }
                }
                is LightMarkdown.Block.Steps -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    b.items.forEachIndexed { n, item ->
                        Row {
                            Text("${n + 1}.", style = style.copy(fontWeight = FontWeight.SemiBold), color = color)
                            Spacer(Modifier.width(8.dp))
                            Text(markdownText(item, linkColor), style = style, color = color, modifier = Modifier.padding(end = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
