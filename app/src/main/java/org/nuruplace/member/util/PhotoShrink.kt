// A photo made small enough to send: upright, its longest side at most a
// given size, as JPEG. The avatar uploaded the picked photo's full bytes, and
// the server caps avatars at 5 MB ("Photo exceeds 5 MB") — a phone camera's
// picture is often more; iOS sends an avatar at most 512 px (ProfileView).
package org.nuruplace.member.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object PhotoShrink {
    /** [bytes] as an upright JPEG whose longest side is at most [maxDim];
     *  null when they aren't an image. */
    fun jpeg(bytes: ByteArray, maxDim: Int, quality: Int = 85): ByteArray? {
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val longest = maxOf(src.width, src.height)
        val scale = if (longest > maxDim && longest > 0) maxDim.toFloat() / longest else 1f
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val shaped = if (scale == 1f && rotation == 0f) src
        else Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        val out = ByteArrayOutputStream()
        shaped.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
