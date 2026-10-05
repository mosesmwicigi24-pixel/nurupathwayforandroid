// The featured video takes its own shape (owner, 2026-10-06): a portrait
// video was pillarboxed inside a fixed 16:9 frame on Home. The frame now
// takes the video's width ÷ height — tall for a portrait video, wide for a
// landscape one — at the card's full content width, and the player fills it.
//
// Pure (no Android types), so FeaturedVideoShapeTest pins every rule.
package org.nuruplace.member.ui.components

object VideoShape {
    /** Before anything is known: the old frame. */
    const val DEFAULT = 16f / 9f

    /** The tallest frame drawn — 9:20. A taller video is cropped to it. */
    const val TALLEST = 9f / 20f

    /** The widest frame drawn — 21:9. */
    const val WIDEST = 21f / 9f

    /** [ratio] held within 9:20 … 21:9; anything unusable is 16:9. */
    fun clamp(ratio: Float): Float =
        if (ratio.isNaN() || ratio.isInfinite() || ratio <= 0f) DEFAULT else ratio.coerceIn(TALLEST, WIDEST)

    /**
     * The shape a picture is SEEN in: width × the pixel aspect ratio ÷ height,
     * turned a quarter when the player left a 90° / 270° rotation for the app
     * to apply (Media3's `VideoSize.unappliedRotationDegrees` — on a
     * SurfaceView the decoder has usually applied it already, and the size is
     * already turned). Null when the size isn't known yet.
     */
    fun displayAspect(
        width: Int,
        height: Int,
        pixelWidthHeightRatio: Float = 1f,
        unappliedRotationDegrees: Int = 0,
    ): Float? {
        if (width <= 0 || height <= 0) return null
        val pixel = if (pixelWidthHeightRatio.isNaN() || pixelWidthHeightRatio <= 0f) 1f else pixelWidthHeightRatio
        val ratio = width * pixel / height
        val quarterTurn = Math.floorMod(unappliedRotationDegrees, 180) == 90
        return if (quarterTurn) 1f / ratio else ratio
    }

    /**
     * The frame's shape, from what is known so far:
     * - [player] — Media3's report while it plays: the truth, and remembered;
     * - [remembered] — that report from an earlier load of the same asset, so
     *   the first paint is already right;
     * - [thumbnail] — the poster's own size once it loads (an admin poster
     *   or the frame cut from the video), a good guess before the video plays;
     * - else 16:9.
     * Clamped to 9:20 … 21:9.
     */
    fun frame(player: Float?, remembered: Float?, thumbnail: Float?): Float =
        clamp(player ?: remembered ?: thumbnail ?: DEFAULT)

    /** Whether [next] is a different frame from [current] — a pixel's rounding
     *  on a reloaded poster is not a new shape, so the card never jitters. */
    fun differs(current: Float?, next: Float): Boolean =
        current == null || kotlin.math.abs(current - next) > 0.01f
}
