// The verse, beheld — not only read (iOS parity: VerseTableau.swift).
//   • VerseTableauHeader — the day's server-curated photograph with the verse
//     set in serif over a warm scrim; the cream body (season chip, reactions)
//     continues below in VerseCard.
//   • SelahDivider — two gold hairlines meeting at a small cross: a quiet
//     rest between text-dense stretches of the Home feed.
//   • VerseImageShare — renders the tableau to a real photograph (art +
//     verse + reference + brand line, text drawn by us so it is always
//     crisp) and hands it to the system share sheet via FileProvider.
package org.nuruplace.member.feature.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nuruplace.member.R
import org.nuruplace.member.data.net.VerseArt
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.io.File

private val GoldLight = Color(0xFFF2DDA0)

/** The deep-navy veil laid over a tableau photograph so the type stays legible:
 *  the image shows through (a bit hidden), deepening toward the base where the
 *  text sits. Shared by the verse tableau and the liturgy card. */
val DeepNavyBlockBrush = Brush.verticalGradient(
    0f to Color(0xFF0A1628).copy(alpha = 0.40f),
    0.45f to Color(0xFF0A1628).copy(alpha = 0.58f),
    1f to Color(0xFF0A1628).copy(alpha = 0.97f),
)

@Composable
fun VerseTableauHeader(art: VerseArt, text: String?, refLine: String, version: String) {
    Box(Modifier.fillMaxWidth().height(216.dp)) {
        AsyncImage(
            model = art.url,
            contentDescription = art.alt,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // The photograph SHOWS (owner, 2026-08-25): no full veil — a slim top
        // scrim for the kicker and a bottom-third scrim for the verse, so the
        // words occupy only the image's lower third (iOS parity).
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.28f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0.5f to Color.Transparent,
                    0.78f to Color(0xFF0A1C33).copy(alpha = 0.85f),
                    1f to Color(0xFF06111F).copy(alpha = 0.95f),
                ),
            ),
        )
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📖  VERSE FOR TODAY", style = NuruType.micro, color = GoldLight, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(version.uppercase(), style = NuruType.micro, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(Spacing.base)) {
            if (!text.isNullOrBlank()) {
                // Smaller, lower-third (owner, 2026-08-25) — long verses step down.
                val size = when {
                    text.length > 220 -> 12.sp
                    text.length > 140 -> 13.sp
                    else -> 14.sp
                }
                Text(
                    "“$text”",
                    style = NuruType.rowTitle.copy(fontSize = size, lineHeight = size * 1.35),
                    color = Color.White, maxLines = 4,
                )
                Spacer(Modifier.height(5.dp))
            }
            Text(refLine, style = NuruType.micro.copy(fontSize = 11.sp), color = GoldLight, fontWeight = FontWeight.Bold)
        }
    }
}

/** Two hairlines meeting at a small gold cross — a rest for the eye. */
@Composable
fun SelahDivider() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 44.dp).height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Nuru.gold.copy(alpha = 0.45f)))),
        )
        Spacer(Modifier.width(14.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 2.5.dp, height = 13.dp).background(Nuru.gold.copy(alpha = 0.75f), RoundedCornerShape(1.dp)))
            Box(Modifier.size(width = 9.dp, height = 2.5.dp).offset(y = (-2).dp).background(Nuru.gold.copy(alpha = 0.75f), RoundedCornerShape(1.dp)))
        }
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier.weight(1f).height(1.dp)
                .background(Brush.horizontalGradient(listOf(Nuru.gold.copy(alpha = 0.45f), Color.Transparent))),
        )
    }
}

/**
 * Renders the postcard (1080×1332: art, scrim, serif verse, reference, brand
 * line) fully offscreen and opens the system share sheet. Returns false on
 * any failure so the caller can quietly fall back to sharing the words.
 */
object VerseImageShare {
    suspend fun share(context: Context, art: VerseArt, verseText: String, reference: String, version: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val loader = coil.ImageLoader(context)
                val req = coil.request.ImageRequest.Builder(context).data(art.url).allowHardware(false).build()
                val src = ((loader.execute(req) as? coil.request.SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                    ?: return@runCatching false

                val w = 1080; val h = 1332
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)

                // Center-crop the photograph onto the canvas.
                val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
                val dw = src.width * scale; val dh = src.height * scale
                c.drawBitmap(src, null, RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))

                // Scrim.
                val scrim = Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                        intArrayOf(0x1A000000, 0x0D000000, 0xB8000000.toInt()),
                        floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                }
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), scrim)

                val fraunces = ResourcesCompat.getFont(context, R.font.fraunces_medium)
                val interBold = ResourcesCompat.getFont(context, R.font.inter_bold)
                val interSemi = ResourcesCompat.getFont(context, R.font.inter_semibold)
                val pad = 62f

                // Verse text (bottom-anchored stack, drawn upward).
                val versePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = fraunces; color = android.graphics.Color.WHITE
                    textSize = if (verseText.length > 200) 46f else 56f
                    setShadowLayer(6f, 0f, 2f, 0x66000000)
                }
                val layout = StaticLayout.Builder
                    .obtain("“$verseText”", 0, verseText.length + 2, versePaint, (w - 2 * pad).toInt())
                    .setLineSpacing(10f, 1f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

                val brandH = 46f; val refH = 58f
                val verseTop = h - pad - brandH - refH - layout.height
                c.save(); c.translate(pad, verseTop.toFloat()); layout.draw(c); c.restore()

                val refPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = interBold; color = 0xFFF2DDA0.toInt(); textSize = 34f
                    setShadowLayer(4f, 0f, 2f, 0x66000000)
                }
                c.drawText("$reference · ${version.uppercase()}", pad, verseTop + layout.height + 44f, refPaint)

                // Brand line: a small gold cross + "Nuru Pathway".
                val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9A227.toInt() }
                val by = verseTop + layout.height + refH + 24f
                c.drawRoundRect(RectF(pad + 10f, by, pad + 16f, by + 30f), 3f, 3f, gold)
                c.drawRoundRect(RectF(pad + 2f, by + 7f, pad + 24f, by + 13f), 3f, 3f, gold)
                val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = interSemi; color = 0xCCFFFFFF.toInt(); textSize = 30f
                }
                c.drawText("Nuru Pathway", pad + 36f, by + 24f, brandPaint)

                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, "verse-${System.currentTimeMillis()}.jpg")
                file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(send, "Share today's verse"))
                }
                true
            }.getOrDefault(false)
        }
}
