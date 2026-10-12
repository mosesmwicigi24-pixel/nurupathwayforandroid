package org.nuruplace.member.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.nuruplace.member.ui.theme.TypeSourceScan

/**
 * The member's text size reaches every window (owner, 2026-10-07: "the owner
 * asked about large text"). A Compose Dialog — and Material's AlertDialog,
 * DatePickerDialog, ModalBottomSheet and DropdownMenu, each a window of its
 * own — composes under a root that provides the platform's density, so
 * NuruTheme's (which carries AppPrefs.textScale) was lost inside. The Nuru*
 * wrappers in NuruDialog.kt carry it across; this holds the line, with the
 * comment- and string-aware scan the type and icon checks use:
 *  · no window of those kinds opened directly outside NuruDialog.kt — each
 *    ratchet taken to 0 (the counts when this began are written beside it);
 *  · each wrapper gives its content the density it was called with;
 *  · no wrapper builds a density of its own, so nothing is scaled twice.
 */
class DialogDensitySourceTest {
    private companion object {
        const val HELPER = "org/nuruplace/member/ui/components/NuruDialog.kt"

        val sources by lazy { TypeSourceScan.load() }

        /** Each kind of window, the wrapper that replaces it, and how many
         *  were opened directly when the wrapper arrived (all now 0). */
        val KINDS = listOf(
            Kind("Dialog", "NuruDialog", before = 7),
            Kind("AlertDialog", "NuruAlertDialog", before = 29),
            Kind("ModalBottomSheet", "NuruModalBottomSheet", before = 20),
            Kind("DropdownMenu", "NuruDropdownMenu", before = 11),
            Kind("DatePickerDialog", "NuruDatePickerDialog", before = 3),
            // Not used today; listed so the first one goes through a wrapper.
            Kind("BasicAlertDialog", "NuruAlertDialog", before = 0),
            Kind("TimePickerDialog", "a NuruDatePickerDialog-style wrapper", before = 0),
            Kind("ExposedDropdownMenu", "a NuruDropdownMenu-style wrapper", before = 0),
            Kind("Popup", "a NuruDialog-style wrapper", before = 0),
        )
    }

    data class Kind(val name: String, val wrapper: String, val before: Int) {
        val call = Regex("""(?<![A-Za-z0-9_])$name\(""")
    }

    private fun sites(pattern: Regex, from: List<TypeSourceScan.Source>): List<String> =
        from.filter { it.path != HELPER }.flatMap { src ->
            pattern.findAll(src.code).map { "${src.path}:${src.lineOf(it.range.first)}" }.toList()
        }

    @Test fun `no window is opened directly outside NuruDialog — every ratchet is at 0`() {
        val open = KINDS.associateWith { sites(it.call, sources) }.filterValues { it.isNotEmpty() }
        if (open.isNotEmpty()) {
            fail(open.entries.joinToString("\n") { (k, at) -> "${k.name}: ${at.size}, ratchet 0 (was ${k.before}) — use ${k.wrapper}:\n" + at.joinToString("\n") { "  $it" } })
        }
    }

    @Test fun `each wrapper gives its content the density it was called with`() {
        val code = sources.single { it.path == HELPER }.code
        fun body(name: String): String {
            val start = code.indexOf("fun $name(")
            assertTrue("$name is in NuruDialog.kt", start >= 0)
            val next = Regex("""\bfun [A-Za-z]+\(""").find(code, start + 4)?.range?.first ?: code.length
            return code.substring(start, next)
        }
        for (name in listOf("NuruDialog", "NuruAlertDialog", "NuruModalBottomSheet", "NuruDropdownMenu", "NuruDatePickerDialog")) {
            val b = body(name)
            val captured = b.indexOf("val appDensity = LocalDensity.current")
            assertTrue("$name captures the density it was called with", captured >= 0)
            assertTrue("$name provides it inside its window", "LocalDensity provides appDensity" in b || "withDensity(appDensity" in b)
        }
        val alert = body("NuruAlertDialog")
        for (slot in listOf("confirmButton = withDensity(", "dismissButton = dismissButton?.let { withDensity(", "icon = icon?.let { withDensity(", "title = title?.let { withDensity(", "text = text?.let { withDensity(")) {
            assertTrue("NuruAlertDialog gives $slot the density", slot in alert)
        }
        val picker = body("NuruDatePickerDialog")
        assertTrue("confirmButton = withDensity(" in picker && "dismissButton = dismissButton?.let { withDensity(" in picker)
        assertTrue("LocalDensity provides appDensity) { column.content() }" in picker)
        for (name in listOf("NuruModalBottomSheet", "NuruDropdownMenu")) {
            assertTrue("$name gives its content the density", "LocalDensity provides appDensity) { column.content() }" in body(name))
        }
        assertTrue("withDensity provides it", "LocalDensity provides density" in body("withDensity"))
    }

    @Test fun `no wrapper builds a density of its own — nothing is scaled twice`() {
        val code = sources.single { it.path == HELPER }.code
        // A wrapper inside a wrapper captures the density the outer one
        // provided — the same one — and passes it on unchanged.
        assertFalse("a new Density( in NuruDialog.kt would scale a second time", Regex("""(?<![A-Za-z0-9_.])Density\(""").containsMatchIn(code))
        assertFalse("nor any font-scale arithmetic", "fontScale" in code)
    }

    @Test fun `the scan sees each kind, and not the wrappers, comments or strings`() {
        val code = """
            fun A() { Dialog(onDismissRequest = {}) { } }
            fun B() { androidx.compose.ui.window.Dialog(onDismissRequest = {}) { } }
            fun C() { NuruDialog(onDismissRequest = {}) { } ; NuruAlertDialog(onDismissRequest = {}, confirmButton = {}) }
            fun D() { LetterDialog(letter) ; AlertDialog(onDismissRequest = {}, confirmButton = {}) }
            fun E() { ModalBottomSheet(onDismissRequest = {}) { } ; NuruModalBottomSheet(onDismissRequest = {}) { } }
            fun F() { DropdownMenu(expanded = true, onDismissRequest = {}) { DropdownMenuItem(text = {}, onClick = {}) } }
            fun G() { DatePickerDialog(onDismissRequest = {}, confirmButton = {}) { DatePicker(state = s) } }
            // Dialog( AlertDialog( ModalBottomSheet( DropdownMenu( in a comment are not code
            val s = "DatePickerDialog( in a string is not code"
        """.trimIndent()
        val src = listOf(TypeSourceScan.Source("demo/Demo.kt", code))
        fun at(name: String) = sites(KINDS.single { it.name == name }.call, src)
        assertEquals(listOf("demo/Demo.kt:1", "demo/Demo.kt:2"), at("Dialog"))
        assertEquals(listOf("demo/Demo.kt:4"), at("AlertDialog"))
        assertEquals(listOf("demo/Demo.kt:5"), at("ModalBottomSheet"))
        assertEquals(listOf("demo/Demo.kt:6"), at("DropdownMenu"))
        assertEquals(listOf("demo/Demo.kt:7"), at("DatePickerDialog"))
        assertEquals(emptyList<String>(), at("Popup"))
    }
}
