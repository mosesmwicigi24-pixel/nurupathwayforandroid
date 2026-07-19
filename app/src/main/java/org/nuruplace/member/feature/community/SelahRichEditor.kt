// Selah's rich-text engine: an EditText bridged into Compose (bold, italic,
// color, font — the four traits ThoughtSpan can persist) plus the plain-text
// <-> Editable spans <-> [ThoughtSpan] round-trip that keeps the wire shape
// exactly what packages/backend/src/modules/thoughts/service.ts expects.
//
// "Spacing" (owner spec) is real but intentionally NOT per-span: the backend's
// ThoughtSpan has no spacing field (only start/end/bold/italic/color/font), so
// baking a line-height into one run would silently do nothing for the rest of
// the note. Line spacing is instead a single global reading/writing preference
// applied to the whole EditText (setLineSpacing) — honest to what the schema
// can actually carry, still a real working control. Mirrors the iOS build's
// SelahRichEditor.swift exactly (same enums, same reasoning).
package org.nuruplace.member.feature.community

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.StyleSpan
import android.widget.EditText
import androidx.compose.ui.graphics.Color
import androidx.core.content.res.ResourcesCompat
import org.nuruplace.member.R
import org.nuruplace.member.data.net.ThoughtSpan

// MARK: - Fonts a member can pick for a run of their thought

enum class SelahFont(val key: String, val label: String) {
    INTER("inter", "Inter"),
    FRAUNCES("fraunces", "Fraunces"),
    SERIF("serif", "Serif"),
    CURSIVE("cursive", "Cursive"),
    ;

    fun typeface(context: Context): Typeface = when (this) {
        INTER -> ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.DEFAULT
        FRAUNCES -> ResourcesCompat.getFont(context, R.font.fraunces_regular) ?: Typeface.SERIF
        SERIF -> Typeface.SERIF
        CURSIVE -> Typeface.create("cursive", Typeface.NORMAL)
    }

    companion object {
        fun fromKey(k: String?): SelahFont = entries.firstOrNull { it.key == k } ?: INTER
    }
}

/** Line spacing presets (global preference — see header note). */
enum class SelahSpacing(val label: String, val extraPx: Float) {
    COZY("Cozy", 4f),
    COMFORTABLE("Comfortable", 9f),
    RELAXED("Relaxed", 15f),
    ;

    companion object {
        fun fromName(n: String?): SelahSpacing = entries.firstOrNull { it.name == n } ?: COMFORTABLE
    }
}

// MARK: - Color palette a span.color can hold ("#RRGGBB")

enum class SelahColor(val hex: String, val label: String) {
    INK("#0B1F33", "Ink"),
    GOLD("#C9A227", "Gold"),
    CRIMSON("#B91C1C", "Crimson"),
    FOREST("#166534", "Forest"),
    OCEAN("#1D4ED8", "Ocean"),
    PLUM("#7C3AED", "Plum"),
    ;

    val composeColor: Color get() = Color(android.graphics.Color.parseColor(hex))
    val argb: Int get() = android.graphics.Color.parseColor(hex)
}

/** A custom family swap that preserves whatever bold/italic style is already
 *  on the paint (from a co-located StyleSpan) — minSdk 26 predates the stock
 *  API-28 `android.text.style.TypefaceSpan(Typeface)`, so this is the standard
 *  Android idiom for a pre-28 custom-font span. Carries [fontKey] alongside
 *  the resolved [Typeface] so extraction can read the SelahFont back directly
 *  instead of trying to reverse-map a Typeface instance (ResourcesCompat.getFont
 *  gives no identity guarantee across calls). */
class CustomTypefaceSpan(val fontKey: String, private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(ds: TextPaint) = apply(ds)
    override fun updateMeasureState(paint: TextPaint) = apply(paint)
    private fun apply(paint: Paint) {
        val oldStyle = paint.typeface?.style ?: Typeface.NORMAL
        paint.typeface = Typeface.create(typeface, oldStyle)
    }
}

object SelahRichText {
    const val BASE_COLOR_HEX = "#0B1F33"

    /** Compose the editor's starting Editable from the stored plain body + its
     *  formatting spans (server truth → on-screen truth). */
    fun build(context: Context, body: String, spans: List<ThoughtSpan>?): Editable {
        val sb = SpannableStringBuilder(body)
        val length = body.length
        for (span in spans.orEmpty()) {
            val start = span.start.coerceIn(0, length)
            val end = span.end.coerceIn(start, length)
            if (end <= start) continue
            if (span.bold == true) sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (span.italic == true) sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.color?.let { hex ->
                runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()?.let {
                    sb.setSpan(ForegroundColorSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            span.font?.let { key ->
                val font = SelahFont.entries.firstOrNull { it.key == key }
                if (font != null && font != SelahFont.INTER) {
                    sb.setSpan(CustomTypefaceSpan(font.key, font.typeface(context)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return sb
    }

    /** Reverse direction: scan the edited Editable's spans and emit one
     *  ThoughtSpan per (start,end) boundary run that carries any formatting. */
    fun extract(editable: Editable): Pair<String, List<ThoughtSpan>> {
        val text = editable.toString()
        val boundaries = sortedSetOf(0, text.length)
        val styleSpans = editable.getSpans(0, text.length, StyleSpan::class.java)
        val colorSpans = editable.getSpans(0, text.length, ForegroundColorSpan::class.java)
        val fontSpans = editable.getSpans(0, text.length, CustomTypefaceSpan::class.java)
        (styleSpans.asSequence() + colorSpans.asSequence() + fontSpans.asSequence()).forEach { s ->
            boundaries.add(editable.getSpanStart(s))
            boundaries.add(editable.getSpanEnd(s))
        }
        val cuts = boundaries.filter { it in 0..text.length }.sorted()
        val spans = mutableListOf<ThoughtSpan>()
        var pending: ThoughtSpan? = null
        for (i in 0 until cuts.size - 1) {
            val start = cuts[i]
            val end = cuts[i + 1]
            if (start >= end) continue
            val bold = styleSpans.any { editable.getSpanStart(it) <= start && editable.getSpanEnd(it) >= end && it.style == Typeface.BOLD }
            val italic = styleSpans.any { editable.getSpanStart(it) <= start && editable.getSpanEnd(it) >= end && it.style == Typeface.ITALIC }
            val colorSpan = colorSpans.firstOrNull { editable.getSpanStart(it) <= start && editable.getSpanEnd(it) >= end }
            val colorHex = colorSpan?.let { String.format("#%06X", 0xFFFFFF and it.foregroundColor) }
            val fontSpan = fontSpans.firstOrNull { editable.getSpanStart(it) <= start && editable.getSpanEnd(it) >= end }
            val fontKey = fontSpan?.fontKey

            val differs = bold || italic || (colorHex != null && colorHex != BASE_COLOR_HEX) || fontKey != null
            if (!differs) { pending?.let { spans.add(it) }; pending = null; continue }
            val candidate = ThoughtSpan(
                start = start, end = end,
                bold = if (bold) true else null,
                italic = if (italic) true else null,
                color = if (colorHex != null && colorHex != BASE_COLOR_HEX) colorHex else null,
                font = fontKey,
            )
            val p = pending
            if (p != null && p.end == candidate.start && p.bold == candidate.bold && p.italic == candidate.italic &&
                p.color == candidate.color && p.font == candidate.font
            ) {
                pending = p.copy(end = candidate.end)
            } else {
                p?.let { spans.add(it) }
                pending = candidate
            }
        }
        pending?.let { spans.add(it) }
        return text to spans.take(2000)
    }
}

/** Live controller bridging Compose toolbar buttons ↔ the hosted EditText. */
class RichEditorController {
    var editText: EditText? = null
    var selectionBold: Boolean = false
        private set
    var selectionItalic: Boolean = false
        private set
    var onSelectionChanged: (() -> Unit)? = null

    fun refreshSelectionState() {
        val et = editText ?: return
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(start)
        val probeEnd = if (end > start) end else (start + 1).coerceAtMost(et.text.length)
        val probeStart = if (end > start) start else (start - 1).coerceAtLeast(0)
        if (probeEnd <= probeStart) { selectionBold = false; selectionItalic = false; onSelectionChanged?.invoke(); return }
        val styles = et.text.getSpans(probeStart, probeEnd, StyleSpan::class.java)
        selectionBold = styles.any { it.style == Typeface.BOLD && et.text.getSpanStart(it) <= probeStart && et.text.getSpanEnd(it) >= probeEnd }
        selectionItalic = styles.any { it.style == Typeface.ITALIC && et.text.getSpanStart(it) <= probeStart && et.text.getSpanEnd(it) >= probeEnd }
        onSelectionChanged?.invoke()
    }

    fun toggleBold() = toggleStyle(Typeface.BOLD)
    fun toggleItalic() = toggleStyle(Typeface.ITALIC)

    private fun toggleStyle(style: Int) {
        val et = editText ?: return
        val (start, end) = selectionOrWordRange(et)
        if (start >= end) return
        val editable = et.text
        val existing = editable.getSpans(start, end, StyleSpan::class.java)
            .filter { it.style == style && editable.getSpanStart(it) <= start && editable.getSpanEnd(it) >= end }
        if (existing.isNotEmpty()) {
            existing.forEach { editable.removeSpan(it) }
        } else {
            editable.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        et.text = editable
        et.setSelection(start, end)
        refreshSelectionState()
    }

    fun applyColor(color: SelahColor) {
        val et = editText ?: return
        val (start, end) = selectionOrWordRange(et)
        if (start >= end) return
        val editable = et.text
        editable.getSpans(start, end, ForegroundColorSpan::class.java).forEach { editable.removeSpan(it) }
        editable.setSpan(ForegroundColorSpan(color.argb), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        et.text = editable
        et.setSelection(start, end)
    }

    fun applyFont(context: Context, font: SelahFont) {
        val et = editText ?: return
        val (start, end) = selectionOrWordRange(et)
        if (start >= end) return
        val editable = et.text
        editable.getSpans(start, end, CustomTypefaceSpan::class.java).forEach { editable.removeSpan(it) }
        if (font != SelahFont.INTER) {
            editable.setSpan(CustomTypefaceSpan(font.key, font.typeface(context)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        et.text = editable
        et.setSelection(start, end)
    }

    fun applySpacing(spacing: SelahSpacing) {
        editText?.setLineSpacing(spacing.extraPx, 1f)
    }

    /** Formatting applies to a real selection. Unlike iOS's UITextView (which
     *  has "typing attributes" that carry forward from an empty cursor),
     *  EditText has no such concept without a custom InputConnection — so with
     *  nothing selected, start == end and the caller's `if (start >= end) return`
     *  makes the toolbar button a no-op. The member selects text, then formats
     *  it, which is the standard Android rich-text idiom (Docs, Keep, Notion). */
    private fun selectionOrWordRange(et: EditText): Pair<Int, Int> {
        val start = et.selectionStart.coerceIn(0, et.text.length)
        val end = et.selectionEnd.coerceIn(0, et.text.length)
        return start to end
    }
}
