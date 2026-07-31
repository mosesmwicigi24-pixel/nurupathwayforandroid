// Glance has no arbitrary Canvas draw scope (unlike Compose proper), so the
// Pathway widget's progress ring is pre-rendered to a Bitmap with plain
// android.graphics and shown via Image(ImageProvider(bitmap)) — the standard
// Glance workaround for anything beyond its built-in shapes/text/images.
package org.nuruplace.member.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

object WidgetRing {
    /** Renders a [sizeDp] ring at [pct] (0-100) — gold arc over a translucent
     *  white track, matching the app's HubRing (PathwayHubScreen.kt). */
    fun render(
        context: Context,
        pct: Int,
        sizeDp: Int,
        strokeDp: Float,
        trackColor: Int,
        ringColor: Int,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(1)
        val strokePx = strokeDp * density
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.ROUND
        }
        val inset = strokePx / 2f
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        paint.color = trackColor
        canvas.drawArc(rect, 0f, 360f, false, paint)
        val sweep = 360f * (pct.coerceIn(0, 100) / 100f)
        if (sweep > 0f) {
            paint.color = ringColor
            canvas.drawArc(rect, -90f, sweep, false, paint)
        }
        return bitmap
    }
}
