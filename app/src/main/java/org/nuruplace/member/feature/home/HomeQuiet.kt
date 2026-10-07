// A quiet divider when dark cards would touch (owner, 2026-10-07): only where
// a dark card would sit directly on another dark card, or on a photograph's
// dark edge, the Selah "rest for the eye" divider goes between them. Nothing
// moves — the owner's Home order stays verse → video → letter → reflection →
// liturgy. On a first day the reflection is held back (§9.2 #4), so the navy
// Sunday Letter would land on the liturgy's photograph; a service near puts
// the navy Live-now card right on the letter.
//
// Pure, so HomeQuietTest pins it.
package org.nuruplace.member.feature.home

/** The blocks of Home's opening, in the owner's order. */
enum class HomeEdge { VERSE, VIDEO, LIVE_NOW, LETTER, NEEDS, REFLECTION, LITURGY, WEEK }

/** One block as it stands, by its edges: [darkTop] — a navy card, or a
 *  photograph's dark edge, at its top; [darkBottom] — the same at its foot. */
data class HomeBlock(val edge: HomeEdge, val darkTop: Boolean, val darkBottom: Boolean)

object HomeQuiet {
    /** Each block that follows a dark-bottomed block with a dark top of its
     *  own gets the quiet divider before it — a general check over the
     *  assembled list, whichever blocks are there today. */
    fun dividersBefore(blocks: List<HomeBlock>): Set<HomeEdge> =
        blocks.zipWithNext().filter { (above, below) -> above.darkBottom && below.darkTop }.map { it.second.edge }.toSet()

    /** Home's opening as it will be drawn, from what Home knows: the verse
     *  (its photograph at the top when it has art, its cream foot below);
     *  the featured video (white); the Live-now card and the Sunday Letter
     *  (navy, every state); "What needs you today" (a light label first) or
     *  the reflection strip (cream); the liturgy (its hour's photograph at
     *  the top); YOUR WEEK (white — its navy band sits inside). */
    fun opening(
        verseArt: Boolean,
        video: Boolean,
        liveNow: Boolean,
        needsRail: Boolean,
        reflectionStrip: Boolean,
    ): List<HomeBlock> = listOfNotNull(
        HomeBlock(HomeEdge.VERSE, darkTop = verseArt, darkBottom = false),
        HomeBlock(HomeEdge.VIDEO, darkTop = false, darkBottom = false).takeIf { video },
        HomeBlock(HomeEdge.LIVE_NOW, darkTop = true, darkBottom = true).takeIf { liveNow },
        HomeBlock(HomeEdge.LETTER, darkTop = true, darkBottom = true),
        HomeBlock(HomeEdge.NEEDS, darkTop = false, darkBottom = false).takeIf { needsRail },
        HomeBlock(HomeEdge.REFLECTION, darkTop = false, darkBottom = false).takeIf { reflectionStrip },
        HomeBlock(HomeEdge.LITURGY, darkTop = true, darkBottom = false),
        HomeBlock(HomeEdge.WEEK, darkTop = false, darkBottom = false),
    )
}
