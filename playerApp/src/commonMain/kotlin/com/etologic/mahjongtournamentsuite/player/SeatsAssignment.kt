package com.etologic.mahjongtournamentsuite.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.presentation.components.SeatWind
import com.etologic.mahjongtournamentsuite.presentation.components.WindIcon
import com.etologic.mahjongtournamentsuite.presentation.components.label
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.UiText
import com.etologic.mahjongtournamentsuite.presentation.util.asString
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.launch
import mahjongtournamentsuite.playerapp.generated.resources.Res
import mahjongtournamentsuite.playerapp.generated.resources.action_cancel
import mahjongtournamentsuite.playerapp.generated.resources.seats_assign
import mahjongtournamentsuite.playerapp.generated.resources.seats_change
import mahjongtournamentsuite.playerapp.generated.resources.seats_restart
import mahjongtournamentsuite.playerapp.generated.resources.seats_save
import mahjongtournamentsuite.playerapp.generated.resources.seats_title
import mahjongtournamentsuite.playerapp.generated.resources.seats_who_sits
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedButton as Button
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedOutlinedButton as OutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.DebouncedTextButton as TextButton

/** The buttons with the names of the players that the seats dialog offers. */
const val SEAT_OPTION_TAG = "seat-option"

/**
 * A player of the table sets the four seats before the first hand: East, South, West and North, one after the other.
 * The last seat takes the player who is left. The button says "Change seats" once the seats are set.
 */
@Composable
internal fun SeatsAssignment(
    tournamentId: String,
    view: PlayerTournamentView,
    table: PlayerTable,
    onSaved: () -> Unit,
) {
    val presenter = koinInject<PlayerTablePresenter>()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<UiText?>(null) }
    val picks = remember { mutableStateListOf<Int>() }

    val label = if (table.seatsAssigned) Res.string.seats_change else Res.string.seats_assign
    if (table.seatsAssigned) {
        TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
    } else {
        Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(label)) }
    }
    message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
    if (!open) return

    fun close() {
        open = false
        picks.clear()
    }

    val players = table.table.playerIds
    val left = players.filter { it !in picks }
    // The last player takes the last seat without a question.
    if (picks.size == 3 && left.size == 1) picks.add(left.single())
    val complete = picks.size == 4

    TrackedDialog("AssignSeats") {
        AlertDialog(
            onDismissRequest = { if (!busy) close() },
            title = { Text(stringResource(Res.string.seats_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    picks.forEachIndexed { index, slot ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WindIcon(SeatWind.entries[index], size = 20.dp)
                            Text("${SeatWind.entries[index].label()}: ${view.nameOf(slot)}")
                        }
                    }
                    if (!complete) {
                        val wind = SeatWind.entries[picks.size]
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WindIcon(wind, size = 20.dp)
                            Text(stringResource(Res.string.seats_who_sits, wind.label()), style = MaterialTheme.typography.titleSmall)
                        }
                        left.forEach { slot ->
                            OutlinedButton(onClick = { picks.add(slot) }, modifier = Modifier.fillMaxWidth().testTag(SEAT_OPTION_TAG)) { Text(view.nameOf(slot)) }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = complete && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = presenter.assignSeats(tournamentId, table.roundId, table.tableId, picks.toList(), table.table.version)
                            busy = false
                            message = (result as? AppResult.Failure)?.error?.toUiText()
                            // A table that changed loads again, so the player sees the seats of the other player.
                            if (result is AppResult.Success || (result as? AppResult.Failure)?.error is AppError.Conflict) onSaved()
                            close()
                        }
                    },
                ) { Text(stringResource(Res.string.seats_save)) }
            },
            dismissButton = {
                Row {
                    if (picks.isNotEmpty()) TextButton(enabled = !busy, onClick = { picks.clear() }) { Text(stringResource(Res.string.seats_restart)) }
                    TextButton(enabled = !busy, onClick = { close() }) { Text(stringResource(Res.string.action_cancel)) }
                }
            },
        )
    }
}
