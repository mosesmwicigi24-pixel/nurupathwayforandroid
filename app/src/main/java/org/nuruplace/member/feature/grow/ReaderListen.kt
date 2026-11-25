// What the readers in Grow read aloud (feature/listen) — pure, so
// ReaderListenTest pins it. Each function follows its screen's own render
// order, so what a member hears is what they see, top to bottom.
package org.nuruplace.member.feature.grow

import org.nuruplace.member.data.net.Devotional
import org.nuruplace.member.data.net.PlanSegment
import org.nuruplace.member.feature.listen.ListenColors
import org.nuruplace.member.feature.listen.ListenPiece

/** A plan part (PlanPartReaderScreen's PartContent order). [passages] holds
 *  the texts fetched for bare references, keyed by ScriptureRefs.normalize.
 *  The Watch/Listen part is a film or a recording with keynotes beside it —
 *  reading the keynotes over it would talk over the speaker, so it has none. */
internal fun planPartListenPieces(
    part: String,
    group: List<PlanSegment>,
    dayTitle: String?,
    passages: Map<String, String>,
): List<ListenPiece> = when (part) {
    "word" -> buildList {
        dayTitle?.takeIf { it.isNotBlank() }?.let { add(ListenPiece.Heading(it)) }
        for (seg in group) {
            when (seg.kind.lowercase()) {
                "scripture" -> {
                    val ref = seg.reference?.trim()?.takeIf { it.isNotEmpty() }
                    val start = ref?.let(ScriptureRefs::startVerse)
                    val text = seg.content?.takeIf { it.isNotBlank() }
                        ?: ref?.takeIf(ScriptureRefs::isReference)?.let { passages[ScriptureRefs.normalize(it)] }
                    when {
                        text != null -> add(ListenPiece.Scripture(text, ref, start))
                        ref != null -> add(ListenPiece.Heading(ref))
                    }
                }
                // Go Deeper names passages to open — say which, not all of them.
                "reading" -> seg.content?.takeIf { it.isNotBlank() }?.let { refs ->
                    val lines = ScriptureRefs.split(refs)
                    if (lines.isNotEmpty()) {
                        add(ListenPiece.Heading("Go deeper"))
                        add(ListenPiece.Prose(lines.joinToString("\n\n")))
                    }
                }
                else -> seg.content?.takeIf { it.isNotBlank() }?.let { add(ListenPiece.Prose(it)) }
            }
        }
    }
    "respond" -> group.mapNotNull { seg -> seg.content?.takeIf { it.isNotBlank() }?.let { ListenPiece.Prose(it) } }
    else -> emptyList()
}

/** The bare references in a Word part whose passages have to be fetched
 *  before they can be read (the same fetch RScriptureQuote makes, so the
 *  session cache answers it). */
internal fun planPartPassagesToFetch(part: String, group: List<PlanSegment>): List<String> =
    if (part != "word") emptyList()
    else group.filter { it.kind.lowercase() == "scripture" && it.content.isNullOrBlank() }
        .mapNotNull { it.reference?.trim()?.takeIf(ScriptureRefs::isReference) }

/** Today's devotional (DevotionalScreen order): title, the verse, the body —
 *  then the reflection prompt, so the listener is left with the question. */
internal fun devotionalListenPieces(d: Devotional): List<ListenPiece> = buildList {
    add(ListenPiece.Heading(d.title))
    d.scriptureText?.takeIf { it.isNotBlank() }?.let {
        add(ListenPiece.Scripture(it, d.scriptureRef, d.scriptureRef?.let(ScriptureRefs::startVerse)))
    }
    if (d.body.isNotBlank()) add(ListenPiece.Prose(d.body))
    d.reflectionPrompt?.takeIf { it.isNotBlank() }?.let {
        add(ListenPiece.Heading("Reflection"))
        add(ListenPiece.Prose(it))
    }
}

/** The bar's colours on a reader page, day or night. */
internal fun ReaderPalette.listenColors() = ListenColors(
    bar = card, ink = ink, inkDim = inkDim, accent = gold, onAccent = navy, border = border,
)
