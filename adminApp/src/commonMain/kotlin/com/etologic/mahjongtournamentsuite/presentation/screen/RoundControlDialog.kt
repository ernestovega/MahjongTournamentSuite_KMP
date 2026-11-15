package com.etologic.mahjongtournamentsuite.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etologic.mahjongtournamentsuite.domain.model.AppError
import com.etologic.mahjongtournamentsuite.domain.model.AppResult
import com.etologic.mahjongtournamentsuite.domain.model.ControlMode
import com.etologic.mahjongtournamentsuite.domain.model.RoundControlChange
import com.etologic.mahjongtournamentsuite.domain.model.TournamentTable
import com.etologic.mahjongtournamentsuite.domain.repository.RefreshMode
import com.etologic.mahjongtournamentsuite.domain.repository.TournamentRepository
import com.etologic.mahjongtournamentsuite.presentation.components.AppErrorDialog
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedIconButton
import com.etologic.mahjongtournamentsuite.presentation.components.FocusedOutlinedButton
import com.etologic.mahjongtournamentsuite.presentation.components.ScrollableColumnWithScrollbar
import com.etologic.mahjongtournamentsuite.presentation.components.appFocusGroup
import com.etologic.mahjongtournamentsuite.presentation.telemetry.LocalTelemetry
import com.etologic.mahjongtournamentsuite.presentation.telemetry.TrackedDialog
import com.etologic.mahjongtournamentsuite.presentation.util.toUiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mahjongtournamentsuite.adminapp.generated.resources.Res
import mahjongtournamentsuite.adminapp.generated.resources.cancel
import mahjongtournamentsuite.adminapp.generated.resources.close
import mahjongtournamentsuite.adminapp.generated.resources.decrease_round
import mahjongtournamentsuite.adminapp.generated.resources.enter_remaining_time_in_minutes
import mahjongtournamentsuite.adminapp.generated.resources.increase_round
import mahjongtournamentsuite.adminapp.generated.resources.remaining_time
import mahjongtournamentsuite.adminapp.generated.resources.reset_timer
import mahjongtournamentsuite.adminapp.generated.resources.round_control_auto
import mahjongtournamentsuite.adminapp.generated.resources.round_control_clock
import mahjongtournamentsuite.adminapp.generated.resources.round_control_closed
import mahjongtournamentsuite.adminapp.generated.resources.round_control_completed_notice
import mahjongtournamentsuite.adminapp.generated.resources.round_control_hands
import mahjongtournamentsuite.adminapp.generated.resources.round_control_hands_help
import mahjongtournamentsuite.adminapp.generated.resources.round_control_manual_warning
import mahjongtournamentsuite.adminapp.generated.resources.round_control_no_tables
import mahjongtournamentsuite.adminapp.generated.resources.round_control_not_started
import mahjongtournamentsuite.adminapp.generated.resources.round_control_open
import mahjongtournamentsuite.adminapp.generated.resources.round_control_other_round_in_play
import mahjongtournamentsuite.adminapp.generated.resources.round_control_over
import mahjongtournamentsuite.adminapp.generated.resources.round_control_paused
import mahjongtournamentsuite.adminapp.generated.resources.round_control_round_in_play
import mahjongtournamentsuite.adminapp.generated.resources.round_control_running
import mahjongtournamentsuite.adminapp.generated.resources.round_control_seats
import mahjongtournamentsuite.adminapp.generated.resources.round_control_seats_help
import mahjongtournamentsuite.adminapp.generated.resources.round_control_table_completed
import mahjongtournamentsuite.adminapp.generated.resources.round_control_table_in_play
import mahjongtournamentsuite.adminapp.generated.resources.round_control_table_round
import mahjongtournamentsuite.adminapp.generated.resources.round_control_tables
import mahjongtournamentsuite.adminapp.generated.resources.round_control_tables_help
import mahjongtournamentsuite.adminapp.generated.resources.round_control_title
import mahjongtournamentsuite.adminapp.generated.resources.round_n
import mahjongtournamentsuite.adminapp.generated.resources.set_time
import mahjongtournamentsuite.adminapp.generated.resources.table
import mahjongtournamentsuite.adminapp.generated.resources.timer_gong_resume
import mahjongtournamentsuite.adminapp.generated.resources.timer_gong_start
import mahjongtournamentsuite.adminapp.generated.resources.timer_late_start
import mahjongtournamentsuite.adminapp.generated.resources.timer_offline
import mahjongtournamentsuite.adminapp.generated.resources.timer_pause
import mahjongtournamentsuite.adminapp.generated.resources.timer_reset_confirm
import mahjongtournamentsuite.adminapp.generated.resources.timer_reset_confirm_message
import mahjongtournamentsuite.adminapp.generated.resources.timer_reset_confirm_title
import mahjongtournamentsuite.adminapp.generated.resources.timer_started_ago
import mahjongtournamentsuite.adminapp.generated.resources.timer_started_ago_message
import mahjongtournamentsuite.adminapp.generated.resources.timer_started_ago_title
import mahjongtournamentsuite.adminapp.generated.resources.timer_stop
import mahjongtournamentsuite.adminapp.generated.resources.timer_stop_confirm
import mahjongtournamentsuite.adminapp.generated.resources.timer_stop_confirm_message
import mahjongtournamentsuite.adminapp.generated.resources.timer_stop_confirm_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * Round control: the plan B of the Timer, for admins. A dialog over the tournament screen. It holds the clock buttons
 * of a round, so nobody depends on the Timer, and the manual control of what the players can do: hand entry and seats
 * for the whole round, the hand entry of one table, and whether a table is completed (the same mark as the Completed
 * switch of the table editor). A manual mode stays until an admin sets it back to automatic.
 */
@Composable
fun RoundControlDialog(
    tournamentId: String,
    initialRound: Int,
    onDismiss: () -> Unit,
) {
    val repository = koinInject<TournamentRepository>()
    val telemetry = LocalTelemetry.current
    val controller = remember(tournamentId) { RoundTimerController(repository, tournamentId, initialRound, telemetry = telemetry) }
    RoundControlDialogContent(
        controller = controller,
        loadTables = { roundId, mode -> repository.listTournamentTables(tournamentId, roundId, mode) },
        onDismiss = onDismiss,
    )
}

/** How often the screen reads the clocks and the tables, to follow the other admins and the players. */
private const val ROUND_CONTROL_POLL_MS = 5_000L

private enum class RoundControlPrompt { SetTime, StartedAgo, ConfirmStop }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RoundControlDialogContent(
    controller: RoundTimerController,
    loadTables: suspend (roundId: Int, mode: RefreshMode) -> AppResult<List<TournamentTable>>,
    onDismiss: () -> Unit,
    deviceNowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val scope = rememberCoroutineScope()
    val state by controller.state.collectAsState()
    var tables by remember { mutableStateOf<List<TournamentTable>?>(null) }
    var error by remember { mutableStateOf<AppError?>(null) }
    var dialog by remember { mutableStateOf<RoundControlPrompt?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Bumped after a table changes its completed mark, so the tables load again at once.
    var tablesRevision by remember { mutableStateOf(0) }

    LaunchedEffect(controller) {
        while (true) {
            controller.refresh()
            delay(ROUND_CONTROL_POLL_MS)
        }
    }
    LaunchedEffect(state.roundId) { tables = null }
    LaunchedEffect(state.roundId, state.tournament != null, tablesRevision) {
        if (state.tournament == null) return@LaunchedEffect
        var mode = if (tablesRevision > 0) RefreshMode.FORCE else RefreshMode.IF_CHANGED
        while (true) {
            (loadTables(state.roundId, mode) as? AppResult.Success)?.let { result -> tables = result.value.sortedBy { it.tableId } }
            mode = RefreshMode.IF_CHANGED
            delay(ROUND_CONTROL_POLL_MS)
        }
    }
    val now by produceState(deviceNowMs()) {
        while (true) {
            delay(500)
            value = deviceNowMs()
        }
    }

    fun change(change: RoundControlChange) {
        if (busy) return
        busy = true
        scope.launch {
            val result = controller.changeControl(change)
            busy = false
            if (result is AppResult.Failure) error = result.error
            if (change.reopenTables.isNotEmpty() || change.completeTables.isNotEmpty()) tablesRevision += 1
        }
    }

    val remaining = state.remainingSeconds(now)
    val finished = state.isStarted && remaining <= 0L
    val canClock = state.canControl && !state.isOffline
    val control = state.control
    // The round whose clock runs now, if any. The dialog says when the admin looks at another round.
    val runningRound = state.clocks.runningRound(state.durationMinutes, state.serverNow(now))

    error?.let { AppErrorDialog(it.toUiText(), onDismiss = { error = null }) }
    when (dialog) {
        RoundControlPrompt.SetTime -> MinutesDialog(
            name = "RoundControlSetTime",
            title = stringResource(Res.string.remaining_time),
            message = stringResource(Res.string.enter_remaining_time_in_minutes),
            initialMinutes = ((remaining + 59) / 60).coerceAtLeast(1),
            allowZero = false,
            onApply = { minutes ->
                dialog = null
                scope.launch { controller.setRemainingMinutes(minutes) }
            },
            onDismiss = { dialog = null },
        )
        RoundControlPrompt.StartedAgo -> MinutesDialog(
            name = "RoundControlStartedAgo",
            title = stringResource(Res.string.timer_started_ago_title),
            message = stringResource(Res.string.timer_started_ago_message),
            initialMinutes = 0,
            allowZero = true,
            onApply = { minutes ->
                dialog = null
                scope.launch { controller.startedMinutesAgo(minutes.toInt()) }
            },
            onDismiss = { dialog = null },
        )
        RoundControlPrompt.ConfirmStop -> ConfirmDialog(
            name = "RoundControlConfirmStop",
            title = stringResource(Res.string.timer_stop_confirm_title),
            message = stringResource(Res.string.timer_stop_confirm_message),
            confirm = stringResource(Res.string.timer_stop_confirm),
            onConfirm = {
                dialog = null
                scope.launch { controller.stop() }
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
    if (state.confirmReset) {
        ConfirmDialog(
            name = "RoundControlConfirmReset",
            title = stringResource(Res.string.timer_reset_confirm_title),
            message = stringResource(Res.string.timer_reset_confirm_message),
            confirm = stringResource(Res.string.timer_reset_confirm),
            onConfirm = { scope.launch { controller.reset(force = true) } },
            onDismiss = { controller.dismissResetConfirmation() },
        )
    }

    val scrollState = rememberScrollState()
    TrackedDialog("RoundControl") {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = {
                Column {
                    Text(stringResource(Res.string.round_control_title))
                    state.tournament?.name?.ifBlank { null }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                ScrollableColumnWithScrollbar(state = scrollState, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // The round. Picking a round changes nothing on the server.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FocusedIconButton(enabled = state.roundId > 1, onClick = { controller.selectRound(state.roundId - 1) }) {
                                Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.decrease_round), modifier = Modifier.size(18.dp))
                            }
                            Text(
                                text = stringResource(Res.string.round_n, state.roundId),
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(horizontal = 8.dp).testTag("round_control_round"),
                            )
                            FocusedIconButton(enabled = state.roundId < state.numRounds, onClick = { controller.selectRound(state.roundId + 1) }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.increase_round), modifier = Modifier.size(18.dp))
                            }
                        }
                        when {
                            runningRound == state.roundId -> Text(
                                stringResource(Res.string.round_control_round_in_play),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            runningRound != null -> Text(
                                stringResource(Res.string.round_control_other_round_in_play, runningRound),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.tournamentCompleted) Notice(stringResource(Res.string.round_control_completed_notice), warning = false)
                        if (control.isManual) Notice(stringResource(Res.string.round_control_manual_warning), warning = true)

                        Section(title = stringResource(Res.string.round_control_clock)) {
                            val status = when {
                                !state.isStarted -> Res.string.round_control_not_started
                                finished -> Res.string.round_control_over
                                !state.isRunning -> Res.string.round_control_paused
                                else -> Res.string.round_control_running
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Like the Timer: an ended round shows its time and its state in red.
                                val statusColor = if (finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                Text(
                                    formatHms(remaining),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (finished) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(stringResource(status), style = MaterialTheme.typography.titleMedium, color = statusColor)
                            }
                            if (state.isOffline) Text(stringResource(Res.string.timer_offline), color = MaterialTheme.colorScheme.error)
                            state.latePlannedStart(now)?.let { Text(stringResource(Res.string.timer_late_start, it), color = MaterialTheme.colorScheme.error) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when {
                                    !state.isStarted -> {
                                        FocusedButton(enabled = canClock, onClick = { scope.launch { controller.play() } }) {
                                            Text(stringResource(Res.string.timer_gong_start))
                                        }
                                        FocusedOutlinedButton(enabled = canClock, onClick = { dialog = RoundControlPrompt.StartedAgo }) {
                                            Text(stringResource(Res.string.timer_started_ago))
                                        }
                                    }
                                    !finished -> {
                                        if (state.isRunning) {
                                            FocusedButton(enabled = canClock, onClick = { scope.launch { controller.pause() } }) {
                                                Text(stringResource(Res.string.timer_pause))
                                            }
                                        } else {
                                            FocusedButton(enabled = canClock, onClick = { scope.launch { controller.play() } }) {
                                                Text(stringResource(Res.string.timer_gong_resume))
                                            }
                                        }
                                        FocusedOutlinedButton(enabled = canClock, onClick = { dialog = RoundControlPrompt.ConfirmStop }) {
                                            Text(stringResource(Res.string.timer_stop))
                                        }
                                    }
                                }
                                if (state.isStarted) {
                                    FocusedOutlinedButton(enabled = canClock, onClick = { dialog = RoundControlPrompt.SetTime }) {
                                        Text(stringResource(Res.string.set_time))
                                    }
                                    FocusedOutlinedButton(enabled = canClock, onClick = { scope.launch { controller.askReset() } }) {
                                        Text(stringResource(Res.string.reset_timer))
                                    }
                                }
                            }
                        }

                        Section(title = stringResource(Res.string.round_control_hands)) {
                            Text(stringResource(Res.string.round_control_hands_help), style = MaterialTheme.typography.bodySmall)
                            ModeChips(tag = "hands", selected = control.hands, enabled = !busy) { change(RoundControlChange(hands = it)) }
                        }
                        Section(title = stringResource(Res.string.round_control_seats)) {
                            Text(stringResource(Res.string.round_control_seats_help), style = MaterialTheme.typography.bodySmall)
                            ModeChips(tag = "seats", selected = control.seats, enabled = !busy) { change(RoundControlChange(seats = it)) }
                        }

                        Section(title = stringResource(Res.string.round_control_tables)) {
                            Text(stringResource(Res.string.round_control_tables_help), style = MaterialTheme.typography.bodySmall)
                            val rows = tables
                            when {
                                rows == null -> Unit
                                rows.isEmpty() -> Text(stringResource(Res.string.round_control_no_tables))
                                else -> rows.forEachIndexed { index, table ->
                                    if (index > 0) HorizontalDivider()
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("${stringResource(Res.string.table)} ${table.tableId}", fontWeight = FontWeight.SemiBold)
                                            // The same mark as the Completed switch of the table editor. A completed table takes no hand.
                                            FilterChip(
                                                selected = !table.isCompleted,
                                                enabled = !busy,
                                                onClick = { if (table.isCompleted) change(RoundControlChange(reopenTables = listOf(table.tableId))) },
                                                label = { Text(stringResource(Res.string.round_control_table_in_play)) },
                                                modifier = Modifier.testTag("table_${table.tableId}_in_play"),
                                            )
                                            FilterChip(
                                                selected = table.isCompleted,
                                                enabled = !busy,
                                                onClick = { if (!table.isCompleted) change(RoundControlChange(completeTables = listOf(table.tableId))) },
                                                label = { Text(stringResource(Res.string.round_control_table_completed)) },
                                                modifier = Modifier.testTag("table_${table.tableId}_completed"),
                                            )
                                        }
                                        ModeChips(
                                            tag = "table_${table.tableId}",
                                            selected = control.tables[table.tableId] ?: ControlMode.AUTO,
                                            enabled = !busy,
                                            autoLabel = Res.string.round_control_table_round,
                                        ) { mode -> change(RoundControlChange(table = table.tableId to mode)) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FocusedButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChips(
    tag: String,
    selected: ControlMode,
    enabled: Boolean,
    autoLabel: StringResource = Res.string.round_control_auto,
    onSelect: (ControlMode) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlMode.entries.forEach { mode ->
            val label = when (mode) {
                ControlMode.AUTO -> autoLabel
                ControlMode.OPEN -> Res.string.round_control_open
                ControlMode.CLOSED -> Res.string.round_control_closed
            }
            FilterChip(
                selected = mode == selected,
                enabled = enabled,
                onClick = { if (mode != selected) onSelect(mode) },
                label = { Text(stringResource(label)) },
                modifier = Modifier.testTag("${tag}_${mode.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Notice(text: String, warning: Boolean) {
    Surface(
        color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ConfirmDialog(
    name: String,
    title: String,
    message: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    TrackedDialog(name) {
        AlertDialog(
            modifier = Modifier.appFocusGroup(),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { FocusedButton(onClick = onConfirm) { Text(confirm) } },
            dismissButton = { FocusedButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}
