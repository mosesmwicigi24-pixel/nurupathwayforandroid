// Stylus drawing for Selah — a Compose ink canvas gated to tablet-class
// devices (smallestScreenWidthDp >= 600, the standard Android "is this a
// tablet" heuristic — the closest honest analog to iOS's iPad-only gate,
// since Android styluses ship on tablets/Chromebooks, not phones). Within the
// canvas, each pointer event's `PointerType` (Compose's decode of the
// underlying MotionEvent tool type) is inspected so a real stylus draws a
// thinner, more precise line than a finger — genuine MotionEvent-tool-type
// capture, not a finger-only stand-in.
//
// A drawing exports to a PNG Bitmap and uploads through the SAME
// me/media/image endpoint the app already uses for post/event images
// (Net.client.api.uploadPostImage) — this module invents no upload path of
// its own.
package org.nuruplace.member.feature.community

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.nuruplace.member.data.net.Net
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.io.ByteArrayOutputStream

/** True on tablet-class Android hardware — see header note. */
fun isPenCapableDevice(context: Context): Boolean =
    context.resources.configuration.smallestScreenWidthDp >= 600

private data class InkStroke(val points: List<Offset>, val widthDp: Float)

@Composable
fun SelahDrawingSheet(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var strokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }
    var current by remember { mutableStateOf<InkStroke?>(null) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Nuru.paper)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Cancel", style = NuruType.cardCta, color = Nuru.ink600, modifier = Modifier.clickable { onDismiss() })
            Text("Draw", style = NuruType.title, color = Nuru.navy)
            Text(
                "Clear", style = NuruType.cardCta, color = Nuru.ink600,
                modifier = Modifier.clickable { Haptics.tap(view); strokes = emptyList(); current = null },
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .border(1.dp, Nuru.border)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = if (down.type == PointerType.Stylus) 2.5f else 5f
                        var pts = listOf(down.position)
                        current = InkStroke(pts, width)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) { change.consume(); break }
                            pts = pts + change.position
                            current = InkStroke(pts, width)
                            change.consume()
                        }
                        val finished = current
                        if (finished != null && finished.points.size > 1) strokes = strokes + finished
                        current = null
                    }
                },
        ) {
            canvasSize = Offset(size.width, size.height)
            (strokes + listOfNotNull(current)).forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                val path = Path()
                path.moveTo(stroke.points.first().x, stroke.points.first().y)
                stroke.points.drop(1).forEach { path.lineTo(it.x, it.y) }
                drawPath(path, color = Color.Black, style = Stroke(width = stroke.widthDp, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        error?.let {
            Text(it, style = NuruType.caption, color = Nuru.danger, modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(Spacing.screen)
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (strokes.isNotEmpty() && !uploading) Nuru.gold else Nuru.ink300)
                .clickable(enabled = strokes.isNotEmpty() && !uploading) {
                    uploading = true
                    error = null
                    scope.launch {
                        try {
                            val bmp = renderStrokesToBitmap(strokes, canvasSize.x.toInt().coerceAtLeast(1), canvasSize.y.toInt().coerceAtLeast(1))
                            val stream = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            val part = MultipartBody.Part.createFormData(
                                "file", "selah-drawing.png", stream.toByteArray().toRequestBody("image/png".toMediaTypeOrNull()),
                            )
                            val url = Net.client.api.uploadPostImage(part).url
                            uploading = false
                            if (url.isNotBlank()) { Haptics.confirm(view); onSave(url) } else { error = "Couldn't save the drawing. Try again." }
                        } catch (e: Exception) {
                            uploading = false
                            error = "Couldn't save the drawing. Try again."
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (uploading) CircularProgressIndicator(color = Color.White, modifier = Modifier.height(20.dp))
            else Text("Save drawing", style = NuruType.cardCta, color = Color.White)
        }
    }
}

private fun renderStrokesToBitmap(strokes: List<InkStroke>, width: Int, height: Int): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(AndroidColor.WHITE)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        color = AndroidColor.BLACK
    }
    strokes.forEach { stroke ->
        if (stroke.points.size < 2) return@forEach
        paint.strokeWidth = stroke.widthDp * 2f
        val path = AndroidPath()
        path.moveTo(stroke.points.first().x, stroke.points.first().y)
        stroke.points.drop(1).forEach { path.lineTo(it.x, it.y) }
        canvas.drawPath(path, paint)
    }
    return bmp
}
