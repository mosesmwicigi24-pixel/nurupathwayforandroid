package org.nuruplace.member.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.nuruplace.member.ui.theme.TypeSourceScan

/**
 * The member's text size reaches every dialog (owner follow-up, 2026-10-07).
 * A Compose Dialog is its own window, and its root provides the platform's
 * density — NuruTheme's, which carries AppPrefs.textScale, was lost inside.
 * NuruDialog and NuruAlertDialog carry it across; this holds the line:
 *  · no plain Compose Dialog outside NuruDialog.kt;
 *  · the helpers give their content the app's density;
 *  · Material AlertDialogs not yet through NuruAlertDialog may fall, never rise.
 */
class DialogDensitySourceTest {
    private companion object {
        const val HELPER = "org/nuruplace/member/ui/components/NuruDialog.kt"

        /** Material AlertDialogs still drawn directly: 29 when NuruAlertDialog
         *  arrived, 2 moved (the prayer composer, the voice note recorder). */
        const val ALERT_CEILING = 27

        val sources by lazy { TypeSourceScan.load() }
        val PLAIN_DIALOG = Regex("""(?<![A-Za-z0-9_])Dialog\(""")
        val ALERT_DIALOG = Regex("""(?<![A-Za-z0-9_])AlertDialog\(""")
    }

    private fun sites(pattern: Regex, from: List<TypeSourceScan.Source>): List<String> =
        from.filter { it.path != HELPER }.flatMap { src ->
            pattern.findAll(src.code).map { "${src.path}:${src.lineOf(it.range.first)}" }.toList()
        }

    @Test fun `no Compose Dialog outside NuruDialog — each keeps the member's text size`() {
        val plain = sites(PLAIN_DIALOG, sources)
        assertEquals("plain Compose Dialogs — use NuruDialog:\n" + plain.joinToString("\n"), emptyList<String>(), plain)
    }

    @Test fun `the helpers give their content the app's density`() {
        val code = sources.single { it.path == HELPER }.code
        val dialog = code.substringAfter("fun NuruDialog(").substringBefore("fun NuruAlertDialog(")
        // Captured outside the window, provided inside it.
        assertTrue(dialog.indexOf("LocalDensity.current") in 0 until dialog.indexOf("Dialog(onDismissRequest"))
        assertTrue("NuruDialog provides the app's density to its content", "LocalDensity provides appDensity, content = content" in dialog)
        val alert = code.substringAfter("fun NuruAlertDialog(").substringBefore("private fun withDensity(")
        for (slot in listOf("confirmButton = withDensity(", "dismissButton = dismissButton?.let { withDensity(", "icon = icon?.let { withDensity(", "title = title?.let { withDensity(", "text = text?.let { withDensity(")) {
            assertTrue("NuruAlertDialog gives $slot the app's density", slot in alert)
        }
        assertTrue("withDensity provides it", "LocalDensity provides density" in code.substringAfter("private fun withDensity("))
    }

    @Test fun `Material alert dialogs not through NuruAlertDialog — the count may fall, never rise`() {
        val alerts = sites(ALERT_DIALOG, sources)
        val listing = alerts.joinToString("\n") { "  $it" }
        if (alerts.size > ALERT_CEILING) fail("AlertDialogs rose to ${alerts.size} (ceiling $ALERT_CEILING) — use NuruAlertDialog:\n$listing")
        if (alerts.size < ALERT_CEILING) fail("AlertDialogs fell to ${alerts.size} — lower the ceiling from $ALERT_CEILING to ${alerts.size}:\n$listing")
    }

    @Test fun `the scan sees a plain Dialog, and not the ones that keep the density`() {
        val code = """
            fun A() { Dialog(onDismissRequest = {}) { } }
            fun B() { androidx.compose.ui.window.Dialog(onDismissRequest = {}) { } }
            fun C() { NuruDialog(onDismissRequest = {}) { } }
            fun D() { LetterDialog(letter) ; AlertDialog(onDismissRequest = {}, confirmButton = {}) }
            // Dialog( in a comment is not code
            val s = "Dialog( in a string is not code"
        """.trimIndent()
        val src = TypeSourceScan.Source("demo/Demo.kt", code)
        assertEquals(listOf("demo/Demo.kt:1", "demo/Demo.kt:2"), sites(PLAIN_DIALOG, listOf(src)))
        assertEquals(listOf("demo/Demo.kt:4"), sites(ALERT_DIALOG, listOf(src)))
    }
}
