package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteFailure
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteOutcome
import com.etologic.mahjongtournamentsuite.domain.model.EmaPlayerDeleteResult
import com.etologic.mahjongtournamentsuite.domain.model.Player
import com.etologic.mahjongtournamentsuite.domain.model.displayName
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedTextButton
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.components.focusLoop
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.clear
import mahjongtournamentsuite.adminapp.generated.resources.delete_players_count
import mahjongtournamentsuite.adminapp.generated.resources.dialog_done_title
import mahjongtournamentsuite.adminapp.generated.resources.ema_player_delete_failure_not_found
import mahjongtournamentsuite.adminapp.generated.resources.ema_player_delete_failure_unknown
import mahjongtournamentsuite.adminapp.generated.resources.ema_players_delete_cannot_undo
import mahjongtournamentsuite.adminapp.generated.resources.ema_players_delete_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.ema_players_delete_deactivated
import mahjongtournamentsuite.adminapp.generated.resources.ema_players_delete_deleted
import mahjongtournamentsuite.adminapp.generated.resources.ema_players_delete_failed
import mahjongtournamentsuite.adminapp.generated.resources.ema_players_delete_partial_title
import mahjongtournamentsuite.adminapp.generated.resources.ema_players_delete_will_deactivate
import mahjongtournamentsuite.adminapp.generated.resources.ema_players_delete_will_delete
import mahjongtournamentsuite.adminapp.generated.resources.ok
import mahjongtournamentsuite.adminapp.generated.resources.select_all_shown
import mahjongtournamentsuite.adminapp.generated.resources.selected_count
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/** The bar above the player base list. Admins select EMA players here and delete them. Their accounts stay. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PlayerSelectionBar(
    shownCount: Int,
    selectedCount: Int,
    busy: Boolean,
    onSelectAllShown: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(enabled = shownCount > 0 && !busy, onClick = onSelectAllShown) {
            Text(stringResource(Res.string.select_all_shown, shownCount))
        }
        TextButton(enabled = selectedCount > 0 && !busy, onClick = onClear) { Text(stringResource(Res.string.clear)) }
        Text(stringResource(Res.string.selected_count, selectedCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(
            enabled = selectedCount > 0 && !busy,
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(Res.string.delete_players_count, selectedCount), Modifier.padding(start = 8.dp))
        }
    }
}

/** A chosen player with data in tournaments: its name, as the list shows it, and the names of those tournaments. */
internal data class PlayerWithData(val name: String, val tournaments: List<String>)

/**
 * Asks before the app deletes many EMA players. The text tells apart the players that are deleted for good and the ones
 * with results in tournaments, which are deactivated instead and listed with their tournaments. Cancel has the focus.
 */
@Composable
internal fun EmaPlayerDeletesConfirmDialog(
    count: Int,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    withData: List<PlayerWithData> = emptyList(),
) {
    val deleteFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val deletable = (count - withData.size).coerceAtLeast(0)
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    TrackedDialog("DeletePlayersConfirm") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = { if (!busy) onDismiss() },
            title = { Text(stringResource(Res.string.ema_players_delete_confirm_title)) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (deletable > 0) {
                        Text(pluralStringResource(Res.plurals.ema_players_delete_will_delete, deletable, deletable))
                    }
                    if (withData.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(pluralStringResource(Res.plurals.ema_players_delete_will_deactivate, withData.size, withData.size))
                            PlayerLinesCard(withData.map { player -> PlayerLine(player.name, player.tournaments.map { "• $it" }) })
                        }
                    }
                    if (deletable > 0) {
                        Text(
                            stringResource(Res.string.ema_players_delete_cannot_undo),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                FocusedButton(
                    onClick = onConfirm,
                    enabled = !busy,
                    focusRequester = deleteFocus,
                    buttonModifier = Modifier.focusLoop(cancelFocus, cancelFocus),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(Res.string.delete_players_count, count)) }
            },
            dismissButton = {
                FocusedTextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    focusRequester = cancelFocus,
                    buttonModifier = Modifier.focusLoop(deleteFocus, deleteFocus),
                ) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }
}

/** One player in a [PlayerLinesCard]: the name and the lines under it. */
private class PlayerLine(val name: String, val details: List<String>)

/** A soft card with a scrollable list of players, so the list reads apart from the text around it. */
@Composable
private fun PlayerLinesCard(lines: List<PlayerLine>, detailColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
// The dialog is already surfaceContainerHigh: the lowest container and a thin border set the card apart in both themes.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 200.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lines) { line ->
                Column {
                    Text(line.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    line.details.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = detailColor) }
                }
            }
        }
    }
}

private fun EmaPlayerDeleteFailure?.textRes(): StringResource = when (this) {
    EmaPlayerDeleteFailure.NOT_FOUND -> Res.string.ema_player_delete_failure_not_found
    EmaPlayerDeleteFailure.UNKNOWN, null -> Res.string.ema_player_delete_failure_unknown
}

/** The result of a bulk delete: how many players are gone or deactivated, and one line for each player that failed. */
@Composable
internal fun EmaPlayerDeletesResultDialog(results: List<EmaPlayerDeleteResult>, players: List<Player>, onDismiss: () -> Unit) {
    val deleted = results.count { it.outcome == EmaPlayerDeleteOutcome.DELETED }
    val disabled = results.count { it.outcome == EmaPlayerDeleteOutcome.DISABLED }
    val failed = results.filter { it.outcome == EmaPlayerDeleteOutcome.FAILED }
    TrackedDialog("DeletePlayersDone") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (failed.isEmpty()) Res.string.dialog_done_title else Res.string.ema_players_delete_partial_title)) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (deleted > 0) Text(pluralStringResource(Res.plurals.ema_players_delete_deleted, deleted, deleted))
                    if (disabled > 0) Text(pluralStringResource(Res.plurals.ema_players_delete_deactivated, disabled, disabled))
                    if (failed.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                pluralStringResource(Res.plurals.ema_players_delete_failed, failed.size, failed.size),
                                color = MaterialTheme.colorScheme.error,
                            )
                            PlayerLinesCard(
                                failed.map { result ->
                                    val name = players.firstOrNull { it.emaId == result.emaId }?.let { "${it.displayName} (${it.emaId})" } ?: result.emaId
                                    PlayerLine(name, listOf(stringResource(result.reason.textRes())))
                                },
                                detailColor = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = onDismiss) { Text(stringResource(Res.string.ok)) } },
        )
    }
}
