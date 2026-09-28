package com.etologic.mahjongtournamentsuite.presentation.components

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

@Composable
fun AppErrorDialog(
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
        title = { Text("Error") },
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
                Text("OK")
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
