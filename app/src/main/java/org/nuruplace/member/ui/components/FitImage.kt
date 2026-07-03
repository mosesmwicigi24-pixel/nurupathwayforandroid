// FitImage — a remote image that sizes itself to the picture's NATURAL aspect
// ratio, so cards GROW to fit their media instead of cropping it into a fixed box
// or leaving letterbox/pillarbox bars. A faithful port of the iOS FitImage.swift:
// fills the container width, height = width / aspect. The aspect starts at a 16:9
// placeholder and animates to the true value once Coil reports the bitmap size,
// clamped to 0.5…2.2 so a panorama or a portrait can't wreck the feed. A missing
// or failed URL shows a branded gradient — never a blank grey slab.
package org.nuruplace.member.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import org.nuruplace.member.ui.theme.Nuru

/**
 * Width-driven adaptive image. Put the rounding/clip on [modifier]; the height is
 * computed from the image's natural aspect — do NOT pass a fixed height/aspect.
 */
@Composable
fun FitImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholderAspect: Float = 16f / 9f,
    fallback: Brush = Nuru.heroGradient,
) {
    var target by remember(url) { mutableFloatStateOf(placeholderAspect) }
    val aspect by animateFloatAsState(target, label = "fitAspect")

    if (url.isNullOrBlank()) {
        Box(modifier.fillMaxWidth().aspectRatio(placeholderAspect).background(fallback))
        return
    }
    Box(modifier.fillMaxWidth().aspectRatio(aspect).background(fallback)) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop, // box already IS the natural aspect → fills, no crop, no bars
            onSuccess = { state ->
                val s = state.painter.intrinsicSize
                if (s.width > 0 && s.height > 0) target = (s.width / s.height).coerceIn(0.5f, 2.2f)
            },
        )
    }
}
