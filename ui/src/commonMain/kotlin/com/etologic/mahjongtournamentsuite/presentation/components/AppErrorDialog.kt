package com.etologic.mahjongtournamentsuite.presentation.components

import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.theme.MtsTheme
import mahjongtournamentsuite.ui.generated.resources.Res
import mahjongtournamentsuite.ui.generated.resources.action_ok
import mahjongtournamentsuite.ui.generated.resources.dialog_done_title
import mahjongtournamentsuite.ui.generated.resources.dialog_error_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) = AppMessageDialog(title = stringResource(Res.string.dialog_error_title), message = message, onDismiss = onDismiss)

@Composable
fun AppErrorDialog(
    message: UiText,
    onDismiss: () -> Unit,
) = AppErrorDialog(message = message.asString(), onDismiss = onDismiss)

/** A confirmation of a finished action. It has the same layout as the error dialog. */
@Composable
fun AppSuccessDialog(
    message: String,
    onDismiss: () -> Unit,
) = AppMessageDialog(title = stringResource(Res.string.dialog_done_title), message = message, onDismiss = onDismiss)

@Composable
fun AppSuccessDialog(
    message: UiText,
    onDismiss: () -> Unit,
) = AppSuccessDialog(message = message.asString(), onDismiss = onDismiss)

@Composable
private fun AppMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val dismissFocusRequester = remember { FocusRequester() }
    val scrollbarPadding = PlatformScrollbarThickness

    LaunchedEffect(message) {
        dismissFocusRequester.requestFocus()
    }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .padding(end = scrollbarPadding),
                ) {
                    item {
                        SelectionContainer {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (PlatformScrollbarThickness > 0.dp) {
                    PlatformVerticalScrollbar(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .heightIn(max = 240.dp)
                            .width(scrollbarPadding),
                    )
                }
            }
        },
        confirmButton = {
            FocusedButton(
                onClick = onDismiss,
                focusRequester = dismissFocusRequester,
                buttonModifier = Modifier.focusLoop(
                    previous = dismissFocusRequester,
                    next = dismissFocusRequester,
                ),
            ) {
                Text(stringResource(Res.string.action_ok))
            }
        },
    )
}

@Preview(device = Devices.DESKTOP)
@Composable
private fun AppErrorDialogPreview() {
    MtsTheme(useDarkTheme = false) {
        AppErrorDialog(
            message = "Something went wrong.\nThis is a sample error preview.",
            onDismiss = {},
        )
    }
}
