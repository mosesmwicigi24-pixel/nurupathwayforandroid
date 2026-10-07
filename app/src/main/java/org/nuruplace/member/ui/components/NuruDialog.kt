// Dialogs that keep the app's density. A Compose Dialog — and Material's
// AlertDialog, built on it — is its own window, and the root of its
// composition provides the platform's own LocalDensity. NuruTheme folds the
// member's text size from the app's settings (AppPrefs.textScale) into the
// density's font scale, so inside a plain Dialog that choice was lost: the
// Sunday Letter, Nuru Coach, Explain, a notice's detail, the Selah editor and
// drawing pad, the prayer composer, the voice note recorder and the location
// invite all read at the phone's size, not the member's.
//
// These two carry the density across: NuruDialog for a Dialog, NuruAlertDialog
// (Material's AlertDialog, its slots each re-given the density) for an alert.
// DialogDensitySourceTest holds the line — no Compose Dialog outside this file.
package org.nuruplace.member.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A [Dialog] whose content reads at the app's density — the member's text size included. */
@Composable
fun NuruDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val appDensity = LocalDensity.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalDensity provides appDensity, content = content)
    }
}

/** Material's [AlertDialog], with the same parameters, whose slots read at
 *  the app's density — the member's text size included. */
@Composable
fun NuruAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    val appDensity = LocalDensity.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = withDensity(appDensity, confirmButton),
        modifier = modifier,
        dismissButton = dismissButton?.let { withDensity(appDensity, it) },
        icon = icon?.let { withDensity(appDensity, it) },
        title = title?.let { withDensity(appDensity, it) },
        text = text?.let { withDensity(appDensity, it) },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** [slot], composed at [density]. */
private fun withDensity(density: Density, slot: @Composable () -> Unit): @Composable () -> Unit =
    { CompositionLocalProvider(LocalDensity provides density, content = slot) }
