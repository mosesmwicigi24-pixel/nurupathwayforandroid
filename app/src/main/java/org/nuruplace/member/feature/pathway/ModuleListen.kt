// What a Pathway lesson reads aloud (feature/listen) — pure, so
// ModuleListenTest pins it. The lesson's own order: its title, the key verse,
// then each section by number. The reflection box, the gate and the quiz are
// things to do, not to hear.
package org.nuruplace.member.feature.pathway

import org.nuruplace.member.data.net.ModuleDetail
import org.nuruplace.member.feature.listen.ListenPiece

internal fun moduleListenPieces(m: ModuleDetail): List<ListenPiece> = buildList {
    if (m.title.isNotBlank()) add(ListenPiece.Heading(m.title))
    m.keyVerses?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
        add(ListenPiece.Heading("Key verse"))
        add(ListenPiece.Prose(it))
    }
    val pages = m.pages.filter { it.isNotBlank() }
    pages.forEachIndexed { i, page ->
        // "Section 2" only when there is more than one — a one-section lesson
        // just begins.
        if (pages.size > 1) add(ListenPiece.Heading("Section ${i + 1}"))
        add(ListenPiece.Prose(page))
    }
}
