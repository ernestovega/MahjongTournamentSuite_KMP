package com.etologic.mahjongtournamentsuite.presentation.screen

import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.enter_these_values_again_if_they
import mahjongtournamentsuite.adminapp.generated.resources.lost_change_line
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.table_headline_latest
import mahjongtournamentsuite.adminapp.generated.resources.table_headline_rejected
import mahjongtournamentsuite.adminapp.generated.resources.table_headline_replaced
import mahjongtournamentsuite.adminapp.generated.resources.table_updated
import mahjongtournamentsuite.adminapp.generated.resources.tables_updated
import org.jetbrains.compose.resources.stringResource

/** The headline of one notice. The wording tells the user what happened to the typed values. */
internal fun TableUpdateNotice.headline(): UiText = when {
    saveRejected -> UiText.of(Res.string.table_headline_rejected, key.roundId, key.tableId)
    lostChanges.isNotEmpty() -> UiText.of(Res.string.table_headline_replaced, key.roundId, key.tableId)
    else -> UiText.of(Res.string.table_headline_latest, key.roundId, key.tableId)
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

    TrackedDialog("TableUpdated") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (notices.size == 1) Res.string.table_updated else Res.string.tables_updated)) },
            text = {
                ScrollableColumnWithScrollbar(
                    state = scrollState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                ) {
                    notices.forEachIndexed { index, notice ->
                        if (index > 0) Spacer(Modifier.height(16.dp))
                        Text(notice.headline().asString(), fontWeight = FontWeight.Bold)
                        if (notice.lostChanges.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(Res.string.enter_these_values_again_if_they), style = MaterialTheme.typography.bodySmall)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                notice.lostChanges.forEach { change ->
                                    Text(stringResource(Res.string.lost_change_line, change.label.asString(), change.value.asString()), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FocusedButton(onClick = onDismiss, focusRequester = okFocusRequester) { Text(stringResource(Res.string.ok)) }
            },
        )
    }
}
