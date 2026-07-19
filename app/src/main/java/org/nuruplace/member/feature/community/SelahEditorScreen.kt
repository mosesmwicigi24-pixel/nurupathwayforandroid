// Selah's quiet page — the rich-text + pen editor for one thought. New-or-
// existing, always full screen (this is the "write what's on your heart"
// surface). Bold/italic/color/font persist per span (ThoughtSpan); line
// spacing is a global preference (see SelahRichEditor.kt header note);
// drawings upload via the existing me/media/image flow and attach as
// `drawing_urls`. Port of iOS SelahEditorView.
package org.nuruplace.member.feature.community

import android.text.Editable
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import org.nuruplace.member.data.net.Thought
import org.nuruplace.member.data.net.ThoughtSpan
import org.nuruplace.member.ui.components.Haptics
import org.nuruplace.member.ui.theme.Nuru
import org.nuruplace.member.ui.theme.NuruType
import org.nuruplace.member.ui.theme.Spacing
import java.util.UUID

/** A new-or-existing thought being edited. */
data class SelahDraft(
    val thoughtId: String,
    val isNew: Boolean,
    var title: String,
    var body: String,
    var spans: List<ThoughtSpan>,
    var drawingUrls: List<String>,
) {
    constructor() : this(UUID.randomUUID().toString(), true, "", "", emptyList(), emptyList())
    constructor(t: Thought) : this(t.thoughtId, false, t.title ?: "", t.body, t.bodySpans ?: emptyList(), t.drawingUrls)
}

@Composable
fun SelahEditorScreen(
    draft: SelahDraft,
    onDismiss: () -> Unit,
    onSave: (SelahDraft) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val view = LocalView.current
    val context = LocalContext.current
    var title by remember { mutableStateOf(draft.title) }
    var drawingUrls by remember { mutableStateOf(draft.drawingUrls) }
    val controller = remember { RichEditorController() }
    var isEmpty by remember { mutableStateOf(draft.body.isBlank()) }
    var spacing by remember { mutableStateOf(SelahSpacing.COMFORTABLE) }
    var showDrawing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var fontMenuOpen by remember { mutableStateOf(false) }
    var colorMenuOpen by remember { mutableStateOf(false) }
    var spacingMenuOpen by remember { mutableStateOf(false) }
    // Held so Save can read the live Editable straight from the EditText.
    var editableRef by remember { mutableStateOf<Editable?>(null) }

    Column(Modifier.fillMaxSize().background(Nuru.paper).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Nuru.surface).clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Close", tint = Nuru.navy, modifier = Modifier.size(14.dp)) }
            Spacer(Modifier.weight(1f))
            Text("SELAH", style = NuruType.sectionLabel, color = Nuru.eyebrow)
            Spacer(Modifier.weight(1f))
            if (!draft.isNew) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Nuru.surface)
                        .clickable { Haptics.tap(view); pendingDelete = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Delete, "Delete thought", tint = Nuru.danger, modifier = Modifier.size(14.dp)) }
            } else {
                Box(Modifier.size(32.dp))
            }
        }

        androidx.compose.foundation.text.BasicTextField(
            value = title,
            onValueChange = { title = it },
            textStyle = NuruType.title.copy(color = Nuru.navy),
            singleLine = true,
            decorationBox = { inner ->
                if (title.isEmpty()) Text("Untitled", style = NuruType.title, color = Nuru.ink300)
                inner()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )

        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = Spacing.md)) {
            if (isEmpty) {
                Text(
                    "Pause here — write your first thought.",
                    style = NuruType.bodyLg, color = Nuru.ink300,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    EditText(ctx).apply {
                        setText(SelahRichText.build(ctx, draft.body, draft.spans))
                        setTextColor(android.graphics.Color.parseColor(SelahRichText.BASE_COLOR_HEX))
                        textSize = 16f
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setPadding(28, 28, 28, 28)
                        setLineSpacing(spacing.extraPx, 1f)
                        controller.editText = this
                        controller.onSelectionChanged = {}
                        editableRef = text
                        addTextChangedListener(
                            object : android.text.TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                    isEmpty = s.isNullOrBlank()
                                }
                                override fun afterTextChanged(s: Editable?) { editableRef = s }
                            },
                        )
                        setOnFocusChangeListener { _, _ -> controller.refreshSelectionState() }
                    }
                },
            )
        }

        if (drawingUrls.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(top = Spacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screen),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(drawingUrls) { url ->
                    Box(Modifier.size(72.dp)) {
                        AsyncImage(
                            model = url, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).border(1.dp, Nuru.border, RoundedCornerShape(12.dp)),
                        )
                        Box(
                            Modifier.size(18.dp).align(Alignment.TopEnd).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { Haptics.tap(view); drawingUrls = drawingUrls - url },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(10.dp)) }
                    }
                }
            }
        }

        // Toolbar: bold · italic · color · font · spacing · draw
        Row(
            Modifier.fillMaxWidth().background(Nuru.white)
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarIcon(Icons.Filled.FormatBold, controller.selectionBold) { Haptics.tap(view); controller.toggleBold() }
            ToolbarIcon(Icons.Filled.FormatItalic, controller.selectionItalic) { Haptics.tap(view); controller.toggleItalic() }
            Box {
                ToolbarIcon(Icons.Filled.FormatColorText, false) { colorMenuOpen = true }
                DropdownMenu(expanded = colorMenuOpen, onDismissRequest = { colorMenuOpen = false }) {
                    SelahColor.entries.forEach { c ->
                        DropdownMenuItem(text = { Text(c.label) }, onClick = {
                            colorMenuOpen = false; Haptics.tap(view); controller.applyColor(c)
                        })
                    }
                }
            }
            Box {
                ToolbarIcon(Icons.Filled.TextFields, false) { fontMenuOpen = true }
                DropdownMenu(expanded = fontMenuOpen, onDismissRequest = { fontMenuOpen = false }) {
                    SelahFont.entries.forEach { f ->
                        DropdownMenuItem(text = { Text(f.label) }, onClick = {
                            fontMenuOpen = false; Haptics.tap(view); controller.applyFont(context, f)
                        })
                    }
                }
            }
            Box {
                ToolbarIcon(Icons.Filled.FormatLineSpacing, false) { spacingMenuOpen = true }
                DropdownMenu(expanded = spacingMenuOpen, onDismissRequest = { spacingMenuOpen = false }) {
                    SelahSpacing.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = {
                            spacingMenuOpen = false; Haptics.tap(view); spacing = s; controller.applySpacing(s)
                        })
                    }
                }
            }
            if (isPenCapableDevice(context)) {
                ToolbarIcon(Icons.Filled.EditNote, false) { Haptics.tap(view); showDrawing = true }
            }
        }

        Box(
            Modifier.fillMaxWidth().padding(Spacing.screen).height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (!isEmpty) Nuru.gold else Nuru.ink300)
                .clickable(enabled = !isEmpty) {
                    Haptics.confirm(view)
                    val editable = editableRef ?: controller.editText?.text
                    val (body, spans) = editable?.let { SelahRichText.extract(it) } ?: (draft.body to draft.spans)
                    draft.title = title.trim()
                    draft.body = body
                    draft.spans = spans
                    draft.drawingUrls = drawingUrls
                    onSave(draft)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (draft.isNew) "Save thought" else "Save changes", style = NuruType.cardCta, color = Nuru.navy)
        }
    }

    if (showDrawing) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDrawing = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SelahDrawingSheet(
                onDismiss = { showDrawing = false },
                onSave = { url -> drawingUrls = drawingUrls + url; showDrawing = false },
            )
        }
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { pendingDelete = false },
            title = { Text("Delete this thought?", style = NuruType.rowTitle, color = Nuru.ink) },
            text = { Text("It will be removed from Selah on every device.", style = NuruType.body, color = Nuru.ink600) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = false; onDelete?.invoke() }) {
                    Text("Delete thought", style = NuruType.cardCta, color = Nuru.danger)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = false }) { Text("Keep it", style = NuruType.cardCta, color = Nuru.ink600) } },
            containerColor = Nuru.white,
        )
    }
}

@Composable
private fun ToolbarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
            .background(if (active) Nuru.navy else Nuru.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) Color.White else Nuru.navy, modifier = Modifier.size(17.dp))
    }
}
