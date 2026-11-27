package com.etologic.mahjongtournamentsuite.presentation.components

import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties

/**
 * The back closes the dialog that calls this, and only the dialog, when [dismissOnBack]. Without it the back does
 * nothing while the dialog shows. Android, iOS and the desktop already do that for every dialog. On the web the
 * browser back and the back gesture of a phone go through the browser history, so the dialog adds an entry there.
 * Call it next to the dialog, with its `onDismissRequest`.
 */
@Composable
expect fun DialogBackHandler(dismissOnBack: Boolean = true, onDismiss: () -> Unit)

/**
 * The `AlertDialog` of Material 3, with the back of [DialogBackHandler]. The screens use this one, so the browser
 * back closes the dialog on the web too.
 */
@Composable
fun AlertDialog(
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
    DialogBackHandler(properties.dismissOnBackPress, onDismissRequest)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}
