package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup

/** The headline of one notice. The wording tells the user what happened to the typed values. */
internal fun TableUpdateNotice.headline(): String {
    val table = "Round ${key.roundId} · Table ${key.tableId}"
    return when {
        saveRejected -> "$table: someone else saved this table first. Your changes were not saved."
        lostChanges.isNotEmpty() -> "$table: another user changed this table. Your unsaved changes were replaced."
        else -> "$table: another user changed this table. It now shows the latest data."
    }
}

/** Tells the user that server data replaced one or more tables. The user must accept it. */
@Composable
internal fun TableUpdatesDialog(
    notices: List<TableUpdateNotice>,
    onDismiss: () -> Unit,
) {
    val okFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    LaunchedEffect(notices.size) { okFocusRequester.requestFocus() }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        onDismissRequest = onDismiss,
        title = { Text(if (notices.size == 1) "Table updated" else "Tables updated") },
        text = {
            ScrollableColumnWithScrollbar(
                state = scrollState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            ) {
                notices.forEachIndexed { index, notice ->
                    if (index > 0) Spacer(Modifier.height(16.dp))
                    Text(notice.headline(), fontWeight = FontWeight.Bold)
                    if (notice.lostChanges.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Enter these values again if they are still correct:", style = MaterialTheme.typography.bodySmall)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            notice.lostChanges.forEach { change ->
                                Text("• ${change.label}: ${change.value}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FocusedButton(onClick = onDismiss, focusRequester = okFocusRequester) { Text("OK") }
        },
    )
}

/** Shown when the user was away. Polling stopped, so the data may be old until the user continues. */
@Composable
internal fun IdlePauseDialog(onContinue: () -> Unit, isChecking: Boolean) {
    val continueFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { continueFocusRequester.requestFocus() }

    AlertDialog(
        modifier = Modifier.appFocusGroup(),
        // The user must choose Continue. A tap outside the dialog must not hide it.
        onDismissRequest = {},
        title = { Text("Updates paused") },
        text = {
            Text(
                "The app stopped checking for changes because you were away. " +
                    "Continue to load the latest data before you edit.",
            )
        },
        confirmButton = {
            FocusedButton(
                enabled = !isChecking,
                onClick = onContinue,
                focusRequester = continueFocusRequester,
            ) { Text(if (isChecking) "Checking…" else "Continue") }
        },
    )
}
