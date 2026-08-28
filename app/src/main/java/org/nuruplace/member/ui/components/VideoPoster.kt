// The poster the server never sent (owner, 2026-08-26: "the thumbnail in the
// Nuru Pathway featured should be displayed").
//
// Uploaded (direct/hosted) videos carry `thumbnail_url = null` — there is no
// ffmpeg on the API host to cut one — so the featured card painted a flat grey
// slab. The frame we need is already inside the video: MediaMetadataRetriever
// pulls one ~1s in over HTTP range requests (it does NOT download the whole
// file), off the main thread, and we keep the bitmap for the session so the
// card paints instantly on every later appearance.
//
// This is the port of iOS NuruMember/Features/Home/VideoPoster.swift
// (AVAssetImageGenerator at t=1s + VideoPosterCache). MediaMetadataRetriever is
// the direct analogue of AVAssetImageGenerator and needs no new dependency;
// Coil's `coil-video` VideoFrameDecoder was the alternative but it fetches the
// ENTIRE video into the disk cache before it can decode a frame, which is a
// full download to draw one thumbnail.
//
// YouTube/Vimeo keep their provider thumbnails (derived server-side) and never
// reach this path.
package org.nuruplace.member.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Session-scoped poster cache — one decode per video URL, ever. */
object VideoPosterCache {
    private val frames = mutableMapOf<String, Bitmap>()
    private val lock = Mutex()

    fun cached(url: String): Bitmap? = frames[url]

    /** Cut a poster frame once per URL per session. Silent on failure — the
     *  caller simply keeps its neutral placeholder, exactly as before. */
    suspend fun poster(url: String): Bitmap? {
        frames[url]?.let { return it }
        return lock.withLock {
            // Re-check: a second card may have decoded it while we waited.
            frames[url] ?: frame(url)?.also { frames[url] = it }
        }
    }

    private suspend fun frame(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(url, HashMap())
            // A frame a second in: past any black/fade-in first frame (iOS uses
            // the same t=1s). Micros, and CLOSEST_SYNC so a keyframe-sparse
            // encode still yields something.
            retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Throwable) {
            // Any failure (offline, redirect, unsupported codec, dead URL) is a
            // non-event: no poster, no crash, no log spam.
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/**
 * Draws the video's own first-second frame, or nothing at all while it is being
 * cut / if it cannot be. Sits UNDER the play disc and duration pill, so the
 * card's chrome is unchanged either way.
 */
@Composable
fun VideoPosterFrame(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (url.isNullOrBlank()) return
    var bitmap by remember(url) { mutableStateOf(VideoPosterCache.cached(url)) }
    LaunchedEffect(url) {
        if (bitmap == null) bitmap = VideoPosterCache.poster(url)
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
