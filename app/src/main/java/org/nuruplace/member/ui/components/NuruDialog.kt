// Windows that keep the app's density. A Compose Dialog — and everything
// built on a window of its own: Material's AlertDialog and DatePickerDialog,
// ModalBottomSheet, a DropdownMenu's popup — composes under a root that
// provides the platform's own LocalDensity. NuruTheme folds the member's text
// size from the app's settings (AppPrefs.textScale) into the density's font
// scale, so inside any of them that choice was lost: they read at the
// phone's size, not the member's.
//
// These carry the density across, each by providing the density it was
// called with (the app's, from NuruTheme) to its content — never a new one,
// so a wrapper inside a wrapper passes the same density on and nothing is
// scaled twice:
//  · NuruDialog — a Dialog;
//  · NuruAlertDialog — Material's AlertDialog, its slots each re-given it;
//  · NuruModalBottomSheet — Material's ModalBottomSheet;
//  · NuruDropdownMenu — Material's DropdownMenu;
//  · NuruDatePickerDialog — Material's DatePickerDialog, its buttons and content.
// DialogDensitySourceTest holds the line: none of those windows is opened
// directly outside this file.
package org.nuruplace.member.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
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

/** Material's [ModalBottomSheet], as the app calls it, whose content reads at
 *  the app's density — the member's text size included. */
@ExperimentalMaterial3Api
@Composable
fun NuruModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appDensity = LocalDensity.current
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState, containerColor = containerColor) {
        val column = this
        CompositionLocalProvider(LocalDensity provides appDensity) { column.content() }
    }
}

/** Material's [DropdownMenu], as the app calls it, whose items read at the
 *  app's density — the member's text size included. */
@Composable
fun NuruDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    containerColor: Color = MenuDefaults.containerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appDensity = LocalDensity.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, containerColor = containerColor) {
        val column = this
        CompositionLocalProvider(LocalDensity provides appDensity) { column.content() }
    }
}

/** Material's [DatePickerDialog], as the app calls it, whose buttons and
 *  calendar read at the app's density — the member's text size included. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuruDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appDensity = LocalDensity.current
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = withDensity(appDensity, confirmButton),
        dismissButton = dismissButton?.let { withDensity(appDensity, it) },
    ) {
        val column = this
        CompositionLocalProvider(LocalDensity provides appDensity) { column.content() }
    }
}

/** [slot], composed at [density]. */
private fun withDensity(density: Density, slot: @Composable () -> Unit): @Composable () -> Unit =
    { CompositionLocalProvider(LocalDensity provides density, content = slot) }
